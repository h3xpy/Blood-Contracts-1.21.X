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


def poster(w=128, h=176):
    """Aged wanted poster: parchment, torn and burnt edges, darker corners, a nail at the top."""
    paper = parchment(256)
    ppx = paper.load()
    edge = random.Random(42)
    # Torn outline: per-column / per-row inset depth.
    top = [edge.randint(0, 3) for _ in range(w)]
    bottom = [edge.randint(0, 4) for _ in range(w)]
    left = [edge.randint(0, 3) for _ in range(h)]
    right = [edge.randint(0, 3) for _ in range(h)]
    for arr in (top, bottom, left, right):
        for i in range(1, len(arr) - 1):
            arr[i] = round((arr[i - 1] + arr[i] * 2 + arr[i + 1]) / 4)
    # A couple of bigger tears.
    for arr, n in ((bottom, 2), (right, 1), (left, 1)):
        for _ in range(n):
            c = edge.randrange(8, len(arr) - 8)
            for i in range(-4, 5):
                arr[c + i] = max(arr[c + i], 7 - abs(i))
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            d = min(y - top[x], h - 1 - y - bottom[x], x - left[y], w - 1 - x - right[y])
            if d < 0:
                continue
            r, g, b, _ = ppx[(x * 2) % 256, (y * 2 + 40) % 256]
            # yellowed, burnt rim
            burn = max(0.0, 1 - d / 7.0)
            cx, cy = (x - w / 2) / (w / 2), (y - h / 2) / (h / 2)
            vignette = min(1.0, (cx * cx + cy * cy) * 0.35)
            k = 1 - 0.55 * burn - 0.25 * vignette
            r, g, b = r * k + 30 * burn, g * k, b * k * 0.9
            if d == 0:
                r, g, b = r * 0.55, g * 0.45, b * 0.4
            px[x, y] = (clamp(r), clamp(g), clamp(b), 255)
    # nail at the top centre
    nx, ny = w // 2, 6
    for y in range(ny - 3, ny + 4):
        for x in range(nx - 3, nx + 4):
            dist = math.hypot(x - nx, y - ny)
            if dist <= 3.2:
                light = max(0.0, 1 - math.hypot(x - nx + 1, y - ny + 1) / 3.5)
                v = 70 + 120 * light
                px[x, y] = (clamp(v), clamp(v * 0.95), clamp(v * 0.9), 255)
            elif dist <= 4.2 and px[x, y][3] > 0:
                r, g, b, a = px[x, y]
                px[x, y] = (clamp(r * 0.6), clamp(g * 0.55), clamp(b * 0.5), a)
    return img


parchment().save(OUT + "/parchment.png")
poster().save(OUT + "/poster.png")
blood_drop().save(OUT + "/blood_drop.png")
print("textures written to", OUT)
