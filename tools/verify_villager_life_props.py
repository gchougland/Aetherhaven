"""Validate native-item grips and supporting-hand contacts against original meshes."""
import json,math
import numpy as np
from generate_villager_life_assets import ROOT,RES,quat,write_json
from villager_life_ik import ArmIK,matrix,interpolate
from villager_native_items import ASSETS,ITEMS,grips,animated_parts
from villager_native_reading import SUPPORT
from villager_prop_preview import geometry_points

def head_clearance(ik,world,parts):
    """Conservative separation bound using each native box/quad's SAT axes."""
    r,p=world['R-Attachment'];hr,hp=world['Head']
    head_size=np.array([ik.nodes['Head']['shape']['settings']['size'][k] for k in 'xyz'])/2
    minimum=float('inf')
    for part in parts:
        if not part['shape'].get('visible',True):continue
        pr=matrix(part['orientation']);pp=np.array([part['position'][k] for k in 'xyz'])
        pts=(hr.T@((r@((pr@geometry_points(part['shape']).T).T+pp).T).T+p-hp).T).T
        axes=hr.T@r@pr
        candidates=[*np.eye(3),*axes.T,*[np.cross(a,b) for a in np.eye(3) for b in axes.T]]
        gap=0
        for axis in candidates:
            length=np.linalg.norm(axis)
            if length<1e-6:continue
            axis=axis/length;projection=pts@axis;extent=head_size@abs(axis)
            gap=max(gap,float(projection.min()-extent),float(-extent-projection.max()))
        minimum=min(minimum,gap)
    return minimum

def main():
    ik=ArmIK(ASSETS,quat);report=[]
    for gesture,item in ITEMS.items():
        a=json.loads((RES/f'Common/Characters/Animations/Aetherhaven/Life/{gesture}.blockyanim').read_text());tracks=a['nodeAnimations']
        expectedP,expectedR=grips(item)
        for f in tracks['R-Attachment']['position']:assert np.allclose([f['delta'][k] for k in 'xyz'],expectedP),('changed original grip',gesture)
        for f in tracks['R-Attachment']['orientation']:assert np.allclose(matrix(f['delta']),expectedR,atol=1e-6),('changed original grip rotation',gesture)
        if gesture not in ('Sweep','Read','ReadLoop'):continue
        parts=animated_parts(item)
        brush=np.concatenate([(matrix(n['orientation'])@geometry_points(n['shape']).T).T+np.array([n['position'][k] for k in 'xyz']) for n in parts if n['shape']['type']=='quad']) if gesture=='Sweep' else None
        rest=ik.fk({'R-Attachment':tracks['R-Attachment']},0);rr,rp=rest['R-Attachment'];grip=rr.T@(rest['R-Hand'][1]-rp)
        second=0;low=100;high=-100;wrist=0
        for frame in range(a['duration']+1):
            w=ik.fk(tracks,frame);r,p=w['R-Attachment']
            active=gesture in ('Sweep','ReadLoop') or .35<=frame/a['duration']<=.78
            if active:
                target=p+r@(grip+[0,-18,0] if gesture=='Sweep' else SUPPORT)
                second=max(second,float(np.linalg.norm(target-w['L-Hand'][1])))
                if gesture in ('Read','ReadLoop'):
                    assert head_clearance(ik,w,animated_parts(item,tracks,frame))>2.5,(gesture,frame,'book too close to face')
                    # Local +Y is the top of the printed page. It must point
                    # away from the chest and slightly upward, not under the chin.
                    top=r@np.array([0,1,0])
                    assert top[1]>.1 and top[2]>.8,(gesture,frame,'book is reversed or tilted down',top)
                    # Pages are double-sided quads on opposite covers. Check
                    # the outward side of each page, not its texture winding.
                    for page in animated_parts(item,tracks,frame):
                        if page['name'] not in ('Page-Top','Page-Bot'):continue
                        normal=r@matrix(page['orientation'])@np.array([0,-1 if page['name']=='Page-Top' else 1,0])
                        center=p+r@np.array([page['position'][axis] for axis in 'xyz'])
                        assert normal[1]>.5,(gesture,frame,'pages face down')
                        assert normal@(w['Head'][1]-center)>2,(gesture,frame,'pages face away from reader')
            if brush is not None:
                y=float(np.min((r@brush.T)[1])+p[1]);low=min(low,y);high=max(high,y)
                for side in 'LR':
                    q=interpolate(tracks[side+'-Hand']['orientation'],frame);wrist=max(wrist,math.degrees(2*math.acos(min(1,abs(q['w'])))))
        assert second<.7,(gesture,'support lost',second)
        if brush is not None:
            assert low>=-.5 and high<4,('broom floor clearance',low,high)
            assert wrist<35,('sweep wrist',wrist)
        report.append(dict(gesture=gesture,maxSupportingHandError=second,minBrushY=low,maxBrushY=high,maxWristDegrees=wrist))
    write_json(ROOT/'build/villager-life-preview/native-item-validation.json',report)
    print('Original item grips preserved; support hands stay in contact; broom brush stays at floor level.')
if __name__=='__main__':main()
