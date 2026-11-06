"""Convert pinned stockpot recipes without flattening ingredient alternatives."""

from __future__ import annotations

from audit_upstream import AuditError
from cookery_recipes import ART, MissingTag, RecipeSources, mapped

SOUP_BUCKETS = {f'minecraft:{name}': f'minecraft:{name}_bucket' for name in ('water', 'lava', 'milk')}
SOUP_BUCKETS.update({f'minecraft:{name}_bucket': f'minecraft:{name}_bucket'
                     for name in ('axolotl', 'cod', 'salmon', 'tropical_fish', 'pufferfish', 'tadpole')})
# Forge supplies this tag outside the fixed Cookery tree. Do not invent its membership.
EXTERNAL_TAGS = {'forge:mushrooms'}


def surface_id(texture: str) -> str:
    return 'show:stockpot/' + texture.replace(':', '/')


def stockpot_recipes(sources: RecipeSources, records: list[dict]) -> tuple[dict, set[str]]:
    recipes, required = {}, set()
    for row in records:
        doc = row['original']
        if doc['type'] not in (ART + ':stockpot', ART + ':flex_stockpot'):
            continue
        if not 1 <= len(doc['ingredients']) <= 9:
            raise AuditError(f'{row["sourceId"]}: stockpot requires 1..9 ingredients')
        try:
            choices = [sources.expand(value) for value in doc['ingredients']]
            carrier = sources.expand(doc['carrier']) if 'carrier' in doc else []
        except MissingTag as failure:
            if str(failure) not in {f'required tag absent from pinned resources: {tag}' for tag in EXTERNAL_TAGS}:
                raise
            row['reason'] = str(failure)
            continue
        if any(not choice for choice in choices) or len(carrier) > 1:
            raise AuditError(f'{row["sourceId"]}: empty input or multiple carriers need an explicit handler')
        if bool(doc.get('empty_carrier')) == bool(carrier):
            raise AuditError(f'{row["sourceId"]}: ambiguous stockpot carrier')
        if doc['soup_base'] not in SOUP_BUCKETS:
            raise AuditError(f'{row["sourceId"]}: unregistered soup base {doc["soup_base"]}')
        output = doc['result']
        if 'nbt' in output or not 1 <= output.get('count', 1) <= 9:
            raise AuditError(f'{row["sourceId"]}: unsupported stockpot output count or NBT')
        key = mapped(row['sourceId'])
        recipes[key] = {
            'match': 'exact' if doc['type'] == ART + ':stockpot' else 'distinct',
            'ingredients': choices, 'soup_base': SOUP_BUCKETS[doc['soup_base']],
            'result': mapped(output['item']), 'result_count': output.get('count', 1),
            'time': doc['time'], 'carrier': carrier[0] if carrier else 'minecraft:air',
            'visuals': {'cooking_model': surface_id(doc['cooking_texture']),
                        'finished_model': surface_id(doc['finished_texture']),
                        'cooking_color': doc['cooking_bubble_color'],
                        'finished_color': doc['finished_bubble_color']}}
        required.update(value for choice in choices for value in choice)
        required.update(carrier)
        required.add(mapped(output['item']))
        row.update(status='imported', section='stockpot_recipes', pluginIds=[key],
                   reason='Exact then distinct matching; legacy cosine fallback and quality retained. Player acceptance pending.')
    return recipes, required
