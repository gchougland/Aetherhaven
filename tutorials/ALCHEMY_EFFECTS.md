# Alchemy effects

Every potion effect in Aetherhaven, the plants that give it, and the rules of the cauldron.

This page is written from the game data by the test `AlchemyEffectsDocTest`. Do not edit it by hand. Change the data,
then run that test once with the environment variable `AETHERHAVEN_WRITE_DOCS=1` to rewrite the page. The test fails
whenever this page and the data disagree.

## What each effect does

There are 20 effects in 10 kinds. A higher strength of the same kind is the same effect, only better.

| Effect | Strength | What it does | Lasts | Ingredients |
| --- | --- | --- | --- | --- |
| Healing | 1 | Restores 15% of your maximum health at once | Instant | Blood Rose |
| Healing | 2 | Restores 30% of your maximum health at once | Instant | Bloodcap Mushroom |
| Healing | 3 | Restores 50% of your maximum health at once | Instant | Blood Leaf |
| Mana | 1 | Restores 15% of your maximum mana at once | Instant | Azure Fern |
| Mana | 2 | Restores 30% of your maximum mana at once | Instant | Azurecap Mushroom |
| Mana | 3 | Restores 50% of your maximum mana at once | Instant | Azure Kelp |
| Stamina | 1 | Restores 15% of your maximum stamina at once | Instant | Storm Thistle |
| Stamina | 2 | Restores 30% of your maximum stamina at once | Instant | Stormcap Mushroom |
| Stamina | 3 | Restores 50% of your maximum stamina at once | Instant | Storm Rush |
| Blast Ward | 1 | You take no damage from explosions | 60 seconds | Small Boomshroom, Large Boomshroom |
| Poison Ward | 1 | You take no poison damage, and poison already on you is cleared | 60 seconds | Puffy Green Common Mushroom |
| Health Boost | 1 | Adds 15 to your maximum health | 120 seconds (2 minutes) | Red Cap Mushroom |
| Health Boost | 2 | Adds 20 to your maximum health | 240 seconds (4 minutes) | Red Glowing Mushroom |
| Mana Boost | 1 | Adds 15 to your maximum mana | 120 seconds (2 minutes) | Blue Common Mushroom |
| Mana Boost | 2 | Adds 20 to your maximum mana | 240 seconds (4 minutes) | Blue Glowing Mushroom |
| Regeneration | 1 | Restores 15% of your maximum health in all, 1% every 2 seconds | 30 seconds | Blue Aloe, Green Flatcap Mushroom |
| Regeneration | 2 | Restores 30% of your maximum health in all, 1.5% every 2 seconds | 40 seconds | Spotted Green Cap Mushroom |
| Regeneration | 3 | Restores 50% of your maximum health in all, 2% every 2 seconds | 50 seconds | Green Glowing Mushroom |
| Fire Ward | 1 | You take no damage from fire, burning or lava | 60 seconds | Fire Flower |
| Bug Repellent | 1 | Scaraks, spiders and scorpions ignore you. It ends early if you attack one of them. Broodmothers are not fooled | 120 seconds (2 minutes) | Lavender, Chrysanthemum |

## What each ingredient gives

23 plants can be brewed. The cauldron shows a plant's property as unknown until your town has bottled it once.

