"""Original villager animation, bubble icon and sound-event source.

Run with Python, numpy, Pillow and soundfile. No sampled game audio or animations.
Hytale's shipped Player rig and codecs are format references only. Angles below
are authored local Euler deltas; the exporter converts them to quaternions.
"""
from pathlib import Path
import json
import math
import sys
import shutil
import copy
from collections import Counter
from villager_life_personality_data import THOUGHTS, SOCIAL, style, idle
from villager_life_faces import DURATIONS, faces
from villager_life_ik import ArmIK
import villager_life_props as props
from villager_blockyanim import complete_channels
from villager_life_bubbles import fit_icon, anchor_layer, BUBBLE_SCALE, CANVAS_SIZE

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'build/life-tools'))
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
import soundfile as sf

RES = ROOT / 'src/main/resources'
COMMON = RES / 'Common'
PREFIX = 'Aetherhaven_Life_'


def write_json(path, obj):
    if path.suffix == '.blockyanim':
        complete_channels(obj)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n', encoding='utf-8')


def quat(angles):
    x, y, z = [math.radians(a) / 2 for a in angles]
    sx, sy, sz, cx, cy, cz = math.sin(x), math.sin(y), math.sin(z), math.cos(x), math.cos(y), math.cos(z)
    return dict(zip('xyzw', [round(v, 7) for v in (
        sx*cy*cz + cx*sy*sz, cx*sy*cz - sx*cy*sz,
        cx*cy*sz + sx*sy*cz, cx*cy*cz - sx*sy*sz)]))


