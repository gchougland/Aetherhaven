"""Geometric two-bone IK with an explicit outward elbow pole."""
import numpy as np
from villager_life_ik import matrix
from villager_life_props import quaternion

def align(a,b):
    a=a/np.linalg.norm(a);b=b/np.linalg.norm(b);v=np.cross(a,b);c=np.clip(a@b,-1,1)
    if c<-.999999:
        axis=np.cross(a,[1.,0,0] if abs(a[0])<.8 else [0,1.,0]);axis/=np.linalg.norm(axis)
        return 2*np.outer(axis,axis)-np.eye(3)
    skew=np.array([[0,-v[2],v[1]],[v[2],0,-v[0]],[-v[1],v[0],0]])
    return np.eye(3)+skew+skew@skew/max(1e-9,1+c)

def solve(ik,world,side,palm,hand_rotation,pole_angle=0,free_hand=False):
    nodes=[ik.nodes[side+'-'+n] for n in ('Arm','Forearm','Hand')]
    vec=lambda d:np.array([d[k] for k in 'xyz'])
    offsets=[vec(n['shape']['offset']) for n in nodes]
    upper=offsets[0]+vec(nodes[1]['position']);lower=offsets[1]+vec(nodes[2]['position'])
    sr,sp=world[side+'-Shoulder'];start=sp+sr@vec(nodes[0]['position'])
    if free_hand:lower=lower+offsets[2];target=palm
    else:target=palm-hand_rotation@offsets[2]
    delta=target-start;distance=np.linalg.norm(delta);a=np.linalg.norm(upper);b=np.linalg.norm(lower)
    if distance>=a+b-.01 or distance<=abs(a-b)+.01:return None
    axis=delta/distance;pole=sr@np.array([1 if side=='L' else -1,0.,0.]);pole-=axis*(pole@axis);pole/=np.linalg.norm(pole)
    pole=pole*np.cos(pole_angle)+np.cross(axis,pole)*np.sin(pole_angle)
    along=(distance*distance+a*a-b*b)/(2*distance);radius=np.sqrt(max(0,a*a-along*along))
    elbow=start+axis*along+pole*radius
    lr=align(hand_rotation@lower,target-elbow)@hand_rotation
    ur=align(lr@upper,elbow-start)@lr
    hr=lr if free_hand else hand_rotation
    rotations=[matrix(nodes[0]['orientation']).T@sr.T@ur,matrix(nodes[1]['orientation']).T@ur.T@lr,matrix(nodes[2]['orientation']).T@lr.T@hr]
    points={side+'-Arm':(ur,start+ur@offsets[0]),side+'-Forearm':(lr,elbow+lr@offsets[1]),side+'-Hand':(hr,palm)}
    return rotations,points
