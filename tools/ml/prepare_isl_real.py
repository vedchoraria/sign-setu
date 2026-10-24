"""Real-signer ISL letters and digits from photographs, through MediaPipe Hand Landmarker (never shipped).
Sources and licences are in tools/README.md."""
import pathlib
import pickle
import sys

import numpy as np

STILL = 12


def upright(path: pathlib.Path):
    from PIL import Image
    im = Image.open(path).convert("RGB")
    # ISLAN photographs are stored a quarter turn clockwise with no orientation tag.
    return im.rotate(90, expand=True) if "islan" in path.parts else im


def hands(detector, im):
    import mediapipe as mp
    r = detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.asarray(im)))
    pts = [np.array([[p.x, p.y] for p in h], np.float32) for h in r.hand_landmarks]
    if not pts:
        return None
    pts.sort(key=lambda h: h[0, 0])
    right = pts[0] if len(pts) == 2 or pts[0][0, 0] < 0.5 else None
    left = pts[-1] if len(pts) == 2 or right is None else None
    return left, right


def still(label: str, split: str, lr, aspect: float) -> dict:
    left, right = lr
    z = np.zeros((STILL, 21, 2), np.float32)
    return {
        "split": split, "label": label, "aspect": aspect,
        "left": np.repeat(left[None], STILL, 0) if left is not None else z,
        "right": np.repeat(right[None], STILL, 0) if right is not None else z,
        "pose": np.zeros((STILL, 33, 2), np.float32),
        "has_left": np.full(STILL, left is not None), "has_right": np.full(STILL, right is not None),
        "has_pose": np.zeros(STILL, bool),
    }


def main(root: pathlib.Path, model: str, out: pathlib.Path) -> None:
    from mediapipe.tasks.python import BaseOptions, vision
    detector = vision.HandLandmarker.create_from_options(
        vision.HandLandmarkerOptions(base_options=BaseOptions(model_asset_path=model), num_hands=2,
                                     min_hand_detection_confidence=0.3))
    jobs = []
    mm = root / "mm" / "x" / "Dataset"
    for group in ("English", "Numbers"):
        for d in sorted((mm / group / "RGB").iterdir()):
            jobs += [(d.name.lower(), "train", f) for f in sorted(d.glob("*.jpg"))]
    islan = root / "islan" / "x"
    for f in sorted(islan.rglob("*.jpg")):
        label = f.parent.name if f.parent.parent.name in ("Double Handed", "Numbers") else f.stem.rstrip("0123456789")
        if f.parent.name == "Single_Handed_Alphabets":  # one-handed variants, not the two-handed ISL alphabet
            continue
        if f.parent.name in ("Number", "Double_Handed_Alphabets"):
            label = f.stem.rstrip("0123456789") or f.stem[:-1]
        label = label.lower()
        if len(label) == 1 and label.isalnum():
            jobs.append((label, "test", f))
    samples, seen = [], set()
    for label, split, f in jobs:
        try:
            im = upright(f)
        except OSError:  # a few ISLAN files are cut short in the published archive
            continue
        lr = hands(detector, im)
        if lr is None:
            continue
        key = (label, split, f.read_bytes()[:4096])
        if key in seen:  # the multimodal set pads small classes with copies
            continue
        seen.add(key)
        w, h = im.size
        samples.append(still(label, split, lr, w / h))
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "wb") as fh:
        pickle.dump({"aspect": None, "norm": "hands", "samples": samples}, fh)
    for split in ("train", "test"):
        s = [x for x in samples if x["split"] == split]
        print(f"{split}: {len(s)} samples, {len({x['label'] for x in s})} labels")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]), sys.argv[2], pathlib.Path(sys.argv[3]))
