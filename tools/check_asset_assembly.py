"""Geometry gate for original assets: contact graph and coplanar visible faces.

SAT checks oriented boxes, including the portal rim. This supplements native visual
inspection; it cannot prove that a connected assembly is attractive or sensible.
"""
import json
from pathlib import Path
import numpy as np
from villager_prop_preview import world_shapes
from check_ledger_geometry import find_overlaps

def connected(a,b):
    edges=lambda p:[p[4]-p[0],p[2]-p[0],p[1]-p[0]]
    ae,be=edges(a),edges(b)
    for axis in ae+be+[np.cross(x,y) for x in ae for y in be]:
        length=np.linalg.norm(axis)
        if length<1e-9:continue
        axis=axis/length;pa=a@axis;pb=b@axis
        if pa.max()<pb.min()-1e-5 or pb.max()<pa.min()-1e-5:return False
    return True

def disconnected(model):
    boxes=[(n,p) for n,p in world_shapes(model['nodes']) if n['shape']['type']=='box']
    if not boxes:return []
    reached={0}
    while True:
        added={i for i,(_,p) in enumerate(boxes) if i not in reached and any(connected(p,boxes[j][1]) for j in reached)}
        if not added:break
        reached|=added
    return [n['name'] for i,(n,_) in enumerate(boxes) if i not in reached]

def separate_trim_faces(nodes):
    """Give overlapping box trim a tiny physical relief instead of coincident faces.

    Expansion never opens a seam. Texture pixel density is unchanged. This is only
    used for authored prop boxes, not native models or marketplace prefabs.
    """
    model={'nodes':nodes};lookup={n['id']:n for n in nodes}
    for _ in range(80):
        issues=find_overlaps(model)
        if not issues:return
        for _,other,_ in issues:
            node=lookup[other.split(' ',1)[0]];face=other.rsplit(' ',1)[1]
            axis={'front':'z','back':'z','left':'x','right':'x','top':'y','bottom':'y'}[face]
            size=node['shape']['settings']['size'][axis]
            node['shape']['stretch'][axis]+=0.02/size
    raise ValueError('Unresolved coplanar trim: '+str(find_overlaps(model)))

def validate(model,label):
    gaps=disconnected(model);overlaps=find_overlaps(model)
    if gaps or overlaps:raise ValueError(f'{label}: disconnected={gaps}; coplanar={overlaps}')

if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]/'src/main/resources/Common'
    paths=list((root/'Items/Aetherhaven/Profession_Kits').glob('*.blockymodel'))+list((root/'Items/Aetherhaven/Guard_Kits').glob('*.blockymodel'))
    paths+=list((root/'Blocks/Aetherhaven/Hunt').glob('*.blockymodel'))
    paths+=list((root/'Blocks/Aetherhaven/Artisans').glob('*.blockymodel'))
    paths+=[root/'NPC/Aetherhaven/Hunt/Portal.blockymodel']
    failures=[]
    for p in paths:
        try:validate(json.loads(p.read_text()),p.stem)
        except ValueError as e:failures.append(str(e))
    for failure in failures:print(failure)
    print(f'{len(paths)} models checked, {len(failures)} failed')
    raise SystemExit(bool(failures))
