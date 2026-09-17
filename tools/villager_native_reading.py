"""Center the opened native book between both palms using outward elbow IK."""
import numpy as np
from villager_life_ik import matrix
from villager_native_items import grip_tracks
from villager_life_props import quaternion
from villager_two_bone import solve
from villager_book_support import bake as support
SUPPORT=np.array([6.8,-4.,-3.])

def bake(ik,original,duration):
    tracks={'R-Attachment':grip_tracks('Weapon_Spellbook_Grimoire_Brown',duration)}
    world=ik.fk(tracks,0)
    binding=world['R-Hand'][0].T@world['R-Attachment'][0]
    item_rotation=matrix(ik.quat((-100,0,180)))
    # The native spine is x=2.4; the right palm is x=-2.0. A left palm at
    # x=6.8 centers their midpoint on the spine without moving the native grip.
    rotations,_=solve(ik,world,'R',np.array([0.,80.,26.]),item_rotation@binding.T,-np.pi/6)
    for bone,r in zip(('Arm','Forearm','Hand'),rotations):
        tracks['R-'+bone]={'orientation':[{'time':f,'delta':quaternion(r),'interpolationType':'smooth'} for f in (0,duration)]}
    for bone,angle in [('Book-Top',75),('Book-Bot',-75)]:
        tracks[bone]={'orientation':[{'time':t,'delta':ik.quat((0,0,a)),'interpolationType':'smooth'} for t,a in [(0,0),(round(duration*.3),angle),(round(duration*.82),angle),(duration,0)]]}
    return support(ik,tracks,duration,SUPPORT)
