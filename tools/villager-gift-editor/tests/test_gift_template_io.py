from __future__ import annotations

import json
from pathlib import Path

from gift_editor.gift_template_io import load_gift_template, save_gift_template


def test_save_load_roundtrip(tmp_path: Path) -> None:
    p = tmp_path / "t.json"
    save_gift_template(p, ["A"], ["B"], ["C"])
    loves, likes, dislikes = load_gift_template(p)
    assert loves == ["A"] and likes == ["B"] and dislikes == ["C"]


def test_load_from_minimal_villager_shape(tmp_path: Path) -> None:
    p = tmp_path / "v.json"
    p.write_text(
        json.dumps(
            {
                "npcRoleId": "X",
                "giftLoves": ["L"],
                "giftLikes": [],
                "giftDislikes": ["D"],
            }
        ),
        encoding="utf-8",
    )
    assert load_gift_template(p) == (["L"], [], ["D"])
