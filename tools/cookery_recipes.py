"""Read every pinned recipe and explicitly account for the supported kitchen subset."""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
from typing import Any

from audit_upstream import AuditError, git_text, read_blobs, unique_object
from export_bamboo_tray import KEY, positive_int
from export_cookery_catalog import COMMIT

ART = 'kaleidoscope_cookery'
PLUGIN = 'kaleidoscopecookery'
VANILLA_ALIASES = {'minecraft:chain': 'minecraft:iron_chain'}
RESOURCE = re.compile(r'src/(?:main|generated)/resources/data/([^/]+)/(recipes|tags/items)/(.+)\.json\Z')
SCHEMAS = {
    'bamboo_tray': ({'duration', 'ingredient', 'result', 'subtype'}, set()),
    'chopping_board': ({'cut_count', 'ingredient', 'model_id', 'result'}, set()),
    'flex_pot': ({'ingredients', 'result', 'stir_fry_count', 'time'}, {'carrier'}),
    'pot': ({'ingredients', 'result', 'stir_fry_count', 'time'}, {'carrier'}),
    'flex_stockpot': ({'ingredients', 'result', 'soup_base', 'time', 'cooking_bubble_color',
                       'cooking_texture', 'finished_bubble_color', 'finished_texture'}, {'carrier', 'empty_carrier'}),
    'stockpot': ({'ingredients', 'result', 'soup_base', 'time', 'cooking_bubble_color',
                  'cooking_texture', 'finished_bubble_color', 'finished_texture'}, {'carrier', 'empty_carrier'}),
    'millstone': ({'ingredient', 'result'}, set()),
    'rice_bowl': ({'ingredient', 'result', 'category'}, set()),
    'steamer': ({'ingredient', 'result', 'cook_tick'}, set()),
    'teapot': ({'ingredient', 'ingredient_count', 'result', 'tea_fluid', 'time'}, set()),
}
VANILLA_SCHEMAS = {
    'crafting_shaped': ({'category', 'key', 'pattern', 'result', 'show_notification'}, set()),
    'crafting_shapeless': ({'category', 'ingredients', 'result'}, set()),
    'smithing_transform': ({'addition', 'base', 'template', 'result'}, set()),
    **{kind: ({'category', 'cookingtime', 'experience', 'ingredient', 'result'}, set())
       for kind in ('smelting', 'smoking', 'campfire_cooking')},
}
PENDING = {
    'bamboo_tray': 'Separate bamboo-tray fixture; kitchen pack does not yet include its block.',
    'millstone': 'Millstone block and recipe resources are not included in this kitchen pack.',
    'rice_bowl': 'Rice serving handler and carrier conservation require a dedicated conversion.',
    'pot': 'Exact-priority recipes require reconciliation with the existing cosine matcher.',
    'flex_pot': 'Ingredient alternatives and proportional collisions require explicit reconciliation.',
    'stockpot': 'Soup base, exact-priority matching and per-recipe liquid visuals remain pending.',
    'flex_stockpot': 'Soup base, proportional collisions and per-recipe liquid visuals remain pending.',
    'smithing_transform': 'Smithing item components and enchantment preservation require acceptance.',
    'smelting': 'Furnace recipe import remains pending.',
    'smoking': 'Smoker recipe import remains pending.',
    'campfire_cooking': 'Campfire recipe import remains pending.',
    'crafting_shaped': 'Recipe references content not yet included in this kitchen pack.',
    'crafting_shapeless': 'Recipe references content not yet included in this kitchen pack.',
}
MILLSTONE_MISSING_TAGS = frozenset(('forge:gravel', 'forge:sand/red', 'forge:sand/colorless'))


def identifier(value: Any) -> str:
    if not isinstance(value, str) or not KEY.fullmatch(value) or '..' in value:
        raise AuditError(f'invalid resource identifier: {value!r}')
    return value


def mapped(value: str) -> str:
    namespace, name = identifier(value).split(':', 1)
    if namespace == 'minecraft':
        return VANILLA_ALIASES.get(value, value)
    if namespace == ART:
        return PLUGIN + ':' + name
    raise AuditError(f'unmapped custom item: {value}')


def ingredient(value: Any) -> dict:
    if not isinstance(value, dict) or set(value) not in ({'item'}, {'tag'}):
        raise AuditError(f'unsupported ingredient structure: {value!r}')
    identifier(next(iter(value.values())))
    return value


