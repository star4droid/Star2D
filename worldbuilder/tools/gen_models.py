# World Builder asset library. Run:  blender -b -P gen_models.py -- out/models.bin
import sys, os, math, random
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np, bmesh
from mathutils import Vector
from wb_lib import *

OUT = sys.argv[sys.argv.index('--') + 1] if '--' in sys.argv else 'models.bin'
MODELS = []
def model(*a, **k):
    m = Model(*a, **k); MODELS.append(m); return m

# palette
LEAF_D, LEAF_L = rgb('#3c8a36'), rgb('#8fd455')
BARK = rgb('#7b5a3e'); ROCK = rgb('#9a9fa6'); MOSS = rgb('#6aa843'); STONE = rgb('#aaa79f')
WOOD = rgb('#a9794c'); TIMBER = rgb('#6d4b30'); PLASTER = rgb('#efe1c4'); TILE = rgb('#c2553c'); THATCH = rgb('#d9b865')
GRASS_D, GRASS_L = rgb('#4f9a35'), rgb('#a9dc5c')

# ================================================================== TREES
def trunk_path(rnd, h, lean=0.12, n=6):
    pts = []; x = y = 0.0
    for i in range(n):
        t = i / (n - 1); pts.append((x, y, h * t)); x += rnd.uniform(-lean, lean) * h / n; y += rnd.uniform(-lean, lean) * h / n
    return pts
def bark_color(base, h):
    f = shade(base, 0.10, 0.0, 0.0, 2.2)
    def fn(P, N): return f(P, N) * (0.78 + 0.22 * np.clip(P[:, 2] / max(h, 0.1), 0, 1))[:, None]
    return fn
def broadleaf(id, name, seed, h=3.2, tr=0.26, R=2.3, blobs=6, cell=0, dark=LEAF_D, light=LEAF_L, bark=BARK, csize=1.25, per=20, squash=0.8, strands=0, tintname='Leaves', sub='Trees'):
    rnd = random.Random(seed)
    m = model(id, name, 'nature', [(tintname, '#%02x%02x%02x' % tuple(int(c * 255) for c in light)), ('Bark', '#%02x%02x%02x' % tuple(int(c * 255) for c in bark))], M_ALIGN * 0, sub)
    pts = trunk_path(rnd, h); rad = [tr * (1.55 - 1.0 * (i / 5.0) ** 0.7) for i in range(6)]
    wood = m.part('bark', tint=2, flags=F_NOAO)
    wood.add_bm(tube(pts, rad, 9, lobes=0.28), bark_color(bark, h), sharp=60)
    top = np.array(pts[-1]); cen = []
    cen.append((top + np.array([0, 0, R * 0.42 * squash]), R * 0.78))
    for i in range(blobs):
        a = i / blobs * 2 * math.pi + rnd.uniform(-0.35, 0.35); d = R * rnd.uniform(0.55, 0.78)
        c = top + np.array([math.cos(a) * d, math.sin(a) * d, R * rnd.uniform(-0.12, 0.34) * squash]); cen.append((c, R * rnd.uniform(0.5, 0.68)))
    cc = np.mean([c for c, r in cen], axis=0)
    for c, r in cen[1:]:
        s = np.array(pts[rnd.choice([3, 4])]); mid = (s + c) / 2 + np.array([0, 0, 0.15 * R])
        wood.add_bm(tube([tuple(s), tuple(mid), tuple(c * 0.92 + s * 0.08)], [tr * 0.5, tr * 0.3, tr * 0.1], 6), bark_color(bark * 0.92, h), sharp=60)
    core = m.part('flat', tint=1, flags=F_NOAO); leaves = m.part('leaf', tint=1, flags=F_ALPHA | F_DOUBLE | F_WIND | F_NOSHADOW)
    for c, r in cen:
        b = ico(r * 0.66, 2, loc=tuple(c), scale=(1, 1, squash))
        displace(b, lambda p: p + (p - Vector(c)).normalized() * fnoise(p, 1.3, 2, seed) * r * 0.16)
        core.add_bm(b, lambda P, N, c=c, r=r: np.clip(dark[None, :] * (0.62 + 0.3 * np.clip((P[:, 2:3] - cc[2]) / R * 0.5 + 0.5, 0, 1)), 0, 1), sharp=80, a=0.25)
        clump(leaves, rnd, c, (r, r, r * squash), per, csize * r / 1.3, cell if rnd.random() < 0.6 or cell >= 2 else cell + 1, dark, light, cc, R, wind=1.0)
    if strands:
        for i in range(strands):
            a = i / strands * 2 * math.pi + rnd.uniform(-0.2, 0.2); d = R * rnd.uniform(0.7, 1.0)
            p = cc + np.array([math.cos(a) * d, math.sin(a) * d, -R * 0.15]); L = R * rnd.uniform(1.0, 1.5)
            right = np.array([-math.sin(a), math.cos(a), 0]); nv = np.array([math.cos(a) * 0.7, math.sin(a) * 0.7, 0.5])
            card(leaves, p + np.array([0, 0, -L]), right, [0, 0, 1], L * 0.8, L, 14, lerp(dark, light, 0.4), lerp(dark, light, 0.75), nrm=nv, wind=(1.0, 0.5), origin_bottom=True)
    return m
def pine(id, name, seed, h=6.5, tr=0.2, R=1.9, tiers=12, dark=rgb('#1f5a34'), light=rgb('#58a652'), bark=rgb('#6b4c38'), snow=False):
    """Conifer: whorls of boughs around the trunk, long and drooping at the bottom, short and rising at the top."""
    rnd = random.Random(seed)
    m = model(id, name, 'nature', [('Needles', '#%02x%02x%02x' % tuple(int(c * 255) for c in light)), ('Bark', '#%02x%02x%02x' % tuple(int(c * 255) for c in bark))], 0, 'Trees')
    wood = m.part('bark', tint=2, flags=F_NOAO)
    wood.add_bm(tube([(0, 0, 0), (0.03, 0, h * 0.4), (0, 0.03, h * 0.75), (0, 0, h * 0.97)], [tr * 1.4, tr, tr * 0.55, 0.03], 8, lobes=0.2), bark_color(bark, h), sharp=60)
    core = m.part('flat', tint=1, flags=F_NOAO); leaves = m.part('leaf', tint=1, flags=F_ALPHA | F_DOUBLE | F_WIND | F_NOSHADOW)
    z0 = h * 0.15
    core.add_bm(cyl(R * 0.2, 0.02, h * 0.97 - z0 - 0.3, 10, loc=(0, 0, z0 + 0.3)), lambda P, N: np.clip(lerp(dark, light, 0.12)[None, :] * (0.7 + 0.3 * np.clip((P[:, 2:3] - z0) / (h - z0), 0, 1)), 0, 1), sharp=70, a=0.15)
    white = np.array([0.95, 0.97, 1.0])
    def bough(z, a, d, L, k, roll):
        out = np.array([math.cos(a), math.sin(a), 0.0]); side0 = np.array([-math.sin(a), math.cos(a), 0.0])
        dirv = out * math.cos(d) - np.array([0, 0, math.sin(d)])
        up0 = np.cross(side0, dirv); up0 /= np.linalg.norm(up0)
        side = side0 * math.cos(roll) + up0 * math.sin(roll); hang = np.cross(dirv, side); hang /= np.linalg.norm(hang)
        base = np.array([0, 0, z]) + out * 0.04
        c0 = lerp(dark, light, k * 0.6); c1 = lerp(dark, light, min(1.0, k + 0.5))
        if snow: c0 = lerp(c0, white, 0.15); c1 = lerp(c1, white, 0.75)
        nv = out * 0.5 + np.array([0, 0, 0.85]); nv /= np.linalg.norm(nv)
        card(leaves, base, side, dirv, L * 0.8, L, 3, c0, c1, nrm=nv, wind=(0.05, 0.8), origin_bottom=True)
        nv2 = out * 0.8 + side0 * (0.35 if roll > 0 else -0.35) + np.array([0, 0, 0.5]); nv2 /= np.linalg.norm(nv2)
        card(leaves, base - hang * L * 0.06, hang, dirv, L * 0.62, L * 0.96, 3, c0 * 0.9, c1 * 0.96, nrm=nv2, wind=(0.05, 0.8), origin_bottom=True)
    for t in range(tiers):
        f = t / (tiers - 1); z = z0 + (h * 0.94 - z0) * f ** 0.9
        r = R * (1 - f) ** 0.85 + 0.3
        n = max(5, int(round(9 - 4 * f)))
        droop = math.radians(34 - 70 * f)                         # below the horizon at the bottom, rising near the top
        for i in range(n):
            a = (i + 0.5 * (t % 2)) / n * 2 * math.pi + rnd.uniform(-0.25, 0.25)
            k = np.clip(0.22 + 0.45 * f + rnd.uniform(-0.12, 0.12), 0, 1)
            bough(z + rnd.uniform(-0.1, 0.1), a, droop + rnd.uniform(-0.14, 0.14), r * rnd.uniform(0.86, 1.14), k, rnd.uniform(-0.5, 0.5))
            if i % 2 == 0:          # shorter inner boughs fill the middle
                bough(z + (h / tiers) * 0.45 + rnd.uniform(-0.08, 0.08), a + math.pi / n, droop - 0.35, r * rnd.uniform(0.5, 0.66), k * 0.7, rnd.uniform(-0.6, 0.6))
    # leader shoot
    for a in (0.0, math.pi / 2):
        side = np.array([math.cos(a), math.sin(a), 0.0]); c1 = lerp(light, white, 0.7) if snow else light
        card(leaves, (0, 0, h * 0.86), side, (0, 0, 1), 0.42, h * 0.17, 3, lerp(dark, light, 0.5), c1, nrm=(side[1], -side[0], 0.5), wind=(0.2, 1.0), origin_bottom=True)
    return m
