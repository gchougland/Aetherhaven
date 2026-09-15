"""Offline pose QA using Hytale's local Player rig; never ships reference geometry.

Usage: python tools/preview_villager_life.py [path/to/HytaleAssets]
Requires numpy and Pillow. Produces a pose contact sheet and animated previews in build/.
"""
import json
import math
from pathlib import Path
import sys
import numpy as np
from PIL import Image, ImageDraw
from villager_prop_preview import textured_faces

ROOT = Path(__file__).resolve().parents[1]
ASSETS = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent / 'HytaleSourceCode/hytale-shared-source/HytaleAssets'
RIG = json.loads((ASSETS/'Common/Characters/Player.blockymodel').read_text())
ANIMS = ROOT/'src/main/resources/Common/Characters/Animations/Aetherhaven/Life'
OUT = ROOT/'build/villager-life-preview'
OUT.mkdir(parents=True, exist_ok=True)


def rot(q):
    x, y, z, w = [q.get(k, 1 if k == 'w' else 0) for k in 'xyzw']
    return np.array([[1-2*y*y-2*z*z, 2*x*y-2*z*w, 2*x*z+2*y*w],
                     [2*x*y+2*z*w, 1-2*x*x-2*z*z, 2*y*z-2*x*w],
                     [2*x*z-2*y*w, 2*y*z+2*x*w, 1-2*x*x-2*y*y]])


def sample(frames, time):
    if time <= frames[0]['time']: return frames[0]['delta']
    for a, b in zip(frames, frames[1:]):
        if a['time'] <= time <= b['time']:
            t = (time-a['time']) / (b['time']-a['time'])
            t = t*t*(3-2*t)
            qa = np.array([a['delta'][k] for k in 'xyzw'])
            qb = np.array([b['delta'][k] for k in 'xyzw'])
            if np.dot(qa, qb) < 0: qb = -qb
            q = qa*(1-t)+qb*t
            q /= np.linalg.norm(q)
            return dict(zip('xyzw', q))
    return frames[-1]['delta']


