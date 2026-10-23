"""Tracked files must not carry a developer's absolute home path.

The workspace moved from C:\\Users\\<name>\\dev to C:\\work; a checked-in path from the old tree would break
the build for everyone else and for CI. This scans git's tracked text files, so ignored build output and
the repository's own .git folder are not considered.
"""

import pathlib
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SELF = "tools/security/test_paths.py"  # it names the patterns it looks for
PATTERNS = ("C:\\Users\\", "C:/Users/", "/c/Users/", "C:\\work\\")


def tracked_files():
    out = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
        check=True,
        capture_output=True,
    ).stdout
    return [name for name in out.decode("utf-8", "replace").split("\0") if name]


class NoAbsoluteDeveloperPathsTest(unittest.TestCase):
    def test_no_tracked_file_contains_an_absolute_developer_path(self):
        offenders = []
        for name in tracked_files():
            if name == SELF:
                continue
            path = ROOT / name
            if not path.is_file():
                continue
            try:
                text = path.read_text(encoding="utf-8")
            except (UnicodeDecodeError, OSError):
                continue  # binary assets are not source code
            for number, line in enumerate(text.splitlines(), start=1):
                if any(pattern in line for pattern in PATTERNS):
                    offenders.append(f"{name}:{number}")
        self.assertEqual([], offenders, "absolute developer paths in tracked files")


if __name__ == "__main__":
    unittest.main()