def palm(id, name, seed, h=5.5):
    rnd = random.Random(seed); bark = rgb('#a08258'); dark, light = rgb('#3d8f3a'), rgb('#9ad65a')
    m = model(id, name, 'nature', [('Leaves', '#9ad65a'), ('Bark', '#a08258')], 0, 'Trees')
    pts = [(0, 0, 0), (0.25, 0, h * 0.3), (0.7, 0.1, h * 0.65), (1.2, 0.15, h)]
    wood = m.part('bark', tint=2, flags=F_NOAO)
    def rings(P, N): return np.clip(bark[None, :] * (0.85 + 0.15 * np.sin(P[:, 2:3] * 22)), 0, 1)
    wood.add_bm(tube(pts, [0.3, 0.2, 0.17, 0.15], 9, lobes=0.1), rings, sharp=60)
    leaves = m.part('leaf', tint=1, flags=F_ALPHA | F_DOUBLE | F_WIND | F_NOSHADOW); top = np.array(pts[-1])
    for i in range(11):
        a = i / 11 * 2 * math.pi + rnd.uniform(-0.2, 0.2); out = np.array([math.cos(a), math.sin(a), 0]); right = np.array([-math.sin(a), math.cos(a), 0])
        L = rnd.uniform(2.3, 2.9); segs = 4; p = top.copy(); ang = rnd.uniform(0.5, 0.9)
        for s in range(segs):
            d = out * math.cos(ang) + np.array([0, 0, math.sin(ang)]); q = p + d * L / segs
            nv = np.cross(right, d); nv = nv if nv[2] > 0 else -nv
            uv = cell_uv(13); v0 = uv[0][1] + (uv[3][1] - uv[0][1]) * s / segs; v1 = uv[0][1] + (uv[3][1] - uv[0][1]) * (s + 1) / segs
            w = 1.15; P = np.array([p - right * w / 2, p + right * w / 2, q + right * w / 2, q - right * w / 2])
            c0 = lerp(dark, light, 0.3 + 0.5 * s / segs); c1 = lerp(dark, light, 0.3 + 0.5 * (s + 1) / segs)
            C = np.array([[*c0, s / segs], [*c0, s / segs], [*c1, (s + 1) / segs], [*c1, (s + 1) / segs]])
            leaves.add(P, np.tile(nv, (4, 1)), C, np.array([[uv[0][0], v0], [uv[1][0], v0], [uv[1][0], v1], [uv[0][0], v1]]), [0, 1, 2, 0, 2, 3])
            p = q; ang -= 0.5
    core = m.part('flat', flags=F_NOAO); core.add_bm(ico(0.26, 1, loc=tuple(top)), bark * 0.8, sharp=80)
    return m
def dead_tree(id, name, seed):
    rnd = random.Random(seed); bark = rgb('#8a7a6c'); h = 3.6
    m = model(id, name, 'nature', [('Bark', '#8a7a6c')], 0, 'Trees'); wood = m.part('bark', tint=1, flags=F_NOAO)
    pts = trunk_path(rnd, h, 0.2); wood.add_bm(tube(pts, [0.36, 0.27, 0.22, 0.17, 0.12, 0.05], 8, lobes=0.3), bark_color(bark, h), sharp=60)
    for i in range(7):
        s = np.array(pts[rnd.choice([2, 3, 4])]); a = rnd.uniform(0, 6.28); L = rnd.uniform(1.0, 2.0)
        e = s + np.array([math.cos(a) * L, math.sin(a) * L, rnd.uniform(0.5, 1.4)]); mid = (s + e) / 2 + np.array([0, 0, 0.3])
        wood.add_bm(tube([tuple(s), tuple(mid), tuple(e)], [0.11, 0.07, 0.02], 6), bark_color(bark, h), sharp=60)
    return m
def stump(id, name, seed, log=False):
    bark = BARK; m = model(id, name, 'nature', [('Bark', '#7b5a3e')], M_ALIGN, 'Trees'); wood = m.part('bark', tint=1)
    inner = rgb('#d8b98a')
    if log:
        b = tube([(-1.1, 0, 0.32), (0, 0.03, 0.33), (1.1, 0, 0.32)], [0.33, 0.31, 0.3], 10, cap=False); wood.add_bm(b, bark_color(bark, 1), sharp=60)
        cut = m.part('flat'); 
        for sx in (-1, 1):
            c = cyl(0.31, 0.31, 0.02, 10, loc=(sx * 1.1, 0, 0.32), rot=(0, math.pi / 2, 0)); cut.add_bm(c, lambda P, N: np.clip(inner[None, :] * (0.8 + 0.2 * np.cos(np.hypot(P[:, 1], P[:, 2] - 0.32)[:, None] * 60)), 0, 1), sharp=50)
    else:
        wood.add_bm(tube([(0, 0, 0), (0, 0, 0.25), (0, 0, 0.55)], [0.55, 0.4, 0.38], 10, cap=False, lobes=0.3), bark_color(bark, 1), sharp=60)
        cut = m.part('flat'); cut.add_bm(cyl(0.38, 0.38, 0.02, 10, loc=(0, 0, 0.54)), lambda P, N: np.clip(inner[None, :] * (0.8 + 0.2 * np.cos(np.hypot(P[:, 0], P[:, 1])[:, None] * 60)), 0, 1), sharp=50)
    return m

# ================================================================== PLANTS
def bush(id, name, seed, R=0.75, blobs=4, dark=LEAF_D, light=LEAF_L, flowers=None, cell=0):
    rnd = random.Random(seed)
    tints = [('Leaves', '#%02x%02x%02x' % tuple(int(c * 255) for c in light))] + ([('Flowers', flowers)] if flowers else [])
    m = model(id, name, 'nature', tints, M_SCATTER, 'Bushes')
    core = m.part('flat', tint=1, flags=F_NOAO); leaves = m.part('leaf', tint=1, flags=F_ALPHA | F_DOUBLE | F_WIND | F_NOSHADOW)
    cen = [(np.array([0, 0, R * 0.55]), R * 0.8)]
    for i in range(blobs):
        a = i / blobs * 6.283 + rnd.uniform(-0.4, 0.4); d = R * rnd.uniform(0.45, 0.7); cen.append((np.array([math.cos(a) * d, math.sin(a) * d, R * rnd.uniform(0.3, 0.5)]), R * rnd.uniform(0.5, 0.65)))
    cc = np.array([0, 0, R * 0.4])
    for c, r in cen:
        core.add_bm(ico(r * 0.72, 2, loc=tuple(c), scale=(1, 1, 0.8)), np.clip(dark * 0.68, 0, 1), sharp=80, a=0.2)
        clump(leaves, rnd, c, (r, r, r * 0.8), 13, 0.62 * r / 0.6, cell + (i % 2 if cell == 0 else 0), dark, light, cc, R, skip_bottom=0.5, wind=0.7)
    if flowers:
        fl = m.part('leaf', tint=2, flags=F_ALPHA | F_DOUBLE | F_WIND | F_NOSHADOW); fc = rgb(flowers)
        for c, r in cen:
            for i in range(7):
                z = rnd.uniform(0.15, 1.0); a = rnd.uniform(0, 6.283); rr = math.sqrt(1 - z * z); d = np.array([math.cos(a) * rr, math.sin(a) * rr, z])
                p = c + d * r * np.array([1, 1, 0.8]) * 1.02; t1 = np.cross(d, [0, 0, 1.0]); t1 = t1 / (np.linalg.norm(t1) + 1e-6); t2 = np.cross(d, t1)
                card(fl, p + d * 0.03, t1, t2, 0.26, 0.26, 8, fc * 0.95, fc, nrm=d * 0.6 + np.array([0, 0, 0.6]), wind=(0.7, 0.7))
    return m
def grass(id, name, seed, cell=4, n=3, w=0.9, h=0.6, dark=GRASS_D, light=GRASS_L, cat='Grass'):
    rnd = random.Random(seed); m = model(id, name, 'nature', [('Grass', '#%02x%02x%02x' % tuple(int(c * 255) for c in light))], M_SMALL | M_SCATTER | M_ALIGN, cat)
    p = m.part('leaf', tint=1, flags=F_ALPHA | F_DOUBLE | F_WIND | F_NOSHADOW | F_NOAO)
    for i in range(n):
        a = i / n * math.pi + rnd.uniform(-0.2, 0.2); right = np.array([math.cos(a), math.sin(a), 0]); off = np.array([rnd.uniform(-0.12, 0.12), rnd.uniform(-0.12, 0.12), 0]) * w
        lean = np.array([-right[1], right[0], 0]) * rnd.uniform(-0.2, 0.2); up = np.array([0, 0, 1.0]) + lean; up /= np.linalg.norm(up)
        card(p, off, right, up, w * rnd.uniform(0.85, 1.1), h * rnd.uniform(0.8, 1.15), cell, dark, light, nrm=[0, 0, 1], wind=(0.0, 1.0), origin_bottom=True)
    return m
def flower(id, name, seed, petal, cell=7, count=6, h=0.45, size=0.22, center=rgb('#f6c531'), upright=False, spread=0.38):
    rnd = random.Random(seed); m = model(id, name, 'nature', [('Petals', petal), ('Leaves', '#7cc24e')], M_SMALL | M_SCATTER | M_ALIGN, 'Flowers')
    pc = rgb(petal); leaf = m.part('leaf', tint=2, flags=F_ALPHA | F_DOUBLE | F_WIND | F_NOSHADOW | F_NOAO); pet = m.part('leaf', tint=1, flags=F_ALPHA | F_DOUBLE | F_WIND | F_NOSHADOW | F_NOAO)
    cen = m.part('leaf', flags=F_ALPHA | F_DOUBLE | F_WIND | F_NOSHADOW | F_NOAO); stem = m.part('flat', tint=2, flags=F_NOAO | F_WIND | F_NOSHADOW)
    for i in range(2):
        a = i * math.pi / 2 + 0.3; card(leaf, (0, 0, 0), [math.cos(a), math.sin(a), 0], [0, 0, 1], 0.55, 0.3, 5, GRASS_D, GRASS_L, nrm=[0, 0, 1], wind=(0, 0.6), origin_bottom=True)
    for i in range(count):
        a = rnd.uniform(0, 6.283); d = rnd.uniform(0.02, spread) * (i > 0); hh = h * rnd.uniform(0.7, 1.15); x, y = math.cos(a) * d, math.sin(a) * d
        lean = np.array([rnd.uniform(-0.12, 0.12), rnd.uniform(-0.12, 0.12)]); top = np.array([x + lean[0], y + lean[1], hh])
        b = tube([(x, y, 0), tuple((np.array([x, y, 0]) + top) / 2), tuple(top)], [0.012, 0.011, 0.009], 4, cap=False)
        stem.add_bm(b, lambda P, N: np.tile(rgb('#5e9f3c'), (len(P), 1)), sharp=80, a=lambda P, N, hh=hh: np.clip(P[:, 2] / hh, 0, 1) * 0.0)
        s = size * rnd.uniform(0.85, 1.15)
        if upright:
            for k in range(2):
                aa = k * math.pi / 2 + rnd.uniform(0, 1); card(pet, top - np.array([0, 0, s * 0.25]), [math.cos(aa), math.sin(aa), 0], [0, 0, 1], s, s * 1.5, cell, pc * 0.85, pc, nrm=[0, 0, 1], wind=(0.8, 1.0), origin_bottom=True)
        else:
            tilt = np.array([rnd.uniform(-0.35, 0.35), rnd.uniform(-0.35, 0.35), 1.0]); tilt /= np.linalg.norm(tilt)
            r1 = np.cross(tilt, [0, 1.0, 0]); r1 /= np.linalg.norm(r1); r2 = np.cross(tilt, r1)
            card(pet, top, r1, r2, s, s, cell, pc * 0.92, pc, nrm=tilt, wind=(0.9, 0.9))
            if center is not None: card(cen, top + tilt * 0.012, r1, r2, s * 0.3, s * 0.3, 9, center * 0.9, center, nrm=tilt, wind=(0.9, 0.9))
    return m
