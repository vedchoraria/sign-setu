"""Checks the content bundle before it ships: ids, references and language coverage.

    python tools/content/check_content.py

Unlike `build_content.py --check`, which compares SHA-256 hashes, this reads the JSON and verifies that
every card, sign, gesture and group a question, word or phrase points at actually exists; that ids are
unique; that every card has its WebP; and that every subject-facing label is present in all 17 languages.
Exit 1 with a list of errors, or print a summary and exit 0. No file is written.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "content"


def load(assets: pathlib.Path, name: str) -> dict:
    return json.loads((assets / name).read_text(encoding="utf-8"))


def check(assets: pathlib.Path = ASSETS) -> list[str]:
    errors: list[str] = []

    cards_file = load(assets, "cards.json")
    triage = load(assets, "triage.json")
    words_file = load(assets, "words.json")
    phrases_file = load(assets, "phrases.json")
    languages = [entry["code"] for entry in load(assets, "languages.json")["languages"]]
    subject_ui = load(assets, "subject_ui.json")
    motion = set()
    for name in ("gestures.json", "signs.json", "isl_signs.json"):
        motion |= {g["id"] for g in load(assets, name)["gestures"]}

    def unique(where: str, ids: list[str]) -> set[str]:
        seen, duplicated = set(), set()
        for i in ids:
            if i in seen:
                duplicated.add(i)
            seen.add(i)
        if duplicated:
            errors.append(f"{where}: duplicate ids {sorted(duplicated)}")
        return seen

    cards = unique("cards.json", [c["id"] for c in cards_file["cards"]])
    groups = unique("triage.json groups", [g["id"] for g in triage["groups"]])
    questions = unique("triage.json questions", [q["id"] for q in triage["questions"]])
    unique("words.json", [w["w"][0] for w in words_file["words"] if w.get("w")])
    unique("isl_signs.json", [g["id"] for g in load(assets, "isl_signs.json")["gestures"]])

    def label(where: str, values, allowed: set[str]) -> None:
        missing = sorted(allowed - set(values))
        extra = sorted(set(values) - allowed)
        blank = sorted(k for k, v in values.items() if not str(v).strip())
        if missing:
            errors.append(f"{where}: missing languages {missing}")
        if extra:
            errors.append(f"{where}: unknown languages {extra}")
        if blank:
            errors.append(f"{where}: blank labels {blank}")

    allowed = set(languages)
    for card in cards_file["cards"]:
        label(f"card {card['id']}", card.get("label", {}), allowed)
        if not (assets / "cards" / f"{card['id']}.webp").is_file():
            errors.append(f"card {card['id']}: missing cards/{card['id']}.webp")
    for category in cards_file["categories"]:
        label(f"card category {category['id']}", category.get("label", {}), allowed)
    for group in triage["groups"]:
        label(f"group {group['id']}", group.get("label", {}), allowed)
    for question in triage["questions"]:
        label(f"question {question['id']}", question.get("text", {}), allowed)
        if question.get("group") not in groups:
            errors.append(f"question {question['id']}: unknown group {question.get('group')!r}")
        for card in question.get("cards", []):
            if card not in cards:
                errors.append(f"question {question['id']}: unknown card {card!r}")
        for key in ("gesture", "lexicon"):
            if question.get(key) and question[key] not in motion:
                errors.append(f"question {question['id']}: unknown {key} {question[key]!r}")
        if question.get("after"):
            # "s2:yes" or "s2:any": the parent and the answer that must hold before this is asked.
            parts = str(question["after"]).split(":")
            if len(parts) != 2 or not all(parts):
                errors.append(f"question {question['id']}: malformed after {question['after']!r}")
            elif parts[0] not in questions:
                errors.append(f"question {question['id']}: unknown after question {parts[0]!r}")
    for phrase in phrases_file["phrases"]:
        label(f"phrase {phrase['id']}", phrase.get("label", {}), allowed)
        for sign in phrase.get("signs", []):
            if sign != "$" and sign not in motion:
                errors.append(f"phrase {phrase['id']}: unknown sign {sign!r} (use $ for the slot)")
        for slot in phrase.get("slots", []):
            label(f"phrase {phrase['id']} slot {slot.get('card', '?')}", slot.get("text", {}), allowed)
            if slot.get("card") and slot["card"] not in cards:
                errors.append(f"phrase {phrase['id']}: unknown slot card {slot['card']!r}")
            if slot.get("sign") and slot["sign"] not in motion:
                errors.append(f"phrase {phrase['id']}: unknown slot sign {slot['sign']!r}")
    for word in words_file["words"]:
        if not word.get("w"):
            errors.append("word entry has no written forms")
            continue
        name = word["w"][0]
        for key in ("sign", "gesture"):
            if word.get(key) and word[key] not in motion:
                errors.append(f"word {name!r}: unknown {key} {word[key]!r}")
        if word.get("card") and word["card"] not in cards:
            errors.append(f"word {name!r}: unknown card {word['card']!r}")
    for key, values in subject_ui.items():
        if isinstance(values, dict) and set(values) & allowed:
            label(f"subject_ui {key}", values, allowed)
    return errors


if __name__ == "__main__":
    problems = check()
    if problems:
        print(f"{len(problems)} content error(s):", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        sys.exit(1)
    print("content references, ids, card images and 17-language labels are consistent")
