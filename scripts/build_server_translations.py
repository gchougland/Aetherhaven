#!/usr/bin/env python3
"""
Build server.lang translations from en-US using Google Translate (deep_translator).

Preserves keys, {placeholders}, and literal \\n in values.

Batch joining was removed: Google often alters or strips batch separators, which caused
very deep recursive fallback and apparent hangs on the 2nd+ language.

Run:
  python scripts/build_server_translations.py
  python scripts/build_server_translations.py --no-resume   # rebuild all
  python scripts/build_server_translations.py --only fr-FR,de-DE
"""
from __future__ import annotations

import argparse
import re
import sys
import time
from pathlib import Path

try:
    from deep_translator import GoogleTranslator
except ImportError:
    print("Install: pip install deep-translator", file=sys.stderr)
    raise

LANGS: list[tuple[str, str]] = [
    ("zh-CN", "zh-CN"),
    ("zh-TW", "zh-TW"),
    ("fr-FR", "fr"),
    ("de-DE", "de"),
    ("ja-JP", "ja"),
    ("ko-KR", "ko"),
    ("pt-BR", "pt"),
    ("ru-RU", "ru"),
    ("es-ES", "es"),
    ("es-419", "es"),
    ("tr-TR", "tr"),
    ("uk-UA", "uk"),
]

REPO = Path(__file__).resolve().parents[1]
SRC = REPO / "src/main/resources/Server/Languages/en-US/server.lang"
OUT_BASE = REPO / "src/main/resources/Server/Languages"

PH = re.compile(r"(\{[a-zA-Z0-9_.]+\})")


def tokenize_braces(s: str) -> tuple[str, list[str]]:
    tokens: list[str] = []
    n = 0

    def repl(m: re.Match) -> str:
        nonlocal n
        tokens.append(m.group(1))
        t = f"__PH_{n}__"
        n += 1
        return t

    return PH.sub(repl, s), tokens


def detokenize_braces(s: str, tokens: list[str]) -> str:
    for i, tok in enumerate(tokens):
        s = s.replace(f"__PH_{i}__", tok)
    return s


def translate_one(translator, text: str) -> str:
    for attempt in range(6):
        try:
            t = translator.translate(text)
            if t and str(t).strip():
                return t.strip()
        except Exception:
            time.sleep(0.6 * (attempt + 1))
    return text


def count_key_lines(path: Path) -> int:
    if not path.is_file():
        return -1
    n = 0
    for ln in path.read_text(encoding="utf-8").splitlines():
        st = ln.strip()
        if st and not st.startswith("#") and "=" in ln:
            k, _ = ln.split("=", 1)
            if k.strip():
                n += 1
    return n


def collect_items(lines: list[str]) -> list[tuple[int, str, list[str]]]:
    items: list[tuple[int, str, list[str]]] = []
    for i, line in enumerate(lines):
        st = line.strip()
        if not st or st.startswith("#") or "=" not in line:
            continue
        key, val = line.split("=", 1)
        if not key.strip():
            continue
        tx, tlist = tokenize_braces(val)
        items.append((i, tx, tlist))
    return items


def build_for_lang_fixed(lines: list[str], gt_code: str) -> str:
    translator = GoogleTranslator(source="en", target=gt_code)
    out = list(lines)
    items = collect_items(lines)
    total = len(items)
    for n, (line_i, tx, tlist) in enumerate(items):
        if n % 100 == 0 or n == total - 1:
            print(f"  keys {n + 1}/{total}", flush=True)
        tr = translate_one(translator, tx)
        k = lines[line_i].split("=", 1)[0] + "="
        out[line_i] = k + detokenize_braces(tr, tlist)
        time.sleep(0.06)
    return "\n".join(out) + "\n"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--no-resume",
        action="store_true",
        help="Rebuild every language even if key count already matches the source file.",
    )
    ap.add_argument(
        "--only",
        type=str,
        default="",
        help="Comma-separated folder names only, e.g. zh-TW,fr-FR",
    )
    args = ap.parse_args()

    if not SRC.is_file():
        print(f"Missing {SRC}", file=sys.stderr)
        sys.exit(1)
    src_text = SRC.read_text(encoding="utf-8")
    lines = src_text.splitlines()
    src_keys = count_key_lines(SRC)
    only_set = {x.strip() for x in args.only.split(",") if x.strip()}

    nval = sum(
        1
        for ln in lines
        if ln.strip() and not ln.strip().startswith("#") and "=" in ln
    )
    print(f"Source: {SRC}  ({len(lines)} lines, {nval} key lines, {src_keys} key lines counted)")

    for folder, gt in LANGS:
        if only_set and folder not in only_set:
            continue
        p = OUT_BASE / folder / "server.lang"
        if not args.no_resume and p.is_file():
            if count_key_lines(p) == src_keys:
                print(f"--- skip {folder} (already has {src_keys} keys) ---", flush=True)
                continue
        print(f"--- {folder} ({gt}) ---", flush=True)
        text = build_for_lang_fixed(lines, gt)
        out_dir = OUT_BASE / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        if folder == "es-419":
            header = (
                "# Spanish (Latin America) — machine translation; "
                "regionalize from es-ES as needed.\n"
            )
            text = header + text
        p.write_text(text, encoding="utf-8")
        print(f"Wrote {p} ({p.stat().st_size} bytes)")

    print("Done.")


if __name__ == "__main__":
    main()
