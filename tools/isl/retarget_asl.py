"""Retargets PopSign ASL signers onto the rig like retarget.py and authors the one-hand alphabet; drafts until ASL
review. Usage and licences are in tools/isl/README.md."""
import functools
import io
import json
import os
import pathlib
import pickle
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

import numpy as np

import retarget as rt

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))

ROOT = pathlib.Path(__file__).resolve().parents[2]
CONTENT = ROOT / "app" / "src" / "main" / "assets" / "content"
GLOSSARY = pathlib.Path(__file__).resolve().parent / "asl_hindi.json"  # word -> [topic category, Hindi]
MODEL = CONTENT / "models" / "holistic_landmarker.task"
DATA = DATA_ROOT / "popsign"  # frame parquets, for the re-detect
LANDMARKS = DATA / "landmarks"  # prepare_asl.py's per-shard cache
ROWS = "https://datasets-server.huggingface.co/rows?dataset=sign/popsign-images&config=game&split=test"
GAME_ROWS = 33492
FPS, SRC_FPS = 5.0, 15.0
CLIPS = 4  # recordings a sign is picked from when no two-hand clip is wanted; the cleanest is retargeted
PX = 256.0  # frames are 256x256, so normalised x, y and z share one scale

CASE = {"tv": "TV"}  # prepare_asl.py lowercases labels; ids keep the gloss
# PopSign glosses that are not plain words; the id keeps the gloss so reader labels find their sign.
WORD = {"haveto": "have to", "hesheit": "he", "weus": "we", "minemy": "my", "callonphone": "call", "frenchfries": "french fries",
        "glasswindow": "window", "TV": "tv", "owie": "hurt", "yucky": "yuck", "thankyou": "thank you", "icecream": "ice cream",
        "shhh": "hush"}
# Reader label to a written form in words.json where the concept is the same, beyond the word itself.
ALIAS = {"thankyou": "thank", "callonphone": "phone", "owie": "hurt", "minemy": "my", "grandma": "grandmother",
         "grandpa": "grandfather", "store": "shop"}
# Words with another everyday meaning; offered in the dictionary but never signed for typed text.
HOMONYMS = {"fall", "can", "will", "close", "drop", "fine", "high", "dry", "have", "cut", "orange", "store"}
# Glosses whose citation form uses both hands. Without a clip showing the left hand the right is drawn alone.
TWO_HANDED = set("""after all alligator awake balloon bath bedroom before beside boat book can car chair clean close closet
cloud cowboy dance drawer empty every fall farm feet finger finish first fish flag gift give glasswindow go hate have
helicopter hide into jacket jeans jump loud make many mitten morning night noisy on open owl pen pencil person pizza
puzzle quiet radio rain read refrigerator ride room same scissors shoe sick snow stairs store story table tiger time
touch toy tree vacuum wait wake wet zebra zipper""".split())


def flag(g: dict) -> dict:
    """A single keyframe is marked static; a known two-handed gloss without a left hand is marked one_hand_source."""
    if g["rec"] == "authored":
        return g
    g.pop("static", None), g.pop("one_hand_source", None)
    out = {**g}
    if len(g["keys"]) < 2:
        out["static"] = True
    if g["id"].removeprefix("asl.").lower() in TWO_HANDED and not any(k.get("L", {}).get("at", "rest") != "rest" for k in g["keys"]):
        out["one_hand_source"] = True
    return {**{k: v for k, v in out.items() if k != "keys"}, "keys": out["keys"]}



