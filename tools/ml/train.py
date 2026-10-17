"""Trains a format 1 reader on a landmark cache (INCLUDE, synthetic ISL letters, ASL) and reports test-split accuracy.

    python tools/ml/train.py [cache.pkl] [out.pt] [epochs]   # default INCLUDE: include.pkl -> sign_reader.pt, 400
SIGN_HIDDEN and SIGN_DROPOUT override the width and dropout; export.py needs the same SIGN_HIDDEN (asl_reader: 320).
"""
import os
import pathlib
import pickle
import sys

# Threaded BLAS on tiny per-sample matrices burns every core for no gain.
os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
os.environ.setdefault("OMP_NUM_THREADS", "1")
import numpy as np
import torch
from torch import nn

from features import ARMS, ARMS_FLIPPED, FEATURES, FRAMES, featurize, featurize_hands, isotropic

HIDDEN, KERNEL = int(os.environ.get("SIGN_HIDDEN", 160)), 5
DROPOUT = float(os.environ.get("SIGN_DROPOUT", 0.4))


class SignNet(nn.Module):
    """Per-frame linear layer, two temporal convolutions, average and max pooling, linear classifier."""

    def __init__(self, classes: int, hidden: int = HIDDEN):
        super().__init__()
        self.embed = nn.Linear(FEATURES, hidden)
        self.conv1 = nn.Conv1d(hidden, hidden, KERNEL, padding=KERNEL // 2)
        self.bn1 = nn.BatchNorm1d(hidden)
        self.conv2 = nn.Conv1d(hidden, hidden, KERNEL, padding=KERNEL // 2)
        self.bn2 = nn.BatchNorm1d(hidden)
        self.drop = nn.Dropout(DROPOUT)
        self.head = nn.Linear(2 * hidden, classes)

    def forward(self, x):  # x: (B, FRAMES, FEATURES)
        h = torch.relu(self.embed(x)).transpose(1, 2)
        h = torch.relu(self.bn1(self.conv1(h)))
        h = torch.relu(self.bn2(self.conv2(h)))
        return self.head(self.drop(torch.cat([h.mean(2), h.amax(2)], 1)))


def features(s, aspect, rng=None):
    aspect = s.get("aspect") or aspect
    left, right, pose = isotropic(s["left"], s["right"], s["pose"], aspect)
    hl, hr, hp = s["has_left"], s["has_right"], s["has_pose"]
    if rng is not None:
        n = len(pose)
        # Time jitter: crop up to 15% at each end, then drop or repeat frames at random.
        a, b = rng.integers(0, max(1, n * 15 // 100) + 1, 2)
        keep = np.arange(a, n - b)
        keep = np.sort(rng.choice(keep, size=max(2, int(len(keep) * rng.uniform(0.8, 1.2))), replace=True))
        left, right, pose, hl, hr, hp = (v[keep] for v in (left, right, pose, hl, hr, hp))
        if rng.random() < 0.5:  # a left-handed signer: mirror, which swaps the hands and the arms
            flip = np.array([-1.0, 1.0])
            left, right, hl, hr = right * flip, left * flip, hr, hl
            pose = pose * flip
            pose[:, ARMS] = pose[:, ARMS_FLIPPED]
        th = rng.uniform(-0.2, 0.2)
        m = np.array([[np.cos(th), -np.sin(th)], [np.sin(th), np.cos(th)]]) * rng.uniform(0.9, 1.1, 2)
        left, right, pose = left @ m.T, right @ m.T, pose @ m.T
        left = left + rng.normal(0, 0.003, left.shape)
        right = right + rng.normal(0, 0.003, right.shape)
    return (featurize_hands if s.get("norm") == "hands" else featurize)(left, right, pose, hl, hr, hp)


def batch(samples, aspect, rng=None):
    xs = [features(s, aspect, rng) for s in samples]
    return torch.tensor(np.stack([x if x is not None else np.zeros((FRAMES, FEATURES), np.float32) for x in xs]))


def evaluate(model, x, y):
    model.eval()
    with torch.no_grad():
        top = model(x).topk(3, 1).indices
    return (top[:, 0] == y).float().mean().item(), (top == y[:, None]).any(1).float().mean().item()


def main(cache: pathlib.Path, out: pathlib.Path, epochs: int = 400) -> None:
    with open(cache, "rb") as f:
        cache = pickle.load(f)
    aspect, samples = cache["aspect"], cache["samples"]
    labels = sorted({s["label"] for s in samples})
    index = {l: i for i, l in enumerate(labels)}
    train = [s for s in samples if s["split"] == "train"]
    test = [s for s in samples if s["split"] == "test"]
    dev = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    rng = np.random.default_rng(7)
    torch.manual_seed(7)

    xt = batch(test, aspect).to(dev)
    yt = torch.tensor([index[s["label"]] for s in test], device=dev)
    ytr = np.array([index[s["label"]] for s in train])
    model = SignNet(len(labels)).to(dev)
    opt = torch.optim.AdamW(model.parameters(), lr=2e-3, weight_decay=0.05)
    steps = epochs * ((len(train) + 63) // 64)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=2e-3, total_steps=steps)
    loss_fn = nn.CrossEntropyLoss(label_smoothing=0.1)
    for epoch in range(epochs):
        model.train()
        order = rng.permutation(len(train))
        for k in range(0, len(order), 64):
            ids = order[k:k + 64]
            x = batch([train[i] for i in ids], aspect, rng).to(dev)
            loss = loss_fn(model(x), torch.tensor(ytr[ids], device=dev))
            opt.zero_grad()
            loss.backward()
            opt.step()
            sched.step()
        if epoch % 25 == 24 or epoch == epochs - 1:
            top1, top3 = evaluate(model, xt, yt)
            print(f"epoch {epoch + 1}: loss {loss.item():.3f} test top-1 {top1:.4f} top-3 {top3:.4f}", flush=True)
    torch.save({"state": model.cpu().state_dict(), "labels": labels, "top1": top1, "top3": top3, "epochs": epochs}, out)


if __name__ == "__main__":
    data = pathlib.Path("C:/Users/Yash/dev/ml-data/include_pose")
    a = sys.argv[1:]
    main(pathlib.Path(a[0]) if a else data / "include.pkl", pathlib.Path(a[1]) if len(a) > 1 else data / "sign_reader.pt",
         int(a[2]) if len(a) > 2 else 400)