def product(value: Any) -> dict:
    if isinstance(value, str):
        return {'item': identifier(value)}
    if not isinstance(value, dict) or set(value) not in ({'item'}, {'item', 'count'}, {'item', 'nbt'}):
        raise AuditError(f'unsupported result structure: {value!r}')
    identifier(value['item'])
    positive_int(value.get('count', 1), 'result.count')
    if 'nbt' in value and (not isinstance(value['nbt'], str) or not value['nbt']):
        raise AuditError('result.nbt must be a non-empty string')
    return value


def validate_recipe(doc: dict) -> None:
    namespace, kind = identifier(doc.get('type')).split(':', 1)
    schema = (SCHEMAS if namespace == ART else VANILLA_SCHEMAS if namespace == 'minecraft' else {}).get(kind)
    if schema is None:
        raise AuditError(f'unknown recipe type: {doc["type"]}')
    required, optional = schema
    fields = set(doc) - {'type'}
    if not required <= fields or fields - required - optional:
        raise AuditError(f'{doc["type"]}: unrecognized recipe fields {sorted(fields)}')
    for field in ('ingredient', 'carrier', 'addition', 'base', 'template'):
        if field in doc:
            ingredient(doc[field])
    if 'ingredients' in doc:
        if not isinstance(doc['ingredients'], list) or not doc['ingredients']:
            raise AuditError('ingredients must be a non-empty list')
        for value in doc['ingredients']:
            ingredient(value)
    if 'key' in doc:
        for symbol, value in doc['key'].items():
            if not isinstance(symbol, str) or len(symbol) != 1 or symbol == ' ':
                raise AuditError(f'invalid crafting symbol: {symbol!r}')
            ingredient(value)
        pattern = doc['pattern']
        if (not isinstance(pattern, list) or not 1 <= len(pattern) <= 3
                or any(not isinstance(row, str) or not 1 <= len(row) <= 3 for row in pattern)
                or len({len(row) for row in pattern}) != 1
                or set(''.join(pattern)) - {' '} != set(doc['key'])):
            raise AuditError('crafting pattern and key differ')
    outputs = doc['result'] if kind == 'chopping_board' else [doc['result']]
    if not isinstance(outputs, list) or not outputs:
        raise AuditError('result must not be empty')
    for value in outputs:
        product(value)
    for field in ('duration', 'cut_count', 'stir_fry_count', 'time', 'cook_tick', 'ingredient_count', 'cookingtime'):
        if field in doc:
            positive_int(doc[field], field)
    for field in ('tea_fluid', 'soup_base', 'model_id', 'cooking_texture', 'finished_texture'):
        if field in doc:
            identifier(doc[field])
    for field in ('cooking_bubble_color', 'finished_bubble_color'):
        if field in doc and (type(doc[field]) is not int or not 0 <= doc[field] <= 0xFFFFFF):
            raise AuditError(f'{field}: expected RGB integer')
    for field in ('show_notification', 'empty_carrier'):
        if field in doc and type(doc[field]) is not bool:
            raise AuditError(f'{field}: expected boolean')
    if 'subtype' in doc and doc['subtype'] not in ('drying', 'wetting'):
        raise AuditError(f'unknown bamboo-tray subtype: {doc["subtype"]}')


class MissingTag(AuditError):
    """Valid external data dependency absent from the pinned resource tree."""


@dataclass(frozen=True)
class RecipeSources:
    recipes: dict[str, dict]
    tags: dict[str, dict]
    origins: dict[str, dict]
    files: dict[str, str]

    def expand(self, raw: dict, chain: tuple[str, ...] = (), vanilla_tags: bool = False) -> list[str]:
        ingredient(raw)
        if 'item' in raw:
            return [mapped(raw['item'])]
        tag = raw['tag']
        if tag in chain:
            raise AuditError(f'cyclic ingredient tag: {chain + (tag,)}')
        if tag not in self.tags:
            if vanilla_tags and tag.startswith('minecraft:'):
                return ['#' + tag]
            raise MissingTag(f'required tag absent from pinned resources: {tag}')
        values = []
        for entry in self.tags[tag]['values']:
            ref = entry if isinstance(entry, str) else entry['id']
            if not ref.startswith(('#', 'minecraft:', ART + ':')):
                raise MissingTag(f'external tag alternative requires a mapping: {tag}/{ref}')
            # Keep optional alternatives too; a partially available set is not a complete recipe.
            values.extend(self.expand({'tag': ref[1:]} if ref.startswith('#') else {'item': ref},
                                      chain + (tag,), vanilla_tags))
        return list(dict.fromkeys(values))