def load(s: dict) -> dict:
    """One prepare_asl.py clip in retarget.load's layout, at the source 5 fps: its 15 fps upsampling keeps every
    third frame exact. Holistic names the hands by the signer's own side."""
    k = slice(None, None, int(SRC_FPS // FPS))
    hand = lambda side: np.concatenate([s[side][k], s[f"{side}_z"][k][..., None]], -1) * PX
    return {"pose": s["pose"][k] * PX, "has_pose": s["has_pose"][k], "L": hand("left"), "R": hand("right"),
            "has_L": s["has_left"][k], "has_R": s["has_right"][k]}


def load_all() -> dict[str, list[tuple[str, dict]]]:
    """Every landmarked clip per gloss, test signers first."""
    recs: dict[str, list] = {}
    for p in sorted(LANDMARKS.glob("*.pkl"), key=lambda p: (not p.name.startswith("test"), p.name)):
        try:
            with open(p, "rb") as f:
                shard = pickle.load(f)
        except (EOFError, pickle.UnpicklingError):  # prepare_asl.py still writing it
            print(f"skip {p.name}: incomplete")
            continue
        for s in shard:
            recs.setdefault(CASE.get(s["label"], s["label"]), []).append((s["rec"], load(s)))
    return recs


def two_hands(rec: dict) -> bool:
    """Both hands seen together in a third of the frames, as two hands, the left one raised or level with the right.
    Looser than retarget.used_hands: clips run 2 to 10 frames, close crops push the shoulders low or out of frame."""
    both = rec["has_L"] & rec["has_R"] & rec["has_pose"]
    if both.mean() < 0.3:
        return False
    mid, width = rt.body_frame(rec["pose"])
    L, R = rec["L"][both, 0, :2], rec["R"][both, 0, :2]
    # Holistic sometimes reports one hand twice, wrists a few pixels apart.
    apart = np.linalg.norm(L - R, axis=1) > 0.08 * width
    up = (L[:, 1] < mid[1] + rt.RAISED * width) | (L[:, 1] - R[:, 1] < 0.35 * width)
    return bool(apart.mean() > 0.5 and (apart & up).any())


def _get(url: str, tries: int = 8) -> bytes:
    for i in range(tries):
        try:
            return urllib.request.urlopen(url, timeout=60).read()
        except Exception as e:  # the rows API rate-limits bursts with 429
            if i == tries - 1:
                raise
            time.sleep(5 * (i + 1) if "429" in str(e) else 2)


def fetch_extra(glosses: list[str]) -> None:
    """Up to 12 more game test clips per sign into game-extra.parquet, keeping the other signs already there."""
    import pyarrow as pa
    import pyarrow.parquet as pq
    out = DATA / "game-extra.parquet"
    rows = [r for r in pq.read_table(out).to_pylist() if r["text"] not in glosses] if out.exists() else []
    new = []
    for w in glosses:
        lo, hi = 0, GAME_ROWS
        while hi - lo > 100:  # rows are sorted by gloss
            mid = (lo + hi) // 2
            lo, hi = (mid, hi) if json.loads(_get(f"{ROWS}&offset={mid}&length=1"))["rows"][0]["row"]["text"] < w else (lo, mid)
        got = []
        for off in range(max(0, lo - 100), lo + 400, 100):
            got += [r["row"] for r in json.loads(_get(f"{ROWS}&offset={off}&length=100"))["rows"] if r["row"]["text"] == w]
        new += got[:12]
        print(f"{w}: {len(got[:12])} clips", flush=True)
    with ThreadPoolExecutor(16) as ex:
        for r in new:
            r["images"] = [{"bytes": b, "path": ""} for b in ex.map(_get, [im["src"] for im in r["images"]])]
    pq.write_table(pa.Table.from_pylist(rows + new), out)


def frames(gloss: str) -> list[tuple[str, list[bytes]]]:
    """Every clip of one sign in the frame parquets, the extra clips first; only row groups holding it are read."""
    import pyarrow.parquet as pq
    out = []
    for p in [DATA / "game-extra.parquet"] + sorted(DATA.glob("test-*.parquet")) + [DATA / "game-sample.parquet"]:
        if not p.exists():
            continue
        f = pq.ParquetFile(p)
        for g in range(f.num_row_groups):
            if gloss in f.read_row_group(g, columns=["text"]).column("text").to_pylist():
                out += [(r["file"], [im["bytes"] for im in r["images"]]) for r in f.read_row_group(g, columns=["file", "text", "images"]).to_pylist()
                        if r["text"] == gloss and len(r["images"]) >= 2]
    return out


def landmarks(images: list[bytes], tracker, scale: int = 1) -> dict:
    """load()'s layout from frames, hands by the nearer pose wrist (15 left, 16 right) rather than their own label."""
    import mediapipe as mp
    from PIL import Image
    T = len(images)
    pose, hands = np.zeros((T, 33, 2)), {s: np.zeros((T, 21, 3)) for s in "LR"}
    has = {s: np.zeros(T, bool) for s in ("pose", "L", "R")}
    for i, b in enumerate(images):
        im = Image.open(io.BytesIO(b)).convert("RGB")
        if scale > 1:
            im = im.resize((int(PX) * scale,) * 2, Image.BICUBIC)
        res = tracker.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.asarray(im)))
        if not res.pose_landmarks:
            continue
        pose[i] = [[p.x * PX, p.y * PX] for p in res.pose_landmarks]
        has["pose"][i] = True
        for lms in (res.left_hand_landmarks, res.right_hand_landmarks):
            if not lms:
                continue
            h = np.array([[p.x * PX, p.y * PX, p.z * PX] for p in lms])
            side = "L" if np.linalg.norm(h[0, :2] - pose[i, 15]) < np.linalg.norm(h[0, :2] - pose[i, 16]) else "R"
            hands[side][i], has[side][i] = h, True
    return {"pose": pose, "has_pose": has["pose"], "L": hands["L"], "R": hands["R"], "has_L": has["L"], "has_R": has["R"]}


