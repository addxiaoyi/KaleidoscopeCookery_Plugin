import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from check_acceptance_matrix import load as load_matrix
from prepare_independent_servers import _within, verify_zip


class AcceptancePreparationTests(unittest.TestCase):
    def test_matrix_keeps_all_five_modules_explicit(self):
        matrix = load_matrix()
        self.assertEqual({"cookery", "doll", "tavern", "deco", "music"},
                         {row["module"] for row in matrix["modules"]})

    def test_server_root_must_be_inside_acceptance_directory(self):
        parent = Path("/tmp/senluo-acceptance")
        self.assertTrue(_within(parent / "paper", parent))
        self.assertFalse(_within(Path("/tmp/senluo-prod"), parent))

    def test_archive_path_escape_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "unsafe.zip"
            with ZipFile(archive, "w") as output:
                output.writestr("../outside", b"bad")
            with self.assertRaises(ValueError):
                verify_zip(archive, ())


if __name__ == "__main__":
    unittest.main()
