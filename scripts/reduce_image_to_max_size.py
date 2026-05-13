#!/usr/bin/env python3
"""
Shrink image(s) so encoded file size is at most a given limit (default 2 MiB).

Requires: pip install pillow

Single file (INPUT = path to image):
  Default output: ``<stem>_under_<N>mb.png`` next to the input (PNG). Use ``-o`` for a path.

Directory (INPUT = folder):
  Processes every image file in that folder (non-recursive). Writes to ``INPUT/<subdir>/``
  using the **same filenames** as the sources (e.g. ``INPUT/a.png`` -> ``INPUT/reduced/a.png``).
  Skips subfolders. Do not use ``-o``; set the output subfolder with ``--subdir``.

  **Windows / PowerShell:** if the path contains spaces, you must quote it, or the shell will
  split it into multiple arguments and the path will be wrong. Example::

    python reduce_image_to_max_size.py "C:\\Users\\you\\Pictures\\Hytale Screenshots\\Wiki_Pictures"

  Or use ``-i`` / ``--input`` (same quoting rules)::

    python reduce_image_to_max_size.py -i "C:\\Users\\you\\Pictures\\Hytale Screenshots\\Wiki_Pictures"

Strategy:
  - JPEG/WebP: lower quality in steps; if still too large, scale down and retry.
  - PNG: try optimize + compress_level; if still too large, scale down (PNG stays lossless
    so size drops mainly from fewer pixels).

Journal wiki markdown references ``wiki/*.png``; keep that extension or pass ``-o .../wiki/foo.png``. JPEG
still works in-game if the markdown URL uses ``.jpg``, but PNG avoids lossy compression and
alpha flattening.
"""

from __future__ import annotations

import argparse
import io
import os
import sys
from pathlib import Path

