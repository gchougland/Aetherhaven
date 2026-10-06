"""Small isometric plot icons with fitted bounds and oriented roof slopes."""
import json,math,sys
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
p=json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
faces=[]
yaw=math.radians(-38);pitch=math.radians(28)
cam=np.array([[math.cos(yaw),0,math.sin(yaw)],[0,1,0],[-math.sin(yaw),0,math.cos(yaw)]])
cam=np.array([[1,0,0],[0,math.cos(pitch),-math.sin(pitch)],[0,math.sin(pitch),math.cos(pitch)]])@cam
for b in p['blocks']:
 name=b['name']
 if name=='Empty' or b.get('filler') or b['y']<-1:continue
 color=(119,86,55)
 if 'Roof' in name:color=(174,140,84) if 'Softwood' in name else (65,76,83)
 elif 'Wall' in name:color=(196,183,144)
 elif 'Rock' in name:color=(103,107,97)
 elif 'Window' in name:color=(60,90,106)
 elif 'Trunk' in name:color=(79,60,41)
 pts=np.array([[x,y,z] for x in (-.5,.5) for y in (0,1) for z in (-.5,.5)])
 if 'Roof' in name and 'Flat' not in name:
  for pt in pts:
   if pt[1]==1:pt[1]=.18 if pt[2]>.0 else 1
 elif 'Roof_Flat' in name:
  pts[:,1]*=.2
 rot=b.get('rotation',0)*math.pi/2;r=np.array([[math.cos(rot),0,math.sin(rot)],[0,1,0],[-math.sin(rot),0,math.cos(rot)]])
 pts=(cam@((r@pts.T).T+np.array([b['x'],b['y'],b['z']])).T).T
 for indices,shade in [([3,7,5,1],.82),([7,6,4,5],.67),([2,6,7,3],1.0),([2,3,1,0],.78),([6,2,0,4],.8)]:
  q=pts[indices];faces.append((q[:,2].mean(),q,tuple(int(c*shade) for c in color)))
allpts=np.concatenate([f[1] for f in faces]);lo,hi=allpts[:,:2].min(axis=0),allpts[:,:2].max(axis=0);center=(lo+hi)/2;scale=470/max(hi-lo)
im=Image.new('RGBA',(512,512));d=ImageDraw.Draw(im)
for _,q,color in sorted(faces,key=lambda f:f[0]):d.polygon([(256+(v[0]-center[0])*scale,256-(v[1]-center[1])*scale) for v in q],fill=(*color,255))
im.resize((128,128),Image.Resampling.LANCZOS).save(sys.argv[2])
