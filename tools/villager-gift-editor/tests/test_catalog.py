from __future__ import annotations

import json
from pathlib import Path

from gift_editor.catalog import merge_catalogs, pick_icon_path_for_item


def _write_item(tmp: Path, name: str, icon: str, item_id_override: str | None = None) -> None:
    p = tmp / "Server" / "Item" / "Items" / "Test" / f"{name}.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    data: dict = {
        "Icon": icon,
        "Categories": ["Items.Food"],
    }
    if item_id_override:
        data["Id"] = item_id_override
    p.write_text(json.dumps(data), encoding="utf-8")


def test_merge_later_root_overrides(tmp_path: Path) -> None:
    early = tmp_path / "early"
    late = tmp_path / "late"
    _write_item(early, "Apple", "Icons/a.png")
    _write_item(late, "Apple", "Icons/b.png")
    merged = merge_catalogs([early, late])
    assert len(merged) == 1
    assert merged["Apple"].icon_relative == "Icons/b.png"


def test_pick_icon_later_root_first(tmp_path: Path) -> None:
    early = tmp_path / "early"
    late = tmp_path / "late"
    _write_item(early, "Berry", "Icons/x.png")
    rec = merge_catalogs([early])["Berry"]
    ic = tmp_path / "late" / "Common" / "Icons" / "x.png"
    ic.parent.mkdir(parents=True, exist_ok=True)
    ic.write_bytes(b"\x89PNG\r\n\x1a\n")
    path = pick_icon_path_for_item(rec, [early, late])
    assert path is not None
    assert path.resolve() == ic.resolve()
