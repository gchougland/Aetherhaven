"""Hunter lodge: a roofed L shaped trophy hall overlooking an octagonal open arena.
Original rune beacon, hunt portal, and three friendship furnishings use a painted
stone, verdigris and brass palette. Native furniture occupancy is preserved.
"""
if __name__ == '__main__':
    raise SystemExit('Generated specialist publishing is retired. Use tools/import_v4_user_buildings.py '
                     'to import the authored Core prefabs. Removed Stormwind variants must not be recreated.')

from pathlib import Path
import json, math, copy, random, zipfile
from PIL import Image, ImageDraw
from painted_asset_materials import painted_patch
from check_asset_assembly import separate_trim_faces, validate
from compact_atlas_rows import crop_unused_rows
from generate_v4_alchemy_content import create, read, write, SERVER, ROOT

COMMON=ROOT/'src/main/resources/Common'
NATIVE=ROOT.parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets'

def assets():
    directory=COMMON/'Blocks/Aetherhaven/Hunt';directory.mkdir(parents=True,exist_ok=True)
    texture=Image.new('RGBA',(512,512));patches={};cursor=[0,0,0]
    materials=['stone','copper','teal','purple','wood','paper','red','green']
    def uv(w,h,mat):
        key=(w,h,mat)
        if key in patches:return patches[key]
        x,y,row=cursor
        if x+w+1>texture.width:x=0;y+=row+1;row=0
        assert y+h<=texture.height
        texture.paste(painted_patch(w,h,materials[mat]),(x,y))
        result=dict(offset=dict(x=x,y=y),mirror=dict(x=False,y=False),angle=0)
        patches[key]=result;cursor[:]=[x+w+1,y,max(row,h)]
        return result
    def model(name,parts):
        nodes=[]
        for n,(label,pos,size,mat,angle) in enumerate(parts):
            sx,sy,sz=size
            shape=dict(type='box',offset=dict.fromkeys('xyz',0),stretch=dict.fromkeys('xyz',1),settings=dict(isPiece=False,size=dict(zip('xyz',size))),
                textureLayout={face:uv(w,h,mat) for face,(w,h) in {'front':(sx,sy),'back':(sx,sy),'left':(sz,sy),'right':(sz,sy),'top':(sx,sz),'bottom':(sx,sz)}.items()},
                unwrapMode='custom',visible=True,doubleSided=False,shadingMode='standard')
            # Every face has a correctly sized painted UV region, including long beams.
            nodes.append(dict(id=str(n+1),name=label,position=dict(zip('xyz',pos)),orientation=dict(x=0,y=0,z=math.sin(angle/2),w=math.cos(angle/2)),shape=shape))
        separate_trim_faces(nodes)
        validate(dict(nodes=nodes),name)
        write(directory/(name+'.blockymodel'),dict(nodes=nodes,lod='auto'))
        return dict(nodes=nodes,lod='auto')
    # Block model coordinates use 32 units per voxel. No raised collision obstructs the arena.
    beacon=model('Beacon',[('Carved footing',(0,15,0),(32,30,32),0,0),('Runic face',(0,31,0),(32,2,32),2,0)])
    furnishing={
        'Practice_Standard': [('Weighted foot',(0,2,0),(22,4,22),0,0),('Standard pole',(0,28,0),(4,48,4),1,0),('Crossbar',(0,50,0),(30,3,4),1,0),('Veteran cloth',(0,34,2),(26,28,2),6,0)],
        'Recovery_Brazier': [('Foot',(0,2,0),(24,4,24),0,0),('Stem',(0,13,0),(8,18,8),1,0),('Bowl',(0,23,0),(28,6,28),1,0),('Ember stone',(0,28,0),(20,4,20),2,0)],
        'Warding_Sigil': [('Foot',(0,3,0),(26,6,20),0,0),('Upright',(0,23,0),(10,34,8),1,0),('Ward face',(0,26,5),(26,28,2),7,0)]}
    meshes={'Beacon':beacon}
    from generate_furniture_hitboxes import block_boxes
    for name,parts in furnishing.items():
        meshes[name]=model(name,parts)
        write(SERVER/f'Item/Block/Hitboxes/Aetherhaven_Hunt_{name}.json',block_boxes(meshes[name]['nodes']))
    # Entity units differ from block units. Closed segmented ring; no coplanar overlapping faces.
    parts=[('Foundation',(0,4,0),(144,8,28),0,0)]
    for i in range(16):
        a=i*math.tau/16
        # Tangential blocks overlap inside the masonry; the old short pieces left gaps.
        parts.append(('Portal rim '+str(i),(76*math.sin(a),100+84*math.cos(a),0),(38,18,15),1,-a))
        parts.append(('Inlaid enamel '+str(i),(69*math.sin(a),100+76*math.cos(a),7.75),(30,4,1),2,-a))
    for x in (-64,64):parts.append(('Rune anchor',(x,16,0),(24,32,24),0,0))
    portal=model('Portal',parts)
    texture = crop_unused_rows(texture, [*meshes.values(), portal], 128)
    texture.save(directory/'Hunt.png')
    # Icons from actual geometry. This renderer is an authoring aid, not a substitute for in-game review.
    import numpy as np
    from villager_prop_preview import world_shapes,textured_faces
    def icon(mesh,size=128):
        yaw=-.55;pitch=.5
        camera=np.array([[math.cos(yaw),0,math.sin(yaw)],[0,1,0],[-math.sin(yaw),0,math.cos(yaw)]])
        camera=np.array([[1,0,0],[0,math.cos(pitch),-math.sin(pitch)],[0,math.sin(pitch),math.cos(pitch)]])@camera
        faces=[]
        for node,points in world_shapes(mesh['nodes']):faces.extend(textured_faces((camera@points.T).T,node['shape'],texture))
        bounds=np.concatenate([f[1] for f in faces]);lo,hi=bounds[:,:2].min(axis=0),bounds[:,:2].max(axis=0);mid=(lo+hi)/2;scale=size*.85/max(hi-lo)
        im=Image.new('RGBA',(size*2,size*2));d=ImageDraw.Draw(im)
        for _,poly,color,_ in sorted(faces,key=lambda f:f[0]):d.polygon([(size+(p[0]-mid[0])*scale*2,size-(p[1]-mid[1])*scale*2) for p in poly],fill=(*color,255))
        return im.resize((size,size),Image.Resampling.LANCZOS)
    for name,mesh in meshes.items():
        item='Aetherhaven_Hunt_'+name
        icon(mesh).save(COMMON/f'Icons/ItemsGenerated/{item}.png')
        block=dict(Material='Solid',DrawType='Model',Opacity='Transparent' if name!='Beacon' else 'Solid',
            CustomModel=f'Blocks/Aetherhaven/Hunt/{name}.blockymodel',CustomModelTexture=[dict(Texture='Blocks/Aetherhaven/Hunt/Hunt.png',Weight=1)],
            HitboxType='Full' if name=='Beacon' else item,VariantRotation='NESW',
            Gathering=dict(Breaking=dict(GatherType='Rocks')),BlockParticleSetId='Stone',BlockSoundSetId='Stone',ParticleColor='#526566')
        data=dict(TranslationProperties=dict(Name=f'aetherhaven_monster_hunter.item.{name}.name',Description=f'aetherhaven_monster_hunter.item.{name}.description'),
            Icon=f'Icons/ItemsGenerated/{item}.png',Categories=['Furniture.Decoration','Aetherhaven.All'],Set='Aetherhaven',BlockType=block,PlayerAnimationsId='Block',MaxStack=10)
        if name=='Beacon':data['Recipe']=dict(TimeSeconds=3,Input=[dict(ResourceTypeId='Rock',Quantity=8),dict(ItemId='Ingredient_Bar_Copper',Quantity=4)],BenchRequirement=[dict(Id='Aetherhaven_TownPlanningDesk',Type='Crafting',Categories=['Aetherhaven_TownPlanning_Misc'])])
        write(SERVER/f'Item/Items/Aetherhaven/{item}.json',data)
    write(COMMON/'NPC/Aetherhaven/Hunt/Portal.blockymodel',portal)
    texture.save(COMMON/'NPC/Aetherhaven/Hunt/Hunt.png')
    write(SERVER/'Models/Aetherhaven_Hunt_Portal.json',dict(Model='NPC/Aetherhaven/Hunt/Portal.blockymodel',Texture='NPC/Aetherhaven/Hunt/Hunt.png',EyeHeight=1,MinScale=1,MaxScale=1,Light=dict(Color='#8cc'),HitBox=dict(Min=dict(X=-1.5,Y=0,Z=-.3),Max=dict(X=1.5,Y=3.5,Z=.3)),
        Particles=[dict(SystemId='Aetherhaven_Hunt_Portal',PositionOffset=dict(X=0,Y=1.5625,Z=.15),ClearParticlesOnRemove=True)]))
    write(SERVER/'NPC/Roles/Aetherhaven/Aetherhaven_Hunt_Portal.json',dict(Type='Generic',Appearance='Aetherhaven_Hunt_Portal',NameTranslationKey='aetherhaven_monster_hunter.portal',MaxHealth=1,Invulnerable=True,IsMemory=False,StartState='Idle',MotionControllerList=[dict(Type='Walk',MaxWalkSpeed=1,Gravity=10)],Instructions=[dict(Sensor=dict(Type='State',State='Idle'),BodyMotion=dict(Type='Nothing'))]))
    roles={
        'Skeleton':'Undead/Skeleton/Skeleton/Skeleton_Fighter.json',
        'Spider':'Creature/Vermin/Spider_Cave.json',
        'Scarak':'Intelligent/Faction/Scarak/Scarak_Louse.json'}
    # Author against the installed release used by runServer, not the newer extracted source snapshot.
    with zipfile.ZipFile(Path.home()/'AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip') as pack:
        spawners=[]
        for suffix,scale in [('Blue',.65),('Blue_Center',.62),('Blue_Sparks_Small',.5)]:
            native_name='Portal_Round_'+suffix
            path=next(n for n in pack.namelist() if n.endswith('/'+native_name+'.particlespawner'))
            particle=json.loads(pack.read(path))
            # Reuse native painted effect textures, with a bounded custom composition.
            particle['MaxConcurrentParticles']=min(12,particle.get('MaxConcurrentParticles',12))
            if suffix=='Blue_Sparks_Small':
                particle['SpawnRate']=dict(Min=8,Max=10)
                for values in particle.get('EmitOffset',{}).values():
                    for key in ('Min','Max'):
                        if key in values:values[key]*=.35
                for attractor in particle.get('Attractors',[]):
                    for key in ('RadialTangentAcceleration','RadialImpulse','RadialTangentImpulse'):
                        if key in attractor:attractor[key]*=.35
            for frame in [particle['Particle'].get('InitialAnimationFrame',{})]+list(particle['Particle'].get('Animation',{}).values()):
                if 'Color' in frame:frame['Color']='#70b9b2'
            if 'Scale' in particle['Particle'].get('InitialAnimationFrame',{}):
                for values in particle['Particle']['InitialAnimationFrame']['Scale'].values():
                    for key in ('Min','Max'):
                        if key in values:values[key]*=scale
            ident='Aetherhaven_Hunt_Portal_'+suffix
            write(SERVER/f'Particles/Aetherhaven/{ident}.particlespawner',particle)
            spawners.append(dict(SpawnerId=ident))
        write(SERVER/'Particles/Aetherhaven/Aetherhaven_Hunt_Portal.particlesystem',dict(Spawners=spawners,CullDistance=48,IsImportant=False))
        for name,path in roles.items():
            actual=next(n for n in pack.namelist() if n.startswith('Server/NPC/Roles/') and n.endswith('/'+Path(path).name))
            data=json.loads(pack.read(actual));data['Modify']['DropList']=''
            if 'IsMemory' in data['Modify']:data['Modify']['IsMemory']=False
            data['Modify']['MaxHealth']={'Skeleton':70,'Spider':80,'Scarak':90}[name]
            if name=='Scarak':
                data['Modify']['FlockArray']=[]
                data['Modify']['Attack']='Aetherhaven_Hunt_Scarak_Bite'
                bite=json.loads(pack.read('Server/Item/Interactions/NPCs/Beast/Scarak_Louse/Scarak_Louse_Bite.json'))
                def explicit_damage(value):
                    if isinstance(value,dict):
                        if value.get('Type')=='Replace' and value.get('Var')=='Melee_Damage':
                            return dict(Parent='NPC_Attack_Melee_Damage')
                        return {k:explicit_damage(v) for k,v in value.items()}
                    return [explicit_damage(v) for v in value] if isinstance(value,list) else value
                write(SERVER/'Item/Interactions/Aetherhaven/Aetherhaven_Hunt_Scarak_Bite.json',explicit_damage(bite))
                write(SERVER/'Item/RootInteractions/Aetherhaven/Aetherhaven_Hunt_Scarak_Bite.json',dict(Interactions=['Aetherhaven_Hunt_Scarak_Bite'],Tags=dict(Attack=['Melee'])))
            write(SERVER/f'NPC/Roles/Aetherhaven/Aetherhaven_Hunt_{name}.json',data)