PASSES = ((0.5, 1), (0.2, 1), (0.5, 2), (0.2, 2))  # (hand and pose confidence, upscale)


@functools.cache
def _trackers() -> list:
    from mediapipe.tasks.python import BaseOptions, vision
    return [vision.HolisticLandmarker.create_from_options(vision.HolisticLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(MODEL)), min_hand_landmarks_confidence=c,
        min_pose_detection_confidence=c, min_pose_landmarks_confidence=c)) for c, _ in PASSES]


def rescue(gloss: str) -> list[tuple[str, dict]]:
    """Landmarks again for one sign at the plain and a lower confidence and at 2x, a frame taking the pass that saw most."""
    out = []
    for f, fr in frames(gloss)[:3 * CLIPS]:
        passes = [landmarks(fr, tr, scale) for tr, (_, scale) in zip(_trackers(), PASSES)]
        rec = {k: v.copy() for k, v in passes[0].items()}
        for i in range(len(fr)):
            best = max(passes, key=lambda r: int(r["has_pose"][i]) * (1 + r["has_L"][i] + r["has_R"][i]))
            for k in rec:
                rec[k][i] = best[k][i]
        if rec["has_pose"].mean() > 0.5:
            out.append((f, right_dominant(rec)))
    print(f"{gloss}: re-detected {len(out)} clips", flush=True)
    return out


POSE_SWAP = [0, 4, 5, 6, 1, 2, 3, 8, 7, 10, 9] + [j for i in range(11, 33, 2) for j in (i + 1, i)]


def right_dominant(rec: dict) -> dict:
    """Mirrors a recording whose left hand does more (a left-handed signer or a mirrored selfie) so the rig's right leads."""
    def work(s):
        w = rec[s][rec[f"has_{s}"], 0, :2]
        return len(w) + (np.linalg.norm(np.diff(w, axis=0), axis=1).sum() / 10 if len(w) > 1 else 0)
    if work("L") <= work("R"):
        return rec
    flip = lambda a: np.concatenate([PX - a[..., :1], a[..., 1:]], axis=-1)
    pose = flip(rec["pose"])[:, POSE_SWAP]
    pose[~rec["has_pose"]] = 0
    return {"pose": pose, "has_pose": rec["has_pose"], "L": flip(rec["R"]), "R": flip(rec["L"]),
            "has_L": rec["has_R"], "has_R": rec["has_L"]}



# One-hand ASL manual alphabet, right hand held up beside the shoulder ("c:" thumb, index, middle, ring, pinky,
# thumbAcross, spread). Roll 0 shows the hand from the thumb side, for letters read in profile.
LETTERS = {
    "a": ("c:20,100,100,100,100,0,0", 1, 0), "b": ("c:55,0,0,0,0,100,-20", 1, 0), "c": ("CUP", 0, -20),
    "d": ("c:45,0,70,75,80,70,0", 1, 0), "e": ("c:50,75,75,75,75,100,-20", 1, 0), "f": ("c:45,55,0,0,0,60,40", 1, 0),
    "g": ("c:10,0,100,100,100,0,0", 0, -90), "h": ("c:55,0,0,100,100,100,-20", 0, -90), "i": ("PINKY", 1, 0),
    "k": ("c:20,0,0,100,100,60,50", 1, 0), "l": ("c:0,0,100,100,100,0,0", 1, 0), "m": ("c:50,80,80,80,100,100,0", 1, 0),
    "n": ("c:50,80,80,100,100,100,0", 1, 0), "o": ("BUNCH", 0, -10), "p": ("c:20,0,0,100,100,60,50", 0, 150),
    "q": ("c:10,0,100,100,100,0,0", 0, 170), "r": ("c:55,0,0,100,100,100,-30", 1, 0), "s": ("FIST", 1, 0),
    "t": ("c:40,85,100,100,100,80,0", 1, 0), "u": ("c:55,0,0,100,100,100,-20", 1, 0), "v": ("TWO", 1, 0),
    "w": ("THREE", 1, 0), "x": ("HOOK", 1, 0), "y": ("Y", 1, 0),
}
# ASL numbers 0-9 in the same hand frame; 6-9 touch the thumb to pinky, ring, middle, index.
DIGITS = {
    "0": ("BUNCH", 0, -10), "1": ("POINT", 1, 0), "2": ("TWO", 1, 0), "3": ("c:0,0,0,100,100,0,40", 1, 0),
    "4": ("c:100,0,0,0,0,100,35", 1, 0), "5": ("OPEN", 1, 0), "6": ("c:50,0,0,0,100,70,30", 1, 0),
    "7": ("c:50,0,0,100,0,70,30", 1, 0), "8": ("c:50,0,100,0,0,70,30", 1, 0), "9": ("c:50,100,0,0,0,70,30", 1, 0),
}
HELD = {"at": "shoulder", "dx": 8, "dy": -40, "elbow": "down"}


