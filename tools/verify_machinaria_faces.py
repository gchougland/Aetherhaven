"""Validate optional Machinaria assets against native bones and voice timing."""
import json
import math
from pathlib import Path
from generate_machinaria_faces import RES, JAW_DEGREES, JAW_DROP
from villager_creature_faces import walk


def verify(res):
    def read(path):
        data=json.loads(path.read_text())
        if path.parent.name=='Animations' and 'Parent' in data:
            from villager_native_items import merge
            data=merge(read(path.with_name(data.pop('Parent')+'.json')),data)
        return data
    model = read(res / 'Server/Models/Machinaria_Robot_Base.json')
    mesh = read(res / 'Common' / model['Model'])
    bones = {n['name'] for root in mesh['nodes'] for n in walk(root)}
    human = read(RES / 'Server/Models/Human/Aetherhaven_Human.json')
    checked = set()
    for key, binding in model['AnimationSets'].items():
        if not key.startswith(('Aetherhaven_Life_Face_', 'Aetherhaven_Life_Mouth_')) and key not in ('Talk', 'Talk2', 'Talk3', 'Talk4', 'Talk5', 'Grin', 'Frown'):
            continue
        for original, animation in zip(human['AnimationSets'][key]['Animations'], binding['Animations'], strict=True):
            assert original.get('Speed', 1) == animation.get('Speed', 1)
            assert original.get('Looping', False) == animation.get('Looping', False)
            assert 'SoundEventId' not in animation
            path = res / 'Common' / animation['Animation']
            if path in checked:
                continue
            checked.add(path)
            data = read(path)
            assert set(data['nodeAnimations']) == {'Jaw'} <= bones
            jaw = data['nodeAnimations']['Jaw']
            assert jaw['shapeStretch'] == jaw['shapeVisible'] == jaw['shapeUvOffset'] == []
            for track in (jaw['orientation'], jaw['position']):
                times = [k['time'] for k in track]
                assert times == sorted(set(times)) and times[-1] <= data['duration']
            for frame in jaw['orientation']:
                q = frame['delta']
                assert abs(sum(v*v for v in q.values()) - 1) < 1e-6
                assert 0 <= q['x'] <= math.sin(math.radians(JAW_DEGREES)/2) + 1e-6
                assert q['y'] == q['z'] == 0
            for frame in jaw['position']:
                delta = frame['delta']
                assert -JAW_DROP - 1e-6 <= delta['y'] <= 0
                assert delta['x'] == delta['z'] == 0
            if '/LipSync/' in path.as_posix():
                assert jaw['orientation'][-1]['delta']['x'] == 0
                assert jaw['position'][-1]['delta']['y'] == 0
    for pitch in ('', '_Lower', '_Higher'):
        name = 'Aetherhaven_Life_Actions' + pitch
        original = read(RES / f'Server/Item/Animations/{name}.json')['Animations']
        table = read(res / f'Server/Item/Animations/{name}_MachinariaRobot.json')['Animations']
        assert table.keys() == original.keys()
        for key, action in table.items():
            expected = dict(original[key])
            expected['ThirdPersonFace'] = action['ThirdPersonFace']
            assert action == expected, key
            path = res / 'Common' / action['ThirdPersonFace']
            if path not in checked:
                # Silent reading/ponder faces are Action-only, without a Face binding.
                data = read(path)
                if key.endswith('_Speech'):
                    assert not data['nodeAnimations'], 'Robot speech Action must not compete with Face jaw poses'
                    checked.add(path)
                    continue
                assert set(data['nodeAnimations']) == {'Jaw'} <= bones
                jaw = data['nodeAnimations']['Jaw']
                assert jaw['shapeStretch'] == jaw['shapeVisible'] == jaw['shapeUvOffset'] == []
                assert all(-JAW_DROP <= frame['delta']['y'] <= 0 for frame in jaw['position'])
                assert data['duration'] == read(RES / 'Common' / original[key]['ThirdPersonFace'])['duration']
                checked.add(path)
    for path in ['Server/Models/Townsfolk/Copper_Pin.json', 'Server/Models/Townsfolk/Reginald_Volt.json',
                 'Server/Aetherhaven/NpcModels/Mechanic.json']:
        resident = read(res / path)
        assert resident['Parent'] == 'Machinaria_Robot_Base'
        assert not set(resident.get('AnimationSets', {})) & set(human['AnimationSets'])
    apertures=set()
    for shape in ('A','B','C','D','E','F'):
        binding=model['AnimationSets']['Aetherhaven_Life_Mouth_'+shape]['Animations'][0]
        pose=read(res/'Common'/binding['Animation'])
        opening=pose['nodeAnimations']['Jaw']['orientation'][0]['delta']['x']
        apertures.add(opening)
        if shape=='A':assert opening==0
    assert len(apertures)>=4
    combined=list((res/'Common/NPC/Gear/Animations/AetherhavenFaces').rglob('*.blockyanim'))+list((RES/'Common/Characters/Animations/Aetherhaven').rglob('*.blockyanim'))
    assert len(combined)<1000, ('Combined villager animation budget exceeded',len(combined))
    print(f'Verified {len(checked)} native jaw animations, pitch bindings and all three resident models.')


if __name__ == '__main__':
    verify(RES.parents[2].parent / 'Machinaria/src/main/resources')
