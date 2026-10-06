"""Connected specialist friendship rewards. Uses only existing native ingredients."""
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
SERVER=ROOT/'src/main/resources/Server'
REPUTATION=ROOT/'subplugin-assets/ReputationUnlocks/Server/Aetherhaven/WorkplaceRecipes'

def read(p):return json.loads(p.read_text(encoding='utf-8'))
def write(p,data):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')

rewards={
    'druid':('Botanist','Aetherhaven_Verdant_Catalyst',[
        'Good tools make gentle hands. This tool roll helps a gardener tend crops, plant herbs and gather them with care. Equip it through a townsfolk profile.',
        'Your garden deserves more than one good pair of hands. I have shared my tool roll plans with the town. A practiced blacksmith can make more at an anvil.',
        'These notes describe how to concentrate the life in three kinds of alchemical plants into a Verdant Catalyst. Your alchemist can teach a skilled brewer to prepare it. The town now knows the recipe.']),
    'alchemist':('Alchemist','Aetherhaven_Shard_Of_Gaia',[
        'A careful brewer keeps every measure close at hand. Take this case for one of your Apprentices. Equip it through their profile to help them brew more quickly.',
        'Let us equip the rest of your brewers. I have shared the plans for my case with the town. Choose Browse all recipes at an alchemy workplace to find them.',
        'I trust you with my most demanding formula. It draws on the strongest alchemical plants to form a Shard of Gaia. Find it under Browse all recipes at an alchemy workplace. It needs an expert brewer and a fully improved bench.'])
}

for kind,(kit,advanced,texts) in rewards.items():
    role='Aetherhaven_'+kind.capitalize();lang='aetherhaven_'+kind
    path=SERVER/f'Aetherhaven/Villagers/{role}.json';data=read(path)
    data['reputationMilestones']=[]
    dialogue_path=SERVER/f'Aetherhaven/Dialogue/{lang}.json';dialogue=read(dialogue_path)
    for level,text in zip((25,50,75),texts):
        reward=f'rep_{kind}_{level}'
        data['reputationMilestones'].append(dict(rewardId=reward,minReputation=level,
            itemId=f'Aetherhaven_{kit}_Kit' if level==25 else '',itemCount=1 if level==25 else 0,
            dialogueNodeId=f'rep_reward_{level}',learnRecipeItemId=None if level==25 else f'Aetherhaven_{kit}_Kit' if level==50 else advanced))
        dialogue['nodes'][f'rep_reward_{level}']=dict(speaker=lang+'.speaker',text=f'{lang}.reward.{level}',speechClip='Agree',
            actions=[dict(type='reputation_reward_grant',rewardId=reward)],
            choices=[dict(text='aetherhaven_ui_town.aetherhaven.reputation.reward.continue',next='main_hub')])
    write(path,data);write(dialogue_path,dialogue)
    path=SERVER/f'Languages/en-US/{lang}.lang'
    lines=[line for line in path.read_text(encoding='utf-8').splitlines() if not line.startswith('reward.')]
    path.write_text('\n'.join(lines)+'\n'+''.join(f'reward.{level}={text}\n' for level,text in zip((25,50,75),texts)),encoding='utf-8')

# Keep formulas with their existing output items in the optional Reputation Unlocks
# pack, so disabling that pack does not expose recipes for unavailable items.
for ident,name,output,tier,skill,bench,gold,essence,seconds in [
    ('brew_verdant_catalyst','Verdant Catalyst','Aetherhaven_Verdant_Catalyst',2,6,3,1,10,45),
    ('brew_gaia_shard','Shard of Gaia','Aetherhaven_Shard_Of_Gaia',3,9,4,3,20,60)]:
    inputs=[dict(itemId=f'Plant_Crop_{family}{tier}',count=1) for family in ('Health','Stamina','Mana')]
    inputs += [dict(itemId='Ingredient_Bar_Gold',count=gold),dict(itemId='Ingredient_Life_Essence',count=essence)]
    write(REPUTATION/f'{ident}.json',dict(id=ident,name=name,workplace='alchemy',sourceRecipe='workplace:'+ident,
        bench='Alchemybench',benchTier=bench,skillLevel=skill,inputs=inputs,output=output,outputCount=1,
        workSeconds=seconds,requiresKnowledge=True))
print('Authored three rewards each for Rowan and Orin, plus two optional town brewing formulas.')
