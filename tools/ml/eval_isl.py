"""Top-1 and top-3 of the shipped ISL readers on held-out data: words (INCLUDE test split, sign_reader) and letters
and digits (synthetic test split, isl_spell; synthetic scores flatter real signers). Then a sentence demo: held-out
segments read one by one, top-1 each, written out as English and Hindi by glossToText's rules (TextSigning.kt).
The combined rows read each held-out segment through both models and the app's sign-or-spell gate (SignReaders.kt).
The real rows read ISLRTC dictionary signers (prepare_islrtc.py, evaluation only).
    python tools/ml/eval_isl.py [include.pkl] [isl_spell.pkl] [islrtc.pkl]
"""
import json
import pathlib
import pickle
import sys

import numpy as np

from prepare_islrtc import ACRONYMS, DIGITS
from runtime import reader, topk
from train import features

INCLUDE = "C:/Users/Yash/dev/ml-data/include_pose/include.pkl"
SPELL = "C:/Users/Yash/dev/ml-data/isl_spell/isl_spell.pkl"
ISLRTC = "C:/Users/Yash/dev/ml-data/islrtc/islrtc.pkl"
CONTENT = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content"

# Mirrors TextSigning.kt; keep the two in step.
TIME_SIGNS = {"sign.today", "sign.yesterday", "sign.tomorrow", "sign.now", "sign.morning", "sign.night", "sign.day", "sign.time"}
WHEN = {"when", "kab", "कब"}
QUESTION_SIGNS = {"ask", "sign.what", "sign.where", "sign.how", "sign.how_many"}
HINDI = {"what": "क्या", "where": "कहाँ", "who": "कौन", "why": "क्यों", "how": "कैसे", "which": "कौन सा", "when": "कब",
         "yesterday": "कल", "tomorrow": "कल", "today": "आज", "now": "अब", "morning": "सुबह", "night": "रात"}


def isl_rank(word: str, entry) -> int:
    sign = (entry or {}).get("sign")
    return 3 if sign in QUESTION_SIGNS or word in WHEN else 2 if sign == "no.shake" else 0 if sign in TIME_SIGNS else 1


def gloss_to_text(gloss: list[str], words: dict) -> tuple[str, str]:
    """glossToText: ISL order (time first, verb last, negation and question after it) to English and Hindi."""
    tokens: list[list] = []
    for g in (g.lower() for g in gloss if g.strip() and g != "?"):
        letter = len(g) == 1 and g.isalnum()
        if letter and tokens and tokens[-1][1]:
            tokens[-1][0] += g
        else:
            tokens.append([g, letter])
    rank = lambda t: 1 if t[1] else isl_rank(t[0], words.get(t[0]))
    time = [t for t in tokens if rank(t) == 0]
    rest = [t for t in tokens if rank(t) == 1]
    negated = any(rank(t) == 2 for t in tokens)
    ask = [t[0] for t in tokens if rank(t) == 3]
    question = bool(ask)
    en = lambda t: t[0].capitalize() if t[1] else (words.get(t[0], {}).get("w") or [t[0]])[0]
    verb_last = [en(t) for t in rest]
    if len(verb_last) >= 3:
        verb_last.insert(1, verb_last.pop())
    svo = ["I" if i == 0 and w in ("i", "me") else "me" if w == "i" else w for i, w in enumerate(verb_last)]
    aux = "did" if any(t[0] == "yesterday" for t in time) else "do"
    if negated:
        svo.insert(min(1, len(svo)), f"{aux} not")
    out = ask + ([aux] if question and not negated else []) + ([] if question else [en(t) for t in time]) + svo + \
        ([en(t) for t in time] if question else [])
    english = " ".join(out)
    english = english[:1].upper() + english[1:] + ("?" if question else ".")
    deva = lambda w: any("\u0900" <= c <= "\u097f" for c in w)
    hi = lambda t: t[0] if t[1] else HINDI.get(t[0]) or next((w for w in words.get(t[0], {}).get("w", []) if deva(w)), t[0])
    hi_rest = [hi(t) for t in rest]
    hindi = " ".join([hi(t) for t in time] + hi_rest[:-1] + [HINDI.get(a, a) for a in ask] + (["नहीं"] if negated else []) +
                     hi_rest[-1:]) + ("?" if question else "।")
    return english, hindi


