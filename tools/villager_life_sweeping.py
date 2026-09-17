"""Two-handed sweeping with the approved lower staff grip and outward elbow poles."""
import copy,math
import numpy as np
from villager_life_ik import matrix
from villager_life_props import quaternion
from villager_native_items import grip_tracks,animated_parts
from villager_prop_preview import geometry_points
from villager_pose_clearance import torso_gaps
from villager_two_bone import solve

def bake(ik,duration):
    tracks={'R-Attachment':grip_tracks('Halloween_Broomstick',duration)}
    # User-approved activity-only grip; move the broom four pixels through the
    # fixed-height hands. Native item geometry and other activities are unchanged.
    for key in tracks['R-Attachment']['position']:key['delta']['z']=-4
    rest=ik.fk(tracks,0);hr,_=rest['R-Hand'];ar,_=rest['R-Attachment']
    binding=hr.T@ar
    brush=np.concatenate([(matrix(p['orientation'])@geometry_points(p['shape']).T).T+np.array([p['position'][k] for k in 'xyz']) for p in animated_parts('Halloween_Broomstick') if p['shape']['type']=='quad'])
    poles={'R':.5,'L':-1.};limits=np.array([-2.6,-2.6,1.,.1,.1,1.,.1,.1,1.])
    for frame in sorted({*range(0,duration+1,3),duration}):
        phase=frame/duration*math.pi*2
        body={n:{'orientation':[{'time':frame,'delta':ik.quat(a)}]} for n,a in [('Chest',(0,0,0)),('Head',(0,0,0))]}
        world=ik.fk(body,frame)
        horizontal=np.array([-1.,0,0.]);horizontal/=np.linalg.norm(horizontal)
        y=horizontal*math.cos(math.radians(60))+np.array([0,math.sin(math.radians(60)),0])
        x=np.cross(y,[0,0,1]);x/=np.linalg.norm(x)
        rotation=np.column_stack([x,y,np.cross(x,y)])@matrix(ik.quat((0,60,0)))
        # Height is anchored to the previously approved palm, independently of
        # the new grip. Transparent brush corners extend below the visible tips.
        height=.7-np.min((rotation@(brush-np.array([0.,0.,-1.])).T)[1])
        palm=np.array([3*math.cos(phase*2),height,25.+2*math.sin(phase*2)])
        for side,target,hand,free in [('R',palm,rotation@binding.T,False),('L',palm-rotation[:,1]*18,world['L-Hand'][0],True)]:
            best=None
            for pole in np.unique(np.r_[np.linspace(-math.pi,math.pi,181),poles[side]]):
                result=solve(ik,world,side,target,hand,pole,free)
                if result is None:continue
                rotations,points=result
                wrist=math.degrees(math.acos(np.clip((np.trace(rotations[2])-1)/2,-1,1)))
                cr,cp=world['Chest'];outward=(cr.T@(points[side+'-Arm'][1]-cp))[0]*(1 if side=='L' else -1)
                gap=torso_gaps(ik,world,points)
                score=np.maximum(0,limits-gap).sum()*100+max(0,wrist-33)*20+max(0,16-outward)*15+(pole-(.5 if side=='R' else -1.))**2*.25
                if best is None or score<best[0]:best=(score,pole,rotations)
            assert best is not None,('unreachable broom palm',frame,side)
            _,poles[side],rotations=best
            for bone,r in zip(('Arm','Forearm','Hand'),rotations):
                tracks.setdefault(side+'-'+bone,{'orientation':[]})['orientation'].append({'time':frame,'delta':quaternion(r),'interpolationType':'smooth'})
        for name in body:tracks.setdefault(name,{'orientation':[]})['orientation'].append(dict(body[name]['orientation'][0],interpolationType='smooth'))
    for channels in tracks.values():
        if channels.get('orientation'):channels['orientation'][-1]['delta']=copy.deepcopy(channels['orientation'][0]['delta'])
    return tracks
