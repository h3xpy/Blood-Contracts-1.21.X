"""Generates the Blood Contracts promotional images (logo, header, feature cards) for CurseForge.

Usage: python make_promo.py <vanilla textures dir> <output dir> <in-jar logo path>
Fonts: Playbill (titles) and Rockwell (text), shipped with Windows.
"""
import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

TEX, OUT, JAR_LOGO = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(OUT, exist_ok=True)
FONTS = "C:/Windows/Fonts/"
rng = random.Random(42)

BLOOD = (142, 14, 14)
BLOOD_DARK = (90, 6, 6)
INK = (59, 36, 18)
INK_LIGHT = (107, 74, 42)
PARCH = (226, 206, 160)
GOLD = (122, 74, 0)
CREAM = (244, 230, 200)


def font(name, size):
    return ImageFont.truetype(FONTS + name, size)


def playbill(size):
    return font("PLAYBILL.TTF", size)


def rock(size, bold=False):
    return font("ROCKB.TTF" if bold else "ROCK.TTF", size)


def noise(w, h, cell, seed):
    r = random.Random(seed)
    small = Image.new("L", (max(2, w // cell), max(2, h // cell)))
    small.putdata([r.randint(0, 255) for _ in range(small.width * small.height)])
    return small.resize((w, h), Image.BICUBIC)


def parchment(w, h, seed=1):
    base = Image.new("RGB", (w, h), PARCH)
    n = Image.new("L", (w, h), 128)
    for cell, weight, s in ((160, 0.45, 1), (60, 0.3, 2), (18, 0.18, 3), (4, 0.07, 4)):
        n = Image.blend(n, noise(w, h, cell, seed * 10 + s), weight)
    dark = Image.new("RGB", (w, h), (150, 110, 60))
    shade = n.point(lambda v: max(0, min(255, int((128 - v) * 2.2 + 40))))
    img = Image.composite(dark, base, shade)
    stains = noise(w, h, 90, seed * 10 + 9).point(lambda v: 255 if v > 205 else 0).filter(ImageFilter.GaussianBlur(14))
    img = Image.composite(Image.new("RGB", (w, h), (170, 130, 80)), img, stains.point(lambda v: v // 3))
    return img


def burnt_edges(img, depth=40, seed=3):
    """Darkens and roughens the borders like singed paper."""
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    for i in range(depth):
        a = int(255 * (1 - i / depth) ** 2.2)
        d.rectangle([i, i, w - 1 - i, h - 1 - i], outline=a)
    rough = noise(w, h, 9, seed).point(lambda v: v)
    mask = ImageChops.multiply(mask, rough.point(lambda v: 140 + v // 2))
    burnt = Image.new("RGB", (w, h), (55, 30, 12))
    return Image.composite(burnt, img, mask)


def wood(w, h, seed=5):
    base = Image.new("RGB", (w, h), (52, 32, 16))
    grain = noise(w, h // 1 + 1, 6, seed).resize((w, h)).transform((w, h), Image.AFFINE, (1, 0.04, 0, 0, 0.15, 0)).filter(ImageFilter.GaussianBlur(1))
    light = Image.new("RGB", (w, h), (92, 60, 32))
    img = Image.composite(light, base, grain.point(lambda v: int(v * 0.8)))
    # planks
    d = ImageDraw.Draw(img)
    for y in range(0, h, 120):
        d.line([(0, y), (w, y)], fill=(28, 16, 8), width=3)
    return img


def vignette(img, strength=170, color=(0, 0, 0)):
    w, h = img.size
    m = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(m)
    steps = 60
    for i in range(steps):
        a = int(strength * (1 - i / steps) ** 2)
        d.rectangle([i * w // (steps * 4), i * h // (steps * 4), w - i * w // (steps * 4), h - i * h // (steps * 4)], outline=a, width=max(1, w // (steps * 4)))
    m = m.filter(ImageFilter.GaussianBlur(min(w, h) // 20))
    return Image.composite(Image.new("RGB", (w, h), color), img, m)


def icon(name, size):
    path = os.path.join(TEX, name)
    im = Image.open(path).convert("RGBA")
    return im.resize((size, size), Image.NEAREST)


def text(d, xy, s, f, fill, anchor="la", shadow=None, offset=3):
    if shadow:
        d.text((xy[0] + offset, xy[1] + offset), s, font=f, fill=shadow, anchor=anchor)
    d.text(xy, s, font=f, fill=fill, anchor=anchor)


SKULL = [
    "..XXXXXXXXXX..",
    ".XXXXXXXXXXXX.",
    "XXXXXXXXXXXXXX",
    "XXX...XX...XXX",
    "XXX...XX...XXX",
    "XXX...XX...XXX",
    "XXXXXXXXXXXXXX",
    "XXXXXX..XXXXXX",
    ".XXXXXXXXXXXX.",
    "..XX.XX.XX.X..",
    "..XXXXXXXXXX..",
]


def pixel_art(pattern, cell, color):
    h, w = len(pattern), len(pattern[0])
    im = Image.new("RGBA", (w * cell, h * cell), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for y, row in enumerate(pattern):
        for x, c in enumerate(row):
            if c == "X":
                d.rectangle([x * cell, y * cell, (x + 1) * cell - 1, (y + 1) * cell - 1], fill=color)
    return im


def wax_seal(size, seed=7):
    r = random.Random(seed)
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    cx = cy = size / 2
    pts = []
    for i in range(72):
        ang = i / 72 * math.tau
        rad = size * 0.46 * (1 + r.uniform(-0.035, 0.035) + 0.03 * math.sin(ang * 9))
        pts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang)))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    grad = Image.new("RGB", (size, size))
    gd = ImageDraw.Draw(grad)
    for i in range(size // 2, 0, -1):
        t = i / (size / 2)
        c = tuple(int(a * (1 - t) + b * t) for a, b in zip((196, 34, 30), (96, 8, 8)))
        gd.ellipse([cx - i - size * 0.08, cy - i - size * 0.08, cx + i - size * 0.08, cy + i - size * 0.08], fill=c)
    im.paste(grad, (0, 0), mask)
    # inner ring
    d = ImageDraw.Draw(im)
    ring = size * 0.36
    d.ellipse([cx - ring, cy - ring, cx + ring, cy + ring], outline=(70, 4, 4), width=max(2, size // 60))
    d.ellipse([cx - ring + size * 0.012, cy - ring + size * 0.012, cx + ring + size * 0.012, cy + ring + size * 0.012],
              outline=(220, 80, 70), width=max(1, size // 120))
    skull = pixel_art(SKULL, int(size * 0.034), (232, 120, 110, 255))
    shadow = pixel_art(SKULL, int(size * 0.034), (60, 0, 0, 255))
    sx, sy = int(cx - skull.width / 2), int(cy - skull.height / 2 - size * 0.01)
    im.alpha_composite(shadow, (sx + max(2, size // 100), sy + max(2, size // 100)))
    im.alpha_composite(skull, (sx, sy))
    return im.filter(ImageFilter.SMOOTH)


def blocky_head(size, hair=(40, 28, 20), skin=(196, 150, 110), seed=11):
    """A generic blocky head silhouette (not any real skin)."""
    cell = size // 8
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r = random.Random(seed)
    for y in range(8):
        for x in range(8):
            if y < 2 or (y == 2 and x in (0, 7)):
                c = tuple(max(0, v + r.randint(-10, 10)) for v in hair)
            else:
                c = tuple(max(0, min(255, v + r.randint(-12, 12))) for v in skin)
            d.rectangle([x * cell, y * cell, (x + 1) * cell - 1, (y + 1) * cell - 1], fill=c)
    for x in (1, 5):  # eyes
        d.rectangle([x * cell, 4 * cell, (x + 2) * cell - 1, 5 * cell - 1], fill=(240, 240, 240))
        d.rectangle([(x + (1 if x == 1 else 0)) * cell, 4 * cell, (x + (2 if x == 1 else 1)) * cell - 1, 5 * cell - 1], fill=(50, 60, 120))
    d.rectangle([3 * cell, 6 * cell, 5 * cell - 1, 7 * cell - 1], fill=(120, 70, 50))
    return im


def crosshair(size, color=(200, 20, 20, 255), width=6):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = size / 2
    for rad in (size * 0.42, size * 0.25):
        d.ellipse([c - rad, c - rad, c + rad, c + rad], outline=color, width=width)
    gap = size * 0.1
    for (x1, y1, x2, y2) in ((0, c, c - gap, c), (c + gap, c, size, c), (c, 0, c, c - gap), (c, c + gap, c, size)):
        d.line([(x1, y1), (x2, y2)], fill=color, width=width)
    return im


def poster(w, h, title, head, rewards, seed=21, reward_label="REWARD"):
    """A wanted poster: parchment, WANTED title, a framed head, a row of reward icons."""
    p = burnt_edges(parchment(w, h, seed), depth=max(20, w // 14), seed=seed)
    d = ImageDraw.Draw(p)
    text(d, (w // 2, int(h * 0.045)), title, playbill(int(w * 0.30)), BLOOD, "ma", shadow=(80, 40, 20, 120), offset=max(2, w // 120))
    hs = int(w * 0.52)
    hx, hy = (w - hs) // 2, int(h * 0.30)
    d.rectangle([hx - 8, hy - 8, hx + hs + 8, hy + hs + 8], fill=INK)
    p.paste(head, (hx, hy), head)
    ch = crosshair(hs + 40, width=max(3, w // 90))
    p.paste(ch, (hx - 20, hy - 20), ch)
    text(d, (w // 2, hy + hs + int(h * 0.025)), "DEAD", playbill(int(w * 0.16)), INK, "ma")
    text(d, (w // 2, int(h * 0.80)), reward_label, rock(int(w * 0.055), True), INK_LIGHT, "ma")
    isz = int(w * 0.13)
    total = len(rewards) * (isz + 12) - 12
    x0 = (w - total) // 2
    for i, (name, count) in enumerate(rewards):
        ic = icon(name, isz)
        p.paste(ic, (x0 + i * (isz + 12), int(h * 0.855)), ic)
        if count > 1:
            text(d, (x0 + i * (isz + 12) + isz, int(h * 0.855) + isz), str(count), rock(int(isz * 0.42), True), CREAM, "rd", shadow=(30, 20, 10), offset=2)
    return p


def bullets(d, x, y, items, f, color, gap, bullet_color=BLOOD, max_w=None):
    """Lines starting with spaces continue the previous bullet (no dot, tighter spacing)."""
    first = True
    for line in items:
        cont = line.startswith(" ")
        if cont:
            y -= gap - int(f.size * 1.25)
        elif not first:
            y += 6
        if not cont:
            d.ellipse([x, y + f.size * 0.36, x + f.size * 0.4, y + f.size * 0.76], fill=bullet_color)
        d.text((x + f.size * 0.8, y), line.strip(), font=f, fill=color)
        y += gap
        first = False
    return y


def card(name, left, title, subtitle, items, accent=BLOOD):
    """1280x720 feature card: illustration on the left, dark wood panel with text on the right."""
    w, h = 1280, 720
    img = wood(w, h, seed=hash(name) % 100)
    img = vignette(img, 150)
    img.paste(left, (40, (h - left.height) // 2), left if left.mode == "RGBA" else None)
    d = ImageDraw.Draw(img)
    x = 40 + left.width + 50
    text(d, (x, 70), title, playbill(92), (230, 60, 50), shadow=(20, 0, 0), offset=4)
    text(d, (x + 2, 178), subtitle, rock(28, True), CREAM)
    d.line([(x, 225), (w - 50, 225)], fill=accent, width=4)
    bullets(d, x, 255, items, rock(27), (236, 222, 196), 64)
    img.save(os.path.join(OUT, name))


# ------------------------------------------------------------------- logo
def logo(size):
    img = burnt_edges(parchment(size, size, 2), depth=size // 9, seed=4)
    seal = wax_seal(int(size * 0.74))
    img.paste(seal, ((size - seal.width) // 2, int(size * 0.04)), seal)
    d = ImageDraw.Draw(img)
    # ribbon
    rh = int(size * 0.2)
    ry = int(size * 0.72)
    rib = [(size * 0.04, ry), (size * 0.96, ry), (size * 0.9, ry + rh / 2), (size * 0.96, ry + rh), (size * 0.04, ry + rh), (size * 0.1, ry + rh / 2)]
    d.polygon([(x + size * 0.012, y + size * 0.012) for x, y in rib], fill=(40, 20, 8))
    d.polygon(rib, fill=(36, 22, 12))
    d.polygon([(x, y) for x, y in rib], outline=(150, 110, 60), width=max(1, size // 140))
    text(d, (size // 2, ry + rh // 2 + int(size * 0.008)), "BLOOD CONTRACTS", playbill(int(size * 0.17)), (232, 64, 52), "mm")
    return img


lg = logo(800)
lg.resize((400, 400), Image.LANCZOS).save(os.path.join(OUT, "logo_400.png"))
lg.save(os.path.join(OUT, "logo_800.png"))
lg.resize((128, 128), Image.LANCZOS).save(JAR_LOGO)

# ------------------------------------------------------------------- header (description banner)
W, H = 1600, 520
hdr = vignette(burnt_edges(parchment(W, H, 8), depth=50, seed=9), 120, (40, 18, 6))
d = ImageDraw.Draw(hdr)
seal = wax_seal(400, seed=3)
hdr.paste(seal, (W - 470, 60), seal)
text(d, (90, 40), "BLOOD CONTRACTS", playbill(210), BLOOD, shadow=(90, 50, 20), offset=5)
text(d, (98, 262), "Put a price on your friends' heads.", rock(48, True), INK)
text(d, (98, 326), "Funded by players, never by the server. Claimed by whoever gets them.", rock(34), INK_LIGHT)
items = [("item/diamond.png", 12), ("item/netherite_sword.png", 1), ("item/totem_of_undying.png", 1), ("item/golden_apple.png", 8), ("item/emerald.png", 32)]
for i, (n, c) in enumerate(items):
    ic = icon(n, 72)
    x = 98 + i * 92
    d.rectangle([x - 4, 400, x + 76, 480], fill=(139, 115, 85), outline=INK, width=3)
    hdr.paste(ic, (x, 404), ic)
    if c > 1:
        text(d, (x + 74, 478), str(c), rock(26, True), CREAM, "rd", shadow=(20, 10, 5), offset=2)
hdr.save(os.path.join(OUT, "header.png"))

# ------------------------------------------------------------------- feature cards
head = blocky_head(256)
p1 = poster(470, 640, "WANTED", head, [("item/diamond.png", 3), ("item/totem_of_undying.png", 1), ("item/netherite_ingot.png", 2)])
card("feature_contracts.png", p1.rotate(2.5, expand=True, resample=Image.BICUBIC, fillcolor=(0, 0, 0, 0)).convert("RGBA"),
     "SIGN THE CONTRACT", "Any player the server knows, online or not.",
     ["Put real items in escrow: one stack,", "    or up to 9 different ones", "The item leaves your inventory, the server", "    never creates or duplicates anything",
      "Cancel anytime (not mid-fight!)", "Several sponsors can stack bounties"])

hunt = Image.new("RGBA", (470, 600), (0, 0, 0, 0))
hd = ImageDraw.Draw(hunt)
hh = blocky_head(300, hair=(90, 60, 30), skin=(170, 120, 90), seed=5)
hunt.paste(hh, (85, 120), hh)
ch = crosshair(380, width=8)
hunt.paste(ch, (45, 80), ch)
for i, n in enumerate(["item/iron_sword.png", "item/bow.png", "block/tnt_side.png", "item/bone.png"]):
    ic = icon(n, 72)
    x = 45 + i * 98
    hd.rectangle([x - 5, 490, x + 77, 572], fill=(60, 40, 22), outline=(150, 110, 60), width=3)
    hunt.paste(ic, (x, 495), ic)
card("feature_hunt.png", hunt, "FAIR KILLS ONLY", "Smart attribution, no false payouts.",
     ["Sword, bow, TNT, tamed wolf: the player", "    behind it gets paid, not the arrow", "Knocked into the void or lava? The last",
      "    attacker within 10 s collects", "/kill, mobs, machines: nobody", "Create Big Cannons: the gunner collects"])

# alert illustration
alert = vignette(parchment(560, 600, 12).point(lambda v: int(v * 0.35)), 230, (110, 0, 0))
ad = ImageDraw.Draw(alert)
text(ad, (280, 200), "A PRICE ON", playbill(96), (225, 20, 20), "mm", shadow=(0, 0, 0), offset=4)
text(ad, (280, 285), "YOUR HEAD", playbill(96), (225, 20, 20), "mm", shadow=(0, 0, 0), offset=4)
text(ad, (280, 360), "Sponsor_42 wants you dead.", rock(28, True), (240, 210, 180), "mm", shadow=(0, 0, 0), offset=2)
for i, n in enumerate(["item/diamond.png", "item/totem_of_undying.png", "item/netherite_sword.png"]):
    ic = icon(n, 56)
    alert.paste(ic, (190 + i * 64, 400), ic)
alert = burnt_edges(alert, depth=12, seed=13)
card("feature_alert.png", alert, "YOU'VE BEEN MARKED", "The target is warned. Loudly.",
     ["Full-screen alert, not a chat line:", "    pulsing blood vignette, shaking title", "Curse sting, then a racing heartbeat",
      "Offline targets get it when they log in", "Sponsor name shown or hidden (config)"])

claim = parchment(500, 560, 14)
claim = burnt_edges(claim, depth=24, seed=15)
cd = ImageDraw.Draw(claim)
text(cd, (250, 30), "TO CLAIM", playbill(92), BLOOD, "ma", shadow=(90, 50, 20), offset=3)
rows = [("item/diamond.png", "3x Diamond", "Bounty on Alex_7: your reward"), ("item/netherite_ingot.png", "2x Netherite Ingot", "Bounty on Alex_7: your reward"),
        ("item/golden_apple.png", "8x Golden Apple", "Refund: bounty cancelled")]
for i, (n, a, b) in enumerate(rows):
    y = 150 + i * 110
    cd.rectangle([30, y, 470, y + 92], fill=(214, 190, 146), outline=INK, width=2)
    cd.rectangle([44, y + 14, 108, y + 78], fill=(139, 115, 85), outline=BLOOD, width=3)
    ic = icon(n, 52)
    claim.paste(ic, (50, y + 20), ic)
    text(cd, (124, y + 18), a, rock(26, True), GOLD)
    text(cd, (124, y + 54), b, rock(19), INK_LIGHT)
cd.rectangle([150, 490, 350, 530], fill=(140, 16, 16), outline=(40, 10, 5), width=3)
text(cd, (250, 510), "Claim all", rock(24, True), CREAM, "mm")
card("feature_claim.png", claim, "BLOOD MONEY", "Rewards wait for you on the board.",
     ["Nothing lands in your inventory unasked", "A toast tells you what is waiting", "Claim one stack or everything at once",
      "Offline when you won? It is kept safe", "Full inventory: dropped at your feet,", "    reserved for you, never despawns"])

print("written to", OUT)
