import unittest
from import_sources import (parse_json, resolve_ingredient, strip_trailing_commas, model_tree,
                            inherit_template, loot_components, crafting, json_bytes, pack_groups,
                            load_module_specs)


class ImportTests(unittest.TestCase):
    def test_module_specs_define_unique_converter_boundaries(self):
        version, modules, shared = load_module_specs()
        self.assertRegex(version, r"^[A-Za-z0-9._-]+$")
        self.assertEqual(("doll", "tavern", "deco", "music"), tuple(m["id"] for m in modules))
        self.assertEqual("shared", shared)

    def test_forge_tags_keep_vanilla_members_and_reject_unknown_groups(self):
        self.assertEqual(["minecraft:copper_ingot"], resolve_ingredient({"tag": "forge:ingots/copper"}, {}))
        self.assertEqual(17, len(resolve_ingredient({"tag": "forge:glass_panes"}, {})))
        with self.assertRaises(ValueError):
            resolve_ingredient({"tag": "forge:ingots/unobtainium"}, {})

    def test_duplicate_json_keys_are_rejected(self):
        with self.assertRaises(ValueError):
            parse_json(b'{"id":"first","id":"second"}')

    def test_nested_tag_alternatives_are_resolved_without_duplicates(self):
        tags = {"test/tags/items/a.json": json_bytes({"values": ["minecraft:apple", "#test:b"]}),
                "test/tags/items/b.json": json_bytes({"values": ["minecraft:apple", "minecraft:sugar"]})}
        self.assertEqual(["minecraft:apple", "minecraft:sugar"], resolve_ingredient({"tag": "test:a"}, tags))

    def test_cyclic_and_missing_tags_are_errors(self):
        tags = {"test/tags/items/a.json": json_bytes({"values": ["#test:a"]})}
        with self.assertRaises(ValueError):
            resolve_ingredient({"tag": "test:a"}, tags)
        with self.assertRaises(ValueError):
            resolve_ingredient({"tag": "test:missing"}, tags)

    def test_snbt_commas_inside_strings_are_preserved(self):
        self.assertEqual('{x:[1f,2f],text:"a,] b,}",q:"\\\"",a:3}',
                         strip_trailing_commas('{x:[1f,2f,],text:"a,] b,}",q:"\\\"",a:3,}'))

    def test_model_branches_remap_only_resource_references(self):
        model = {"type": "select", "property": "display_context", "cases": [
            {"when": "gui", "model": {"type": "model", "model": "deco:item/preview"}}],
                 "fallback": {"type": "composite", "models": [{"type": "model", "model": "deco:item/body"}]}}
        converted = model_tree(model, {"deco"})
        self.assertEqual("minecraft:select", converted["type"])
        self.assertEqual("gui", converted["cases"][0]["when"])
        self.assertEqual("kdeco_deco:item/preview", converted["cases"][0]["model"]["path"])
        self.assertEqual("kdeco_deco:item/body", converted["fallback"]["models"][0]["path"])
        self.assertIn("model", model["cases"][0]["model"])

    def test_template_inheritance_keeps_unoverridden_nested_fields(self):
        templates = {"base": ({"interactsize": {"height": 1, "width": 2}}, "source")}
        self.assertEqual({"height": 1, "width": 3}, inherit_template(
            {"template": "base", "interactsize": {"width": 3}}, templates)["interactsize"])
        with self.assertRaises(ValueError):
            inherit_template({"template": "cycle"}, {"cycle": ({"template": "cycle"}, "source")})

    def test_ambiguous_loot_does_not_choose_an_arbitrary_model(self):
        entries = [{"functions": [{"function": "set_components", "components": {"minecraft:item_model": model}}]}
                   for model in ["test:a", "test:b"]]
        with self.assertRaises(ValueError):
            loot_components({"pools": [{"entries": entries}]})

    def test_crafting_keeps_vanilla_tags_and_reports_missing_custom_items(self):
        recipe = {"type": "minecraft:crafting_shaped", "pattern": ["A"],
                  "key": {"A": {"tag": "minecraft:planks"}}, "result": {"item": "test:chair"}}
        tags = {"test/recipes/chair.json": json_bytes(recipe)}
        converted, pending = crafting(tags, {"test:chair": {}})
        self.assertFalse(pending)
        self.assertEqual("#minecraft:planks", converted["test:chair"]["ingredients"]["A"])
        converted, pending = crafting(tags, {})
        self.assertFalse(converted)
        self.assertEqual(1, len(pending))

    def test_ce_bundle_splits_into_independent_module_trees(self):
        bundle = {
            "doll/pack.yml": b"doll",
            "doll/configuration/content.yml": b"{}",
            "tavern/pack.yml": b"tavern",
            "tavern/configuration/content.yml": b"{}",
            "deco/pack.yml": b"deco",
            "deco/configuration/content.yml": b"{}",
            "music/pack.yml": b"music",
            "music/configuration/content.yml": b"{}",
            "shared/pack.yml": b"shared",
        }
        groups = pack_groups(bundle)
        self.assertEqual(b"doll", groups["doll"]["pack.yml"])
        self.assertNotIn("tavern/pack.yml", groups["doll"])
        with self.assertRaisesRegex(ValueError, "Unscoped"):
            pack_groups({"root.txt": b"bad"})


if __name__ == "__main__":
    unittest.main()
