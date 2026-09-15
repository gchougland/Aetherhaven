"""Bake constrained arm IK against the actual Player rig into ordinary keyframes.

Uses a damped least-squares shoulder/elbow solver, anatomical elbow hinge limits,
and authored pole preferences. Targets follow the posed torso/head every 0.1 s.
No runtime dependency; no copied animation tracks or reference geometry shipped.
"""
import json
import math
import numpy as np

TARGETS = {
    'Laugh':{'R':('Belly',(-11,5,5)),'L':('Belly',(11,5,5))},
    'Hungry':{'R':('Belly',(-4,2,11)),'L':('Belly',(5,-1,11))},
    'Sleepy':{'R':('Mouth-Attachment',(-4,-1,3))},
    'Read':{'R':('Chest',(-14,-10,21)),'L':('Chest',(14,-10,21))},
    'Craft':{'R':('Chest',(-7,-16,23)),'L':('Chest',(7,-16,23))},
    'Sweep':{'R':('Chest',(-6,-4,20)),'L':('Chest',(3,-16,24))},
    'Inspect':{'R':('Chest',(-5,-2,23))},
    'Tend':{'R':('Chest',(-7,-19,21))},
    'Fidget':{'R':('Chest',(-3,-14,16)),'L':('Chest',(4,-14,16))},
}

def contact_target(name, side, world, phase):
    anchor,offset=TARGETS[name][side]
    offset=np.array(offset,dtype=float)
    if name=='Craft' and side=='R':offset[1]+=1.5*math.sin(phase*math.pi*6)
    if name=='Tend':offset[2]+=1.2*math.sin(phase*math.pi*2)
    rotation,position=world[anchor]
    return position+rotation@offset


def matrix(q):
    x,y,z,w=[q.get(k,1 if k=='w' else 0) for k in 'xyzw']
    return np.array([[1-2*y*y-2*z*z,2*x*y-2*z*w,2*x*z+2*y*w],
                     [2*x*y+2*z*w,1-2*x*x-2*z*z,2*y*z-2*x*w],
                     [2*x*z-2*y*w,2*y*z+2*x*w,1-2*x*x-2*y*y]])


def interpolate(frames,time):
    if time<=frames[0]['time']:return frames[0]['delta']
    for a,b in zip(frames,frames[1:]):
        if a['time']<=time<=b['time']:
            u=(time-a['time'])/(b['time']-a['time']);u=u*u*(3-2*u)
            qa=np.array([a['delta'][k] for k in 'xyzw']);qb=np.array([b['delta'][k] for k in 'xyzw'])
            if qa@qb<0:qb=-qb
            q=qa*(1-u)+qb*u;q/=np.linalg.norm(q)
            return dict(zip('xyzw',q))
    return frames[-1]['delta']


