#!/usr/bin/env python3
"""
List block type names in a Hytale prefab JSON (Server/Prefabs/*.prefab.json).

Usage:
  python scripts/list_prefab_blocks.py path/to/File.prefab.json
  python scripts/list_prefab_blocks.py path/to/File.prefab.json --counts
  python scripts/list_prefab_blocks.py path/to/File.prefab.json --no-empty

Outputs one block name per line (sorted), or with --counts: name<TAB>count sorted by count desc.
Empty air cells use name "Empty" unless --no-empty is set (then they are skipped).

After the list, prints a summary: total block instances whose type name contains "wood" or "rock"
(case-insensitive), counting each placed block once toward each matching category.
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path


def wood_rock_summary(names: list[str]) -> tuple[int, int]:
    """Counts of block instances whose name contains 'wood' or 'rock' (case-insensitive)."""
    wood = 0
    rock = 0
    for name in names:
        ln = name.lower()
        if "wood" in ln:
            wood += 1
        if "rock" in ln:
            rock += 1
    return wood, rock


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("prefab", type=Path, help="Path to a .prefab.json file")
    p.add_argument("--counts", action="store_true", help="Print block name and occurrence counts")
    p.add_argument("--no-empty", action="store_true", help="Skip blocks named Empty")
    p.add_argument(
        "--no-summary",
        action="store_true",
        help="Do not print the wood/rock summary after the list",
    )
    args = p.parse_args()

    path: Path = args.prefab
    if not path.is_file():
        print(f"Not a file: {path}", file=sys.stderr)
        return 1

    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as e:
        print(f"Failed to read JSON: {e}", file=sys.stderr)
        return 1

    blocks = data.get("blocks")
    if not isinstance(blocks, list):
        print("JSON has no 'blocks' array.", file=sys.stderr)
        return 1

    names: list[str] = []
    for b in blocks:
        if not isinstance(b, dict):
            continue
        n = b.get("name")
        if isinstance(n, str):
            names.append(n)

    if args.no_empty:
        names = [n for n in names if n != "Empty"]

    if args.counts:
        for name, c in Counter(names).most_common():
            print(f"{name}\t{c}")
    else:
        for name in sorted(set(names)):
            print(name)

    if not args.no_summary:
        wood_n, rock_n = wood_rock_summary(names)
        print()
        print("--- Summary (block names containing 'wood' / 'rock', case-insensitive) ---")
        print(f"Wood: {wood_n}")
        print(f"Rock: {rock_n}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
