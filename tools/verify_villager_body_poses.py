"""Audit every frame of the shared body gestures without starting a game server."""
import json,math
import numpy as np
from generate_villager_life_assets import ROOT,RES,quat,write_json
from villager_life_ik import ArmIK,interpolate
from villager_native_items import ASSETS
from villager_pose_clearance import torso_gaps

def verify(names=None):
    ik=ArmIK(ASSETS,quat);report=[];failures=[]
    for path in sorted((RES/'Common/Characters/Animations/Aetherhaven/Life').glob('*.blockyanim')):
        if names and path.stem not in names:continue
        anim=json.loads(path.read_text());tracks=anim['nodeAnimations'];worst=0;location=None;head=100
        previous={};max_step=0
        # Rest-pose shoulder sockets deliberately meet the chest. All other
        # bounds permit only sub-pixel contact tolerance, never deep penetration.
        limits=np.array([-2.9,-2.9,-.2,-.85,-.85,-.2,-.4,-.4,-.2])
        for frame in range(anim['duration']+1):
            world=ik.fk(tracks,frame)
            for side in 'LR':
                bones=[side+'-'+n for n in ('Arm','Forearm','Hand')]
                for bone in bones:
                    keys=tracks.get(bone,{}).get('orientation')
                    if not keys:continue
                    q=interpolate(keys,frame);q=np.array([q[k] for k in 'xyzw'])
                    if bone in previous:
                        max_step=max(max_step,math.degrees(2*math.acos(min(1,abs(q@previous[bone])))))
                    previous[bone]=q
                gaps=torso_gaps(ik,world,{n:world[n] for n in bones})
                head=min(head,float(gaps[2::3].min()))
                violation=limits-gaps;index=int(np.argmax(violation))
                if violation[index]>worst:
                    worst=float(violation[index]);location=dict(frame=frame,arm=bones[index//3],body=('Chest','Belly','Head')[index%3],gap=float(gaps[index]))
        report.append(dict(gesture=path.stem,maxPenetrationBeyondTolerance=worst,worst=location,minHeadClearance=head,maxAngularStep=max_step))
        # Allow a fifth of a model pixel around the conservative soft limits.
        if worst>.2 or max_step>10:failures.append(report[-1])
    write_json(ROOT/'build/villager-life-preview/body-clearance-validation.json',report)
    for f in failures:print(f['gesture'],f['worst'])
    print(f'Audited {len(report)} shared body animations at every frame; {len(failures)} need attention.')
    return failures

if __name__=='__main__':
    assert not verify(), 'Body animation clearance audit failed; see body-clearance-validation.json'