def mushroom(id, name, seed, cap='#d8452f'):
    rnd = random.Random(seed); m = model(id, name, 'nature', [('Cap', cap)], M_SMALL | M_SCATTER | M_ALIGN, 'Flowers'); cc = rgb(cap)
    st = m.part('flat'); cp = m.part('flat', tint=1); dots = m.part('flat')
    for i in range(3):
        x, y = (0, 0) if i == 0 else (rnd.uniform(-0.3, 0.3), rnd.uniform(-0.3, 0.3)); s = 1.0 if i == 0 else rnd.uniform(0.45, 0.7)
        st.add_bm(tube([(x, y, 0), (x + 0.01, y, 0.14 * s), (x, y, 0.28 * s)], [0.06 * s, 0.045 * s, 0.05 * s], 8, cap=False), rgb('#efe6d2'), sharp=80)
        c = ico(0.2 * s, 2, loc=(x, y, 0.27 * s), scale=(1, 1, 0.62)); displace(c, lambda p, z0=0.27 * s: (p.x, p.y, max(p.z, z0 - 0.02 * s)))
        cp.add_bm(c, shade(cc, 0.06, 0, 0.15), sharp=70)
        for k in range(5):
            a = rnd.uniform(0, 6.283); r = rnd.uniform(0.04, 0.15) * s; z = 0.27 * s + math.sqrt(max(0.0, (0.2 * s) ** 2 - r * r)) * 0.62
            dots.add_bm(ico(0.022 * s, 1, loc=(x + math.cos(a) * r, y + math.sin(a) * r, z), scale=(1, 1, 0.5)), rgb('#fff6e6'), sharp=80)
    return m
def lily(id, name, seed, flower_col=None):
    rnd = random.Random(seed); tints = [('Pads', '#5fb04a')] + ([('Flower', flower_col)] if flower_col else [])
    m = model(id, name, 'nature', tints, M_SMALL | M_SCATTER | 16, 'Water plants'); pad = m.part('leaf', tint=1, flags=F_ALPHA | F_DOUBLE | F_NOSHADOW | F_NOAO)
    for i in range(4):
        x, y = (0, 0) if i == 0 else (rnd.uniform(-0.8, 0.8), rnd.uniform(-0.8, 0.8)); s = rnd.uniform(0.45, 0.8); a = rnd.uniform(0, 6.283)
        card(pad, (x, y, 0.015 + i * 0.003), [math.cos(a), math.sin(a), 0], [-math.sin(a), math.cos(a), 0], s, s, 11, rgb('#4f9f43'), rgb('#6fc257'), nrm=[0, 0, 1], wind=(0, 0))
    if flower_col:
        fl = m.part('leaf', tint=2, flags=F_ALPHA | F_DOUBLE | F_NOSHADOW | F_NOAO); fc = rgb(flower_col)
        for k in range(3):
            aa = k * math.pi / 3; card(fl, (0.05, 0.05, 0.02), [math.cos(aa), math.sin(aa), 0], [0, 0, 1], 0.42, 0.3, 10, fc * 0.9, fc, nrm=[0, 0, 1], wind=(0, 0), origin_bottom=True)
        card(fl, (0.05, 0.05, 0.05), [1, 0, 0], [0, 1, 0], 0.4, 0.4, 10, fc * 0.95, fc, nrm=[0, 0, 1], wind=(0, 0))
    return m

# ================================================================== ROCKS
def rock_bm(seed, size=(1.5, 1.2, 1.0), sub=3, cuts=8, rough=0.22, sink=0.22, cutdepth=(0.70, 0.93)):
    rnd = random.Random(seed); bm = ico(1.0, sub)
    for v in bm.verts:
        d = v.co.normalized(); v.co = d * (1 + fnoise(d, 1.1, 3, seed) * rough + fnoise(d, 3.1, 2, seed + 3) * rough * 0.3)
    for k in range(cuts):
        n = Vector((rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-0.25, 1))).normalized(); d = rnd.uniform(*cutdepth)
        for v in bm.verts:
            e = v.co.dot(n) - d
            if e > 0: v.co -= n * e * 0.94
    for v in bm.verts:
        v.co = Vector((v.co.x * size[0], v.co.y * size[1], v.co.z * size[2]))
    zmin = min(v.co.z for v in bm.verts); zmax = max(v.co.z for v in bm.verts); base = zmin + (zmax - zmin) * sink
    for v in bm.verts: v.co.z = max(v.co.z - base, 0.0)
    return bm
def rock_color(base=ROCK, moss=0.0, seed=0.0):
    def col(P, N):
        n = np.array([fnoise(p, 0.9, 3, seed) for p in P]); c = base[None, :] * (0.92 + n[:, None] * 0.12)
        c = c * (0.86 + 0.2 * np.clip(N[:, 2:3], 0, 1)) * (0.8 + 0.2 * np.clip(P[:, 2:3] / 0.45, 0, 1))
        return np.clip(c, 0, 1)
    def a(P, N):
        if moss <= 0: return np.zeros(len(P))
        n = np.array([fnoise(p, 1.4, 2, seed + 9) for p in P]); return np.clip((N[:, 2] - (0.82 - moss * 0.5)) * 5 + n * 1.2, 0, 1)
    return col, a
def rock(id, name, seed, size, moss=0.0, sub=3, cuts=8, rough=0.22, sink=0.22, flags=M_ALIGN | M_SCATTER, pieces=None, subcat='Rocks', base=ROCK, cutdepth=(0.70, 0.93)):
    tints = [('Rock', '#%02x%02x%02x' % tuple(int(c * 255) for c in base))] + ([('Moss', '#6aa843')] if moss > 0 else [])
    m = model(id, name, 'rocks', tints, flags, subcat); p = m.part('rock', tint=1, tint2=2 if moss > 0 else 0, flags=F_NOAO if False else 0)
    for i, (sz, loc, sd) in enumerate(pieces or [(size, (0, 0, 0), seed)]):
        bm = rock_bm(sd, sz, sub, cuts, rough, sink, cutdepth); xf(bm, loc=loc, rot=(0, 0, sd * 1.7))
        col, a = rock_color(base, moss, sd)
        if moss > 0:
            mossc = MOSS
            def col2(P, N, col=col, a=a): return col(P, N) * (1 - a(P, N)[:, None]) + mossc[None, :] * (0.9 + 0.2 * np.clip(N[:, 2:3], 0, 1)) * a(P, N)[:, None]
            p.add_bm(bm, col2, sharp=34, a=a)
        else: p.add_bm(bm, col, sharp=34)
    return m

# ================================================================== STRUCTURES (smart axis scaling)
def Z(lo_y, hi_y): return (-hi_y, -lo_y)        # Blender y range -> engine z range
def wood_color(c=WOOD, v=0.09): return shade(c, v, 0.12, 0.08, 2.5)
def window(m, x, y_side, zc, w=0.9, h=1.05, modes='RRH', rep=None, facing=0, frame_c=TIMBER, shutters=True, box=True):
    """Window module on a wall. facing: 0 front (-y), 1 back (+y), 2 left (-x), 3 right (+x). Returns nothing; adds parts."""
    fr = m.part('planks', modes=modes, rep=rep); gl = m.part('glass', modes=modes, rep=rep, flags=F_NOAO)
    def place(bm):
        if facing == 0: return xf(bm, loc=(x, y_side, zc))
        if facing == 1: return xf(bm, loc=(x, y_side, zc), rot=(0, 0, math.pi))
        if facing == 2: return xf(bm, loc=(x, y_side, zc), rot=(0, 0, -math.pi / 2))
        return xf(bm, loc=(x, y_side, zc), rot=(0, 0, math.pi / 2))
    t = 0.09
    pieces = [rbox((w + 2 * t, 0.16, t), (0, -0.04, h / 2), 0.02), rbox((w + 2 * t + 0.14, 0.24, t), (0, -0.08, -h / 2 - t), 0.02),
              rbox((t, 0.16, h), (-w / 2 - t / 2, -0.04, -h / 2), 0.02), rbox((t, 0.16, h), (w / 2 + t / 2, -0.04, -h / 2), 0.02),
              rbox((0.05, 0.1, h), (0, -0.04, -h / 2), 0.01), rbox((w, 0.1, 0.05), (0, -0.04, -0.025), 0.01)]
    if shutters:
        for sx in (-1, 1): pieces.append(rbox((w * 0.46, 0.05, h * 0.98), (sx * (w / 2 + t + w * 0.25), -0.03, -h / 2), 0.015))
    fr.add_bm(place(join(*pieces)), wood_color(frame_c), sharp=40)
    gl.add_bm(place(rbox((w, 0.04, h), (0, 0.0, -h / 2), 0)), rgb('#3b5f86'), sharp=40)
    if box:
        bx = m.part('planks', modes=modes, rep=rep); bx.add_bm(place(rbox((w + 0.1, 0.22, 0.16), (0, -0.2, -h / 2 - t - 0.2), 0.02)), wood_color(WOOD), sharp=40)
        fl = m.part('leaf', modes=modes, rep=rep, flags=F_ALPHA | F_DOUBLE | F_NOSHADOW | F_NOAO); rnd = random.Random(int(abs(x * 10 + zc * 7)) + facing)
        for i in range(4):
            px = (i - 1.5) * w / 4; b = bmesh.new(); b.free()
            base = Vector((px, -0.2, -h / 2 - t - 0.03))
            ang = [0, math.pi, -math.pi / 2, math.pi / 2][facing]; ca, sa = math.cos(ang), math.sin(ang)
            wp = np.array([x + base.x * ca - base.y * sa, y_side + base.x * sa + base.y * ca, zc + base.z])
            col = [rgb('#ff6fa0'), rgb('#ffd24a'), rgb('#ffffff'), rgb('#ff8a3c')][i % 4]
            card(fl, wp, [ca, sa, 0], [0, 0, 1], 0.3, 0.28, 4, GRASS_D, GRASS_L, nrm=[0, 0, 1], wind=(0, 0), origin_bottom=True)
            card(fl, wp + np.array([0, 0, 0.26]), [ca, sa, 0], [-sa * 0.6, ca * 0.6, 0.8], 0.2, 0.2, 8, col * 0.92, col, nrm=[0, 0, 1], wind=(0, 0))
