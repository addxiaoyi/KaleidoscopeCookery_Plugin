"""Boot and stop isolated Paper/Folia copies twice; preserve existing acceptance servers."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
import zipfile

from export_cookery_catalog import COMMIT, publish
from prepare_test_servers import SERVERS, digest, verify_plugin

WORKSPACE = Path(__file__).resolve().parents[1]
ACCEPTANCE = WORKSPACE / 'build' / 'acceptance'
PORTS = {'paper': 25583, 'folia': 25584}
MISSING_BLOCKS = (
    'chopping_board', 'enamel_basin', 'kitchenware_racks', 'pot', 'shawarma_spit',
    'steamer', 'stockpot', 'stove', 'fruit_basket', 'teapot', 'teacup_coaster',
)
ANSI_ESCAPE = re.compile(r'\x1b\[[0-?]*[ -/]*[@-~]')


def console_text(path: Path) -> str:
    return ANSI_ESCAPE.sub('', path.read_text(encoding='utf-8', errors='replace'))


def missing_block_error(line: str) -> bool:
    """Keep this known fixture gap visible without masking new runtime errors."""
    if '[KaleidoscopeCookeryPlugin]' not in line:
        return False
    message = line.split('[KaleidoscopeCookeryPlugin]', 1)[1].strip()
    if message == 'craftengine.registry.blocks.missing':
        return True
    if message.startswith('请检查上述 CraftEngine custom 配置是否存在'):
        return True
    return any(message.startswith(f'- kaleidoscopecookery:{name}    配置：') for name in MISSING_BLOCKS)


def prepare(platform: str, root: Path, accept_eula: bool) -> Path:
    if not accept_eula:
        raise ValueError('Explicit --accept-eula is required before launching a test server')
    root = root.resolve()
    if not root.is_relative_to(ACCEPTANCE) or root == ACCEPTANCE:
        raise ValueError('Copies must live in a separate directory under build/acceptance')
    source = ACCEPTANCE / platform
    instance = root / platform
    if instance.exists():
        raise ValueError(f'{instance}: use a new run directory to preserve prior evidence')
    port = PORTS[platform]
    with socket.socket() as check:
        check.bind(('127.0.0.1', port))
    if digest(source / 'server.jar') != SERVERS[platform][1]:
        raise ValueError(f'{platform}: pinned server JAR changed')
    instance.mkdir(parents=True)
    for name in ('cache', 'libraries'):
        shutil.copytree(source / name, instance / name)
    shutil.copyfile(source / 'server.jar', instance / 'server.jar')
    plugins = instance / 'plugins'
    plugins.mkdir()
    jars = (
        source / 'plugins' / 'craft-engine-paper-plugin-26.9.1.jar',
        WORKSPACE / 'build' / 'libs' / 'KaleidoscopeCookeryPlugin-1.3.0.jar',
        ACCEPTANCE / 'probe' / 'CookeryAcceptanceProbe.jar',
    )
    for jar, name in zip(jars, ('CraftEngine', 'KaleidoscopeCookery', 'CookeryAcceptanceProbe'), strict=True):
        verify_plugin(jar, name)
        shutil.copyfile(jar, plugins / jar.name)
    engine = plugins / 'CraftEngine'
    engine.mkdir()
    for name in ('resources', 'libs'):
        shutil.copytree(source / 'plugins' / 'CraftEngine' / name, engine / name)
    shutil.copyfile(source / 'plugins' / 'CraftEngine' / 'config.yml', engine / 'config.yml')
    (instance / 'eula.txt').write_text('# Explicitly accepted by the user for local tests.\neula=true\n', encoding='utf-8')
    (instance / 'server.properties').write_text(
        f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=true\nenable-rcon=false\n'
        'enable-query=false\nmax-players=8\nview-distance=2\nsimulation-distance=2\n'
        'spawn-protection=0\nlevel-name=acceptance-world\nlevel-seed=8675309\n', encoding='utf-8')
    datapacks = instance / 'acceptance-world' / 'datapacks'
    shutil.copytree(WORKSPACE / 'examples' / 'cookery-enchantments', datapacks / 'cookery_enchantments')
    return instance


def install_kitchen(instance: Path, archive: Path) -> dict:
    """Replace only the new copy's food fixture; keep its original files as evidence."""
    instance = instance.resolve()
    if not instance.is_relative_to(ACCEPTANCE) or instance.parent == ACCEPTANCE:
        raise ValueError('Kitchen pack installation requires an isolated acceptance copy')
    engine = instance / 'plugins' / 'CraftEngine'
    resources = engine / 'resources'
    with zipfile.ZipFile(archive) as package:
        manifest = json.loads(package.read('manifest.json'))
        if manifest['sourceCommit'] != COMMIT or manifest['target'] != {'minecraft': '1.21.11', 'craftEngine': '26.9.1'}:
            raise ValueError('Kitchen pack baseline mismatch')
        names = package.namelist()
        if len(names) != len(set(names)) or set(names) != set(manifest['files']) | {'manifest.json'}:
            raise ValueError('Kitchen pack contains duplicate or unmanifested entries')
        contents = {}
        for name, expected in manifest['files'].items():
            target = (resources / name).resolve()
            if (not name.startswith('cookery_kitchen/') or '..' in Path(name).parts or '\\' in name
                    or not target.is_relative_to((resources / 'cookery_kitchen').resolve())):
                raise ValueError(f'Unsafe kitchen pack path: {name}')
            content = package.read(name)
            if hashlib.sha256(content).hexdigest() != expected:
                raise ValueError(f'Kitchen pack digest mismatch: {name}')
            if target.exists():
                raise ValueError(f'Kitchen fixture already installed: {target}')
            contents[target] = content
    backup = engine / 'fixture-backups' / 'cookery_foods'
    original = (resources / 'cookery_foods').resolve()
    if not original.is_relative_to(instance) or not backup.resolve().is_relative_to(instance):
        raise ValueError('Fixture backup escapes isolated instance')
    backup.parent.mkdir(parents=True, exist_ok=True)
    original.rename(backup)
    for target, content in contents.items():
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)
    probe = instance / 'plugins' / 'CookeryAcceptanceProbe'
    probe.mkdir(exist_ok=True)
    settings = 'kitchen-smoke:\n  enabled: true\n'
    if manifest.get('recipes'):
        settings += 'recipe-smoke:\n  enabled: true\n'
    if manifest.get('recipes', {}).get('pot_recipes'):
        settings += 'pot-smoke:\n  enabled: true\n'
    if manifest.get('millstoneModel'):
        settings += 'millstone-smoke:\n  enabled: true\n'
    (probe / 'config.yml').write_text(settings, encoding='utf-8')
    publish(instance / 'ce-kitchen-manifest.json', manifest)
    return manifest


