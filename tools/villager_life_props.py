"""Original held prop geometry and baked attachment transforms on the Player rig."""
import copy
import json
import numpy as np
from PIL import Image
from villager_life_ik import matrix, interpolate

PROPS={'Read':'OpenBook','Sweep':'Broom','Craft':'Mallet','Inspect':'Stone','Tend':'Plant'}
BOOK_PALM_OFFSET = 5.0
BOOK_HALF_GRIP = 14.0

def quaternion(m):
    # Stable conversion using the largest diagonal component.
    candidates=np.array([1+m[0,0]-m[1,1]-m[2,2],1-m[0,0]+m[1,1]-m[2,2],
                         1-m[0,0]-m[1,1]+m[2,2],1+np.trace(m)])
    i=int(np.argmax(candidates));q=np.zeros(4);q[i]=np.sqrt(max(0,candidates[i]))/2
    d=4*q[i]
    if i==3:q[:3]=[(m[2,1]-m[1,2])/d,(m[0,2]-m[2,0])/d,(m[1,0]-m[0,1])/d]
    elif i==0:q[1:]=[(m[0,1]+m[1,0])/d,(m[0,2]+m[2,0])/d,(m[2,1]-m[1,2])/d]
    elif i==1:q[[0,2,3]]=[(m[0,1]+m[1,0])/d,(m[1,2]+m[2,1])/d,(m[0,2]-m[2,0])/d]
    else:q[[0,1,3]]=[(m[0,2]+m[2,0])/d,(m[1,2]+m[2,1])/d,(m[1,0]-m[0,1])/d]
    q/=np.linalg.norm(q)
    if q[3]<0:q=-q
    return dict(zip('xyzw',[round(float(v),7) for v in q]))

def desired(gesture,world,phase=0):
    chest=world['Chest'][0];hand=world['R-Hand'][1]
    if gesture=='Read':
        rotation=world['R-Hand'][0]
        return rotation,hand+rotation@np.array([BOOK_HALF_GRIP,BOOK_PALM_OFFSET,0])
    if gesture=='Sweep':
        up=np.array([-9,12,-4],dtype=float);up/=np.linalg.norm(up)
        carry=np.array([-.2,.3,-.93]);carry/=np.linalg.norm(carry)
        # Keep the broom tipped outward until the hand has risen far enough.
        blend=min(1,max(0,(phase-.18)/.16),max(0,(.97-phase)/.16));blend=blend*blend*(3-2*blend)
        up=carry*(1-blend)+up*blend;up/=np.linalg.norm(up)
        right=np.cross(up,[0,0,1]);right/=np.linalg.norm(right)
        rotation=chest@np.column_stack([right,up,np.cross(right,up)])
        return rotation,hand
    return chest,hand

def local_transform(gesture,world,phase=0):
    parent,origin=world['R-Attachment'];rotation,position=desired(gesture,world,phase)
    return parent.T@rotation,parent.T@(position-origin)