def door(m, x, y_side, w=1.0, h=1.9, modes='MLH', arch=False, col=rgb('#7a4e2c')):
    fr = m.part('planks', modes=modes); t = 0.11
    fr.add_bm(join(rbox((t, 0.2, h + t), (x - w / 2 - t / 2, y_side - 0.05, 0), 0.02), rbox((t, 0.2, h + t), (x + w / 2 + t / 2, y_side - 0.05, 0), 0.02),
                   rbox((w + 2 * t + 0.1, 0.22, t * 1.2), (x, y_side - 0.06, h), 0.02)), wood_color(TIMBER), sharp=40)
    d = m.part('planks', modes=modes); d.add_bm(join(rbox((w, 0.08, h), (x, y_side - 0.02, 0), 0.015), rbox((w * 0.9, 0.03, 0.1), (x, y_side - 0.08, h * 0.25), 0.01), rbox((w * 0.9, 0.03, 0.1), (x, y_side - 0.08, h * 0.75), 0.01)), wood_color(col, 0.12), sharp=40)
    k = m.part('metal', modes=modes); k.add_bm(ico(0.045, 1, loc=(x + w * 0.34, y_side - 0.1, h * 0.48)), rgb('#d9b24a'), sharp=80)
    st = m.part('bricks', modes=modes); st.add_bm(rbox((w + 0.5, 0.5, 0.14), (x, y_side - 0.3, 0), 0.04), shade(STONE, 0.08), sharp=40)
def house(id, name, W=6.0, D=4.6, H=2.9, RH=2.3, wall='plaster', roof='tiles', timber=True, wall_c=PLASTER, roof_c=TILE, floors=1, chimney=True, big_door=False, sub='Houses'):
    m = model(id, name, 'buildings', [('Walls', '#%02x%02x%02x' % tuple(int(c * 255) for c in wall_c)), ('Roof', '#%02x%02x%02x' % tuple(int(c * 255) for c in roof_c))], M_SMART, sub)
    hw, hd = W / 2, D / 2; ov = 0.42
    m.zone = {'x': (-hw + 0.25, hw - 0.25), 'y': (0.75, H - 0.35), 'z': (-hd + 0.25, hd - 0.25)}
    f = m.part('bricks', modes='SLS'); f.add_bm(rbox((W + 0.3, D + 0.3, 0.5), (0, 0, 0), 0.06), shade(STONE, 0.1, 0.15), sharp=40)
    w = m.part(wall, tint=1, modes='SSS'); w.add_bm(rbox((W, D, H - 0.4), (0, 0, 0.4), 0.03), shade(wall_c, 0.05, 0.0, 0.0), sharp=40)
    if timber:
        for sx, mx in ((-1, 'L'), (1, 'H')):
            for sy, mz in ((-1, 'H'), (1, 'L')):
                p = m.part('planks', modes=mx + 'S' + mz); p.add_bm(rbox((0.3, 0.3, H - 0.3), (sx * hw, sy * hd, 0.35), 0.03), wood_color(TIMBER), sharp=40)
        for sy, mz in ((-1, 'H'), (1, 'L')):
            p = m.part('planks', modes='SH' + mz); p.add_bm(rbox((W + 0.2, 0.26, 0.26), (0, sy * (hd + 0.01), H - 0.26), 0.03), wood_color(TIMBER), sharp=40)
            p = m.part('planks', modes='SL' + mz); p.add_bm(rbox((W + 0.1, 0.2, 0.22), (0, sy * (hd + 0.01), 0.42), 0.03), wood_color(TIMBER), sharp=40)
        for sx, mx in ((-1, 'L'), (1, 'H')):
            p = m.part('planks', modes=mx + 'HS'); p.add_bm(rbox((0.26, D + 0.2, 0.26), (sx * (hw + 0.01), 0, H - 0.26), 0.03), wood_color(TIMBER), sharp=40)
            p = m.part('planks', modes=mx + 'LS'); p.add_bm(rbox((0.2, D + 0.1, 0.22), (sx * (hw + 0.01), 0, 0.42), 0.03), wood_color(TIMBER), sharp=40)
    # windows: repeat along the wall and, on tall houses, per floor
    zc = 1.95; yr = (0.45, H - 0.25, floors)
    dw = 1.6 if big_door else 0.75
    for facing, ys in ((0, -hd - 0.02), (1, hd + 0.02)):
        mz = 'H' if facing == 0 else 'L'
        if facing == 0:
            window(m, -(hw + dw) / 2, ys, zc, modes='RR' + mz, rep={'x': (-hw + 0.4, -dw, 1), 'y': yr}, facing=facing)
            window(m, (hw + dw) / 2, ys, zc, modes='RR' + mz, rep={'x': (dw, hw - 0.4, 1), 'y': yr}, facing=facing)
        else:
            window(m, 0, ys, zc, modes='RR' + mz, rep={'x': (-hw + 0.4, hw - 0.4, 2), 'y': yr}, facing=facing)
    for facing, xs in ((2, -hw - 0.02), (3, hw + 0.02)):
        window(m, xs, 0, zc, modes=('L' if facing == 2 else 'H') + 'RR', rep={'z': (-hd + 0.4, hd - 0.4, 1), 'y': yr}, facing=facing, box=False)
    door(m, 0, -hd - 0.02, w=2.0 if big_door else 1.0, h=2.3 if big_door else 1.9)
    # roof (ridge along x), gables, barge boards: follow the wall height and keep their slope when the house gets deeper
    rmat = roof; rc = shade(roof_c, 0.07, 0, 0.1, 1.2)
    th = 0.16; e = hd + ov; prof = [(-e, H - ov * RH / hd - 0.02), (0, H + RH), (e, H - ov * RH / hd - 0.02), (e, H - ov * RH / hd - 0.02 + th * 1.2), (0, H + RH + th * 1.25), (-e, H - ov * RH / hd - 0.02 + th * 1.2)]
    r = m.part(rmat, tint=2, modes='SHS', flags=F_SLOPE, slope=H, sref=hd); r.add_bm(prism(prof, W + 2 * ov, bevel=0.03), rc, sharp=30)
    cap = m.part('planks', modes='SHS', flags=F_SLOPE, slope=H, sref=hd); cap.add_bm(rbox((W + 2 * ov + 0.06, 0.2, 0.16), (0, 0, H + RH + th), 0.04), wood_color(TIMBER), sharp=40)
    for sx, mx in ((-1, 'L'), (1, 'H')):
        g = m.part(wall, tint=1, modes=mx + 'HS', flags=F_SLOPE, slope=H, sref=hd)
        g.add_bm(prism([(-hd, H - 0.02), (hd, H - 0.02), (0, H + RH - 0.05)], 0.24, loc=(sx * (hw - 0.12), 0, 0)), shade(wall_c, 0.05), sharp=40)
        b = m.part('planks', modes=mx + 'HS', flags=F_SLOPE, slope=H, sref=hd)
        bp = [(-e, H - ov * RH / hd - 0.1), (0, H + RH - 0.08), (e, H - ov * RH / hd - 0.1), (e, H - ov * RH / hd + 0.12), (0, H + RH + 0.16), (-e, H - ov * RH / hd + 0.12)]
        b.add_bm(prism(bp, 0.1, loc=(sx * (hw + ov + 0.02), 0, 0), bevel=0.02), wood_color(TIMBER), sharp=30)
        if timber:
            t = m.part('planks', modes=mx + 'HM', flags=F_SLOPE, slope=H, sref=hd); t.add_bm(rbox((0.1, 0.22, RH - 0.3), (sx * (hw + 0.01), 0, H), 0.02), wood_color(TIMBER), sharp=40)
        gw = m.part('glass', modes=mx + 'HM', flags=F_SLOPE | F_NOAO, slope=H, sref=hd); gw.add_bm(cyl(0.3, 0.3, 0.06, 12, loc=(sx * (hw + 0.02), 0, H + RH * 0.38), rot=(0, math.pi / 2, 0)), rgb('#3b5f86'), sharp=40)
        gf = m.part('planks', modes=mx + 'HM', flags=F_SLOPE, slope=H, sref=hd)
        ring = cyl(0.38, 0.38, 0.08, 12, loc=(sx * (hw + 0.0), 0, H + RH * 0.38), rot=(0, math.pi / 2, 0)); gf.add_bm(ring, wood_color(TIMBER), sharp=40)
    if chimney:
        c = m.part('bricks', modes='MHM', flags=F_SLOPE, slope=H, sref=hd); cx = hw * 0.5; cy = hd * 0.3
        c.add_bm(join(rbox((0.7, 0.7, RH + 0.5), (cx, cy, H + 0.3), 0.04), rbox((0.86, 0.86, 0.2), (cx, cy, H + RH + 0.75), 0.04)), shade(rgb('#a8876c'), 0.1), sharp=40)
    return m
def tower(id, name, R=2.2, H=7.5, roof=True):
    m = model(id, name, 'buildings', [('Walls', '#b3b0a8'), ('Roof', '#5b7089')], M_SMART, 'Houses'); wc = rgb('#b3b0a8')
    m.zone = {'y': (0.7, H - 0.25)}
    b = m.part('bricks', tint=1, modes='SSS'); b.add_bm(cyl(R * 1.06, R, H, 20, bevel=0.05), shade(wc, 0.08, 0.15), sharp=40)
    base = m.part('bricks', tint=1, modes='SLS'); base.add_bm(cyl(R * 1.22, R * 1.12, 0.6, 20, bevel=0.06), shade(wc * 0.9, 0.08, 0.15), sharp=40)
    top = m.part('bricks', tint=1, modes='SHS'); pcs = [cyl(R * 1.18, R * 1.18, 0.45, 20, loc=(0, 0, H - 0.1), bevel=0.05)]
    for i in range(10):
        a = i / 10 * 2 * math.pi; pcs.append(rbox((0.62, 0.42, 0.7), (math.cos(a) * R * 1.02, math.sin(a) * R * 1.02, H + 0.3), 0.05, rot=(0, 0, a + math.pi / 2)))
    top.add_bm(join(*pcs), shade(wc, 0.08), sharp=40)
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4; x, y = math.cos(a) * (R + 0.02), math.sin(a) * (R + 0.02)
        s = m.part('glass', modes='SRS', rep={'y': (1.6, H - 0.6, 2)}, flags=F_NOAO); s.add_bm(rbox((0.3, 0.2, 1.0), (x, y, H * 0.5 - 0.5), 0.03, rot=(0, 0, a + math.pi / 2)), rgb('#2e3d52'), sharp=40)
    d = m.part('planks', modes='MLS'); d.add_bm(rbox((1.1, 0.25, 2.0), (0, -R * 1.04, 0), 0.06), wood_color(rgb('#6d4526')), sharp=40)
    if roof:
        r = m.part('tiles', tint=2, modes='SHS'); r.add_bm(cyl(R * 1.35, 0.05, R * 1.9, 20, loc=(0, 0, H + 0.62)), shade(rgb('#5b7089'), 0.08, 0, 0.1), sharp=50)
    return m
