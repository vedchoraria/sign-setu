"""Top-1 and top-3 of word-reader checkpoints on a cache split, e.g. ISLRTC signers held out of training.
    python tools/ml/eval_signers.py <cache.pkl> <split> <model.pt>...
"""
import pickle
import sys

import torch

from train import SignNet, batch, evaluate


def main(cache_path: str, split: str, models: list) -> None:
    cache = pickle.load(open(cache_path, "rb"))
    for path in models:
        ckpt = torch.load(path, weights_only=True)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
        labels = ckpt["labels"]
        index = {l: i for i, l in enumerate(labels)}
        test = [s for s in cache["samples"] if s["split"] == split and s["label"] in index]
        model = SignNet(len(labels))
        model.load_state_dict(ckpt["state"])
        x = batch(test, cache["aspect"])
        y = torch.tensor([index[s["label"]] for s in test])
        top1, top3 = evaluate(model, x, y)
        print(f"{path}: {split} top-1 {top1:.4f} top-3 {top3:.4f} (n={len(test)})")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
