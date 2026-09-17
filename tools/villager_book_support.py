"""Bake outward approaches, exact centered contacts, and smooth book transitions."""
import copy,math
import numpy as np
from villager_life_props import quaternion
from villager_two_bone import solve
from villager_life_ik import interpolate
from villager_pose_clearance import torso_gaps

def bake(ik,tracks,duration,contact):
    from villager_body_safety import correct,smooth_arms,AUDIT_LIMITS
    tracks=copy.deepcopy(tracks);neutral=ik.fk({},0)
    world=ik.fk(tracks,duration*.4);r,p=world['R-Attachment'];support=p+r@contact
    way,_=solve(ik,neutral,'R',np.array([-20.,68.,22.]),neutral['R-Hand'][0],0,True)
    for bone,rotation in zip(('Arm','Forearm','Hand'),way):
        name='R-'+bone;held=tracks[name]['orientation'][0]['delta'];keys=[]
        path=[{'time':0,'delta':ik.quat((0,0,0))},{'time':.5,'delta':quaternion(rotation)},{'time':1,'delta':held}]
        for frame in range(0,duration+1,3):
            phase=frame/duration;blend=max(0,min(1,(phase-.1)/.22,(1-phase)/.18))
            keys.append({'time':frame,'delta':interpolate(path,blend),'interpolationType':'smooth'})
        tracks[name]['orientation']=keys
    def bad_frames():
        bad=[]
        for frame in range(1,duration):
            if .32<=frame/duration<=.82:continue
            w=ik.fk(tracks,frame)
            if np.max(AUDIT_LIMITS-torso_gaps(ik,w,{'R-'+n:w['R-'+n] for n in ('Arm','Forearm','Hand')}))>.05:bad.append(frame)
        return bad
    def repair(frames):
        corrected=correct(ik,tracks,duration,'ReadingTransition',frames=frames)
        for bone in ('Arm','Forearm','Hand'):
            name='R-'+bone;keys={k['time']:k for k in tracks[name]['orientation']}
            keys.update({k['time']:k for k in corrected[name]['orientation']})
            tracks[name]['orientation']=[keys[t] for t in sorted(keys)]
    def smooth(side,radius,passes):
        result=smooth_arms(tracks,duration,radius,passes)
        for bone in ('Arm','Forearm','Hand'):tracks[side+'-'+bone]=result[side+'-'+bone]
    for _ in range(3):
        bad=bad_frames()
        if not bad:break
        repair(bad)
    smooth('R',5,2)
    for _ in range(3):
        bad=bad_frames()
        if not bad:break
        repair(bad);smooth('R',3,1)
    repair(bad_frames())
    smooth('R',2,1)
    repair(bad_frames())
    # Support the opposite page with an explicit outward pole. Only the
    # approach follows an arc; the contact remains exact while reading.
    hr,hp=neutral['L-Hand'];limits=np.array([-2.9,-2.9,.5,-.7,-.7,.5,-.3,-.3,.5])
    for bone in ('Arm','Forearm','Hand'):tracks['L-'+bone]={'orientation':[]}
    for frame in range(duration+1):
        phase=frame/duration;blend=max(0,min(1,(phase-.1)/.22,(1-phase)/.18));blend=blend*blend*(3-2*blend)
        target=hp*(1-blend)+support*blend+np.array([12.,4.,18.])*math.sin(math.pi*blend)
        rotations=[np.eye(3)]*3
        if blend>.001:
            best=None
            for pole in np.linspace(-.3,1.2,91):
                candidate=solve(ik,neutral,'L',target,hr,pole,True)
                if candidate is None:continue
                score=np.maximum(0,limits-torso_gaps(ik,neutral,candidate[1])).sum()*1000+(pole-(.6*(1-blend)-np.pi/15*blend))**2
                if best is None or score<best[0]:best=(score,candidate[0])
            assert best is not None,('unreachable supporting palm',frame)
            rotations=best[1]
        for bone,rotation in zip(('Arm','Forearm','Hand'),rotations):
            q=interpolate([{'time':0,'delta':ik.quat((0,0,0))},{'time':1,'delta':quaternion(rotation)}],min(1,blend/.03))
            tracks['L-'+bone]['orientation'].append({'time':frame,'delta':q,'interpolationType':'smooth'})
    smooth('L',5,2)
    # Soften the last corrective forearm/wrist keys without moving the elbow.
    softened=smooth_arms(tracks,duration,2,1)
    for bone in ('Forearm','Hand'):tracks['R-'+bone]=softened['R-'+bone]
    return tracks
