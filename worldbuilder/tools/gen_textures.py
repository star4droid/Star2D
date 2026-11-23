# Procedural textures for World Builder (PIL + numpy). Everything tiles.
import numpy as np, math, os, sys
from PIL import Image, ImageDraw, ImageFilter
OUT = sys.argv[1] if len(sys.argv) > 1 else '../assets/wb/tex'
os.makedirs(OUT, exist_ok=True)
R = np.random.RandomState(7)

def pnoise(n, cells, seed=0):
    """Tileable value noise n x n with `cells` lattice cells per side (smooth)."""
    r = np.random.RandomState(seed)
    g = r.rand(cells, cells)
    x = np.arange(n) * cells / n
    i = np.floor(x).astype(int); f = x - i; f = f * f * (3 - 2 * f)
    i1 = (i + 1) % cells
    a = g[np.ix_(i, i)]; b = g[np.ix_(i, i1)]; c = g[np.ix_(i1, i)]; d = g[np.ix_(i1, i1)]
    fx = f[None, :]; fy = f[:, None]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy

def fbm(n, cells=4, octs=5, seed=0, gain=0.5):
    t = np.zeros((n, n)); amp = 1; tot = 0
    for o in range(octs):
        t += pnoise(n, cells * 2 ** o, seed + o * 13) * amp; tot += amp; amp *= gain
    return t / tot

def worley(n, pts, seed=0):
    """Tileable F1 and F2-F1 distance fields."""
    r = np.random.RandomState(seed); p = r.rand(pts, 2)
    y, x = np.mgrid[0:n, 0:n] / n
    d1 = np.full((n, n), 9.0); d2 = np.full((n, n), 9.0); idx = np.zeros((n, n), int)
    for k, (px, py) in enumerate(p):
        dx = np.abs(x - px); dx = np.minimum(dx, 1 - dx); dy = np.abs(y - py); dy = np.minimum(dy, 1 - dy)
        d = np.sqrt(dx * dx + dy * dy)
        m = d < d1; d2 = np.where(m, d1, np.minimum(d2, d)); idx = np.where(m, k, idx); d1 = np.where(m, d, d1)
    return d1, d2 - d1, idx

def save_gray(a, name):
    Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8), 'L').convert('RGB').save(os.path.join(OUT, name))
def save_rgb(a, name):
    Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8), 'RGB').save(os.path.join(OUT, name))
def norm(a): return (a - a.min()) / (a.max() - a.min() + 1e-9)
def tile_blur(img, rad):
    """Gaussian blur that wraps."""
    w, h = img.size; big = Image.new(img.mode, (w * 3, h * 3))
    for i in range(3):
        for j in range(3): big.paste(img, (i * w, j * h))
    return big.filter(ImageFilter.GaussianBlur(rad)).crop((w, h, 2 * w, 2 * h))
def tiled_draw(n, fn, mode='L', bg=0):
    """Draw with fn(draw, ox, oy) at 9 offsets so shapes wrap."""
    im = Image.new(mode, (n, n), bg); d = ImageDraw.Draw(im)
    for ox in (-n, 0, n):
        for oy in (-n, 0, n): fn(d, ox, oy)
    return im

N = 256
# ---------------------------------------------------------------- detail maps (multiply x2: 0.5 = neutral)
def detail_rock():
    d1, edge, idx = worley(N, 26, 3)
    cracks = np.clip(1 - edge * 22, 0, 1) ** 2
    facet = np.random.RandomState(5).rand(64)[idx % 64] * 0.16
    a = 0.5 + (fbm(N, 4, 5, 11) - 0.5) * 0.34 + facet - 0.08 - cracks * 0.22 + (fbm(N, 32, 2, 4) - 0.5) * 0.08
    save_gray(a, 'd_rock.png')