def read_sources(source: Path) -> RecipeSources:
    paths = [p for p in git_text(source, 'ls-tree', '-r', '--name-only', COMMIT).splitlines() if RESOURCE.fullmatch(p)]
    recipes, tags, origins, files = {}, {}, {}, {}
    for path, raw in read_blobs(source, COMMIT, paths).items():
        namespace, kind, name = RESOURCE.fullmatch(path).groups()
        key = identifier(namespace + ':' + name)
        doc = json.loads(raw, object_pairs_hook=unique_object)
        if not isinstance(doc, dict):
            raise AuditError(f'{path}: expected JSON object')
        target = recipes if kind == 'recipes' else tags
        if key in target:
            raise AuditError(f'duplicate {kind} resource: {key}')
        if kind == 'recipes':
            validate_recipe(doc)
            origins[key] = {'path': path, 'sha256': hashlib.sha256(raw).hexdigest()}
        else:
            if (set(doc) - {'values', 'replace'} or not isinstance(doc.get('values'), list)
                    or ('replace' in doc and type(doc['replace']) is not bool)):
                raise AuditError(f'{path}: unrecognized item tag structure')
            for entry in doc['values']:
                if isinstance(entry, str):
                    identifier(entry.removeprefix('#'))
                elif (isinstance(entry, dict) and set(entry) == {'id', 'required'}
                      and type(entry['required']) is bool):
                    identifier(entry['id'].removeprefix('#'))
                else:
                    raise AuditError(f'{path}: unsupported tag value {entry!r}')
        target[key] = doc
        files[path] = hashlib.sha256(raw).hexdigest()
    if not recipes:
        raise AuditError('no recipe resources found')
    return RecipeSources(recipes, tags, origins, files)


def processing_recipes(sources: RecipeSources) -> tuple[dict, list[dict], set[str]]:
    config = {'teapot_result': {}, 'accurate_foods': {}, 'chopping_board_raws': {}}
    records, required = [], set()
    for source_id, doc in sorted(sources.recipes.items()):
        kind = doc['type'].split(':', 1)[1]
        row = {'sourceCommit': COMMIT, 'sourceId': source_id, 'type': doc['type'],
               'source': sources.origins[source_id], 'original': doc, 'pluginIds': [],
               'status': 'pending', 'reason': PENDING.get(kind, '')}
        records.append(row)
        if doc['type'] not in {ART + ':' + t for t in ('teapot', 'steamer', 'chopping_board', 'millstone')}:
            continue
        validate_recipe(doc)
        try:
            inputs = sources.expand(doc['ingredient'])
        except MissingTag as failure:
            if kind != 'millstone' or str(failure) not in {
                    'required tag absent from pinned resources: ' + tag for tag in MILLSTONE_MISSING_TAGS}:
                raise
            row['reason'] = str(failure)
            continue
        if not inputs:
            raise AuditError(f'{source_id}: ingredient tag resolved to no items')
        outputs = doc['result'] if kind == 'chopping_board' else [doc['result']]
        if any('nbt' in output for output in outputs):
            raise AuditError(f'{source_id}: processing output NBT requires an explicit save-format converter')
        required.update(inputs)
        required.update(mapped(p['item']) for p in outputs)
        for raw in inputs:
            key = mapped(source_id) + ('/from/' + raw.replace(':', '/') if len(inputs) > 1 else '')
            first = outputs[0]
            output = mapped(first['item']) + ' ' + str(first.get('count', 1))
            if kind == 'teapot':
                if doc['tea_fluid'] not in ('minecraft:water', 'minecraft:milk', 'minecraft:lava'):
                    raise AuditError(f'{source_id}: unsupported tea fluid')
                section = 'teapot_result'
                converted = {'fluid': doc['tea_fluid'], 'require': raw + ' ' + str(doc['ingredient_count']),
                             'result': output, 'time': doc['time']}
            elif kind in ('steamer', 'millstone'):
                section = 'accurate_foods'
                converted = {'require': raw, 'result': mapped(first['item']), 'result_count': first.get('count', 1),
                             'cook': kind.upper()}
                converted.update({'cooking_time': doc['cook_tick']} if kind == 'steamer' else {'rotations': 1})
            else:
                section = 'chopping_board_raws'
                model_namespace, model_name = doc['model_id'].split(':', 1)
                if model_namespace != ART:
                    raise AuditError(f'{source_id}: external cutting model')
                converted = {'require': raw, 'stage': doc['cut_count'], 'values': 'show:chopping/' + model_name,
                             'mode': 'single_extra' if len(outputs) > 1 else 'single', 'result': output}
                if len(outputs) > 1:
                    converted['extra'] = [mapped(p['item']) + ' ' + str(p.get('count', 1)) + ' 100' for p in outputs[1:]]
            if key in config[section]:
                raise AuditError(f'duplicate converted recipe: {key}')
            config[section][key] = converted
            row['pluginIds'].append(key)
        row.update(status='imported', section=section, reason='Existing plugin controller and save semantics retained; player acceptance pending.')
        if kind == 'millstone':
            row['reason'] = 'One revolution for new imported inputs; legacy default rotations and saved plans retained. Player acceptance pending.'
    return config, records, required


