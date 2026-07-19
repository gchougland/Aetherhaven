from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple

from .world_lang_keys import (
    json_key_to_lang_key,
    world_quest_description_lang_key,
    world_quest_title_lang_key,
)

QUEST_TYPES = ("fetch", "hunt", "raid")


@dataclass
class WorldQuestRef:
    index: int
    entry: dict

    @property
    def quest_id(self) -> str:
        return str(self.entry.get("id", ""))

    @property
    def rank(self) -> str:
        # World pool entries don't have a card rank; use minRank as list label.
        return str(self.entry.get("minRank") or self.entry.get("rank") or "E")

    @property
    def quest_type(self) -> str:
        t = self.entry.get("questType")
        return str(t).strip() if isinstance(t, str) and t.strip() else "fetch"


@dataclass
class WorldQuestBoardDocument:
    data: dict

    @property
    def ranks(self) -> List[str]:
        raw = self.data.get("ranks") or []
        out: List[str] = []
        for r in raw:
            if isinstance(r, dict) and r.get("id"):
                out.append(str(r["id"]))
        return out

    @property
    def profile_id(self) -> str:
        pid = self.data.get("profileId")
        return str(pid).strip() if isinstance(pid, str) else ""

    def pool(self) -> List[dict]:
        pool = self.data.get("pool")
        if not isinstance(pool, list):
            pool = []
            self.data["pool"] = pool
        return pool

    def flatten(self) -> List[WorldQuestRef]:
        refs: List[WorldQuestRef] = []
        for i, entry in enumerate(self.pool()):
            if isinstance(entry, dict):
                refs.append(WorldQuestRef(index=i, entry=entry))
        return refs

    def insert_quest(self, entry: dict, index: Optional[int] = None) -> WorldQuestRef:
        pool = self.pool()
        if index is None or index < 0 or index > len(pool):
            pool.append(entry)
            idx = len(pool) - 1
        else:
            pool.insert(index, entry)
            idx = index
        return WorldQuestRef(idx, entry)

    def remove_quest(self, ref: WorldQuestRef) -> None:
        pool = self.pool()
        if 0 <= ref.index < len(pool):
            pool.pop(ref.index)

    def rank_index(self, rank_id: str) -> int:
        try:
            return self.ranks.index(rank_id)
        except ValueError:
            return -1


@dataclass
class WorldQuestFilter:
    quest_type: Optional[str] = None
    rank: Optional[str] = None
    search: str = ""

    def matches(self, ref: WorldQuestRef, lang_getter) -> bool:
        if self.quest_type and ref.quest_type != self.quest_type:
            return False
        if self.rank and ref.rank != self.rank and str(ref.entry.get("minRank", "")) != self.rank:
            return False
        if self.search.strip():
            q = self.search.strip().lower()
            title = resolve_world_title(lang_getter, ref).lower()
            if q not in ref.quest_id.lower() and q not in title:
                return False
        return True


def resolve_world_title(lang_getter, ref: WorldQuestRef) -> str:
    key = ref.entry.get("titleLangKey")
    if not isinstance(key, str) or not key:
        return ref.quest_id
    return lang_getter(json_key_to_lang_key(key), ref.quest_id)


def filter_world_quests(
    refs: List[WorldQuestRef], filt: WorldQuestFilter, lang_getter
) -> List[WorldQuestRef]:
    return [r for r in refs if filt.matches(r, lang_getter)]


def make_world_template(quest_id: str = "new_quest", quest_type: str = "fetch") -> dict:
    return {
        "id": quest_id,
        "weight": 10,
        "titleLangKey": world_quest_title_lang_key(quest_id),
        "descriptionLangKey": world_quest_description_lang_key(quest_id),
        "minRank": "E",
        "maxRank": "C",
        "rankXpReward": 10,
        "daysLimit": 3,
        "questType": quest_type if quest_type in QUEST_TYPES else "fetch",
        "rewards": [
            {"kind": "item", "itemId": "Aetherhaven_Gold_Coin", "count": 5}
        ],
    }


