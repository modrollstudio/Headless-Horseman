"""Generates the Horseman's and the steed's GeckoLib models, texture sheets, glowmasks and animations, plus the
item textures and item models. Run from the repo root with Python 3 and Pillow. Output is deterministic: all
randomness comes from hashing fixed keys.

Models face north (-z). Coordinates are model pixels from the ground under the entity, in Bedrock geometry as GeckoLib
reads it: x is mirrored, so the rider's right arm is at negative x. Each cube face gets its own region of the sheet,
packed in creation order, and is painted by a material function.

Face sides as GeckoLib draws them: north is the front, south the back, east the model's right and west its left. On an
east face the front is at the right of its region, on a west face at the left; on an up face the front is at the
bottom."""
import hashlib
import json
import math
import os

from PIL import Image

RES = 'common/src/main/resources/'
ASSETS = RES + 'assets/critfall_horseman/'
GEO = ASSETS + 'geo/entity/'
ANIMATIONS = ASSETS + 'animations/entity/'
ENTITY = ASSETS + 'textures/entity/'
ITEM = ASSETS + 'textures/item/'

CLEAR = (0, 0, 0, 0)

IRON = [(30, 30, 35), (44, 43, 49), (60, 58, 64), (78, 76, 82), (100, 97, 102), (128, 124, 126)]
RUST = [(58, 30, 20), (82, 42, 24), (108, 56, 28), (134, 72, 34), (160, 92, 44), (184, 116, 58)]
CLOTH = [(11, 10, 12), (17, 15, 18), (24, 21, 25), (33, 29, 33), (44, 38, 42)]
CRIMSON = [(36, 9, 10), (52, 13, 14), (70, 19, 18), (88, 26, 22)]
LEATHER = [(34, 22, 16), (46, 30, 20), (60, 40, 26), (74, 50, 32)]
ROPE = [(70, 56, 36), (92, 74, 48), (112, 92, 60)]
PUMPKIN = [(96, 34, 6), (132, 52, 8), (170, 74, 12), (206, 102, 20), (232, 134, 36), (246, 168, 64)]
STEM = [(36, 30, 18), (52, 44, 24), (70, 60, 32)]
CARVED = [(40, 12, 4), (70, 22, 6)]
GLOW = [(255, 236, 150), (255, 200, 72), (250, 150, 36), (226, 98, 22)]
FLAME = [(255, 246, 196), (255, 214, 96), (252, 160, 44), (232, 102, 26), (186, 58, 18)]
EMBER = [(255, 156, 52), (238, 92, 26), (168, 44, 16), (84, 20, 10), (34, 12, 10)]
HIDE = [(9, 8, 11), (14, 13, 17), (20, 18, 24), (28, 26, 34), (40, 38, 50), (58, 56, 72)]
MANE = [(38, 36, 40), (56, 54, 58), (78, 76, 80), (104, 102, 106), (132, 130, 134)]
HOOF = [(18, 16, 16), (28, 25, 24), (40, 36, 34)]
STEEL = [(15, 16, 20), (22, 23, 28), (30, 31, 37), (40, 41, 48), (54, 55, 63), (82, 83, 92)]
CHAR = [(20, 13, 10), (32, 18, 12), (46, 24, 14), (64, 32, 16)]
APRON = [(40, 27, 19), (52, 35, 24), (64, 44, 30), (78, 54, 36)]
STAIN = [(22, 10, 9), (34, 13, 11), (46, 17, 13), (58, 22, 15)]
HOOD = [(32, 29, 32), (44, 40, 43), (57, 52, 54), (72, 65, 65)]


def rnd(*key):
    return hashlib.sha256(':'.join(map(str, key)).encode()).digest()[0] / 256.0


def noise(key, x, y, scale):
    """Smooth value noise in [0, 1): a hashed grid every `scale` pixels, smoothstep-interpolated."""
    gx, gy = x / scale, y / scale
    x0, y0 = math.floor(gx), math.floor(gy)
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = rnd(key, x0, y0), rnd(key, x0 + 1, y0)
    c, d = rnd(key, x0, y0 + 1), rnd(key, x0 + 1, y0 + 1)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def pick(ramp, t):
    return ramp[max(0, min(len(ramp) - 1, int(t * len(ramp))))]


def rgba(colour):
    return colour if len(colour) == 4 else colour + (255,)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(json.dumps(obj, indent=2) + '\n')


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


# --- Materials ------------------------------------------------------------------------------------------
# A material function paints one face through Face.put and Face.glow. Face.key differs per face, so no two faces get
# the same noise.

