"""Authored acting curves for Hytale's existing detachable face and mouth atlas.

No animation is copied. Texture cells and bone names reference the shipped rig.
All timing is seconds at the engine's actual 60 FPS, including the preview.
"""
import json
import math
import copy

DURATIONS = {
    'ShowItem': 4.8, 'Mix': 7.0,
    'Greet': 2.8, 'Explain': 3.5, 'Story': 4.2, 'Question': 3.0,
    'Agree': 2.2, 'Laugh': 4.0, 'Surprise': 2.0, 'Disagree': 2.8,
    'Hungry': 4.8, 'Sleepy': 6.0, 'Bored': 4.8,
    'LookAround': 5.8, 'Stretch': 6.0, 'Fidget': 5.0,
    'Read': 6.0, 'Craft': 4.8, 'Sweep': 6.0, 'Inspect': 5.2, 'Tend': 5.6,
}
SOCIAL = {'Greet','Explain','Story','Question','Agree','Laugh','Surprise','Disagree','ShowItem'}
# Brow height, brow slant, upper lid, lower lid, pupil scale, mouth UV cell.
EXPRESSIONS = {
    'Mix': (-.2,3,2,1,.98,(20,0)),
    'ShowItem': (1.1,-7,1,2,1.03,(120,-10)),
    'Greet': (1.6,-9,1,3,1.02,(120,-10)),
    'Explain': (.7,-5,1,1,1,(20,0)),
    'Story': (1.8,-10,1,2,1.08,(120,-10)),
    'Question': (1.2,-12,1,1,.95,(160,0)),
    'Agree': (.8,-7,1,4,.95,(120,-10)),
    'Laugh': (1.8,-6,.6,3,1.02,(140,-10)),
    'Surprise': (2.6,-4,.3,.4,.77,(160,0)),
    'Disagree': (-.8,12,3,1,.95,(0,-20)),
    'Hungry': (.2,-15,3,2,.95,(0,-20)),
    'Sleepy': (-.5,-6,9,5,.9,(220,-30)),
    'Bored': (-.7,4,5,1,1,(0,-20)),
    'LookAround': (.6,-4,1,1,1,(0,0)),
    'Stretch': (.8,-7,7,5,.95,(140,0)),
    'Fidget': (.2,-8,2,1,.98,(0,0)),
    'Read': (-.2,3,2,1,.95,(0,0)),
    'Craft': (-.4,5,2,1,.95,(0,0)),
    'Sweep': (0,-3,2,1,1,(20,0)),
    'Inspect': (.8,-10,2,1,.9,(0,0)),
    'Tend': (.3,-5,2,2,1,(20,0)),
}


