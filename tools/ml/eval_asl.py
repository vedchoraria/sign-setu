"""Scores the shipped ASL readers: word top-1 and top-3 (PopSign test signers, asl_reader) and fingerspelling
character error rate (sequences from held-out ASLNow! letters, asl_spell; synthetic, so it flatters real signing).
The combined rows read the same segments through both models and the app's sign-or-spell gate (SignReaders.kt).
    python tools/ml/eval_asl.py [asl.pkl] [asl_spell_dir]
"""
import pathlib
import pickle
import sys

import numpy as np

from eval_isl import score
from features import spell_features
from runtime import reader, speller
from spell_ctc import DATA, cer, greedy, load, sequence, texts, word_list
from train import features

ASL = "C:/Users/Yash/dev/ml-data/popsign/asl.pkl"
SPELL_MIN_LETTERS, SPELL_MIN_PEAK = 3, 0.9  # SignReaders.kt


def spelled(logits: np.ndarray) -> bool:
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
    return len(peaks) >= SPELL_MIN_LETTERS and min(peaks) >= SPELL_MIN_PEAK


def combined(asl: str, spell_dir: pathlib.Path) -> None:
    with open(asl, "rb") as f:
        cache = pickle.load(f)
    spec, words = reader("asl_reader")
    _, ctc = speller()
    alone = gated = n = 0
    for s in cache["samples"]:
        x = features(s, cache["aspect"]) if s["split"] == "test" and s["label"] in spec["labels"] else None
        if x is None:
            continue
        right = spec["labels"][int(np.argmax(words(x[None].astype(np.float64))))] == s["label"]
        ts = (np.arange(len(s["pose"])) * 1000 / cache["fps"]).astype(np.int64)
        f = spell_features(ts, s["left"], s["right"], s["has_left"], s["has_right"], cache["aspect"])
        n, alone = n + 1, alone + right
        gated += right and not (f[:, -1].any() and spelled(ctc(f)))
    print(f"ASL combined words   top-1 {gated / n:.4f}  word-only {alone / n:.4f}  (n={n}, gate costs {(alone - gated) / n:.4f})")
    assert (alone - gated) / n <= 0.02, "the sign-or-spell gate costs word reading more than 0.02 top-1"
    letters, aspect = load(spell_dir)
    rng = np.random.default_rng(99)
    seqs = [sequence(t, letters, "test", aspect, rng) for t in texts(rng, word_list(), 400)]
    read = np.mean([spelled(ctc(spell_features(ts, l, r, hl, hr, aspect))) for ts, l, r, hl, hr in seqs])
    print(f"ASL combined spelled read as spelling {read:.4f}  (400 sequences)")


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
