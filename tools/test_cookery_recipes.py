"""Check source coverage, recipe quantities and strict conversion boundaries."""

import copy
import json
from pathlib import Path
import unittest

import yaml

from audit_upstream import AuditError
from cookery_recipes import ART, RecipeSources, mapped, read_sources, processing_recipes, validate_recipe
from cookery_stockpot import stockpot_recipes
from cookery_pot import pot_recipes
from export_cookery_kitchen_pack import build, PACK

SOURCE = Path(__file__).resolve().parents[2] / '.references' / 'KaleidoscopeCookery'


class RecipeImportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sources = read_sources(SOURCE)
        cls.bundle, cls.manifest = build(SOURCE)
        cls.config = yaml.safe_load(cls.bundle[f'{PACK}/configuration/kitchen.yml'])
        cls.report = json.loads(cls.bundle[f'{PACK}/recipe-parity.json'])

    def test_every_recipe_is_accounted_for_in_both_namespaces(self):
        rows = self.report['recipes']
        self.assertEqual(612, len(rows))
        self.assertEqual(612, len({row['sourceId'] for row in rows}))
        self.assertTrue(any(row['sourceId'].startswith('minecraft:') for row in rows))
        self.assertEqual(16, len(self.report['sourceTypes']))
        for row in rows:
            self.assertEqual(self.manifest['sourceCommit'], row['sourceCommit'])
            self.assertTrue(row['reason'])
            self.assertEqual(row['status'] == 'imported', bool(row['pluginIds']))
            self.assertEqual(row['source']['sha256'], self.report['sourceFiles'][row['source']['path']])
            for key in row['pluginIds']:
                self.assertIn(key, self.config[row['section']])

    def test_tea_expansion_keeps_fluid_count_and_time(self):
        recipes = self.config['teapot_result']
        self.assertEqual(11, len(recipes))
        butter = recipes['kaleidoscopecookery:teapot/butter_tea']
        self.assertEqual('minecraft:milk', butter['fluid'])
        self.assertEqual('kaleidoscopecookery:butter_tea_bag 1', butter['require'])
        self.assertEqual('kaleidoscopecookery:butter_tea 1', butter['result'])
        self.assertTrue(all(row['time'] == 240 for row in recipes.values()))
        flowers = [row['require'] for key, row in recipes.items() if '/flower_tea/' in key]
        self.assertEqual({f'minecraft:{name} 1' for name in ('lilac', 'rose_bush', 'peony', 'pitcher_plant', 'sunflower')}, set(flowers))

    def test_stockpot_keeps_exact_counts_and_distinct_matching_separate(self):
        recipes = self.config.get('stockpot_recipes', {})
        self.assertEqual(101, len(recipes))
        rice = recipes['kaleidoscopecookery:stockpot/rice_9']
        self.assertEqual('exact', rice['match'])
        self.assertEqual(9, len(rice['ingredients']))
        self.assertEqual(9, rice['result_count'])
        self.assertEqual(900, rice['time'])
        self.assertEqual('minecraft:water_bucket', rice['soup_base'])
        self.assertEqual(0xE9E3DB, rice['visuals']['finished_color'])
        flex = recipes['kaleidoscopecookery:flex_stockpot/pork_bone_soup']
        self.assertEqual('distinct', flex['match'])
        self.assertEqual([['minecraft:bone']], flex['ingredients'])
        self.assertEqual(1, flex['result_count'])
        for row in recipes.values():
            for field in ('cooking_model', 'finished_model'):
                self.assertIn(row['visuals'][field], self.config['items'])
        pending = [r for r in self.report['recipes'] if r['type'] in
                   (ART + ':stockpot', ART + ':flex_stockpot') and r['status'] == 'pending']
        self.assertEqual(4, len(pending))
        self.assertTrue(all('forge:mushrooms' in r['reason'] for r in pending))

    def test_pot_import_preserves_every_alternative_and_official_batch_size(self):
        recipes = self.config.get('pot_recipes', {})
        self.assertEqual(219, len(recipes))
        rows = [r for r in self.report['recipes'] if r['type'] in (ART + ':pot', ART + ':flex_pot')]
        self.assertEqual(225, len(rows))
        for row in rows:
            if row['status'] != 'imported':
                self.assertTrue('forge:mushrooms' in row['reason'] or 'farmersdelight:cooked_rice' in row['reason'])
                continue
            recipe = recipes[mapped(row['sourceId'])]
            original = row['original']
            self.assertEqual([self.sources.expand(value) for value in original['ingredients']], recipe['ingredients'])
            self.assertEqual(original['result'].get('count', 1), recipe['result_count'])
            self.assertEqual(original['stir_fry_count'], recipe['stir_fry_count'])
            self.assertEqual(original['time'], recipe['source_time'])
            self.assertEqual('distinct' if row['type'].endswith(':flex_pot') else 'exact', recipe['match'])
        self.assertEqual(6, sum(row['status'] == 'pending' for row in rows))

    def test_dough_tag_and_steamer_times_are_not_guessed(self):
        recipes = {key: row for key, row in self.config['accurate_foods'].items() if row['cook'] == 'STEAMER'}
        self.assertEqual(4, len(recipes))
        self.assertTrue(all(row['cook'] == 'STEAMER' and row['cooking_time'] == 1200 and row['result_count'] == 1
                            for row in recipes.values()))
        self.assertEqual('kaleidoscopecookery:raw_dough', recipes['kaleidoscopecookery:steamer/mantou']['require'])

    def test_millstone_keeps_all_inputs_outputs_and_one_revolution(self):
        recipes = {key: row for key, row in self.config['accurate_foods'].items() if row['cook'] == 'MILLSTONE'}
        self.assertEqual(87, len(recipes))
        rows = [row for row in self.report['recipes'] if row['type'] == ART + ':millstone']
        self.assertEqual(85, len(rows))
        self.assertEqual(82, sum(row['status'] == 'imported' for row in rows))
        self.assertEqual({'forge:gravel', 'forge:sand/red', 'forge:sand/colorless'},
                         {row['original']['ingredient']['tag'] for row in rows if row['status'] == 'pending'})
        for row in rows:
            if row['status'] != 'imported':
                self.assertIn('required tag absent', row['reason'])
                continue
            original = row['original']
            converted = [recipes[key] for key in row['pluginIds']]
            self.assertEqual(self.sources.expand(original['ingredient']), [value['require'] for value in converted])
            for value in converted:
                self.assertEqual(mapped(original['result']['item']), value['result'])
                self.assertEqual(original['result'].get('count', 1), value['result_count'])
                self.assertEqual(1, value['rotations'])
                self.assertNotIn('cooking_time', value)

    def test_millstone_unknown_tag_and_output_structure_fail(self):
        for field, value in (('ingredient', {'tag': 'forge:unknown'}),
                             ('result', [{'item': 'minecraft:stone'}]),
                             ('result', {'item': 'minecraft:stone', 'nbt': '{}'})):
            sources = copy.deepcopy(self.sources)
            doc = sources.recipes[ART + ':millstone/bone_meal_from_bone']
            doc[field] = value
            with self.subTest(field=field, value=value), self.assertRaises(AuditError):
                processing_recipes(sources)

    def test_cutting_keeps_byproducts_and_all_four_saved_stages(self):
        recipes = self.config['chopping_board_raws']
        self.assertEqual(16, len(recipes))
        chicken = recipes['kaleidoscopecookery:chopping_board/raw_cut_small_meats_from_chicken']
        self.assertEqual('kaleidoscopecookery:raw_cut_small_meats 3', chicken['result'])
        self.assertEqual(['minecraft:bone 1 100'], chicken['extra'])
        for row in recipes.values():
            self.assertEqual(4, row['stage'])
            for stage in range(4):
                self.assertIn(row['values'] + '/' + str(stage), self.config['items'])
            self.assertNotIn(row['values'] + '/4', self.config['items'])

    def test_cups_exclude_the_coaster_and_bars_reference_real_pngs(self):
        self.assertEqual(8, len(self.config['tea_cup']))
        for cup in self.config['tea_cup'].values():
            model = self.config['items'][cup['display_model']]['model']['path']
            namespace, path = model.split(':', 1)
            geometry = json.loads(self.bundle[f'{PACK}/resourcepack/assets/{namespace}/models/{path}.json'])
            self.assertFalse(any(e['from'] == [1, 0, 1] and e['to'] == [15, 1, 15] for e in geometry['elements']))
        for liquid in self.config['teapot_liquid'].values():
            for name, image in liquid.items():
                if not name.startswith('bar_'):
                    continue
                namespace, path = self.config['images'][image]['file'].split(':', 1)
                self.assertTrue(self.bundle[f'{PACK}/resourcepack/assets/{namespace}/textures/{path}'].startswith(b'\x89PNG'))

    def test_soup_geometry_matches_existing_spawn_coordinates(self):
        self.assertEqual(9, len(self.config['stock_food_raw']['liquid']))
        for name in ('water', 'milk', 'lava', 'finished'):
            node = self.config['items']['show:stove_' + name]['model']
            namespace, path = node['path'].split(':', 1)
            model = json.loads(self.bundle[f'{PACK}/resourcepack/assets/{namespace}/models/{path}.json'])
            for corner in ('from', 'to'):
                x, y, z = model['elements'][0][corner]
                self.assertAlmostEqual(.38, .9 + y / 16 - .5)
                self.assertAlmostEqual(3 / 16 if corner == 'from' else 13 / 16, .3 + 3.2 / 16 + x / 16 - .5)
                self.assertEqual(x, z)

    def test_crafting_does_not_strip_nbt_or_missing_alternatives(self):
        crafting = self.config['recipes']
        for name in ('barley', 'biluochun', 'butter', 'oolong', 'sakura_fubuki', 'tieguanyin'):
            key = f'kaleidoscopecookery:upstream/{ART}/{name}_tea_bag'
            self.assertIn(key, crafting)
            self.assertEqual(['LLL', 'LCL', 'LLL'], crafting[key]['pattern'])
        nbt = [row for row in self.report['recipes'] if isinstance(row['original']['result'], dict) and 'nbt' in row['original']['result']]
        self.assertEqual(2, len(nbt))
        self.assertTrue(all(row['status'] == 'pending' and 'NBT' in row['reason'] for row in nbt))

    def test_chain_uses_its_target_version_registry_name(self):
        self.assertEqual('minecraft:iron_chain', mapped('minecraft:chain'))
        spit = self.config['recipes']['kaleidoscopecookery:upstream/kaleidoscope_cookery/shawarma_spit']
        self.assertEqual('minecraft:iron_chain', spit['ingredients']['I'])

    def test_unknown_fields_fail_even_for_pending_recipe_types(self):
        doc = copy.deepcopy(next(doc for doc in self.sources.recipes.values() if doc['type'] == ART + ':pot'))
        for bad in ({**doc, 'secret_output': {'item': 'minecraft:diamond'}}, {**doc, 'type': ART + ':new_machine'},
                    {**doc, 'ingredients': [{'item': 'minecraft:beef', 'count': 2}]},
                    {**doc, 'time': True}):
            with self.subTest(bad=bad), self.assertRaises(AuditError):
                validate_recipe(bad)

    def test_unknown_cyclic_or_empty_required_tags_abort_processing(self):
        doc = copy.deepcopy(self.sources.recipes[ART + ':steamer/mantou'])
        doc['ingredient'] = {'tag': 'test:input'}
        for tags in ({}, {'test:input': {'values': ['#test:input']}}, {'test:input': {'values': []}}):
            source = RecipeSources({ART + ':steamer/mantou': doc}, tags,
                                   {ART + ':steamer/mantou': {}}, {})
            with self.subTest(tags=tags), self.assertRaises(AuditError):
                processing_recipes(source)

    def test_processing_output_nbt_cannot_be_silently_discarded(self):
        doc = copy.deepcopy(self.sources.recipes[ART + ':steamer/mantou'])
        doc['result']['nbt'] = '{CustomModelData:17}'
        source = RecipeSources({ART + ':steamer/mantou': doc}, self.sources.tags,
                               {ART + ':steamer/mantou': {}}, {})
        with self.assertRaisesRegex(AuditError, 'NBT'):
            processing_recipes(source)

    def test_stockpot_unknown_tags_and_nbt_fail_instead_of_becoming_pending(self):
        source_id = ART + ':stockpot/pork_bone_soup'
        doc = copy.deepcopy(self.sources.recipes[source_id])
        for field, value in (('ingredients', [{'tag': 'test:new_unknown'}]),
                             ('result', {'item': ART + ':pork_bone_soup', 'nbt': '{CustomModelData:17}'}),
                             ('empty_carrier', True), ('soup_base', 'test:unknown')):
            changed = {**doc, field: value}
            source = RecipeSources({source_id: changed}, self.sources.tags, {source_id: {}}, {})
            _, rows, _ = processing_recipes(source)
            with self.subTest(field=field), self.assertRaises(AuditError):
                stockpot_recipes(source, rows)

    def test_pot_unknown_inputs_or_output_components_cannot_silently_become_pending(self):
        source_id = ART + ':pot/potato_to_baked_potato_3'
        doc = copy.deepcopy(self.sources.recipes[source_id])
        for field, value in (('ingredients', [{'tag': 'test:new_unknown'}]),
                             ('ingredients', [{'item': 'minecraft:potato'}] * 10),
                             ('result', {'item': 'minecraft:baked_potato', 'nbt': '{CustomModelData:17}'}),
                             ('carrier', {'tag': 'forge:raw_beef'}), ('stir_fry_count', 0),
                             ('new_rule', True)):
            changed = {**doc, field: value}
            source = RecipeSources({source_id: changed}, self.sources.tags, {source_id: {}}, {})
            _, rows, _ = processing_recipes(source)
            with self.subTest(field=field), self.assertRaises(AuditError):
                pot_recipes(source, rows)


if __name__ == '__main__':
    unittest.main()
