from __future__ import annotations

LANG_KEY_PREFIX = "aetherhaven_quest_board."

VILLAGER_SLUGS: dict[str, str] = {
    "Aetherhaven_Miner": "miner",
    "Aetherhaven_Farmer": "farmer",
    "Aetherhaven_Blacksmith": "blacksmith",
    "Aetherhaven_Logger": "logger",
    "Aetherhaven_Rancher": "rancher",
    "Aetherhaven_Priestess": "priestess",
    "Aetherhaven_Innkeeper": "innkeeper",
    "Aetherhaven_Elder_Lyren": "elder",
    "Aetherhaven_Guild_Master": "guild_master",
    "Aetherhaven_Builder": "builder",
    "Aetherhaven_Chef": "chef",
}


def villager_slug(villager_id: str) -> str:
    hit = VILLAGER_SLUGS.get(villager_id)
    if hit:
        return hit
    if villager_id.startswith("Aetherhaven_"):
        return villager_id[len("Aetherhaven_") :].lower()
    return villager_id.lower()


def json_key_to_lang_key(json_key: str) -> str:
    if json_key.startswith(LANG_KEY_PREFIX):
        return json_key[len(LANG_KEY_PREFIX) :]
    return json_key


def lang_key_to_json_key(lang_key: str) -> str:
    if lang_key.startswith(LANG_KEY_PREFIX):
        return lang_key
    return LANG_KEY_PREFIX + lang_key


def quest_title_lang_key(villager_id: str, quest_id: str) -> str:
    slug = villager_slug(villager_id)
    return lang_key_to_json_key(f"aetherhaven.questBoard.{slug}.{quest_id}.title")


def quest_description_lang_key(villager_id: str, quest_id: str) -> str:
    slug = villager_slug(villager_id)
    return lang_key_to_json_key(f"aetherhaven.questBoard.{slug}.{quest_id}.description")


def target_label_lang_key(target_slug: str) -> str:
    return lang_key_to_json_key(f"aetherhaven.questBoard.targets.{target_slug}")
