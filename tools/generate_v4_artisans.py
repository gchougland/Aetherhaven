"""Crafter and Leatherworker recruitment with distinct, planned workshop footprints.

Draft buildings require native review before their visual design is approved.
Shared building authoring checks rotate native furniture fillers and close gables.
"""
if __name__ == '__main__':
    raise SystemExit('Generated specialist publishing is retired. Use tools/import_v4_user_buildings.py '
                     'to import the authored Core prefabs. Removed Stormwind variants must not be recreated.')

from pathlib import Path
import shutil
import zipfile
from PIL import Image
from generate_v4_alchemy_buildings import Building, rectangle
from generate_v4_alchemy_content import create, read, write, ROOT, SERVER
from render_villager_portrait import render

COMMON=ROOT/'src/main/resources/Common'

PEOPLE=[dict(kind='crafter',model='Crafter',name='Tobin Reedwright',building='Artisan_Workshop',level=3,
    voice='MellowMale',order=25,season='Spring',day=18,gold=65,wood=320,rock=140,
    management=[4,0,5],bench='Bench_WorkBench',benchpos=[12,0,5],activity='craft',
    loves=['Tool_Repair_Kit_Iron'],likes=['Ingredient_Fabric_Scrap_Linen','Wood_Beech_Trunk'],
    title='Room to Make Things',description='Build Tobin a workshop to teach townsfolk practical crafting.',
    greetings=['A good tool should be easy to mend.', 'I keep the spare parts upstairs. Usually.', 'There is room at the bench for another pair of hands.'],
    offer='Would you open a workshop here?',
    pitch='I mend what breaks and make what is missing. A work hall with a supply loft and a little shopfront would suit me. Your Apprentices can learn to make repair kits and equipment for the rest of town.',
    accepted='Here is the workshop plan. Keep a clear aisle between the benches and the door for deliveries.',
    welcome='The benches are ready. Ask about workplace upgrades to unlock Crafting, mark a site with your Building Staff and enable Crafting for your Apprentices in the town journal.',
    how='Crafting workplaces use an ordinary workbench. Choose a task, check its skill and bench requirements, and keep the ingredients in town storage. Couriers deliver them and an Apprentice works at the bench. Finished supplies go to Crafted Goods in the warehouse. More workplaces let more people work at once.'),
 dict(kind='leatherworker',model='Leatherworker',name='Sera Brindle',building='Tannery',level=4,
    voice='MellowFemale',order=26,season='Autumn',day=16,gold=70,wood=280,rock=170,
    management=[4,0,12],bench='Bench_Tannery',benchpos=[14,0,5],activity='craft',
    loves=['Ingredient_Leather_Heavy'],likes=['Ingredient_Leather_Light','Ingredient_Leather_Medium'],
    title='Hides and Handywork',description='Build Sera a tannery with room to prepare hides and leather goods.',
    greetings=['A strong stitch is worth taking your time over.', 'Nothing useful goes to waste here.', 'Bring your hides in. Leave the muddy boots by the door.'],
    offer='Would you settle here as our leatherworker?',
    pitch='A covered cutting room and an open drying yard would make a fine tannery. I can teach your Apprentices to turn hides into leather and make the satchels our couriers need.',
    accepted='Take this tannery plan. The drying yard should stay open and the cutting benches need a clear path.',
    welcome='We are ready to begin. Ask about workplace upgrades to unlock Leatherworking. A workplace needs a tanning rack, hides in town storage and an Apprentice with Leatherworking enabled in the journal.',
    how='Leatherworking sites prepare leather and leather goods at tanning racks. Tasks list the materials and skill needed. Couriers bring the supplies and an Apprentice must work to finish each task. Collect the leather and bags from Crafted Goods in the warehouse. Higher skill opens tougher hides and better equipment.')]

