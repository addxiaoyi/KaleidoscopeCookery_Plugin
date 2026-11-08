"""Inventory upstream license evidence without making a legal determination.

The source lock is authoritative for repository names and commits.  This tool
only records root license/notice files and SPDX/copyright markers.  It marks
every result ``review_required`` until a maintainer confirms that each code and
asset class may be redistributed in the generated plugin and CE packs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "docs/research/organization-checkouts.json"
POLICY = ROOT / "docs/license-policy.json"
LICENSE_NAMES = {"license", "licence", "copying", "notice", "license.md", "copying.md", "notice.md",
                 "license.txt", "copying.txt", "notice.txt"}
MARKERS = re.compile(r"(?i)\b(?:spdx-license-identifier|copyright|license|licensed under)\b")


class LicenseAuditError(ValueError):
    pass


def git(root: Path, *args: str, input: bytes | None = None) -> bytes:
    result = subprocess.run(["git", "-C", str(root), *args], input=input,
                            capture_output=True, check=False, timeout=60)
    if result.returncode:
        raise LicenseAuditError(result.stderr.decode("utf-8", errors="replace").strip() or
                                 f"git {' '.join(args)} failed")
    return result.stdout


def load_policy(path: Path = POLICY) -> dict[str, Any]:
    policy = json.loads(path.read_text(encoding="utf-8"))
    if policy.get("schema_version") != 1 or not isinstance(policy.get("repositories"), list):
        raise LicenseAuditError("invalid license policy schema")
    names = [entry.get("name") for entry in policy["repositories"]]
    if len(names) != len(set(names)) or any(not isinstance(name, str) or not name for name in names):
        raise LicenseAuditError("license policy has duplicate or invalid repository names")
    if any(entry.get("redistribution") != "review_required" for entry in policy["repositories"]):
        raise LicenseAuditError("unreviewed redistribution status must not be inferred")
    return policy


def _license_paths(root: Path, commit: str) -> list[str]:
    paths = git(root, "ls-tree", "-r", "--name-only", commit).decode("utf-8", errors="strict").splitlines()
    result: list[str] = []
    for path in paths:
        basename = Path(path).name.lower()
        if (basename in LICENSE_NAMES or basename.startswith(("license-", "license.", "licence-", "licence.",
                                                               "copying-", "copying.", "notice-", "notice."))):
            result.append(path)
    return sorted(result)


def _read_blobs(root: Path, commit: str, paths: list[str]) -> dict[str, bytes]:
    if not paths:
        return {}
    request = b"".join(f"{commit}:{path}\n".encode() for path in paths)
    raw = git(root, "cat-file", "--batch", input=request)
    offset = 0
    output: dict[str, bytes] = {}
    for path in paths:
        end = raw.find(b"\n", offset)
        header = raw[offset:end].split()
        if len(header) != 3 or header[1] != b"blob":
            raise LicenseAuditError(f"invalid license blob response for {path}")
        start = end + 1
        stop = start + int(header[2])
        output[path] = raw[start:stop]
        if raw[stop:stop + 1] != b"\n":
            raise LicenseAuditError(f"truncated license blob response for {path}")
        offset = stop + 1
    return output


def audit(lock: Path = LOCK, references: Path | None = None,
          policy: Path = POLICY) -> dict[str, Any]:
    lock_data = json.loads(lock.read_text(encoding="utf-8"))
    policy_data = load_policy(policy)
    pinned = {entry["name"]: entry for entry in lock_data.get("repositories", [])}
    policy_names = {entry["name"] for entry in policy_data["repositories"]}
    if set(pinned) != policy_names:
        raise LicenseAuditError("license policy and source lock repositories differ")
    reference_root = (references or ROOT.parent / ".references").resolve()
    rows: list[dict[str, Any]] = []
    for entry in policy_data["repositories"]:
        name = entry["name"]
        checkout = reference_root / name
        row: dict[str, Any] = {**entry, "commit": pinned[name]["commit"],
                               "checkout": str(checkout), "status": "missing_checkout", "files": []}
        if not checkout.is_dir():
            rows.append(row)
            continue
        head = git(checkout, "rev-parse", "HEAD").decode().strip()
        dirty = git(checkout, "status", "--porcelain", "--untracked-files=no").decode().strip()
        if head != row["commit"]:
            row["status"] = "commit_mismatch"
            row["actual_commit"] = head
            rows.append(row)
            continue
        if dirty:
            row["status"] = "dirty_checkout"
            rows.append(row)
            continue
        paths = _license_paths(checkout, head)
        blobs = _read_blobs(checkout, head, paths)
        row["files"] = [{"path": path, "sha256": hashlib.sha256(raw).hexdigest(),
                         "markers": sorted(set(marker.lower() for marker in MARKERS.findall(raw.decode("utf-8", errors="replace"))))}
                        for path, raw in blobs.items()]
        row["status"] = "review_required" if paths else "license_file_missing"
        rows.append(row)
    return {"schema_version": 1, "generated_by": "tools/audit_licenses.py",
            "scope": "license evidence inventory; human redistribution review required",
            "reference_root": str(reference_root), "repositories": rows}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--references", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--strict", action="store_true", help="fail if a checkout or license file is unresolved")
    args = parser.parse_args()
    try:
        report = audit(references=args.references)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        unresolved = [row for row in report["repositories"] if row["status"] != "review_required"]
        if args.strict and unresolved:
            raise LicenseAuditError("unresolved license evidence: " + ", ".join(
                f"{row['name']}={row['status']}" for row in unresolved))
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print(f"License audit failed: {error}", file=sys.stderr)
        return 1
    print(json.dumps({"output": str(args.output), "statuses": {
        status: sum(row["status"] == status for row in report["repositories"])
        for status in sorted({row["status"] for row in report["repositories"]})}},
        ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
