import copy
import unittest
from repair_villager_animation_exports import repair


class ExportRepairTest(unittest.TestCase):
    def test_neutral_exported_face_and_leg_tracks_are_removed_without_changing_body_motion(self):
        arm = {'orientation': [{'time': 12, 'delta': {'x': 0.6, 'y': 0, 'z': 0, 'w': 0.8}}]}
        neutral = {'orientation': [{'time': 0, 'delta': {'x': 0, 'y': 0, 'z': 0, 'w': 1}}]}
        source = {'duration': 60, 'nodeAnimations': {'R-Arm': arm, 'Mouth': neutral, 'L-Thigh': neutral}}
        before = copy.deepcopy(source)
        result = repair(source)
        self.assertEqual(source, before)
        self.assertEqual(set(result['nodeAnimations']), {'R-Arm'})
        self.assertEqual(result['nodeAnimations']['R-Arm']['orientation'], arm['orientation'])
        self.assertEqual(repair(result), result)

    def test_active_unexpected_tracks_cannot_be_silently_deleted(self):
        with self.assertRaisesRegex(ValueError, 'Mouth'):
            repair({'nodeAnimations': {'Mouth': {'position': [{'time': 0, 'delta': {'x': 0, 'y': 1, 'z': 0}}]}}})

    def test_rounded_rotation_is_normalized_without_changing_its_direction(self):
        q = dict(x=0.60001, y=0, z=0, w=0.8)
        fixed = repair({'nodeAnimations': {'R-Arm': {'orientation': [{'time': 10, 'delta': q}]}}})
        corrected = fixed['nodeAnimations']['R-Arm']['orientation'][0]['delta']
        self.assertAlmostEqual(sum(v*v for v in corrected.values()), 1, places=6)
        self.assertAlmostEqual(corrected['x']/corrected['w'], q['x']/q['w'], places=6)
        self.assertEqual(repair(fixed), fixed)


if __name__ == '__main__':
    unittest.main()
