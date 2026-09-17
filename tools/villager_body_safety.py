"""Small collision-aware IK corrections around the authored acting poses.

Targets are the original palms and hand orientations, so gestures retain their
intent. Held book contacts get much stronger constraints than free gestures.
Only arms change; faces, attachment grips, legs and timing remain authored.
"""
import copy
import numpy as np
from villager_life_ik import matrix,interpolate
from villager_life_props import quaternion
from villager_pose_clearance import torso_gaps

# The rest rig's shoulder socket overlaps the chest slightly. Preserve that
# connection, but never allow a forearm/hand inside the torso or any arm in head.
LIMITS=np.array([-2.6,-2.6,.5, .15,.15,.5, .15,.15,.5])
AUDIT_LIMITS=np.array([-2.9,-2.9,-.2,-.85,-.85,-.2,-.4,-.4,-.2])

def euler(r):
    return np.degrees([np.arctan2(-r[1,2],r[2,2]),np.arcsin(np.clip(r[0,2],-1,1)),np.arctan2(-r[0,1],r[0,0])])

def correct(ik,tracks,duration,gesture,frames=None):
    if gesture in ('Mix','Sweep','Read') and frames is None:return tracks
    if frames is None and gesture in ('Question','Sleepy','Stretch'):
        tracks=copy.deepcopy(tracks)
        tracks['Head']={'orientation':[{'time':f,'delta':ik.quat((0,0,0))} for f in (0,duration)]}
        if gesture in ('Question','Stretch'):return tracks
    result=copy.deepcopy(tracks)
    clearance_limits=AUDIT_LIMITS+.1 if gesture=='ReadingTransition' else LIMITS
    for side in ('R' if gesture=='Read' else 'LR'):
        names=[side+'-'+n for n in ('Arm','Forearm','Hand')]
        if not any(n in tracks for n in names) and 'Chest' not in tracks:continue
        output={n:[] for n in names};previous=np.zeros(9)
        for frame in (frames if frames is not None else sorted({*range(0,duration+1,6),duration})):
            world=ik.fk(tracks,frame)
            original=[matrix(interpolate(tracks[n]['orientation'],frame)) if tracks.get(n,{}).get('orientation') else np.eye(3) for n in names]
            target_r,target_p=world[names[-1]]
            if gesture=='Sleepy' and side=='R':
                reach=min(1,frame/duration/.3,(1-frame/duration)/.2)
                target_p=target_p+np.array([0.,0.,8.*max(0,reach)])
            def pose(q):
                r,p=world[side+'-Shoulder'];points={}
                for i,n in enumerate(names):
                    node=ik.nodes[n]
                    p=p+r@np.array([node['position'][k] for k in 'xyz'])
                    r=r@matrix(node['orientation'])@original[i]@matrix(ik.quat(q[i*3:i*3+3]))
                    p=p+r@np.array([node['shape']['offset'][k] for k in 'xyz'])
                    points[n]=(r,p)
                return points
            held=gesture=='ReadLoop' or (gesture in ('Read','Tend') and .32<=frame/duration<=.82)
            def residual(q):
                points=pose(q);r,p=points[names[-1]]
                orientation=(r-target_r).ravel()*(100 if held else 2)
                return np.concatenate([(p-target_p)*(100 if held else 4 if gesture=='Sleepy' else .65),
                    orientation,
                    np.maximum(0,clearance_limits-torso_gaps(ik,world,points))*(65 if held else 30),
                    q*.025])
            q=np.zeros(9)
            # Exact neutral endpoints are part of the animation contract.
            if (gesture=='ReadLoop' or frame not in (0,duration)) and ( np.max(clearance_limits-torso_gaps(ik,world,pose(q)))>.02):
                q=previous.copy() if frames is None else np.zeros(9);damping=.002
                for _ in range(85):
                    error=residual(q)
                    jac=np.column_stack([(residual(q+np.eye(9)[i]*.3)-residual(q-np.eye(9)[i]*.3))/.6 for i in range(9)])
                    step=-np.linalg.solve(jac.T@jac+np.eye(9)*damping,jac.T@error)
                    step*=min(1,8/max(.0001,max(abs(step))))
                    if np.linalg.norm(step)<.01:break
                    accepted=False
                    for fraction in (1,.5,.25,.125):
                        bound=85
                        candidate=np.clip(q+step*fraction,-bound,bound)
                        candidate[6:]=np.clip(candidate[6:],-8,8)
                        if np.linalg.norm(residual(candidate))<np.linalg.norm(error):
                            q=candidate;damping=max(.0001,damping*.7);accepted=True;break
                    if not accepted:damping=min(100,damping*5)
            previous=q
            for i,n in enumerate(names):
                output[n].append({'time':frame,'delta':quaternion(original[i]@matrix(ik.quat(q[i*3:i*3+3]))),'interpolationType':'smooth'})
        for n in names:
            result.setdefault(n,{})['orientation']=output[n]
            if gesture=='ReadLoop':output[n][-1]['delta']=copy.deepcopy(output[n][0]['delta'])
    if gesture in ('Read','ReadLoop','Tend') and frames is None:
        # Preserve the item-bearing palm's authored world orientation exactly.
        # Even a tiny solver compromise can tip a page downward.
        keys=[];rest=matrix(ik.nodes['R-Hand']['orientation'])
        for frame in sorted({*range(0,duration+1,3),duration}):
            before=ik.fk(tracks,frame);after=ik.fk(result,frame)
            wanted=before['R-Hand'][0] if gesture not in ('Read','Tend') or .32<=frame/duration<=.82 else after['R-Hand'][0]
            local=rest.T@after['R-Forearm'][0].T@wanted
            keys.append({'time':frame,'delta':quaternion(local),'interpolationType':'smooth'})
        result['R-Hand']['orientation']=keys
    if frames is None and gesture=='Read':
        from villager_book_support import bake
        from villager_native_reading import SUPPORT
        return bake(ik,result,duration,SUPPORT)
    if frames is None and gesture not in ('Mix','Sweep','ReadLoop'):
        result=refine(ik,result,duration,gesture)
    return result

