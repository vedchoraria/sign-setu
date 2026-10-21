"""Real-signer ISL letters and digits for evaluation only (never shipped): clips from the ISLRTC Indian Sign Language
Dictionary (data.gov.in, Government Open Data Licence India; huggingface.co/datasets/silentone0725/
Indian_Sign_Language_Data.gov_Rencoded, MIT). The dictionary has no clip per letter, so letters come from acronyms it
fingerspells (ATM, DVD, ...); digits are its 0 to 9 clips. Landmarks by the app's Holistic model at the full 25 fps.
Runs in the mediapipe venv:
    python -I tools/ml/prepare_islrtc.py [data_dir]   # default $SIGNSETU_DATA/islrtc -> islrtc.pkl
"""
import os
import pathlib
import pickle
import sys
import urllib.request

import numpy as np

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODEL = ROOT / "app" / "src" / "main" / "assets" / "content" / "models" / "holistic_landmarker.task"
BASE = "https://huggingface.co/datasets/silentone0725/Indian_Sign_Language_Data.gov_Rencoded/resolve/main/"
DIGITS = {"Z/Zero": "0", "Numbers/1_One": "1", "Numbers/2_Two": "2", "Numbers/3_Three": "3", "Numbers/4_Four": "4",
          "Numbers/5_Five": "5", "Numbers/6_Six": "6", "Numbers/7_Seven": "7", "Numbers/8_Eight": "8", "Numbers/9_Nine": "9"}
# Acronyms checked by eye to be fingerspelled, with how many takes open the clip (DTP is signed twice).
ACRONYMS = {"A/ATM": 1, "B/BMW": 1, "C/CD": 1, "D/DVD": 1, "D/DTP": 2, "G/GST": 1, "U/URL": 1}


def landmarks(path: pathlib.Path) -> dict:
    import cv2
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions, vision
    opts = vision.HolisticLandmarkerOptions(base_options=BaseOptions(model_asset_path=str(MODEL)),
                                            running_mode=vision.RunningMode.VIDEO)
    cap = cv2.VideoCapture(str(path))
    fps = cap.get(cv2.CAP_PROP_FPS)
    w, h = cap.get(cv2.CAP_PROP_FRAME_WIDTH), cap.get(cv2.CAP_PROP_FRAME_HEIGHT)
    pose, left, right, hp, hl, hr = [], [], [], [], [], []
    with vision.HolisticLandmarker.create_from_options(opts) as lm:
        t = 0
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            r = lm.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)),
                                    int(t * 1000 / fps))
            t += 1
            pts = lambda ps, n: np.array([(p.x, p.y) for p in ps], np.float32) if ps else np.zeros((n, 2), np.float32)
            pose.append(pts(r.pose_landmarks, 33)), hp.append(bool(r.pose_landmarks))
            left.append(pts(r.left_hand_landmarks, 21)), hl.append(bool(r.left_hand_landmarks))
            right.append(pts(r.right_hand_landmarks, 21)), hr.append(bool(r.right_hand_landmarks))
    return {"fps": fps, "aspect": w / h, "pose": np.stack(pose), "left": np.stack(left), "right": np.stack(right),
            "has_pose": np.array(hp), "has_left": np.array(hl), "has_right": np.array(hr)}


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
