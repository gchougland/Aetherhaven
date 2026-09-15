"""Retarget authored facial acting to native townsfolk rigs and mute their idle vocals.

Reads the local Hytale assets as rig references; never edits vanilla assets. Jaw
angles are additive about the native hinge, following the existing phonetic cues.
"""
import copy
import json
import math

RIGS = {
    'Trork': (['Grunk_Stonebelly'], 20),
    'Feran': (['Saffra_Dunear', 'Zephyr_Sandtail'], 26),
    'Klops': (['Nell_Clinkjar', 'Pippin_Geargrin'], 18),
    'Slothian': (['Haku_Mistclaw', 'Momo_Canopy'], 24),
    'Skeleton': (['Rattle_Morrow'], 22),
    'Kweebec': (['Briar_Mosscap'], 0),
    'KweebecSharp': (['Tumble_Reedwhistle'], 0),
    'Outlander': (['Vask_Hollowmark'], 0),
}
# Movement grunts are separate from FootstepIntervals. Keep skeleton bone
# clatter, injury/death, combat reactions, and all material footstep effects.
AMBIENT_VOCALS = {
    'SFX_Klops_Idle', 'SFX_Klops_Run', 'SFX_Trork_Run', 'SFX_Trork_Sleep',
    'SFX_Skeleton_Search_2',
}
# Relative jaw aperture for each player mouth used by the authored expressions.
APERTURE = {
    (0,0):0, (20,0):.16, (40,0):.1, (120,0):.18, (140,0):.58,
    (160,0):1, (180,0):.7, (0,-10):.12, (20,-10):.18, (40,-10):.12,
    (80,-10):.4, (120,-10):.25, (140,-10):.65, (160,-10):1,
    (180,-10):.7, (181,-10):.7, (200,-10):.95, (0,-20):0,
    (40,-20):.1, (160,-20):.8, (160,-30):.55, (180,-30):.4,
    (220,-30):.65, (0,-50):0, (60,-50):.1,
}


def walk(node):
    yield node
    for child in node.get('children', []):
        yield from walk(child)


def load_references(res):
    res = res.resolve()
    base = res.parents[2].parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets'
    models = {p.stem: json.loads(p.read_text()) for p in (base/'Server/Models').rglob('*.json')}
    models.update({p.stem: json.loads(p.read_text()) for p in (res/'Server/Models').rglob('*.json')})

    def resolve(name):
        model = models[name]
        effective = resolve(model['Parent']) if model.get('Parent') else {}
        animations = {**effective.get('AnimationSets', {}), **model.get('AnimationSets', {})}
        effective.update(copy.deepcopy(model))
        effective['AnimationSets'] = animations
        return effective

    def read_common(source):
        path = res/'Common'/source
        return json.loads((path if path.exists() else base/'Common'/source).read_text())

    def bones(model):
        result = {}
        for attachment in [{'Model': model['Model']}, *model.get('DefaultAttachments', [])]:
            for root in read_common(attachment['Model'])['nodes']:
                for node in walk(root):
                    # Attachment anchor duplicates must not replace actual geometry.
                    if node['name'] not in result or node.get('shape', {}).get('type') in ('box', 'quad'):
                        result[node['name']] = node
        return result
    return resolve, read_common, bones


def jaw_track(frames, degrees):
    """Small eased transitions, with full closure held through silent cue gaps."""
    keys = {}
    previous_time, previous_value = 0, 0
    for frame in frames:
        time = frame['time']
        value = APERTURE[tuple(frame['delta'][axis] for axis in ('x', 'y'))]
        if time > previous_time:
            keys[max(previous_time, time - 3)] = previous_value
        keys[time] = value
        previous_time, previous_value = time, value
    return [{'time': time, 'delta': {'x': math.sin(math.radians(degrees*value)/2),
             'y': 0, 'z': 0, 'w': math.cos(math.radians(degrees*value)/2)},
             'interpolationType': 'smooth'} for time, value in sorted(keys.items())]


def retarget(data, rig, bones, degrees):
    result = copy.deepcopy(data)
    result['nodeAnimations'] = nodes = {}
    for name, original in data.get('nodeAnimations', {}).items():
        if name == 'Mouth' and degrees:
            nodes['Jaw'] = {'orientation': jaw_track(original.get('shapeUvOffset', []), degrees)}
            continue
        target = name
        if rig == 'Klops':
            if name.startswith('R-'):
                continue  # One eye: never apply both sides to the same node.
            target = {'L-Eye': 'Eye', 'L-Eyelid': 'Eyelid-Top',
                      'L-Eyelid-Bot': 'Eyelid-Bot', 'L-Eyebrow': 'Eyebrow'}.get(name, name)
        elif rig == 'KweebecSharp':
            target = {'L-Eyebrow': 'Eyebrow_L', 'R-Eyebrow': 'Eyebrow_R'}.get(name, name)
        if target not in bones:
            continue
        # Face layers must not take ownership of body/root transforms.
        if not any(part in target for part in ('Eye', 'Mouth')):
            continue
        track = copy.deepcopy(original)
        if target != 'Mouth':
            track['shapeUvOffset'] = []
            for frame in track.get('position', []):
                frame['delta'] = {axis: value*.6 for axis, value in frame['delta'].items()}
            if rig == 'Klops' and 'Eyelid' in target:
                # Klops lids start at .7/.75 scale, unlike the .1 humanoid lids.
                for frame in track.get('shapeStretch', []):
                    frame['delta']['y'] = 1 + (frame['delta']['y']-1)*.14
        nodes[target] = track
    return result


