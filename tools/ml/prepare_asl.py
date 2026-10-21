"""Builds the ASL landmark cache from PopSign ASL v1.0 frames with the app's own Holistic model.

PopSign ASL v1.0 (Georgia Tech, NTID; 250 signs, 47 Deaf signers) is CC BY 4.0. Google's Kaggle "Isolated Sign
Language Recognition" set is the same corpus as landmarks, also CC BY 4.0, but needs Kaggle credentials; the
huggingface.co/datasets/sign/popsign-images release holds the clips as 256x256 frames at about 5 a second instead.
Its train and validation signers train and its test signers test (signer-independent). Each shard is deleted once
read, so the 14.5 GB train split passes through the disk 500 MB at a time.
The non-game split misses some of the 250 signs; the game split's validation and test signers fill them in, read
by HTTP range so only the row groups holding a missing sign are fetched.
Runs in a venv with mediapipe, pyarrow and pillow (not the torch one):
    python -I tools/ml/prepare_asl.py [data_dir] [workers] [limit]   # default $SIGNSETU_DATA/popsign
"""
import io
import json
import os
import pathlib
import pickle
import sys
import urllib.request
from concurrent.futures import ProcessPoolExecutor

import numpy as np

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODEL = ROOT / "app" / "src" / "main" / "assets" / "content" / "models" / "holistic_landmarker.task"
BASE = "https://huggingface.co/datasets/sign/popsign-images/resolve/main/data/non-game/"
GAME = "https://huggingface.co/datasets/sign/popsign-images/resolve/main/data/game/"
GAME_SHARDS = [(f"validation-{i:05d}-of-00030.parquet", "train") for i in range(30)] + \
              [(f"test-{i:05d}-of-00034.parquet", "test") for i in range(34)]
CAP = {"train": 60, "test": 15}  # clips per missing sign per game shard
SHARDS = [(f"train-{i:05d}-of-00028.parquet", "train") for i in range(28)] +          [(f"validation-{i:05d}-of-00009.parquet", "train") for i in range(9)] + \
         [(f"test-{i:05d}-of-00004.parquet", "test") for i in range(4)]
FPS_IN, FPS_OUT = 5, 15
LABELS = pathlib.Path(__file__).with_name("popsign_labels.json")


def request(url: str, **headers) -> urllib.request.Request:
    req = urllib.request.Request(url, headers=headers)
    token = pathlib.Path.home() / ".cache" / "huggingface" / "token"
    if token.exists():
        req.add_header("Authorization", "Bearer " + token.read_text().strip())
    return req


class Ranged(io.RawIOBase):
    """A remote file read by HTTP range requests, so pyarrow fetches only the column chunks it needs."""

    def __init__(self, url: str):
        with urllib.request.urlopen(request(url, Range="bytes=0-0")) as r:
            self.url, self.size = r.url, int(r.headers["Content-Range"].split("/")[1])
        self.pos = 0

    def seekable(self): return True
    def readable(self): return True
    def tell(self): return self.pos

    def seek(self, off, whence=0):
        self.pos = off if whence == 0 else self.pos + off if whence == 1 else self.size + off
        return self.pos

    def readinto(self, buf):
        end = min(self.size, self.pos + len(buf))
        if end <= self.pos:
            return 0
        with urllib.request.urlopen(urllib.request.Request(self.url, headers={"Range": f"bytes={self.pos}-{end - 1}"})) as r:
            b = r.read()
        buf[:len(b)] = b
        self.pos += len(b)
        return len(b)


def fetch(name: str, out: pathlib.Path) -> pathlib.Path:
    path = out / name
    if not path.exists():
        with urllib.request.urlopen(request(BASE + name)) as r, open(path.with_suffix(".part"), "wb") as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
            if f.tell() != int(r.headers["Content-Length"]):
                raise OSError(name + ": truncated download")
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
    with vision.HolisticLandmarker.create_from_options(opts) as lm, pq.ParquetFile(path) as f:
        for g in range(f.num_row_groups):
            for row in f.read_row_group(g, columns=["file", "text", "images"]).to_pylist():
                if limit and len(samples) >= limit:
                    break
                if len(row["images"]) < 2:
                    continue
                # Glosses keep their case (TV): asl_signs.json ids and asl_words.json keys use it.
                samples.append({"split": split, "label": row["text"].strip(), "rec": row["file"],
                                **clip(lm, row["images"])})
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "wb") as fo:
        pickle.dump(samples, fo)
    if not limit:
        path.unlink()
    print(f"{name}: {len(samples)} clips", flush=True)
    return out


def game_shard(args) -> pathlib.Path:
    """Clips of the [want] signs from one game shard, at most CAP per sign."""
    name, split, data, want = args
    out = data / "landmarks" / ("game-" + name + ".pkl")
    if out.exists():
        return out
    import pyarrow.parquet as pq
    from mediapipe.tasks.python import BaseOptions, vision
    opts = vision.HolisticLandmarkerOptions(base_options=BaseOptions(model_asset_path=str(MODEL)),
                                            running_mode=vision.RunningMode.IMAGE)
    f = pq.ParquetFile(io.BufferedReader(Ranged(GAME + name), 1 << 20))
    taken: dict[str, int] = {}
    samples = []
    with vision.HolisticLandmarker.create_from_options(opts) as lm:
        for g in range(f.num_row_groups):
            texts = [t.strip() for t in f.read_row_group(g, columns=["text"]).column("text").to_pylist()]
            if not any(t in want and taken.get(t, 0) < CAP[split] for t in texts):
                continue
            for row in f.read_row_group(g, columns=["file", "text", "images"]).to_pylist():
                label = row["text"].strip()
                if label not in want or taken.get(label, 0) >= CAP[split] or len(row["images"]) < 2:
                    continue
                taken[label] = taken.get(label, 0) + 1
                samples.append({"split": split, "label": label, "rec": "game/" + row["file"], **clip(lm, row["images"])})
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "wb") as fo:
        pickle.dump(samples, fo)
    print(f"game {name}: {len(samples)} clips", flush=True)
    return out


def main(data: pathlib.Path, workers: int = 6, limit: int = 0, shards=SHARDS) -> None:
    data.mkdir(parents=True, exist_ok=True)
    jobs = [(n, s, data, limit) for n, s in shards]
    with ProcessPoolExecutor(workers) as ex:
        parts = list(ex.map(shard, jobs))
    samples = [s for p in parts for s in pickle.load(open(p, "rb"))]
    if not limit:
        signs = set(json.loads(LABELS.read_text(encoding="utf-8"))["labels"])
        have = {sp: {s["label"] for s in samples if s["split"] == sp} for sp in CAP}
        jobs = [(n, sp, data, signs - have[sp]) for n, sp in GAME_SHARDS]
        with ProcessPoolExecutor(workers) as ex:
            samples += [s for p in ex.map(game_shard, jobs) for s in pickle.load(open(p, "rb"))]
    with open(data / "asl.pkl", "wb") as f:
        pickle.dump({"aspect": 1.0, "fps": FPS_OUT, "samples": samples}, f)
    labels = sorted({s["label"] for s in samples})
    print(f"{len(samples)} clips, {len(labels)} labels -> {data / 'asl.pkl'}")


if __name__ == "__main__":
    a = sys.argv[1:]
    main(pathlib.Path(a[0]) if a else DATA_ROOT / "popsign",
         int(a[1]) if len(a) > 1 else 6, int(a[2]) if len(a) > 2 else 0)
