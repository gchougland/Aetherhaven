"""Split voice masters at reviewed boundaries; never change pitch.

Sources remain untouched. The split manifest uses seconds in the original MP3s.
"""
from pathlib import Path
import argparse
import json
import hashlib
import sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/life-tools'))
import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw

LEGACY_PROFILES=('BrightFemale','BrightMale','WarmFemale','WarmMale','MellowFemale','MellowMale','GravelyMale','GravelyFemale')
PROFILES=LEGACY_PROFILES+('OldMale','OldFemale','RustyRobot')
RES=ROOT/'src/main/resources'
OUT=ROOT/'build/villager-life-preview/user-voices'

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')

def events(clips):
    timing={};directory=RES/'Server/Audio/SoundEvents/Aetherhaven/Life'
    for voice in dict.fromkeys(c['voice'] for c in clips):
        for mood in ('Talk','Question','Laugh','Gasp','Grumble','Groan','Yawn','Sigh','Stomach','Idle','Work','Thinking'):
            source_mood=mood
            group=[c for c in clips if c['voice']==voice and c['mood']==source_mood]
            assert group,(voice,mood)
            timing[voice+'_'+mood]=max(c['durationMs'] for c in group)
            for clip in group:
                write(directory/f'Aetherhaven_Life_{Path(clip["file"]).stem}.json',{
                    'Layers':[{'Files':['Sounds/Aetherhaven/Life/'+clip['file']]}],
                    'Volume':-6,'MaxDistance':10 if mood=='Stomach' else 16,
                    'StartAttenuationDistance':3,'PreventSoundInterruption':True})
    write(RES/'defaults/villager_life_voice_timing.json',timing)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('source',type=Path)
    parser.add_argument('--splits',type=Path,default=ROOT/'tools/villager_voice_splits.json')
    parser.add_argument('--stage-only',action='store_true',help='Validate and preview without replacing shipped assets')
    args=parser.parse_args()
    splits=json.loads(args.splits.read_text())
    OUT.mkdir(parents=True,exist_ok=True);clips=[];audition={};review=[]
    expected_count=0
    for name,spec in splits.items():
        cuts=spec if isinstance(spec,list) else spec['cuts']
        profiles=LEGACY_PROFILES if isinstance(spec,list) else tuple(spec['profiles'])
        assert profiles and len(profiles)==len(set(profiles)),name
        expected_count+=len(profiles)
        path=args.source/name;data,sr=sf.read(path)
        if isinstance(spec,dict) and 'sourceSha256' in spec:
            assert hashlib.sha256(path.read_bytes()).hexdigest()==spec['sourceSha256'],f'Changed source: {name}'
        mono=data.mean(axis=1) if data.ndim==2 else data
        assert len(cuts)==len(profiles)-1 and cuts==sorted(set(cuts)),name
        assert not cuts or 0<cuts[0]<=cuts[-1]<len(mono)/sr,name
        boundaries=[0]+[round(c*sr) for c in cuts]+[len(mono)]
        mood=name.removeprefix('All_').removeprefix('Humans_').removeprefix('RustyRobot_').removesuffix('.mp3').split('_')[0]
        suffix=name.removesuffix('.mp3').split('_')[-1]
        variant=suffix if suffix.isdigit() else '1'
        sheet=Image.new('RGB',(1200,150),'#f1f3f5');draw=ImageDraw.Draw(sheet)
        draw.text((12,8),name+' | '+', '.join(profiles),fill='#1f3348')
        stride=max(1,len(mono)//1160)
        for x in range(1160):
            chunk=mono[x*stride:(x+1)*stride]
            if len(chunk):
                amp=min(1,float(np.max(np.abs(chunk)))*1.5)*45
                draw.line((20+x,82-amp,20+x,82+amp),fill='#587b96')
        for index,(start,end) in enumerate(zip(boundaries,boundaries[1:])):
            clip=mono[start:end].copy()
            # Trim only low-level outer padding, retaining breaths and internal pauses.
            loud=np.flatnonzero(np.abs(clip)>.0015)
            assert len(loud),name
            lo=max(0,loud[0]-round(.035*sr));hi=min(len(clip),loud[-1]+round(.06*sr))
            clip=clip[lo:hi]
            fade=min(round(.004*sr),len(clip)//2)
            clip[:fade]*=np.linspace(0,1,fade);clip[-fade:]*=np.linspace(1,0,fade)
            # Leave headroom for Vorbis reconstruction overshoot.
            clip*=min(1,.80/max(.001,np.max(np.abs(clip))))
            voice=profiles[index];filename=f'{voice}_{mood}_{variant}.ogg'
            sf.write(OUT/filename,clip,sr,format='OGG',subtype='VORBIS')
            sf.write(OUT/(filename[:-4]+'.wav'),clip,sr)
            clips.append({'file':filename,'voice':voice,'mood':mood,'variant':int(variant),
                          'source':name,'sourceSha256':hashlib.sha256(path.read_bytes()).hexdigest(),
                          'sourceStartSeconds':round((start+lo)/sr,5),'sourceEndSeconds':round((start+hi)/sr,5),
                          'sampleRate':sr,'durationMs':int(np.ceil(len(clip)/sr*1000)),
                          'sha256':hashlib.sha256((OUT/filename).read_bytes()).hexdigest()})
            if mood in ('Talk','Laugh','Question') and variant=='1':audition[(voice,mood)]=(clip,sr)
            xx=20+round(start/len(mono)*1160)
            draw.line((xx,25,xx,134),fill='#b94c50',width=2);draw.text((xx+3,136),str(index+1),fill='#1f3348')
        review.append(sheet)
    assert len(clips)==expected_count
    assert len({c['file'] for c in clips})==len(clips),'Duplicate output clip'
    present_profiles=list(dict.fromkeys(c['voice'] for c in clips))
    montage=[];sr=next(iter(audition.values()))[1] if audition else 44100
    for voice in present_profiles:
        for mood in ('Talk','Question','Laugh'):
            if (voice,mood) not in audition:continue
            clip,rate=audition[(voice,mood)];assert rate==sr
            montage.extend([clip,np.zeros(round(sr*.5))])
    if montage:sf.write(OUT/'audition.wav',np.concatenate(montage),sr)
    overview=Image.new('RGB',(1200,len(review)*150),'white')
    for i,sheet in enumerate(review):overview.paste(sheet,(0,i*150))
    overview.save(OUT/'split-review.png')
    # Validate the entire staged cast before replacing any shipped recording.
    for clip in clips:
        a,rate=sf.read(OUT/clip['file']);assert np.isfinite(a).all() and np.max(np.abs(a))<1,clip['file']
    manifest={'source':'ElevenLabs Generation 1 masters from the user voice script',
        'profileOrder':present_profiles,'processing':'Reviewed splits, outer padding trim, 4 ms fades; no pitch or speed changes.',
        'clips':clips}
    write(OUT/'manifest.json',manifest)
    if args.stage_only:
        print(f'Staged {len(clips)} clips across {len(present_profiles)} profiles. Shipped assets unchanged.')
        return
    required={'Talk','Question','Laugh','Gasp','Grumble','Groan','Yawn','Sigh','Stomach','Idle','Work','Thinking'}
    for voice in present_profiles:
        assert {c['mood'] for c in clips if c['voice']==voice}==required,voice
    shipped=RES/'Common/Sounds/Aetherhaven/Life';shipped.mkdir(parents=True,exist_ok=True)
    for clip in clips:(shipped/clip['file']).write_bytes((OUT/clip['file']).read_bytes())
    expected={c['file'] for c in clips}
    for path in shipped.glob('*.ogg'):
        if path.name not in expected:path.unlink()
    eventdir=RES/'Server/Audio/SoundEvents/Aetherhaven/Life'
    for path in eventdir.glob('Aetherhaven_Life_*.json'):path.unlink()
    events(clips)
    write(ROOT/'tools/villager_life_voice_manifest.json',manifest)
    print(f'Imported {len(clips)} clips and exact events across {len(present_profiles)} profiles. Originals preserved.')

if __name__=='__main__':main()