def generate(res, write):
    resolve, read_common, bones_for = load_references(res)
    human = json.loads((res/'Server/Models/Human/Aetherhaven_Human.json').read_text())
    # Only face bindings, never native body animations from the Human parent.
    face_bindings = {name: binding for name, binding in human['AnimationSets'].items()
                     if name.startswith(('Aetherhaven_Life_Face_', 'Aetherhaven_Life_Lip_'))
                     or name in ('Talk', 'Talk2', 'Talk3', 'Talk4', 'Talk5', 'Grin', 'Frown')}
    report = {}
    for rig, (names, degrees) in RIGS.items():
        effective = resolve(names[0])
        bones = bones_for(effective)
        generated = {}

        def adapt(source):
            if source not in generated:
                data = retarget(read_common(source), rig, bones, degrees)
                relative = source.removeprefix('Characters/Animations/Aetherhaven/Life/')
                if relative == source:
                    relative = 'Vanilla/'+source.rsplit('/', 1)[-1]
                target = f'Characters/Animations/Aetherhaven/CreatureFaces/{rig}/{relative}'
                write(res/'Common'/target, data)
                generated[source] = target
            return generated[source]

        overrides = copy.deepcopy(face_bindings)
        for binding in overrides.values():
            for animation in binding.get('Animations', []):
                animation['Animation'] = adapt(animation['Animation'])
        for variant in ('', '_Lower', '_Higher'):
            name = 'Aetherhaven_Life_Actions'+variant
            table = json.loads((res/f'Server/Item/Animations/{name}.json').read_text())
            for action in table['Animations'].values():
                if action.get('ThirdPersonFace'):
                    action['ThirdPersonFace'] = adapt(action['ThirdPersonFace'])
            write(res/f'Server/Item/Animations/{name}_{rig}.json', table)
        for name in names:
            model_path = res/f'Server/Models/Townsfolk/{name}.json'
            model = json.loads(model_path.read_text())
            model.setdefault('AnimationSets', {}).update(overrides)
            muted = []
            # Read the parent too, so rerunning the compiler remains auditable.
            inherited = resolve(model['Parent'])['AnimationSets']
            for key, binding in inherited.items():
                if not any(a.get('SoundEventId') in AMBIENT_VOCALS for a in binding.get('Animations', [])):
                    continue
                quiet = copy.deepcopy(model['AnimationSets'].get(key, binding))
                for animation in quiet.get('Animations', []):
                    if animation.get('SoundEventId') in AMBIENT_VOCALS:
                        del animation['SoundEventId']
                model['AnimationSets'][key] = quiet
                muted.append(key)
            write(model_path, model)
            report[name] = {'rig': rig, 'model': resolve(name)['Model'],
                            'jawDegrees': degrees, 'mutedAnimations': muted,
                            'faceNodes': sorted({node for source in generated for node in
                                retarget(read_common(source), rig, bones, degrees)['nodeAnimations']})}
        print(f'{rig}: {len(generated)} native face timelines; {len(names)} townsfolk.', flush=True)
    write(res/'defaults/villager_creature_faces.json', report)
    mute_town_idle_sounds(res, write)


def mute_town_idle_sounds(res, write):
    """Override every inherited idle sound on mod residents, including human rigs."""
    resolve, _, _ = load_references(res)
    changed = 0
    paths = [res/'Server/Models/Human/Aetherhaven_Human.json']
    for folder in ('Townsfolk', 'Villager'):
        paths.extend((res/'Server/Models'/folder).glob('*.json'))
    for path in paths:
        model = json.loads(path.read_text())
        dirty = False
        for key, binding in resolve(path.stem)['AnimationSets'].items():
            if 'idle' not in key.lower():
                continue
            if not any(a.get('SoundEventId') for a in binding.get('Animations', [])):
                continue
            quiet = copy.deepcopy(binding)
            for animation in quiet.get('Animations', []):
                animation.pop('SoundEventId', None)
            model.setdefault('AnimationSets', {})[key] = quiet
            dirty = True
        if dirty:
            write(path, model)
            changed += 1
    print(f'Removed inherited idle sounds on {changed} town model assets.', flush=True)


if __name__ == '__main__':
    from generate_villager_lip_sync import RES, write
    generate(RES, write)
