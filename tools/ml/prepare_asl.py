"""Builds the ASL landmark cache from PopSign ASL v1.0 frames with the app's own Holistic model.

PopSign ASL v1.0 (Georgia Tech, NTID; 250 signs, 47 Deaf signers) is CC BY 4.0. Google's Kaggle "Isolated Sign
Language Recognition" set is the same corpus as landmarks, also CC BY 4.0, but needs Kaggle credentials; the
huggingface.co/datasets/sign/popsign-images release holds the clips as 256x256 frames at about 5 a second instead.
Its validation signers train and its test signers test (signer-independent). Each shard is deleted once read.
Runs in a venv with mediapipe, pyarrow and pillow (not the torch one):
    python -I tools/ml/prepare_asl.py [data_dir] [workers] [limit]   # default C:/Users/Yash/dev/ml-data/popsign
"""
import io
import pathlib
import pickle
import sys
import urllib.request
from concurrent.futures import ProcessPoolExecutor

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODEL = ROOT / "app" / "src" / "main" / "assets" / "content" / "models" / "holistic_landmarker.task"
BASE = "https://huggingface.co/datasets/sign/popsign-images/resolve/main/data/non-game/"
# ponytail: the 14.5 GB train split is over the download cap; add its 28 shards when disk allows.
SHARDS = [(f"validation-{i:05d}-of-00009.parquet", "train") for i in range(9)] + \
         [(f"test-{i:05d}-of-00004.parquet", "test") for i in range(4)]
FPS_IN, FPS_OUT = 5, 15


def fetch(name: str, out: pathlib.Path) -> pathlib.Path:
    path = out / name
    if not path.exists():
        req = urllib.request.Request(BASE + name)
        token = pathlib.Path.home() / ".cache" / "huggingface" / "token"
        if token.exists():
            req.add_header("Authorization", "Bearer " + token.read_text().strip())
        with urllib.request.urlopen(req) as r, open(path.with_suffix(".part"), "wb") as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
        path.with_suffix(".part").rename(path)
    return path


def upsample(x: np.ndarray, ok: np.ndarray, k: int):
    """k frames per source frame: linear between two detections, else the nearest source frame."""
    n = len(x)
    t = np.arange((n - 1) * k + 1) / k
    i = np.minimum(np.floor(t).astype(int), n - 1)
    j = np.minimum(i + 1, n - 1)
    f = (t - i).reshape(-1, *[1] * (x.ndim - 1))
    both = ok[i] & ok[j]
    near = np.where(t - i < 0.5, i, j)
    y = np.where(both.reshape(-1, *[1] * (x.ndim - 1)), x[i] * (1 - f) + x[j] * f, x[near])
    return y.astype(np.float32), np.where(both, True, ok[near])


def clip(lm, images) -> dict:
    import mediapipe as mp
    from PIL import Image
    n = len(images)
    pose, left, right = np.zeros((n, 33, 2)), np.zeros((n, 21, 3)), np.zeros((n, 21, 3))
    hp, hl, hr = np.zeros(n, bool), np.zeros(n, bool), np.zeros(n, bool)
    for t, im in enumerate(images):
        a = np.asarray(Image.open(io.BytesIO(im["bytes"])).convert("RGB"))
        r = lm.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=a))
        if r.pose_landmarks:
            pose[t], hp[t] = [(p.x, p.y) for p in r.pose_landmarks], True
        if r.left_hand_landmarks:
            left[t], hl[t] = [(p.x, p.y, p.z) for p in r.left_hand_landmarks], True
        if r.right_hand_landmarks:
            right[t], hr[t] = [(p.x, p.y, p.z) for p in r.right_hand_landmarks], True
    k = FPS_OUT // FPS_IN
    pose, hp = upsample(pose, hp, k)
    left, hl = upsample(left, hl, k)
    right, hr = upsample(right, hr, k)
    return {"left": left[..., :2], "right": right[..., :2], "left_z": left[..., 2], "right_z": right[..., 2],
            "pose": pose, "has_left": hl, "has_right": hr, "has_pose": hp}


def shard(args) -> pathlib.Path:
    name, split, data, limit = args
    out = data / "landmarks" / (name + ".pkl")
    if out.exists():
        return out
    import pyarrow.parquet as pq
    from mediapipe.tasks.python import BaseOptions, vision
    path = fetch(name, data)
    opts = vision.HolisticLandmarkerOptions(base_options=BaseOptions(model_asset_path=str(MODEL)),
                                            running_mode=vision.RunningMode.IMAGE)
    samples = []
    with vision.HolisticLandmarker.create_from_options(opts) as lm:
        with pq.ParquetFile(path) as f:
            for g in range(f.num_row_groups):
                for row in f.read_row_group(g, columns=["file", "text", "images"]).to_pylist():
                    if limit and len(samples) >= limit:
                        break
                    if len(row["images"]) < 2:
                        continue
                    samples.append({"split": split, "label": row["text"].strip().lower(), "rec": row["file"],
                                    **clip(lm, row["images"])})
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "wb") as fo:
        pickle.dump(samples, fo)
    if not limit:
        path.unlink()
    print(f"{name}: {len(samples)} clips", flush=True)
    return out


def main(data: pathlib.Path, workers: int = 6, limit: int = 0, shards=SHARDS) -> None:
    data.mkdir(parents=True, exist_ok=True)
    jobs = [(n, s, data, limit) for n, s in shards]
    with ProcessPoolExecutor(workers) as ex:
        parts = list(ex.map(shard, jobs))
    samples = [s for p in parts for s in pickle.load(open(p, "rb"))]
    with open(data / "asl.pkl", "wb") as f:
        pickle.dump({"aspect": 1.0, "fps": FPS_OUT, "samples": samples}, f)
    labels = sorted({s["label"] for s in samples})
    print(f"{len(samples)} clips, {len(labels)} labels -> {data / 'asl.pkl'}")


if __name__ == "__main__":
    a = sys.argv[1:]
    main(pathlib.Path(a[0]) if a else pathlib.Path("C:/Users/Yash/dev/ml-data/popsign"),
         int(a[1]) if len(a) > 1 else 6, int(a[2]) if len(a) > 2 else 0)
