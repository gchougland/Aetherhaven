"""Support the original opening spellbook at its authored grip and page edge."""
import copy,math
import numpy as np
from villager_life_ik import matrix,interpolate
from villager_held_ik import HeldIK
from villager_native_items import grip_tracks
SUPPORT=np.array([-18.,0.,0.])
def bake(ik,original,duration):
    tracks=copy.deepcopy(original);item='Weapon_Spellbook_Grimoire_Brown'
    tracks['R-Attachment']=grip_tracks(item,duration)
    rest=ik.fk(tracks,0);ar,ap=rest['R-Attachment'];grip=ar.T@(rest['R-Hand'][1]-ap)
    r=HeldIK(ik,'R',item);l=HeldIK(ik,'L',item)
    # Supinate from the authored pronated grip; distribute the turn through the
    # forearm, with bounded wrist flexion. The item attachment itself is unchanged.
    r.lo[4]=-175;r.hi[5]=70
    seeds={'R':[-38,30,7,-72,-163,70,22,22],'L':[-30,0,15,-100,0,0,0,0]}
    tracks['Head']={'orientation':[]}
    for side in 'LR':
        for n in ('Arm','Forearm','Hand'):tracks[side+'-'+n]={'orientation':[]}
    for frame in range(0,duration+1,3):
        # interpolate() already eases; easing twice compressed the forearm turn
        # into the middle of the transition and caused a visible speed spike.
        phase=frame/duration;blend=max(0,min(1,(phase-.1)/.22,(1-phase)/.18))
        world=ik.fk(original,frame);cr,cp=world['Chest']
        seeds['R'],rp=r.solve(cp+cr@np.array([-10.,0.,22.]),world,seeds['R'],local=grip,rotation=cr@matrix(ik.quat((-105,0,180))),orientation_weight=24)
        # Keep the chin out of the book. Pupils scan the page; the head only
        # follows gently, instead of tipping the entire face into the near edge.
        tracks['Head']['orientation'].append({'time':frame,'delta':ik.quat((0,2*math.sin(phase*math.pi*2)*blend,0)),'interpolationType':'smooth'})
        rr,ro=rp['R-Attachment'];target=ro+rr@SUPPORT
        seeds['L'],lp=l.solve(target,world,seeds['L'],local=[0,0,-1])
        for side,solver in [('R',r),('L',l)]:
            before={};solver.append(before,seeds[side],frame)
            for name,channels in before.items():
                q=interpolate([{'time':0,'delta':ik.quat((0,0,0))},{'time':1,'delta':channels['orientation'][0]['delta']}],blend)
                tracks[name]['orientation'].append({'time':frame,'delta':q,'interpolationType':'smooth'})
    for bone,angle in [('Book-Top',75),('Book-Bot',-75)]:
        tracks[bone]={'orientation':[{'time':t,'delta':ik.quat((0,0,a)),'interpolationType':'smooth'} for t,a in [(0,0),(round(duration*.3),angle),(round(duration*.82),angle),(duration,0)]]}
    return tracks
