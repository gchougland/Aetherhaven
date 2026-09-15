"""Adapt face UV tracks to Prowl's existing 18x8 mouth cells. Never rewrite its rig or texture."""
import copy,json
from pathlib import Path

# Standard player mouth cell -> vertical row in Prowl's original texture.
MOUTH_ROWS={
    (0,0):24,(20,0):32,(40,0):40,(120,0):16,(140,0):56,(160,0):48,(180,0):96,
    (0,-10):32,(20,-10):112,(40,-10):120,(80,-10):80,(120,-10):32,(140,-10):8,
    (160,-10):48,(180,-10):56,(181,-10):56,(200,-10):120,
    (0,-20):0,(40,-20):40,(160,-20):64,(160,-30):96,(180,-30):104,(220,-30):96,
    (0,-50):72,(60,-50):88,
}

def generate(res,write):
    root=res.parents[2]
    vanilla=root.parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets/Common'
    model_path=res/'Server/Models/Townsfolk/Prowl.json'
    model=json.loads(model_path.read_text())
    human=json.loads((res/'Server/Models/Human/Aetherhaven_Human.json').read_text())
    generated={}
    def adapt(source):
        if source in generated:return generated[source]
        path=res/'Common'/source
        if not path.is_file():path=vanilla/source
        data=json.loads(path.read_text())
        mouth=data.get('nodeAnimations',{}).get('Mouth')
        if mouth is None:return source
        for frame in mouth.get('shapeUvOffset',[]):
            uv=tuple(frame['delta'][axis] for axis in ('x','y'))
            if uv not in MOUTH_ROWS:raise ValueError(f'Unmapped Prowl mouth: {source}: {uv}')
            # Hytale positive UV Y moves up the texture. Neutral is at (494,24).
            frame['delta']={'x':0,'y':24-MOUTH_ROWS[uv]}
        # Prowl's mouth has a deliberate rest orientation/placement. Neutral
        # orientation deltas from vanilla expressions remain relative to it.
        relative=source.removeprefix('Characters/Animations/Aetherhaven/Life/')
        if relative==source:relative='Vanilla/'+Path(source).name
        target='Characters/Animations/Aetherhaven/ProwlFaces/'+relative
        write(res/'Common'/target,data);generated[source]=target
        return target
    overrides={}
    for name,binding in human['AnimationSets'].items():
        updated=copy.deepcopy(binding);changed=False
        for animation in updated.get('Animations',[]):
            source=animation.get('Animation')
            if source:
                target=adapt(source)
                if target!=source:animation['Animation']=target;changed=True
        if changed:overrides[name]=updated
    model['AnimationSets']=overrides
    write(model_path,model)
    for variant in ['', '_Lower', '_Higher']:
        name='Aetherhaven_Life_Actions'+variant
        table=json.loads((res/f'Server/Item/Animations/{name}.json').read_text())
        for action in table['Animations'].values():
            if action.get('ThirdPersonFace'):action['ThirdPersonFace']=adapt(action['ThirdPersonFace'])
        write(res/f'Server/Item/Animations/{name}_Prowl.json',table)
    print(f'Adapted {len(generated)} Prowl facial timelines to the existing mouth atlas.',flush=True)
