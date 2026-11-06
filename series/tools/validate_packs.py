"""Validate imported CE references without claiming a server or renderer smoke test."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any
from zipfile import ZipFile


def load_files(path: Path) -> dict[str, bytes]:
    if path.is_dir():
        return {p.relative_to(path).as_posix(): p.read_bytes() for p in path.rglob('*') if p.is_file()}
    with ZipFile(path) as archive:
        return {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}


def validate(files: dict[str, bytes]) -> dict[str, Any]:
    assets: dict[str, bytes] = {}
    items: dict[str, Any] = {}
    furnishings: dict[str, Any] = {}
    recipes: dict[str, Any] = {}
    errors: set[str] = set()
    checked: set[str] = set()
    for path, content in files.items():
        if '/resourcepack/assets/' in path:
            key = path.split('/resourcepack/assets/', 1)[1]
            if key in assets and assets[key] != content:
                errors.add(f'Conflicting shared resource: {key}')
            assets[key] = content
        if path.endswith('/configuration/content.yml'):
            config = json.loads(content)
            for category, target in [('items', items), ('furniture', furnishings), ('recipes', recipes)]:
                for key, value in config.get(category, {}).items():
                    if key in target:
                        errors.add(f'Duplicate {category} ID: {key}')
                    target[key] = value

    def reference(id_: str, folder: str, extension: str, owner: str) -> str | None:
        namespace, name = id_.split(':', 1) if ':' in id_ else ('minecraft', id_)
        if namespace == 'minecraft':
            return None
        path = f'{namespace}/{folder}/{name}{extension}'
        if path not in assets:
            errors.add(f'{owner}: missing {path}')
            return None
        return path

    def inherit(id_: str, trail: tuple[str, ...] = ()) -> dict[str, Any]:
        path = reference(id_, 'models', '.json', trail[-1] if trail else id_)
        if path is None:
            return {}
        if path in trail:
            errors.add(f'Cyclic model parent: {" -> ".join((*trail, path))}')
            return {}
        checked.add(path)
        definition = json.loads(assets[path])
        if 'loader' in definition:
            errors.add(f'Client-only model loader: {path}')
        inherited = inherit(definition['parent'], (*trail, path)) if 'parent' in definition else {}
        return {**inherited, **definition,
                'textures': {**inherited.get('textures', {}), **definition.get('textures', {})}}

    def model(id_: str, trail: tuple[str, ...] = ()) -> None:
        if id_ in trail:
            errors.add(f'Cyclic model override: {id_}')
            return
        definition = inherit(id_)
        for texture in definition.get('textures', {}).values():
            if isinstance(texture, str) and not texture.startswith('#'):
                reference(texture, 'textures', '.png', id_)
        for override in definition.get('overrides', []):
            if 'model' in override:
                model(override['model'], (*trail, id_))

    def model_tree(node: Any) -> None:
        if isinstance(node, list):
            for child in node:
                model_tree(child)
        elif isinstance(node, dict):
            if node.get('type') in ('model', 'minecraft:model') and 'path' in node:
                model(node['path'])
            for child in node.values():
                if isinstance(child, (dict, list)):
                    model_tree(child)

    def item_id(id_: str, owner: str) -> None:
        if not id_.startswith(('minecraft:', '#minecraft:')) and id_ not in items:
            errors.add(f'{owner}: undefined item {id_}')

    for id_, definition in items.items():
        model_tree(definition.get('model', {}))
        behavior = definition.get('behavior', {})
        if behavior.get('type') == 'furniture_item' and behavior.get('furniture') not in furnishings:
            errors.add(f'{id_}: undefined furniture {behavior.get("furniture")}')
        remainder = definition.get('settings', {}).get('consume_replacement')
        if remainder:
            item_id(remainder, id_)
    for id_, definition in furnishings.items():
        item_id(definition['settings']['item'], id_)
        for variant in definition['variants'].values():
            for element in variant.get('elements', []):
                if 'item' in element:
                    item_id(element['item'], id_)
    for id_, recipe in recipes.items():
        item_id(recipe['result']['id'], id_)

        def ingredient(value: Any) -> None:
            if isinstance(value, str):
                item_id(value, id_)
            elif isinstance(value, list):
                for alternative in value:
                    ingredient(alternative)
            elif isinstance(value, dict):
                for alternative in value.values():
                    ingredient(alternative)

        ingredient(recipe['ingredients'])
    if not items:
        errors.add('No generated item definitions found')
    return {'items': len(items), 'furniture': len(furnishings), 'recipes': len(recipes),
            'reachable_custom_models': len(checked), 'errors': sorted(errors),
            'scope': 'Custom model parents, concrete texture references, item/furniture IDs and shared asset collisions; no server/render validation'}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('packs', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    report = validate(load_files(args.packs))
    text = json.dumps(report, ensure_ascii=False, indent=2)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text + '\n', encoding='utf-8')
    print(text)
    if report['errors']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
