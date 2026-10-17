"""Retargets PopSign ASL signers onto the app's 2D rig like retarget.py: writes asl_signs.json and asl_words.json.

PopSign ASL v1.0 (Starner et al. 2023, the 250 signs of Google's Isolated Sign Language Recognition), CC BY 4.0,
frames at 5 fps from huggingface.co/datasets/sign/popsign-images. MediaPipe Holistic gives the landmarks, then
retarget.py's maths makes the keys. The one-hand ASL alphabet is authored as keyframes. Draft until ASL review.
    py=C:/Users/Yash/dev/venvs/signsetu-mp/Scripts/python   # mediapipe, pyarrow
    $py tools/isl/retarget_asl.py fetch      # non-game test shards plus a sample of game test for the other signs
    $py tools/isl/retarget_asl.py            # landmarks (cached), then the JSON
    python tools/content/build_content.py --manifest-only
"""
import io
import json
import pathlib
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

import numpy as np
import pyarrow as pa
import pyarrow.parquet as pq

import retarget as rt

ROOT = pathlib.Path(__file__).resolve().parents[2]
CONTENT = ROOT / "app" / "src" / "main" / "assets" / "content"
MODEL = CONTENT / "models" / "holistic_landmarker.task"
DATA = pathlib.Path("C:/Users/Yash/dev/ml-data/popsign")
REPO = "https://huggingface.co/datasets/sign/popsign-images/resolve/main/data/non-game"
ROWS = "https://datasets-server.huggingface.co/rows?dataset=sign/popsign-images&config=game&split=test"
GAME_ROWS = 33492
FPS = 5.0
CLIPS = 4  # recordings landmarked per sign; the cleanest is retargeted
PX = 256.0  # frames are 256x256, so normalised x, y and z share one scale

# PopSign glosses that are not plain words; the id keeps the gloss so reader labels find their sign.
WORD = {"haveto": "have to", "hesheit": "he", "weus": "we", "minemy": "my", "callonphone": "call", "frenchfries": "french fries",
        "glasswindow": "window", "TV": "tv", "owie": "hurt", "yucky": "yuck", "thankyou": "thank you", "icecream": "ice cream",
        "shhh": "hush"}
# Reader label to a written form in words.json where the concept is the same, beyond the word itself.
ALIAS = {"thankyou": "thank", "callonphone": "phone", "owie": "hurt", "minemy": "my", "grandma": "grandmother",
         "grandpa": "grandfather", "store": "shop"}
# Words with another everyday meaning; offered in the dictionary but never signed for typed text.
HOMONYMS = {"fall", "can", "will", "close", "drop", "fine", "high", "dry", "have", "cut", "orange", "store"}
# Glosses made with both hands; the recordings give no left hand for them, so the right hand alone is drawn.
TWO_HANDED = {"book", "open", "car", "boat", "chair", "table", "drawer", "closet", "puzzle", "read", "ride", "same",
              "refrigerator", "vacuum", "story", "dance", "stairs", "bath", "farm", "helicopter", "scissors"}


def flag(g: dict) -> dict:
    """A single keyframe is marked static; a known two-handed gloss without a left hand is marked one_hand_source."""
    if g["category"] != "Words":
        return g
    g.pop("static", None), g.pop("one_hand_source", None)
    out = {**g}
    if len(g["keys"]) < 2:
        out["static"] = True
    if g["id"].removeprefix("asl.").lower() in TWO_HANDED and not any(k.get("L", {}).get("at", "rest") != "rest" for k in g["keys"]):
        out["one_hand_source"] = True
    return {**{k: v for k, v in out.items() if k != "keys"}, "keys": out["keys"]}


# ---- Data ------------------------------------------------------------------------------------------------------

def _get(url: str, tries: int = 8) -> bytes:
    for i in range(tries):
        try:
            return urllib.request.urlopen(url, timeout=60).read()
        except Exception as e:  # the rows API rate-limits bursts with 429
            if i == tries - 1:
                raise
            time.sleep(5 * (i + 1) if "429" in str(e) else 2)


def fetch() -> None:
    """Non-game test covers 115 signs; game test rows sampled through the rows API fill in the rest."""
    DATA.mkdir(parents=True, exist_ok=True)
    for i in range(4):
        out = DATA / f"test-{i}.parquet"
        if not out.exists():
            out.write_bytes(_get(f"{REPO}/test-0000{i}-of-00004.parquet"))
    have = {t for p in DATA.glob("test-*.parquet") for t in pq.read_table(p, columns=["text"]).column("text").to_pylist()}
    rows, seen = [], {}
    for off in range(0, GAME_ROWS, 90):  # rows are sorted by gloss, about 130 per gloss
        for r in json.loads(_get(f"{ROWS}&offset={off}&length=3"))["rows"]:
            r = r["row"]
            if r["text"] in have or seen.get(r["text"], 0) >= CLIPS:
                continue
            seen[r["text"]] = seen.get(r["text"], 0) + 1
            rows.append(r)
        print(f"{off}/{GAME_ROWS} {len(seen)} signs", flush=True)
        time.sleep(0.5)
    with ThreadPoolExecutor(16) as ex:
        for r in rows:
            r["images"] = [{"bytes": b, "path": ""} for b in ex.map(_get, [im["src"] for im in r["images"]])]
    pq.write_table(pa.Table.from_pylist(rows), DATA / "game-sample.parquet")
    print(f"fetched {len(rows)} game clips for {len(seen)} signs; {len(have)} signs in non-game test")


