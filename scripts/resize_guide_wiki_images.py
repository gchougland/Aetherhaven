#!/usr/bin/env python3
"""
Resize journal guide wiki PNGs to power-of-two dimensions (per side).

Backs up originals outside the repo, then overwrites files in:
  src/main/resources/Common/UI/Custom/Aetherhaven/wiki/

Requires: pip install pillow

Examples (PowerShell — quote paths with spaces):

  # Back up originals only (default backup folder is next to the repo)
  python scripts/resize_guide_wiki_images.py --backup-only

  # Preview 1024 px longest side, POT snap per dimension
  python scripts/resize_guide_wiki_images.py --max-long-edge 1024 --dry-run

  # Apply resize in the repo
  python scripts/resize_guide_wiki_images.py --max-long-edge 512 --apply

  # Restore wiki PNGs from backup
  python scripts/resize_guide_wiki_images.py --restore

Strategy:
  1. Copy each source PNG to BACKUP_DIR/original/ (skipped if already present unless --force-backup).
  2. Scale down so max(width, height) <= --max-long-edge (must be a power of 2).
  3. Snap each side independently to the nearest power of 2 (slight stretch is OK).
  4. Images already within the limit are only POT-snapped; never upscaled past source size.
"""

from __future__ import annotations

import argparse
import math
import shutil
import sys
from pathlib import Path

try:
    from PIL import Image, ImageOps
except ImportError as e:
    print("Install Pillow: pip install pillow", file=sys.stderr)
    raise SystemExit(1) from e

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_WIKI_DIR = REPO_ROOT / "src/main/resources/Common/UI/Custom/Aetherhaven/wiki"
DEFAULT_BACKUP_DIR = REPO_ROOT.parent / "Aetherhaven-guide-wiki-backup"

IMAGE_EXTENSIONS = frozenset({".png", ".jpg", ".jpeg", ".webp"})


def is_power_of_two(n: int) -> bool:
    return n > 0 and (n & (n - 1)) == 0


def nearest_power_of_two(n: int) -> int:
    if n <= 1:
        return 1
    lo = 1 << (n.bit_length() - 1)
    if lo == n:
        return n
    hi = lo << 1
    return lo if n - lo <= hi - n else hi


def floor_power_of_two(n: int) -> int:
    if n <= 1:
        return 1
    if is_power_of_two(n):
        return n
    return 1 << (n.bit_length() - 1)


def iter_wiki_images(wiki_dir: Path) -> list[Path]:
    out: list[Path] = []
    for p in sorted(wiki_dir.iterdir()):
        if not p.is_file():
            continue
        if p.name.startswith("."):
            continue
        if p.suffix.lower() not in IMAGE_EXTENSIONS:
            continue
        out.append(p)
    return out


def compute_target_size(
    width: int,
    height: int,
    max_long_edge: int,
    *,
    snap: str,
) -> tuple[int, int, float]:
    """Return (target_w, target_h, scale_factor_applied)."""
    long_edge = max(width, height)
    if long_edge > max_long_edge:
        scale = max_long_edge / long_edge
    else:
        scale = 1.0

    scaled_w = max(1, int(round(width * scale)))
    scaled_h = max(1, int(round(height * scale)))

    snap_fn = nearest_power_of_two if snap == "nearest" else floor_power_of_two
    target_w = snap_fn(scaled_w)
    target_h = snap_fn(scaled_h)

    # Never upscale beyond source dimensions (POT ceiling per axis).
    target_w = min(target_w, nearest_power_of_two(width))
    target_h = min(target_h, nearest_power_of_two(height))

    return target_w, target_h, scale


def backup_images(
    sources: list[Path],
    backup_original_dir: Path,
    *,
    force: bool,
) -> int:
    backup_original_dir.mkdir(parents=True, exist_ok=True)
    copied = 0
    for src in sources:
        dst = backup_original_dir / src.name
        if dst.exists() and not force:
            continue
        shutil.copy2(src, dst)
        copied += 1
    return copied


def restore_images(sources: list[Path], backup_original_dir: Path) -> int:
    restored = 0
    for src in sources:
        bak = backup_original_dir / src.name
        if not bak.is_file():
            print(f"  skip (no backup): {src.name}", file=sys.stderr)
            continue
        shutil.copy2(bak, src)
        restored += 1
    return restored


def resize_image(
    src: Path,
    dst: Path,
    target_w: int,
    target_h: int,
) -> None:
    with Image.open(src) as img:
        img = ImageOps.exif_transpose(img)
        if img.mode not in ("RGB", "RGBA"):
            img = img.convert("RGBA" if "A" in img.getbands() else "RGB")
        resized = img.resize((target_w, target_h), Image.Resampling.LANCZOS)
        dst.parent.mkdir(parents=True, exist_ok=True)
        save_kwargs: dict = {"format": "PNG", "optimize": True}
        if resized.mode == "RGBA":
            resized.save(dst, compress_level=9, **save_kwargs)
        else:
            resized.save(dst, compress_level=9, **save_kwargs)