class Face:
    def __init__(self, sheet, x0, y0, w, h, side, key):
        self.sheet, self.x0, self.y0, self.w, self.h, self.side, self.key = sheet, x0, y0, w, h, side, key

    def put(self, x, y, colour):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.sheet.im.putpixel((self.x0 + x, self.y0 + y), rgba(colour))

    def glow(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.sheet.glow.putpixel((self.x0 + x, self.y0 + y), self.sheet.im.getpixel((self.x0 + x, self.y0 + y)))

    def clear(self, x, y):
        self.put(x, y, CLEAR)

    def front_u(self):
        """On a side face: 1 if the model's front is at the right of the region, -1 if at the left, 0 otherwise."""
        return {'east': 1, 'west': -1}.get(self.side, 0)


def shade(colour, by):
    return tuple(max(0, min(255, round(c * by))) for c in colour[:3])


def rivet(f, x, y):
    """Rivet head with a highlight, a shadow and sometimes a short rust streak below."""
    f.put(x, y, STEEL[5])
    f.put(x + 1, y + 1, STEEL[0])
    if rnd(f.key, 'weep', x, y) < 0.3:
        for k in range(1, 2 + int(rnd(f.key, 'weeplen', x, y) * 2)):
            f.put(x, y + k, pick(RUST, 0.6 - k * 0.15))


def strap(f, row, buckle=True):
    """Two-row leather strap across the face at `row`, with a buckle in the middle."""
    for x in range(f.w):
        f.put(x, row, pick(LEATHER, 0.55 + rnd(f.key, 'strap', x) * 0.3))
        f.put(x, row + 1, pick(LEATHER, 0.2 + rnd(f.key, 'strap2', x) * 0.3))
        if x % 2:
            f.put(x, row, ROPE[0])
    if buckle and f.w >= 5:
        bx = f.w // 2 - 1
        for dx, dy in ((0, -1), (1, -1), (2, -1), (0, 0), (2, 0), (0, 1), (1, 1), (2, 1), (0, 2), (1, 2), (2, 2)):
            if dy in (-1, 2) or dx != 1:
                f.put(bx + dx, row + dy, IRON[4] if dy < 1 else IRON[3])
        f.put(bx + 1, row, IRON[5])


def rusted(f, rust=0.5, rivets=True, ridge=False, lames=0, straps=(), strap_sides=('east', 'west'), dents=True):
    """Dark steel plate: soft sheen, lit top edge, shaded bottom edge, rust at the edges and lame seams, a few dents
    and gouges, rows of rivets, and optional straps."""
    side = f.side not in ('up', 'down')
    w, h = f.w, f.h
    for y in range(h):
        for x in range(w):
            u = x / (w - 1) if w > 1 else 0.5
            v = y / (h - 1) if h > 1 else 0.5
            sheen = 0.42 + 0.22 * (1 - v) - 0.22 * abs(u - 0.5)
            grain = (noise((f.key, 'steel'), x, y * 0.6, 6.0) - 0.5) * 0.1
            c = pick(STEEL, sheen + grain)
            edge = min(x, w - 1 - x, y, h - 1 - y)
            r = noise((f.key, 'edge'), x, y, 2.2)
            corner = (x in (0, w - 1)) and (y in (0, h - 1))
            if (corner and r > 0.35) or (edge == 0 and r > 0.72 - rust * 0.2) or (edge == 1 and r > 0.9 - rust * 0.1):
                c = pick(RUST, 0.1 + r * 0.5)
            f.put(x, y, c)
    seams = []
    if lames and side:
        seams = [y + 1 for y in range(lames - 1, h - 1, lames)]
        for s in seams:
            for x in range(w):
                f.put(x, s - 1, shade(f.sheet.im.getpixel((f.x0 + x, f.y0 + s - 1)), 0.5))
                lip = pick(STEEL, 0.75 + rnd(f.key, 'lip', x, s) * 0.2)
                if noise((f.key, 'seamrust'), x, s, 2.0) > 0.75 - rust * 0.15:
                    lip = pick(RUST, 0.35 + rnd(f.key, 'sr', x, s) * 0.4)
                f.put(x, s, lip)
    if dents and w * h >= 40:
        for i in range(w * h // 140):
            dx, dy = 1 + int(rnd(f.key, 'dx', i) * (w - 3)), 1 + int(rnd(f.key, 'dy', i) * (h - 3))
            f.put(dx, dy, shade(f.sheet.im.getpixel((f.x0 + dx, f.y0 + dy)), 0.75))
            f.put(dx + 1, dy + 1, shade(f.sheet.im.getpixel((f.x0 + dx + 1, f.y0 + dy + 1)), 1.3))
        if w * h >= 60 and rnd(f.key, 'gouge') < 0.5:
            gx, gy = 1 + int(rnd(f.key, 'gx') * (w - 6)), 2 + int(rnd(f.key, 'gy') * (h - 4))
            for k in range(4):
                f.put(gx + k, gy + k // 2, STEEL[5])
                f.put(gx + k, gy + k // 2 + 1, STEEL[0])
    if side:
        for x in range(w):
            if f.side != 'down':
                f.put(x, 0, shade(f.sheet.im.getpixel((f.x0 + x, f.y0)), 1.45))
            if h > 2:
                f.put(x, h - 1, shade(f.sheet.im.getpixel((f.x0 + x, f.y0 + h - 1)), 0.6))
    if ridge and f.side == 'north':
        mid = w // 2
        for y in range(h):
            f.put(mid, y, shade(pick(STEEL, 0.85), 1.1))
            f.put(mid - 1, y, STEEL[0])
    if rivets and side and w >= 4 and h >= 4:
        step = 3 if w >= 8 else max(1, w - 3)
        rows = [1] + ([h - 2] if h >= 6 else []) + [s + 1 for s in seams if s + 1 < h - 2]
        for row in rows:
            for x in range(1, w - 1, step):
                rivet(f, x, row)
    if f.side in strap_sides:
        for row in straps:
            strap(f, row)


def mail(f):
    """Mail rings with rust in the gaps."""
    for y in range(f.h):
        for x in range(f.w):
            ring = (x + (y // 2) % 2) % 2 == 0
            n = noise((f.key, 'mail'), x, y, 2.5)
            if ring:
                c = pick(IRON, 0.35 + n * 0.4 + (0.2 if y % 2 == 0 else 0))
            else:
                c = pick(RUST, n * 0.5) if n > 0.4 else IRON[0]
            f.put(x, y, c)


def iron(f):
    """Plain dark iron for buckles and shackles."""
    for y in range(f.h):
        for x in range(f.w):
            n = noise((f.key, 'iron'), x, y, 1.5)
            c = pick(RUST, n) if n > 0.75 else pick(IRON, 0.3 + n * 0.5 - (0.2 if y == f.h - 1 else 0))
            f.put(x, y, c)


def cloth(f, ramp=CLOTH, ragged=0, holes=0.0):
    """Coarse cloth, darker towards the bottom, with an optional torn hem and holes."""
    for y in range(f.h):
        for x in range(f.w):
            n = noise((f.key, 'cloth'), x, y * 0.5, 3.0)
            t = 0.2 + n * 0.5 + (0.12 if x % 2 else 0) - y / max(1, f.h) * 0.2
            c = pick(ramp, t + rnd(f.key, x, y) * 0.1)
            f.put(x, y, c)
            torn = ragged and f.side not in ('up', 'down') and y >= f.h - ragged
            if torn and rnd(f.key, 'tear', x) * ragged > (f.h - 1 - y) + rnd(f.key, 'tear2', x, y) * 1.5:
                f.clear(x, y)
            elif holes and 1 < y < f.h - ragged and noise((f.key, 'hole'), x, y, 2.0) > 1 - holes:
                f.clear(x, y)


def tatter(f, ramp=CLOTH, ragged=4, holes=0.12, eyes=None, embers=True):
    """Torn cloth for the cloak and hood: ragged hem, frayed side edges, burn holes with charred rims, and a few
    glowing embers along the hem. `eyes` are pixels cut clean through."""
    cut = {}
    for x in range(f.w):
        cut[x] = int(rnd(f.key, 'cut', x) ** 0.7 * ragged) if f.side not in ('up', 'down') else 0
    gone, burnt = set(), set()
    for y in range(f.h):
        for x in range(f.w):
            n = noise((f.key, 'cloth'), x, y * 0.5, 3.0)
            t = 0.25 + n * 0.5 + (0.1 if x % 2 else 0) - y / max(1, f.h) * 0.2
            f.put(x, y, pick(ramp, t + rnd(f.key, x, y) * 0.1))
            end = f.h - cut[x]
            fray = f.side in ('north', 'south') and f.w > 2 and x in (0, f.w - 1) and rnd(f.key, 'fray', x, y) < 0.3
            hole = holes and 1 < y < end - 2 and noise((f.key, 'hole'), x, y, 1.8) > 1 - holes
            if y >= end or fray or hole or (eyes and (x, y) in eyes):
                gone.add((x, y))
            if hole:
                burnt.add((x, y))
    for y in range(f.h):
        for x in range(f.w):
            if (x, y) in gone:
                continue
            near = [(x + dx, y + dy) in burnt for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            end = f.h - cut[x]
            if any(near) or (ragged and y >= end - 1):
                # Char around holes and along the hem, with the odd ember on the hem.
                if embers and y >= end - 1 and rnd(f.key, 'ember', x, y) < 0.12 and f.side in ('north', 'south'):
                    f.put(x, y, EMBER[0 if rnd(f.key, 'hot', x, y) < 0.4 else 1])
                    f.glow(x, y)
                else:
                    f.put(x, y, pick(CHAR, rnd(f.key, 'char', x, y)))
    for x, y in gone:
        f.clear(x, y)


def leather(f, ramp=LEATHER, stitch=True):
    for y in range(f.h):
        for x in range(f.w):
            n = noise((f.key, 'leather'), x, y, 2.0)
            f.put(x, y, pick(ramp, 0.2 + n * 0.6 + rnd(f.key, x, y) * 0.15))
    if stitch and f.h >= 4 and f.side not in ('up', 'down'):
        for x in range(1, f.w - 1, 2):
            f.put(x, 1, ROPE[0])


def apron(f):
    """Stained leather apron with stitched edges and a ragged hem."""
    for y in range(f.h):
        for x in range(f.w):
            n = noise((f.key, 'leather'), x, y, 2.0)
            c = pick(APRON, 0.2 + n * 0.6 + rnd(f.key, x, y) * 0.15)
            blot = noise((f.key, 'stain'), x, y, 2.5)
            run = rnd(f.key, 'run', x) < 0.3 and y < 2 + rnd(f.key, 'runlen', x) * f.h * 0.8
            if blot > 0.66 or run:
                c = pick(STAIN, rnd(f.key, 's', x, y) * 0.8 + (blot - 0.66))
            if x in (0, f.w - 1) and y % 2 == 0:
                c = ROPE[0]
            f.put(x, y, c)
            if y == f.h - 1 and rnd(f.key, 'hem', x) < 0.4:
                f.clear(x, y)


def rope(f, coil=False):
    """Twisted rope; `coil` adds the gaps between turns."""
    for y in range(f.h):
        for x in range(f.w):
            twist = (x + y) % 3
            c = ROPE[2 if twist == 0 else 1] if twist < 2 else ROPE[0]
            if coil and y % 2 == 1 and f.side not in ('up', 'down'):
                c = shade(ROPE[0], 0.55)
            if coil and f.side in ('up', 'down') and 0 < x < f.w - 1 and 0 < y < f.h - 1:
                c = shade(ROPE[0], 0.4)
            f.put(x, y, shade(c, 0.85 + rnd(f.key, x, y) * 0.25))


def stump(f):
    """Charred neck stump; the top face glows."""
    for y in range(f.h):
        for x in range(f.w):
            n = noise((f.key, 'stump'), x, y, 1.5)
            if f.side == 'up':
                c = pick(EMBER, 0.6 - n * 0.6)
                f.put(x, y, c)
                if n > 0.35:
                    f.glow(x, y)
            else:
                f.put(x, y, pick(EMBER, 0.7 + n * 0.3))
                if y == 0 and n > 0.5:
                    f.put(x, y, EMBER[1])
                    f.glow(x, y)


def blade(f):
    """Rusted blade with a fuller, a nicked edge and a hole near the square end."""
    for y in range(f.h):
        for x in range(f.w):
            n = noise((f.key, 'blade'), x, y, 3.0) * 0.75 + rnd(f.key, x, y) * 0.25
            c = pick(RUST, 0.15 + n * 0.85) if n > 0.5 else pick(IRON, 0.35 + n * 0.6)
            f.put(x, y, c)
    if f.side in ('east', 'west'):
        # Side faces: u runs along the blade, v across it. The edge is the bottom row, the spine the top.
        front = f.front_u()
        for x in range(f.w):
            along = x if front > 0 else f.w - 1 - x  # 0 at the hilt
            mid = f.h // 2
            if 2 < along < f.w - 4:
                f.put(x, mid, shade(pick(IRON, 0.2), 1.0))
            edge = f.h - 1
            nick = rnd(f.key, 'nick', along) < 0.12
            f.put(x, edge, RUST[1] if nick else IRON[4] if along % 5 else IRON[5])
            f.put(x, 0, shade(pick(IRON, 0.55), 1.15))
        hx = f.w - 3 if front > 0 else 2
        f.clear(hx, f.h // 2 - 1)
    elif f.side in ('up', 'down'):
        for x in range(f.w):
            for y in range(f.h):
                f.put(x, y, IRON[4] if f.side == 'down' else pick(IRON, 0.55))


def grip(f):
    """Leather-wrapped grip."""
    for y in range(f.h):
        for x in range(f.w):
            band = (x + y) % 3 == 0
            f.put(x, y, LEATHER[0] if band else pick(CLOTH, 0.5 + rnd(f.key, x, y) * 0.4))


def pumpkin(f, face=None):
    """Ribbed pumpkin skin, with the carved `face` painted in and glowing."""
    for y in range(f.h):
        for x in range(f.w):
            rib = x % 3 == 0 if f.side != 'up' else (x + y) % 4 == 0
            n = noise((f.key, 'pumpkin'), x, y, 2.5)
            t = 0.35 + n * 0.45 - (0.25 if rib else 0) + (0.12 if 0 < y < 3 else 0) - (0.15 if y == f.h - 1 else 0)
            f.put(x, y, pick(PUMPKIN, t))
            if rnd(f.key, 'blot', x, y) < 0.05:
                f.put(x, y, PUMPKIN[0])
    if face:
        for (x, y), kind in face.items():
            if kind == 'carved':
                f.put(x, y, CARVED[rnd(f.key, 'c', x, y) < 0.5])
            else:
                f.put(x, y, GLOW[kind])
                f.glow(x, y)


def flame(f, tongues=1, core=False):
    """Flame texture: `tongues` side by side, widest at the base, hottest low in the middle. Fully glowing.
    `core` makes it hotter, for the inner planes."""
    licks = []
    for i in range(tongues):
        cx = (i + 0.5) / tongues * f.w - 0.5 + (rnd(f.key, 'cx', i) - 0.5) * 0.8
        height = 0.55 + rnd(f.key, 'h', i) * 0.45 if tongues > 1 else 1.0
        licks.append((cx, height, max(1.0, f.w / tongues * 0.75)))
    for y in range(f.h):
        for x in range(f.w):
            up = 1 - y / max(1, f.h - 1)  # 1 at the top
            best = None
            for cx, height, half in licks:
                if up > height:
                    continue
                along = up / height
                reach = half * (1 - along) ** 0.8 + (noise((f.key, 'lick'), x, y, 1.5) - 0.5) * 1.2
                d = abs(x - cx)
                if d <= reach:
                    heat = along * 0.65 + d / max(0.6, reach) * 0.35
                    best = heat if best is None else min(best, heat)
            if best is None:
                f.clear(x, y)
            else:
                f.put(x, y, pick(FLAME, best * (0.75 if core else 1.0)))
                f.glow(x, y)


def face_light(f, hot=True):
    """Overlay for the plane just in front of the carved face. `hot` draws the holes white-hot (the flicker layer);
    otherwise a one-pixel ring of light around them."""
    holes = {k for k, v in jack_face().items() if v != 'carved'}
    for y in range(f.h):
        for x in range(f.w):
            f.clear(x, y)
    if f.side != 'north':
        return
    for y in range(f.h):
        for x in range(f.w):
            if hot and (x, y) in holes:
                f.put(x, y, GLOW[0] if rnd('hot', x, y) < 0.7 else (255, 250, 220))
                f.glow(x, y)
            elif not hot and (x, y) not in holes and any(
                    (x + dx, y + dy) in holes for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                f.put(x, y, GLOW[3] if rnd('halo', x, y) < 0.6 else GLOW[2])
                f.glow(x, y)


def hide(f, sheen=True, scars=0):
    """Near-black horse hide with a sheen along the top and `scars` pale cuts."""
    for y in range(f.h):
        for x in range(f.w):
            n = noise((f.key, 'hide'), x, y, 3.0)
            t = 0.15 + n * 0.45 + rnd(f.key, x, y) * 0.1
            if f.side == 'up' and sheen:
                t += 0.25
            elif sheen and y < 2 and f.side != 'down':
                t += 0.15
            if f.side == 'down':
                t -= 0.1
            f.put(x, y, pick(HIDE, t))
            if rnd(f.key, 'fleck', x, y) < 0.006:
                f.put(x, y, HIDE[4])
    if f.side in ('east', 'west', 'north', 'south'):
        for i in range(scars):
            if f.w < 6 or f.h < 4:
                break
            sx, sy = int(rnd(f.key, 'sx', i) * (f.w - 5)), int(rnd(f.key, 'sy', i) * (f.h - 3))
            slope = 1 if rnd(f.key, 'ss', i) < 0.5 else -1
            for k in range(3 + int(rnd(f.key, 'sl', i) * 4)):
                y = sy + (k * slope) // 2 + (3 if slope < 0 else 0)
                f.put(sx + k, y, HIDE[5])
                f.put(sx + k, y + 1, HIDE[0])


def mane(f, ragged=3):
    """Grey mane and tail strands with ragged ends."""
    for y in range(f.h):
        for x in range(f.w):
            strand = rnd(f.key, 'strand', x)
            t = 0.15 + strand * 0.6 + noise((f.key, 'mane'), x, y, 4.0) * 0.25 - y / max(1, f.h) * 0.2
            f.put(x, y, pick(MANE, t))
            if f.side not in ('up', 'down') and y >= f.h - ragged and rnd(f.key, 'end', x) * ragged > f.h - 1 - y:
                f.clear(x, y)


def hoof(f):
    for y in range(f.h):
        for x in range(f.w):
            f.put(x, y, pick(HOOF, rnd(f.key, x, y) * 0.9))
        if f.side not in ('up', 'down') and y == f.h - 1:
            for x in range(f.w):
                if rnd(f.key, 'ember', x) < 0.3:
                    f.put(x, y, EMBER[2])
                    f.glow(x, y)


def shoe(f):
    """Iron horseshoe with nail heads and glowing embers along the bottom."""
    for y in range(f.h):
        for x in range(f.w):
            c = pick(STEEL, 0.55 + noise((f.key, 'shoe'), x, y, 2.0) * 0.3)
            if x % 3 == 1 and y == 0 and f.side not in ('up', 'down'):
                c = STEEL[5]
            f.put(x, y, c)
            if (f.side == 'down' or y == f.h - 1) and rnd(f.key, 'glow', x, y) < 0.45:
                f.put(x, y, EMBER[0] if rnd(f.key, 'hot', x, y) < 0.5 else EMBER[1])
                f.glow(x, y)


def steed_head(f):
    """Steed head hide with glowing eyes."""
    hide(f)
    if f.side in ('east', 'west') and f.h >= 4:
        front = f.front_u()
        ex = f.w - 3 if front > 0 else 2
        f.put(ex, 1, EMBER[0])
        f.put(ex - front, 1, EMBER[1])
        f.glow(ex, 1)
        f.glow(ex - front, 1)
        f.put(ex, 2, HIDE[0])


def muzzle(f):
    hide(f, sheen=False)
    if f.side == 'north':
        for x in (1, f.w - 2):
            f.put(x, f.h - 3, HIDE[0])
            f.put(x, f.h - 2, EMBER[3])


def chain(f):
    for y in range(f.h):
        for x in range(f.w):
            link = (x + y) % 2 == 0
            f.put(x, y, pick(IRON, 0.6) if link else pick(RUST, 0.3))


# --- Sheets ---------------------------------------------------------------------------------------------

SIDES = ('north', 'south', 'east', 'west', 'up', 'down')


class Sheet:
    """A texture atlas filled row by row (shelf packing), in the order faces are added."""

    def __init__(self, size):
        self.size = size
        self.im = Image.new('RGBA', (size, size), CLEAR)
        self.glow = Image.new('RGBA', (size, size), CLEAR)
        self.x, self.y, self.row = 0, 0, 0

    def take(self, w, h):
        if self.x + w > self.size:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        if self.y + h > self.size:
            raise ValueError('sheet full')
        at = (self.x, self.y)
        self.x += w
        self.row = max(self.row, h)
        return at


class Model:
    def __init__(self, name, sheet_size):
        self.name = name
        self.sheet = Sheet(sheet_size)
        self.bones = []
        self.n = 0

    def bone(self, name, pivot, parent=None, rotation=None):
        b = {'name': name, 'pivot': list(pivot), 'cubes': []}
        if parent:
            b['parent'] = parent
        if rotation:
            b['rotation'] = list(rotation)
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, material, inflate=0, rotation=None, pivot=None, sides=SIDES, **kw):
        """Adds a cube to `bone`, allocating and painting a sheet region for each face with `material`."""
        self.n += 1
        w, h, d = (0 if s == 0 else max(1, round(s)) for s in size)
        dims = {'north': (w, h), 'south': (w, h), 'east': (d, h), 'west': (d, h), 'up': (w, d), 'down': (w, d)}
        uv = {}
        for side in SIDES:
            fw, fh = dims[side]
            if side not in sides or fw <= 0 or fh <= 0:
                continue
            u, v = self.sheet.take(fw, fh)
            material(Face(self.sheet, u, v, fw, fh, side, (self.name, self.n, side)), **{**kw.get('all', {}),
                                                                                       **kw.get(side, {})})
            uv[side] = {'uv': [u, v], 'uv_size': [fw, fh]}
        c = {'origin': list(origin), 'size': list(size), 'uv': uv}
        if inflate:
            c['inflate'] = inflate
        if rotation:
            c['rotation'] = list(rotation)
            c['pivot'] = list(pivot or origin)
        next(b for b in self.bones if b['name'] == bone)['cubes'].append(c)

    def write(self, bounds):
        bones = [{k: v for k, v in b.items() if k != 'cubes' or v} for b in self.bones]
        write(GEO + self.name + '.geo.json', {
            'format_version': '1.12.0',
            'minecraft:geometry': [{
                'description': {
                    'identifier': 'geometry.' + self.name,
                    'texture_width': self.sheet.size,
                    'texture_height': self.sheet.size,
                    'visible_bounds_width': bounds[0],
                    'visible_bounds_height': bounds[1],
                    'visible_bounds_offset': [0, bounds[1] / 2, 0],
                },
                'bones': bones,
            }],
        })
        save(self.sheet.im, ENTITY + self.name + '.png')
        save(self.sheet.glow, ENTITY + self.name + '_glowmask.png')


def material(fn, **fixed):
    return lambda f, **kw: fn(f, **{**fixed, **kw})


# --- The Horseman ---------------------------------------------------------------------------------------

FACE = (
    '.........',
    '.....c0c.',
    '.10..000.',
    '..01.000.',
    '...c.....',
    'c1.0.0.1c',
    '.1000001.',
    '..1.0.1..',
    '.........',
)


def jack_face():
    """The 9x9 carved face as seen from the front. Digits in FACE are glow levels; 'c' is a dark cut edge."""
    face = {}
    for y, row in enumerate(FACE):
        for x, ch in enumerate(row):
            if ch == 'c':
                face[(x, y)] = 'carved'
            elif ch != '.':
                face[(x, y)] = int(ch)
    return face


def head_fire(m, parent):
    """The standing flame on top of the head (three crossed planes plus a hotter core) and the two face light
    overlays. Each is its own bone so it can flicker independently."""
    m.bone('head_flame', (8, 9.5, 0), parent)
    m.bone('head_flame_fire', (8, 9.5, 0), 'head_flame')
    for angle in (0, 60, 120):
        m.cube('head_flame_fire', (3.5, 9.4, 0), (9, 14, 0), flame, all={'tongues': 3},
               rotation=(0, angle, 0), pivot=(8, 9.4, 0))
    m.bone('head_flame_core', (8, 9.5, 0), 'head_flame_fire')
    for angle in (30, 90, 150):
        m.cube('head_flame_core', (5.5, 9.4, 0), (5, 8, 0), flame, all={'tongues': 2, 'core': True},
               rotation=(0, angle, 0), pivot=(8, 9.4, 0))
    m.bone('face_glow', (8, 5, -4.6), parent)
    m.bone('face_hot', (8, 5, -4.6), 'face_glow')
    m.cube('face_hot', (3.5, 0.5, -4.58), (9, 9, 0), face_light, all={'hot': True})
    m.bone('face_halo', (8, 5, -4.6), 'face_glow')
    m.cube('face_halo', (3.5, 0.5, -4.66), (9, 9, 0), face_light, all={'hot': False})


def horseman():
    m = Model('horseman', 256)
    plate = material(rusted)
    plain = {'rivets': False}

    m.bone('root', (0, 0, 0))
    for side, sx in (('right', -1), ('left', 1)):
        thigh, shin = side + '_leg', side + '_shin'
        x0 = 0.5 if sx > 0 else -5.5
        m.bone(thigh, (3 * sx, 16, 0), 'root')
        m.cube(thigh, (x0, 8, -2.5), (5, 8, 5), plate,
               all={'lames': 3, 'rivets': False, 'straps': (2,), 'strap_sides': ('south',)})
        m.cube(thigh, (x0 + 0.3, 7, -2.2), (4.4, 1.5, 4.4), mail)
        m.bone(shin, (3 * sx, 8, 0), thigh)
        m.cube(shin, (x0, 3, -2.5), (5, 4.5, 5), plate, all={'rivets': False, 'straps': (1,), 'strap_sides': ('south',)})
        m.cube(shin, (x0 + 0.5, 5.5, -3.3), (4, 3.5, 1), plate, all=plain)
        m.cube(shin, (x0, 0, -3.5), (5, 3, 6), plate, all={'lames': 1, 'rivets': False})

    m.bone('body', (0, 16, 0), 'root')
    m.cube('body', (-6, 17, -3.5), (12, 13, 7), plate, all={'lames': 0, 'straps': (3, 9)}, north={'ridge': True})
    m.cube('body', (-6.3, 15, -3.3), (12.6, 2, 6.6), leather)
    m.cube('body', (-1.5, 14.6, -3.9), (3, 2.8, 0.6), iron)
    m.cube('body', (-5.8, 12, -3), (11.6, 3.2, 6), mail)
    # Gorget.
    m.cube('body', (-5, 28.8, -4.3), (10, 2.6, 8.6), plate)
    m.cube('body', (-4, 31.2, -3.4), (8, 1.6, 6.8), plate, all=plain)

    m.bone('front_tasset', (0, 16, -3.5), 'body')
    m.cube('front_tasset', (-5, 9.5, -4.5), (10, 6.5, 1), plate, all={'lames': 3})
    m.bone('back_tasset', (0, 16, 3.5), 'body')
    m.cube('back_tasset', (-5, 9.5, 3.5), (10, 6.5, 1), plate, all={'lames': 3})
    # Apron, visible below the front tasset.
    m.bone('apron', (0, 15, -3.3), 'body')
    m.cube('apron', (-4, 2, -3.3), (8, 13, 0), apron)
    # Rope coil on the right hip, shackles on the left.
    m.bone('rope', (-7, 15, -3.7), 'body')
    m.cube('rope', (-8.5, 10, -4.8), (2.5, 5, 2.2), rope, all={'coil': True})
    m.cube('rope', (-7.8, 6.5, -4.2), (1, 3.5, 1), rope)
    m.bone('shackles', (6.5, 15, -3.7), 'body')
    m.cube('shackles', (6, 11.5, -4.2), (1, 3.5, 1), chain)
    for i, top in enumerate((11.5, 8.2)):
        cx = 5.2 + i * 1.2
        m.cube('shackles', (cx, top - 0.6, -4.6), (3, 0.6, 1.2), iron)
        m.cube('shackles', (cx, top - 3, -4.6), (3, 0.6, 1.2), iron)
        m.cube('shackles', (cx, top - 3, -4.6), (0.6, 3, 1.2), iron)
        m.cube('shackles', (cx + 2.4, top - 3, -4.6), (0.6, 3, 1.2), iron)

    m.bone('neck', (0, 31, 0), 'body')
    m.cube('neck', (-2, 31.5, -2), (4, 2.2, 4), stump)
    # Cloak: a shoulder piece and strips of different lengths below it.
    m.bone('cape', (0, 30, 4.4), 'body')
    m.cube('cape', (-5.5, 21, 4.1), (11, 9, 1), tatter, all={'ragged': 2, 'holes': 0.07})
    m.bone('cape_tail', (0, 21.5, 4.6), 'cape')
    x = -5.5
    for i, (w, length) in enumerate(((2, 14), (1, 8), (2, 17), (2, 11), (1, 15), (1, 6), (2, 13))):
        m.cube('cape_tail', (x, 22 - length, 4.2 + 0.03 * i), (w, length, 0.8), tatter,
               all={'ragged': 3, 'holes': 0.06}, rotation=(0, 0, (rnd('strip', i) - 0.5) * 8), pivot=(x + w / 2, 22, 4.6))
        x += w
    # Executioner's hood hanging behind the neck stump.
    m.bone('hood', (0, 31.5, 5.3), 'body', rotation=(12, 0, 0))
    m.cube('hood', (-3.5, 26.5, 5.3), (7, 5, 1.2), tatter, all={'ramp': HOOD, 'ragged': 0, 'holes': 0, 'embers': False})
    m.cube('hood', (-3, 22.5, 5.4), (6, 4, 1.2), tatter,
           all={'ramp': HOOD, 'ragged': 1, 'holes': 0, 'embers': False}, south={'eyes': {(1, 1), (4, 1), (1, 2)}},
           north={'eyes': {(1, 1), (4, 1), (4, 2)}})
    m.cube('hood', (-2, 20, 5.5), (4, 2.5, 1.2), tatter, all={'ramp': HOOD, 'ragged': 1, 'holes': 0, 'embers': False})
    m.cube('hood', (-1, 18.5, 5.6), (2, 1.5, 1.2), tatter, all={'ramp': HOOD, 'ragged': 1, 'holes': 0, 'embers': False})

    for side, sx in (('right', -1), ('left', 1)):
        arm = side + '_arm'
        m.bone(arm, (7 * sx, 28, 0), 'body')
        x = (lambda lo, w: lo if sx > 0 else -lo - w)
        m.cube(arm, (x(4.5, 6), 25, -3.5), (6, 5.5, 7), plate, all={'lames': 2})
        m.cube(arm, (x(5, 5.5), 22.5, -3.2), (5.5, 2.5, 6.4), plate, all=plain)
        m.cube(arm, (x(6, 4), 17, -2), (4, 6, 4), mail)
        m.cube(arm, (x(5.6, 4.8), 15.8, -2.4), (4.8, 2.2, 4.8), plate, all=plain)
        m.cube(arm, (x(5.5, 5), 12, -2.5), (5, 4, 5), plate,
               all={'rivets': False, 'straps': (1,), 'strap_sides': ('north', 'south', 'east', 'west')})

    m.bone('sword', (-8, 13, 0), 'right_arm')
    m.cube('sword', (-9, 12, -2), (2, 2, 5), grip)
    m.cube('sword', (-9.5, 11.5, 3), (3, 3, 2), plate, all=plain)
    m.cube('sword', (-9.5, 8.5, -4), (3, 9, 2), plate, all=plain)
    m.cube('sword', (-8.5, 10, -34), (1, 6, 30), blade)

    # The held head, hanging from a rope tied to its stem.
    m.bone('held_head', (8, 12, 0), 'left_arm')
    m.cube('held_head', (7.3, 9.5, -0.7), (1.4, 2.5, 1.4), rope)
    m.cube('held_head', (3.5, 0.5, -4.5), (9, 9, 9), pumpkin, north={'face': jack_face()})
    head_fire(m, 'held_head')

    m.write((5, 4))


# --- The steed ------------------------------------------------------------------------------------------

def steed():
    m = Model('gallows_steed', 512)
    plate = material(rusted, rust=0.6)
    lean = {'rivets': False}
    m.bone('root', (0, 0, 11))
    m.bone('body', (0, 29, 0), 'root')
    m.cube('body', (-6.5, 23, -16), (13, 12, 32), hide, all={'scars': 4})
    m.cube('body', (-5.5, 21.5, -14), (11, 2, 26), hide)
    # Chest barding with shoulder flaps.
    m.cube('body', (-7, 20.5, -18.5), (14, 12, 2.5), plate, all={'lames': 3})
    for sx in (-1, 1):
        m.cube('body', (7.0 if sx > 0 else -8.5, 21.5, -18), (1.5, 9, 8), plate, all={'lames': 3, 'rivets': False})
    m.cube('body', (-7.5, 26, -5), (15, 9, 11), material(cloth, ramp=CRIMSON), all={'ragged': 3, 'holes': 0.08},
           sides=('north', 'south', 'east', 'west'))
    m.bone('saddle', (0, 35, 0), 'body')
    m.cube('saddle', (-5.5, 35, -6), (11, 1, 11), material(leather))
    m.cube('saddle', (-2, 36, -7), (4, 2, 2), material(leather, stitch=False))
    m.cube('saddle', (-4, 36, 5), (8, 2, 2), material(leather, stitch=False))
    for sx in (-1, 1):
        x = 7.6 if sx > 0 else -8.4
        m.cube('saddle', (x, 24.5, -2.5), (0.8, 11, 1), material(leather, stitch=False))
        m.cube('saddle', (7.4 * sx - 1.4, 22.8, -4), (2.8, 0.8, 4), iron)
        m.cube('saddle', (7.4 * sx - 1.4, 22.8, -4.2), (2.8, 2.4, 0.6), iron)

    # Neck with crinet plates on the crest and the mane hanging on its right side.
    m.bone('neck', (0, 32, -13), 'body', rotation=(30, 0, 0))
    m.cube('neck', (-4, 28.5, -21.5), (8, 19, 9), hide, all={'scars': 2})
    for k in range(3):
        m.cube('neck', (-4.5, 31 + k * 5.5, -13.2), (9, 6, 1.8), plate, all={'lames': 2, **lean})
    m.bone('mane', (0, 32, -13), 'neck')
    for k in range(7):
        y = 31.5 + k * 2.4
        length = 5 + int(rnd('mane', k) * 6)
        m.cube('mane', (-5.6, y - length + 3, -15 + rnd('manez', k) * 1.5), (1.2, length, 2.2),
               material(mane, ragged=3), rotation=(0, 0, -8 - rnd('maner', k) * 10), pivot=(-5, y + 3, -14))
    m.bone('head', (0, 46, -17), 'neck', rotation=(12, 0, 0))
    m.cube('head', (-4, 43, -25), (8, 7, 9), steed_head)
    m.cube('head', (-3, 41, -33), (6, 6, 9), muzzle)
    m.cube('head', (-3, 40, -31), (6, 1, 7), hide)
    # Chanfron with a ridge and cheek guards.
    m.cube('head', (-3.5, 46.6, -32.6), (7, 1, 14), plate, all={'lames': 4})
    m.cube('head', (-0.5, 47.5, -31), (1, 0.8, 11), plate, all=lean)
    for sx in (-1, 1):
        m.cube('head', (4.0 if sx > 0 else -4.8, 43.5, -25.5), (0.8, 4, 6), plate, all=lean)
    m.cube('head', (-3.5, 50, -19), (2, 4, 2), hide)
    m.cube('head', (1.5, 50, -19), (2, 4, 2), hide)
    m.cube('head', (-1.5, 46, -27), (3, 5, 2), material(mane, ragged=2))
    m.cube('head', (-4, 42, -29), (8, 1, 1), chain)

    # Tail: a chain of four bones, widening into strands.
    parent = 'body'
    top = 33.0
    for i, (w, length, bend) in enumerate(((3, 6, 18), (4, 5.5, 5), (5, 5.5, 5), (6, 6, 4)), 1):
        name = 'tail_%d' % i
        m.bone(name, (0, top, 16), parent, rotation=(bend, 0, 0))
        if i < 4:
            m.cube(name, (-w / 2, top - length, 15), (w, length + 0.5, 3), material(mane, ragged=0))
        for k in range(3 if i < 4 else 4):
            sw = 1.4
            sx = -w / 2 + k * (w - sw) / (2 if i < 4 else 3)
            extra = 1 + rnd('strand', i, k) * (2.5 if i < 4 else 5)
            m.cube(name, (sx, top - length - extra + 0.5, 15.4 + rnd('sz', i, k)), (sw, length + extra, 1.4),
                   material(mane, ragged=3), rotation=(0, 0, (rnd('st', i, k) - 0.5) * 14),
                   pivot=(sx + sw / 2, top, 16))
        parent = name
        top -= length

    # Legs: upper and lower bones, feathering, hoof and shoe.
    for name, x, z, hind in (('leg_front_right', -4, -11, False), ('leg_front_left', 4, -11, False),
                             ('leg_back_right', -4, 11, True), ('leg_back_left', 4, 11, True)):
        m.bone(name, (x, 26, z), 'root')
        if hind:
            m.cube(name, (x - 3.5, 13.5, z - 4.5), (7, 13.5, 9), hide, all={'scars': 1})
        else:
            m.cube(name, (x - 3.25, 14, z - 3.25), (6.5, 12.5, 6.5), hide, all={'scars': 1})
        lower = name + '_lower'
        m.bone(lower, (x, 14.5, z), name)
        m.cube(lower, (x - 3, 12.5, z - 3), (6, 3, 6), hide)
        m.cube(lower, (x - 2.5, 4, z - 2.5), (5, 9, 5), hide)
        m.cube(lower, (x - 3.2, 2.8, z - 3.2), (6.4, 2.6, 6.4), material(mane, ragged=1))
        m.cube(lower, (x - 3, 0.8, z - 3), (6, 2.4, 6), hoof)
        m.cube(lower, (x - 3.3, 0, z - 3.3), (6.6, 0.9, 6.6), shoe)

    m.write((5, 5))


# --- Animations -----------------------------------------------------------------------------------------
# Keyframes as {bone: {channel: {time: [x, y, z]}}}, interpolated linearly. Rotations in degrees: positive x pitches a
# part's top towards the front (so a hanging arm swings back), y turns it, z tips it sideways.

def anim(length, bones, loop=True):
    a = {'animation_length': length, 'bones': {
        b: {ch: {('%g' % t if t != int(t) else '%.1f' % t): v for t, v in sorted(keys.items())}
            for ch, keys in chans.items()} for b, chans in bones.items()}}
    if loop is True:
        a['loop'] = True
    elif loop == 'hold':
        a['loop'] = 'hold_on_last_frame'
    return a


def wave(length, low, high, steps=4, offset=0.0, axis=0, base=(0, 0, 0)):
    """Sine keyframes between `low` and `high` on one axis, one cycle per `length`, starting at phase `offset`."""
    keys = {}
    for i in range(steps + 1):
        t = length * i / steps
        v = list(base)
        phase = math.sin((i / steps + offset) * 2 * math.pi)
        v[axis] = round(low + (high - low) * (phase + 1) / 2, 2)
        keys[round(t, 3)] = v
    return keys


def roar(key, length, steps=6, base=(0, 0, 0), size=1.0):
    """Random scale and rotation keyframes for a flame bone, looping back to the first key."""
    scale, rotation = {}, {}
    for i in range(steps + 1):
        j = i % steps
        t = round(length * i / steps, 3)
        sy = 0.75 + rnd(key, 'sy', j) * 0.6
        sx = 0.85 + rnd(key, 'sx', j) * 0.3
        scale[t] = [round(sx * size, 2), round(sy * size, 2), round(sx * size, 2)]
        rotation[t] = [round(base[0] + (rnd(key, 'rx', j) - 0.5) * 12, 1), base[1],
                       round(base[2] + (rnd(key, 'rz', j) - 0.5) * 10, 1)]
    return {'scale': scale, 'rotation': rotation}


def flicker_on(key, length, steps=10, on=0.7):
    """Random on/off scale keyframes; each switch happens within 0.01 s so it reads as a flicker, not a fade."""
    scale = {}
    for i in range(steps):
        t = round(length * i / steps, 3)
        lit = rnd(key, 'on', i) < on
        v = [1, 1, 1] if lit else [0, 0, 0]
        scale[t] = v
        scale[round(t + length / steps - 0.01, 3)] = v
    scale[length] = scale[0]
    return {'scale': scale}


def horseman_animations():
    hang = {'sword': {'rotation': {0: [22, 0, 0]}}}
    trinkets = lambda length, size: {
        'rope': {'rotation': wave(length, -size, size, offset=0.1)},
        'shackles': {'rotation': wave(length, -size * 1.5, size * 1.5, offset=0.35, axis=0)}}
    # The thighs are moved outward so the shins hang outside the steed's barrel, down to the stirrups.
    riding_legs = {'right_leg': {'rotation': {0: [-60, 0, 15]}, 'position': {0: [-2.5, 0, 0]}},
                   'left_leg': {'rotation': {0: [-60, 0, -15]}, 'position': {0: [2.5, 0, 0]}},
                   'right_shin': {'rotation': {0: [60, 0, -5]}}, 'left_shin': {'rotation': {0: [60, 0, 5]}},
                   'front_tasset': {'rotation': {0: [-50, 0, 0]}}, 'apron': {'rotation': {0: [-62, 0, 0]}},
                   'back_tasset': {'rotation': {0: [8, 0, 0]}}}
    a = {}
    a['head_fire'] = anim(1.2, {
        'head_flame_fire': roar('head_flame_fire', 1.2, steps=10),
        'head_flame_core': roar('head_flame_core', 1.2, steps=8),
        'face_hot': flicker_on('face_hot', 1.2, steps=12, on=0.6),
        'face_halo': flicker_on('face_halo', 1.2, steps=8, on=0.75)})
    a['idle'] = anim(2.4, {
        'body': {'position': wave(2.4, -0.3, 0, axis=1)},
        'cape': {'rotation': wave(2.4, 2, 6)},
        'cape_tail': {'rotation': wave(2.4, 0, 4, offset=0.2)},
        'hood': {'rotation': wave(2.4, -2, 2, offset=0.3)},
        'right_arm': {'rotation': {0: [-8, 0, 4]}},
        'left_arm': {'rotation': wave(2.4, -18, -12, base=(0, 0, -4))},
        'held_head': {'rotation': wave(2.4, 8, 14, offset=0.25)},
        **trinkets(2.4, 3), **hang})
    a['ride'] = anim(0.8, {
        **riding_legs,
        'body': {'rotation': {0: [6, 0, 0]}, 'position': wave(0.8, -0.6, 0.2, axis=1)},
        'cape': {'rotation': wave(0.8, 14, 22)},
        'cape_tail': {'rotation': wave(0.8, 6, 16, offset=0.3)},
        'hood': {'rotation': wave(0.8, 10, 18, offset=0.2)},
        'right_arm': {'rotation': {0: [-35, 0, 6]}},
        'sword': {'rotation': {0: [-20, 0, 0]}},
        'left_arm': {'rotation': wave(0.8, -42, -36, base=(0, 0, -6))},
        'held_head': {'rotation': wave(0.8, 30, 40, offset=0.25)},
        'head_flame': {'rotation': {0: [-12, 0, 0]}},
        **trinkets(0.8, 8)})
    a['charge'] = anim(0.5, {
        **riding_legs,
        'body': {'rotation': {0: [22, 0, 0]}, 'position': wave(0.5, -1.0, 0.4, axis=1)},
        'cape': {'rotation': wave(0.5, 38, 52)},
        'cape_tail': {'rotation': wave(0.5, 20, 40, offset=0.3)},
        'hood': {'rotation': wave(0.5, 30, 45, offset=0.2)},
        'right_arm': {'rotation': {0: [-88, 0, 8]}},
        'sword': {'rotation': {0: [72, 0, 0]}},
        'left_arm': {'rotation': {0: [-25, 0, -35]}},
        'held_head': {'rotation': wave(0.5, 20, 34)},
        'head_flame': {'rotation': {0: [-45, 0, 0]}, 'scale': {0: [1.1, 1.25, 1.1]}},
        **trinkets(0.5, 14)})
    a['swing'] = anim(0.7, {
        'body': {'rotation': {0: [0, 0, 0], 0.25: [-10, 15, 0], 0.42: [18, -20, 0], 0.7: [0, 0, 0]}},
        'right_arm': {'rotation': {0: [-35, 0, 6], 0.25: [-160, 0, 15], 0.42: [-30, 0, -10], 0.55: [-15, 0, -5],
                                   0.7: [-35, 0, 6]}},
        'sword': {'rotation': {0: [-20, 0, 0], 0.25: [-10, 0, 0], 0.42: [40, 0, 0], 0.7: [-20, 0, 0]}}}, loop=False)
    a['throw_head'] = anim(0.9, {
        'body': {'rotation': {0: [0, 0, 0], 0.35: [-8, -25, 0], 0.55: [15, 20, 0], 0.9: [0, 0, 0]}},
        'left_arm': {'rotation': {0: [-30, 0, -6], 0.35: [55, 0, -15], 0.55: [-150, 0, 0], 0.7: [-80, 0, 0],
                                  0.9: [-30, 0, -6]}},
        'head_flame': {'rotation': {0: [0, 0, 0], 0.35: [25, 0, 0], 0.5: [-50, 0, 0]},
                      'scale': {0: [1, 1, 1], 0.35: [1.2, 1.4, 1.2], 0.5: [1.3, 1.6, 1.3]}},
        'held_head': {'scale': {0: [1, 1, 1], 0.5: [1, 1, 1], 0.55: [0, 0, 0], 0.9: [0, 0, 0]}}}, loop=False)
    a['headless_reach'] = anim(1.0, {
        'body': {'rotation': wave(1.0, 12, 18)},
        'cape': {'rotation': wave(1.0, 18, 30)},
        'right_arm': {'rotation': {0: [-25, 0, 12]}},
        'sword': {'rotation': {0: [-25, 0, 0]}},
        'left_arm': {'rotation': wave(1.0, -100, -84, base=(0, 0, 8))},
        'held_head': {'scale': {0: [0, 0, 0]}}})
    a['walk'] = anim(1.4, {
        'right_leg': {'rotation': wave(1.4, -24, 24)},
        'left_leg': {'rotation': wave(1.4, -24, 24, offset=0.5)},
        'right_shin': {'rotation': wave(1.4, 0, 34, offset=0.85)},
        'left_shin': {'rotation': wave(1.4, 0, 34, offset=0.35)},
        'front_tasset': {'rotation': wave(1.4, -14, 0, offset=0.25)},
        'apron': {'rotation': wave(0.7, -20, -6, offset=0.25)},
        'body': {'position': wave(0.7, -0.6, 0, axis=1), 'rotation': wave(1.4, -3, 3, axis=1)},
        'cape': {'rotation': wave(0.7, 4, 10)},
        'cape_tail': {'rotation': wave(0.7, 2, 10, offset=0.3)},
        'right_arm': {'rotation': wave(1.4, 2, 12, base=(0, 0, 4))},
        'sword': {'rotation': {0: [24, 0, 0]}},
        'left_arm': {'rotation': wave(1.4, -28, -14, offset=0.5, base=(0, 0, -4))},
        'held_head': {'rotation': wave(1.4, 8, 22, offset=0.75)},
        **trinkets(0.7, 6)})
    a['swing_foot'] = anim(1.0, {
        'right_leg': {'rotation': {0: [0, 0, 0], 0.35: [10, 0, 0], 0.55: [-35, 0, 0], 1.0: [0, 0, 0]}},
        'right_shin': {'rotation': {0: [0, 0, 0], 0.35: [10, 0, 0], 0.55: [30, 0, 0], 1.0: [0, 0, 0]}},
        'left_leg': {'rotation': {0: [0, 0, 0], 0.55: [20, 0, 0], 1.0: [0, 0, 0]}},
        'left_shin': {'rotation': {0: [0, 0, 0], 0.55: [15, 0, 0], 1.0: [0, 0, 0]}},
        'body': {'rotation': {0: [0, 0, 0], 0.35: [-14, 20, 0], 0.55: [24, -15, 0], 0.75: [18, -10, 0],
                              1.0: [0, 0, 0]},
                 'position': {0: [0, 0, 0], 0.55: [0, -1.5, -2], 1.0: [0, 0, 0]}},
        'right_arm': {'rotation': {0: [2, 0, 4], 0.35: [-170, 0, 20], 0.55: [-40, 0, -12], 0.75: [-25, 0, -8],
                                   1.0: [2, 0, 4]}},
        'sword': {'rotation': {0: [24, 0, 0], 0.35: [-15, 0, 0], 0.55: [45, 0, 0], 1.0: [24, 0, 0]}}},
        loop=False)
    # Sweep: wind up turned to the right, then a level swing across the front.
    a['sweep'] = anim(1.0, {
        'body': {'rotation': {0: [0, 0, 0], 0.35: [-8, 70, 0], 0.55: [14, -110, 0], 0.75: [10, -120, 0],
                              1.0: [0, 0, 0]},
                 'position': {0: [0, 0, 0], 0.55: [0, -1.5, 0], 1.0: [0, 0, 0]}},
        'right_arm': {'rotation': {0: [2, 0, 4], 0.35: [-20, 0, 80], 0.55: [-10, 0, 88], 0.75: [-10, 0, 70],
                                   1.0: [2, 0, 4]}},
        'sword': {'rotation': {0: [24, 0, 0], 0.35: [-10, 0, 0], 0.55: [0, 0, 0], 1.0: [24, 0, 0]}},
        'right_leg': {'rotation': {0: [0, 0, 0], 0.55: [-20, 0, 0], 1.0: [0, 0, 0]}},
        'left_leg': {'rotation': {0: [0, 0, 0], 0.55: [15, 0, 0], 1.0: [0, 0, 0]}}}, loop=False)
    a['sweep_mounted'] = anim(0.9, {
        'body': {'rotation': {0: [0, 0, 0], 0.3: [-5, 75, 0], 0.45: [10, -110, 0], 0.65: [8, -115, 0],
                              0.9: [0, 0, 0]}},
        'right_arm': {'rotation': {0: [-35, 0, 6], 0.3: [-25, 0, 80], 0.45: [-15, 0, 88], 0.65: [-15, 0, 70],
                                   0.9: [-35, 0, 6]}},
        'sword': {'rotation': {0: [-20, 0, 0], 0.3: [-10, 0, 0], 0.45: [0, 0, 0], 0.9: [-20, 0, 0]}}}, loop=False)
    # Death: kneel, the head's fire goes out, fall forward.
    a['death'] = anim(2.4, {
        'root': {'position': {0: [0, 0, 0], 0.6: [0, -7, 0], 1.3: [0, -7, 0], 1.9: [0, -12, -4], 2.4: [0, -12, -4]},
                 'rotation': {0: [0, 0, 0], 1.3: [8, 0, 0], 1.9: [80, 0, 0], 2.4: [84, 0, 0]}},
        'right_leg': {'rotation': {0: [0, 0, 0], 0.6: [-8, 4, 0], 2.4: [-8, 4, 0]}},
        'left_leg': {'rotation': {0: [0, 0, 0], 0.6: [-4, -4, 0], 2.4: [-4, -4, 0]}},
        'right_shin': {'rotation': {0: [0, 0, 0], 0.6: [88, 0, 0], 1.9: [70, 0, 0], 2.4: [70, 0, 0]}},
        'left_shin': {'rotation': {0: [0, 0, 0], 0.6: [84, 0, 0], 1.9: [66, 0, 0], 2.4: [66, 0, 0]}},
        'right_arm': {'rotation': {0: [0, 0, 0], 0.6: [-20, 0, 25], 1.3: [-10, 0, 40], 1.9: [-150, 0, 30],
                                   2.4: [-160, 0, 30]}},
        'sword': {'rotation': {0: [22, 0, 0], 0.5: [60, 0, 30], 2.4: [60, 0, 30]}},
        'left_arm': {'rotation': {0: [-12, 0, -4], 0.6: [10, 0, -20], 1.9: [-160, 0, -20], 2.4: [-165, 0, -20]}},
        'cape': {'rotation': {0: [3, 0, 0], 1.9: [-30, 0, 0], 2.4: [-35, 0, 0]}},
        'face_glow': {'scale': {0: [1, 1, 1], 0.6: [1, 1, 1], 0.9: [0, 0, 0], 2.4: [0, 0, 0]}},
        'head_flame': {'scale': {0: [1, 1, 1], 0.5: [0.8, 0.5, 0.8], 0.9: [0.5, 0.2, 0.5], 1.2: [0, 0, 0],
                                2.4: [0, 0, 0]}}}, loop='hold')
    write(ANIMATIONS + 'horseman.animation.json', {'format_version': '1.8.0', 'animations': a})


def steed_animations():
    tail = ('tail_1', 'tail_2', 'tail_3', 'tail_4')

    def tail_motion(length, sway, trail, lift=0.0, steps=8):
        """Tail keyframes with each bone slightly behind the one above, so a wave runs down the tail: `sway` degrees
        side to side, `trail` up and down, and `lift` raising the root."""
        out = {}
        for i, bone in enumerate(tail):
            side = wave(length, -sway, sway, steps=steps, offset=-0.12 * i, axis=2)
            up = wave(length, -trail, trail, steps=steps, offset=0.25 - 0.12 * i)
            keys = {t: [up[t][0] + (lift if i == 0 else lift * 0.15), 0, side[t][2]] for t in side}
            out[bone] = {'rotation': keys}
        return out

    a = {}
    a['idle'] = anim(3.0, {
        'neck': {'rotation': wave(3.0, -3, 2)},
        'head': {'rotation': wave(3.0, -2, 4, offset=0.2)},
        'mane': {'rotation': wave(3.0, -2, 2, axis=2)},
        'body': {'position': wave(3.0, -0.2, 0, axis=1)},
        **tail_motion(3.0, 6, 2)})

    def stride(length, swing, bend, bob, pitch, phases):
        legs = {}
        for name, phase in phases:
            hind = 'back' in name
            legs[name] = {'rotation': wave(length, -swing, swing, steps=8, offset=phase)}
            # The lower leg bends while the leg swings forward and straightens to land.
            legs[name + '_lower'] = {'rotation': wave(length, 0, bend * (0.7 if hind else 1.0), steps=8,
                                                      offset=phase + 0.25)}
        return {**legs, 'body': {'position': wave(length / 2, -bob, 0, axis=1), 'rotation': wave(length, -pitch, pitch)}}

    a['walk'] = anim(1.2, {
        **stride(1.2, 20, 35, 0.4, 1.5, (('leg_front_left', 0.0), ('leg_back_right', 0.1), ('leg_front_right', 0.5),
                                        ('leg_back_left', 0.6))),
        'neck': {'rotation': wave(0.6, -4, 4)},
        'mane': {'rotation': wave(0.6, -4, 4, axis=2)},
        **tail_motion(1.2, 8, 4, lift=6)})
    a['gallop'] = anim(0.5, {
        **stride(0.5, 44, 70, 1.4, 5, (('leg_front_left', 0.0), ('leg_front_right', 0.12), ('leg_back_left', 0.5),
                                       ('leg_back_right', 0.62))),
        'neck': {'rotation': wave(0.5, -14, -4, steps=8, offset=0.25)},
        'head': {'rotation': wave(0.5, -6, 4, steps=8, offset=0.5)},
        'mane': {'rotation': {0: [-20, 0, -15]}},
        **tail_motion(0.5, 10, 9, lift=40)})
    a['rear'] = anim(1.4, {
        'root': {'rotation': {0: [0, 0, 0], 0.4: [-38, 0, 0], 1.0: [-38, 0, 0], 1.4: [0, 0, 0]},
                 'position': {0: [0, 0, 0], 0.4: [0, 2, 0], 1.0: [0, 2, 0], 1.4: [0, 0, 0]}},
        'leg_back_right': {'rotation': {0: [0, 0, 0], 0.4: [38, 0, 0], 1.0: [38, 0, 0], 1.4: [0, 0, 0]}},
        'leg_back_left': {'rotation': {0: [0, 0, 0], 0.4: [38, 0, 0], 1.0: [38, 0, 0], 1.4: [0, 0, 0]}},
        'leg_back_right_lower': {'rotation': {0: [0, 0, 0], 0.4: [-10, 0, 0], 1.0: [-10, 0, 0], 1.4: [0, 0, 0]}},
        'leg_back_left_lower': {'rotation': {0: [0, 0, 0], 0.4: [-10, 0, 0], 1.0: [-10, 0, 0], 1.4: [0, 0, 0]}},
        'leg_front_right': {'rotation': {0: [0, 0, 0], 0.4: [-70, 0, 0], 0.6: [-35, 0, 0], 0.8: [-75, 0, 0],
                                         1.0: [-45, 0, 0], 1.4: [0, 0, 0]}},
        'leg_front_left': {'rotation': {0: [0, 0, 0], 0.4: [-45, 0, 0], 0.6: [-80, 0, 0], 0.8: [-40, 0, 0],
                                        1.0: [-70, 0, 0], 1.4: [0, 0, 0]}},
        'leg_front_right_lower': {'rotation': {0: [0, 0, 0], 0.4: [80, 0, 0], 0.6: [40, 0, 0], 0.8: [85, 0, 0],
                                               1.0: [50, 0, 0], 1.4: [0, 0, 0]}},
        'leg_front_left_lower': {'rotation': {0: [0, 0, 0], 0.4: [50, 0, 0], 0.6: [90, 0, 0], 0.8: [45, 0, 0],
                                              1.0: [80, 0, 0], 1.4: [0, 0, 0]}},
        'neck': {'rotation': {0: [0, 0, 0], 0.5: [-25, 0, 0], 1.0: [-20, 0, 0], 1.4: [0, 0, 0]}},
        'head': {'rotation': {0: [0, 0, 0], 0.5: [-15, 0, 0], 1.4: [0, 0, 0]}},
        **tail_motion(1.4, 12, 6, lift=30, steps=6)}, loop=False)
    a['death'] = anim(2.0, {
        'root': {'rotation': {0: [0, 0, 0], 0.5: [0, 0, 12], 1.4: [0, 0, 88], 2.0: [0, 0, 90]},
                 'position': {0: [0, 0, 0], 0.5: [0, -3, 0], 1.4: [5, -3, 0], 2.0: [5, -3, 0]}},
        'leg_front_right': {'rotation': {0: [0, 0, 0], 0.5: [-60, 0, 0], 1.4: [-30, 0, 20], 2.0: [-30, 0, 20]}},
        'leg_front_left': {'rotation': {0: [0, 0, 0], 0.5: [-50, 0, 0], 1.4: [-15, 0, -10], 2.0: [-15, 0, -10]}},
        'leg_front_right_lower': {'rotation': {0: [0, 0, 0], 0.5: [70, 0, 0], 1.4: [40, 0, 0], 2.0: [40, 0, 0]}},
        'leg_front_left_lower': {'rotation': {0: [0, 0, 0], 0.5: [60, 0, 0], 1.4: [25, 0, 0], 2.0: [25, 0, 0]}},
        'leg_back_right': {'rotation': {0: [0, 0, 0], 0.5: [40, 0, 0], 1.4: [25, 0, 15], 2.0: [25, 0, 15]}},
        'leg_back_left': {'rotation': {0: [0, 0, 0], 0.5: [45, 0, 0], 1.4: [10, 0, -10], 2.0: [10, 0, -10]}},
        'leg_back_right_lower': {'rotation': {0: [0, 0, 0], 0.5: [-20, 0, 0], 2.0: [-30, 0, 0]}},
        'leg_back_left_lower': {'rotation': {0: [0, 0, 0], 0.5: [-25, 0, 0], 2.0: [-15, 0, 0]}},
        'neck': {'rotation': {0: [0, 0, 0], 0.5: [-20, 0, 0], 1.4: [30, 0, 10], 2.0: [32, 0, 10]}},
        'tail_1': {'rotation': {0: [0, 0, 0], 1.4: [5, 0, -20], 2.0: [5, 0, -20]}},
        'tail_2': {'rotation': {0: [0, 0, 0], 1.4: [10, 0, -10], 2.0: [10, 0, -10]}},
        'tail_3': {'rotation': {0: [0, 0, 0], 1.4: [10, 0, -5], 2.0: [10, 0, -5]}}}, loop='hold')
    write(ANIMATIONS + 'gallows_steed.animation.json', {'format_version': '1.8.0', 'animations': a})


# --- Item icons -----------------------------------------------------------------------------------------

def sword_icon():
    """The sword item texture: a broad three-pixel blade on the diagonal with a lit edge, rust along the spine,
    a hole near the square end, a crossguard, a wrapped grip, a pommel and a dark outline."""
    OUTLINE = (14, 12, 16)
    cells = {}
    # u = x - y runs along the sword (pommel at bottom left, square end at top right), v = x + y across it. The blade
    # covers v = 13 to 17.
    for x in range(16):
        for y in range(16):
            u, v = x - y, x + y
            if -5 <= u <= 12 and 13 <= v <= 17:
                if (u, v) == (8, 16):
                    continue  # the hole
                if v == 17:
                    c = IRON[5] if u % 5 else IRON[4]  # edge
                elif v == 16:
                    c = IRON[4]
                elif v == 15:
                    c = IRON[2] if 0 <= u <= 6 else IRON[3]  # fuller
                elif v == 14:
                    c = IRON[2]
                else:
                    c = IRON[1]  # the spine
                if v < 16 and rnd('sword', 'rust', x, y) < (0.5 if v == 13 else 0.22):
                    c = pick(RUST, 0.25 + rnd('sword', 'rr', x, y) * 0.55)
                cells[(x, y)] = c
            elif u in (-6, -7) and 10 <= v <= 20:
                cells[(x, y)] = IRON[5] if u == -6 else IRON[3]  # the guard
            elif -11 <= u <= -8 and 14 <= v <= 16:
                cells[(x, y)] = LEATHER[3] if (u + v) % 4 < 2 else LEATHER[0]  # the wrapped grip
            elif u in (-12, -13) and 13 <= v <= 17:
                cells[(x, y)] = IRON[4] if v == 16 else IRON[2]  # the pommel
    im = Image.new('RGBA', (16, 16), CLEAR)
    for (x, y), c in cells.items():
        im.putpixel((x, y), rgba(c))
    for y in range(16):
        for x in range(16):
            if (x, y) not in cells and any(
                    (x + dx, y + dy) in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im.putpixel((x, y), rgba(OUTLINE))
    save(im, ITEM + 'executioners_sword.png')


def head_textures():
    """Front, side and top textures for the head item, which uses a block model."""
    def face_tex(name, face=None, top=False):
        im = Image.new('RGBA', (16, 16), CLEAR)
        glow = Image.new('RGBA', (16, 16), CLEAR)
        sheet = type('S', (), {'im': im, 'glow': glow})()
        f = Face(sheet, 0, 0, 16, 16, 'up' if top else 'north', ('item', name))
        big = {}
        if face:
            small = jack_face()
            for x in range(16):
                for y in range(16):
                    kind = small.get((x * 9 // 16, y * 9 // 16))
                    if kind is not None:
                        big[(x, y)] = kind
        pumpkin(f, face=big or None)
        if top:
            for x in range(6, 10):
                for y in range(6, 10):
                    f.put(x, y, pick(STEM, rnd('stem', x, y)))
            # Rope knotted around the stem.
            for x, y in ((5, 6), (5, 7), (5, 8), (6, 9), (7, 10), (8, 10), (9, 9), (10, 8), (10, 7), (11, 7), (12, 8)):
                f.put(x, y, ROPE[(x + y) % 3])
        save(im, ITEM + name + '.png')

    face_tex('horseman_head_front', face=True)
    face_tex('horseman_head_side')
    face_tex('horseman_head_top', top=True)


def item_models():
    """Item model JSON for the sword, the two head items and the spawn eggs."""
    models = ASSETS + 'models/item/'
    write(models + 'executioners_sword.json', {
        'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'critfall_horseman:item/executioners_sword'},
        # Oversized in third person. In first person about 1.3x a normal sword: the broad blade fills the texture
        # corner to corner, so a smaller scale than vanilla's 0.68 is needed to look that size.
        'display': {'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 5, 0.5],
                                              'scale': [1.25, 1.25, 0.85]},
                    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 5, 0.5],
                                             'scale': [1.25, 1.25, 0.85]},
                    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13],
                                              'scale': [0.6, 0.6, 0.6]},
                    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13],
                                             'scale': [0.6, 0.6, 0.6]}}})
    write(models + 'horseman_head.json', {
        'parent': 'minecraft:block/orientable', 'textures': {
            'top': 'critfall_horseman:item/horseman_head_top', 'front': 'critfall_horseman:item/horseman_head_front',
            'side': 'critfall_horseman:item/horseman_head_side',
            'particle': 'critfall_horseman:item/horseman_head_side'}})
    # Same look as the wearable head.
    write(models + 'burning_head.json', {'parent': 'critfall_horseman:item/horseman_head'})
    for egg in ('horseman_spawn_egg', 'gallows_steed_spawn_egg'):
        write(models + egg + '.json', {'parent': 'minecraft:item/template_spawn_egg'})


def main():
    horseman()
    steed()
    horseman_animations()
    steed_animations()
    sword_icon()
    head_textures()
    item_models()


if __name__ == '__main__':
    main()
