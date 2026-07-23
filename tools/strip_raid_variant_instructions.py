#!/usr/bin/env python3
"""Raid spawn variants cannot attach Instructions; keep Reference-only variants."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RAID_DIR = ROOT / "subplugin-assets/Quests/Server/NPC/Roles/Aetherhaven/Raid"


def main() -> None:
    count = 0
    for path in sorted(RAID_DIR.glob("Aetherhaven_Raid_*.json")):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        data.pop("Instructions", None)
        data.pop("Modify", None)
        path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        count += 1
    print(f"Updated {count} raid spawn roles")


if __name__ == "__main__":
    main()
