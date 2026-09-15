"""Continuous reading and an original chin-resting ponder, with no leg tracks."""
import copy,json,math
from villager_life_ik import interpolate

def generate_loops(res,write):
    common=res/'Common/Characters/Animations/Aetherhaven/Life'
    source=json.loads((common/'Read.blockyanim').read_text())
    loop=copy.deepcopy(source);loop['duration']=360
    for name,channels in loop['nodeAnimations'].items():
        for channel,keys in channels.items():
            if not keys:continue
            original=source['nodeAnimations'][name][channel]
            frames=[]
            for frame in range(0,361,3):
                sample=source['duration']*(.36+.36*(1-math.cos(frame/360*2*math.pi))/2)
                if channel=='orientation':delta=interpolate(original,sample)
                else:
                    left=original[0];right=original[-1]
                    for a,b in zip(original,original[1:]):
                        if sample<=b['time']:left,right=a,b;break
                    t=max(0,min(1,(sample-left['time'])/max(1,right['time']-left['time'])))
                    t=t*t*(3-2*t)
                    delta={k:v*(1-t)+right['delta'][k]*t for k,v in left['delta'].items()}
                frames.append({'time':frame,'delta':delta,'interpolationType':'smooth'})
            channels[channel]=frames
    write(common/'ReadLoop.blockyanim',loop)
    face=json.loads((common/'Faces/Read.blockyanim').read_text())
    write(common/'Faces/ReadLoop.blockyanim',face)
    # Inspect already raises one hand thoughtfully. The Ponder variant carries no prop.
    ponder=json.loads((common/'Inspect.blockyanim').read_text())
    for bone in ['LifePropRoot','R-Attachment']:ponder['nodeAnimations'].pop(bone,None)
    write(common/'Ponder.blockyanim',ponder)
    write(common/'Faces/Ponder.blockyanim',json.loads((common/'Faces/Inspect.blockyanim').read_text()))
    path=res/'Server/Item/Animations/Aetherhaven_Life_Actions.json';actions=json.loads(path.read_text())
    for name,duration in [('ReadLoop',360),('Ponder',ponder['duration'])]:
        actions['Animations'][name]={'ThirdPerson':f'Characters/Animations/Aetherhaven/Life/{name}.blockyanim',
            'ThirdPersonMoving':f'Characters/Animations/Aetherhaven/Life/{name}.blockyanim',
            'ThirdPersonFace':f'Characters/Animations/Aetherhaven/Life/Faces/{name}.blockyanim',
            'Speed':1,'Looping':name=='ReadLoop','BlendingDuration':.65 if name=='ReadLoop' else .3}
    write(path,actions)
    path=res/'defaults/villager_life_timing.json';timing=json.loads(path.read_text());timing.update(ReadLoop=6000,Ponder=ponder['duration']*1000//60);write(path,timing)