def detail_bark():
    y, x = np.mgrid[0:N, 0:N] / N
    warp = fbm(N, 3, 4, 21) * 0.5
    ridges = np.abs(np.sin((x * 14 + warp * 3 + np.sin(y * 2 * math.pi * 2) * 0.08) * math.pi))
    a = 0.5 + (ridges ** 0.6 - 0.6) * 0.42 + (fbm(N, 8, 4, 2) - 0.5) * 0.18
    save_gray(a, 'd_bark.png')
def detail_planks():
    y, x = np.mgrid[0:N, 0:N] / N
    boards = 6; bx = x * boards; bi = np.floor(bx); bf = bx - bi
    gap = np.clip(1 - np.minimum(bf, 1 - bf) * 24, 0, 1)
    r = np.random.RandomState(3).rand(boards)
    tone = r[bi.astype(int) % boards] * 0.16 - 0.08
    grain = np.sin((y * 3 + fbm(N, 3, 3, 9) * 1.4 + r[bi.astype(int) % boards] * 7) * 2 * math.pi * 3 + bf * 5) * 0.035
    fine = (pnoise(N, 64, 5)[:, :] - 0.5) * 0.05
    # board ends (staggered)
    ends = np.zeros((N, N))
    for b in range(boards):
        yy = (r[b] * 0.8 + 0.1)
        ends += ((bi == b) & (np.abs(y - yy) < 0.006)) * 1.0
    a = 0.5 + tone + grain + fine - gap * 0.3 - ends * 0.25
    save_gray(a, 'd_planks.png')
def detail_bricks():
    rows = 6; n = N
    im = Image.new('L', (n, n), 70); d = ImageDraw.Draw(im); r = np.random.RandomState(8)
    rh = n / rows
    for j in range(rows):
        x = -r.rand() * 40
        while x < n:
            w = 44 + r.rand() * 46
            tone = int(120 + r.rand() * 46)
            for ox in (-n, 0, n):
                d.rounded_rectangle([x + ox + 3, j * rh + 3, x + ox + w - 3, (j + 1) * rh - 3], radius=7, fill=tone)
            x += w
    a = np.asarray(tile_blur(im, 1.6)) / 255.0
    a = a + (fbm(N, 8, 4, 31) - 0.5) * 0.16
    a = 0.5 + (a - 0.52) * 0.95
    save_gray(a, 'd_bricks.png')
def detail_plaster():
    a = 0.5 + (fbm(N, 3, 5, 41) - 0.5) * 0.2 + (fbm(N, 24, 3, 42) - 0.5) * 0.08
    save_gray(a, 'd_plaster.png')
def detail_tiles():
    rows = 6; cols = 6; n = N
    im = Image.new('L', (n, n), 60); d = ImageDraw.Draw(im); r = np.random.RandomState(12)
    rh = n / rows; cw = n / cols
    for j in range(rows + 1):
        for i in range(-1, cols + 1):
            x = (i + (0.5 if j % 2 else 0)) * cw; y = j * rh
            tone = int(140 + r.rand() * 50)
            for oy in (-n, 0):
                for ox in (-n, 0, n):
                    d.rounded_rectangle([x + ox + 2, y + oy - rh * 0.25, x + ox + cw - 2, y + oy + rh * 0.98], radius=int(cw * 0.42), fill=tone)
                    d.arc([x + ox + 2, y + oy + rh * 0.25, x + ox + cw - 2, y + oy + rh * 1.02], 20, 160, fill=int(tone * 0.55), width=3)
    a = np.asarray(tile_blur(im, 1.1)) / 255.0
    a = 0.5 + (a - 0.62) * 0.8 + (fbm(N, 12, 3, 51) - 0.5) * 0.08
    save_gray(a, 'd_tiles.png')
def detail_thatch():
    y, x = np.mgrid[0:N, 0:N] / N
    s = np.zeros((N, N))
    for k in range(3):
        s += np.sin((x * (40 + k * 23) + fbm(N, 4, 3, 60 + k) * 3 + np.sin(y * 2 * math.pi) * 0.3) * 2 * math.pi) * (0.5 / (k + 1))
    layers = (y * 4) % 1.0
    a = 0.5 + s * 0.11 - (layers < 0.08) * 0.22 + (fbm(N, 6, 3, 66) - 0.5) * 0.14
    save_gray(a, 'd_thatch.png')

