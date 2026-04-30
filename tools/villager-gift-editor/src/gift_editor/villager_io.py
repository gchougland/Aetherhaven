from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, List, Tuple


def load_villager(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def save_villager(path: Path, data: Dict[str, Any]) -> None:
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    path.write_text(text, encoding="utf-8")


def extract_gift_lists(data: Dict[str, Any]) -> Tuple[List[str], List[str], List[str]]:
    def as_str_list(key: str) -> List[str]:
        v = data.get(key)
        if not isinstance(v, list):
            return []
        out: List[str] = []
        for x in v:
            if isinstance(x, str) and x.strip():
                out.append(x.strip())
        return out

    return (
        as_str_list("giftLoves"),
        as_str_list("giftLikes"),
        as_str_list("giftDislikes"),
    )


def ensure_gift_keys_on_data(
    data: Dict[str, Any],
    loves: List[str],
    likes: List[str],
    dislikes: List[str],
) -> None:
    data["giftLoves"] = loves
    data["giftLikes"] = likes
    data["giftDislikes"] = dislikes


def apply_gifts_to_data(
    data: Dict[str, Any],
    loves: List[str],
    likes: List[str],
    dislikes: List[str],
) -> None:
    ensure_gift_keys_on_data(data, loves, likes, dislikes)
