"""NumPy forward passes over the exported assets, as the app runs them, so evaluation scores what ships."""
import json
import pathlib

import numpy as np

MODELS = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content" / "models"


def _tensors(name: str):
    spec = json.loads((MODELS / f"{name}.json").read_text(encoding="utf-8"))
    flat = np.frombuffer((MODELS / f"{name}.bin").read_bytes(), "<f2" if spec.get("dtype") == "float16" else "<f4")
    out, at = [], 0
    for t in spec["tensors"]:
        n = int(np.prod(t["shape"]))
        out.append(flat[at:at + n].reshape(t["shape"]).astype(np.float64))
        at += n
    assert at == len(flat), "weights size"
    return spec, out


def _conv(a, w, b, d=1):
    """a: (B, C, T); same padding, dilation d."""
    k = w.shape[2]
    pad = d * (k // 2)
    p = np.pad(a, ((0, 0), (0, 0), (pad, pad)))
    t = a.shape[2]
    return sum(np.einsum("oi,bit->bot", w[:, :, q], p[:, :, q * d:q * d + t]) for q in range(k)) + b[None, :, None]


def reader(name: str):
    """Format 1: (B, frames, features) -> (spec, logits function)."""
    spec, (ew, eb, c1w, c1b, c2w, c2b, hw, hb) = _tensors(name)

    def logits(x):
        a = np.maximum(x @ ew.T + eb, 0).transpose(0, 2, 1)
        a = np.maximum(_conv(a, c1w, c1b), 0)
        a = np.maximum(_conv(a, c2w, c2b), 0)
        return np.concatenate([a.mean(2), a.max(2)], 1) @ hw.T + hb
    return spec, logits


def speller(name: str = "asl_spell"):
    """Format 2: (frames, features) -> (spec, per-frame logits function)."""
    spec, t = _tensors(name)
    ew, eb, hw, hb = t[0], t[1], t[-2], t[-1]
    convs = list(zip(t[2:-2:2], t[3:-2:2], spec["dilations"]))

    def logits(x):
        a = np.maximum(x[None] @ ew.T + eb, 0).transpose(0, 2, 1)
        for w, b, d in convs:
            a = a + np.maximum(_conv(a, w, b, d), 0)
        return a[0].T @ hw.T + hb
    return spec, logits


def topk(logits: np.ndarray, y: np.ndarray) -> tuple[float, float]:
    top = np.argsort(-logits, 1)[:, :3]
    return float((top[:, 0] == y).mean()), float((top == y[:, None]).any(1).mean())