# Each gesture has an anticipation, two expressive poses, a settle and a return.
# Arms rotate forward around X; forearm bends bring hands toward chest/face.
POSES = {
    'Mix': ({'Head': (16,0,0)}, {'Head': (14,0,0)}, 420),
    'ShowItem': ({'R-Arm': (-50, 0, -16), 'R-Forearm': (-70, 0, 0), 'L-Arm': (-25, 0, 18), 'L-Forearm': (-55, 0, -10), 'Head': (9, -8, 3)},
                 {'R-Arm': (-65, -12, -18), 'R-Forearm': (-48, 0, 0), 'L-Arm': (-35, -8, 22), 'Head': (-3, 7, 0), 'Chest': (0, -6, 0)}, 96),
    'Greet': ({'Head': (0, -5, -2)}, {}, 72),  # Authored arm arc: villager_life_greeting.bake.
    'Explain': ({'R-Arm': (-42, -12, -24), 'R-Forearm': (-48, 0, 15), 'L-Arm': (-24, 0, 18), 'Head': (-5, 10, 4)}, {'R-Arm': (-60, 16, -38), 'L-Forearm': (-55, 0, -15), 'Chest': (0, -8, 0)}, 78),
    'Story': ({'R-Arm': (-65, 0, -45), 'L-Arm': (-50, 0, 35), 'R-Forearm': (-35, 0, 0), 'L-Forearm': (-35, 0, 0), 'Chest': (-7, 8, 0)}, {'R-Arm': (-90, 0, -18), 'L-Arm': (-30, 0, 50), 'Head': (-12, -12, 7)}, 84),
    'Question': ({'R-Arm': (-35, 0, -28), 'L-Arm': (-35, 0, 28), 'R-Forearm': (-65, 0, 30), 'L-Forearm': (-65, 0, -30), 'Head': (4, 0, 14)}, {'Head': (-6, 8, 18), 'Chest': (-4, 0, 0)}, 72),
    'Agree': ({'Head': (16, 0, 0), 'R-Arm': (-20, 0, -10), 'R-Forearm': (-60, 0, 0)}, {'Head': (-8, 0, -3)}, 60),
    'Laugh': ({'Chest': (-14, 0, -3), 'Head': (-12, 0, 4), 'L-Arm': (-15, -25, 25), 'R-Arm': (-15, 25, -25), 'L-Forearm': (-85, 0, 0), 'R-Forearm': (-85, 0, 0)}, {'Chest': (-9, 0, 3), 'Head': (-8, 0, -4)}, 72),
    'Surprise': ({'R-Arm': (-90, 0, -25), 'L-Arm': (-90, 0, 25), 'R-Forearm': (-75, 0, 0), 'L-Forearm': (-75, 0, 0), 'Head': (-16, 0, 0)}, {'Head': (0, 7, 0), 'Chest': (-7, 0, 0)}, 60),
    'Disagree': ({'Head': (3, 18, 0), 'R-Arm': (-40, 0, -20), 'R-Forearm': (-50, 0, 0)}, {'Head': (3, -18, 0), 'R-Hand': (0, 0, -20)}, 66),
    'Hungry': ({'Chest': (14, 0, 0), 'Head': (16, 0, -8), 'L-Arm': (-28, -28, 16), 'R-Arm': (-28, 28, -16), 'L-Forearm': (-82, 0, -48), 'R-Forearm': (-82, 0, 48)}, {'Chest': (20, 0, -4), 'Head': (22, 0, 4), 'L-Hand': (0, 12, 0)}, 90),
    'Sleepy': ({'R-Arm': (-72, 0, -12), 'R-Forearm': (-108, 0, 14), 'Head': (-18, 0, 0), 'Chest': (-8, 0, 0)}, {'Head': (18, 0, 9), 'Chest': (8, 0, 0), 'L-Arm': (8, 0, 9)}, 96),
    'Bored': ({'Chest': (9, 0, 0), 'Head': (12, 0, 10), 'R-Arm': (4, 0, -8)}, {'Head': (5, -20, -7), 'Chest': (12, 0, 4), 'L-Hand': (0, 0, 12)}, 84),
    'LookAround': ({'Head': (-4, 25, 3), 'Chest': (0, 6, 0)}, {'Head': (2, -24, -5), 'Chest': (0, -5, 0)}, 90),
    'Stretch': ({'R-Arm': (-100, 0, -80), 'L-Arm': (-100, 0, 80), 'R-Forearm': (-20, 0, 0), 'L-Forearm': (-20, 0, 0), 'Belly': (-7, 0, 0), 'Chest': (-18, 0, 0), 'Head': (-10, 0, 0)}, {'Belly': (-5, 0, 0), 'Chest': (-14, 0, 5), 'Head': (0, 0, 10)}, 96),
    'Fidget': ({'R-Arm': (-25, 0, -8), 'R-Forearm': (-88, 0, 12), 'L-Arm': (-32, 0, 12), 'L-Forearm': (-75, 0, -24), 'Head': (12, 0, 0)}, {'R-Hand': (15, 0, -12), 'Head': (6, 8, 0)}, 78),
    'Read': ({'R-Arm': (-30, 0, -10), 'L-Arm': (-30, 0, 10), 'R-Forearm': (-65, 0, 12), 'L-Forearm': (-65, 0, -12), 'Head': (18, 0, 0)}, {'R-Hand': (0, -25, 12), 'Head': (16, -8, 0)}, 84),
    'Craft': ({'R-Arm': (-45, 0, -8), 'L-Arm': (-45, 0, 8), 'R-Forearm': (-40, 0, 0), 'L-Forearm': (-50, 0, 0), 'Head': (20, 0, 0)}, {'R-Arm': (-60, 0, -5), 'L-Hand': (0, 0, -15), 'Chest': (5, 0, 0)}, 72),
    'Sweep': ({'R-Arm': (-38, -18, -12), 'L-Arm': (-55, 0, 15), 'R-Forearm': (-42, 0, 0), 'L-Forearm': (-45, 0, 0), 'Chest': (8, -12, 0)}, {'Chest': (8, 14, 0), 'R-Arm': (-55, 15, -14), 'Head': (14, 8, 0)}, 72),
    'Inspect': ({'R-Arm': (-55, 0, -12), 'R-Forearm': (-85, 0, 0), 'Head': (12, -8, 7), 'Chest': (6, 0, 0)}, {'R-Hand': (0, 30, 0), 'Head': (8, 6, -5)}, 78),
    'Tend': ({'Chest': (20, 0, 0), 'Head': (22, 0, 0), 'R-Arm': (-45, 0, -14), 'R-Forearm': (-25, 0, 0), 'L-Arm': (-30, 0, 18)}, {'R-Arm': (-65, -15, -10), 'Chest': (24, 8, 0)}, 78),
}


