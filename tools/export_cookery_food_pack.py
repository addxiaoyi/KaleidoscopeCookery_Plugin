"""Build an isolated CE food/tea pack from the pinned catalog and original assets.

This pack supplies consumable items, not the missing appliance configuration.
Install in an empty test instance; do not overlay an existing kitchen pack.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile

import yaml

from audit_upstream import AuditError, git_text, read_blobs, unique_object
from export_cookery_catalog import COMMIT, export, publish
from cookery_models import convert_models, model_roots, verify_models


def baked(entry: dict) -> dict | None:
    drink = entry['tea'] is not None or entry.get('consumeAction') == 'remove_harmful_effects'
    food = entry['food']
    if food is None and drink:
        food = {'nutrition': 0, 'saturation': 0, 'alwaysEat': True, 'meat': False, 'consumeTicks': 32,
                'effects': entry['tea']['effects'] if entry['tea'] else []}
    if food is None:
        return None
    food = {key: value for key, value in food.items() if key != 'saturationModifier'}
    return {'version': 1, 'sourceCommit': COMMIT, 'itemId': entry['pluginId'], 'quality': {'name': 'BASE', 'ratio': 1.0},
            'food': food, 'drink': drink, **({'container': entry['container']} if entry['container'] else {}),
            'damage': entry.get('consumeDamage', 0), 'removeHarmful': entry.get('consumeAction') == 'remove_harmful_effects'}


def source_assets(source: Path) -> tuple[dict[str, bytes], dict[str, bytes]]:
    paths = git_text(source, 'ls-tree', '-r', '--name-only', COMMIT).splitlines()
    selected = [path for path in paths if '/resources/assets/' in path or path.upper().startswith(('LICENSE', 'COPYING'))]
    blobs = read_blobs(source, COMMIT, selected)
    assets = {}
    for path, content in blobs.items():
        if '/resources/assets/' in path:
            relative = path.split('/resources/assets/')[1]
            if relative in assets and assets[relative] != content:
                raise AuditError(f'conflicting source asset: {relative}')
            assets[relative] = content
    licenses = {path: content for path, content in blobs.items() if '/resources/assets/' not in path}
    return assets, licenses


def food_items(catalog: dict, assets: dict[str, bytes], definitions: dict[str, dict]) -> dict:
    names = json.loads(assets['kaleidoscope_cookery/lang/zh_cn.json'], object_pairs_hook=unique_object)
    items = {}
    for entry in catalog['entries']:
        snapshot = baked(entry)
        name = entry['sourceId'].split(':', 1)[1]
        if snapshot is None and name != 'empty_cup':
            continue
        model = f'kaleidoscope_cookery/models/item/{name}.json'
        if model not in assets:
            raise AuditError(f'missing food item model: {model}')
        properties = {'item_name': names.get(f'item.kaleidoscope_cookery.{name}', names.get(f'block.kaleidoscope_cookery.{name}', name)),
                      'max_stack_size': 16 if snapshot and snapshot['drink'] else 64}
        if snapshot:
            food = snapshot['food']
            properties['food'] = {'nutrition': food['nutrition'], 'saturation': food['saturation'], 'can_always_eat': food['alwaysEat']}
            properties['pdc'] = {'kaleidoscopecookery:food_v1': json.dumps(snapshot, ensure_ascii=False, separators=(',', ':'))}
            effects = [{'type': 'minecraft:apply_effects', 'effects': [{'id': effect['id'], 'duration': effect['duration'], 'amplifier': effect['amplifier']}],
                        'probability': effect['probability']} for effect in food['effects'] if effect['id'].startswith('minecraft:')]
            properties['components'] = {'minecraft:consumable': {'consume_seconds': food['consumeTicks'] / 20,
                                        'animation': 'drink' if snapshot['drink'] else 'eat', 'on_consume_effects': effects}}
        items[entry['pluginId']] = {'material': 'paper', 'data': properties,
                                   'model': definitions[f'kaleidoscope_cookery:item/{name}']}
    return items


def build(source: Path) -> tuple[dict[str, bytes], dict]:
    catalog = export(source, COMMIT)
    assets, licenses = source_assets(source)
    definitions, converted = convert_models(assets)
    items = food_items(catalog, assets, definitions)
    checked_models = verify_models(assets, set().union(*(model_roots(item['model']) for item in items.values())))
    bundle = {f'cookery_foods/resourcepack/assets/{path}': content for path, content in assets.items()}
    bundle['cookery_foods/pack.yml'] = yaml.safe_dump({'namespace': 'kaleidoscopecookery', 'description': 'Pinned Cookery food and tea acceptance pack', 'version': '1'}).encode()
    bundle['cookery_foods/configuration/foods.yml'] = yaml.safe_dump({'items': items}, allow_unicode=True, sort_keys=False).encode('utf-8')
    for path, content in licenses.items():
        bundle['cookery_foods/licenses/' + path] = content
    manifest = {'sourceCommit': COMMIT, 'items': len(items), 'convertedPerspectives': converted, 'checkedModels': checked_models,
                'scope': 'Food/tea item consumption only; appliances and placed meals remain pending',
                'files': {path: hashlib.sha256(content).hexdigest() for path, content in sorted(bundle.items())}}
    bundle['manifest.json'] = (json.dumps(manifest, indent=2) + '\n').encode()
    return bundle, manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.output.exists() or args.output.resolve().is_relative_to(args.source.resolve()):
            raise AuditError('output must be new and outside the reference checkout')
        bundle, manifest = build(args.source)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.output, 'x', zipfile.ZIP_DEFLATED) as archive:
            for path, content in sorted(bundle.items()):
                archive.writestr(path, content)
        print(json.dumps({'items': manifest['items'], 'files': len(bundle), 'sha256': hashlib.sha256(args.output.read_bytes()).hexdigest()}))
    except (OSError, ValueError) as exc:
        print(f'Cookery food pack failed: {exc}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
