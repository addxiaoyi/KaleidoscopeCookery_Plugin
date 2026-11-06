"""Export the reviewed Cookery commit into an atomic, deterministic parity catalog.

This is a deliberately small Java grammar, not a best-effort Java interpreter.
Unrecognized registrations/builders abort before replacing the previous catalog.
"""

from __future__ import annotations

import argparse
import ast
import hashlib
import json
import math
import os
from pathlib import Path
import re
import sys
import tempfile

from audit_upstream import AuditError, JAVA_ROOT, JAVA_TOKENS, git_text, read_blobs, uncomment_java

COMMIT = '2f4e386ce23f49a385ddf003c67fc6415c55417a'
NAMESPACE = 'kaleidoscope_cookery'
PLUGIN_NAMESPACE = 'kaleidoscopecookery'
CUSTOM_EFFECTS = frozenset('flatulence tundra_strider warmth satiated_shield vigor sulfur mustard preservation hinder projectile_dodge instant_smelting vitality'.split())
VANILLA_EFFECTS = {
    **{name.upper(): name for name in 'absorption blindness fire_resistance luck poison regeneration unluck water_breathing slow_falling'.split()},
    'CONFUSION': 'nausea', 'DAMAGE_RESISTANCE': 'resistance', 'DIG_SLOWDOWN': 'mining_fatigue',
    'DIG_SPEED': 'haste', 'JUMP': 'jump_boost', 'MOVEMENT_SPEED': 'speed',
}
ITEM_CLASSES = frozenset('EmptyCupItem FlourItem FruitBasketItem KitchenShovelItem OilItem OilPotItem RawDoughItem RecipeItem RiceItem ScarecrowItem SickleItem SteamerItem StockpotLidItem TeaBannerItem TeapotItem TransmutationLunchBagItem ArmorItem BambooTubeRiceBlockItem BlockItem BowlFoodOnlyItem ChiliItem ClayPotMilkTeaItem FoodWithEffectsItem Item ItemNameBlockItem KitchenKnifeItem LiftBlockItem StrawHatItem WithTooltipsBlockItem WithTooltipsItem'.split())


def number(expression: str) -> int | float:
    expression = re.sub(r'(?<=[0-9.])[fFdD]\b', '', expression.strip())
    try:
        tree = ast.parse(expression, mode='eval')
    except (SyntaxError, RecursionError) as exc:
        raise AuditError(f'invalid numeric expression: {expression}') from exc
    if len(list(ast.walk(tree))) > 40:
        raise AuditError('numeric expression exceeds size limit')

    def visit(node: ast.AST) -> int | float:
        if isinstance(node, ast.Constant) and type(node.value) in (int, float):
            value = node.value
        elif isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
            value = visit(node.operand) * (-1 if isinstance(node.op, ast.USub) else 1)
        elif isinstance(node, ast.BinOp) and isinstance(node.op, (ast.Add, ast.Sub, ast.Mult)):
            left, right = visit(node.left), visit(node.right)
            value = left + right if isinstance(node.op, ast.Add) else left - right if isinstance(node.op, ast.Sub) else left * right
        else:
            raise AuditError(f'unsupported numeric expression: {expression}')
        if not math.isfinite(value) or abs(value) > 2_147_483_647:
            raise AuditError(f'numeric value out of bounds: {expression}')
        return value

    return visit(tree.body)


def bounded(expression: str, minimum: float, maximum: float, *, integer: bool = False):
    value = number(expression)
    if not minimum <= value <= maximum or integer and int(value) != value:
        raise AuditError(f'value outside [{minimum}, {maximum}]: {expression}')
    return int(value) if integer else value


def closing(text: str, start: int) -> int:
    depth, quoted, escaped = 0, False, False
    for index in range(start, len(text)):
        char = text[index]
        if quoted:
            if escaped:
                escaped = False
            elif char == '\\':
                escaped = True
            elif char == '"':
                quoted = False
            continue
        if char == '"':
            quoted = True
        elif char == '(':
            depth += 1
        elif char == ')':
            depth -= 1
            if depth == 0:
                return index
    raise AuditError('unterminated registration or builder call')


def arguments(text: str) -> list[str]:
    chunks, start, index = [], 0, 0
    while index < len(text):
        if text[index] == '(':
            index = closing(text, index)
        elif text[index] == ',':
            chunks.append(text[start:index].strip())
            start = index + 1
        index += 1
    chunks.append(text[start:].strip())
    return chunks