def clips(p: pathlib.Path) -> dict[str, list[tuple[str, list[bytes]]]]:
    """Up to [CLIPS] recordings per gloss in one parquet file."""
    out: dict[str, list] = {}
    f = pq.ParquetFile(p)
    for g in range(f.num_row_groups):
        for r in f.read_row_group(g).to_pylist():
            got = out.setdefault(r["text"], [])
            if len(got) < CLIPS and len(r["images"]) >= 2:
                got.append((r["file"], [im["bytes"] for im in r["images"]]))
    return out


def landmarks(frames: list[bytes], tracker) -> dict:
    """retarget.load's layout: pose (T,33,2) and hands (T,21,3) in pixels, hands by the signer's own side."""
    import mediapipe as mp
    from PIL import Image
    T = len(frames)
    pose, hands = np.zeros((T, 33, 2)), {s: np.zeros((T, 21, 3)) for s in "LR"}
    has = {s: np.zeros(T, bool) for s in ("pose", "L", "R")}
    for i, b in enumerate(frames):
        img = np.asarray(Image.open(io.BytesIO(b)).convert("RGB"))
        res = tracker.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=img))
        if not res.pose_landmarks:
            continue
        pose[i] = [[p.x * PX, p.y * PX] for p in res.pose_landmarks]
        has["pose"][i] = True
        for lms in (res.left_hand_landmarks, res.right_hand_landmarks):
            if not lms:
                continue
            h = np.array([[p.x * PX, p.y * PX, p.z * PX] for p in lms])
            # Assign by the nearer pose wrist (15 left, 16 right) rather than trust the hand's own label.
            side = "L" if np.linalg.norm(h[0, :2] - pose[i, 15]) < np.linalg.norm(h[0, :2] - pose[i, 16]) else "R"
            hands[side][i], has[side][i] = h, True
    return {"pose": pose, "has_pose": has["pose"], "L": hands["L"], "R": hands["R"], "has_L": has["L"], "has_R": has["R"]}


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


def load_all() -> dict[str, list[tuple[str, dict]]]:
    """Landmarks per gloss, cached next to each parquet file; non-game recordings first."""
    from mediapipe.tasks.python import BaseOptions, vision
    recs: dict[str, list] = {}
    for p in sorted(DATA.glob("test-*.parquet")) + [DATA / "game-sample.parquet"]:
        cache = p.with_suffix(".landmarks.npz")
        if not cache.exists() and p.exists():
            tracker = vision.HolisticLandmarker.create_from_options(
                vision.HolisticLandmarkerOptions(base_options=BaseOptions(model_asset_path=str(MODEL))))
            got = {g: [(f, landmarks(fr, tracker)) for f, fr in items] for g, items in clips(p).items()}
            np.savez(cache, recs=np.array(got, dtype=object))
            print(f"{p.name}: {len(got)} signs", flush=True)
        if cache.exists():
            for g, items in np.load(cache, allow_pickle=True)["recs"].item().items():
                recs[g] = (recs.get(g, []) + items)[:CLIPS]
    return recs


# ---- Alphabet ----------------------------------------------------------------------------------------------------

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
    shape, roll, ang = LETTERS[ch]
    hand = {**HELD, "shape": shape, "roll": roll, "angle": ang}
    return [{"t": t, "R": hand, "L": {"at": "rest"}} for t in (0.0, 0.35)]


# ---- Output -----------------------------------------------------------------------------------------------------

def word_forms() -> set[str]:
    words = json.loads((CONTENT / "words.json").read_text(encoding="utf-8"))["words"]
    return {w for e in words for w in e["w"]}


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
        rid, rec, hands = rt.pick(recs)
        word = WORD.get(gloss, gloss.lower())
        made.append({"id": f"asl.{gloss}", "word": word, "category": "Words", "rec": rid,
                     **({"homonym": True} if word in HOMONYMS else {}), "keys": rt.retarget(rec, hands, FPS)})
    made += [{"id": f"asl.letter.{c}", "word": c, "category": "Alphabet", "rec": "authored", "keys": letter_keys(c)}
             for c in "abcdefghijklmnopqrstuvwxyz"]
    write(made)


def write(made: list[dict]) -> None:
    made = [flag(g) for g in made]
    cats = {"Words": {"en": "Words", "hi": "शब्द"}, "Alphabet": {"en": "Alphabet", "hi": "वर्णमाला"}}
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
        if g["category"] != "Words":
            continue
        for cand in (ALIAS.get(gloss), g["word"], gloss.lower()):
            if cand and cand in forms:
                pairs[gloss] = cand
                break
    (CONTENT / "asl_words.json").write_text(json.dumps({
        "status": "draft: each pairing needs ASL review, because a PopSign sign and the app's word may differ in meaning",
        "note": "ASL reader labels (models/asl_reader.json) to a written form in words.json, for concepts the two share.",
        "words": pairs}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
    print(f"asl signs: {len(made) - 26} words + 26 letters, {out.stat().st_size // 1024} KB; {len(pairs)} paired to words")


if __name__ == "__main__":
    if sys.argv[1:] == ["fetch"]:
        fetch()
    elif sys.argv[1:] == ["flag"]:  # re-flag the written file without the landmark cache
        write(json.loads((CONTENT / "asl_signs.json").read_text(encoding="utf-8"))["gestures"])
    else:
        main()