def lodge(stormwind):
    from generate_v4_alchemy_buildings import Building, rectangle
    b=Building('Hunter_Lodge',stormwind,(0,31,0,29))
    b.room(2,13,3,11,'z',4);b.room(2,8,11,23,'x',3);b.structure()
    for x,z in [(2,3),(13,3),(13,11),(8,11),(8,23),(2,23),(2,11)]:b.post(x,z,4)
    b.doorway(10,3,2);b.doorway(5,11,2);b.doorway(8,16,2,'z')
    b.windows([(4,3,0),(7,3,0),(13,6,1),(2,7,1),(2,15,1),(2,20,1),(5,23,0)])
    for x,z in rectangle(9,12,0,2):b.put(x,-1,z,b.stone)
    for x in range(9,13):b.put(x,-1,-1,'Rock_Stone_Cobble_Stairs',2)
    # An octagonal sunken-look court, level with the lodge for reliable navigation.
    court={(x,z) for x,z in rectangle(12,28,10,26) if abs(x-20)+abs(z-18)<=13}
    for x,z in court:
        edge=any((x+dx,z+dz) not in court for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)])
        b.put(x,-1,z,b.stone if edge else 'Rock_Stone_Brick_Smooth' if (x+z)%3==0 else 'Soil_Gravel')
        if edge and not(x==12 and 15<=z<=17):
            b.put(x,0,z,b.stone);b.put(x,1,z,'Wood_Hardwood_Fence' if stormwind else 'Wood_Softwood_Fence')
    for x,z in rectangle(8,13,15,17):b.put(x,-1,z,b.stone)
    b.put(20,-1,18,'Aetherhaven_Hunt_Beacon')
    # Fence-mounted lanterns illuminate the court without obstructing combat or the entrance.
    for x,z in [(15,10),(25,10),(28,18),(25,26),(15,26),(12,22)]:
        b.furniture(x,2,z,'Deco_Lantern')
    # Spectator benches are outside the required clear fighting area.
    for z in (12,17,22):
        b.furniture(30,0,z,'Furniture_Village_Stool');b.put(30,-1,z,b.stone)
    b.furniture(4,0,7,'Furniture_Village_Table');b.furniture(6,0,7,'Furniture_Village_Stool')
    b.furniture(10,0,9,'Furniture_Village_Counter');b.furniture(11,0,5,'Aetherhaven_Management_Block')
    b.furniture(4,0,20,'Furniture_Village_Bed');b.furniture(6,0,13,'Aetherhaven_Production_Storage')
    b.furniture(3,1,16,'Furniture_Village_Shelf_Full',1)
    for x,z in [(6,6),(5,14),(5,20)]:b.put(x,4,z,b.plank);b.furniture(x,3,z,'Deco_Lantern_Ceiling')
    b.prop('Deco_Bone_Skulls',4.4,1.04,7.4,.45,.3)
    b.prop('Deco_Book_Pile_Small',4.6,1.03,7.65,.6,-.2)
    b.save()

