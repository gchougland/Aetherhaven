#!/usr/bin/env python3
"""Read dialogue JSON at HEAD, emit aetherhaven.dialogue.*= lines (same as migration)."""
import json
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DIALOGUE = REPO / "src/main/resources/Server/Aetherhaven/Dialogue"


def collect(data: dict) -> dict[str, str]:
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
    return out


def main() -> int:
    all_k: dict[str, str] = {}
    for path in sorted(DIALOGUE.glob("*.json")):
        rel = path.relative_to(REPO).as_posix()
        p = subprocess.run(
            ["git", "-C", str(REPO), "show", f"HEAD:{rel}"],
            capture_output=True,
        )
        if p.returncode != 0:
            print(p.stderr.decode(), file=sys.stderr)
            return 1
        data = json.loads(p.stdout.decode("utf-8"))
        all_k.update(collect(data))
    for k in sorted(all_k):
        v = all_k[k].replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
        print(f"{k}={v}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