def render(anim, phase, size=(192, 256), yaw=-.54, pitch=.12):
    faces = []
    camera = rot({'y': math.sin(yaw/2), 'w': math.cos(yaw/2)})
    camera = rot({'x': math.sin(pitch/2), 'w': math.cos(pitch/2)}) @ camera
    kept = {'Pelvis','Belly','Chest','Head','R-Arm','L-Arm','R-Forearm','L-Forearm','R-Hand','L-Hand','R-Thigh','L-Thigh','R-Calf','L-Calf','R-Foot','L-Foot'}
    def walk(node, matrix, pos, prop=False, texture=None):
        offset = np.array([node.get('position', {}).get(k, 0) for k in 'xyz'])
        position = pos + matrix @ offset
        local = rot(node.get('orientation', {}))
        track = anim['nodeAnimations'].get(node['name'], {})
        if track.get('position'):
            frames=track['position'];time=phase*anim['duration']
            delta=frames[-1]['delta']
            for a,b in zip(frames,frames[1:]):
                if time<=b['time']:
                    t=max(0,(time-a['time'])/(b['time']-a['time']));t=t*t*(3-2*t)
                    delta={k:a['delta'][k]*(1-t)+b['delta'][k]*t for k in 'xyz'};break
            position+=matrix@np.array([delta[k] for k in 'xyz'])
        if track.get('orientation'): local = local @ rot(sample(track['orientation'], phase*anim['duration']))
        world = matrix @ local
        shape = node.get('shape', {})
        # BlockyModelBoundsParser passes the shape center to child nodes.
        position += world @ np.array([shape.get('offset', {}).get(k, 0) for k in 'xyz'])
        if (node['name'] in kept or prop) and shape.get('type') == 'box':
            dims = np.array([shape['settings']['size'][k] for k in 'xyz'])
            stretch = np.array([shape.get('stretch', {}).get(k, 1) for k in 'xyz'])
            corners = np.array([[x,y,z] for x in (-.5,.5) for y in (-.5,.5) for z in (-.5,.5)]) * dims * stretch
            points = (world @ corners.T).T + position
            points = (camera @ points.T).T
            color = '#dcaa83' if node['name'] in ('Head','R-Hand','L-Hand') else '#76a8a0' if node['name'] in ('Chest','Belly','R-Arm','L-Arm','R-Forearm','L-Forearm') else '#5c6583'
            if prop:
                color='#ebe0c3' if 'Pages' in node['name'] else '#6c9a58' if 'Leaf' in node['name'] or 'Stem' in node['name'] else '#b5a7ca' if 'Stone' in node['name'] else '#c3a569' if 'Bristles' in node['name'] else '#926449'
                if texture is not None:
                    uv=shape['textureLayout']['front']['offset'];pixel=texture.getpixel((uv['x'],uv['y']))
                    color='#'+''.join(f'{v:02x}' for v in pixel[:3])
            if prop and texture is not None:
                faces.extend(textured_faces(points,shape,texture))
            for indices, shade in ([] if prop and texture is not None else [([0,1,3,2],.65),([4,6,7,5],.88),([0,4,5,1],.55),([2,3,7,6],1.1),([0,2,6,4],.70),([1,5,7,3],1)]):
                rgb = tuple(min(255,int(int(color[i:i+2],16)*shade)) for i in (1,3,5))
                poly = points[indices]
                faces.append((poly[:,2].mean(), poly, rgb, '#434b57'))
        for child in node.get('children', []): walk(child, world, position,prop,texture)
        if node['name']=='R-Attachment' and not prop and 'LifePropRoot' in anim['nodeAnimations']:
            from villager_life_props import PROPS
            prop_name=anim.get('previewProp',PROPS.get(anim.get('previewName')))
            if prop_name:
                model=json.loads((ROOT/f'src/main/resources/Common/Items/Aetherhaven/Life/{prop_name}.blockymodel').read_text())
                item=json.loads((ROOT/f'src/main/resources/Server/Item/Items/Aetherhaven/Life/Aetherhaven_Life_Prop_{prop_name}.json').read_text())
                atlas=Image.open(ASSETS/'Common'/item['Texture']).convert('RGBA')
                for child in model['nodes'][0]['children']:walk(child,world,position,True,atlas)
    for node in RIG['nodes']: walk(node, np.eye(3), np.zeros(3))
    im = Image.new('RGB', (size[0]*2,size[1]*2), '#e9e9e6')
    d = ImageDraw.Draw(im)
    scale = 1.55 * size[1]/256 * 2
    d.ellipse((size[0]-65,size[1]*2-48,size[0]+65,size[1]*2-25),fill='#cdd3d4')
    for _, poly, color, outline in sorted(faces, key=lambda f:f[0]):
        xy = [(size[0]+p[0]*scale, size[1]*2-35-p[1]*scale) for p in poly]
        d.polygon(xy, fill=color, outline=outline, width=1)
    return im.resize(size, Image.Resampling.LANCZOS)


if __name__ == '__main__':
    files = sorted(ANIMS.glob('*.blockyanim'))
    sheet = Image.new('RGB', (192*5, 280*4), '#e9e9e6')
    for i, file in enumerate(files):
        anim = json.loads(file.read_text())
        anim['previewName']=file.stem
        x, y = (i%5)*192, (i//5)*280
        sheet.paste(render(anim,.49), (x,y))
        ImageDraw.Draw(sheet).text((x+15,y+257),file.stem,fill='#303b4b')
    sheet.save(OUT/'poses.png')
    frames = []
    names = ['Laugh','Read','Sweep','Craft']
    animation_data = {name: json.loads((ANIMS/f'{name}.blockyanim').read_text()) for name in names}
    for name,anim in animation_data.items():anim['previewName']=name
    # Preview frames are sampled by elapsed seconds, matching Hytale's 60 FPS.
    for frame in range(91):
        im = Image.new('RGB',(192*4,280),'#e9e9e6')
        for i,name in enumerate(names):
            anim = animation_data[name]
            seconds = anim['duration']/60
            im.paste(render(anim,min(1,frame/15/seconds)),(i*192,0))
            ImageDraw.Draw(im).text((i*192+15,257),f'{name} ({seconds:.1f}s)',fill='#303b4b')
        frames.append(im)
    frames[0].save(OUT/'motions.gif',save_all=True,append_images=frames[1:],duration=67,loop=0)
    print('Rendered pose contact sheet and motion preview.')