PERSON=dict(kind='monster_hunter',model='Monster_Hunter',name='Kael Thorne',building='Hunter_Lodge',level=4,
    voice='MellowMale',order=24,season='Winter',day=17,gold=125,wood=300,rock=360,
    management=[11,0,5],bench='Aetherhaven_Hunt_Beacon',benchpos=[10,0,9],activity='read',
    loves=['Ingredient_Chitin_Sturdy','Ingredient_Sac_Venom'],likes=['Ingredient_Bone_Fragment'],
    title='Through the Breach',description='Build Kael a lodge with an arena for survival hunts.',
    greetings=['A good hunt begins with preparation.', 'Every creature has a lesson to teach. Some teach it rather sharply.', 'Shall we see how far I can get today?'],
    offer='Would you build a hunting lodge here?',pitch='Give me a lodge and a clear arena. We can bind creatures to a portal, one at a time, and see how long I can stand against them. Bring samples from your travels to unlock new creatures. I collect their spoils for the town.',
    accepted='Here is the lodge plan. Leave the floor around the Hunt Beacon clear. The spectators belong outside the railing.',
    welcome='The arena is ready. Ask me about survival runs. Every run costs treasury gold and begins at your command. Train me between runs and unlock creatures with gold and their drops.',
    how='I fight one creature at a time until I go down. Each wave is harder. When I fall, the portal closes and I return immediately, ready for your next run. There is no need to visit Gaia. Collected spoils remain in the lodge. Nothing starts again without your order.')

