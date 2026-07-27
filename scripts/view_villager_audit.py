"""CLI pretty-printer for Aetherhaven villager audit JSONL logs."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from villager_audit_lib import (
    DEFAULT_AUDIT_ROOT,
    discover_audit_files,
    event_label,
    filter_rows,
    format_details,
    format_pos,
    format_time,
    load_from_paths,
    source_label,
    summary_text,
)


def resolve_paths(paths: list[str]) -> list[Path]:
    resolved: list[Path] = []
    for raw in paths:
        p = Path(raw)
        if p.is_dir():
            resolved.extend(sorted(p.rglob("audit.jsonl")))
        else:
            resolved.append(p)
    return resolved


def print_rows(rows: list[dict], *, compact: bool) -> None:
    if not rows:
        print("No matching audit events.")
        return

    for i, row in enumerate(rows, start=1):
        if compact:
            print(
                f"{format_time(row.get('epochMs'))} | "
                f"{event_label(str(row.get('event', '?')))} | "
                f"{row.get('displayName') or '?'} | "
                f"{row.get('townName') or row.get('townId') or '?'} | "
                f"{source_label(str(row.get('source', '')))}"
            )
            continue

        print(f"--- {i} ---")
        print(format_details(row))
        print()


def print_summary(rows: list[dict]) -> None:
    if not rows:
        return
    by_event: dict[str, int] = {}
    by_source: dict[str, int] = {}
    for row in rows:
        ev = str(row.get("event", "?"))
        src = str(row.get("source", "?"))
        by_event[ev] = by_event.get(ev, 0) + 1
        by_source[src] = by_source.get(src, 0) + 1

    print("Summary")
    print(f"  Total events: {len(rows)}")
    print("  By event:")
    for key in sorted(by_event):
        print(f"    {event_label(key)}: {by_event[key]}")
    print("  By source:")
    for key in sorted(by_source, key=lambda s: (-by_source[s], s)):
        print(f"    {key} ({source_label(key)}): {by_source[key]}")
    print()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="View Aetherhaven villager audit JSONL logs in readable form."
    )
    parser.add_argument(
        "paths",
        nargs="*",
        help="audit.jsonl file or folder (default: run/mods/Hexvane_Aetherhaven/villager_audit)",
    )
    parser.add_argument("--event", help="Filter by event: DEATH, REMOVED, DETECTED_MISSING")
    parser.add_argument("--town", help="Filter by town name or id substring")
    parser.add_argument("--name", help="Filter by villager display name substring")
    parser.add_argument("--source", help="Filter by source tag substring")
    parser.add_argument("--compact", action="store_true", help="One line per event")
    parser.add_argument("--summary", action="store_true", help="Print counts by event and source")
    args = parser.parse_args()

    if args.paths:
        files = resolve_paths(args.paths)
    else:
        files = discover_audit_files(DEFAULT_AUDIT_ROOT)

    if not files:
        print("No audit.jsonl files found.", file=sys.stderr)
        return 1

    rows, warnings = load_from_paths(files)
    for warning in warnings:
        print(f"Warning: {warning}", file=sys.stderr)

    rows = filter_rows(
        rows,
        event=args.event,
        town=args.town,
        name=args.name,
        source=args.source,
    )

    if args.summary:
        print_summary(rows)

    print_rows(rows, compact=args.compact)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