def validate_kitchen(report: dict, manifest: dict, cycle: int) -> None:
    if (report['failures'] or report['checkedBlocks'] != manifest['blocks']
            or report['checkedStates'] != sum(manifest['states'].values())
            or report['checkedItems'] != manifest['items'] or report['nonemptyContainers'] != 2
            or (cycle == 1 and report['placedBlocks'] != manifest['blocks'])
            or (cycle > 1 and report['reloadedBlocks'] != manifest['blocks'])):
        raise ValueError(f'Kitchen smoke differs from the installed manifest: {report}')


def validate_recipes(report: dict, manifest: dict, cycle: int) -> None:
    counts = manifest['recipes']
    expected_plans = sum(counts[name] for name in ('teapot_result', 'accurate_foods', 'chopping_board_raws'))
    expected_plans += counts.get('stockpot_recipes', 0)
    if (report['failures'] or report['checkedPlans'] != expected_plans
            or report['checkedCrafting'] != counts['recipes'] or report['teaCups'] != manifest['teaCups']
            or report['soupBases'] != manifest['soupBases']):
        raise ValueError(f'Recipe registration or output construction failed: {report}')
    phase = 'IN_PROGRESS_SAVED' if cycle == 1 else 'COMPLETED_RELOADED'
    if report.get('phase') != phase:
        raise ValueError(f'Recipe lifecycle requires {phase}: {report}')
    steamers = manifest.get('accurateAppliances', {}).get('STEAMER', counts['accurate_foods'])
    if cycle > 1 and (report.get('steamedOutputs') != steamers or report.get('teaServings') != 8):
        raise ValueError(f'Recipe controllers did not produce the expected outputs: {report}')
    if cycle == 2 and (report.get('reloadedBatches') != 3 or report.get('cuttingDrops') != {
            'kaleidoscopecookery:raw_cut_small_meats': 3, 'minecraft:bone': 1}):
        raise ValueError(f'Recipe reload or cutting conservation failed: {report}')
    if counts.get('stockpot_recipes'):
        if report.get('exactStockpotRecipes', 0) + report.get('distinctStockpotRecipes', 0) != counts['stockpot_recipes']:
            raise ValueError(f'Stockpot recipe coverage differs: {report}')
        if cycle > 1 and (report.get('stockpotServings') != [2, 0] or not report.get('stockpotEmptyNoDuplicate')
                          or abs(report.get('stockpotLiquidOffset', 1) + 0.105) > 0.00001):
            raise ValueError(f'Stockpot serving or liquid state differs: {report}')
        if cycle == 2 and report.get('reloadedStockpots') != 2:
            raise ValueError(f'Stockpot batch reload missing: {report}')


