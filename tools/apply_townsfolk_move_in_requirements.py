#!/usr/bin/env python3
"""One-shot helper: assign moveInRequirements to tourist-eligible townsfolk JSON files."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOWNSFOLK_DIR = ROOT / "src/main/resources/Server/Aetherhaven/Townsfolk"

# character_id -> list of (itemId, count). Use food, ingredients, and useful crafted goods only.
ASSIGNMENTS: dict[str, list[tuple[str, int]]] = {
    "briar_mosscap": [("Food_Salad_Berry", 2), ("Ingredient_Hay", 2)],
    "tumble_reedwhistle": [("Food_Popcorn", 3)],
    "pippin_geargrin": [("Ingredient_Bar_Copper", 2), ("Ingredient_Spices", 1)],
    "nell_clinkjar": [("Ingredient_Bar_Bronze", 2), ("Ingredient_Stud_Iron", 2)],
    "momo_canopy": [("Food_Vegetable_Cooked", 3), ("Ingredient_Hay", 2)],
    "haku_mistclaw": [("Food_Salad_Mushroom", 2)],
    "zephyr_sandtail": [("Ingredient_Crystal_Yellow", 1), ("Food_Wildmeat_Cooked", 1)],
    "saffra_dunear": [("Food_Kebab_Fruit", 2), ("Food_Cheese", 1)],
    "vask_hollowmark": [("Food_Fish_Grilled", 2)],
    "rattle_morrow": [("Ingredient_Crystal_White", 1), ("Food_Wildmeat_Cooked", 2)],
    "grunk_stonebelly": [("Food_Pie_Meat", 2)],
    "celly_beans": [("Food_Bread", 3), ("Food_Cheese", 1)],
    "female_darkelf_01": [("Ingredient_Crystal_Purple", 1)],
    "female_darkelf_02": [("Food_Kebab_Meat", 2)],
    "female_elf_01": [("Food_Pie_Apple", 1), ("Food_Salad_Berry", 1)],
    "female_elf_02": [("Food_Pie_Apple", 1), ("Food_Cheese", 1)],
    "female_elf_03": [("Ingredient_Hay", 3)],
    "female_elf_04": [("Food_Kebab_Vegetable", 2)],
    "female_elf_05": [("Ingredient_Motes_Light", 2), ("Food_Cheese", 1)],
    "female_goblin_01": [("Food_Popcorn", 2)],
    "female_goblin_03": [("Ingredient_Stud_Iron", 3), ("Ingredient_Bar_Copper", 1)],
    "female_human_01": [("Food_Bread", 2), ("Ingredient_Flour", 1)],
    "female_human_02": [("Food_Cheese", 2), ("Food_Bread", 1)],
    "female_human_03": [("Food_Candy_Cane", 3)],
    "female_human_04": [("Food_Kebab_Fruit", 2)],
    "female_human_05": [("Food_Fish_Raw_Uncommon", 2)],
    "female_human_06": [("Food_Pie_Pumpkin", 1), ("Ingredient_Salt", 1)],
    "female_human_07": [("Food_Pie_Pumpkin", 1), ("Ingredient_Salt", 1)],
    "female_human_08": [("Ingredient_Powder_Boom", 1)],
    "female_human_09": [("Food_Bread", 2), ("Ingredient_Flour", 1)],
    "female_human_10": [("Ingredient_Bar_Iron", 1)],
    "jimmy_g": [("Food_Kebab_Meat", 1)],
    "joszael_greenleaf": [("Food_Wildmeat_Cooked", 2)],
    "kate_g": [("Ingredient_Bar_Copper", 1), ("Ingredient_Charcoal", 2)],
    "male_darkelf_01": [("Ingredient_Crystal_Cyan", 1)],
    "male_darkelf_02": [("Food_Kebab_Meat", 2), ("Ingredient_Spices", 1)],
    "male_elf_01": [("Food_Salad_Caesar", 1), ("Food_Cheese", 1)],
    "male_elf_02": [("Food_Salad_Caesar", 1)],
    "male_elf_03": [("Ingredient_Crystal_Green", 1), ("Food_Salad_Mushroom", 1)],
    "male_elf_04": [("Food_Fish_Grilled", 1), ("Food_Cheese", 1)],
    "male_elf_05": [("Food_Pie_Apple", 2)],
    "male_goblin_01": [("Food_Vegetable_Cooked", 3)],
    "male_goblin_02": [("Ingredient_Bar_Copper", 1)],
    "male_goblin_03": [("Food_Popcorn", 2), ("Food_Cheese", 1)],
    "male_human_01": [("Ingredient_Motes_Light", 1)],
    "male_human_02": [("Food_Fish_Raw_Rare", 1)],
    "male_human_03": [("Ingredient_Bar_Iron", 2)],
    "male_human_04": [("Food_Fish_Raw_Uncommon", 2)],
    "male_human_05": [("Food_Candy_Cane", 2)],
    "male_human_06": [("Food_Pie_Meat", 1), ("Ingredient_Flour", 2)],
    "male_human_07": [("Food_Kebab_Vegetable", 2), ("Ingredient_Hay", 2)],
    "male_human_08": [("Food_Salad_Berry", 2)],
    "male_human_09": [("Ingredient_Bar_Copper", 2), ("Ingredient_Charcoal", 1)],
    "male_human_10": [("Food_Salad_Mushroom", 2), ("Food_Vegetable_Cooked", 1)],
    "mekhi": [("Food_Pie_Apple", 1)],
    "mertie": [("Ingredient_Fire_Essence", 1)],
    "prowl": [("Food_Kebab_Mushroom", 2)],
    "tebryn_xarn": [("Ingredient_Crystal_Red", 1)],
}


def main() -> None:
    missing: list[str] = []
    for path in sorted(TOWNSFOLK_DIR.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        cid = data.get("id", "")
        kinds = data.get("allowedAssignmentKinds") or []
        if "tourist" not in kinds:
            continue
        if cid not in ASSIGNMENTS:
            missing.append(cid)
            continue
        data["moveInRequirements"] = [
            {"itemId": item, "count": count} for item, count in ASSIGNMENTS[cid]
        ]
        path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    if missing:
        raise SystemExit(f"Missing assignments for: {missing}")
    print(f"Updated {len(ASSIGNMENTS)} townsfolk files.")


if __name__ == "__main__":
    main()
