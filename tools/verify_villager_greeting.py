"""Verify the greeting is an arm wave, not a rotating wrist or a full-body overlay."""
import json
import math
import numpy as np
from generate_villager_life_assets import RES, quat
from villager_life_ik import ArmIK, interpolate
from villager_native_items import ASSETS
from verify_villager_body_poses import verify

anim = json.loads((RES / 'Common/Characters/Animations/Aetherhaven/Life/Greet.blockyanim').read_text())
tracks = anim['nodeAnimations']
ik = ArmIK(ASSETS, quat)
start, turn = round(anim['duration'] * .25), round(anim['duration'] * .375)
positions = np.array([ik.fk(tracks, f)['R-Hand'][1] for f in range(start, round(anim['duration'] * .75) + 1)])
palms = np.array([ik.fk(tracks, f)['R-Hand'][0] @ np.array([1., 0, 0])
                 for f in range(start, round(anim['duration'] * .75) + 1)])
assert palms[:, 2].min() > .85, 'Native +X palm must face the partner (+Z), not sideways'
travel = np.ptp(positions[:, 0])
assert travel > 12, f'Wave too small: {travel:.2f} model pixels'

def angle_change(bone):
    a = np.array([interpolate(tracks[bone]['orientation'], start)[k] for k in 'xyzw'])
    b = np.array([interpolate(tracks[bone]['orientation'], turn)[k] for k in 'xyzw'])
    return math.degrees(2 * math.acos(min(1, abs(a @ b))))

assert angle_change('R-Forearm') > 25
assert angle_change('R-Hand') < 6, 'Wave must come from the arm, not wrist rotation'
assert not any(n in tracks for n in ('Pelvis', 'R-Thigh', 'L-Thigh', 'R-Calf', 'L-Calf', 'R-Foot', 'L-Foot'))
for channels in tracks.values():
    keys = channels.get('orientation', [])
    if keys:
        assert abs(keys[0]['delta']['w']) > .9999 and abs(keys[-1]['delta']['w']) > .9999
assert not verify(['Greet'])
print(f'Greeting passes: {travel:.2f} pixels arm sweep, {angle_change("R-Forearm"):.2f} degrees forearm motion, '
      f'{angle_change("R-Hand"):.2f} degrees wrist motion.')
