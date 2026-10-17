"""Synthesises Holistic landmarks for the ISL two-handed alphabet and digits from the app's own keyframes (signs.json).

No redistributable ISL fingerspelling landmark set was found (the public ones are CC BY-NC), so the letters and digits
the character signs are posed on a Python copy of the rig (Rig.kt), turned into 21-point hands and the arm points of the
pose, and jittered. The model trained on it is labelled synthetic.
    python tools/ml/synth_isl.py [out.pkl] [per_class]   # default C:/Users/Yash/dev/ml-data/isl_spell/isl_spell.pkl
"""
import json
import math
import pathlib
import pickle
import sys

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
SIGNS = ROOT / "app" / "src" / "main" / "assets" / "content" / "signs.json"

# Rig.kt HandShape.ALL: thumb, index, middle, ring, pinky, thumbAcross, spread.
SHAPES = {
    "RELAX": (0.25, 0.2, 0.28, 0.34, 0.4, 0.2, 0), "FLAT": (0, 0, 0, 0, 0, 0.15, 0), "OPEN": (0, 0, 0, 0, 0, 0, 1),
    "FIST": (0.55, 1, 1, 1, 1, 1, 0), "POINT": (0.55, 0, 1, 1, 1, 1, 0), "TWO": (0.55, 0, 0, 1, 1, 1, 0.6),
    "THREE": (0.55, 0, 0, 0, 1, 1, 0.4), "FOUR": (0.9, 0, 0, 0, 0, 1, 0.35), "PINKY": (0.55, 1, 1, 1, 0, 1, 0),
    "THUMB": (0, 1, 1, 1, 1, 0, 0), "Y": (0, 1, 1, 1, 0, 0, 0.4), "BUNCH": (0.45, 0.5, 0.5, 0.5, 0.52, 0.7, -1),
    "CUP": (0.3, 0.35, 0.35, 0.38, 0.4, 0.35, -0.3), "PINCH": (0.45, 0.55, 1, 1, 1, 0.6, 0),
    "CLAW": (0.35, 0.6, 0.6, 0.62, 0.65, 0.3, 0.3), "SALUTE": (0.1, 0, 0, 0, 0, 0.6, -0.2),
    "WALK_A": (0.55, 0, 0.45, 1, 1, 1, 0.4), "WALK_B": (0.55, 0.45, 0, 1, 1, 1, 0.4),
    "RUB": (0.7, 0.55, 0.55, 0.6, 0.65, 0.9, -0.6), "HOOK": (0.55, 0.5, 1, 1, 1, 1, 0),
}
SIGN = {"R": -1.0, "L": 1.0}
CENTER = 100.0
SIDED = {"cheek", "ear", "shoulder", "temple", "side", "eye", "hip", "arm", "nose_side", "hair_side"}
BUILDS = {  # Rig.kt AdultBuild and ChildBuild
    "adult": dict(shoulder_y=136, half=44, upper=41, fore=38, hand=2.0, anchors={
        "hairline": (100, 38), "forehead": (100, 52), "eye": (88, 67), "nose": (100, 78), "nose_side": (96, 80),
        "mouth": (100, 91), "chin": (100, 104), "cheek": (84, 84), "ear": (68, 72), "temple": (73, 58),
        "hair_side": (72, 46), "head_top": (100, 33), "chest": (100, 154), "belly": (100, 188), "shoulder": (56, 136),
        "side": (52, 178), "front": (100, 166), "arm": (53, 168), "hip": (66, 196), "low": (100, 196)}),
    "child": dict(shoulder_y=140, half=39, upper=38, fore=35, hand=1.85, anchors={
        "hairline": (100, 40), "forehead": (100, 56), "eye": (84, 74), "nose": (100, 85), "nose_side": (95, 86),
        "mouth": (100, 99), "chin": (100, 114), "cheek": (78, 92), "ear": (58, 80), "temple": (66, 62),
        "hair_side": (64, 48), "head_top": (100, 30), "chest": (100, 156), "belly": (100, 188), "shoulder": (61, 140),
        "side": (56, 176), "front": (100, 168), "arm": (58, 166), "hip": (70, 196), "low": (100, 196)}),
}
FINGER_LEN = (9.2, 10.2, 9.6, 7.7)
PALM_W = 13.0


def shape(name: str) -> np.ndarray:
    if name.startswith("c:"):
        v = [int(x) / 100 for x in name[2:].split(",")]
        return np.array(v, float)
    return np.array(SHAPES.get(name, SHAPES["RELAX"]), float)


