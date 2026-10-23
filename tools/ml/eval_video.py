"""Video files through the chain the app runs: MediaPipe Holistic, the app's sign segmentation, the shipped reader.

This is the measurement behind "sign extraction from video": decode each clip at its own frame rate, track it with the
same holistic model the app bundles, cut it into signs with the same pause and motion rules (`sign/SignCapture.kt`),
featurize each sign the way `LandmarkSignReader` does (`features.py`) and score it with the exported NumPy reader, so
the numbers are what ships rather than what training saw.

Runs in the mediapipe venv (opencv + mediapipe + numpy):

    signsetu-mp/python -I tools/ml/eval_video.py <clip.mp4> [<clip.mp4> ...]
    signsetu-mp/python -I tools/ml/eval_video.py --dir <folder>          # every .mp4 in it

A clip's expected label comes from a `labels.json` beside it ({"clip.mp4": "word"}), else from its file name
(`1_One.mp4` is digit 1, `ATM.mp4` is not scored as a word), else it is only transcribed and not scored.
Words are scored with `sign_reader`; digits and single letters with `isl_spell`, as `SignReaders.kt` decides.
"""
import json
import os
import pathlib
import sys

import numpy as np

from features import FEATURES, FRAMES, resample_index
from runtime import reader

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODELS = ROOT / "app" / "src" / "main" / "assets" / "content" / "models"
CONTENT = ROOT / "app" / "src" / "main" / "assets" / "content"
HOLISTIC = MODELS / "holistic_landmarker.task"

# Mirrors SignWindow.ANSWER and SignCapture's rules; keep in step with sign/SignCapture.kt.
PAUSE_MS = 300
MIN_MS = 180
MIN_ACTIVE_FRAMES = 3
STILL_SPEED = 0.3
LIMIT_MS = 20_000
RETRACT_MAX_MS = 500
RETRACT_DROP = 0.6
RETRACT_LOW = 1.2
# Mirrors SignReaders.kt's sign-or-spell gate: a letter or digit wins only at twice the word reader's top score.
SPELL_ODDS = 2.0


def landmarks(path: pathlib.Path) -> dict:
    """Every frame of the clip, as MediaPipe normalised points, at the clip's own frame rate."""
    import cv2
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions, vision

    options = vision.HolisticLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(HOLISTIC)), running_mode=vision.RunningMode.VIDEO
    )
    cap = cv2.VideoCapture(str(path))
    fps = cap.get(cv2.CAP_PROP_FPS) or 25.0
    width, height = cap.get(cv2.CAP_PROP_FRAME_WIDTH), cap.get(cv2.CAP_PROP_FRAME_HEIGHT)
    pose, left, right, has_pose, has_left, has_right, times = [], [], [], [], [], [], []
    with vision.HolisticLandmarker.create_from_options(options) as model:
        i = 0
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            ms = int(i * 1000 / fps)
            r = model.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)), ms)
            pts = lambda ps, n: np.array([(p.x, p.y) for p in ps], np.float32) if ps else np.zeros((n, 2), np.float32)
            pose.append(pts(r.pose_landmarks, 33)), has_pose.append(bool(r.pose_landmarks))
            left.append(pts(r.left_hand_landmarks, 21)), has_left.append(bool(r.left_hand_landmarks))
            right.append(pts(r.right_hand_landmarks, 21)), has_right.append(bool(r.right_hand_landmarks))
            times.append(ms)
            i += 1
    cap.release()
    return {
        "fps": fps, "aspect": width / height, "pose": pose, "left": left, "right": right,
        "has_pose": has_pose, "has_left": has_left, "has_right": has_right, "times": times,
    }


def moved(prev, cur) -> bool:
    """The app's `moving()`: a hand faster than STILL_SPEED frame-widths a second, or one that just appeared."""
    seconds = 1 / 30 if prev is None or cur["t"] <= prev["t"] else (cur["t"] - prev["t"]) / 1000
    for key in ("left", "right"):
        b, a = cur[key], None if prev is None else prev[key]
        if b is None:
            continue
        if a is None:
            return True
        if float(np.linalg.norm(np.asarray(b) - np.asarray(a), axis=1).mean()) / seconds > STILL_SPEED:
            return True
    return False