def refine(ik,tracks,duration,gesture):
    """Add corrective keys only where interpolation cuts through the body."""
    for _ in range(3):
        bad=[]
        for frame in range(1,duration):
            if gesture in ('Read','Tend') and .32<=frame/duration<=.82:continue
            world=ik.fk(tracks,frame)
            if any(np.max(AUDIT_LIMITS-torso_gaps(ik,world,{side+'-'+n:world[side+'-'+n] for n in ('Arm','Forearm','Hand')}))>.02 for side in 'LR'):
                bad.append(frame)
        if not bad:break
        adjusted=correct(ik,tracks,duration,gesture,frames=bad)
        for side in 'LR':
            for bone in ('Arm','Forearm','Hand'):
                name=side+'-'+bone
                if name not in adjusted:continue
                keys={k['time']:k for k in tracks.get(name,{}).get('orientation',[])}
                keys.update({k['time']:k for k in adjusted[name]['orientation']})
                tracks.setdefault(name,{})['orientation']=[keys[t] for t in sorted(keys)]
    result=recover(ik,tracks,duration,gesture)
    return smooth_arms(result,duration) if gesture=='Sleepy' else result


def smooth_arms(tracks,duration,radius=8,passes=3):
    """Blend corrective pole changes through the neighboring authored frames."""
    tracks=copy.deepcopy(tracks)
    for name,channels in tracks.items():
        if not name.endswith(('Arm','Forearm','Hand')) or not channels.get('orientation'):continue
        values=np.array([[interpolate(channels['orientation'],f)[k] for k in 'xyzw'] for f in range(duration+1)])
        for f in range(1,len(values)):
            if values[f]@values[f-1]<0:values[f]*=-1
        for _ in range(passes):
            padded=np.pad(values,((radius,radius),(0,0)),mode='edge')
            values=np.stack([padded[i:i+radius*2+1].mean(axis=0) for i in range(len(values))])
            values/=np.linalg.norm(values,axis=1)[:,None]
        keys=[]
        for f,q in enumerate(values):
            delta=dict(zip('xyzw',q.tolist()))
            blend=min(1,f/12,(duration-f)/12)
            delta=interpolate([{'time':0,'delta':{'x':0,'y':0,'z':0,'w':1}},{'time':1,'delta':delta}],blend)
            keys.append({'time':f,'delta':delta,'interpolationType':'smooth'})
        channels['orientation']=keys
    return tracks


def recover(ik,tracks,duration,gesture):
    """Escape local IK minima using the outward-elbow geometric solution."""
    from villager_two_bone import solve
    result=copy.deepcopy(tracks)
    for side in 'LR':
        names=[side+'-'+n for n in ('Arm','Forearm','Hand')]
        keys={n:[] for n in names}
        for frame in range(duration+1):
            world=ik.fk(tracks,frame)
            original=[matrix(interpolate(tracks[n]['orientation'],frame)) if tracks.get(n,{}).get('orientation') else np.eye(3) for n in names]
            rotations=original
            gaps=torso_gaps(ik,world,{n:world[n] for n in names})
            if frame not in (0,duration) and np.max(AUDIT_LIMITS-gaps)>.001:
                hand,palm=world[names[-1]];best=None
                held=gesture in ('Read','ReadLoop','Tend')
                sign=-1 if side=='R' else 1
                for offset in ([0,0,0],[0,0,2],[0,0,4],[2*sign,0,4],[0,4,0],[4*sign,-4,4],[8*sign,-6,4],[12*sign,-8,0]):
                    for pole in np.linspace(-np.pi,np.pi,61):
                        candidate=solve(ik,world,side,palm+offset,hand,pole,not held)
                        if candidate is None:continue
                        rr,points=candidate
                        collision=np.maximum(0,LIMITS-torso_gaps(ik,world,points)).sum()
                        change=sum(np.linalg.norm(a-b)**2 for a,b in zip(rr,original))
                        score=collision*1000+change+np.linalg.norm(offset)*2
                        if best is None or score<best[0]:best=(score,rr)
                    if best is not None and best[0]<20:break
                if best is not None:rotations=best[1]
            for name,r in zip(names,rotations):
                keys[name].append({'time':frame,'delta':quaternion(r),'interpolationType':'smooth'})
        for name in names:result.setdefault(name,{})['orientation']=keys[name]
    return result
