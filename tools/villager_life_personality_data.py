"""Authoring defaults for item thoughts and expressive personality traits."""
COIN = 'Aetherhaven_Gold_Coin'
BOOK = 'Deco_Scrap_Book_Pile_Small'
PIE = 'Food_Pie_Meat'
FLOWER = 'Plant_Flower_Blood_Rose'
FISH = 'Food_Fish_Grilled'
MAP = 'Tool_Map'
GEM = 'Rock_Gem_Diamond'
HAMMER = 'Tool_Hammer_Iron'
CARROT = 'Plant_Crop_Carrot'
APPLE = 'Plant_Fruit_Apple'
SWORD = 'Weapon_Sword_Iron'
FEED = 'Tool_Feedbag'
BED = 'Furniture_Village_Bed'

# Explicit authored interests, blended when a character has several traits.
THOUGHTS = {
    'adventurer': [MAP, SWORD, GEM], 'bitter': [COIN, PIE, BOOK],
    'blunt': [HAMMER, SWORD, PIE], 'bookworm': [BOOK, BOOK, MAP],
    'cheerful': [FLOWER, APPLE, PIE], 'crafty': [HAMMER, 'Ingredient_Bar_Iron', 'Tool_Repair_Kit_Iron'],
    'curious': [BOOK, MAP, 'Rock_Gem_Zephyr'], 'cynical': [BOOK, COIN, MAP],
    'dramatic': [GEM, FLOWER, 'Rock_Gem_Ruby'], 'feran_sunseeker': [MAP, FISH, 'Rock_Gem_Topaz'],
    'fisher': [FISH, 'Tool_Fishing_Trap', 'Fish_Salmon_Item'], 'foodie': [PIE, 'Food_Bread', 'Food_Pie_Apple', FISH],
    'gardener': [FLOWER, CARROT, 'Plant_Seeds_Carrot', 'Tool_Watering_Can'],
    'gloomy': [BOOK, BED, 'Rock_Gem_Voidstone'], 'gossipy': [COIN, FLOWER, GEM],
    'grumpy': [BED, HAMMER, PIE], 'homebody': [BED, BOOK, 'Food_Bread'],
    'impatient': [MAP, HAMMER, COIN], 'klops_trader': [COIN, COIN, GEM, 'Ingredient_Bar_Gold'],
    'kweebec_forestfolk': [APPLE, FLOWER, CARROT], 'lazy': [BED, PIE, FISH],
    'mannequin_display': [GEM, FLOWER, BOOK], 'mischievous': [COIN, 'Rock_Gem_Voidstone', APPLE],
    'musical': ['Music', FLOWER, BOOK], 'outlander_wildbound': [MAP, 'Tool_Hatchet_Iron', FEED],
    'rude': [SWORD, COIN, PIE], 'shy': [BOOK, FLOWER, BED],
    'silly': [PIE, APPLE, 'Fish_Pufferfish_Item'], 'skeleton_bonedry': [BOOK, MAP, 'Ingredient_Bar_Iron'],
    'slothian_canopy': [BED, APPLE, FLOWER], 'snooty': [GEM, 'Ingredient_Bar_Gold', FLOWER],
    'sporty': [SWORD, MAP, APPLE], 'stingy': [COIN, COIN, 'Ingredient_Bar_Gold', GEM],
    'stoic': [HAMMER, BOOK, MAP], 'trork_stoneheart': [HAMMER, PIE, SWORD],
}

SOCIAL = {
    'reserved': {'Agree': 6, 'Question': 3, 'Explain': 1},
    'exuberant': {'Story': 5, 'Laugh': 4, 'Surprise': 2, 'Agree': 1},
    'skeptical': {'Disagree': 5, 'Question': 3, 'Explain': 2},
    'curious': {'Question': 6, 'Surprise': 2, 'Explain': 3},
    'warm': {'Agree': 4, 'Explain': 3, 'Laugh': 2, 'Story': 2},
}


def style(trait):
    if trait in ('shy', 'stoic', 'bookworm', 'homebody', 'skeleton_bonedry', 'mannequin_display'): return 'reserved'
    if trait in ('silly', 'dramatic', 'gossipy', 'cheerful', 'musical', 'sporty', 'mischievous'): return 'exuberant'
    if trait in ('grumpy', 'bitter', 'cynical', 'rude', 'blunt', 'stingy', 'impatient', 'snooty'): return 'skeptical'
    if trait in ('curious', 'adventurer', 'crafty', 'klops_trader'): return 'curious'
    return 'warm'


def idle(trait):
    if trait in ('bookworm', 'curious', 'shy', 'mannequin_display'): return {'Fidget': 4, 'LookAround': 3}
    if trait in ('lazy', 'slothian_canopy', 'homebody', 'gloomy'): return {'Stretch': 4, 'Sleepy': 2, 'LookAround': 1}
    if trait in ('grumpy', 'bitter', 'impatient', 'rude', 'cynical'): return {'Bored': 3, 'Fidget': 2, 'LookAround': 2}
    if trait in ('cheerful', 'silly', 'musical', 'dramatic', 'sporty'): return {'Stretch': 3, 'Greet': 2, 'Laugh': 2}
    return {'LookAround': 4, 'Fidget': 2, 'Stretch': 1}