def validate_pots(report: dict, manifest: dict, cycle: int) -> None:
    count = manifest['recipes']['pot_recipes']
    if (report['failures'] or report.get('checkedPlans') != count
            or report.get('exactRecipes', 0) + report.get('distinctRecipes', 0) != count):
        raise ValueError(f'Pot recipe coverage differs: {report}')
    phase = 'IN_PROGRESS_SAVED' if cycle == 1 else 'COMPLETED_RELOADED'
    if report.get('phase') != phase:
        raise ValueError(f'Pot lifecycle requires {phase}: {report}')
    if cycle == 1 and report.get('stirs') != [1, 1]:
        raise ValueError(f'Pot must save unfinished batches: {report}')
    if cycle == 2 and report.get('reloadedPots') != 2:
        raise ValueError(f'Pot reload missing: {report}')
    if cycle > 1 and (report.get('servings') != [2, 0] or not report.get('emptyNoDuplicate')):
        raise ValueError(f'Pot serving conservation failed: {report}')


def validate_millstone(report: dict, manifest: dict, cycle: int) -> None:
    if report['failures'] or report.get('checkedFurniture') != manifest['furniture']:
        raise ValueError(f'Millstone furniture registration failed: {report}')
    phase = {1: 'IN_PROGRESS_SAVED', 2: 'COMPLETED_RELOADED', 3: 'EMPTY_RELOADED'}[cycle]
    if report.get('phase') != phase:
        raise ValueError(f'Millstone lifecycle requires {phase}: {report}')
    if cycle == 1:
        progress = report.get('progressDegrees', [])
        if (len(progress) != 2 or any(not 0 < value < 360 for value in progress)
                or report.get('rawCount') != 2 or report.get('completionEvents') != 0 or report.get('spawnedOutput') != 0):
            raise ValueError(f'Millstone did not save an unfinished animal-driven batch: {report}')
    elif report.get('reloadedFurniture') != 1 or not report.get('emptyNoDuplicate'):
        raise ValueError(f'Millstone reload or conservation missing: {report}')
    if cycle == 2 and (report.get('completionEvents') != 1 or report.get('spawnedOutput') != 10
                       or report.get('outputCount') != 10 or not report.get('animalRestored')):
        raise ValueError(f'Millstone resumed output or animal state differs: {report}')
    if cycle == 3 and (report.get('completionEvents') != 0 or report.get('spawnedOutput') != 0):
        raise ValueError(f'Millstone produced again after restart: {report}')


