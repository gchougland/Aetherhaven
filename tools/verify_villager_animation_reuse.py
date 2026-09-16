"""Validate shared exports against each rig, all aliases, and distinct timelines."""
import json
from collections import defaultdict
from generate_villager_lip_sync import RES
from villager_animation_pool import fingerprint
from villager_creature_faces import RIGS, load_references, retarget


def verify():
    common = RES/'Common'
    root = common/'Characters/Animations/Aetherhaven'
    resolve, read_common, bones_for = load_references(RES)
    def read(path):
        data = json.loads(path.read_text())
        if path.parent.name=='Animations' and 'Parent' in data:
            from villager_native_items import merge
            data=merge(read(path.with_name(data.pop('Parent')+'.json')), data)
        return data
    cache = {}
    def digest(source):
        if source not in cache:
            cache[source] = fingerprint(read_common(source))
        return cache[source]
    groups = defaultdict(list)
    paths = list(root.rglob('*.blockyanim'))
    assert len(paths)<800, ('Villager animation budget exceeded',len(paths))
    for path in paths:
        groups[digest(path.relative_to(common).as_posix())].append(path)
    # The small editable template set is deliberately retained as compiler input.
    # No generated copy may duplicate a template or any other generated file.
    templates = {* (root/'Life').glob('*.blockyanim'), * (root/'Life/Faces').glob('*.blockyanim')}
    for same in groups.values():
        assert len(same) == 1 or all(path in templates for path in same), same
    human = read(RES/'Server/Item/Animations/Aetherhaven_Life_Actions.json')['Animations']
    bodies = {a['ThirdPerson'] for a in human.values()}
    assert len(bodies) < 80, len(bodies)
    references = 0
    for table in (RES/'Server/Item/Animations').glob('Aetherhaven_Life_Actions*.json'):
        for action in read(table)['Animations'].values():
            body = read(common/action['ThirdPerson'])
            face = read(common/action['ThirdPersonFace'])
            assert body['duration'] == face['duration'], (table, action)
            assert action['ThirdPerson'] == action['ThirdPersonMoving']
            references += 3
    # Sharing across races must preserve the complete original retargeted motion,
    # including each jaw aperture, single-eye mapping and texture coordinates.
    human_model = read(RES/'Server/Models/Human/Aetherhaven_Human.json')['AnimationSets']
    for folder in ('Villager','Townsfolk'):
        for path in (RES/'Server/Models'/folder).glob('*.json'):
            effective=resolve(path.stem)
            if effective.get('Model')=='Characters/Player.blockymodel':
                for shape in ('A','B','C','D','E','F'):
                    assert 'Aetherhaven_Life_Mouth_'+shape in effective['AnimationSets'], (path,'missing mouth binding',shape)
    for gesture in ('Read','ReadLoop','Read_Speech','ReadLoop_Speech'):
        action=human[gesture]
        held=read(common/action['FirstPerson'])
        body=read(common/action['ThirdPerson'])
        assert set(held['nodeAnimations'])=={'Book-Top','Book-Bot'}
        for bone,track in held['nodeAnimations'].items():assert track==body['nodeAnimations'][bone]
    for rig, (names, degrees) in RIGS.items():
        bones = bones_for(resolve(names[0]))
        expected = {}
        def check(source, target):
            if source not in expected:
                expected[source] = fingerprint(retarget(read_common(source), rig, bones, degrees))
            assert expected[source] == digest(target), (rig, source, target)
        for pitch in ('', '_Lower', '_Higher'):
            source = read(RES/f'Server/Item/Animations/Aetherhaven_Life_Actions{pitch}.json')['Animations']
            actual = read(RES/f'Server/Item/Animations/Aetherhaven_Life_Actions{pitch}_{rig}.json')['Animations']
            assert source.keys() == actual.keys()
            for key, action in actual.items():
                check(source[key]['ThirdPersonFace'], action['ThirdPersonFace'])
        for name in names:
            for key, binding in read(RES/f'Server/Models/Townsfolk/{name}.json')['AnimationSets'].items():
                if key.startswith(('Aetherhaven_Life_Face_', 'Aetherhaven_Life_Mouth_')) or key in ('Talk','Talk2','Talk3','Talk4','Talk5','Grin','Frown'):
                    for source, actual in zip(human_model[key]['Animations'], binding['Animations'], strict=True):
                        check(source['Animation'], actual['Animation'])
    print(f'Checked {references} action references, exact rig motion and no duplicated generated exports.')
    print(f'{len(paths)} animations, {sum(p.stat().st_size for p in paths)/1024**2:.1f} MiB; '
          f'{len(human)} action IDs share {len(bodies)} body files.')


if __name__ == '__main__':
    verify()