class ArmIK:
    def __init__(self,assets,quat):
        self.assets=assets
        self.rig=json.loads((assets/'Common/Characters/Player.blockymodel').read_text())
        self.quat=quat
        self.nodes={}
        def visit(nodes):
            for n in nodes:self.nodes[n['name']]=n;visit(n.get('children',[]))
        visit(self.rig['nodes'])
        self.report=[]

    def fk(self,tracks,time):
        world={}
        def visit(nodes,parent,origin):
            for n in nodes:
                position=np.array([n['position'][k] for k in 'xyz'],dtype=float)
                positions=tracks.get(n['name'],{}).get('position')
                if positions:
                    delta=positions[-1]['delta']
                    for a,b in zip(positions,positions[1:]):
                        if time<=b['time']:
                            u=max(0,(time-a['time'])/(b['time']-a['time']));u=u*u*(3-2*u)
                            delta={k:a['delta'][k]*(1-u)+b['delta'][k]*u for k in 'xyz'};break
                    position+=np.array([delta[k] for k in 'xyz'])
                pos=origin+parent@position
                rot=matrix(n['orientation'])
                frames=tracks.get(n['name'],{}).get('orientation')
                if frames:rot=rot@matrix(interpolate(frames,time))
                rot=parent@rot
                # Match the server's BlockyModelBoundsParser: children inherit
                # their parent's rotated shape offset as well as its joint pivot.
                pos+=rot@np.array([n.get('shape',{}).get('offset',{}).get(k,0) for k in 'xyz'])
                world[n['name']]=(rot,pos)
                visit(n.get('children',[]),rot,pos)
        visit(self.rig['nodes'],np.eye(3),np.zeros(3))
        return world

    def solve(self,side,target,world,seed,hand):
        shoulder,origin=world[side+'-Shoulder']
        armrest=matrix(self.nodes[side+'-Arm']['orientation'])
        elbow=np.array([self.nodes[side+'-Forearm']['position'][k] for k in 'xyz'],dtype=float)
        wrist=np.array([self.nodes[side+'-Hand']['position'][k] for k in 'xyz'],dtype=float)
        elbow+=np.array([self.nodes[side+'-Arm']['shape']['offset'][k] for k in 'xyz'])
        wrist+=np.array([self.nodes[side+'-Forearm']['shape']['offset'][k] for k in 'xyz'])
        palm=np.array([0,-5,0])
        target=shoulder.T@(target-origin)
        def points(q):
            upper=armrest@matrix(self.quat(q[:3]))
            lower=matrix(self.quat((q[3],q[4],0)))
            e=upper@elbow
            return e+upper@lower@(wrist+hand@palm),e
        q=np.array(seed,dtype=float)
        lower=np.array([-175,-85,-95,-150,-90]);upper=np.array([60,85,95,-2,90])
        q=np.clip(q,lower,upper)
        # Keep the solution near its original elbow direction rather than flipping.
        pole=points(q)[1]
        def residual(q):
            p,e=points(q)
            return np.concatenate([target-p,(pole-e)*.045])
        for _ in range(90):
            error=residual(q)
            if np.linalg.norm(error[:3])<.12:break
            jac=np.column_stack([(residual(q+np.eye(5)[i]*.4)-residual(q-np.eye(5)[i]*.4))/.8 for i in range(5)])
            step=-np.linalg.solve(jac.T@jac+np.eye(5)*.001,jac.T@error)
            q=np.clip(q+np.clip(step,-12,12),lower,upper)
        return q,float(np.linalg.norm(points(q)[0]-target))

    def bake(self,name,tracks,duration,a,b):
        # Palm centers expressed in a local anchor's coordinates. Mirrored hands
        # have staggered stomach contacts; work hands share a stable grip plane.
        targets=TARGETS.get(name)
        if not targets:return tracks
        original=tracks
        result=dict(tracks)
        for side in targets:
            result[side+'-Arm']={'orientation':[]};result[side+'-Forearm']={'orientation':[]}
        worst=0
        previous={}
        for frame in sorted(set(range(0,duration+1,6))|{duration}):
            world=self.fk(original,frame)
            phase=frame/duration
            blend=min(1,max(0,(phase-.12)/.20),max(0,(1-phase)/.18))
            blend=blend*blend*(3-2*blend)
            for side,(anchor,offset) in targets.items():
                if blend==0:
                    for bone in (side+'-Arm',side+'-Forearm'):
                        result[bone]['orientation'].append({'time':frame,'delta':interpolate(original[bone]['orientation'],frame),'interpolationType':'smooth'})
                    continue
                contact=contact_target(name,side,world,phase)
                handframes=original.get(side+'-Hand',{}).get('orientation')
                hand=matrix(interpolate(handframes,frame)) if handframes else np.eye(3)
                # Interpolate preferred shoulder/elbow angles only as a pole hint.
                u=min(1,max(0,(phase-.3)/.32))
                arm=np.array(a[side+'-Arm'])*(1-u)+np.array(b.get(side+'-Arm',a[side+'-Arm']))*u
                fore=np.array(a[side+'-Forearm'])*(1-u)+np.array(b.get(side+'-Forearm',a[side+'-Forearm']))*u
                # Solve the contact pose continuously, then ease into that pose.
                # Blending the target from a hanging arm can cross unreachable
                # regions and suddenly flip the elbow during the first reach.
                q,error=self.solve(side,contact,world,previous.get(side,[*arm,fore[0],fore[1]]),hand)
                previous[side]=q
                if blend>.99:worst=max(worst,error)
                for bone,angles in [(side+'-Arm',q[:3]),(side+'-Forearm',(q[3],q[4],0))]:
                    start=interpolate(original[bone]['orientation'],frame)
                    target=self.quat(angles)
                    mixed=interpolate([{'time':0,'delta':start},{'time':1,'delta':target}], min(1,max(0,(phase-.12)/.20),max(0,(1-phase)/.18)))
                    result[bone]['orientation'].append({'time':frame,'delta':mixed,'interpolationType':'smooth'})
        self.report.append({'gesture':name,'maxContactErrorModelUnits':round(worst,3),'sampleEveryFrames':6})
        return result
