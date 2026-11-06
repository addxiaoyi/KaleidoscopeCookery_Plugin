"""Translate pinned Forge model perspectives to vanilla item definitions."""

from __future__ import annotations

import copy
import json
from typing import Any

from audit_upstream import AuditError, unique_object

CONTEXTS = frozenset(('none', 'gui', 'ground', 'fixed', 'head', 'firstperson_righthand',
                      'firstperson_lefthand', 'thirdperson_righthand', 'thirdperson_lefthand'))


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def model_path(identifier: str) -> str:
    namespace, name = identifier.split(':', 1)
    if not namespace or not name or '..' in name or name.startswith('/'):
        raise AuditError(f'invalid model identifier: {identifier}')
    return f'{namespace}/models/{name}.json'


def model_id(path: str) -> str:
    namespace, name = path.split('/models/', 1)
    return namespace + ':' + name.removesuffix('.json')


def model_node(identifier: str) -> dict:
    return {'type': 'minecraft:model', 'path': identifier}


def convert_models(assets: dict[str, bytes]) -> tuple[dict[str, dict], list[str]]:
    """Validate all loaders before changing assets; unknown renderers abort the export."""
    definitions, additions, converted = {}, {}, []
    for path, content in sorted(assets.items()):
        if '/models/' not in path or not path.endswith('.json'):
            continue
        model = json.loads(content, object_pairs_hook=unique_object)
        identifier = model_id(path)
        definitions[identifier] = model_node(identifier)
        if 'loader' not in model:
            continue
        if model['loader'] != 'forge:separate_transforms':
            raise AuditError(f'unsupported model loader in {path}: {model["loader"]}')
        extra = set(model) - {'loader', 'base', 'perspectives', 'gui_light'}
        if extra or not isinstance(model.get('base'), dict) or not isinstance(model.get('perspectives'), dict):
            raise AuditError(f'unknown separate_transforms structure: {path} ({sorted(extra)})')
        shared = {key: value for key, value in model.items() if key == 'gui_light'}
        base = {**shared, **model['base']}
        if 'loader' in base:
            raise AuditError(f'nested model loader: {path}')
        additions[path] = json_bytes(base)
        cases = []
        for context, perspective in sorted(model['perspectives'].items()):
            if context not in CONTEXTS or not isinstance(perspective, dict) or 'loader' in perspective:
                raise AuditError(f'unknown model perspective: {path}/{context}')
            perspective_id = identifier + '_ce_' + context
            target = model_path(perspective_id)
            if target in assets or target in additions:
                raise AuditError(f'generated perspective collides with source: {target}')
            additions[target] = json_bytes({**shared, **perspective})
            cases.append({'when': [context], 'model': model_node(perspective_id)})
        definitions[identifier] = {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                                   'cases': cases, 'fallback': model_node(identifier)}
        converted.append(identifier)
    assets.update(additions)
    return definitions, converted


def geometry_part(model: dict, indices: list[int]) -> dict:
    if not indices or len(indices) != len(set(indices)) or any(i < 0 or i >= len(model['elements']) for i in indices):
        raise AuditError('invalid model element selection')
    # Display entities use NONE; inventory transforms must not move these parts twice.
    return {'textures': copy.deepcopy(model['textures']),
            'elements': [copy.deepcopy(model['elements'][i]) for i in indices]}


def group_indices(model: dict, name: str) -> list[int]:
    matches = []

    def visit(nodes: list) -> None:
        for node in nodes:
            if isinstance(node, dict):
                if node.get('name') == name:
                    matches.append(node)
                visit(node.get('children', []))

    def flatten(nodes: list) -> list[int]:
        return [index for node in nodes for index in
                ([node] if isinstance(node, int) else flatten(node['children']))]

    visit(model.get('groups', []))
    if len(matches) != 1:
        raise AuditError(f'expected exactly one model group {name}, found {len(matches)}')
    return flatten(matches[0]['children'])


def verify_models(assets: dict[str, bytes], roots: set[str]) -> int:
    """Follow custom parents and textures; external vanilla assets stay in the client JAR."""
    checked: dict[str, dict] = {}
    visiting: set[str] = set()

    def visit(identifier: str) -> dict:
        if ':' not in identifier:
            identifier = 'minecraft:' + identifier
        if identifier.startswith(('minecraft:', 'builtin/')):
            return {}
        if identifier in checked:
            return checked[identifier]
        if identifier in visiting:
            raise AuditError(f'cyclic model parent: {identifier}')
        path = model_path(identifier)
        if path not in assets:
            raise AuditError(f'missing model: {identifier}')
        visiting.add(identifier)
        model = json.loads(assets[path], object_pairs_hook=unique_object)
        if 'loader' in model:
            raise AuditError(f'client-only loader remains: {identifier}')
        inherited = visit(model['parent']) if 'parent' in model else {}
        textures = {**inherited, **model.get('textures', {})}

        def texture(ref: str, chain: frozenset[str] = frozenset()) -> None:
            if ref.startswith('#'):
                key = ref[1:]
                if key in chain or key not in textures:
                    raise AuditError(f'unresolved texture {ref} in {identifier}')
                texture(textures[key], chain | {key})
            elif ':' in ref and not ref.startswith('minecraft:'):
                namespace, name = ref.split(':', 1)
                if f'{namespace}/textures/{name}.png' not in assets:
                    raise AuditError(f'missing texture {ref} in {identifier}')

        for ref in textures.values():
            texture(ref)
        for element in model.get('elements', []):
            for face in element.get('faces', {}).values():
                texture(face['texture'])
        visiting.remove(identifier)
        checked[identifier] = textures
        return textures

    for identifier in sorted(roots):
        visit(identifier)
    return len(checked)


def model_roots(node: dict) -> set[str]:
    if node['type'] == 'minecraft:model':
        return {node['path']}
    if node['type'] == 'minecraft:select':
        return model_roots(node['fallback']) | set().union(*(model_roots(case['model']) for case in node['cases']))
    raise AuditError(f'unsupported item model node: {node["type"]}')
