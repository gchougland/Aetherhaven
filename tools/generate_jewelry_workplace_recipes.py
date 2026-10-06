"""Author the standard jewelry plans. Runtime definitions remain editable and pack overridable."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "subplugin-assets/Jewelry/Server/Aetherhaven/WorkplaceRecipes"
DEST.mkdir(parents=True, exist_ok=True)
TIERS = [("Common", 1, 1, 0, 20), ("Uncommon", 3, 5, 0, 30),
         ("Rare", 5, 12, 0, 45), ("Mythic", 7, 5, 1, 60),
         ("Legendary", 10, 5, 2, 90)]

for gem in ["Zephyr", "Topaz", "Emerald", "Diamond", "Sapphire", "Ruby", "Voidstone"]:
    for metal in ["Silver", "Gold"]:
        for shape, bars in [("Ring", 5), ("Necklace", 10)]:
            output = f"Aetherhaven_{shape}_{metal}_{gem}"
            assert (ROOT / f"subplugin-assets/Jewelry/Server/Item/Items/Aetherhaven/{output}.json").is_file()
            for rarity, skill, essence, concentrated, seconds in TIERS:
                identity = f"jewel_{shape}_{metal}_{gem}_{rarity}".lower()
                inputs = [{"itemId": f"Ingredient_Bar_{metal}", "count": bars},
                          {"itemId": f"Rock_Gem_{gem}", "count": 1},
                          {"itemId": "Ingredient_Life_Essence", "count": essence}]
                if concentrated:
                    inputs.append({"itemId": "Ingredient_Life_Essence_Concentrated", "count": concentrated})
                data = {"id": identity, "name": f"{rarity} {metal.lower()} {gem.lower()} {shape.lower()}",
                        "workplace": "jewelry", "sourceRecipe": "workplace:" + identity,
                        "bench": "Aetherhaven_Jewelry_Crafting_Bench", "benchTier": 1,
                        "skillLevel": skill, "inputs": inputs, "output": output, "outputCount": 1,
                        "workSeconds": seconds, "jewelryRarity": rarity.lower()}
                (DEST / f"{identity}.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
