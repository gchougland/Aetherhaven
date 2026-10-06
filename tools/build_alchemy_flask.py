"""Author the flask mesh, its pixel UV atlas, and matching inventory render.

This is an original prop, built from textured cubes in Hytale's prop format.
Run from any directory. The Blockbench preview is written to build/art-preview.
"""
from pathlib import Path
import base64
import json
import math
import uuid
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'src/main/resources/Common/Items/Aetherhaven/Alchemy_Flask'
PREVIEW = ROOT / 'build/art-preview'
DEST.mkdir(parents=True, exist_ok=True)
PREVIEW.mkdir(parents=True, exist_ok=True)
atlas = Image.new('RGBA', (256, 256))
draw = ImageDraw.Draw(atlas)
cursor = [1, 1, 0]
cache = {}
boxes = []
palette = {
    'glass': ((43, 77, 93), (128, 207, 206)),
    'elixir': ((77, 57, 125), (157, 124, 214)),
    'brass': ((108, 68, 39), (235, 184, 94)),
    'leather': ((62, 35, 30), (148, 91, 55)),
    'cork': ((118, 76, 46), (197, 150, 95)),
    'gem': ((79, 149, 142), (185, 247, 214)),
    'mark': ((189, 132, 57), (255, 226, 135)),
}


def patch(material, w, h, face):
    key = material, w, h, face == 'top'
    if key in cache:
        return cache[key]
    if cursor[0] + w + 1 >= 256:
        cursor[0] = 1
        cursor[1] += cursor[2] + 1
        cursor[2] = 0
    x, y = cursor[:2]
    assert y + h < 256
    dark, light = palette[material]
    for v in range(h):
        for u in range(w):
            edge = min(u, w - u - 1, v, h - v - 1)
            amount = 0.38 + 0.20 * (1 - v / max(h - 1, 1))
            amount += (((u * 7 + v * 11) % 13) - 6) / 95
            if edge == 0:
                amount = 0.80 if v == 0 or u == 0 else 0.12
            if material in ('glass', 'elixir'):
                amount += 0.20 if u == 2 and 1 < v < h - 2 else 0
                amount += 0.12 if u + v == w - 2 else 0
            if material == 'leather' and v % 4 == 1 and u in (1, w - 2):
                amount = 0.76
            if material == 'cork' and (u * 3 + v) % 7 == 0:
                amount -= .22
            if face == 'top':
                amount += .13
            amount = max(0, min(1, amount))
            color = tuple(round(a + (b - a) * amount) for a, b in zip(dark, light))
            draw.point((x + u, y + v), fill=color + (255,))
    cursor[0] += w + 1
    cursor[2] = max(cursor[2], h)
    cache[key] = x, y
    return x, y


def cube(name, center, size, material, angle=0):
    w, h, d = size
    layout = {}
    for face, (fw, fh) in {'front': (w, h), 'back': (w, h), 'left': (d, h),
                           'right': (d, h), 'top': (w, d), 'bottom': (w, d)}.items():
        x, y = patch(material, fw, fh, face)
        layout[face] = {'offset': {'x': x, 'y': y}, 'mirror': {'x': False, 'y': False}, 'angle': 0}
    node = {'id': str(len(boxes) + 3), 'name': name,
            'position': dict(zip('xyz', center)),
            'orientation': {'x': 0, 'y': 0, 'z': round(math.sin(math.radians(angle) / 2), 6), 'w': round(math.cos(math.radians(angle) / 2), 6)},
            'shape': {'type': 'box', 'offset': {'x': 0, 'y': 0, 'z': 0},
                      'stretch': {'x': 1, 'y': 1, 'z': 1}, 'settings': {'isPiece': False, 'size': dict(zip('xyz', size))},
                      'textureLayout': layout, 'unwrapMode': 'custom', 'visible': True, 'doubleSided': False, 'shadingMode': 'flat'}}
    boxes.append((node, center, size, angle))