def chain(text: str) -> list[tuple[str, list[str]]]:
    calls = []
    while text.strip():
        text = text.strip()
        match = re.match(r'\.([A-Za-z][\w]*)\s*\(', text)
        if not match:
            raise AuditError(f'unrecognized builder tail: {text[:100]}')
        end = closing(text, match.end() - 1)
        body = text[match.end():end].strip()
        calls.append((match[1], arguments(body) if body else []))
        text = text[end + 1:]
    return calls


def registrations(source: str, call: str) -> dict[str, str]:
    text = uncomment_java(source)
    masked = JAVA_TOKENS.sub(lambda m: re.sub(r'[^\n]', ' ', m[0]), text)
    entries = {}
    for match in re.finditer(rf'(?<![\w.]){re.escape(call)}\s*\(', masked):
        end = closing(text, match.end() - 1)
        args = arguments(text[match.end():end])
        if len(args) != 2 or not re.fullmatch(r'"[a-z0-9_]+"', args[0]):
            raise AuditError(f'{call}: expected literal ID and one initializer')
        name = args[0][1:-1]
        if name in entries:
            raise AuditError(f'{call}: duplicate registration {name}')
        entries[name] = args[1]
    if not entries:
        raise AuditError(f'{call}: no registrations')
    return entries


def effect(args: list[str]) -> dict:
    if len(args) not in (1, 2):
        raise AuditError('effect requires a supplier and optional probability')
    match = re.fullmatch(r'\(\)\s*->\s*new MobEffectInstance\((.*)\)', args[0], re.S)
    if not match:
        raise AuditError(f'unrecognized effect supplier: {args[0]}')
    params = arguments(match[1])
    if len(params) not in (2, 3):
        raise AuditError('unsupported MobEffectInstance constructor')
    symbol = params[0].removeprefix('ModEffects.').removeprefix('MobEffects.')
    if symbol.endswith('.get()') and symbol[:-6].lower() in CUSTOM_EFFECTS:
        effect_id = f'{NAMESPACE}:{symbol[:-6].lower()}'
    elif symbol in VANILLA_EFFECTS:
        effect_id = f'minecraft:{VANILLA_EFFECTS[symbol]}'
    else:
        raise AuditError(f'unrecognized effect {symbol}')
    return {'id': effect_id, 'duration': bounded(params[1], 1, 72_000_000, integer=True),
            'amplifier': bounded(params[2], 0, 255, integer=True) if len(params) == 3 else 0,
            'probability': bounded(args[1], 0, 1) if len(args) == 2 else 1.0}


def parse_foods(source: str) -> dict[str, dict]:
    text = uncomment_java(source)
    declarations = list(re.finditer(r'\bFoodProperties\s+([A-Z][A-Z_0-9]*)\s*=\s*([^;]+);', text))
    if not declarations or len(declarations) != len(re.findall(r'\bFoodProperties\s+\w+\s*=', text)):
        raise AuditError('unrecognized FoodProperties declaration')
    foods = {}
    for match in declarations:
        name, initializer = match.groups()
        prefix = re.match(r'(?:\(new FoodProperties\.Builder\(\)\)|new FoodProperties\.Builder\(\))', initializer)
        if not prefix or name in foods:
            raise AuditError(f'{name}: unrecognized or duplicate food initializer')
        food = {'nutrition': None, 'saturationModifier': None, 'alwaysEat': False, 'meat': False,
                'consumeTicks': 32, 'effects': []}
        seen = set()
        calls = chain(initializer[prefix.end():])
        for method, args in calls:
            if method != 'effect' and method in seen:
                raise AuditError(f'{name}: duplicate food builder method {method}')
            seen.add(method)
            if method == 'nutrition' and len(args) == 1:
                food['nutrition'] = bounded(args[0], 0, 1000, integer=True)
            elif method == 'saturationMod' and len(args) == 1:
                food['saturationModifier'] = bounded(args[0], 0, 100)
            elif method == 'effect':
                food['effects'].append(effect(args))
            elif method in ('alwaysEat', 'meat', 'fast', 'build') and not args:
                if method in ('alwaysEat', 'meat'):
                    food[method] = True
                elif method == 'fast':
                    food['consumeTicks'] = 16
            else:
                raise AuditError(f'{name}: unsupported food method {method}')
        if not calls or calls[-1] != ('build', []) or food['nutrition'] is None or food['saturationModifier'] is None:
            raise AuditError(f'{name}: incomplete food definition')
        food['saturation'] = round(2 * food['nutrition'] * food['saturationModifier'], 6)
        foods[name] = food
    return foods