def workshop(stormwind):
    b=Building('Artisan_Workshop',stormwind,(0,21,0,19))
    # Tall rear work hall, offset front shop and lower side store. Loft stops
    # short of the workbenches, leaving them double height and well lit.
    b.room(8,18,3,15,'x',4);b.room(2,8,3,9,'z',3);b.room(3,8,9,15,'x',3)
    b.structure()
    b.doorway(4,3,2);b.doorway(8,6,2,'z');b.doorway(8,12,2,'z')
    for x,z in [(2,3),(8,3),(18,3),(18,15),(8,15),(3,15),(3,9)]:b.post(x,z)
    b.windows([(2,6,1),(5,9,0),(11,3,0),(15,3,0),(18,7,1),(18,11,1),(11,15,0),(15,15,0),(3,12,1)])
    # High windows break up the work hall gables and bring daylight to the loft.
    for z in (3,15):
        for x in (12,14):
            for y in (5,6):b.put(x,y,z,b.window)
        for y in range(4,9):b.put(13,y,z,'Wood_Beech_Trunk')
    for x,z in rectangle(10,16,10,14):b.put(x,4,z,b.plank)
    stairs='Wood_Hardwood_Stairs' if stormwind else 'Wood_Softwood_Stairs'
    for z in range(6,10):
        y=z-6
        for below in range(y):b.put(10,below,z,b.plank)
        b.put(10,y,z,stairs,2)
    fence='Wood_Hardwood_Fence' if stormwind else 'Wood_Softwood_Fence'
    for x in range(11,17):b.put(x,5,10,fence)
    for z in range(11,15):b.put(16,5,z,fence,1)
    b.furniture(12,0,5,'Bench_WorkBench');b.furniture(15,0,8,'Bench_WorkBench')
    b.furniture(4,0,5,'Aetherhaven_Management_Block')
    b.furniture(6,0,7,'Furniture_Village_Counter');b.furniture(4,0,7,'Furniture_Village_Stool')
    b.furniture(5,0,12,'Furniture_Village_Chest_Large')
    b.furniture(12,5,13,'Furniture_Village_Chest_Large')
    b.furniture(14,0,13,'Furniture_Village_Table')
    for lo,hi,z,y,light in [(2,8,6,3,6),(8,18,7,4,14),(8,18,12,3,14)]:
        for x in range(lo,hi+1):b.put(x,y,z,b.plank)
        b.furniture(light,y-1,z,'Deco_Lantern_Ceiling')
    # A beam meets both roof slopes above the supply loft.
    for x in range(12,15):b.put(x,8,12,b.plank)
    b.furniture(13,7,12,'Deco_Lantern_Ceiling')
    b.prop('Tool_Repair_Kit_Iron',6.5,1.02,7.5,.65,.2)
    b.prop('Ingredient_Fabric_Scrap_Linen',14.5,1.02,13.5,1.4,.5)
    for x,z in rectangle(3,6,0,2):b.put(x,-1,z,b.stone)
    b.save()

def tannery(stormwind):
    b=Building('Tannery',stormwind,(0,22,0,21))
    # Parallel enclosed rooms are joined by a rear service hall. The front
    # court remains open for drying racks and a central delivery route.
    b.room(2,7,3,17,'x',3);b.room(12,19,3,17,'x',3);b.room(7,12,12,17,'z',3)
    b.structure();b.doorway(7,7,2,'z');b.doorway(12,7,2,'z')
    b.doorway(9,12,2);b.doorway(7,14,2,'z');b.doorway(12,14,2,'z')
    for x,z in [(2,3),(7,3),(12,3),(19,3),(2,17),(7,17),(12,17),(19,17)]:b.post(x,z)
    b.windows([(2,7,1),(2,12,1),(4,3,0),(4,17,0),(15,3,0),(19,7,1),(19,12,1),(15,17,0),(9,17,0)])
    for x in (4,15):
        for y in (4,5):b.put(x,y,3,b.window)
    for x,z in rectangle(8,11,0,11):b.put(x,-1,z,b.stone)
    b.furniture(14,0,5,'Bench_Tannery');b.furniture(16,0,10,'Bench_Tannery',1)
    b.furniture(4,0,12,'Aetherhaven_Management_Block')
    b.furniture(4,0,5,'Furniture_Village_Counter');b.furniture(4,0,8,'Furniture_Village_Table')
    b.furniture(5,0,15,'Furniture_Village_Chest_Large')
    b.furniture(15,0,14,'Furniture_Village_Counter')
    for lo,hi,z,light in [(2,7,9,5),(12,19,8,15),(2,19,15,9)]:
        for x in range(lo,hi+1):b.put(x,3,z,b.plank)
        b.furniture(light,2,z,'Deco_Lantern_Ceiling')
    b.prop('Ingredient_Leather_Light',4.5,1.02,5.5,.7,.2)
    b.prop('Ingredient_Leather_Medium',4.5,1.02,8.5,.7,-.3)
    b.prop('Ingredient_Fibre',15.5,1.02,14.5,.7,.2)
    b.save()

