"""Author a quiet looping examination gesture for the Doctor's human rig."""
import json
import math
from pathlib import Path
from villager_blockyanim import complete_channels

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'

def rotation(x, y, z):
    x, y, z = [math.radians(a) / 2 for a in (x, y, z)]
    cx, cy, cz, sx, sy, sz = math.cos(x), math.cos(y), math.cos(z), math.sin(x), math.sin(y), math.sin(z)
    return dict(x=round(sx*cy*cz+cx*sy*sz, 6), y=round(cx*sy*cz-sx*cy*sz, 6),
                z=round(cx*cy*sz+sx*sy*cz, 6), w=round(cx*cy*cz-sx*sy*sz, 6))

poses = {
    'R-Arm': [(-48, -8, -12), (-58, -5, -10), (-50, 2, -14), (-44, -6, -12)],
    'L-Arm': [(-38, 8, 12), (-42, 5, 10), (-48, -2, 14), (-40, 6, 12)],
    'R-Forearm': [(-48, 0, 8), (-38, 0, 4), (-46, 0, 10), (-52, 0, 6)],
    'L-Forearm': [(-62, 0, -8), (-58, 0, -4), (-50, 0, -10), (-60, 0, -6)],
    'Head': [(8, -4, 0), (12, 0, 0), (8, 4, 0), (6, 0, 0)],
    'Chest': [(4, -2, 0), (6, 0, 0), (4, 2, 0), (3, 0, 0)],
}
nodes = {}
for bone, frames in poses.items():
    nodes[bone] = {'position': [], 'orientation': [
        {'time': i*30, 'delta': rotation(*pose), 'interpolationType': 'smooth'}
        for i, pose in enumerate(frames + frames[:1])], 'shapeStretch': [], 'shapeVisible': []}
animation = ROOT / 'Common/Characters/Animations/Aetherhaven/Clinic/Treat.blockyanim'
animation.parent.mkdir(parents=True, exist_ok=True)
animation.write_text(json.dumps(complete_channels({'formatVersion': 1, 'duration': 120, 'holdLastKeyframe': False,
                                 'nodeAnimations': nodes}), indent=2) + '\n')
asset = ROOT / 'Server/Item/Animations/Aetherhaven_Clinic_Care.json'
asset.write_text(json.dumps({'WiggleWeights': {}, 'Animations': {'Treat': {
    'ThirdPerson': 'Characters/Animations/Aetherhaven/Clinic/Treat.blockyanim',
    'Speed': 1, 'Looping': True, 'BlendingDuration': 0.4}}}, indent=2) + '\n')
print('Generated Doctor examination gesture.')
