"""Exports a trained format 1 reader to the app's content bundle and writes its Kotlin parity fixture.

    python tools/ml/export.py [name] [checkpoint.pt] [cache.pkl]   # default sign_reader from include_pose
Names: sign_reader (INCLUDE), isl_spell (synthetic ISL letters and digits), asl_reader (PopSign ASL).
Then rebuild the manifest: python tools/content/build_content.py --manifest-only
"""
import json
import pathlib
import pickle
import sys

import numpy as np
import torch

from features import FEATURES, FRAMES
from train import HIDDEN, KERNEL, SignNet, features

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODELS = ROOT / "app" / "src" / "main" / "assets" / "content" / "models"
RESOURCES = ROOT / "app" / "src" / "test" / "resources"
DATASETS = {
    "sign_reader": "INCLUDE (AI4Bharat), OpenHands pose release, zenodo.org/records/6674324, CC BY 4.0",
    "isl_spell": "synthetic: the app's ISL letter and digit keyframes (signs.json) posed on the rig, jittered",
    "asl_reader": "PopSign ASL v1.0 (Georgia Tech, NTID), signdata.cc.gatech.edu, CC BY 4.0; frames from "
                  "huggingface.co/datasets/sign/popsign-images, landmarks by MediaPipe Holistic",
}


def folded(conv, bn):
    k = bn.weight / torch.sqrt(bn.running_var + bn.eps)
    return conv.weight * k[:, None, None], (conv.bias - bn.running_mean) * k + bn.bias


def rounded(s):
    """A sample as the app sees it: rounded normalised coordinates, missing parts dropped."""
    r = {key: np.round(s[key].astype(np.float64), 5) for key in ("left", "right", "pose")}
    return {**s, **r}


def frames_json(s):
    def pts(a, ok):
        return [round(float(v), 5) for v in a.reshape(-1)] if ok else None
    return [{"left": pts(s["left"][i], s["has_left"][i]), "right": pts(s["right"][i], s["has_right"][i]),
             "pose": pts(s["pose"][i, :17], s["has_pose"][i])} for i in range(len(s["pose"]))]


def main(name: str, ckpt_path: pathlib.Path, cache_path: pathlib.Path) -> None:
    ckpt = torch.load(ckpt_path)
    labels = ckpt["labels"]
    model = SignNet(len(labels))
    model.load_state_dict(ckpt["state"])
    model.eval()
    with open(cache_path, "rb") as f:
        cache = pickle.load(f)
    with torch.no_grad():
        c1w, c1b = folded(model.conv1, model.bn1)
        c2w, c2b = folded(model.conv2, model.bn2)
        tensors = [model.embed.weight, model.embed.bias, c1w, c1b, c2w, c2b, model.head.weight, model.head.bias]
        blob = b"".join(t.detach().numpy().astype("<f4").tobytes() for t in tensors)
    MODELS.mkdir(parents=True, exist_ok=True)
    (MODELS / f"{name}.bin").write_bytes(blob)
    spec = {
        "format": 1,
        "architecture": "per-frame linear+relu; conv1d+relu x2 (same padding, batch norm folded); "
                        "global average and max pooling concatenated; linear to logits",
        "frames": FRAMES, "features": FEATURES, "hidden": HIDDEN, "kernel": KERNEL, "classes": len(labels),
        "tensors": [{"shape": list(t.shape)} for t in tensors],
        "weights": "float32 little-endian, tensors in the order above, row-major; conv weight is [out][in][kernel]",
        "feature_spec": "MediaPipe Holistic normalised image points, unmirrored; y scaled by height/width; "
                        "centre = mean shoulder midpoint (pose 11, 12) over the segment, scale = mean shoulder width; "
                        "per frame: left hand 21x(x,y), right hand 21x(x,y), pose 11..16 (x,y), left and right "
                        "presence flags; a missing part is zeros; segment resampled to `frames` by nearest index "
                        "(i*(n-1)*2 + frames-1) div (2*(frames-1))",
        "dataset": DATASETS.get(name, name),
        **({"synthetic": True} if cache.get("synthetic") else {}),
        "epochs": ckpt.get("epochs"),
        "test_top1": round(ckpt["top1"], 4), "test_top3": round(ckpt["top3"], 4),
        "labels": labels,
    }
    (MODELS / f"{name}.json").write_text(json.dumps(spec, indent=1) + "\n", encoding="utf-8", newline="\n")
    print(f"{name}: weights {len(blob)} bytes, {len(labels)} classes")

    aspect = cache["aspect"]
    test = [s for s in cache["samples"] if s["split"] == "test" and s["has_pose"][:7].any()]
    pick = lambda i: test[i % len(test)]
    one_hand = dict(pick(3), has_left=np.zeros_like(pick(3)["has_left"]))
    short = {k: (v[:7] if isinstance(v, np.ndarray) else v) for k, v in pick(5).items()}
    cases = []
    for s in (pick(0), pick(200), pick(400), one_hand, short):
        s = rounded(s)
        x = torch.tensor(features(s, aspect)[None])
        with torch.no_grad():
            logits = model(x)[0].double().numpy()
        cases.append({"word": s["label"], "frames": frames_json(s), "logits": [round(float(v), 6) for v in logits]})
    RESOURCES.mkdir(parents=True, exist_ok=True)
    (RESOURCES / f"{name}_fixture.json").write_text(json.dumps({"aspect": aspect, "cases": cases}), encoding="utf-8")


if __name__ == "__main__":
    data = pathlib.Path("C:/Users/Yash/dev/ml-data/include_pose")
    a = sys.argv[1:]
    main(a[0] if a else "sign_reader", pathlib.Path(a[1]) if len(a) > 1 else data / "sign_reader.pt",
         pathlib.Path(a[2]) if len(a) > 2 else data / "include.pkl")