def boot(instance: Path, cycle: int, timeout: float, kitchen: bool = False) -> dict:
    log = instance / f'boot-{cycle}.log'
    probe = instance / 'plugins' / 'CookeryAcceptanceProbe'
    reports = ('food-smoke.json', 'enchantment-smoke.json') + (('kitchen-smoke.json',) if kitchen else ())
    manifest = json.loads((instance / 'ce-kitchen-manifest.json').read_text(encoding='utf-8')) if kitchen else {}
    if manifest.get('recipes'):
        reports += ('recipe-smoke.json',)
    if manifest.get('recipes', {}).get('pot_recipes'):
        reports += ('pot-smoke.json',)
    if manifest.get('millstoneModel'):
        reports += ('millstone-smoke.json',)
    # New report files are required; do not accept a previous successful boot as this boot's evidence.
    for name in reports:
        if (probe / name).exists():
            (probe / name).rename(probe / f'previous-{cycle}-{name}')
    with log.open('w', encoding='utf-8') as output:
        process = subprocess.Popen(
            ['java', '-Xms512M', '-Xmx2G', '-Dfile.encoding=UTF-8', '-jar', 'server.jar', 'nogui'],
            cwd=instance, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
            text=True, encoding='utf-8', creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == 'win32' else 0)
        try:
            deadline = time.monotonic() + timeout
            while True:
                text = console_text(log)
                if ('Done (' in text and 'COOKERY_ENCHANTMENT_SMOKE' in text
                        and all((probe / name).is_file() for name in reports)):
                    break
                if process.poll() is not None:
                    raise RuntimeError(f'{instance.name}: exited {process.returncode}; see {log}')
                if time.monotonic() >= deadline:
                    raise TimeoutError(f'{instance.name}: smoke probe timed out; see {log}')
                time.sleep(0.5)
            food, enchanted, *appliances = (json.loads((probe / name).read_text(encoding='utf-8')) for name in reports)
            if food['checkedConsumables'] != 103 or food['failures']:
                raise ValueError(f'{instance.name}: food smoke failed: {food}')
            if enchanted['checkedTools'] != 5 or enchanted['failures'] or (cycle > 1 and enchanted['reloadedTools'] != 5):
                raise ValueError(f'{instance.name}: enchantment smoke failed: {enchanted}')
            for report in (food, enchanted, *appliances):
                if report['sourceCommit'] != COMMIT or report['craftEngine'] != '26.9.1':
                    raise ValueError(f'{instance.name}: smoke baseline mismatch')
            if appliances:
                validate_kitchen(appliances[0], manifest, cycle)
            if len(appliances) > 1:
                validate_recipes(appliances[1], manifest, cycle)
            if len(appliances) > 2:
                validate_pots(appliances[2], manifest, cycle)
            if len(appliances) > 3:
                validate_millstone(appliances[3], manifest, cycle)
            assert process.stdin is not None
            process.stdin.write('ce reload pack\n')
            process.stdin.flush()
            while '资源包生成完成（' not in console_text(log):
                generation_log = console_text(log)
                if 'Incorrect argument for command' in generation_log or 'Unknown or incomplete command' in generation_log:
                    raise ValueError(f'{instance.name}: resource command rejected; see {log}')
                if process.poll() is not None or time.monotonic() >= deadline:
                    raise TimeoutError(f'{instance.name}: resource pack generation failed; see {log}')
                time.sleep(0.5)
            errors = [line for line in console_text(log).splitlines()
                      if '/ERROR]' in line or ' ERROR]:' in line]
            unexpected = errors if kitchen else [line for line in errors if not missing_block_error(line)]
            if unexpected:
                raise ValueError(f'{instance.name}: unexpected runtime errors: {unexpected}')
            pack = instance / 'plugins' / 'CraftEngine' / 'cache' / 'hosted' / 'default' / 'resource_pack.zip'
            if not pack.is_file():
                raise ValueError(f'{instance.name}: generated pack missing')
            evidence = {'cycle': cycle, 'food': food, 'enchantments': enchanted,
                        'resourcePackSha256': digest(pack), 'knownFixtureErrors': errors,
                        'startup': 'PASS' if kitchen else 'PASS_WITH_MISSING_APPLIANCE_DEFINITIONS',
                        'nativeEnchantmentSerialization': 'PASS', 'resourceGeneration': 'PASS',
                        'pluginArtifacts': {path.relative_to(instance).as_posix(): digest(path)
                                            for path in (instance / 'plugins').glob('*.jar')}}
            if appliances:
                evidence['kitchen'] = appliances[0]
            if len(appliances) > 1:
                evidence['recipes'] = appliances[1]
            if len(appliances) > 2:
                evidence['pots'] = appliances[2]
            if len(appliances) > 3:
                evidence['millstone'] = appliances[3]
        finally:
            if process.poll() is None:
                assert process.stdin is not None
                try:
                    process.stdin.write('stop\n')
                    process.stdin.flush()
                    process.wait(timeout=45)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
                    raise RuntimeError(f'{instance.name}: graceful shutdown failed; see {log}')
            if process.stdin is not None:
                process.stdin.close()
            if process.returncode != 0:
                raise RuntimeError(f'{instance.name}: shutdown exit code {process.returncode}; see {log}')
            shutdown_errors = [line for line in console_text(log).splitlines()
                               if ('/ERROR]' in line or ' ERROR]:' in line)
                               and (kitchen or not missing_block_error(line))]
            if shutdown_errors:
                raise ValueError(f'{instance.name}: runtime/shutdown errors retained in {log}')
    evidence['stoppedCleanly'] = True
    publish(instance / f'smoke-{cycle}.json', evidence)
    return evidence


