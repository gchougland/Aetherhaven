"""Native Hytale meshes/atlases with fixed grip pivots; one newly modeled wooden spoon."""
import copy
import json
import re
import numpy as np
from villager_life_ik import matrix


def flattened(nodes, quat, rotation=None, origin=None, scale=1):
    """Bake a mesh hierarchy without discarding its UVs, quads, painted detail or shading."""
    rotation=np.eye(3) if rotation is None else rotation
    origin=np.zeros(3) if origin is None else origin
    result=[]
    for node in nodes:
        r=rotation@matrix(node.get('orientation',{}));shape=copy.deepcopy(node.get('shape',{}))
        pos=origin+rotation@np.array([node.get('position',{}).get(k,0) for k in 'xyz'])
        pos+=r@np.array([shape.get('offset',{}).get(k,0) for k in 'xyz'])
        if shape.get('type') in ('box','quad'):
            shape['offset']=dict.fromkeys('xyz',0)
            shape['stretch']={k:shape.get('stretch',{}).get(k,1)*scale for k in 'xyz'}
            shape['shadingMode']='standard'
            result.append({'name':node['name'],'position':dict(zip('xyz',(pos*scale).tolist())),
                           'orientation':quat(r),'shape':shape,'children':[]})
        result+=flattened(node.get('children',[]),quat,r,pos,scale)
    return result


def generate(res,ik,quat,write):
    """Only the genuinely new spoon ships here. All other activities use native IDs.

    Its existing model/grip is preserved, not rebuilt to compensate for a pose.
    """
    path=res/'Server/Item/Items/Aetherhaven/Life/Aetherhaven_Life_Prop_Spoon.json'
    assert path.is_file(), 'The original spoon asset must be present'
    spoon=json.loads(path.read_text())
    spoon['Utility']={'Usable':True,'Compatible':True}
    write(path,spoon)
    mesh_path=res/'Common/Items/Aetherhaven/Life/Spoon.blockymodel'
    mesh=json.loads(mesh_path.read_text())
    # Bind the existing spoon geometry to the offhand, without changing its grip.
    mesh['nodes'][0]['name']='L-Attachment'
    write(mesh_path,mesh)
    # Articulate the item's own mesh through Item.Animation, as native animated
    # staves/weapons do. NPC character action channels did not open this mesh.
    # Retain the original ID, gameplay definition, model, scale, texture and grip.
    assets=res.parents[3]/'HytaleSourceCode/hytale-shared-source/HytaleAssets'
    book=json.loads((assets/'Server/Item/Items/Weapon/Spellbook/Weapon_Spellbook_Grimoire_Brown.json').read_text())
    # Item.Animation has different allowed roots from ItemAnimation.FirstPerson.
    # Keep the existing character timelines for cross-mod action references;
    # this tiny static pose belongs to the item mesh, independent of action time.
    book['Animation']='Items/Animations/Aetherhaven/Life/Book_Open.blockyanim'
    write(res/'Common'/book['Animation'],{
        'formatVersion':1,'duration':1,'holdLastKeyframe':True,
        'nodeAnimations':{bone:{'orientation':[
            {'time':time,'delta':quat((0,0,angle)),'interpolationType':'smooth'}
            for time in (0,1)]} for bone,angle in [('Book-Top',75),('Book-Bot',-75)]}})
    write(res/'Server/Item/Items/Weapon/Spellbook/Weapon_Spellbook_Grimoire_Brown.json',book)
