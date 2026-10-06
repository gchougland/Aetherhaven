"""Author Jack's initial assets without altering the shared skeleton or user prefabs."""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT.parent / 'HytaleSourceCode/hytale-shared-source/HytaleAssets'
RES = ROOT / 'src/main/resources'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise FileExistsError(path)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

model = json.loads((ASSETS / 'Common/NPC/Undead/Skeleton/Models/Model.blockymodel').read_text(encoding='utf-8'))
def remove_skull(nodes, head=False):
    for node in nodes:
        hidden = head or node['name'] == 'Head'
        if hidden:
            node['shape']['type'] = 'none'
            node['shape']['visible'] = False
            node['shape']['settings'] = {'isPiece': node['name'] == 'Head'}
            node['shape']['textureLayout'] = {}
        remove_skull(node.get('children', []), hidden)
remove_skull(model['nodes'])
write(RES / 'Common/Characters/Aetherhaven/Halloween/Headless_Skeleton.blockymodel', model)
base = {
    'Parent': 'Skeleton', 'Model': 'Characters/Aetherhaven/Halloween/Headless_Skeleton.blockymodel',
    'Texture': 'NPC/Undead/Skeleton/Models/Model_Textures/White.png',
    'DefaultAttachments': [], 'RandomAttachmentSets': {},
    'Icon': 'Icons/ModelsGenerated/Aetherhaven_Halloween.png',
    'IconProperties': {'Scale': 0.5, 'Rotation': [0, 19, 0], 'Translation': [0, -99]}
}
write(RES / 'Server/Models/Villager/Aetherhaven_Halloween_Headless.json', base)
headed = copy.deepcopy(base)
headed['DefaultAttachments'] = [{'Model': 'Cosmetics/Head/Jack_O_Lantern_Hat.blockymodel',
    'Texture': 'Items/Halloween_Props/Pumpkin_Carve_Texture_02.png'}]
write(RES / 'Server/Models/Villager/Aetherhaven_Halloween.json', headed)
for suffix in ['', '_Rescue']:
    path = RES / f'Server/NPC/Roles/Aetherhaven/Aetherhaven_Clown{suffix}.json'
    role = json.loads(path.read_text(encoding='utf-8'))
    role = json.loads(json.dumps(role).replace('Aetherhaven_Clown', 'Aetherhaven_Halloween').replace('"clown"', '"halloween"'))
    role['Appearance'] = 'Aetherhaven_Halloween'
    role['IsMemory'] = False
    role['Parameters']['NameTranslationKey']['Value'] = 'aetherhaven_halloween.npc.name'
    write(RES / f'Server/NPC/Roles/Aetherhaven/Aetherhaven_Halloween{suffix}.json', role)
villager = {
    'npcRoleId': 'Aetherhaven_Halloween', 'dialogueVillagerKind': 'halloween',
    'residentTreeId': 'aetherhaven_halloween', 'visitorTreeId': 'aetherhaven_visitor_generic',
    'workConstructionId': 'plot_halloween', 'displayName': 'Jack Hollow',
    'portraitIcon': 'Aetherhaven_Halloween.png', 'speechVoiceId': 'GravelyMaleLower',
    'uiSortOrder': 29, 'innPoolEligible': True, 'innPoolOrder': 29, 'innPoolWeight': 1,
    'visitorBindingKind': 'visitor_halloween', 'innPoolRequires': {'completedQuestIds': ['q_halloween_rescue']},
    'giftLoves': ['Aetherhaven_Spooky_Candy', 'Deco_Halloween_Pumpkin_Scary'],
    'giftLikes': ['Food_Pie_Pumpkin', 'Deco_Halloween_Pumpkin_Cute'],
    'giftDislikes': ['Ingredient_Poop'],
    'dialogueGreetingLangKeys': ['aetherhaven_halloween.dialogue.greeting'],
    'birthdaySeason': 'Autumn', 'birthdayDay': 28,
    'reputationMilestones': [
        {'rewardId':'rep_halloween_25','minReputation':25,'itemId':'Aetherhaven_Spooky_Candy','itemCount':5},
        {'rewardId':'rep_halloween_50','minReputation':50,'itemId':'Aetherhaven_Villager_Cosmetic_Jack_O_Lantern_Hat','itemCount':1},
        {'rewardId':'rep_halloween_75','minReputation':75,'itemId':'Aetherhaven_Spooky_Candy','itemCount':15}
    ]
}
for reward in villager['reputationMilestones']:
    reward['dialogueNodeId']=f"rep_reward_{reward['minReputation']}"
