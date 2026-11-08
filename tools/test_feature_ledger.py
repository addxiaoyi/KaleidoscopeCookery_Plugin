import json
import tempfile
import unittest
from pathlib import Path

from feature_ledger import load, summary


class FeatureLedgerTests(unittest.TestCase):
    def test_repository_ledger_is_valid_and_does_not_infer_acceptance(self):
        data = load()
        report = summary(data)
        self.assertGreater(report["features"], 0)
        self.assertTrue(set(report["accepted"]).issubset({f["feature_id"] for f in data["features"]}))
        self.assertIn("blocked", report["by_status"])

    def test_duplicate_feature_ids_are_rejected(self):
        data = load()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "ledger.json"
            data["features"].append(dict(data["features"][0]))
            path.write_text(json.dumps(data), encoding="utf-8")
            with self.assertRaises(ValueError):
                load(path)


if __name__ == "__main__":
    unittest.main()