def animations():
    assets = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets'
    ik = ArmIK(assets, quat)
    model_path = RES / 'Server/Models/Human/Aetherhaven_Human.json'
    model = json.loads(model_path.read_text())
    actions = {}
    for name, (a, b, _) in POSES.items():
        # Hytale BlockyAnimationCache.FRAMES_PER_SECOND = 60, not 30.
        duration = round(DURATIONS[name] * 60)
        b = a | b
        tracks = {}
        for bone in a.keys() | b.keys():
            first, second = a.get(bone, (0, 0, 0)), b.get(bone, (0, 0, 0))
            poses = [(0, (0, 0, 0)), (int(duration*.10), tuple(-v*.06 for v in first)),
                     (int(duration*.30), first), (int(duration*.62), second),
                     (int(duration*.82), second), (duration, (0, 0, 0))]
            if name in ('Question','Stretch'):
                poses[1]=(int(duration*.10),(0,0,0))
            if name in ('Greet', 'Laugh', 'Agree', 'Disagree', 'Craft', 'Sweep'):
                poses = poses[:3] + [(int(duration*.45), second), (int(duration*.61), first),
                                     (int(duration*.77), second), (duration, (0, 0, 0))]
            tracks[bone] = {'orientation': [{'time': t, 'delta': quat(p), 'interpolationType': 'smooth'} for t, p in poses]}
        tracks = ik.bake(name, tracks, duration, a, b)
        tracks = props.bake(name, tracks, duration, ik)
        if name == 'Greet':
            from villager_life_greeting import bake
            tracks = bake(ik, duration)
        elif name == 'Mix':
            from villager_life_mixing import bake
            tracks = bake(ik, duration)
        else:
            from villager_body_safety import correct
            tracks = correct(ik, tracks, duration, name)
        path = f'Characters/Animations/Aetherhaven/Life/{name}.blockyanim'
        write_json(COMMON / path, {'formatVersion': 1, 'duration': duration, 'holdLastKeyframe': name in ('Mix','Sweep'), 'nodeAnimations': tracks})
        model['AnimationSets'][PREFIX + name] = {'Animations': [{'Animation': path, 'Looping': name in ('Mix','Sweep'), 'BlendingDuration': .35}]}
        actions[name] = {'ThirdPerson': path, 'ThirdPersonMoving': path,
                         'ThirdPersonFace': f'Characters/Animations/Aetherhaven/Life/Faces/{name}.blockyanim',
                         'Speed': 1, 'Looping': name in ('Mix','Sweep'), 'BlendingDuration': .35}
    write_json(model_path, model)
    write_json(RES/'Server/Item/Animations/Aetherhaven_Life_Actions.json', {'Animations': actions, 'WiggleWeights': {}})
    write_json(RES/'defaults/villager_life_timing.json', {name: round(seconds*1000) for name, seconds in DURATIONS.items()})
    faces(RES, quat, write_json)
    props.generate(RES, ik, quat, write_json)
    from villager_life_loops import generate_loops
    generate_loops(RES, write_json)
    write_json(ROOT/'build/villager-life-preview/ik-report.json',ik.report)


# Small hand-drawn vector primitives, rasterized at 4x for clean particle edges.
INK = '#403b57'


