#!/usr/bin/env python3
"""Validate tourist move-in requirements on townsfolk character JSON."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOWNSFOLK = ROOT / "src/main/resources/Server/Aetherhaven/Townsfolk"
SHOP = ROOT / "src/main/resources/defaults/shop_prices.json"

# Move-in gifts should be food, ingredients, or useful crafted goods — not dungeon furniture loot.
DISALLOWED_ITEM_PREFIXES = (
    "Furniture_Ancient_",
    "Furniture_Castle_",
    "Furniture_Dungeon_",
    "Furniture_Ruins_",
)


def is_disallowed_move_in_item(item_id: str) -> bool:
    item = item_id.strip()
    return any(item.startswith(prefix) for prefix in DISALLOWED_ITEM_PREFIXES)


def main() -> int:
    shop_root = json.loads(SHOP.read_text(encoding="utf-8-sig"))
    prices = shop_root.get("prices", shop_root)
    shop_ids = set(prices.keys()) if isinstance(prices, dict) else set()
    errors: list[str] = []
    warnings: list[str] = []
    seen_primary: dict[str, str] = {}

    for path in sorted(TOWNSFOLK.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        cid = data.get("id", path.stem)
        kinds = data.get("allowedAssignmentKinds") or []
        if "tourist" not in kinds:
            continue
        reqs = data.get("moveInRequirements") or []
        if not reqs:
            errors.append(f"{cid}: missing moveInRequirements")
            continue
        for entry in reqs:
            item = entry.get("itemId", "")
            if not item:
                errors.append(f"{cid}: moveInRequirements entry without itemId")
                continue
            if is_disallowed_move_in_item(item):
                errors.append(f"{cid}: disallowed move-in item {item} (no ancient or junk furniture)")
                continue
            if item not in shop_ids:
                errors.append(f"{cid}: unknown itemId {item} (not in shop_prices.json)")
            count = entry.get("count", 1)
            if count < 1:
                errors.append(f"{cid}: invalid count for {item}")
        primary = reqs[0].get("itemId", "")
        if primary:
            other = seen_primary.get(primary)
            if other and other != cid:
                warnings.append(f"{cid}: shares primary item {primary} with {other}")
            seen_primary.setdefault(primary, cid)

    if errors:
        for e in errors:
            print(e, file=sys.stderr)
        return 1
    for w in warnings:
        print("warn:", w, file=sys.stderr)
    print("OK: all tourist townsfolk have valid moveInRequirements")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