def fence(id, name, L=4.0, H=1.0, kind='rail'):
    m = model(id, name, 'fences', [('Wood', '#a9794c')], M_SMART | M_ALIGN * 0, 'Fences'); hl = L / 2; wc = wood_color(WOOD)
    for sx, mx in ((-1, 'L'), (1, 'H')):
        p = m.part('planks', tint=1, modes=mx + 'SM'); p.add_bm(join(rbox((0.16, 0.16, H + 0.12), (sx * hl, 0, 0), 0.03, rot=(0, 0, 0.1 * sx)), rbox((0.2, 0.2, 0.06), (sx * hl, 0, H + 0.1), 0.02)), wc, sharp=40)
    if kind == 'rail':
        mid = m.part('planks', tint=1, modes='RSM', rep={'x': (-hl, hl, 1)}); mid.add_bm(rbox((0.14, 0.14, H + 0.05), (0, 0, 0), 0.03, rot=(0.04, 0, 0.3)), wc, sharp=40)
        for z, tilt in ((H * 0.42, 0.012), (H * 0.82, -0.01)):
            r = m.part('planks', tint=1, modes='SMM'); r.add_bm(rbox((L + 0.2, 0.06, 0.13), (0, -0.07, z), 0.02, rot=(0, tilt, 0)), wc, sharp=40)
    else:
        for z in (H * 0.3, H * 0.75):
            r = m.part('planks', tint=1, modes='SMM'); r.add_bm(rbox((L, 0.05, 0.1), (0, 0.04, z), 0.02), wc, sharp=40)
        pk = m.part('planks', tint=1, modes='RSM', rep={'x': (-hl + 0.1, hl - 0.1, 14)})
        pk.add_bm(join(rbox((0.13, 0.035, H * 0.92), (0, -0.01, 0.05), 0.012), xf(cyl(0.092, 0.0, 0.12, 4, loc=(0, -0.01, H * 0.92 + 0.05)), scale=(1, 0.27, 1))), wood_color(rgb('#f1ece0') if kind == 'picket' else WOOD), sharp=40)
        if kind == 'picket': m.tints = [('Paint', '#f1ece0')]
    return m
def stone_wall(id, name, L=4.0, H=2.2, T=0.7, merlons=True, low=False):
    m = model(id, name, 'fences', [('Stone', '#a9a8a2')], M_SMART, 'Walls'); hl = L / 2; sc = shade(STONE, 0.1, 0.18)
    m.zone = {'y': (0.1, H - 0.08)}
    b = m.part('bricks', tint=1, modes='SSS'); b.add_bm(rbox((L, T, H), (0, 0, 0), 0.05), sc, sharp=40)
    if low:
        cap = m.part('rock', tint=1, modes='RHS', rep={'x': (-hl, hl, 5)}); cb = rock_bm(4, (L / 10 * 1.05, T * 0.62, 0.22), 2, 5, 0.15, 0.3); cap.add_bm(xf(cb, loc=(0, 0, H - 0.04)), sc, sharp=34)
    else:
        cap = m.part('bricks', tint=1, modes='SHS'); cap.add_bm(rbox((L + 0.06, T + 0.18, 0.2), (0, 0, H), 0.05), sc, sharp=40)
        if merlons:
            me = m.part('bricks', tint=1, modes='RHS', rep={'x': (-hl, hl, 4)}); me.add_bm(rbox((0.52, T + 0.1, 0.55), (0, 0, H + 0.2), 0.05), sc, sharp=40)
    return m
def gate(id, name):
    m = model(id, name, 'fences', [('Stone', '#a9a8a2'), ('Wood', '#7a4e2c')], M_SMART, 'Walls'); sc = shade(STONE, 0.1, 0.18); W = 4.4; H = 4.2
    m.zone = {'x': (-1.0, 1.0), 'y': (0.4, H - 1.5)}
    for sx, mx in ((-1, 'L'), (1, 'H')):
        p = m.part('bricks', tint=1, modes=mx + 'SS'); p.add_bm(join(rbox((1.1, 1.1, H), (sx * (W / 2 - 0.55), 0, 0), 0.06), rbox((1.3, 1.3, 0.3), (sx * (W / 2 - 0.55), 0, 0), 0.06)), sc, sharp=40)
        c = m.part('bricks', tint=1, modes=mx + 'HS'); c.add_bm(join(rbox((1.3, 1.3, 0.3), (sx * (W / 2 - 0.55), 0, H), 0.06), cyl(0.75, 0.0, 0.8, 4, loc=(sx * (W / 2 - 0.55), 0, H + 0.3), rot=(0, 0, math.pi / 4))), sc, sharp=40)
    arch = m.part('bricks', tint=1, modes='SHS'); pts = []
    for i in range(13):
        a = math.pi * i / 12; pts.append((-math.cos(a) * 1.25, H - 1.4 + math.sin(a) * 0.9))
    prof = [(-1.25, H - 0.2)] + [(1.25, H - 0.2)][:0] + pts[::-1][:0]
    outer = [(-1.3, H - 0.25), (1.3, H - 0.25)] ; poly = [(-1.3, H - 1.4)] + [(x, z) for x, z in pts[1:-1]] + [(1.3, H - 1.4), (1.3, H - 0.3), (-1.3, H - 0.3)]
    bm = bmesh.new(); h = 0.45
    v0 = [bm.verts.new((x, -h, z)) for x, z in poly]; v1 = [bm.verts.new((x, h, z)) for x, z in poly]; n = len(poly)
    for i in range(n): bm.faces.new([v0[i], v0[(i + 1) % n], v1[(i + 1) % n], v1[i]])
    bmesh.ops.triangle_fill(bm, edges=[e for e in bm.edges if all(abs(v.co.y + h) < 1e-5 for v in e.verts)], use_beauty=True)
    bmesh.ops.triangle_fill(bm, edges=[e for e in bm.edges if all(abs(v.co.y - h) < 1e-5 for v in e.verts)], use_beauty=True)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:]); arch.add_bm(bm, sc, sharp=30)
    d = m.part('planks', tint=2, modes='SSM')
    for sx in (-1, 1): d.add_bm(rbox((1.2, 0.12, H - 1.5), (sx * 0.62, 0.1, 0), 0.02, rot=(0, 0, sx * 0.5)), wood_color(rgb('#7a4e2c'), 0.1), sharp=40)
    return m
def bridge(id, name, L=7.0, W=2.6, stone=False):
    hl, hw = L / 2, W / 2
    if stone:
        m = model(id, name, 'buildings', [('Stone', '#a9a8a2')], M_SMART, 'Bridges'); sc = shade(STONE, 0.1, 0.1)
        b = m.part('bricks', tint=1, modes='SSS'); n = 16; poly = []
        for i in range(n + 1):
            t = i / n; x = -hl + L * t; poly.append((x, 0.55 + math.sin(t * math.pi) * 0.9))
        under = [(x, -0.15 + max(0.0, math.sin((i / n) * math.pi)) * 1.05 - (0.0 if 2 < i < n - 2 else 0.6)) for i, (x, z) in enumerate(poly)]
        bm = bmesh.new(); ring = poly + under[::-1]
        v0 = [bm.verts.new((x, -hw, z)) for x, z in ring]; v1 = [bm.verts.new((x, hw, z)) for x, z in ring]; k = len(ring)
        for i in range(k): bm.faces.new([v0[i], v0[(i + 1) % k], v1[(i + 1) % k], v1[i]])
        bmesh.ops.triangle_fill(bm, edges=[e for e in bm.edges if all(abs(v.co.y + hw) < 1e-5 for v in e.verts)], use_beauty=True)
        bmesh.ops.triangle_fill(bm, edges=[e for e in bm.edges if all(abs(v.co.y - hw) < 1e-5 for v in e.verts)], use_beauty=True)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:]); b.add_bm(bm, sc, sharp=30)
        for sy, mz in ((-1, 'H'), (1, 'L')):
            r = m.part('bricks', tint=1, modes='SS' + mz); pcs = []
            for i in range(n):
                x0, z0 = poly[i]; x1, z1 = poly[i + 1]; a = math.atan2(z1 - z0, x1 - x0)
                pcs.append(rbox((math.hypot(x1 - x0, z1 - z0) + 0.04, 0.3, 0.55), ((x0 + x1) / 2, sy * (hw - 0.15), (z0 + z1) / 2 - 0.02), 0.04, rot=(0, -a, 0)))
            r.add_bm(join(*pcs), sc, sharp=40)
        return m
    m = model(id, name, 'buildings', [('Wood', '#a9794c')], M_SMART, 'Bridges'); wc = wood_color(WOOD)
    arc = lambda x: 0.55 + (1 - (x / hl) ** 2) * 0.45
    for sy, mz in ((-1, 'H'), (1, 'L')):
        b = m.part('planks', tint=1, modes='SS' + mz); pts = [(x, sy * (hw - 0.25), arc(x) - 0.2) for x in np.linspace(-hl, hl, 9)]
        b.add_bm(tube(pts, [0.13] * 9, 6), wc, sharp=50)
        rail = m.part('planks', tint=1, modes='SS' + mz); pts = [(x, sy * (hw - 0.1), arc(x) + 0.95) for x in np.linspace(-hl, hl, 9)]
        rail.add_bm(tube(pts, [0.065] * 9, 6), wc, sharp=50)
        post = m.part('planks', tint=1, modes='RS' + mz, rep={'x': (-hl, hl, 5)}); post.add_bm(rbox((0.13, 0.13, 1.2), (0, sy * (hw - 0.1), arc(0) - 0.2), 0.03), wc, sharp=40)
    pl = m.part('planks', tint=1, modes='RSS', rep={'x': (-hl, hl, 18)})
    pl.add_bm(rbox((L / 18 * 0.9, W - 0.1, 0.09), (0, 0, arc(0) - 0.08), 0.02), wood_color(WOOD * 0.95, 0.14), sharp=40)
    m.arc = True
    return m