def hand21(wrist, sh, roll, angle_deg, side, scale):
    """MediaPipe's 21 hand points on screen for a rig hand; the rig draws tips only, so joints follow the curls."""
    # Built palm to the viewer, then turned about the long axis by the roll: the thumb lands where Rig.kt point() puts it.
    c0 = -SIGN[side]
    pts = np.zeros((21, 3))
    for k in range(4):
        u = PALM_W / 2 - 1.8 - k * (PALM_W - 3.6) / 3  # towards the thumb
        base = np.array([u, -12.0, 0.0])
        fan = math.radians((1.5 - k) * 7 * sh[6])
        d = np.array([math.sin(fan), -math.cos(fan), 0.0])
        seg = np.array([0.45, 0.30, 0.25]) * FINGER_LEN[k]
        pts[5 + 4 * k] = p = base
        bend = 0.0
        for j in range(3):
            bend += math.radians(80 * sh[k + 1])
            step = d * math.cos(bend) + np.array([0, 0, 1.0]) * math.sin(bend)
            p = p + step * seg[j]
            pts[6 + 4 * k + j] = p
    a = math.radians(52 - sh[5] * 112)
    d = np.array([math.sin(a), -math.cos(a), 0.0])
    pts[1] = [2.5, -2.0, 0.0]
    pts[2] = [PALM_W / 2 - 0.5, -4.0, sh[5] * 1.5]
    length = 8.8 * (1 - 0.4 * sh[0])
    p, bend = pts[2], 0.0
    for j in range(2):
        bend += math.radians(45 * sh[0])
        p = p + (d * math.cos(bend) + np.array([0, 0, 1.0]) * math.sin(bend)) * length / 2
        pts[3 + j] = p
    phi = (1 - roll) * math.pi / 2
    u, z = pts[:, 0].copy(), pts[:, 2].copy()
    pts[:, 0] = (u * math.cos(phi) - z * math.sin(phi)) * c0
    pts[:, 2] = u * math.sin(phi) + z * math.cos(phi)
    t = math.radians(angle_deg)
    x = (pts[:, 0] * math.cos(t) - pts[:, 1] * math.sin(t)) * scale
    y = (pts[:, 0] * math.sin(t) + pts[:, 1] * math.cos(t)) * scale
    return np.stack([x + wrist[0], y + wrist[1]], 1)


class Rig:
    def __init__(self, build: dict, rng: np.random.Generator):
        self.b = build
        self.half = build["half"] * rng.uniform(0.92, 1.08)
        self.hand = build["hand"] * rng.uniform(0.85, 1.15)
        self.jit = {a: rng.normal(0, 3, 2) for a in build["anchors"]}

    def shoulder(self, side):
        return np.array([CENTER + SIGN[side] * self.half, self.b["shoulder_y"]])

    def anchor(self, name, side):
        if name == "rest":
            return np.array([CENTER + SIGN[side] * (self.half - 6), 206.0])
        opp = name.endswith("_opp")
        base = name.removesuffix("_opp")
        x, y = self.b["anchors"].get(base, (CENTER, 206))
        s = (-SIGN[side] if opp else SIGN[side])
        x = CENTER + (x - CENTER) * (s / SIGN["R"]) if base in SIDED else x
        return np.array([x, y]) + self.jit.get(base, 0)

    def pose_hand(self, k: dict, side: str, other=None):
        """Wrist, shape, roll, screen angle and 21 points, as Timeline.resolve and reachBack place them."""
        at = k.get("at", "rest")
        sh = shape(k.get("shape", "RELAX"))
        roll = float(k.get("roll", -1))
        ang = k.get("angle")
        if at.endswith("_opp") and at != "rest" and other is not None:
            part = at.removesuffix("_opp")
            o = other["points"]
            base = {"wrist": o[0], "palm": (o[0] + o[9]) / 2, "thumb": o[4], "index": o[8], "middle": o[12],
                    "ring": o[16], "pinky": o[20]}[part]
        else:
            base = self.anchor(at, side)
        p = base + np.array([k.get("dx", 0) * SIGN[side], k.get("dy", 0)])
        reach = {"palm": 6.5, "thumb": 9.0, "tip": 12 + 9.5 * (1 - 0.75 * sh[1])}.get(k.get("touch", "wrist"))
        a = math.radians((ang or 0) * SIGN[side])
        if reach:
            p = p - np.array([math.sin(a), -math.cos(a)]) * reach * self.hand
        elbow = self.elbow(self.shoulder(side), p, side, k.get("elbow", "out"))
        if ang is None:
            v = p - elbow
            screen = math.degrees(math.atan2(v[0], -v[1]))
        else:
            screen = ang * SIGN[side]
        return {"wrist": p, "elbow": elbow, "points": hand21(p, sh, roll, screen, side, self.hand)}

    def elbow(self, s, t, side, mode):
        """Rig.kt solveElbow."""
        upper, fore = self.b["upper"], self.b["fore"]
        d = t - s
        n = float(np.clip(np.hypot(*d), abs(upper - fore) + 0.01, upper + fore - 0.01))
        base = math.atan2(d[1], d[0])
        off = math.acos(float(np.clip((upper ** 2 + n ** 2 - fore ** 2) / (2 * upper * n), -1, 1)))
        e1 = s + upper * np.array([math.cos(base + off), math.sin(base + off)])
        e2 = s + upper * np.array([math.cos(base - off), math.sin(base - off)])
        out = {"in": -1.0, "down": 0.0}.get(mode, 1.0)

        def score(e):
            outward = (e[0] - CENTER) * SIGN[side]
            return outward * out + e[1] * (1 - out) if out >= 0 else -outward
        return e1 if score(e1) >= score(e2) - 1e-3 else e2


