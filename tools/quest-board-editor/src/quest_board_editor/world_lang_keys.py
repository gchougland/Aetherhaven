from __future__ import annotations

LANG_KEY_PREFIX = "aetherhaven_world_quest_board."


def json_key_to_lang_key(json_key: str) -> str:
    if json_key.startswith(LANG_KEY_PREFIX):
        return json_key[len(LANG_KEY_PREFIX) :]
    return json_key


def lang_key_to_json_key(lang_key: str) -> str:
    if lang_key.startswith(LANG_KEY_PREFIX):
        return lang_key
    return LANG_KEY_PREFIX + lang_key


def world_quest_title_lang_key(quest_id: str) -> str:
    return lang_key_to_json_key(f"aetherhaven.worldQuestBoard.{quest_id}.title")


def world_quest_description_lang_key(quest_id: str) -> str:
    return lang_key_to_json_key(f"aetherhaven.worldQuestBoard.{quest_id}.description")
