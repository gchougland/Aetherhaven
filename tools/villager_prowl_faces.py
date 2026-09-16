"""Use the standard player mouth atlas at Prowl's original mouth placement."""
import copy,json

def generate(res,write,pool=None):
    mesh_path=res/'Common/NPC/Prowl/prowl_hytale.blockymodel'
    attachment_path=res/'Common/NPC/Prowl/Player_Mouth.blockymodel'
    mesh=json.loads(mesh_path.read_text())
    def walk(nodes):
        for node in nodes:
            yield node
            yield from walk(node.get('children',[]))
    anchor=next(n for n in walk(mesh['nodes']) if n['name']=='Mouth-Attachment')
    old=next((n for n in anchor.get('children',[]) if n['name']=='Mouth'),None)
    if old is not None:
        mouth=copy.deepcopy(old)
        mouth['shape']['settings']['size']={'x':20,'y':10}
        mouth['shape']['stretch']={'x':.9,'y':.8,'z':1}
        mouth['shape']['textureLayout']['front']['offset']={'x':0,'y':0}
        root=copy.deepcopy(anchor);root['children']=[mouth]
        write(attachment_path,{'lod':'auto','nodes':[root]})
        anchor['children']=[n for n in anchor['children'] if n['name']!='Mouth']
        write(mesh_path,mesh)
    assert attachment_path.is_file()
    # Attachment roots must be pieces, like the native Mouth1 attachment. A
    # copied skeleton node has isPiece=false and is not mounted as a mouth.
    attachment=json.loads(attachment_path.read_text())
    attachment['nodes'][0]['shape']['settings']['isPiece']=True
    write(attachment_path,attachment)
    model_path=res/'Server/Models/Townsfolk/Prowl.json'
    model=json.loads(model_path.read_text())
    model['DefaultAttachments']=[{'Model':'NPC/Prowl/Player_Mouth.blockymodel',
        'Texture':'Characters/Body_Attachments/Mouths/Mouth1_Textures/Default_Greyscale.png',
        'GradientSet':'Skin','GradientId':'09'}]
    # All authored eye/brow/mouth tracks now come from the Human parent.
    model['AnimationSets']={k:v for k,v in model.get('AnimationSets',{}).items()
                           if k not in ('Talk','Talk2','Talk3','Talk4','Talk5','Grin','Frown')
                           and not k.startswith('Aetherhaven_Life_')
                           and not any('/ProwlFaces/' in a.get('Animation','') for a in v.get('Animations',[]))}
    write(model_path,model)
    for pitch in ('','_Lower','_Higher'):
        write(res/f'Server/Item/Animations/Aetherhaven_Life_Actions{pitch}_Prowl.json',
              {'Parent':'Aetherhaven_Life_Actions'})
    print('Prowl shares all Human face and mouth animations.',flush=True)