def icon(name):
    im = Image.new('RGBA', (512, 512))
    d = ImageDraw.Draw(im)
    if name in ('Speech', 'Thought'):
        mask = Image.new('L', im.size)
        m = ImageDraw.Draw(mask)
        if name == 'Speech':
            m.polygon([(52*4, 78*4), (64*4, 110*4), (76*4, 78*4)], fill=255)
            m.rounded_rectangle((11*4, 15*4, 117*4, 89*4), radius=24*4, fill=255)
        else:
            for box in [(12, 28, 64, 77), (29, 12, 85, 70), (61, 19, 115, 75), (26, 38, 96, 90),
                        (56, 89, 71, 101), (60, 102, 68, 110)]:
                m.ellipse(tuple(v*4 for v in box), fill=255)
        shifted = Image.new('L', im.size)
        shifted.paste(mask, (0, 13*4))
        mask = shifted
        im.paste(INK, mask=mask.filter(ImageFilter.MaxFilter(21)))
        im.paste('#fff8ea', mask=mask)
        return im.resize((128, 128), Image.Resampling.LANCZOS)
    def ellipse(box, fill, outline=INK, width=3):
        d.ellipse(tuple(int(v*4) for v in box), fill, outline, width*4)
    def line(points, fill=INK, width=4):
        d.line([(int(x*4), int(y*4)) for x, y in points], fill, width*4, joint='curve')
    def poly(points, fill, outline=INK):
        d.polygon([(int(x*4), int(y*4)) for x, y in points], fill, outline, 3*4)
    def rect(box, fill, radius=8):
        d.rounded_rectangle(tuple(int(v*4) for v in box), radius*4, fill, INK, 3*4)
    if name == 'Speech':
        poly([(43, 80), (32, 108), (65, 86)], '#fff8ea')
        rect((9, 13, 119, 91), '#fff8ea', 25)
    elif name == 'Thought':
        for box in [(31, 99, 41, 109), (39, 84, 57, 101)]: ellipse(box, '#fff8ea', width=2)
        for box in [(10, 26, 62, 78), (29, 10, 85, 66), (63, 17, 116, 75), (27, 40, 94, 91)]: ellipse(box, '#fff8ea')
        ellipse((25, 24, 103, 78), '#fff8ea', '#fff8ea', 1)
    elif name == 'Food':
        ellipse((35, 65, 93, 79), '#b5d7d4')
        rect((39, 42, 89, 68), '#e6a44f', 11)
        for x in (50, 63, 76): line([(x, 46), (x-3, 56)], '#fff1c1', 3)
    elif name == 'Sleep':
        line([(38, 38), (59, 38), (39, 61), (60, 61)], '#9aa6dc', 5)
        line([(68, 53), (85, 53), (68, 72), (85, 72)], '#7679b7', 5)
    elif name == 'Work':
        poly([(46, 71), (72, 36), (80, 42), (54, 78)], '#c48e66')
        poly([(57, 35), (66, 25), (91, 44), (83, 55)], '#a2c0cb')
    elif name == 'Home':
        rect((43, 45, 86, 77), '#f5d093', 3)
        poly([(33, 48), (64, 24), (96, 48)], '#dc7a80')
        rect((59, 59, 71, 77), '#987465', 2)
    elif name == 'Music':
        line([(53, 68), (53, 36), (82, 30), (82, 61)], '#7764ae', 5)
        ellipse((39, 61, 55, 74), '#a294d2', width=2)
        ellipse((68, 55, 84, 68), '#a294d2', width=2)
    elif name == 'Flower':
        line([(65, 54), (65, 80)], '#62a47b', 4)
        ellipse((66, 60, 86, 71), '#87b779', width=2)
        for x, y in [(64, 31), (48, 41), (53, 58), (75, 58), (80, 40)]: ellipse((x-11, y-11, x+11, y+11), '#ec9fad', width=2)
        ellipse((54, 35, 75, 56), '#f5ce76', width=2)
    elif name == 'Rain':
        for box in [(35, 35, 61, 55), (47, 25, 79, 57), (67, 34, 93, 55)]: ellipse(box, '#b1c9dc', width=2)
        for x in (45, 64, 83): line([(x, 64), (x-4, 74)], '#669fce', 3)
    else:
        ellipse((39, 27, 89, 77), '#f6d37e')
        if name == 'Love':
            for x in (51, 76): poly([(x-7, 44), (x-5, 39), (x, 42), (x+5, 39), (x+7, 44), (x, 52)], '#d77386', '#d77386')
        else:
            for x in (53, 76):
                if name == 'Bored': line([(x-4, 44), (x+4, 45)], width=3)
                else: ellipse((x-2, 41, x+2, 47), INK, width=1)
        if name == 'Surprise': ellipse((59, 55, 69, 68), INK, width=1)
        elif name == 'Bored': line([(56, 63), (73, 63)], width=3)
        elif name == 'Disagree': line([(54, 64), (64, 59), (75, 64)], width=3)
        else: line([(53, 57), (57, 63), (65, 66), (74, 63), (78, 57)], width=3)
    return im.resize((128, 128), Image.Resampling.LANCZOS)