def sentence_demo(include: str, spell: str) -> None:
    """Signs a sentence from held-out segments: words by sign_reader, a name by isl_spell, top-1 each."""
    words = {w: e for e in json.loads((CONTENT / "words.json").read_text(encoding="utf-8"))["words"] for w in e["w"]}
    sign_words = json.loads((CONTENT / "sign_words.json").read_text(encoding="utf-8"))["words"]
    plan = [("yesterday", include), ("r", spell), ("a", spell), ("m", spell), ("hospital", include)]
    caches = {p: pickle.load(open(p, "rb")) for p in {include, spell}}
    models = {include: reader("sign_reader"), spell: reader("isl_spell")}
    gloss = []
    for label, path in plan:
        cache = caches[path]
        s = next(s for s in cache["samples"] if s["split"] == "test" and s["label"] == label)
        spec, logits = models[path]
        top = spec["labels"][int(np.argmax(logits(featured(s, cache["aspect"], spec.get("norm"))[None].astype(np.float64))))]
        gloss.append(sign_words.get(top, top) if path == include else top.lower().removeprefix("letter.").removeprefix("digit."))
    got = gloss_to_text(gloss, words)
    expected = ("Yesterday Ram hospital.", "कल ram अस्पताल।")
    assert got == expected, f"sentence demo: gloss {gloss} gave {got}, expected {expected}"
    print(f"ISL sentence demo passed: {' '.join(gloss)} -> {got[0]} / {got[1]}")


SPELL_ODDS = 2.0  # SignReaders.kt


def held_out(cache_path: str, labels: list[str], keep=lambda label: True, norms=(None,)):
    """Feature batches (one per entry of [norms], each model's feature mode) and label indices of the test split,
    skipping labels outside [labels] and samples any mode cannot read."""
    with open(cache_path, "rb") as f:
        cache = pickle.load(f)
    index = {l: i for i, l in enumerate(labels)}
    test = [s for s in cache["samples"] if s["split"] == "test" and keep(s["label"]) and s["label"] in index]
    xs = [[featured(s, cache["aspect"], n) for s in test] for n in norms]
    ok = [i for i in range(len(test)) if all(x[i] is not None for x in xs)]
    y = np.array([index[test[i]["label"]] for i in ok])
    batches = [np.stack([x[i] for i in ok]).astype(np.float64) for x in xs]
    return (*batches, y)


def featured(s, aspect, norm):
    """A sample's features in a model's mode: "hands" for a hand-normalised reader, else the cache's own."""
    return features({**s, "norm": norm} if norm else s, aspect)


def probs(logits, x):
    z = np.concatenate([logits(x[i:i + 256]) for i in range(0, len(x), 256)])
    e = np.exp(z - z.max(1, keepdims=True))
    return e / e.sum(1, keepdims=True)


def score(model: str, cache_path: str, keep=lambda label: True):
    spec, logits = reader(model)
    x, y = held_out(cache_path, spec["labels"], keep, (spec.get("norm"),))
    return (*topk(probs(logits, x), y), len(y))


def combined(include: str, spell: str) -> None:
    """Word top-1 through the gate against word-only, and how often held-out letters and digits still read as spelled."""
    (ws, wl), (ss, sl) = reader("sign_reader"), reader("isl_spell")
    for what, path, labels in (("words", include, ws["labels"]), ("spelled", spell, ss["labels"])):
        xw, xs, y = held_out(path, labels, norms=(ws.get("norm"), ss.get("norm")))
        pw, ps = probs(wl, xw), probs(sl, xs)
        spelled = ps.max(1) > SPELL_ODDS * pw.max(1)
        if what == "words":
            alone, gated = (pw.argmax(1) == y).mean(), ((pw.argmax(1) == y) & ~spelled).mean()
            print(f"ISL combined words   top-1 {gated:.4f}  word-only {alone:.4f}  (n={len(y)}, gate costs {alone - gated:.4f})")
            assert alone - gated <= 0.02, "the sign-or-spell gate costs word reading more than 0.02 top-1"
        else:
            print(f"ISL combined spelled read as spelling {spelled.mean():.4f}, "
                  f"correctly {(spelled & (ps.argmax(1) == y)).mean():.4f}  (n={len(y)})")


