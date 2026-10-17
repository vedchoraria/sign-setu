"""Writes the content review record: every gesture, phrase sentence and placeholder recording, ready for sign-off.

Regenerate after any content change, then commit the record with the change:
    python tools/content/review_record.py

With --reviews, the verdicts and notes exported from the reviewer portal (portal/) fill the record:
    python tools/content/review_record.py --reviews content-review.json
"""
import argparse
import collections
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "content"
OUT = ROOT / "docs" / "governance" / "forms" / "content-review-record.md"
WELFARE = "Child-welfare reviewer"

# Gestures that carry no lexicon meaning of their own: their role on the stage.
ROLES = {
    "okay": "Acknowledgement after any answer: a nod with \"okay\"",
    "namaste": "Greeting on the home screen and at the start of a session",
    "wave": "Hello, with the language finder",
    "ask": "General asking pose (palms up, raised brows) for typed questions and pictures",
    "show_me": "\"Show me\"",
    "thumbs_up": "\"Good\"",
    "count": "Counting on the fingers (age question)",
    "sick.forehead": "Feeling unwell: hand to the forehead",
    "medicine.pill": "Medicine: taking a pill",
}


def role(gid, meanings):
    if gid.startswith("letter."):
        return f"Fingerspelling: letter {gid[-1].upper()}"
    if gid.startswith("digit."):
        return f"Number {gid[-1]}"
    return f"\"{meanings[gid]}\"" if gid in meanings else ""


def load(name):
    return json.loads((ASSETS / name).read_text(encoding="utf-8"))


def cell(text):
    return text.replace("|", "\\|").replace("\r", " ").replace("\n", " ")


class Reviews:
    """Portal verdicts by (section, id). Sections: gesture, phrase ("<phrase id>.<card>"), question, ui, greeting."""

    def __init__(self, path=None):
        self.by_item = collections.defaultdict(list)
        if path is None:
            return
        data = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
        if data.get("format") != "signsetu-content-review":
            raise SystemExit(f"{path}: not a reviewer portal export")
        for r in data["reviews"]:
            self.by_item[(r["section"], r["id"])].append(r)

    def verdict(self, section, item, role=None):
        """Verdict letters for an item: those of one role, or every role's with the role named."""
        found = [r for r in self.by_item[(section, item)] if r.get("verdict") and role in (None, r["role"])]
        return ", ".join(r["verdict"] if role else f"{r['verdict']} ({r['role']})" for r in found)

    def notes(self, section, item):
        return cell("; ".join(f"{r['role']}: {r['notes'].strip()}" for r in self.by_item[(section, item)] if r["notes"].strip()))


def audio_item(folder, stem, question_ids):
    """The portal item an audio file belongs to."""
    if folder == "greet":
        return "greeting", stem
    if stem.startswith("phrase."):
        return "phrase", stem.removeprefix("phrase.")
    return ("question" if stem in question_ids else "ui"), stem


