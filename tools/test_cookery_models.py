import copy
import json
import unittest

from audit_upstream import AuditError
from cookery_models import convert_models, geometry_part, group_indices, json_bytes, model_roots, verify_models


class CookeryModelsTest(unittest.TestCase):
    def test_perspectives_keep_flat_gui_and_solid_hand(self):
        assets = {'cookery/models/item/pot.json': json_bytes({
            'loader': 'forge:separate_transforms', 'gui_light': 'front',
            'base': {'parent': 'cookery:item/pot_3d'},
            'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'cookery:item/pot'}}}})}
        definitions, converted = convert_models(assets)
        node = definitions['cookery:item/pot']
        self.assertEqual(['cookery:item/pot'], converted)
        self.assertEqual('minecraft:display_context', node['property'])
        self.assertEqual(['gui'], node['cases'][0]['when'])
        self.assertEqual('cookery:item/pot_3d', json.loads(assets['cookery/models/item/pot.json'])['parent'])
        self.assertEqual('front', json.loads(assets['cookery/models/item/pot_ce_gui.json'])['gui_light'])
        self.assertEqual({'cookery:item/pot', 'cookery:item/pot_ce_gui'}, model_roots(node))

    def test_unknown_loader_leaves_assets_unchanged(self):
        assets = {'cookery/models/item/pot.json': json_bytes({'loader': 'mod:unknown'})}
        previous = copy.deepcopy(assets)
        with self.assertRaisesRegex(AuditError, 'unsupported model loader'):
            convert_models(assets)
        self.assertEqual(previous, assets)

    def test_unknown_perspective_is_rejected(self):
        assets = {'cookery/models/item/pot.json': json_bytes({'loader': 'forge:separate_transforms',
                  'base': {}, 'perspectives': {'vr': {}}})}
        with self.assertRaisesRegex(AuditError, 'unknown model perspective'):
            convert_models(assets)

    def test_parts_exclude_inventory_transforms_and_keep_geometry(self):
        model = {'textures': {'all': 'minecraft:block/stone'}, 'elements': [{'from': [0, 0, 0]}, {'from': [1, 1, 1]}],
                 'groups': [{'name': 'body', 'children': [0, {'name': 'handle', 'children': [1]}]}], 'display': {'head': {}}}
        part = geometry_part(model, group_indices(model, 'body'))
        self.assertEqual(model['elements'], part['elements'])
        self.assertNotIn('display', part)
        part['elements'][0]['from'][0] = 4
        self.assertEqual(0, model['elements'][0]['from'][0])

    def test_missing_parent_texture_and_cycle_fail(self):
        for model, reason in (({'parent': 'cookery:absent'}, 'missing model'),
                              ({'textures': {'all': 'cookery:absent'}}, 'missing texture'),
                              ({'parent': 'cookery:pot'}, 'cyclic model parent'),
                              ({'elements': [{'faces': {'up': {'texture': '#missing'}}}]}, 'unresolved texture')):
            with self.subTest(reason=reason), self.assertRaisesRegex(AuditError, reason):
                verify_models({'cookery/models/pot.json': json_bytes(model)}, {'cookery:pot'})


if __name__ == '__main__':
    unittest.main()
