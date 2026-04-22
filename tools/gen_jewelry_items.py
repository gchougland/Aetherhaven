#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/Server/Item/Items/Aetherhaven"

gems = ["Zephyr", "Topaz", "Emerald", "Diamond", "Sapphire", "Ruby", "Voidstone"]
metals = ["Silver", "Gold"]
types = [
    ("Ring", "Items/Aetherhaven/Jewelry/Ring.blockymodel"),
    ("Necklace", "Items/Aetherhaven/Jewelry/Necklace.blockymodel"),
]

for pfx, model in types:
    for m in metals:
        for g in gems:
            item_id = f"Aetherhaven_{pfx}_{m}_{g}"
            tex = f"Items/Aetherhaven/Jewelry/{m}_Ring_{g}.png"
            data = {
                "TranslationProperties": {
                    "Name": f"server.items.{item_id}.name",
                    "Description": "server.items.Aetherhaven_Jewelry.genericDescription",
                },
                "Categories": ["Items.Utility"],
                "Icon": tex,
                "Model": model,
                "Texture": tex,
                "MaxStack": 1,
                "PlayerAnimationsId": "Item",
                "Quality": "Rare",
                "Set": "Aetherhaven",
                "IconProperties": {"Scale": 0.55, "Rotation": [22.5, 45, 22.5], "Translation": [0, -8]},
                "Tags": {"Type": ["Jewelry"], "Family": ["Aetherhaven"]},
                "ItemSoundSetId": "ISS_Blocks_Wood",
            }
            path = OUT / f"{item_id}.json"
            path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

print("wrote", len(gems) * len(metals) * len(types), "files")
