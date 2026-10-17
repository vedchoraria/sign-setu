"""Top-1 and top-3 of the shipped ISL readers on held-out data: words (INCLUDE test split, sign_reader) and letters
and digits (synthetic test split, isl_spell; synthetic scores flatter real signers).
    python tools/ml/eval_isl.py [include.pkl] [isl_spell.pkl]
"""
import pickle
import sys

import numpy as np

from runtime import reader, topk
from train import features

INCLUDE = "C:/Users/Yash/dev/ml-data/include_pose/include.pkl"
SPELL = "C:/Users/Yash/dev/ml-data/isl_spell/isl_spell.pkl"


def score(model: str, cache_path: str, keep=lambda label: True):
    with open(cache_path, "rb") as f:
        cache = pickle.load(f)
    spec, logits = reader(model)
    index = {l: i for i, l in enumerate(spec["labels"])}
    test = [s for s in cache["samples"] if s["split"] == "test" and keep(s["label"]) and s["label"] in index]
    xs = [features(s, cache["aspect"]) for s in test]
    ok = [i for i, x in enumerate(xs) if x is not None]
    x = np.stack([xs[i] for i in ok]).astype(np.float64)
    y = np.array([index[test[i]["label"]] for i in ok])
    out = np.concatenate([logits(x[i:i + 256]) for i in range(0, len(x), 256)])
    return (*topk(out, y), len(y))


def main(include: str, spell: str) -> None:
    rows = [("words", "sign_reader", include, lambda l: True),
            ("letters", "isl_spell", spell, str.isalpha),
            ("digits", "isl_spell", spell, str.isdigit)]
    for what, model, cache, keep in rows:
        try:
            top1, top3, n = score(model, cache, keep)
        except FileNotFoundError as e:
            print(f"ISL {what:8s} skipped: {e.filename} missing")
            continue
        print(f"ISL {what:8s} top-1 {top1:.4f}  top-3 {top3:.4f}  (n={n}, {model})")


if __name__ == "__main__":
    a = sys.argv[1:]
    main(a[0] if a else INCLUDE, a[1] if len(a) > 1 else SPELL)
