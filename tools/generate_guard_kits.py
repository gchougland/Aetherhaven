"""Generate original packed guard equipment, icons and pack-extensible progression definitions."""
import json, math, zipfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from villager_prop_preview import textured_faces, world_shapes
from painted_asset_materials import painted_patch
from check_asset_assembly import separate_trim_faces, validate
ROOT=Path(__file__).resolve().parents[1]; R=ROOT/'src/main/resources'; COMMON=R/'Common'; SERVER=R/'Server'
NATIVE=zipfile.ZipFile(Path.home()/'AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip')
paths={Path(p).stem:p for p in NATIVE.namelist() if p.startswith('Server/Item/Items/') and p.endswith('.json')}
METALS=('Copper','Iron','Thorium','Cobalt','Adamantite')
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
def merge(a,b):
    result=dict(a)
    for k,v in b.items():result[k]=merge(result[k],v) if isinstance(v,dict) and isinstance(result.get(k),dict) else v
    return result
def native(id):
    data=json.loads(NATIVE.read(paths[id]).decode('utf-8-sig'));return merge(native(data['Parent']),data) if data.get('Parent') else data
for family in ('Iron','Thorium','Cobalt','Adamantite','Leather_Heavy','Cloth_Silk','Cloth_Cindercloth'):
    for slot in ('Head','Chest','Hands','Legs'):
        data=native(f'Armor_{family}_{slot}')
        for key in ('Parent','Recipe','Recipes'):data.pop(key,None)
        data.update(Categories=[],MaxDurability=0,DurabilityLossOnHit=0)
        data['Armor'].update(BaseDamageResistance=0,DamageResistance={},StatModifiers={})
        # The native knockback system skips an absent map but dereferences entries
        # in a present map. An empty map throws on every knockback damage event.
        for key in ('KnockbackResistances','KnockbackEnhancements'):
            data['Armor'].pop(key,None)
        write(SERVER/f'Item/Items/Aetherhaven/Guard_Equipment/Aetherhaven_Guard_Armor_{family}_{slot}.json',data)

