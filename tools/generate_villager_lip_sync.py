"""Analyze phonetic mouth cues, then compile editable JSON into Hytale face assets.

Unchanged audio reuses its JSON, preserving manual timing/shape edits. Pass
--reanalyze to replace cues. Only the generated assets run inside the game.
"""
from pathlib import Path
import argparse
import concurrent.futures
import copy
import hashlib
import json
import math
import subprocess
from villager_blockyanim import complete_channels

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
CUES=RES/'Server/Aetherhaven/VillagerLipSync'
RHUBARB=ROOT/'build/rhubarb/Rhubarb-Lip-Sync-1.14.0-Windows/rhubarb.exe'
GESTURES={'Talk':['Greet','Explain','Story','Agree'],'Question':['Question'],'Laugh':['Laugh'],
          'Gasp':['Surprise'],'Grumble':['Disagree'],'Groan':['Hungry','Bored'],'Yawn':['Sleepy','Stretch'],'Sigh':['Bored']}
GESTURES.update(Idle=['LookAround','Fidget','ReadLoop','Craft','Sweep','Inspect','Tend'],
                Work=['LookAround','Craft','Sweep','Inspect','Tend','ReadLoop'],Thinking=['Ponder','ReadLoop'])
SHAPES={'A':(0,0),'B':(120,0),'C':(140,0),'D':(160,0),'E':(220,-30),'F':(180,-30),'X':(0,0)}

def write(path,data):
    if path.suffix == '.blockyanim':
        complete_channels(data)
    path.parent.mkdir(parents=True,exist_ok=True)
    serialized=json.dumps(data,indent=2)+'\n'
    if not path.exists() or path.read_text(encoding='utf-8') != serialized:
        path.write_text(serialized,encoding='utf-8')

def analyze(clip,reanalyze,source_root=None,cues_root=None):
    source=(source_root if source_root is not None else RES/'Common/Sounds/Aetherhaven/Life')/clip['file']
    digest=hashlib.sha256(source.read_bytes()).hexdigest()
    target=(cues_root if cues_root is not None else CUES)/(source.stem+'.json')
    if target.exists() and not reanalyze:
        old=json.loads(target.read_text())
        if old.get('audioSha256')==digest:return old
    if not RHUBARB.is_file():raise RuntimeError('Install official Rhubarb 1.14.0 under build/rhubarb first')
    raw=ROOT/'build/villager-life-preview/lip-analysis'/(source.stem+'.json')
    raw.parent.mkdir(parents=True,exist_ok=True)
    result=subprocess.run([str(RHUBARB),'-q','-r','phonetic','--extendedShapes','X','--threads','1','-f','json','-o',str(raw),str(source)],
                          capture_output=True,text=True,check=True)
    data=json.loads(raw.read_text(encoding='utf-8-sig'))
    document={'audio':source.name,'audioSha256':digest,'durationMs':clip['durationMs'],
              'recognizer':'Rhubarb 1.14.0 phonetic','mouthCues':data['mouthCues']}
    write(target,document)
    return document