def letter_keys(ch: str) -> list[dict]:
    if ch == "j":  # I hand drawing a hook: down, then the palm turns in
        path = [(0.0, 0, 0, 1, 0), (0.25, -2, 6, 1, -20), (0.5, -8, 8, -0.4, -70)]
        return [{"t": t, "R": {**HELD, "dx": 8 + x, "dy": -40 + y, "shape": "PINKY", "roll": r, "angle": a}, "L": {"at": "rest"}}
                for t, x, y, r, a in path]
    if ch == "z":  # index drawing a Z, inward (-dx) first as the signer sees it
        path = [(0.0, -4, -46), (0.25, 12, -46), (0.5, -4, -34), (0.75, 12, -34)]
        return [{"t": t, "R": {**HELD, "dx": x, "dy": y, "shape": "POINT", "roll": 1, "angle": -10}, "L": {"at": "rest"}}
                for t, x, y in path]
    shape, roll, ang = LETTERS.get(ch) or DIGITS[ch]
    hand = {**HELD, "shape": shape, "roll": roll, "angle": ang}
    return [{"t": t, "R": hand, "L": {"at": "rest"}} for t in (0.0, 0.35)]



def word_forms() -> set[str]:
    words = json.loads((CONTENT / "words.json").read_text(encoding="utf-8"))["words"]
    return {w for e in words for w in e["w"]}


MOVE = 8.0  # rig units the hand must travel for a clip of a few frames to count as a moving sign
_key_frames = rt.key_frames


def key_frames(track: np.ndarray, fps: float = rt.FPS, **kw) -> list[int]:
    """retarget.key_frames, but a short moving clip keeps every frame: at 5 fps no frame is a speed minimum."""
    keep = _key_frames(track, fps, **kw)
    if len(keep) < 2 <= len(track) and np.linalg.norm(track - track[0], axis=1).max() > MOVE:
        return list(range(len(track)))
    return keep


rt.key_frames = key_frames


