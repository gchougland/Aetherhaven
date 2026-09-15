"""Mux the actual exported body/face timelines with the selected shipped audio."""
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/life-tools'))
import json
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
          ('GravelyMale','Talk','Explain'),('GravelyFemale','Gasp','Surprise')]
    index=json.loads((ROOT/'src/main/resources/defaults/villager_life_playback.json').read_text())
    scenes=[];sound=[];rate=None;fps=30
    for profile,mood,gesture in cast:
        clip=index[profile+'_'+mood][0];a,sr=sf.read(ROOT/f'src/main/resources/Common/Sounds/Aetherhaven/Life/{clip["clip"]}.ogg')
        if rate is None:rate=sr
        assert rate==sr
        face=json.loads((ANIMS/f'LipSync/{gesture}_{clip["clip"]}.blockyanim').read_text())
        body=json.loads((ANIMS/f'{gesture}.blockyanim').read_text());body['previewName']=gesture
        frames=math.ceil((face['duration']/60+.25)*fps)
        samples=round(frames/fps*rate);a=np.pad(a,(0,max(0,samples-len(a))))[:samples]
        sound.append(a);scenes.append((profile,mood,face,body,frames))
    audio_path=OUT/'lip-sync-preview.wav';sf.write(audio_path,np.concatenate(sound),rate)
    command=[imageio_ffmpeg.get_ffmpeg_exe(),'-y','-loglevel','error','-f','rawvideo','-pix_fmt','rgb24',
             '-s','720x440','-r',str(fps),'-i','pipe:0','-i',str(audio_path),'-c:v','libx264','-pix_fmt','yuv420p',
             '-crf','20','-c:a','aac','-b:a','192k','-shortest','-movflags','+faststart',str(OUT/'lip-sync-preview.mp4')]
    process=subprocess.Popen(command,stdin=subprocess.PIPE)
    for profile,mood,face,body,frames in scenes:
        for i in range(frames):
            seconds=i/fps;im=Image.new('RGB',(720,440),'#e9e9e6');draw=ImageDraw.Draw(im)
            label=profile.replace('Female',' Female').replace('Male',' Male')
            draw.text((30,24),label+' / '+mood,fill='#283e50')
            im.paste(render_face(face,seconds).resize((300,315),Image.Resampling.NEAREST),(24,65))
            im.paste(render_body(body,min(1,seconds/(body['duration']/60)),(280,370)),(388,35))
            draw.text((30,407),'Exported animation preview with the actual recording',fill='#526579')
            process.stdin.write(im.tobytes())
    process.stdin.close()
    if process.wait()!=0:raise RuntimeError('Preview video encoding failed')
    print('Created synchronized lip-sync-preview.mp4 with all eight profiles.')

if __name__=='__main__':main()
