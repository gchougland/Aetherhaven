"""Create Apprentice seed/sapling recipes from an installed native Assets.zip.

All outputs use Life Essence. Authored native Farming Bench levels determine
Farming skill requirements, rather than the Crafting skill of the workplace.
Recipes without native crafting data receive explicit late-game defaults.
"""
import argparse
import json
from pathlib import Path
import re
import zipfile


def generate(assets: Path, destination: Path) -> int:
    with zipfile.ZipFile(assets) as archive:
        plants = {}
        for name in archive.namelist():
            item = Path(name).stem
            if name.startswith("Server/Item/Items/") and name.endswith(".json") and item.startswith(("Plant_Seeds_", "Plant_Sapling_")):
                plants[item] = json.loads(archive.read(name))
    destination.mkdir(parents=True, exist_ok=True)
    for item, asset in sorted(plants.items()):
        native = asset.get("Recipe", {})
        # Old tree seed items use the matching sapling's native progression.
        if not native and item.startswith("Plant_Seeds_"):
            native = plants.get(item.replace("Plant_Seeds_", "Plant_Sapling_"), {}).get("Recipe", {})
        benches = native.get("BenchRequirement", [])
        skill = max(1, min(10, min((b.get("RequiredTierLevel", 1) for b in benches), default=10)))
        quantity = sum(row.get("Quantity", 1) for row in native.get("Input", []) if row.get("ItemId") == "Ingredient_Life_Essence")
        output_quantity = native.get("OutputQuantity", 1)
        if quantity:
            quantity = max(1, (quantity + output_quantity - 1) // output_quantity)
        else:
            quantity = 5 * skill
        match = re.fullmatch(r"Plant_Seeds_(Health|Mana|Stamina)([123])", item)
        if match:
            tier = int(match.group(2))
            skill = (1, 4, 7)[tier - 1]
            quantity = (12, 24, 48)[tier - 1]
        recipe_id = "grow_" + item.lower()
        recipe = {
            "id": recipe_id,
            "name": item.removeprefix("Plant_").replace("_", " "),
            "workplace": "crafting",
            "sourceRecipe": "workplace:" + recipe_id,
            "bench": "Workbench",
            "benchTier": 1,
            "skillLevel": skill,
            "inputs": [{"itemId": "Ingredient_Life_Essence", "count": quantity}],
            "output": item,
            "outputCount": 1,
            "workSeconds": ((8 + skill * 2 + 4) // 5) * 5,
        }
        target = destination / (recipe_id + ".json")
        target.write_text(json.dumps(recipe, indent=2) + "\n", encoding="utf-8")
    return len(plants)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("assets", type=Path)
    parser.add_argument("--destination", type=Path, default=Path(__file__).resolve().parents[1] / "src/main/resources/Server/Aetherhaven/WorkplaceRecipes")
    args = parser.parse_args()
    print(f"Generated {generate(args.assets, args.destination)} Life Essence recipes.")