def regenerate_world_lang_keys(
    entry: dict,
    quest_id: str,
    *,
    title_text: str,
    desc_text: str,
) -> Tuple[Dict[str, str], List[str]]:
    old_title = str(entry.get("titleLangKey", ""))
    old_desc = str(entry.get("descriptionLangKey", ""))
    entry["titleLangKey"] = world_quest_title_lang_key(quest_id)
    entry["descriptionLangKey"] = world_quest_description_lang_key(quest_id)
    pending: Dict[str, str] = {
        entry["titleLangKey"]: title_text,
        entry["descriptionLangKey"]: desc_text,
    }
    new_keys = set(pending.keys())
    stale = [k for k in (old_title, old_desc) if k and k not in new_keys]
    return pending, stale


def sync_lang_from_world_quests(
    doc: WorldQuestBoardDocument, lang_doc, texts: Dict[str, str]
) -> None:
    for json_key, value in texts.items():
        lang_doc.set(json_key_to_lang_key(json_key), value)
    for ref in doc.flatten():
        e = ref.entry
        for field in ("titleLangKey", "descriptionLangKey"):
            lk = e.get(field)
            if isinstance(lk, str) and lk and lk not in texts:
                lang_key = json_key_to_lang_key(lk)
                if not lang_doc.get(lang_key, "").strip():
                    lang_doc.set(lang_key, "")


def validate_world_document(doc: WorldQuestBoardDocument, lang_getter) -> List[str]:
    errors: List[str] = []
    ranks = set(doc.ranks)
    seen: set[str] = set()

    if not doc.profile_id:
        errors.append("Missing profileId")

    slot = doc.data.get("slotCount", 0)
    if not isinstance(slot, int) or slot < 1:
        errors.append("slotCount must be >= 1")

    for ref in doc.flatten():
        e = ref.entry
        qid = str(e.get("id", "")).strip()
        if not qid:
            errors.append(f"[pool #{ref.index}] missing id")
            continue
        if qid in seen:
            errors.append(f"Duplicate pool id '{qid}'")
        seen.add(qid)

        for field in ("minRank", "maxRank"):
            val = e.get(field)
            if not val:
                errors.append(f"[{qid}] missing {field}")
            elif ranks and val not in ranks:
                errors.append(f"[{qid}] unknown {field}: {val}")

        weight = e.get("weight", 0)
        if not isinstance(weight, int) or weight < 1:
            errors.append(f"[{qid}] weight must be >= 1")

        days = e.get("daysLimit", 0)
        if not isinstance(days, int) or days < 1:
            errors.append(f"[{qid}] daysLimit must be >= 1")

        xp = e.get("rankXpReward", 0)
        if not isinstance(xp, int) or xp < 0:
            errors.append(f"[{qid}] rankXpReward must be >= 0")

        qtype = e.get("questType")
        if qtype is not None and str(qtype).strip() and str(qtype).strip() not in QUEST_TYPES:
            errors.append(f"[{qid}] unknown questType: {qtype}")

        for lang_field in ("titleLangKey", "descriptionLangKey"):
            lk = e.get(lang_field)
            if not isinstance(lk, str) or not lk:
                errors.append(f"[{qid}] missing {lang_field}")
            else:
                lang_key = json_key_to_lang_key(lk)
                if not lang_getter(lang_key, "").strip():
                    errors.append(f"[{qid}] empty lang text for {lang_key}")

        for i, reward in enumerate(e.get("rewards") or []):
            if not isinstance(reward, dict):
                errors.append(f"[{qid}] reward #{i + 1} is not an object")
                continue
            kind = str(reward.get("kind", "")).strip()
            if kind == "item":
                if not str(reward.get("itemId", "")).strip():
                    errors.append(f"[{qid}] item reward #{i + 1} missing itemId")
            elif kind == "reputation":
                amount = reward.get("amount", 0)
                if not isinstance(amount, int) or amount < 1:
                    errors.append(f"[{qid}] reputation reward #{i + 1} amount must be >= 1")

    return errors
