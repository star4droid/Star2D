"""Images of the Adventure UI, drawn to look like the original web game (glass panels, round gradient skill buttons, chips, minimap
frame, dialogue box, menu). Everything is drawn with PIL at 3x and scaled down; no text is baked in (texts are live TEXT elements with the
Cairo font so Arabic is shaped by the engine)."""
import math, os
from PIL import Image, ImageDraw, ImageFilter

SS = 3
GOLD = (255, 211, 106)
GLASS = (12, 20, 46, 168)
EDGE = (160, 200, 255, 70)


def _new(w, h):
    return Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))


def _save(img, w, h, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.resize((w, h), Image.LANCZOS).save(path, optimize=True)


def _vgrad(w, h, c1, c2):
    g = Image.new("RGBA", (w, h))
    px = g.load()
    for y in range(h):
        t = y / max(1, h - 1)
        col = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))
        for x in range(w):
            px[x, y] = col
    return g


def _masked(img_grad, mask_draw_fn, size):
    m = Image.new("L", size, 0)
    mask_draw_fn(ImageDraw.Draw(m))
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    out.paste(img_grad, (0, 0), m)
    return out


def glass(path, w, h, r, fill=GLASS, edge=EDGE, edge_w=2, gradient=None):
    img = _new(w, h)
    W, H = w * SS, h * SS
    if gradient:
        g = _vgrad(W, H, gradient[0], gradient[1])
        img = _masked(g, lambda d: d.rounded_rectangle([0, 0, W - 1, H - 1], radius=r * SS, fill=255), (W, H))
    else:
        ImageDraw.Draw(img).rounded_rectangle([0, 0, W - 1, H - 1], radius=r * SS, fill=fill)
    d = ImageDraw.Draw(img)
    if edge:
        d.rounded_rectangle([0, 0, W - 1, H - 1], radius=r * SS, outline=edge, width=edge_w * SS)
    _save(img, w, h, path)


def _shadow_text_icon(d, fn, W, H, c):
    fn(d, W, H, SS, c)


def round_button(path, size, c1, c2, icon, pressed_path=None, ring=(255, 255, 255, 150), icon_dy=-0.08, icon_scale=1.0):
    """A round gradient button with a glossy top, ring and a white icon."""
    for pressed in (False, True):
        W = size * SS
        g = _vgrad(W, W, c1 + (255,), c2 + (255,))
        if pressed:
            g = Image.blend(g, Image.new("RGBA", (W, W), (255, 255, 255, 255)), 0.22)
        img = _masked(g, lambda d: d.ellipse([SS * 3, SS * 3, W - SS * 3, W - SS * 3], fill=255), (W, W))
        # shadow below
        sh = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        ImageDraw.Draw(sh).ellipse([SS * 3, SS * 7, W - SS * 3, W - SS * 1], fill=(0, 0, 0, 90))
        sh = sh.filter(ImageFilter.GaussianBlur(SS * 3))
        base = Image.alpha_composite(sh, img)
        d = ImageDraw.Draw(base)
        d.ellipse([SS * 3, SS * 3, W - SS * 3, W - SS * 3], outline=ring, width=3 * SS)
        d.ellipse([W * 0.16, W * 0.1, W * 0.84, W * 0.5], fill=(255, 255, 255, 34))
        ic = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        di = ImageDraw.Draw(ic)
        icon(di, W, W, SS, (255, 255, 255, 255))
        if icon_scale != 1.0 or icon_dy:
            ic = ic.resize((int(W * icon_scale), int(W * icon_scale)), Image.LANCZOS)
            canvas = Image.new("RGBA", (W, W), (0, 0, 0, 0))
            canvas.paste(ic, (int((W - ic.width) / 2), int((W - ic.height) / 2 + W * icon_dy)), ic)
            ic = canvas
        s2 = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        s2.paste((0, 0, 0, 90), (SS * 2, SS * 3), ic.split()[3])
        s2 = s2.filter(ImageFilter.GaussianBlur(SS * 1.5))
        base = Image.alpha_composite(base, s2)
        base = Image.alpha_composite(base, ic)
        _save(base, size, size, path.replace(".png", "_p.png") if pressed else path)


# ---- icons (white, drawn in a W x H box)
def ic_sword(d, w, h, ss, c):
    d.polygon([(w * 0.71, h * 0.2), (w * 0.8, h * 0.29), (w * 0.44, h * 0.66), (w * 0.34, h * 0.56)], fill=c)
    d.line([(w * 0.26, h * 0.54), (w * 0.46, h * 0.74)], fill=c, width=int(w * 0.05))
    d.line([(w * 0.36, h * 0.65), (w * 0.24, h * 0.78)], fill=c, width=int(w * 0.06))


