"""Shared oriented-box arm clearance against the actual mirrored Player rig."""
import numpy as np

def torso_gaps(ik,world,points):
    """Separating gaps for actual arm boxes against torso and head boxes."""
    gaps=[]
    for name,(r,p) in points.items():
        if not name.endswith(('-Arm','-Forearm','-Hand')):continue
        shape=ik.nodes[name]['shape'];half=np.array([shape['settings']['size'][k]*abs(shape.get('stretch',{}).get(k,1))/2 for k in 'xyz'])
        for body in ('Chest','Belly','Head'):
            center=p;extent=half.copy()
            if name.endswith('-Arm') and body!='Head':
                # Exclude the shoulder socket only against the torso. The whole
                # upper arm must clear the head, including its proximal end.
                center=p+r@np.array([0.,-6.,0.]);extent[1]-=6
            br,bp=world[body];bs=ik.nodes[body]['shape']
            bh=np.array([bs['settings']['size'][k]*abs(bs.get('stretch',{}).get(k,1))/2 for k in 'xyz'])
            axes=np.concatenate([br.T,r.T,np.cross(br.T[:,None,:],r.T[None,:,:]).reshape(9,3)])
            lengths=np.linalg.norm(axes,axis=1);axes=axes[lengths>1e-7]/lengths[lengths>1e-7,None]
            gaps.append(float(np.max(abs(axes@(center-bp))-abs(axes@br)@bh-abs(axes@r)@extent)))
    return np.array(gaps)
