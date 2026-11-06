"""Import pinned wok recipes while retaining the plugin's stir-to-finish rule."""

from __future__ import annotations

from audit_upstream import AuditError
from cookery_recipes import ART, MissingTag, RecipeSources, mapped, validate_recipe

KNOWN_DEPENDENCIES = {
    'required tag absent from pinned resources: forge:mushrooms',
    'external tag alternative requires a mapping: forge:cooked_rice/farmersdelight:cooked_rice',
}


def pot_recipes(sources: RecipeSources, records: list[dict]) -> tuple[dict, set[str]]:
    recipes, required = {}, set()
    for row in records:
        doc = row['original']
        if doc['type'] not in (ART + ':pot', ART + ':flex_pot'):
            continue
        validate_recipe(doc)
        if not 1 <= len(doc['ingredients']) <= 9:
            raise AuditError(f'{row["sourceId"]}: pot requires 1..9 ingredients')
        output = doc['result']
        if 'nbt' in output or not 1 <= output.get('count', 1) <= 9:
            raise AuditError(f'{row["sourceId"]}: unsupported pot output count or NBT')
        try:
            choices = [sources.expand(value) for value in doc['ingredients']]
            carrier = sources.expand(doc['carrier']) if 'carrier' in doc else []
        except MissingTag as failure:
            if str(failure) not in KNOWN_DEPENDENCIES:
                raise
            row['reason'] = str(failure)
            continue
        if any(not choice or len(choice) > 128 for choice in choices) or ('carrier' in doc and len(carrier) != 1):
            raise AuditError(f'{row["sourceId"]}: invalid alternatives or carrier; explicit handler required')
        key = mapped(row['sourceId'])
        recipes[key] = {
            'match': 'exact' if doc['type'] == ART + ':pot' else 'distinct',
            'ingredients': choices, 'result': mapped(output['item']),
            'result_count': output.get('count', 1), 'stir_fry_count': doc['stir_fry_count'],
            'source_time': doc['time'], 'carrier': carrier[0] if carrier else 'minecraft:air',
        }
        required.update(value for choice in choices for value in choice)
        required.update(carrier)
        required.add(mapped(output['item']))
        row.update(status='imported', section='pot_recipes', pluginIds=[key],
                   reason='Exact then distinct matching; legacy quality and cosine fallback retained. '
                          'Completion uses stir count and existing animation, not the source time gate. Player acceptance pending.')
    return recipes, required
