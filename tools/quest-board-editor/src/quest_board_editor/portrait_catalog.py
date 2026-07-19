from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional

from .config import _repo_root_from_here, default_villagers_dir

ICON_PREFIX = "Icons/ModelsGenerated/"

# Mirrors NpcPortraitProvider.ROLE_ID_TO_FILE (subset for offline fallback).
ROLE_FALLBACK: Dict[str, str] = {
    "Aetherhaven_Elder_Lyren": "Aetherhaven_Elder_Lyren.png",
    "Aetherhaven_Innkeeper": "Aetherhaven_Innkeeper.png",
    "Aetherhaven_Merchant": "Aetherhaven_Merchant.png",
    "Aetherhaven_Blacksmith": "Aetherhaven_Blacksmith.png",
    "Aetherhaven_Farmer": "Aetherhaven_Farmer.png",
    "Aetherhaven_Priestess": "Aetherhaven_Priestess.png",
    "Aetherhaven_Miner": "Aetherhaven_Miner.png",
    "Aetherhaven_Logger": "Aetherhaven_Logger.png",
    "Aetherhaven_Rancher": "Aetherhaven_Rancher.png",
    "Aetherhaven_Crystal_Keeper": "Aetherhaven_Crystal_Keeper.png",
    "Aetherhaven_Pyrotechnic": "Aetherhaven_Pyrotechnic.png",
    "Aetherhaven_Florist": "Aetherhaven_Florist.png",
    "Aetherhaven_Builder": "Aetherhaven_Builder.png",
    "Aetherhaven_Guild_Master": "Aetherhaven_Guild_Master.png",
    "Aetherhaven_Chef": "Aetherhaven_Chef.png",
}


@dataclass(frozen=True)
class VillagerRecord:
    npc_role_id: str
    display_name: str
    portrait_icon: str
    json_path: Path


def default_icons_dir() -> Path:
    root = _repo_root_from_here()
    p = root / "src" / "main" / "resources" / "Common" / "Icons" / "ModelsGenerated"
    if p.is_dir():
        return p
    for anc in Path(__file__).resolve().parents:
        q = anc / "src" / "main" / "resources" / "Common" / "Icons" / "ModelsGenerated"
        if q.is_dir():
            return q
    return p


def load_villager_records(villagers_dir: Path | None = None) -> Dict[str, VillagerRecord]:
    root = villagers_dir or default_villagers_dir()
    out: Dict[str, VillagerRecord] = {}
    if not root.is_dir():
        return out
    for path in sorted(root.glob("*.json")):
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            continue
        if not isinstance(raw, dict):
            continue
        rid = str(raw.get("npcRoleId") or path.stem).strip()
        if not rid:
            continue
        out[rid] = VillagerRecord(
            npc_role_id=rid,
            display_name=str(raw.get("displayName") or rid).strip(),
            portrait_icon=str(raw.get("portraitIcon") or "").strip(),
            json_path=path,
        )
    return out


def canonical_role_id(role_id: str, catalog: Dict[str, VillagerRecord]) -> str:
    r = role_id.strip()
    if r.endswith("_Rescue"):
        base = r[: -len("_Rescue")]
        if base in catalog or base in ROLE_FALLBACK:
            return base
    return r


def portrait_filename(role_id: str, catalog: Dict[str, VillagerRecord]) -> Optional[str]:
    r = canonical_role_id(role_id, catalog)
    rec = catalog.get(r)
    if rec and rec.portrait_icon:
        icon = rec.portrait_icon
        if icon.startswith("UI/") or icon.startswith("Icons/"):
            if icon.startswith(ICON_PREFIX):
                return icon[len(ICON_PREFIX) :]
            return None
        return icon
    return ROLE_FALLBACK.get(r)


def portrait_png_path(
    role_id: str,
    catalog: Dict[str, VillagerRecord],
    icons_dir: Path | None = None,
) -> Optional[Path]:
    filename = portrait_filename(role_id, catalog)
    if not filename:
        return None
    root = icons_dir or default_icons_dir()
    p = root / filename
    return p if p.is_file() else None


def display_label(role_id: str, catalog: Dict[str, VillagerRecord]) -> str:
    rec = catalog.get(canonical_role_id(role_id, catalog))
    if rec and rec.display_name and rec.display_name != role_id:
        return f"{rec.display_name} ({role_id})"
    return role_id


def all_role_ids(catalog: Dict[str, VillagerRecord]) -> List[str]:
    return sorted(catalog.keys())
