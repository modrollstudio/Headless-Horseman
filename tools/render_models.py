"""Renders the GeckoLib models, posed by their animations, to design/renders/ so the art can be checked without
starting the game. Run from the repo root after horseman_art.py, with Python 3, Pillow and numpy.

Bones and cubes are placed the way GeckoLib does it: x mirrored, x and y rotations of bones, cubes and keyframes
negated, each bone rotated about its pivot in z, y, x order, and face corners given their UVs in GeckoLib's order.
Rendering is orthographic with no face culling, like GeckoLib's entity cutout; glowmask pixels are unshaded. The
rider is drawn on the steed at the same offset the game seats him at."""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ASSETS = 'common/src/main/resources/assets/critfall_horseman/'
OUT = 'design/renders/'

# Rider model origin relative to the steed's, in pixels; matches GallowsSteed.SEAT.
SEAT = (0, 20, -0.5)


def load(name):
    geo = json.load(open(ASSETS + 'geo/entity/' + name + '.geo.json'))['minecraft:geometry'][0]
    anims = json.load(open(ASSETS + 'animations/entity/' + name + '.animation.json'))['animations']
    tex = np.asarray(Image.open(ASSETS + 'textures/entity/' + name + '.png').convert('RGBA')).astype(np.float32)
    glow = np.asarray(Image.open(ASSETS + 'textures/entity/' + name + '_glowmask.png').convert('RGBA'))[..., 3] > 0
    return geo, anims, tex, glow


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])


