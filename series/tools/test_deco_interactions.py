import unittest

from deco_interactions import function_sounds, link_variants, rules, selected_model


class InteractionTests(unittest.TestCase):
    def test_only_presentation_functions_can_be_used_as_simple_transitions(self):
        files = {"src/example/data/demo/function/simple.mcfunction": b"playsound block.wood.place block @a\nparticle flame ~ ~ ~ 0 0 0 0 1"}
        self.assertEqual(["minecraft:block.wood.place"], function_sounds(files, "demo:simple"))
        files["src/example/data/demo/function/simple.mcfunction"] += b"\nclear @a diamond"
        with self.assertRaises(ValueError):
            function_sounds(files, "demo:simple")

    def test_unknown_side_effect_blocks_the_entire_transition(self):
        definition = {"events": {"right_click": {"fallback": {"event": "trans", "args": {"index": "other", "func": "demo:unknown"}}}}}
        pending = []
        parsed = rules(definition, {}, pending, "first")
        self.assertTrue(parsed[0]["pending"])
        self.assertEqual("", parsed[0]["target"])
        self.assertEqual(1, len(pending))

    def test_dye_model_selects_color_and_form_without_client_components(self):
        base = {"type": "model", "model": "demo:plain"}
        model = {"type": "select", "property": "custom_model_data", "index": 0, "fallback": base,
                 "cases": [{"when": "red", "model": {"type": "condition", "property": "custom_model_data", "index": 0,
                            "on_true": {"type": "model", "model": "demo:red_short"}, "on_false": base}}]}
        self.assertEqual("demo:red_short", selected_model(model, ["red"], [True])["model"])
        self.assertEqual("demo:plain", selected_model(model, ["blue"], [True])["model"])

    def test_variant_graph_handles_cycles_and_rejects_absent_targets(self):
        entries = [{"id": "demo:a", "rules": [{"target": "demo:b", "pending": False}]},
                   {"id": "demo:b", "rules": [{"target": "demo:a", "pending": False}, {"target": "demo:missing", "pending": False}]}]
        furniture = {key: {"variants": {"ground": {"elements": [{"item": key}]}}} for key in ("demo:a", "demo:b")}
        pending = []
        link_variants(entries, furniture, pending)
        self.assertIn("demo/b", furniture["demo:a"]["variants"])
        self.assertTrue(entries[1]["rules"][1]["pending"])
        self.assertEqual(1, len(pending))


if __name__ == "__main__":
    unittest.main()
