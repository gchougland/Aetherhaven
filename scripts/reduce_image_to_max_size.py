#!/usr/bin/env python3
"""
Shrink an image so its encoded file size is at most a given limit (default 2 MiB).

Requires: pip install pillow

Strategy:
  - JPEG/WebP: lower quality in steps; if still too large, scale down and retry.
  - PNG: try optimize + compress_level; if still too large, scale down (PNG stays lossless
    so size drops mainly from fewer pixels).
"""

from __future__ import annotations

import argparse
import io
import sys
from pathlib import Path

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
        output_path = input_path.with_name(f"{stem}_under_{max_bytes // (1024 * 1024)}mb.jpg")

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
        # Default: JPEG for broad compatibility and size control
        fmt = "JPEG"
        if ext not in (".jpg", ".jpeg", ".webp", ".png"):
            output_path = output_path.with_suffix(".jpg")

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


def main() -> None:
    p = argparse.ArgumentParser(description="Reduce image file size to a maximum byte limit.")
    p.add_argument("input", type=Path, help="Source image path")
    p.add_argument(
        "-o",
        "--output",
        type=Path,
        default=None,
        help="Output path (default: <name>_under_Nmb.jpg next to input)",
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
        help="Output format (default: from output extension, else JPEG)",
    )
    p.add_argument(
        "--min-scale",
        type=float,
        default=0.05,
        help="Stop if dimensions would shrink below this fraction of original (default: 0.05)",
    )
    args = p.parse_args()

    max_bytes = int(args.max_mb * 1024 * 1024)
    prefer = args.format.upper() if args.format else None
    if prefer == "JPG":
        prefer = "JPEG"

    out = reduce_image(
        args.input.resolve(),
        args.output.resolve() if args.output else None,
        max_bytes,
        prefer_format=prefer,
        min_scale=args.min_scale,
    )
    size = out.stat().st_size
    print(f"Wrote {out} ({size / 1024:.1f} KiB, max was {max_bytes / 1024:.1f} KiB)")


if __name__ == "__main__":
    main()
