"""Author the Druid and Alchemist recruitment content from the shared villager schema.

Historical reference only. Publishing is retired; use import_v4_user_buildings.py
for the current authored Core buildings. Existing recruitment content is maintained directly.
"""
RETIRED_PUBLISH = ('Generated specialist publishing is retired. Use tools/import_v4_user_buildings.py '
                   'to import the authored Core prefabs. Removed Stormwind variants must not be recreated.')
if __name__ == '__main__':
    raise SystemExit(RETIRED_PUBLISH)

from pathlib import Path
import copy
import json
import shutil

ROOT=Path(__file__).resolve().parents[1]
SERVER=ROOT/'src/main/resources/Server'
QUESTS=ROOT/'subplugin-assets/Quests/Server/Aetherhaven/Quests/Town'

def read(path): return json.loads(path.read_text(encoding='utf-8'))
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')

PEOPLE=[dict(kind='druid',model='Druid',name='Rowan Mossweald',building='Conservatory',level=4,
    voice='MellowFemale',order=21,season='Spring',day=12,gold=85,wood=420,rock=190,
    management=[17,0,16],bench='Bench_Farming',benchpos=[5,0,5],activity='tend',
    loves=['Plant_Crop_Mana1','Plant_Crop_Health1'],likes=['Plant_Seeds_Stamina1','Soil_Dirt_Crystal'],
    title='A Garden of Quiet Wonders',
    description='Build Rowan a conservatory to cultivate plants for alchemy.',
    greetings=['A little patience makes a very good gardener.',
        'The crystal beds are quiet today. Come, tell me what you need.',
        'Blood rose, azure fern, storm thistle. Each has its own rhythm.'],
    offer='Would you like to tend a conservatory here?',
    pitch='There is more to a garden than supper. Give me a sheltered court with crystal soil and I can teach your townsfolk to cultivate plants for potions. We will use the same seeds you find out in the world.',
    accepted='Here is my conservatory plan. Keep the path beside the crystal beds clear so the gardeners can reach each plant.',
    welcome='A lovely place to put down roots. Speak to me about workplace upgrades to unlock Herb Cultivation, then mark a workplace with your Building Staff. Enable Herbs in the town journal when you are ready to employ a gardener.',
    how='A cultivation workplace needs a Farmer\'s Workbench, crystal soil and room above each bed. Set a growing task and put its seeds in General storage. Townsfolk with Logistics enabled bring the seeds. A gardener plants them, leaves them to grow, then returns to harvest. Collect the ingredients from Crafted Goods and move them to General storage for brewing. These gardens supply ingredients, not food support.'),
 dict(kind='alchemist',model='Alchemist',name='Orin Glasswick',building='Laboratory',level=5,
    voice='MellowMale',order=22,season='Winter',day=6,gold=100,wood=400,rock=210,
    management=[19,0,13],bench='Bench_Alchemy',benchpos=[11,0,5],activity='mix',
    loves=['Plant_Crop_Health2','Plant_Crop_Mana2'],likes=['Ingredient_Charcoal','Plant_Crop_Stamina1'],
    title='Measure Twice, Brew Once',
    description='Build Orin a laboratory where townsfolk can brew expedition supplies.',
    greetings=['The green bottle is quite safe. The unlabelled one stays on the shelf.',
        'Good ingredients, careful measures, and a clean bench. That is most of the secret.',
        'An expedition goes better with a few remedies packed.'],
    offer='Would you like to open a laboratory here?',
    pitch='I could use a proper brewing hall, a little ingredient pantry, and somewhere to keep my notes. Your townsfolk can learn to brew health, stamina and energy potions. Better recipes will need practiced hands and better benches.',
    accepted='Take this laboratory plan. Leave room around the alchemy benches for the brewers and their deliveries.',
    welcome='The benches are ready. Speak to me about workplace upgrades to unlock Alchemy. Mark a brewing workplace, choose its recipes and enable Alchemy in the town journal. Our couriers will bring the ingredients.',
    how='Every brewing task lists its ingredients, skill level and required alchemy bench tier. Stock the ingredients in town storage and enable Logistics for deliveries. A brewer must work at the bench to finish a potion. Finished batches go to Crafted Goods in the warehouse and brewing pauses when that storage is full.')]

