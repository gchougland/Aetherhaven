"""Author the clinic from a planned stepped layout in core and Stormwind palettes.

Front waiting room: 2..10 by 2..8, with a covered south entrance.
Rear recovery ward: 7..15 by 8..19, with two beds and a clear central aisle.
Eastern dispensary: 15..19 by 12..18, with records and medicine preparation.
Furniture uses native rotated occupancy and roof intersections use valley pieces.
"""
if __name__ == '__main__':
    raise SystemExit('Generated specialist publishing is retired. Use tools/import_v4_user_buildings.py '
                     'to import the authored Core prefabs. Removed Stormwind variants must not be recreated.')

from pathlib import Path
import json
from PIL import Image
from generate_v4_alchemy_buildings import Building, rectangle
from generate_v4_alchemy_content import create, read, write, SERVER, ROOT

def building(stormwind):
    b = Building('Clinic', stormwind, (0, 22, 0, 22))
    b.room(2, 10, 2, 8, 'z', 3)
    b.room(7, 15, 8, 19, 'x', 4)
    b.room(15, 19, 12, 18, 'x', 3)
    b.structure()
    for x,z in [(2,2),(10,2),(2,8),(7,8),(15,8),(7,19),(15,19),(19,12),(19,18)]: b.post(x,z)
    b.doorway(5,2,2)
    b.doorway(8,8,2)
    b.doorway(15,14,2,'z')
    b.windows([(2,5,1),(10,5,1),(4,8,0),(7,12,1),(15,10,1),(9,19,0),(13,19,0),(19,15,1)])
    for x in (10,11,12): b.put(x,5,19,b.window)
    for x,z in rectangle(4,7,0,1): b.put(x,-1,z,b.stone)
    for x in range(4,8): b.put(x,-1,-1,'Rock_Stone_Cobble_Stairs',2)
    for x,z in rectangle(4,7,0,1): b.put(x,3,z,b.roof+'_Flat')
    for x in (4,7): b.post(x,0,3)
    # Waiting seats face a low table without narrowing the central path.
    b.furniture(4,0,5,'Furniture_Village_Table')
    b.furniture(3,0,4,'Furniture_Village_Stool')
    b.furniture(3,0,6,'Furniture_Village_Stool')
    b.furniture(8,0,5,'Furniture_Village_Counter')
    # Two separate bed bays, with a generous aisle between them and the door.
    b.furniture(8,0,16,'Furniture_Village_Bed',0)
    b.furniture(13,0,16,'Furniture_Village_Bed',0)
    b.furniture(9,0,14,'Furniture_Village_Counter')
    b.furniture(13,0,14,'Furniture_Village_Counter')
    b.furniture(17,0,16,'Furniture_Village_Counter')
    b.furniture(18,0,13,'Aetherhaven_Management_Block')
    b.furniture(18,1,17,'Furniture_Village_Shelf_Full',2)
    for x,z in [(6,5),(11,11),(11,16),(17,14)]:
        b.put(x,4,z,b.plank); b.furniture(x,3,z,'Deco_Lantern_Ceiling')
    for x,z in [(3,1),(9,1)]:
        b.furniture(x,0,z,'Furniture_Village_Planter'); b.put(x,1,z,'Plant_Flower_Common_White2')
    b.prop('Potion_Health_Lesser',8.5,1.02,5.5,.65,.2)
    b.prop('Potion_Health_Lesser',9.5,1.02,14.5,.65,-.3)
    b.prop('Potion_Health_Lesser',13.5,1.02,14.5,.65,.3)
    b.prop('Plant_Crop_Health1',17.4,1.02,16.4,.6,.6)
    b.prop('Deco_Book_Pile_Small',4.45,1.02,5.4,.65,.2)
    b.save()

