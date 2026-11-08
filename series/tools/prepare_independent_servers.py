"""Prepare isolated Paper/Folia directories for the four series plugins.

The command only copies verified artifacts and writes an install manifest.  It
never launches a server, accepts an EULA implicitly, or overwrites an existing
instance.  Runtime and vanilla-client results remain separate evidence.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path
from zipfile import BadZipFile, ZipFile

from check_independence import check as check_independence

SUITE = Path(__file__).resolve().parents[1]
WORKSPACE = SUITE.parent
MODULES = ("doll", "tavern", "deco", "music")
PLATFORMS = {"paper": 25591, "folia": 25592}


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def verify_zip(path: Path, required: tuple[str, ...]) -> None:
    try:
        with ZipFile(path) as archive:
            names = set(archive.namelist())
            missing = [entry for entry in required if entry not in names]
            if missing:
                raise ValueError(f"{path.name}: missing {missing}")
            if any(name.startswith("/") or ".." in Path(name).parts for name in names):
                raise ValueError(f"{path.name}: unsafe archive path")
            bad = archive.testzip()
            if bad:
                raise ValueError(f"{path.name}: CRC failed for {bad}")
    except BadZipFile as error:
        raise ValueError(f"{path.name}: invalid ZIP: {error}") from error


def single_match(roots: tuple[Path, ...], pattern: str, description: str) -> Path:
    matches: dict[Path, None] = {}
    for root in roots:
        if root.is_dir():
            for candidate in root.glob(pattern):
                if candidate.is_file():
                    matches[candidate.resolve()] = None
    if not matches:
        raise ValueError(f"missing {description} matching {pattern}")
    if len(matches) > 1:
        raise ValueError(f"ambiguous {description}: {sorted(str(path) for path in matches)}")
    return next(iter(matches))


def required_artifacts(artifacts: Path, packs: Path, cookery_jar: Path | None,
                       cookery_pack: Path | None, craft_engine: Path | None) -> list[tuple[str, Path, str]]:
    found: list[tuple[str, Path, str]] = []
    for module in MODULES:
        jar = single_match((artifacts / module / "build/libs", artifacts),
                           f"Kaleidoscope{module.capitalize()}-*.jar", f"{module} JAR")
        pack = single_match((packs,), f"{module}-ce-pack-*.zip", f"{module} CE pack")
        verify_zip(jar, ("plugin.yml",))
        verify_zip(pack, ("pack.yml", "configuration/content.yml"))
        found.extend(((f"plugins/{jar.name}", jar, "plugin"),
                      (f"plugins/CraftEngine/resources/{pack.name}", pack, "ce-pack")))
    shared_matches = sorted(packs.glob("shared-ce-pack-*.zip"))
    if len(shared_matches) > 1:
        raise ValueError(f"ambiguous shared CE packs: {[path.name for path in shared_matches]}")
    if shared_matches:
        shared = shared_matches[0]
        verify_zip(shared, ("pack.yml",))
        found.append((f"plugins/CraftEngine/resources/{shared.name}", shared, "shared-pack"))
    if cookery_jar:
        if not cookery_jar.is_file():
            raise ValueError(f"missing Cookery JAR: {cookery_jar}")
        verify_zip(cookery_jar, ("plugin.yml",))
        found.append((f"plugins/{cookery_jar.name}", cookery_jar, "plugin"))
    if cookery_pack:
        if not cookery_pack.is_file():
            raise ValueError(f"missing Cookery CE pack: {cookery_pack}")
        verify_zip(cookery_pack, ("cookery_kitchen/pack.yml", "cookery_kitchen/configuration/kitchen.yml"))
        found.append(("plugins/CraftEngine/resources/cookery_kitchen", cookery_pack, "cookery-pack"))
    if craft_engine:
        if not craft_engine.is_file():
            raise ValueError(f"missing CraftEngine runtime JAR: {craft_engine}")
        try:
            verify_zip(craft_engine, ("plugin.yml",))
        except ValueError:
            verify_zip(craft_engine, ("paper-plugin.yml",))
        found.append((f"plugins/{craft_engine.name}", craft_engine, "runtime"))
    return found


def _within(path: Path, parent: Path) -> bool:
    try:
        path.resolve().relative_to(parent.resolve())
        return True
    except ValueError:
        return False


def install_artifact(root: Path, relative: str, source: Path, kind: str) -> Path:
    destination = root / "artifacts" / relative
    if kind != "cookery-pack":
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
        return destination
    destination.mkdir(parents=True, exist_ok=True)
    with ZipFile(source) as archive:
        prefix = "cookery_kitchen/"
        for name in archive.namelist():
            if not name.startswith(prefix) or name.endswith("/"):
                continue
            member = Path(name)
            target = destination / member.relative_to("cookery_kitchen")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(name))
    if not (destination / "pack.yml").is_file() or not (destination / "configuration/kitchen.yml").is_file():
        raise ValueError(f"Cookery pack did not extract a complete resources directory: {source}")
    return destination


def prepare(root: Path, artifacts: Path, packs: Path, *, cookery_jar: Path | None = None,
            cookery_pack: Path | None = None, accept_eula: bool = False,
            paper_jar: Path | None = None, folia_jar: Path | None = None,
            craft_engine: Path | None = None, base_resources: Path | None = None) -> dict:
    root = root.resolve()
    allowed = WORKSPACE / "build" / "acceptance"
    # Keep test worlds out of the source tree and production locations.
    if not _within(root, allowed):
        raise ValueError(f"isolated server root must be under {allowed}")
    if root.exists() and any(root.iterdir()):
        raise ValueError(f"refusing to overwrite existing acceptance root: {root}")
    artifacts = artifacts.resolve()
    packs = packs.resolve()
    independent = check_independence(source_root=SUITE, artifacts=artifacts, packs=packs)
    if independent["errors"]:
        raise ValueError("independence check failed: " + "; ".join(independent["errors"]))
    files = required_artifacts(artifacts, packs, cookery_jar, cookery_pack, craft_engine)
    if base_resources and not base_resources.is_dir():
        raise ValueError(f"missing CraftEngine base resources directory: {base_resources}")
    runtimes = {"paper": paper_jar, "folia": folia_jar}
    report = {
        "created_at": datetime.now(timezone.utc).isoformat(),
        "scope": "isolated installation preparation; server and vanilla-client acceptance not run",
        "minecraft": "1.21.11",
        "java": 21,
        "craftEngine": "26.9.1",
        "independence": independent,
        "artifacts": {},
        "base_resources": [],
        "instances": {},
    }
    root.mkdir(parents=True, exist_ok=False)
    for relative, source, kind in files:
        destination = install_artifact(root, relative, source, kind)
        report["artifacts"][relative] = {"kind": kind, "source": str(source),
                                          "sha256": digest(source), "bytes": source.stat().st_size}
    if base_resources:
        target_root = root / "artifacts/plugins/CraftEngine/resources"
        for source in sorted(base_resources.iterdir()):
            if source.name == "cookery_kitchen":
                continue
            target = target_root / source.name
            if source.is_dir():
                shutil.copytree(source, target, dirs_exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
            report["base_resources"].append({"name": source.name, "source": str(source)})
    for platform, port in PLATFORMS.items():
        instance = root / platform
        plugins = instance / "plugins"
        plugins.mkdir(parents=True, exist_ok=True)
        runtime = runtimes[platform]
        if runtime:
            if not runtime.is_file():
                raise ValueError(f"missing {platform} server JAR: {runtime}")
            shutil.copyfile(runtime, instance / "server.jar")
        (instance / "server.properties").write_text(
            f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=true\n"
            "enable-rcon=false\nenable-query=false\nmax-players=8\nview-distance=4\n"
            "simulation-distance=4\nspawn-protection=0\nlevel-name=acceptance-world\n",
            encoding="utf-8")
        eula_comment = ("# Accepted explicitly by the server operator.\n"
                        if accept_eula else
                        "# Set true only after the server operator accepts the Minecraft EULA.\n")
        (instance / "eula.txt").write_text(eula_comment +
                                           f"eula={'true' if accept_eula else 'false'}\n",
                                           encoding="utf-8")
        for relative, _, kind in files:
            if relative.startswith("plugins/"):
                source = root / "artifacts" / relative
                target = instance / relative
                if source.is_dir():
                    shutil.copytree(source, target, dirs_exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(source, target)
        if base_resources:
            source_root = root / "artifacts/plugins/CraftEngine/resources"
            target_root = instance / "plugins/CraftEngine/resources"
            for entry in report["base_resources"]:
                source = source_root / entry["name"]
                target = target_root / entry["name"]
                if source.is_dir():
                    shutil.copytree(source, target, dirs_exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(source, target)
        (instance / "start.sh").write_text(
            "#!/usr/bin/env sh\nset -eu\ncd \"$(dirname \"$0\")\"\n"
            "exec java -Xms512M -Xmx2G -Dfile.encoding=UTF-8 -jar server.jar nogui\n",
            encoding="utf-8")
        report["instances"][platform] = {
            "directory": str(instance), "port": port,
            "serverJar": str(runtime) if runtime else None,
            "eulaAccepted": accept_eula,
            "startup": "NOT_RUN", "resourcePack": "NOT_RUN", "vanillaClient": "NOT_RUN",
            "plugins": {path: value["sha256"] for path, value in report["artifacts"].items()
                        if path.startswith("plugins/") and "/resources/" not in path},
            "cePacks": {path: value["sha256"] for path, value in report["artifacts"].items()
                        if "/resources/" in path},
        }
    (root / "INSTALL-MANIFEST.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                                                 encoding="utf-8")
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=WORKSPACE / "build/acceptance/series-independent")
    parser.add_argument("--artifacts", type=Path, default=SUITE)
    parser.add_argument("--packs", type=Path, required=True)
    parser.add_argument("--cookery-jar", type=Path)
    parser.add_argument("--cookery-pack", type=Path)
    parser.add_argument("--paper-jar", type=Path)
    parser.add_argument("--folia-jar", type=Path)
    parser.add_argument("--craft-engine", type=Path, help="CraftEngine runtime plugin JAR")
    parser.add_argument("--base-resources", type=Path,
                        help="Prepared CraftEngine resources directory containing default packs")
    parser.add_argument("--accept-eula", action="store_true",
                        help="Only when the server operator explicitly accepted the Minecraft EULA")
    args = parser.parse_args()
    try:
        report = prepare(args.root, args.artifacts, args.packs, cookery_jar=args.cookery_jar,
                         cookery_pack=args.cookery_pack, accept_eula=args.accept_eula,
                         paper_jar=args.paper_jar, folia_jar=args.folia_jar,
                         craft_engine=args.craft_engine, base_resources=args.base_resources)
    except (OSError, ValueError, BadZipFile) as error:
        print(f"Independent server preparation failed: {error}", file=sys.stderr)
        return 1
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
