import hashlib
import importlib.util
import json
import pathlib
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from unittest import mock

SPEC = importlib.util.spec_from_file_location("build_content", pathlib.Path(__file__).resolve().parent / "build_content.py")
build = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(build)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class ManifestCheckTest(unittest.TestCase):
    """--check is what CI runs, so it must report drift exactly and never touch the working tree."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.assets = pathlib.Path(self.tmp.name)
        (self.assets / "a.json").write_text('{"a": 1}', encoding="utf-8")
        (self.assets / "cards").mkdir()
        (self.assets / "cards" / "x.webp").write_bytes(b"picture")
        self.patch = mock.patch.object(build, "ASSETS", self.assets)
        self.patch.start()

    def tearDown(self):
        self.patch.stop()
        self.tmp.cleanup()

    def run_quietly(self, function, *args):
        out = StringIO()
        with redirect_stdout(out):
            result = function(*args)
        return result, out.getvalue()

    def manifest(self) -> dict:
        return json.loads((self.assets / "manifest.json").read_text(encoding="utf-8"))

    def test_the_written_manifest_lists_every_file_by_hash_and_check_accepts_it(self):
        self.run_quietly(build.write_manifest)
        self.assertEqual(self.manifest(), {"version": 1, "files": {
            "a.json": digest(b'{"a": 1}'),
            "cards/x.webp": digest(b"picture"),
        }})
        ok, out = self.run_quietly(build.check_manifest)
        self.assertTrue(ok)
        self.assertIn("manifest is current: 2 files", out)

    def test_check_reports_added_changed_and_removed_files_without_writing(self):
        self.run_quietly(build.write_manifest)
        before = (self.assets / "manifest.json").read_bytes()
        (self.assets / "b.json").write_text("{}", encoding="utf-8")

        ok, out = self.run_quietly(build.check_manifest)
        self.assertFalse(ok)
        self.assertIn("added: b.json", out)
        self.assertEqual((self.assets / "manifest.json").read_bytes(), before)

        (self.assets / "a.json").write_text('{"a": 2}', encoding="utf-8")
        _, out = self.run_quietly(build.check_manifest)
        self.assertIn("changed: a.json", out)

        (self.assets / "b.json").unlink()
        _, out = self.run_quietly(build.check_manifest)
        self.assertIn("added: -", out)
        self.assertIn("changed: a.json", out)
        self.assertEqual((self.assets / "manifest.json").read_bytes(), before)

    def test_check_does_not_normalise_line_endings_or_repair_a_missing_manifest(self):
        crlf = b'{\r\n "a": 1\r\n}'
        (self.assets / "a.json").write_bytes(crlf)
        self.run_quietly(build.write_manifest)
        (self.assets / "a.json").write_bytes(crlf)
        self.assertTrue((self.assets / "a.json").read_bytes().count(b"\r\n") == 2)

        ok, _ = self.run_quietly(build.check_manifest)
        self.assertFalse(ok)
        self.assertEqual((self.assets / "a.json").read_bytes().count(b"\r\n"), 2)

        (self.assets / "manifest.json").unlink()
        ok, out = self.run_quietly(build.check_manifest)
        self.assertFalse(ok)
        self.assertIn("missing or unreadable", out)
        self.assertFalse((self.assets / "manifest.json").exists())


if __name__ == "__main__":
    unittest.main()
