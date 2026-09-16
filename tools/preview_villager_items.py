"""Render active held-item geometry with native UVs; generate the spoon inventory icon."""
import json
import math
from PIL import Image, ImageDraw
import numpy as np
from preview_villager_life import ROOT, ASSETS, OUT, ANIMS, render
from villager_life_items import flattened
from villager_life_props import quaternion
from villager_life_ik import matrix
from villager_prop_preview import geometry_points, textured_faces

RES=ROOT/'src/main/resources'

def item_image(name, size=256):
    from villager_native_items import geometry,animated_parts
    ids={'Broom':'Halloween_Broomstick','Mallet':'Tool_Hammer_Iron','OpenBook':'Weapon_Spellbook_Grimoire_Brown',
         'Salad':'Food_Salad_Caesar','Plant':'Plant_Flower_Bushy_Blue','Spoon':'Aetherhaven_Life_Prop_Spoon'}
    _,texture_path,_=geometry(ids[name]);texture=Image.open(ASSETS/'Common'/texture_path).convert('RGBA')
    parts=animated_parts(ids[name])
    yaw=-.55; pitch=.5
    camera=np.array([[math.cos(yaw),0,math.sin(yaw)],[0,1,0],[-math.sin(yaw),0,math.cos(yaw)]])
    camera=np.array([[1,0,0],[0,math.cos(pitch),-math.sin(pitch)],[0,math.sin(pitch),math.cos(pitch)]])@camera
    faces=[];bounds=[]
    for part in parts:
        points=(matrix(part['orientation'])@geometry_points(part['shape']).T).T+np.array([part['position'][k] for k in 'xyz'])
        points=(camera@points.T).T;bounds.extend(points)
        faces.extend(textured_faces(points,part['shape'],texture))
    points=np.array(bounds);low=points[:,:2].min(axis=0);high=points[:,:2].max(axis=0)
    center=(low+high)/2;scale=size*2*.86/max(high-low)
    im=Image.new('RGBA',(size*2,size*2));draw=ImageDraw.Draw(im)
    for _,poly,color,_ in sorted(faces,key=lambda f:f[0]):
        draw.polygon([(size+(p[0]-center[0])*scale,size-(p[1]-center[1])*scale) for p in poly],fill=(*color,255))
    return im.resize((size,size),Image.Resampling.LANCZOS)

def main():
    names=['Broom','Mallet','OpenBook','Salad','Plant','Spoon']
    sheet=Image.new('RGB',(256*3,288*2),'#e9e9e6')
    for i,name in enumerate(names):
        im=item_image(name);x=i%3*256;y=i//3*288
        sheet.paste(im,(x,y),im);ImageDraw.Draw(sheet).text((x+12,y+264),name,fill='#303b4b')
        if name == 'Spoon':
            path=RES/f'Common/Icons/ItemsGenerated/Aetherhaven_Life_{name}.png'
            path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
    sheet.save(OUT/'held-items.png')
    sheet=Image.new('RGB',(256*4,370*2),'#e9e9e6')
    for i,(gesture,prop) in enumerate([('Sweep','Broom'),('Craft','Mallet'),('Read','OpenBook'),('Tend','Plant'),('Mix','Spoon'),('ShowItem',None)]):
        anim=json.loads((ANIMS/f'{gesture}.blockyanim').read_text());anim['previewName']=gesture
        if prop:anim['previewProp']=prop
        else:anim['previewItem']='Food_Bread'
        x=i%4*256;y=i//4*370
        sheet.paste(render(anim,.5,size=(256,340)),(x,y))
        ImageDraw.Draw(sheet).text((x+10,y+346),gesture+' / '+(prop or 'bread'),fill='#303b4b')
    sheet.save(OUT/'held-poses.png')
    print('Rendered six textured held items, one unique icon and six in-hand poses.')

if __name__=='__main__':main()
