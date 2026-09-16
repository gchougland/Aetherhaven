"""Validate original grips, limited wrists and spoon contact at every client frame."""
import json,math
import numpy as np
from generate_villager_life_assets import ROOT,RES,quat,write_json
from villager_life_ik import ArmIK,matrix,interpolate
from villager_native_items import ASSETS,grips,animated_parts
from villager_prop_preview import geometry_points
from villager_life_mixing import SPOON_TIP,torso_gaps

def main():
    ik=ArmIK(ASSETS,quat)
    food_bounds=[]
    for part in animated_parts('Food_Salad_Caesar'):
        pts=(matrix(part['orientation'])@geometry_points(part['shape']).T).T+np.array([part['position'][k] for k in 'xyz'])
        food_bounds.append((pts.min(axis=0),pts.max(axis=0)))
    anim=json.loads((RES/'Common/Characters/Animations/Aetherhaven/Life/Mix.blockyanim').read_text());tracks=anim['nodeAnimations']
    max_radius=0;max_tilt=0;wrists={'L':0,'R':0};min_y=100;max_y=-100;clearance=100;exposed=100;head_gap=100
    for frame in range(anim['duration']+1):
        w=ik.fk(tracks,frame);br,bp=w['R-Attachment'];sr,sp=w['L-Attachment']
        assert bp[1]<70 and bp[2]>22,('salad must stay low and in front',frame,bp)
        point=br.T@(sp+sr@SPOON_TIP-bp)
        max_radius=max(max_radius,float(np.linalg.norm(point[[0,2]])))
        min_y=min(min_y,float(point[1]));max_y=max(max_y,float(point[1]))
        max_tilt=max(max_tilt,math.degrees(math.acos(np.clip(br[1,1],-1,1))))
        for side in 'LR':
            gaps=torso_gaps(ik,w,{n:w[n] for n in (side+'-Arm',side+'-Forearm',side+'-Hand')})
            clearance=min(clearance,float(gaps.min()))
            head_gap=min(head_gap,float(gaps[2::3].min()))
            elbow=matrix(interpolate(tracks[side+'-Forearm']['orientation'],frame))
            bend=math.degrees(math.atan2(elbow[2,1],elbow[1,1]))
            assert -150.1<bend<-1.9,('elbow bends backwards',side,frame,bend)
        # Exposed shaft between the gripping fingers and the spoon bowl. Test
        # against the actual food toppings under each point, not just the rim
        # or the maximum height of an unrelated cheese slice across the plate.
        hr,hp=w['L-Hand'];shape=ik.nodes['L-Hand']['shape'];half=np.array([shape['settings']['size'][k]/2 for k in 'xyz'])
        visible=0
        for z in np.linspace(4,11,29):
            point_world=sp+sr@np.array([0.,0.,z]);point_food=br.T@(point_world-bp)
            in_hand=np.all(abs(hr.T@(point_world-hp))<=half+.2)
            below_food=any(np.all(point_food[[0,2]]>=low[[0,2]]) and np.all(point_food[[0,2]]<=high[[0,2]]) and point_food[1]<=high[1] for low,high in food_bounds)
            if not in_hand and not below_food:visible+=1
        exposed=min(exposed,visible*7/29)
        for side in 'LR':
            q=interpolate(tracks[side+'-Hand']['orientation'],frame)
            wrists[side]=max(wrists[side],math.degrees(2*math.acos(min(1,abs(q['w'])))))
    write_json(ROOT/'build/villager-life-preview/mixing-validation.json',dict(maxSpoonRadius=max_radius,tipHeightRange=[min_y,max_y],maxBowlTiltDegrees=max_tilt,maxWristDegrees=wrists,minArmBodyGap=clearance,minArmHeadGap=head_gap,minVisibleSpoonGap=exposed))
    assert max_radius<10,(max_radius,'spoon outside salad')
    assert 11<min_y and max_y<17.5,(min_y,max_y,'spoon above/below food')
    assert max_tilt<12,('bowl spills',max_tilt)
    assert wrists['L']<35,('stirring wrist bent too far',wrists['L'])
    # The underhand support extends the wrist to keep the native bowl level at
    # waist height; the stirring wrist has the stricter neutral-grip limit.
    assert wrists['R']<42,('support wrist bent too far',wrists['R'])
    assert clearance>.05,('arm intersects torso or head',clearance)
    assert exposed>2,('spoon concealed by hand/salad',exposed)
    for side,item in [('R','Food_Salad_Caesar'),('L','Aetherhaven_Life_Prop_Spoon')]:
        p,r=grips(item,side)
        for key in tracks[side+'-Attachment']['position']:
            assert np.allclose([key['delta'][k] for k in 'xyz'],p),'changed original grip'
        for key in tracks[side+'-Attachment']['orientation']:
            assert np.allclose(matrix(key['delta']),r,atol=1e-6),'changed original grip rotation'
    for channels in tracks.values():
        for keys in channels.values():
            if keys:assert keys[0]['delta']==keys[-1]['delta'],'loop seam'
    print(f'Mixing: normal elbow bends; head/body clearance {clearance:.2f}; visible spoon {exposed:.2f}; wrists {wrists}; bowl tilt {max_tilt:.1f}; closed loop.')
if __name__=='__main__':main()
