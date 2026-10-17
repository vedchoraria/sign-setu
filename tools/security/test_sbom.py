import pathlib
import unittest

import sbom

LOCK = pathlib.Path(__file__).resolve().parents[2] / "app" / "gradle.lockfile"


class ShippedLinesTest(unittest.TestCase):
    def test_matches_any_flavor_case_insensitively(self):
        lines = ["a:b:1=standardReleaseRuntimeClasspath", "c:d:1=nearbyReleaseRuntimeClasspath",
                 "e:f:1=debugRuntimeClasspath", "g:h:1=lintClassPath"]
        self.assertEqual(sbom.shipped_lines(lines), lines[:2])

    def test_nearby_dependency_is_in_real_lockfile_scan(self):
        shipped = sbom.shipped_lines(LOCK.read_text(encoding="utf-8").splitlines())
        self.assertTrue(any("play-services-nearby" in line for line in shipped))


if __name__ == "__main__":
    unittest.main()
