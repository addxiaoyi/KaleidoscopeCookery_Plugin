"""Reject unsupported mesh transforms instead of silently changing the display geometry."""

from pathlib import Path
import unittest

from audit_upstream import AuditError, read_blobs
from cookery_millstone import MODEL_SOURCE, cube_element, parse_mesh
from export_cookery_catalog import COMMIT

SOURCE = Path(__file__).resolve().parents[2] / '.references' / 'KaleidoscopeCookery'


class MillstoneMeshTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = read_blobs(SOURCE, COMMIT, [MODEL_SOURCE])[MODEL_SOURCE].decode('utf-8')

    def test_parent_pivots_cannot_change_without_an_explicit_anchor_mapping(self):
        for old, new in (
                ('PartPose.offset(0.0F, 3.0F, 0.0F)', 'PartPose.offset(1.0F, 3.0F, 0.0F)'),
                ('PartPose.offset(0.0F, 0.0F, 0.0F)', 'PartPose.offset(0.0F, 0.0F, 1.0F)'),
                ('PartPose.offset(0.0F, 4.0F, 0.0F)', 'PartPose.offset(0.0F, 5.0F, 0.0F)'),
                ('PartPose.offset(0.0F, 3.0F, 0.0F)', 'PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, 0.0F, 0.5F, 0.0F)')):
            with self.subTest(pose=new), self.assertRaisesRegex(AuditError, 'parent transform'):
                parse_mesh(self.source.replace(old, new))

    def test_roll_bounds_and_texture_faces_keep_source_dimensions(self):
        part = parse_mesh(self.source)['roll']
        mesh = cube_element(part.cubes[0], part, (0, 0, -8), 1)
        self.assertEqual([2, 2, 1], mesh['from'])
        self.assertEqual([14, 14, 15], mesh['to'])
        self.assertEqual({'down', 'up', 'north', 'south', 'east', 'west'}, set(mesh['faces']))
        # Source -Y becomes +Y: (u + depth, v) to (u + depth + width, v + depth).
        self.assertEqual([11.25, 0, 12.75, 1.75], mesh['faces']['up']['uv'])

    def test_unmapped_leaf_rotation_is_rejected(self):
        parts = parse_mesh(self.source.replace('-0.7854F', '-0.3F'))
        with self.assertRaisesRegex(AuditError, 'unsupported rotation'):
            cube_element(parts['cube_r5'].cubes[0], parts['cube_r5'], (0, 8.8, 0), .5)


if __name__ == '__main__':
    unittest.main()