IMAGE_EXTENSIONS = frozenset(
    {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tif", ".tiff", ".gif"}
)

try:
    from PIL import Image, ImageOps
except ImportError as e:
    print("Install Pillow: pip install pillow", file=sys.stderr)
    raise SystemExit(1) from e

def _bytes_size(buf: io.BytesIO) -> int:
    return len(buf.getvalue())


def _jpeg_size_rgb(im_rgb: Image.Image, quality: int, progressive: bool = True) -> int:
    buf = io.BytesIO()
    im_rgb.save(buf, format="JPEG", quality=quality, optimize=True, progressive=progressive)
    return _bytes_size(buf)


def _jpeg_size(img: Image.Image, quality: int, progressive: bool = True) -> int:
    rgb = ImageOps.exif_transpose(img).convert("RGB")
    return _jpeg_size_rgb(rgb, quality, progressive=progressive)


def _webp_size(img: Image.Image, quality: int) -> int:
    buf = io.BytesIO()
    im = ImageOps.exif_transpose(img)
    im.save(buf, format="WEBP", quality=quality, method=6)
    return _bytes_size(buf)


def _png_size(img: Image.Image, compress_level: int = 9) -> int:
    buf = io.BytesIO()
    im = ImageOps.exif_transpose(img)
    im.save(buf, format="PNG", optimize=True, compress_level=compress_level)
    return _bytes_size(buf)


def _best_jpeg_quality_under_limit(im_rgb: Image.Image, max_bytes: int) -> int | None:
    """Return highest JPEG quality (5–95) with size <= max_bytes, or None if even q=5 is too big."""
    if _jpeg_size_rgb(im_rgb, 5) > max_bytes:
        return None
    lo, hi = 5, 95
    best = 5
    while lo <= hi:
        mid = (lo + hi) // 2
        if _jpeg_size_rgb(im_rgb, mid) <= max_bytes:
            best = mid
            lo = mid + 1
        else:
            hi = mid - 1
    return best


def _best_webp_quality_under_limit(im: Image.Image, max_bytes: int) -> int | None:
    if _webp_size(im, 5) > max_bytes:
        return None
    lo, hi = 5, 95
    best = 5
    while lo <= hi:
        mid = (lo + hi) // 2
        if _webp_size(im, mid) <= max_bytes:
            best = mid
            lo = mid + 1
        else:
            hi = mid - 1
    return best


def reduce_image(
    input_path: Path,
    output_path: Path | None,
    max_bytes: int,
    prefer_format: str | None,
    min_scale: float,
) -> Path:
    img = Image.open(input_path)
    img.load()

    if output_path is None:
        stem = input_path.stem
        mb = max(1, max_bytes // (1024 * 1024))
        output_path = input_path.with_name(f"{stem}_under_{mb}mb.png")

    # Choose output format
    ext = output_path.suffix.lower()
    if prefer_format:
        fmt = prefer_format.upper()
        if fmt == "JPG":
            fmt = "JPEG"
    elif ext in (".jpg", ".jpeg"):
        fmt = "JPEG"
    elif ext == ".webp":
        fmt = "WEBP"
    elif ext == ".png":
        fmt = "PNG"
    else:
        # Unknown extension: default to PNG (lossless, matches Common/Docs wiki image paths)
        fmt = "PNG"
        if ext and ext not in (".jpg", ".jpeg", ".webp", ".png"):
            output_path = output_path.with_suffix(".png")

    base = ImageOps.exif_transpose(img)
    working = base
    min_dim_orig = min(base.width, base.height)

    while True:
        if fmt == "JPEG":
            im_rgb = working.convert("RGB")
            q = _best_jpeg_quality_under_limit(im_rgb, max_bytes)
            if q is not None:
                buf = io.BytesIO()
                im_rgb.save(buf, format="JPEG", quality=q, optimize=True, progressive=True)
                output_path.parent.mkdir(parents=True, exist_ok=True)
                output_path.write_bytes(buf.getvalue())
                return output_path
        elif fmt == "WEBP":
            im_w = working
            q = _best_webp_quality_under_limit(im_w, max_bytes)
            if q is not None:
                buf = io.BytesIO()
                im_w.save(buf, format="WEBP", quality=q, method=6)
                output_path.parent.mkdir(parents=True, exist_ok=True)
                output_path.write_bytes(buf.getvalue())
                return output_path
        else:
            # PNG: optimize; if too big, only scaling helps meaningfully
            im = working
            for cl in (9, 6, 3):
                sz = _png_size(im, compress_level=cl)
                if sz <= max_bytes:
                    buf = io.BytesIO()
                    im.save(buf, format="PNG", optimize=True, compress_level=cl)
                    output_path.parent.mkdir(parents=True, exist_ok=True)
                    output_path.write_bytes(buf.getvalue())
                    return output_path

        w = max(1, int(working.width * 0.85))
        h = max(1, int(working.height * 0.85))
        if min(w, h) < min_dim_orig * min_scale:
            break
        working = working.resize((w, h), Image.Resampling.LANCZOS)

    raise RuntimeError(
        f"Could not get image under {max_bytes} bytes (stopped at {working.width}x{working.height}). "
        "Try a lower --min-scale or use JPEG/WEBP output."
    )


def iter_images_in_directory(directory: Path) -> list[Path]:
    """Image files directly under ``directory`` (non-recursive), sorted by name."""
    out: list[Path] = []
    for p in sorted(directory.iterdir()):
        if not p.is_file():
            continue
        if p.name.startswith("."):
            continue
        if p.suffix.lower() not in IMAGE_EXTENSIONS:
            continue
        out.append(p)
    return out


def reduce_directory(
    input_dir: Path,
    *,
    subdir: str,
    max_bytes: int,
    min_scale: float,
) -> None:
    name = subdir.strip()
    if not name or name in (".", ".."):
        raise SystemExit("--subdir must be a non-empty folder name.")
    if "/" in name or "\\" in name or Path(name).is_absolute():
        raise SystemExit("--subdir must be a single folder name (no path separators).")
    out_root = (input_dir / name).resolve()
    if out_root == input_dir.resolve():
        raise SystemExit("--subdir must not be the same as the input directory.")
    out_root.mkdir(parents=True, exist_ok=True)
    files = iter_images_in_directory(input_dir)
    if not files:
        print(f"No image files found in {input_dir}", file=sys.stderr)
        raise SystemExit(2)
    for src in files:
        dst = out_root / src.name
        reduce_image(src, dst, max_bytes, prefer_format=None, min_scale=min_scale)
        sz = dst.stat().st_size
        print(f"{src.name} -> {dst} ({sz / 1024:.1f} KiB)")


def _parse_input_path(raw: str) -> Path:
    """Strip shell quotes, expand %VAR% / ~, return a pathlib.Path (not necessarily resolved)."""
    t = raw.strip().strip('"').strip("'")
    t = os.path.expandvars(os.path.expanduser(t))
    return Path(t)


def main() -> None:
    p = argparse.ArgumentParser(
        description="Reduce image file size to a maximum byte limit.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Paths with spaces (common under Pictures\\) must be wrapped in double quotes in PowerShell/cmd.",
    )
    p.add_argument(
        "input_pos",
        nargs="?",
        type=str,
        default=None,
        metavar="INPUT",
        help="Source image file or directory (quote if the path contains spaces)",
    )
    p.add_argument(
        "-i",
        "--input",
        dest="input_opt",
        type=str,
        default=None,
        help="Same as positional INPUT; use when quoting a path with spaces is clearer",
    )
    p.add_argument(
        "-o",
        "--output",
        type=Path,
        default=None,
        help="Output path when INPUT is a file (default: <name>_under_Nmb.png next to input). Not used for directories.",
    )
    p.add_argument(
        "--subdir",
        default="reduced",
        help="When INPUT is a directory: write into INPUT/SUBDIR/ using the same filenames (default: reduced).",
    )
    p.add_argument(
        "-m",
        "--max-mb",
        type=float,
        default=2.0,
        help="Maximum file size in MiB (default: 2)",
    )
    p.add_argument(
        "-f",
        "--format",
        choices=("jpeg", "jpg", "webp", "png"),
        default=None,
        help="Output format when INPUT is a file (default: from output extension; unknown ext -> PNG). Ignored for directories.",
    )
    p.add_argument(
        "--min-scale",
        type=float,
        default=0.05,
        help="Stop if dimensions would shrink below this fraction of original (default: 0.05)",
    )
    args = p.parse_args()

    chosen = args.input_opt or args.input_pos
    if not chosen:
        p.error("Missing INPUT: pass the path as an argument or use -i/--input \"...path...\"")
    if args.input_opt and args.input_pos:
        print("Both positional INPUT and -i/--input were given; using -i/--input.", file=sys.stderr)

    inp = _parse_input_path(args.input_opt or args.input_pos)

    if not inp.exists():
        print(f"Not found: {inp}", file=sys.stderr)
        if sys.platform == "win32":
            print(
                "Hint (Windows): Paths with spaces must be quoted for the shell, e.g.",
                file=sys.stderr,
            )
            print(
                f'  python "{Path(sys.argv[0]).resolve()}" "{inp}"',
                file=sys.stderr,
            )
        par = inp.parent
        if par.exists() and par.is_dir():
            print(f"Hint: Parent folder exists: {par}", file=sys.stderr)
            try:
                names = sorted(x.name for x in par.iterdir() if x.is_dir())
                if names:
                    print(f"  Subfolders there: {', '.join(names[:20])}", file=sys.stderr)
            except OSError:
                pass
        raise SystemExit(2)

    inp = inp.resolve()
    max_bytes = int(args.max_mb * 1024 * 1024)
    prefer = args.format.upper() if args.format else None
    if prefer == "JPG":
        prefer = "JPEG"

    if inp.is_dir():
        if args.output is not None:
            print("Directory mode: omit -o/--output (outputs go under INPUT/--subdir/).", file=sys.stderr)
            raise SystemExit(2)
        if args.format:
            print(
                "Directory mode: ignoring --format (each output keeps the source file extension).",
                file=sys.stderr,
            )
        reduce_directory(inp, subdir=args.subdir, max_bytes=max_bytes, min_scale=args.min_scale)
        return

    if not inp.is_file():
        print(f"Not a file or directory: {inp}", file=sys.stderr)
        raise SystemExit(2)

    out = reduce_image(
        inp,
        args.output.resolve() if args.output else None,
        max_bytes,
        prefer_format=prefer,
        min_scale=args.min_scale,
    )
    size = out.stat().st_size
    print(f"Wrote {out} ({size / 1024:.1f} KiB, max was {max_bytes / 1024:.1f} KiB)")


if __name__ == "__main__":
    main()
