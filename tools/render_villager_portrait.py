"""Render a villager portrait from its actual model attachments and gradient textures.

Two framings:

  python tools/render_villager_portrait.py <model.json> <out.png>
      The plain frontal bust this tool has always drawn.

  python tools/render_villager_portrait.py <model.json> <out.png> --icon [Scale,Yaw,TranslationX,TranslationY]
      The game's own generated model icon, which is how every portrait up to 3.2.1 was made. With no
      numbers the asset's IconProperties are used. The standing values are 0.5,19,0,-110 for townsfolk
      and 0.4,33,0,-107 for the later core villagers. Add --resources <dir> to read another checkout's
      src/main/resources before this one.
"""
import io, json, math, sys, zipfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from villager_prop_preview import geometry_points, rotation_matrix, world_shapes, textured_faces
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
   # The client remaps only exactly grey texels; coloured ones (lenses, hair clips) keep their own colour.
   grey=(a[:,:,0]==a[:,:,1])&(a[:,:,1]==a[:,:,2]);a[grey,:3]=ramp[a[grey,0]];im=Image.fromarray(a)
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
  faces.extend(textured_faces((camera@p.T).T,n['shape'],tex,strict=False))
 size=512;im=Image.new('RGBA',(size,size));draw=ImageDraw.Draw(im)
 top=max(float(p[:,1].max()) for _,p,_,_ in faces)+2
 scale=size/(top-70)
 for _,p,rgb,_ in sorted(faces,key=lambda f:f[0]):
  draw.polygon([(size/2+v[0]*scale,size-(v[1]-70)*scale) for v in p],fill=(*rgb,255))
 output.parent.mkdir(parents=True,exist_ok=True);im.resize((128,128),Image.Resampling.LANCZOS).save(output)
 print(output)


# --------------------------------------------------------------------------------------------------
# The game's generated model icon.
#
# Measured against the 93 portraits shipped in 3.2.1 (silhouette agreement 0.95 to 0.99 on every one
# whose IconProperties are current): an orthographic view, level with the model, turned by
# Rotation[1] degrees, drawn at 4 x Scale pixels per model unit in a 128 pixel picture whose middle is
# the model point (-Translation[0], -Translation[1]). Texels are shown unlit, a texel is cut out only
# at alpha 0, the model stands in its rest pose, and the picture sits half a pixel right and down.
# --------------------------------------------------------------------------------------------------
# The pre-release build is the one this mod targets (the tests read the same file); the release build is the fallback.
INSTALLS = [Path.home() / 'AppData/Roaming/Hytale/install' / channel / 'package/game/latest/Assets.zip'
            for channel in ('pre-release', 'release')]
ASSETS_ZIP = next((candidate for candidate in INSTALLS if candidate.exists()), INSTALLS[0])
ICON_SIZE = 128
TOWNSFOLK_ICON = (0.5, 19.0, 0.0, -110.0)
CORE_VILLAGER_ICON = (0.4, 33.0, 0.0, -107.0)
BOX_FACES = (('left', (2, 3, 1, 0), 'z', 'y'), ('right', (7, 6, 4, 5), 'z', 'y'), ('bottom', (1, 5, 4, 0), 'x', 'z'),
             ('top', (2, 6, 7, 3), 'x', 'z'), ('back', (6, 2, 0, 4), 'x', 'y'), ('front', (3, 7, 5, 1), 'x', 'y'))
QUAD_FACES = (('front', (3, 2, 1, 0), 'x', 'y'),)


class Source:
    """Asset files: this mod first, then the installed game, then the reference checkout.

    The installed game is preferred to the checkout because the checkout can carry local edits
    (its copy of the Blazen Wizard hood has every shape hidden).
    """

    def __init__(self, extra_roots=()):
        self.roots = [Path(r) for r in extra_roots] + [ROOT / 'src/main/resources']
        self.zip = zipfile.ZipFile(ASSETS_ZIP) if ASSETS_ZIP.exists() else None
        self.names = set(self.zip.namelist()) if self.zip else set()

    def read(self, relative):
        for root in self.roots:
            if (root / relative).exists():
                return (root / relative).read_bytes()
        if relative in self.names:
            return self.zip.read(relative)
        return (NATIVE / relative).read_bytes()

    def json(self, relative):
        return json.loads(self.read(relative).decode('utf-8'))

    def image(self, relative):
        return Image.open(io.BytesIO(self.read(relative))).convert('RGBA')

    def model_asset(self, asset_id):
        for root in self.roots:
            for hit in (root / 'Server/Models').rglob(asset_id + '.json'):
                return json.loads(hit.read_text(encoding='utf-8'))
        for name in self.names:
            if name.startswith('Server/Models/') and name.endswith('/' + asset_id + '.json'):
                return self.json(name)
        for hit in (NATIVE / 'Server/Models').rglob(asset_id + '.json'):
            return json.loads(hit.read_text(encoding='utf-8'))
        raise FileNotFoundError('No model asset named ' + asset_id)


