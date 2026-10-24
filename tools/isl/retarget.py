"""Retargets INCLUDE signers' Holistic landmarks onto the app's rig into isl_signs.json; drafts until ISL review.
Usage and licences are in tools/isl/README.md."""
import json
import math
import os
import pathlib
import pickle
import re
import sys

import numpy as np

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app" / "src" / "main" / "assets" / "content" / "isl_signs.json"
GLOSSES = pathlib.Path(__file__).resolve().parent / "hindi.json"
FPS = 25.0
# The release divides by a 1080x1920 portrait frame, but the videos are 1920x1080 landscape: undo the stretch.
ASPECT_FIX = np.array([1920 / 1080, 1080 / 1920])
Z_SCALE = 1920.0  # MediaPipe hand z is in the same normalised units as x

# The adult build's anchors (Rig.kt AdultBuild); right-side values, mirrored for the left.
CENTER = 100.0
SHOULDER_Y, SHOULDER_HALF = 136.0, 44.0
UPPER_ARM = 41.0
REACH = UPPER_ARM + 38.0 - 3.0
ANCHORS = {
    "forehead": (100, 52), "eye": (88, 67), "nose": (100, 78), "mouth": (100, 91), "chin": (100, 104),
    "cheek": (84, 84), "ear": (68, 72), "temple": (73, 58), "head_top": (100, 33),
    "chest": (100, 154), "belly": (100, 188), "shoulder": (56, 136), "side": (52, 178), "front": (100, 166),
}
SIDED = {"cheek", "ear", "shoulder", "temple", "side", "eye"}
RIG_EYES_MID, RIG_MOUTH = np.array([100.0, 67.0]), np.array([100.0, 91.0])
SIGN = {"R": -1.0, "L": 1.0}  # Side.sign in Rig.kt: +dx is outward
ARM = {"R": (12, 14), "L": (11, 13)}  # pose shoulder and elbow, the signer's own side
# Words with another everyday meaning; the app offers them in the dictionary but never signs them for typed text.
HOMONYMS = {"fall", "light", "mean", "second", "sign", "ring", "letter", "bill", "card", "cool", "flat", "fan", "it", "key",
            "page", "spring", "court", "race (ethnicity)", "low", "high", "hard", "fast", "loud"}
RAISED = 0.45# a wrist less than this many shoulder widths below the shoulders is signing, not resting


def label(folder: str) -> str:
    return re.sub(r"^(\d+|Ex)\.\s*", "", folder).strip().lower()


def load(path: pathlib.Path) -> dict:
    """Landmarks in true pixels: pose (T,33,2), hands (T,21,3) as the signer's own left and right, with presence."""
    with open(path, "rb") as f:
        k = pickle.load(f)["keypoints"].astype(np.float64)
    pose = k[:, :33, :2] * ASPECT_FIX
    out = {"pose": pose, "has_pose": np.abs(k[:, 11:13, :2]).sum((1, 2)) > 0}
    for side, start in (("L", 33), ("R", 54)):
        h = k[:, start:start + 21].copy()
        out[f"has_{side}"] = np.abs(h[..., :2]).sum((1, 2)) > 0
        h[..., :2] *= ASPECT_FIX
        h[..., 2] *= Z_SCALE
        out[side] = h
    return out



def _angle(u: np.ndarray, v: np.ndarray) -> float:
    c = np.dot(u, v) / (np.linalg.norm(u) * np.linalg.norm(v) + 1e-9)
    return math.degrees(math.acos(max(-1.0, min(1.0, c))))


FINGERS = {"index": (5, 6, 7, 8), "middle": (9, 10, 11, 12), "ring": (13, 14, 15, 16), "pinky": (17, 18, 19, 20)}


def finger_curl(h: np.ndarray, finger: str) -> float:
    """0 straight to 1 fully bent: the finger's total flexion from the palm, about 240 degrees in a fist."""
    m, p, d, t = (h[i] for i in FINGERS[finger])
    total = _angle(m - h[0], p - m) + _angle(p - m, d - p) + _angle(d - p, t - d)
    return min(1.0, total / 240.0)


def thumb(h: np.ndarray) -> tuple[float, float]:
    """Thumb curl (0 to about 0.6 in a fist) and how far it lies across the palm towards the little finger."""
    bend = _angle(h[2] - h[1], h[3] - h[2]) + _angle(h[3] - h[2], h[4] - h[3])
    width = np.linalg.norm(h[5] - h[17]) + 1e-9
    across = (1.6 - np.linalg.norm(h[4] - h[17]) / width) / 1.1
    return min(0.6, bend / 150.0 * 0.6), float(np.clip(across, 0.0, 1.0))


def spread(h: np.ndarray) -> float:
    a = _angle(h[8] - h[5], h[20] - h[17])
    return float(np.clip((a - 20.0) / 35.0, -0.3, 1.0))