def main():
    parser = argparse.ArgumentParser(description="Writes the content review record.")
    parser.add_argument("--reviews", help="JSON exported from the reviewer portal")
    parser.add_argument("--out", type=pathlib.Path, default=OUT, help="where to write the record")
    args = parser.parse_args()
    reviews = Reviews(args.reviews)
    # Recorded sign files are optional: asl_signs.json only exists once ASL content is built.
    sign_files = [name for name in ("isl_signs.json", "asl_signs.json") if (ASSETS / name).exists()]
    gestures = [g["id"] for name in ("gestures.json", "signs.json") for g in load(name)["gestures"]]
    gestures += [g["id"] for name in sign_files for g in load(name)["gestures"]]
    caveats = {g["id"]: "; ".join(t for f, t in (("static", "one held keyframe"),
               ("one_hand_source", "two-handed sign drawn with the right hand only")) if g.get(f))
               for name in sign_files for g in load(name)["gestures"]}
    words = load("words.json")["words"]
    meanings = {}
    for w in words:
        for gid in {w.get("sign"), w.get("gesture")} - {None}:
            meanings.setdefault(gid, w["w"][0])
    for name in sign_files:
        for g in load(name)["gestures"]:
            meanings.setdefault(g["id"], g["word"])
    lexicon = {e["id"]: e for e in load("lexicon.json")["entries"]}
    phrases = load("phrases.json")["phrases"]
    audio_files = sorted((ASSETS / "audio").rglob("*.ogg"))
    audio = collections.Counter(p.parent.name for p in audio_files)
    questions = load("triage.json")["questions"]
    english = {lang["code"]: lang["english"] for lang in load("languages.json")["languages"]} | {"en": "English"}

    out = ["# Content Review Record", "",
           "Sign-off sheet for the subject-facing content in this release, generated from the content files by "
           "`tools/content/review_record.py`. Reviewers follow the [content policy](../policies/content-policy.md); "
           "every item stays a candidate until it is approved here.", "",
           "Verdicts: **A** approved, **C** change needed (say what in the notes), **R** reject.", "",
           "## 1. Gestures", "",
           "Seen in the gesture gallery of a debug build, frozen at the moment of contact:", "",
           "```",
           "adb shell am start -n in.signsetu/app.signsetu.GalleryActivity --es only <ids, comma separated>",
           "adb shell am start -n in.signsetu/app.signsetu.GalleryActivity --es c adult --es only <ids>",
           "```", "",
           "Reviewers: child-welfare reviewer for every gesture; ISL reviewer as well for entries a Deaf subject may see.", "",
           "| Gesture | Meaning | How it is made | Child | Adult | Notes |",
           "| --- | --- | --- | --- | --- | --- |"]
    for gid in gestures:
        entry = lexicon.get(gid)
        meaning = entry["label"]["en"] if entry else ROLES.get(gid) or role(gid, meanings)
        how = entry["how"]["en"] if entry else caveats.get(gid, "")
        # The portal plays each gesture on both characters at once, so one verdict fills both columns.
        v = reviews.verdict("gesture", gid)
        out.append(f"| `{gid}` | {cell(meaning)} | {cell(how)} | {v} | {v} | {reviews.notes('gesture', gid)} |")

    out += ["", "## 2. Phrase-bank sentences", "",
            "Each sentence is signed as the sequence shown, with its picture held up. Reviewers: language reviewer for "
            "each language, ISL reviewer for the sign sequence, child-welfare reviewer for the wording.", "",
            "| Sentence (English) | Sentence (Hindi) | Signs | English | Hindi | Signs | Notes |",
            "| --- | --- | --- | --- | --- | --- | --- |"]
    for p in phrases:
        for slot in p["slots"]:
            signs = ", ".join(f"`{slot['sign'] if s == '$' else s}`" for s in p["signs"])
            item = ("phrase", f"{p['id']}.{slot['card']}")
            en, hi, isl, welfare = (reviews.verdict(*item, role=r) for r in
                                    ("Language reviewer (English)", "Language reviewer (Hindi)", "ISL reviewer", WELFARE))
            notes = "; ".join(filter(None, [welfare and f"Child welfare: {welfare}", reviews.notes(*item)]))
            out.append(f"| {cell(slot['text'].get('en', ''))} | {cell(slot['text'].get('hi', ''))} | {signs} | "
                       f"{en} | {hi} | {isl} | {notes} |")

    out += ["", "## 3. Typed-word vocabulary", "",
            "Typed words the character signs, gestures or pictures (`words.json`, D-22). Reviewers: language reviewer "
            "for the written forms of their language, ISL reviewer for the sign column.", "",
            "| Written forms | Sign | Gesture | Picture | Language | ISL | Notes |",
            "| --- | --- | --- | --- | --- | --- | --- |"]
    for w in words:
        sign, gesture, card = (f"`{w[k]}`" if w.get(k) else "" for k in ("sign", "gesture", "card"))
        out.append(f"| {cell(', '.join(w['w']))} | {sign} | {gesture} | {card} |  |  |  |")

    out += ["", "## 4. Placeholder recordings", "",
            "Every file under `assets/content/audio/` is machine-generated placeholder speech until a fluent speaker "
            "records it again or approves it. The language reviewer listens to each file in the folder for their language.", "",
            "| Language folder | Files | Approved by | Notes |",
            "| --- | --- | --- | --- |"]
    question_ids = {q["id"] for q in questions}
    for lang, count in sorted(audio.items()):
        items = [audio_item(lang, f.stem, question_ids) for f in audio_files if f.parent.name == lang]
        if lang == "greet":
            approved = [i for s, i in items if reviews.verdict(s, i, role=f"Language reviewer ({english.get(i, i)})") == "A"]
            by = f"{len(approved)} of {count} approved by their language's reviewer" if approved else ""
        else:
            reviewer = f"Language reviewer ({english.get(lang, lang)})"
            approved = sum(reviews.verdict(s, i, role=reviewer) == "A" for s, i in items)
            by = f"{reviewer}: {approved} of {count} approved" if approved else ""
        note = "One greeting per language-finder language; each goes to that language's reviewer" if lang == "greet" else ""
        if lang == "greet":
            note = "; ".join(filter(None, [note] + [reviews.notes(s, i) for s, i in items]))
        out.append(f"| `{lang}` | {count} | {by} | {note} |")

    out += ["", "## 5. Prepared questions and subject screen words", "",
            "The officer's prepared questions (`triage.json`) and the words on the subject's screen (`subject_ui.json`). "
            "Reviewers: child-welfare reviewer for the wording, language reviewer for each language.", "",
            "| Item | English | Hindi | Child welfare | English | Hindi | Notes |",
            "| --- | --- | --- | --- | --- | --- | --- |"]
    rows = [("question", q["id"], q["text"]) for q in questions]
    rows += [("ui", key, text) for key, text in load("subject_ui.json").items()]
    for section, item, text in rows:
        welfare, en, hi = (reviews.verdict(section, item, role=r) for r in
                           (WELFARE, "Language reviewer (English)", "Language reviewer (Hindi)"))
        out.append(f"| `{item}` | {cell(text.get('en', ''))} | {cell(text.get('hi', ''))} | {welfare} | {en} | {hi} | "
                   f"{reviews.notes(section, item)} |")

    out += ["", "## 6. Sign-off", "",
            "| Role | Name | Organisation | Signature | Date |",
            "| --- | --- | --- | --- | --- |",
            "| Child-welfare reviewer |  |  |  |  |",
            "| ISL reviewer |  |  |  |  |",
            "| Language reviewer (Hindi) |  |  |  |  |",
            "| Language reviewer (English) |  |  |  |  |",
            "| Language reviewer (Chhattisgarhi) |  |  |  |  |",
            "| Language reviewer (Gondi) |  |  |  |  |",
            "| Language reviewer (Halbi) |  |  |  |  |",
            "| Content owner |  |  |  |  |", ""]
    args.out.write_text("\n".join(out), encoding="utf-8", newline="\n")
    print(f"{args.out}: {len(gestures)} gestures, "
          f"{sum(len(p['slots']) for p in phrases)} sentences, {sum(audio.values())} recordings")


if __name__ == "__main__":
    main()
