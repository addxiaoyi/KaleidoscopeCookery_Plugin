"""Inventory a pinned Cookery checkout without equating source counts with parity."""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from typing import Any


NAMESPACE = "kaleidoscope_cookery"
JAVA_ROOT = "src/main/java/com/github/ysbbbbbb/kaleidoscopecookery"
RESOURCE_ROOTS = ("src/main/resources", "src/generated/resources")
REGISTRIES = {
    "items": ("init/ModItems.java", "ITEMS.register"),
    "blocks": ("init/ModBlocks.java", "BLOCKS.register"),
    "block_entities": ("init/ModBlocks.java", "BLOCK_ENTITIES.register"),
    "effects": ("init/ModEffects.java", "EFFECTS.register"),
    "enchantments": ("init/ModEnchantments.java", "ENCHANTMENTS.register"),
    "entities": ("init/ModEntities.java", "ENTITY_TYPES.register"),
    "recipe_serializers": ("init/ModRecipes.java", "RECIPE_SERIALIZERS.register"),
    "professions": ("init/ModVillager.java", "VILLAGER_PROFESSION.register"),
    "points_of_interest": ("init/ModPoi.java", "POI_TYPES.register"),
    "plated_foods": ("init/registry/FoodBiteRegistry.java", "registry.registerFoodData"),
}
JAVA_TOKENS = re.compile(r'"""(?:\\[\s\S]|(?!""")[^\\])*"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')


class AuditError(ValueError):
    pass


def git_text(root: Path, *args: str) -> str:
    run = subprocess.run(
        ["git", "-C", str(root), *args], capture_output=True, encoding="utf-8", timeout=60
    )
    if run.returncode:
        raise AuditError(f"git {' '.join(args)} failed: {run.stderr.strip()}")
    return run.stdout.strip()


def read_blobs(root: Path, commit: str, paths: list[str]) -> dict[str, bytes]:
    if any("\n" in path or "\r" in path for path in paths):
        raise AuditError("source paths containing newlines require manual inspection")
    requests = "".join(f"{commit}:{path}\n" for path in paths).encode("utf-8")
    run = subprocess.run(
        ["git", "-C", str(root), "cat-file", "--batch"], input=requests,
        capture_output=True, timeout=60,
    )
    if run.returncode:
        raise AuditError(f"git cat-file failed: {run.stderr.decode('utf-8', errors='replace').strip()}")
    blobs: dict[str, bytes] = {}
    offset = 0
    for path in paths:
        header_end = run.stdout.find(b"\n", offset)
        header = run.stdout[offset:header_end].split()
        if header_end < offset or len(header) != 3 or header[1] != b"blob" or not header[2].isdigit():
            raise AuditError(f"{path}: missing or invalid Git blob response")
        start = header_end + 1
        end = start + int(header[2])
        if run.stdout[end:end + 1] != b"\n":
            raise AuditError(f"{path}: truncated Git blob response")
        blobs[path] = run.stdout[start:end]
        offset = end + 1
    if offset != len(run.stdout):
        raise AuditError("unexpected trailing Git blob response")
    return blobs


def uncomment_java(source: str) -> str:
    def keep_lines(match: re.Match[str]) -> str:
        token = match.group()
        if token.startswith(("//", "/*")):
            return "".join("\n" if char == "\n" else " " for char in token)
        return token

    return JAVA_TOKENS.sub(keep_lines, source)


def literal_registrations(source: str, call: str) -> list[dict[str, Any]]:
    text = uncomment_java(source)
    # Keep offsets while excluding example code embedded in strings and text blocks.
    code = JAVA_TOKENS.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), text)
    calls = re.finditer(rf"(?<![\w.]){re.escape(call)}\s*\(", code)
    entries: list[dict[str, Any]] = []
    for match in calls:
        argument = re.match(r'\s*"([a-z0-9_./-]+)"\s*,', text[match.end():])
        if argument is None:
            raise AuditError(f"{call}: non-literal registration requires manual inspection")
        entries.append({"id": f"{NAMESPACE}:{argument.group(1)}", "line": text.count("\n", 0, match.start()) + 1})
    ids = [entry["id"] for entry in entries]
    if len(ids) != len(set(ids)):
        raise AuditError(f"{call}: duplicate literal registration")
    return entries


def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    document: dict[str, Any] = {}
    for key, value in pairs:
        if key in document:
            raise AuditError(f"duplicate JSON key: {key}")
        document[key] = value
    return document