def roll(h: np.ndarray, side: str) -> float:
    """1 palm to the viewer, 0 thumb edge, -1 back of the hand: the palm normal's depth component."""
    n = np.cross(h[5] - h[0], h[17] - h[0])
    facing = -n[2] / (np.linalg.norm(n) + 1e-9)
    return float(np.clip(facing * (1.0 if side == "R" else -1.0) * 1.4, -1.0, 1.0))


def angle(h: np.ndarray, side: str) -> float:
    """Rig key angle: 0 fingers up, positive tips the fingers outward (towards the hand's own side)."""
    v = h[9, :2] - h[0, :2]
    screen = math.degrees(math.atan2(v[0], -v[1]))
    return screen * SIGN[side]


def shape_code(h: np.ndarray) -> str:
    """The rig's continuous hand shape: "c:" thumb, index, middle, ring, pinky, thumbAcross, spread in percent."""
    t, across = thumb(h)
    vals = [t] + [finger_curl(h, f) for f in FINGERS] + [across, spread(h)]
    return "c:" + ",".join(str(int(round(v * 20)) * 5) for v in vals)



def body_frame(pose: np.ndarray) -> tuple[np.ndarray, float]:
    """Shoulder midpoint and width, medians over the recording so one bad frame does not move the body."""
    mid = np.median((pose[:, 11] + pose[:, 12]) / 2, axis=0)
    width = float(np.median(np.linalg.norm(pose[:, 11] - pose[:, 12], axis=1)))
    return mid, width


def face_frame(pose: np.ndarray) -> tuple[np.ndarray, float]:
    eyes = np.median((pose[:, 2] + pose[:, 5]) / 2, axis=0)
    mouth = np.median((pose[:, 9] + pose[:, 10]) / 2, axis=0)
    return eyes, float(np.linalg.norm(mouth - eyes))


def to_rig(p: np.ndarray, body: tuple, face: tuple) -> np.ndarray:
    """A source pixel point in adult rig units: shoulder-scaled on the body, face-scaled near the face, blended."""
    (mid, width), (eyes, eye_mouth) = body, face
    on_body = np.array([CENTER, SHOULDER_Y]) + (p - mid) * (2 * SHOULDER_HALF / width)
    on_face = RIG_EYES_MID + (p - eyes) * (np.linalg.norm(RIG_MOUTH - RIG_EYES_MID) / max(eye_mouth, 1e-6))
    near = np.linalg.norm(p - (eyes + np.array([0.0, eye_mouth]))) / width
    w = float(np.clip((0.8 - near) / 0.35, 0.0, 1.0))
    return w * on_face + (1 - w) * on_body


def anchor_xy(name: str, side: str) -> tuple[float, float]:
    base = name.removesuffix("_opp")
    x, y = ANCHORS[base]
    s = SIGN[side] if not name.endswith("_opp") else -SIGN[side]
    return (CENTER + (x - CENTER) * (s / SIGN["R"]), y) if base in SIDED else (x, y)


def clamp_reach(p: np.ndarray, side: str) -> np.ndarray:
    """Keeps the wrist within the arm's reach and the whole hand inside the body view."""
    s = np.array([CENTER + SIGN[side] * SHOULDER_HALF, SHOULDER_Y])
    d = p - s
    n = np.linalg.norm(d)
    if n > REACH:
        p = s + d * (REACH / n)
    # Margins leave room for the child's anchors, up to 10 units further out, and an outward-pointing hand's length.
    return np.clip(p, [36.0, 20.0], [164.0, 186.0])


def place(p: np.ndarray, side: str) -> dict:
    """Nearest anchor and the outward/downward offset from it, in whole units."""
    names = list(ANCHORS) + [f"{n}_opp" for n in SIDED]
    best = min(names, key=lambda n: np.linalg.norm(p - np.array(anchor_xy(n, side))))
    ax, ay = anchor_xy(best, side)
    return {"at": best, "dx": int(round((p[0] - ax) * SIGN[side])), "dy": int(round(p[1] - ay))}


def elbow_offsets(pose: np.ndarray, side: str) -> np.ndarray:
    """Per frame elbow minus shoulder in rig units, +x outward. The signer's arms are about twice the rig's in
    shoulder widths, so the longest projected upper arm (95th percentile) becomes the rig's and shorter ones foreshorten."""
    sh, el = ARM[side]
    seen = (np.abs(pose[:, sh]).sum(1) > 0) & (np.abs(pose[:, el]).sum(1) > 0)
    arm = smooth(fill_gaps(pose[:, el] - pose[:, sh], seen))
    n = np.linalg.norm(arm, axis=1, keepdims=True)
    full = max(float(np.percentile(n, 95)), 1e-6)
    return arm * (UPPER_ARM / full) / np.maximum(n / full, 1.0) * [SIGN[side], 1.0]



