"""Check that the five gameplay plugins remain independently installable.

This is a structural check.  It verifies plugin metadata, generated CE pack
roots and optional JAR contents; it does not claim that a Paper/Folia server
or a vanilla client has loaded the packs.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from zipfile import ZipFile

import yaml

from import_sources import load_module_specs

SUITE = Path(__file__).resolve().parents[1]
MODULES = SUITE / "tools/module-specs.json"


def _plugin(path: Path) -> dict:
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"invalid plugin metadata: {path}")
    return data


def _single_match(roots: tuple[Path, ...], pattern: str) -> tuple[Path | None, str | None]:
    matches: dict[Path, None] = {}
    for root in roots:
        if root.is_dir():
            for candidate in root.glob(pattern):
                if candidate.is_file():
                    matches[candidate.resolve()] = None
    if len(matches) > 1:
        return None, f"ambiguous artifacts for {pattern}: {sorted(str(path) for path in matches)}"
    return (next(iter(matches), None), None)


def check(source_root: Path = SUITE, artifacts: Path | None = None,
          packs: Path | None = None) -> dict:
    _, specs, _ = load_module_specs()
    errors: list[str] = []
    plugins: list[dict] = []
    for spec in specs:
        module = spec["module"]
        metadata_path = source_root / module / "src/main/resources/plugin.yml"
        try:
            metadata = _plugin(metadata_path)
        except (OSError, ValueError, yaml.YAMLError) as error:
            errors.append(str(error))
            continue
        name = metadata.get("name")
        if not isinstance(name, str) or not name.startswith("Kaleidoscope"):
            errors.append(f"{module}: plugin name is missing or not public")
        dependencies = metadata.get("depend", []) or []
        if dependencies != ["CraftEngine"]:
            errors.append(f"{module}: hard dependencies must be exactly CraftEngine, got {dependencies!r}")
        soft = metadata.get("softdepend", []) or []
        if any(dep.endswith("Plugin") and dep not in {"Dominion", "KaleidoscopeCookeryPlugin", "KaleidoscopeTavernPlugin"}
               for dep in soft):
            errors.append(f"{module}: unexpected hard-coupled soft dependency: {soft!r}")
        plugins.append({"module": module, "name": name, "pack_namespace": spec["packNamespace"],
                        "metadata": str(metadata_path.relative_to(source_root))})

        if artifacts:
            pattern = f"Kaleidoscope{module.capitalize()}-*.jar"
            jar, error = _single_match((artifacts, artifacts / module / "build/libs",
                                        source_root / module / "build/libs"), pattern)
            if error:
                errors.append(f"{module}: {error}")
            elif jar is None:
                errors.append(f"{module}: missing JAR matching {pattern}")
            else:
                with ZipFile(jar) as archive:
                    names = archive.namelist()
                    if "plugin.yml" not in names:
                        errors.append(f"{module}: JAR has no plugin.yml")
                    forbidden = ("net/kaleidoscope/series/", "org/bukkit/", "io/papermc/")
                    if any(name.endswith(".class") and name.startswith(forbidden) for name in names):
                        errors.append(f"{module}: JAR contains unrelocated shared/server classes")

        if packs:
            pattern = f"{module}-ce-pack-*.zip"
            pack, error = _single_match((packs,), pattern)
            if error:
                errors.append(f"{module}: {error}")
            elif pack is None:
                errors.append(f"{module}: missing CE pack matching {pattern}")
            else:
                with ZipFile(pack) as archive:
                    names = set(archive.namelist())
                    if "pack.yml" not in names or "configuration/content.yml" not in names:
                        errors.append(f"{module}: CE pack is missing pack.yml/content.yml")
                    else:
                        try:
                            pack_data = json.loads(archive.read("pack.yml"))
                            if pack_data.get("namespace") != spec["packNamespace"]:
                                errors.append(f"{module}: pack namespace mismatch")
                        except (ValueError, UnicodeDecodeError) as error:
                            errors.append(f"{module}: invalid pack.yml: {error}")
                    for entry in names:
                        if entry.startswith("/") or ".." in Path(entry).parts:
                            errors.append(f"{module}: unsafe CE path {entry}")
    return {"plugins": plugins, "modules": len(specs), "errors": sorted(set(errors)),
            "scope": "plugin metadata, optional JAR/CE pack independence; no server/client runtime"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts", type=Path, help="Directory containing module JARs")
    parser.add_argument("--packs", type=Path, help="Directory containing independent CE ZIPs")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = check(artifacts=args.artifacts, packs=args.packs)
    text = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text, encoding="utf-8")
    print(text, end="")
    if report["errors"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