# ---------------------------------------------------------------- terrain colors (painterly, low contrast)
def blotch(seed, cells=5): return norm(fbm(N, cells, 4, seed))
def mix(a, b, t): return a * (1 - t[..., None]) + b * t[..., None]
def col(c): return np.array(c, float)[None, None, :] / 255.0
def strokes(seed, count, length, width, tone=(0.9, 1.1), angle=(60, 120)):
    r = np.random.RandomState(seed)
    def fn(d, ox, oy):
        rr = np.random.RandomState(seed)
        for _ in range(count):
            x = rr.rand() * N + ox; y = rr.rand() * N + oy; a = math.radians(rr.uniform(*angle)); l = length * rr.uniform(0.6, 1.3)
            v = int(128 * rr.uniform(*tone))
            d.line([x, y, x + math.cos(a) * l, y - math.sin(a) * l], fill=v, width=width)
    im = tiled_draw(N, fn, 'L', 128)
    return np.asarray(tile_blur(im, 0.8)) / 255.0
def t_grass():
    b = blotch(101, 4); b2 = blotch(102, 9)
    c = mix(col((96, 168, 62)), col((128, 196, 74)), b)
    c = mix(c, col((84, 150, 58)), np.clip(b2 * 1.4 - 0.75, 0, 1))
    s = strokes(103, 900, 11, 2, (0.82, 1.22))
    c = c * (0.5 + s)[..., None]
    c = mix(c, col((176, 214, 96)), np.clip(blotch(104, 14) * 2 - 1.5, 0, 1) * 0.5)
    save_rgb(c, 't_grass.png')
def t_dirt():
    b = blotch(111, 5)
    c = mix(col((150, 112, 74)), col((178, 138, 92)), b)
    d1, e, idx = worley(N, 60, 112)
    peb = np.clip(1 - d1 * 26, 0, 1) * (np.random.RandomState(1).rand(64)[idx % 64] > 0.6)
    c = mix(c, col((196, 164, 122)), peb * 0.6)
    c = c * (0.94 + (fbm(N, 20, 3, 113) - 0.5) * 0.22)[..., None]
    save_rgb(c, 't_dirt.png')
def t_sand():
    y, x = np.mgrid[0:N, 0:N] / N
    b = blotch(121, 4)
    c = mix(col((222, 198, 140)), col((240, 220, 166)), b)
    rip = np.sin((y * 9 + fbm(N, 3, 3, 122) * 1.6) * 2 * math.pi) * 0.035
    c = c * (1 + rip + (pnoise(N, 96, 123) - 0.5) * 0.06)[..., None]
    save_rgb(c, 't_sand.png')
def t_rock():
    y, x = np.mgrid[0:N, 0:N] / N
    d1, edge, idx = worley(N, 22, 131)
    facet = np.random.RandomState(9).rand(64)[idx % 64]
    c = mix(col((128, 132, 140)), col((164, 166, 170)), facet)
    c = c * (1 - np.clip(1 - edge * 20, 0, 1) * 0.32)[..., None]
    strata = np.sin((y * 5 + fbm(N, 3, 3, 132) * 0.9) * 2 * math.pi) * 0.05
    c = c * (0.95 + strata + (fbm(N, 10, 4, 133) - 0.5) * 0.2)[..., None]
    save_rgb(c, 't_rock.png')
def t_snow():
    b = blotch(141, 4)
    c = mix(col((226, 236, 248)), col((250, 252, 255)), b)
    c = c * (0.98 + (pnoise(N, 64, 142) - 0.5) * 0.05)[..., None]
    save_rgb(c, 't_snow.png')
def t_noise():
    a = np.stack([fbm(N, 4, 5, 201), fbm(N, 8, 4, 202), norm(worley(N, 40, 203)[0]), fbm(N, 2, 6, 204)], -1)
    Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'noise.png'))