def crafting_recipes(sources: RecipeSources, records: list[dict], available: set[str]) -> dict:
    """Import only recipes whose complete ingredient sets and results are available."""
    recipes = {}
    for row in records:
        doc = row['original']
        if doc['type'] not in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless'):
            continue
        if 'nbt' in doc['result']:
            row['reason'] = 'Source embeds Forge block-entity NBT; conversion to plugin saves is pending.'
            continue
        output = mapped(doc['result']['item'])
        if not output.startswith('minecraft:') and output not in available:
            continue
        try:
            inputs = doc.get('key', doc.get('ingredients'))
            expanded = [sources.expand(value, vanilla_tags=True) for value in
                        (inputs.values() if isinstance(inputs, dict) else inputs)]
        except MissingTag as exception:
            row['reason'] = str(exception)
            continue
        missing = sorted({value for group in expanded for value in group
                          if not value.startswith(('minecraft:', '#minecraft:')) and value not in available})
        if missing or any(not group for group in expanded):
            row['reason'] = 'Uninstalled ingredients or empty tag; no alternatives silently removed: ' + ', '.join(missing)
            continue
        choices = [group[0] if len(group) == 1 else group for group in expanded]
        key = PLUGIN + ':upstream/' + row['sourceId'].replace(':', '/')
        converted = {'type': doc['type'].removeprefix('minecraft:crafting_'),
                     'category': doc['category'], 'result': {'id': output, 'count': doc['result'].get('count', 1)}}
        if 'pattern' in doc:
            converted.update(pattern=doc['pattern'], ingredients=dict(zip(inputs, choices, strict=True)))
        else:
            converted['ingredients'] = choices
        recipes[key] = converted
        row.update(status='imported', section='recipes', pluginIds=[key],
                   reason='All ingredient alternatives retained; workbench/player acceptance pending.')
    return recipes


def cooking_recipes(sources: RecipeSources, records: list[dict]) -> tuple[dict[str, dict], set[str]]:
    """Import vanilla furnace-family recipes whose item inputs are directly portable."""
    recipes, required = {}, set()
    type_names = {
        'minecraft:smelting': 'smelting',
        'minecraft:smoking': 'smoking',
        'minecraft:campfire_cooking': 'campfire-cooking',
    }
    for row in records:
        recipe_type = row['type']
        if recipe_type not in type_names:
            continue
        doc = row['original']
        ingredient_value = doc['ingredient']
        if 'tag' in ingredient_value:
            row['reason'] = 'Cooking ingredient tag requires an explicit CraftEngine tag mapping.'
            continue
        source_ingredient = sources.expand(ingredient_value)
        if len(source_ingredient) != 1:
            raise AuditError(f'{row["sourceId"]}: cooking ingredient expanded to multiple values')
        result = doc['result']
        result_item = result if isinstance(result, str) else result['item']
        result_count = 1 if isinstance(result, str) else result.get('count', 1)
        result_id = mapped(result_item)
        input_id = source_ingredient[0]
        key = PLUGIN + ':upstream/' + row['sourceId'].replace(':', '/')
        if key in recipes:
            raise AuditError(f'duplicate converted recipe: {key}')
        recipes[key] = {
            'type': type_names[recipe_type],
            'category': doc['category'],
            'experience': doc['experience'],
            'time': doc['cookingtime'],
            'ingredient': input_id,
            'result': {'id': result_id, 'count': result_count},
        }
        required.update((input_id, result_id))
        row.update(status='imported', section='recipes', pluginIds=[key],
                   reason='Vanilla furnace recipe converted with source time and experience; player acceptance pending.')
    return recipes, required


def recipe_report(sources: RecipeSources, records: list[dict], config: dict) -> dict:
    return {'sourceCommit': COMMIT, 'sourceFiles': sources.files, 'recipes': records,
            'targetItemAliases': VANILLA_ALIASES,
            'sourceCount': len(records), 'statusCounts': dict(sorted(Counter(r['status'] for r in records).items())),
            'sourceTypes': dict(sorted(Counter(r['type'] for r in records).items())),
            'importedSections': {key: len(value) for key, value in config.items()},
            'accurateAppliances': dict(sorted(Counter(row['cook'] for row in config.get('accurate_foods', {}).values()).items())),
            'playerFlow': 'NOT_TESTED'}
