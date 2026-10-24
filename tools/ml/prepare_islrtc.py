"""Evaluation-only ISLRTC dictionary digits and fingerspelled acronyms, landmarked by the app's Holistic model."""
import os
import pathlib
import pickle
import sys
import urllib.request

import numpy as np

from eval_video import landmarks as video_landmarks

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))

BASE = "https://huggingface.co/datasets/silentone0725/Indian_Sign_Language_Data.gov_Rencoded/resolve/main/"
DIGITS = {"Z/Zero": "0", "Numbers/1_One": "1", "Numbers/2_Two": "2", "Numbers/3_Three": "3", "Numbers/4_Four": "4",
          "Numbers/5_Five": "5", "Numbers/6_Six": "6", "Numbers/7_Seven": "7", "Numbers/8_Eight": "8", "Numbers/9_Nine": "9"}
# Acronyms checked by eye to be fingerspelled, with how many takes open the clip (DTP is signed twice).
ACRONYMS = {"A/ATM": 1, "B/BMW": 1, "C/CD": 1, "D/DVD": 1, "D/DTP": 2, "G/GST": 1, "U/URL": 1}


def landmarks(path: pathlib.Path) -> dict:
    c = video_landmarks(path)
    return {"fps": c["fps"], "aspect": c["aspect"], **{k: np.stack(c[k]) for k in ("pose", "left", "right")},
            **{k: np.array(c[k]) for k in ("has_pose", "has_left", "has_right")}}


def main(data: pathlib.Path) -> None:
    raw = data / "raw"
    raw.mkdir(parents=True, exist_ok=True)
    clips = []
    for name, letters in [(n, d) for n, d in DIGITS.items()] + [(n, n.split("/")[1].lower()) for n in ACRONYMS]:
        path = raw / (name.split("/")[1] + ".mp4")
        if not path.exists():
            path.write_bytes(urllib.request.urlopen(BASE + name + ".mp4").read())
        clips.append({"name": name, "letters": letters, **landmarks(path)})
        print(name, len(clips[-1]["pose"]), "frames", flush=True)
    with open(data / "islrtc.pkl", "wb") as f:
        pickle.dump(clips, f)


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DATA_ROOT / "islrtc")
