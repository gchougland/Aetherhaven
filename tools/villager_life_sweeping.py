"""Two-arm constrained IK, retaining the original broom's vanilla staff grip."""
import copy,math
import numpy as np
from villager_held_ik import HeldIK
from villager_native_items import geometry,grip_tracks
from villager_life_items import flattened
from villager_life_props import quaternion
from villager_life_ik import matrix,interpolate
from villager_prop_preview import geometry_points

def bake(ik,duration):
    r=HeldIK(ik,'R','Halloween_Broomstick');l=HeldIK(ik,'L','Halloween_Broomstick')
    tracks={'R-Attachment':grip_tracks(r.item,duration)}
    mesh,_,scale=geometry(r.item)
    points=np.concatenate([(matrix(n['orientation'])@geometry_points(n['shape']).T).T+np.array([n['position'][k] for k in 'xyz']) for n in flattened(mesh,quaternion,scale=scale) if n['shape']['type']=='quad'])
    rest=ik.fk(tracks,0);ar,ap=rest['R-Attachment']
    grip=ar.T@(rest['R-Hand'][1]-ap)
    leftGrip=grip+np.array([0.,-18.,0.])
    seeds={'R':[-40,0,-30,-65,0,0,0,0],'L':[-30,0,25,-85,0,0,0,0]}
    anchors=copy.deepcopy(seeds)
    for frame in range(0,duration+1,3):
        phase=frame/duration;beat=math.sin(phase*math.pi*2)
        blend=1.
        body={'Chest':{'orientation':[{'time':frame,'delta':ik.quat((5*blend,3*beat*blend,0))}]},'Head':{'orientation':[{'time':frame,'delta':ik.quat((18*blend,6*beat*blend,0))}]}}
        world=ik.fk(body,frame);cr,cp=world['Chest'];palm=cp+cr@np.array([-13.+4*beat,-1.,20.+2*math.cos(phase*math.pi*2)])
        horizontal=np.array([-.9,0,-.35]);horizontal/=np.linalg.norm(horizontal)
        def tilted(angle):
            y=horizontal*math.cos(angle)+np.array([0,1,0])*math.sin(angle)
            x=np.cross(y,[0,0,1]);x/=np.linalg.norm(x)
            return np.column_stack([x,y,np.cross(x,y)])
        lo,hi=.05,1.4
        for _ in range(24):
            a=(lo+hi)/2;rot=tilted(a);minimum=np.min((rot@(points-grip).T)[1])+palm[1]
            if minimum>.7:lo=a
            else:hi=a
        rot=tilted((lo+hi)/2)
        seeds['R'],rp=r.solve(palm,world,anchors['R'],local=grip,rotation=rot,orientation_weight=18)
        if frame==0:
            for _ in range(4):seeds['R'],rp=r.solve(palm,world,seeds['R'],local=grip,rotation=rot,orientation_weight=18)
            anchors['R']=seeds['R'].copy()
        actualR,actualP=rp['R-Attachment'];target=actualP+actualR@leftGrip
        # Left palm follows a real point on the unchanged shaft; wrist remains bounded.
        seeds['L'],lp=l.solve(target,world,anchors['L'],local=[0,0,-1])
        if frame==0:
            for _ in range(3):seeds['L'],lp=l.solve(target,world,seeds['L'],local=[0,0,-1])
            anchors['L']=seeds['L'].copy()
        for side,solver in [('R',r),('L',l)]:
            before={};solver.append(before,seeds[side],frame)
            for name,channels in before.items():
                q=interpolate([{'time':0,'delta':ik.quat((0,0,0))},{'time':1,'delta':channels['orientation'][0]['delta']}],blend)
                tracks.setdefault(name,{'orientation':[]})['orientation'].append({'time':frame,'delta':q,'interpolationType':'smooth'})
        for name in ('Head','Chest'):tracks.setdefault(name,{'orientation':[]})['orientation'].append(dict(body[name]['orientation'][0],interpolationType='smooth'))
    for channels in tracks.values():
        if channels.get('orientation'):channels['orientation'][-1]['delta']=copy.deepcopy(channels['orientation'][0]['delta'])
    return tracks
