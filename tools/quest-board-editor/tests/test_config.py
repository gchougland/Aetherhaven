from __future__ import annotations

from quest_board_editor.config import (
    default_item_roots,
    default_quest_board_path,
    default_world_board_path,
    default_world_lang_path,
)


def test_default_paths_resolve():
    qb = default_quest_board_path()
    assert qb.name == "quest_board.json"
    wb = default_world_board_path()
    assert wb.name.endswith(".json")
    wl = default_world_lang_path()
    assert "world_quest_board" in wl.name
    roots = default_item_roots()
    assert isinstance(roots, list)