# ---------------------------------------------------------------- foliage atlas: luminance + alpha, 4x4 cells of 256
C = 256
def leaf_poly(cx, cy, ang, l, w, pointed=True):
    pts = []
    for i in range(13):
        t = i / 12.0; x = t * l
        y = math.sin(t * math.pi) ** (0.8 if pointed else 0.6) * w * (1 - 0.3 * t if pointed else 1)
        pts.append((x, y))
    pts += [(x, -y) for x, y in reversed(pts[1:-1])]
    ca, sa = math.cos(ang), math.sin(ang)
    return [(cx + x * ca - y * sa, cy + x * sa + y * ca) for x, y in pts]
def cell_canvas(): return Image.new('L', (C * 2, C * 2), 0), Image.new('L', (C * 2, C * 2), 0)   # lum, alpha (2x supersample)
def finish(lum, alpha, soft=0.0):
    lum = lum.resize((C, C), Image.LANCZOS); alpha = alpha.resize((C, C), Image.LANCZOS)
    a = np.asarray(alpha).astype(float) / 255
    # bleed luminance outward so mip-mapping never pulls in black
    l = np.asarray(lum).astype(float); w = a.copy()
    lb = Image.fromarray((l * a).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6)); wb = Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6))
    bleed = np.asarray(lb).astype(float) / np.maximum(np.asarray(wb).astype(float) / 255, 0.02)
    l = np.where(a > 0.5, l, np.clip(bleed, 0, 255))
    l = np.where(np.asarray(wb) < 2, 150, l)
    return np.stack([l, l, l, a * 255], -1).astype(np.uint8)
def cluster(seed, n=46, leaf=(54, 15), spread=0.80, pointed=True, hole=0.0):
    r = np.random.RandomState(seed); lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha)
    cx = cy = C; items = []
    for i in range(n):
        a = r.rand() * 2 * math.pi; d = (r.rand() ** 0.6) * C * spread
        if d < hole * C: continue
        items.append((d, a))
    items.sort(reverse=True)     # outer leaves first (drawn under), inner on top
    for d, a in items:
        x = cx + math.cos(a) * d; y = cy + math.sin(a) * d
        ang = a + r.uniform(-0.9, 0.9); l = leaf[0] * r.uniform(0.75, 1.25) * 2 * 0.5 * 2; w = leaf[1] * r.uniform(0.8, 1.2)
        l = min(l, (C * 0.97 - d)) if d + l > C * 0.97 else l
        if l < 14: continue
        p = leaf_poly(x, y, ang, l, w, pointed)
        tone = int(np.clip(150 + r.uniform(-48, 62) - (d / C) * 10, 70, 250))
        dl.polygon(p, fill=tone); da.polygon(p, fill=255)
        # midrib highlight
        dl.line([x, y, x + math.cos(ang) * l * 0.9, y + math.sin(ang) * l * 0.9], fill=min(255, tone + 26), width=3)
    return finish(lum, alpha)
def blossom(seed):
    r = np.random.RandomState(seed); lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha)
    for i in range(78):
        a = r.rand() * 2 * math.pi; d = (r.rand() ** 0.55) * C * 0.84
        x = C + math.cos(a) * d; y = C + math.sin(a) * d; s = r.uniform(15, 27)
        tone = int(r.uniform(150, 250))
        for k in range(5):
            pa = a + k * 2 * math.pi / 5; px = x + math.cos(pa) * s * 0.55; py = y + math.sin(pa) * s * 0.55
            dl.ellipse([px - s * 0.5, py - s * 0.5, px + s * 0.5, py + s * 0.5], fill=tone); da.ellipse([px - s * 0.5, py - s * 0.5, px + s * 0.5, py + s * 0.5], fill=255)
        dl.ellipse([x - s * 0.2, y - s * 0.2, x + s * 0.2, y + s * 0.2], fill=max(60, tone - 70))
    return finish(lum, alpha)