# A flattened field flask, with a broad violet viewing window and protected corners.
cube('Faceted glass body', (0, 0, 0), (12, 12, 8), 'glass')
cube('Left glass bevel', (-5.2, 0, 0), (4, 10, 8), 'glass', -12)
cube('Right glass bevel', (5.2, 0, 0), (4, 10, 8), 'glass', 12)
cube('Elixir front window', (0, -.4, 4.05), (10, 9, 1), 'elixir')
cube('Elixir back window', (0, -.4, -4.05), (10, 9, 1), 'elixir')
cube('Brass base', (0, -6.3, 0), (12, 2, 9), 'brass')
cube('Left protective leather', (-6.25, -.3, 0), (2, 11, 9), 'leather', -10)
cube('Right protective leather', (6.25, -.3, 0), (2, 11, 9), 'leather', 10)
cube('Left brass shoulder', (-3.6, 5.6, 0), (7, 2, 9), 'brass', 27)
cube('Right brass shoulder', (3.6, 5.6, 0), (7, 2, 9), 'brass', -27)
cube('Glass throat', (0, 8.0, 0), (5, 4, 5), 'glass')
cube('Leather neck wrap', (0, 8.3, 0), (6, 2, 6), 'leather')
cube('Brass lip', (0, 10.1, 0), (7, 1, 7), 'brass')
cube('Cork stopper', (0, 11.8, 0), (5, 3, 5), 'cork')
cube('Stopper cap', (0, 13.4, 0), (6, 1, 6), 'brass')
cube('Front alchemist seal', (0, 1, 4.85), (4, 4, 1), 'brass', 45)
cube('Front inset jade', (0, 1, 5.40), (2, 2, 1), 'gem', 45)
cube('Back alchemist seal', (0, 1, -4.85), (4, 4, 1), 'brass', 45)
cube('Back inset jade', (0, 1, -5.40), (2, 2, 1), 'gem', 45)
for i in range(5):
    cube('Capacity mark ' + str(i + 1), ((i - 2) * 2, -4.3, 4.8), (1, 1, 1), 'mark')


def compact_atlas():
    """Deduplicate matching faces and choose the smallest power-of-two UV sheet."""
    global atlas
    tiles = sorted(cache.items(), key=lambda pair: (-pair[0][2], -pair[0][1]))
    candidates = []
    for width in (16, 32, 64, 128, 256):
        x = y = row_h = 0
        positions = {}
        for (_, w, h, _), old in tiles:
            if w > width:
                break
            if x + w > width:
                x = 0; y += row_h; row_h = 0
            positions[old] = (x, y)
            x += w; row_h = max(row_h, h)
        else:
            height = 1 << max(0, (y + row_h - 1).bit_length())
            candidates.append((width * height, max(width, height), width, height, positions))
    _, _, width, height, positions = min(candidates, key=lambda c: c[:4])
    packed = Image.new('RGBA', (width, height))
    for (_, w, h, _), old in tiles:
        x, y = old
        packed.paste(atlas.crop((x, y, x + w, y + h)), positions[old])
    for node, *_ in boxes:
        for face in node['shape']['textureLayout'].values():
            off = face['offset']
            x, y = positions[(off['x'], off['y'])]
            face['offset'] = {'x': x, 'y': y}
    atlas = packed


compact_atlas()


def group(node_id, name, position, children):
    return {'id': node_id, 'name': name, 'position': dict(zip('xyz', position)),
            'orientation': {'x': 0, 'y': 0, 'z': 0, 'w': 1},
            'shape': {'type': 'none', 'offset': {'x': 0, 'y': 0, 'z': 0}, 'stretch': {'x': 1, 'y': 1, 'z': 1},
                      'settings': {'isPiece': True}, 'textureLayout': {}, 'unwrapMode': 'custom', 'visible': True,
                      'doubleSided': False, 'shadingMode': 'flat'}, 'children': children}


model = {'nodes': [group('1', 'R-Attachment', (-4, 4, -5),
                 [group('2', 'Origin_Item', (4, 3, 5), [b[0] for b in boxes])])], 'format': 'prop', 'lod': 'auto'}
(DEST / 'Alchemy_Flask.blockymodel').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
atlas.save(DEST / 'Alchemy_Flask.png')

# A regular Blockbench project also carries its atlas, making visual review a single open.
elements = []
for node, center, size, angle in boxes:
    faces = {}
    for bbface, hyface, dims in [('north', 'back', (size[0], size[1])), ('south', 'front', (size[0], size[1])),
                               ('west', 'left', (size[2], size[1])), ('east', 'right', (size[2], size[1])),
                               ('up', 'top', (size[0], size[2])), ('down', 'bottom', (size[0], size[2]))]:
        off = node['shape']['textureLayout'][hyface]['offset']
        faces[bbface] = {'uv': [off['x'], off['y'], off['x'] + dims[0], off['y'] + dims[1]], 'texture': 0}
    elements.append({'name': node['name'], 'from': [c - s / 2 for c, s in zip(center, size)],
                     'to': [c + s / 2 for c, s in zip(center, size)], 'origin': list(center), 'rotation': [0, 0, angle],
                     'faces': faces, 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, node['name'])), 'type': 'cube'})
bb = {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False}, 'name': 'Alchemy Flask',
      'resolution': {'width': atlas.width, 'height': atlas.height}, 'elements': elements, 'outliner': [e['uuid'] for e in elements],
      'textures': [{'path': str(DEST / 'Alchemy_Flask.png'), 'name': 'Alchemy_Flask.png', 'id': '0', 'width': atlas.width, 'height': atlas.height,
                    'source': 'data:image/png;base64,' + base64.b64encode((DEST / 'Alchemy_Flask.png').read_bytes()).decode()}]}