def animate(argument: str) -> str:
    if not re.fullmatch(r'FoodBiteAnimateTicks\.[A-Z_]+', argument):
        raise AuditError(f'unknown animation {argument}')
    return argument.split('.')[1]


def loot(argument: str) -> str:
    if not re.fullmatch(r'Items\.[A-Z_]+', argument):
        raise AuditError(f'unknown loot item {argument}')
    return 'minecraft:' + argument[6:].lower()


def parse_bites(source: str, foods: dict[str, dict]) -> dict[str, dict]:
    entries = {}
    for name, initializer in registrations(source, 'registry.registerFoodData').items():
        if not initializer.startswith('FoodData'):
            raise AuditError(f'{name}: unknown FoodData initializer')
        calls = chain(initializer[len('FoodData'):])
        method, args = calls[0]
        if method not in ('create', 'createOneByTwo') or len(args) != 3 or any(key not in foods for key in args[1:]):
            raise AuditError(f'{name}: invalid food references/constructor')
        bites = {'maxBites': bounded(args[0], 1, 64, integer=True), 'layout': '1x2' if method == 'createOneByTwo' else '1x1',
                 'food': foods[args[1]], 'drops': ['minecraft:bowl'], 'collision': 'FoodBlock.AABB', 'animation': None}
        for method, params in calls[1:]:
            if method == 'addLootItems' and params:
                bites['drops'].extend(loot(item) for item in params)
            elif method == 'setLootItem' and len(params) == 1:
                bites['drops'] = [loot(params[0])]
            elif method == 'setAnimateTick' and len(params) == 1:
                bites['animation'] = animate(params[0])
            elif method in ('bowlAABB', 'soupPotAABB', 'potSoupAnimateTick') and not params:
                if method == 'potSoupAnimateTick':
                    bites['animation'] = 'POT_SOUP_ANIMATE_TICK'
                else:
                    bites['collision'] = [[2, 0, 2, 14, 6, 14]] if method == 'bowlAABB' else [[1, 0, 1, 15, 1, 15], [4, 1, 4, 12, 7, 12]]
            elif method == 'setAABB' and len(params) == 1 and re.fullmatch(r'Block\.box\([^()]+\)', params[0]):
                box = [bounded(v, 0, 16) for v in arguments(params[0][10:-1])]
                if len(box) != 6 or any(box[i] >= box[i + 3] for i in range(3)):
                    raise AuditError(f'{name}: invalid AABB')
                bites['collision'] = [box]
            else:
                raise AuditError(f'{name}: unsupported bite method {method}')
        entries[name] = {'food': foods[args[2]], 'bites': bites, 'container': 'minecraft:bowl'}
    return entries


def parse_teas(source: str) -> dict[str, dict]:
    teas = {}
    for name, initializer in registrations(source, 'registry.registerTeacupData').items():
        if not initializer.startswith('TeacupData'):
            raise AuditError(f'{name}: unknown TeacupData initializer')
        calls = chain(initializer[len('TeacupData'):])
        method, args = calls[0]
        if method != 'create' or len(args) != 1:
            raise AuditError(f'{name}: invalid tea constructor')
        tea = {'maxCups': bounded(args[0], 1, 64, integer=True), 'effects': [], 'animation': None,
               'container': f'{PLUGIN_NAMESPACE}:empty_cup', 'consumeTicks': 32}
        for method, params in calls[1:]:
            if method == 'addEffect':
                tea['effects'].append(effect(params))
            elif method == 'setAnimateTick' and len(params) == 1:
                tea['animation'] = animate(params[0])
            else:
                raise AuditError(f'{name}: unsupported tea method {method}')
        teas[name] = tea
    return teas