def ic_slash(d, w, h, ss, c):
    # a crescent wave
    d.pieslice([w * 0.18, h * 0.22, w * 0.86, h * 0.9], 200, 340, fill=c)
    d.pieslice([w * 0.18, h * 0.34, w * 0.86, h * 1.0], 200, 340, fill=(0, 0, 0, 0))
    d.line([(w * 0.28, h * 0.64), (w * 0.72, h * 0.28)], fill=c, width=int(w * 0.07))


def ic_whirl(d, w, h, ss, c):
    cx, cy = w / 2, h / 2
    for k, r in enumerate((0.3, 0.2, 0.1)):
        d.arc([cx - w * r, cy - h * r, cx + w * r, cy + h * r], 30 + k * 70, 300 + k * 70, fill=c, width=int(w * 0.055))
    d.polygon([(cx + w * 0.3, cy - h * 0.06), (cx + w * 0.42, cy + h * 0.06), (cx + w * 0.2, cy + h * 0.08)], fill=c)


def ic_arrow(d, w, h, ss, c):
    d.line([(w * 0.26, h * 0.74), (w * 0.72, h * 0.28)], fill=c, width=int(w * 0.055))
    d.polygon([(w * 0.8, h * 0.2), (w * 0.8, h * 0.46), (w * 0.54, h * 0.2)], fill=c)
    d.polygon([(w * 0.2, h * 0.8), (w * 0.16, h * 0.62), (w * 0.34, h * 0.78)], fill=c)


def ic_dash(d, w, h, ss, c):
    for k in range(3):
        x = w * (0.22 + k * 0.17)
        d.polygon([(x, h * 0.28), (x + w * 0.18, h * 0.5), (x, h * 0.72), (x + w * 0.07, h * 0.5)], fill=c)


def ic_up(d, w, h, ss, c):
    d.polygon([(w * 0.5, h * 0.2), (w * 0.24, h * 0.52), (w * 0.4, h * 0.52), (w * 0.4, h * 0.78), (w * 0.6, h * 0.78), (w * 0.6, h * 0.52), (w * 0.76, h * 0.52)], fill=c)


def ic_coin(d, w, h, ss, c=None):
    cx, cy, r = w / 2, h / 2, min(w, h) * 0.42
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 200, 40, 255), outline=(255, 232, 140, 255), width=int(r * 0.16))
    r2 = r * 0.55
    d.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], outline=(214, 150, 20, 255), width=int(r * 0.14))


def ic_gem(d, w, h, ss, c=None):
    cx, cy = w / 2, h / 2
    r = min(w, h) * 0.42
    pts = [(cx, cy - r), (cx + r * 0.78, cy - r * 0.1), (cx, cy + r), (cx - r * 0.78, cy - r * 0.1)]
    d.polygon(pts, fill=(80, 200, 255, 255), outline=(190, 240, 255, 255))
    d.polygon([(cx, cy - r), (cx + r * 0.78, cy - r * 0.1), (cx, cy - r * 0.1)], fill=(150, 228, 255, 255))
    d.polygon([(cx, cy - r), (cx - r * 0.78, cy - r * 0.1), (cx, cy - r * 0.1)], fill=(110, 212, 255, 255))


def ic_pause(d, w, h, ss, c):
    for x in (0.36, 0.56):
        d.rounded_rectangle([w * x, h * 0.3, w * (x + 0.09), h * 0.7], radius=int(w * 0.02), fill=c)


def ic_castle(d, w, h, ss, c):
    d.rectangle([w * 0.2, h * 0.4, w * 0.8, h * 0.9], outline=c, width=int(w * 0.03))
    for x in (0.2, 0.42, 0.62):
        d.rectangle([w * x, h * 0.2, w * (x + 0.18), h * 0.42], outline=c, width=int(w * 0.03))
    d.arc([w * 0.4, h * 0.55, w * 0.6, h * 0.95], 180, 360, fill=c, width=int(w * 0.03))


def ic_ff(d, w, h, ss, c):
    # skip icon: two triangles and a bar
    d.polygon([(w * 0.1, h * 0.2), (w * 0.1, h * 0.8), (w * 0.45, h * 0.5)], fill=c)
    d.polygon([(w * 0.45, h * 0.2), (w * 0.45, h * 0.8), (w * 0.8, h * 0.5)], fill=c)
    d.rectangle([w * 0.84, h * 0.2, w * 0.92, h * 0.8], fill=c)


def icon_png(path, size, fn, color=(255, 255, 255, 255)):
    img = _new(size, size)
    fn(ImageDraw.Draw(img), size * SS, size * SS, SS, color)
    _save(img, size, size, path)