def particles():
    names = ['Speech', 'Thought', 'Food', 'Sleep', 'Work', 'Home', 'Music', 'Flower', 'Rain', 'Happy', 'Love', 'Surprise', 'Bored', 'Disagree']
    path = COMMON / 'Particles/Aetherhaven/Life'
    path.mkdir(parents=True, exist_ok=True)
    preview = Image.new('RGBA', (128*7, 160*2), '#8b98ae')
    for i, name in enumerate(names):
        pic = icon(name)
        if name not in ('Speech', 'Thought'):
            pic = fit_icon(pic)
        else:
            pic = anchor_layer(pic)
        pic.save(path / f'{name}.png')
        display = pic.copy() if name in ('Speech', 'Thought') else icon('Thought')
        if name not in ('Speech', 'Thought'): display.alpha_composite(pic, (32, 32))
        display = display.resize((128,128), Image.Resampling.LANCZOS)
        preview.paste(display, ((i % 7)*128, (i//7)*160), display)
        ImageDraw.Draw(preview).text(((i % 7)*128+14, (i//7)*160+132), name, fill='white')
        scale = {axis: {'Min': BUBBLE_SCALE, 'Max': BUBBLE_SCALE} for axis in ('X', 'Y')}
        spawn = {'RenderMode': 'BlendLinear', 'ParticleRotationInfluence': 'BillboardY', 'CameraOffset': -.025 if name not in ('Speech', 'Thought') else 0,
                 'LinearFiltering': True, 'LightInfluence': 0, 'MaxConcurrentParticles': 1,
                 'TotalParticles': {'Min': 1, 'Max': 1}, 'SpawnBurst': True, 'SpawnRate': {'Min': 1, 'Max': 1},
                 'ParticleLifeSpan': {'Min': 2.6, 'Max': 2.6}, 'LifeSpan': .1,
                 'Particle': {'Texture': f'Particles/Aetherhaven/Life/{name}.png', 'FrameSize': {'Width': pic.width, 'Height': pic.height},
                              'ScaleRatioConstraint': 'OneToOne', 'UVOption': 'None',
                              'InitialAnimationFrame': {'Opacity': 1, 'Scale': scale},
                              'Animation': {'0': {'Opacity': 0}, '8': {'Opacity': 1}, '85': {'Opacity': 1}, '100': {'Opacity': 0}}}}
        write_json(RES / f'Server/Particles/Aetherhaven/Life/{PREFIX}{name}.particlespawner', spawn)
    for bubble in ('Speech', 'Thought'):
        for name in names[2:]:
            write_json(RES / f'Server/Particles/Aetherhaven/Life/{PREFIX}{bubble}_{name}.particlesystem',
                       {'CullDistance': 28, 'BoundingRadius': 2, 'LifeSpan': 2.8,
                        'Spawners': [{'SpawnerId': PREFIX+bubble}, {'SpawnerId': PREFIX+name}]})
    out = ROOT / 'build/villager-life-preview'
    out.mkdir(parents=True, exist_ok=True)
    preview.save(out / 'bubbles.png')


def item_thoughts():
    assets = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets'
    available = {}
    # Mod overrides win over base icons. Resolve explicit item Icon paths too, since
    # jewelry and other custom gifts do not always use the item id as a PNG name.
    for base in (assets, RES):
        for p in (base/'Common/Icons/ItemsGenerated').glob('*.png'):
            available[p.stem] = p
        for p in (base/'Server/Item/Items').rglob('*.json'):
            try: data = json.loads(p.read_text(encoding='utf-8-sig'))
            except (ValueError, UnicodeError): continue
            icon_path = data.get('Icon')
            if icon_path and (base/'Common'/icon_path).is_file(): available[p.stem] = base/'Common'/icon_path
    wanted = {item for items in THOUGHTS.values() for item in items if item != 'Music'}
    wanted.update(['Tool_Pickaxe_Iron', 'Tool_Hatchet_Iron', 'Tool_Hammer_Iron', 'Tool_Feedbag',
                   'Food_Bread', 'Food_Pie_Meat', 'Plant_Crop_Carrot', 'Plant_Flower_Blood_Rose',
                   'Rock_Gem_Zephyr', 'Aetherhaven_Gold_Coin'])
    # Named villagers can think about their actual loved gifts, not just job stereotypes.
    for p in (RES/'Server/Aetherhaven/Villagers').glob('*.json'):
        data = json.loads(p.read_text(encoding='utf-8-sig'))
        wanted.update([item for item in data.get('giftLoves', []) if item in available][:4])
    missing = wanted - available.keys()
    if missing: raise ValueError(f'Missing item icons: {sorted(missing)}')
    particle_dir = RES/'Server/Particles/Aetherhaven/Life'
    texture_dir = COMMON/'Particles/Aetherhaven/Life/Items'
    texture_dir.mkdir(parents=True, exist_ok=True)
    template = json.loads((particle_dir/f'{PREFIX}Food.particlespawner').read_text())
    manifest = {}
    sheet = Image.new('RGBA',(128*8,160*math.ceil(len(wanted)/8)),'#8b98ae')
    for index, item in enumerate(sorted(wanted)):
        src = available[item]
        raw = Image.open(src).convert('RGBA')
        centered = texture_dir/'Centered'/(item+'.png')
        centered.parent.mkdir(parents=True, exist_ok=True)
        fit_icon(raw).save(centered)
        spawn = copy.deepcopy(template)
        spawn['Particle']['Texture'] = f'Particles/Aetherhaven/Life/Items/Centered/{item}.png'
        spawn['Particle']['FrameSize'] = {'Width': CANVAS_SIZE, 'Height': CANVAS_SIZE}
        topic = 'Item_'+item
        write_json(particle_dir/f'{PREFIX}{topic}.particlespawner', spawn)
        for bubble in ('Speech','Thought'):
            write_json(particle_dir/f'{PREFIX}{bubble}_{topic}.particlesystem',
                       {'CullDistance': 28,'BoundingRadius': 2,'LifeSpan': 2.8,
                        'Spawners': [{'SpawnerId': PREFIX+bubble},{'SpawnerId': PREFIX+topic}]})
        pack = 'Aetherhaven' if src.is_relative_to(RES) else 'Hytale'
        manifest[item] = {'topic': topic, 'sourcePack': pack, 'sourceIcon': src.relative_to(RES if pack == 'Aetherhaven' else assets).as_posix()}
        panel = icon('Thought')
        raw.thumbnail((60,60),Image.Resampling.LANCZOS)
        panel.alpha_composite(raw,((128-raw.width)//2,24+(60-raw.height)//2))
        x,y = (index%8)*128,(index//8)*160
        sheet.alpha_composite(panel,(x,y))
        label = item.removeprefix('Aetherhaven_').removeprefix('Ingredient_').removeprefix('Plant_')
        ImageDraw.Draw(sheet).text((x+4,y+130),label[:21],fill='white')
    write_json(RES/'defaults/villager_life_items.json',manifest)
    sheet.save(ROOT/'build/villager-life-preview/item-thoughts.png')
    for p in (RES/'Server/Aetherhaven/Personalities').glob('*.json'):
        data = json.loads(p.read_text(encoding='utf-8-sig'))
        trait = data['id']
        data['thoughtItemWeights'] = dict(Counter(THOUGHTS[trait]))
        data['socialEmoteWeights'] = SOCIAL[style(trait)]
        data['idleEmoteWeights'] = idle(trait)
        write_json(p,data)
    print(f'Copied {len(manifest)} item icons with source provenance and generated their speech/thought layers.')


# User voice assets are imported separately; geometry export must not replace them.
def sounds():
    from import_villager_voices import events
    manifest = json.loads((ROOT/'tools/villager_life_voice_manifest.json').read_text())
    if 'profileOrder' not in manifest:
        raise RuntimeError('Import the user recordings with import_villager_voices.py first')
    events(manifest['clips'])


if __name__ == '__main__':
    animations()
    particles()
    item_thoughts()
    sounds()
    print(f'Generated {len(POSES)} animations, layered bubbles and user voice events. Recordings preserved.')