def model(cls,tier):
    atlas=Image.new('RGBA',(64,64));nodes=[];patches={};cursor=[0,0,0]
    def patch(w,h,mat):
        key=(w,h,mat)
        if key in patches:return patches[key]
        x,y,row=cursor
        if x+w+1>64:x=0;y+=row+1;row=0
        assert y+h<=64
        material=METALS[tier].lower() if mat=='metal' else {'Knight':'blue','Archer':'green','Rogue':'purple','Mage':'blue'}[cls] if mat=='cloth' else mat
        im=painted_patch(w,h,material)
        atlas.paste(im,(x,y));result={'offset':{'x':x,'y':y},'mirror':{'x':False,'y':False},'angle':0}
        patches[key]=result;cursor[:]=[x+w+1,y,max(row,h)];return result
    def box(name,pos,size,mat):
        sx,sy,sz=size;layout={side:patch(w,h,mat) for side,(w,h) in {'front':(sx,sy),'back':(sx,sy),'left':(sz,sy),'right':(sz,sy),'top':(sx,sz),'bottom':(sx,sz)}.items()}
        nodes.append({'id':str(len(nodes)+1),'name':name,'position':dict(zip('xyz',pos)), 'orientation':{'x':0,'y':0,'z':0,'w':1},'shape':{'type':'box','offset':dict.fromkeys('xyz',0),'stretch':dict.fromkeys('xyz',1),'settings':{'isPiece':False,'size':dict(zip('xyz',size))},'textureLayout':layout,'unwrapMode':'custom','visible':True,'doubleSided':False,'shadingMode':'standard'}})
    box('Packed armor case',(0,-5,0),(20,12,10),'leather');box('Canvas equipment cover',(0,2,0),(20,2,10),'cloth')
    for x in (-7,7):
        box('Front securing strap',(x,-5,5.5),(2,12,1),'dark');box('Lid securing strap',(x,3.5,0),(2,1,10),'dark');box('Buckle',(x,-3,6.5),(3,3,1),'metal')
    for i in range(3):box('Tier stud',(-3+3*i,-8,5.5),(2,2,1),'metal')
    if cls=='Knight':
        box('Shield face',(0,7,6),(10,10,2),'metal');box('Shield point',(0,1,6),(6,2,2),'metal')
        box('Shield center',(0,7,7.5),(6,7,1),'cloth');box('Shield boss',(0,7,8.5),(3,3,1),'gold')
        box('Helmet',(0,7,-2),(10,8,8),'metal');box('Visor slit',(0,7,2.5),(8,1,1),'dark')
        box('Sword handle',(9,7,-2),(2,9,2),'dark');box('Sword guard',(9,10,-2),(7,2,2),'metal');box('Sword blade',(9,16,-2),(3,10,1),'metal')
    elif cls=='Archer':
        box('Quiver',(-3,9,-1),(7,13,6),'cloth');box('Quiver rim',(-3,16,-1),(9,2,8),'metal')
        for x,y in ((-5,23),(-2,21),(1,24)):
            box('Arrow shaft',(x,y-5,-1),(1,10,1),'wood');box('Arrow feather',(x,y,-1),(3,4,1),'paper')
        box('Bow grip',(8,10,5),(2,6,2),'dark')
        for y,x in ((5,7),(1,5),(15,7),(19,5)):box('Bow limb',(x,y,5),(2,4,2),'metal')
        box('Bow string',(4,10,5),(1,20,1),'string')
    elif cls=='Rogue':
        box('Folded hood',(0,7,-1),(12,6,8),'cloth');box('Hood top',(0,11,-2),(8,2,6),'cloth')
        for x in (-6,6):
            box('Dagger grip',(x,5,5),(2,5,2),'dark');box('Dagger crossguard',(x,8,5),(6,2,2),'gold')
            box('Dagger blade',(x,14,5),(3,10,1),'metal');box('Dagger point',(x,20,5),(1,2,1),'metal')
    else:
        box('Folded robes',(-3,5,-1),(12,4,8),'cloth');box('Spellbook pages',(-3,9,-1),(9,3,7),'paper')
        for y in (7,11):box('Spellbook cover',(-3,y,-1),(11,1,9),'cloth')
        box('Book clasp',(-3,9,4),(2,5,1),'gold');box('Staff shaft',(8,9,2),(2,17,2),'wood')
        box('Staff collar',(8,17,2),(5,2,4),'metal');box('Crystal body',(8,21,2),(4,6,4),'metal');box('Crystal tip',(8,25,2),(2,2,2),'metal')
    separate_trim_faces(nodes);validate({'nodes':nodes},f'{cls} {tier}')
    stem=f'{METALS[tier]}_{cls}_Kit';folder=COMMON/'Items/Aetherhaven/Guard_Kits'
    shared_model={'nodes':nodes,'format':'prop','lod':'auto'}
    model_path=folder/f'{cls}_Kit.blockymodel'
    if tier==0:write(model_path,shared_model)
    else:assert json.loads(model_path.read_text())==shared_model, 'Metal tiers must share identical geometry and UVs'
    atlas.save(folder/f'{stem}.png')
    yaw=-.5;pitch=.35;camera=np.array([[math.cos(yaw),0,math.sin(yaw)],[0,1,0],[-math.sin(yaw),0,math.cos(yaw)]])
    camera=np.array([[1,0,0],[0,math.cos(pitch),-math.sin(pitch)],[0,math.sin(pitch),math.cos(pitch)]])@camera
    faces=[f for part,points in world_shapes(nodes) for f in textured_faces((camera@points.T).T,part['shape'],atlas)]
    pts=np.concatenate([f[1] for f in faces]);low,high=pts[:,:2].min(axis=0),pts[:,:2].max(axis=0);center=(low+high)/2;scale=224/max(high-low)
    icon=Image.new('RGBA',(256,256));draw=ImageDraw.Draw(icon)
    for _,poly,color,_ in sorted(faces,key=lambda f:f[0]):draw.polygon([(128+(p[0]-center[0])*scale,128-(p[1]-center[1])*scale) for p in poly],fill=(*color,255))
    icon.resize((64,64),Image.Resampling.LANCZOS).save(COMMON/f'Icons/ItemsGenerated/Aetherhaven_{stem}.png')
    return stem,icon.resize((128,128),Image.Resampling.LANCZOS)
