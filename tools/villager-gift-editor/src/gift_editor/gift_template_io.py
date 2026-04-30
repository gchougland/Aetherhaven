from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, List, Tuple

from .villager_io import extract_gift_lists

TEMPLATE_SCHEMA_VERSION = 1


def save_gift_template(
    path: Path,
    loves: List[str],
    likes: List[str],
    dislikes: List[str],
) -> None:
    """Write gift lists JSON (same keys as villager definitions)."""
    data: Dict[str, Any] = {
        "schemaVersion": TEMPLATE_SCHEMA_VERSION,
        "giftLoves": list(loves),
        "giftLikes": list(likes),
        "giftDislikes": list(dislikes),
    }
    path.write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def load_gift_template(path: Path) -> Tuple[List[str], List[str], List[str]]:
    """Load gift lists from a template file or any villager-style JSON containing the three keys."""
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError("Template must be a JSON object")
    return extract_gift_lists(raw)
