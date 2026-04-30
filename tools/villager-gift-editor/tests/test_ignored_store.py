from __future__ import annotations

import json
from pathlib import Path

from gift_editor.ignored_items_store import (
    load_ignored_ids,
    save_ignored_ids,
    toggle_ignored_id,
)


def test_save_roundtrip(tmp_path: Path, monkeypatch) -> None:
    p = tmp_path / "gift_editor_ignored_items.json"
    monkeypatch.setattr(
        "gift_editor.ignored_items_store.ignored_items_file_path", lambda: p
    )
    save_ignored_ids({"A", "B"})
    assert load_ignored_ids() == {"A", "B"}


def test_load_legacy_list_format(tmp_path: Path, monkeypatch) -> None:
    p = tmp_path / "x.json"
    p.write_text(json.dumps(["X", "Y"]), encoding="utf-8")
    monkeypatch.setattr(
        "gift_editor.ignored_items_store.ignored_items_file_path", lambda: p
    )
    assert load_ignored_ids() == {"X", "Y"}


def test_toggle_removes(tmp_path: Path, monkeypatch) -> None:
    p = tmp_path / "gift_editor_ignored_items.json"
    monkeypatch.setattr(
        "gift_editor.ignored_items_store.ignored_items_file_path", lambda: p
    )
    s = {"Z"}
    assert toggle_ignored_id(s, "Z") is False
    assert s == set()
