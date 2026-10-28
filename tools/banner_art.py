"""Draws the 1500x500 store banner in the icon's pixel style: night sky, moon, mist, a line of fire, the rider at a
charge rendered from his model, and the title in pixel letters next to the icon. Run from the repo root after
horseman_art.py and icon_art.py, with Python 3, Pillow and numpy. Writes branding/horseman-banner.png. Deterministic."""
import hashlib
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import icon_art  # noqa: E402
import render_models as R  # noqa: E402

OUT = 'branding/horseman-banner.png'
W, H, SCALE = 375, 125, 4  # drawn at quarter size, then scaled up 4x

SKY = [(9, 9, 22), (14, 13, 32), (22, 20, 46), (33, 30, 64), (48, 42, 82)]
GROUND = (10, 9, 16)
MIST = (120, 112, 150)
MOON = [(232, 228, 208), (210, 204, 182), (186, 180, 160)]
NAVY = (13, 17, 38)
GOLD = (255, 224, 138)
EMBER = (240, 146, 58)

# 5x7 glyphs, only the letters the title uses.
GLYPHS = {
    'A': ('01110', '10001', '10001', '11111', '10001', '10001', '10001'),
    'C': ('01111', '10000', '10000', '10000', '10000', '10000', '01111'),
    'D': ('11110', '10001', '10001', '10001', '10001', '10001', '11110'),
    'E': ('11111', '10000', '10000', '11110', '10000', '10000', '11111'),
    'F': ('11111', '10000', '10000', '11110', '10000', '10000', '10000'),
    'H': ('10001', '10001', '10001', '11111', '10001', '10001', '10001'),
    'I': ('11111', '00100', '00100', '00100', '00100', '00100', '11111'),
    'L': ('10000', '10000', '10000', '10000', '10000', '10000', '11111'),
    'M': ('10001', '11011', '10101', '10101', '10001', '10001', '10001'),
    'N': ('10001', '11001', '10101', '10011', '10001', '10001', '10001'),
    'O': ('01110', '10001', '10001', '10001', '10001', '10001', '01110'),
    'R': ('11110', '10001', '10001', '11110', '10100', '10010', '10001'),
    'S': ('01111', '10000', '10000', '01110', '00001', '00001', '11110'),
    'T': ('11111', '00100', '00100', '00100', '00100', '00100', '00100'),
    ':': ('00000', '00100', '00100', '00000', '00100', '00100', '00000'),
    ' ': ('00000',) * 7,
}


def rnd(*key):
    return hashlib.sha256(':'.join(map(str, key)).encode()).digest()[0] / 256.0


def hex_colour(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def put(im, x, y, colour):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), colour)


def text(im, x, y, words, size, colour, shadow=NAVY):
    """`words` in the 5x7 pixel font, scaled by `size`, with a hard shadow down and right."""
    for dx, dy, c in ((size, size, shadow), (0, 0, colour)):
        cx = x + dx
        for ch in words:
            for row, line in enumerate(GLYPHS[ch]):
                for col, bit in enumerate(line):
                    if bit == '1':
                        for i in range(size):
                            for j in range(size):
                                put(im, cx + col * size + i, y + dy + row * size + j, c)
            cx += 6 * size


def background():
    im = Image.new('RGB', (W, H))
    horizon = 92
    for y in range(H):
        t = min(1.0, y / horizon)
        c = SKY[min(len(SKY) - 1, int(t * len(SKY)))]
        for x in range(W):
            im.putpixel((x, y), c)
    for i in range(140):
        x, y = int(rnd('star', i, 'x') * W), int(rnd('star', i, 'y') * (horizon - 10))
        bright = rnd('star', i, 'b')
        put(im, x, y, (200, 200, 220) if bright > 0.85 else (120, 120, 150))
    mx, my, r = 318, 30, 17
    for y in range(my - r, my + r + 1):
        for x in range(mx - r, mx + r + 1):
            d = ((x - mx) ** 2 + (y - my) ** 2) ** 0.5
            if d <= r:
                shade = MOON[0] if d < r - 3 else MOON[1]
                if rnd('crater', x // 4, y // 4) > 0.8 and d < r - 2:
                    shade = MOON[2]
                put(im, x, y, shade)
    # Hills, field and mist.
    for x in range(W):
        top = horizon + int(4 * np.sin(x / 23.0) + 3 * np.sin(x / 9.0 + 1))
        for y in range(top, H):
            put(im, x, y, GROUND)
    for y in range(horizon - 6, horizon + 10):
        for x in range(W):
            a = (1 - abs(y - (horizon + 2)) / 9.0) * 0.35 * (0.6 + 0.4 * np.sin(x / 17.0 + y))
            if a > 0:
                c = im.getpixel((x, y))
                put(im, x, y, tuple(int(c[k] * (1 - a) + MIST[k] * a) for k in range(3)))
    # Line of fire.
    for i, x in enumerate(range(140, 300, 3)):
        h = 2 + int(rnd('fire', i) * 5)
        base = 112 + int(rnd('firey', i) * 3)
        for k in range(h):
            put(im, x, base - k, GOLD if k < h // 3 else EMBER)
            if k < h - 2:
                put(im, x + 1, base - k, EMBER)
    return im


def icon(im, x, y, size):
    for row, line in enumerate(icon_art.SPRITE):
        for col, ch in enumerate(line):
            if ch != '.':
                c = hex_colour(icon_art.COLOURS[ch])
                for i in range(size):
                    for j in range(size):
                        put(im, x + col * size + i, y + row * size + j, c)


def rider():
    """The mounted rider at a charge, rendered at the banner's pixel scale."""
    rider_geo, rider_anims, rider_tex, rider_glow = R.load('horseman')
    horse_geo, horse_anims, horse_tex, horse_glow = R.load('gallows_steed')
    seat = R.trans(-R.SEAT[0], R.SEAT[1], R.SEAT[2])
    rider_pose = R.pose(rider_anims, [('charge', 0), ('head_fire', 0)], 0.2)
    canvas = R.Canvas((700, 560), SCALE * 1.5, -105, 6, (0, 30, -4))
    canvas.draw(R.faces(horse_geo, R.pose(horse_anims, [('gallop', 0)], 0.2)), horse_tex, horse_glow)
    canvas.draw(R.faces(rider_geo, rider_pose, seat), rider_tex, rider_glow)
    rgba = np.zeros((560, 700, 4), np.uint8)
    rgba[..., :3] = np.clip(canvas.rgb, 0, 255).astype(np.uint8)
    rgba[..., 3] = np.where(canvas.alpha, 255, 0)
    return Image.fromarray(rgba, 'RGBA')


def main():
    im = background()
    icon(im, 14, 22, 3)
    text(im, 70, 20, 'CRITFALL:', 2, (200, 196, 214))
    text(im, 70, 38, 'HEADLESS', 4, GOLD)
    text(im, 70, 72, 'HORSEMAN', 4, GOLD)
    big = im.resize((W * SCALE, H * SCALE), Image.NEAREST).convert('RGBA')
    big.alpha_composite(rider(), (800, -20))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    big.convert('RGB').save(OUT)


if __name__ == '__main__':
    main()