| Ingredient | Effect | Strength | Shown at the cauldron as | Item id | Effect id |
| --- | --- | --- | --- | --- | --- |
| Blood Rose | Healing | 1 | Restores 15% health | `Plant_Crop_Health1` | `Aetherhaven_Alchemy_Health1` |
| Bloodcap Mushroom | Healing | 2 | Restores 30% health | `Plant_Crop_Health2` | `Aetherhaven_Alchemy_Health2` |
| Blood Leaf | Healing | 3 | Restores 50% health | `Plant_Crop_Health3` | `Aetherhaven_Alchemy_Health3` |
| Azure Fern | Mana | 1 | Restores 15% mana | `Plant_Crop_Mana1` | `Aetherhaven_Alchemy_Mana1` |
| Azurecap Mushroom | Mana | 2 | Restores 30% mana | `Plant_Crop_Mana2` | `Aetherhaven_Alchemy_Mana2` |
| Azure Kelp | Mana | 3 | Restores 50% mana | `Plant_Crop_Mana3` | `Aetherhaven_Alchemy_Mana3` |
| Storm Thistle | Stamina | 1 | Restores 15% stamina | `Plant_Crop_Stamina1` | `Aetherhaven_Alchemy_Stamina1` |
| Stormcap Mushroom | Stamina | 2 | Restores 30% stamina | `Plant_Crop_Stamina2` | `Aetherhaven_Alchemy_Stamina2` |
| Storm Rush | Stamina | 3 | Restores 50% stamina | `Plant_Crop_Stamina3` | `Aetherhaven_Alchemy_Stamina3` |
| Small Boomshroom | Blast Ward | 1 | Explosion immunity for 60 seconds | `Plant_Crop_Mushroom_Boomshroom_Small` | `Aetherhaven_Alchemy_BlastWard1` |
| Large Boomshroom | Blast Ward | 1 | Explosion immunity for 60 seconds | `Plant_Crop_Mushroom_Boomshroom_Large` | `Aetherhaven_Alchemy_BlastWard1` |
| Puffy Green Common Mushroom | Poison Ward | 1 | Poison immunity for 60 seconds | `Plant_Crop_Mushroom_Common_Lime` | `Aetherhaven_Alchemy_PoisonWard1` |
| Red Cap Mushroom | Health Boost | 1 | Health Boost 1: +15 maximum health for 120 seconds | `Plant_Crop_Mushroom_Cap_Red` | `Aetherhaven_Alchemy_HealthBoost1` |
| Red Glowing Mushroom | Health Boost | 2 | Health Boost 2: +20 maximum health for 240 seconds | `Plant_Crop_Mushroom_Glowing_Red` | `Aetherhaven_Alchemy_HealthBoost2` |
| Blue Common Mushroom | Mana Boost | 1 | Mana Boost 1: +15 maximum mana for 120 seconds | `Plant_Crop_Mushroom_Common_Blue` | `Aetherhaven_Alchemy_ManaBoost1` |
| Blue Glowing Mushroom | Mana Boost | 2 | Mana Boost 2: +20 maximum mana for 240 seconds | `Plant_Crop_Mushroom_Glowing_Blue` | `Aetherhaven_Alchemy_ManaBoost2` |
| Blue Aloe | Regeneration | 1 | Regeneration 1: restores 15% health over 30 seconds | `Plant_Flower_Common_Blue2` | `Aetherhaven_Alchemy_Regen1` |
| Green Flatcap Mushroom | Regeneration | 1 | Regeneration 1: restores 15% health over 30 seconds | `Plant_Crop_Mushroom_Flatcap_Green` | `Aetherhaven_Alchemy_Regen1` |
| Spotted Green Cap Mushroom | Regeneration | 2 | Regeneration 2: restores 30% health over 40 seconds | `Plant_Crop_Mushroom_Cap_Green` | `Aetherhaven_Alchemy_Regen2` |
| Green Glowing Mushroom | Regeneration | 3 | Regeneration 3: restores 50% health over 50 seconds | `Plant_Crop_Mushroom_Glowing_Green` | `Aetherhaven_Alchemy_Regen3` |
| Fire Flower | Fire Ward | 1 | Fire and lava immunity for 60 seconds | `Plant_Flower_Flax_Orange` | `Aetherhaven_Alchemy_FireWard1` |
| Lavender | Bug Repellent | 1 | Bug repellent: scaraks, spiders and scorpions ignore you for 120 seconds or until you attack one | `Plant_Flower_Tall_Purple` | `Aetherhaven_Alchemy_Repellent1` |
| Chrysanthemum | Bug Repellent | 1 | Bug repellent: scaraks, spiders and scorpions ignore you for 120 seconds or until you attack one | `Plant_Flower_Common_Orange` | `Aetherhaven_Alchemy_Repellent1` |

## Mixing

- A mixture is 1 to 3 different plants. The cauldron starts with 1 ingredient slot. The second slot needs Alchemist Mastery 3 and 150 gold,
  the third needs Alchemist Mastery 6 and 350 gold. Buy them under Building upgrades.
- Each plant adds its own effect, and one drink gives you all of them at once.
- Two plants of the same kind of effect do not add up. Only the stronger one counts and the weaker one is spent for nothing.
- The same rule holds between drinks. While a stronger effect of a kind is running, a weaker drink of that kind does nothing for it.
  A stronger drink replaces a weaker running effect, and the same strength starts its time again.
- To drink, hold the flask's secondary use for 1.5 seconds. Each drink uses one charge.

## Brewing: hold the brew steady

Fill flask always works and gives a Good potion. For a better one, press Start brewing first.

- A brew lasts 10 beats. Each press of a button is one beat, so there is no clock and no hurry.
- Heat runs from 0 to 100 and starts at 30. Every beat it rises by 6. Stoke makes that 18. Stir cools it by 12 instead.
- A glowing band drifts up or down the gauge every beat. The page says which way, and a pale outline shows about where it will be.
- A beat counts when the heat ends inside the band. Touching its edge counts too.
- Nothing is spent until a flask is filled. Brew again as often as you like, or stop and bottle a Good potion at any moment.
- Changing the ingredients starts a new brew. Filling a flask uses the brew up.

