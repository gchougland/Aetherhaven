from __future__ import annotations

import json
from pathlib import Path

from quest_board_editor.item_catalog import merge_catalogs, scan_root


def test_scan_root_parses_item_with_icon(tmp_path: Path):
    item_dir = tmp_path / "Server" / "Item" / "Items" / "Test"
    item_dir.mkdir(parents=True)
    (item_dir / "Rock_Stone.json").write_text(
        json.dumps(
            {
                "Id": "Rock_Stone",
                "Icon": "Items/Rock/Rock_Stone.png",
                "Categories": ["Material"],
                "TranslationProperties": {"Name": "Stone"},
            }
        ),
        encoding="utf-8",
    )
    cat = scan_root(tmp_path)
    assert "Rock_Stone" in cat
    assert cat["Rock_Stone"].translation_name == "Stone"
    assert "Material" in cat["Rock_Stone"].categories


def test_merge_catalogs_later_overrides(tmp_path: Path):
    a = tmp_path / "a"
    b = tmp_path / "b"
    for root, name in ((a, "First"), (b, "Second")):
        d = root / "Server" / "Item" / "Items"
        d.mkdir(parents=True)
        (d / "Shared.json").write_text(
            json.dumps(
                {
                    "Id": "Shared",
                    "Icon": "x.png",
                    "TranslationProperties": {"Name": name},
                }
            ),
            encoding="utf-8",
        )
    merged = merge_catalogs([a, b])
    assert merged["Shared"].translation_name == "Second"
