"""Adapt pinned cup and cutting geometry to the existing display-entity positions."""

from __future__ import annotations

import json
import struct
import zlib

from audit_upstream import AuditError, unique_object
from cookery_models import geometry_part, json_bytes, model_node, model_path
from cookery_recipes import ART, PLUGIN


def source_model(assets: dict[str, bytes], identifier: str) -> dict:
    path = model_path(identifier)
    if path not in assets:
        raise AuditError(f'missing processing display: {identifier}')
    return json.loads(assets[path], object_pairs_hook=unique_object)


def display_item(assets: dict[str, bytes], items: dict, identifier: str, model: dict, tint: int | None = None) -> None:
    target = PLUGIN + ':block/imported/' + identifier.split(':', 1)[1]
    path = model_path(target)
    if path in assets or identifier in items:
        raise AuditError(f'duplicate processing display: {identifier}')
    assets[path] = json_bytes(model)
    node = model_node(target)
    if tint is not None:
        node['tints'] = [{'type': 'minecraft:constant', 'value': tint}]
    items[identifier] = {'material': 'paper', 'data': {'item_name': '<!i>' + identifier}, 'model': node}


def tea_cups(assets: dict[str, bytes], items: dict, catalog: dict) -> dict:
    cups = {}
    for entry in catalog['entries']:
        if entry['tea'] is None:
            continue
        name = entry['sourceId'].split(':', 1)[1]
        source = source_model(assets, ART + ':block/teacup/' + name + '/count1_1')
        base = source['elements'][0]
        if base['from'] != [1, 0, 1] or base['to'] != [15, 1, 15]:
            raise AuditError(f'{name}: unknown coaster geometry')
        cup = list(range(1, len(source['elements'])))
        key = 'show:teacup/' + name
        display_item(assets, items, key, geometry_part(source, cup))
        cups[entry['pluginId']] = {'item': entry['pluginId'], 'display_model': key}
    return cups


def cutting_models(assets: dict[str, bytes], items: dict, records: list[dict]) -> None:
    for row in records:
        if row.get('section') != 'chopping_board_raws':
            continue
        doc = row['original']
        name = doc['model_id'].split(':', 1)[1]
        for stage in range(doc['cut_count']):
            key = f'show:chopping/{name}/{stage}'
            if key in items:
                continue
            source = source_model(assets, ART + f':chopping_board/{name}/{stage}')
            display_item(assets, items, key, geometry_part(source, list(range(len(source['elements'])))))
        # The controller produces on the final cut; the official final frame is not a saved stage.
        source_model(assets, ART + f':chopping_board/{name}/{doc["cut_count"]}')


def png(width: int, rows: list[bytes]) -> bytes:
    if not rows or any(len(row) != width * 4 for row in rows):
        raise AuditError('invalid RGBA image dimensions')

    def chunk(name: bytes, payload: bytes) -> bytes:
        return struct.pack('>I', len(payload)) + name + payload + struct.pack('>I', zlib.crc32(name + payload))

    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, len(rows), 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(b''.join(b'\x00' + row for row in rows), 9)) + chunk(b'IEND', b''))


def tea_bars(assets: dict[str, bytes]) -> tuple[dict, dict]:
    # The mod has no bitmap-font bar. These small glyphs are the vanilla-client HUD substitute.
    colors = {'left': (184, 178, 162), 'right': (184, 178, 162), 'empty': (42, 42, 42),
              'water': (63, 118, 228), 'lava': (255, 152, 56), 'milk': (255, 244, 214)}
    images = {}
    for name, color in colors.items():
        rows = [bytes((*((184, 178, 162) if y in (0, 7) else color), 255)) * 3 for y in range(8)]
        path = f'{PLUGIN}/textures/font/teapot/{name}.png'
        if path in assets:
            raise AuditError(f'generated font texture collision: {path}')
        assets[path] = png(3, rows)
        images[PLUGIN + ':teapot/' + name] = {'height': 8, 'ascent': 7, 'font': PLUGIN + ':teapot',
                                             'file': PLUGIN + ':font/teapot/' + name + '.png'}
    liquids = {}
    for name in ('water', 'lava', 'milk'):
        liquids['minecraft:' + name] = {'display_name': 'item.minecraft.' + name + '_bucket',
            **{'bar_' + part: PLUGIN + ':teapot/' + part for part in ('left', 'right', 'empty')},
            'bar_full': PLUGIN + ':teapot/' + name}
    return images, liquids


def soup_models(assets: dict[str, bytes], items: dict) -> dict:
    milk = PLUGIN + ':block/imported/milk'
    assets[f'{PLUGIN}/textures/block/imported/milk.png'] = png(1, [bytes((255, 244, 214, 255))])
    surfaces = {'water': ('minecraft:block/water_still', 0x3F76E4),
                'lava': ('minecraft:block/lava_still', None), 'milk': (milk, None),
                'finished': (ART + ':stockpot/default_finished', None)}
    for name, (texture, tint) in surfaces.items():
        # StockpotElement spawns at (.3, .9, .3), in HEAD context. Correct only these generated models.
        model = {'textures': {'surface': texture},
                 'display': {'head': {'translation': [3.2, 0, 3.2]}},
                 'elements': [{'from': [3, -.32, 3], 'to': [13, -.32, 13],
                               'faces': {'up': {'uv': [0, 0, 10, 10], 'texture': '#surface',
                                                **({'tintindex': 0} if tint is not None else {})}}}]}
        display_item(assets, items, 'show:stove_' + name, model, tint)
    buckets = ('water', 'lava', 'milk', 'axolotl', 'cod', 'salmon', 'tropical_fish', 'pufferfish', 'tadpole')
    return {'liquid': [{'item': 'minecraft:' + name + '_bucket',
                        'show': 'show:stove_' + (name if name in ('water', 'lava', 'milk') else 'water')}
                       for name in buckets]}


def stockpot_models(assets: dict[str, bytes], items: dict, records: list[dict]) -> None:
    from cookery_stockpot import surface_id
    textures = {row['original'][field] for row in records if row.get('section') == 'stockpot_recipes'
                for field in ('cooking_texture', 'finished_texture')}
    for texture in sorted(textures):
        namespace, path = texture.split(':', 1)
        if f'{namespace}/textures/{path}.png' not in assets:
            raise AuditError(f'missing stockpot surface: {texture}')
        model = {'textures': {'surface': texture},
                 'display': {'head': {'translation': [3.2, 0, 3.2]}},
                 'elements': [{'from': [3, -.32, 3], 'to': [13, -.32, 13],
                               'faces': {'up': {'uv': [0, 0, 10, 10], 'texture': '#surface'}}}]}
        display_item(assets, items, surface_id(texture), model)
