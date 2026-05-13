"""Generate player-facing villager wiki markdown from Server JSON (no raw ids in body)."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
VILL = ROOT / "Server/Aetherhaven/Villagers"
QUEST = ROOT / "Server/Aetherhaven/Quests"
SCHED = ROOT / "Server/Aetherhaven/VillagerSchedules"
OUT = ROOT / "Common/Docs/Hexvane_AetherhavenWiki"

# Where they spend "work" hours on the schedule, in plain language.
WORKPLACE_AT = {
    "Aetherhaven_Innkeeper": "the inn",
    "Aetherhaven_Elder_Lyren": "the town hall",
    "Aetherhaven_Merchant": "the market stall",
    "Aetherhaven_Blacksmith": "the blacksmith shop",
    "Aetherhaven_Priestess": "the Gaia altar",
    "Aetherhaven_Farmer": "the farm plot",
    "Aetherhaven_Rancher": "the barn",
    "Aetherhaven_Miner": "the miner's hut",
    "Aetherhaven_Logger": "the lumber mill",
}

SCHED_LOC = {
    "work": "workplace",
    "inn": "the inn commons",
    "park": "the town park",
    "home": "home",
    "gaia_altar": "the Gaia altar",
}


def load_json(p: Path) -> dict:
    return json.loads(p.read_text(encoding="utf-8"))


def all_quests() -> list[Path]:
    return sorted(QUEST.rglob("*.json"))


def quests_for_role(role_id: str) -> list[dict]:
    out: list[dict] = []
    seen: set[str] = set()
    for path in all_quests():
        q = load_json(path)
        qid = q.get("id") or path.stem
        if q.get("assignNpcRoleId") == role_id:
            if qid not in seen:
                seen.add(qid)
                out.append(q)
            continue
        for ob in q.get("objectives") or []:
            if ob.get("npcRoleId") == role_id:
                if qid not in seen:
                    seen.add(qid)
                    out.append(q)
                break
        else:
            for rw in q.get("rewards") or []:
                if (
                    rw.get("kind") == "reputation"
                    and rw.get("npcRoleId") == role_id
                    and rw.get("grantTo") == "quest_beneficiary_npc"
                ):
                    if qid not in seen:
                        seen.add(qid)
                        out.append(q)
                    break
    out.sort(key=lambda x: x.get("id", ""))
    return out


def human_item(item_id: str) -> str:
    s = (item_id or "").strip()
    if not s:
        return ""
    if s.startswith("Aetherhaven_"):
        s = s[len("Aetherhaven_") :]
    return " ".join(w.capitalize() for w in s.replace("_", " ").split())


def gift_section(title: str, items: list[str]) -> str:
    if not items:
        return f"### {title}\n\nNothing listed in this tier.\n\n"
    lines = [f"### {title}", ""]
    for it in items:
        lines.append(f"- {human_item(it)}")
    lines.append("")
    return "\n".join(lines)


def schedule_md(role_id: str) -> str:
    p = SCHED / f"{role_id}.json"
    if not p.exists():
        return "_Weekly timings are not documented for this villager yet._\n"
    d = load_json(p)
    tr = d.get("transitions") or []
    if not tr:
        return "_Schedule file is empty._\n"
    work_at = WORKPLACE_AT.get(role_id, "their workplace")
    lines = [
        "Times are in world hours. Most days follow the same rhythm; Sunday can include a short visit to the altar.\n"
    ]
    by_day: dict[str, list] = {}
    for t in tr:
        day = t.get("dayOfWeek", "?")
        by_day.setdefault(day, []).append(t)
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
        lines.append("### " + day.title())
        for t in by_day[day]:
            raw = t.get("location", "?")
            h = t.get("hour", 0)
            m = t.get("minute", 0)
            slot = SCHED_LOC.get(raw, raw)
            if slot == "workplace":
                place = work_at
            else:
                place = slot
            lines.append(f"- **{h:02d}:{m:02d}** — {place}")
        lines.append("")
    return "\n".join(lines)


def rep_md(milestones: list[dict]) -> str:
    if not milestones:
        return (
            "### Friendship rewards\n\n"
            "No milestone gifts are scripted yet. Keep chatting and helping out in town.\n\n"
        )
    lines = [
        "### Friendship rewards",
        "",
        "Visit them after your friendship reaches each level to pick up the reward through dialogue.\n",
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


def quest_md(qs: list[dict]) -> str:
    if not qs:
        return "### Quests\n\nNothing specific is listed for them in this guide yet.\n\n"
    lines = ["### Quests", ""]
    for q in qs:
        title = q.get("title", "")
        desc = (q.get("description") or "").strip()
        lines.append(f"#### {title}")
        if desc:
            lines.append(desc + "\n")
        for i, ob in enumerate(q.get("objectives") or [], 1):
            txt = (ob.get("text") or "").strip()
            if txt:
                lines.append(f"{i}. {txt}")
        lines.append("")
    return "\n".join(lines)


TOPICS = [
    ("villager_innkeeper", "Aetherhaven_Innkeeper"),
    ("villager_elder", "Aetherhaven_Elder_Lyren"),
    ("villager_merchant", "Aetherhaven_Merchant"),
    ("villager_blacksmith", "Aetherhaven_Blacksmith"),
    ("villager_priestess", "Aetherhaven_Priestess"),
    ("villager_farmer", "Aetherhaven_Farmer"),
    ("villager_rancher", "Aetherhaven_Rancher"),
    ("villager_miner", "Aetherhaven_Miner"),
    ("villager_logger", "Aetherhaven_Logger"),
]


def opening_and_glance(name: str, role: str, inn: bool | None) -> str:
    where = WORKPLACE_AT.get(role, "their usual spot in town")
    lines = [
        f"**{name}** is usually found at **{where}** when they are on shift.\n",
        "## At a glance",
        "",
    ]
    if role == "Aetherhaven_Innkeeper":
        lines.append("- They **run the inn** once you finish raising it.")
    elif inn:
        lines.append(
            "- They can **appear as an extra guest at the inn** when the town needs more faces there."
        )
    else:
        lines.append("- They are **not** pulled in as a spare inn visitor.")
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    for stem, role in TOPICS:
        v = load_json(VILL / f"{role}.json")
        name = v.get("displayName", role)
        inn = v.get("innPoolEligible")
        qs = quests_for_role(role)
        body: list[str] = []
        body.append(opening_and_glance(name, role, inn))
        body.append(quest_md(qs))
        body.append(rep_md(v.get("reputationMilestones") or []))
        body.append("## Weekly routine\n\n")
        body.append(schedule_md(role))
        body.append("## Gift tastes\n\n")
        body.append(
            "Bring items as gifts to raise friendship. Weekly limits still apply. "
            "Roughly: **greatly loved** gives the most, **liked** is positive, **disliked** hurts the mood.\n"
        )
        body.append(gift_section("Greatly loved", v.get("giftLoves") or []))
        body.append(gift_section("Liked", v.get("giftLikes") or []))
        body.append(gift_section("Disliked", v.get("giftDislikes") or []))
        desc = f"Quests, friendship, schedule, and gifts for {name}."
        front = (
            "---\n"
            f"name: {json.dumps(name)}\n"
            f"description: {json.dumps(desc)}\n"
            "author: Hexvane\n"
            f"npcRoleId: {role}\n"
            "---\n\n"
        )
        img = f"![](wiki/{stem}.png)\n\n"
        out = front + img + "\n".join(body)
        outp = OUT / f"{stem}.md"
        outp.write_text(out, encoding="utf-8")
        print("Wrote", outp, "chars", len(out))


if __name__ == "__main__":
    main()
