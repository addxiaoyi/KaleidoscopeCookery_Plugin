"""Translate the pinned Java millstone mesh into the existing furniture displays."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import itertools
from pathlib import Path
import re

from audit_upstream import AuditError, read_blobs, uncomment_java
from export_cookery_catalog import COMMIT, arguments, chain, number
from cookery_kitchen_displays import display_item

MODEL_SOURCE = 'src/main/java/com/github/ysbbbbbb/kaleidoscopecookery/client/model/MillstoneModel.java'
ART = 'kaleidoscope_cookery'
PLUGIN = 'kaleidoscopecookery'


@dataclass(frozen=True)
class Cube:
    uv: tuple[float, ...]
    origin: tuple[float, ...]
    size: tuple[float, ...]
    inflation: float


@dataclass(frozen=True)
class Part:
    parent: str
    offset: tuple[float, ...]
    rotation: tuple[float, ...]
    cubes: tuple[Cube, ...]


def parse_mesh(source: str) -> dict[str, Part]:
    text = uncomment_java(source)
    match = re.search(r'public static LayerDefinition createBodyLayer\(\)\s*\{([^{}]+)\}', text)
    if not match:
        raise AuditError('millstone: unrecognized model factory')
    statements = [row.strip() for row in match[1].split(';') if row.strip()]
    if statements[:2] != ['MeshDefinition meshdefinition = new MeshDefinition()',
                          'PartDefinition partdefinition = meshdefinition.getRoot()']:
        raise AuditError('millstone: unrecognized mesh root')
    if statements[-1] != 'return LayerDefinition.create(meshdefinition, 128, 128)':
        raise AuditError('millstone: texture dimensions changed')
    parts = {}
    for statement in statements[2:-1]:
        declaration = re.fullmatch(r'PartDefinition (\w+) = (\w+)\.addOrReplaceChild\((.*)\)', statement, re.S)
        if not declaration:
            raise AuditError('millstone: unrecognized part declaration')
        name, parent, params = declaration.groups()
        args = arguments(params)
        if len(args) != 3 or args[0] != '"' + name + '"' or name in parts:
            raise AuditError('millstone: duplicate or dynamic part name')
        if parent != 'partdefinition' and parent not in parts:
            raise AuditError('millstone: unknown parent ' + parent)
        prefix = 'CubeListBuilder.create()'
        if not args[1].startswith(prefix):
            raise AuditError('millstone: unknown cube builder')
        uv, cubes = None, []
        for method, values in chain(args[1][len(prefix):]):
            if method == 'texOffs' and len(values) == 2:
                uv = tuple(number(v) for v in values)
            elif method == 'addBox' and len(values) == 7 and uv is not None:
                deformation = re.fullmatch(r'new CubeDeformation\(([^()]+)\)', values[6])
                if not deformation:
                    raise AuditError('millstone: unknown cube deformation')
                size = tuple(number(v) for v in values[3:6])
                if any(v <= 0 for v in size):
                    raise AuditError('millstone: non-positive cube size')
                cubes.append(Cube(uv, tuple(number(v) for v in values[:3]), size, number(deformation[1])))
            else:
                raise AuditError('millstone: unsupported cube call ' + method)
        pose = re.fullmatch(r'PartPose\.(offset|offsetAndRotation)\(([^()]+)\)', args[2])
        if not pose:
            raise AuditError('millstone: unsupported part pose')
        values = tuple(number(v) for v in arguments(pose[2]))
        if len(values) != (3 if pose[1] == 'offset' else 6):
            raise AuditError('millstone: invalid pose dimensions')
        parts[name] = Part(parent, values[:3], values[3:] if len(values) == 6 else (0, 0, 0), tuple(cubes))
    expected = {'base': ('partdefinition', 6), 'wheel': ('partdefinition', 0), 'roll': ('wheel', 1),
                'stick': ('wheel', 0), 'rotStick': ('stick', 0), 'cube_r5': ('rotStick', 1),
                **{'cube_r' + str(i): ('stick', 1) for i in range(1, 5)}}
    if {key: (part.parent, len(part.cubes)) for key, part in parts.items()} != expected:
        raise AuditError('millstone: unknown part hierarchy; explicit conversion required')
    # These pivots are replaced by MillstoneAnimation's display anchors, not baked twice.
    for name, offset in {'wheel': (0, 3, 0), 'stick': (0, 0, 0), 'rotStick': (0, 4, 0)}.items():
        if parts[name].offset != offset or parts[name].rotation != (0, 0, 0):
            raise AuditError('millstone: unknown parent transform for ' + name)
    return parts


# Vertex orders from ModelPart.Cube and FaceInfo; match UV corners after baking quarter turns.
SOURCE_FACES = ((5, 4, 0, 1), (2, 3, 7, 6), (0, 4, 7, 3),
                (1, 0, 3, 2), (5, 1, 2, 6), (4, 5, 6, 7))
FACE_CORNERS = {
    'down': ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
    'up': ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    'north': ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    'south': ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    'west': ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    'east': ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
}


def face_uv(vertices: list[tuple], indices: tuple, rect: tuple, low: list, high: list) -> tuple[str, dict]:
    u0, v0, u1, v1 = rect
    uv = ((u1, v0), (u0, v0), (u0, v1), (u1, v1))
    points = {vertices[index]: value for index, value in zip(indices, uv, strict=True)}
    for name, corners in FACE_CORNERS.items():
        face = [tuple((low, high)[bit][axis] for axis, bit in enumerate(corner)) for corner in corners]
        if set(face) != set(points):
            continue
        expected = [points[point] for point in face]
        for us, vs in itertools.product(((u0, u1), (u1, u0)), ((v0, v1), (v1, v0))):
            corners_uv = [(us[0], vs[0]), (us[0], vs[1]), (us[1], vs[1]), (us[1], vs[0])]
            for quarter in range(4):
                if [corners_uv[(i + quarter) % 4] for i in range(4)] == expected:
                    return name, {'uv': [v / 8 for v in (us[0], vs[0], us[1], vs[1])],
                                  'texture': '#body', **({'rotation': quarter * 90} if quarter else {})}
    raise AuditError('millstone: cannot preserve cube UV orientation')


def cube_element(cube: Cube, part: Part, offset: tuple, scale: float) -> dict:
    rx, ry, rz = part.rotation
    quarter_x = abs(rx + 1.5708) < 0.00001
    diagonal_y = abs(ry + .7854) < .00001
    if (rx != 0 and not quarter_x) or (ry != 0 and not diagonal_y) or rz != 0 or quarter_x and diagonal_y:
        raise AuditError('millstone: unsupported rotation')

    def transform(point: tuple) -> tuple:
        x, y, z = point
        if quarter_x:
            y, z = z, -y
        x, y, z = (a + b for a, b in zip((x, y, z), part.offset, strict=True))
        return tuple(round(8 + scale * (v + shift), 6) for v, shift in zip((x, -y, -z), offset, strict=True))

    lo = [v - cube.inflation for v in cube.origin]
    hi = [a + b + cube.inflation for a, b in zip(cube.origin, cube.size, strict=True)]
    vertices = [transform(tuple((lo, hi)[bit][axis] for axis, bit in enumerate(corner)))
                for corner in ((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0),
                               (0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1))]
    low = [min(point[i] for point in vertices) for i in range(3)]
    high = [max(point[i] for point in vertices) for i in range(3)]
    if any(v < -16 or v > 32 for v in low + high):
        raise AuditError('millstone: geometry exceeds vanilla model bounds')
    u, v = cube.uv
    dx, dy, dz = cube.size
    rects = ((u + dz, v, u + dz + dx, v + dz), (u + dz + dx, v + dz, u + dz + 2 * dx, v),
             (u, v + dz, u + dz, v + dz + dy), (u + dz, v + dz, u + dz + dx, v + dz + dy),
             (u + dz + dx, v + dz, u + 2 * dz + dx, v + dz + dy),
             (u + 2 * dz + dx, v + dz, u + 2 * dz + 2 * dx, v + dz + dy))
    faces = dict(face_uv(vertices, indices, rect, low, high) for indices, rect in zip(SOURCE_FACES, rects, strict=True))
    element = {'from': low, 'to': high, 'faces': faces}
    if diagonal_y:
        element['rotation'] = {'origin': list(transform((0, 0, 0))), 'axis': 'y', 'angle': 45, 'rescale': False}
    return element


def millstone_furniture(source: Path, assets: dict[str, bytes], items: dict, definitions: dict) -> tuple[dict, dict]:
    raw = read_blobs(source, COMMIT, [MODEL_SOURCE])[MODEL_SOURCE]
    parts = parse_mesh(raw.decode('utf-8'))
    groups = {'millstone_base': (('base',), (0, 24, 0), 1),
              'new_millstone_stick': (tuple('cube_r' + str(i) for i in range(1, 5)), (0, 8, 0), .5),
              'new_millstone_stick2': (('cube_r5',), (0, 8.8, 0), .5),
              'new_millstone_stone': (('roll',), (0, 0, -8), 1)}
    for name, (members, offset, scale) in groups.items():
        geometry = [cube_element(cube, parts[member], offset, scale) for member in members for cube in parts[member].cubes]
        model = {'textures': {'body': ART + ':block/millstone'}, 'elements': geometry}
        if scale != 1:
            model['display'] = {'fixed': {'scale': [1 / scale] * 3}}
        display_item(assets, items, 'show:' + name, model)
    key = PLUGIN + ':millstone'
    items[key] = {'material': 'paper', 'data': {'item_name': '<!i>石磨'}, 'model': definitions[ART + ':item/millstone'],
                  'behavior': {'type': 'furniture_item', 'furniture': key,
                               'rules': {'ground': {'rotation': 'four', 'alignment': 'center'}}}}
    # Keep the existing menu and advancement icon available without another placeable furniture ID.
    items[PLUGIN + ':new_millstone'] = {'material': 'paper', 'data': {'item_name': '<!i>石磨'},
                                       'model': definitions[ART + ':item/millstone']}
    furniture = {'settings': {'item': key, 'hit_times': 3, 'sounds': {'place': 'minecraft:block.stone.place',
                 'break': 'minecraft:block.stone.break'}},
                 'behavior': [{'type': key, 'stick_item': 'show:new_millstone_stick',
                               'stick2_item': 'show:new_millstone_stick2', 'stone_item': 'show:new_millstone_stone'}],
                 'variants': {'ground': {'elements': [{'item': 'show:millstone_base', 'display_transform': 'none'}],
                     'hitboxes': [{'type': 'interaction', 'position': '0,0,0', 'width': 2, 'height': 1,
                                   'interactive': True, 'blocks_building': True}]}},
                 'loot': {'pools': [{'rolls': 1, 'entries': [{'type': 'item', 'item': key}]}]}}
    provenance = {'sourceCommit': COMMIT, 'path': MODEL_SOURCE, 'sha256': hashlib.sha256(raw).hexdigest(),
                  'parts': {name: list(value[0]) for name, value in groups.items()}, 'clientVisual': 'NOT_TESTED',
                  'differences': ['Existing plugin anchors and six-times rolling spin retained; official lift animation is not applied.',
                                  'Roll center remains at plugin height 1.4 instead of official 1.3125.',
                                  'Interaction hitbox prevents building but does not reproduce the solid mod voxel collision.']}
    return {key: furniture}, provenance