def dock(id, name, L=6.0, W=2.2):
    m = model(id, name, 'buildings', [('Wood', '#a9794c')], M_SMART, 'Bridges'); wc = wood_color(WOOD); hl, hw = L / 2, W / 2
    pl = m.part('planks', tint=1, modes='RHS', rep={'x': (-hl, hl, 15)}); pl.add_bm(rbox((L / 15 * 0.9, W, 0.09), (0, 0, 0.62), 0.02), wood_color(WOOD * 0.95, 0.14), sharp=40)
    for sy, mz in ((-1, 'H'), (1, 'L')):
        b = m.part('planks', tint=1, modes='SH' + mz); b.add_bm(rbox((L, 0.14, 0.16), (0, sy * (hw - 0.2), 0.46), 0.02), wc, sharp=40)
        p = m.part('planks', tint=1, modes='RS' + mz, rep={'x': (-hl + 0.2, hl - 0.2, 3)}); p.add_bm(cyl(0.12, 0.1, 1.9, 8, loc=(0, sy * (hw - 0.05), -0.9), bevel=0.02), wood_color(TIMBER), sharp=50)
    return m
def pillar(id, name, H=4.0, broken=False):
    m = model(id, name, 'buildings', [('Stone', '#c9c5ba')], M_SMART, 'Ruins'); sc = shade(rgb('#c9c5ba'), 0.08, 0.15)
    m.zone = {'y': (0.5, H * (0.55 if broken else 1.0) - (0.2 if broken else 0.55))}
    b = m.part('rock', tint=1, modes='SLS'); b.add_bm(join(rbox((1.1, 1.1, 0.25), (0, 0, 0), 0.04), cyl(0.5, 0.44, 0.22, 16, loc=(0, 0, 0.25), bevel=0.03)), sc, sharp=40)
    sh = m.part('rock', tint=1, modes='SSS'); hh = H * (0.55 if broken else 1.0) - 0.47
    bm = cyl(0.4, 0.35, hh, 16, loc=(0, 0, 0.47))
    def flute(p):
        a = math.atan2(p.y, p.x); r = math.hypot(p.x, p.y); return (p.x * (1 + 0.035 * math.cos(a * 8)), p.y * (1 + 0.035 * math.cos(a * 8)), p.z)
    if broken:
        top = 0.47 + hh
        def brk(p): return (p.x, p.y, p.z - (0.5 * max(0.0, (p.x * 0.8 + p.y * 0.4 + 0.2)) if p.z > top - 0.01 else 0))
        displace(bm, brk)
    sh.add_bm(bm, sc, sharp=40)
    if not broken:
        c = m.part('rock', tint=1, modes='SHS'); c.add_bm(join(cyl(0.36, 0.52, 0.25, 16, loc=(0, 0, H - 0.5), bevel=0.02), rbox((1.15, 1.15, 0.25), (0, 0, H - 0.25), 0.04)), sc, sharp=40)
    return m
def ruin_arch(id, name):
    m = model(id, name, 'buildings', [('Stone', '#bdb8ab')], M_SMART, 'Ruins'); c0 = rgb('#bdb8ab'); sc = shade(c0, 0.1, 0.18); W = 4.6; H = 5.2
    m.zone = {'x': (-W / 2 + 0.7, W / 2 - 0.7), 'y': (0.4, 3.6)}
    for sx, mx in ((-1, 'L'), (1, 'H')):
        p = m.part('bricks', tint=1, modes=mx + 'SS'); pcs = [rbox((1.25, 1.2, 0.35), (sx * W / 2, 0, 0), 0.05)]
        z = 0.35
        for i in range(7):
            hh = 0.58; pcs.append(rbox((1.0 - 0.02 * (i % 2), 0.95, hh - 0.03), (sx * W / 2 + 0.03 * ((i % 2) * 2 - 1), 0.02 * ((i % 3) - 1), z), 0.05, rot=(0, 0, 0.03 * ((i % 2) * 2 - 1)))); z += hh
        p.add_bm(join(*pcs), sc, sharp=40)
    a = m.part('bricks', tint=1, modes='SHS'); pcs = []; n = 11; R = W / 2
    for i in range(n):
        if i in (7, 8): continue                      # a gap: the arch is broken
        t0 = math.pi * i / n; t1 = math.pi * (i + 1) / n; tm = (t0 + t1) / 2
        pcs.append(rbox((R * (t1 - t0) * 1.02, 0.95, 0.62), (-math.cos(tm) * R, 0, H - 1.0 + math.sin(tm) * R * 0.62 - 0.31), 0.05, rot=(0, -(math.pi / 2 - tm) * 0.75, 0)))
    a.add_bm(join(*pcs), sc, sharp=40)
    return m
def ruin_wall(id, name):
    m = model(id, name, 'buildings', [('Stone', '#bdb8ab'), ('Moss', '#6aa843')], M_SMART, 'Ruins'); c0 = rgb('#bdb8ab'); rnd = random.Random(3)
    p = m.part('bricks', tint=1, tint2=2, modes='SSS'); pcs = []
    heights = [3, 5, 6, 4, 6, 3, 2]
    for cx, hh in enumerate(heights):
        for r in range(hh):
            pcs.append(rbox((0.78, 0.7, 0.48), ((cx - 3) * 0.8 + (0.4 if r % 2 else 0) - 0.2, rnd.uniform(-0.03, 0.03), r * 0.5), 0.05, rot=(0, 0, rnd.uniform(-0.04, 0.04))))
    col, _ = rock_color(c0, 0, 2)
    def a(P, N): return np.clip((N[:, 2] - 0.6) * 4 + np.array([fnoise(q, 1.2, 2, 4) for q in P]), 0, 1)
    def c2(P, N): aa = a(P, N)[:, None]; return col(P, N) * (1 - aa) + MOSS[None, :] * aa
    p.add_bm(join(*pcs), c2, sharp=40, a=a)
    return m
def simple(id, name, cat, sub, tints, build, flags=0):
    m = model(id, name, cat, tints, flags, sub); build(m); return m
def b_crate(m):
    p = m.part('planks', tint=1); wc = wood_color(WOOD, 0.12)
    p.add_bm(rbox((1, 1, 1), (0, 0, 0), 0.02), wc, sharp=40)
    f = m.part('planks', tint=1); pcs = []
    for a in (0, 1, 2):
        for s in (-1, 1):
            for t in (-1, 1):
                size = [1.06, 1.06, 1.06]; size[a] = 1.06; b = [0.12, 0.12, 0.12]; b[a] = 1.06
                loc = [0, 0, 0.5]; o = [x for x in (0, 1, 2) if x != a]; loc[o[0]] += s * 0.47; loc[o[1]] += t * 0.47
                pcs.append(rbox(tuple(b), (loc[0], loc[1], loc[2] - b[2] / 2), 0.015))
    f.add_bm(join(*pcs), wood_color(TIMBER), sharp=40)
def b_barrel(m):
    p = m.part('planks', tint=1); bm = cyl(0.42, 0.42, 1.1, 16, bevel=0.03)
    displace(bm, lambda q: (q.x * (1 + 0.16 * math.sin(max(0, min(1, q.z / 1.1)) * math.pi)), q.y * (1 + 0.16 * math.sin(max(0, min(1, q.z / 1.1)) * math.pi)), q.z))
    p.add_bm(bm, wood_color(WOOD, 0.14), sharp=50)
    h = m.part('metal'); h.add_bm(join(cyl(0.475, 0.475, 0.07, 16, loc=(0, 0, 0.2)), cyl(0.475, 0.475, 0.07, 16, loc=(0, 0, 0.83))), rgb('#5c5f66'), sharp=50)
def b_lantern(m):
    pole = m.part('planks', modes='SSS'); pole.add_bm(join(rbox((0.16, 0.16, 2.6), (0, 0, 0), 0.03), rbox((0.3, 0.3, 0.2), (0, 0, 0), 0.03)), wood_color(TIMBER), sharp=40)
    arm = m.part('planks', modes='SHS'); arm.add_bm(join(rbox((0.75, 0.1, 0.1), (0.3, 0, 2.45), 0.02), rbox((0.08, 0.08, 0.5), (0.2, 0, 2.05), 0.02, rot=(0, 0.75, 0))), wood_color(TIMBER), sharp=40)
    cage = m.part('metal', modes='SHS'); cage.add_bm(join(cyl(0.2, 0.05, 0.14, 4, loc=(0.6, 0, 2.2), rot=(0, 0, math.pi / 4)), rbox((0.3, 0.3, 0.04), (0.6, 0, 1.82), 0.01), cyl(0.012, 0.012, 0.12, 5, loc=(0.6, 0, 2.33))), rgb('#3c3f45'), sharp=40)
    g = m.part('glow', tint=1, modes='SHS', flags=F_EMISSIVE | F_NOAO | F_NOSHADOW); g.add_bm(rbox((0.24, 0.24, 0.34), (0.6, 0, 1.86), 0.03), rgb('#ffd98a'), sharp=40)
def b_sign(m):
    p = m.part('planks', tint=1); wc = wood_color(WOOD)
    p.add_bm(join(rbox((0.13, 0.13, 1.9), (0, 0, 0), 0.03), rbox((1.1, 0.07, 0.34), (0.25, -0.08, 1.35), 0.03, rot=(0, 0.05, 0)), rbox((0.9, 0.07, 0.3), (-0.2, -0.08, 0.95), 0.03, rot=(0, -0.06, 0))), wc, sharp=40)
def b_bench(m):
    p = m.part('planks', tint=1); wc = wood_color(WOOD)
    p.add_bm(join(rbox((1.6, 0.42, 0.07), (0, 0, 0.44), 0.02), rbox((1.6, 0.07, 0.32), (0, 0.2, 0.62), 0.02, rot=(-0.15, 0, 0)), *[rbox((0.09, 0.4, 0.44), (sx * 0.68, 0, 0), 0.02) for sx in (-1, 1)], *[rbox((0.08, 0.08, 0.5), (sx * 0.68, 0.2, 0.44), 0.02, rot=(-0.15, 0, 0)) for sx in (-1, 1)]), wc, sharp=40)
def b_haystack(m):
    p = m.part('thatch', tint=1); bm = ico(1.0, 3, scale=(1.05, 1.05, 1.0)); displace(bm, lambda q: (q.x * (1 + 0.06 * fnoise(q, 2, 2)), q.y * (1 + 0.06 * fnoise(q, 2, 2)), max(0.0, q.z * 1.15 + 0.35)))
    p.add_bm(bm, shade(THATCH, 0.12, 0.1, 0.1), sharp=60)
