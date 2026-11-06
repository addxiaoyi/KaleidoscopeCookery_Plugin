from pathlib import Path
import tempfile
import subprocess
import unittest

from audit_upstream import AuditError, collect_recipes, literal_registrations, read_blobs, recipe_type


class UpstreamInventoryTests(unittest.TestCase):
    def test_comments_do_not_inflate_inventory_or_shift_lines(self) -> None:
        source = '// ITEMS.register("fake", Factory::new);\n/* block\ncomment */\nITEMS.register("rice", Factory::new);'
        self.assertEqual(
            literal_registrations(source, "ITEMS.register"),
            [{"id": "kaleidoscope_cookery:rice", "line": 4}],
        )

    def test_comment_markers_inside_strings_are_preserved(self) -> None:
        source = 'String url = "https://example.invalid";\nITEMS.register("rice", Factory::new);'
        self.assertEqual(literal_registrations(source, "ITEMS.register")[0]["line"], 2)

    def test_example_calls_inside_strings_are_not_registrations(self) -> None:
        source = 'String example = "ITEMS.register(\\"fake\\", Factory::new)";\nITEMS.register("rice", Factory::new);'
        self.assertEqual(literal_registrations(source, "ITEMS.register"), [{"id": "kaleidoscope_cookery:rice", "line": 2}])

    def test_text_block_examples_are_not_registrations(self) -> None:
        source = 'String example = """\nITEMS.register("fake", Factory::new);\n""";\nITEMS.register("rice", Factory::new);'
        self.assertEqual(literal_registrations(source, "ITEMS.register"), [{"id": "kaleidoscope_cookery:rice", "line": 4}])

    def test_dynamic_registrations_require_manual_review(self) -> None:
        with self.assertRaisesRegex(AuditError, "non-literal"):
            literal_registrations('ITEMS.register(prefix + "rice", Factory::new);', "ITEMS.register")

    def test_duplicate_ids_are_rejected(self) -> None:
        with self.assertRaisesRegex(AuditError, "duplicate literal"):
            literal_registrations('ITEMS.register("rice", f); ITEMS.register("rice", g);', "ITEMS.register")

    def test_bad_recipe_shapes_fail_with_path(self) -> None:
        for raw in (b"[]", b"{}", b'{"type": null}', b'{"type":"a","type":"b"}'):
            with self.subTest(raw=raw), self.assertRaisesRegex(AuditError, "rice.json"):
                recipe_type(raw, "rice.json")

    def test_resource_root_collisions_are_not_silently_overwritten(self) -> None:
        paths = [f"src/{kind}/resources/data/kaleidoscope_cookery/recipes/rice.json" for kind in ("main", "generated")]
        with self.assertRaisesRegex(AuditError, "duplicate recipe resource"):
            collect_recipes({path: b'{"type":"minecraft:smelting"}' for path in paths})

    def test_nested_recipe_id_and_hash_are_kept(self) -> None:
        path = "src/main/resources/data/kaleidoscope_cookery/recipes/tray/rice.json"
        recipes = collect_recipes({path: b'{"type":"kaleidoscope_cookery:bamboo_tray"}'})
        self.assertEqual(recipes[0]["id"], "kaleidoscope_cookery:tray/rice")
        self.assertEqual(len(recipes[0]["source"]["sha256"]), 64)

    def test_pinned_blobs_ignore_hidden_worktree_edits(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            def git(*args: str) -> str:
                return subprocess.run(["git", "-C", str(root), *args], check=True, capture_output=True, text=True).stdout.strip()

            git("init", "-q")
            target = root / "source.json"
            target.write_bytes(b'{"type":"original"}')
            git("add", "source.json")
            git("-c", "user.name=Inventory Test", "-c", "user.email=inventory@example.invalid", "commit", "-qm", "fixture")
            commit = git("rev-parse", "HEAD")
            git("update-index", "--assume-unchanged", "source.json")
            target.write_bytes(b'{"type":"modified"}')
            self.assertEqual(git("status", "--porcelain"), "")
            self.assertEqual(read_blobs(root, commit, ["source.json"])["source.json"], b'{"type":"original"}')
            with self.assertRaisesRegex(AuditError, "missing or invalid"):
                read_blobs(root, commit, ["missing.json"])


if __name__ == "__main__":
    unittest.main()
