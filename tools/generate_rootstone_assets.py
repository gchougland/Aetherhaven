"""Paints the Rootstone block textures and its inventory icon. Pure standard library.

The look follows the game's stone blocks: large soft rock shapes with dark seams, a light edge toward
the upper left, carved lines with a bevel, and bright moss with a dark underside.

Run from the project root: python tools/generate_rootstone_assets.py
"""
import math
import os
import random
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "Common")
SIZE = 32

# Sampled from the game's plain and mossy stone so the block sits naturally beside them.
STONE = (122, 121, 90)
STONE_LIGHT = (141, 140, 106)
STONE_SHADE = (104, 103, 77)
SEAM = (88, 87, 66)
POLISHED = (134, 134, 104)
MOSS = (96, 146, 38)
MOSS_LIGHT = (136, 186, 52)
MOSS_DARK = (58, 96, 32)
ROOT_WOOD = (98, 68, 42)
ROOT_LIGHT = (136, 100, 62)
ROOT_DARK = (62, 42, 28)
GLOW_EDGE = (98, 160, 52)
GLOW = (170, 236, 98)
GLOW_CORE = (234, 255, 176)


def write_png(path, width, height, pixels):
    """pixels: rows of (r, g, b, a) tuples."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as out:
        out.write(b"\x89PNG\r\n\x1a\n")
        out.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)))
        out.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        out.write(chunk(b"IEND", b""))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color)


def rocks(seed, columns, rows):
    """Painted rock shapes: flat tones per rock, dark seams, light upper left edges, soft lower right shade."""
    rng = random.Random(seed)
    seeds = []
    for gy in range(rows):
        for gx in range(columns):
            seeds.append(((gx + 0.5 + rng.uniform(-0.32, 0.32)) * SIZE / columns,
                          (gy + 0.5 + rng.uniform(-0.32, 0.32)) * SIZE / rows,
                          rng.uniform(-0.05, 0.05)))

    def nearest(x, y):
        best, second, which = 1e9, 1e9, 0
        for i, (sx, sy, _) in enumerate(seeds):
            d = math.hypot(x - sx, y - sy)
            if d < best:
                best, second, which = d, best, i
            elif d < second:
                second = d
        return which, second - best

    grid = [[STONE] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            cell, gap = nearest(x, y)
            tone = shade(STONE, 1 + seeds[cell][2])
            if gap < 0.9:
                color = mix(tone, SEAM, 0.8)
            elif gap < 1.7:
                color = mix(tone, SEAM, 0.3)
            elif nearest(x - 1.4, y - 1.4)[0] != cell:
                color = mix(tone, STONE_LIGHT, 0.75)
            elif nearest(x + 1.4, y + 1.4)[0] != cell:
                color = mix(tone, STONE_SHADE, 0.7)
            else:
                # A gentle roll of light across each rock keeps the flats from looking printed.
                sx, sy, _ = seeds[cell]
                color = mix(tone, STONE_LIGHT, max(0.0, ((sx - x) + (sy - y)) * 0.035))
            grid[y][x] = color
    return grid


def put(grid, x, y, color, amount=1.0):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        grid[y][x] = mix(grid[y][x], color, amount)


def moss(grid, rng, blobs):
    """Bright clumps with a dark lower right rim, like the game's mossy stone."""
    cover = [[0.0] * SIZE for _ in range(SIZE)]
    for cx, cy, radius in blobs:
        for y in range(SIZE):
            for x in range(SIZE):
                # Ragged edges: each clump is pulled in and out around its rim like real growth.
                angle = math.atan2(y - cy, x - cx)
                wobble = math.sin(angle * 3 + cx) * 0.9 + math.sin(angle * 5 + cy * 2) * 0.6 + rng.uniform(-0.35, 0.35)
                d = math.hypot(x - cx, y - cy) + wobble
                if d < radius:
                    cover[y][x] = max(cover[y][x], 1 - d / radius)

    def inside(x, y):
        return 0 <= x < SIZE and 0 <= y < SIZE and cover[y][x] > 0

    for y in range(SIZE):
        for x in range(SIZE):
            if not inside(x, y):
                # Cast a thin shadow on the stone below and to the right of each clump.
                if inside(x - 1, y - 1):
                    put(grid, x, y, SEAM, 0.45)
                continue
            if not inside(x + 1, y + 1) or not inside(x, y + 1):
                color = MOSS_DARK
            elif not inside(x - 1, y - 1) or not inside(x, y - 1):
                color = MOSS_LIGHT
            else:
                color = mix(MOSS, MOSS_LIGHT, cover[y][x] * 0.5)
                if rng.random() < 0.12:
                    color = MOSS_LIGHT
                elif rng.random() < 0.1:
                    color = mix(MOSS, MOSS_DARK, 0.6)
            grid[y][x] = color


