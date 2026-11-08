import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from audit_licenses import audit


class LicenseAuditTests(unittest.TestCase):
    def test_missing_reference_root_is_reported_without_claiming_clearance(self):
        with tempfile.TemporaryDirectory() as directory:
            report = audit(references=Path(directory) / "references")
        self.assertEqual(8, len(report["repositories"]))
        self.assertTrue(all(row["status"] == "missing_checkout" for row in report["repositories"]))

    def test_pinned_license_file_is_hashed_from_git_blob(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "references" / "Fixture"
            root.mkdir(parents=True)
            def run(*args: str) -> str:
                return subprocess.run(["git", "-C", str(root), *args], check=True,
                                      capture_output=True, text=True).stdout.strip()
            run("init", "-q")
            (root / "LICENSE").write_text("Copyright Fixture\n", encoding="utf-8")
            run("add", "LICENSE")
            run("-c", "user.name=License Test", "-c", "user.email=license@example.invalid",
                "commit", "-qm", "fixture")
            commit = run("rev-parse", "HEAD")
            lock = Path(directory) / "lock.json"
            policy = Path(directory) / "policy.json"
            lock.write_text(json.dumps({"repositories": [{"name": "Fixture", "commit": commit}]}), encoding="utf-8")
            policy.write_text(json.dumps({"schema_version": 1, "repositories": [
                {"name": "Fixture", "role": "test", "redistribution": "review_required"}]}), encoding="utf-8")
            report = audit(lock, Path(directory) / "references", policy)
        self.assertEqual("review_required", report["repositories"][0]["status"])
        self.assertEqual("LICENSE", report["repositories"][0]["files"][0]["path"])


if __name__ == "__main__":
    unittest.main()