PERSON=dict(kind='doctor',model='Doctor',name='Elara Willowmere',building='Clinic',level=3,
    voice='MellowFemale',order=23,season='Autumn',day=9,gold=75,wood=280,rock=160,
    management=[18,0,13],bench='Furniture_Village_Bed',benchpos=[11,0,13],activity='read',
    loves=['Potion_Health_Lesser','Plant_Crop_Health1'],likes=['Plant_Crop_Health2','Ingredient_Fabric_Scrap_Linen'],
    title='A Place to Recover',description='Build Elara a clinic to care for injured residents and guards.',
    greetings=['Come in. There is always time to make sure you are well.',
        'A quiet room and clean supplies can do a great deal.', 'Tell me where it hurts. We will take things one step at a time.'],
    offer='Would you open a clinic in our town?',
    pitch='Your guards need somewhere safe to recover between expeditions. A waiting room, a small ward and a medicine cabinet would let me look after them here. Bring Lesser Health Potions to keep the cabinet supplied.',
    accepted='Here is the clinic plan. Keep the aisle beside the beds open so our patients can reach me.',
    welcome='The ward is ready. Ask me about clinic care to donate medicine and see who needs help. Bring injured guards here with Follow, then let them wait nearby while they recover.',
    how='I treat one injured resident at a time inside the clinic, starting with the most serious injuries nearby. A course takes twelve quiet seconds and restores up to forty health. Each Lesser Health Potion supplies eighty points of medicine. Fighting or leaving interrupts the course without spending medicine. I cannot treat anyone while away, and the Gaia Altar remains the place for revival.')

if __name__ == '__main__':
    for stormwind in (False, True): building(stormwind)
    create(PERSON)
    path=SERVER/'Aetherhaven/Dialogue/aetherhaven_doctor.json'; data=read(path)
    data['nodes']['main_hub']['choices'].insert(1,dict(text='aetherhaven_doctor.clinic', next=None,
        condition=dict(type='not',condition=dict(type='npc_binding_is_visitor')),whenFalse='hide',actions=[dict(type='open_clinic')]))
    # Three useful care packages. Their balance is provisional along with clinic costs.
    rewards=[]
    for reputation,item,count in [(25,'Potion_Health_Lesser',5),(50,'Potion_Health',3),(75,'Potion_Health_Greater',2)]:
        reward='rep_doctor_'+str(reputation); node='rep_reward_'+str(reputation)
        rewards.append(dict(rewardId=reward,minReputation=reputation,itemId=item,itemCount=count,dialogueNodeId=node))
        data['nodes'][node]=dict(speaker='aetherhaven_doctor.speaker',text='aetherhaven_doctor.reward.'+str(reputation),speechClip='Agree',
            actions=[dict(type='reputation_reward_grant',rewardId=reward)],
            choices=[dict(text='aetherhaven_ui_town.aetherhaven.reputation.reward.continue',next='main_hub')])
    write(path,data)
    path=SERVER/'Aetherhaven/Villagers/Aetherhaven_Doctor.json';data=read(path);data['reputationMilestones']=rewards;write(path,data)
    lang=SERVER/'Languages/en-US/aetherhaven_doctor.lang'
    existing='\n'.join(line for line in lang.read_text(encoding='utf-8').splitlines()
        if not line.startswith(('reward.', 'clinic=')))
    lang.write_text(existing+'\nclinic=Let us check the clinic.\n'
        +'reward.25=You have helped make this a caring town. Take these supplies for the medicine cabinet.\n'
        +'reward.50=For your next journey. These remedies are stronger, so save them for when you need them.\n'
        +'reward.75=I trust you to bring everyone home. Take my best remedies with you.\n',encoding='utf-8')
    path=SERVER/'Aetherhaven/plot_creator_main_constructions.json';data=read(path)
    if not any(x['id']=='plot_doctor' for x in data['mainConstructions']):
        data['mainConstructions'].append(dict(id='plot_doctor',labelLang='aetherhaven_doctor.building.name'))
    write(path,data)
    path=SERVER/'Languages/en-US/aetherhaven_town.lang';text=path.read_text(encoding='utf-8')
    if 'aetherhaven.profession.kind.doctor=' not in text:text+='\naetherhaven.profession.kind.doctor=Doctor\n'
    path.write_text(text,encoding='utf-8')
    # Existing plot marker language with an original portrait medallion for this plan.
    portrait=Image.open(ROOT/'src/main/resources/Common/Icons/ModelsGenerated/Aetherhaven_Doctor.png').convert('RGBA')
    # Use the supplied character artwork at icon scale, with a clean transparent canvas.
    icon=Image.new('RGBA',(64,64));portrait.thumbnail((56,56),Image.Resampling.LANCZOS)
    icon.alpha_composite(portrait,((64-portrait.width)//2,(64-portrait.height)//2))
    icon.save(ROOT/'src/main/resources/Common/Icons/ItemsGenerated/Aetherhaven_Token_Doctor.png')