def bake(gesture,tracks,duration,ik):
    if gesture not in PROPS:return tracks
    # Item/Idle adds (2,-2,2.1) to R-Attachment. An absent channel inherits that
    # translation and moves the book away from the second palm in the client.
    tracks=copy.deepcopy(tracks)
    tracks['R-Attachment']={
        'position':[{'time':t,'delta':{'x':0,'y':0,'z':0},'interpolationType':'smooth'} for t in (0,duration)],
        'orientation':[{'time':t,'delta':{'x':0,'y':0,'z':0,'w':1},'interpolationType':'smooth'} for t in (0,duration)]}
    tracks.setdefault('R-Hand',{'orientation':[{'time':t,'delta':{'x':0,'y':0,'z':0,'w':1},
                                               'interpolationType':'smooth'} for t in (0,duration)]})
    if gesture=='Read':
        for bone in ('L-Shoulder','R-Shoulder','L-Hand'):
            tracks.setdefault(bone,{'orientation':[{'time':t,'delta':{'x':0,'y':0,'z':0,'w':1},
                                                     'interpolationType':'smooth'} for t in (0,duration)]})
        # The book is a rigid held item. Move the wrists and supporting arm to it,
        # rather than relying on animation of an item-only node to counter-rotate it.
        original=copy.deepcopy(tracks)
        for bone in ('R-Hand','L-Arm','L-Forearm'):
            tracks[bone]={'orientation':[]}
        seed=None
        for frame in sorted(set(range(0,duration+1,3))|{duration}):
            phase=frame/duration
            blend=min(1,max(0,(phase-.12)/.20),max(0,(1-phase)/.18))
            blend=blend*blend*(3-2*blend)
            world=ik.fk(original,frame)
            desired=world['R-Forearm'][0].T@world['Chest'][0]
            start=interpolate(original['R-Hand']['orientation'],frame)
            # Quaternion interpolation leaves the entry and exit pose unchanged.
            wrist=interpolate([{'time':0,'delta':start},{'time':1,'delta':quaternion(desired)}],blend)
            work=copy.deepcopy(original)
            work['R-Hand']={'orientation':[{'time':frame,'delta':wrist}]}
            world=ik.fk(work,frame)
            target=world['R-Hand'][1]+world['R-Hand'][0]@np.array([2*BOOK_HALF_GRIP,0,0])
            if seed is None:seed=[-30,0,10,-65,0]
            seed,error=ik.solve('L',target,world,seed,matrix(interpolate(original['L-Hand']['orientation'],frame)))
            tracks['R-Hand']['orientation'].append({'time':frame,'delta':wrist,'interpolationType':'smooth'})
            for bone,angles in [('L-Arm',seed[:3]),('L-Forearm',[seed[3],seed[4],0])]:
                q=interpolate([{'time':0,'delta':interpolate(original[bone]['orientation'],frame)},
                               {'time':1,'delta':ik.quat(angles)}],blend)
                tracks[bone]['orientation'].append({'time':frame,'delta':q,'interpolationType':'smooth'})
    rest_rot,rest_pos=local_transform(gesture,ik.fk({},0))
    root={'orientation':[],'position':[]}
    for frame in sorted(set(range(0,duration+1,3))|{duration}):
        rotation,position=local_transform(gesture,ik.fk(tracks,frame),frame/duration)
        root['orientation'].append({'time':frame,'delta':quaternion(rest_rot.T@rotation),'interpolationType':'smooth'})
        root['position'].append({'time':frame,'delta':dict(zip('xyz',[round(float(v),6) for v in position-rest_pos])), 'interpolationType':'smooth'})
    # Exactly neutral endpoints avoid small accumulated floating-point deltas.
    for frame in (0,-1):
        root['orientation'][frame]['delta']={'x':0,'y':0,'z':0,'w':1}
        root['position'][frame]['delta']={'x':0,'y':0,'z':0}
    return tracks|{'LifePropRoot':root}