def run(root: Path, accept_eula: bool, timeout: float, kitchen_pack: Path | None = None) -> dict:
    evidence: dict = {'sourceCommit': COMMIT, 'instances': {}, 'playerFlow': 'NOT_TESTED', 'clientResourcePack': 'NOT_TESTED'}
    for platform in PORTS:
        print(f'{platform}: preparing isolated copy', flush=True)
        instance = prepare(platform, root, accept_eula)
        if kitchen_pack is not None:
            manifest = install_kitchen(instance, kitchen_pack)
            evidence['kitchenPack'] = {'sha256': digest(kitchen_pack), 'manifest': manifest}
        cycles = []
        for cycle in ((1, 2, 3) if kitchen_pack is not None and manifest.get('recipes', {}).get('stockpot_recipes') else (1, 2)):
            print(f'{platform}: boot {cycle}', flush=True)
            cycles.append(boot(instance, cycle, timeout, kitchen=kitchen_pack is not None))
            print(f'{platform}: boot {cycle} passed, stopped cleanly', flush=True)
        installed = {path.relative_to(instance).as_posix(): digest(path) for path in (instance / 'plugins').glob('*.jar')}
        installed['server.jar'] = digest(instance / 'server.jar')
        datapack = instance / 'acceptance-world' / 'datapacks' / 'cookery_enchantments'
        installed.update({path.relative_to(instance).as_posix(): digest(path) for path in datapack.rglob('*') if path.is_file()})
        evidence['instances'][platform] = {'port': PORTS[platform], 'artifacts': installed, 'cycles': cycles, 'stoppedCleanly': True}
        publish(root / 'acceptance.json', evidence)
    return evidence


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ACCEPTANCE / 'enchantments')
    parser.add_argument('--accept-eula', action='store_true')
    parser.add_argument('--timeout', type=float, default=180)
    parser.add_argument('--kitchen-pack', type=Path)
    args = parser.parse_args()
    try:
        run(args.root.resolve(), args.accept_eula, args.timeout, args.kitchen_pack)
    except (OSError, ValueError, RuntimeError, TimeoutError) as exception:
        print(f'Enchantment acceptance failed: {exception}', file=sys.stderr)
        return 1
    print(f'Both platforms passed; evidence: {args.root / "acceptance.json"}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