def needles(seed):
    """A conifer bough seen from above: base at the bottom, tip at the top, side twigs sweeping forward, all covered in needles."""
    r = np.random.RandomState(seed); lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha)
    S = C * 2; cx = S / 2; y0 = S * 0.985; y1 = S * 0.03; L = y0 - y1
    def needle_run(x0, yy0, x1, yy1, nl, dens, tone0):
        dx, dy = x1 - x0, yy1 - yy0; ln = math.hypot(dx, dy); n = max(3, int(ln / dens)); base = math.atan2(dy, dx)
        dl.line([x0, yy0, x1, yy1], fill=int(tone0 * 0.7), width=5); da.line([x0, yy0, x1, yy1], fill=255, width=5)
        for i in range(n + 1):
            t = i / n; x = x0 + dx * t; y = yy0 + dy * t
            for sgn in (-1, 1):
                ang = base + sgn * r.uniform(0.45, 0.95); l = nl * (1.0 - 0.45 * t) * r.uniform(0.75, 1.2)
                tone = int(np.clip(tone0 + r.uniform(-45, 45) + 40 * t, 60, 250))
                p = leaf_poly(x, y, ang, l, 4.6, True); dl.polygon(p, fill=tone); da.polygon(p, fill=255)
        p = leaf_poly(x1, yy1, base, nl * 0.7, 4.6, True); dl.polygon(p, fill=int(min(250, tone0 + 50))); da.polygon(p, fill=255)
    twigs = []
    n = 13
    for i in range(n):
        t = 0.04 + 0.86 * i / (n - 1)                      # along the stem
        w = (S * 0.47) * (math.sin(min(1.0, t / 0.3) * math.pi / 2) ** 0.8) * (1.0 - t) ** 0.72 + 14
        for sgn in (-1, 1):
            ang = -math.pi / 2 + sgn * r.uniform(0.82, 1.02) * (1.0 - 0.25 * t)
            ln = w / abs(math.cos(ang)) if abs(math.cos(ang)) > 0.2 else w
            ln = min(ln, w * 1.45) * r.uniform(0.88, 1.05)
            sy = y0 - L * t + r.uniform(-6, 6)
            twigs.append((sy, cx, sy, cx + math.cos(ang) * ln, sy + math.sin(ang) * ln, t))
    twigs.sort(key=lambda q: -q[0])
    for _, x0, yy0, x1, yy1, t in twigs:
        # small side shoots on the longer twigs
        ln = math.hypot(x1 - x0, yy1 - yy0)
        if ln > 110:
            for k in (0.38, 0.66):
                bx = x0 + (x1 - x0) * k; by = yy0 + (yy1 - yy0) * k; base = math.atan2(yy1 - yy0, x1 - x0)
                for sg in (-1, 1):
                    a2 = base + sg * 0.62; l2 = ln * 0.3 * (1.2 - k)
                    needle_run(bx, by, bx + math.cos(a2) * l2, by + math.sin(a2) * l2, 22, 9, 120 + 30 * t)
        needle_run(x0, yy0, x1, yy1, 27, 8, 128 + 34 * t)
    needle_run(cx, y0, cx, y1 + 30, 30, 8, 150)
    return finish(lum, alpha)
def grass_tuft(seed, n=26, tall=1.0, wide=1.0):
    r = np.random.RandomState(seed); lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha)
    blades = sorted([(r.rand(), r.uniform(-1, 1)) for _ in range(n)])
    for hgt, off in blades:
        x0 = C + off * C * 0.52 * wide; lean = off * 0.5 + r.uniform(-0.25, 0.25); h = C * 2 * (0.42 + 0.56 * hgt) * tall; w0 = r.uniform(11, 19)
        L = []; Rr = []
        for i in range(11):
            t = i / 10.0; x = x0 + lean * h * 0.42 * t * t; y = 2 * C - 2 - t * h; w = w0 * (1 - t) ** 0.8
            L.append((x - w, y)); Rr.append((x + w, y))
        p = L + Rr[::-1]
        tone = int(np.clip(118 + hgt * 96 + r.uniform(-20, 20), 70, 250))
        dl.polygon(p, fill=tone); da.polygon(p, fill=255)
    return finish(lum, alpha)
