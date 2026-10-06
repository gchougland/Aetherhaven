"""Render a villager portrait from its actual model attachments and gradient textures."""
import json, math, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from villager_prop_preview import rotation_matrix, world_shapes, textured_faces
ROOT=Path(__file__).resolve().parents[1]
NATIVE=ROOT.parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets'
COMMON=ROOT/'src/main/resources/Common'
def asset(name):
 p=COMMON/name
 return p if p.exists() else NATIVE/'Common'/name
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def frames(nodes,rotation=None,position=None,out=None):
 rotation=np.eye(3) if rotation is None else rotation;position=np.zeros(3) if position is None else position;out={} if out is None else out
 for n in nodes:
  r=rotation@rotation_matrix(n.get('orientation',{}));p=position+rotation@np.array([n.get('position',{}).get(k,0) for k in 'xyz'])
  p+=r@np.array([n.get('shape',{}).get('offset',{}).get(k,0) for k in 'xyz'])
  out[n['name']]=(r,p)
  frames(n.get('children',[]),r,p,out)
 return out
def render(model_file,output):
 definition=load(model_file);base=load(asset(definition['Model']));bones=frames(base['nodes'])
 gradients={x['Id']:x['Gradients'] for x in load(NATIVE/'Cosmetics/CharacterCreator/GradientSets.json')}
 def texture(d):
  im=Image.open(asset(d['Texture'])).convert('RGBA')
  if d.get('GradientSet'):
   g=Image.open(asset(gradients[d['GradientSet']][d['GradientId']]['Texture'])).convert('RGB')
   a=np.array(im);ramp=np.array(g.resize((256,1),Image.Resampling.BILINEAR))[0]
   a[:,:,:3]=ramp[a[:,:,0]];im=Image.fromarray(a)
  return im
 geometry=[(n,p,texture(definition)) for n,p in world_shapes(base['nodes'])]
 for a in definition.get('DefaultAttachments',[]):
  attachment=load(asset(a['Model']));tex=texture(a)
  for root in attachment['nodes']:
   if root['name'] not in bones:raise ValueError('Unmatched attachment bone '+root['name'])
   r,p=bones[root['name']]
   geometry.extend((n,points,tex) for n,points in world_shapes(root.get('children',[]),r,p))
 # Frontal bust, matching the existing character icons. Slightly raised camera.
 pitch=.04;camera=np.array([[1,0,0],[0,math.cos(pitch),-math.sin(pitch)],[0,math.sin(pitch),math.cos(pitch)]])
 faces=[]
 for n,p,tex in geometry:
  if p[:,1].max()<65:continue
  faces.extend(textured_faces((camera@p.T).T,n['shape'],tex))
 size=512;im=Image.new('RGBA',(size,size));draw=ImageDraw.Draw(im)
 top=max(float(p[:,1].max()) for _,p,_,_ in faces)+2
 scale=size/(top-70)
 for _,p,rgb,_ in sorted(faces,key=lambda f:f[0]):
  draw.polygon([(size/2+v[0]*scale,size-(v[1]-70)*scale) for v in p],fill=(*rgb,255))
 output.parent.mkdir(parents=True,exist_ok=True);im.resize((128,128),Image.Resampling.LANCZOS).save(output)
 print(output)
if __name__=='__main__':render(Path(sys.argv[1]),Path(sys.argv[2]))
