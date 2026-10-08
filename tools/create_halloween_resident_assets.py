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

# The authored Spook House prefab and building definition are maintained separately.
# Do not regenerate or furnish this building here.

# Recruitment uses the same town quest, token and visitor lock flow as existing residents.
quests = ROOT / 'subplugin-assets/Quests/Server/Aetherhaven/Quests/Town'
rescue = json.loads((quests/'q_clown_rescue.json').read_text(encoding='utf-8'))
rescue.update(id='q_halloween_rescue',title='A Knock from Within',description='You met Jack Hollow inside a coffin in October.')
rescue.pop('titleLangKey',None); rescue.pop('descriptionLangKey',None)
write(quests/'q_halloween_rescue.json',rescue)
quest = json.loads((quests/'q_clown_tent.json').read_text(encoding='utf-8'))
quest = json.loads(json.dumps(quest).replace('q_clown_tent','q_halloween_shelter').replace('plot_clown_tent','plot_halloween').replace('Aetherhaven_Clown','Aetherhaven_Halloween'))
quest.update(title="Jack's Spook House",description='Build a Spook House so Jack Hollow can settle in town.')
quest.pop('titleLangKey',None);quest.pop('descriptionLangKey',None)
for obj,text in zip(quest['objectives'],["Take Jack's Spook House token.",'Build the Spook House.','Talk to Jack.']):
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
