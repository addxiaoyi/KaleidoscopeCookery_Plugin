import unittest

from tavern_furniture import VANILLA_BOTTLES, add_variants


class TavernFurnitureTests(unittest.TestCase):
    def fixtures(self):
        furniture = {"kaleidoscope_tavern:" + name: {"variants": {"ground": {
            "elements": [{"item": "kaleidoscope_tavern:" + name}], "hitboxes": []}}}
            for name in ("trellis", "empty_bottle", "empty_glassware")}
        assets = {}
        for fruit in ("grape", "ice_grape", "gold_grape"):
            plant = fruit.replace("grape", "grapevine_trellis")
            for age in range(4):
                assets[f"kaleidoscope_tavern/models/block/plant/{plant}/single_stage{age}.json"] = b"{}"
            for age in range(6):
                assets[f"kaleidoscope_tavern/models/block/plant/{fruit}_crop/stage{age}.json"] = b"{}"
        for bottle in VANILLA_BOTTLES.values():
            assets[f"kaleidoscope_tavern/models/block/brew/{bottle}.json"] = b"{}"
        return furniture, assets

    def test_vanilla_drinks_use_block_models_without_fake_custom_item_ids(self):
        furniture, assets = self.fixtures()
        items = {}
        add_variants(items, furniture, {"effects": {}, "barrel": []}, assets)
        for carrier in ("empty_bottle", "empty_glassware"):
            variants = furniture["kaleidoscope_tavern:" + carrier]["variants"]
            for output, model in VANILLA_BOTTLES.items():
                display = variants[output.replace(":", "/")]["elements"][0]["item"]
                self.assertEqual(f"kaleidoscope_tavern:block/brew/{model}", items[display]["model"]["path"])
                self.assertNotIn("kaleidoscope_tavern:" + model, items)
            self.assertEqual("kaleidoscope_tavern:" + carrier, variants["ground"]["elements"][0]["item"])

    def test_missing_bottle_model_rejects_export(self):
        furniture, assets = self.fixtures()
        del assets["kaleidoscope_tavern/models/block/brew/water_bottle.json"]
        with self.assertRaisesRegex(ValueError, "water_bottle"):
            add_variants({}, furniture, {"effects": {}, "barrel": []}, assets)


if __name__ == "__main__":
    unittest.main()