if __name__=='__main__':
    assets()
    for style in (False,True):lodge(style)
    create(PERSON)
    # The hunter keeps civilian dialogue and travel states with an explicit locked-target melee state.
    path=SERVER/'NPC/Roles/Aetherhaven/Aetherhaven_Monster_Hunter.json';npc=read(path)
    knight=read(SERVER/'NPC/Roles/Aetherhaven/Aetherhaven_Guard_Knight.json')
    npc['Instructions']=npc['Instructions'][1:]
    npc['MaxHealth']=260;npc['HotbarItems']=['Weapon_Sword_Iron'];npc['HotbarSize']=3;npc['OffHandSlots']=2
    npc['InteractionVars']=knight['InteractionVars'];npc['InteractionVars']['Melee_Damage']['Interactions'][0]['DamageCalculator']['BaseDamage']['Physical']=18
    npc['InteractionVars']['Melee_Selector']=dict(Interactions=['NPC_Attack_Selector_Left'])
    npc['Instructions'].insert(0,copy.deepcopy(knight['Instructions'][0]))
    combat=dict(Sensor=dict(Type='State',State='Combat',IgnoreMissingSetState=True),Instructions=[dict(Sensor=dict(Type='Target',TargetSlot='LockedTarget',Range=24),Instructions=[dict(Reference='Component_Instruction_Aetherhaven_Guard_Melee_Combat')]),dict(Actions=[dict(Type='ReleaseTarget'),dict(Type='State',State='Idle')])])
    npc['Instructions'][1]['Instructions'].insert(0,combat)
    write(path,npc)
    path=SERVER/'Aetherhaven/Dialogue/aetherhaven_monster_hunter.json';dialogue=read(path)
    dialogue['nodes']['main_hub']['choices'].insert(1,dict(text='aetherhaven_monster_hunter.hunts',next=None,condition=dict(type='not',condition=dict(type='npc_binding_is_visitor')),whenFalse='hide',actions=[dict(type='open_hunts')]))
    rewards=[]
    for level,name in [(25,'Practice_Standard'),(50,'Recovery_Brazier'),(75,'Warding_Sigil')]:
        ident='rep_monster_hunter_'+str(level);node='rep_reward_'+str(level)
        rewards.append(dict(rewardId=ident,minReputation=level,itemId='Aetherhaven_Hunt_'+name,itemCount=1,dialogueNodeId=node))
        dialogue['nodes'][node]=dict(speaker='aetherhaven_monster_hunter.speaker',text='aetherhaven_monster_hunter.reward.'+str(level),speechClip='Agree',actions=[dict(type='reputation_reward_grant',rewardId=ident)],choices=[dict(text='aetherhaven_ui_town.aetherhaven.reputation.reward.continue',next='main_hub')])
    write(path,dialogue)
    path=SERVER/'Aetherhaven/Villagers/Aetherhaven_Monster_Hunter.json';data=read(path);data['reputationMilestones']=rewards;write(path,data)
    path=SERVER/'Languages/en-US/aetherhaven_monster_hunter.lang'
    lines=[]
    for line in path.read_text(encoding='utf-8').splitlines():
        if '=' in line:
            key,value=line.split('=',1)
            line=key+'='+value.replace('Hunter_Lodge',"Hunter's Lodge").replace('hunter_lodge','hunting lodge').replace('Monster_Hunter','Monster Hunter')
        lines.append(line)
    text='\n'.join(lines)
    text='\n'.join(line for line in text.splitlines() if not line.startswith(('hunts=','portal=','item.','reward.')))+'\n'
    text+='\nhunts=Let us plan a survival run.\nportal=Hunt Portal\n'
    descriptions={
        'Beacon':('Hunt Beacon','Set into the arena floor with a clear 9 by 9 space around it.'),
        'Practice_Standard':('Practice Standard','Place in the hunting lodge for 5% more damage during runs. Duplicates do not stack.'),
        'Recovery_Brazier':('Recovery Brazier','Place in the hunting lodge to recover 4 extra health between waves. Duplicates do not stack.'),
        'Warding_Sigil':('Warding Sigil','Place in the hunting lodge to make Second Wind restore 45% health instead of 30%. Train Second Wind first.')}
    for name,(title,description) in descriptions.items():text+=f'item.{name}.name={title}\nitem.{name}.description={description}\n'
    for level,name in [(25,'Practice_Standard'),(50,'Recovery_Brazier'),(75,'Warding_Sigil')]:text+=f'reward.{level}=A gift for our arena. {descriptions[name][1]}\n'
    path.write_text(text,encoding='utf-8')
    path=SERVER/'Aetherhaven/plot_creator_main_constructions.json';data=read(path)
    if not any(x['id']=='plot_monster_hunter' for x in data['mainConstructions']):data['mainConstructions'].append(dict(id='plot_monster_hunter',labelLang='aetherhaven_monster_hunter.building.name'))
    write(path,data)
    for path in [SERVER/'Aetherhaven/Buildings/plot_monster_hunter.json',SERVER/'Aetherhaven/Buildings/Stormwind/plot_stormwind_monster_hunter.json']:
        data=read(path);data['materials'][-1]['count']=1;data['displayName']=data['displayName'].replace('Hunter_Lodge',"Hunter's Lodge");write(path,data)
    portrait=Image.open(COMMON/'Icons/ModelsGenerated/Aetherhaven_Monster_Hunter.png').convert('RGBA');portrait.thumbnail((56,56),Image.Resampling.LANCZOS)
    token=Image.new('RGBA',(64,64));token.alpha_composite(portrait,((64-portrait.width)//2,(64-portrait.height)//2));token.save(COMMON/'Icons/ItemsGenerated/Aetherhaven_Token_Monster_Hunter.png')
    path=SERVER/'Languages/en-US/aetherhaven_town.lang';text=path.read_text(encoding='utf-8')
    if 'aetherhaven.profession.kind.monster_hunter=' not in text:text+='\naetherhaven.profession.kind.monster_hunter=Monster Hunter\n'
    path.write_text(text,encoding='utf-8')
