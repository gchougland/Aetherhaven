"""Read original item meshes, scale and held-idle grips without modifying assets."""
import copy,json
from functools import lru_cache
from pathlib import Path
import numpy as np
from villager_life_ik import matrix
from villager_life_props import quaternion
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT.parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets'
RES=ROOT/'src/main/resources'
ITEMS={'Read':'Weapon_Spellbook_Grimoire_Brown','ReadLoop':'Weapon_Spellbook_Grimoire_Brown',
 'Sweep':'Halloween_Broomstick','Craft':'Tool_Hammer_Iron','Tend':'Plant_Flower_Bushy_Blue',
 'Mix':'Food_Salad_Caesar'}
def merge(a,b):
    a=copy.deepcopy(a)
    for k,v in b.items():a[k]=merge(a[k],v) if isinstance(v,dict) and isinstance(a.get(k),dict) else v
    return a
@lru_cache(None)
def item(item_id):
    for base in (RES,ASSETS):
        found=list((base/'Server/Item/Items').rglob(item_id+'.json'))
        if found:
            d=json.loads(found[0].read_text(encoding='utf-8-sig'))
            return merge(item(d['Parent']),d) if 'Parent' in d else d
    raise FileNotFoundError(item_id)
@lru_cache(None)
def animation_table(name):
    d=json.loads((ASSETS/f'Server/Item/Animations/{name}.json').read_text())
    return merge(animation_table(d['Parent']),d) if 'Parent' in d else d
@lru_cache(None)
def geometry(item_id):
    d=item(item_id);block=d.get('BlockType',{})
    path=d.get('Model') or block['CustomModel']
    texture=d.get('Texture') or block['CustomModelTexture'][0]['Texture']
    base=RES if (RES/'Common'/path).exists() else ASSETS
    model=json.loads((base/'Common'/path).read_text());nodes=model['nodes']
    roots=[n for n in nodes if n['name'] in ('R-Attachment','L-Attachment')]
    nodes=roots[0].get('children',[]) if roots else nodes
    return nodes,texture,d.get('Scale',1)*block.get('CustomModelScale',1)
def grips(item_id,side='R'):
    d=item(item_id);table=animation_table(d.get('PlayerAnimationsId','Item'))
    idle=json.loads((ASSETS/'Common'/table['Animations']['Idle']['ThirdPerson']).read_text())
    channels=idle['nodeAnimations'].get(side+'-Attachment',{}) if side=='R' else {}
    # Reuse the existing vanilla held-idle grip exactly; no per-emote adjustments.
    pos=channels.get('position') or [{'delta':dict.fromkeys('xyz',0)}]
    ori=channels.get('orientation') or [{'delta':dict(x=0,y=0,z=0,w=1)}]
    return np.array([pos[0]['delta'][k] for k in 'xyz']),matrix(ori[0]['delta'])
def grip_tracks(item_id,duration,side='R'):
    p,r=grips(item_id,side)
    return {'position':[{'time':t,'delta':dict(zip('xyz',p.tolist())),'interpolationType':'smooth'} for t in (0,duration)],
       'orientation':[{'time':t,'delta':quaternion(r),'interpolationType':'smooth'} for t in (0,duration)]}

def animated_parts(item_id,tracks=None,time=0):
    from villager_life_ik import interpolate
    nodes,_,scale=geometry(item_id);out=[]
    def visit(ns,r,p):
        for n in ns:
            pos=p+r@np.array([n.get('position',{}).get(k,0) for k in 'xyz'])
            rot=r@matrix(n.get('orientation',{}))
            ch=(tracks or {}).get(n['name'],{})
            if ch.get('orientation'):rot=rot@matrix(interpolate(ch['orientation'],time))
            sh=copy.deepcopy(n.get('shape',{}));pos+=rot@np.array([sh.get('offset',{}).get(k,0) for k in 'xyz'])
            if sh.get('type') in ('quad','box'):
                sh['offset']=dict.fromkeys('xyz',0)
                sh['stretch']={k:sh.get('stretch',{}).get(k,1)*scale for k in 'xyz'}
                out.append(dict(name=n['name'],position=dict(zip('xyz',(pos*scale).tolist())),orientation=quaternion(rot),shape=sh))
            visit(n.get('children',[]),rot,pos)
    visit(nodes,np.eye(3),np.zeros(3));return out
