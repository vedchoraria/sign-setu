"""Training cache for the ISL letter and digit reader: real photographs (prepare_isl_real.py) plus synthetic poses
(synth_isl.py) in train, real held-out signers only in test, all with hand-normalised features.
    python tools/ml/mix_isl_spell.py <isl_real.pkl> <synthetic isl_spell.pkl> <out.pkl>
"""
import pickle
import sys

import numpy as np

REPEAT = 8


def main(real_path: str, synth_path: str, out: str) -> None:
    real = pickle.load(open(real_path, "rb"))["samples"]
    synth = pickle.load(open(synth_path, "rb"))
    samples = []
    for s in real:
        # One aspect for the whole cache: fold each photograph's own aspect into y.
        k = np.array([1.0, 1.0 / s["aspect"]], np.float32)
        samples.append({**s, "left": s["left"] * k, "right": s["right"] * k, "aspect": 1.0, "norm": "hands"})
    # Real photographs are few beside 400 synthetic poses a class, so they are repeated to carry weight.
    samples += [s for s in samples if s["split"] == "train"] * REPEAT
    samples += [{**s, "aspect": synth["aspect"], "norm": "hands"} for s in synth["samples"] if s["split"] == "train"]
    with open(out, "wb") as f:
        pickle.dump({"aspect": 1.0, "norm": "hands", "samples": samples}, f)
    for split in ("train", "test"):
        print(split, sum(s["split"] == split for s in samples))


if __name__ == "__main__":
    main(*sys.argv[1:4])