def create(person):
    # Preserve the historical content recipe for reference without publishing stale assets.
    raise SystemExit(RETIRED_PUBLISH)
    p=person;k=p['kind'];model=p['model'];role='Aetherhaven_'+model
    lang='aetherhaven_'+k;construction='plot_'+k;quest='q_'+k;building=p['building']
    def substitute(value):
        if isinstance(value,str):
            return value.replace('Aetherhaven_Warehouse_Manager',role).replace('aetherhaven_warehouse_manager',lang).replace('warehouse_manager',k).replace('plot_warehouse',construction).replace('q_warehouse',quest)
        if isinstance(value,list):return [substitute(v) for v in value]
        if isinstance(value,dict):return {key:substitute(v) for key,v in value.items()}
        return value
    definition=substitute(read(SERVER/'Aetherhaven/Villagers/Aetherhaven_Warehouse_Manager.json'))
    target=SERVER/f'Aetherhaven/Villagers/{role}.json'
    rewards=read(target).get('reputationMilestones',[]) if target.exists() else []
    definition.update(displayName=p['name'],speechVoiceId=p['voice'],uiSortOrder=p['order'],innPoolOrder=p['order'],minimumTownLevel=p['level'],
        birthdaySeason=p['season'],birthdayDay=p['day'],giftLoves=p['loves'],giftLikes=p['likes'],reputationMilestones=rewards)
    definition['giftDislikes']=[item for item in p.get('dislikes',definition.get('giftDislikes',[])) if item not in p['loves'] and item not in p['likes']]
    write(target,definition)
    npc=substitute(read(SERVER/'NPC/Roles/Aetherhaven/Aetherhaven_Warehouse_Manager.json'))
    npc['Appearance']=model
    write(SERVER/f'NPC/Roles/Aetherhaven/{role}.json',npc)
    schedule=read(SERVER/'Aetherhaven/VillagerSchedules/Aetherhaven_Farmer.json')
    if k=='alchemist':
        for transition in schedule['transitions']:
            if transition['hour']==6:transition['hour']=8
    write(SERVER/f'Aetherhaven/VillagerSchedules/{role}.json',schedule)

    dialogue=substitute(read(SERVER/'Aetherhaven/Dialogue/aetherhaven_warehouse_manager.json'))
    dialogue['nodes']={key:value for key,value in dialogue['nodes'].items() if not key.startswith('rep_reward_')}
    old_dialogue=SERVER/f'Aetherhaven/Dialogue/{lang}.json'
    if old_dialogue.exists():
        dialogue['nodes'].update({key:value for key,value in read(old_dialogue)['nodes'].items() if key.startswith('rep_reward_')})
    write(old_dialogue,dialogue)
    content=substitute(read(QUESTS/'q_warehouse.json'))
    content.update(title=p['title'],description=p['description'])
    for objective,text in zip(content['objectives'],[f"Collect the {building.lower()} plot token from {p['name'].split()[0]}.",f'Build the {building.lower()}.',f"Return to {p['name'].split()[0]}."]):objective['text']=text
    write(QUESTS/f'{quest}.json',content)

    base=read(SERVER/'Aetherhaven/Buildings/plot_warehouse.json')
    base.update(id=construction,displayName=building,description=p['description'],prefabPath=building+'.prefab.json',
        plotTokenItemId='Aetherhaven_Plot_Token_'+model,managementBlockLocalPos=p['management'],treasuryGoldCoinCost=p['gold'],
        displayNameLangKey=lang+'.building.name',tags=['work',k],
        materials=[dict(resourceTypeId='Wood_All',count=p['wood']),dict(resourceTypeId='Rock',count=p['rock']),dict(itemId=p['bench'],count=2)],
        # Work POIs name the standing cell, not the bench anchor. Keep the bench
        # one cell ahead, matching the Building Editor's stand-cell convention.
        pois=[dict(localX=p['benchpos'][0],localY=0,localZ=p['benchpos'][2]+1,tags=['WORK','workActivity:'+p['activity']],capacity=1,
            blockTypeId='Wood_Softwood_Planks',interactionKind='WORK_SURFACE',workResidentKind=k,interactionTargetYawDegrees=0.0)])
    if k=='druid':base['materials'].append(dict(itemId='Soil_Dirt_Crystal',count=9))
    write(SERVER/f'Aetherhaven/Buildings/{construction}.json',base)
    variant=copy.deepcopy(base)
    variant.update(id='plot_stormwind_'+k,displayName='Stormwind '+building,countsAsConstructionId=construction,styleId='stormwind',
        prefabPath='Stormwind/'+building+'.prefab.json',plotTokenItemId='Aetherhaven_Plot_Token',displayNameLangKey=lang+'.building.stormwind')
    variant['pois'][0]['blockTypeId']='Wood_Hardwood_Planks'
    write(SERVER/f'Aetherhaven/Buildings/Stormwind/plot_stormwind_{k}.json',variant)
    for style,path in [('Core',SERVER/f'Prefabs/{building}.prefab.json'),('Stormwind',SERVER/f'Prefabs/Stormwind/{building}.prefab.json')]:
        path.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ROOT/f'build/art/alchemy-prefabs/{building}_{style}_Draft.prefab.json',path)
    token=substitute(read(SERVER/'Item/Items/Aetherhaven/Aetherhaven_Plot_Token_Warehouse.json'))
    token['Icon']='Icons/ItemsGenerated/Aetherhaven_Token_'+model+'.png'
    write(SERVER/f'Item/Items/Aetherhaven/Aetherhaven_Plot_Token_{model}.json',token)
    text={f'npcRoles.{role}.name':p['name'],'profession':model,'speaker':p['name'],'hub':p['greetings'][0],
        'quest.title':p['title'],'quest.description':p['description'],
        'quest.objective.token':content['objectives'][0]['text'],'quest.objective.build':content['objectives'][1]['text'],'quest.objective.talk':content['objectives'][2]['text'],
        'bye':'See you later.','how':'Tell me about your work.','offer':p['offer'],'finish':f'The {building.lower()} is ready.',
        'progress':f'About your {building.lower()}...','accept':'Let us build it.','later':'Perhaps later.',
        'offer.body':p['pitch'],'accepted.body':p['accepted'],'cancel':'I need to put this project aside.',
        'building.body':f"I will keep my things packed until the {building.lower()} is ready. The plot plan lists the materials we need.",
        'cancelled.body':'All right. Find me when you are ready to begin again.','back':'Understood.',
        'welcome.body':p['welcome'],'how.body':p['how'],'gift.confirm':'You brought something for me? Let me see.',
        'gift_love':'What a thoughtful gift. I have just the place for this.',
        'gift_like':'Oh, this will be useful. Thank you.', 'gift_neutral':'Thank you for thinking of me.',
        'gift_dislike':'I appreciate the thought, though this is not quite my sort of thing.',
        'building.name':building,'building.stormwind':'Stormwind '+building,'token.name':building+' Plot',
        'token.description':f'Stake a plot for a {building.lower()}.'}
    for i,greeting in enumerate(p['greetings']):text['greeting.'+str(i)]=greeting
    for reaction in ['love','like','neutral','dislike']:text['gift_'+reaction+'_birthday']='You remembered my birthday! '+text['gift_'+reaction]
    langpath=SERVER/f'Languages/en-US/{lang}.lang'
    preserved=[]
    if langpath.exists():preserved=[line for line in langpath.read_text(encoding='utf-8').splitlines() if line.startswith('reward.')]
    langpath.write_text('\n'.join(key+'='+value for key,value in text.items())+'\n'+'\n'.join(preserved)+'\n',encoding='utf-8')
    print(role,construction,'Town Level',p['level'])

if __name__=='__main__':
    for person in PEOPLE:create(person)
    path=SERVER/'Aetherhaven/plot_creator_main_constructions.json';data=read(path)
    for person in PEOPLE:
        entry=dict(id='plot_'+person['kind'],labelLang='aetherhaven_'+person['kind']+'.building.name')
        if not any(v['id']==entry['id'] for v in data['mainConstructions']):data['mainConstructions'].append(entry)
    write(path,data)
    path=SERVER/'Languages/en-US/aetherhaven_town.lang';text=path.read_text(encoding='utf-8')
    for person in PEOPLE:
        key='aetherhaven.profession.kind.'+person['kind']
        if key+'=' not in text and key+' =' not in text:text+='\n'+key+'='+person['model']+'\n'
    path.write_text(text,encoding='utf-8')
