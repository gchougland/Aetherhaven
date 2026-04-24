#!/usr/bin/env python3
"""
One-off / repeatable: replace literal dialogue strings in JSON with server.* keys
and print .lang lines for keys that are not already in server.lang.

Re-run is safe: strings already starting with "server." are left unchanged.

Use emit_dialogue_lang_from_head.py to re-emit aetherhaven.dialogue.*= lines
from the last-committed JSON (e.g. after a migration, to fill server.lang).
"""
import json
import re
import sys
from pathlib import Path

DIALOGUE_DIR = Path(__file__).resolve().parents[1] / "src/main/resources/Server/Aetherhaven/Dialogue"
LANG_FILE = (
    Path(__file__).resolve().parents[1]
    / "src/main/resources/Server/Languages/en-US/server.lang"
)


def load_existing_lang_keys(path: Path) -> set[str]:
    if not path.exists():
        return set()
    keys = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        m = re.match(r"^([a-zA-Z0-9_.]+)=", line)
        if m:
            keys.add(m.group(1))
    return keys


def migrate_tree(data: dict) -> dict[str, str]:
    """Returns new key -> en text for this tree."""
    out: dict[str, str] = {}
    tree_id = data["id"]
    for node_id, node in data.get("nodes", {}).items():
        if not isinstance(node, dict):
            continue
        for field in ("speaker", "text"):
            if field not in node:
                continue
            v = node[field]
            if not isinstance(v, str) or not v.strip() or v.strip().startswith("server."):
                continue
            if field == "speaker":
                k = f"aetherhaven.dialogue.{tree_id}.{node_id}.speaker"
            else:
                k = f"aetherhaven.dialogue.{tree_id}.{node_id}.body"
            out[k] = v
            node[field] = "server." + k
        for i, ch in enumerate(node.get("choices", []) or []):
            if not isinstance(ch, dict):
                continue
            for sub in ("text", "disabledReason"):
                if sub not in ch:
                    continue
                v = ch[sub]
                if not isinstance(v, str) or not v.strip() or v.strip().startswith("server."):
                    continue
                if sub == "text":
                    k = f"aetherhaven.dialogue.{tree_id}.{node_id}.c{i}"
                else:
                    k = f"aetherhaven.dialogue.{tree_id}.{node_id}.c{i}.disabled"
                out[k] = v
                ch[sub] = "server." + k
    return out


def main() -> int:
    existing = load_existing_lang_keys(LANG_FILE)
    all_new: dict[str, str] = {}
    for path in sorted(DIALOGUE_DIR.glob("*.json")):
        with path.open(encoding="utf-8") as f:
            data = json.load(f)
        new = migrate_tree(data)
        if not new:
            print(f"skip (no changes): {path.name}", file=sys.stderr)
            continue
        with path.open("w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(f"updated: {path.name} (+{len(new)} keys)", file=sys.stderr)
        all_new.update(new)

    # Filter to keys not already in server.lang
    to_add = {k: v for k, v in all_new.items() if k not in existing}
    if not to_add:
        print("No new .lang lines needed (all keys present).", file=sys.stderr)
        return 0
    for k in sorted(to_add):
        v = to_add[k]
        # Escape: common .lang line format — avoid raw newlines in value
        v2 = v.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
        print(f"{k}={v2}")
    print(f"Total new .lang lines: {len(to_add)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
