"""First core villager reward batch. Keeps existing milestones and native ingredient IDs."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
from generate_profession_kits import make, ROOT, SERVER

def read(p):return json.loads(p.read_text(encoding='utf-8'))
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')

KITS=[
 ('Cook',"Chef's Utensil Roll",'cooking',.15,'crafting','Workbench',2,4,True,
  [('Ingredient_Bar_Iron',4),('Ingredient_Leather_Light',3),('Ingredient_Fabric_Scrap_Linen',6)],
  '15% faster cooking work. Food support, ingredient costs and skill requirements stay the same.'),
 ('Jeweler',"Jeweler's Case",'jewelry',.15,'jewelry','Aetherhaven_Jewelry_Crafting_Bench',1,4,False,
  [('Ingredient_Bar_Silver',4),('Ingredient_Leather_Light',4),('Ingredient_Crystal_White',2)],
  '15% faster jewelry crafting. Does not improve rarity or replace an Appraisal Bench.'),
 ('Rancher',"Ranchhand's Bag",'ranching',.15,'leatherworking','Tannery',1,3,False,
  [('Ingredient_Leather_Light',6),('Ingredient_Bar_Iron',2),('Ingredient_Fibre',8)],
  '15% faster ranch tending. Food support and the time before neglect stay the same.'),
 ('MasterSmith',"Master Smith's Chest",'blacksmithing',.25,'blacksmithing','Weapon_Bench',2,5,True,
  [('Ingredient_Bar_Iron',8),('Ingredient_Bar_Cobalt',2),('Ingredient_Leather_Medium',6)],
  '25% faster blacksmithing and armor crafting. Does not speed up furnaces or change skill requirements.')]

sheet=Image.new('RGBA',(640,185),'#283a3b');draw=ImageDraw.Draw(sheet)
lang=SERVER/'Languages/en-US/aetherhaven_profession_kits.lang'
lines=lang.read_text(encoding='utf-8').splitlines()
for index,(kind,name,skill,speed,workplace,bench,tier,level,knowledge,inputs,benefit) in enumerate(KITS):
 icon=make(kind);sheet.alpha_composite(icon,(16+160*index,8));draw.text((18+160*index,150),kind,fill='#e0cfab')
 ident=kind.lower();item=f'Aetherhaven_{kind}_Kit'
 write(SERVER/f'Aetherhaven/ProfessionKits/{ident}.json',dict(itemId=item,name=name,skill=skill,workSpeedBonus=speed,extraCarryingStacks=0))
 write(SERVER/f'Aetherhaven/WorkplaceRecipes/craft_{ident}_kit.json',dict(id=f'craft_{ident}_kit',name=name,workplace=workplace,sourceRecipe=f'workplace:{ident}_kit',bench=bench,benchTier=tier,skillLevel=level,requiresKnowledge=knowledge,inputs=[dict(itemId=i,count=c) for i,c in inputs],output=item,outputCount=1,workSeconds=30 if kind!='MasterSmith' else 45))
 lines=[line for line in lines if not line.startswith(ident+'.')]
 lines += [f'{ident}.name = {name}',f'{ident}.description = Equip through a townsfolk profile for {benefit} Replaces the equipped profession kit. Crafted by townsfolk.']
lang.write_text('\n'.join(lines)+'\n',encoding='utf-8')
(ROOT/'build/profession-kits').mkdir(parents=True,exist_ok=True);sheet.save(ROOT/'build/profession-kits/core-rewards.png')

REWARDS=[
 ('Chef','chef',25,'Cook',False,'A cook should spend time at the stove, not hunting for a spoon. Take this utensil roll for an Apprentice. Equip it through their profile to help them cook more quickly.'),
 ('Chef','chef',75,'Cook',True,'Good kitchens need more than one set of hands. I have shared my utensil roll plans with the town. A skilled crafter can make more at a tier two workbench.'),
 ('Merchant','merchant',25,'Jeweler',False,'A steady hand deserves proper tools. This case will help an Apprentice make jewelry more quickly. It cannot improve the quality of a gem, though. That still takes skill.'),
 ('Rancher','rancher',25,'Rancher',False,'Keep the brush and shears close. Give this bag to an Apprentice through their profile and they can tend the ranch more quickly. The animals still need the same care each day.'),
 ('Blacksmith','blacksmith',75,'MasterSmith',True,'You have earned my finest plans. A skilled Apprentice can forge this tool chest at a tier two anvil. Equip it through their profile for quicker smithing. Good tools never replace practice.')]
for role,kind,level,kit,learn,text in REWARDS:
 path=SERVER/f'Aetherhaven/Villagers/Aetherhaven_{role}.json';villager=read(path);rid='rep_merchant_jeweler_case' if kind=='merchant' and level==25 else f'rep_{kind}_{level}';node=f'rep_reward_{level}'
 # The old Merchant 25 ID is permanently retired by save migration.
 rewards=[x for x in villager.get('reputationMilestones',[]) if x['rewardId'] not in {rid,f'rep_{kind}_{level}'}]
 rewards.append(dict(rewardId=rid,minReputation=level,itemId='' if learn else f'Aetherhaven_{kit}_Kit',itemCount=0 if learn else 1,dialogueNodeId=node,learnRecipeItemId=f'Aetherhaven_{kit}_Kit' if learn else None))
 villager['reputationMilestones']=sorted(rewards,key=lambda r:r['minReputation']);write(path,villager)
 path=SERVER/f'Aetherhaven/Dialogue/aetherhaven_{kind}.json';dialogue=read(path)
 speaker=dialogue['nodes']['main_hub']['speaker']
 dialogue['nodes'][node]=dict(speaker=speaker,text=f'aetherhaven_core_rewards.{kind}.{level}',speechClip='Agree',actions=[dict(type='reputation_reward_grant',rewardId=rid)],choices=[dict(text='aetherhaven_ui_town.aetherhaven.reputation.reward.continue',next='main_hub')]);write(path,dialogue)
path=SERVER/'Languages/en-US/aetherhaven_core_rewards.lang';path.write_text(''.join(f'{kind}.{level}={text}\n' for role,kind,level,kit,learn,text in REWARDS),encoding='utf-8')
print('Four original profession kits; Chef, Merchant, Rancher and Blacksmith now each have three rewards.')