def b_campfire(m):
    st = m.part('rock'); pcs = []
    for i in range(8):
        a = i / 8 * 6.283; pcs.append(xf(rock_bm(i + 2, (0.2, 0.16, 0.15), 2, 5, 0.2, 0.3), loc=(math.cos(a) * 0.55, math.sin(a) * 0.55, 0), rot=(0, 0, a)))
    st.add_bm(join(*pcs), rock_color(ROCK * 0.9)[0], sharp=34)
    lg = m.part('bark'); pcs = []
    for i in range(4):
        a = i / 4 * 6.283 + 0.4; pcs.append(tube([(math.cos(a) * 0.42, math.sin(a) * 0.42, 0.06), (math.cos(a) * 0.05, math.sin(a) * 0.05, 0.36)], [0.075, 0.06], 7))
    lg.add_bm(join(*pcs), bark_color(rgb('#4a3526'), 0.5), sharp=60)
    e = m.part('glow', flags=F_EMISSIVE | F_NOAO | F_NOSHADOW); e.add_bm(ico(0.16, 1, loc=(0, 0, 0.1), scale=(1.3, 1.3, 0.5)), rgb('#ff8a2a'), sharp=80)
def b_well(m):
    sc = shade(STONE, 0.1, 0.15); b = m.part('bricks', tint=1); outer = cyl(1.0, 0.95, 0.85, 18, bevel=0.05); b.add_bm(outer, sc, sharp=40)
    w = m.part('flat', flags=F_NOAO); w.add_bm(cyl(0.78, 0.78, 0.02, 18, loc=(0, 0, 0.86)), rgb('#2c6f9c'), sharp=40)
    wd = m.part('planks'); wd.add_bm(join(*[rbox((0.14, 0.14, 2.3), (sx * 0.88, 0, 0.3), 0.03) for sx in (-1, 1)], rbox((2.0, 0.1, 0.1), (0, 0, 1.9), 0.02), cyl(0.09, 0.09, 1.5, 8, loc=(-0.75, 0, 1.6), rot=(0, math.pi / 2, 0))), wood_color(TIMBER), sharp=40)
    r = m.part('tiles', tint=2); r.add_bm(prism([(-0.95, 2.4), (0, 3.1), (0.95, 2.4), (0.95, 2.55), (0, 3.28), (-0.95, 2.55)], 2.5, bevel=0.03), shade(TILE, 0.07, 0, 0.1), sharp=30)
    bk = m.part('planks'); bk.add_bm(cyl(0.16, 0.13, 0.3, 10, loc=(0, 0, 1.15)), wood_color(WOOD), sharp=50)
def b_cart(m):
    wc = wood_color(WOOD); p = m.part('planks', tint=1)
    p.add_bm(join(rbox((2.0, 1.1, 0.1), (0, 0, 0.6), 0.02), *[rbox((2.0, 0.07, 0.42), (0, sy * 0.55, 0.68), 0.02) for sy in (-1, 1)], rbox((0.07, 1.1, 0.42), (-1.0, 0, 0.68), 0.02), rbox((0.07, 1.1, 0.3), (1.0, 0, 0.68), 0.02),
                  *[rbox((1.5, 0.07, 0.07), (1.7, sy * 0.4, 0.62), 0.02, rot=(0, 0.18, 0)) for sy in (-1, 1)]), wc, sharp=40)
    wh = m.part('planks'); pcs = []
    for sy in (-1, 1):
        pcs.append(cyl(0.5, 0.5, 0.1, 16, loc=(-0.2, sy * 0.68 - 0.05 * sy, 0.5), rot=(math.pi / 2, 0, 0)))
    wh.add_bm(join(*pcs), wood_color(TIMBER), sharp=50)
    hay = m.part('thatch'); bm = ico(0.6, 2, loc=(-0.1, 0, 1.0), scale=(1.4, 0.8, 0.6)); hay.add_bm(bm, shade(THATCH, 0.12), sharp=60)
def b_tent(m):
    c = m.part('plaster', tint=1); col = rgb('#e8dcc0')
    c.add_bm(prism([(-1.5, 0), (0, 2.0), (1.5, 0), (1.42, 0), (0, 1.9), (-1.42, 0)], 3.0, axis='y', bevel=0.02), shade(col, 0.06, 0.12), sharp=30)
    back = m.part('plaster', tint=1); back.add_bm(prism([(-1.4, 0), (0, 1.88), (1.4, 0)], 0.04, loc=(0, 1.45, 0), axis='y'), shade(col * 0.85, 0.06), sharp=30)
    p = m.part('planks'); p.add_bm(join(rbox((0.08, 0.08, 2.15), (0, -1.5, 0), 0.02), rbox((0.08, 0.08, 2.15), (0, 1.5, 0), 0.02)), wood_color(TIMBER), sharp=40)
    s = m.part('flat', tint=2); s.add_bm(prism([(-1.52, 0.0), (0, 2.03), (1.52, 0.0), (1.52, 0.04), (0, 2.07), (-1.52, 0.04)], 0.5, loc=(0, -0.8, 0), axis='y'), rgb('#c2553c'), sharp=30)
