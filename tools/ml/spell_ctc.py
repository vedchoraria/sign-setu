"""The ASL fingerspelling reader: a small CTC model over one hand's landmarks (format 2, CtcSpellReader.kt).

Google's Kaggle "ASL Fingerspelling" set is CC BY 4.0 but needs Kaggle credentials (none here), and FSboard is video.
Instead, sequences are synthesised from real static letters: ASLNow! (huggingface.co/datasets/sid220/
asl-now-fingerspelling, MIT), 21 MediaPipe hand points per sample, A-Z with J and Z as their end shape. Letters are
chained with holds, transitions, J's twist and Z's trace, a slide for double letters, camera jitter and dropped frames.
    python tools/ml/spell_ctc.py prepare [data_dir] [per_letter]   # download and cache asl_now.pkl
    python tools/ml/spell_ctc.py train [data_dir] [epochs]          # asl_spell.pt, prints test CER
    python tools/ml/spell_ctc.py export [data_dir]                  # assets models/asl_spell.{bin,json} + fixture
"""
import hashlib
import json
import math
import os
import pathlib
import pickle
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

import numpy as np
import torch
from torch import nn

import runtime
from export import folded
from features import SPELL_FEATURES, SPELL_FPS, SPELL_MAX, spell_features

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODELS = ROOT / "app" / "src" / "main" / "assets" / "content" / "models"
RESOURCES = ROOT / "app" / "src" / "test" / "resources"
WORDS = ROOT / "app" / "src" / "main" / "assets" / "content" / "words.json"
REPO = "sid220/asl-now-fingerspelling"
ALPHABET = "abcdefghijklmnopqrstuvwxyz"
HIDDEN, KERNEL, DILATIONS = 192, 5, (1, 2, 4, 1)
NOW_ASPECT = 4 / 3  # ASLNow! records webcam frames; their size is not stored
DATA = DATA_ROOT / "asl_spell"