def jitter_key(k: dict | None, rng) -> dict:
    k = dict(k or {"at": "rest"})
    if k.get("at", "rest") == "rest":
        return k
    k["dx"] = k.get("dx", 0) + rng.normal(0, 2.5)
    k["dy"] = k.get("dy", 0) + rng.normal(0, 2.5)
    k["roll"] = float(np.clip(k.get("roll", -1) + rng.normal(0, 0.15), -1, 1))
    if k.get("angle") is not None:
        k["angle"] = k["angle"] + rng.normal(0, 8)
    sh = np.clip(shape(k.get("shape", "RELAX")) + rng.normal(0, 0.06, 7), [0] * 6 + [-1], 1)
    k["shape"] = "c:" + ",".join(str(int(round(v * 100))) for v in sh)
    return k


def frame(rig: Rig, r: dict, l: dict):
    """Pose landmarks (only 11..16 matter to the features) and both hands for one held key."""
    if r.get("at", "rest").endswith("_opp"):
        L = rig.pose_hand(l, "L")
        R = rig.pose_hand(r, "R", L)
    else:
        R = rig.pose_hand(r, "R")
        L = rig.pose_hand(l, "L", R)
    pose = np.zeros((33, 2))
    pose[11], pose[12] = rig.shoulder("L"), rig.shoulder("R")
    pose[13], pose[14] = L["elbow"], R["elbow"]
    pose[15], pose[16] = L["wrist"], R["wrist"]
    return pose, L["points"], R["points"]


def sample(gesture: dict, build: str, rng: np.random.Generator) -> dict:
    """One clip: lead in from rest, through the keys, a hold, part of the way back; 10 to 30 frames a second."""
    rig = Rig(BUILDS[build], rng)
    keys = sorted(gesture["keys"], key=lambda k: k["t"])
    ks = [(k["t"], jitter_key(k.get("R"), rng), jitter_key(k.get("L"), rng)) for k in keys]
    rest = {"at": "rest"}
    lead = rng.uniform(0.1, 0.35)
    ks = [(-lead, rest, rest)] + ks + [(keys[-1]["t"] + rng.uniform(0.2, 0.6), ks[-1][1], ks[-1][2])]
    poses = [frame(rig, r, l) for _, r, l in ks]
    fps = rng.uniform(10, 30)
    times = np.arange(ks[0][0], ks[-1][0], 1 / fps)
    tk = np.array([t for t, _, _ in ks])
    out = {k: [] for k in ("pose", "left", "right")}
    for t in times:
        i = int(np.clip(np.searchsorted(tk, t, side="right") - 1, 0, len(tk) - 2))
        f = float(np.clip((t - tk[i]) / (tk[i + 1] - tk[i]), 0, 1))
        f = f * f * (3 - 2 * f)
        for j, name in enumerate(("pose", "left", "right")):
            out[name].append(poses[i][j] * (1 - f) + poses[i + 1][j] * f)
    pose, left, right = (np.array(out[n]) for n in ("pose", "left", "right"))
    # A wobbling camera and MediaPipe's own jitter.
    left = left + rng.normal(0, 0.6, left.shape)
    right = right + rng.normal(0, 0.6, right.shape)
    pose = pose + rng.normal(0, 0.8, pose.shape)
    n = len(pose)
    # A hand below the frame (at rest) is not detected; a few frames drop out anyway.
    has_left = (left[:, 0, 1] < 196) & (rng.random(n) > 0.05)
    has_right = (right[:, 0, 1] < 196) & (rng.random(n) > 0.05)
    s = 1 / 200.0
    return {"left": (left * s).astype(np.float32), "right": (right * s).astype(np.float32),
            "pose": (pose * s).astype(np.float32), "has_left": has_left, "has_right": has_right,
            "has_pose": np.ones(n, bool)}


def labels_and_gestures():
    signs = json.loads(SIGNS.read_text(encoding="utf-8"))["gestures"]
    out = {}
    for g in signs:
        kind, _, ch = g["id"].partition(".")
        if kind in ("letter", "digit"):
            out[ch] = g
    return dict(sorted(out.items()))


def main(path: pathlib.Path, per_class: int = 400) -> None:
    gestures = labels_and_gestures()
    samples = []
    for split, n, seed in (("train", per_class, 1), ("test", max(2, per_class // 4), 2)):
        rng = np.random.default_rng(seed)
        for label, g in gestures.items():
            for i in range(n):
                s = sample(g, "adult" if i % 3 else "child", rng)
                samples.append({"split": split, "label": label, **s})
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as f:
        pickle.dump({"aspect": 1.0, "synthetic": True, "samples": samples}, f)
    print(f"{len(samples)} synthetic samples, {len(gestures)} labels -> {path}")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "C:/Users/Yash/dev/ml-data/isl_spell/isl_spell.pkl"),
         int(sys.argv[2]) if len(sys.argv) > 2 else 400)
