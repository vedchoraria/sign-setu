"""Downloads the INCLUDE pose release (OpenHands, MediaPipe Holistic keypoints) and caches it as normalised landmarks.

    python tools/ml/prepare.py [data_dir]   # default C:/Users/Yash/dev/ml-data/include_pose
"""
import csv
import pathlib
import pickle
import re
import sys
import urllib.request
import zipfile

import numpy as np

URL = "https://zenodo.org/records/6674324/files/INCLUDE.zip?download=1"
WIDTH, HEIGHT = 1080, 1920  # the release scaled its 0..1 points by a portrait 1080x1920 frame
# The videos are landscape 1920x1080, so after undoing that scaling the true frame aspect is 16:9, not 9:16.
ASPECT = 1920 / 1080


def label(folder: str) -> str:
    return re.sub(r"^(\d+|Ex)\.\s*", "", folder).strip().lower()


def main(data: pathlib.Path) -> None:
    data.mkdir(parents=True, exist_ok=True)
    archive, raw = data / "INCLUDE.zip", data / "raw"
    if not archive.exists():
        urllib.request.urlretrieve(URL, archive)
    root = raw / "INCLUDE"
    if not (root / "Pose_Signs").exists():
        zipfile.ZipFile(archive).extractall(raw)
        for inner in ("Pose_Signs.zip", "Train_Test_Split.zip"):
            zipfile.ZipFile(root / inner).extractall(root)

    samples = []
    for split in ("train", "test"):
        with open(root / "Train_Test_Split" / f"{split}_include.csv", newline="") as f:
            for row in csv.DictReader(f):
                path = root / "Pose_Signs" / pathlib.Path(row["FilePath"]).with_suffix(".pkl")
                if not path.exists():  # the release has no poses for "Second (Number)"
                    continue
                with open(path, "rb") as p:
                    k = pickle.load(p)["keypoints"][..., :2] / np.array([WIDTH, HEIGHT])
                pose, left, right = k[:, :33], k[:, 33:54], k[:, 54:75]
                samples.append({
                    "split": split, "label": label(pathlib.Path(row["FilePath"]).parts[1]),
                    "left": left.astype(np.float32), "right": right.astype(np.float32), "pose": pose.astype(np.float32),
                    # The release stores an undetected hand or body as zeros.
                    "has_left": np.abs(left).sum((1, 2)) > 0, "has_right": np.abs(right).sum((1, 2)) > 0,
                    "has_pose": np.abs(pose[:, 11:13]).sum((1, 2)) > 0,
                })
    with open(data / "include.pkl", "wb") as f:
        pickle.dump({"aspect": ASPECT, "samples": samples}, f)
    labels = sorted({s["label"] for s in samples})
    print(f"{len(samples)} samples, {len(labels)} labels")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "C:/Users/Yash/dev/ml-data/include_pose"))
