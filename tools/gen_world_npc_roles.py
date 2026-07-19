#!/usr/bin/env python3
"""Generate stand-still Aetherhaven_World_* role JSONs from town villager roles."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROLES_DIR = Path(__file__).resolve().parents[1] / "src/main/resources/Server/NPC/Roles/Aetherhaven"
VILLAGERS = [
    "Aetherhaven_Blacksmith",
    "Aetherhaven_Builder",
    "Aetherhaven_Bard",
    "Aetherhaven_Chef",
    "Aetherhaven_Crystal_Keeper",
    "Aetherhaven_Elder_Lyren",
    "Aetherhaven_Farmer",
    "Aetherhaven_Florist",
    "Aetherhaven_Guild_Master",
    "Aetherhaven_Innkeeper",
    "Aetherhaven_Logger",
    "Aetherhaven_Merchant",
    "Aetherhaven_Miner",
    "Aetherhaven_Priestess",
    "Aetherhaven_Pyrotechnic",
    "Aetherhaven_Rancher",
]


def world_role(
    *,
    memories: str,
    name_key: str,
    appearance: str,
    kind: str,
) -> dict:
    return {
        "Type": "Generic",
        "Parameters": {
            "Invulnerable": {"Value": True, "Description": "World hub NPCs are invulnerable."},
            "MemoriesNameOverride": {"Value": memories, "Description": "Memory name override"},
            "NameTranslationKey": {
                "Value": name_key,
                "Description": "Translation key for NPC name display",
            },
        },
        "StartState": "Idle",
        "AttitudeGroup": "Aetherhaven_Townsfolk",
        "DefaultNPCAttitude": "Neutral",
        "DefaultPlayerAttitude": "Neutral",
        "Appearance": appearance,
        "MaxHealth": 200,
        "IsMemory": True,
        "MemoriesCategory": "Aetherhaven",
        "MemoriesNameOverride": {"Compute": "MemoriesNameOverride"},
        "DisableDamageGroups": ["Self", "Player", "Aetherhaven_Townsfolk", "Friendly"],
        "CombatConfig": {"EntityEffect": "Immunity_Environmental"},
        "BusyStates": ["$Interaction"],
        "Invulnerable": {"Compute": "Invulnerable"},
        "KnockbackScale": 0.0,
        "ApplySeparation": False,
        "MotionControllerList": [
            {
                "Type": "Walk",
                "MaxWalkSpeed": 4,
                "Gravity": 10,
                "RunThreshold": 0.3,
                "MaxFallSpeed": 15,
                "MaxRotationSpeed": 360,
                "Acceleration": 10,
            }
        ],
        "Instructions": [
            {
                "Instructions": [
                    {
                        "Sensor": {"Type": "State", "State": "Idle"},
                        "Instructions": [
                            {
                                "Continue": True,
                                "Sensor": {"Type": "Target", "Range": 16},
                                "HeadMotion": {"Type": "Watch"},
                            },
                            {"Sensor": {"Type": "Any"}, "BodyMotion": {"Type": "Nothing"}},
                        ],
                    },
                    {
                        "Sensor": {"Type": "State", "State": "$Interaction"},
                        "Instructions": [
                            {
                                "Continue": True,
                                "Sensor": {"Type": "Target", "Range": 16},
                                "HeadMotion": {"Type": "Watch"},
                            },
                            {"Sensor": {"Type": "Any"}, "BodyMotion": {"Type": "Nothing"}},
                        ],
                    },
                ]
            }
        ],
        "InteractionInstruction": {
            "Instructions": [
                {
                    "Sensor": {
                        "Type": "Not",
                        "Sensor": {"Type": "CanInteract", "ViewSector": 360},
                    },
                    "Actions": [{"Type": "SetInteractable", "Interactable": False}],
                },
                {
                    "Continue": True,
                    "Sensor": {"Type": "Any"},
                    "Actions": [
                        {
                            "Type": "SetInteractable",
                            "Interactable": True,
                            "Hint": "aetherhaven_misc.aetherhaven.interaction.dialogue",
                        }
                    ],
                },
                {
                    "Sensor": {"Type": "HasInteracted"},
                    "Instructions": [
                        {
                            "Sensor": {
                                "Type": "Not",
                                "Sensor": {"Type": "State", "State": "$Interaction"},
                            },
                            "Actions": [
                                {"Type": "LockOnInteractionTarget"},
                                {"Type": "OpenAetherhavenDialogue", "VillagerKind": kind},
                            ],
                        }
                    ],
                },
            ]
        },
        "NameTranslationKey": {"Compute": "NameTranslationKey"},
    }


def main() -> None:
    for vid in VILLAGERS:
        path = ROLES_DIR / f"{vid}.json"
        raw = path.read_text(encoding="utf-8")
        data = json.loads(raw)
        appearance = data["Appearance"]
        params = data.get("Parameters", {})
        name_key = params.get("NameTranslationKey", {}).get(
            "Value", f"aetherhaven_ui_journal_items_tail.npcRoles.{vid}.name"
        )
        memories = params.get("MemoriesNameOverride", {}).get("Value", vid)
        match = re.search(r'"VillagerKind"\s*:\s*"([^"]+)"', raw)
        kind = match.group(1) if match else "townsfolk"
        world_id = "Aetherhaven_World_" + vid[len("Aetherhaven_") :]
        out_path = ROLES_DIR / f"{world_id}.json"
        payload = world_role(
            memories=memories, name_key=name_key, appearance=appearance, kind=kind
        )
        out_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {out_path.name} appearance={appearance} kind={kind}")


if __name__ == "__main__":
    main()
