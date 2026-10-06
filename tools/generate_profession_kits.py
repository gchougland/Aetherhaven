"""Original profession equipment, atlas textures and icons at native item texel density."""
import json, math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from villager_prop_preview import textured_faces, world_shapes
from painted_asset_materials import painted_patch
from check_asset_assembly import separate_trim_faces, validate
from compact_atlas_rows import crop_unused_rows

ROOT = Path(__file__).resolve().parents[1]
COMMON = ROOT / 'src/main/resources/Common'
SERVER = ROOT / 'src/main/resources/Server'
OUT = COMMON / 'Items/Aetherhaven/Profession_Kits'
OUT.mkdir(parents=True, exist_ok=True)

def ledger_grip(model):
    """Mount the original closed book in the native spellbook attachment frame.

    These empty pivots follow Grimoire.blockymodel; no native mesh or texture is
    copied. Keep the icon's presentation separate from the in-hand transform.
    """
    if any(n['name'] == 'R-Attachment' for n in model['nodes']):
        return model
    def pivot(number, name, position, orientation, children):
        return dict(id=str(number), name=name, position=dict(zip('xyz', position)),
                    orientation=dict(zip('xyzw', orientation)), children=children,
                    shape=dict(type='none', offset=dict.fromkeys('xyz', 0),
                               stretch=dict.fromkeys('xyz', 1), settings=dict(isPiece=False)))
    body = pivot(1005, 'Ledger', (10, 0, 0), (-math.sqrt(.5), 0, 0, math.sqrt(.5)), model['nodes'])
    handle = pivot(1004, 'Handle', (-2, 0, -8), (.5, -.5, .5, .5), [body])
    origin = pivot(1003, 'Origin_Item', (0, -9.192388, .707107), (-.382683, 0, 0, .92388), [handle])
    projectile = pivot(1002, 'Origin_Projectile', (2, 7, 14), (.382683, 0, 0, .92388), [origin])
    model['nodes'] = [pivot(1001, 'R-Attachment', (-2, 13, -8), (0, 0, 0, 1), [projectile])]
    return model

