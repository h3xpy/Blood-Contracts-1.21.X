"""Generates the Blood Contracts GUI textures (deterministic). Usage: python make_gui_textures.py <output dir>"""
import math
import random
import sys

from PIL import Image

OUT = sys.argv[1]
rng = random.Random(1337)


def tileable_noise(size, cells, seed):
    """Value noise that wraps around both axes."""
    r = random.Random(seed)
    grid = [[r.random() for _ in range(cells)] for _ in range(cells)]

    def smooth(t):
        return t * t * (3 - 2 * t)

    out = [[0.0] * size for _ in range(size)]
    for y in range(size):
        gy = y * cells / size
        y0 = int(gy) % cells
        y1 = (y0 + 1) % cells
        ty = smooth(gy - int(gy))
        for x in range(size):
            gx = x * cells / size
            x0 = int(gx) % cells
            x1 = (x0 + 1) % cells
            tx = smooth(gx - int(gx))
            a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
            b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def parchment(size=256):
    octaves = [(4, 0.5), (8, 0.25), (16, 0.15), (64, 0.10)]
    layers = [tileable_noise(size, c, 100 + i) for i, (c, _) in enumerate(octaves)]
    stains = tileable_noise(size, 6, 7)
    img = Image.new("RGBA", (size, size))
    px = img.load()
    base = (226, 206, 160)
    for y in range(size):
        for x in range(size):
            n = sum(layers[i][y][x] * w for i, (_, w) in enumerate(octaves))  # ~0..1
            shade = (n - 0.5) * 46
            stain = max(0.0, stains[y][x] - 0.62) * 120  # a few darker blotches
            grain = rng.uniform(-5, 5)
            r = base[0] + shade - stain * 0.9 + grain
            g = base[1] + shade - stain * 1.05 + grain
            b = base[2] + shade - stain * 1.25 + grain
            px[x, y] = (clamp(r), clamp(g), clamp(b), 255)
    return img


def clamp(v):
    return max(0, min(255, int(round(v))))


def blood_drop(size=16):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    cx, cy, rad = 7.5, 10.0, 4.6
    for y in range(size):
        for x in range(size):
            # circle bottom + pointed top
            dx, dy = x - cx, y - cy
            inside = dx * dx + dy * dy <= rad * rad
            if not inside and y < cy:
                half = rad * (y - 1.5) / (cy - 1.5)
                inside = y >= 1.5 and abs(dx) <= half
            if inside:
                light = max(0.0, 1 - math.hypot(x - 6, y - 8) / 6)
                r = 120 + 110 * light
                px[x, y] = (clamp(r), clamp(10 + 30 * light), clamp(12 + 30 * light), 255)
    # dark outline
    out = img.copy()
    opx = out.load()
    for y in range(size):
        for x in range(size):
            if px[x, y][3] == 0 and any(
                0 <= x + ox < size and 0 <= y + oy < size and px[x + ox, y + oy][3] > 0
                for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            ):
                opx[x, y] = (40, 0, 0, 255)
    return out


parchment().save(OUT + "/parchment.png")
blood_drop().save(OUT + "/blood_drop.png")
print("textures written to", OUT)
