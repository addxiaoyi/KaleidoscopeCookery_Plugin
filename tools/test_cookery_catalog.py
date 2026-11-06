"""Regression coverage for the pinned food/tea importer and atomic snapshots."""

import json
from pathlib import Path
import tempfile
import unittest

from audit_upstream import AuditError
from export_cookery_catalog import parse_foods, parse_bites, parse_teas, parse_plates, publish, number


class CookeryImportTest(unittest.TestCase):
    def test_food_effects_and_flags(self):
        foods = parse_foods('''FoodProperties SOUP = (new FoodProperties.Builder())
            .nutrition(0).saturationMod(0.5F).alwaysEat().fast()
            .effect(() -> new MobEffectInstance(WARMTH.get(), 3 * 60 * 20, 1), .25F).build();''')
        soup = foods['SOUP']
        self.assertEqual(0, soup['nutrition'])
        self.assertEqual(0, soup['saturation'])
        self.assertTrue(soup['alwaysEat'])
        self.assertEqual(16, soup['consumeTicks'])
        self.assertEqual({'id': 'kaleidoscope_cookery:warmth', 'duration': 3600,
                          'amplifier': 1, 'probability': .25}, soup['effects'][0])

    def test_unknown_builder_must_fail(self):
        with self.assertRaises(AuditError):
            parse_foods('FoodProperties X = new FoodProperties.Builder().nutrition(1).saturationMod(0).mystery().build();')

    def test_unrecognized_initializer_must_fail(self):
        with self.assertRaises(AuditError):
            parse_foods('FoodProperties X = externalFactory();')

    def test_duplicates_must_fail(self):
        declaration = 'FoodProperties X = new FoodProperties.Builder().nutrition(1).saturationMod(0).build();'
        with self.assertRaises(AuditError):
            parse_foods(declaration + declaration)

    def test_numbers_are_not_executable(self):
        for expression in ['__import__("os")', '1/0', '2 ** 100000', 'true', '1 << 4', 'float("nan")']:
            with self.subTest(expression=expression), self.assertRaises(AuditError):
                number(expression)

    def test_bites_are_separate_and_loot_is_appended(self):
        foods = parse_foods('''FoodProperties BITE = new FoodProperties.Builder().nutrition(3).saturationMod(.5F).build();
            FoodProperties WHOLE = new FoodProperties.Builder().nutrition(9).saturationMod(.5F).build();''')
        bites = parse_bites('''registry.registerFoodData("meal", FoodData.createOneByTwo(3, BITE, WHOLE)
            .addLootItems(Items.SNOWBALL, Items.SNOWBALL));''', foods)
        self.assertEqual(9, bites['meal']['food']['nutrition'])
        self.assertEqual(3, bites['meal']['bites']['food']['nutrition'])
        self.assertEqual('1x2', bites['meal']['bites']['layout'])
        self.assertEqual(['minecraft:bowl', 'minecraft:snowball', 'minecraft:snowball'], bites['meal']['bites']['drops'])

    def test_unknown_bite_and_tea_methods_fail(self):
        with self.assertRaises(AuditError):
            parse_teas('registry.registerTeacupData("tea", TeacupData.create(4).secret());')
        with self.assertRaises(AuditError):
            parse_bites('registry.registerFoodData(getName(), FoodData.create(3, X, X));', {})

    def test_tea_default_probability_and_vanilla_alias(self):
        tea = parse_teas('''registry.registerTeacupData("oolong", TeacupData.create(4)
            .addEffect(() -> new MobEffectInstance(MobEffects.JUMP, 6 * 60 * 20)));''')['oolong']
        self.assertEqual(4, tea['maxCups'])
        self.assertEqual('minecraft:jump_boost', tea['effects'][0]['id'])
        self.assertEqual(7200, tea['effects'][0]['duration'])

    def test_serving_plates_preserve_alternative_items(self):
        plate = parse_plates('''registry.registerPlateData("berries", PlateData.create(4)
            .addServingItems(() -> Items.SWEET_BERRIES, () -> Items.GLOW_BERRIES).setLootItem(Items.BOWL));''')['berries']
        self.assertEqual(['minecraft:sweet_berries', 'minecraft:glow_berries'], plate['servings'])
        self.assertEqual(4, plate['maxServings'])
        with self.assertRaises(AuditError):
            parse_plates('registry.registerPlateData("x", PlateData.create(4).setServingItems(unknown()));')

    def test_failed_serialization_keeps_previous_generation(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'catalog.json'
            publish(path, {'generation': 1})
            previous = path.read_bytes()
            with self.assertRaises(ValueError):
                publish(path, {'bad': float('nan')})
            self.assertEqual(previous, path.read_bytes())

    def test_check_does_not_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'catalog.json'
            publish(path, {'generation': 1})
            with self.assertRaises(AuditError):
                publish(path, {'generation': 2}, check=True)
            self.assertEqual(1, json.loads(path.read_text())['generation'])


if __name__ == '__main__':
    unittest.main()