def main() -> None:
    rt.smooth.__defaults__ = (0.5,)  # sigma in frames: retarget.py's 1.2 is for 25 fps, these clips are 5 fps
    made = []
    for gloss, recs in sorted(load_all().items(), key=lambda x: x[0].lower()):
        if not recs:
            print(f"skip {gloss}: no clip with frames")
            continue
        clean = [(rid, r) for rid, r in recs if r["has_pose"].mean() > 0.8]
        # Square crops of portrait clips can lose the body; then take the most visible clip if it is mostly seen.
        clean = clean or [max(recs, key=lambda x: x[1]["has_pose"].mean())]
        recs = [(rid, right_dominant(r)) for rid, r in clean if r["has_pose"].mean() > 0.5]
        if not recs:
            print(f"skip {gloss}: no clean pose")
            continue
        both = [x for x in recs if two_hands(x[1])] if gloss in TWO_HANDED else []
        if both:
            typical = float(np.median([len(r["pose"]) for _, r in both]))
            (rid, rec), hands = max(both, key=lambda x: rt.score(x[1], ["R", "L"], typical)), ["R", "L"]
        else:
            rid, rec, hands = rt.pick(recs[:CLIPS])
        keys = rt.retarget(rec, hands, FPS)
        if len(keys) < 2:  # a moving sign whose hand was found in one frame: the other clips, then a keener detector
            typical = float(np.median([len(r["pose"]) for _, r in recs]))

            def moving(cands):  # the cleanest clip at a typical length that keeps two keys; the longest would win on count
                for (rid2, rec2), h2 in sorted(cands, key=lambda c: -rt.score(c[0][1], c[1], typical)):
                    keys2 = rt.retarget(rec2, h2, FPS)
                    if len(keys2) >= 2 and sum(rec2[f"has_{h}"].sum() for h in h2) >= 2:
                        return rid2, keys2
            rid, keys = moving([(x, hands) for x in both] if both else [(x, rt.pick([x])[2]) for x in recs]) or moving(
                [(x, ["R", "L"] if gloss in TWO_HANDED and two_hands(x[1]) else rt.pick([x])[2]) for x in rescue(gloss)]) or (rid, keys)
        word = WORD.get(gloss, gloss.lower())
        made.append({"id": f"asl.{gloss}", "word": word, "category": "Words", "rec": rid,
                     **({"homonym": True} if word in HOMONYMS else {}), "keys": keys})
    made += [{"id": f"asl.letter.{c}", "word": c, "category": "Alphabet", "rec": "authored", "keys": letter_keys(c)}
             for c in "abcdefghijklmnopqrstuvwxyz"]
    made += [{"id": f"asl.digit.{c}", "word": c, "category": "Alphabet", "rec": "authored", "keys": letter_keys(c)} for c in DIGITS]
    write(made)


def write(made: list[dict]) -> None:
    made = [flag(g) for g in made]
    glossary = json.loads(GLOSSARY.read_text(encoding="utf-8"))
    for g in made:
        if g["rec"] != "authored":  # KeyError on a word missing from the glossary: fail the build
            cat, hi = glossary["words"][g["id"].removeprefix("asl.")]
            g.update(hi=hi, category=cat)
    cats = {k: {"en": en, "hi": hi} for k, (en, hi) in glossary["categories"].items()}
    cats["Alphabet"] = {"en": "Alphabet", "hi": "वर्णमाला"}
    note = ("Retargeted by tools/isl/retarget_asl.py from PopSign signers' landmarks, alphabet authored there; edit the "
            "script, not this file. Draft: every sign needs Deaf ASL review on both characters.")
    source = "PopSign ASL v1.0 (Starner et al. 2023, Georgia Tech and NTID), CC BY 4.0"
    lines = ",\n".join("  " + json.dumps(g, ensure_ascii=False, separators=(",", ":")) for g in made)
    out = CONTENT / "asl_signs.json"
    out.write_text(
        f'{{"note": {json.dumps(note)},\n "source": {json.dumps(source)},\n "review": "draft",\n'
        f' "categories": {json.dumps(cats, ensure_ascii=False)},\n "gestures": [\n{lines}\n]}}\n',
        encoding="utf-8", newline="\n",
    )
    forms = word_forms()
    pairs = {}
    for g in made:
        gloss = g["id"].removeprefix("asl.")
        if g["rec"] == "authored":
            continue
        for cand in (ALIAS.get(gloss), g["word"], gloss.lower()):
            if cand and cand in forms:
                pairs[gloss] = cand
                break
    for label, gloss in CASE.items():  # lowercased reader label, signed back through the gloss's own sign
        pairs.setdefault(label, gloss)
    (CONTENT / "asl_words.json").write_text(json.dumps({
        "status": "draft: each pairing needs ASL review, because a PopSign sign and the app's word may differ in meaning",
        "note": "ASL reader labels (models/asl_reader.json) to a written form in words.json, for concepts the two share.",
        "words": pairs}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
    words = [g for g in made if g["rec"] != "authored"]
    left = sum(any(k.get("L", {}).get("at", "rest") != "rest" for k in g["keys"]) for g in words)
    print(f"asl signs: {len(words)} words + 26 letters + 10 digits, {out.stat().st_size // 1024} KB; {len(pairs)} paired to words; "
          f"{left} with a left hand, {sum(bool(g.get('one_hand_source')) for g in words)} one_hand_source")


if __name__ == "__main__":
    if sys.argv[1:2] == ["extra"]:
        fetch_extra(sys.argv[2:])
    elif sys.argv[1:] == ["flag"]:  # re-flag the written file without the landmark cache
        write(json.loads((CONTENT / "asl_signs.json").read_text(encoding="utf-8"))["gestures"])
    else:
        main()