def format_dim_change(w0: int, h0: int, w1: int, h1: int) -> str:
    return f"{w0}x{h0} -> {w1}x{h1}"


def main() -> None:
    p = argparse.ArgumentParser(
        description="Back up and resize Aetherhaven journal wiki images to power-of-two sizes.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument(
        "--wiki-dir",
        type=Path,
        default=DEFAULT_WIKI_DIR,
        help=f"Wiki PNG folder in the repo (default: {DEFAULT_WIKI_DIR.relative_to(REPO_ROOT)})",
    )
    p.add_argument(
        "--backup-dir",
        type=Path,
        default=DEFAULT_BACKUP_DIR,
        help=f"Backup root outside the repo (default: {DEFAULT_BACKUP_DIR})",
    )
    p.add_argument(
        "--max-long-edge",
        type=int,
        default=1024,
        help="Longest side after downscale, before POT snap (must be power of 2: 256, 512, 1024, …)",
    )
    p.add_argument(
        "--snap",
        choices=("nearest", "floor"),
        default="nearest",
        help="How to snap each side to a power of 2 after scaling (default: nearest)",
    )
    p.add_argument(
        "--backup-only",
        action="store_true",
        help="Only copy originals to BACKUP_DIR/original/; do not resize",
    )
    p.add_argument(
        "--restore",
        action="store_true",
        help="Copy originals from backup back into the wiki folder",
    )
    p.add_argument(
        "--force-backup",
        action="store_true",
        help="Overwrite files in BACKUP_DIR/original/ even if they already exist",
    )
    p.add_argument(
        "--dry-run",
        action="store_true",
        help="Print planned changes without writing wiki files",
    )
    p.add_argument(
        "--apply",
        action="store_true",
        help="Write resized PNGs into the wiki folder (implies backup first unless --no-backup)",
    )
    p.add_argument(
        "--no-backup",
        action="store_true",
        help="Skip backup step (not recommended)",
    )
    args = p.parse_args()

    wiki_dir = args.wiki_dir.resolve()
    backup_dir = args.backup_dir.resolve()
    backup_original = backup_dir / "original"

    if not is_power_of_two(args.max_long_edge):
        p.error(f"--max-long-edge must be a power of 2 (got {args.max_long_edge})")

    if not wiki_dir.is_dir():
        print(f"Wiki folder not found: {wiki_dir}", file=sys.stderr)
        raise SystemExit(2)

    images = iter_wiki_images(wiki_dir)
    if not images:
        print(f"No images in {wiki_dir}", file=sys.stderr)
        raise SystemExit(2)

    if args.restore:
        n = restore_images(images, backup_original)
        print(f"Restored {n} file(s) from {backup_original} -> {wiki_dir}")
        return

    do_backup = not args.no_backup
    if do_backup:
        n = backup_images(images, backup_original, force=args.force_backup)
        print(f"Backup: {backup_original}")
        print(f"  copied {n} new file(s), {len(images)} total in wiki folder")

    if args.backup_only:
        return

    if not args.apply and not args.dry_run:
        print("Nothing to do: pass --dry-run to preview or --apply to resize.", file=sys.stderr)
        raise SystemExit(2)

    total_before = 0
    total_after = 0
    print(f"\n{'DRY RUN' if args.dry_run else 'APPLY'}: max long edge {args.max_long_edge}, snap={args.snap}\n")

    for src in images:
        with Image.open(src) as img:
            img = ImageOps.exif_transpose(img)
            w, h = img.size

        tw, th, scale = compute_target_size(w, h, args.max_long_edge, snap=args.snap)
        before_bytes = src.stat().st_size
        total_before += before_bytes

        if tw == w and th == h and not args.dry_run:
            print(f"  {src.name}: {w}x{h} (unchanged)")
            total_after += before_bytes
            continue

        line = f"  {src.name}: {format_dim_change(w, h, tw, th)}"
        if scale < 1.0:
            line += f"  scale={scale:.3f}"
        if not is_power_of_two(w) or not is_power_of_two(h):
            line += "  [POT snap]"
        print(line)

        if args.dry_run:
            est = before_bytes * (tw * th) / max(1, w * h)
            total_after += int(est)
            print(f"           ~{before_bytes / 1024:.0f} KiB -> ~{est / 1024:.0f} KiB (estimate)")
            continue

        resize_image(src, src, tw, th)
        after_bytes = src.stat().st_size
        total_after += after_bytes
        print(f"           {before_bytes / 1024:.0f} KiB -> {after_bytes / 1024:.0f} KiB")

    print(
        f"\nTotal: {total_before / 1024 / 1024:.2f} MiB -> "
        f"{total_after / 1024 / 1024:.2f} MiB"
        + (" (estimate)" if args.dry_run else "")
    )


if __name__ == "__main__":
    main()