def chip(path, w, h, icon_fn, border=EDGE):
    """Pill with a round icon on its left side (counter chip of the original)."""
    glass(path, w, h, h // 2, edge=border)
    img = Image.open(path).convert("RGBA")
    ic = _new(h, h)
    icon_fn(ImageDraw.Draw(ic), h * SS, h * SS, SS)
    ic = ic.resize((int(h * 0.62), int(h * 0.62)), Image.LANCZOS)
    img.paste(ic, (int(h * 0.2), int((h - ic.height) / 2)), ic)
    img.save(path, optimize=True)


def circle_icon_button(path, size, icon, fill=GLASS, edge=EDGE):
    img = _new(size, size)
    W = size * SS
    d = ImageDraw.Draw(img)
    d.ellipse([SS, SS, W - SS, W - SS], fill=fill, outline=edge, width=2 * SS)
    icon(d, W, W, SS, (255, 255, 255, 255))
    _save(img, size, size, path)


def face_ring(src, path, size, ring, ring_w=4):
    im = Image.open(src).convert("RGBA")
    s = min(im.size)
    im = im.crop(((im.width - s) // 2, 0, (im.width - s) // 2 + s, s)).resize((size * SS, size * SS), Image.LANCZOS)
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).ellipse([ring_w * SS, ring_w * SS, size * SS - ring_w * SS, size * SS - ring_w * SS], fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(out).ellipse([0, 0, size * SS - 1, size * SS - 1], fill=(10, 16, 36, 255))
    out.paste(im, (0, 0), mask)
    ImageDraw.Draw(out).ellipse([ring_w * SS // 2, ring_w * SS // 2, size * SS - ring_w * SS // 2, size * SS - ring_w * SS // 2], outline=ring, width=ring_w * SS)
    _save(out, size, size, path)


def joystick(proj):
    size = 256
    W = size * SS
    img = _new(size, size)
    d = ImageDraw.Draw(img)
    d.ellipse([SS * 4, SS * 4, W - SS * 4, W - SS * 4], fill=(255, 255, 255, 26), outline=(255, 255, 255, 120), width=3 * SS)
    # dashed inner ring
    r = W * 0.34
    for k in range(24):
        a0 = k * 15
        d.arc([W / 2 - r, W / 2 - r, W / 2 + r, W / 2 + r], a0, a0 + 8, fill=(255, 255, 255, 110), width=2 * SS)
    for a in (0, 90, 180, 270):
        rr = math.radians(a)
        cx, cy = W / 2 + math.cos(rr) * W * 0.43, W / 2 + math.sin(rr) * W * 0.43
        t = W * 0.035
        d.polygon([(cx + math.cos(rr) * t, cy + math.sin(rr) * t), (cx + math.cos(rr + 2.2) * t, cy + math.sin(rr + 2.2) * t),
                   (cx + math.cos(rr - 2.2) * t, cy + math.sin(rr - 2.2) * t)], fill=(255, 255, 255, 150))
    _save(img, size, size, os.path.join(proj, "images/joy_pad.png"))
    k = 120
    K = k * SS
    g = _vgrad(K, K, (200, 225, 255, 235), (110, 160, 255, 235))
    knob = _masked(g, lambda dd: dd.ellipse([SS * 3, SS * 3, K - SS * 3, K - SS * 3], fill=255), (K, K))
    ImageDraw.Draw(knob).ellipse([SS * 3, SS * 3, K - SS * 3, K - SS * 3], outline=(255, 255, 255, 230), width=3 * SS)
    _save(knob, k, k, os.path.join(proj, "images/joy_knob.png"))


def menu_side(path, w, h):
    """The dark gradient on the left of the main menu (fades to the right)."""
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for x in range(w):
        t = x / (w - 1)
        a = int(235 * (1 - t) ** 1.25) if t > 0.55 else 235 - int((235 - 235 * (1 - 0.55) ** 1.25) * (t / 0.55) * 0.0)
        a = int(228 * min(1.0, (1 - t) / 0.45))
        for y in range(h):
            px[x, y] = (7, 14, 30, a)
    img.save(path, optimize=True)


def shade(path, w, h, alpha):
    Image.new("RGBA", (w, h), (4, 8, 20, alpha)).save(path)


def button_gold(path, w, h):
    glass(path, w, h, 24, edge=(255, 240, 190, 200), gradient=((255, 196, 80, 255), (255, 140, 30, 255)))


def button_glass(path, w, h):
    glass(path, w, h, 22, fill=(18, 36, 24, 120), edge=(200, 235, 255, 90))


def make_all(proj, repo, image_from):
    p = lambda n: os.path.join(proj, "images", n)
    os.makedirs(os.path.join(proj, "images"), exist_ok=True)
    # skill buttons (colors of the original)
    round_button(p("btn_attack.png"), 150, (110, 170, 255), (36, 82, 220), ic_sword, icon_scale=0.95)
    round_button(p("btn_skill_a.png"), 104, (255, 170, 80), (236, 90, 24), ic_slash, icon_dy=-0.1)
    round_button(p("btn_skill_b.png"), 104, (190, 130, 255), (104, 54, 208), ic_whirl, icon_dy=-0.1)
    round_button(p("btn_dash.png"), 92, (80, 226, 210), (18, 140, 134), ic_dash, icon_dy=-0.1)
    round_button(p("btn_jump.png"), 92, (120, 228, 110), (38, 150, 62), ic_up, icon_dy=-0.1)
    joystick(proj)
    # HUD
    glass(p("panel_hero.png"), 252, 84, 18)
    chip(p("chip_coin.png"), 98, 42, ic_coin)
    chip(p("chip_gem.png"), 98, 42, ic_gem)
    circle_icon_button(p("btn_pause.png"), 46, ic_pause)
    circle_icon_button(p("btn_pause_p.png"), 46, ic_pause, fill=(60, 80, 130, 220))
    img = _new(128, 128)
    d = ImageDraw.Draw(img)
    d.ellipse([SS * 2, SS * 2, 128 * SS - SS * 2, 128 * SS - SS * 2], fill=(10, 20, 36, 235))
    _save(img, 128, 128, p("map_back.png"))
    img = _new(128, 128)
    d = ImageDraw.Draw(img)
    d.ellipse([SS * 2, SS * 2, 128 * SS - SS * 2, 128 * SS - SS * 2], outline=(205, 222, 245, 255), width=5 * SS)
    d.ellipse([SS * 6, SS * 6, 128 * SS - SS * 6, 128 * SS - SS * 6], outline=(20, 30, 60, 150), width=2 * SS)
    _save(img, 128, 128, p("map_frame.png"))
    glass(p("panel_quest.png"), 252, 84, 16)
    icon_img = _new(30, 30)
    d = ImageDraw.Draw(icon_img)
    W = 30 * SS
    d.ellipse([SS, SS, W - SS, W - SS], fill=(255, 211, 106, 255))
    d.rounded_rectangle([W * 0.45, W * 0.2, W * 0.55, W * 0.6], radius=SS * 2, fill=(70, 40, 0, 255))
    d.ellipse([W * 0.44, W * 0.68, W * 0.56, W * 0.8], fill=(70, 40, 0, 255))
    _save(icon_img, 30, 30, p("icon_quest.png"))
    # faces
    for k in ("warrior", "archer", "gunner"):
        src = os.path.join(repo, "img", "face_%s.webp" % k)
        face_ring(src, p("face_%s.png" % k), 64, (255, 211, 106, 255))
        face_ring(src, p("pface_%s.png" % k), 56, (255, 255, 255, 140), 3)
        face_ring(src, p("pface_%s_on.png" % k), 56, (255, 211, 106, 255), 4)
    for k in ("warrior", "archer", "gunner", "healer"):
        image_from(os.path.join(repo, "img", "bust_%s.webp" % k), "bust_%s.png" % k, (420, 460))
    # dialogue
    glass(p("panel_dialog.png"), 700, 150, 24, fill=(14, 20, 44, 238), edge=(150, 185, 235, 190), edge_w=2)
    glass(p("pill_name.png"), 150, 40, 16, edge=None, gradient=((214, 62, 98, 255), (188, 44, 80, 255)))
    glass(p("pill_skip.png"), 118, 40, 20, fill=(10, 14, 28, 190), edge=(255, 255, 255, 110))
    icon_png(p("icon_skip.png"), 24, ic_ff)
    # boss bar + banner
    glass(p("boss_frame.png"), 440, 34, 17, fill=(10, 14, 28, 215), edge=(255, 255, 255, 90))
    glass(p("chip_white.png"), 190, 34, 17, fill=(255, 255, 255, 235), edge=None)
    glass(p("banner.png"), 380, 84, 24, fill=(12, 20, 46, 205), edge=(255, 211, 106, 150))
    # menu / modals
    menu_side(p("menu_side.png"), 600, 720)
    shade(p("shade.png"), 64, 64, 150)
    shade(p("shade_menu.png"), 64, 64, 70)
    button_gold(p("btn_gold.png"), 442, 78)
    button_glass(p("btn_glass.png"), 442, 66)
    button_glass(p("btn_glass_s.png"), 300, 62)
    button_gold(p("btn_gold_s.png"), 300, 62)
    glass(p("panel_center.png"), 560, 360, 28, fill=(12, 20, 46, 238), edge=(160, 200, 255, 140))
    icon_png(p("icon_castle.png"), 96, ic_castle, (190, 235, 255, 255))
