"""Validate held geometry against both animated palms and the ground plane."""
import json
import itertools
import copy
import numpy as np
from generate_villager_life_assets import ROOT, RES, quat, write_json
from villager_life_ik import ArmIK, matrix, interpolate
from villager_life_props import PROPS, BOOK_PALM_OFFSET, BOOK_HALF_GRIP

def position(frames,time):
    for a,b in zip(frames,frames[1:]):
        if time<=b['time']:
            t=max(0,(time-a['time'])/(b['time']-a['time']));t=t*t*(3-2*t)
            return np.array([a['delta'][k]*(1-t)+b['delta'][k]*t for k in 'xyz'])
    return np.array([frames[-1]['delta'][k] for k in 'xyz'])

def main():
    ik=ArmIK(ROOT.parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets',quat);report=[]
    item_idle=json.loads((ik.assets/'Common/Characters/Animations/Items/Main_Handed/Item/Idle.blockyanim').read_text())['nodeAnimations']
    for gesture,prop in {**PROPS,'ReadLoop':'OpenBook'}.items():
        anim=json.loads((RES/f'Common/Characters/Animations/Aetherhaven/Life/{gesture}.blockyanim').read_text())
        # Simulate the lower-priority held-item idle, which contributed attachment
        # offsets missing from the original isolated-action verification.
        layered=copy.deepcopy(item_idle)
        for bone,channels in anim['nodeAnimations'].items():
            for channel,frames in channels.items():
                if frames:layered.setdefault(bone,{})[channel]=frames
        root=json.loads((RES/f'Common/Items/Aetherhaven/Life/{prop}.blockymodel').read_text())['nodes'][0]['children'][0]
        rest_rotation=matrix(root['orientation']);rest_position=np.array([root['position'][k] for k in 'xyz'])
        track=anim['nodeAnimations']['LifePropRoot'];worst=0;second=0;lowest=1e9;active_highest=-1e9
        for frame in range(anim['duration']+1):
            world=ik.fk(layered,frame);parent,origin=world['R-Attachment']
            rotation=parent@rest_rotation@matrix(interpolate(track['orientation'],frame))
            center=origin+parent@(rest_position+position(track['position'],frame))
            grip=center+rotation@np.array([-BOOK_HALF_GRIP,-BOOK_PALM_OFFSET,0]) if gesture in ('Read','ReadLoop') else center
            worst=max(worst,float(np.linalg.norm(grip-world['R-Hand'][1])))
            active=gesture=='ReadLoop' or .35<=frame/anim['duration']<=.78
            if active and gesture in ('Read','ReadLoop','Sweep'):
                other=center+rotation@np.array([BOOK_HALF_GRIP,-BOOK_PALM_OFFSET,0] if gesture in ('Read','ReadLoop') else [0,-np.sqrt(241),0])
                second=max(second,float(np.linalg.norm(other-world['L-Hand'][1])))
            if gesture=='Sweep':
                bristles=next(n for n in root['children'] if n['name']=='Life_Bristles')
                dims=np.array([bristles['shape']['stretch'][k] for k in 'xyz'])
                corners=np.array(list(itertools.product([-.5,.5],repeat=3)))*dims
                local=np.array([bristles['position'][k] for k in 'xyz'])
                low=float(np.min((rotation@(corners+local).T).T[:,1]+center[1]))
                if low<lowest:lowest=low;lowest_frame=frame
                if active:active_highest=max(active_highest,low)
        assert worst<.35,(gesture,'right grip',worst)
        assert second<.5,(gesture,'left grip',second)
        if gesture=='Sweep':
            assert lowest>=-.5,('Broom through floor',lowest,lowest_frame,anim['duration'])
            assert active_highest<3,('Broom too far from floor',active_highest)
        report.append({'gesture':gesture,'rightGripMaxError':round(worst,3),'leftGripMaxError':round(second,3),
                       **({'lowestBristleY':round(lowest,3),'highestSweepingBristleY':round(active_highest,3)} if gesture=='Sweep' else {})})
    write_json(ROOT/'build/villager-life-preview/prop-validation.json',report)
    print('Verified six held prop motions including continuous reading, two-hand grips and broom ground clearance at every engine frame.')

if __name__=='__main__':main()