def fern(seed):
    r = np.random.RandomState(seed); lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha)
    for f in range(7):
        base_a = -math.pi / 2 + (f - 3) * 0.42; l = C * 1.7 * r.uniform(0.75, 1.0)
        for i in range(16):
            t = (i + 1) / 16.0; a = base_a + (f - 3) * 0.16 * t * t
            x = C + math.cos(a) * l * t; y = 2 * C - 6 + math.sin(a) * l * t
            for sgn in (-1, 1):
                p = leaf_poly(x, y, a + sgn * 1.15, 44 * (1.1 - t), 9, True); tone = int(r.uniform(120, 235))
                dl.polygon(p, fill=tone); da.polygon(p, fill=255)
        dl.line([C, 2 * C - 6, C + math.cos(base_a) * l, 2 * C - 6 + math.sin(base_a) * l], fill=120, width=4)
    return finish(lum, alpha)
def petals(n=8, inner=0.0, l=0.86, w=0.21, seed=0, pointed=False):
    lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha); r = np.random.RandomState(seed)
    for layer in range(2):
        for k in range(n):
            a = (k + layer * 0.5) * 2 * math.pi / n; ll = C * l * (1 - layer * 0.25); ww = C * w * (1 - layer * 0.15)
            p = leaf_poly(C + math.cos(a) * C * inner, C + math.sin(a) * C * inner, a, ll, ww, pointed)
            tone = int(200 + layer * 40 + r.uniform(-14, 14)); dl.polygon(p, fill=tone); da.polygon(p, fill=255)
            dl.line([C, C, C + math.cos(a) * ll * 0.8, C + math.sin(a) * ll * 0.8], fill=tone - 26, width=3)
    return finish(lum, alpha)
def disc(tex=True, seed=0):
    lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha); r = np.random.RandomState(seed)
    da.ellipse([20, 20, 2 * C - 20, 2 * C - 20], fill=255); dl.ellipse([20, 20, 2 * C - 20, 2 * C - 20], fill=215)
    for i in range(160):
        a = r.rand() * 6.28; d = r.rand() ** 0.5 * (C - 40); x = C + math.cos(a) * d; y = C + math.sin(a) * d; s = r.uniform(8, 16)
        dl.ellipse([x - s, y - s, x + s, y + s], fill=int(r.uniform(160, 250)))
    return finish(lum, alpha)
def lilypad(seed=0):
    lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha)
    da.pieslice([14, 14, 2 * C - 14, 2 * C - 14], 14, 346, fill=255); dl.pieslice([14, 14, 2 * C - 14, 2 * C - 14], 14, 346, fill=190)
    for k in range(14):
        a = math.radians(20 + k * 24.5); dl.line([C, C, C + math.cos(a) * (C - 22), C + math.sin(a) * (C - 22)], fill=232, width=5)
    dl.ellipse([40, 40, 2 * C - 40, 2 * C - 40], outline=160, width=6)
    return finish(lum, alpha)
def reed(seed=0):
    r = np.random.RandomState(seed); lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha)
    for i in range(9):
        x0 = C + (i - 4) * 42 + r.uniform(-12, 12); h = 2 * C * r.uniform(0.6, 0.97); lean = r.uniform(-0.12, 0.12)
        x1 = x0 + lean * h; y1 = 2 * C - h
        tone = int(r.uniform(140, 225)); dl.line([x0, 2 * C, x1, y1], fill=tone, width=9); da.line([x0, 2 * C, x1, y1], fill=255, width=9)
        if i % 2 == 0:   # cattail head (dark)
            dl.rounded_rectangle([x1 - 13, y1 + 20, x1 + 13, y1 + 120], 12, fill=60); da.rounded_rectangle([x1 - 13, y1 + 20, x1 + 13, y1 + 120], 12, fill=255)
        else:
            p = leaf_poly(x0, 2 * C, -math.pi / 2 + lean * 3 + r.uniform(-0.3, 0.3), h * 0.8, 15, True); dl.polygon(p, fill=tone + 20); da.polygon(p, fill=255)
    return finish(lum, alpha)
