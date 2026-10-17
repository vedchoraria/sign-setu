"""Scores the shipped ASL readers: word top-1 and top-3 (PopSign test signers, asl_reader) and fingerspelling
character error rate (sequences from held-out ASLNow! letters, asl_spell; synthetic, so it flatters real signing).
    python tools/ml/eval_asl.py [asl.pkl] [asl_spell_dir]
"""
import pathlib
import sys

from eval_isl import score
from runtime import speller
from spell_ctc import DATA, cer, greedy, load

ASL = "C:/Users/Yash/dev/ml-data/popsign/asl.pkl"


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


if __name__ == "__main__":
    a = sys.argv[1:]
    main(a[0] if a else ASL, pathlib.Path(a[1]) if len(a) > 1 else DATA)