class SpellNet(nn.Module):
    """Per-frame linear layer, residual dilated convolutions, per-frame logits; class 0 is the CTC blank."""

    def __init__(self):
        super().__init__()
        self.embed = nn.Linear(SPELL_FEATURES, HIDDEN)
        self.convs = nn.ModuleList(nn.Conv1d(HIDDEN, HIDDEN, KERNEL, padding=d * (KERNEL // 2), dilation=d) for d in DILATIONS)
        self.bns = nn.ModuleList(nn.BatchNorm1d(HIDDEN) for _ in DILATIONS)
        self.drop = nn.Dropout(0.2)
        self.head = nn.Linear(HIDDEN, len(ALPHABET) + 1)

    def forward(self, x):  # (B, T, F) -> (B, T, classes)
        h = torch.relu(self.embed(x)).transpose(1, 2)
        for conv, bn in zip(self.convs, self.bns):
            h = h + torch.relu(bn(conv(self.drop(h))))
        return self.head(h.transpose(1, 2))



def prepare(data: pathlib.Path, per_letter: int = 0) -> None:
    api = f"https://huggingface.co/api/datasets/{REPO}"
    files = [s["rfilename"] for s in json.load(urllib.request.urlopen(api))["siblings"] if s["rfilename"].endswith(".json")]
    by_letter: dict[str, list] = {}
    for f in files:
        by_letter.setdefault(f.split("/")[0].lower(), []).append(f)
    if per_letter:
        files = [f for fs in by_letter.values() for f in fs[:per_letter]]
    raw = data / "raw"

    def get(f):
        out = raw / f
        if not out.exists():
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(urllib.request.urlopen(f"https://huggingface.co/datasets/{REPO}/resolve/main/{f}").read())
        return f, json.loads(out.read_text())

    letters: dict[str, dict[str, list]] = {c: {"train": [], "test": []} for c in ALPHABET}
    with ThreadPoolExecutor(16) as ex:
        for f, pts in ex.map(get, files):
            a = np.array([[p["x"], p["y"], p["z"]] for p in pts], np.float32)
            if a.shape != (21, 3):
                continue
            # A fifth of the files test, chosen by name so the split never moves.
            split = "test" if hashlib.sha1(f.encode()).digest()[0] < 52 else "train"
            letters[f.split("/")[0].lower()][split].append(a)
    with open(data / "asl_now.pkl", "wb") as fo:
        pickle.dump({"aspect": NOW_ASPECT, "letters": letters}, fo)
    print({c: (len(v["train"]), len(v["test"])) for c, v in letters.items()})


def word_list() -> list[str]:
    words = json.loads(WORDS.read_text(encoding="utf-8"))
    forms = {f for e in words["words"] for f in e.get("w", [])}
    return sorted({w for w in forms if w.isascii() and w.isalpha() and 2 <= len(w) <= 12})


def rot(a: float) -> np.ndarray:
    return np.array([[math.cos(a), -math.sin(a)], [math.sin(a), math.cos(a)]])


def sequence(text: str, letters: dict, split: str, aspect: float, rng: np.random.Generator):
    """A fingerspelled [text] as timestamps (ms) and one hand per frame, in normalised image points."""
    size = rng.uniform(0.12, 0.3)  # wrist to middle knuckle, in frame widths
    wrist = np.array([rng.uniform(0.35, 0.75), rng.uniform(0.45, 0.8) / aspect])  # y in frame widths
    tilt = rng.uniform(-0.25, 0.25)
    keys = []  # (time, wrist, shape points relative to wrist in frame-width units)
    t = rng.uniform(0, 0.3)
    prev = None
    for c in text:
        pool = letters[c][split] or letters[c]["train"]
        h = pool[rng.integers(len(pool))][:, :2] * np.array([1.0, 1.0 / aspect])
        h = (h - h[0]) / max(np.hypot(*(h[9] - h[0])), 1e-6)
        h = h @ rot(tilt + rng.normal(0, 0.08)).T * size * rng.uniform(0.9, 1.1)
        if c == prev:  # a double letter slides sideways
            wrist = wrist + np.array([size * rng.uniform(0.3, 0.6), 0])
        t += rng.uniform(0.06, 0.18)
        hold = rng.uniform(0.1, 0.35)
        if c == "j":  # the little finger draws a hook: the hand twists and drops
            for f in np.linspace(0, 1, 4):
                keys.append((t + f * hold, wrist + np.array([-0.4, 0.5]) * size * f, h @ rot(-1.4 * f).T))
        elif c == "z":  # the index traces a Z
            for f, off in zip(np.linspace(0, 1, 4), ([0, 0], [0.8, 0], [0, 0.6], [0.8, 0.6])):
                keys.append((t + f * hold, wrist + np.array(off) * size, h))
        else:
            keys.append((t, wrist.copy(), h))
            keys.append((t + hold, wrist + rng.normal(0, 0.03, 2) * size, h))
        t += hold
        wrist = wrist + np.array([rng.normal(0.05, 0.1), rng.normal(0, 0.05)]) * size
        prev = c
    end = t + rng.uniform(0, 0.3)
    ts = np.cumsum(rng.uniform(1000 / 32, 1000 / 8, 10000))
    ts = ts[ts < end * 1000].astype(np.int64)
    if len(ts) < 2:
        ts = np.array([0, int(end * 1000)], np.int64)
    kt = np.array([k[0] for k in keys]) * 1000
    hands = np.zeros((len(ts), 21, 2), np.float32)
    seen = np.zeros(len(ts), bool)
    for i, ms in enumerate(ts):
        j = np.searchsorted(kt, ms, side="right") - 1
        if j < 0 or ms > kt[-1] + 150:
            continue  # before the first letter or after the last: the hand is still rising or gone
        a, b = keys[j], keys[min(j + 1, len(keys) - 1)]
        f = 0.0 if b[0] <= a[0] else min(1.0, (ms / 1000 - a[0]) / (b[0] - a[0]))
        w = a[1] * (1 - f) + b[1] * f
        shape = a[2] * (1 - f) + b[2] * f
        hands[i] = (w + shape) * np.array([1.0, aspect]) + rng.normal(0, 0.002, (21, 2))
        seen[i] = rng.random() > 0.04
    if rng.random() < 0.5:  # a left-handed signer
        hands[..., 0] = 1 - hands[..., 0]
        return ts, hands, np.zeros_like(hands), seen, np.zeros_like(seen)
    return ts, np.zeros_like(hands), hands, np.zeros_like(seen), seen


def texts(rng: np.random.Generator, words: list[str], n: int) -> list[str]:
    out = []
    for _ in range(n):
        if words and rng.random() < 0.6:
            out.append(words[rng.integers(len(words))])
        else:
            out.append("".join(rng.choice(list(ALPHABET), rng.integers(1, 9))))
    return out


def batch(items, letters, split, aspect, rng):
    feats, targets = [], []
    for text in items:
        ts, left, right, hl, hr = sequence(text, letters, split, aspect, rng)
        feats.append(spell_features(ts, left, right, hl, hr, aspect))
        targets.append([ALPHABET.index(c) + 1 for c in text])
    t = max(len(f) for f in feats)
    x = np.zeros((len(feats), t, SPELL_FEATURES), np.float32)
    for i, f in enumerate(feats):
        x[i, :len(f)] = f
    return torch.tensor(x), [len(f) for f in feats], targets


def greedy(logits: np.ndarray) -> str:
    best = logits.argmax(-1)
    return "".join(ALPHABET[c - 1] for i, c in enumerate(best) if c != 0 and (i == 0 or c != best[i - 1]))


def edit_distance(a: str, b: str) -> int:
    row = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        prev, row[0] = row[0], i
        for j, cb in enumerate(b, 1):
            prev, row[j] = row[j], min(row[j] + 1, row[j - 1] + 1, prev + (ca != cb))
    return row[-1]


def cer(predict, letters, aspect, n: int = 400, seed: int = 99) -> float:
    """Character error rate over [n] test sequences built from the held-out letter samples."""
    rng = np.random.default_rng(seed)
    errors = chars = 0
    for text in texts(rng, word_list(), n):
        ts, left, right, hl, hr = sequence(text, letters, "test", aspect, rng)
        errors += edit_distance(predict(spell_features(ts, left, right, hl, hr, aspect)), text)
        chars += len(text)
    return errors / chars



def load(data: pathlib.Path):
    with open(data / "asl_now.pkl", "rb") as f:
        c = pickle.load(f)
    return c["letters"], c["aspect"]


def train(data: pathlib.Path, epochs: int = 60, per_epoch: int = 4000) -> None:
    letters, aspect = load(data)
    words = word_list()
    dev = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    rng = np.random.default_rng(7)
    torch.manual_seed(7)
    model = SpellNet().to(dev)
    opt = torch.optim.AdamW(model.parameters(), lr=2e-3, weight_decay=0.01)
    steps = epochs * (per_epoch // 32)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=2e-3, total_steps=steps)
    ctc = nn.CTCLoss(blank=0, zero_infinity=True)
    for epoch in range(epochs):
        model.train()
        for _ in range(per_epoch // 32):
            x, lens, targets = batch(texts(rng, words, 32), letters, "train", aspect, rng)
            logp = model(x.to(dev)).log_softmax(-1).transpose(0, 1)
            y = torch.tensor([c for t in targets for c in t], device=dev)
            loss = ctc(logp, y, torch.tensor(lens), torch.tensor([len(t) for t in targets]))
            opt.zero_grad()
            loss.backward()
            opt.step()
            sched.step()
        model.eval()
        with torch.no_grad():
            score = cer(lambda f: greedy(model(torch.tensor(f[None]).to(dev))[0].cpu().numpy()), letters, aspect, 200)
        print(f"epoch {epoch + 1}: loss {loss.item():.3f} test CER {score:.4f}", flush=True)
    torch.save({"state": model.cpu().state_dict(), "cer": score, "epochs": epochs}, data / "asl_spell.pt")  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch


def export(data: pathlib.Path) -> None:
    ckpt = torch.load(data / "asl_spell.pt", weights_only=True)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    model = SpellNet()
    model.load_state_dict(ckpt["state"])
    model.eval()
    with torch.no_grad():
        tensors = [model.embed.weight, model.embed.bias]
        for conv, bn in zip(model.convs, model.bns):
            tensors += list(folded(conv, bn))
        tensors += [model.head.weight, model.head.bias]
        blob = b"".join(t.detach().numpy().astype("<f2").tobytes() for t in tensors)
    (MODELS / "asl_spell.bin").write_bytes(blob)
    spec = {
        "format": 2,
        "architecture": "per-frame linear+relu; residual blocks h += relu(conv1d(h)) (same padding, dilated, batch "
                        "norm folded); per-frame linear to logits; class 0 is the CTC blank, greedy decoding",
        "fps": SPELL_FPS, "max_frames": SPELL_MAX, "features": SPELL_FEATURES, "hidden": HIDDEN, "kernel": KERNEL,
        "dilations": list(DILATIONS), "alphabet": ALPHABET,
        "tensors": [{"shape": list(t.shape)} for t in tensors],
        "dtype": "float16",
        "weights": "float16 little-endian, tensors in the order above, row-major; conv weight is [out][in][kernel]",
        "feature_spec": "frames resampled to `fps` by nearest timestamp (ties to the earlier), at most `max_frames`; "
                        "the hand present in more resampled frames (ties to the right), a left hand mirrored in x; "
                        "y scaled by height/width; per frame: 21x(x,y) minus the wrist, divided by the wrist to "
                        "middle-knuckle (point 9) length, then the wrist's step since the previous frame in that unit "
                        "(zero after a frame without the hand), then a presence flag; a frame without the hand is zeros",
        "dataset": "synthetic sequences from ASLNow! static letters (huggingface.co/datasets/sid220/"
                   "asl-now-fingerspelling, MIT)",
        "synthetic": True,
        "epochs": ckpt.get("epochs"),
        "test_cer": round(ckpt["cer"], 4),
    }
    (MODELS / "asl_spell.json").write_text(json.dumps(spec, indent=1) + "\n", encoding="utf-8", newline="\n")
    print(f"asl_spell: weights {len(blob)} bytes")

    # The fixture is what the shipped half-precision file computes, so Kotlin must match it exactly.
    _, shipped = runtime.speller()
    letters, aspect = load(data)
    rng = np.random.default_rng(5)
    cases = []
    for text in ("police", "anjali", "zz", "j"):
        ts, left, right, hl, hr = sequence(text, letters, "test", aspect, rng)
        left, right = np.round(left.astype(np.float64), 5), np.round(right.astype(np.float64), 5)
        f = spell_features(ts, left, right, hl, hr, aspect)
        logits = shipped(f.astype(np.float64))
        frames = [{"t": int(ts[i]), "left": [round(float(v), 5) for v in left[i].reshape(-1)] if hl[i] else None,
                   "right": [round(float(v), 5) for v in right[i].reshape(-1)] if hr[i] else None} for i in range(len(ts))]
        cases.append({"text": text, "decoded": greedy(logits), "frames": frames,
                      "logits": [[round(float(v), 5) for v in row] for row in logits]})
    (RESOURCES / "asl_spell_fixture.json").write_text(json.dumps({"aspect": aspect, "cases": cases}), encoding="utf-8")


if __name__ == "__main__":
    cmd, a = sys.argv[1], sys.argv[2:]
    data = pathlib.Path(a[0]) if a else DATA
    data.mkdir(parents=True, exist_ok=True)
    if cmd == "prepare":
        prepare(data, int(a[1]) if len(a) > 1 else 0)
    elif cmd == "train":
        train(data, int(a[1]) if len(a) > 1 else 60, int(a[2]) if len(a) > 2 else 4000)
    else:
        export(data)
