"""Export the pinned kitchen into a new, isolated CraftEngine acceptance pack.

This includes foods. Do not install alongside cookery_foods or another kitchen pack.
The manifest records remaining geometry and player-acceptance differences.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import itertools
import json
from pathlib import Path
import sys
import zipfile

import yaml

from audit_upstream import AuditError, unique_object
from cookery_models import (convert_models, geometry_part, group_indices, json_bytes,
                            model_node, model_path, model_roots, verify_models)
from export_cookery_catalog import COMMIT, export
from export_cookery_food_pack import food_items, source_assets
from cookery_recipes import read_sources, processing_recipes, crafting_recipes, recipe_report
from cookery_kitchen_displays import tea_cups, cutting_models, tea_bars, soup_models, stockpot_models
from cookery_stockpot import stockpot_recipes
from cookery_pot import pot_recipes
from cookery_millstone import millstone_furniture
from export_bamboo_tray import export as export_bamboo_tray

NAMESPACE = 'kaleidoscopecookery'
ART = 'kaleidoscope_cookery'
PACK = 'cookery_kitchen'
FACING = ('north', 'east', 'south', 'west')
BOOL = ('false', 'true')
APPLIANCES = {
    'chopping_board': {},
    'enamel_basin': {'oil_level': tuple(map(str, range(5)))},
    'kitchenware_racks': {},
    'pot': {'has_base': BOOL, 'has_oil': BOOL},
    'shawarma_spit': {'half': ('lower', 'upper'), 'powered': BOOL},
    'steamer': {'type': ('bottom', 'top', 'double'), 'has_base': BOOL, 'has_lid': BOOL},
    'stockpot': {'has_base': BOOL, 'has_chains': BOOL, 'has_lid': BOOL},
    'stove': {'lit': BOOL},
    'fruit_basket': {},
    'teapot': {'has_base': BOOL, 'has_chains': BOOL},
    'teacup_coaster': {},
    'oil_pot': {'has_oil': BOOL},
}
PENDING = [
    'Tripwire-backed appliances have vanilla selection boxes and no solid collision; exact mod voxel shapes remain pending.',
    'Waterlogging is not enabled by this acceptance pack.',
    'Teapot base and chains use extracted stockpot support geometry; the Java entity texture layout still needs a dedicated conversion.',
    'Steamer TOP is retained for old states using a translated half model; official mod has no TOP state.',
    'Imported stockpot recipes include cooking/finished surfaces and official liquid heights; legacy recipes retain existing visuals. Vanilla-client visual acceptance remains pending.',
    'Stockpot colored bubbles use vanilla dust particles; imported recipes are read-only in the recipe menu. Player carriers and workbench remainders still require acceptance.',
    'Imported pot recipes retain official stir counts and fixed outputs; source_time is metadata. Completion still follows the plugin stir animation instead of the mod cooking-time gate.',
    'Milk liquid uses a generated flat texture because the Forge milk renderer is unavailable to vanilla clients.',
    'Teapot bitmap-font bars substitute for the mod client HUD; client visual acceptance remains pending.',
    'Recipe manifest records every pending conversion; placed meal bites, protection and multiplayer acceptance remain pending.',
    'Tool durability and lid use behavior beyond existing kitchen interactions require separate parity acceptance.',
    'Millstone uses converted Java mesh parts with existing plugin anchors, fixed-angle lift and interaction hitbox; client visuals and solid collision remain pending.',
]


def read_json(assets: dict[str, bytes], path: str) -> dict:
    if path not in assets:
        raise AuditError(f'missing source asset: {path}')
    return json.loads(assets[path], object_pairs_hook=unique_object)


def item(identifier: str, model: dict, label: str) -> dict:
    return {'material': 'paper', 'data': {'item_name': '<!i>' + label}, 'model': copy.deepcopy(model)}


def add_model(assets: dict[str, bytes], name: str, model: dict) -> str:
    identifier = NAMESPACE + ':block/imported/' + name
    path = model_path(identifier)
    if path in assets:
        raise AuditError(f'duplicate generated model: {identifier}')
    assets[path] = json_bytes(model)
    return identifier


def extract_parts(assets: dict[str, bytes], items: dict) -> dict[str, str]:
    teapot = read_json(assets, model_path(ART + ':item/teapot_3d'))
    lid, body = group_indices(teapot, 'top'), group_indices(teapot, 'body')
    if sorted(lid + body) != list(range(len(teapot['elements']))):
        raise AuditError('teapot groups do not cover every source element exactly once')
    for name, indices in (('teapot_1', lid), ('teapot_2', body)):
        model = add_model(assets, name, geometry_part(teapot, indices))
        items['show:' + name] = item('show:' + name, model_node(model), name)
    parts = {}
    for kind in ('base', 'chains'):
        bare = read_json(assets, model_path(ART + ':block/stockpot'))
        whole = read_json(assets, model_path(ART + ':block/stockpot_' + kind))
        geometry = lambda element: {key: element.get(key) for key in ('from', 'to', 'rotation')}
        if list(map(geometry, whole['elements'][:len(bare['elements'])])) != list(map(geometry, bare['elements'])):
            raise AuditError(f'stockpot {kind} no longer has the expected body prefix')
        parts[kind] = add_model(assets, 'teapot_' + kind,
                               geometry_part(whole, list(range(len(bare['elements']), len(whole['elements'])))))
    cup = read_json(assets, model_path(ART + ':block/teacup/butter_tea/count1_1'))
    parts['coaster'] = add_model(assets, 'teacup_coaster', geometry_part(cup, group_indices(cup, 'base')))
    empty = read_json(assets, model_path(ART + ':block/teacup/empty_cup/count1'))
    base = empty['elements'][0]
    if base['from'] != [1, 0, 1] or base['to'] != [15, 1, 15]:
        raise AuditError('empty cup coaster geometry changed')
    cup_model = add_model(assets, 'empty_cup', geometry_part(empty, list(range(1, len(empty['elements'])))))
    items['show:empty_cup'] = item('show:empty_cup', model_node(cup_model), '空茶杯')
    return parts


def source_states(assets: dict[str, bytes], name: str) -> list[tuple[dict[str, str], dict]]:
    root = read_json(assets, f'{ART}/blockstates/{name}.json')
    if set(root) != {'variants'}:
        raise AuditError(f'unsupported blockstate structure: {name}')
    states = []
    dimensions: dict[str, set[str]] = {}
    for selector, model in root['variants'].items():
        values = dict(pair.split('=', 1) for pair in selector.split(',') if pair)
        if not isinstance(model, dict) or set(model) - {'model', 'x', 'y', 'uvlock'} or 'model' not in model:
            raise AuditError(f'unsupported model variant: {name}/{selector}')
        for key, value in values.items():
            dimensions.setdefault(key, set()).add(value)
        states.append((values, model))
    expected = {**APPLIANCES[name], 'facing': FACING}
    if name == 'enamel_basin':
        expected = {'has_lid': BOOL, 'oil_count': tuple(map(str, range(33)))}
    if name == 'steamer':
        expected.pop('type')
        expected['half'] = BOOL
    if name == 'pot':
        expected['show_oil'] = BOOL
    if name != 'stove':
        expected['waterlogged'] = BOOL
    if dimensions != {key: set(values) for key, values in expected.items()}:
        raise AuditError(f'unrecognized source state dimensions for {name}: {dimensions}')
    return states


def source_model(name: str, values: dict[str, str], states: list) -> dict:
    query = dict(values)
    if name != 'stove':
        query['waterlogged'] = 'false'
    if name == 'pot':
        query['show_oil'] = values['has_oil']
    if name == 'enamel_basin':
        level = int(query.pop('oil_level'))
        query.pop('facing')
        query.update(has_lid='true' if level == 0 else 'false', oil_count=str((0, 0, 1, 16, 32)[level]))
    if name == 'steamer':
        query['half'] = 'false' if query.pop('type') == 'double' else 'true'
    if name == 'stockpot' and query['has_chains'] == 'true':
        query['has_base'] = 'false'
    matches = [model for selector, model in states if selector == query]
    if len(matches) != 1:
        raise AuditError(f'expected one model for {name}: {query}, found {len(matches)}')
    return matches[0]


def properties(name: str) -> dict:
    props = {'facing': {'type': 'horizontal_direction', 'default': 'north'}}
    for key, values in APPLIANCES[name].items():
        if values == BOOL:
            props[key] = {'type': 'boolean', 'default': False}
        elif key == 'oil_level':
            props[key] = {'type': 'int', 'range': '0~4', 'default': 0}
        elif key == 'half':
            props[key] = {'type': 'double_block_half', 'default': 'lower'}
        elif key == 'type':
            props[key] = {'type': 'slab_type', 'default': 'bottom'}
        else:
            raise AuditError(f'unsupported plugin property: {name}/{key}')
    return props


def display(assets: dict[str, bytes], items: dict, model: str, yaw: int = 0, lift: float = 0) -> dict:
    key = 'show:kitchen_' + hashlib.sha256(model.encode()).hexdigest()[:16]
    definition = item(key, model_node(model), model)
    if key in items and items[key] != definition:
        raise AuditError(f'display key collision: {key}')
    items[key] = definition
    return {'item': key, 'yaw': yaw, 'display_context': 'none', 'translation': f'0,{lift},0'}


def block_definition(assets: dict[str, bytes], items: dict, parts: dict, name: str) -> dict:
    key = NAMESPACE + ':' + name
    behavior = {'pot': 'cooking_pot', 'enamel_basin': 'cooking_enamel_basin'}.get(name, name)
    behaviors = [{'type': NAMESPACE + ':' + behavior}]
    loot = {'pools': [{'rolls': 1, 'entries': [{'type': 'item', 'item': key}]}]}
    if name in ('teapot', 'oil_pot'):
        loot = {'pools': []}  # The controller drops the filled item, including its saved contents.
    if name == 'shawarma_spit':
        behaviors.append({'type': 'double_high_block'})
        loot['pools'][0]['conditions'] = [{'type': 'match_block_property', 'properties': {'half': 'lower'}}]
    if name == 'steamer':
        behaviors.append({'type': NAMESPACE + ':stacked_extra_drop',
                          'extra_drops': [{'property': 'type', 'value': 'double', 'item': key, 'amount': 1}]})
    dimensions = {'facing': FACING, **APPLIANCES[name]}
    source = [] if name in ('teapot', 'teacup_coaster') else source_states(assets, name)
    appearances, variants = {}, {}
    for combination in itertools.product(*dimensions.values()):
        values = dict(zip(dimensions, combination, strict=True))
        selector = ','.join(f'{k}={v}' for k, v in values.items())
        appearance = 's' + str(len(appearances))
        yaw = FACING.index(values['facing']) * 90
        low = name in ('chopping_board', 'enamel_basin', 'kitchenware_racks', 'teacup_coaster')
        state = {'auto_state': {'type': 'lower_tripwire' if low else 'higher_tripwire',
                               'id': 'cookery_kitchen_clear'}, 'transparent': True}
        if name == 'teapot':
            support = 'chains' if values['has_chains'] == 'true' else 'base' if values['has_base'] == 'true' else None
            if support:
                state['entity_renderer'] = display(assets, items, parts[support], yaw)
        elif name == 'teacup_coaster':
            state['entity_renderer'] = display(assets, items, parts['coaster'], yaw)
        else:
            model = source_model(name, values, source)
            if name == 'stove':
                state = {'auto_state': 'note_block', 'model': {('path' if k == 'model' else k): v for k, v in model.items()}}
            else:
                if model.get('x', 0) != 0:
                    raise AuditError(f'unhandled vertical rotation: {name}/{selector}')
                state['entity_renderer'] = display(assets, items, model['model'], model.get('y', yaw),
                                                   .5 if name == 'steamer' and values['type'] == 'top' else 0)
        appearances[appearance] = state
        variants[selector] = {'appearance': appearance}
        if name == 'stove' and values['lit'] == 'true':
            variants[selector]['settings'] = {'luminance': 13}
    return {'settings': {'item': key, 'hardness': 1, 'resistance': 1, 'push_reaction': 'block',
                         'can_occlude': False, 'is_suffocating': False, 'is_view_blocking': False},
            'behavior': behaviors, 'loot': loot,
            'states': {'properties': properties(name), 'appearances': appearances, 'variants': variants}}


def tools(items: dict, definitions: dict, names: dict) -> None:
    models = {'kitchen_shovel_no_oil': 'kitchen_shovel_no_oil', 'kitchen_shovel_has_oil': 'kitchen_shovel_has_oil',
              'oil': 'oil', 'oil_pot_empty': 'pot_no_oil', 'stockpot_lid': 'stockpot_lid_normal',
              'recipe_item_no_recipe': 'recipe_item_no_recipe', 'recipe_item_has_recipe': 'recipe_item_has_recipe'}
    for metal in ('iron', 'gold', 'diamond', 'netherite'):
        models[metal + '_kitchen_knife'] = metal + '_kitchen_knife'
    for name, model in models.items():
        key = NAMESPACE + ':' + name
        label_key = name if not name.startswith('kitchen_shovel') else 'kitchen_shovel'
        items[key] = item(key, definitions[ART + ':item/' + model], names.get(f'item.{ART}.{label_key}', name))
        if name.endswith('_kitchen_knife'):
            metal = name.split('_')[0]
            items[key]['material'] = ('golden' if metal == 'gold' else metal) + '_sword'


def sound_aliases(assets: dict[str, bytes]) -> int:
    sounds = read_json(assets, ART + '/sounds.json')
    aliases = {}
    for prefix, event, count in (('stockpot', 'block.stockpot', 7), ('teapot', 'block.teapot.processing', 5),
                                 ('millstone', 'block.millstone', 6)):
        source = sounds[event]['sounds']
        if len(source) != count:
            raise AuditError(f'changed sound variants: {event}')
        for index, ref in enumerate(source):
            namespace, path = ref.split(':', 1)
            if f'{namespace}/sounds/{path}.ogg' not in assets:
                raise AuditError(f'missing sound: {ref}')
            aliases[f'{prefix}_{index}'] = {'sounds': [ref], 'subtitle': sounds[event]['subtitle']}
    assets[NAMESPACE + '/sounds.json'] = json_bytes(aliases)
    return len(aliases)


def build(source: Path) -> tuple[dict[str, bytes], dict]:
    catalog = export(source, COMMIT)
    assets, licenses = source_assets(source)
    definitions, converted = convert_models(assets)
    items = food_items(catalog, assets, definitions)
    names = read_json(assets, ART + '/lang/zh_cn.json')
    parts = extract_parts(assets, items)
    # Bamboo tray is a first-class kitchen appliance. Keep its official geometry
    # and merge the pinned 28-recipe export into the same immutable config.
    tray_base = ART + ':block/bamboo_tray/base'
    tray_stand = ART + ':block/bamboo_tray/stand'
    for model in (tray_base, tray_stand):
        if model not in definitions:
            raise AuditError(f'missing bamboo tray model: {model}')
    tools(items, definitions, names)
    blocks = {}
    for name in APPLIANCES:
        key = NAMESPACE + ':' + name
        model = model_node(parts['coaster']) if name == 'teacup_coaster' else definitions[ART + ':item/' + ('pot_has_oil' if name == 'oil_pot' else name)]
        items[key] = item(key, model, names.get(f'block.{ART}.{name}', '茶杯垫' if name == 'teacup_coaster' else name))
        placement = {'shawarma_spit': 'double_high_block_item', 'teapot': NAMESPACE + ':teapot_item',
                     'oil_pot': NAMESPACE + ':oil_pot_item'}.get(name, 'block_item')
        items[key]['behavior'] = {'type': placement, 'block': key}
        if name in ('oil_pot', 'teapot'):
            items[key]['data']['max_stack_size'] = 1
        if name == 'oil_pot':
            items[key]['data']['max_damage'] = 256
        blocks[key] = block_definition(assets, items, parts, name)
    tray_key = NAMESPACE + ':bamboo_tray'
    items[tray_key] = item(tray_key, definitions[tray_base], names.get('block.' + ART + '.bamboo_tray', '竹筛'))
    items[tray_key]['behavior'] = {'type': 'block_item', 'block': tray_key}
    blocks[tray_key] = {
        'settings': {'item': tray_key, 'hardness': 0.5, 'resistance': 0.5,
                     'push_reaction': 'destroy', 'can_occlude': False,
                     'is_suffocating': False, 'is_view_blocking': False},
        'behavior': {'type': tray_key},
        'loot': {'pools': [{'rolls': 1, 'entries': [{'type': 'item', 'item': tray_key}]}]},
        'states': {'properties': {'stand': {'type': 'boolean', 'default': False}},
                   'appearances': {
                       'base': {'auto_state': {'type': 'lower_tripwire', 'id': 'cookery_kitchen_bamboo_tray'},
                                'model': definitions[tray_base]},
                       'stand': {'auto_state': {'type': 'lower_tripwire', 'id': 'cookery_kitchen_bamboo_tray'},
                                 'model': definitions[tray_stand]}},
                   'variants': {'stand=false': {'appearance': 'base'},
                                'stand=true': {'appearance': 'stand'}}}
    }
    furniture, millstone_model = millstone_furniture(source, assets, items, definitions)
    sounds = sound_aliases(assets)
    sources = read_sources(source)
    recipes, records, required = processing_recipes(sources)
    tray_output = source.parent / '.bamboo-tray-export'
    export_bamboo_tray(source, COMMIT, tray_output)
    tray_doc = yaml.safe_load((tray_output / 'pack/configuration/bamboo_tray_recipes.yml').read_text())
    tray_optional = yaml.safe_load((tray_output / 'optional/bamboo_tray_tea.yml').read_text())
    recipes['bamboo_tray_recipes'] = {**tray_doc.get('bamboo_tray_recipes', {}), **tray_optional.get('bamboo_tray_recipes', {})}
    recipes['stockpot_recipes'], stockpot_items = stockpot_recipes(sources, records)
    required.update(stockpot_items)
    recipes['pot_recipes'], pot_items = pot_recipes(sources, records)
    required.update(pot_items)
    required.add(NAMESPACE + ':dried_tea_leaves')
    registered = {entry['pluginId'] for entry in catalog['entries']}
    for key in sorted(required - set(items)):
        if key.startswith('minecraft:'):
            continue
        name = key.split(':', 1)[1]
        model = ART + ':item/' + name
        if key not in registered or model not in definitions:
            raise AuditError(f'unregistered recipe ingredient or missing model: {key}')
        items[key] = item(key, definitions[model], names.get(f'item.{ART}.{name}', name))
    recipes['recipes'] = crafting_recipes(sources, records, set(items))
    recipe_manifest = recipe_report(sources, records, recipes)
    cups = tea_cups(assets, items, catalog)
    cutting_models(assets, items, records)
    images, liquids = tea_bars(assets)
    soups = soup_models(assets, items)
    stockpot_models(assets, items, records)
    roots = set().union(*(model_roots(definition['model']) for definition in items.values()))
    for definition in blocks.values():
        roots.update(state['model']['path'] for state in definition['states']['appearances'].values() if 'model' in state)
    checked = verify_models(assets, roots)
    config = {'items': items, 'blocks': blocks, 'furniture': furniture, **recipes, 'tea_cup': cups,
              'images': images, 'teapot_liquid': liquids, 'stock_food_raw': soups}
    bundle = {f'{PACK}/resourcepack/assets/{path}': content for path, content in assets.items()}
    bundle.update({f'{PACK}/licenses/{path}': content for path, content in licenses.items()})
    bundle[f'{PACK}/pack.yml'] = yaml.safe_dump({'namespace': NAMESPACE, 'description': 'Pinned Cookery kitchen acceptance pack', 'version': '1'}).encode()
    bundle[f'{PACK}/configuration/kitchen.yml'] = yaml.safe_dump(config, allow_unicode=True, sort_keys=False).encode('utf-8')
    bundle[f'{PACK}/recipe-parity.json'] = json_bytes(recipe_manifest)
    manifest = {'sourceCommit': COMMIT, 'target': {'minecraft': '1.21.11', 'craftEngine': '26.9.1'},
                'items': len(items), 'blocks': len(blocks), 'convertedPerspectives': converted, 'checkedModels': checked,
                'furniture': len(furniture), 'millstoneModel': millstone_model,
                'soundAliases': sounds, 'states': {key: len(value['states']['variants']) for key, value in blocks.items()},
                'recipeSources': recipe_manifest['sourceCount'], 'recipeStatuses': recipe_manifest['statusCounts'],
                'recipes': recipe_manifest['importedSections'], 'accurateAppliances': recipe_manifest['accurateAppliances'],
                'teaCups': len(cups), 'images': len(images),
                'soupBases': len(soups['liquid']),
                'pending': PENDING, 'playerFlow': 'NOT_TESTED', 'clientResourcePack': 'NOT_TESTED',
                'files': {path: hashlib.sha256(content).hexdigest() for path, content in sorted(bundle.items())}}
    bundle['manifest.json'] = json_bytes(manifest)
    return bundle, manifest


def write_archive(output: Path, bundle: dict[str, bytes]) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'x', zipfile.ZIP_DEFLATED) as archive:
        for path, content in sorted(bundle.items()):
            entry = zipfile.ZipInfo(path, date_time=(2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, content)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.output.exists() or args.output.resolve().is_relative_to(args.source.resolve()):
            raise AuditError('output must be new and outside the reference checkout')
        bundle, manifest = build(args.source)
        write_archive(args.output, bundle)
        print(json.dumps({key: value for key, value in manifest.items() if key not in ('files', 'pending')}, ensure_ascii=False))
        print('sha256=' + hashlib.sha256(args.output.read_bytes()).hexdigest())
    except (OSError, ValueError) as exception:
        print(f'Kitchen export failed: {exception}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
