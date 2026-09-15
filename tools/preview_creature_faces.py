"""Offline native head/hinge pose reference sheet; no game or source assets modified."""
import copy
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from preview_villager_life import rot, sample
from villager_creature_faces import load_references, RIGS

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'src/main/resources'
BASE = ROOT.parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets/Common'


def textured_faces(points, shape, texture):
    sx, sy, sz = [int(shape['settings']['size'][k]) for k in 'xyz']
    sides = [('left', [0,1,3,2], sz, sy, .72), ('right', [5,4,6,7], sz, sy, .88),
             ('bottom', [0,4,5,1], sx, sz, .6), ('top', [2,6,7,3], sx, sz, 1),
             ('back', [4,0,2,6], sx, sy, .76), ('front', [1,5,7,3], sx, sy, .94)]
    for side, indices, width, height, shade in sides:
        layout = shape['textureLayout'].get(side)
        if not layout:
            continue
        quad = points[indices]
        angle = math.radians(layout.get('angle', 0))
        rotation = np.array([[math.cos(angle), -math.sin(angle)], [math.sin(angle), math.cos(angle)]])
        offset = np.array([layout['offset'][k] for k in 'xy'])
        def at(u, v):
            return quad[0]+(quad[1]-quad[0])*u+(quad[3]-quad[0])*v
        for y in range(height):
            for x in range(width):
                u = -(x+.5) if layout.get('mirror', {}).get('x') else x+.5
                v = -(y+.5) if layout.get('mirror', {}).get('y') else y+.5
                tx, ty = np.floor(offset+rotation@np.array([u, v])+1e-7).astype(int)
                # Several native ears have unused side UVs outside the atlas.
                if not (0 <= tx < texture.width and 0 <= ty < texture.height):
                    continue
                pixel = texture.getpixel((tx, ty))
                if pixel[3] < 128:
                    continue
                color = tuple(int(c*shade) for c in pixel[:3])
                poly = np.array([at(x/width,1-y/height), at((x+1)/width,1-y/height),
                                 at((x+1)/width,1-(y+1)/height), at(x/width,1-(y+1)/height)])
                yield poly[:,2].mean(), poly, color, None


def linear(frames, time, default):
    if not frames:
        return default
    if time <= frames[0]['time']:
        return frames[0]['delta']
    for a, b in zip(frames, frames[1:]):
        if time <= b['time']:
            t = (time-a['time'])/(b['time']-a['time'])
            t = t*t*(3-2*t)
            return {k: a['delta'][k]*(1-t)+b['delta'][k]*t for k in a['delta']}
    return frames[-1]['delta']


def render(model, animation, time, read_common):
    faces, anchors = [], {}
    camera = rot({'y': math.sin(-.22), 'w': math.cos(-.22)})
    def visit(node, matrix, pos, texture, head=False):
        name = node['name']
        head = head or name == 'Head'
        track = animation['nodeAnimations'].get(name, {})
        delta = linear(track.get('position'), time, {})
        position = pos + matrix @ np.array([node.get('position', {}).get(k, 0)+delta.get(k, 0) for k in 'xyz'])
        local = rot(node.get('orientation', {}))
        if track.get('orientation'):
            local = local @ rot(sample(track['orientation'], time))
        world = matrix @ local
        shape = copy.deepcopy(node.get('shape', {}))
        position += world @ np.array([shape.get('offset', {}).get(k, 0) for k in 'xyz'])
        anchors[name] = world, position, head
        if head and shape.get('visible', True) and shape.get('type') in ('quad', 'box'):
            stretch = linear(track.get('shapeStretch'), time, {})
            scale = np.array([shape.get('stretch', {}).get(k, 1)*stretch.get(k, 1) for k in 'xyz'])
            dims = np.array([shape['settings']['size'].get(k, 0) for k in 'xyz'])
            points = np.array([[x,y,z] for x in (-.5,.5) for y in (-.5,.5) for z in (-.5,.5)])*dims*scale
            points = (camera @ ((world @ points.T).T+position).T).T
            if shape['type'] == 'quad':
                shape['settings']['size']['z'] = 0
                uv = {}
                for frame in track.get('shapeUvOffset', []):
                    if frame['time'] > time:
                        break
                    uv = frame['delta']
                for layout in shape['textureLayout'].values():
                    layout['offset']['x'] += uv.get('x', 0)
                    layout['offset']['y'] -= uv.get('y', 0)
            try:
                faces.extend(textured_faces(points, shape, texture))
            except AssertionError as error:
                raise AssertionError((name, texture.size, shape, error)) from error
        for child in node.get('children', []):
            visit(child, world, position, texture, head)

    texture = Image.open(BASE/model['Texture']).convert('RGBA')
    for root in read_common(model['Model'])['nodes']:
        visit(root, np.eye(3), np.zeros(3), texture)
    for attachment in model.get('DefaultAttachments', []):
        # Inspect facial geometry without hair, hats or armor hiding the hinges.
        if not any(part in attachment['Model'].lower() for part in ('mouth', 'eye')):
            continue
        texture = Image.open(BASE/attachment['Texture']).convert('RGBA')
        for root in read_common(attachment['Model'])['nodes']:
            if root['name'] in anchors:
                matrix, pos, head = anchors[root['name']]
                for child in root.get('children', []):
                    visit(child, matrix, pos, texture, head)
    # Keep the camera fixed relative to the neutral head, including jaw motion.
    center = camera @ anchors['Head'][1]
    im = Image.new('RGB', (280, 250), '#dce1e4')
    draw = ImageDraw.Draw(im)
    for _, poly, color, _ in sorted(faces, key=lambda f: f[0]):
        xy = [(140+(p[0]-center[0])*4, 108-(p[1]-center[1])*4) for p in poly]
        draw.polygon(xy, fill=color)
    return im


def main():
    resolve, read_common, bones_for = load_references(RES)
    rows = []
    for rig, (names, degrees) in RIGS.items():
        model = resolve(names[0])
        path = RES/f'Common/Characters/Animations/Aetherhaven/CreatureFaces/{rig}/LipSync/Explain_BrightMale_Talk_1.blockyanim'
        data = json.loads(path.read_text())
        # Validate every generated face against every resident's actual attachments.
        for name in names:
            bones = bones_for(resolve(name))
            for file in path.parent.parent.rglob('*.blockyanim'):
                tracks = json.loads(file.read_text())['nodeAnimations']
                assert set(tracks) <= set(bones), (name, file, set(tracks)-set(bones))
        mouth = data['nodeAnimations']['Jaw' if degrees else 'Mouth']
        keys = mouth['orientation' if degrees else 'shapeUvOffset']
        if degrees:
            peak = max(keys, key=lambda f: f['delta']['x'])['time']
        else:
            peak = next(f['time'] for f in keys if f['delta']['x'] == 160)
        row = Image.new('RGB', (560, 278), '#dce1e4')
        for i, (label, time) in enumerate([('Rest', 0), ('Speaking', peak)]):
            row.paste(render(model, data, time, read_common), (280*i, 28))
            ImageDraw.Draw(row).text((280*i+10, 8), f'{rig}: {label}', fill='#26333e')
        rows.append(row)
        print(f'{rig}: native bones checked; closed/open pose rendered.', flush=True)
    sheet = Image.new('RGB', (1120, 278*4), '#dce1e4')
    for i, row in enumerate(rows):
        sheet.paste(row, ((i%2)*560, (i//2)*278))
    out = ROOT/'build/villager-life-preview/creature-faces.png'
    sheet.save(out)
    print(out)


if __name__ == '__main__':
    main()