write(RES / 'Server/Aetherhaven/Villagers/Aetherhaven_Halloween.json', villager)
schedule = json.loads((RES / 'Server/Aetherhaven/VillagerSchedules/Aetherhaven_Clown.json').read_text(encoding='utf-8'))
write(RES / 'Server/Aetherhaven/VillagerSchedules/Aetherhaven_Halloween.json', schedule)

# Seven-block shelter with an open two-block entrance, clear interior and records shelf.
cells = {}
def block(x, y, z, name, **extra):
    cells[x,y,z] = dict(x=x, y=y, z=z, name=name, **extra)
for x in range(-3,4):
    for z in range(-3,4):
        block(x,-1,z,'Wood_Hardwood_Planks')
        for y in range(4): block(x,y,z,'Empty')
        block(x,4,z,'Wood_Softwood_Roof_Flat')
for x in [-3,3]:
    for z in [-3,3]:
        for y in range(4):block(x,y,z,'Wood_Beech_Trunk')
for x in range(-2,3):block(x,0,-3,'Wood_Hardwood_Beam')
for z in range(-2,3):
    for x in [-3,3]:block(x,0,z,'Wood_Hardwood_Beam')
block(-2,0,2,'Wood_Hardwood_Beam')
block(-2,1,2,'Aetherhaven_Management_Block',components={'Components':{'AetherhavenManagementBlock':{'PlotId':'','TownId':''}}})
block(2,0,-2,'Deco_Halloween_Pumpkin_Scary')
write(RES / 'Server/Prefabs/Halloween_Shelter.prefab.json', {'version':8,'blockIdVersion':11,'anchorX':0,'anchorY':0,'anchorZ':0,'blocks':list(cells.values()),'fluids':[]})
write(RES / 'Server/Aetherhaven/Buildings/plot_halloween.json', {
    'id':'plot_halloween','displayName':'Pumpkin Shelter',
    'description':'A simple shelter for Jack Hollow and his curious games.',
    'prefabPath':'Halloween_Shelter.prefab.json','plotTokenItemId':'Aetherhaven_Plot_Token',
    'plotAnchorOffset':[0,1,0],'rotationYaw':'None','frontFacing':'South','styleId':'core',
    'boundsLocal':{'min':[-3,-1,-3],'max':[3,4,3]},'managementBlockLocalPos':[-2,1,2],
    'selfBuildGameDays':1.0,'treasuryGoldCoinCost':25,'materials':[{'resourceTypeId':'Wood_All','count':80}],
    'tags':['work'],'pois':[{'localX':0,'localY':0,'localZ':0,'tags':['WORK','workActivity:leisure'],
        'capacity':1,'blockTypeId':'Wood_Hardwood_Beam','interactionKind':'WORK_SURFACE',
        'interactionTargetYawDegrees':180,'equipmentProfileId':'work_bard','workResidentKind':'halloween'}]
})

# Recruitment uses the same town quest, token and visitor lock flow as existing residents.
quests = ROOT / 'subplugin-assets/Quests/Server/Aetherhaven/Quests/Town'
rescue = json.loads((quests/'q_clown_rescue.json').read_text(encoding='utf-8'))
rescue.update(id='q_halloween_rescue',title='A Knock from Within',description='You met Jack Hollow inside a coffin in October.')
rescue.pop('titleLangKey',None); rescue.pop('descriptionLangKey',None)
write(quests/'q_halloween_rescue.json',rescue)
quest = json.loads((quests/'q_clown_tent.json').read_text(encoding='utf-8'))
quest = json.loads(json.dumps(quest).replace('q_clown_tent','q_halloween_shelter').replace('plot_clown_tent','plot_halloween').replace('Aetherhaven_Clown','Aetherhaven_Halloween'))
quest.update(title="Jack's Pumpkin Shelter",description='Build a simple shelter so Jack Hollow can settle in town.')
quest.pop('titleLangKey',None);quest.pop('descriptionLangKey',None)
for obj,text in zip(quest['objectives'],["Take Jack's Pumpkin Shelter token.",'Build the Pumpkin Shelter.','Talk to Jack.']):
    obj['text']=text;obj.pop('textLangKey',None)
