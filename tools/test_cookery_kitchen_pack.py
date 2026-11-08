"""Guard state mapping, model dependencies and item conservation in the CE export."""

import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import yaml

from audit_upstream import AuditError
from export_cookery_kitchen_pack import (APPLIANCES, ART, NAMESPACE, PACK, build, source_model,
                                         source_states, write_archive)

SOURCE = Path(__file__).resolve().parents[2] / '.references' / 'KaleidoscopeCookery'


class KitchenPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundle, cls.manifest = build(SOURCE)
        cls.config = yaml.safe_load(cls.bundle[f'{PACK}/configuration/kitchen.yml'])

    def test_every_plugin_property_combination_has_an_appearance(self):
        self.assertEqual(24, len(self.config['blocks']))
        self.assertEqual(28, len(self.config['bamboo_tray_recipes']))
        for name in APPLIANCES:
            with self.subTest(name=name):
                block = self.config['blocks'][NAMESPACE + ':' + name]
                self.assertTrue(block['behavior'][0]['type'].startswith(NAMESPACE + ':'))
                expected = 4
                for values in APPLIANCES[name].values():
                    expected *= len(values)
                self.assertEqual(expected, len(block['states']['variants']))
                for selector, variant in block['states']['variants'].items():
                    self.assertEqual(set(block['states']['properties']), {p.split('=')[0] for p in selector.split(',')})
                    self.assertIn(variant['appearance'], block['states']['appearances'])

    def test_bamboo_tray_sources_are_accounted_as_imported(self):
        self.assertEqual(522, self.manifest['recipeStatuses']['imported'])
        self.assertEqual(90, self.manifest['recipeStatuses']['pending'])
        parity = json.loads(self.bundle[f'{PACK}/recipe-parity.json'])
        tray_records = [row for row in parity['recipes']
                        if row.get('section') == 'bamboo_tray_recipes']
        self.assertEqual(28, len(tray_records))
        self.assertTrue(all(row['status'] == 'imported' for row in tray_records))

    def test_official_plates_use_serving_states_and_runtime_controller(self):
        expected = {
            'apple_platter': (4, ['minecraft:apple']),
            'berry_platter': (4, ['minecraft:sweet_berries', 'minecraft:glow_berries']),
            'baozi_plate': (5, ['kaleidoscopecookery:baozi']),
            'watermelon_platter': (3, ['minecraft:melon_slice']),
        }
        for name, (maximum, servings) in expected.items():
            with self.subTest(name=name):
                block = self.config['blocks'][NAMESPACE + ':' + name]
                self.assertEqual(NAMESPACE + ':plate', block['behavior'][0]['type'])
                self.assertEqual(maximum + 1, len(block['states']['variants']) // 4)
                self.assertEqual(maximum, block['behavior'][0]['max_servings'])
                self.assertEqual(servings, block['behavior'][0]['serving_items'])
                self.assertEqual({'facing', 'servings'}, set(block['states']['properties']))

    def test_basin_visual_levels_keep_old_plugin_oil_semantics(self):
        block = self.config['blocks'][NAMESPACE + ':enamel_basin']['states']
        for level, expected in enumerate(('base', 'empty', 'low', 'middle', 'high')):
            variant = block['variants'][f'facing=north,oil_level={level}']
            display = block['appearances'][variant['appearance']]['entity_renderer']['item']
            self.assertEqual(ART + ':block/enamel_basin/' + expected, self.config['items'][display]['model']['path'])

    def test_steamer_maps_slab_type_and_preserves_double_drop(self):
        block = self.config['blocks'][NAMESPACE + ':steamer']
        for slab, suffix, lift in (('bottom', 'half', '0,0,0'), ('top', 'half', '0,0.5,0'), ('double', 'full', '0,0,0')):
            variant = block['states']['variants'][f'facing=north,type={slab},has_base=false,has_lid=false']
            renderer = block['states']['appearances'][variant['appearance']]['entity_renderer']
            self.assertEqual(lift, renderer['translation'])
            self.assertEqual(ART + ':block/steamer_' + suffix, self.config['items'][renderer['item']]['model']['path'])
        self.assertEqual('double', block['behavior'][1]['extra_drops'][0]['value'])

    def test_filled_containers_and_double_block_do_not_duplicate_base_drops(self):
        for name in ('teapot', 'oil_pot'):
            self.assertEqual([], self.config['blocks'][NAMESPACE + ':' + name]['loot']['pools'])
        spit = self.config['blocks'][NAMESPACE + ':shawarma_spit']
        self.assertIn({'type': 'double_high_block'}, spit['behavior'])
        self.assertEqual({'half': 'lower'}, spit['loot']['pools'][0]['conditions'][0]['properties'])
        self.assertEqual('double_high_block_item', self.config['items'][NAMESPACE + ':shawarma_spit']['behavior']['type'])

    def test_teapot_has_separate_parts_and_preserves_gui_perspective(self):
        node = self.config['items'][NAMESPACE + ':teapot']['model']
        self.assertEqual('minecraft:display_context', node['property'])
        self.assertEqual({'fixed', 'gui', 'ground'}, {case['when'][0] for case in node['cases']})
        parts = []
        for name in ('teapot_1', 'teapot_2'):
            identifier = self.config['items']['show:' + name]['model']['path']
            namespace, path = identifier.split(':', 1)
            model = json.loads(self.bundle[f'{PACK}/resourcepack/assets/{namespace}/models/{path}.json'])
            self.assertNotIn('display', model)
            parts.append(len(model['elements']))
        self.assertEqual([2, 10], parts)

    def test_model_loaders_and_sound_files_are_resolved(self):
        for path, contents in self.bundle.items():
            if '/models/' in path and path.endswith('.json'):
                self.assertNotIn('loader', json.loads(contents), path)
        sounds = json.loads(self.bundle[f'{PACK}/resourcepack/assets/{NAMESPACE}/sounds.json'])
        self.assertEqual(18, len(sounds))
        for event in sounds.values():
            namespace, path = event['sounds'][0].split(':', 1)
            self.assertIn(f'{PACK}/resourcepack/assets/{namespace}/sounds/{path}.ogg', self.bundle)

    def test_millstone_is_furniture_with_all_twelve_source_cubes(self):
        key = NAMESPACE + ':millstone'
        self.assertNotIn(key, self.config['blocks'])
        furniture = self.config['furniture'][key]
        self.assertEqual(1, self.manifest['furniture'])
        self.assertEqual(key, furniture['behavior'][0]['type'])
        self.assertEqual('furniture_item', self.config['items'][key]['behavior']['type'])
        counts = []
        for name in ('millstone_base', 'new_millstone_stick', 'new_millstone_stick2', 'new_millstone_stone'):
            ref = self.config['items']['show:' + name]['model']['path']
            namespace, path = ref.split(':', 1)
            model = json.loads(self.bundle[f'{PACK}/resourcepack/assets/{namespace}/models/{path}.json'])
            counts.append(len(model['elements']))
            for cube in model['elements']:
                self.assertEqual(6, len(cube['faces']))
                self.assertTrue(all(-16 <= v <= 32 for corner in ('from', 'to') for v in cube[corner]))
        self.assertEqual([6, 4, 1, 1], counts)
        provenance = self.manifest['millstoneModel']
        self.assertEqual(self.manifest['sourceCommit'], provenance['sourceCommit'])
        self.assertEqual(64, len(provenance['sha256']))
        self.assertEqual('NOT_TESTED', provenance['clientVisual'])

    def test_unknown_source_state_fails_instead_of_dropping_variants(self):
        source = {'variants': {'facing=north,unknown=true': {'model': ART + ':block/pot'}}}
        with patch('export_cookery_kitchen_pack.read_json', return_value=source), self.assertRaisesRegex(AuditError, 'dimensions'):
            source_states({}, 'pot')
        with self.assertRaisesRegex(AuditError, 'expected one model'):
            source_model('stove', {'facing': 'north', 'lit': 'true'}, [])

    def test_manifest_and_zip_are_reproducible_and_refuse_overwrite(self):
        for path, digest in self.manifest['files'].items():
            self.assertEqual(digest, hashlib.sha256(self.bundle[path]).hexdigest())
        with tempfile.TemporaryDirectory() as directory:
            first, second = Path(directory) / 'one.zip', Path(directory) / 'two.zip'
            write_archive(first, self.bundle)
            write_archive(second, self.bundle)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            before = first.read_bytes()
            with self.assertRaises(FileExistsError):
                write_archive(first, {'wrong': b'changed'})
            self.assertEqual(before, first.read_bytes())


if __name__ == '__main__':
    unittest.main()
