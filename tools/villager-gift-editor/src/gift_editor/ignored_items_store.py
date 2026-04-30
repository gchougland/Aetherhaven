from __future__ import annotations

import json
from pathlib import Path
from typing import Set


def ignored_items_file_path() -> Path:
    """Next to `gift_editor_config.json`: `gift_editor_ignored_items.json`."""
    return Path(__file__).resolve().parents[2] / "gift_editor_ignored_items.json"


def load_ignored_ids() -> Set[str]:
    path = ignored_items_file_path()
    if not path.is_file():
        return set()
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return set()
    if isinstance(raw, list):
        return {str(x).strip() for x in raw if isinstance(x, str) and str(x).strip()}
    if isinstance(raw, dict):
        arr = raw.get("itemIds") or raw.get("ids")
        if isinstance(arr, list):
            return {str(x).strip() for x in arr if isinstance(x, str) and str(x).strip()}
    return set()


def save_ignored_ids(ids: Set[str]) -> None:
    path = ignored_items_file_path()
    ordered = sorted(ids, key=str.lower)
    data = {"itemIds": ordered}
    path.write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def toggle_ignored_id(ids: Set[str], item_id: str) -> bool:
    """Add or remove `item_id`. Returns True if now ignored, False if now visible."""
    if item_id in ids:
        ids.discard(item_id)
        return False
    ids.add(item_id)
    return True
