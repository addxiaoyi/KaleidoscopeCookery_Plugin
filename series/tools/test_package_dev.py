import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

from package_dev import load_module_packs


class PackageDevTests(unittest.TestCase):
    def test_load_module_packs_requires_four_independent_plugin_packs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for module in ("doll", "tavern", "deco", "music"):
                with ZipFile(root / f"{module}-ce-pack-0.1.0-dev.zip", "w", ZIP_DEFLATED) as archive:
                    archive.writestr("pack.yml", module.encode())
                    archive.writestr("configuration/content.yml", b"{}")
            merged, archives = load_module_packs(root)
            self.assertEqual(4, len(archives))
            self.assertEqual(b"doll", merged["doll/pack.yml"])
            self.assertEqual(b"{}", merged["music/configuration/content.yml"])


if __name__ == "__main__":
    unittest.main()