def generate(res,ik,quat,write):
    assets=ik.assets
    book=json.loads((assets/'Common/Items/Weapons/Spellbook/Book.blockymodel').read_text())
    nodes={}
    def collect(ns):
        for n in ns:nodes[n['name']]=n;collect(n.get('children',[]))
    collect(book['nodes'])
    cover=nodes['Book-Bot']['shape']['textureLayout']
    page_uv=next(iter(nodes['Page-Bot']['shape']['textureLayout'].values()))
    pages={side:copy.deepcopy(page_uv) for side in ('front','back','left','right','top','bottom')}
    texture='Items/Weapons/Spellbook/Grimoire_Brown_Texture.png'
    def box(name,pos,size,angle=(0,0,0),paper=False):
        return {'id':name,'name':'Life_'+name,'position':dict(zip('xyz',pos)),'orientation':quat(angle),
                'shape':{'type':'box','offset':{'x':0,'y':0,'z':0},'stretch':{'x':1,'y':1,'z':1},
                         'settings':{'size':dict(zip('xyz',size))},'textureLayout':copy.deepcopy(pages if paper else cover),
                         'unwrapMode':'custom','visible':True,'doubleSided':False,'shadingMode':'flat'},'children':[]}
    shapes={
        'OpenBook':[box('Spine',(0,-1,0),(2,2,18)),
                    box('CoverLeft',(-6,-.5,0),(12,1,18),(0,0,-8)),box('CoverRight',(6,-.5,0),(12,1,18),(0,0,8)),
                    box('PagesLeft',(-6,.5,0),(10.5,1.4,16),(0,0,-8),True),box('PagesRight',(6,.5,0),(10.5,1.4,16),(0,0,8),True)],
        'Broom':[box('Shaft',(0,-31,0),(3,88,3)),box('Bristles',(0,-76,0),(18,12,7)),box('Binding',(0,-69,0),(14,3,6))],
        'Mallet':[box('Handle',(0,3,0),(3,18,3)),box('MalletHead',(0,14,0),(12,6,6))],
        'Spoon':[box('Handle',(0,3,0),(2,18,2)),box('SpoonBowl',(0,14,0),(6,7,2))],
        'Stone':[box('PolishedStone',(0,2,0),(7,8,7),(0,45,0),True)],
        'Plant':[box('Pot',(0,2,0),(8,6,8)),box('Stem',(0,10,0),(1,12,1)),
                 box('LeafLeft',(-3,13,0),(6,2,4),(0,0,-25),True),box('LeafRight',(3,16,0),(6,2,4),(0,0,25),True)],
    }
    # Keep full native atlas regions, including leather grain, page writing and
    # painted edge shadows. Texture size and geometric size are independent.
    def texture_book(part, native, layout):
        shape=part['shape'];physical=shape['settings']['size']
        shape['stretch']={axis:physical[axis]/native[i] for i,axis in enumerate('xyz')}
        shape['settings']['size']=dict(zip('xyz',native))
        shape['textureLayout']=copy.deepcopy(layout)
        shape['shadingMode']='standard'
    def uv(x,y,angle=0):
        return {'offset':{'x':x,'y':y},'mirror':{'x':False,'y':False},'angle':angle}
    sides=('front','back','left','right','top','bottom')
    for part in shapes['OpenBook']:
        for axis in ('x','z'):
            factor=1.6 if axis=='x' else 1.4
            part['position'][axis]*=factor
            part['shape']['settings']['size'][axis]*=factor
        name=part['name']
        if 'Cover' in name:
            layout=copy.deepcopy(nodes['Book-Bot']['shape']['textureLayout'])
            layout['left']=uv(21,59)
            # Both exposed covers use the painted leather panel.
            layout['bottom']=uv(1,29)
            texture_book(part,(20,4,26),layout)
        elif 'Pages' in name:
            layout={s:uv(1,59) for s in sides}
            layout['top']=copy.deepcopy(nodes['Page-Top']['shape']['textureLayout']['front'])
            layout['top']['mirror']['x']='Left' in name
            layout['bottom']=uv(25,29)
            texture_book(part,(18,2,26),layout)
        else:
            texture_book(part,(2,2,18),{s:uv(21,32) for s in sides})
    for sign in (-1,1):
        tilt=matrix(quat((0,0,sign*8)))
        for z in (-11.5,11.5):
            pos=np.array([sign*9.6,-.5,0])+tilt@np.array([sign*8.4,.62,z])
            corner=box(f'Corner{sign}_{z}',pos.tolist(),(1.3,.3,1.8),(0,0,sign*8))
            texture_book(corner,(2,2,2),{s:uv(16,59) for s in sides})
            shapes['OpenBook'].append(corner)
    ribbon=box('Bookmark',(0,.3,7.5),(1.2,.18,12))
    texture_book(ribbon,(2,1,12),{s:uv(1,30) for s in sides})
    shapes['OpenBook'].append(ribbon)
    labels={'OpenBook':'Open book','Broom':'Broom','Mallet':'Crafting mallet','Spoon':'Wooden spoon','Stone':'Polished stone','Plant':'Small plant'}
    for prop,parts in shapes.items():
        prop_texture = 'Items/Halloween_Props/Broomstick_Texture.png' if prop=='Broom' else 'Blocks/Decorative_Sets/Village/Planter_Texture.png' if prop=='Plant' else texture
        atlas=np.asarray(Image.open(assets/'Common'/prop_texture).convert('RGBA'))
        opaque=atlas[:,:,3]>250
        for part in parts:
            if prop=='OpenBook':
                continue
            name=part['name']
            color='#ebe0c3' if 'Pages' in name else '#6c9a58' if 'Leaf' in name or 'Stem' in name else '#69b9cd' if 'Stone' in name else '#c3a569' if 'Bristles' in name else '#59432c' if 'Ink' in name else '#926449'
            target=np.array([int(color[i:i+2],16) for i in (1,3,5)])
            distances=np.sum((atlas[:,:,:3].astype(float)-target)**2,axis=2)
            distances[~opaque]=np.inf
            y,x=np.unravel_index(np.argmin(distances),distances.shape)
            # Unit UV faces sample a real opaque pigment from the base-game atlas.
            # Shape stretch supplies geometry size, preventing long shafts from
            # reading outside the texture bounds or into transparent atlas regions.
            shape=part['shape'];shape['stretch']=shape['settings']['size'];shape['settings']['size']={'x':1,'y':1,'z':1}
            uv={'offset':{'x':int(x),'y':int(y)},'mirror':{'x':False,'y':False},'angle':0}
            shape['textureLayout']={side:copy.deepcopy(uv) for side in ('front','back','left','right','top','bottom')}
        gesture=next((g for g,p in PROPS.items() if p==prop),'Craft')
        rotation,position=local_transform(gesture,ik.fk({},0))
        none={'type':'none','offset':{'x':0,'y':0,'z':0},'stretch':{'x':1,'y':1,'z':1},
              'settings':{'isPiece':True},'textureLayout':{},'visible':True,'doubleSided':False,'shadingMode':'flat'}
        root={'id':'1','name':'R-Attachment','position':{'x':0,'y':0,'z':0},'orientation':quat((0,0,0)),
              'shape':none,'children':[{'id':'2','name':'LifePropRoot','position':dict(zip('xyz',[float(v) for v in position])),
                                       'orientation':quaternion(rotation),'shape':copy.deepcopy(none),'children':parts}]}
        for index,part in enumerate(parts,3):part['id']=str(index)
        model=f'Items/Aetherhaven/Life/{prop}.blockymodel'
        write(res/'Common'/model,{'nodes':[root],'lod':'auto'})
        write(res/f'Server/Item/Items/Aetherhaven/Life/Aetherhaven_Life_Prop_{prop}.json',{
            'TranslationProperties':{'Name':f'aetherhaven_life.items.Aetherhaven_Life_Prop_{prop}.name'},
            'Model':model,'Texture':prop_texture,'PlayerAnimationsId':'Item','MaxStack':1,'Quality':'Common',
            'Icon':'Icons/ItemsGenerated/Weapon_Spellbook_Grimoire_Brown.png' if prop=='OpenBook' else 'Icons/ItemsGenerated/Halloween_Broomstick.png',
            'Tags':{'Type':['AetherhavenLifeProp']}})
    lang=res/'Server/Languages/en-US/aetherhaven_life.lang'
    lang.write_text(''.join(f'items.Aetherhaven_Life_Prop_{k}.name = {v}\n' for k,v in labels.items()),encoding='utf-8')
