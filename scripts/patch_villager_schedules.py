"""Append weekly routine sections to villager wiki pages from schedule JSON."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
SCHED = ROOT / "Server/Aetherhaven/VillagerSchedules"
VILL = ROOT / "Server/Aetherhaven/Villagers"
OUT = ROOT / "Server/Aetherhaven/GuideTopics/en-US"

WORKPLACE_AT = {
    "Aetherhaven_Bard": "the guild hall",
    "Aetherhaven_Builder": "the builder's hut",
    "Aetherhaven_Florist": "the flower shop",
    "Aetherhaven_Chef": "the restaurant",
    "Aetherhaven_Guild_Master": "the guild hall",
    "Aetherhaven_Pyrotechnic": "the bomb shop",
    "Aetherhaven_Crystal_Keeper": "the crystal shop",
}

SCHED_LOC = {
    "work": "workplace",
    "inn": "the inn commons",
    "park": "the town park",
    "home": "home",
    "gaia_altar": "the Gaia altar",
    "shop": "town shops",
}

TOPICS = [
    ("villager_bard", "Aetherhaven_Bard"),
    ("villager_builder", "Aetherhaven_Builder"),
    ("villager_florist", "Aetherhaven_Florist"),
    ("villager_chef", "Aetherhaven_Chef"),
    ("villager_guild_master", "Aetherhaven_Guild_Master"),
    ("villager_pyrotechnic", "Aetherhaven_Pyrotechnic"),
    ("villager_crystal_keeper", "Aetherhaven_Crystal_Keeper"),
]

SCHEDULE_TOGGLE = re.compile(
    r"\nUse \*\*Show weekly schedule\*\* below to see where .+?\.\n", re.MULTILINE
)


def human_item(item_id: str) -> str:
    s = (item_id or "").strip()
    if not s:
        return ""
    if s.startswith("Aetherhaven_"):
        s = s[len("Aetherhaven_") :]
    return " ".join(w.capitalize() for w in s.replace("_", " ").split())


def rep_md(milestones: list[dict]) -> str:
    if not milestones:
        return ""
    lines = [
        "### Friendship rewards",
        "",
        "Visit them after your friendship reaches each level to pick up the reward through dialogue.",
        "",
    ]
    for m in milestones:
        mn = m.get("minReputation", 0)
        item = (m.get("itemId") or "").strip()
        cnt = m.get("itemCount", 0)
        learn = m.get("learnRecipeItemId")
        learn_s = (learn or "").strip() if learn else ""
        parts: list[str] = []
        if learn_s:
            parts.append(f"teaches you to craft **{human_item(learn_s)}**")
        if item and cnt:
            parts.append(f"gives you **{cnt}** × **{human_item(item)}**")
        elif item:
            parts.append(f"gives you **{human_item(item)}**")
        if not parts:
            parts.append("visit them for their next friendship scene")
        lines.append(f"- **Friendship {mn}:** " + "; ".join(parts))
    lines.append("")
    return "\n".join(lines)


def schedule_md(role_id: str) -> str:
    p = SCHED / f"{role_id}.json"
    if not p.exists():
        return ""
    d = json.loads(p.read_text(encoding="utf-8"))
    tr = d.get("transitions") or []
    if not tr:
        return ""
    work_at = WORKPLACE_AT.get(role_id, "their workplace")
    lines = [
        "## Weekly routine",
        "",
        "Times are in world hours. Most days follow the same rhythm; Sunday can include a short visit to the altar.",
        "",
    ]
    by_day: dict[str, list] = {}
    for t in tr:
        by_day.setdefault(t.get("dayOfWeek", "?"), []).append(t)
    for day in [
        "MONDAY",
        "TUESDAY",
        "WEDNESDAY",
        "THURSDAY",
        "FRIDAY",
        "SATURDAY",
        "SUNDAY",
    ]:
        if day not in by_day:
            continue
        day_rows = sorted(by_day[day], key=lambda x: (x.get("hour", 0), x.get("minute", 0)))
        lines.append("### " + day.title())
        for t in day_rows:
            raw = t.get("location", "?")
            h = t.get("hour", 0)
            m = t.get("minute", 0)
            slot = SCHED_LOC.get(raw, raw)
            place = work_at if slot == "workplace" else slot
            lines.append(f"- **{h:02d}:{m:02d}** — {place}")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def patch(stem: str, role: str) -> None:
    path = OUT / f"{stem}.md"
    text = path.read_text(encoding="utf-8")
    text = SCHEDULE_TOGGLE.sub("\n", text)
    if "## Weekly routine" in text:
        print("skip (already has schedule):", path.name)
        return
    v = json.loads((VILL / f"{role}.json").read_text(encoding="utf-8"))
    rep = rep_md(v.get("reputationMilestones") or [])
    if rep and "### Friendship rewards" not in text:
        text = text.rstrip() + "\n\n" + rep
    sched = schedule_md(role)
    if sched:
        text = text.rstrip() + "\n\n" + sched
    path.write_text(text, encoding="utf-8")
    print("patched", path.name)


def main() -> None:
    for stem, role in TOPICS:
        patch(stem, role)


if __name__ == "__main__":
    main()
