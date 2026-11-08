"""Validate and summarize the explicit feature/evidence ledger.

The ledger is deliberately declarative: code or a passing unit test never
silently upgrades a feature to ``accepted``.  A maintainer records the status
and points at reproducible evidence after the corresponding test layer runs.
"""
from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / "docs/feature-ledger.json"
STATUSES = {"not_started", "implemented", "unit_tested", "runtime_tested",
            "client_tested", "accepted", "blocked"}
PARITY = {"A", "B", "C"}


def load(path: Path = LEDGER) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1 or set(data.get("statuses", [])) != STATUSES:
        raise ValueError("feature ledger schema/status list is invalid")
    features = data.get("features")
    if not isinstance(features, list) or not features:
        raise ValueError("feature ledger has no features")
    seen: set[str] = set()
    for feature in features:
        if not isinstance(feature, dict):
            raise ValueError("feature entry must be an object")
        required = {"feature_id", "module", "title", "parity_level", "status", "source_ids", "evidence"}
        if set(feature) != required:
            raise ValueError(f"feature {feature.get('feature_id', '<unknown>')} has unsupported fields")
        feature_id = feature["feature_id"]
        if not isinstance(feature_id, str) or not feature_id or feature_id in seen:
            raise ValueError(f"duplicate or invalid feature id: {feature_id!r}")
        if feature["status"] not in STATUSES or feature["parity_level"] not in PARITY:
            raise ValueError(f"invalid status/parity for {feature_id}")
        for field in ("source_ids", "evidence"):
            values = feature[field]
            if not isinstance(values, list) or not values or any(not isinstance(v, str) or not v for v in values):
                raise ValueError(f"{feature_id}: {field} must be a non-empty string list")
        seen.add(feature_id)
    return data


def summary(data: dict[str, Any]) -> dict[str, Any]:
    features = data["features"]
    return {
        "features": len(features),
        "by_status": dict(sorted(Counter(f["status"] for f in features).items())),
        "by_module": dict(sorted(Counter(f["module"] for f in features).items())),
        "accepted": [f["feature_id"] for f in features if f["status"] == "accepted"],
        "blocked": [f["feature_id"] for f in features if f["status"] == "blocked"],
        "policy": "accepted is explicit and is never inferred from source presence or unit tests",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ledger", type=Path, default=LEDGER)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = summary(load(args.ledger))
    text = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text, encoding="utf-8")
    print(text, end="")


if __name__ == "__main__":
    main()
