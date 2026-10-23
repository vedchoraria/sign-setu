import json
import pathlib
import tempfile
import unittest

import check_content


class ContentCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.assets = pathlib.Path(self.tmp.name)
        (self.assets / "cards").mkdir()
        (self.assets / "cards" / "yes.webp").write_bytes(b"webp")
        self.write("languages.json", {"languages": [{"code": "en"}, {"code": "hi"}]})
        self.write("cards.json", {
            "categories": [{"id": "answers", "label": {"en": "Answers", "hi": "जवाब"}}],
            "cards": [{"id": "yes", "category": "answers", "label": {"en": "Yes", "hi": "हाँ"}}],
        })
        self.write("triage.json", {
            "groups": [{"id": "safety", "label": {"en": "Safety", "hi": "सुरक्षा"}}],
            "questions": [{"id": "q1", "group": "safety", "text": {"en": "Are you hurt?", "hi": "क्या आपको चोट है?"},
                           "cards": ["yes"], "gesture": "yes.ok"}],
        })
        self.write("gestures.json", {"gestures": [{"id": "yes.ok"}]})
        self.write("signs.json", {"gestures": []})
        self.write("isl_signs.json", {"gestures": []})
        self.write("words.json", {"words": [{"w": ["yes"], "sign": "yes.ok", "card": "yes"}]})
        self.write("phrases.json", {"phrases": [{
            "id": "want", "label": {"en": "Do you want …?", "hi": "क्या आपको … चाहिए?"},
            "signs": ["yes.ok", "$"],
            "slots": [{"card": "yes", "sign": "yes.ok", "text": {"en": "Do you want water?", "hi": "क्या पानी चाहिए?"}}],
        }]})
        self.write("subject_ui.json", {"is_this_it": {"en": "Is this it?", "hi": "क्या यही है?"}})

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, name: str, value) -> None:
        (self.assets / name).write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")

    def read(self, name: str):
        return json.loads((self.assets / name).read_text(encoding="utf-8"))

    def test_a_consistent_bundle_has_no_errors(self):
        self.assertEqual(check_content.check(self.assets), [])

    def test_a_missing_card_image_is_reported(self):
        (self.assets / "cards" / "yes.webp").unlink()
        self.assertIn("card yes: missing cards/yes.webp", check_content.check(self.assets))

    def test_unknown_and_duplicate_references_are_reported(self):
        triage = self.read("triage.json")
        triage["questions"][0]["cards"] = ["ghost"]
        triage["questions"][0]["gesture"] = "ghost.sign"
        triage["questions"].append({"id": "q1", "group": "nowhere", "text": {"en": "x", "hi": "x"}})
        self.write("triage.json", triage)
        errors = check_content.check(self.assets)
        self.assertTrue(any("unknown card 'ghost'" in e for e in errors))
        self.assertTrue(any("unknown gesture 'ghost.sign'" in e for e in errors))
        self.assertTrue(any("unknown group 'nowhere'" in e for e in errors))
        self.assertTrue(any("duplicate ids ['q1']" in e for e in errors))

    def test_a_missing_language_in_a_subject_facing_label_is_reported(self):
        cards = self.read("cards.json")
        cards["cards"][0]["label"].pop("hi")
        self.write("cards.json", cards)
        self.assertIn("card yes: missing languages ['hi']", check_content.check(self.assets))

    def test_follow_up_questions_must_name_a_real_parent(self):
        triage = self.read("triage.json")
        triage["questions"][0]["after"] = "missing:yes"
        self.write("triage.json", triage)
        self.assertTrue(any("unknown after question 'missing'" in e for e in check_content.check(self.assets)))

        triage["questions"][0]["after"] = "q1"
        self.write("triage.json", triage)
        self.assertTrue(any("malformed after 'q1'" in e for e in check_content.check(self.assets)))

    def test_a_phrase_slot_must_point_at_a_real_card_and_sign(self):
        phrases = self.read("phrases.json")
        phrases["phrases"][0]["signs"] = ["ghost.sign", "$"]
        phrases["phrases"][0]["slots"][0]["card"] = "ghost"
        self.write("phrases.json", phrases)
        errors = check_content.check(self.assets)
        self.assertTrue(any("unknown sign 'ghost.sign'" in e for e in errors))
        self.assertTrue(any("unknown slot card 'ghost'" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
