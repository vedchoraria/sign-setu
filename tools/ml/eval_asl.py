"""Scores the shipped ASL readers and the app's sign-or-spell gate (SignReaders.kt). See tools/README.md."""
import os
import pathlib
import pickle
import sys

import numpy as np

from eval_isl import score
from features import spell_features
from runtime import reader, speller
from spell_ctc import DATA, cer, greedy, load, sequence, texts, word_list
from train import features

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))

ASL = str(DATA_ROOT / "popsign/asl.pkl")
SPELL_MIN_LETTERS, SPELL_MIN_PEAK = 3, 0.9  # SignReaders.kt


def spelled(logits: np.ndarray, peak: float = SPELL_MIN_PEAK) -> bool:
    """CtcSpell's gate: enough letters, each sure at its best frame. A read that passes wins over the word reader."""
    z = np.exp(logits - logits.max(1, keepdims=True))
    p = (z / z.sum(1, keepdims=True)).max(1)
    best, peaks, last = logits.argmax(1), [], 0
    for b, q in zip(best, p):
        if b != 0 and b != last:
            peaks.append(q)
        elif b != 0:
            peaks[-1] = max(peaks[-1], q)
        last = b
    return len(peaks) >= SPELL_MIN_LETTERS and min(peaks) >= peak


def combined(asl: str, spell_dir: pathlib.Path) -> None:
    with open(asl, "rb") as f:
        cache = pickle.load(f)
    spec, words = reader("asl_reader")
    _, ctc = speller()
    peaks = (0.8, SPELL_MIN_PEAK, 0.95)
    alone = n = 0
    gated = dict.fromkeys(peaks, 0)
    for s in cache["samples"]:
        x = features(s, cache["aspect"]) if s["split"] == "test" and s["label"] in spec["labels"] else None
        if x is None:
            continue
        right = spec["labels"][int(np.argmax(words(x[None].astype(np.float64))))] == s["label"]
        ts = (np.arange(len(s["pose"])) * 1000 / cache["fps"]).astype(np.int64)
        f = spell_features(ts, s["left"], s["right"], s["has_left"], s["has_right"], cache["aspect"])
        n, alone = n + 1, alone + right
        z = ctc(f) if right and f[:, -1].any() else None
        for p in peaks:
            gated[p] += right and not (z is not None and spelled(z, p))
    g = gated[SPELL_MIN_PEAK]
    print(f"ASL combined words   top-1 {g / n:.4f}  word-only {alone / n:.4f}  (n={n}, gate costs {(alone - g) / n:.4f})")
    assert (alone - g) / n <= 0.02, "the sign-or-spell gate costs word reading more than 0.02 top-1"
    letters, aspect = load(spell_dir)
    rng = np.random.default_rng(99)
    seqs = [sequence(t, letters, "test", aspect, rng) for t in texts(rng, word_list(), 400)]
    z = [ctc(spell_features(ts, l, r, hl, hr, aspect)) for ts, l, r, hl, hr in seqs]
    print(f"ASL combined spelled read as spelling {np.mean([spelled(q) for q in z]):.4f}  (400 sequences)")
    for p in peaks:
        print(f"ASL gate peak {p:.2f}: real PopSign words lost {(alone - gated[p]) / n:.4f}, "
              f"spelled sequences passed {np.mean([spelled(q, p) for q in z]):.4f}")


def main(asl: str, spell_dir: pathlib.Path) -> None:
    try:
        top1, top3, n = score("asl_reader", asl)
        print(f"ASL words    top-1 {top1:.4f}  top-3 {top3:.4f}  (n={n}, asl_reader)")
    except FileNotFoundError as e:
        print(f"ASL words    skipped: {e.filename} missing")
    try:
        letters, aspect = load(spell_dir)
    except FileNotFoundError as e:
        print(f"ASL spelling skipped: {e.filename} missing")
        return
    _, logits = speller()
    print(f"ASL spelling CER {cer(lambda f: greedy(logits(f)), letters, aspect):.4f}  (400 sequences, asl_spell)")
    try:
        combined(asl, spell_dir)
    except FileNotFoundError as e:
        print(f"ASL combined skipped: {e.filename} missing")


if __name__ == "__main__":
    a = sys.argv[1:]
    main(a[0] if a else ASL, pathlib.Path(a[1]) if len(a) > 1 else DATA)