def real_segments(path: str):
    """(label, sample, aspect) per letter or digit of the ISLRTC clips (prepare_islrtc.py). A take is a run of frames
    with a wrist above the chest; an acronym's take is split evenly among its letters, transitions included."""
    with open(path, "rb") as f:
        clips = pickle.load(f)
    out = []
    for c in clips:
        takes = 1 if c["name"] in DIGITS else ACRONYMS.get(c["name"])
        if not takes:
            continue
        k = np.array([1.0, 1.0 / c["aspect"]])
        pose = c["pose"] * k
        chest = (pose[:, 11, 1] + pose[:, 12, 1]) / 2 + 0.9 * np.linalg.norm(pose[:, 11] - pose[:, 12], axis=1)
        up = lambda hand, has: has & (c[hand][:, 0, 1] / c["aspect"] < chest)
        active = up("left", c["has_left"]) | up("right", c["has_right"])
        runs, start, gap = [], None, 0
        for t, a in enumerate(list(active) + [False] * 4):
            if a:
                start, gap = t if start is None else start, 0
            elif start is not None:
                gap += 1
                if gap > 3:
                    runs += [(start, t - gap + 1)] if t - gap + 1 - start >= 15 else []
                    start = None
        for a, b in runs[:takes]:
            cut = np.linspace(a, b, len(c["letters"]) + 1).round().astype(int)
            for ch, i, j in zip(c["letters"], cut, cut[1:]):
                out.append((ch, {key: c[key][i:j] for key in ("left", "right", "pose", "has_left", "has_right", "has_pose")},
                            c["aspect"]))
    return out


def real(islrtc: str, include: str) -> None:
    """isl_spell on real signers, and how the sign-or-spell gate treats them at a few odds against the word cost."""
    (ws, wl), (ss, sl) = reader("sign_reader"), reader("isl_spell")
    segs = real_segments(islrtc)
    index = {l: i for i, l in enumerate(ss["labels"])}
    segs = [g for g in segs if featured(g[1], g[2], ss.get("norm")) is not None and featured(g[1], g[2], ws.get("norm")) is not None]
    x = np.stack([featured(s, a, ss.get("norm")) for _, s, a in segs]).astype(np.float64)
    xr = np.stack([featured(s, a, ws.get("norm")) for _, s, a in segs]).astype(np.float64)
    y = np.array([index[l] for l, _, _ in segs])
    ps, pw = probs(sl, x), probs(wl, xr)
    xw, xws, yw = held_out(include, ws["labels"], norms=(ws.get("norm"), ss.get("norm")))
    pww, psw = probs(wl, xw), probs(sl, xws)
    for what, keep in (("letters", str.isalpha), ("digits", str.isdigit)):
        m = np.array([keep(l) for l, _, _ in segs])
        top1, top3 = topk(ps[m], y[m])
        print(f"ISL real {what:8s} top-1 {top1:.4f}  top-3 {top3:.4f}  (n={m.sum()}, ISLRTC signers, isl_spell)")
    for odds in (1.5, SPELL_ODDS, 3.0, 4.0):
        spelled = ps.max(1) > odds * pw.max(1)
        cost = ((pww.argmax(1) == yw) & (psw.max(1) > odds * pww.max(1))).mean()
        print(f"ISL real gate odds {odds:.1f}: spelled {spelled.mean():.4f}, correctly {(spelled & (ps.argmax(1) == y)).mean():.4f}"
              f"  (n={len(y)}); INCLUDE words lost {cost:.4f}")


def main(include: str, spell: str, islrtc: str = ISLRTC) -> None:
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
    try:
        combined(include, spell)
    except FileNotFoundError as e:
        print(f"ISL combined skipped: {e.filename} missing")
    try:
        real(islrtc, include)
    except FileNotFoundError as e:
        print(f"ISL real skipped: {e.filename} missing")
    sentence_demo(include, SPELL)  # the wiring, on synthetic letters; real accuracy is the rows above


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")  # the Hindi line fails on a cp1252 console
    a = sys.argv[1:]
    main(a[0] if a else INCLUDE, a[1] if len(a) > 1 else SPELL, a[2] if len(a) > 2 else ISLRTC)
