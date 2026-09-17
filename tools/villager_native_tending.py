"""Hold the native flowers upright using the original item grip."""
import copy, math
import numpy as np
from villager_life_ik import matrix, interpolate
from villager_life_props import quaternion
from villager_two_bone import solve


def bake(ik, original, duration):
    tracks = copy.deepcopy(original)
    rest = ik.fk(tracks, 0)
    binding = rest['R-Hand'][0].T @ rest['R-Attachment'][0]
    for bone in ('Arm', 'Forearm', 'Hand'):
        tracks['R-' + bone] = {'orientation': []}
    for frame in range(0, duration + 1, 3):
        phase = frame / duration
        blend = max(0, min(1, (phase - .1) / .22, (1 - phase) / .18))
        world = ik.fk(original, frame)
        rotation = matrix(ik.quat((0, 0, 15)))
        target = np.array([-25., 70., 20. + math.sin(phase * math.pi * 2)])
        result = solve(ik, world, 'R', target, rotation @ binding.T, 1.)
        assert result is not None, ('unreachable flower palm', frame)
        for bone, r in zip(('Arm', 'Forearm', 'Hand'), result[0]):
            q = interpolate([{'time': 0, 'delta': ik.quat((0, 0, 0))},
                             {'time': 1, 'delta': quaternion(r)}], blend)
            tracks['R-' + bone]['orientation'].append(
                {'time': frame, 'delta': q, 'interpolationType': 'smooth'})
    return tracks