def fill_gaps(x: np.ndarray, present: np.ndarray) -> np.ndarray:
    """Linear interpolation over frames where the hand was not detected; ends hold the nearest detection."""
    idx = np.flatnonzero(present)
    if len(idx) == 0:
        return x
    t = np.arange(len(x))
    flat = x.reshape(len(x), -1)
    out = np.stack([np.interp(t, idx, flat[idx, j]) for j in range(flat.shape[1])], axis=1)
    return out.reshape(x.shape)


def smooth(x: np.ndarray, sigma: float = 1.2) -> np.ndarray:
    r = int(math.ceil(3 * sigma))
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2)
    k /= k.sum()
    pad = np.concatenate([np.repeat(x[:1], r, 0), x, np.repeat(x[-1:], r, 0)])
    flat = pad.reshape(len(pad), -1)
    out = np.stack([np.convolve(flat[:, j], k, mode="valid") for j in range(flat.shape[1])], axis=1)
    return out.reshape(x.shape)


def key_frames(track: np.ndarray, fps: float = FPS, gap: float = 0.5, min_gap: float = 0.2, turn: float = 60.0) -> list[int]:
    """Frames to keep: both ends, pauses (speed minima) and turns of the path, with a key at least every [gap] s."""
    n = len(track)
    if n < 3:
        return list(range(n))
    v = np.diff(track, axis=0)
    speed = np.linalg.norm(v, axis=1)
    picks = {0, n - 1}
    for i in range(1, n - 1):
        a, b = v[i - 1], v[i]
        slow = speed[i - 1] <= speed[i] and (i < 2 or speed[i - 1] <= speed[i - 2]) and speed[i - 1] < 0.5 * np.median(speed)
        bent = min(np.linalg.norm(a), np.linalg.norm(b)) > 0.3 and _angle(a, b) > turn
        if slow or bent:
            picks.add(i)
    keep = []
    for i in sorted(picks):
        if not keep or (i - keep[-1]) / fps >= min_gap or i == n - 1:
            if keep and i == n - 1 and (i - keep[-1]) / fps < min_gap:
                keep[-1] = i
            else:
                keep.append(i)
    filled = [keep[0]]
    for i in keep[1:]:
        steps = int(math.ceil((i - filled[-1]) / fps / gap))
        filled += [round(filled[-1] + (i - filled[-1]) * s / steps) for s in range(1, steps)] + [i]
    return sorted(set(filled))



def used_hands(rec: dict) -> list[str]:
    """Hands that sign: detected in most frames and raised above the lower chest at some point."""
    mid, width = body_frame(rec["pose"])
    out = []
    for side in ("R", "L"):
        present = rec[f"has_{side}"]
        if present.mean() < 0.4:
            continue
        wrist_y = rec[side][present, 0, 1]
        if (wrist_y < mid[1] + RAISED * width).mean() > 0.2:
            out.append(side)
    return out


def score(rec: dict, hands: list[str], typical: float) -> float:
    if not hands:
        return -1.0
    both = np.logical_and.reduce([rec[f"has_{h}"] for h in hands]) & rec["has_pose"]
    return float(both.mean()) - 0.5 * abs(len(both) - typical) / typical


def pick(recs: list[tuple[str, dict]]) -> tuple[str, dict, list[str]]:
    """The recording with the most frames where every needed hand is seen, at a typical length."""
    votes = [h for _, r in recs for h in used_hands(r)]
    hands = [h for h in ("R", "L") if votes.count(h) >= len(recs) / 2] or ["R"]
    typical = float(np.median([len(r["pose"]) for _, r in recs]))
    rid, rec = max(recs, key=lambda x: score(x[1], hands, typical))
    return rid, rec, hands



