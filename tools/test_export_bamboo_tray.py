import json
import unittest

from audit_upstream import AuditError
from export_bamboo_tray import convert_recipe, render_recipes


class BambooTrayExportTests(unittest.TestCase):
    def recipe(self, **fields):
        return {"type": "kaleidoscope_cookery:bamboo_tray", "duration": 1800,
                "ingredient": {"item": "minecraft:kelp"},
                "result": {"item": "minecraft:dried_kelp"}, "subtype": "drying", **fields}

    def convert(self, source):
        return convert_recipe("kaleidoscope_cookery:bamboo_tray/drying/kelp", json.dumps(source).encode())

    def test_vanilla_semantics_are_retained(self):
        recipe = self.convert(self.recipe())
        self.assertEqual((recipe["require"], recipe["result"], recipe["mode"], recipe["time"], recipe["result_count"]),
                         ("minecraft:kelp", "minecraft:dried_kelp", "drying", 1800, 1))
        self.assertIn('time: 1800', render_recipes([recipe], 'a' * 40))

    def test_custom_aliases_are_explicit(self):
        recipe = self.convert(self.recipe(ingredient={"item": "kaleidoscope_cookery:fresh_tea_leaves"},
                                          result={"item": "kaleidoscope_cookery:dried_tea_leaves"}))
        self.assertEqual(recipe["require"], "kaleidoscopecookery:fresh_tea_leaves")
        with self.assertRaisesRegex(AuditError, "unmapped"):
            self.convert(self.recipe(result={"item": "kaleidoscope_cookery:new_food"}))

    def test_unrecognized_structure_cannot_be_silently_lost(self):
        cases = [self.recipe(ingredient={"tag": "minecraft:leaves"}), self.recipe(extra=True),
                 self.recipe(result={"item": "minecraft:kelp", "nbt": "{}"}),
                 self.recipe(subtype="smoking"), self.recipe(type="minecraft:smelting")]
        for source in cases:
            with self.subTest(source=source), self.assertRaises(AuditError):
                self.convert(source)

    def test_invalid_counts_and_times_are_rejected(self):
        for value in (True, 0, -1, 1.5, "1800", 2**31):
            with self.subTest(value=value), self.assertRaises(AuditError):
                self.convert(self.recipe(duration=value))
            with self.subTest(count=value), self.assertRaises(AuditError):
                self.convert(self.recipe(result={"item": "minecraft:kelp", "count": value}))

    def test_duplicate_json_keys_fail(self):
        with self.assertRaisesRegex(AuditError, "duplicate JSON key"):
            convert_recipe("kaleidoscope_cookery:bamboo_tray/test", b'{"duration":1,"duration":2}')


if __name__ == "__main__":
    unittest.main()