def trans(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def scale(x, y, z):
    return np.diag([x, y, z, 1.0])


def sample(keys, t, length, loop):
    """A channel value at `t`: interpolated between keyframes, wrapped if looping, held after the end otherwise."""
    if isinstance(keys, list):
        return keys
    times = sorted((float(k), v) for k, v in keys.items())
    if loop is True and length > 0:
        t = t % length
    if t <= times[0][0]:
        return times[0][1]
    for (t0, v0), (t1, v1) in zip(times, times[1:]):
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return times[-1][1]


def pose(anims, layers, t):
    """Bone transforms at `t`; later layers override earlier ones per bone and channel, like later GeckoLib controllers."""
    out = {}
    for name, start in layers:
        a = anims[name]
        local = t - start
        for bone, chans in a['bones'].items():
            for ch, keys in chans.items():
                out.setdefault(bone, {})[ch] = sample(keys, local, a.get('animation_length', 0), a.get('loop'))
    return out


def faces(geo, posed, base=np.eye(4), hidden=()):
    """Every face as (corner positions, corner UVs), in GeckoLib's vertex and quad order."""
    bones = {b['name']: b for b in geo['bones']}
    children = {}
    for b in geo['bones']:
        children.setdefault(b.get('parent'), []).append(b['name'])
    out = []

    def walk(name, m):
        b = bones[name]
        p = posed.get(name, {})
        px, py, pz = -b['pivot'][0], b['pivot'][1], b['pivot'][2]
        r0 = b.get('rotation', [0, 0, 0])
        ar = p.get('rotation', [0, 0, 0])
        rx, ry, rz = math.radians(-(r0[0] + ar[0])), math.radians(-(r0[1] + ar[1])), math.radians(r0[2] + ar[2])
        pos = p.get('position', [0, 0, 0])
        sc = p.get('scale', [1, 1, 1])
        m = (m @ trans(-pos[0], pos[1], pos[2]) @ trans(px, py, pz) @ rot_z(rz) @ rot_y(ry) @ rot_x(rx)
             @ scale(*sc) @ trans(-px, -py, -pz))
        if name in hidden or min(sc) <= 0.001:
            return
        for c in b.get('cubes', []):
            cm = m
            if 'rotation' in c:
                cp = c['pivot']
                cr = c['rotation']
                cm = (m @ trans(-cp[0], cp[1], cp[2]) @ rot_z(math.radians(cr[2])) @ rot_y(math.radians(-cr[1]))
                      @ rot_x(math.radians(-cr[0])) @ trans(cp[0], -cp[1], -cp[2]))
            (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
            inf = c.get('inflate', 0)
            x0, y0, z0 = -(ox + sx) - inf, oy - inf, oz - inf
            x1, y1, z1 = x0 + sx + 2 * inf, y0 + sy + 2 * inf, z0 + sz + 2 * inf
            blb, brb, tlb, trb = (x0, y0, z0), (x0, y0, z1), (x0, y1, z0), (x0, y1, z1)
            tlf, trf, blf, brf = (x1, y1, z0), (x1, y1, z1), (x1, y0, z0), (x1, y0, z1)
            quads = {'west': (trb, tlb, blb, brb), 'east': (tlf, trf, brf, blf), 'north': (tlb, tlf, blf, blb),
                     'south': (trf, trb, brb, brf), 'up': (trb, trf, tlf, tlb), 'down': (blb, blf, brf, brb)}
            for side, uv in c['uv'].items():
                u, v = uv['uv']
                w, h = uv['uv_size']
                corners = np.array([[*q, 1.0] for q in quads[side]]) @ cm.T
                uvs = [(u + w, v), (u, v), (u, v + h), (u + w, v + h)]
                out.append((corners[:, :3], uvs))
        for child in children.get(name, []):
            walk(child, m)

    for root in children.get(None, []):
        walk(root, base)
    return out


class Canvas:
    def __init__(self, size, px, yaw, pitch, centre):
        self.size, self.px = size, px
        self.rgb = np.zeros((size[1], size[0], 3), np.float32)
        self.alpha = np.zeros((size[1], size[0]), bool)
        self.depth = np.full((size[1], size[0]), np.inf, np.float32)
        y, p = math.radians(yaw), math.radians(pitch)
        # The camera looks along f; yaw 0 looks at the model's front.
        f = np.array([math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])
        r = np.cross(f, [0, 1, 0])
        r /= np.linalg.norm(r)
        self.f, self.r, self.u = f, r, np.cross(r, f)
        self.centre = np.array(centre, float)
        self.light = np.array([0.4, 0.85, -0.35])
        self.light /= np.linalg.norm(self.light)

    def project(self, p):
        q = p - self.centre
        return (self.size[0] / 2 + (q @ self.r) * self.px, self.size[1] / 2 - (q @ self.u) * self.px, q @ self.f)

    def draw(self, quads, tex, glow):
        th, tw = tex.shape[:2]
        for corners, uvs in quads:
            sx, sy, sd = self.project(corners)
            e_u = np.array([sx[0] - sx[1], sy[0] - sy[1]])
            e_v = np.array([sx[2] - sx[1], sy[2] - sy[1]])
            det = e_u[0] * e_v[1] - e_u[1] * e_v[0]
            if abs(det) < 1e-6:
                continue
            n = np.cross(corners[0] - corners[1], corners[2] - corners[1])
            nl = np.linalg.norm(n)
            if nl == 0:
                continue
            n /= nl
            light = 0.45 + 0.55 * abs(n @ self.light)
            xmin, xmax = int(max(0, math.floor(sx.min()))), int(min(self.size[0] - 1, math.ceil(sx.max())))
            ymin, ymax = int(max(0, math.floor(sy.min()))), int(min(self.size[1] - 1, math.ceil(sy.max())))
            if xmin > xmax or ymin > ymax:
                continue
            gx, gy = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
            dx, dy = gx - sx[1], gy - sy[1]
            a = (dx * e_v[1] - dy * e_v[0]) / det
            b = (e_u[0] * dy - e_u[1] * dx) / det
            inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
            if not inside.any():
                continue
            (u0, v0), (u1, v1), (_, v2) = uvs[0], uvs[1], uvs[2]
            tu = np.clip(np.floor(u1 + a * (u0 - u1)), 0, tw - 1).astype(int)
            tv = np.clip(np.floor(v1 + b * (v2 - v1)), 0, th - 1).astype(int)
            texel = tex[tv, tu]
            d = sd[1] + a * (sd[0] - sd[1]) + b * (sd[2] - sd[1])
            ys, xs = gy.astype(int), gx.astype(int)
            ok = inside & (texel[..., 3] > 25) & (d < self.depth[ys, xs] - 1e-4)
            lit = np.where(glow[tv, tu][..., None], 1.0, light)
            col = texel[..., :3] * lit
            self.rgb[ys[ok], xs[ok]] = col[ok]
            self.depth[ys[ok], xs[ok]] = d[ok]
            self.alpha[ys[ok], xs[ok]] = True

    def image(self, background=(24, 24, 32)):
        bg = np.zeros_like(self.rgb)
        h = self.size[1]
        for row in range(h):
            t = row / h
            bg[row] = [background[0] * (1 - t) + 8 * t, background[1] * (1 - t) + 10 * t, background[2] * (1 - t) + 18 * t]
        out = np.where(self.alpha[..., None], self.rgb, bg)
        return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def render(scene, path, yaw=-35, pitch=18, size=(560, 640), px=8, centre=(0, 22, 0), label=None):
    c = Canvas(size, px, yaw, pitch, centre)
    for quads, tex, glow in scene:
        c.draw(quads, tex, glow)
    im = c.image()
    if label:
        ImageDraw.Draw(im).text((10, 8), label, fill=(210, 200, 190))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    return im


def sheet(images, path, cols):
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    out = Image.new('RGB', (w * cols, h * rows), (0, 0, 0))
    for i, im in enumerate(images):
        out.paste(im, ((i % cols) * w, (i // cols) * h))
    out.save(path)


def gif(frames, path, seconds):
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=round(seconds * 1000 / len(frames)), loop=0)


def main():
    rider_geo, rider_anims, rider_tex, rider_glow = load('horseman')
    horse_geo, horse_anims, horse_tex, horse_glow = load('gallows_steed')
    seat = trans(-SEAT[0], SEAT[1], SEAT[2])

    def rider(layers, t, base=np.eye(4), headless=False, hide=()):
        # head_fire runs on its own controller in game, so add it to every pose.
        hidden = (('held_head',) if headless else ()) + hide
        posed = pose(rider_anims, layers + [('head_fire', 0)], t)
        return faces(rider_geo, posed, base, hidden), rider_tex, rider_glow

    def horse(layers, t):
        return faces(horse_geo, pose(horse_anims, layers, t)), horse_tex, horse_glow

    shots = []

    def shot(name, scene, label, **kw):
        shots.append(render(scene, OUT + name + '.png', label=label, **kw))

    mounted = dict(size=(760, 760), px=8, centre=(0, 34, -6))
    for yaw, pitch, side in ((-15, 10, 'front'), (90, 6, 'side'), (40, 16, 'three_quarter'), (160, 14, 'back'),
                             (0, 89, 'top')):
        shot('mounted_' + side, [horse([('idle', 0)], 0), rider([('ride', 0)], 0, seat)],
             'mounted, ' + side.replace('_', ' '), yaw=yaw, pitch=pitch, **mounted)
    shot('charge', [horse([('gallop', 0)], 0.2), rider([('charge', 0)], 0.2, seat)], 'charge', yaw=-60, **mounted)
    for t in (0.25, 0.42):
        shot('swing_%g' % t, [horse([('idle', 0)], 0), rider([('ride', 0), ('swing', 0)], t, seat)],
             'swing at %gs' % t, yaw=-40, **mounted)
    for t in (0.35, 0.55):
        shot('throw_%g' % t, [horse([('idle', 0)], 0), rider([('ride', 0), ('throw_head', 0)], t, seat)],
             'throw the head at %gs' % t, yaw=-40, **mounted)
    shot('headless_reach', [horse([('gallop', 0)], 0.1), rider([('ride', 0), ('headless_reach', 0)], 0.1, seat, True)],
         'headless, reaching', yaw=-50, **mounted)
    shot('steed_rear', [horse([('rear', 0)], 0.6)], 'steed rears', yaw=70, **mounted)
    for yaw, pitch, side in ((90, 4, 'side'), (-35, 14, 'front'), (145, 14, 'back')):
        shot('steed_' + side, [horse([('idle', 0)], 0)], 'steed, ' + side, yaw=yaw, pitch=pitch, **mounted)
    shot('steed_gallop', [horse([('gallop', 0)], 0.15)], 'steed, gallop', yaw=80, pitch=6, **mounted)
    shot('steed_legs', [horse([('idle', 0)], 0)], 'steed, legs and hooves', yaw=-60, pitch=8, size=(640, 480), px=14,
         centre=(0, 10, -6))
    shot('steed_head', [horse([('idle', 0)], 0)], 'steed, head and neck', yaw=-55, pitch=8, size=(560, 560), px=13,
         centre=(0, 44, -24))
    shot('steed_death', [horse([('death', 0)], 2.0)], 'steed death', yaw=-30, **mounted)

    foot = dict(size=(560, 640), px=10, centre=(0, 17, -6))
    for yaw, side in ((-30, 'front'), (60, 'side'), (160, 'back')):
        shot('foot_' + side, [rider([('idle', 0)], 0)], 'on foot, ' + side, yaw=yaw, **foot)
    shot('walk', [rider([('walk', 0)], 0.35)], 'walk', yaw=-60, **foot)
    for t in (0.35, 0.55):
        shot('swing_foot_%g' % t, [rider([('walk', 0), ('swing_foot', 0)], t)], 'swing on foot at %gs' % t, yaw=-45,
             **foot)
    for t in (0.35, 0.55):
        shot('sweep_%g' % t, [rider([('idle', 0), ('sweep', 0)], t)], 'sweep at %gs' % t, yaw=-20, pitch=40,
             size=(640, 640), px=8, centre=(0, 14, 0))
    shot('sweep_mounted', [horse([('idle', 0)], 0), rider([('ride', 0), ('sweep_mounted', 0)], 0.45, seat)],
         'mounted sweep at 0.45s', yaw=-20, pitch=40, **mounted)
    shot('death', [rider([('death', 0)], 2.4)], 'death', yaw=-70, size=(700, 520), px=9, centre=(0, 4, -18))
    head = dict(yaw=-20, pitch=8, size=(420, 480), px=16, centre=(-11, 8, -8))
    shot('head_close', [rider([('idle', 0)], 0)], 'the head', **head)
    shot('head_side', [rider([('idle', 0)], 0)], 'the head, side', yaw=-80, pitch=8, size=(420, 480), px=16,
         centre=(-11, 8, -10))
    shot('armour_close', [rider([('idle', 0)], 0)], 'armour, front', yaw=-25, pitch=6, size=(560, 640), px=14,
         centre=(0, 18, -2))
    shot('back_close', [rider([('idle', 0)], 0)], 'hood and cloak', yaw=170, pitch=6, size=(560, 640), px=14,
         centre=(0, 18, 4))
    shot('belt_close', [rider([('idle', 0)], 0, hide=('right_arm', 'left_arm'))], 'rope, shackles and apron (arms hidden)',
         yaw=-20, pitch=4, size=(640, 480), px=18, centre=(0, 9, -4))
    shot('sword_close', [rider([('idle', 0)], 0)], 'the sword', yaw=-90, pitch=10, size=(640, 360), px=11,
         centre=(8, 4, -14))

    sheet([s for s in shots], OUT + 'contact_sheet.png', 4)
    # Animated GIFs.
    gif([render([rider([('idle', 0)], i * 0.05)], OUT + '_frame.png', **head)
         for i in range(24)], OUT + 'head_fire.gif', 1.2)
    gif([render([horse([('gallop', 0)], i * 0.05), rider([('charge', 0)], i * 0.05, seat)], OUT + '_frame.png',
                yaw=-70, pitch=12, size=(560, 560), px=6, centre=(0, 34, -8)) for i in range(20)],
        OUT + 'charge.gif', 1.0)
    gif([render([horse([('gallop', 0)], i * 0.025)], OUT + '_frame.png', yaw=80, pitch=6, size=(560, 480), px=6,
                centre=(0, 28, -2)) for i in range(20)], OUT + 'steed_gallop.gif', 1.0)
    gif([render([horse([('idle', 0)], i * 0.15)], OUT + '_frame.png', yaw=150, pitch=8, size=(480, 480), px=7,
                centre=(0, 28, 10)) for i in range(20)], OUT + 'steed_tail.gif', 3.0)
    os.remove(OUT + '_frame.png')
    print('wrote', len(shots), 'renders and 2 animations to', OUT)


if __name__ == '__main__':
    sys.exit(main())