def b_stall(m):
    m.zone = {'y': (1.1, 2.3)}
    wc = wood_color(WOOD)
    for sx, mx in ((-1, 'L'), (1, 'H')):
        for sy, mz in ((-1, 'H'), (1, 'L')):
            p = m.part('planks', modes=mx + 'S' + mz); p.add_bm(rbox((0.12, 0.12, 2.4), (sx * 1.4, sy * 0.9, 0), 0.02), wood_color(TIMBER), sharp=40)
    c = m.part('planks', modes='SLS'); c.add_bm(join(rbox((3.0, 0.9, 0.1), (0, -0.5, 0.95), 0.02), rbox((2.9, 0.06, 0.9), (0, -0.9, 0.05), 0.02)), wc, sharp=40)
    r = m.part('plaster', tint=1, modes='SHS'); pcs = []
    for i in range(6):
        col = None
        pcs.append(prism([(-1.25, 2.3), (1.25, 2.75), (1.25, 2.8), (-1.25, 2.35)], 0.52, loc=(-1.3 + i * 0.52, 0, 0)))
    def stripes(P, N): return np.where((np.floor((P[:, 0:1] + 1.56) / 0.52) % 2) < 0.5, rgb('#d94f3d')[None, :], rgb('#f4ecd8')[None, :])
    r.add_bm(join(*pcs), stripes, sharp=30)
    g = m.part('flat', modes='MLM'); pcs = []; rnd = random.Random(5)
    for i in range(9): pcs.append(ico(0.11, 1, loc=(-1.0 + (i % 5) * 0.45 + rnd.uniform(-0.05, 0.05), -0.5 + (i // 5) * 0.25, 1.12)))
    def fruit(P, N): return np.where(P[:, 0:1] > 0, rgb('#e8562e')[None, :], rgb('#f0c23a')[None, :])
    g.add_bm(join(*pcs), fruit, sharp=80)
def b_flag(m):
    p = m.part('planks'); p.add_bm(join(rbox((0.1, 0.1, 4.2), (0, 0, 0), 0.02), ico(0.1, 1, loc=(0, 0, 4.25))), wood_color(TIMBER), sharp=40)
    f = m.part('plaster', tint=1, flags=F_DOUBLE | F_WIND | F_NOAO); col = rgb('#d94f3d'); n = 8; P = []; C = []; UVs = []; I = []
    for i in range(n + 1):
        t = i / n
        for j in (0, 1): P.append((0.06 + t * 1.7, 0, 4.1 - j * 1.0 + (0.12 * t if j else 0))); C.append([*col * (0.9 + 0.1 * math.cos(t * 9)), t]); UVs.append((t, j))
    for i in range(n): a = i * 2; I += [a, a + 2, a + 3, a, a + 3, a + 1]
    f.add(np.array(P), np.tile([0, -1.0, 0], (len(P), 1)), np.array(C), np.array(UVs), I)
def b_stairs(m):
    sc = shade(STONE, 0.1, 0.1); p = m.part('bricks', tint=1, modes='SSS'); pcs = []
    for i in range(6): pcs.append(rbox((2.2, 0.5 * (6 - i), 0.26), (0, 0.25 * i, i * 0.25), 0.03))
    p.add_bm(join(*pcs), sc, sharp=40)
def b_statue(m):
    sc = shade(rgb('#c9c5ba'), 0.08, 0.1); p = m.part('rock', tint=1)
    p.add_bm(join(rbox((1.3, 1.3, 0.5), (0, 0, 0), 0.05), rbox((1.0, 1.0, 0.9), (0, 0, 0.5), 0.05), cyl(0.33, 0.24, 1.3, 10, loc=(0, 0, 1.4)), ico(0.3, 2, loc=(0, 0, 3.0)), rbox((1.0, 0.3, 0.3), (0, 0, 2.3), 0.08), cyl(0.07, 0.05, 2.0, 6, loc=(0.5, -0.1, 1.4))), sc, sharp=40)
def b_logpile(m):
    lg = m.part('bark', tint=1); pcs = []; cut = m.part('flat'); cuts = []
    for i, (y, z) in enumerate([(-0.3, 0.16), (0, 0.16), (0.3, 0.16), (-0.15, 0.42), (0.15, 0.42), (0, 0.68)]):
        pcs.append(cyl(0.15, 0.15, 1.5, 9, loc=(-0.75, y, z), rot=(0, math.pi / 2, 0), cap=False))
        for sx in (-0.75, 0.75): cuts.append(cyl(0.148, 0.148, 0.02, 9, loc=(sx, y, z), rot=(0, math.pi / 2, 0)))
    lg.add_bm(join(*pcs), bark_color(BARK, 1), sharp=60); cut.add_bm(join(*cuts), rgb('#d8b98a'), sharp=50)

# ================================================================== BUILD THE LIBRARY
broadleaf('tree_oak', 'Oak Tree', 11)
broadleaf('tree_oak_big', 'Great Oak', 12, h=4.4, tr=0.42, R=3.4, blobs=8, per=22, csize=1.35)
broadleaf('tree_round', 'Round Tree', 13, h=2.6, tr=0.2, R=1.7, blobs=5, squash=1.0, dark=rgb('#2f8a45'), light=rgb('#7fd96a'))
broadleaf('tree_sakura', 'Cherry Blossom', 14, h=3.0, tr=0.22, R=2.2, blobs=6, cell=2, dark=rgb('#e17aa8'), light=rgb('#ffd3e4'), bark=rgb('#5d4037'), tintname='Blossom', per=22)
broadleaf('tree_autumn', 'Autumn Tree', 15, h=3.2, tr=0.25, R=2.2, blobs=6, dark=rgb('#d0661a'), light=rgb('#ffc13d'))
broadleaf('tree_maple', 'Red Maple', 16, h=3.4, tr=0.25, R=2.3, blobs=7, cell=1, dark=rgb('#a82a22'), light=rgb('#f2603a'))
broadleaf('tree_birch', 'Birch', 17, h=4.6, tr=0.15, R=1.6, blobs=5, cell=1, dark=rgb('#5ba03a'), light=rgb('#b9e06a'), bark=rgb('#e9e4d8'), squash=1.25, csize=1.0)
broadleaf('tree_willow', 'Willow', 18, h=3.2, tr=0.3, R=2.5, blobs=7, dark=rgb('#4d9440'), light=rgb('#a8d860'), strands=14, squash=0.7)
broadleaf('tree_young', 'Young Tree', 19, h=1.8, tr=0.1, R=1.0, blobs=3, csize=1.1, per=14)
pine('tree_pine', 'Pine', 21)
pine('tree_pine_tall', 'Tall Pine', 22, h=9.5, tr=0.26, R=2.2, tiers=16)
pine('tree_fir_blue', 'Blue Spruce', 23, h=5.5, R=1.75, tiers=11, dark=rgb('#24545f'), light=rgb('#74b8b4'))
pine('tree_pine_snow', 'Snowy Pine', 24, snow=True)
palm('tree_palm', 'Palm', 25)
dead_tree('tree_dead', 'Dead Tree', 26)
stump('stump', 'Tree Stump', 27); stump('log', 'Fallen Log', 28, log=True)
bush('bush', 'Bush', 31); bush('bush_big', 'Large Bush', 32, R=1.1, blobs=5)
bush('bush_flower_pink', 'Flowering Bush', 33, flowers='#ff7fb0'); bush('bush_flower_white', 'White Rose Bush', 34, flowers='#ffffff', dark=rgb('#2f7d3a'), light=rgb('#6fc45a'))
bush('bush_berry', 'Berry Bush', 35, flowers='#d8344a', R=0.65, cell=1); bush('bush_autumn', 'Golden Bush', 36, dark=rgb('#c9861d'), light=rgb('#ffd75a'))
grass('grass_tuft', 'Grass', 41); grass('grass_tall', 'Tall Grass', 42, cell=5, n=3, w=0.8, h=1.0); grass('grass_patch', 'Grass Patch', 43, n=5, w=1.3, h=0.5)
grass('fern', 'Fern', 44, cell=6, n=3, w=1.1, h=0.7, dark=rgb('#2f7d3a'), light=rgb('#6fc45a'), cat='Grass'); grass('reeds', 'Reeds', 45, cell=12, n=3, w=0.9, h=1.5, dark=rgb('#5f8f3a'), light=rgb('#b4d070'), cat='Water plants')
grass('grass_dry', 'Dry Grass', 46, cell=5, n=3, w=0.9, h=0.8, dark=rgb('#b59a4a'), light=rgb('#ecd98a'))
flower('flower_daisy', 'Daisies', 51, '#ffffff', 7); flower('flower_poppy', 'Poppies', 52, '#f0442e', 8, center=rgb('#2a2a2a'))
flower('flower_blue', 'Bluebells', 53, '#5a8cff', 8, size=0.17, count=8); flower('flower_sun', 'Sunflowers', 54, '#ffc928', 7, count=3, h=1.2, size=0.45, center=rgb('#6b4226'), spread=0.3)
flower('flower_tulip', 'Tulips', 55, '#ff5f8f', 10, upright=True, size=0.2, count=6, center=None); flower('flower_lavender', 'Lavender', 56, '#a77bff', 15, upright=True, size=0.3, count=5, h=0.4, center=None)
flower('flower_yellow', 'Buttercups', 57, '#ffe14a', 8, size=0.16, count=9, h=0.3); flower('flower_pink', 'Pink Cosmos', 58, '#ff8fc4', 7, size=0.24, count=6)
mushroom('mushroom_red', 'Red Mushrooms', 61); mushroom('mushroom_brown', 'Brown Mushrooms', 62, '#b07a4a')
lily('lily_pads', 'Lily Pads', 63); lily('lily_lotus', 'Lotus', 64, '#ff9ac6')

rock('rock_boulder', 'Boulder', 71, (1.5, 1.25, 1.1)); rock('rock_round', 'Round Rock', 72, (1.0, 0.95, 0.8), cuts=5, rough=0.14)
rock('rock_flat', 'Flat Rock', 73, (1.9, 1.5, 0.5), cuts=7); rock('rock_tall', 'Standing Stone', 74, (0.8, 0.7, 2.2), cuts=9, sink=0.1)
rock('rock_mossy', 'Mossy Boulder', 75, (1.6, 1.3, 1.1), moss=0.7); rock('rock_mossy_flat', 'Mossy Slab', 76, (2.2, 1.7, 0.6), moss=0.9)
rock('rock_pile', 'Rock Pile', 77, None, pieces=[((1.1, 0.9, 0.8), (0, 0, 0), 77), ((0.7, 0.6, 0.5), (1.1, 0.3, 0), 78), ((0.5, 0.5, 0.4), (0.3, -0.9, 0), 79), ((0.35, 0.3, 0.25), (-0.9, -0.5, 0), 80)])
rock('rock_pebbles', 'Pebbles', 81, None, sub=2, cuts=4, flags=M_ALIGN | M_SCATTER | M_SMALL, pieces=[((0.2, 0.16, 0.1), (0, 0, 0), 81), ((0.14, 0.12, 0.08), (0.35, 0.1, 0), 82), ((0.17, 0.13, 0.09), (-0.2, 0.3, 0), 83), ((0.1, 0.1, 0.06), (0.1, -0.3, 0), 84), ((0.12, 0.1, 0.07), (-0.35, -0.15, 0), 85)])
rock('rock_sandstone', 'Sandstone', 86, (1.5, 1.2, 1.0), base=rgb('#cfa778')); rock('rock_dark', 'Basalt', 87, (1.3, 1.2, 1.3), base=rgb('#5d6068'), cuts=10)
rock('cliff_block', 'Cliff Block', 91, (3.0, 2.2, 3.2), cuts=12, rough=0.16, sink=0.08, flags=0, subcat='Cliffs', cutdepth=(0.62, 0.9))
rock('cliff_wall', 'Cliff Wall', 92, (5.0, 1.8, 3.6), cuts=14, rough=0.14, sink=0.06, flags=0, subcat='Cliffs', cutdepth=(0.6, 0.9))
rock('cliff_mossy', 'Mossy Cliff', 93, (3.4, 2.4, 2.8), moss=0.8, cuts=12, rough=0.16, sink=0.08, flags=0, subcat='Cliffs', cutdepth=(0.62, 0.9))
rock('cliff_spire', 'Rock Spire', 94, (1.6, 1.5, 5.5), cuts=14, rough=0.2, sink=0.05, flags=0, subcat='Cliffs', cutdepth=(0.6, 0.9))
rock('stepping_stones', 'Stepping Stones', 95, None, sub=2, cuts=5, pieces=[((0.5, 0.45, 0.14), (0, 0, 0), 95), ((0.45, 0.4, 0.13), (1.0, 0.25, 0), 96), ((0.5, 0.42, 0.14), (2.0, -0.1, 0), 97)], flags=M_ALIGN)

house('house_cottage', 'Timber Cottage')
house('house_stone', 'Stone Cottage', W=5.4, D=4.4, wall='bricks', roof='thatch', timber=False, wall_c=rgb('#b9b3a6'), roof_c=THATCH)
house('house_tall', 'Town House', W=5.0, D=4.6, H=5.4, RH=2.6, floors=2, wall_c=rgb('#f3e9d2'), roof_c=rgb('#5b7089'))
house('house_small', 'Small Hut', W=3.6, D=3.4, H=2.5, RH=1.8, wall_c=rgb('#e6d3b0'), roof_c=rgb('#8f5a3c'), chimney=False)
house('barn', 'Barn', W=7.5, D=5.5, H=3.6, RH=2.8, wall='planks', timber=True, wall_c=rgb('#b5483a'), roof_c=rgb('#6e6a66'), chimney=False, big_door=True)
tower('tower', 'Watch Tower'); tower('tower_open', 'Stone Turret', R=1.8, H=5.0, roof=False)
fence('fence_wood', 'Wooden Fence'); fence('fence_picket', 'Picket Fence', kind='picket', H=0.95); fence('fence_plank', 'Plank Fence', kind='plank', H=1.5)
stone_wall('wall_stone', 'Castle Wall'); stone_wall('wall_plain', 'Stone Wall', merlons=False, H=1.8, T=0.6); stone_wall('wall_low', 'Low Stone Fence', H=0.75, T=0.5, low=True)
gate('gate_stone', 'Stone Gate')
bridge('bridge_wood', 'Wooden Bridge'); bridge('bridge_stone', 'Stone Bridge', stone=True); dock('dock', 'Dock')
pillar('pillar', 'Pillar'); pillar('pillar_broken', 'Broken Pillar', broken=True); ruin_arch('ruin_arch', 'Ruined Arch'); ruin_wall('ruin_wall', 'Ruined Wall')
simple('stairs_stone', 'Stone Stairs', 'buildings', 'Ruins', [('Stone', '#aaa79f')], b_stairs, M_SMART)
simple('statue', 'Statue', 'buildings', 'Ruins', [('Stone', '#c9c5ba')], b_statue)
simple('well', 'Well', 'props', 'Village', [('Stone', '#aaa79f'), ('Roof', '#c2553c')], b_well)
simple('market_stall', 'Market Stall', 'props', 'Village', [('Awning', '#ffffff')], b_stall, M_SMART)
simple('tent', 'Tent', 'props', 'Village', [('Cloth', '#e8dcc0'), ('Trim', '#c2553c')], b_tent)
simple('cart', 'Hay Cart', 'props', 'Village', [('Wood', '#a9794c')], b_cart)
simple('crate', 'Crate', 'props', 'Village', [('Wood', '#a9794c')], b_crate, M_ALIGN)
simple('barrel', 'Barrel', 'props', 'Village', [('Wood', '#a9794c')], b_barrel)
simple('lantern_post', 'Lantern Post', 'props', 'Village', [('Light', '#ffd98a')], b_lantern, M_SMART)
simple('signpost', 'Signpost', 'props', 'Village', [('Wood', '#a9794c')], b_sign)
simple('bench', 'Bench', 'props', 'Village', [('Wood', '#a9794c')], b_bench)
simple('haystack', 'Haystack', 'props', 'Village', [('Hay', '#d9b865')], b_haystack)
simple('campfire', 'Campfire', 'props', 'Camp', [], b_campfire)
simple('log_pile', 'Log Pile', 'props', 'Camp', [('Bark', '#7b5a3e')], b_logpile)
simple('flag', 'Banner', 'props', 'Camp', [('Cloth', '#d94f3d')], b_flag)

for m in MODELS: bake_ao(m)
export(MODELS, OUT)
