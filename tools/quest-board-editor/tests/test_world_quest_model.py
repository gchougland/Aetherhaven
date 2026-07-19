from __future__ import annotations

import copy

from quest_board_editor.config import detect_board_kind, default_quest_board_path
from quest_board_editor.world_lang_keys import (
    json_key_to_lang_key,
    world_quest_title_lang_key,
)
from quest_board_editor.world_quest_model import (
    WorldQuestBoardDocument,
    WorldQuestFilter,
    filter_world_quests,
    make_world_template,
    validate_world_document,
)

SAMPLE_WORLD = {
    "schemaVersion": 1,
    "profileId": "hub_default",
    "slotCount": 3,
    "ranks": [
        {"id": "E", "xpRequired": 0, "xpReward": 5},
        {"id": "D", "xpRequired": 30, "xpReward": 8},
    ],
    "pool": [
        {
            "id": "hub_welcome_supplies",
            "weight": 10,
            "titleLangKey": world_quest_title_lang_key("hub_welcome_supplies"),
            "descriptionLangKey": (
                "aetherhaven_world_quest_board.aetherhaven.worldQuestBoard."
                "hub_welcome_supplies.description"
            ),
            "minRank": "E",
            "maxRank": "D",
            "rankXpReward": 10,
            "daysLimit": 3,
            "questType": "fetch",
            "rewards": [{"kind": "item", "itemId": "Aetherhaven_Gold_Coin", "count": 5}],
        },
        {
            "id": "hub_scout_roads",
            "weight": 8,
            "titleLangKey": world_quest_title_lang_key("hub_scout_roads"),
            "descriptionLangKey": (
                "aetherhaven_world_quest_board.aetherhaven.worldQuestBoard."
                "hub_scout_roads.description"
            ),
            "minRank": "D",
            "maxRank": "D",
            "rankXpReward": 12,
            "daysLimit": 2,
            "questType": "hunt",
            "rewards": [{"kind": "item", "itemId": "Aetherhaven_Gold_Coin", "count": 8}],
        },
    ],
}


def test_detect_board_kind():
    assert detect_board_kind(SAMPLE_WORLD) == "world"
    assert detect_board_kind({"villagers": {}, "ranks": []}) == "town"
    assert detect_board_kind({"pool": [], "profileId": "x"}) == "world"


def test_world_flatten_and_filter():
    doc = WorldQuestBoardDocument(copy.deepcopy(SAMPLE_WORLD))
    refs = doc.flatten()
    assert len(refs) == 2
    out = filter_world_quests(refs, WorldQuestFilter(quest_type="hunt"), lambda k, d="": d)
    assert len(out) == 1
    assert out[0].quest_id == "hub_scout_roads"
    out2 = filter_world_quests(refs, WorldQuestFilter(rank="E"), lambda k, d="": d)
    assert {r.quest_id for r in out2} == {"hub_welcome_supplies"}


def test_make_world_template():
    entry = make_world_template("my_quest", "raid")
    assert entry["id"] == "my_quest"
    assert entry["questType"] == "raid"
    assert "titleLangKey" in entry
    assert entry["titleLangKey"].startswith("aetherhaven_world_quest_board.")


def test_world_lang_key_strip():
    key = world_quest_title_lang_key("hub_welcome_supplies")
    assert json_key_to_lang_key(key) == "aetherhaven.worldQuestBoard.hub_welcome_supplies.title"


def test_validate_world_document():
    doc = WorldQuestBoardDocument(copy.deepcopy(SAMPLE_WORLD))
    texts = {
        "aetherhaven.worldQuestBoard.hub_welcome_supplies.title": "Welcome",
        "aetherhaven.worldQuestBoard.hub_welcome_supplies.description": "Desc",
        "aetherhaven.worldQuestBoard.hub_scout_roads.title": "Scout",
        "aetherhaven.worldQuestBoard.hub_scout_roads.description": "Desc2",
    }

    def getter(k, d=""):
        return texts.get(k, d)

    errors = validate_world_document(doc, getter)
    assert errors == []


def test_default_quest_board_path_points_at_subplugin():
    path = default_quest_board_path()
    assert path.name == "quest_board.json"
    # Prefer subplugin-assets when present in this repo.
    if path.is_file():
        assert "subplugin-assets" in str(path).replace("\\", "/") or path.is_file()
