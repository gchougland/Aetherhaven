from __future__ import annotations

import copy
import tempfile
from pathlib import Path

from quest_board_editor.io_json import load_quest_board, save_quest_board
from quest_board_editor.lang_keys import json_key_to_lang_key, quest_description_lang_key, quest_title_lang_key
from quest_board_editor.quest_model import QuestBoardDocument, QuestFilter, filter_quests, regenerate_entry_lang_keys


SAMPLE_BOARD = {
    "schemaVersion": 1,
    "slotCount": 3,
    "ranks": [
        {"id": "E", "xpRequired": 0, "xpReward": 5, "icon": "UI/Custom/e.png"},
        {"id": "D", "xpRequired": 30, "xpReward": 8, "icon": "UI/Custom/d.png"},
    ],
    "questTypes": {"fetch": {"weight": 65}, "hunt": {"weight": 45}, "raid": {"weight": 20}},
    "villagers": {
        "Aetherhaven_Miner": {
            "fetchEntries": [
                {
                    "id": "stone_haul",
                    "rank": "E",
                    "minRank": "E",
                    "maxRank": "D",
                    "weight": 12,
                    "daysLimit": 3,
                    "titleLangKey": "aetherhaven_quest_board.aetherhaven.questBoard.miner.stone_haul.title",
                    "descriptionLangKey": "aetherhaven_quest_board.aetherhaven.questBoard.miner.stone_haul.description",
                    "itemSets": [{"weight": 1, "items": [{"itemId": "Rock_Stone", "count": 24}]}],
                    "rewards": [{"kind": "item", "itemId": "Aetherhaven_Gold_Coin", "count": 8, "grantTo": "player"}],
                },
                {
                    "id": "copper_ore",
                    "rank": "D",
                    "minRank": "D",
                    "maxRank": "D",
                    "weight": 10,
                    "daysLimit": 4,
                    "titleLangKey": "aetherhaven_quest_board.aetherhaven.questBoard.miner.copper_ore.title",
                    "descriptionLangKey": "aetherhaven_quest_board.aetherhaven.questBoard.miner.copper_ore.description",
                    "itemSets": [{"weight": 1, "items": [{"itemId": "Ore_Copper", "count": 16}]}],
                    "rewards": [{"kind": "item", "itemId": "Aetherhaven_Gold_Coin", "count": 14, "grantTo": "player"}],
                },
            ],
        },
        "Aetherhaven_Priestess": {
            "huntEntries": [
                {
                    "id": "cull_undead",
                    "rank": "E",
                    "minRank": "E",
                    "maxRank": "D",
                    "weight": 10,
                    "daysLimit": 4,
                    "titleLangKey": "aetherhaven_quest_board.aetherhaven.questBoard.priestess.cull_undead.title",
                    "descriptionLangKey": "aetherhaven_quest_board.aetherhaven.questBoard.priestess.cull_undead.description",
                    "targetLabelLangKey": "aetherhaven_quest_board.aetherhaven.questBoard.targets.undead",
                    "killSets": [{"weight": 1, "killCount": 5, "entityTagsAny": ["Undead"]}],
                    "rewards": [{"kind": "item", "itemId": "Aetherhaven_Gold_Coin", "count": 8, "grantTo": "player"}],
                },
            ],
            "raidEntries": [
                {
                    "id": "undead_raid",
                    "rank": "D",
                    "minRank": "D",
                    "maxRank": "D",
                    "weight": 8,
                    "daysLimit": 5,
                    "titleLangKey": "aetherhaven_quest_board.aetherhaven.questBoard.priestess.undead_raid.title",
                    "descriptionLangKey": "aetherhaven_quest_board.aetherhaven.questBoard.priestess.undead_raid.description",
                    "targetLabelLangKey": "aetherhaven_quest_board.aetherhaven.questBoard.targets.undead",
                    "raidSets": [
                        {
                            "weight": 1,
                            "mobCountsByRank": {"D": 6},
                            "mobPool": [{"roleId": "Skeleton_Soldier_Wander", "weight": 3}],
                        }
                    ],
                    "rewards": [{"kind": "item", "itemId": "Aetherhaven_Gold_Coin", "count": 18, "grantTo": "player"}],
                },
            ],
        },
    },
}