def appearance(kind):
    model='Crafter' if kind=='crafter' else 'Leatherworker'
    source=SERVER/('Models/Villager/Builder.json' if kind=='crafter' else 'Models/Townsfolk/Female_Human_05.json')
    data=read(source)
    data['DefaultAttachments']=[a for a in data['DefaultAttachments'] if a['Model'] not in ('Characters/Player.blockymodel','Cosmetics/Head/Hard_Hat.blockymodel')]
    for a in data['DefaultAttachments']:
        if a.get('GradientSet')=='Hair':a['GradientId']='BrownSemiLight' if kind=='crafter' else 'Red'
        if a.get('GradientSet')=='Eyes_Gradient':a['GradientId']='BrownLight'
        if 'Mouth' in a['Model'] and kind=='crafter':a['Texture']='Characters/Body_Attachments/Mouths/Mouth1_Textures/Thin_Lips.png'
        if 'Overtops' in a['Model']:a['GradientId']='Brown'
        if 'Undertops' in a['Model']:a['GradientId']='Blue' if kind=='crafter' else 'White'
    if kind=='crafter':data['DefaultAttachments'].append(dict(Model='Cosmetics/Head/Goggles.blockymodel',Texture='Cosmetics/Head/Goggles_Texture.png'))
    data['Icon']='Icons/ModelsGenerated/Aetherhaven_'+model+'.png'
    # Validate every native texture/model against the actual installed game.
    with zipfile.ZipFile(Path.home()/'AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip') as pack:
        assets=set(pack.namelist())
        for a in [data]+data['DefaultAttachments']:
            for field in ('Model','Texture'):
                assert (COMMON/a[field]).exists() or 'Common/'+a[field] in assets,(model,a[field])
    path=SERVER/f'Models/Villager/{model}.json';write(path,data)
    portrait=COMMON/f'Icons/ModelsGenerated/Aetherhaven_{model}.png';render(path,portrait)
    memory=COMMON/f'UI/Custom/Pages/Memories/npcs/Aetherhaven_{model}.png';memory.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(portrait,memory)
    icon=Image.new('RGBA',(64,64));face=Image.open(portrait).convert('RGBA');face.thumbnail((56,56),Image.Resampling.LANCZOS)
    icon.alpha_composite(face,((64-face.width)//2,(64-face.height)//2));icon.save(COMMON/f'Icons/ItemsGenerated/Aetherhaven_Token_{model}.png')

if __name__=='__main__':
    for stormwind in (False,True):workshop(stormwind);tannery(stormwind)
    for person in PEOPLE:
        appearance(person['kind']);create(person)
        # Identifiers keep underscores, but player facing building names use spaces.
        p=SERVER/f'Languages/en-US/aetherhaven_{person["kind"]}.lang'
        p.write_text('\n'.join(line.partition('=')[0]+'='+line.partition('=')[2].replace('Artisan_Workshop','Artisan Workshop').replace('artisan_workshop','artisan workshop') if '=' in line else line for line in p.read_text().splitlines())+'\n',encoding='utf-8')
        for prefix in ('','Stormwind/'):
            p=SERVER/f'Aetherhaven/Buildings/{prefix}plot_{"stormwind_" if prefix else ""}{person["kind"]}.json';data=read(p);data['displayName']=data['displayName'].replace('_',' ');write(p,data)
        p=ROOT/f'subplugin-assets/Quests/Server/Aetherhaven/Quests/Town/q_{person["kind"]}.json';data=read(p)
        for objective in data['objectives']:objective['text']=objective['text'].replace('artisan_workshop','artisan workshop')
        write(p,data)
        p=SERVER/'Aetherhaven/plot_creator_main_constructions.json';data=read(p)
        if not any(row['id']=='plot_'+person['kind'] for row in data['mainConstructions']):data['mainConstructions'].append(dict(id='plot_'+person['kind'],labelLang='aetherhaven_'+person['kind']+'.building.name'))
        write(p,data)
    p=SERVER/'Languages/en-US/aetherhaven_town.lang';text=p.read_text()
    for person in PEOPLE:
        key='aetherhaven.profession.kind.'+person['kind']
        if key+'=' not in text and key+' =' not in text:text+='\n'+key+'='+person['model']+'\n'
    p.write_text(text,encoding='utf-8')
    for weight,skill in [('medium',3),('heavy',5)]:
        write(SERVER/f'Aetherhaven/WorkplaceRecipes/tan_{weight}_leather.json',dict(
            id=f'tan_{weight}_leather',name=weight.capitalize()+' leather',workplace='leatherworking',
            sourceRecipe=f'Ingredient_Leather_{weight.capitalize()}_Recipe_Generated_0',bench='Tannery',benchTier=1,skillLevel=skill))