def merged_definition(source, model_file):
    """The model asset with its Parent chain folded in: a child field replaces the parent's."""
    chain = [json.loads(Path(model_file).read_text(encoding='utf-8'))]
    while chain[-1].get('Parent'):
        chain.append(source.model_asset(chain[-1]['Parent']))
    merged = {}
    for part in reversed(chain):
        merged.update(part)
    return merged


def tinted_texture(source, part, gradients):
    image = source.image('Common/' + part['Texture'])
    gradient = gradients.get(part.get('GradientSet'), {}).get(part.get('GradientId'))
    if gradient:
        ramp = np.array(source.image('Common/' + gradient['Texture']).convert('RGB').resize((256, 1), Image.Resampling.BILINEAR))[0]
        texels = np.array(image)
        # The client remaps only exactly grey texels; coloured ones (lenses, hair clips) keep their own colour.
        grey = (texels[:, :, 0] == texels[:, :, 1]) & (texels[:, :, 1] == texels[:, :, 2])
        texels[grey, :3] = ramp[texels[grey, 0]]
        image = Image.fromarray(texels)
    return image


def placed_shapes(nodes, bones, rotation, position, anchors=None):
    """Attachment shapes in model space.

    Every shapeless piece node named like a bone sits on that bone, however deep it is nested: a coat
    lists its sleeves under Chest, and each must still follow its own arm. The frames of this
    attachment's own nodes are collected in ``anchors`` so a later attachment can sit on them.
    """
    for node in nodes:
        shape = node.get('shape', {})
        if shape.get('type', 'none') == 'none' and shape.get('settings', {}).get('isPiece') and node['name'] in bones:
            r, p = bones[node['name']]
        else:
            r = rotation @ rotation_matrix(node.get('orientation', {}))
            p = position + rotation @ np.array([node.get('position', {}).get(k, 0) for k in 'xyz'])
            p = p + r @ np.array([shape.get('offset', {}).get(k, 0) for k in 'xyz'])
            if anchors is not None:
                anchors.setdefault(node['name'], (r, p))
            if shape.get('visible', True) and shape.get('type') in ('box', 'quad'):
                yield node, (r @ geometry_points(shape).T).T + p
        yield from placed_shapes(node.get('children', []), bones, r, p, anchors)


def face_texels(shape, side, width, height, texture):
    """The texels one face shows, as rows from its top left. Texels mapped off the sheet are left empty."""
    layout = shape['textureLayout'].get(side)
    if layout is None:
        return None
    angle = math.radians(layout.get('angle', 0))
    turn = np.array([[math.cos(angle), -math.sin(angle)], [math.sin(angle), math.cos(angle)]])
    xs, ys = np.meshgrid(np.arange(width) + .5, np.arange(height) + .5)
    if layout.get('mirror', {}).get('x'):
        xs = -xs
    if layout.get('mirror', {}).get('y'):
        ys = -ys
    uv = np.stack([xs, ys], axis=-1) @ turn.T + np.array([layout['offset']['x'], layout['offset']['y']], dtype=float) + 1e-7
    tx, ty = np.floor(uv[..., 0]).astype(int), np.floor(uv[..., 1]).astype(int)
    sheet = np.array(texture)
    inside = (tx >= 0) & (tx < sheet.shape[1]) & (ty >= 0) & (ty < sheet.shape[0])
    texels = np.zeros((height, width, 4), dtype=np.uint8)
    texels[inside] = sheet[ty[inside], tx[inside]]
    return texels


def icon_faces(source, definition):
    """Every drawn face of the model and its default attachments: (top left, across, down, texels)."""
    gradients = {entry['Id']: entry['Gradients'] for entry in source.json('Cosmetics/CharacterCreator/GradientSets.json')}
    base = source.json('Common/' + definition['Model'])
    bones = frames(base['nodes'])
    parts = [(node, points, tinted_texture(source, definition, gradients)) for node, points in world_shapes(base['nodes'])]
    for attachment in definition.get('DefaultAttachments', []):
        texture = tinted_texture(source, attachment, gradients)
        nodes = source.json('Common/' + attachment['Model'])['nodes']
        anchors = {}
        parts.extend((node, points, texture) for node, points in placed_shapes(nodes, bones, np.eye(3), np.zeros(3), anchors))
        # Ear models carry the anchors that earrings hang from, so what one attachment defines the next may use.
        for name, frame in anchors.items():
            bones.setdefault(name, frame)
    faces = []
    for node, points, texture in parts:
        shape = node['shape']
        size = shape['settings']['size']
        for side, corners, across, down in (QUAD_FACES if shape['type'] == 'quad' else BOX_FACES):
            width, height = int(size[across]), int(size.get(down, 1))
            if width < 1 or height < 1:
                continue
            texels = face_texels(shape, side, width, height, texture)
            if texels is None or not (texels[..., 3] > 0).any():
                continue
            quad = points[list(corners)]
            faces.append((quad[0], quad[1] - quad[0], quad[3] - quad[0], texels))
    return faces, bones


