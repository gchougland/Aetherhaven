"""Check exported motion, including frames between the IK bake samples."""
import json
import math
import numpy as np
from generate_villager_life_assets import ROOT, COMMON, quat, write_json
from villager_life_ik import ArmIK, TARGETS, contact_target
from villager_life_props import BOOK_HALF_GRIP

def main():
    ik=ArmIK(ROOT.parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets',quat)
    report=[]
    neutral=ik.fk({},0)
    for path in sorted((COMMON/'Characters/Animations/Aetherhaven/Life').glob('*.blockyanim')):
        anim=json.loads(path.read_text()); tracks=anim['nodeAnimations']; duration=anim['duration']
        peak=0; contact_error=0
        for bone in tracks.values():
            frames=bone['orientation']
            for a,b in zip(frames,frames[1:]):
                qa=np.array([a['delta'][k] for k in 'xyzw']); qb=np.array([b['delta'][k] for k in 'xyzw'])
                angle=math.degrees(2*math.acos(min(1,abs(qa@qb))))
                peak=max(peak,angle*60/(b['time']-a['time']))
        assert peak<300,(path.stem,'sudden joint transition',peak)
        for frame in range(duration+1):
            world=ik.fk(tracks,frame)
            for foot in ('L-Foot','R-Foot'):
                assert np.linalg.norm(world[foot][1]-neutral[foot][1])<.001,(path.stem,'foot sliding')
            if path.stem in TARGETS and .35<=frame/duration<=.78:
                if path.stem=='Read':
                    # Supporting palm follows the opposite grip on the rigid book.
                    target=world['R-Hand'][1]+world['R-Hand'][0]@np.array([2*BOOK_HALF_GRIP,0,0])
                    contact_error=max(contact_error,float(np.linalg.norm(world['L-Hand'][1]-target)))
                    continue
                for side in TARGETS[path.stem]:
                    target=contact_target(path.stem,side,world,frame/duration)
                    contact_error=max(contact_error,float(np.linalg.norm(world[side+'-Hand'][1]-target)))
        assert contact_error<.5,(path.stem,'hand contact drift',contact_error)
        report.append({'gesture':path.stem,'seconds':duration/60,
                       'maxAdjacentKeyDegreesPerSecond':round(peak,2),
                       'maxContactErrorModelUnits':round(contact_error,3)})
    write_json(ROOT/'build/villager-life-preview/motion-validation.json',report)
    print(f'Validated {len(report)} motions at every engine frame: stable feet, continuous joints, hand contacts within 0.5 model units.')

if __name__=='__main__':main()
