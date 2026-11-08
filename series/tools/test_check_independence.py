import unittest
import tempfile
from pathlib import Path

from check_independence import _single_match, check


class IndependenceTests(unittest.TestCase):
    def test_source_metadata_is_independent(self):
        report = check()
        self.assertEqual([], report["errors"])
        self.assertEqual(4, report["modules"])

    def test_artifact_versions_are_discovered_without_a_version_constant(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "KaleidoscopeDoll-2.0.0.jar"
            first.write_bytes(b"jar")
            path, error = _single_match((root,), "KaleidoscopeDoll-*.jar")
            self.assertEqual(first, path)
            self.assertIsNone(error)
            (root / "KaleidoscopeDoll-2.1.0.jar").write_bytes(b"jar")
            path, error = _single_match((root,), "KaleidoscopeDoll-*.jar")
            self.assertIsNone(path)
            self.assertIn("ambiguous", error)


if __name__ == "__main__":
    unittest.main()