def parse_plates(source: str) -> dict[str, dict]:
    plates = {}
    for name, initializer in registrations(source, 'registry.registerPlateData').items():
        if not initializer.startswith('PlateData'):
            raise AuditError(f'{name}: unknown PlateData initializer')
        calls = chain(initializer[len('PlateData'):])
        method, args = calls[0]
        if method != 'create' or len(args) != 1:
            raise AuditError(f'{name}: invalid plate constructor')
        plate = {'maxServings': bounded(args[0], 1, 64, integer=True), 'servings': [], 'drops': [], 'collision': 'PlateBlock.AABB'}
        for method, params in calls[1:]:
            if method in ('setServingItems', 'addServingItems') and params:
                servings = []
                for param in params:
                    if re.fullmatch(r'ModItems\.[A-Z_]+', param):
                        servings.append(f'{PLUGIN_NAMESPACE}:' + param[9:].lower())
                    elif re.fullmatch(r'\(\)\s*->\s*Items\.[A-Z_]+', param):
                        servings.append(loot(param.split('->')[1].strip()))
                    else:
                        raise AuditError(f'{name}: unknown plate serving {param}')
                if method == 'setServingItems':
                    if len(servings) != 1:
                        raise AuditError(f'{name}: setServingItems expects one supplier')
                    plate['servings'] = servings
                else:
                    plate['servings'].extend(servings)
            elif method == 'setLootItem' and len(params) == 1:
                plate['drops'] = [loot(params[0])]
            elif method == 'addLootItems' and params:
                plate['drops'].extend(loot(param) for param in params)
            elif method == 'platterAABB' and not params:
                plate['collision'] = 'PlateData.platterAABB'
            else:
                raise AuditError(f'{name}: unsupported plate method {method}')
        if not plate['servings']:
            raise AuditError(f'{name}: missing plate servings')
        plates[name] = plate
    return plates


def export(source: Path, commit: str) -> dict:
    if commit != COMMIT or git_text(source, 'rev-parse', 'HEAD') != commit:
        raise AuditError(f'only reviewed Cookery commit {COMMIT} is supported')
    if git_text(source, 'status', '--porcelain', '--untracked-files=no'):
        raise AuditError('reference checkout has tracked modifications')
    paths = git_text(source, 'ls-tree', '-r', '--name-only', commit).splitlines()
    selected = [p for p in paths if p.startswith(JAVA_ROOT + '/') or
                p.startswith(('src/main/resources/', 'src/generated/resources/')) and
                any(k in p for k in ('/models/', '/structures/', '/tags/', '/sounds.json'))]
    blobs = read_blobs(source, commit, selected)

    def java(path: str) -> str:
        return blobs[f'{JAVA_ROOT}/{path}'].decode('utf-8-sig')

    foods = parse_foods(java('init/ModFoods.java'))
    bites = parse_bites(java('init/registry/FoodBiteRegistry.java'), foods)
    teas = parse_teas(java('init/registry/TeacupRegistry.java'))
    plates = parse_plates(java('init/registry/PlateRegistry.java'))
    records = {}

    def record(name: str, kind: str, **fields) -> dict:
        key = (kind, name)
        if key in records:
            raise AuditError(f'duplicate catalog entry {key}')
        row = {'sourceCommit': commit, 'sourceId': f'{NAMESPACE}:{name}', 'pluginId': f'{PLUGIN_NAMESPACE}:{name}',
               'type': kind, 'food': None, 'bites': None, 'tea': None, 'container': None, 'effects': [],
               'models': [p for p in paths if p.endswith(f'/models/item/{name}.json')],
               'clientOnly': False, 'status': 'pending', **fields}
        records[key] = row
        return row

    for name, initializer in registrations(java('init/ModItems.java'), 'ITEMS.register').items():
        match = re.search(r'(?:new (\w+)\(|(\w+)::new)', initializer)
        if not match or (constructor := match[1] or match[2]) not in ITEM_CLASSES:
            raise AuditError(f'{name}: unrecognized item constructor: {initializer}')
        refs = re.findall(r'ModFoods\.([A-Z_0-9]+)', initializer)
        if constructor == 'ChiliItem':
            refs = ['CHILI']
        if len(refs) > 1 or any(key not in foods for key in refs):
            raise AuditError(f'{name}: invalid food references {refs}')
        kind = 'food' if refs else 'tool' if constructor in ('KitchenKnifeItem', 'KitchenShovelItem', 'SickleItem') else 'item'
        row = record(name, kind, constructor=constructor)
        if refs:
            row['food'] = foods[refs[0]]
            row['status'] = 'catalogued'
            if constructor == 'BowlFoodOnlyItem':
                row['container'] = 'minecraft:bowl'
            elif constructor == 'BambooTubeRiceBlockItem':
                row['container'] = 'minecraft:bamboo'
            if constructor == 'ChiliItem':
                damage = re.fullmatch(r'\(\)\s*->\s*new ChiliItem\((\d+)\)', initializer)
                if not damage:
                    raise AuditError(f'{name}: unknown chili damage')
                row['consumeDamage'] = int(damage[1])
        if constructor == 'ClayPotMilkTeaItem':
            row['container'] = 'minecraft:flower_pot'
            row['consumeAction'] = 'remove_harmful_effects'
        if name == 'cold_cut_ham_slices':
            if not re.search(r'new FoodBiteThreeByThreeBlock\(\s*ModFoods.COLD_CUT_HAM_SLICES_BLOCK, 8, null', java('init/ModBlocks.java')):
                raise AuditError('unrecognized 3x3 food definition')
            row['bites'] = {'maxBites': 8, 'layout': '3x3', 'food': foods['COLD_CUT_HAM_SLICES_BLOCK'],
                            'drops': ['minecraft:bowl'], 'collision': 'FoodBiteThreeByThreeBlock.PART', 'animation': None}
            row['clientOnly'] = True
            row['status'] = 'catalogued'
    for name, definition in bites.items():
        if definition['bites']['collision'] == 'FoodBlock.AABB':
            shape = re.search(r'AABB\s*=\s*Block\.box\(([^)]+)\)', java('block/food/FoodBlock.java'))
            if not shape:
                raise AuditError('unknown default food collision')
            definition['bites']['collision'] = [[number(value) for value in arguments(shape[1])]]
        record(name, 'food', **definition, status='catalogued')
    for name, definition in teas.items():
        record(name, 'tea', tea=definition, container=definition['container'], status='catalogued')
    for name, definition in plates.items():
        record(name, 'item', plate=definition, status='catalogued')
    for name in registrations(java('init/ModEffects.java'), 'EFFECTS.register'):
        if name not in CUSTOM_EFFECTS:
            raise AuditError(f'unrecognized custom effect {name}')
        record(name, 'effect')
    if len([r for r in records.values() if r['type'] == 'effect']) != len(CUSTOM_EFFECTS):
        raise AuditError('custom effect registration missing')
    for name in registrations(java('init/ModEnchantments.java'), 'ENCHANTMENTS.register'):
        record(name, 'enchantment')
    for name in registrations(java('init/ModVillager.java'), 'VILLAGER_PROFESSION.register'):
        record(name, 'profession')
    for path in paths:
        if '/structures/' in path and path.endswith('.nbt'):
            name = path.split('/structures/', 1)[1][:-4]
            record(name, 'structure', resource=path)
    return {'schemaVersion': 1, 'sourceCommit': commit, 'sourceRepository': 'KaleidoscopeMods/KaleidoscopeCookery',
            'qualityPolicy': {'ratios': {'SUPERB': 1.2, 'EXCELLENT': .9, 'STANDARD': .6, 'POOR': .3},
                              'nutritionMinimum': 'preserve-zero-otherwise-one', 'saturation': 'absolute-times-ratio',
                              'effects': 'duration-times-ratio-probability-preserved',
                              'upstreamDifference': 'QualityUtils drops probability<1 effects; plugin retains their probabilities'},
            'sourceFiles': {p: hashlib.sha256(raw).hexdigest() for p, raw in sorted(blobs.items())},
            'foodProperties': foods, 'entries': sorted(records.values(), key=lambda r: (r['type'], r['sourceId'])),
            'limitations': ['Catalogued is not runtime acceptance', 'Ancillary source is hashed; pending handlers are not implemented',
                            'PlateRegistry serving plates are catalogued separately; placement/serving remains pending',
                            'Client renderers and animations require vanilla resource-pack substitutes']}