def dropping_to_rest(clip: dict, a: int, b: int) -> bool:
    """The app's `droppingToRest()`: short, every hand down half a shoulder width or more, ending below the shoulders."""
    if clip["times"][b] - clip["times"][a] > RETRACT_MAX_MS:
        return False
    if not (clip["has_pose"][a] and clip["has_pose"][b]):
        return False
    end = clip["pose"][b]
    aspect = clip["aspect"] or 1.0
    width = float(np.linalg.norm([end[11][0] - end[12][0], (end[11][1] - end[12][1]) / aspect]))
    if width < 1e-3:
        return False
    shoulder_y = (end[11][1] + end[12][1]) / 2
    seen = 0
    for key, has in (("left", "has_left"), ("right", "has_right")):
        if not (clip[has][a] and clip[has][b]):
            continue
        seen += 1
        down = (clip[key][b][0][1] - clip[key][a][0][1]) / aspect
        below = (clip[key][b][0][1] - shoulder_y) / aspect
        if down < RETRACT_DROP * width or below < RETRACT_LOW * width:
            return False
    return seen > 0


def segment(clip: dict) -> list[tuple[int, int]]:
    """The app's SignCapture over a finished clip: boundaries are (first, last) frame indices of each sign."""
    frames = [
        {"t": clip["times"][i],
         "left": clip["left"][i] if clip["has_left"][i] else None,
         "right": clip["right"][i] if clip["has_right"][i] else None}
        for i in range(len(clip["times"]))
    ]
    out, start, last_active, active = [], -1, -1, 0

    def close() -> None:
        nonlocal start, active
        if frames[last_active]["t"] - frames[start]["t"] >= MIN_MS and active >= MIN_ACTIVE_FRAMES \
                and not dropping_to_rest(clip, start, last_active):
            out.append((start, last_active))
        start, active = -1, 0

    for i, f in enumerate(frames):
        if start >= 0 and f["t"] - frames[last_active]["t"] >= PAUSE_MS:
            close()
        if moved(frames[i - 1] if i else None, f):
            if start < 0:
                start = max(0, i - 1)
            last_active, active = i, active + 1
        if f["t"] - frames[0]["t"] >= LIMIT_MS and start >= 0:
            close()
    if start >= 0:
        close()
    return out


def featurize(clip: dict, a: int, b: int, norm: str) -> np.ndarray | None:
    """One sign as the reader's input: `features.py`, as LandmarkSignReader.kt does it."""
    aspect = clip["aspect"]
    k = np.array([1.0, 1.0 / aspect], np.float32)
    left, right, pose = clip["left"][a:b + 1] * k, clip["right"][a:b + 1] * k, clip["pose"][a:b + 1] * k
    hl, hr, hp = clip["has_left"][a:b + 1], clip["has_right"][a:b + 1], clip["has_pose"][a:b + 1]
    if norm == "hands":
        present = [h[m] for h, m in ((left, np.array(hl)), (right, np.array(hr))) if m.any()]
        if not present:
            return None
        pts = np.concatenate(present)
        center = pts.reshape(-1, 2).mean(0)
        scale = np.linalg.norm(pts[:, 0] - pts[:, 9], axis=1).mean()
        if scale < 1e-6:
            return None
    else:
        if not any(hp):
            return None
        p = pose[np.array(hp)]
        center = ((p[:, 11] + p[:, 12]) / 2).mean(0)
        scale = np.linalg.norm(p[:, 11] - p[:, 12], axis=1).mean()
        if scale < 1e-6:
            return None
    idx = resample_index(len(pose))
    arms = [11, 12, 13, 14, 15, 16]
    out = np.zeros((FRAMES, FEATURES), np.float32)
    for t, j in enumerate(idx):
        if hl[j]:
            out[t, 0:42] = ((left[j] - center) / scale).reshape(-1)
            out[t, 96] = 1
        if hr[j]:
            out[t, 42:84] = ((right[j] - center) / scale).reshape(-1)
            out[t, 97] = 1
        if norm != "hands" and hp[j]:
            out[t, 84:96] = ((pose[j, arms] - center) / scale).reshape(-1)
    return out


