"""Reject overlapping, equally facing coplanar polygons in the authored desk."""
import json
from pathlib import Path
import numpy as np
from villager_prop_preview import world_shapes
ROOT=Path(__file__).resolve().parents[1]
FACES={'front':[3,7,5,1],'back':[6,2,0,4],'left':[2,3,1,0],
       'right':[7,6,4,5],'top':[2,6,7,3],'bottom':[1,5,4,0]}

def cross2(a,b):return a[0]*b[1]-a[1]*b[0]

def area(poly):
 return abs(sum(cross2(poly[(i+1)%len(poly)],p) for i,p in enumerate(poly)))/2 if len(poly)>2 else 0

def intersect(subject,clip):
 signed=sum(cross2(clip[(i+1)%len(clip)]-p,clip[(i+2)%len(clip)]-clip[(i+1)%len(clip)]) for i,p in enumerate(clip))
 sign=1 if signed>0 else -1
 for i,a in enumerate(clip):
  b=clip[(i+1)%len(clip)];out=[]
  if not len(subject):return []
  for j,p in enumerate(subject):
   q=subject[(j+1)%len(subject)]
   dp=sign*cross2(b-a,p-a);dq=sign*cross2(b-a,q-a)
   if dp>=-1e-7:out.append(p)
   if (dp>1e-7 and dq<-1e-7) or (dp<-1e-7 and dq>1e-7):out.append(p+(q-p)*(dp/(dp-dq)))
  subject=out
 return subject

def find_overlaps(model):
 faces=[];issues=[]
 for node,points in world_shapes(model['nodes']):
  if node['shape']['type']!='box':continue
  for side,indices in FACES.items():
   if side not in node['shape']['textureLayout']:continue
   p=points[indices];n=np.cross(p[1]-p[0],p[3]-p[0]);n/=np.linalg.norm(n)
   faces.append((node['id']+' '+node['name']+' '+side,p,n))
 for i,(name,p,n) in enumerate(faces):
  for other,q,m in faces[i+1:]:
   if np.dot(n,m)<.999999 or abs(np.dot(n,q[0]-p[0]))>1e-6:continue
   axes=[a for a in range(3) if a!=np.argmax(abs(n))]
   a=area(intersect([v[axes] for v in p],[v[axes] for v in q]))
   if a>1e-5:issues.append((name,other,round(float(a),3)))
 return issues

if __name__=='__main__':
 p=ROOT/'src/main/resources/Common/Blocks/Aetherhaven/Warehouse_Ledger_Desk/Warehouse_Ledger_Desk.blockymodel'
 issues=find_overlaps(json.loads(p.read_text(encoding='utf-8')))
 for issue in issues:print(issue)
 print(f'{len(issues)} overlapping coplanar face pairs')
 raise SystemExit(bool(issues))