def faces(res, quat, write_json):
    model_path = res/'Server/Models/Human/Aetherhaven_Human.json'
    model = json.loads(model_path.read_text())
    timing = {name: round(seconds*1000) for name,seconds in DURATIONS.items()}
    voice_path = res/'defaults/villager_life_voice_timing.json'
    voice_timing = json.loads(voice_path.read_text()) if voice_path.exists() else {}
    for name, seconds in DURATIONS.items():
        end = round(seconds*60)
        brow, slant, upper, lower, pupil, mouth = EXPRESSIONS[name]
        phases = [0,.16,.32,.57,.78,1]
        power = [0,.85,1,.8,1,0]
        def track(values, dimensions='xyz', times=phases, smooth=True):
            return [{'time':round(t*end),'delta':dict(zip(dimensions,value)),
                     **({'interpolationType':'smooth'} if smooth else {})} for t,value in zip(times,values)]
        nodes = {}
        for side, sign in [('L',1),('R',-1)]:
            # Questions and inspecting raise one brow and tighten the other.
            height = brow*(1.8 if side=='L' else -.2) if name in ('Question','Inspect') else brow
            nodes[side+'-Eyebrow'] = {
                'position':track([(0,height*p,0) for p in power]),
                'orientation':[{'time':round(t*end),'delta':quat((0,0,slant*sign*p)),
                                'interpolationType':'smooth'} for t,p in zip(phases,power)]}
            gaze = [(0,0,0)]*len(phases)
            if name in ('LookAround','Bored','Fidget'):
                gaze = [(0,0,0),(-1.1,-.2,0),(-1.1,-.2,0),(1.2,.2,0),(1.2,.2,0),(0,0,0)]
            elif name in ('Read','Craft','Inspect','Tend','Hungry'):
                gaze = [(0,-.8*p,0) for p in power]
            nodes[side+'-Eye'] = {'position':track(gaze),
                'shapeStretch':track([(1+(pupil-1)*p,1+(pupil-1)*p,1) for p in power])}
            if name=='Read':
                # Slow line tracking, quick return to the next line, both eyes together.
                nodes[side+'-Eye']['position']=track(
                    [(0,0,0),(-1.1,-.65,0),(1.1,-.65,0),(-1.1,-.85,0),
                     (1.1,-.85,0),(-1.1,-1.05,0),(1.1,-1.05,0),(-1.1,-.85,0),(1.1,-.85,0),(0,0,0)],
                    times=[0,.16,.31,.34,.50,.53,.70,.73,.86,1])
            # Upper eyelids shrink/grow vertically on the Player rig (neutral scale .1).
            lid_times = [0,.18,.32,.53,.55,.59,.80,1]
            lid_power = [0,.9,1,.85,1,.8,1,0]
            lid = [(1,1+(upper-1)*p,1) for p in lid_power]
            if name not in ('Sleepy','Laugh','Surprise','Stretch'):
                lid[4] = (1,10,1)  # A short blink during the gesture, never uniform looping.
            nodes[side+'-Eyelid'] = {'shapeStretch':track(lid,times=lid_times)}
            nodes[side+'-Eyelid-Bot'] = {'shapeStretch':track([(1,1+(lower-1)*p,1) for p in power]),
                'position':track([(0,.45*p if lower>2 else 0,0) for p in power])}
        nodes['Mouth'] = {
            'shapeUvOffset':track([(0,0),mouth,mouth,mouth,mouth,(0,0)],dimensions='xy',smooth=False),
            'shapeStretch':track([(1,1,1),(1,1,1),(1.07,1.12,1),(1,.95,1),(1.02,1.05,1),(1,1,1)]),
        }
        if name=='Sleepy':
            nodes['Mouth']['shapeUvOffset']=track([(0,0),(140,0),(220,-30),(220,-30),(20,0),(0,0)],dimensions='xy',smooth=False)
        if name=='Laugh':
            nodes['Mouth']['shapeUvOffset']=track(
                [(0,0),(120,-10),(140,-10),(120,-10),(140,-10),(120,-10),(140,-10),(120,-10),(0,0)],
                dimensions='xy',times=[0,.16,.30,.42,.53,.64,.75,.86,1],smooth=False)
        def save(suffix, tracks):
            path=f'Characters/Animations/Aetherhaven/Life/Faces/{suffix}.blockyanim'
            write_json(res/'Common'/path,{'formatVersion':1,'duration':end,'holdLastKeyframe':False,'nodeAnimations':tracks})
            model['AnimationSets']['Aetherhaven_Life_Face_'+suffix]={'Animations':[{'Animation':path,'Looping':False,'BlendingDuration':.15}]}
        save(name,nodes)
        if name in SOCIAL:
            # Coarticulated mouth shapes with holds and closures; bones keep the emotion.
            talking = copy.deepcopy(nodes)
            cells=[(0,0),(20,0),(140,0),(0,0),(120,0),(160,0),(20,0),(140,0),(0,0),mouth,(0,0)]
            times=[0,.06,.13,.21,.26,.35,.43,.51,.61,.73,1]
            talking['Mouth']['shapeUvOffset']=track(cells,dimensions='xy',times=times,smooth=False)
            if name=='Laugh':talking['Mouth']=copy.deepcopy(nodes['Mouth'])
            mood = {'Question':'Question','Laugh':'Laugh','Surprise':'Gasp','Disagree':'Grumble'}.get(name,'Talk')
            voice_ms = max((ms for key,ms in voice_timing.items() if key.endswith('_'+mood)),default=0)
            talking_end = max(end, math.ceil(voice_ms/100)*6)
            for bone in talking.values():
                for frames in bone.values():
                    for key in frames:key['time']=round(key['time']*talking_end/end)
            end = talking_end
            timing['Talking_'+name]=round(end/60*1000)
            save('Talking_'+name,talking)
    write_json(model_path,model)
    write_json(res/'defaults/villager_life_timing.json',timing)
