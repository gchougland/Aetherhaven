"""Compile jaw-only speech for Machinaria's robot residents from Aetherhaven cues.

Run after generate_villager_lip_sync.py when recordings or expression tracks change.
The robot pack owns its bindings and animations; no robot assets are required by
Aetherhaven when Machinaria is absent.
"""
import argparse
import copy
import json
import math
from pathlib import Path
from generate_villager_lip_sync import RES, write
from villager_creature_faces import jaw_track, walk

RIG = 'MachinariaRobot'
JAW_DEGREES = 12
JAW_DROP = 3


def retarget(data):
    result = copy.deepcopy(data)
    frames = data['nodeAnimations']['Mouth']['shapeUvOffset']
    rotation = jaw_track(frames, JAW_DEGREES)
    # A small piston travel complements the native hinge without stretching metal.
    position = [{'time': frame['time'], 'delta': {
        'x': 0, 'y': -JAW_DROP * math.degrees(2 * math.asin(frame['delta']['x'])) / JAW_DEGREES,
        'z': 0}, 'interpolationType': 'smooth'} for frame in rotation]
    result['nodeAnimations'] = {'Jaw': {'orientation': rotation, 'position': position}}
    return result


def generate(destination):
    destination = destination.resolve()
    model_path = destination / 'Server/Models/Machinaria_Robot_Base.json'
    model = json.loads(model_path.read_text())
    mesh_path = destination / 'Common' / model['Model']
    mesh = json.loads(mesh_path.read_text())
    jaws = [node for root in mesh['nodes'] for node in walk(root) if node['name'] == 'Jaw']
    assert len(jaws) == 1 and jaws[0]['shape']['type'] == 'box', 'Expected the native rigid robot jaw'
    human = json.loads((RES / 'Server/Models/Human/Aetherhaven_Human.json').read_text())
    bindings = {key: copy.deepcopy(value) for key, value in human['AnimationSets'].items()
                if key.startswith(('Aetherhaven_Life_Face_', 'Aetherhaven_Life_Lip_'))
                or key in ('Talk', 'Talk2', 'Talk3', 'Talk4', 'Talk5', 'Grin', 'Frown')}
    base = RES.parents[2].parent / 'HytaleSourceCode/hytale-shared-source/HytaleAssets/Common'
    generated = {}

    def adapt(source):
        if source not in generated:
            path = RES / 'Common' / source
            data = json.loads((path if path.exists() else base / source).read_text())
            relative = source.removeprefix('Characters/Animations/Aetherhaven/Life/')
            if relative == source:
                relative = 'Vanilla/' + Path(source).name
            target = f'NPC/Gear/Animations/AetherhavenFaces/{relative}'
            write(destination / 'Common' / target, retarget(data))
            generated[source] = target
        return generated[source]

    for binding in bindings.values():
        for animation in binding['Animations']:
            animation['Animation'] = adapt(animation['Animation'])
            animation.pop('SoundEventId', None)
    for pitch in ('', '_Lower', '_Higher'):
        name = 'Aetherhaven_Life_Actions' + pitch
        table = json.loads((RES / f'Server/Item/Animations/{name}.json').read_text())
        for action in table['Animations'].values():
            if action.get('ThirdPersonFace'):
                action['ThirdPersonFace'] = adapt(action['ThirdPersonFace'])
        write(destination / f'Server/Item/Animations/{name}_{RIG}.json', table)
    model.setdefault('AnimationSets', {}).update(bindings)
    write(model_path, model)
    print(f'Generated {len(generated)} jaw timelines and three action tables for all robot residents.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path,
                        default=RES.parents[2].parent / 'Machinaria/src/main/resources')
    generate(parser.parse_args().resources)