def make(kind,block=False):
    atlas=Image.new('RGBA',(128,128)); nodes=[]; patches={}; cursor=[0,0,0]
    def patch(w,h,material):
        key=(w,h,material)
        if key in patches:return patches[key]
        x,y,row=cursor
        if x+w+1>128:x=0;y+=row+1;row=0
        assert y+h<=128
        im=painted_patch(w,h,material)
        atlas.paste(im,(x,y));result={'offset':{'x':x,'y':y},'mirror':{'x':False,'y':False},'angle':0}
        patches[key]=result;cursor[:]=[x+w+1,y,max(row,h)];return result
    def box(name,center,size,mat):
        sx,sy,sz=size
        layout={side:patch(w,h,mat) for side,(w,h) in {'front':(sx,sy),'back':(sx,sy),'left':(sz,sy),'right':(sz,sy),'top':(sx,sz),'bottom':(sx,sz)}.items()}
        nodes.append({'id':str(len(nodes)+1),'name':name,'position':dict(zip('xyz',center)),
            'orientation':{'x':0,'y':0,'z':0,'w':1},'shape':{'type':'box','offset':dict.fromkeys('xyz',0),
            'stretch':dict.fromkeys('xyz',1),'settings':{'isPiece':False,'size':dict(zip('xyz',size))},
            'textureLayout':layout,'unwrapMode':'custom','visible':True,'doubleSided':False,'shadingMode':'standard'}})
    if kind=='PatternCabinet':
        for x in (-11,11):box('Cabinet foot',(x,2,0),(5,4,18),'wood')
        box('Cabinet carcass',(0,17,0),(26,26,18),'wood')
        box('Crown',(0,31,0),(28,2,20),'wood')
        for y in (9,17,25):
            box('Pattern drawer',(0,y,9.5),(22,6,1),'dark')
            box('Drawer face',(0,y,10.5),(20,5,1),'wood')
            box('Iron pull plate',(0,y,11.5),(5,2,1),'iron')
            box('Iron handle',(0,y,12.5),(3,1,1),'iron')
        box('Plans laid on cabinet',(-4,32.5,0),(12,1,14),'paper')
        box('Wooden plane',(7,34,1),(5,4,10),'wood')
        box('Plane blade',(7,36.5,0),(4,1,3),'iron')
    elif kind=='StitchingFrame':
        for x in (-12,12):
            box('Frame foot',(x,2,0),(5,4,22),'wood')
            box('Frame upright',(x,19,0),(4,30,4),'wood')
        box('Lower stretcher',(0,7,0),(22,4,4),'wood')
        box('Upper stretcher',(0,32,0),(22,4,4),'wood')
        box('Hide pattern',(0,20,0),(17,18,1),'leather')
        for x in (-10,10):
            for y in (13,20,27):box('Tension tie',(x,y,0),(4,1,1),'string')
        box('Tool tray',(0,9,4),(24,2,7),'wood')
        for x in (-6,6):box('Spool',(x,12,4),(4,4,4),'string')
        box('Iron needle',(0,11,4),(1,2,6),'iron')
    elif kind=='Crafter':
        box('Joiner toolbox',(0,-4,0),(21,12,9),'wood')
        box('Reinforced bottom',(0,-11,0),(21,2,9),'dark')
        for x in (-9,9):box('Iron corner strap',(x,-4,5),(2,12,1),'iron')
        box('Plane body',(-5,5,0),(7,6,8),'wood')
        box('Plane iron',(-5,9,0),(5,2,3),'iron')
        box('Mallet handle',(5,5,0),(2,9,2),'wood')
        box('Mallet head',(5,11,0),(8,5,5),'wood')
        box('Measuring rule',(9,5,0),(1,10,2),'paper')
        box('Box label',(0,-4,5),(6,3,1),'paper')
    elif kind=='Leatherworker':
        box('Folded leather wrap',(0,-3,0),(21,11,6),'leather')
        box('Dark backing',(0,-9,0),(21,2,6),'dark')
        box('Cutting tools pocket',(0,-4,3.5),(19,7,1),'red')
        for x in (-8,8):box('Wrap tie',(x,-3,4.5),(2,11,1),'strap')
        box('Awl grip',(-6,5,0),(3,6,3),'wood')
        box('Awl point',(-6,11,0),(1,6,1),'iron')
        box('Leather knife handle',(0,5,0),(2,6,2),'wood')
        box('Leather knife blade',(1,10,0),(5,4,1),'iron')
        box('Thread spindle',(7,6,0),(2,12,2),'wood')
        box('Waxed thread',(7,7,0),(5,7,4),'string')
    elif kind=='FieldLedger':
        box('Page block',(0,0,0),(15,19,3),'paper')
        for z in (-2.5,2.5):box('Embossed cover',(0,0,z),(17,21,2),'green')
        box('Leather spine',(-8,0,0),(2,21,3),'edge')
        for y in (-7,7):box('Spine binding',(-8,y,0),(3,2,7),'brass')
        box('Clasp bridge',(8,0,0),(3,3,8),'strap')
        box('Clasp face',(5,0,4),(4,3,1),'brass')
        for x in (-4,2):box('Parcel seal edge',(x,2,4),(1,7,1),'brass')
        for y in (-1,5):box('Parcel seal rail',(-1,y,4),(5,1,1),'brass')
        box('Parcel ribbon',(-1,2,4),(1,5,1),'brass')
        box('Bookmark',(4,-12,0),(2,5,1),'strap')
    elif kind=='CourierPack':
        box('Tall pack',(0,1,0),(18,20,9),'leather')
        box('Strong base',(0,-10,0),(18,2,9),'edge')
        box('Canvas hood',(0,9,5),(18,4,1),'green')
        box('Front pocket',(0,-4,5.5),(12,8,2),'green')
        for x in (-6,6):
            box('Shoulder strap',(x,0,-5),(3,18,1),'strap')
            box('Cover strap',(x,3,5),(2,8,1),'strap')
            box('Cover clasp',(x,-2,6),(3,2,2),'brass')
        for x in (-11,11):box('Side pouch',(x,-3,0),(4,8,7),'leather')
        box('Rolled canvas',(0,13,0),(20,4,7),'green')
        for x in (-6,6):box('Roll binding',(x,13,0),(2,5,8),'strap')
        box('Parcel label',(0,-3,7),(5,3,1),'paper')
    elif kind=='Courier':
        box('Satchel body',(0,0,0),(18,14,8),'leather')
        box('Reinforced base',(0,-8,0),(18,2,8),'edge')
        box('Folded flap',(0,4,4.5),(18,6,1),'green')
        for x in (-6,6):
            box('Strap',(x,-1,4.5),(2,4,1),'strap')
            box('Brass clasp',(x,-4,5),(3,2,2),'brass')
        box('Handle top',(0,11,0),(10,2,2),'strap')
        for x in (-5,5):box('Handle upright',(x,9,0),(2,4,2),'strap')
        box('Parcel pocket',(0,-4,5),(6,4,2),'leather')
        box('Delivery label',(0,-3,6.5),(4,2,1),'paper')
    elif kind=='Cook':
        box('Folded linen wrap',(0,-3,0),(20,11,6),'paper')
        box('Leather backing',(0,-9,0),(20,2,6),'edge')
        box('Canvas utensil pockets',(0,-4,3.5),(18,7,1),'red')
        for x in (-7,7):box('Wrap tie',(x,-3,4.5),(2,11,1),'strap')
        box('Chef knife handle',(-6,3,0),(2,6,2),'wood')
        box('Chef knife blade',(-5,9,0),(4,7,1),'iron')
        box('Knife point',(-6,13,0),(2,1,1),'iron')
        box('Spoon handle',(1,7,0),(1,11,1),'wood')
        box('Spoon bowl',(1,13,0),(4,3,2),'wood')
        box('Whisk handle',(7,5,0),(2,6,2),'wood')
        for x in (5,8):box('Whisk loop',(x,11,0),(1,6,1),'iron')
        box('Whisk crown',(6.5,14,0),(4,1,1),'iron')
    elif kind=='Jeweler':
        box('Velvet case',(0,-5,0),(20,9,10),'purple')
        box('Case base',(0,-10,0),(20,1,10),'edge')
        for x in (-10,10):box('Brass corner',(x,-5,0),(1,9,10),'brass')
        box('Case lid',(0,4,-5),(20,9,2),'leather')
        box('Lid lining',(0,4,-3.5),(18,7,1),'purple')
        box('Clasp',(0,-3,5.5),(3,3,1),'brass')
        box('Loupe grip',(-5,2,0),(2,5,2),'wood')
        for x in (-8,-2):box('Loupe edge',(x,8,0),(1,7,2),'brass')
        for y in (5,11):box('Loupe rim',(-5,y,0),(5,1,2),'brass')
        box('Loupe lens',(-5,8,0),(5,5,1),'glass')
        for x in (3,6):box('Fine pliers handle',(x,3,0),(2,7,2),'wood')
        for x in (4,5):box('Fine pliers jaw',(x,9,0),(1,5,1),'iron')
        box('Gem tray',(0,0,4),(17,1,2),'brass')
    elif kind=='Rancher':
        box('Waxed saddle bag',(0,-2,0),(20,15,8),'leather')
        box('Reinforced bottom',(0,-10,0),(20,2,8),'edge')
        box('Green front flap',(0,1,4.5),(18,8,1),'green')
        for x in (-6,6):
            box('Bag strap',(x,-2,5.5),(2,12,1),'strap')
            box('Strap clasp',(x,-6,6.5),(3,2,1),'brass')
        box('Grooming brush',(-5,8,0),(8,5,4),'wood')
        for x in (-8,-6,-4,-2):box('Brush bristles',(x,11,0),(1,2,4),'paper')
        for x in (3,6):box('Shears grip',(x,5,0),(2,5,2),'wood')
        for x in (4,5):box('Shears blade',(x,10,0),(1,5,1),'iron')
        box('Feed pocket',(0,-5,6),(7,5,2),'leather')
    elif kind=='MasterSmith':
        box('Steel tool chest',(0,-5,0),(22,10,11),'iron')
        box('Leather base',(0,-11,0),(22,2,11),'edge')
        for x in (-9,9):box('Chest binding',(x,-5,6),(2,10,1),'brass')
        box('Hammer grip',(-5,4,0),(3,9,3),'wood')
        box('Hammer head',(-5,10,0),(10,5,5),'iron')
        for x in (-9,-1):box('Hammer striking face',(x,10,0),(2,5,5),'brass')
        for x in (3,7):box('Tongs handle',(x,5,0),(1,11,1),'iron')
        box('Tongs hinge',(5,10,0),(5,2,2),'brass')
        for x in (4,6):box('Tongs jaw',(x,13,0),(1,4,1),'iron')
        box('Maker plate',(0,-6,6),(7,4,1),'brass')
        box('Chest handle',(0,1,5),(7,2,2),'edge')
    elif kind=='Botanist':
        box('Waxed canvas roll',(0,-2,0),(19,13,6),'green')
        box('Leather base',(0,-9,0),(19,2,6),'edge')
        box('Seed pocket',(-5,-4,4),(7,7,2),'leather')
        box('Leaf label',(-5,-3,5.5),(3,4,1),'paper')
        box('Tool pocket',(4,-4,4),(8,7,2),'leather')
        for x in (-8,8):box('Roll ties',(x,-2,4),(1,11,1),'strap')
        box('Trowel grip',(-5,6,0),(2,5,2),'wood')
        box('Trowel shoulder',(-5,10,0),(5,3,1),'iron')
        box('Trowel blade',(-5,12,0),(3,1,1),'iron')
        box('Trowel tip',(-5,13,0),(1,1,1),'iron')
        for x in (2,5):
            box('Pruner handle',(x,4,0),(2,5,2),'wood')
            box('Pruner blade',(x,9,0),(1,4,1),'iron')
        box('Pruner hinge',(3.5,7,0),(4,2,2),'brass')
        box('Planting dibber',(8,7,-1),(1,8,1),'wood')
    elif kind=='Alchemist':
        box('Padded case',(0,-5,0),(20,9,9),'purple')
        box('Case base',(0,-10,0),(20,2,9),'edge')
        for x in (-10,10):box('Corner binding',(x,-5,0),(1,9,9),'leather')
        box('Front brass rail',(0,-2,5),(20,1,1),'brass')
        for x,mat in [(-6,'red'),(0,'glass'),(6,'gold')]:
            box('Glass vial',(x,3,0),(4,7,4),'glass')
            box('Visible contents',(x,1,2.5),(3,4,1),mat)
            box('Vial neck',(x,7,0),(2,1,2),'glass')
            box('Vial cork',(x,8,0),(2,1,2),'wood')
            box('Bottle label',(x,3,3),(2,2,1),'paper')
        for x in (-10,10):box('Handle upright',(x,5,-4),(2,13,2),'strap')
        box('Carry handle',(0,12,-4),(20,2,2),'strap')
        box('Mortar emblem',(0,-6,5),(6,2,1),'brass')
        box('Emblem bowl',(0,-4.5,5),(4,1,1),'brass')
    else:
        box('Leather tool roll',(0,-1,0),(19,12,6),'leather')
        box('Rolled edge',(0,-8,0),(19,2,8),'edge')
        box('Tool pocket',(0,-3,3.5),(17,6,1),'green')
        for x in (-6,6):box('Binding strap',(x,-1,4.5),(2,12,1),'strap')
        box('Hammer handle',(-3,7,0),(2,6,2),'wood')
        box('Hammer head',(-3,12,0),(8,4,4),'iron')
        for x in (3,5):box('Tongs arm',(x,7,0),(1,6,1),'iron')
        box('Tongs hinge',(4,10.5,0),(3,1,2),'brass')
        box('File',(8,8,0),(2,8,1),'iron')
        box('Roll buckle',(0,-4,4.5),(4,3,1),'brass')
    separate_trim_faces(nodes)
    model={'nodes':nodes,'format':'prop','lod':'auto'}
    if kind=='FieldLedger':
        ledger_grip(model)
    validate(model,kind)
    if not block or kind == 'StitchingFrame':
        atlas = crop_unused_rows(atlas, [model], 64 if kind in ('CourierPack', 'StitchingFrame') else 32)
    stem=kind if block else 'Field_Ledger' if kind=='FieldLedger' else f'{kind}_Kit'
    directory=COMMON/'Blocks/Aetherhaven/Artisans' if block else OUT
    directory.mkdir(parents=True,exist_ok=True)
    (directory/f'{stem}.blockymodel').write_text(json.dumps(model,indent=2)+'\n')
    atlas.save(directory/f'{stem}.png')
    # Show the ledger's exposed pages instead of its reinforced spine. The
    # silhouette should read as a book even in a native 64 pixel inventory slot.
    yaw=.65 if kind=='FieldLedger' else -.55;pitch=.42
    camera=np.array([[math.cos(yaw),0,math.sin(yaw)],[0,1,0],[-math.sin(yaw),0,math.cos(yaw)]])
    camera=np.array([[1,0,0],[0,math.cos(pitch),-math.sin(pitch)],[0,math.sin(pitch),math.cos(pitch)]])@camera
    faces=[face for part,points in world_shapes(nodes) for face in textured_faces((camera@points.T).T,part['shape'],atlas)]
    points=np.concatenate([f[1] for f in faces]);low,high=points[:,:2].min(axis=0),points[:,:2].max(axis=0);center=(low+high)/2
    scale=220/max(high-low);icon=Image.new('RGBA',(256,256));draw=ImageDraw.Draw(icon)
    for _,poly,color,_ in sorted(faces,key=lambda f:f[0]):draw.polygon([(128+(p[0]-center[0])*scale,128-(p[1]-center[1])*scale) for p in poly],fill=(*color,255))
    icon=icon.resize((128,128),Image.Resampling.LANCZOS)
    # Native item atlas cells are 64 pixels. Larger portrait images use a different UI path.
    icon.resize((64,64),Image.Resampling.LANCZOS).save(COMMON/f'Icons/ItemsGenerated/Aetherhaven_{stem}.png')
    item={'TranslationProperties':{'Name':f'aetherhaven_profession_kits.{kind.lower()}.name','Description':f'aetherhaven_profession_kits.{kind.lower()}.description'},
          'Icon':f'Icons/ItemsGenerated/Aetherhaven_{stem}.png','Model':f'Items/Aetherhaven/Profession_Kits/{stem}.blockymodel',
          'Texture':f'Items/Aetherhaven/Profession_Kits/{stem}.png','PlayerAnimationsId':'Block','Categories':['Items.Tools','Aetherhaven.All'],
          'Set':'Aetherhaven','MaxStack':1,'Quality':'Uncommon','ItemSoundSetId':'ISS_Blocks_Wood',
          'IconProperties':{'Scale':1,'Rotation':[22.5,45,0],'Translation':[0,0]}}
    if kind=='FieldLedger':
        item['PlayerAnimationsId']='Spellbook'
        item['ItemSoundSetId']='ISS_Weapons_Books'
        item['Interactions']={'Secondary':{'Interactions':[{'Type':'OpenCustomUI','Page':{'Id':'AetherhavenFieldLedger'}}]}}
    if block:
        item.pop('Model');item.pop('Texture')
        item['Categories']=['Furniture.Decoration','Aetherhaven.All'];item['MaxStack']=10
        item['BlockType']=dict(Material='Solid',DrawType='Model',Opacity='Transparent',
            CustomModel=f'Blocks/Aetherhaven/Artisans/{stem}.blockymodel',
            CustomModelTexture=[dict(Texture=f'Blocks/Aetherhaven/Artisans/{stem}.png',Weight=1)],
            HitboxType='Aetherhaven_'+stem,VariantRotation='NESW',
            Gathering=dict(Breaking=dict(GatherType='Benches')),
            BlockParticleSetId='Wood',BlockSoundSetId='Wood',ParticleColor='#745137')
        write_block_hitboxes(stem, nodes)
    (SERVER/f'Item/Items/Aetherhaven/Aetherhaven_{stem}.json').write_text(json.dumps(item,indent=2)+'\n')
    return icon