def recipe_type(raw: bytes, path: str) -> str:
    try:
        document = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=unique_object)
    except (UnicodeError, ValueError) as exc:
        raise AuditError(f"{path}: invalid recipe JSON: {exc}") from exc
    kind = document.get("type") if isinstance(document, dict) else None
    if not isinstance(kind, str) or not kind.strip():
        raise AuditError(f"{path}: recipe must declare a non-empty type")
    return kind


def recipe_id(path: str) -> str | None:
    for resource_root in RESOURCE_ROOTS:
        prefix = f"{resource_root}/data/{NAMESPACE}/recipes/"
        if path.startswith(prefix) and path.endswith(".json"):
            return f"{NAMESPACE}:{path[len(prefix):-5]}"
    return None


def collect_recipes(blobs: dict[str, bytes]) -> list[dict[str, Any]]:
    recipes: dict[str, dict[str, Any]] = {}
    for path, raw in sorted(blobs.items()):
        key = recipe_id(path)
        if key is None:
            continue
        origin = {"path": path, "sha256": hashlib.sha256(raw).hexdigest()}
        if key in recipes:
            # Both directories are resource roots; choosing one would hide a packaging conflict.
            raise AuditError(f"duplicate recipe resource {key}: {recipes[key]['source']['path']} and {path}")
        recipes[key] = {"id": key, "type": recipe_type(raw, path), "source": origin}
    if not recipes:
        raise AuditError("no tracked Cookery recipes found")
    return [recipes[key] for key in sorted(recipes)]


def audit(root: Path, expected_commit: str) -> dict[str, Any]:
    commit = git_text(root, "rev-parse", "HEAD")
    if not re.fullmatch(r"[0-9a-f]{40}", expected_commit) or commit != expected_commit:
        raise AuditError(f"expected full commit {expected_commit}, checkout is {commit}")
    if git_text(root, "status", "--porcelain", "--untracked-files=no"):
        raise AuditError("reference checkout has tracked changes; restore a clean reference first")
    tracked = git_text(root, "ls-tree", "-r", "--name-only", "-z", commit).split("\0")
    tracked_set = set(tracked)
    registry_paths = {f"{JAVA_ROOT}/{path}" for path, _ in REGISTRIES.values()}
    selected = sorted(path for path in tracked if path in registry_paths or recipe_id(path))
    blobs = read_blobs(root, commit, selected)
    registries: dict[str, Any] = {}
    for name, (relative_path, call) in REGISTRIES.items():
        path = f"{JAVA_ROOT}/{relative_path}"
        if path not in tracked_set:
            raise AuditError(f"missing tracked registry source: {path}")
        raw = blobs[path]
        entries = literal_registrations(raw.decode("utf-8-sig"), call)
        if not entries:
            raise AuditError(f"{path}: no {call} entries found")
        registries[name] = {
            "path": path, "sha256": hashlib.sha256(raw).hexdigest(),
            "literal_count": len(entries), "entries": entries,
        }
    recipes = collect_recipes(blobs)
    return {
        "schema_version": 1,
        "upstream": {"repository": "KaleidoscopeMods/KaleidoscopeCookery", "commit": commit},
        "scope": {
            "content_method": "Git blobs from the pinned commit; hashes ignore working-tree line endings",
            "registration_method": "Literal calls in the listed files, not a runtime registry dump",
            "recipe_method": "Tracked JSON in main and generated resource roots; duplicates rejected",
            "limitations": [
                "Dynamic registrations, food and tea registries need separate review",
                "Recipe counts exclude external add-ons and do not prove loaded or matching recipes",
                "No implementation coverage or completion percentage is inferred",
            ],
        },
        "registries": registries,
        "recipe_count": len(recipes),
        "recipe_types": dict(sorted(Counter(recipe["type"] for recipe in recipes).items())),
        "recipes": recipes,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        report = audit(args.source.resolve(strict=True), args.commit)
        output = args.output.resolve()
        source = args.source.resolve()
        if output.is_relative_to(source):
            raise AuditError("write the report outside the upstream reference checkout")
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    except (OSError, ValueError, subprocess.SubprocessError) as exc:
        print(f"Upstream audit failed: {exc}", file=sys.stderr)
        return 1
    print(f"Inventoried {report['recipe_count']} recipes at {report['upstream']['commit']}")
    print(f"Report: {output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
