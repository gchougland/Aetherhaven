#!/usr/bin/env python3
"""
Generate defaults/shop_prices.json (or a sidecar file) from tier rules and pattern overrides.

Configuration:
  scripts/shop_price_config.json   — tier prices, furniture bases, consumables, gems
  scripts/shop_price_overrides.txt — exact/pattern overrides (like prefab_material_conversions.txt)

Item ids are collected from defaults/shop_loot/*.json, merged with shop_prices.json keys,
and all building blocks plus auto-priced gear (Weapon_/Armor_/Tool_ with a configured
gearTypes entry and material tier) discovered under hytaleItemsRoot.

Run from repo root:
  python scripts/generate_shop_prices.py
  python scripts/generate_shop_prices.py --dry-run
  python scripts/generate_shop_prices.py --report
  python scripts/generate_shop_prices.py --out src/main/resources/defaults/shop_prices.generated.json
  python scripts/generate_shop_prices.py --omit-defaults
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = Path(__file__).resolve().parent / "shop_price_config.json"
OVERRIDES_PATH = Path(__file__).resolve().parent / "shop_price_overrides.txt"
DEFAULT_LOOT_DIR = REPO_ROOT / "src" / "main" / "resources" / "defaults" / "shop_loot"
DEFAULT_OUT = REPO_ROOT / "src" / "main" / "resources" / "defaults" / "shop_prices.generated.json"
DEFAULT_HYTALE_ITEMS_ROOT = (
    REPO_ROOT.parent / "HytaleSourceCode" / "Assets" / "Server" / "Item" / "Items"
)

JEWELRY_RE = re.compile(
    r"^Aetherhaven_(Ring|Necklace)_(Gold|Silver)_(Topaz|Zephyr|Emerald|Sapphire|Ruby|Voidstone|Diamond)$"
)
CRYSTAL_SHARD_RE = re.compile(r"^Ingredient_Crystal_")
CRYSTAL_ROCK_RE = re.compile(r"^Rock_Crystal_[A-Za-z]+_(Small|Medium|Large|Block)$")


@dataclass(frozen=True)
class PriceEntry:
    gold: int
    batch_size: int = 1

    def to_json(self) -> int | dict[str, int]:
        if self.batch_size > 1:
            return {"gold": self.gold, "batchSize": self.batch_size}
        return self.gold


@dataclass(frozen=True)
class OverrideRule:
    skip: bool
    entry: PriceEntry | None


@dataclass
class OverrideTable:
    exact: dict[str, OverrideRule]
    patterns: list[tuple[str, OverrideRule]]


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def parse_override_value(raw: str) -> OverrideRule:
    text = raw.strip()
    if not text or text.lower() == "skip":
        return OverrideRule(skip=True, entry=None)
    batch_size = 1
    if " batch " in text.lower():
        left, _, right = text.lower().partition(" batch ")
        gold = int(left.strip())
        batch_size = max(1, int(right.strip()))
        return OverrideRule(skip=False, entry=PriceEntry(gold=gold, batch_size=batch_size))
    return OverrideRule(skip=False, entry=PriceEntry(gold=int(text), batch_size=1))


def load_overrides(path: Path) -> OverrideTable:
    exact: dict[str, OverrideRule] = {}
    patterns: list[tuple[str, OverrideRule]] = []
    if not path.is_file():
        return OverrideTable(exact=exact, patterns=patterns)
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            print(f"Warning: overrides line {line_no}: missing '=' — {line!r}", file=sys.stderr)
            continue
        left, _, right = line.partition("=")
        key = left.strip()
        if not key:
            print(f"Warning: overrides line {line_no}: empty key", file=sys.stderr)
            continue
        try:
            rule = parse_override_value(right)
        except ValueError as e:
            raise ValueError(f"overrides line {line_no}: {e}") from e
        if key.lower().startswith("pattern:"):
            pat = key.split(":", 1)[1].strip()
            if pat:
                patterns.append((pat, rule))
        else:
            exact[key] = rule
    return OverrideTable(exact=exact, patterns=patterns)


def lookup_override(item_id: str, table: OverrideTable) -> OverrideRule | None:
    if item_id in table.exact:
        return table.exact[item_id]
    for pat, rule in table.patterns:
        if fnmatch.fnmatch(item_id, pat):
            return rule
    return None


def first_matching_multiplier(item_id: str, rules: list[dict[str, Any]]) -> float | None:
    for rule in rules:
        for pat in rule.get("patterns", []):
            if fnmatch.fnmatch(item_id, pat):
                return float(rule.get("multiplier", 1.0))
    return None


def default_building_block_batch_size(cfg: dict[str, Any]) -> int:
    return max(1, int(cfg.get("buildingBlockBatchSize", 10)))


def first_matching_material_rule(
    material_id: str,
    rules: list[dict[str, Any]],
    default_batch: int,
) -> tuple[float, int] | None:
    for rule in rules:
        for pat in rule.get("patterns", []):
            if fnmatch.fnmatch(material_id, pat):
                base = float(rule.get("baseGold", 0))
                batch_raw = rule.get("batchSize")
                batch = max(1, int(batch_raw)) if batch_raw is not None else default_batch
                return base, batch
    return None


def resolve_material_pricing(material_id: str, cfg: dict[str, Any]) -> tuple[float, int]:
    rules = cfg.get("buildingBlockMaterialRules", [])
    default_batch = default_building_block_batch_size(cfg)
    matched = first_matching_material_rule(material_id, rules, default_batch)
    if matched is not None:
        return matched
    legacy = cfg.get("buildingBlockBaseGoldPerBatch")
    if legacy is not None:
        return float(legacy), default_batch
    return float(cfg.get("buildingBlockDefaultMaterialBaseGold", 12)), default_batch


def material_base_gold(material_id: str, cfg: dict[str, Any]) -> float:
    return resolve_material_pricing(material_id, cfg)[0]


def round_gold(value: float, minimum: int = 1) -> int:
    return max(minimum, int(round(value)))


def ingot_ore_batch_size(cfg: dict[str, Any]) -> int:
    return max(1, int(cfg.get("ingotOreBatchSize", 4)))


def ingot_bar_premium(cfg: dict[str, Any]) -> float:
    return float(cfg.get("ingotBarPremium", 1.0))


def bar_tier_gold(ore_tier_gold: int, cfg: dict[str, Any]) -> int:
    return round_gold(ore_tier_gold * ingot_bar_premium(cfg))


def hide_leather_batch_size(cfg: dict[str, Any]) -> int:
    return max(1, int(cfg.get("hideLeatherBatchSize", 5)))


def leather_gold_for_tier(hide_gold: int, cfg: dict[str, Any]) -> int:
    mult = float(cfg.get("leatherPremiumMultiplier", 1.25))
    return round_gold(hide_gold * mult)


def match_hide_leather_tier(item_id: str, cfg: dict[str, Any]) -> tuple[str, int, int] | None:
    """Returns (kind, goldPerBatch, batchSize) where kind is 'hide' or 'leather'."""
    batch = hide_leather_batch_size(cfg)
    for tier in cfg.get("hideLeatherTiers", []):
        hide_gold = int(tier.get("hideGold", 0))
        if hide_gold <= 0:
            continue
        leather_gold = int(tier.get("leatherGold", leather_gold_for_tier(hide_gold, cfg)))
        for token in tier.get("hideTokens", []):
            if str(token) in item_id:
                return "hide", hide_gold, batch
        for token in tier.get("leatherTokens", []):
            if str(token) in item_id:
                return "leather", leather_gold, batch
    return None


def match_common_material_bundle(item_id: str, cfg: dict[str, Any]) -> PriceEntry | None:
    for rule in cfg.get("commonMaterialBundles", []):
        gold = int(rule.get("gold", 0))
        batch = max(1, int(rule.get("batchSize", 1)))
        for exact in rule.get("itemIds", []):
            if item_id == str(exact):
                return PriceEntry(gold=gold, batch_size=batch)
        for pat in rule.get("patterns", []):
            if fnmatch.fnmatch(item_id, str(pat)):
                return PriceEntry(gold=gold, batch_size=batch)
    return None


def price_hide_or_leather(item_id: str, cfg: dict[str, Any]) -> PriceEntry | None:
    if not (item_id.startswith("Ingredient_Hide_") or item_id.startswith("Ingredient_Leather_")):
        return None
    matched = match_hide_leather_tier(item_id, cfg)
    if matched is None:
        return None
    _kind, gold, batch = matched
    return PriceEntry(gold=gold, batch_size=batch)


def price_common_material_bundle(item_id: str, cfg: dict[str, Any]) -> PriceEntry | None:
    return match_common_material_bundle(item_id, cfg)


def material_unit_gold(item_id: str | None, resource_type_id: str | None, cfg: dict[str, Any]) -> float:
    """Gold value of one unit for recipe costing (not batched shop listing)."""
    secondary: dict[str, Any] = cfg.get("recipeSecondaryMaterials", {})
    default_item = float(secondary.get("_defaultItemGold", cfg.get("defaultGoldPrice", 5)))

    if resource_type_id:
        wood_key = str(resource_type_id)
        if wood_key in secondary:
            return float(secondary[wood_key])
        if wood_key == "Wood_Trunk":
            return float(secondary.get("Wood_Trunk", 1))
        return default_item

    if not item_id:
        return default_item

    if item_id.startswith("Ore_"):
        tier = detect_material_tier(item_id, cfg)
        if tier is not None:
            _tid, ore_base = tier
            return ore_base / ingot_ore_batch_size(cfg)
        return default_item

    if item_id.startswith("Ingredient_Bar_"):
        tier = detect_material_tier(item_id, cfg)
        if tier is not None:
            _tid, ore_base = tier
            return bar_tier_gold(ore_base, cfg) / ingot_ore_batch_size(cfg)
        return default_item

    hide_leather = match_hide_leather_tier(item_id, cfg)
    if hide_leather is not None:
        _kind, gold, batch = hide_leather
        return gold / batch

    bundle = match_common_material_bundle(item_id, cfg)
    if bundle is not None:
        return bundle.gold / bundle.batch_size

    if item_id in secondary:
        return float(secondary[item_id])

    return default_item


def build_item_data_index(items_root: Path) -> dict[str, dict[str, Any]]:
    index: dict[str, dict[str, Any]] = {}
    for path in items_root.rglob("*.json"):
        try:
            data = load_json(path)
        except (OSError, json.JSONDecodeError):
            continue
        index[path.stem] = data
    return index


def recipe_input_gold(row: dict[str, Any], cfg: dict[str, Any]) -> float:
    qty = int(row.get("Quantity", 0))
    if qty <= 0:
        return 0.0
    item_id = row.get("ItemId")
    resource_type_id = row.get("ResourceTypeId")
    item_key = str(item_id) if isinstance(item_id, str) else None
    resource_key = str(resource_type_id) if isinstance(resource_type_id, str) else None
    unit = material_unit_gold(item_key, resource_key, cfg)
    return unit * qty


def armor_recipe_multiplier(item_id: str, item_data: dict[str, Any], cfg: dict[str, Any]) -> float:
    mult = gear_variant_multiplier(item_id, cfg)
    recipe = item_data.get("Recipe")
    if isinstance(recipe, dict) and recipe.get("KnowledgeRequired") is True:
        mult *= float(cfg.get("knowledgeRecipePremium", 1.0))
    return mult


def price_armor_from_recipe(item_id: str, item_data: dict[str, Any], cfg: dict[str, Any]) -> PriceEntry | None:
    if not item_id.startswith("Armor_"):
        return None
    recipe = item_data.get("Recipe")
    if not isinstance(recipe, dict):
        return None
    inputs = recipe.get("Input")
    if not isinstance(inputs, list) or not inputs:
        return None
    total = 0.0
    for row in inputs:
        if isinstance(row, dict):
            total += recipe_input_gold(row, cfg)
    if total <= 0:
        return None
    mult = armor_recipe_multiplier(item_id, item_data, cfg)
    return PriceEntry(gold=round_gold(total * mult), batch_size=1)


def item_has_recipe(item_data: dict[str, Any]) -> bool:
    recipe = item_data.get("Recipe")
    if not isinstance(recipe, dict):
        return False
    inputs = recipe.get("Input")
    return isinstance(inputs, list) and len(inputs) > 0


def collect_configured_material_ids(cfg: dict[str, Any], item_data_index: dict[str, dict[str, Any]] | None = None) -> set[str]:
    ids: set[str] = set()
    for tier in cfg.get("hideLeatherTiers", []):
        for token in tier.get("hideTokens", []):
            ids.add(f"Ingredient_{token}" if not str(token).startswith("Ingredient_") else str(token))
        for token in tier.get("leatherTokens", []):
            ids.add(f"Ingredient_{token}" if not str(token).startswith("Ingredient_") else str(token))
    for rule in cfg.get("commonMaterialBundles", []):
        for exact in rule.get("itemIds", []):
            ids.add(str(exact))
    if item_data_index:
        for item_id in item_data_index:
            if item_id.startswith("Ingredient_Hide_") or item_id.startswith("Ingredient_Leather_"):
                ids.add(item_id)
            elif item_id == "Ingredient_Fibre":
                ids.add(item_id)
            elif item_id.startswith("Ingredient_Fabric_Scrap_"):
                ids.add(item_id)
            elif item_id.startswith("Ingredient_Feathers_"):
                ids.add(item_id)
            elif item_id.startswith("Ingredient_Bolt_"):
                ids.add(item_id)
            elif item_id.startswith("Weapon_Spear_"):
                ids.add(item_id)
    return ids


def collect_override_item_ids(overrides: OverrideTable) -> set[str]:
    return set(overrides.exact.keys())


def collect_item_ids(
    loot_dir: Path,
    extra_files: list[Path],
    extra_ids: list[str] | None = None,
) -> list[str]:
    ids: set[str] = set()
    for loot_file in sorted(loot_dir.glob("*.json")):
        data = load_json(loot_file)
        for entry in data.get("entries", []):
            item_id = entry.get("itemId")
            if isinstance(item_id, str) and item_id.strip():
                ids.add(item_id.strip())
    for path in extra_files:
        if not path.is_file():
            continue
        data = load_json(path)
        prices = data.get("prices")
        if isinstance(prices, dict):
            ids.update(prices.keys())
    if extra_ids:
        ids.update(extra_ids)
    return sorted(ids)


def resolve_hytale_items_root(cfg: dict[str, Any]) -> Path | None:
    raw = cfg.get("hytaleItemsRoot")
    if raw is None or raw == "":
        candidate = DEFAULT_HYTALE_ITEMS_ROOT
    else:
        candidate = Path(str(raw))
        if not candidate.is_absolute():
            candidate = REPO_ROOT / candidate
    if candidate.is_dir():
        return candidate
    return None


def building_block_prefixes(cfg: dict[str, Any]) -> list[str]:
    return list(
        cfg.get("buildingBlockPrefixes", ["Rock_", "Wood_", "Soil_", "Cloth_", "Metal_", "Rubble_", "Prototype_"])
    )


def normalize_block_item_id(item_id: str) -> str:
    """Normalize legacy Metal-Copper ids to Metal_Copper."""
    if item_id.startswith("Metal-"):
        return "Metal_" + item_id[len("Metal-") :]
    return item_id


def is_mushroom_trunk_block(item_id: str) -> bool:
    return item_id.startswith("Plant_Crop_Mushroom_Block_") and item_id.endswith("_Trunk")


def discover_building_block_ids(items_root: Path, cfg: dict[str, Any]) -> set[str]:
    prefixes = building_block_prefixes(cfg)
    ids: set[str] = set()
    for path in items_root.rglob("*.json"):
        item_id = path.stem
        if item_id.startswith("Rock_Gem_"):
            continue
        if any(item_id.startswith(prefix) for prefix in prefixes):
            ids.add(item_id)
    return ids


def discover_gear_item_ids(items_root: Path, cfg: dict[str, Any], item_data: dict[str, dict[str, Any]] | None = None) -> set[str]:
    """Weapon_/Armor_/Tool_ items with a configured gear type and material tier or armor recipe."""
    if not cfg.get("discoverGearFromAssets", True):
        return set()
    data_index = item_data or build_item_data_index(items_root)
    ids: set[str] = set()
    for path in items_root.rglob("*.json"):
        item_id = path.stem
        if not (
            item_id.startswith("Weapon_")
            or item_id.startswith("Armor_")
            or item_id.startswith("Tool_")
        ):
            continue
        if parse_gear_type(item_id, cfg) is None:
            continue
        if item_id.startswith("Armor_"):
            armor_data = data_index.get(item_id)
            if armor_data is not None and item_has_recipe(armor_data):
                ids.add(item_id)
                continue
        if detect_gear_tier(item_id, cfg) is not None:
            ids.add(item_id)
    return ids


def building_block_type_keys(cfg: dict[str, Any]) -> list[str]:
    mults: dict[str, Any] = cfg.get("buildingBlockTypeMultipliers", {})
    return sorted((str(k) for k in mults if str(k) != "Base"), key=len, reverse=True)


def parse_block_material_type(item_id: str, type_keys: list[str]) -> tuple[str, str]:
    for typ in type_keys:
        token = "_" + typ
        if item_id.endswith(token):
            return item_id[: -len(token)], typ
    return item_id, "Base"


def resolve_block_type_multiplier(type_suffix: str, cfg: dict[str, Any]) -> float:
    mults: dict[str, Any] = cfg.get("buildingBlockTypeMultipliers", {})
    base = float(mults.get("Base", 1.0))
    if not type_suffix or type_suffix == "Base":
        return base
    best_key = ""
    best = base
    for key, mult in mults.items():
        key_s = str(key)
        if key_s == "Base":
            continue
        if type_suffix == key_s or type_suffix.endswith("_" + key_s):
            if len(key_s) > len(best_key):
                best_key = key_s
                best = float(mult)
    return best


def is_building_block_item(item_id: str, cfg: dict[str, Any], catalog: set[str]) -> bool:
    item_id = normalize_block_item_id(item_id)
    if item_id.startswith("Rock_Gem_"):
        return False
    if is_mushroom_trunk_block(item_id):
        return True
    if item_id in catalog:
        return True
    return any(item_id.startswith(prefix) for prefix in building_block_prefixes(cfg))


def count_recipe_bars(item_data: dict[str, Any]) -> int | None:
    recipe = item_data.get("Recipe")
    if not isinstance(recipe, dict):
        return None
    inputs = recipe.get("Input")
    if not isinstance(inputs, list):
        return None
    total = 0
    found_bar = False
    for row in inputs:
        if not isinstance(row, dict):
            continue
        item_id = row.get("ItemId")
        if not isinstance(item_id, str) or not item_id.startswith("Ingredient_Bar_"):
            continue
        found_bar = True
        total += int(row.get("Quantity", 0))
    return total if found_bar else None


def build_recipe_bar_index(items_root: Path) -> dict[str, int]:
    index: dict[str, int] = {}
    for path in items_root.rglob("*.json"):
        try:
            data = load_json(path)
        except (OSError, json.JSONDecodeError):
            continue
        bars = count_recipe_bars(data)
        if bars is None:
            continue
        item_id = path.stem
        index[item_id] = bars
    return index


def gear_ingot_units(
    item_id: str,
    category: str,
    gear_type: str,
    cfg: dict[str, Any],
    recipe_index: dict[str, int],
) -> tuple[float, str]:
    if item_id in recipe_index:
        return float(recipe_index[item_id]), "recipe"
    type_def = cfg.get("gearTypes", {}).get(category, {}).get(gear_type, {})
    if isinstance(type_def, dict):
        return float(type_def.get("ingotUnits", 1)), "fallback"
    return 1.0, "fallback"


def detect_material_tier(item_id: str, cfg: dict[str, Any]) -> tuple[str, int] | None:
    """Longest matching material token wins. Returns (tier_id, ingotBaseGold per ingot batch)."""
    ranked: list[tuple[int, str, str, int]] = []
    for tier in cfg.get("gearTiers", []):
        tier_id = str(tier.get("id", ""))
        base = int(tier.get("ingotBaseGold", tier.get("price", 0)))
        for token in tier.get("tokens", []):
            ranked.append((len(str(token)), str(token), tier_id, base))
    ranked.sort(key=lambda row: row[0], reverse=True)
    for length, token, tier_id, base in ranked:
        if token in item_id:
            return tier_id, base
    return None


def detect_gear_tier(item_id: str, cfg: dict[str, Any]) -> tuple[str, int] | None:
    return detect_material_tier(item_id, cfg)


def price_ingot_or_ore(item_id: str, cfg: dict[str, Any]) -> PriceEntry | None:
    if item_id.startswith("Ingredient_Bar_") or item_id.startswith("Ore_"):
        tier = detect_material_tier(item_id, cfg)
        if tier is None:
            return None
        _tier_id, ore_base = tier
        batch = ingot_ore_batch_size(cfg)
        gold = ore_base
        if item_id.startswith("Ingredient_Bar_"):
            gold = bar_tier_gold(ore_base, cfg)
        return PriceEntry(gold=gold, batch_size=batch)
    return None


def parse_gear_type(item_id: str, cfg: dict[str, Any]) -> tuple[str, str] | None:
    """Returns (category, gearTypeKey) when item id is a configured gear piece."""
    gear_types: dict[str, Any] = cfg.get("gearTypes", {})
    if item_id.startswith("Weapon_"):
        body = item_id.removeprefix("Weapon_")
        weapon_types: dict[str, Any] = gear_types.get("Weapon", {})
        for gtype in sorted(weapon_types.keys(), key=len, reverse=True):
            if body == gtype or body.startswith(gtype + "_"):
                return "Weapon", gtype
        return None
    if item_id.startswith("Armor_"):
        body = item_id.removeprefix("Armor_")
        slots: list[str] = list(cfg.get("gearArmorSlots", ["Chest", "Legs", "Head", "Hands"]))
        for slot in sorted(slots, key=len, reverse=True):
            if body == slot or body.endswith("_" + slot):
                return "Armor", slot
        return None
    if item_id.startswith("Tool_"):
        body = item_id.removeprefix("Tool_")
        tool_types: dict[str, Any] = gear_types.get("Tool", {})
        for gtype in sorted(tool_types.keys(), key=len, reverse=True):
            if body == gtype or body.startswith(gtype + "_"):
                return "Tool", gtype
        return None
    return None


def gear_variant_multiplier(item_id: str, cfg: dict[str, Any]) -> float:
    mult = 1.0
    for name, factor in cfg.get("gearVariantMultipliers", {}).items():
        if str(name) in item_id:
            mult *= float(factor)
    special_mult = float(cfg.get("gearSpecialVariantMultiplier", 1.0))
    for token in cfg.get("gearSpecialVariantTokens", []):
        if str(token) in item_id:
            mult *= special_mult
    return mult


def price_gear(
    item_id: str,
    cfg: dict[str, Any],
    recipe_index: dict[str, int] | None = None,
    item_data_index: dict[str, dict[str, Any]] | None = None,
) -> PriceEntry | None:
    parsed = parse_gear_type(item_id, cfg)
    if parsed is None:
        return None
    category, gear_type = parsed
    if category == "Armor":
        data_index = item_data_index or {}
        item_data = data_index.get(item_id)
        if item_data is not None:
            armor = price_armor_from_recipe(item_id, item_data, cfg)
            if armor is not None:
                return armor
    tier = detect_gear_tier(item_id, cfg)
    if tier is None:
        return None
    _tier_id, ore_base = tier
    ingot_base = bar_tier_gold(ore_base, cfg)
    type_def = cfg.get("gearTypes", {}).get(category, {}).get(gear_type, {})
    if not isinstance(type_def, dict):
        return None
    index = recipe_index or {}
    ingot_units, _source = gear_ingot_units(item_id, category, gear_type, cfg, index)
    type_mult = float(type_def.get("multiplier", 1.0))
    variant_mult = gear_variant_multiplier(item_id, cfg)
    batch = ingot_ore_batch_size(cfg)
    gold = round_gold(ingot_base * (ingot_units / batch) * type_mult * variant_mult)
    return PriceEntry(gold=gold, batch_size=1)


def price_jewelry(item_id: str, cfg: dict[str, Any]) -> PriceEntry | None:
    m = JEWELRY_RE.match(item_id)
    if not m:
        return None
    kind, metal, gem = m.group(1), m.group(2), m.group(3)
    gems = cfg.get("gems", {})
    if gem not in gems:
        return None
    gem_price = int(gems[gem])
    premiums = cfg.get("jewelryMetalPremium", {})
    metal_prem = premiums.get(metal, {}).get(kind, 0)
    return PriceEntry(gold=gem_price + int(metal_prem), batch_size=1)


def price_gem_item(item_id: str, cfg: dict[str, Any]) -> PriceEntry | None:
    if not item_id.startswith("Rock_Gem_"):
        return None
    gem = item_id.removeprefix("Rock_Gem_")
    gems = cfg.get("gems", {})
    if gem not in gems:
        return None
    return PriceEntry(gold=int(gems[gem]), batch_size=1)


def price_crystal_item(item_id: str, cfg: dict[str, Any]) -> PriceEntry | None:
    """Shard and colored crystal blocks: one base price × size type (color ignored)."""
    crystal_cfg = cfg.get("crystalPricing")
    if not isinstance(crystal_cfg, dict):
        return None

    type_multipliers: dict[str, Any] = crystal_cfg.get("typeMultipliers", {})
    base = float(crystal_cfg.get("baseGold", 12))
    crystal_type: str | None = None
    batch_size = int(crystal_cfg.get("shardBatchSize", 1))

    if CRYSTAL_SHARD_RE.match(item_id):
        crystal_type = "Shard"
        batch_size = int(crystal_cfg.get("shardBatchSize", 1))
    else:
        match = CRYSTAL_ROCK_RE.match(item_id)
        if not match:
            return None
        crystal_type = match.group(1)
        if crystal_type == "Block":
            batch_size = int(crystal_cfg.get("blockBatchSize", 10))
        else:
            batch_size = int(crystal_cfg.get("sizeBatchSize", 1))

    multiplier = float(type_multipliers.get(crystal_type, 1.0))
    return PriceEntry(gold=round_gold(base * multiplier), batch_size=max(1, batch_size))


def price_furniture(item_id: str, cfg: dict[str, Any]) -> PriceEntry | None:
    if not item_id.startswith("Furniture_"):
        return None
    body = item_id.removeprefix("Furniture_")
    set_rarities: dict[str, float] = cfg.get("furnitureSetRarity", {})
    type_bases: dict[str, Any] = cfg.get("furnitureTypeBaseGold", {})

    best_set = ""
    best_set_len = 0
    ftype = body
    for set_name in sorted(set_rarities.keys(), key=len, reverse=True):
        prefix = set_name + "_"
        if body.startswith(prefix):
            best_set = set_name
            best_set_len = len(set_name)
            ftype = body[best_set_len + 1 :]
            break
    if not best_set:
        # Bookshelf_* variants without set prefix
        if body.startswith("Bookshelf_"):
            best_set = "Bookshelf"
            ftype = body
        else:
            split = body.split("_", 1)
            if len(split) == 2 and split[0] in set_rarities:
                best_set, ftype = split[0], split[1]

    set_mult = float(set_rarities.get(best_set, 1.0))
    base = float(type_bases.get("_default", 22))
    if ftype in type_bases:
        base = float(type_bases[ftype])
    else:
        # Longest suffix match (Door_Large before Door)
        for key in sorted(type_bases.keys(), key=len, reverse=True):
            if key == "_default":
                continue
            if ftype == key or ftype.endswith("_" + key):
                base = float(type_bases[key])
                break
    gold = round_gold(base * set_mult)
    return PriceEntry(gold=gold, batch_size=1)


def price_deco(item_id: str, cfg: dict[str, Any]) -> PriceEntry | None:
    if not item_id.startswith("Deco_"):
        return None
    body = item_id.removeprefix("Deco_")
    type_bases: dict[str, Any] = cfg.get("decoTypeBaseGold", {})
    base = float(type_bases.get("_default", 15))
    if body in type_bases:
        base = float(type_bases[body])
    else:
        for key in sorted(type_bases.keys(), key=len, reverse=True):
            if key == "_default":
                continue
            if body == key or body.endswith("_" + key):
                base = float(type_bases[key])
                break
    return PriceEntry(gold=round_gold(base), batch_size=1)


def is_building_block(item_id: str, cfg: dict[str, Any], catalog: set[str] | None = None) -> bool:
    if catalog is None:
        catalog = set()
    return is_building_block_item(item_id, cfg, catalog)


def price_building_block(
    item_id: str,
    cfg: dict[str, Any],
    catalog: set[str] | None = None,
) -> PriceEntry | None:
    block_catalog = catalog or set()
    if not is_building_block_item(item_id, cfg, block_catalog):
        return None
    item_id = normalize_block_item_id(item_id)
    type_keys = building_block_type_keys(cfg)
    material_id, type_suffix = parse_block_material_type(item_id, type_keys)
    mat_base, batch = resolve_material_pricing(material_id, cfg)
    type_mult = resolve_block_type_multiplier(type_suffix, cfg)
    gold = round_gold(mat_base * type_mult)
    return PriceEntry(gold=gold, batch_size=batch)


def price_plant(item_id: str, cfg: dict[str, Any]) -> PriceEntry | None:
    if not item_id.startswith("Plant_"):
        return None
    rules = cfg.get("plantBatchRules", {})
    batch = int(rules.get("batchSize", 5))
    base = float(rules.get("baseGoldPerBatch", 10))
    mult = float(rules.get("cropMultiplier", 1.0))
    if "Plant_Seeds_" in item_id:
        mult = float(rules.get("seedMultiplier", 1.0))
        if item_id.endswith("_Eternal"):
            mult *= 2.5
    elif "Plant_Sapling_" in item_id:
        mult = float(rules.get("saplingMultiplier", 1.5))
    elif "Plant_Flower_" in item_id:
        mult = float(rules.get("flowerMultiplier", 0.8))
    elif "Plant_Fruit_" in item_id:
        mult = float(rules.get("fruitMultiplier", 1.2))
    elif "Plant_Crop_" in item_id:
        mult = float(rules.get("cropMultiplier", 1.0))
    return PriceEntry(gold=round_gold(base * mult), batch_size=batch)


def price_item(
    item_id: str,
    cfg: dict[str, Any],
    recipe_index: dict[str, int] | None = None,
    block_catalog: set[str] | None = None,
    item_data_index: dict[str, dict[str, Any]] | None = None,
) -> tuple[PriceEntry | None, str]:
    consumables = cfg.get("consumables", {})
    if item_id in consumables:
        return PriceEntry(gold=int(consumables[item_id]), batch_size=1), "consumable"

    aetherhaven = cfg.get("aetherhavenItems", {})
    if item_id in aetherhaven:
        return PriceEntry(gold=int(aetherhaven[item_id]), batch_size=1), "aetherhaven"

    jewelry = price_jewelry(item_id, cfg)
    if jewelry:
        return jewelry, "jewelry"

    gem = price_gem_item(item_id, cfg)
    if gem:
        return gem, "gem"

    crystal = price_crystal_item(item_id, cfg)
    if crystal:
        return crystal, "crystal"

    if item_id.startswith("Recipe_Book_"):
        return PriceEntry(gold=int(cfg.get("recipeBookGoldPrice", 45)), batch_size=1), "recipe"
    if item_id.startswith("Recipe_"):
        return PriceEntry(gold=int(cfg.get("recipeBookGoldPrice", 45)), batch_size=1), "recipe"
    if item_id == "Recipe_Page":
        return PriceEntry(gold=int(cfg.get("recipePageGoldPrice", 15)), batch_size=1), "recipe"

    if item_id.startswith("Bench_"):
        return PriceEntry(gold=int(cfg.get("benchGoldPrice", 75)), batch_size=1), "bench"

    furniture = price_furniture(item_id, cfg)
    if furniture:
        return furniture, "furniture"

    deco = price_deco(item_id, cfg)
    if deco:
        return deco, "deco"

    block = price_building_block(item_id, cfg, block_catalog)
    if block:
        return block, "building_block"

    plant = price_plant(item_id, cfg)
    if plant:
        return plant, "plant"

    ingot = price_ingot_or_ore(item_id, cfg)
    if ingot:
        return ingot, "ingot_ore"

    hide_leather = price_hide_or_leather(item_id, cfg)
    if hide_leather:
        return hide_leather, "hide_leather"

    bundle = price_common_material_bundle(item_id, cfg)
    if bundle:
        return bundle, "common_material"

    gear = price_gear(item_id, cfg, recipe_index, item_data_index)
    if gear:
        return gear, "gear"

    # Food/potion pattern fallbacks for unlisted variants
    if item_id.startswith("Potion_"):
        if "Small" in item_id:
            return PriceEntry(gold=12, batch_size=1), "potion_fallback"
        if "Greater" in item_id or "Large" in item_id:
            return PriceEntry(gold=45, batch_size=1), "potion_fallback"
        if "Lesser" in item_id:
            return PriceEntry(gold=18, batch_size=1), "potion_fallback"
        return PriceEntry(gold=30, batch_size=1), "potion_fallback"

    if item_id.startswith("Food_"):
        return PriceEntry(gold=int(cfg.get("defaultGoldPrice", 25)), batch_size=1), "food_fallback"

    return None, "default"


def generate_prices(
    item_ids: list[str],
    cfg: dict[str, Any],
    overrides: OverrideTable,
    omit_defaults: bool,
    recipe_index: dict[str, int] | None = None,
    block_catalog: set[str] | None = None,
    item_data_index: dict[str, dict[str, Any]] | None = None,
) -> tuple[dict[str, Any], Counter[str]]:
    default_gold = int(cfg.get("defaultGoldPrice", 25))
    default_batch = int(cfg.get("defaultBatchSize", 1))
    prices: dict[str, Any] = {}
    categories: Counter[str] = Counter()
    skipped = 0

    for item_id in item_ids:
        ovr = lookup_override(item_id, overrides)
        if ovr is not None:
            if ovr.skip:
                skipped += 1
                continue
            if ovr.entry is not None:
                entry = ovr.entry
                categories["override"] += 1
                if omit_defaults and entry.gold == default_gold and entry.batch_size == default_batch:
                    continue
                prices[item_id] = entry.to_json()
                continue

        entry, category = price_item(item_id, cfg, recipe_index, block_catalog, item_data_index)
        categories[category] += 1
        if entry is None:
            entry = PriceEntry(gold=default_gold, batch_size=default_batch)
        if omit_defaults and entry.gold == default_gold and entry.batch_size == default_batch:
            continue
        prices[item_id] = entry.to_json()

    if skipped:
        categories["skipped_override"] = skipped
    return prices, categories


def print_report(
    prices: dict[str, Any],
    categories: Counter[str],
    cfg: dict[str, Any],
    recipe_index: dict[str, int] | None = None,
    block_catalog: set[str] | None = None,
) -> None:
    print(f"Generated {len(prices)} price entries")
    print("Categories:")
    for cat, count in categories.most_common():
        print(f"  {cat}: {count}")

    ingot_batch = ingot_ore_batch_size(cfg)
    print(f"\nSample gear prices (ingotBase × bars/{ingot_batch} × multipliers):")
    index = recipe_index or {}
    samples = [
        "Weapon_Sword_Wood",
        "Weapon_Sword_Copper",
        "Weapon_Sword_Iron",
        "Weapon_Sword_Silversteel",
        "Weapon_Longsword_Cobalt",
        "Weapon_Longsword_Adamantite",
        "Weapon_Longsword_Mithril",
        "Armor_Iron_Chest",
        "Armor_Prisma_Chest",
        "Tool_Pickaxe_Iron",
    ]
    for sid in samples:
        if sid in prices:
            parsed = parse_gear_type(sid, cfg)
            tier = detect_gear_tier(sid, cfg)
            detail = ""
            if parsed and tier:
                cat, gtype = parsed
                _tid, base = tier
                units, source = gear_ingot_units(sid, cat, gtype, cfg, index)
                detail = f"  [{base}g per {ingot_batch} bars × {units:g}/{ingot_batch} batches ({source}) × variants]"
            print(f"  {sid}: {prices[sid]}{detail}")

    print(f"\nSample ingots/ore (batch {ingot_batch}):")
    for sid in ["Ingredient_Bar_Copper", "Ingredient_Bar_Iron", "Ore_Iron", "Ore_Adamantite"]:
        if sid in prices:
            print(f"  {sid}: {prices[sid]}")

    unmatched = [
        i
        for i in prices
        if (i.startswith("Weapon_") or i.startswith("Armor_") or i.startswith("Tool_"))
        and parse_gear_type(i, cfg) is None
    ]
    if unmatched:
        print(f"\nGear-prefix items without a configured type ({len(unmatched)} priced via override/default):")
        for sid in sorted(unmatched)[:12]:
            print(f"  {sid}: {prices.get(sid)}")
        if len(unmatched) > 12:
            print(f"  ... +{len(unmatched) - 12} more")

    print("\nSample jewelry:")
    for sid in sorted(k for k in prices if k.startswith("Aetherhaven_Ring") or k.startswith("Aetherhaven_Necklace"))[:6]:
        print(f"  {sid}: {prices[sid]}")

    crystal_cfg = cfg.get("crystalPricing", {})
    crystal_base = crystal_cfg.get("baseGold", 12)
    crystal_mults = crystal_cfg.get("typeMultipliers", {})
    print(f"\nSample crystals (base {crystal_base}g × type multiplier; color ignored):")
    for sid in [
        "Ingredient_Crystal_Blue",
        "Ingredient_Crystal_Red",
        "Rock_Crystal_Purple_Small",
        "Rock_Crystal_Green_Medium",
        "Rock_Crystal_Yellow_Large",
        "Rock_Crystal_Cyan_Block",
    ]:
        if sid in prices:
            typ = "Shard" if sid.startswith("Ingredient_Crystal_") else sid.rsplit("_", 1)[-1]
            mult = crystal_mults.get(typ, "?")
            print(f"  {sid}: {prices[sid]}  [type={typ}, mult={mult}]")

    print("\nGem prices:")
    for gem in ["Emerald", "Topaz", "Ruby", "Sapphire", "Zephyr", "Diamond", "Voidstone"]:
        sid = f"Rock_Gem_{gem}"
        if sid in prices:
            print(f"  {sid}: {prices[sid]}")

    block_batch = default_building_block_batch_size(cfg)
    type_keys = building_block_type_keys(cfg)
    print(f"\nSample building blocks (material base × type mult; batch from material rule or default {block_batch}):")
    block_samples = [
        "Rock_Slate",
        "Rock_Slate_Brick",
        "Rock_Slate_Brick_Decorative",
        "Rock_Stone_Cobble_Roof",
        "Rock_Crystal_Blue_Small",
        "Rock_Crystal_Blue_Block",
        "Wood_Oak_Trunk",
        "Wood_Oak_Trunk_Stairs",
    ]
    catalog = block_catalog or set()
    for sid in block_samples:
        if sid not in prices:
            continue
        material_id, type_suffix = parse_block_material_type(sid, type_keys)
        mat_base, mat_batch = resolve_material_pricing(material_id, cfg)
        type_mult = resolve_block_type_multiplier(type_suffix, cfg)
        src = "asset" if sid in catalog else "prefix"
        print(
            f"  {sid}: {prices[sid]}  "
            f"[{material_id} @ {mat_base:g}g × {type_suffix} {type_mult:g}, batch {mat_batch} ({src})]"
        )
    block_count = sum(1 for k in prices if is_building_block_item(k, cfg, catalog))
    print(f"  ({block_count} building-block entries total)")

    gold_values = []
    for val in prices.values():
        if isinstance(val, int):
            gold_values.append(val)
        elif isinstance(val, dict):
            gold_values.append(int(val.get("gold", 0)))
    if gold_values:
        print(
            f"\nGold range: {min(gold_values)}–{max(gold_values)} "
            f"(artifact ingot base ~{cfg.get('gearTiers', [{}])[-1].get('ingotBaseGold', '?')}g)"
        )


def dump_recipe_report(
    item_ids: list[str],
    cfg: dict[str, Any],
    recipe_index: dict[str, int],
) -> None:
    print(f"Recipe bar index: {len(recipe_index)} items with Ingredient_Bar_* inputs")
    gear_ids = [i for i in item_ids if parse_gear_type(i, cfg) is not None and detect_gear_tier(i, cfg)]
    recipe_sourced = [i for i in gear_ids if i in recipe_index]
    fallback = [i for i in gear_ids if i not in recipe_index]
    print(f"Loot-table gear: {len(gear_ids)} total, {len(recipe_sourced)} from recipes, {len(fallback)} fallback")
    if fallback:
        print("\nGear without bar recipes (using config ingotUnits fallback):")
        for sid in sorted(fallback):
            parsed = parse_gear_type(sid, cfg)
            if not parsed:
                continue
            cat, gtype = parsed
            units = cfg.get("gearTypes", {}).get(cat, {}).get(gtype, {}).get("ingotUnits", "?")
            print(f"  {sid}: fallback {units} bars")

    print("\nIron-tier recipe bar counts by gear type:")
    iron_by_type: dict[str, list[int]] = {}
    for item_id, bars in sorted(recipe_index.items()):
        if "_Iron" not in item_id:
            continue
        parsed = parse_gear_type(item_id, cfg)
        if not parsed:
            continue
        _cat, gtype = parsed
        iron_by_type.setdefault(gtype, []).append(bars)
    for gtype in sorted(iron_by_type):
        counts = sorted(set(iron_by_type[gtype]))
        label = str(counts[0]) if len(counts) == 1 else f"{min(counts)}–{max(counts)} (variants: {counts})"
        fallback_units = None
        for cat in ("Weapon", "Armor", "Tool"):
            tdef = cfg.get("gearTypes", {}).get(cat, {}).get(gtype)
            if isinstance(tdef, dict):
                fallback_units = tdef.get("ingotUnits")
                break
        fb = f", config fallback {fallback_units}" if fallback_units is not None else ""
        print(f"  {gtype}: {label}{fb}")


def dump_building_blocks_report(cfg: dict[str, Any], block_catalog: set[str]) -> None:
    type_keys = building_block_type_keys(cfg)
    print(f"Building blocks discovered in assets: {len(block_catalog)}")
    by_material: dict[str, list[str]] = {}
    for item_id in sorted(block_catalog):
        material_id, type_suffix = parse_block_material_type(item_id, type_keys)
        by_material.setdefault(material_id, []).append(type_suffix)
    print(f"Unique materials: {len(by_material)}")

    print("\nRock_Slate family (material base × type):")
    mat_base, mat_batch = resolve_material_pricing("Rock_Slate", cfg)
    print(f"  Rock_Slate material base: {mat_base}g per batch of {mat_batch}")
    for item_id in sorted(i for i in block_catalog if i == "Rock_Slate" or i.startswith("Rock_Slate_")):
        _mat, typ = parse_block_material_type(item_id, type_keys)
        mult = resolve_block_type_multiplier(typ, cfg)
        print(f"  {item_id}: type={typ}, mult={mult}, gold={round_gold(mat_base * mult)}")

    print("\nMaterial bases (sample):")
    sample_materials = [
        "Rock_Stone",
        "Rock_Slate",
        "Rock_Crystal_Blue",
        "Wood_Oak",
        "Soil_Clay",
        "Cloth_Block_Wool_Black",
    ]
    for mat in sample_materials:
        if mat in by_material or any(m.startswith(mat + "_") for m in by_material):
            base, batch = resolve_material_pricing(mat, cfg)
            print(f"  {mat}: {base}g batch {batch}, variants={len(by_material.get(mat, []))}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--config", type=Path, default=CONFIG_PATH, help="Tier/config JSON")
    parser.add_argument("--overrides", type=Path, default=OVERRIDES_PATH, help="Pattern override txt")
    parser.add_argument("--loot-dir", type=Path, default=DEFAULT_LOOT_DIR, help="Shop loot tables")
    parser.add_argument(
        "--also-from",
        type=Path,
        action="append",
        default=[],
        help="Extra shop_prices.json to merge item ids from",
    )
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="Output JSON path")
    parser.add_argument("--dry-run", action="store_true", help="Do not write output file")
    parser.add_argument("--report", action="store_true", help="Print pricing summary")
    parser.add_argument(
        "--omit-defaults",
        action="store_true",
        help="Omit entries equal to defaultGoldPrice/defaultBatchSize",
    )
    parser.add_argument(
        "--dump-recipes",
        action="store_true",
        help="Print recipe bar counts vs config fallbacks and exit",
    )
    parser.add_argument(
        "--dump-blocks",
        action="store_true",
        help="Print building block material/type breakdown from assets and exit",
    )
    args = parser.parse_args()

    if not args.config.is_file():
        print(f"Config missing: {args.config}", file=sys.stderr)
        return 1
    cfg = load_json(args.config)
    overrides = load_overrides(args.overrides)

    items_root = resolve_hytale_items_root(cfg)
    recipe_index: dict[str, int] = {}
    block_catalog: set[str] = set()
    gear_catalog: set[str] = set()
    item_data_index: dict[str, dict[str, Any]] = {}
    if items_root is not None:
        item_data_index = build_item_data_index(items_root)
        recipe_index = build_recipe_bar_index(items_root)
        block_catalog = discover_building_block_ids(items_root, cfg)
        gear_catalog = discover_gear_item_ids(items_root, cfg, item_data_index)
        print(f"Loaded {len(item_data_index)} item definitions from {items_root}")
        print(f"Loaded {len(recipe_index)} item recipes from {items_root}")
        print(f"Discovered {len(block_catalog)} building blocks from {items_root}")
        print(f"Discovered {len(gear_catalog)} gear items from {items_root}")
    else:
        raw = cfg.get("hytaleItemsRoot", DEFAULT_HYTALE_ITEMS_ROOT)
        print(
            f"Warning: Hytale item root not found ({raw}); gear and building-block discovery disabled",
            file=sys.stderr,
        )

    extra = list(args.also_from)
    existing_prices = REPO_ROOT / "src" / "main" / "resources" / "defaults" / "shop_prices.json"
    if existing_prices.is_file() and existing_prices not in extra:
        extra.append(existing_prices)

    asset_ids = sorted(
        block_catalog
        | gear_catalog
        | collect_configured_material_ids(cfg, item_data_index)
        | collect_override_item_ids(overrides)
    )
    item_ids = collect_item_ids(args.loot_dir, extra, extra_ids=asset_ids)
    if not item_ids:
        print(f"No item ids found under {args.loot_dir}", file=sys.stderr)
        return 1

    if args.dump_recipes:
        dump_recipe_report(item_ids, cfg, recipe_index)
        return 0

    if args.dump_blocks:
        if not block_catalog:
            print("No building blocks discovered (check hytaleItemsRoot).", file=sys.stderr)
            return 1
        dump_building_blocks_report(cfg, block_catalog)
        return 0

    prices, categories = generate_prices(
        item_ids, cfg, overrides, args.omit_defaults, recipe_index, block_catalog, item_data_index
    )

    out_doc = {
        "catalogRevision": 1,
        "defaultGoldPrice": int(cfg.get("defaultGoldPrice", 25)),
        "defaultBatchSize": int(cfg.get("defaultBatchSize", 1)),
        "_generatedBy": "scripts/generate_shop_prices.py",
        "_generatedNote": "Review and copy into shop_prices.json; trim entries that match defaults.",
        "prices": prices,
    }

    if args.dry_run:
        print_report(prices, categories, cfg, recipe_index, block_catalog)
        print(f"\nDry run: would write {args.out} ({len(prices)} entries)")
        return 0

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(out_doc, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {args.out} ({len(prices)} entries)")
    if args.report:
        print_report(prices, categories, cfg, recipe_index, block_catalog)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