def expected(path: pathlib.Path, labels: dict) -> str | None:
    """What the clip should read as, if it can be scored at all."""
    if path.name in labels:
        return labels[path.name]
    stem = path.stem.split("_")[-1]
    if stem.isdigit():
        return stem
    if len(stem) == 1 and stem.isalpha():
        return stem.lower()
    return None


def main(argv: list[str]) -> None:
    paths, i, whole = [], 1, "--whole" in argv
    if "--whole" in argv:
        argv = [a for a in argv if a != "--whole"]
    while i < len(argv):
        if argv[i] == "--dir":
            paths += sorted(pathlib.Path(argv[i + 1]).glob("*.mp4"))
            i += 2
        else:
            paths.append(pathlib.Path(argv[i]))
            i += 1
    # One labels.json per folder, keyed by file name; anything unlabelled is transcribed but not scored.
    labels = {}
    for d in {p.parent for p in paths}:
        f = d / "labels.json"
        if f.is_file():
            labels.update(json.loads(f.read_text(encoding="utf-8")))
    words = {w: e for e in json.loads((CONTENT / "words.json").read_text(encoding="utf-8"))["words"] for w in e["w"]}
    sign_words = json.loads((CONTENT / "sign_words.json").read_text(encoding="utf-8"))["words"]
    models = {name: reader(name) for name in ("sign_reader", "isl_spell")}

    scored = hits1 = hits3 = 0
    for path in paths:
        clip = landmarks(path)
        # --whole reads the clip as one sign, the way the exported models are scored: it separates a reader's
        # accuracy from the app's cutting, which is the difference between this tool and eval_isl.py.
        cuts = [(0, len(clip["times"]) - 1)] if whole else segment(clip)
        want = expected(path, labels)
        reads = []
        for a, b in cuts:
            best = {}
            for name, norm in (("sign_reader", "arm"), ("isl_spell", "hands")):
                spec, logits = models[name]
                x = featurize(clip, a, b, spec.get("norm", norm) if name == "isl_spell" else norm)
                if x is None:
                    continue
                p = logits(x[None].astype(np.float64))[0]
                order = np.argsort(-p)[:3]
                best[name] = [(spec["labels"][int(i)], float(p[int(i)])) for i in order]
            word = best.get("sign_reader", [])
            spell = best.get("isl_spell", [])
            # The app's gate: a letter or digit wins only at SPELL_ODDS the word reader's top score.
            take_spell = spell and spell[0][1] >= SPELL_ODDS * (word[0][1] if word else 0.0)
            top = (spell if take_spell else word) or [("?", 0.0)]
            read = top[0][0]
            if take_spell or not word:
                read = read.removeprefix("letter.").removeprefix("digit.")
            reads.append((read, top))
        names = [r[0] for r in reads]
        shown = ", ".join(names) if names else "no sign found"
        if want is None:
            print(f"{path.name}: {len(cuts)} sign(s) -> {shown}", flush=True)
            continue
        scored += 1
        top3 = [label for _, top in reads for label, _ in top]
        top3 += [sign_words.get(label, label) for label in top3]
        hit1 = len(names) == 1 and (names[0] == want or sign_words.get(names[0]) == want)
        hits1 += hit1
        hits3 += bool(top3) and want in top3
        print(f"{path.name}: want {want!r}, {len(cuts)} sign(s) -> {shown}{'' if hit1 else '   MISS'}"
              + (f"   top3 {top3[:3]}" if not hit1 or len(top3) > 1 else ""), flush=True)
    if scored:
        print(f"\nscored {scored} clip(s): top-1 {hits1 / scored:.3f}, top-3 {hits3 / scored:.3f}", flush=True)
        print("(shipped models, signs cut the way the app cuts them)", flush=True)


if __name__ == "__main__":
    main(sys.argv)