def compile_assets(clips):
    model_path=RES/'Server/Models/Human/Aetherhaven_Human.json';model=json.loads(model_path.read_text())
    actions_path=RES/'Server/Item/Animations/Aetherhaven_Life_Actions.json'
    actions=json.loads(actions_path.read_text())
    actions['Animations']={k:v for k,v in actions['Animations'].items() if '_' not in k}
    index={};count=0
    for clip in clips:
        stem=Path(clip['file']).stem;group=clip['voice']+'_'+clip['mood']
        entry={'clip':stem,'audioMs':clip['durationMs'],'faces':{}}
        index.setdefault(group,[]).append(entry)
        if clip['mood']=='Stomach':continue
        source=RES/'Common/Sounds/Aetherhaven/Life'/clip['file'];cues=json.loads((CUES/(stem+'.json')).read_text())
        assert cues['audioSha256']==hashlib.sha256(source.read_bytes()).hexdigest(),f'Stale lip cues: {stem}'
        previous=0
        for cue in cues['mouthCues']:
            assert cue['value'] in SHAPES and previous-.001<=cue['start']<=cue['end']<=clip['durationMs']/1000+.02,stem
            previous=cue['end']
        for gesture in GESTURES[clip['mood']]:
            face=json.loads((RES/f'Common/Characters/Animations/Aetherhaven/Life/Faces/{gesture}.blockyanim').read_text())
            original_end=face['duration'];end=max(original_end,math.ceil(clip['durationMs']/1000*60)+1)
            # Keep eyebrows, gaze and eyelids expressing the actual gesture.
            for bone in face['nodeAnimations'].values():
                for track in bone.values():
                    for key in track:key['time']=round(key['time']*end/original_end)
            keys={0:(0,0)}
            for cue in cues['mouthCues']:
                shape=cue['value'];uv=SHAPES[shape]
                if gesture=='Laugh' and shape in ('B','C','D'):uv={'B':(120,-10),'C':(140,-10),'D':(160,-10)}[shape]
                keys[round(cue['start']*60)]=uv
                keys[round(cue['end']*60)]=(0,0)
            keys[end]=(0,0)
            mouth=face['nodeAnimations']['Mouth']
            mouth['shapeUvOffset']=[{'time':time,'delta':{'x':uv[0],'y':uv[1]}} for time,uv in sorted(keys.items())]
            # Jaw movement is driven by the same cue, not a repeating idle cycle.
            stretch=[]
            for time,uv in sorted(keys.items()):
                open_shape=uv not in ((0,0),(120,0),(120,-10))
                stretch.append({'time':time,'delta':{'x':1,'y':1.08 if open_shape else 1,'z':1},'interpolationType':'smooth'})
            mouth['shapeStretch']=stretch
            face['duration']=end
            suffix=gesture+'_'+stem;asset_id='Aetherhaven_Life_Lip_'+suffix
            path=f'Characters/Animations/Aetherhaven/Life/LipSync/{suffix}.blockyanim'
            write(RES/'Common'/path,face)
            body=json.loads((RES/f'Common/Characters/Animations/Aetherhaven/Life/{gesture}.blockyanim').read_text())
            if end>body['duration']:
                for bone in body['nodeAnimations'].values():
                    for frames in bone.values():
                        if frames:
                            final=copy.deepcopy(frames[-1]);final['time']=end;frames.append(final)
            body['duration']=end
            # A voiced reading beat ends in the same raised-book pose as ReadLoop.
            # Hold it through the server tick that resumes the silent loop.
            if gesture=='ReadLoop':body['holdLastKeyframe']=True
            body_path=f'Characters/Animations/Aetherhaven/Life/Actions/{suffix}.blockyanim'
            write(RES/'Common'/body_path,body)
            actions['Animations'][suffix]={'ThirdPerson':body_path,'ThirdPersonMoving':body_path,
                'ThirdPersonFace':path,'Speed':1,'Looping':False,'BlendingDuration':.35}
            model['AnimationSets'][asset_id]={'Animations':[{'Animation':path,'Looping':False,'BlendingDuration':.04}]}
            entry['faces'][gesture]={'id':asset_id,'durationMs':math.ceil(end/60*1000),'actionId':suffix}
            count+=1
    # Reuse the exact OGG and animation files. Hytale's audio pitch changes playback
    # rate, so each alias uses the same rate for its Action and standalone Face.
    for variant,semitones in [('Lower',-2),('Higher',2)]:
        pitch=2**(semitones/12)
        variant_actions=copy.deepcopy(actions)
        for name,action in variant_actions['Animations'].items():
            if '_' in name:action['Speed']=pitch
        write(RES/f'Server/Item/Animations/Aetherhaven_Life_Actions_{variant}.json',variant_actions)
        for group in index.values():
            for clip in group:
                for expression in clip['faces'].values():
                    binding=copy.deepcopy(model['AnimationSets'][expression['id']])
                    binding['Animations'][0]['Speed']=pitch
                    model['AnimationSets'][expression['id']+'_'+variant]=binding
    write(model_path,model)
    write(actions_path,actions)
    write(RES/'defaults/villager_life_playback.json',index)
    from villager_prowl_faces import generate as generate_prowl_faces
    generate_prowl_faces(RES,write)
    from villager_creature_faces import generate as generate_creature_faces
    generate_creature_faces(RES,write)
    print(f'Compiled {count} synchronized expression variants for {len(clips)} exact audio events.',flush=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--reanalyze',action='store_true');parser.add_argument('--compile-only',action='store_true');args=parser.parse_args()
    clips=json.loads((ROOT/'tools/villager_life_voice_manifest.json').read_text())['clips']
    voiced=[clip for clip in clips if clip['mood']!='Stomach']
    if not args.compile_only:
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            futures=[pool.submit(analyze,clip,args.reanalyze) for clip in voiced]
            for i,future in enumerate(concurrent.futures.as_completed(futures),1):
                future.result()
                if i%16==0:print(f'Analyzed {i}/{len(voiced)} recordings.',flush=True)
    compile_assets(clips)

if __name__=='__main__':main()
