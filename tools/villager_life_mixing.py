"""Constrained IK around unmodified spoon and native salad grips."""
import copy,math
import numpy as np
from villager_life_ik import matrix
from villager_held_ik import HeldIK
from villager_native_items import grip_tracks
SPOON_TIP=np.array([0.,-.48,12.6])

from villager_pose_clearance import torso_gaps

def bake(ik,duration):
    tracks={};solvers={s:HeldIK(ik,s,item) for s,item in [('R','Food_Salad_Caesar'),('L','Aetherhaven_Life_Prop_Spoon')]}
    # Both elbows flex in the normal negative direction. Shoulder rotation
    # supplies the overhand grip; reversing the elbow folds it through the head.
    solvers['R'].lo[:3]=-180;solvers['R'].hi[:3]=180
    solvers['R'].lo[5:]=[-40,-12,-12];solvers['R'].hi[5:]=[15,12,12]
    solvers['L'].lo[:3]=[-220,90,-120];solvers['L'].hi[:3]=[-100,270,0]
    solvers['L'].lo[4]=-180;solvers['L'].hi[4]=180
    solvers['L'].lo[5:]=-18;solvers['L'].hi[5:]=18
    q={'R':[56,67,-96,-60,-69,-35,8,-10],'L':[-190,245,-102,-95,-116,-7,-11,18]}
    for side,solver in solvers.items():tracks[side+'-Attachment']=grip_tracks(solver.item,duration,side)
    for frame in sorted({*range(0,duration+1,12),duration}):
        angle=frame/duration*math.pi*4
        work={n:{'orientation':[{'time':frame,'delta':ik.quat(a)}]} for n,a in [('Head',(4,2*math.sin(angle/2),0)),('Chest',(0,0,0))]}
        world=ik.fk(work,frame);cr,cp=world['Chest']
        if frame==0:
            for _ in range(2):
                q['R'],right=solvers['R'].solve(cp+cr@np.array([-5.,-20.,32.]),world,q['R'],rotation=cr,rotation_axis=1,orientation_weight=22,
                    clearance=lambda p:np.maximum(0,.8-torso_gaps(ik,world,p))*20)
        br,bp=right['R-Attachment']
        target=bp+br@np.array([3.+2.*math.cos(angle),17.,2.*math.sin(angle)])
        for _ in range(2 if frame==0 else 1):
            q['L'],left=solvers['L'].solve(target,world,q['L'],local=SPOON_TIP,rotation=cr@matrix(ik.quat((65,0,0))),rotation_axis=2,orientation_weight=2,
                clearance=lambda p:np.maximum(0,1.5-torso_gaps(ik,world,p))*20)
        for side,solver in solvers.items():solver.append(tracks,q[side],frame)
        for name in ('Head','Chest'):tracks.setdefault(name,{'orientation':[]})['orientation'].append(dict(work[name]['orientation'][0],interpolationType='smooth'))
    for name,channels in tracks.items():
        if channels.get('orientation'):channels['orientation'][-1]['delta']=copy.deepcopy(channels['orientation'][0]['delta'])
    return tracks