| Brew | Beats inside the band | Lasting effects | Health, Mana and Stamina restores |
| --- | --- | --- | --- |
| Good | 5 or fewer, or not brewed | as listed | as listed |
| Great | 6 to 8 | 25% longer | 10% more |
| Perfect | 9 to 10 | 50% longer | 20% more |

The quality belongs to the potion. It stays in the flask until the last charge is drunk or poured away, and the flask's tooltip shows it.

How wide the band is, out of 100. Each extra ingredient makes it narrower and makes it move further each beat. Each level of Alchemist Mastery makes it wider.

| Ingredients | Band moves each beat | Mastery 1 | Mastery 3 | Mastery 6 | Mastery 10 |
| --- | --- | --- | --- | --- | --- |
| 1 | about 8 | 12 | 14 | 17 | 21 |
| 2 | about 10 | 10 | 12 | 15 | 19 |
| 3 | about 12 | 8 | 10 | 13 | 17 |

What each effect becomes with a better brew.

| Effect | Strength | Good | Great | Perfect |
| --- | --- | --- | --- | --- |
| Healing | 1 | 15% | 16.5% | 18% |
| Healing | 2 | 30% | 33% | 36% |
| Healing | 3 | 50% | 55% | 60% |
| Mana | 1 | 15% | 16.5% | 18% |
| Mana | 2 | 30% | 33% | 36% |
| Mana | 3 | 50% | 55% | 60% |
| Stamina | 1 | 15% | 16.5% | 18% |
| Stamina | 2 | 30% | 33% | 36% |
| Stamina | 3 | 50% | 55% | 60% |
| Blast Ward | 1 | 60 seconds | 75 seconds | 90 seconds |
| Poison Ward | 1 | 60 seconds | 75 seconds | 90 seconds |
| Health Boost | 1 | 120 seconds (2 minutes) | 150 seconds | 180 seconds (3 minutes) |
| Health Boost | 2 | 240 seconds (4 minutes) | 300 seconds (5 minutes) | 360 seconds (6 minutes) |
| Mana Boost | 1 | 120 seconds (2 minutes) | 150 seconds | 180 seconds (3 minutes) |
| Mana Boost | 2 | 240 seconds (4 minutes) | 300 seconds (5 minutes) | 360 seconds (6 minutes) |
| Regeneration | 1 | 15% over 30 seconds | 18% over 37 seconds | 22% over 45 seconds |
| Regeneration | 2 | 30% over 40 seconds | 37.5% over 50 seconds | 45% over 60 seconds |
| Regeneration | 3 | 50% over 50 seconds | 62% over 62 seconds | 74% over 75 seconds |
| Fire Ward | 1 | 60 seconds | 75 seconds | 90 seconds |
| Bug Repellent | 1 | 120 seconds (2 minutes) | 150 seconds | 180 seconds (3 minutes) |

## Filling a flask

- Filling takes one of each plant in the mixture for every charge the flask holds. A flask of 5 charges with 3 plants takes 5 of each.
- Plants are taken from your inventory first. If you may open town storage, the rest comes from the warehouse and then from what
  production buildings and grower sites hold. Supplies set aside for a courier are never taken.
- Nothing is spent unless the flask is filled. If something is short, the cauldron names the plant and how many more you need.
- A flask must be empty to be filled. Empty flask pours away what is left.

## The flask

Craft a flask at the Laboratory cauldron with Craft flask. It costs 5 Iron Ingot, 5 Light Leather, 5 Tree Sap. There is no recipe to learn. A new flask is empty and holds 1 charge.

Upgrade flask at the cauldron adds one charge at a time. It keeps the potion inside and does not refill it.

| Capacity after the upgrade | Alchemist Mastery needed | Cost |
| --- | --- | --- |
| 2 charges | 2 | 5 Light Leather |
| 3 charges | 4 | 10 Medium Leather |
| 4 charges | 6 | 15 Heavy Leather |
| 5 charges | 8 | 20 Storm Leather |

## What makes a potion stronger

- The plant. Strength 2 and 3 plants give more, or last longer, as the first table shows.
- The brew. A Great or Perfect brew lasts longer or restores more, as the brewing tables show.
- Flask size does not change the effect. A bigger flask holds more drinks of the same potion.
- Alchemist Mastery does not change the effect itself. It opens ingredient slots and bigger flasks, and it widens the band so better brews come easier.
- Orin earns Mastery only when you fill a flask, and the same whatever the brew: 7 for each plant in the mixture plus 7 for each charge of the flask, so 14 for a new flask with one plant and 56 for the largest flask with three.
