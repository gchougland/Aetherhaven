"""Renders the plot token thumbnail for a festival prefab.

Reads a prefab JSON, takes the top solid block of every column, and draws a small isometric view using a
block-name colour palette. Output is a 64x64 RGBA PNG matching the other Aetherhaven_Token_*.png icons.

Usage: python tools/render_festival_token_icon.py <prefab.json> <out.png>
"""

import json
import sys
from PIL import Image, ImageDraw

PALETTE = [
    ("Rock_Crystal_Cyan", (96, 214, 224)),
    ("Rock_Stone_Brick_Ornate", (176, 168, 148)),
    ("Rock_Stone_Brick_Smooth", (162, 160, 156)),
    ("Rock_Stone_Brick_Mossy", (120, 136, 108)),
    ("Rock_Stone_Brick", (150, 148, 144)),
    ("Rock_Stone_Cobble_Mossy", (108, 124, 100)),
    ("Rock_Stone_Cobble", (132, 130, 126)),
    ("Furniture_Castle_Bench", (128, 92, 58)),
    ("Furniture_Tavern_Planter", (96, 70, 46)),
    ("Furniture_Village_Statue", (188, 184, 176)),
    ("Plant_Flower_Common_Cyan", (110, 208, 216)),
    ("Plant_Flower_Common_Pink", (214, 132, 170)),
    ("Plant_Flower_Common_Yellow", (226, 200, 96)),
    ("Plant_Flower_Common_Orange", (222, 148, 74)),
    ("Plant_Flower_Common_White", (232, 230, 226)),
    ("Plant_Flower_Common_Grey", (176, 176, 180)),
    ("Plant_Flower_Flax_Pink", (216, 140, 176)),
    ("Plant_Flower_Flax_Yellow", (228, 204, 100)),
    ("Plant_Flower", (200, 190, 120)),
    ("Plant_Grass", (108, 152, 78)),
    ("Plant_Crop_Lettuce", (124, 186, 96)),
    ("Plant_Crop", (128, 176, 92)),
    ("Wood", (124, 92, 58)),
    ("Soil_Grass", (98, 146, 74)),
    ("Soil_Pathway", (142, 122, 96)),
    ("Soil", (118, 92, 66)),
]

DEFAULT_COLOR = (150, 148, 144)

SIZE = 64
CELLS = 15
TILE_W = 4
TILE_H = 2
LEVEL_H = 2


def color_for(name):
    for prefix, rgb in PALETTE:
        if name.startswith(prefix):
            return rgb
    return DEFAULT_COLOR


def shade(rgb, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in rgb)


def load_top_surface(prefab_path):
    with open(prefab_path, encoding="utf-8") as handle:
        prefab = json.load(handle)
    blocks = [b for b in prefab["blocks"] if b.get("name") not in (None, "Empty")]
    xs = [b["x"] for b in blocks]
    zs = [b["z"] for b in blocks]
    min_x, max_x = min(xs), max(xs)
    min_z, max_z = min(zs), max(zs)
    top = {}
    for b in blocks:
        key = (b["x"], b["z"])
        current = top.get(key)
        if current is None or b["y"] > current[0]:
            top[key] = (b["y"], b["name"])
    step_x = (max_x - min_x + 1) / CELLS
    step_z = (max_z - min_z + 1) / CELLS
    grid = []
    for cz in range(CELLS):
        row = []
        for cx in range(CELLS):
            sx = min_x + int(cx * step_x)
            sz = min_z + int(cz * step_z)
            best = None
            for ox in range(max(1, int(step_x))):
                for oz in range(max(1, int(step_z))):
                    cell = top.get((sx + ox, sz + oz))
                    if cell is not None and (best is None or cell[0] > best[0]):
                        best = cell
            row.append(best)
        grid.append(row)
    return grid


def render(grid, out_path):
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    heights = [cell[0] for row in grid for cell in row if cell]
    base_y = min(heights) if heights else 0
    origin_x = SIZE // 2
    origin_y = 14
    for cz in range(CELLS):
        for cx in range(CELLS):
            cell = grid[cz][cx]
            if cell is None:
                continue
            level = cell[0] - base_y
            sx = origin_x + (cx - cz) * TILE_W
            sy = origin_y + (cx + cz) * TILE_H - level * LEVEL_H
            rgb = color_for(cell[1])
            top_face = [
                (sx, sy),
                (sx + TILE_W, sy + TILE_H),
                (sx, sy + TILE_H * 2),
                (sx - TILE_W, sy + TILE_H),
            ]
            draw.polygon(top_face, fill=shade(rgb, 1.0) + (255,))
            left_face = [
                (sx - TILE_W, sy + TILE_H),
                (sx, sy + TILE_H * 2),
                (sx, sy + TILE_H * 2 + LEVEL_H + 2),
                (sx - TILE_W, sy + TILE_H + LEVEL_H + 2),
            ]
            draw.polygon(left_face, fill=shade(rgb, 0.7) + (255,))
            right_face = [
                (sx + TILE_W, sy + TILE_H),
                (sx, sy + TILE_H * 2),
                (sx, sy + TILE_H * 2 + LEVEL_H + 2),
                (sx + TILE_W, sy + TILE_H + LEVEL_H + 2),
            ]
            draw.polygon(right_face, fill=shade(rgb, 0.85) + (255,))
    image.save(out_path)


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 1
    grid = load_top_surface(sys.argv[1])
    render(grid, sys.argv[2])
    print("wrote", sys.argv[2])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
