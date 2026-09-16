"""Mux the actual exported body/face timelines with the selected shipped audio."""
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/life-tools'))
import json
import copy
import math
import subprocess
import numpy as np
import soundfile as sf
import imageio_ffmpeg
from PIL import Image, ImageDraw
from preview_villager_faces import render as render_face
from preview_villager_life import render as render_body, ANIMS, OUT

def main():
    cast=[('BrightFemale','Talk','Story'),('BrightMale','Question','Question'),
          ('WarmFemale','Laugh','Laugh'),('WarmMale','Grumble','Disagree'),
          ('MellowFemale','Yawn','Sleepy'),('MellowMale','Groan','Hungry'),
          ('GravelyMale','Talk','Explain'),('GravelyFemale','Gasp','Surprise'),
          ('OldMale','Thinking','Ponder'),('OldFemale','Laugh','Laugh'),('RustyRobot','Talk','Story')]
    index=json.loads((ROOT/'src/main/resources/defaults/villager_life_playback.json').read_text())
    actions=json.loads((ROOT/'src/main/resources/Server/Item/Animations/Aetherhaven_Life_Actions.json').read_text())['Animations']
    scenes=[];sound=[];rate=None;fps=30
    for profile,mood,gesture in cast:
        clip=index[profile+'_'+mood][0];a,sr=sf.read(ROOT/f'src/main/resources/Common/Sounds/Aetherhaven/Life/{clip["clip"]}.ogg')
        if rate is None:rate=sr
        assert rate==sr
        action=actions[clip['faces'][gesture]['actionId']]
        common=ROOT/'src/main/resources/Common'
        face=json.loads((common/action['ThirdPersonFace']).read_text())
        body=json.loads((common/action['ThirdPerson']).read_text());body['previewName']=gesture
        frames=math.ceil((max(body['duration']/60,clip['audioMs']/1000)+.25)*fps)
        samples=round(frames/fps*rate);a=np.pad(a,(0,max(0,samples-len(a))))[:samples]
        sound.append(a);scenes.append((profile,mood,face,body,frames,clip))
    audio_path=OUT/'lip-sync-preview.wav';sf.write(audio_path,np.concatenate(sound),rate)
    command=[imageio_ffmpeg.get_ffmpeg_exe(),'-y','-loglevel','error','-f','rawvideo','-pix_fmt','rgb24',
             '-s','720x440','-r',str(fps),'-i','pipe:0','-i',str(audio_path),'-c:v','libx264','-pix_fmt','yuv420p',
             '-crf','20','-c:a','aac','-b:a','192k','-shortest','-movflags','+faststart',str(OUT/'lip-sync-preview.mp4')]
    process=subprocess.Popen(command,stdin=subprocess.PIPE)
    for profile,mood,face,body,frames,clip in scenes:
        for i in range(frames):
            seconds=i/fps;im=Image.new('RGB',(720,440),'#e9e9e6');draw=ImageDraw.Draw(im)
            label=profile.replace('Female',' Female').replace('Male',' Male')
            draw.text((30,24),label+' / '+mood,fill='#283e50')
            shape='A'
            for time,value in clip['mouthCues']:
                if time>seconds*1000:break
                shape=value
            if seconds*1000>=clip['audioMs']:shape='A'
            if mood=='Laugh' and shape in 'BCD':shape='Smile_'+shape
            composed=copy.deepcopy(face)
            pose=json.loads((ROOT/f'src/main/resources/Common/Characters/Animations/Aetherhaven/Life/Mouth/{shape}.blockyanim').read_text())
            composed['nodeAnimations'].update(pose['nodeAnimations'])
            im.paste(render_face(composed,seconds).resize((300,315),Image.Resampling.NEAREST),(24,65))
            im.paste(render_body(body,min(1,seconds/(body['duration']/60)),(280,370)),(388,35))
            draw.text((30,407),'Exported animation preview with the actual recording',fill='#526579')
            process.stdin.write(im.tobytes())
    process.stdin.close()
    if process.wait()!=0:raise RuntimeError('Preview video encoding failed')
    print(f'Created synchronized lip-sync-preview.mp4 with all {len(cast)} profiles.')

if __name__=='__main__':main()
