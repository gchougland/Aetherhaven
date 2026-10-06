"""Original counting desk. Face-specific painted pixels at native bench density.
Reference assets are read only for the comparison, never exported into our asset.
"""
import json, math, random
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from villager_prop_preview import textured_faces, world_shapes
ROOT=Path(__file__).resolve().parents[1]
COMMON=ROOT/'src/main/resources/Common'
OUT=COMMON/'Blocks/Aetherhaven/Warehouse_Ledger_Desk'
PREVIEW=ROOT/'build/warehouse-assets'
OUT.mkdir(parents=True,exist_ok=True); PREVIEW.mkdir(parents=True,exist_ok=True)
atlas=Image.new('RGBA',(256,256)); cursor_x=cursor_y=row_height=0
parts=[];patches={}

def tint(color,delta):
 return tuple(max(0,min(255,int(v+delta))) for v in color)+(255,)

def paint(w,h,mat,seed,face):
 rng=random.Random(seed)
 palette={'oak':(104,72,44),'edge':(124,89,53),'darkwood':(76,51,34),'drawer':(113,77,46),
 'brass':(155,122,62),'iron':(63,67,62),'green':(44,78,65),'paper':(210,194,151),
 'pages':(183,163,120),'parcel':(140,113,76),'ink':(26,38,37),'red':(112,48,35),'feather':(203,196,163)}
 base=palette['green' if mat=='banner' else mat];im=Image.new('RGBA',(w,h));d=ImageDraw.Draw(im)
 for y in range(h):
  for x in range(w):
   edge=min(x,y,w-1-x,h-1-y);v=6*(1-y/max(1,h-1))-3
   if edge==0:v+=11 if y==0 or x==0 else -16
   elif edge==1:v+=4 if y<h/2 else -4
   if mat in ('oak','edge','darkwood','drawer') and h>6:
    v-=max(0,3-y)*3 + max(0,y-h+4)*2
   im.putpixel((x,y),tint(base,v))
 if mat in ('oak','edge','darkwood','drawer'):
  horizontal=w>=h or face in ('top','bottom');length,cross=(w,h) if horizontal else (h,w)
  def pt(a,b):return (a,b) if horizontal else (b,a)
  for b in range(3,max(3,cross-2),6):
   start=rng.randint(1,max(1,length//3));end=rng.randint(max(start+1,length*2//3),max(start+1,length-2));bend=rng.choice([-1,0,1])
   d.line([pt(start,b),pt((start+end)//2,b+bend),pt(end,b)],fill=tint(base,-rng.randint(7,13)))
   if cross>6:d.line([pt(start+2,b+1),pt(max(start+2,end-2),b+1)],fill=tint(base,7))
  if w>=40 and h>=8:
   x=rng.randint(4,w-5);y=rng.randint(3,h-4)
   d.line([(x-4,y),(x,y-1),(x+4,y),(x,y+1),(x-4,y)],fill=tint(base,-15));d.point((x,y),fill=tint(base,-22))
  if mat=='drawer' and w>6 and h>4:
   d.rectangle((2,2,w-3,h-2),outline=tint(base,-19));d.line((3,3,w-4,3),fill=tint(base,10))
 elif mat=='brass':
  if h>2:d.line((0,1,w-1,1),fill='#c6ab70')
  if w>3:d.line((1,0,1,h-1),fill='#b79a5a')
 elif mat in ('green','banner') and w>5 and h>5:
  d.rectangle((2,2,w-3,h-3),outline='#9e8d57');d.rectangle((3,3,w-4,h-4),outline='#315346')
  if mat=='banner':
   cx=w//2
   d.rectangle((cx-3,2,cx+3,h-3),outline='#ad975b')
   d.line((cx-2,3,cx+2,h-4),fill='#c3ad73')
 elif mat=='paper' and face=='top' and w>6 and h>8:
  d.line((w-4,2,w-4,h-3),fill='#ab8f6d');d.line((2,3,w-3,3),fill='#786448')
  for y in range(6,h-2,3):
   d.line((2,y,rng.randint(3,max(3,w-6)),y),fill='#7c735c');d.point((w-3,y),fill='#968269')
 elif mat=='pages':
  for y in range(1,h-1,2):d.line((0,y,w-1,y),fill='#d1bc90')
 elif mat=='parcel':
  for x in range(w//2-1,w//2+1):d.line((x,0,x,h-1),fill='#cbbb87')
  if face in ('top','bottom'):d.line((0,h//2,w-1,h//2),fill='#d4c18d',width=2)
 elif mat=='feather':
  im=Image.new('RGBA',(w,h));d=ImageDraw.Draw(im)
  d.polygon([(w//2,0),(w-1,4),(w-2,h-7),(w//2,h-2),(1,h-7),(0,6)],fill='#c9c2a2')
  d.line((w//2,2,w//2,h-1),fill='#877c61')
  for y in range(5,h-5,3):
   d.line((w//2,y+2,w-2,y),fill='#ece2bc');d.line((1,y,w//2,y+2),fill='#a79e81')
 return im

def patch(w,h,mat,seed,face):
 global cursor_x,cursor_y,row_height
 face = 'top' if face in ('top','bottom') else 'front'
 key=(w,h,mat,seed,face)
 if key in patches:return patches[key]
 if cursor_x+w+1>atlas.width:cursor_x=0;cursor_y+=row_height+1;row_height=0
 if cursor_y+h>atlas.height:raise ValueError('Atlas overflow')
 atlas.paste(paint(w,h,mat,seed,face),(cursor_x,cursor_y))
 result={'offset':{'x':cursor_x,'y':cursor_y},'mirror':{'x':False,'y':False},'angle':0}
 patches[key]=result;cursor_x+=w+1;row_height=max(row_height,h)
 return result

def box(name,center,size,material='oak',rotation=0,axis='z',seed=0):
 sx,sy,sz=size;dims={'front':(sx,sy),'back':(sx,sy),'left':(sz,sy),'right':(sz,sy),'top':(sx,sz),'bottom':(sx,sz)}
 shape={'type':'box','offset':dict.fromkeys('xyz',0),'stretch':dict.fromkeys('xyz',1),
 'settings':{'isPiece':False,'size':dict(zip('xyz',size))},
 'textureLayout':{face:patch(w,h,material,seed,face) for face,(w,h) in dims.items()},
 'unwrapMode':'custom','visible':True,'doubleSided':False,'shadingMode':'standard'}
 orientation=dict.fromkeys('xyz',0);orientation[axis]=math.sin(math.radians(rotation)/2);orientation['w']=math.cos(math.radians(rotation)/2)
 position=dict(zip('xyz',center));position['x']-=16
 node={'id':str(len(parts)+1),'name':name,'position':position,'orientation':orientation,'shape':shape}
 parts.append(node);return node

# Heavy mortise joinery. The anchor and its western neighbour are occupied.
for x in (-25,25):
 for z in (-10,10):
  box('Oak post',(x,12.5,z),(6,17,6),'oak',seed=1)
  box('Worn foot',(x,2,z),(7,4,7),'darkwood')
  box('Mortise shoulder',(x,23,z),(7,4,7),'edge')
box('Rear stretcher',(0,6,-10),(44,4,4),'darkwood')
box('Low shelf',(0,6,0),(46,3,23),'oak',seed=2)
box('Drawer pedestal',(-20,16.5,0),(14,19,22),'darkwood')
for y in (11,18,24):
 box('Inset drawer',(-19.5,y,13),(13,5,2),'drawer')
 box('Pull plate',(-19.5,y,14.5),(5,2,1),'iron')
 box('Brass drawer pull',(-19.5,y,15.5),(3,1,1),'brass')
box('Front joinery',(4,24,11),(34,4,4),'oak',seed=2)
for x,rot in [(-11,-35),(23,35)]:box('Knee brace',(x,21,8),(4,10,4),'oak',rot,seed=1)
for i,z in enumerate((-10,0,10)):box('Tabletop plank',(0,29,z),(54,4,10),'edge',seed=i)
box('Front lip',(0,26,14),(62,2,2),'oak',seed=1)
box('Rear lip',(0,26,-14),(62,2,2),'oak',seed=1)
for x in (-29,29):
 box('Breadboard end',(x,29,0),(4,4,30),'oak',seed=2)
 for z in (-11,11):box('Iron dowel',(x,31.1,z),(1,1,1),'iron')
box('Leather writing mat',(1,31.5,3),(26,1,26),'green')
box('Hanging leather',(1,26,15.5),(26,10,1),'banner')
box('Runner hem',(1,20.5,15.5),(26,1,1),'brass')
# Sculpted open book, the focal point of the desk.
for side in (-1,1):
 x=1+side*6.3
 box('Ledger cover',(x,33.5,4),(12,1,18),'green',side*9)
 page=box('Bound page edges',(x,34.5,4),(11,2,16),'pages',side*9)
 page['shape']['textureLayout']['top']=patch(11,16,'paper',0,'top')
box('Ledger binding',(1,32.9,4),(2,2,17),'darkwood')
box('Bookmark',(6,35.85,10),(1,1,9),'red',9)
# Open pigeonholes, with sorting slips. Deliberate asymmetric silhouette.
box('Sorter base',(-20,32,-8),(16,2,10),'darkwood')
box('Sorter back',(-20,37,-12),(16,8,2),'oak')
for x in (-27,-20,-13):box('Sorter divider',(x,37,-7),(2,8,8),'oak')
box('Sorter cap',(-20,42,-8),(18,2,11),'edge')
for x,y in [(-23.5,33.5),(-16.5,33.5),(-23.5,34.5)]:box('Folded manifests',(x,y,-7),(5,1,7),'paper')
box('Lower parcel',(-2,12,-2),(16,9,14),'parcel',seed=1)
box('Lower parcel seal',(-2,16.8,0),(4,1,3),'red')
box('Outgoing parcel',(23,34,4),(10,6,12),'parcel')
box('Address label',(24,37.2,5),(5,1,4),'paper')
box('Wax seal',(20,37.4,1),(2,1,2),'red')
for y,width,color in [(9,10,'green'),(12,9,'red')]:
 box('Closed account book',(17,y,1),(width,3,15),color)
 box('Book page edge',(17,y,8.5),(width-2,1,1),'pages')
box('Ink bottle foot',(13,32.5,-10),(5,2,5),'iron')
box('Ink bottle body',(13,34.5,-10),(4,3,4),'ink')
box('Ink bottle rim',(13,36.5,-10),(3,1,3),'brass')
box('Quill shaft',(12,40,-10),(1,8,1),'brass',-20)
quill=box('Cut feather',(10.8,44,-10),(7,15,1),'feather',-20)
quill['shape']['type']='quad'
quill['shape']['settings'].update({'size':{'x':7,'y':15,'z':0},'normal':'+Z'})
quill['shape']['textureLayout']={'front':quill['shape']['textureLayout']['front']}
quill['shape']['doubleSided']=True
model={'nodes':parts,'format':'prop','lod':'auto'}
from check_ledger_geometry import find_overlaps
overlaps=find_overlaps(model)
if overlaps:raise ValueError(f'Overlapping surface faces: {overlaps}')
(OUT/'Warehouse_Ledger_Desk.blockymodel').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
from generate_furniture_hitboxes import write_hitbox
write_hitbox(OUT/'Warehouse_Ledger_Desk.blockymodel',
             ROOT/'src/main/resources/Server/Item/Block/Hitboxes/Aetherhaven/Aetherhaven_Warehouse_Ledger_Desk.json')
atlas.save(OUT/'Warehouse_Ledger_Desk.png')

def mesh_faces(nodes,texture,camera):
 for part,points in world_shapes(nodes):
  yield from textured_faces((camera@points.T).T,part['shape'],texture)

def render(model,texture,size=400,yaw=-.6,fixed_scale=None):
 pitch=.52
 camera=np.array([[math.cos(yaw),0,math.sin(yaw)],[0,1,0],[-math.sin(yaw),0,math.cos(yaw)]])
 camera=np.array([[1,0,0],[0,math.cos(pitch),-math.sin(pitch)],[0,math.sin(pitch),math.cos(pitch)]])@camera
 faces=list(mesh_faces(model['nodes'],texture,camera))
 bounds=np.concatenate([f[1] for f in faces]);low,high=bounds[:,:2].min(axis=0),bounds[:,:2].max(axis=0)
 center=(low+high)/2;scale=fixed_scale or size*.85/max(high-low)
 im=Image.new('RGBA',(size*2,size*2));d=ImageDraw.Draw(im)
 for _,poly,color,_ in sorted(faces,key=lambda f:f[0]):
  d.polygon([(size+(p[0]-center[0])*scale*2,size-(p[1]-center[1])*scale*2) for p in poly],fill=(*color,255))
 return im.resize((size,size),Image.Resampling.LANCZOS)

render(model,atlas,64).save(COMMON/'Icons/ItemsGenerated/Aetherhaven_Warehouse_Ledger_Desk.png')
sheet=Image.new('RGB',(1200,480),'#303a3c');d=ImageDraw.Draw(sheet)
for i,yaw in enumerate((-.6,.65,math.pi-.4)):
 im=render(model,atlas,400,yaw);sheet.paste(im,(i*400,35),im)
 d.text((i*400+145,450),['Front left','Front right','Rear'][i],fill='#ddd3b8')
sheet.save(PREVIEW/'ledger-desk.png')
# Equal camera and physical scale reveal proportion and texel-density differences.
native=ROOT.parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets/Common/Blocks/Benches'
sheet=Image.new('RGB',(1200,500),'#303a3c');d=ImageDraw.Draw(sheet)
for i,(name,mesh,tex) in enumerate([
 ('Hytale Workbench',native/'Workbench.blockymodel',native/'Workbench_Texture.png'),
 ('Warehouse Ledger Desk',OUT/'Warehouse_Ledger_Desk.blockymodel',OUT/'Warehouse_Ledger_Desk.png'),
 ('Hytale Furniture Bench',native/'Furniture.blockymodel',native/'Furniture_Texture.png')]):
 m=json.loads(mesh.read_text(encoding='utf-8'));t=Image.open(tex).convert('RGBA')
 im=render(m,t,400,fixed_scale=4.2);sheet.paste(im,(i*400,40),im)
 d.text((i*400+115,465),name,fill='#ddd3b8')
sheet.save(PREVIEW/'bench-scale-comparison.png')
print(f'Ledger desk: {len(parts)} parts; atlas occupies {cursor_y+row_height}/256 rows. Original pixels only.')
