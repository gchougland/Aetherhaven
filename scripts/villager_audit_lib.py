"""Shared helpers for Aetherhaven villager audit JSONL logs."""
from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

SOURCE_LABELS: dict[str, str] = {
    "pending_removal_queue": "Deferred despawn (caller did not tag reason; often guild hall cleanup)",
    "external_remove": "Removed outside mod code (entity tool, another mod, or game system)",
    "entity_remove_unknown": "Removed outside mod code (entity tool, another mod, or game system)",
    "death_handler": "Died in world",
    "elder_duplicate_reconcile": "Duplicate elder removed during reconcile",
    "townsfolk_duplicate_purge": "Duplicate townsfolk entity removed",
    "townsfolk_reconcile": "Ledger says entity is gone during townsfolk reconcile",
    "guild_hall_stale_adventurer_purge": "Stale guild hall adventurer removed during reconcile",
    "guild_hall_adventurer_despawn": "Guild hall adventurer despawned (morning roll or hall reset)",
    "hired_guard_clear": "Hired guard cleared from town records",
    "dialogue_rescue_vanish": "Rescue quest dialogue vanish effect",
    "tourist_purge": "Tourist removed during reconcile",
    "inn_visitor_despawn": "Inn visitor despawned",
    "town_dissolution": "Town dissolved",
    "dissolved_town_orphan": "Orphan NPC from dissolved town",
    "villager_reset": "Villager reset command or service",
    "guard_hire_replace": "Guard hire replaced existing guard",
    "admin_townsfolk_despawn": "Admin townsfolk despawn command",
    "resident_duplicate_reconcile": "Duplicate story villager removed during reconcile",
    "stale_uuid_after_respawn": "Stale UUID after respawn",
}

EVENT_LABELS: dict[str, str] = {
    "DEATH": "Death",
    "REMOVED": "Removed",
    "DETECTED_MISSING": "Missing",
}

DEFAULT_AUDIT_ROOT = Path(__file__).resolve().parents[1] / "run/mods/Hexvane_Aetherhaven/villager_audit"


def source_label(source: str) -> str:
    key = (source or "").strip()
    if not key:
        return "Unknown"
    return SOURCE_LABELS.get(key, key.replace("_", " ").title())


def event_label(event: str) -> str:
    key = (event or "").strip().upper()
    return EVENT_LABELS.get(key, key or "?")


def format_time(epoch_ms: int | float | None) -> str:
    if epoch_ms is None:
        return "?"
    try:
        dt = datetime.fromtimestamp(float(epoch_ms) / 1000.0, tz=timezone.utc)
    except (OSError, OverflowError, ValueError):
        return str(epoch_ms)
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def format_pos(row: dict) -> str:
    parts = []
    for axis in ("x", "y", "z"):
        val = row.get(axis)
        if val is None:
            parts.append("?")
        else:
            parts.append(f"{float(val):.1f}")
    return ", ".join(parts)


def load_rows(path: Path) -> tuple[list[dict], list[str]]:
    rows: list[dict] = []
    warnings: list[str] = []
    text = path.read_text(encoding="utf-8")
    for line_no, line in enumerate(text.splitlines(), start=1):
        line = line.strip()
        if not line:
            continue
        try:
            row = json.loads(line)
            row["_sourceFile"] = str(path)
            rows.append(row)
        except json.JSONDecodeError as exc:
            warnings.append(f"{path.name} line {line_no}: {exc}")
    return rows, warnings


def load_from_paths(paths: list[Path]) -> tuple[list[dict], list[str]]:
    rows: list[dict] = []
    warnings: list[str] = []
    for path in paths:
        if not path.is_file():
            warnings.append(f"Not found: {path}")
            continue
        loaded, file_warnings = load_rows(path)
        rows.extend(loaded)
        warnings.extend(file_warnings)
    rows.sort(key=lambda r: float(r.get("epochMs") or 0))
    return rows, warnings


def discover_audit_files(root: Path | None = None) -> list[Path]:
    base = root or DEFAULT_AUDIT_ROOT
    if not base.exists():
        return []
    return sorted(base.rglob("audit.jsonl"))


def filter_rows(
    rows: list[dict],
    *,
    event: str | None = None,
    search: str | None = None,
    town: str | None = None,
    name: str | None = None,
    source: str | None = None,
) -> list[dict]:
    out = rows
    if event and event != "All":
        want = event.upper()
        out = [r for r in out if str(r.get("event", "")).upper() == want]
    if search:
        needle = search.lower()
        out = [
            r
            for r in out
            if needle in str(r.get("displayName", "")).lower()
            or needle in str(r.get("townName", "")).lower()
            or needle in str(r.get("source", "")).lower()
            or needle in source_label(str(r.get("source", ""))).lower()
            or needle in str(r.get("roleId", "")).lower()
            or needle in str(r.get("entityUuid", "")).lower()
        ]
    if town:
        needle = town.lower()
        out = [
            r
            for r in out
            if needle in str(r.get("townName", "")).lower()
            or needle in str(r.get("townId", "")).lower()
        ]
    if name:
        needle = name.lower()
        out = [r for r in out if needle in str(r.get("displayName", "")).lower()]
    if source:
        needle = source.lower()
        out = [r for r in out if needle in str(r.get("source", "")).lower()]
    return out


def format_details(row: dict) -> str:
    event = str(row.get("event", "?"))
    source = str(row.get("source", ""))
    lines = [
        f"When:     {format_time(row.get('epochMs'))} UTC",
        f"Event:    {event_label(event)}",
        f"Name:     {row.get('displayName') or '?'}",
        f"Role:     {row.get('roleId') or '?'}",
        f"Kind:     {row.get('bindingKind') or '?'}",
        f"Town:     {row.get('townName') or row.get('townId') or '?'}",
        f"World:    {row.get('world') or '?'}",
        f"Position: {format_pos(row)}",
        f"Source:   {source}",
        f"Reason:   {source_label(source)}",
    ]
    death_cause = (row.get("deathCause") or "").strip()
    if death_cause:
        lines.append(f"Cause:    {death_cause}")
    notes = (row.get("notes") or "").strip()
    if notes:
        lines.append(f"Notes:    {notes}")
    lines.append(f"UUID:     {row.get('entityUuid') or '?'}")
    source_file = row.get("_sourceFile")
    if source_file:
        lines.append(f"File:     {source_file}")
    return "\n".join(lines)


def summary_text(rows: list[dict]) -> str:
    if not rows:
        return "No events"
    by_event: dict[str, int] = {}
    by_source: dict[str, int] = {}
    for row in rows:
        ev = str(row.get("event", "?"))
        src = str(row.get("source", "?"))
        by_event[ev] = by_event.get(ev, 0) + 1
        by_source[src] = by_source.get(src, 0) + 1
    parts = [f"{len(rows)} event(s)"]
    for key in sorted(by_event):
        parts.append(f"{event_label(key)} {by_event[key]}")
    return " · ".join(parts)
