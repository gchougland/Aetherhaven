#!/usr/bin/env python3
"""
Build server.lang translations from en-US.

  • If GOOGLE_TRANSLATE_API_KEY is set: Google Cloud Translation API (v2, REST) — billable, higher quotas.
  • Otherwise: deep_translator (unofficial public endpoint; may rate-limit on large runs).

Preserves keys, {placeholders}, and literal \\n in values.

Why it crawls on *later* languages with the free backend (e.g. es-419, tr-TR):
  The unofficial endpoint rate-limits by IP; thousands of lines × many languages adds up. Use
  GOOGLE_TRANSLATE_API_KEY to avoid that, or run  --only  for stragglers.

  Mitigations (free backend):  --only es-419,tr-TR  in a second pass,  --workers 3,  --sleep-between-langs 8

PowerShell (manual key for this session):
  $env:GOOGLE_TRANSLATE_API_KEY = "your-key"
  python scripts/build_server_translations.py

Run:
  python scripts/build_server_translations.py
  python scripts/build_server_translations.py --no-resume   # rebuild all
  python scripts/build_server_translations.py --only fr-FR,de-DE
  python scripts/build_server_translations.py --workers 12
  python scripts/build_server_translations.py --workers 1
  python scripts/build_server_translations.py --sleep-between-langs 5
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

try:
    from deep_translator import GoogleTranslator
except ImportError:
    print("Install: pip install deep-translator", file=sys.stderr)
    raise

# v2 JSON API: https://cloud.google.com/translate/docs/reference/translate
GCLOUD_TRANSLATE_V2 = "https://translation.googleapis.com/language/translate/v2"

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


def translate_gcloud_v2(text: str, target: str, api_key: str) -> str:
    """Cloud Translation API v2; target is BCP-47 / API language code (e.g. fr, zh-CN)."""
    if not text:
        return text
    key_q = urllib.parse.quote(api_key, safe="")
    url = f"{GCLOUD_TRANSLATE_V2}?key={key_q}"
    payload = json.dumps(
        {
            "q": text,
            "source": "en",
            "target": target,
            "format": "text",
        }
    ).encode("utf-8")
    for attempt in range(6):
        try:
            req = urllib.request.Request(
                url,
                data=payload,
                headers={"Content-Type": "application/json; charset=utf-8"},
            )
            with urllib.request.urlopen(req, timeout=120) as resp:
                body = json.load(resp)
            t = body["data"]["translations"][0]["translatedText"]
            if t is not None and str(t).strip():
                return str(t).strip()
        except urllib.error.HTTPError as e:
            err = e.read().decode("utf-8", errors="replace")
            if attempt == 0:
                print(f"  [gcloud] HTTP {e.code}: {err[:800]}", file=sys.stderr, flush=True)
            time.sleep(0.6 * (attempt + 1))
        except OSError as e:
            if attempt == 0:
                print(f"  [gcloud] network error: {e}", file=sys.stderr, flush=True)
            time.sleep(0.6 * (attempt + 1))
        except Exception as e:
            if attempt == 0:
                print(f"  [gcloud] error: {e}", file=sys.stderr, flush=True)
            time.sleep(0.6 * (attempt + 1))
    return text


def translate_one_deeplike(translator: GoogleTranslator, text: str) -> str:
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


def build_for_lang_fixed(
    lines: list[str],
    gt_code: str,
    workers: int,
    serial_sleep_sec: float,
    gcloud_api_key: str | None,
) -> str:
    out = list(lines)
    items = collect_items(lines)
    total = len(items)
    if total == 0:
        return "\n".join(out) + "\n"

    use_gcloud = gcloud_api_key is not None and gcloud_api_key != ""

    if workers <= 1:
        translator: GoogleTranslator | None = None
        if not use_gcloud:
            translator = GoogleTranslator(source="en", target=gt_code)
        for n, (line_i, tx, tlist) in enumerate(items):
            if n % 100 == 0 or n == total - 1:
                print(f"  keys {n + 1}/{total}", flush=True)
            tr = translate_gcloud_v2(tx, gt_code, gcloud_api_key) if use_gcloud else translate_one_deeplike(
                translator, tx
            )
            k = lines[line_i].split("=", 1)[0] + "="
            out[line_i] = k + detokenize_braces(tr, tlist)
            if serial_sleep_sec > 0:
                time.sleep(serial_sleep_sec)
        return "\n".join(out) + "\n"

    if not use_gcloud:
        tl: threading.local = threading.local()

        def get_translator() -> GoogleTranslator:
            t = getattr(tl, "gt", None)
            if t is None:
                t = GoogleTranslator(source="en", target=gt_code)
                tl.gt = t
            return t
    else:
        get_translator = None  # unused

    def work_item(it: tuple[int, str, list[str]]) -> tuple[int, str]:
        line_i, tx, tlist = it
        key_eq = lines[line_i].split("=", 1)[0] + "="
        if use_gcloud:
            tr = translate_gcloud_v2(tx, gt_code, gcloud_api_key)  # type: ignore[arg-type]
        else:
            tr = translate_one_deeplike(get_translator(), tx)  # type: ignore[misc]
        return line_i, key_eq + detokenize_braces(tr, tlist)

    print(f"  using {workers} workers", flush=True)
    done = 0
    with ThreadPoolExecutor(max_workers=workers) as ex:
        futures = [ex.submit(work_item, it) for it in items]
        for fut in as_completed(futures):
            line_i, new_line = fut.result()
            out[line_i] = new_line
            done += 1
            if done % 100 == 0 or done == total:
                print(f"  keys {done}/{total}", flush=True)

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
    ap.add_argument(
        "--workers",
        type=int,
        default=8,
        metavar="N",
        help="Parallel translation threads per language (default: 8). Use 1 for old serial behavior.",
    )
    ap.add_argument(
        "--serial-sleep",
        type=float,
        default=0.06,
        metavar="SEC",
        help="Only with --workers 1: sleep after each key (default: 0.06). Ignored when workers > 1.",
    )
    ap.add_argument(
        "--sleep-between-langs",
        type=float,
        default=0.0,
        metavar="SEC",
        help="Sleep SEC seconds before each language after the first in this run (0=off). "
        "Helps avoid cumulative rate limits on the free backend when rebuilding many locales.",
    )
    args = ap.parse_args()
    workers = max(1, min(32, args.workers))
    sleep_between = max(0.0, args.sleep_between_langs)

    gcloud_key = (os.environ.get("GOOGLE_TRANSLATE_API_KEY") or "").strip()
    if gcloud_key:
        print("Backend: Google Cloud Translation API v2 (GOOGLE_TRANSLATE_API_KEY set)", flush=True)
    else:
        print("Backend: deep_translator (set GOOGLE_TRANSLATE_API_KEY to use Google Cloud).", flush=True)

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

    did_build_in_run = False
    for folder, gt in LANGS:
        if only_set and folder not in only_set:
            continue
        p = OUT_BASE / folder / "server.lang"
        if not args.no_resume and p.is_file():
            if count_key_lines(p) == src_keys:
                print(f"--- skip {folder} (already has {src_keys} keys) ---", flush=True)
                continue
        if did_build_in_run and sleep_between > 0:
            print(f"  cooldown {sleep_between}s before {folder} ...", flush=True)
            time.sleep(sleep_between)
        print(f"--- {folder} ({gt}) ---", flush=True)
        text = build_for_lang_fixed(
            lines,
            gt,
            workers=workers,
            serial_sleep_sec=args.serial_sleep if workers <= 1 else 0.0,
            gcloud_api_key=gcloud_key if gcloud_key else None,
        )
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
        did_build_in_run = True

    print("Done.")


if __name__ == "__main__":
    main()
