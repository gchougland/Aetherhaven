"""Original-item activity bindings and quaternion helpers."""
import copy
import json
import numpy as np
from PIL import Image
from villager_life_ik import matrix, interpolate

PROPS={'Read':'OpenBook','Sweep':'Broom','Craft':'Mallet','Tend':'Plant'}
BOOK_PALM_OFFSET = 5.0
BOOK_HALF_GRIP = 14.0
BROOM_POINTS = None

def quaternion(m):
    # Stable conversion using the largest diagonal component.
    candidates=np.array([1+m[0,0]-m[1,1]-m[2,2],1-m[0,0]+m[1,1]-m[2,2],
                         1-m[0,0]-m[1,1]+m[2,2],1+np.trace(m)])
    i=int(np.argmax(candidates));q=np.zeros(4);q[i]=np.sqrt(max(0,candidates[i]))/2
    d=4*q[i]
    if i==3:q[:3]=[(m[2,1]-m[1,2])/d,(m[0,2]-m[2,0])/d,(m[1,0]-m[0,1])/d]
    elif i==0:q[1:]=[(m[0,1]+m[1,0])/d,(m[0,2]+m[2,0])/d,(m[2,1]-m[1,2])/d]
    elif i==1:q[[0,2,3]]=[(m[0,1]+m[1,0])/d,(m[1,2]+m[2,1])/d,(m[0,2]-m[2,0])/d]
    else:q[[0,1,3]]=[(m[0,2]+m[2,0])/d,(m[1,2]+m[2,1])/d,(m[1,0]-m[0,1])/d]
    q/=np.linalg.norm(q)
    if q[3]<0:q=-q
    return dict(zip('xyzw',[round(float(v),7) for v in q]))

def bake(gesture,tracks,duration,ik):
    from villager_native_items import ITEMS,grip_tracks
    if gesture=='Sweep':
        from villager_life_sweeping import bake as sweep
        return sweep(ik,duration)
    if gesture not in ITEMS:return tracks
    tracks=copy.deepcopy(tracks)
    tracks['R-Attachment']=grip_tracks(ITEMS[gesture],duration)
    if gesture=='Read':
        from villager_native_reading import bake as read
        return read(ik,tracks,duration)
    if gesture=='Tend':
        from villager_native_tending import bake as tend
        return tend(ik,tracks,duration)
    return tracks


def generate(res,ik,quat,write):
    from villager_life_items import generate as native_items
    native_items(res,ik,quat,write)
