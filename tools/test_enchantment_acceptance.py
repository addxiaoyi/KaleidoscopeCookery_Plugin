"""Acceptance evidence must not hide new errors or mutate existing server directories."""

from pathlib import Path
import hashlib
import json
import unittest
from tempfile import TemporaryDirectory
from unittest.mock import patch
import zipfile

from run_enchantment_acceptance import (ACCEPTANCE, COMMIT, console_text, install_kitchen,
                                        missing_block_error, prepare, validate_kitchen, validate_recipes, validate_pots,
                                        validate_millstone)


class AcceptanceSafetyTests(unittest.TestCase):
    def test_millstone_requires_animal_progress_and_three_restart_phases(self) -> None:
        manifest = {'furniture': 1}
        report = {'failures': [], 'checkedFurniture': 1, 'phase': 'IN_PROGRESS_SAVED',
                  'rawCount': 2, 'progressDegrees': [9, 9], 'completionEvents': 0, 'spawnedOutput': 0}
        validate_millstone(report, manifest, 1)
        for bad in ([], [0, 0], [360, 360], [float('nan'), 9]):
            with self.subTest(progress=bad), self.assertRaises(ValueError):
                validate_millstone({**report, 'progressDegrees': bad}, manifest, 1)
        with self.assertRaisesRegex(ValueError, 'COMPLETED_RELOADED'):
            validate_millstone(report, manifest, 2)
        report.update(phase='COMPLETED_RELOADED', reloadedFurniture=1, emptyNoDuplicate=True,
                      completionEvents=1, spawnedOutput=10, outputCount=10, animalRestored=True)
        validate_millstone(report, manifest, 2)
        for field, bad in (('reloadedFurniture', 0), ('completionEvents', 2), ('spawnedOutput', 5),
                           ('outputCount', 0), ('animalRestored', False), ('emptyNoDuplicate', False)):
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_millstone({**report, field: bad}, manifest, 2)
        with self.assertRaisesRegex(ValueError, 'EMPTY_RELOADED'):
            validate_millstone(report, manifest, 3)
        report.update(phase='EMPTY_RELOADED', completionEvents=0, spawnedOutput=0)
        validate_millstone(report, manifest, 3)
        with self.assertRaises(ValueError):
            validate_millstone({**report, 'spawnedOutput': 10}, manifest, 3)

    def test_pot_probe_requires_real_restart_and_partial_serving_evidence(self) -> None:
        manifest = {'recipes': {'pot_recipes': 219}}
        report = {'failures': [], 'checkedPlans': 219, 'exactRecipes': 195, 'distinctRecipes': 24,
                  'phase': 'IN_PROGRESS_SAVED', 'stirs': [1, 1]}
        validate_pots(report, manifest, 1)
        with self.assertRaisesRegex(ValueError, 'COMPLETED_RELOADED'):
            validate_pots(report, manifest, 2)
        report.update(phase='COMPLETED_RELOADED', reloadedPots=2, servings=[2, 0], emptyNoDuplicate=True)
        validate_pots(report, manifest, 2)
        for field, bad in (('checkedPlans', 218), ('exactRecipes', 0), ('reloadedPots', 0),
                           ('servings', [3, 1]), ('emptyNoDuplicate', False)):
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_pots({**report, field: bad}, manifest, 2)

    def test_runtime_counts_must_match_the_new_manifest(self) -> None:
        manifest = {'blocks': 12, 'states': {'example': 180}, 'items': 252}
        report = {'failures': [], 'checkedBlocks': 12, 'checkedStates': 180, 'checkedItems': 252,
                  'nonemptyContainers': 2, 'placedBlocks': 12, 'reloadedBlocks': 0}
        validate_kitchen(report, manifest, 1)
        with self.assertRaisesRegex(ValueError, 'installed manifest'):
            validate_kitchen({**report, 'checkedItems': 165}, manifest, 1)
        with self.assertRaisesRegex(ValueError, 'installed manifest'):
            validate_kitchen(report, manifest, 2)

    def test_registration_cannot_stand_in_for_resumed_processing(self) -> None:
        manifest = {'recipes': {'teapot_result': 11, 'accurate_foods': 4, 'chopping_board_raws': 16, 'recipes': 20},
                    'teaCups': 8, 'soupBases': 9}
        report = {'failures': [], 'checkedPlans': 31, 'checkedCrafting': 20, 'teaCups': 8, 'soupBases': 9,
                  'phase': 'IN_PROGRESS_SAVED'}
        validate_recipes(report, manifest, 1)
        with self.assertRaisesRegex(ValueError, 'COMPLETED_RELOADED'):
            validate_recipes(report, manifest, 2)
        report.update(phase='COMPLETED_RELOADED', reloadedBatches=3, steamedOutputs=4, teaServings=8,
                      cuttingDrops={'kaleidoscopecookery:raw_cut_small_meats': 3, 'minecraft:bone': 1})
        validate_recipes(report, manifest, 2)
        with self.assertRaisesRegex(ValueError, 'conservation'):
            validate_recipes({**report, 'cuttingDrops': {'minecraft:bone': 2}}, manifest, 2)

    def test_resource_pack_completion_survives_console_colour_codes(self) -> None:
        with TemporaryDirectory() as folder:
            log = Path(folder) / 'server.log'
            log.write_text('[18:28:48 INFO]: \x1b[97m资源包生成完成\x1b[37m（\x1b[33m1893\x1b[37m毫秒）\x1b[0m', encoding='utf-8')
            self.assertIn('资源包生成完成（1893毫秒）', console_text(log))

    def test_stockpot_coverage_and_partial_servings_are_required(self) -> None:
        manifest = {'recipes': {'teapot_result': 11, 'accurate_foods': 4, 'chopping_board_raws': 16,
                                'stockpot_recipes': 101, 'recipes': 33}, 'teaCups': 8, 'soupBases': 9}
        report = {'failures': [], 'checkedPlans': 132, 'checkedCrafting': 33, 'teaCups': 8, 'soupBases': 9,
                  'phase': 'COMPLETED_RELOADED', 'reloadedBatches': 3, 'steamedOutputs': 4, 'teaServings': 8,
                  'cuttingDrops': {'kaleidoscopecookery:raw_cut_small_meats': 3, 'minecraft:bone': 1},
                  'exactStockpotRecipes': 75, 'distinctStockpotRecipes': 26, 'reloadedStockpots': 2,
                  'stockpotServings': [2, 0], 'stockpotLiquidOffset': -0.105, 'stockpotEmptyNoDuplicate': True}
        validate_recipes(report, manifest, 2)
        for field, bad in (('distinctStockpotRecipes', 0), ('reloadedStockpots', 0),
                           ('stockpotServings', [3, 1]), ('stockpotEmptyNoDuplicate', False),
                           ('stockpotLiquidOffset', 0)):
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_recipes({**report, field: bad}, manifest, 2)

    def test_known_missing_appliance_remains_an_explicit_fixture_gap(self) -> None:
        self.assertTrue(missing_block_error('[18:00 ERROR]: [KaleidoscopeCookeryPlugin] craftengine.registry.blocks.missing'))
        self.assertTrue(missing_block_error('[18:00 ERROR]: [KaleidoscopeCookeryPlugin] - kaleidoscopecookery:pot    配置：custom/pot.yml'))
        self.assertFalse(missing_block_error('[18:00 ERROR]: [CraftEngine] - kaleidoscopecookery:pot    配置：custom/pot.yml'))
        self.assertFalse(missing_block_error('[18:00 ERROR]: [KaleidoscopeCookeryPlugin] - kaleidoscopecookery:pot_new    配置：custom/pot.yml'))
        self.assertFalse(missing_block_error('[18:00 ERROR]: Registry loading errors:'))
        self.assertFalse(missing_block_error('[18:00 ERROR]: [KaleidoscopeCookeryPlugin] failed while saving inventory'))

    def test_cannot_launch_without_explicit_eula_flag(self) -> None:
        with self.assertRaisesRegex(ValueError, 'accept-eula'):
            prepare('paper', ACCEPTANCE / 'unused', False)

    def test_cannot_reuse_primary_instances_or_escape_acceptance_root(self) -> None:
        for root in (ACCEPTANCE, ACCEPTANCE.parent, Path.cwd().anchor):
            with self.subTest(root=root), self.assertRaisesRegex(ValueError, 'separate directory'):
                prepare('paper', Path(root), True)

    def test_invalid_kitchen_archive_preserves_food_fixture(self) -> None:
        for name, content, checksum in (('cookery_kitchen/../../escape', b'a', None),
                                        ('cookery_kitchen/../default_assets/injected.yml', b'a', None),
                                        ('cookery_kitchen/pack.yml', b'a', 'bad')):
            with self.subTest(name=name), TemporaryDirectory() as folder:
                root = Path(folder).resolve()
                instance = root / 'run' / 'paper'
                food = instance / 'plugins/CraftEngine/resources/cookery_foods'
                food.mkdir(parents=True)
                (food / 'sentinel').write_text('preserve', encoding='utf-8')
                manifest = {'sourceCommit': COMMIT, 'target': {'minecraft': '1.21.11', 'craftEngine': '26.9.1'},
                            'files': {name: checksum or hashlib.sha256(content).hexdigest()}}
                archive = root / 'pack.zip'
                with zipfile.ZipFile(archive, 'w') as package:
                    package.writestr('manifest.json', json.dumps(manifest))
                    package.writestr(name, content)
                with patch('run_enchantment_acceptance.ACCEPTANCE', root), self.assertRaises(ValueError):
                    install_kitchen(instance, archive)
                self.assertEqual('preserve', (food / 'sentinel').read_text(encoding='utf-8'))
                self.assertFalse((instance / 'plugins/CraftEngine/fixture-backups').exists())

    def test_kitchen_install_keeps_original_foods_outside_active_resources(self) -> None:
        with TemporaryDirectory() as folder:
            root = Path(folder).resolve()
            instance = root / 'run' / 'paper'
            food = instance / 'plugins/CraftEngine/resources/cookery_foods'
            food.mkdir(parents=True)
            (food / 'sentinel').write_text('preserve', encoding='utf-8')
            name, content = 'cookery_kitchen/pack.yml', b'namespace: kaleidoscopecookery\n'
            manifest = {'sourceCommit': COMMIT, 'target': {'minecraft': '1.21.11', 'craftEngine': '26.9.1'},
                        'files': {name: hashlib.sha256(content).hexdigest()}}
            archive = root / 'pack.zip'
            with zipfile.ZipFile(archive, 'w') as package:
                package.writestr('manifest.json', json.dumps(manifest))
                package.writestr(name, content)
            with patch('run_enchantment_acceptance.ACCEPTANCE', root):
                self.assertEqual(manifest, install_kitchen(instance, archive))
            engine = instance / 'plugins/CraftEngine'
            self.assertFalse(food.exists())
            self.assertEqual('preserve', (engine / 'fixture-backups/cookery_foods/sentinel').read_text(encoding='utf-8'))
            self.assertEqual(content, (engine / 'resources' / name).read_bytes())


if __name__ == '__main__':
    unittest.main()
