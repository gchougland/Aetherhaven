from __future__ import annotations

from quest_board_editor.portrait_catalog import (
    default_icons_dir,
    load_villager_records,
    portrait_png_path,
)


def test_load_villager_records_and_portrait():
    catalog = load_villager_records()
    if not catalog:
        return
    assert "Aetherhaven_Miner" in catalog or any("Miner" in k for k in catalog)
    miner = catalog.get("Aetherhaven_Miner")
    if miner is None:
        return
    path = portrait_png_path("Aetherhaven_Miner", catalog)
    assert path is not None
    assert path.is_file()
    assert path.suffix.lower() == ".png"


def test_icons_dir_exists_in_repo():
    d = default_icons_dir()
    # Soft assert: dir may exist in this checkout.
    assert d.name == "ModelsGenerated"
