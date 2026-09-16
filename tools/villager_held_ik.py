"""Eight-DOF constrained arm IK with fixed native attachment transforms."""
import numpy as np
from villager_life_ik import matrix
from villager_life_props import quaternion
from villager_native_items import grips,grip_tracks
class HeldIK:
    def __init__(self,ik,side,item_id):
        self.ik=ik;self.side=side;self.item=item_id
        self.names=[side+'-'+name for name in ('Arm','Forearm','Hand','Attachment')]
        self.pos,self.rot=grips(item_id,side)
        self.lo=np.array([-175,-85,-95,-150,-85,-22,-22,-22.])
        self.hi=np.array([65,85,95,-2,85,22,22,22.])
    def pose(self,q,world):
        r,p=world[self.side+'-Shoulder'];p=p.copy();points={}
        rotations=(q[:3],(q[3],q[4],0),q[5:8],None)
        for name,angles in zip(self.names,rotations):
            n=self.ik.nodes[name]
            local=np.array([n['position'][k] for k in 'xyz'])
            if angles is None:local=local+self.pos
            p=p+r@local
            r=r@matrix(n['orientation'])@(self.rot if angles is None else matrix(self.ik.quat(angles)))
            p=p+r@np.array([n['shape']['offset'][k] for k in 'xyz'])
            points[name]=(r.copy(),p.copy())
        return points
    def solve(self,target,world,seed,local=(0,0,0),rotation=None,orientation_weight=8,clearance=None,rotation_axis=None):
        seed=np.array(seed,dtype=float);q=np.clip(seed,self.lo,self.hi);local=np.array(local)
        def error(q):
            points=self.pose(q,world);r,p=points[self.names[-1]]
            values=[(p+r@local-target),q[5:8]*.018,(q[:5]-seed[:5])*.004]
            if rotation is not None:
                difference=r-rotation
                if rotation_axis is not None:difference=difference[:,rotation_axis]
                values.append(difference.ravel()*orientation_weight)
            if clearance is not None:values.append(clearance(points))
            return np.concatenate(values)
        damping=.0004
        for _ in range(160 if clearance is not None else 65):
            e=error(q)
            j=np.column_stack([(error(q+np.eye(8)[i]*.25)-error(q-np.eye(8)[i]*.25))/.5 for i in range(8)])
            step=-np.linalg.solve(j.T@j+np.eye(8)*damping,j.T@e)
            if np.linalg.norm(step)<.002:break
            if clearance is not None:
                step*=min(1,12/max(abs(step)))
                accepted=False
                for fraction in (1,.5,.25,.125,.0625):
                    candidate=np.clip(q+step*fraction,self.lo,self.hi)
                    if np.linalg.norm(error(candidate))<np.linalg.norm(e):
                        q=candidate;damping=max(.0001,damping*.6);accepted=True;break
                if not accepted:damping=min(100,damping*5)
                continue
            candidate=np.clip(q+np.clip(step,-8,8),self.lo,self.hi)
            if np.linalg.norm(error(candidate))>np.linalg.norm(e):candidate=np.clip(q+np.clip(step,-8,8)*.25,self.lo,self.hi)
            q=candidate
        return q,self.pose(q,world)
    def append(self,tracks,q,frame):
        for name,angles in zip(self.names[:3],(q[:3],(q[3],q[4],0),q[5:8])):
            tracks.setdefault(name,{'orientation':[]})['orientation'].append({'time':frame,'delta':self.ik.quat(angles),'interpolationType':'smooth'})
