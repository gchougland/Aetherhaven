"""Analyze phonetic mouth cues, then compile editable JSON into Hytale face assets.

Unchanged audio reuses its JSON, preserving manual timing/shape edits. Pass
--reanalyze to replace cues. Only the generated assets run inside the game.
"""
from pathlib import Path
import argparse
import concurrent.futures
import hashlib
import json
import subprocess
from villager_blockyanim import complete_channels

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
CUES=RES/'Server/Aetherhaven/VillagerLipSync'
RHUBARB=ROOT/'build/rhubarb/Rhubarb-Lip-Sync-1.14.0-Windows/rhubarb.exe'
GESTURES={'Talk':['Greet','Explain','Story','Agree','ShowItem'],'Question':['Question'],'Laugh':['Laugh'],
          'Gasp':['Surprise'],'Grumble':['Disagree'],'Groan':['Hungry','Bored'],'Yawn':['Sleepy','Stretch'],'Sigh':['Bored']}
GESTURES.update(Idle=['LookAround','Fidget','ReadLoop','Craft','Mix','Sweep','Inspect','Tend'],
                Work=['LookAround','Craft','Mix','Sweep','Inspect','Tend','ReadLoop'],Thinking=['Ponder','ReadLoop'])
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
    from villager_compact_speech import compile_assets as compile_compact
    compile_compact(clips)


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
