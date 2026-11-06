"""Prepare isolated loopback Paper/Folia test instances; never launch or accept EULA implicitly."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
import urllib.request
import zipfile

from export_cookery_catalog import publish

SERVERS = {
    'paper': (132, '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba', 25581),
    'folia': (14, 'f52c408490a0225611e67907a3ca19f7e6da2c6bc899e715d5f46844e7103c39', 25582),
}


def digest(path: Path) -> str:
    return hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()


def write_once(path: Path, content: str) -> None:
    if not path.exists():
        path.write_text(content, encoding='utf-8')


def verify_plugin(path: Path, expected_name: str) -> None:
    with zipfile.ZipFile(path) as jar:
        descriptors = [name for name in ('plugin.yml', 'paper-plugin.yml') if name in jar.namelist()]
        if not descriptors or not any(expected_name in jar.read(name).decode('utf-8') for name in descriptors):
            raise ValueError(f'{path}: expected runnable {expected_name} plugin JAR, not an API dependency')


def prepare(root: Path, craft_engine: Path | None, kitchen: Path | None, accept_eula: bool) -> dict:
    workspace = Path(__file__).resolve().parents[1]
    root = root.resolve()
    if not root.is_relative_to(workspace / 'build' / 'acceptance'):
        raise ValueError('test servers must stay under this checkout/build/acceptance')
    for jar, name in ((craft_engine, 'CraftEngine'), (kitchen, 'KaleidoscopeCookery')):
        if jar:
            verify_plugin(jar, name)
    runtime = root / 'runtime'
    runtime.mkdir(parents=True, exist_ok=True)
    report = {'minecraft': '1.21.11', 'java': 21, 'craftEngineApi': '26.9.1', 'instances': {}}
    for platform, (build, expected, port) in SERVERS.items():
        filename = f'{platform}-1.21.11-{build}.jar'
        jar = runtime / filename
        url = f'https://fill-data.papermc.io/v1/objects/{expected}/{filename}'
        if not jar.exists():
            request = urllib.request.Request(url, headers={'User-Agent': 'Kaleidoscope-local-test/1.0 (https://github.com/addxiaoyi/KaleidoscopeCookery_Plugin)'})
            with urllib.request.urlopen(request, timeout=60) as response:
                content = response.read()
            if hashlib.sha256(content).hexdigest() != expected:
                raise ValueError(f'{platform}: download checksum mismatch')
            jar.write_bytes(content)
        if digest(jar) != expected:
            raise ValueError(f'{platform}: cached runtime checksum mismatch')
        instance = root / platform
        plugins = instance / 'plugins'
        plugins.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(jar, instance / 'server.jar')
        write_once(instance / 'server.properties', f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=true\n'
                   'enable-rcon=false\nenable-query=false\nmax-players=8\nview-distance=4\nsimulation-distance=4\n'
                   'spawn-protection=0\nlevel-name=acceptance-world\nlevel-seed=8675309\n')
        write_once(instance / 'eula.txt', '# Set true only after the server operator accepts the Minecraft EULA.\neula=false\n')
        if accept_eula:
            (instance / 'eula.txt').write_text('# Accepted explicitly by the server operator.\neula=true\n', encoding='utf-8')
        for artifact in (craft_engine, kitchen):
            if artifact:
                shutil.copyfile(artifact, plugins / artifact.name)
        write_once(instance / 'start.ps1', "$ErrorActionPreference = 'Stop'\nSet-Location -LiteralPath $PSScriptRoot\n"
                   "& java '-Xms512M' '-Xmx2G' '-Dfile.encoding=UTF-8' '-jar' 'server.jar' 'nogui'\nexit $LASTEXITCODE\n")
        report['instances'][platform] = {'directory': str(instance), 'port': port, 'serverSha256': expected,
                                        'download': url, 'plugins': {p.name: digest(p) for p in plugins.glob('*.jar')},
                                        'eulaAccepted': 'eula=true' in (instance / 'eula.txt').read_text(encoding='utf-8'),
                                        'startup': 'NOT_RUN', 'resourcePack': 'NOT_RUN', 'vanillaClient': 'NOT_RUN'}
    publish(root / 'runtime-manifest.json', report)
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path('build/acceptance'))
    parser.add_argument('--craft-engine', type=Path)
    parser.add_argument('--kitchen', type=Path)
    parser.add_argument('--accept-eula', action='store_true', help='Only when the server operator explicitly accepted the Minecraft EULA')
    args = parser.parse_args()
    try:
        report = prepare(args.root, args.craft_engine, args.kitchen, args.accept_eula)
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        print(f'Test server preparation failed: {exc}', file=sys.stderr)
        return 1
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
