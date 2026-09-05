#!/usr/bin/env python3
"""
Generate Server/Aetherhaven/Buildings/PrefabMaterials/<constructionId>.json from plot building
definitions and their prefab block lists.

Prefab blocks may use state-specific names (e.g. *Wood_Village_Wall_Full_State_Definitions_Bottom).
Those are merged into the base block/item id (Wood_Village_Wall_Full) for player requirements.

Multi-block furniture/structures store extra cells with a non-zero "filler" field; only the anchor
cell (filler == 0 or omitted) counts toward material requirements.

Conversions: scripts/prefab_material_conversions.txt maps prefab block ids (or pattern: globs) to
replacement materials (itemId / resourceTypeId per block) or "skip".

Run from repo root after prefab edits:
  python scripts/generate_prefab_materials.py
  python scripts/generate_prefab_materials.py --construction plot_farm
  python scripts/generate_prefab_materials.py --dry-run
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


REPO_ROOT = Path(__file__).resolve().parent.parent
BUILDINGS_DIR = REPO_ROOT / "src" / "main" / "resources" / "Server" / "Aetherhaven" / "Buildings"
PREFABS_DIR = REPO_ROOT / "src" / "main" / "resources" / "Server" / "Prefabs"
OUT_DIR = BUILDINGS_DIR / "PrefabMaterials"
CONVERSIONS_FILE = REPO_ROOT / "src" / "main" / "resources" / "Server" / "Aetherhaven" / "prefab_material_conversions.txt"
# Legacy fallback (same content as bundled resource above):
_LEGACY_CONVERSIONS = Path(__file__).resolve().parent / "prefab_material_conversions.txt"

# Prefab-only state suffixes (not separate player items).
_STATE_DEFINITIONS_RE = re.compile(r"_State_Definitions_.*$")
# Prefab hollow variants count as the solid block for survival requirements (e.g. roof hollow -> roof).
_HOLLOW_SUFFIX = "_Hollow"


@dataclass(frozen=True)
class OutputSpec:
    """Material added per one prefab block instance of the matched input."""

    kind: str  # "item" or "resource"
    id: str
    amount: int


@dataclass(frozen=True)
class ConversionRule:
    skip: bool
    outputs: tuple[OutputSpec, ...]


@dataclass
class ConversionTable:
    exact: dict[str, ConversionRule]
    patterns: list[tuple[str, ConversionRule]]


@dataclass
class MaterialCounts:
    items: Counter[str]
    resources: Counter[str]

    def add_output(self, spec: OutputSpec, block_instances: int) -> None:
        total = spec.amount * block_instances
        if total <= 0:
            return
        if spec.kind == "resource":
            self.resources[spec.id] += total
        else:
            self.items[spec.id] += total

    def add_direct_item(self, item_id: str, count: int = 1) -> None:
        if count > 0:
            self.items[item_id] += count


def parse_outputs(raw: str) -> ConversionRule:
    text = raw.strip()
    if not text or text.lower() == "skip":
        return ConversionRule(skip=True, outputs=())
    outputs: list[OutputSpec] = []
    for part in text.split(","):
        part = part.strip()
        if not part:
            continue
        lower = part.lower()
        if lower.startswith("resource:"):
            body = part[9:].strip()
            rid, sep, amt = body.partition(":")
            if not sep or not rid.strip():
                raise ValueError(f"Invalid resource output {part!r} (expected resource:TypeId:count)")
            outputs.append(OutputSpec("resource", rid.strip(), int(amt.strip())))
        elif lower.startswith("item:"):
            body = part[5:].strip()
            iid, sep, amt = body.partition(":")
            if not sep or not iid.strip():
                raise ValueError(f"Invalid item output {part!r} (expected item:ItemId:count)")
            outputs.append(OutputSpec("item", iid.strip(), int(amt.strip())))
        else:
            iid, sep, amt = part.partition(":")
            if not iid.strip():
                raise ValueError(f"Invalid output {part!r}")
            count = int(amt.strip()) if sep else 1
            outputs.append(OutputSpec("item", iid.strip(), count))
    if not outputs:
        return ConversionRule(skip=True, outputs=())
    return ConversionRule(skip=False, outputs=tuple(outputs))


def load_conversions() -> ConversionTable:
    exact: dict[str, ConversionRule] = {"Empty": ConversionRule(skip=True, outputs=())}
    patterns: list[tuple[str, ConversionRule]] = []
    conv_path = CONVERSIONS_FILE if CONVERSIONS_FILE.is_file() else _LEGACY_CONVERSIONS
    if not conv_path.is_file():
        return ConversionTable(exact=exact, patterns=patterns)
    for line_no, line in enumerate(conv_path.read_text(encoding="utf-8").splitlines(), start=1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            print(f"Warning: conversions line {line_no}: missing '=' — {line!r}", file=sys.stderr)
            continue
        left, _, right = line.partition("=")
        key = left.strip()
        if not key:
            print(f"Warning: conversions line {line_no}: empty input key", file=sys.stderr)
            continue
        try:
            rule = parse_outputs(right)
        except ValueError as e:
            print(f"Error: conversions line {line_no}: {e}", file=sys.stderr)
            raise
        if key.lower().startswith("pattern:"):
            pat = key.split(":", 1)[1].strip()
            if pat:
                patterns.append((pat, rule))
        else:
            exact[key] = rule
    return ConversionTable(exact=exact, patterns=patterns)


def lookup_rule(item_id: str, table: ConversionTable) -> ConversionRule | None:
    if item_id in table.exact:
        return table.exact[item_id]
    for pat, rule in table.patterns:
        if fnmatch.fnmatch(item_id, pat):
            return rule
    return None


def normalize_block_to_item_id(raw_name: str) -> str | None:
    """
    Map a prefab block name to the item/block id players supply.
    Strips leading '*', collapses *_State_Definitions_* variants, maps *_Hollow to base id, and maps *_Trunk_Full to *_Trunk.
    Large chests are remapped to small chests in finalize_item_counts (with quantity x2).
    """
    name = raw_name.strip()
    if not name or name == "Empty":
        return None
    if name.startswith("*"):
        name = name[1:]
    if not name:
        return None
    name = _STATE_DEFINITIONS_RE.sub("", name)
    if name.endswith(_HOLLOW_SUFFIX):
        base = name[: -len(_HOLLOW_SUFFIX)]
        if base:
            name = base
    if "_Trunk_Full" in name:
        name = name.replace("_Trunk_Full", "_Trunk")
    return name if name else None


def count_prefab_blocks(prefab_path: Path, conversions: ConversionTable) -> MaterialCounts:
    data = json.loads(prefab_path.read_text(encoding="utf-8"))
    blocks = data.get("blocks")
    if not isinstance(blocks, list):
        raise ValueError(f"No blocks array in {prefab_path}")
    result = MaterialCounts(items=Counter(), resources=Counter())
    state_variants: set[str] = set()
    hollow_merged = 0
    filler_skipped = 0
    converted_blocks = 0
    for b in blocks:
        if not isinstance(b, dict):
            continue
        filler = b.get("filler", 0)
        if isinstance(filler, int) and filler != 0:
            filler_skipped += 1
            continue
        raw = b.get("name")
        if not isinstance(raw, str):
            continue
        item_id = normalize_block_to_item_id(raw)
        if item_id is None:
            continue
        if raw.lstrip("*").endswith(_HOLLOW_SUFFIX) and not raw.lstrip("*").startswith(item_id):
            hollow_merged += 1
        rule = lookup_rule(item_id, conversions)
        if rule is not None:
            if rule.skip:
                continue
            converted_blocks += 1
            for spec in rule.outputs:
                result.add_output(spec, 1)
            normalized_raw = _STATE_DEFINITIONS_RE.sub("", raw.lstrip("*"))
            if normalized_raw != item_id and not normalized_raw.endswith(_HOLLOW_SUFFIX):
                state_variants.add(raw)
            continue
        result.add_direct_item(item_id, 1)
        normalized_raw = _STATE_DEFINITIONS_RE.sub("", raw.lstrip("*"))
        if normalized_raw != item_id and not normalized_raw.endswith(_HOLLOW_SUFFIX):
            state_variants.add(raw)
    if state_variants:
        sample = sorted(state_variants)[:8]
        extra = f" (+{len(state_variants) - len(sample)} more)" if len(state_variants) > len(sample) else ""
        print(f"  normalized {len(state_variants)} state/variant names, e.g. {sample}{extra}")
    if hollow_merged:
        print(f"  merged {hollow_merged} *_Hollow cells into base block ids")
    if filler_skipped:
        print(f"  skipped {filler_skipped} multi-block filler cells (filler != 0)")
    if converted_blocks:
        print(f"  applied conversions to {converted_blocks} prefab block instance(s)")
    result.items = finalize_item_counts(result.items)
    return result


def finalize_item_counts(items: Counter[str]) -> Counter[str]:
    """Large chests are unobtainable; require two matching small chests instead (mirrors PrefabMaterialItemIds)."""
    out: Counter[str] = Counter()
    for item_id, count in items.items():
        if item_id.endswith("_Chest_Large"):
            small = item_id[: -len("_Chest_Large")] + "_Chest_Small"
            out[small] += count * 2
        else:
            out[item_id] += count
    return out


def material_counts_to_json(counts: MaterialCounts) -> list[dict[str, object]]:
    entries: list[tuple[str, str, int]] = []
    for item_id, c in counts.items.items():
        entries.append(("item", item_id, c))
    for rt, c in counts.resources.items():
        entries.append(("resource", rt, c))
    entries.sort(key=lambda e: (-e[2], e[0], e[1]))
    out: list[dict[str, object]] = []
    for kind, id_val, c in entries:
        if kind == "resource":
            out.append({"resourceTypeId": id_val, "count": c})
        else:
            out.append({"itemId": id_val, "count": c})
    return out


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--dry-run", action="store_true", help="Print what would be written without writing files")
    p.add_argument("--construction", type=str, help="Only process this construction id (e.g. plot_farm)")
    args = p.parse_args()

    conversions = load_conversions()
    if not BUILDINGS_DIR.is_dir():
        print(f"Buildings dir missing: {BUILDINGS_DIR}", file=sys.stderr)
        return 1

    building_files = sorted(BUILDINGS_DIR.glob("plot_*.json"))
    if args.construction:
        building_files = [f for f in building_files if f.stem == args.construction]
        if not building_files:
            print(f"No building JSON for id {args.construction}", file=sys.stderr)
            return 1

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    written = 0
    for bf in building_files:
        if bf.parent.name == "PrefabMaterials":
            continue
        try:
            bdef = json.loads(bf.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as e:
            print(f"Skip {bf.name}: {e}", file=sys.stderr)
            continue
        cid = bdef.get("id") or bf.stem
        prefab_name = bdef.get("prefabPath")
        if not isinstance(prefab_name, str) or not prefab_name.strip():
            print(f"Skip {cid}: no prefabPath", file=sys.stderr)
            continue
        prefab_path = PREFABS_DIR / prefab_name.strip()
        if not prefab_path.is_file():
            print(f"Skip {cid}: prefab not found {prefab_path}", file=sys.stderr)
            continue
        print(f"{cid}:")
        try:
            counts = count_prefab_blocks(prefab_path, conversions)
        except (OSError, ValueError, json.JSONDecodeError) as e:
            print(f"Skip {cid}: {e}", file=sys.stderr)
            continue
        materials = material_counts_to_json(counts)
        out = {
            "constructionId": cid,
            "prefabPath": prefab_name.strip(),
            "materials": materials,
        }
        out_path = OUT_DIR / f"{cid}.json"
        if args.dry_run:
            print(f"  would write {out_path} ({len(materials)} material lines)")
        else:
            out_path.write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
            print(f"  wrote {out_path} ({len(materials)} material lines)")
        written += 1

    print(f"Done: {written} file(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
