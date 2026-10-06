"""Six artisan friendship milestones with original kits and workplace furnishings."""
import json
from pathlib import Path
from generate_profession_kits import make, ROOT, SERVER

def read(path):return json.loads(path.read_text(encoding='utf-8'))
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')

ARTISANS=[
 ('Crafter','crafting','Workbench',"Joiner's Toolbox",'PatternCabinet','Pattern Cabinet',
  [('Ingredient_Bar_Iron',4),('Ingredient_Leather_Light',3),('Wood_Beech_Trunk',6)],
  [('Ingredient_Bar_Iron',6),('Ingredient_Fabric_Scrap_Linen',4),('Wood_Beech_Trunk',16)],
  'Keep your tools close and the work goes more smoothly. Give this toolbox to an Apprentice through their profile.',
  'I have shared the toolbox plans with the town. Your crafters can now make a set for each Apprentice who needs one.',
  'These pattern cabinet plans will help your workshop stay organized. Have an Apprentice build one and place it inside a Crafting workplace. It helps the worker finish sooner. More cabinets in the same site give no extra benefit.'),
 ('Leatherworker','leatherworking','Tannery',"Leatherworker's Roll",'StitchingFrame','Stitching Frame',
  [('Ingredient_Bar_Iron',3),('Ingredient_Leather_Light',6),('Ingredient_Fibre',6)],
  [('Ingredient_Bar_Iron',4),('Ingredient_Leather_Medium',8),('Wood_Beech_Trunk',12),('Ingredient_Fibre',12)],
  'A sharp awl and good thread save a lot of trouble. Equip this roll through an Apprentice profile to help with leatherwork.',
  'I have shared my tool roll plans with the town. Your leatherworkers can make more at a tanning rack.',
  'I trust you with my stitching frame plans. Have an Apprentice build one for a Leatherworking site. It keeps the work steady and saves time. A second frame in the same site gives no extra benefit.')]

def main():
    lang=SERVER/'Languages/en-US/aetherhaven_profession_kits.lang'
    lines=lang.read_text(encoding='utf-8').splitlines();dialogue_lines=[]
    for role,skill,bench,title,fixture,fixture_title,kit_cost,fixture_cost,*speeches in ARTISANS:
        kind=role.lower();kit='Aetherhaven_'+role+'_Kit';block='Aetherhaven_'+fixture
        make(role);make(fixture,block=True)
        write(SERVER/f'Aetherhaven/ProfessionKits/{kind}.json',dict(itemId=kit,name=title,skill=skill,workSpeedBonus=.15,extraCarryingStacks=0))
        write(SERVER/f'Aetherhaven/WorkplaceFurnishings/{kind}.json',dict(blockId=block,name=fixture_title,workplace=skill,workSpeedBonus=.10))
        for suffix,name,output,inputs,level,tier in [('kit',title,kit,kit_cost,3,1),('furnishing',fixture_title,block,fixture_cost,5,2 if kind=='crafter' else 1)]:
            ident=f'craft_{kind}_{suffix}'
            write(SERVER/f'Aetherhaven/WorkplaceRecipes/{ident}.json',dict(id=ident,name=name,workplace=skill,
                sourceRecipe='workplace:'+ident,bench=bench,benchTier=tier,skillLevel=level,requiresKnowledge=True,
                inputs=[dict(itemId=item,count=count) for item,count in inputs],output=output,outputCount=1,workSeconds=30 if suffix=='kit' else 45))
        for ident,name,description in [(kind,title,f'Equip through a townsfolk profile for 15% faster {skill} work. Replaces the equipped profession kit. Skill requirements and material costs stay the same.'),
            (fixture.lower(),fixture_title,f'Place inside a {skill} workplace for 10% faster attended work. Duplicates do not stack. Does not replace the required workbench. Crafted by townsfolk from friendship plans.')]:
            lines=[line for line in lines if not line.startswith(ident+'.')]
            lines.extend([f'{ident}.name = {name}',f'{ident}.description = {description}'])
        path=SERVER/f'Aetherhaven/Villagers/Aetherhaven_{role}.json';villager=read(path)
        path_dialogue=SERVER/f'Aetherhaven/Dialogue/aetherhaven_{kind}.json';dialogue=read(path_dialogue)
        rewards=[r for r in villager.get('reputationMilestones',[]) if not r['rewardId'].startswith('rep_'+kind+'_')]
        for level,item,learn,text in zip((25,50,75),(kit,kit,block),(False,True,True),speeches):
            rid=f'rep_{kind}_{level}';node=f'rep_reward_{level}'
            rewards.append(dict(rewardId=rid,minReputation=level,itemId='' if learn else item,itemCount=0 if learn else 1,dialogueNodeId=node,learnRecipeItemId=item if learn else None))
            dialogue['nodes'][node]=dict(speaker=dialogue['nodes']['main_hub']['speaker'],text=f'aetherhaven_artisan_rewards.{kind}.{level}',speechClip='Agree',actions=[dict(type='reputation_reward_grant',rewardId=rid)],choices=[dict(text='aetherhaven_ui_town.aetherhaven.reputation.reward.continue',next='main_hub')])
            dialogue_lines.append(f'{kind}.{level}={text}')
        villager['reputationMilestones']=sorted(rewards,key=lambda row:row['minReputation']);write(path,villager);write(path_dialogue,dialogue)
    lang.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    (SERVER/'Languages/en-US/aetherhaven_artisan_rewards.lang').write_text('\n'.join(dialogue_lines)+'\n',encoding='utf-8')

if __name__=='__main__':main()
