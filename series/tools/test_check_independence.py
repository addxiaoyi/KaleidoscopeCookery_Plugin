import unittest

from check_independence import check


class IndependenceTests(unittest.TestCase):
    def test_source_metadata_is_independent(self):
        report = check()
        self.assertEqual([], report["errors"])
        self.assertEqual(4, report["modules"])


if __name__ == "__main__":
    unittest.main()