def frond(seed=0):
    r = np.random.RandomState(seed); lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha)
    # palm frond along +Y (base at bottom center)
    for i in range(30):
        t = (i + 1) / 30.0; y = 2 * C - 10 - t * (2 * C - 30); x = C
        for sgn in (-1, 1):
            a = -math.pi / 2 + sgn * (1.25 - t * 0.55); l = C * 0.92 * math.sin(min(1, t * 1.25 + 0.12) * math.pi) ** 0.6 * r.uniform(0.85, 1.05)
            p = leaf_poly(x, y, a, l, 11, True); tone = int(r.uniform(135, 235)); dl.polygon(p, fill=tone); da.polygon(p, fill=255)
    dl.line([C, 2 * C, C, 20], fill=110, width=7); da.line([C, 2 * C, C, 20], fill=255, width=7)
    return finish(lum, alpha)
def strand(seed=0):
    r = np.random.RandomState(seed); lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha)
    # hanging strands (willow / vines), attached at top
    for s in range(7):
        x0 = C + (s - 3) * 62 + r.uniform(-14, 14); L = 2 * C * r.uniform(0.7, 0.98)
        for i in range(22):
            t = i / 21.0; x = x0 + math.sin(t * 5 + s) * 14; y = 6 + t * L
            for sgn in (-1, 1):
                p = leaf_poly(x, y, math.pi / 2 + sgn * r.uniform(0.35, 0.8), 40, 8, True); tone = int(r.uniform(125, 240)); dl.polygon(p, fill=tone); da.polygon(p, fill=255)
    return finish(lum, alpha)
def spike(seed=0):
    r = np.random.RandomState(seed); lum, alpha = cell_canvas(); dl = ImageDraw.Draw(lum); da = ImageDraw.Draw(alpha)
    # lavender-like flower spikes (upper 60%); stems are separate geometry
    for s in range(7):
        x0 = C + (s - 3) * 60 + r.uniform(-10, 10); top = r.uniform(10, 120); lean = r.uniform(-0.1, 0.1)
        for i in range(26):
            t = i / 25.0; y = top + t * (2 * C * 0.62); x = x0 + lean * (y - top) + r.uniform(-9, 9); rad = (7 + 12 * math.sin(min(1, t + 0.15) * math.pi)) * r.uniform(0.8, 1.2)
            tone = int(r.uniform(150, 250)); dl.ellipse([x - rad, y - rad, x + rad, y + rad], fill=tone); da.ellipse([x - rad, y - rad, x + rad, y + rad], fill=255)
    return finish(lum, alpha)
def foliage_atlas():
    cells = [cluster(1, 120, (58, 21), 0.80), cluster(2, 150, (46, 17), 0.82), blossom(3), needles(4),
             grass_tuft(5), grass_tuft(6, 20, 1.0, 0.6), fern(7), petals(8, 0.05, 0.86, 0.2, 8),
             petals(5, 0.02, 0.86, 0.34, 9), disc(True, 10), petals(6, 0.0, 0.9, 0.26, 11, True), lilypad(12),
             reed(13), frond(14), strand(15), spike(16)]
    at = np.zeros((C * 4, C * 4, 4), np.uint8)
    for i, c in enumerate(cells): at[(i // 4) * C:(i // 4 + 1) * C, (i % 4) * C:(i % 4 + 1) * C] = c
    Image.fromarray(at, 'RGBA').save(os.path.join(OUT, 'foliage.png'))

for f in (detail_rock, detail_bark, detail_planks, detail_bricks, detail_plaster, detail_tiles, detail_thatch, t_grass, t_dirt, t_sand, t_rock, t_snow, t_noise, foliage_atlas): f()
print('textures ok', sorted(os.listdir(OUT)))
