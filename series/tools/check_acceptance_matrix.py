"""Validate the machine-readable isolated server acceptance matrix."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

MATRIX = Path(__file__).resolve().parents[1] / "docs/acceptance-matrix.json"
MODULES = {"cookery", "doll", "tavern", "deco", "music"}
LEVELS = {f"L{i}" for i in range(1, 8)}
STATUSES = {"not_started", "static-only", "partial", "runtime-tested", "client-tested", "accepted", "blocked"}


def load(path: Path = MATRIX) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1:
        raise ValueError("unsupported acceptance matrix schema")
    if set(data.get("levels", {})) != LEVELS:
        raise ValueError("acceptance matrix must define L1 through L7")
    rows = data.get("modules")
    if not isinstance(rows, list) or len(rows) != len(MODULES) or {row.get("module") for row in rows} != MODULES:
        raise ValueError("acceptance matrix must contain exactly five modules")
    for row in rows:
        if set(row) != {"module", "plugin", "pack", "required_levels", "status", "evidence", "open"}:
            raise ValueError(f"invalid fields for {row.get('module')}")
        if row["status"] not in STATUSES:
            raise ValueError(f"invalid status for {row['module']}")
        if not row["required_levels"] or any(level not in LEVELS for level in row["required_levels"]):
            raise ValueError(f"invalid required levels for {row['module']}")
        if not row["evidence"] or not row["open"]:
            raise ValueError(f"missing evidence/open items for {row['module']}")
    return data


def summary(data: dict) -> dict:
    return {"modules": len(data["modules"]),
            "by_status": {status: sum(row["status"] == status for row in data["modules"])
                          for status in sorted(STATUSES)
                          if any(row["status"] == status for row in data["modules"])},
            "scope": "acceptance planning only; no server/client run inferred"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--matrix", type=Path, default=MATRIX)
    args = parser.parse_args()
    print(json.dumps(summary(load(args.matrix)), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
