#!/usr/bin/env python3
"""
Build localized guide markdown from English sources under
`Server/Aetherhaven/GuideTopics/en-US/`.

Uses the same translation backends as `build_server_translations.py`
(GOOGLE_TRANSLATE_API_KEY or deep_translator).

Translates frontmatter `name` / `description` and the markdown body.
Preserves verbatim: `sub-topics` ids, `npcRoleId`, `author`, image paths.

Run:
  python scripts/translate_guide_pages.py
  python scripts/translate_guide_pages.py --only fr-FR,de-DE
  python scripts/translate_guide_pages.py --no-resume
  python scripts/translate_guide_pages.py --append-only ru-RU
  python scripts/translate_guide_pages.py --workers 1 --sleep-between-langs 5
"""
from __future__ import annotations

import argparse
import re
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
GUIDE_BASE = REPO / "src/main/resources/Server/Aetherhaven/GuideTopics"
SRC_EN = GUIDE_BASE / "en-US"

# Reuse backends from build_server_translations.py
sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_server_translations as bst  # noqa: E402

LANGS = bst.LANGS

MD_IMAGE = re.compile(r"!\[[^\]]*\]\([^)]+\)")
MD_LINK = re.compile(r"\[[^\]]+\]\([^)]+\)")
INLINE_CODE = re.compile(r"`[^`]+`")


def tokenize_protected(text: str) -> tuple[str, list[str]]:
    tokens: list[str] = []
    n = 0

    def repl(m: re.Match[str]) -> str:
        nonlocal n
        tokens.append(m.group(0))
        t = f"__MDPH_{n}__"
        n += 1
        return t

    for pat in (MD_IMAGE, MD_LINK, INLINE_CODE):
        text = pat.sub(repl, text)
    return text, tokens


def detokenize_protected(text: str, tokens: list[str]) -> str:
    for i, tok in enumerate(tokens):
        text = text.replace(f"__MDPH_{i}__", tok)
    return text


def split_frontmatter(raw: str) -> tuple[str | None, str]:
    text = raw.replace("\r\n", "\n")
    if not text.startswith("---\n"):
        return None, text
    end = text.find("\n---\n", 4)
    if end < 0:
        return None, text
    return text[4:end], text[end + 5 :]


def translate_field(
    value: str,
    gt_code: str,
    gcloud_key: str | None,
    translator,
) -> str:
    if not value.strip():
        return value
    tx, tokens = tokenize_protected(value)
    if gcloud_key:
        tr = bst.translate_gcloud_v2(tx, gt_code, gcloud_key)
    else:
        tr = bst.translate_one_deeplike(translator, tx)
    return detokenize_protected(tr, tokens)


def translate_frontmatter(
    fm: str,
    gt_code: str,
    gcloud_key: str | None,
    translator,
) -> str:
    out_lines: list[str] = []
    in_subs = False
    for line in fm.split("\n"):
        stripped = line.strip()
        if in_subs:
            if stripped.startswith("- "):
                out_lines.append(line)
                continue
            if stripped and ":" in stripped and not stripped.startswith("- "):
                key_part = stripped.split(":", 1)[0].strip()
                if key_part and " " not in key_part:
                    in_subs = False
                else:
                    out_lines.append(line)
                    continue
            else:
                out_lines.append(line)
                continue
        if stripped.startswith("sub-topics:"):
            in_subs = True
            out_lines.append(line)
            continue
        if stripped.startswith("name:"):
            val = line.split(":", 1)[1].strip()
            quoted = val.startswith('"') and val.endswith('"')
            inner = val[1:-1] if quoted else val
            tr = translate_field(inner, gt_code, gcloud_key, translator)
            out_lines.append('name: "' + tr.replace('"', '\\"') + '"' if quoted or " " in tr else f"name: {tr}")
            continue
        if stripped.startswith("description:"):
            val = line.split(":", 1)[1].strip()
            quoted = val.startswith('"') and val.endswith('"')
            inner = val[1:-1] if quoted else val
            tr = translate_field(inner, gt_code, gcloud_key, translator)
            if quoted or " " in tr or ":" in tr:
                out_lines.append('description: "' + tr.replace('"', '\\"') + '"')
            else:
                out_lines.append(f"description: {tr}")
            continue
        out_lines.append(line)
    return "\n".join(out_lines)


def build_localized_md(
    raw_en: str,
    gt_code: str,
    gcloud_key: str | None,
    translator,
) -> str:
    fm, body = split_frontmatter(raw_en)
    if fm is None:
        body_tr = translate_field(body, gt_code, gcloud_key, translator)
        return body_tr.rstrip() + "\n"
    fm_tr = translate_frontmatter(fm, gt_code, gcloud_key, translator)
    body_tr = translate_field(body.strip(), gt_code, gcloud_key, translator)
    return f"---\n{fm_tr}\n---\n\n{body_tr.rstrip()}\n"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-resume", action="store_true", help="Rebuild every locale/file even if output exists.")
    ap.add_argument("--only", type=str, default="", help="Comma-separated locale folders, e.g. fr-FR,de-DE")
    ap.add_argument(
        "--append-only",
        type=str,
        default="",
        help="Comma-separated locales where existing translated files are kept.",
    )
    ap.add_argument("--sleep-between-langs", type=float, default=0.0, metavar="SEC")
    args = ap.parse_args()

    if not SRC_EN.is_dir():
        print(f"Missing guide source dir: {SRC_EN}", file=sys.stderr)
        sys.exit(1)

    sources = sorted(SRC_EN.glob("*.md"), key=lambda p: p.name.lower())
    if not sources:
        print(f"No .md files under {SRC_EN}", file=sys.stderr)
        sys.exit(1)

    gcloud_key = (__import__("os").environ.get("GOOGLE_TRANSLATE_API_KEY") or "").strip() or None
    if gcloud_key:
        print("Backend: Google Cloud Translation API v2", flush=True)
    else:
        print("Backend: deep_translator", flush=True)

    only_set = {x.strip() for x in args.only.split(",") if x.strip()}
    append_only_set = {x.strip() for x in args.append_only.split(",") if x.strip()}
    sleep_between = max(0.0, args.sleep_between_langs)

    print(f"Source: {len(sources)} guide page(s) in en-US/", flush=True)

    did_build = False
    for folder, gt in LANGS:
        if only_set and folder not in only_set:
            continue
        if did_build and sleep_between > 0:
            print(f"  cooldown {sleep_between}s before {folder} ...", flush=True)
            time.sleep(sleep_between)

        out_dir = GUIDE_BASE / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        append_only = folder in append_only_set
        print(f"--- {folder} ({gt}) ---", flush=True)

        translator = None if gcloud_key else __import__("deep_translator").GoogleTranslator(source="en", target=gt)

        for src in sources:
            dest = out_dir / src.name
            if not args.no_resume and dest.is_file() and not append_only:
                continue
            if append_only and dest.is_file():
                continue

            raw_en = src.read_text(encoding="utf-8")
            text = build_localized_md(raw_en, gt, gcloud_key, translator)
            dest.write_text(text, encoding="utf-8")
            print(f"  Wrote {dest.relative_to(REPO)} ({dest.stat().st_size} bytes)", flush=True)

        did_build = True

    print("Done.")


if __name__ == "__main__":
    main()
