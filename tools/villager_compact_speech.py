"""Compile reusable expressions/visemes; recordings contain timing data only."""
import copy
import hashlib
import json
from pathlib import Path
from villager_animation_pool import AnimationPool


def compile_assets(clips):
    from generate_villager_lip_sync import RES, CUES, GESTURES, SHAPES, write
    life = RES/'Common/Characters/Animations/Aetherhaven/Life'
    # Authored residents using the Player skeleton must inherit our shared mouth
    # bindings, not just vanilla Player animations (e.g. the furniture merchant).
    for folder in ('Villager','Townsfolk'):
        for path in (RES/'Server/Models'/folder).glob('*.json'):
            resident=json.loads(path.read_text())
            if resident.get('Parent')=='Player':
                resident['Parent']='Aetherhaven_Human'
                write(path,resident)
    pool = AnimationPool(RES, write, [*life.glob('*.blockyanim'), *(life/'Faces').glob('*.blockyanim')])
    model_path = RES/'Server/Models/Human/Aetherhaven_Human.json'
    model = json.loads(model_path.read_text())
    model['AnimationSets'] = {k:v for k,v in model['AnimationSets'].items()
                              if not k.startswith(('Aetherhaven_Life_Lip_', 'Aetherhaven_Life_Mouth_'))}
    actions_path = RES/'Server/Item/Animations/Aetherhaven_Life_Actions.json'
    actions = json.loads(actions_path.read_text())
    actions['Animations'] = base = {k:v for k,v in actions['Animations'].items() if '_' not in k}
    durations = {}
    for gesture, original in list(base.items()):
        body = json.loads((RES/'Common'/original['ThirdPerson']).read_text())
        if gesture in ('Read','ReadLoop'):
            # Reuse these hinges for the held-item channel and for the book's
            # intrinsic Item.Animation, which opens the actual item mesh.
            held=copy.deepcopy(body)
            held['nodeAnimations']={k:v for k,v in held['nodeAnimations'].items() if k.startswith('Book-')}
            original['FirstPerson']=pool.emit(f'Characters/Animations/Aetherhaven/Life/Held/{gesture}.blockyanim',held)
        durations[gesture] = round(body['duration']/60*1000)
        eyes = json.loads((life/f'Faces/{gesture}.blockyanim').read_text())
        model['AnimationSets'].setdefault('Aetherhaven_Life_Face_'+gesture, {
            'Animations':[{'Animation':f'Characters/Animations/Aetherhaven/Life/Faces/{gesture}.blockyanim',
                           'Looping':False,'BlendingDuration':.15}]})
        eyes['nodeAnimations'].pop('Mouth', None)
        action = copy.deepcopy(original)
        action['ThirdPersonFace'] = pool.emit(f'Characters/Animations/Aetherhaven/Life/Eyes/{gesture}.blockyanim', eyes)
        base[gesture+'_Speech'] = action
    shapes = {k:v for k,v in SHAPES.items() if k != 'X'}
    shapes.update(Smile_B=(120,-10), Smile_C=(140,-10), Smile_D=(160,-10))
    for shape, uv in shapes.items():
        data = {'formatVersion':1, 'duration':60, 'holdLastKeyframe':True,
                'nodeAnimations':{'Mouth':{'shapeUvOffset':[
                    {'time':t,'delta':{'x':uv[0],'y':uv[1]}} for t in (0,60)],
                    'shapeStretch':[{'time':t,'delta':{'x':1,'y':1 if shape in ('A','B','Smile_B') else 1.08,'z':1}}
                                    for t in (0,60)]}}}
        path = pool.emit(f'Characters/Animations/Aetherhaven/Life/Mouth/{shape}.blockyanim', data)
        model['AnimationSets']['Aetherhaven_Life_Mouth_'+shape] = {
            'Animations':[{'Animation':path,'Looping':True,'BlendingDuration':.035}]}
    index = {}
    for clip in clips:
        stem = Path(clip['file']).stem
        entry = {'clip':stem,'audioMs':clip['durationMs'],'faces':{}}
        index.setdefault(clip['voice']+'_'+clip['mood'], []).append(entry)
        if clip['mood'] == 'Stomach':continue
        cues = json.loads((CUES/(stem+'.json')).read_text())
        assert cues['audioSha256'] == hashlib.sha256((RES/'Common/Sounds/Aetherhaven/Life'/clip['file']).read_bytes()).hexdigest()
        keys = {0:'A'}
        for cue in cues['mouthCues']:
            assert cue['value'] in SHAPES
            keys[round(cue['start']*1000)] = 'A' if cue['value']=='X' else cue['value']
            keys[round(cue['end']*1000)] = 'A'
        keys[clip['durationMs']] = 'A'
        compact = []
        for time, shape in sorted(keys.items()):
            if not compact or shape != compact[-1][1]:compact.append([time,shape])
        entry['mouthCues'] = compact
        for gesture in GESTURES[clip['mood']]:
            entry['faces'][gesture] = {'id':'Aetherhaven_Life_Face_'+gesture,
                'durationMs':max(durations[gesture],clip['durationMs']), 'actionId':gesture+'_Speech'}
    write(model_path, model)
    write(actions_path, actions)
    write(RES/'defaults/villager_life_playback.json', index)
    # Retain public pitch table IDs; their definitions are shared, not duplicated.
    for suffix in ('_Lower','_Higher'):
        write(RES/f'Server/Item/Animations/Aetherhaven_Life_Actions{suffix}.json', {'Parent':'Aetherhaven_Life_Actions'})
    from villager_prowl_faces import generate as prowl
    from villager_creature_faces import generate as creatures
    prowl(RES, write, pool)
    creatures(RES, write, pool)
    pool.prune(['Characters/Animations/Aetherhaven/'+folder for folder in
                ('Life/Actions','Life/LipSync','Life/Eyes','Life/Mouth','Life/Held','ProwlFaces','CreatureFaces')])
    print(f'Compiled {len(clips)} cue lists with {len(shapes)} reusable mouth poses; no recording-specific animations.')