def prepare(rec: dict, hands: list[str], fps: float = FPS, first: bool = False, visible: bool = False) -> dict:
    """Gap-filled, smoothed hands in source pixels and wrists in rig units, cut to the signing, with key frames."""
    present = np.logical_or.reduce([rec[f"has_{h}"] for h in hands])
    idx = np.flatnonzero(present)
    lo, hi = (idx[0], idx[-1] + 1) if len(idx) else (0, len(present))
    pose = rec["pose"][lo:hi]
    body, face = body_frame(pose), face_frame(pose)
    tracks, rig, elbows = {}, {}, {}
    for h in hands:
        tracks[h] = smooth(fill_gaps(rec[h][lo:hi], rec[f"has_{h}"][lo:hi]))
        elbows[h] = elbow_offsets(pose, h)
        rig[h] = np.array([clamp_reach(to_rig(p[0, :2], body, face), h) for p in tracks[h]])
    # The rig eases in from its own rest, so the signer's raising and lowering of the hands is cut. Dictionary signers
    # rest out of frame and sign low, so for them a visible hand marks the signing.
    up = (np.logical_or.reduce([rec[f"has_{h}"][lo:hi] for h in hands]) if visible
          else np.logical_or.reduce([rig[h][:, 1] < SHOULDER_Y + RAISED * 2 * SHOULDER_HALF for h in hands]))
    raised = np.flatnonzero(up)
    a, b = (raised[0], raised[-1] + 1) if len(raised) else (0, len(up))
    if first and len(raised):
        # Dictionary clips may go on to an example or a repeat: keep the first raise, up to a drop of over half a second.
        gaps = np.flatnonzero(np.diff(raised) > fps / 2)
        if len(gaps):
            b = raised[gaps[0]] + 1
    path = np.concatenate([rig[h][a:b] for h in hands], axis=1)
    frames = [a + i for i in key_frames(path, fps)]
    return {"tracks": tracks, "rig": rig, "elbows": elbows, "frames": frames, "start": a, "pose": pose, "body": body, "face": face}


def retarget(rec: dict, hands: list[str], fps: float = FPS, first: bool = False, visible: bool = False) -> list[dict]:
    p = prepare(rec, hands, fps, first, visible)
    tracks, rig = p["tracks"], p["rig"]
    keys, prev_angle = [], {}
    for i in p["frames"]:
        key = {"t": round((i - p["start"]) / fps, 2)}
        for h in hands:
            hand = tracks[h][i]
            ang = angle(hand, h)
            if h in prev_angle:  # unwrap so the rig never spins the long way round
                ang = prev_angle[h] + ((ang - prev_angle[h] + 180) % 360 - 180)
            prev_angle[h] = ang
            ex, ey = (int(round(v)) for v in p["elbows"][h][i])
            # Signers keep their elbows low; the rig's default "out" elbow lifts the arm above the shoulder.
            key[h] = {**place(rig[h][i], h), "shape": shape_code(hand), "roll": round(roll(hand, h), 1), "angle": int(round(ang)),
                      "elbow": "down", "ex": ex, "ey": ey}
        keys.append(key)
    return keys


def main(data: pathlib.Path) -> None:
    root = data / "raw" / "INCLUDE" / "Pose_Signs"
    glosses = json.loads(GLOSSES.read_text(encoding="utf-8"))
    by_word: dict[str, list] = {}
    category = {}
    for path in sorted(root.glob("*/*/*.pkl")):
        word = label(path.parent.name)
        category.setdefault(word, path.parent.parent.name)
        by_word.setdefault(word, []).append((f"{path.parent.parent.name}/{path.parent.name}/{path.stem}", path))
    made = []
    for word, items in sorted(by_word.items()):
        recs = [(rid, load(p)) for rid, p in items]
        recs = [(rid, r) for rid, r in recs if r["has_pose"].mean() > 0.8]
        if not recs:
            continue
        rid, rec, hands = pick(recs)
        made.append({
            "id": f"isl.{word}", "word": word, "hi": glosses["words"].get(word, ""), "category": category[word],
            "rec": rid, **({"homonym": True} if word in HOMONYMS else {}), "keys": retarget(rec, hands),
        })
    cats = {k: {"en": v[0], "hi": v[1]} for k, v in glosses["categories"].items()}
    note = ("Retargeted by tools/isl/retarget.py from INCLUDE signers' landmarks; edit the script, not this file. "
            "Draft: every sign needs Deaf ISL review on both characters.")
    source = "INCLUDE (AI4Bharat, Sridhar et al. 2020), OpenHands pose release, CC BY 4.0"
    # Words added by retarget_islrtc.py survive a rebuild unless INCLUDE now has them.
    old = json.loads(OUT.read_text(encoding="utf-8")) if OUT.exists() else {}
    words = {g["word"] for g in made}
    kept = [g for g in old.get("gestures", []) if g.get("rec", "").startswith("ISLRTC/") and g["word"] not in words]
    if kept:
        source = old["source"]
        cats.update({k: v for k, v in old["categories"].items() if k not in cats})
    write({"note": note, "source": source, "categories": cats, "gestures": made + kept})
    print(f"isl signs: {len(made)} words, {OUT.stat().st_size // 1024} KB")


def write(signs: dict) -> None:
    lines = ",\n".join("  " + json.dumps(g, ensure_ascii=False, separators=(",", ":")) for g in signs["gestures"])
    OUT.write_text(
        f'{{"note": {json.dumps(signs["note"])},\n "source": {json.dumps(signs["source"])},\n "review": "draft",\n'
        f' "categories": {json.dumps(signs["categories"], ensure_ascii=False)},\n "gestures": [\n{lines}\n]}}\n',
        encoding="utf-8", newline="\n",
    )


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else str(DATA_ROOT / "include_pose")))