write(quests/'q_halloween_shelter.json',quest)

prefix='aetherhaven_halloween.dialogue.'
def choice(key, **extra): return dict(text=prefix+key, **extra)
def cond(kind, **extra): return dict(type=kind, **extra)
def node(body,choices): return dict(speaker='aetherhaven_halloween.npc.name',text=prefix+body,choices=choices)
active=cond('town_quest_active',questId='q_halloween_shelter')
completed=cond('town_quest_completed',questId='q_halloween_shelter')
built=cond('town_has_complete_plot',constructionId='plot_halloween')
nodes={
 'main_hub':node('greeting',[
    choice('profile',actions=[{'type':'open_core_mastery'}]),
    choice('shelter_offer',condition=cond('not',condition=cond('or',conditions=[active,completed])),whenFalse='hide',next='shelter'),
    choice('shelter_done',condition=cond('and',conditions=[active,built]),whenFalse='hide',actions=[{'type':'complete_quest','id':'q_halloween_shelter'}],next='settled'),
    choice('gift',condition=cond('and',conditions=[cond('not',condition=cond('npc_binding_is_visitor')),cond('player_holds_any_item')]),whenFalse='hide',giftDisableWhenNotAllowed=True,next='gift_confirm'),
    choice('goodbye',next=None)]),
 'shelter':node('shelter_body',[choice('accept',actions=[{'type':'start_quest','id':'q_halloween_shelter'}],next='accepted'),choice('later',next=None)]),
 'accepted':node('accepted_body',[choice('goodbye',next=None)]),
 'settled':node('settled_body',[choice('goodbye',next=None)])
}
# Reuse the tested gift confirmation branch with its existing shared localization.
clown = json.loads((RES/'Server/Aetherhaven/Dialogue/aetherhaven_clown.json').read_text(encoding='utf-8'))
for key,value in clown['nodes'].items():
    if key.startswith('gift_'):
        value['speaker']='aetherhaven_halloween.npc.name'
        if value.get('text','').startswith('aetherhaven_dialogue_clown.'):
            value['text']=prefix+'gift.'+key.removeprefix('gift_').replace('_','.')
        nodes[key]=value
nodes['shelter']['speechClip']='Question'
nodes['shelter']['choices'][0]['speechClip']='Agree'
for reward in villager['reputationMilestones']:
    nodes[reward['dialogueNodeId']]={
        'speaker':'aetherhaven_halloween.npc.name','text':prefix+f"reward.{reward['minReputation']}",
        'speechClip':'Agree','actions':[{'type':'reputation_reward_grant','rewardId':reward['rewardId']}],
        'choices':[{'text':'aetherhaven_ui_town.aetherhaven.reputation.reward.continue','next':'main_hub'}]}
write(RES/'Server/Aetherhaven/Dialogue/aetherhaven_halloween.json',{'id':'aetherhaven_halloween','entry':'main_hub','nodes':nodes})
write(RES/'Server/Aetherhaven/Dialogue/aetherhaven_halloween_rescue.json',{'id':'aetherhaven_halloween_rescue','entry':'root','nodes':{
    'root':node('rescue_body',[choice('rescue_accept',condition=cond('halloween_rescue_available'),whenFalse='hide',speechClip='Agree',actions=[{'type':'complete_quest','id':'q_halloween_rescue'},{'type':'despawn_npc','requiredCompletedTownQuest':'q_halloween_rescue'},{'type':'close'}],next=None),choice('goodbye',next=None)])}})
