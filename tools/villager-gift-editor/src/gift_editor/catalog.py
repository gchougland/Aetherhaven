from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterator, List, Optional, Set, Tuple


ITEM_JSON_GLOB = "**/Server/Item/**/*.json"


@dataclass(frozen=True)
class ItemRecord:
    item_id: str
    json_path: Path = field(compare=False, repr=False)
    categories: Tuple[str, ...] = ()
    icon_relative: Optional[str] = None
    translation_name: Optional[str] = None

    def resolved_icon_path(self, root: Path) -> Optional[Path]:
        if not self.icon_relative:
            return None
        rel = self.icon_relative.replace("\\", "/").strip()
        if not rel:
            return None
        p = Path(rel)
        if p.is_absolute():
            return p if p.is_file() else None
        root = root.resolve()
        candidates = [
            root / "Common" / rel,
            root / rel,
        ]
        for c in candidates:
            if c.is_file():
                return c
        return None


def _parse_item_json(path: Path) -> Optional[ItemRecord]:
    stem = path.stem
    if not stem:
        return None
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return None
    if not isinstance(raw, dict):
        return None
    # Skip non-item JSON under Item/ (e.g. interactions without Icon).
    icon = raw.get("Icon")
    if not isinstance(icon, str) or not icon.strip():
        return None
    cats_raw = raw.get("Categories")
    categories: Tuple[str, ...] = ()
    if isinstance(cats_raw, list):
        categories = tuple(
            str(x) for x in cats_raw if isinstance(x, str) and x.strip()
        )
    trans = raw.get("TranslationProperties")
    name_hint: Optional[str] = None
    if isinstance(trans, dict):
        n = trans.get("Name")
        if isinstance(n, str) and n.strip():
            name_hint = n.strip()
    rid = raw.get("Id")
    item_id = stem
    if isinstance(rid, str) and rid.strip():
        item_id = rid.strip()
    return ItemRecord(
        item_id=item_id,
        json_path=path,
        categories=categories,
        icon_relative=icon.strip(),
        translation_name=name_hint,
    )


def iter_item_json_files(root: Path) -> Iterator[Path]:
    root = root.resolve()
    if not root.is_dir():
        return
    # Restrict to .../Items/... when possible to skip RootInteractions noise.
    for p in root.glob(ITEM_JSON_GLOB):
        if not p.is_file():
            continue
        parts = {x.lower() for x in p.parts}
        if "items" not in parts:
            continue
        yield p


def scan_root(root: Path) -> Dict[str, ItemRecord]:
    out: Dict[str, ItemRecord] = {}
    for path in iter_item_json_files(root):
        rec = _parse_item_json(path)
        if rec is None:
            continue
        out[rec.item_id] = rec
    return out


def merge_catalogs(roots: List[Path]) -> Dict[str, ItemRecord]:
    """Later roots override earlier roots for the same item_id."""
    merged: Dict[str, ItemRecord] = {}
    for r in roots:
        layer = scan_root(r)
        merged.update(layer)
    return merged


def all_categories(catalog: Dict[str, ItemRecord]) -> List[str]:
    s: Set[str] = set()
    for rec in catalog.values():
        s.update(rec.categories)
    return sorted(s)


def pick_icon_path_for_item(
    record: ItemRecord, roots: List[Path]
) -> Optional[Path]:
    """First matching file among roots (later roots first for tie-break)."""
    for root in reversed(roots):
        hit = record.resolved_icon_path(root)
        if hit is not None:
            return hit
    return None