def write_block_hitboxes(stem, nodes):
    from generate_furniture_hitboxes import block_boxes
    (SERVER/f'Item/Block/Hitboxes/Aetherhaven_{stem}.json').write_text(json.dumps(block_boxes(nodes),indent=2)+'\n')

if __name__=='__main__':
    sheet=Image.new('RGBA',(960,180),'#283a3b');draw=ImageDraw.Draw(sheet)
    for index,kind in enumerate(('Courier','Smith','CourierPack','FieldLedger','Botanist','Alchemist')):
        icon=make(kind);sheet.alpha_composite(icon,(16+160*index,8));draw.text((30+160*index,150),kind+' kit',fill='#e0cfab')
    (ROOT/'build/profession-kits').mkdir(parents=True,exist_ok=True)
    sheet.save(ROOT/'build/profession-kits/preview.png')

    definitions=SERVER/'Aetherhaven/ProfessionKits';definitions.mkdir(parents=True,exist_ok=True)
    for kind,skill,speed,stacks in [('Courier','logistics',0,1),('Smith','blacksmithing',.15,0)]:
        name='Courier satchel' if kind=='Courier' else "Smith's tool roll"
        (definitions/f'{kind.lower()}.json').write_text(json.dumps(dict(itemId=f'Aetherhaven_{kind}_Kit',name=name,skill=skill,workSpeedBonus=speed,extraCarryingStacks=stacks),indent=2)+'\n')
        ingredients=[dict(itemId='Ingredient_Leather_Light',count=6),dict(itemId='Ingredient_Bar_Copper',count=2),dict(itemId='Ingredient_Fibre',count=4)] if kind=='Courier' else [dict(itemId='Ingredient_Bar_Iron',count=4),dict(itemId='Ingredient_Leather_Light',count=4),dict(resourceType='Wood_Trunk',count=4)]
        rule=dict(id=f'craft_{kind.lower()}_kit',name=name,workplace='leatherworking' if kind=='Courier' else 'blacksmithing',sourceRecipe=f'workplace:{kind.lower()}_kit',bench='Tannery' if kind=='Courier' else 'Weapon_Bench',benchTier=1,skillLevel=3,inputs=ingredients,output=f'Aetherhaven_{kind}_Kit',outputCount=1,workSeconds=20)
        (SERVER/f'Aetherhaven/WorkplaceRecipes/craft_{kind.lower()}_kit.json').write_text(json.dumps(rule,indent=2)+'\n')
    lang=SERVER/'Languages/en-US/aetherhaven_profession_kits.lang'
    owned={'courier','smith','courierpack','fieldledger','botanist','alchemist'}
    extra_lines=[line for line in lang.read_text(encoding='utf-8').splitlines() if line.split('.',1)[0] not in owned] if lang.exists() else []
    lang.write_text("courier.name = Courier Satchel\ncourier.description = A sturdy delivery satchel. Equip it through a townsfolk profile to carry one additional stack per trip. Crafted by townsfolk.\nsmith.name = Smith's Tool Roll\nsmith.description = Well balanced tools for the forge. Equip through a townsfolk profile for 15% faster blacksmithing and armor crafting. Does not speed up furnaces or change skill requirements. Crafted by townsfolk.\n",encoding='utf-8')
    with lang.open('a',encoding='utf-8') as f:
        f.write('courierpack.name = Quartermaster Pack\ncourierpack.description = A reinforced pack with room for two additional stacks per delivery. Replaces the equipped profession kit. Its crafting recipe is a reward from Mara Finch.\nfieldledger.name = Field Ledger\nfieldledger.description = Right click inside your town to open warehouse storage. Requires a resident Warehouse Manager and storage permission. A reputation reward from Mara Finch.\n')
    (definitions/'courier_pack.json').write_text(json.dumps(dict(itemId='Aetherhaven_CourierPack_Kit',name='Quartermaster pack',skill='logistics',workSpeedBonus=0,extraCarryingStacks=2),indent=2)+'\n')
    rule=dict(id='craft_quartermaster_pack',name='Quartermaster pack',workplace='leatherworking',sourceRecipe='workplace:quartermaster_pack',bench='Tannery',benchTier=1,skillLevel=5,requiresKnowledge=True,
              inputs=[dict(itemId='Ingredient_Leather_Light',count=12),dict(itemId='Ingredient_Bar_Iron',count=4),dict(itemId='Ingredient_Fibre',count=8)],output='Aetherhaven_CourierPack_Kit',outputCount=1,workSeconds=30)
    (SERVER/'Aetherhaven/WorkplaceRecipes/craft_quartermaster_pack.json').write_text(json.dumps(rule,indent=2)+'\n')

    for kind,name,skill,workplace,bench,inputs in [
        ('Botanist',"Botanist's Tool Roll",'farming','blacksmithing','Weapon_Bench',
         [dict(itemId='Ingredient_Bar_Iron',count=3),dict(itemId='Ingredient_Leather_Light',count=4),dict(itemId='Ingredient_Fibre',count=8)]),
        ('Alchemist',"Brewer's Case",'alchemy','alchemy','Alchemybench',
         [dict(itemId='Ingredient_Bar_Copper',count=3),dict(itemId='Ingredient_Leather_Light',count=4),dict(itemId='Potion_Empty_Small',count=3)])]:
        (definitions/f'{kind.lower()}.json').write_text(json.dumps(dict(itemId=f'Aetherhaven_{kind}_Kit',name=name,skill=skill,workSpeedBonus=.15,extraCarryingStacks=0),indent=2)+'\n')
        rule=dict(id=f'craft_{kind.lower()}_kit',name=name,workplace=workplace,sourceRecipe=f'workplace:{kind.lower()}_kit',bench=bench,
            benchTier=1,skillLevel=3,requiresKnowledge=True,inputs=inputs,output=f'Aetherhaven_{kind}_Kit',outputCount=1,workSeconds=25)
        (SERVER/f'Aetherhaven/WorkplaceRecipes/craft_{kind.lower()}_kit.json').write_text(json.dumps(rule,indent=2)+'\n')
        benefit='15% faster crop tending, herb planting and harvesting. Does not speed up plant growth.' if kind=='Botanist' else '15% faster brewing. Does not change ingredient costs or skill requirements.'
        with lang.open('a',encoding='utf-8') as f:
            f.write(f'{kind.lower()}.name = {name}\n{kind.lower()}.description = Equip through a townsfolk profile for {benefit} Its crafting plans are a friendship reward.\n')
    with lang.open('a',encoding='utf-8') as f:
        f.write('\n'.join(extra_lines)+'\n' if extra_lines else '')