def icon_camera(yaw_degrees):
    yaw = math.radians(yaw_degrees)
    return np.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])


def icon_point(point, icon, size=ICON_SIZE):
    """Where a model point lands in the picture, in pixels from the top left."""
    scale, yaw, tx, ty = icon
    seen = icon_camera(yaw) @ np.asarray(point, dtype=float)
    per_unit = 4 * scale * size / 128
    return size / 2 + (seen[0] + tx) * per_unit + .5, size / 2 - (seen[1] + ty) * per_unit + .5


def rasterise(faces, icon, size=ICON_SIZE, supersample=4):
    """Depth buffered picture: nearest texel, unlit, edges softened by drawing larger and averaging."""
    scale, yaw, tx, ty = icon
    camera = icon_camera(yaw)
    extent = size * supersample
    per_unit = 4 * scale * extent / 128
    colour = np.zeros((extent, extent, 4), dtype=np.uint8)
    depth = np.full((extent, extent), -1e9)
    for origin, across, down, texels in faces:
        origin, across, down = camera @ origin, camera @ across, camera @ down
        o = np.array([extent / 2 + (origin[0] + tx) * per_unit, extent / 2 - (origin[1] + ty) * per_unit]) + .5 * supersample
        a = np.array([across[0], -across[1]]) * per_unit
        b = np.array([down[0], -down[1]]) * per_unit
        det = a[0] * b[1] - a[1] * b[0]
        if abs(det) < 1e-6:
            continue  # edge on
        corners = np.array([o, o + a, o + b, o + a + b])
        x0, y0 = np.maximum(np.floor(corners.min(axis=0)).astype(int), 0)
        x1, y1 = np.minimum(np.ceil(corners.max(axis=0)).astype(int), extent)
        if x0 >= x1 or y0 >= y1:
            continue
        px, py = np.meshgrid(np.arange(x0, x1) + .5 - o[0], np.arange(y0, y1) + .5 - o[1])
        u = (px * b[1] - py * b[0]) / det
        v = (py * a[0] - px * a[1]) / det
        height, width = texels.shape[:2]
        texel = texels[np.clip((v * height).astype(int), 0, height - 1), np.clip((u * width).astype(int), 0, width - 1)]
        z = origin[2] + u * across[2] + v * down[2]
        window = depth[y0:y1, x0:x1]
        draw = (u >= 0) & (u < 1) & (v >= 0) & (v < 1) & (texel[..., 3] > 0) & (z > window)
        if not draw.any():
            continue
        opaque = np.concatenate([texel[..., :3], np.full(texel.shape[:2] + (1,), 255, dtype=np.uint8)], axis=-1)
        colour[y0:y1, x0:x1][draw] = opaque[draw]
        window[draw] = z[draw]
    picture = Image.fromarray(colour)
    return picture if supersample == 1 else picture.resize((size, size), Image.Resampling.BOX)


def render_icon(model_file, output, icon=None, extra_roots=()):
    source = Source(extra_roots)
    definition = merged_definition(source, model_file)
    if icon is None:
        properties = definition.get('IconProperties')
        if not properties:
            raise ValueError(f'{model_file} has no IconProperties; give --icon Scale,Yaw,TranslationX,TranslationY')
        icon = (properties['Scale'], properties['Rotation'][1], properties['Translation'][0], properties['Translation'][1])
    faces, _ = icon_faces(source, definition)
    picture = rasterise(faces, icon)
    output.parent.mkdir(parents=True, exist_ok=True)
    picture.save(output)
    print(output, 'icon', icon)
    return picture


def main(arguments):
    extra_roots, icon, use_icon, positional = [], None, False, []
    arguments = list(arguments)
    while arguments:
        argument = arguments.pop(0)
        if argument == '--resources':
            extra_roots.append(arguments.pop(0))
        elif argument == '--icon':
            use_icon = True
            if arguments and ',' in arguments[0]:
                icon = tuple(float(value) for value in arguments.pop(0).split(','))
                if len(icon) != 4:
                    raise ValueError('--icon takes Scale,Yaw,TranslationX,TranslationY')
        else:
            positional.append(argument)
    model_file, output = Path(positional[0]), Path(positional[1])
    if use_icon:
        render_icon(model_file, output, icon, extra_roots)
    else:
        render(model_file, output)


if __name__ == '__main__':
    main(sys.argv[1:])