def inlay(grid, cells):
    """A carved channel filled with light: dark cut above and left, soft green spill on the stone around it."""
    cells = set(cells)
    for x, y in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in cells:
                    put(grid, x + dx, y + dy, GLOW_EDGE, 0.22)
    for x, y in cells:
        if (x - 1, y) not in cells or (x, y - 1) not in cells:
            put(grid, x - 1, y - 1, SEAM, 0.35)
    for x, y in cells:
        neighbours = sum((x + dx, y + dy) in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        put(grid, x, y, GLOW_CORE if neighbours >= 3 else GLOW)


def ring(cx, cy, radius, gaps=()):
    cells = []
    for step in range(1440):
        angle = step * math.pi / 720
        degrees = math.degrees(angle) % 360
        if any(abs((degrees - gap + 180) % 360 - 180) < 9 for gap in gaps):
            continue
        cells.append((int(round(cx + math.cos(angle) * radius)), int(round(cy + math.sin(angle) * radius))))
    return cells


def sprout(cx, cy):
    """A two leafed seedling, the mark of the grove."""
    cells = [(cx, y) for y in range(cy - 1, cy + 6)] + [(cx - 1, y) for y in range(cy + 1, cy + 6)]
    for i, (dx, dy) in enumerate(((1, -2), (2, -3), (3, -4), (4, -4), (5, -3), (4, -2), (3, -2), (2, -2))):
        cells.append((cx + dx, cy + dy))
    for dx, dy in ((-2, 0), (-3, -1), (-4, -2), (-5, -2), (-6, -1), (-5, 0), (-4, 0), (-3, 0)):
        cells.append((cx + dx, cy + dy))
    return cells


def top():
    rng = random.Random(41)
    grid = rocks(11, 4, 4)
    c = 15.5
    # A smooth worked disc set into the rough stone, lit from the upper left.
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - c, y - c)
            if d < 12.2:
                light = ((c - x) + (c - y)) / 24.0
                grid[y][x] = mix(POLISHED, STONE_LIGHT if light > 0 else STONE_SHADE, abs(light) * 0.55)
            if 12.2 <= d < 13.3:
                # The groove around the disc: dark where it faces the light, bright on its far lip.
                grid[y][x] = SEAM if (x - c) + (y - c) < 4 else mix(SEAM, STONE_SHADE, 0.5)
            elif 13.3 <= d < 14.2 and (x - c) + (y - c) > 6:
                grid[y][x] = STONE_LIGHT
    inlay(grid, ring(c, c, 9.0, gaps=(45, 135, 225, 315)) + sprout(16, 14))
    moss(grid, rng, ((1.5, 2.0, 5.2), (6.5, 0.5, 3.0), (0.5, 8.5, 2.8), (30.0, 1.5, 4.2), (25.0, 0.0, 2.4),
                     (1.5, 30.0, 4.4), (0.0, 23.5, 2.4), (29.5, 29.0, 5.6), (22.5, 31.0, 3.0), (31.5, 21.5, 2.8),
                     (31.0, 11.0, 2.2), (13.0, 31.5, 2.0)))
    return grid


def root(grid, points):
    """A gnarled root drawn as a chain of short strokes with a light left side and a dark right side."""
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        steps = max(abs(x1 - x0), abs(y1 - y0)) * 2 + 1
        for i in range(steps + 1):
            t = i / steps
            x, y = int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t))
            put(grid, x + 2, y + 1, SEAM, 0.5)
            put(grid, x + 1, y, ROOT_DARK)
            put(grid, x, y, ROOT_WOOD)
            put(grid, x - 1, y, ROOT_LIGHT)


