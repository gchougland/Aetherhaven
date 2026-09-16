"""Regression coverage for motion-preserving animation sharing and cleanup."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from villager_animation_pool import AnimationPool


class AnimationPoolTest(unittest.TestCase):
    def test_shared_rigs_reuse_data_but_different_timing_and_uvs_do_not(self):
        with tempfile.TemporaryDirectory() as temp:
            res = Path(temp)
            def write(path, data):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(data))
            original = {'duration': 120, 'holdLastKeyframe': False, 'nodeAnimations': {
                'Mouth': {'shapeUvOffset': [{'time': 0, 'delta': {'x': 0, 'y': 0}}]}}}
            template = res/'Common/Life/Base.blockyanim'
            write(template, original)
            pool = AnimationPool(res, write, [template])
            equivalent = copy.deepcopy(original)
            equivalent['duration'] = 120.0
            equivalent['nodeAnimations']['Mouth']['position'] = []
            self.assertEqual('Life/Base.blockyanim', pool.emit('Generated/Human.blockyanim', equivalent))
            self.assertEqual('Life/Base.blockyanim', pool.emit('Generated/OtherRace.blockyanim', original))
            for field, value in [('duration', 121), ('holdLastKeyframe', True)]:
                changed = copy.deepcopy(original)
                changed[field] = value
                target = f'Generated/{field}.blockyanim'
                self.assertEqual(target, pool.emit(target, changed))
            changed = copy.deepcopy(original)
            changed['nodeAnimations']['Mouth']['shapeUvOffset'][0]['delta']['y'] = -8
            self.assertEqual('Generated/Prowl.blockyanim', pool.emit('Generated/Prowl.blockyanim', changed))
            stale = res/'Common/Generated/OldVoiceCopy.blockyanim'
            write(stale, original)
            pool.prune(['Generated'])
            self.assertFalse(stale.exists())
            self.assertTrue(template.exists())
            self.assertTrue((res/'Common/Generated/Prowl.blockyanim').exists())
            with self.assertRaises(ValueError):
                pool.prune(['../'])


if __name__ == '__main__':
    unittest.main()