sheet=Image.new('RGBA',(640,900),'#243338');draw=ImageDraw.Draw(sheet);lang=[]
for c,cls in enumerate(('Knight','Archer','Rogue','Mage')):
    base=json.loads((SERVER/f'Aetherhaven/Equipment/guard_{cls.lower()}.json').read_text())
    for tier,metal in enumerate(METALS):
        stem,icon=model(cls,tier);sheet.alpha_composite(icon,(c*160+16,tier*180+8));draw.text((c*160+12,tier*180+145),f'{metal} {cls}',fill='#e0cfab')
        key=stem.lower();name=f'{metal} {cls} Kit';profile=f'guard_{cls.lower()}_{metal.lower()}'
        family=metal if cls=='Knight' else ('Leather_Light','Leather_Heavy','Leather_Heavy','Leather_Heavy','Leather_Heavy')[tier] if cls in ('Archer','Rogue') else ('Cloth_Cotton','Cloth_Silk','Cloth_Cindercloth','Cloth_Cindercloth','Cloth_Cindercloth')[tier]
        bonuses=({'strength':2+tier*2,'vitality':2+tier*2} if cls=='Knight' else {'dexterity':2+tier*2,'speed':1+tier,'vitality':tier} if cls in ('Archer','Rogue') else {'intelligence':2+tier*2,'vitality':1+tier*2})
        description=', '.join(f'+{v} {k.title()}' for k,v in bonuses.items() if v)+'.'
        equipment=dict(base,id=profile,displayName=f'{metal} {cls}',armor=[f'Aetherhaven_Guard_Armor_{family}_{s}' for s in ('Head','Chest','Hands','Legs')],attributeBonuses=bonuses)
        equipment['hotbar']=[dict(slot=0,itemId=base['hotbar'][0]['itemId'].replace('Copper',metal))]
        if cls=='Knight':equipment['offhand']=f'Weapon_Shield_{metal}'
        for slot in equipment['hotbar']:assert slot['itemId'] in paths
        if equipment.get('offhand'):assert equipment['offhand'] in paths
        write(SERVER/f'Aetherhaven/Equipment/{profile}.json',equipment)
        write(SERVER/f'Aetherhaven/GuardKits/{key}.json',dict(itemId=f'Aetherhaven_{stem}',name=name,guardClass=f'guard_{cls.lower()}',equipmentProfile=profile,description=description))
        item=dict(TranslationProperties=dict(Name=f'aetherhaven_guard_kits.{key}.name',Description=f'aetherhaven_guard_kits.{key}.description'),Icon=f'Icons/ItemsGenerated/Aetherhaven_{stem}.png',Model=f'Items/Aetherhaven/Guard_Kits/{cls}_Kit.blockymodel',Texture=f'Items/Aetherhaven/Guard_Kits/{stem}.png',PlayerAnimationsId='Block',Categories=['Items.Tools','Aetherhaven.All'],Set='Aetherhaven',MaxStack=1,Quality=('Common','Uncommon','Rare','Epic','Legendary')[tier],ItemSoundSetId='ISS_Blocks_Wood',IconProperties=dict(Scale=1,Rotation=[22.5,45,0],Translation=[0,0]))
        write(SERVER/f'Item/Items/Aetherhaven/Guard_Equipment/Aetherhaven_{stem}.json',item)
        inputs=[dict(itemId=f'Ingredient_Bar_{metal}',count=((18+tier*6+4)//5)*5),dict(itemId='Ingredient_Leather_Light',count=((6+tier*2+4)//5)*5),dict(itemId='Ingredient_Fibre',count=((8+tier*4+4)//5)*5)]
        write(SERVER/f'Aetherhaven/WorkplaceRecipes/craft_{key}.json',dict(id=f'craft_{key}',name=name,workplace='armorCrafting',sourceRecipe=f'workplace:{key}',bench='Armor_Bench',benchTier=(1,1,2,2,3)[tier],skillLevel=2+tier*2,townRank=('E','D','C','B','A')[tier],inputs=inputs,output=f'Aetherhaven_{stem}',outputCount=1,workSeconds=40+tier*20))
        lang.extend((f'{key}.name = {name}',f'{key}.description = A complete {cls.lower()} equipment set. {description} Equip through a townsfolk profile. Crafted only by townsfolk.'))
(SERVER/'Languages/en-US/aetherhaven_guard_kits.lang').write_text('\n'.join(lang)+'\n')
(ROOT/'build/guard-kits').mkdir(parents=True,exist_ok=True);sheet.save(ROOT/'build/guard-kits/preview.png')

# Remove only obsolete generated tier models after every item points to its shared class model.
for cls in ('Knight','Archer','Rogue','Mage'):
    for metal in METALS:
        obsolete=COMMON/f'Items/Aetherhaven/Guard_Kits/{metal}_{cls}_Kit.blockymodel'
        if obsolete.exists():obsolete.unlink()
