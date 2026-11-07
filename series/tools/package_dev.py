"""Assemble the local development handoff after both Gradle builds pass."""
from __future__ import annotations

import hashlib
import argparse
import json
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

from validate_packs import load_files, validate

SUITE = Path(__file__).resolve().parents[1]
ROOT = SUITE.parent
DIST = SUITE / 'build/distribution'
MODULES = ('doll', 'tavern', 'deco', 'music')
PACK_MODULES = MODULES + ('shared',)


def digest(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def tests(folder: Path) -> dict[str, int]:
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in folder.glob('TEST-*.xml'):
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, 0))
    if totals['tests'] == 0 or totals['failures'] or totals['errors'] or totals['skipped']:
        raise ValueError(f'Missing or unsuccessful tests at {folder}: {totals}')
    return totals


def load_module_packs(folder: Path) -> tuple[dict[str, bytes], list[Path]]:
    """Load one independent CE ZIP per module into validator-relative paths."""
    if not folder.is_dir():
        raise ValueError(f'Module pack directory does not exist: {folder}')
    archives = sorted(folder.glob('*-ce-pack-*.zip'))
    if not archives:
        raise ValueError(f'No independent CE packs found in {folder}')
    merged: dict[str, bytes] = {}
    seen: set[str] = set()
    for archive in archives:
        module = archive.name.split('-ce-pack-', 1)[0]
        if module not in PACK_MODULES or module in seen:
            raise ValueError(f'Unexpected or duplicate CE pack: {archive.name}')
        seen.add(module)
        files = load_files(archive)
        if 'pack.yml' not in files or (module != 'shared' and 'configuration/content.yml' not in files):
            raise ValueError(f'Incomplete independent CE pack: {archive.name}')
        for relative, content in files.items():
            path = f'{module}/{relative}'
            if path in merged:
                raise ValueError(f'Duplicate independent CE entry: {path}')
            merged[path] = content
    missing = set(MODULES) - seen
    if missing:
        raise ValueError(f'Missing independent CE packs: {sorted(missing)}')
    return merged, archives


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--packs', type=Path, default=DIST / 'KaleidoscopeSeries-CE-Packs-0.1.0-dev.zip')
    parser.add_argument('--module-packs-dir', type=Path,
                        help='Directory containing one <module>-ce-pack-<version>.zip per plugin')
    parser.add_argument('--output', type=Path, default=DIST / 'KaleidoscopeSeries-0.1.0-dev.zip')
    args = parser.parse_args()
    DIST.mkdir(parents=True, exist_ok=True)
    destination = args.output.resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        raise FileExistsError(f'Development bundle already exists: {destination}')
    packs = args.packs.resolve()
    module_archives: list[Path] = []
    if args.module_packs_dir:
        pack_files, module_archives = load_module_packs(args.module_packs_dir.resolve())
        validation = validate(pack_files)
    else:
        validation = validate(load_files(packs))
    if validation['errors']:
        raise ValueError(f'Invalid CE content: {validation["errors"]}')
    java_tests = {'cookery': tests(ROOT / 'build/test-results/test')}
    for module in ('common', 'doll', 'tavern', 'deco', 'music'):
        java_tests[module] = tests(SUITE / module / 'build/test-results/test')
    entries: dict[str, bytes] = {}
    for module in MODULES:
        jar = SUITE / module / f'build/libs/Kaleidoscope{module.capitalize()}-0.1.0-dev.jar'
        with ZipFile(jar) as archive:
            names = archive.namelist()
            forbidden = ('org/bukkit/', 'io/papermc/', 'org/junit/', 'net/momirealms/', 'net/kaleidoscope/series/')
            if any(name.endswith('.class') and name.startswith(forbidden) for name in names):
                raise ValueError(f'Unrelocated or server/test classes bundled in {jar.name}')
            metadata = archive.read('plugin.yml').decode('utf-8')
            if "api-version: '1.21.11'" not in metadata or '${version}' in metadata:
                raise ValueError(f'Incorrect plugin metadata: {jar.name}')
            catalog = (SUITE / module / 'src/main/resources/catalog.json').read_bytes()
            if archive.read('catalog.json') != catalog:
                raise ValueError(f'Stale catalog inside {jar.name}; rebuild first')
            if not any(name.startswith(f'net/kaleidoscope/{module}/internal/') for name in names):
                raise ValueError(f'Missing relocated shared classes: {jar.name}')
        entries['plugins/' + jar.name] = jar.read_bytes()
    kitchen = ROOT / 'build/libs/KaleidoscopeCookeryPlugin-1.3.0.jar'
    entries['plugins/' + kitchen.name] = kitchen.read_bytes()
    if module_archives:
        for archive in module_archives:
            entries['ce-packs/' + archive.name] = archive.read_bytes()
    else:
        entries[packs.name] = packs.read_bytes()
    entries['README.md'] = (SUITE / 'docs/INSTALL.md').read_bytes()
    for path in (SUITE / 'docs').glob('*'):
        if path.is_file():
            entries['docs/' + path.name] = path.read_bytes()
    entries['organization-checkouts.json'] = (ROOT / 'docs/research/organization-checkouts.json').read_bytes()
    manifest = {
        'created_at': datetime.now().astimezone().isoformat(),
        'status': 'development; server and vanilla-client acceptance not performed',
        'baseline_commit': subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], text=True).strip(),
        'uncommitted_work': True,
        'compile_target': {'java': 21, 'paper_api': '1.21.11', 'craftengine_api': '26.9.1'},
        'java_tests': java_tests,
        'pack_validation': validation,
        'independent_ce_packs': [path.name for path in module_archives],
        'files': {name: {'size': len(content), 'sha256': digest(content)} for name, content in sorted(entries.items())},
    }
    entries['BUILD-MANIFEST.json'] = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode()
    entries['SHA256SUMS.txt'] = ''.join(f'{digest(content)}  {name}\n' for name, content in sorted(entries.items())).encode()
    with ZipFile(destination, 'x', compression=ZIP_DEFLATED) as archive:
        for name, content in sorted(entries.items()):
            archive.writestr(name, content)
    with ZipFile(destination) as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError(f'Bundle CRC failed: {bad}')
        for name, content in entries.items():
            if digest(archive.read(name)) != digest(content):
                raise ValueError(f'Bundle checksum failed: {name}')
    manifest_path = destination.with_name(destination.stem + '-BUILD-MANIFEST.json')
    manifest_path.write_bytes(entries['BUILD-MANIFEST.json'])
    checksum = digest(destination.read_bytes())
    destination.with_suffix('.sha256').write_text(f'{checksum}  {destination.name}\n', encoding='utf-8')
    print(json.dumps({'bundle': str(destination), 'bytes': destination.stat().st_size, 'sha256': checksum,
                      'java_tests': java_tests, 'pack_validation': validation}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
