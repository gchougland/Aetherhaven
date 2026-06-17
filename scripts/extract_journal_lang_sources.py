#!/usr/bin/env python3
"""
One-time / idempotent extractor: story quest strings and building display names
into en-US lang bundles, and wire lang keys into source JSON.

Run from repo root:
  python scripts/extract_journal_lang_sources.py
"""
from __future__ import annotations

import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
QUESTS_DIR = REPO / "src/main/resources/Server/Aetherhaven/Quests"
BUILDINGS_DIR = REPO / "src/main/resources/Server/Aetherhaven/Buildings"
LANG_DIR = REPO / "src/main/resources/Server/Languages/en-US"

QUEST_BUNDLE = "aetherhaven_story_quests"
BUILDING_BUNDLE = "aetherhaven_buildings"


def esc_lang(s: str) -> str:
    return s.replace("\\", "\\\\").replace("\n", "\\n")


def quest_key(quest_id: str, suffix: str) -> str:
    return f"aetherhaven.storyQuests.{quest_id}.{suffix}"


def building_key(construction_id: str) -> str:
    safe = re.sub(r"[^a-zA-Z0-9_]", "_", construction_id)
    return f"aetherhaven.buildings.{safe}.displayName"


def full_quest_lang_key(quest_id: str, suffix: str) -> str:
    return f"{QUEST_BUNDLE}.{quest_key(quest_id, suffix)}"


def full_building_lang_key(construction_id: str) -> str:
    return f"{BUILDING_BUNDLE}.{building_key(construction_id)}"


def extract_quests() -> list[str]:
    lines: list[str] = [
        "# Story quest titles, descriptions, and journal objective lines (en-US source).",
        f"{quest_key('_meta', 'noDescription')}=No description for this quest yet.",
        "",
    ]
    for path in sorted(QUESTS_DIR.rglob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        qid = (data.get("id") or path.stem).strip()
        if not qid:
            continue
        title = (data.get("title") or "").strip()
        desc = (data.get("description") or "").strip()
        if title:
            lines.append(f"{quest_key(qid, 'title')}={esc_lang(title)}")
            data["titleLangKey"] = full_quest_lang_key(qid, "title")
        if desc:
            lines.append(f"{quest_key(qid, 'description')}={esc_lang(desc)}")
            data["descriptionLangKey"] = full_quest_lang_key(qid, "description")
        objectives = data.get("objectives") or []
        for i, obj in enumerate(objectives):
            if not isinstance(obj, dict):
                continue
            text = (obj.get("text") or "").strip()
            oid = (obj.get("id") or f"step{i}").strip()
            if text:
                sk = f"objective.{oid}"
                lines.append(f"{quest_key(qid, sk)}={esc_lang(text)}")
                obj["textLangKey"] = full_quest_lang_key(qid, sk)
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return lines


def extract_buildings() -> list[str]:
    lines: list[str] = [
        "# Building display names for town journal plot list (en-US source).",
        "",
    ]
    for path in sorted(BUILDINGS_DIR.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        cid = (data.get("id") or path.stem).strip()
        name = (data.get("displayName") or "").strip()
        if not cid or not name:
            continue
        lines.append(f"{building_key(cid)}={esc_lang(name)}")
        data["displayNameLangKey"] = full_building_lang_key(cid)
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return lines


def main() -> None:
    LANG_DIR.mkdir(parents=True, exist_ok=True)
    quest_lines = extract_quests()
    (LANG_DIR / f"{QUEST_BUNDLE}.lang").write_text("\n".join(quest_lines) + "\n", encoding="utf-8")
    building_lines = extract_buildings()
    (LANG_DIR / f"{BUILDING_BUNDLE}.lang").write_text("\n".join(building_lines) + "\n", encoding="utf-8")
    print(f"Wrote {LANG_DIR / QUEST_BUNDLE}.lang ({len(quest_lines)} lines)")
    print(f"Wrote {LANG_DIR / BUILDING_BUNDLE}.lang ({len(building_lines)} lines)")


if __name__ == "__main__":
    main()
