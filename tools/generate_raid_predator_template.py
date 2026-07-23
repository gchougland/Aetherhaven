#!/usr/bin/env python3
"""Build Template_Aetherhaven_Raid_Predator from vanilla Template_Predator plus raid march state."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(
    r"C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleSourceCode/hytale-shared-source/HytaleAssets/Server/NPC/Roles/_Core/Templates/Template_Predator.json"
)
OUT = ROOT / "src/main/resources/Server/NPC/Roles/Aetherhaven/Templates/Template_Aetherhaven_Raid_Predator.json"

MARCH_BLOCK = {
    "Continue": True,
    "Instructions": [
        {
            "Sensor": {
                "Type": "State",
                "State": "AetherhavenRaidMarch",
                "IgnoreMissingSetState": True,
            },
            "Instructions": [
                {
                    "Reference": "Component_Instruction_Aetherhaven_Raid_March_Aggro_Combat",
                    "Modify": {
                        "_ExportStates": ["Combat"],
                        "DetectRange": 20,
                    },
                },
                {"Reference": "Component_Instruction_Aetherhaven_Raid_March_Travel"},
            ],
        }
    ],
}


def main() -> None:
    data = json.loads(SRC.read_text(encoding="utf-8-sig"))
    data["Instructions"] = [MARCH_BLOCK] + data.get("Instructions", [])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
