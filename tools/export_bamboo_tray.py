"""Export the pinned upstream bamboo tray recipes into a reviewable CE example."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
from typing import Any

from audit_upstream import AuditError, audit, read_blobs, unique_object


ITEM_ALIASES = {
    "kaleidoscope_cookery:fresh_tea_leaves": "kaleidoscopecookery:fresh_tea_leaves",
    "kaleidoscope_cookery:dried_tea_leaves": "kaleidoscopecookery:dried_tea_leaves",
}
KEY = re.compile(r"[a-z0-9_.-]+:[a-z0-9_./-]+\Z")
INT_MAX = 2**31 - 1


def positive_int(value: Any, field: str) -> int:
    if type(value) is not int or not 0 < value <= INT_MAX:
        raise AuditError(f"{field}: expected a positive Java integer")
    return value


def mapped_item(value: Any) -> str:
    if not isinstance(value, str) or not KEY.fullmatch(value):
        raise AuditError(f"invalid item ID: {value!r}")
    if value.startswith("minecraft:"):
        return value
    if value in ITEM_ALIASES:
        return ITEM_ALIASES[value]
    raise AuditError(f"unmapped custom item: {value}")


def convert_recipe(recipe_id: str, raw: bytes) -> dict[str, Any]:
    source = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=unique_object)
    if not isinstance(source, dict) or set(source) != {"type", "duration", "ingredient", "result", "subtype"}:
        raise AuditError(f"{recipe_id}: unsupported bamboo tray recipe fields")
    if source["type"] != "kaleidoscope_cookery:bamboo_tray":
        raise AuditError(f"{recipe_id}: unexpected recipe type")
    ingredient, product = source["ingredient"], source["result"]
    if not isinstance(ingredient, dict) or set(ingredient) != {"item"}:
        raise AuditError(f"{recipe_id}: only explicit item ingredients are supported by this exporter")
    if not isinstance(product, dict) or set(product) not in ({"item"}, {"item", "count"}):
        raise AuditError(f"{recipe_id}: unsupported output components")
    if source["subtype"] not in ("drying", "wetting"):
        raise AuditError(f"{recipe_id}: unsupported processing direction")
    if not recipe_id.startswith("kaleidoscope_cookery:bamboo_tray/") or not KEY.fullmatch(recipe_id):
        raise AuditError(f"{recipe_id}: invalid recipe ID")
    return {
        "id": recipe_id.replace("kaleidoscope_cookery:", "kaleidoscopecookery:", 1),
        "require": mapped_item(ingredient["item"]),
        "result": mapped_item(product["item"]),
        "result_count": positive_int(product.get("count", 1), "result.count"),
        "mode": source["subtype"],
        "time": positive_int(source["duration"], "duration"),
    }


def render_recipes(recipes: list[dict[str, Any]], commit: str) -> str:
    lines = [f"# Generated from KaleidoscopeMods/KaleidoscopeCookery @ {commit}",
             "# Regenerate with tools/export_bamboo_tray.py; review custom item aliases before installing.",
             "bamboo_tray_recipes:"]
    for recipe in recipes:
        lines.append(f"  {json.dumps(recipe['id'])}:")
        for key in ("require", "result", "result_count", "mode", "time"):
            lines.append(f"    {key}: {json.dumps(recipe[key])}")
    return "\n".join(lines) + "\n"


def export(source: Path, commit: str, output: Path) -> int:
    source, output = source.resolve(strict=True), output.resolve()
    if output.is_relative_to(source):
        raise AuditError("export outside the upstream reference checkout")
    inventory = audit(source, commit)
    entries = [entry for entry in inventory["recipes"] if entry["type"] == "kaleidoscope_cookery:bamboo_tray"]
    if not entries:
        raise AuditError("no bamboo tray recipes in the pinned commit")
    blobs = read_blobs(source, commit, [entry["source"]["path"] for entry in entries])
    recipes = [convert_recipe(entry["id"], blobs[entry["source"]["path"]]) for entry in entries]
    vanilla, custom = [], []
    for recipe in recipes:
        requires_custom = any(not recipe[field].startswith("minecraft:") for field in ("require", "result"))
        (custom if requires_custom else vanilla).append(recipe)
    files = {
        "pack/configuration/bamboo_tray_recipes.yml": render_recipes(vanilla, commit),
        "optional/bamboo_tray_tea.yml": render_recipes(custom, commit),
        "recipe-sources.json": json.dumps({
            "upstream": inventory["upstream"],
            "item_aliases": ITEM_ALIASES,
            "recipes": [{**recipe, "source": entry["source"]} for recipe, entry in zip(recipes, entries, strict=True)],
            "default_count": len(vanilla), "optional_count": len(custom),
        }, ensure_ascii=False, indent=2) + "\n",
    }
    for relative, content in files.items():
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8", newline="\n")
    return len(recipes)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        count = export(args.source, args.commit, args.output)
    except (OSError, ValueError) as error:
        print(f"Bamboo tray export failed: {error}", file=sys.stderr)
        return 1
    print(f"Exported {count} recipes to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
