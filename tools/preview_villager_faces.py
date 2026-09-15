"""Schematic face preview driven by exported curves and the local mouth atlas.

This is a curve review, not an in-game render; reference textures stay in build/.
"""
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from preview_villager_life import ROOT, ASSETS, ANIMS, OUT, sample

ATLAS=Image.open(ASSETS/'Common/Characters/Body_Attachments/Mouths/Mouth1_Textures/Default_Greyscale.png').convert('RGBA')

def value(frames,time,default):
    if not frames:return default
    if time<=frames[0]['time']:return frames[0]['delta']
    for a,b in zip(frames,frames[1:]):
        if time<=b['time']:
            if 'interpolationType' not in a:return a['delta']
            t=(time-a['time'])/(b['time']-a['time']);t=t*t*(3-2*t)
            return {k:a['delta'][k]*(1-t)+b['delta'][k]*t for k in a['delta']}
    return frames[-1]['delta']

def render(anim,seconds):
    im=Image.new('RGB',(200,210),'#e9e9e6');d=ImageDraw.Draw(im)
    d.rounded_rectangle((29,21,171,171),radius=34,fill='#dcaa83',outline='#745444',width=2)
    tracks=anim['nodeAnimations'];time=min(anim['duration'],seconds*60)
    for side,cx in [('R',69),('L',131)]:
        eye=tracks[side+'-Eye'];gaze=value(eye['position'],time,{'x':0,'y':0})
        pupil=value(eye['shapeStretch'],time,{'x':1,'y':1})
        upper=value(tracks[side+'-Eyelid']['shapeStretch'],time,{'y':1})['y']
        lower=value(tracks[side+'-Eyelid-Bot']['shapeStretch'],time,{'y':1})['y']
        top=78+max(0,upper-1)*1.8;bottom=109-max(0,lower-1)*1.3
        d.rounded_rectangle((cx-16,78,cx+16,109),radius=12,fill='#fff9ed')
        px=cx+gaze['x']*4;py=94-gaze['y']*4;r=8*pupil['x']
        d.ellipse((px-r,py-r,px+r,py+r),fill='#314a50')
        d.rectangle((cx-17,77,cx+17,top),fill='#dcaa83')
        d.rectangle((cx-17,bottom,cx+17,110),fill='#dcaa83')
        brow=tracks[side+'-Eyebrow'];height=value(brow['position'],time,{'y':0})['y']
        q=sample(brow['orientation'],time);angle=2*math.atan2(q['z'],q['w'])
        y=66-height*3;dy=math.sin(angle)*16
        d.line((cx-15,y+dy,cx+15,y-dy),fill='#63483e',width=5)
    uv=value(tracks['Mouth']['shapeUvOffset'],time,{'x':0,'y':0})
    stretch=value(tracks['Mouth']['shapeStretch'],time,{'x':1,'y':1})
    x,y=int(uv['x']),int(-uv['y'])
    mouth=ATLAS.crop((x,y,x+20,y+10)).resize((round(68*stretch['x']),round(34*stretch['y'])),Image.Resampling.NEAREST)
    im.paste(mouth,(100-mouth.width//2,131-mouth.height//2),mouth)
    return im

if __name__=='__main__':
    names=['Talking_Story','Read','Laugh','Sleepy','Bored','Hungry']
    data={n:json.loads((ANIMS/'Faces'/f'{n}.blockyanim').read_text()) for n in names}
    frames=[]
    for index in range(91):
        sheet=Image.new('RGB',(600,420),'#e9e9e6')
        for i,name in enumerate(names):
            tile=render(data[name],index/15)
            ImageDraw.Draw(tile).text((20,185),name.replace('Talking_','Talking '),fill='#303b4b')
            sheet.paste(tile,((i%3)*200,(i//3)*210))
        frames.append(sheet)
    frames[0].save(OUT/'faces.gif',save_all=True,append_images=frames[1:],duration=67,loop=0)
    frames[30].save(OUT/'faces.png')
    print('Rendered schematic face curve preview.')