(PREVIEW / 'Alchemy_Flask.bbmodel').write_text(json.dumps(bb, indent=2) + '\n', encoding='utf-8')


def render(size):
    """Rasterize the authored UV faces; the icon matches the actual prop geometry."""
    import numpy as np
    yaw, pitch = math.radians(-26), math.radians(16)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    def project(p):
        x, y, z = p
        xx, zz = x * cy + z * sy, -x * sy + z * cy
        return np.array([xx, -(y * cp - zz * sp), y * sp + zz * cp])
    faces = []
    for node, center, dims, angle in boxes:
        w, h, d = [v / 2 for v in dims]
        raw = {'front': [(-w,h,d),(w,h,d),(w,-h,d),(-w,-h,d)],
               'back': [(w,h,-d),(-w,h,-d),(-w,-h,-d),(w,-h,-d)],
               'right': [(w,h,d),(w,h,-d),(w,-h,-d),(w,-h,d)],
               'left': [(-w,h,-d),(-w,h,d),(-w,-h,d),(-w,-h,-d)],
               'top': [(-w,h,-d),(w,h,-d),(w,h,d),(-w,h,d)],
               'bottom': [(-w,-h,d),(w,-h,d),(w,-h,-d),(-w,-h,-d)]}
        ca, sa = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        for name, verts in raw.items():
            points = np.array([project((x * ca-y * sa+center[0], x * sa+y * ca+center[1], z+center[2])) for x,y,z in verts])
            fw, fh = (dims[0], dims[2]) if name in ('top', 'bottom') else ((dims[2], dims[1]) if name in ('left','right') else dims[:2])
            uv = node['shape']['textureLayout'][name]['offset']
            faces.append((points, np.array([[0,0],[fw,0],[fw,fh],[0,fh]]) + [uv['x'],uv['y']], name))
    allp = np.concatenate([f[0] for f in faces])
    low, high = allp[:,:2].min(0), allp[:,:2].max(0)
    scale = (size - 12) / max(high - low)
    center = (low + high) / 2
    pixels = np.zeros((size,size,4), dtype=np.uint8)
    zbuffer = np.full((size,size), -np.inf)
    tex = np.array(atlas)
    for pts, uv, name in faces:
        points = pts.copy(); points[:,:2] = (points[:,:2] - center) * scale + size/2
        for ids in ((0,1,2),(0,2,3)):
            p = points[list(ids)]; t = uv[list(ids)]
            xmin,ymin = np.maximum(0,np.floor(p[:,:2].min(0))).astype(int)
            xmax,ymax = np.minimum(size-1,np.ceil(p[:,:2].max(0))).astype(int)
            x,y = np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
            den=(p[1,1]-p[2,1])*(p[0,0]-p[2,0])+(p[2,0]-p[1,0])*(p[0,1]-p[2,1])
            if abs(den)<1e-6: continue
            a=((p[1,1]-p[2,1])*(x-p[2,0])+(p[2,0]-p[1,0])*(y-p[2,1]))/den
            b=((p[2,1]-p[0,1])*(x-p[2,0])+(p[0,0]-p[2,0])*(y-p[2,1]))/den
            c=1-a-b; z=a*p[0,2]+b*p[1,2]+c*p[2,2]
            view=zbuffer[ymin:ymax+1,xmin:xmax+1]
            mask=(a>=0)&(b>=0)&(c>=0)&(z>view)
            u=np.clip((a*t[0,0]+b*t[1,0]+c*t[2,0]).astype(int),0,atlas.width-1)
            v=np.clip((a*t[0,1]+b*t[1,1]+c*t[2,1]).astype(int),0,atlas.height-1)
            colors=tex[v,u].copy()
            factor={'front':1.0,'back':.78,'left':.80,'right':.88,'top':1.12,'bottom':.65}[name]
            colors[:,:,:3]=np.clip(colors[:,:,:3].astype(float)*factor,0,255).astype(np.uint8)
            pixels[ymin:ymax+1,xmin:xmax+1][mask]=colors[mask];view[mask]=z[mask]
    return Image.fromarray(pixels)


preview = render(512)
preview.save(PREVIEW / 'Alchemy_Flask.png')
icon = preview.resize((64,64), Image.Resampling.LANCZOS)
icon.save(ROOT / 'src/main/resources/Common/Icons/ItemsGenerated/Aetherhaven_Alchemy_Flask.png')
print(f'Created {len(boxes)} textured parts, {len(cache)} shared UV patches on {atlas.width}x{atlas.height}, custom icon and Blockbench preview.')