def _repo_quest_board() -> Path:
    here = Path(__file__).resolve()
    root = here.parents[3]
    sub = (
        root
        / "subplugin-assets"
        / "Quests"
        / "Server"
        / "Aetherhaven"
        / "quest_board.json"
    )
    if sub.is_file():
        return sub
    return root / "src" / "main" / "resources" / "Server" / "Aetherhaven" / "quest_board.json"


def test_flatten_finds_all_entry_types():
    doc = QuestBoardDocument(copy.deepcopy(SAMPLE_BOARD))
    refs = doc.flatten()
    by_type = {r.quest_type for r in refs}
    assert by_type == {"fetch", "hunt", "raid"}
    ids = {r.quest_id for r in refs}
    assert ids == {"stone_haul", "copper_ore", "cull_undead", "undead_raid"}


def test_round_trip_json():
    doc = QuestBoardDocument(copy.deepcopy(SAMPLE_BOARD))
    count = len(doc.flatten())

    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as f:
        tmp = Path(f.name)
    try:
        save_quest_board(tmp, doc.data)
        reloaded = load_quest_board(tmp)
        assert len(QuestBoardDocument(reloaded).flatten()) == count
    finally:
        tmp.unlink(missing_ok=True)


def test_filter_by_rank():
    doc = QuestBoardDocument(copy.deepcopy(SAMPLE_BOARD))
    refs = doc.flatten()
    out = filter_quests(refs, QuestFilter(rank="E"), lambda k, d="": d)
    assert {r.quest_id for r in out} == {"stone_haul", "cull_undead"}


def test_filter_by_villager_and_type():
    doc = QuestBoardDocument(copy.deepcopy(SAMPLE_BOARD))
    refs = doc.flatten()
    out = filter_quests(
        refs,
        QuestFilter(villager_id="Aetherhaven_Priestess", quest_type="raid"),
        lambda k, d="": d,
    )
    assert len(out) == 1
    assert out[0].quest_id == "undead_raid"


def test_lang_key_generation():
    key = quest_title_lang_key("Aetherhaven_Miner", "stone_haul")
    assert key.startswith("aetherhaven_quest_board.")
    assert json_key_to_lang_key(key) == "aetherhaven.questBoard.miner.stone_haul.title"


def test_regenerate_entry_lang_keys():
    entry = {
        "id": "old_id",
        "titleLangKey": quest_title_lang_key("Aetherhaven_Miner", "old_id"),
        "descriptionLangKey": quest_description_lang_key("Aetherhaven_Miner", "old_id"),
    }
    pending, stale = regenerate_entry_lang_keys(
        entry,
        "Aetherhaven_Miner",
        "new_id",
        "fetch",
        title_text="My title",
        desc_text="My description",
    )
    assert entry["titleLangKey"] == quest_title_lang_key("Aetherhaven_Miner", "new_id")
    assert entry["descriptionLangKey"] == quest_description_lang_key("Aetherhaven_Miner", "new_id")
    assert pending[entry["titleLangKey"]] == "My title"
    assert pending[entry["descriptionLangKey"]] == "My description"
    assert len(stale) == 2
    assert quest_title_lang_key("Aetherhaven_Miner", "old_id") in stale


def test_repo_quest_board_loads():
    """Smoke test: real data file parses and every entry has an id."""
    path = _repo_quest_board()
    if not path.is_file():
        return
    doc = QuestBoardDocument(load_quest_board(path))
    refs = doc.flatten()
    assert all(r.quest_id for r in refs)
    assert all(r.quest_type in ("fetch", "hunt", "raid") for r in refs)
