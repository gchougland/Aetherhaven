"""Open-palm arm wave: lateral wrist travel comes from shoulder/elbow motion.

Pose reference: https://www.bulgarianpod101.com/blog/2019/08/16/bulgarian-body-gestures/
Raised outward elbow, hand above shoulder, palm facing the person being greeted.
The original motion is shared by every Greet voice/race action.
"""
import math
import numpy as np
from villager_life_ik import matrix, interpolate
from villager_life_props import quaternion
from villager_two_bone import solve


def bake(ik, duration):
    tracks = {name: {'orientation': []} for name in ('R-Arm', 'R-Forearm', 'R-Hand', 'Head', 'Chest')}
    world = ik.fk({}, 0)
    neutral = {'x': 0, 'y': 0, 'z': 0, 'w': 1}
    for frame in range(0, duration + 1, 2):
        phase = frame / duration
        # Two relaxed sweeps. Wrist stays aligned with forearm; the whole arm waves.
        wave = min(1., max(0., (phase - .25) / .5))
        arc = .5 - .5 * math.cos(wave * math.pi * 4)
        palm = np.array([-30 - 14 * arc, 113 - 7 * arc, 5.])
        # The native right palm is local +X, not +Z. Supinate the forearm
        # so that palm points toward the conversation partner (+Z).
        hand = matrix(ik.quat((0, 90, 180)))
        solution = solve(ik, world, 'R', palm, hand, 0, free_hand=True)
        assert solution is not None
        reach = min(1., phase / .25, (1 - phase) / .25)
        reach = max(0., reach)
        reach = reach * reach * (3 - 2 * reach)
        for name, rotation in zip(('R-Arm', 'R-Forearm', 'R-Hand'), solution[0]):
            q = interpolate([{'time': 0, 'delta': neutral}, {'time': 1, 'delta': quaternion(rotation)}], reach)
            tracks[name]['orientation'].append({'time': frame, 'delta': q, 'interpolationType': 'smooth'})
        for name, angles in [('Head', (0, -5 * reach, -2 * reach)), ('Chest', (0, 0, 0))]:
            tracks[name]['orientation'].append({'time': frame, 'delta': ik.quat(angles), 'interpolationType': 'smooth'})
    from villager_body_safety import correct, smooth_arms
    return smooth_arms(correct(ik, tracks, duration, 'Greet'), duration, radius=3, passes=2)


if __name__ == '__main__':
    from generate_villager_life_assets import RES, quat, write_json
    from villager_life_ik import ArmIK
    from villager_native_items import ASSETS
    from villager_life_faces import DURATIONS
    duration = round(DURATIONS['Greet'] * 60)
    write_json(RES / 'Common/Characters/Animations/Aetherhaven/Life/Greet.blockyanim',
        {'formatVersion': 1, 'duration': duration, 'holdLastKeyframe': False,
         'nodeAnimations': bake(ArmIK(ASSETS, quat), duration)})