def publish(path: Path, document: dict, check: bool = False) -> None:
    content = (json.dumps(document, indent=2, ensure_ascii=False, allow_nan=False) + '\n').encode('utf-8')
    if check:
        if not path.is_file() or path.read_bytes() != content:
            raise AuditError(f'{path}: snapshot differs from pinned source; regenerate explicitly')
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=path.name + '.', suffix='.tmp', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--commit', default=COMMIT)
    parser.add_argument('--output', type=Path, default=Path('src/main/resources/cookery-parity.json'))
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    try:
        if args.output.resolve().is_relative_to(args.source.resolve()):
            raise AuditError('output must be outside the official reference checkout')
        catalog = export(args.source, args.commit)
        publish(args.output, catalog, args.check)
    except (ValueError, OSError) as exc:
        print(f'Cookery catalog failed; previous snapshot retained: {exc}', file=sys.stderr)
        return 1
    counts = {kind: sum(row['type'] == kind for row in catalog['entries']) for kind in sorted({r['type'] for r in catalog['entries']})}
    print(json.dumps({'commit': args.commit, 'foodProperties': len(catalog['foodProperties']), 'entries': counts,
                      'sha256': hashlib.sha256(args.output.read_bytes()).hexdigest(), 'check': args.check}))
    return 0


if __name__ == '__main__':
    sys.exit(main())