def side():
    rng = random.Random(77)
    grid = rocks(23, 3, 4)
    # The lip of the worked top, then its shadow.
    for x in range(SIZE):
        grid[0][x] = STONE_LIGHT
        grid[1][x] = mix(POLISHED, STONE_LIGHT, 0.4)
        grid[2][x] = SEAM
        put(grid, x, 3, SEAM, 0.35)
    # The block darkens toward the ground it sits in.
    for y in range(SIZE - 6, SIZE):
        for x in range(SIZE):
            put(grid, x, y, SEAM, (y - (SIZE - 7)) * 0.07)
    trunk = [(16, y) for y in range(13, 25)] + [(15, y) for y in range(15, 25)]
    branches = [(16 - i, 17 - i) for i in range(1, 5)] + [(16 + i, 17 - i) for i in range(1, 5)]
    branches += [(15 - i, 17 - i) for i in range(1, 4)] + [(17 + i, 17 - i) for i in range(1, 4)]
    feet = [(14 - i, 24 + min(i, 1)) for i in range(0, 4)] + [(17 + i, 24 + min(i, 1)) for i in range(0, 4)]
    inlay(grid, trunk + branches + feet)
    root(grid, ((4, 0), (5, 5), (3, 10), (5, 16), (4, 22), (6, 27), (5, 31)))
    root(grid, ((27, 0), (26, 4), (28, 9), (27, 14), (29, 19)))
    root(grid, ((5, 16), (8, 19), (9, 23)))
    moss(grid, rng, ((1.0, 0.5, 4.6), (9.5, -0.5, 4.0), (16.5, 0.0, 5.4), (23.0, -1.0, 3.6), (30.0, 0.5, 5.0),
                     (12.5, 4.0, 2.2), (20.0, 5.0, 2.4), (31.0, 6.0, 2.2)))
    return grid


def bottom():
    grid = rocks(5, 3, 3)
    for y in range(SIZE):
        for x in range(SIZE):
            put(grid, x, y, SEAM, 0.25)
    return grid


def opaque(grid):
    return [[p + (255,) for p in row] for row in grid]


def icon(top_face, side_face):
    """An isometric cube lit from the upper left, drawn by sampling each face for every icon pixel."""
    size = 64
    pixels = [[(0, 0, 0, 0)] * size for _ in range(size)]
    cx, half, rise, tall, oy = 32.0, 27.0, 13.5, 29.0, 4.0

    def sample(face, u, v, factor):
        return shade(face[min(SIZE - 1, int(v * SIZE))][min(SIZE - 1, int(u * SIZE))], factor) + (255,)

    for py in range(size):
        for px in range(size):
            x, y = px + 0.5 - cx, py + 0.5 - oy
            # Top face: a rhombus with corners at (0,0), (half,rise), (0,2*rise), (-half,rise).
            u, v = (x / half + y / rise) / 2, (y / rise - x / half) / 2
            if 0 <= u < 1 and 0 <= v < 1:
                pixels[py][px] = sample(top_face, u, v, 1.06)
                continue
            if -half <= x < 0:
                u = (x + half) / half
                v = (y - rise - u * rise) / tall
                if 0 <= v < 1:
                    pixels[py][px] = sample(side_face, u, v, 0.86)
            elif 0 <= x < half:
                u = x / half
                v = (y - 2 * rise + u * rise) / tall
                if 0 <= v < 1:
                    pixels[py][px] = sample(side_face, u, v, 0.66)
    return pixels


def main():
    top_face, side_face = top(), side()
    textures = os.path.join(ROOT, "BlockTextures")
    write_png(os.path.join(textures, "Aetherhaven_Rootstone_Top.png"), SIZE, SIZE, opaque(top_face))
    write_png(os.path.join(textures, "Aetherhaven_Rootstone_Side.png"), SIZE, SIZE, opaque(side_face))
    write_png(os.path.join(textures, "Aetherhaven_Rootstone_Bottom.png"), SIZE, SIZE, opaque(bottom()))
    write_png(os.path.join(ROOT, "Icons", "ItemsGenerated", "Aetherhaven_Rootstone.png"), 64, 64, icon(top_face, side_face))


if __name__ == "__main__":
    main()
