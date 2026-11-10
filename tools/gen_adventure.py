#!/usr/bin/env python3
"""Builds the "Adventure" Star2D project: a port of the Adventure-Game web game (مغامرات الأفق) to the engine.

    python3 tools/gen_adventure.py <assets dir> <out dir>

<assets dir> holds what the bake scripts produced from the Adventure-Game repository:
  models/*.glb   characters with real glTF animations baked from anims.json (meshopt / webp removed)
  world.s2t      the painted terrain (heights of terrain.js + the glade of world.js, paint, trees, rocks...)
  world.json     zones, barriers, ponds, terrain placement
  sounds/*.wav   synthesized sound effects
and the repository itself (images, story voices) is read from ADVENTURE (default /home/claude/adventure-game).

The scene uses only built-in behaviors and blocks (validated against the real block catalog and compiled like the editor
would, with tools/examples3d_test/Examples3DCheck.java).
"""
import json, math, os, shutil, struct, sys, zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_3d_examples as G  # noqa: E402

ASSETS = sys.argv[1] if len(sys.argv) > 1 else "/tmp/advout"
OUT = sys.argv[2] if len(sys.argv) > 2 else "/tmp/advproj"
REPO = os.environ.get("ADVENTURE", "/home/claude/adventure-game")
FOLDER = "Adventure"
W, H = 1280, 720

G.EXAMPLES = OUT
G.FOLDERS = [FOLDER]
G.MAX_BYTES = 40 * 1024 * 1024

# ---------------------------------------------------------------------- real block definitions
REAL = json.load(open(os.path.join(ASSETS, "blocks.json"), encoding="utf-8"))
SHARED = json.load(open(os.path.join(ASSETS, "shared.json"), encoding="utf-8"))


def block(title):
    """Registers a real block (3D / behavior / shared catalog) under its title for B()."""
    if title in G.BLOCKS:
        return
    d = REAL["defs"].get(title)
    if d is not None:
        src = "bhv" if "." in title else "3d"
        G.BLOCKS[title] = (src, d["fields"], d["code"], d["color"], d["isIf"])
        return
    s = SHARED["shared"].get(title)
    assert s is not None, "no block " + title
    G.BLOCKS[title] = ("cat", s[0], s[1], s[2], False)


def reporter(display):
    if display in G.REPORTERS:
        return
    t = REAL["reporters"].get(display, SHARED["reps"].get(display))
    if t is None:
        for bb in REAL["behaviorBlocks"].values():
            if bb.get("display") == display:
                t = bb["code"]
    assert t is not None, "no reporter " + display
    G.REPORTERS[display] = t


for t in ["setVisible3D", "setPhysics3D", "playSound3D", "cameraShake3D", "showGraphicsMenu3D", "setProgress", "setVisibility", "setText",
          "Dialogue.start", "Spawner3D.start", "Compass3D.setTarget", "Compass3D.setAnchor", "Companion3D.setLeader", "Boss3D.dropIn",
          "ThirdPersonController3D.setLocked", "Pause", "Resume", "setImage", "CharacterSwitch3D.switchTo", "waitSeconds"]:
    block(t)
for r in ["___ == ___", "___ >= ___", "___ and ___", "copies alive ___", "copies made ___", "current character ___", "current character number ___",
          "health percent ___", "count of item ___", "health ___"]:
    reporter(r)
B, R, q = G.B, G.R, G.q

# ---------------------------------------------------------------------- terrain sampling (same triangles as TerrainData.height)
INFO = json.load(open(os.path.join(ASSETS, "world.json"), encoding="utf-8"))


def read_s2t(path):
    b = open(path, "rb").read()
    assert struct.unpack(">i", b[:4])[0] == 0x53325432
    z = zlib.decompress(b[8:])
    res = struct.unpack(">i", z[:4])[0]
    n = res + 1
    h = struct.unpack(">%df" % (n * n), z[4:4 + 4 * n * n])
    return res, h


RES, HN = read_s2t(os.path.join(ASSETS, "world.s2t"))
X0, Z0, SX, SZ = INFO["X0"], INFO["Z0"], INFO["size"][0], INFO["size"][1]
BASE, HEIGHT = INFO["BASE"], INFO["HEIGHT"]


def ground(x, z):
    u = min(max((x - X0) / SX, 0), 1) * RES
    v = min(max((z - Z0) / SZ, 0), 1) * RES
    i, j = min(int(u), RES - 1), min(int(v), RES - 1)
    fu, fv = u - i, v - j
    n = RES + 1
    at = lambda a, c: HN[c * n + a]
    h00, h10, h01, h11 = at(i, j), at(i + 1, j), at(i, j + 1), at(i + 1, j + 1)
    hh = h00 + fu * (h10 - h00) + fv * (h01 - h00) if fu + fv <= 1 else h11 + (1 - fu) * (h01 - h11) + (1 - fv) * (h10 - h11)
    return BASE + hh * HEIGHT


# ---------------------------------------------------------------------- assets
def copy(src, kind, name=None):
    d = os.path.join(PROJ, kind)
    os.makedirs(d, exist_ok=True)
    dst = os.path.join(d, name or os.path.basename(src))
    shutil.copyfile(src, dst)
    return os.path.basename(dst)


def image_from(src, name, size=None):
    from PIL import Image
    im = Image.open(src).convert("RGBA")
    if size:
        im.thumbnail(size)
    os.makedirs(os.path.join(PROJ, "images"), exist_ok=True)
    im.save(os.path.join(PROJ, "images", name), optimize=True)
    return name


def panel_png(name, w, h, fill, radius=24, border=None):
    from PIL import Image, ImageDraw
    ss = 2
    img = Image.new("RGBA", (w * ss, h * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w * ss - 1, h * ss - 1], radius=radius * ss, fill=fill, outline=border, width=3 * ss if border else 0)
    img.resize((w, h), Image.LANCZOS).save(os.path.join(PROJ, "images", name), optimize=True)


def coin_sound(path):
    """A short two-note blip (CC0, synthesized)."""
    import wave
    sr = 22050
    frames = bytearray()
    for i in range(int(sr * 0.22)):
        t = i / sr
        f = 988 if t < 0.07 else 1319
        env = min(1.0, t * 200) * math.exp(-max(0.0, t - 0.07) * 14)
        v = int(9000 * env * math.sin(2 * math.pi * f * t) + 3000 * env * math.sin(4 * math.pi * f * t))
        frames += struct.pack("<h", v)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    w = wave.open(path, "wb")
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(sr)
    w.writeframes(bytes(frames))
    w.close()


def ui():
    import adv_ui_assets as UA
    UA.make_all(PROJ, REPO, image_from)


def font():
    """Arabic + Latin font of the texts: Cairo (the font of the original game, OFL), merged Arabic + Latin subsets."""
    d = os.path.join(PROJ, "files")
    os.makedirs(d, exist_ok=True)
    for f in ("Cairo-700.ttf", "Cairo-900.ttf"):
        shutil.copyfile(os.path.join("/tmp/cairo", f), os.path.join(d, f))
    for name, ttf, size, border in (("ar.s2df", "Cairo-700.ttf", 34, 2.0), ("ar_big.s2df", "Cairo-900.ttf", 64, 3.0), ("ar_ui.s2df", "Cairo-700.ttf", 34, 0.0)):
        cfg = {"font": ttf, "rtl": "true", "size": str(size), "color": "FFFFFFFF", "borderWidth": str(border), "borderColor": "000000B0",
               "shadowOffsetX": "0" if not border else "1", "shadowOffsetY": "0" if not border else "2", "shadowColor": "00000066", "kerning": "true", "hinting": "AutoMedium",
               "genMipMaps": "false", "incremental": "false", "mono": "false", "flip": "false", "renderCount": "2", "borderGamma": "1.8", "borderStraight": "false",
               "spaceX": "0", "spaceY": "0", "padTop": "0", "padLeft": "0", "padRight": "0", "padBottom": "0", "magFilter": "Linear", "minFilter": "Linear"}
        json.dump(cfg, open(os.path.join(d, name), "w"))


# ---------------------------------------------------------------------- story (story.js)
NAMES_SKILL = {"warrior": ("هجوم", "شقّ", "إعصار"), "archer": ("سهم", "وابل", "خارق"), "gunner": ("إطلاق", "الآلة", "قنبلة")}
HELP_TEXT = ("الحركة: عصا التحكم على اليسار\nالهجوم والمهارات: الأزرار على اليمين\nاقفز بزر القفز واندفع لتفادي الضربات\n"
             "بدّل الشخصية بالضغط على وجهها في الأعلى\nاتبع السهم الأزرق وأنجز مهام الوادي")
NAMES = {"warrior": "المحارب", "archer": "الرامية", "healer": "المعالجة", "gunner": "المقاتل"}
SCRIPT = {
    "intro": [("healer", "أخيرًا استيقظتم! منذ الليلة الماضية وطاقةٌ غريبة تتدفّق من الوادي الجنوبي، ولم أشعر بمثلها من قبل."),
              ("warrior", "شعرتُ بها أنا أيضًا. السلايمات أصبحت عدوانيةً على غير عادتها، كأنّ شيئًا ما يتحكّم بها."),
              ("archer", "وأنا رأيتُ أضواءً زرقاء تحلّق فوق الغابة الخريفية… ولم تكن طيورًا."),
              ("gunner", "إذن لا وقت لدينا. سنسلك الطريق جنوبًا عبر الممرّ الصخري، وأنا سأحمي ظهوركم."),
              ("healer", "وسأبقى قريبةً منكم لأداوي جراحكم. اتبعوا السهم الأزرق!")],
    "meadow": [("archer", "احذروا! سلايمات المرج قادمة، وبعضها أكبر وأقوى من المعتاد."),
               ("warrior", "لن تمرّ من هنا. استعدّوا للقتال!")],
    "meadowDone": [("healer", "أحسنتم! انظروا، لقد انطفأ حاجز الطاقة وانفتح الطريق."),
                   ("gunner", "هذه الحواجز تقنيةٌ لا تنتمي إلى عالمنا… أحدهم وضعها هنا عمدًا.")],
    "falls": [("warrior", "بحيرة الشلال… مكانٌ جميل، لكنّ هذا الهدوء لا يُطمئنني."),
              ("archer", "إنها تتجمّع حول البحيرة! ستّةٌ على الأقل، وأحدها ضخم!")],
    "fallsDone": [("gunner", "كلّما اتجهنا جنوبًا ازدادت الطاقة قوّة. مصدرها لم يعد بعيدًا."),
                  ("healer", "الغابة الخريفية أمامنا. كونوا حذرين… أشعر بأنّ شيئًا معدنيًّا يراقبنا.")],
    "autumn": [("archer", "هذه هي الأضواء التي رأيتُها! إنها طائرات مسيّرة!"),
               ("gunner", "تتوقّف لتصوّب ثم تطلق الليزر من بعيد. تحرّكوا ولا تقفوا في مكانكم!")],
    "autumnDone": [("warrior", "أسقطناها كلّها… لكنّ الأرض ترتجف. شيءٌ ضخم ينتظرنا في الساحة القديمة."),
                   ("healer", "سأمنحكم كلّ ما أملك من قوّة. لا تتراجعوا!")],
    "boss": [("gunner", "هذا هو مصدر كلّ شيء… العملاق الأزرق!"),
             ("warrior", "حين يضرب الأرض تنطلق منه موجةٌ دائرية. اقفزوا فوقها في اللحظة المناسبة!"),
             ("archer", "ابتعدوا حين يقفز نحوكم، وهاجموه من كلّ الجهات!")],
    "victory": [("healer", "لقد انتصرنا! الطاقة الغريبة تتلاشى، والوادي يعود إلى هدوئه."),
                ("warrior", "لكنّ من صنع هذا العملاق ما زال في مكانٍ ما هناك."),
                ("gunner", "إذن سنكون مستعدّين له… معًا، كما كنّا اليوم."),
                ("archer", "وحتى ذلك الحين… من يسبقني إلى الشلال؟")],
}


def story_script():
    out = []
    for key, lines in SCRIPT.items():
        out.append("[%s]" % key)
        for i, (who, text) in enumerate(lines):
            voice = "%s_%d.mp3" % (key, i)
            src = os.path.join(REPO, "audio", "ja", voice)
            if os.path.isfile(src):
                copy(src, "sounds", voice)
            else:
                voice = ""
            out.append("%s|%s|%s|%s" % (who, NAMES[who], text.replace("|", " "), "/" + voice if voice else ""))
    return "\n".join(out)


# ---------------------------------------------------------------------- scene
def build():
    global PROJ
    PROJ = G.fresh(FOLDER)
    sc = G.Scene()
    files = []
    # models: characters turned to face -Z (the engine's forward); props as they are
    for m in ("warrior", "archer", "gunner", "healer", "boss"):
        G.glb_turn(os.path.join(ASSETS, "models", m + ".glb"), os.path.join(PROJ, "models", m + ".glb") if os.path.isdir(os.path.join(PROJ, "models"))
                   else (os.makedirs(os.path.join(PROJ, "models")) or os.path.join(PROJ, "models", m + ".glb")), 90)
    for m in ("arrow", "drone", "turret", "tree", "guide"):
        copy(os.path.join(ASSETS, "models", m + ".glb"), "models")
    for s in os.listdir(os.path.join(ASSETS, "sounds")):
        if s not in ("jump.wav", "click.wav"):
            copy(os.path.join(ASSETS, "sounds", s), "sounds")
    coin_sound(os.path.join(PROJ, "sounds", "coin.wav"))
    os.makedirs(os.path.join(PROJ, "terrains"), exist_ok=True)
    shutil.copyfile(os.path.join(ASSETS, "world.s2t"), os.path.join(PROJ, "terrains", "world.s2t"))
    ui()
    font()
    ZONES, BARS, PONDS = INFO["zones"], INFO["barriers"], INFO["ponds"]
    gy = ground

    # ---------------------------------------------------------------- world
    cx, cz = INFO["center"]
    sc.add("OBJ3D", "Ground", Mesh="TERRAIN", x=cx, y=BASE, z=cz, Scale_X=SX, Scale_Y=1, Scale_Z=SZ, Static_Object=True, Body="STATIC",
           Cast_Shadows=False, P={"Terrain Data": "/world.s2t", "Terrain Height": HEIGHT, "Color": "#FFFFFF",
                                  "Layer 1 Texture": "builtin:grass", "Layer 2 Texture": "builtin:road", "Layer 3 Texture": "builtin:rock", "Layer 4 Texture": "builtin:leaves",
                                  "Layer 1 Tiling": 34, "Layer 2 Tiling": 48, "Layer 3 Tiling": 30, "Layer 4 Tiling": 40,
                                  "Grass Density": float(os.environ.get("ADV_GRASS", "2.0")), "Grass Height": 0.34, "Grass Color": "#7CBF45", "Grass Layer": 1, "Grass Distance": 26, "Foliage Distance": float(os.environ.get("ADV_FOL", "0"))})
    for i, p in enumerate(PONDS):
        sc.add("OBJ3D", "Pond%d" % (i + 1), Mesh="WATER", x=p["x"], y=round(p["y"], 2), z=p["z"], Scale_X=p["r"] * 2 + 1.5, Scale_Y=1, Scale_Z=p["r"] * 2 + 1.5,
               Cast_Shadows=False, Static_Object=True, P={"Wave Height": 0.06, "Wave Length": 3, "Water Clarity": 0.7, "Water Resolution": 16, "Buoyancy": 0})
    st = INFO["stream"]
    sc.add("OBJ3D", "Stream", Mesh="WATER", x=st["x"], y=st["y"], z=(st["z0"] + st["z1"]) / 2, Scale_X=9, Scale_Y=1, Scale_Z=st["z1"] - st["z0"] + 2,
           Cast_Shadows=False, Static_Object=True, P={"Wave Height": 0.04, "Wave Length": 2.5, "Water Clarity": 0.75, "Water Resolution": 20, "Buoyancy": 0})
    with sc.grp("Scenery"):
        # bridge over the stream (world.js BR)
        bx = 8 + 1.6 * math.sin(0.22 * -1.0 + 0.5)
        sc.shape("BridgeDeck", "CUBE", bx, 0.32, -1.0, 7.0, 0.18, 1.7, "#8A6240", static=True, body="STATIC")
        for k, side in enumerate((-0.85, 0.85)):
            sc.shape("BridgeRail%d" % k, "CUBE", bx, 0.85, -1.0 + side, 7.0, 0.1, 0.1, "#6B4A2E", static=True)
            for j in range(4):
                sc.shape("BridgePost%d_%d" % (k, j), "CUBE", bx - 3.2 + j * 2.13, 0.6, -1.0 + side, 0.14, 0.62, 0.14, "#6B4A2E", static=True)
        # ruined gate of the glade (GATE)
        gx, gz, gr = -6.8, -7.6, math.degrees(0.55)
        ca, sa = math.cos(0.55), math.sin(0.55)
        for k, off in enumerate((-1.7, 1.7)):
            px, pz = gx + off * ca, gz - off * sa
            sc.shape("GatePillar%d" % k, "CUBE", px, gy(px, pz) + 1.6, pz, 0.8, 3.2, 0.8, "#9A958C", ry=gr, static=True, body="STATIC")
        sc.shape("GateTop", "CUBE", gx, gy(gx, gz) + 3.4, gz, 4.6, 0.6, 1.0, "#8B867E", ry=gr, static=True)
        # chest
        sc.shape("Chest", "CUBE", 14.0, gy(14.0, -4.9) + 0.35, -4.9, 1.1, 0.7, 0.75, "#8C5A2B", static=True, body="STATIC")
        sc.shape("ChestLid", "CUBE", 14.0, gy(14.0, -4.9) + 0.78, -4.9, 1.15, 0.16, 0.8, "#D9A73A", static=True)
        # arena of the giant: broken pillars around the stone floor
        A = ZONES[3]
        for k in range(12):
            a = k * math.pi * 2 / 12 + 0.13
            px, pz = A["x"] + math.cos(a) * (A["r"] + 0.6), A["z"] + math.sin(a) * (A["r"] + 0.6)
            hgt = 2.2 + (k * 37 % 5) * 0.55
            sc.shape("Pillar%d" % k, "CYLINDER", px, gy(px, pz) + hgt / 2 - 0.1, pz, 1.1, hgt, 1.1, "#9A958C", static=True, body="STATIC")
    # energy barriers across the road (open one by one)
    for i, b in enumerate(BARS):
        ry = math.degrees(math.atan2(-b["tx"], -b["tz"]))
        sc.shape("Barrier%d" % (i + 1), "CUBE", b["x"], gy(b["x"], b["z"]) + 1.6, b["z"], b["hw"] * 2, 3.2, 0.35, "#5AD2FF", ry=ry, static=True, body="STATIC",
                 Opacity=0.45, Unlit=True, Emissive="#2FA8E0", Emissive_Intensity=1.2, Cast_Shadows=False, Receive_Shadows=False, Collider="BOX")

    # ---------------------------------------------------------------- quest places: marker, beacon, trigger zone
    keys = ["Meadow", "Falls", "Autumn", "Arena"]
    for i, Z in enumerate(ZONES):
        k = keys[i]
        y0 = gy(Z["x"], Z["z"])
        sc.add("OBJ3D", "Mark" + k, Mesh="EMPTY", x=Z["x"], y=y0 + 1, z=Z["z"], Cast_Shadows=False)
        sc.shape("Beacon" + k, "CYLINDER", Z["x"], y0 + 18, Z["z"], 1.6, 36, 1.6, "#5AD2FF", static=False, Opacity=0.28, Unlit=True, Emissive="#5AD2FF",
                 Emissive_Intensity=1, Cast_Shadows=False, Receive_Shadows=False, Visible=(i == 0), Double_Sided=True)
        sc.add("OBJ3D", "Zone" + k, [("Trigger3D", {"shape": "RADIUS", "radius": round(Z["r"] - 1.0, 1), "namePrefix": "Hero", "watchCamera": False, "once": True})],
               Mesh="EMPTY", x=Z["x"], y=y0 + 1, z=Z["z"], Cast_Shadows=False)

    # ---------------------------------------------------------------- templates (hidden, copied by spawners / skills)
    far = (0, -40, -45)
    hit = ("HitFx", "SPHERE", "#FFF4C2")
    sc.shape("HitFx", "SPHERE", *far, 0.35, 0.35, 0.35, "#FFF4C2", static=False, Unlit=True, Emissive="#FFE08A", Emissive_Intensity=1.5, Cast_Shadows=False,
             Visible=False, behaviors=[("Lifetime3D", {"seconds": 0.35, "ending": "SHRINK"})] if False else None)
    sc.shape("HealFx", "TORUS", *far, 1.4, 0.25, 1.4, "#7CFF9A", static=False, Unlit=True, Emissive="#5BFF8A", Emissive_Intensity=1.5, Cast_Shadows=False, Visible=False)
    sc.shape("DustFx", "DISC", *far, 3.0, 0.1, 3.0, "#D8CFC0", static=False, Unlit=True, Opacity=0.6, Cast_Shadows=False, Visible=False)
    sc.shape("Ring", "TORUS", *far, 2.0, 0.25, 2.0, "#7FE3FF", static=False, Unlit=True, Emissive="#4FD8FF", Emissive_Intensity=1.6, Cast_Shadows=False, Visible=False)
    # projectiles
    proj3 = lambda dmg, speed, life, pierce=False: ("Projectile3D", {"speed": speed, "gravity": 0, "lifetime": life, "damage": dmg, "destroyOnHit": not pierce,
                                                                      "impactEffect": "HitFx", "impactSound": "/hit.wav"})
    sc.model("ArrowShot", "arrow.glb", *far, 1.0, static=False, body="DYNAMIC", collider="BOX", Visible=False, Cast_Shadows=False, Gravity_Scale=0,
             Continuous_Collision=True, behaviors=[proj3(22, 34, 2.5)])
    sc.shape("SlashWave", "CUBE", *far, 2.6, 0.12, 0.5, "#9FE8FF", static=False, body="DYNAMIC", Visible=False, Unlit=True, Emissive="#6FD8FF",
             Emissive_Intensity=1.6, Opacity=0.8, Cast_Shadows=False, Gravity_Scale=0, Collider="BOX", Continuous_Collision=True, behaviors=[proj3(38, 20, 0.9, True)])
    sc.shape("Laser", "CYLINDER", *far, 0.12, 0.12, 0.9, "#FF4040", static=False, body="DYNAMIC", rx=90, Visible=False, Unlit=True, Emissive="#FF2020",
             Emissive_Intensity=2, Cast_Shadows=False, Gravity_Scale=0, Collider="SPHERE", Continuous_Collision=True,
             behaviors=[("Projectile3D", {"speed": 22, "gravity": 0, "lifetime": 2.5, "damage": 9, "targetPrefix": "Hero", "impactEffect": "HitFx"})])
    sc.model("VolleyArrow", "arrow.glb", *far, 1.0, rx=-90, static=False, Visible=False, Cast_Shadows=False)
    sc.shape("Grenade", "SPHERE", *far, 0.35, 0.35, 0.35, "#3A3F4A", static=False, Visible=False, Cast_Shadows=False, Emissive="#FF8040", Emissive_Intensity=0.4)
    sc.shape("Blast", "SPHERE", *far, 2.2, 2.2, 2.2, "#FFB040", static=False, Unlit=True, Emissive="#FF9020", Emissive_Intensity=1.5, Opacity=0.7, Cast_Shadows=False, Visible=False)
    sc.model("TurretT", "turret.glb", *far, 1.2, static=False, body="STATIC", collider="BOX", Visible=False,
             behaviors=[("Turret3D", {"targets": "Slime,Drone,Boss", "range": 16, "turnSpeed": 400, "fireAngle": 10}),
                        ("Shooter3D", {"autoFire": False, "aimWithCamera": False, "fireRate": 5, "mode": "HITSCAN", "damage": 7, "range": 18, "spread": 1.5,
                                       "muzzleDistance": 0.6, "muzzleHeight": 0.5, "impactEffect": "HitFx", "useKeyboard": False, "recoil": 0, "shootSound": "/shoot.wav"})])
    # enemies
    slime_b = lambda hp, dmg, speed, color: [
        ("Chase3D", {"targetNames": "Hero", "sightRange": 40, "fieldOfView": 360, "needLineOfSight": False, "hearRange": 40, "searchTime": 60, "chaseSpeed": speed,
                     "stopDistance": 1.0, "returnHome": False, "attackRange": 1.4, "attackInterval": 1.1, "damage": dmg, "attackSound": "/squish.wav",
                     "runClip": "", "attackClip": "", "idleClip": "", "walkClip": ""}),
        ("Health3D", {"maxHealth": hp, "invincibleTime": 0.15, "onDeath": "DESTROY", "removeAfter": 0, "deathSound": "/squish.wav", "knockback": 4, "hurtShake": 0})]
    sc.shape("Slime", "SPHERE", *far, 1.0, 0.8, 1.0, "#5BD16A", static=False, body="KINEMATIC", Visible=False, Roughness=0.3, Shininess=40,
             Collider="SPHERE", behaviors=slime_b(70, 8, 2.6, None))
    sc.shape("SlimeBlue", "SPHERE", *far, 1.2, 0.95, 1.2, "#4AA8F0", static=False, body="KINEMATIC", Visible=False, Roughness=0.3, Shininess=40,
             Collider="SPHERE", behaviors=slime_b(170, 11, 2.8, None))
    sc.shape("SlimeBig", "SPHERE", *far, 2.0, 1.6, 2.0, "#A45BE0", static=False, body="KINEMATIC", Visible=False, Roughness=0.3, Shininess=40,
             Collider="SPHERE", behaviors=slime_b(360, 16, 2.0, None))
    sc.model("Drone", "drone.glb", *far, 1.0, static=False, body="KINEMATIC", collider="BOX", Visible=False,
             behaviors=[("Chase3D", {"targetNames": "Hero", "sightRange": 45, "fieldOfView": 360, "needLineOfSight": False, "hearRange": 45, "searchTime": 60,
                                     "chaseSpeed": 3.2, "stopDistance": 7, "returnHome": False, "attackRange": 9, "attackInterval": 2, "damage": 0, "flyHeight": 2.8,
                                     "runClip": "", "attackClip": "", "idleClip": "", "walkClip": ""}),
                        ("Turret3D", {"targets": "Hero", "range": 11, "turnSpeed": 220, "fireAngle": 8}),
                        ("Shooter3D", {"autoFire": False, "aimWithCamera": False, "fireRate": 0.6, "mode": "PROJECTILE", "bulletTemplate": "Laser", "bulletSpeed": 22,
                                       "damage": 9, "spread": 2, "muzzleDistance": 0.8, "muzzleHeight": 0, "useKeyboard": False, "recoil": 0, "shootSound": "/laser.wav"}),
                        ("Health3D", {"maxHealth": 150, "invincibleTime": 0.1, "onDeath": "DESTROY", "removeAfter": 0, "deathSound": "/explode.wav", "hurtShake": 0})])
    # spawners of the waves (start with the blocks)
    def spawner(name, zone, temps, n, interval=0.45, alive=None):
        Z = ZONES[zone]
        sc.add("OBJ3D", name, [("Spawner3D", {"templates": temps, "interval": interval, "randomExtra": 0.25, "autoStart": False, "maxAlive": alive or n, "totalLimit": n,
                                              "areaRadius": round(Z["r"] * 0.55, 1), "useSpawnerRotation": False})],
               Mesh="EMPTY", x=Z["x"], y=gy(Z["x"], Z["z"]) + 0.6, z=Z["z"] + 2, Cast_Shadows=False)
    spawner("SpawnMeadow", 0, ["Slime", "Slime", "SlimeBlue"], 6)
    spawner("SpawnFalls", 1, ["Slime", "SlimeBlue"], 7)
    spawner("SpawnFallsBig", 1, ["SlimeBig"], 1, 1.5)
    spawner("SpawnDrones", 2, ["Drone"], 6, 1.4, 3)
    # the Blue Giant
    A = ZONES[3]
    sc.model("Boss", "boss.glb", A["x"], gy(A["x"], A["z"]), A["z"] + 3, 3.0, ry=180, static=False, body="KINEMATIC", collider="CAPSULE", Visible=False,
             Animation="idle", behaviors=[
                 ("Boss3D", {"targets": "Hero", "wakeDistance": 0, "walkSpeed": 2.3, "punchRange": 3.4, "punchDamage": 16, "waveDamage": 18, "waveCount": 3,
                             "waveSpeed": 8, "waveRadius": 16, "slamCooldown": 6.5, "leapDamage": 22, "leapCooldown": 9, "ringTemplate": "Ring", "landEffect": "DustFx",
                             "idleClip": "idle", "walkClip": "walk", "punchClip": "punch", "slamClip": "slam", "jumpClip": "jump", "landClip": "land",
                             "angryClip": "angry", "slamSound": "/slam.wav", "punchSound": "/hit.wav"}),
                 ("Health3D", {"maxHealth": 1600, "invincibleTime": 0.08, "healthBar": "BossBar", "healthText": "BossHp", "showMax": True, "onDeath": "PLAY_CLIP", "deathClip": "death", "removeAfter": 0,
                               "deathSound": "/slam.wav", "hurtShake": 0})])

    # ---------------------------------------------------------------- heroes (party), healer, guide arrow, camera
    sx, sz = 0.0, 3.5
    targets = "Slime,Drone,Boss"
    tpc = lambda: ("ThirdPersonController3D", {"joystick": "Joy", "jumpButton": "JumpBtn", "camera": "Cam", "cameraMode": "ORBIT_TOUCH", "cameraDistance": 6.5,
                                               "cameraHeight": 1.5, "cameraPitch": -16, "walkSpeed": 2.6, "runSpeed": 5.6, "jumpSpeed": 5.2, "radius": 0.35,
                                               "maxSlope": 42, "modelYawOffset": 0, "idleClip": "idle", "walkClip": "walk", "runClip": "run", "jumpClip": "jump",
                                               "useKeyboard": True, "turnSpeed": 720})
    health = lambda: ("Health3D", {"maxHealth": 120, "invincibleTime": 0.5, "healthBar": "HpBar", "healthText": "HpText", "showMax": True, "hurtShake": 0.5, "hurtClip": "hurt", "hurtSound": "/hurt.wav",
                                   "onDeath": "PLAY_CLIP", "deathClip": "death", "removeAfter": 0, "regenPerSecond": 1.5, "regenDelay": 5})
    dash = lambda dmg, back=False: ("Dash3D", {"dashButton": "DashBtn", "joystick": "Joy", "distance": 5, "duration": 0.24, "cooldown": 0.9, "invincible": True,
                                               "clip": "dodge" if back else "dash", "damage": dmg, "targets": targets, "backward": back, "sound": "/switch.wav"})
    hero_kw = dict(static=False, body="KINEMATIC", collider="CAPSULE", Animation="idle")
    sc.model("Hero_Warrior", "warrior.glb", sx, gy(sx, sz), sz, 1.75, ry=180, behaviors=[
        tpc(), health(),
        ("MeleeCombo3D", {"attackButton": "AttackBtn", "heavyButton": "SkillABtn", "targets": targets, "range": 2.4, "arc": 150, "comboClips": "attack1,attack2,attack3",
                          "comboDamage": "22,26,42", "hitTime": 0.42, "hitMoment": 0.45, "comboWindow": 0.7, "lunge": 2.6, "knockback": 4, "clipSpeed": 1.25,
                          "heavyClip": "heavy", "heavyDamage": 48, "heavyRadius": 3.4, "heavyTime": 0.95, "heavyCooldown": 4, "hitEffect": "HitFx",
                          "swingSound": "/slash.wav", "hitSound": "/hit.wav", "hitShake": 0.15, "autoFace": 5}),
        ("Shooter3D", {"fireButton": "SkillBBtn", "autoFire": False, "fireRate": 0.5, "mode": "PROJECTILE", "bulletTemplate": "SlashWave", "bulletSpeed": 20,
                       "damage": 38, "aimWithCamera": False, "spread": 0, "muzzleDistance": 0.9, "muzzleHeight": 0.9, "useKeyboard": False, "recoil": 0.1,
                       "shootSound": "/whirl.wav", "autoAimTargets": targets, "autoAimRange": 14}),
        dash(28)], **hero_kw)
    sc.model("Hero_Archer", "archer.glb", sx, gy(sx, sz), sz, 1.7, ry=180, Visible=False, behaviors=[
        tpc(), health(),
        ("Shooter3D", {"fireButton": "AttackBtn", "autoFire": True, "fireRate": 2.6, "mode": "PROJECTILE", "bulletTemplate": "ArrowShot", "bulletSpeed": 34,
                       "damage": 22, "aimWithCamera": False, "spread": 0.5, "muzzleDistance": 0.7, "muzzleHeight": 1.0, "useKeyboard": True, "recoil": 0.05,
                       "shootSound": "/arrow.wav", "autoAimTargets": targets, "autoAimRange": 22}),
        ("Barrage3D", {"button": "SkillABtn", "cooldown": 6, "targets": targets, "range": 22, "forwardDistance": 8, "areaRadius": 3.5, "template": "VolleyArrow",
                       "count": 16, "spread": 1.2, "height": 12, "fallSpeed": 28, "damage": 16, "impactRadius": 1.5, "impactEffect": "HitFx", "clip": "shoot",
                       "impactSound": "/arrow.wav"}),
        ("MeleeCombo3D", {"heavyButton": "SkillBBtn", "targets": targets, "comboClips": "", "heavyClip": "shoot", "heavyDamage": 30, "heavyRadius": 4.5, "heavyTime": 0.6,
                          "heavyCooldown": 5, "hitEffect": "HitFx", "swingSound": "/whirl.wav", "hitSound": "/hit.wav", "useKeyboard": False}),
        dash(0, True)], **hero_kw)
    sc.model("Hero_Gunner", "gunner.glb", sx, gy(sx, sz), sz, 1.75, ry=180, Visible=False, behaviors=[
        tpc(), health(),
        ("Shooter3D", {"fireButton": "AttackBtn", "autoFire": True, "fireRate": 6, "mode": "HITSCAN", "damage": 9, "range": 28, "aimWithCamera": False, "spread": 1.2,
                       "muzzleDistance": 0.6, "muzzleHeight": 1.0, "impactEffect": "HitFx", "useKeyboard": True, "recoil": 0.04, "shootSound": "/shoot.wav",
                       "autoAimTargets": targets, "autoAimRange": 24}),
        ("Summon3D", {"button": "SkillABtn", "template": "TurretT", "lifetime": 10, "cooldown": 14, "side": 1.4, "front": 0.8, "clip": "power", "effect": "DustFx",
                      "sound": "/barrier.wav", "useKeyboard": False}),
        ("Barrage3D", {"button": "SkillBBtn", "cooldown": 7, "targets": targets, "range": 20, "forwardDistance": 7, "areaRadius": 2.8, "template": "Grenade",
                       "count": 6, "spread": 0.8, "height": 9, "fallSpeed": 18, "damage": 30, "impactRadius": 2.4, "impactEffect": "Blast", "clip": "cast",
                       "impactSound": "/explode.wav", "useKeyboard": False}),
        dash(0)], **hero_kw)
    sc.model("Healer", "healer.glb", sx + 1.6, gy(sx + 1.6, sz + 1.6), sz + 1.6, 1.6, ry=180, static=False, Animation="idle", behaviors=[
        ("Companion3D", {"leader": "Hero_Warrior", "followDistance": 2.2, "sideOffset": 1.3, "walkSpeed": 2.6, "runSpeed": 6.2, "teleportDistance": 22,
                         "healBelow": 0.65, "healAmount": 30, "healCooldown": 7, "healClip": "heal", "healEffect": "HealFx", "healSound": "/heal.wav",
                         "idleClip": "idle", "walkClip": "walk", "runClip": "run"})])
    sc.add("OBJ3D", "Party", [("CharacterSwitch3D", {"characters": ["Hero_Warrior", "Hero_Archer", "Hero_Gunner"], "cooldown": 0.6,
                                                     "switchEffect": "HealFx", "autoSwitchOnDeath": 1.6})], Mesh="EMPTY", x=sx, y=gy(sx, sz) + 3, z=sz, Cast_Shadows=False)
    sc.model("Guide", "guide.glb", sx, gy(sx, sz) + 2.4, sz - 1.5, 0.75, static=False, Color="#5AD2FF", Unlit=True, Emissive="#5AD2FF", Emissive_Intensity=1.4,
             Cast_Shadows=False, Receive_Shadows=False, behaviors=[("Compass3D", {"anchor": "Hero_Warrior", "target": "MarkMeadow", "height": 0.25, "ahead": 1.4, "arriveDistance": 9,
                                                          "modelYawOffset": 0, "bob": 0.1})])
    sc.add("CAMERA3D", "Cam", x=sx, y=gy(sx, sz) + 3, z=sz - 6.5, Rotation_Y=180, Active=True, FOV=62, Near=0.1, Far=320)

    # ---------------------------------------------------------------- coins and gems (counters of the HUD)
    def lerp(a, b, t):
        return a + (b - a) * t
    pts = []
    start = (sx, sz)
    chain = [start] + [(Z["x"], Z["z"]) for Z in ZONES]
    for i in range(len(chain) - 1):
        (x0, z0), (x1, z1) = chain[i], chain[i + 1]
        for t in (0.3, 0.6):
            pts.append((lerp(x0, x1, t) + (1.6 if t < 0.5 else -1.6), lerp(z0, z1, t), "coin"))
    for Z in ZONES[:3]:
        for k in range(4):
            a = k * math.pi / 2 + 0.7
            pts.append((Z["x"] + math.cos(a) * Z["r"] * 0.62, Z["z"] + math.sin(a) * Z["r"] * 0.62, "coin" if k % 2 == 0 else "gem"))
    pts.append((ZONES[3]["x"] + 6, ZONES[3]["z"] - 6, "gem"))
    if True:
        for i, (x, z, kind) in enumerate(pts):
            pick = ("Pickup3D", {"itemId": kind, "count": 1, "playerNames": "Hero", "range": 1.7, "tapObject": False, "sound": "/coin.wav", "flyToPlayer": True})
            spin = ("Rotator3D", {"speedY": 140, "localAxes": False})
            if kind == "coin":
                sc.shape("Coin%d" % i, "CYLINDER", x, gy(x, z) + 0.95, z, 0.6, 0.08, 0.6, "#FFC928", rx=90, static=False, Unlit=True, Emissive="#FFB000",
                         Emissive_Intensity=0.9, Cast_Shadows=False, Receive_Shadows=False, behaviors=[pick, spin])
            else:
                sc.shape("Gem%d" % i, "PYRAMID", x, gy(x, z) + 1.0, z, 0.5, 0.7, 0.5, "#4FC8FF", static=False, Unlit=True, Emissive="#4FC8FF",
                         Emissive_Intensity=1.0, Cast_Shadows=False, Receive_Shadows=False, behaviors=[pick, spin])

    # ---------------------------------------------------------------- 2D layer (the look of the original game)
    AR = "/files/ar.s2df"
    BIG = "/files/ar_big.s2df"
    GOLDC = "#FFD36A"
    ALL_TEXT = []

    def T(name, x, y, w, h, text, scale=0.5, color="#FFFFFF", align="CENTER", visible=True, z=110, font=AR):
        e = sc.text(name, x, y, w, h, text, scale, color, visible, z)
        e["Auto Size"] = "false"
        e["Text Align"] = align
        e["font"] = font
        return e

    def IMG(name, x, y, w, h, image, z=100, visible=True):
        return sc.add("BOX", name, x=x, y=y, width=w, height=h, z=z, Visible=visible, P={"image": "/" + image, "Collider Width": w, "Collider Height": h})

    def BTN(name, x, y, w, h, image, z=100, visible=True, pressed=False):
        return sc.button(name, x, y, w, h, image, pressed=pressed, visible=visible, z=z)

    def skill(name, label, x, y, size, image, text, scale):
        BTN(name, x, y, size, size, image, z=100, pressed=True)
        T(label, x + 4, y + size * 0.1, size - 8, 26, text, scale, z=112)

    with sc.grp("Hud", ui=True):
        sc.add("JOYSTICK", "Joy", x=40, y=34, width=250, height=250, z=120, P={"Pad Image": "/joy_pad.png", "Button Image": "/joy_knob.png"})
        skill("AttackBtn", "AtkLbl", 1100, 34, 150, "btn_attack.png", NAMES_SKILL["warrior"][0], 0.52)
        skill("SkillABtn", "ALbl", 972, 50, 104, "btn_skill_a.png", NAMES_SKILL["warrior"][1], 0.42)
        skill("SkillBBtn", "BLbl", 1124, 204, 104, "btn_skill_b.png", NAMES_SKILL["warrior"][2], 0.42)
        skill("DashBtn", "DashLbl", 1000, 168, 92, "btn_dash.png", "اندفاع", 0.38)
        skill("JumpBtn", "JumpLbl", 858, 40, 92, "btn_jump.png", "قفز", 0.38)
        # hero panel: face, name + level, health / xp / ultimate bars
        IMG("HudBack", 14, 622, 252, 84, "panel_hero.png", 100)
        for k, n in (("warrior", "FaceWarrior"), ("archer", "FaceArcher"), ("gunner", "FaceGunner")):
            IMG(n, 24, 632, 64, 64, "face_%s.png" % k, 105, visible=(k == "warrior"))
        T("HeroName", 96, 676, 96, 26, NAMES["warrior"], 0.5, align="LEFT")
        T("HeroLv", 190, 676, 68, 26, "Lv. 1", 0.5, GOLDC, align="LEFT")
        sc.add("PROGRESS", "HpBar", x=96, y=655, width=160, height=16, z=106, P={"Max": 100, "Progress": 100, "Background Color": "#3A1824", "Progress Color": "#FF5A5F"})
        T("HpText", 96, 654, 160, 18, "120 / 120", 0.3, align="CENTER", z=112)
        sc.add("PROGRESS", "XpBar", x=96, y=647, width=160, height=4, z=106, P={"Max": 100, "Progress": 0, "Background Color": "#26324A", "Progress Color": "#5AD2FF"})
        sc.add("PROGRESS", "UltBar", x=96, y=638, width=160, height=4, z=106, P={"Max": 100, "Progress": 0, "Background Color": "#26324A", "Progress Color": GOLDC})
        # quest
        IMG("QuestBack", 14, 530, 252, 84, "panel_quest.png", 100)
        IMG("QuestIcon", 232, 580, 28, 28, "icon_quest.png", 105)
        T("QuestHead", 86, 580, 142, 28, "مهمة رئيسية", 0.4, GOLDC, align="RIGHT")
        T("QuestCount", 24, 580, 62, 28, "", 0.4, GOLDC, align="LEFT")
        T("Objective", 24, 536, 232, 44, "اتبع السهم الأزرق إلى المرج الأخضر", 0.38, "#EAF1FF", align="RIGHT")
        # counters, pause, minimap
        IMG("CoinChip", 1008, 664, 98, 42, "chip_coin.png", 100)
        T("CoinTxt", 1044, 664, 56, 42, "0", 0.46, z=110, align="CENTER")
        IMG("GemChip", 1112, 664, 98, 42, "chip_gem.png", 100)
        T("GemTxt", 1148, 664, 56, 42, "0", 0.46, z=110, align="CENTER")
        sc.button("PauseBtn", 1218, 664, 46, 46, "btn_pause.png", pressed=True)
        mm = IMG("MapBack", 1142, 526, 128, 128, "map_back.png", 100)
        mm["behaviors"] = json.dumps([{"type": "Minimap3D", "enabled": True, "params": {
            "targetNames": "Hero", "terrain": "Ground", "span": 56, "rotateWithCamera": True, "inset": 6, "showTrees": True,
            "markerNames": "Mark", "markerColor": GOLDC, "enemyNames": "Slime,Drone,Boss", "enemyColor": "#FF4B5C", "arrowColor": "#2A6BFF"}}], ensure_ascii=False)
        IMG("MapFrame", 1142, 526, 128, 128, "map_frame.png", 108)
        # party buttons (the active one gets the gold ring)
        for i, k in enumerate(("warrior", "archer", "gunner")):
            BTN("Party" + k.capitalize(), 1206, 470 - i * 64, 56, 56, "pface_%s%s.png" % (k, "_on" if k == "warrior" else ""), z=100)
        # boss bar (shown in the arena)
        IMG("BossFrame", 420, 30, 440, 34, "boss_frame.png", 100, visible=False)
        sc.add("PROGRESS", "BossBar", x=428, y=36, width=424, height=22, z=104, Visible=False,
               P={"Max": 100, "Progress": 100, "Background Color": "#2A0E18", "Progress Color": "#FF4B3A"})
        T("BossHp", 428, 34, 424, 26, "", 0.3, z=112, visible=False)
        IMG("BossChip", 545, 66, 190, 34, "chip_white.png", 104, visible=False)
        T("BossName", 545, 66, 190, 34, "العملاق الأزرق", 0.46, "#1A2238", z=112, visible=False)
        # zone banner
        IMG("BannerBack", 450, 566, 380, 84, "banner.png", 100, visible=False)
        T("BannerSmall", 450, 614, 380, 28, "منطقة جديدة", 0.34, GOLDC, z=112, visible=False)
        T("BannerName", 450, 576, 380, 42, "", 0.6, z=112, visible=False)
    with sc.grp("DialogUi", ui=True):
        IMG("DlgPanel", 290, 16, 700, 150, "panel_dialog.png", 200, visible=False)
        for k, x in (("warrior", 20), ("gunner", 20), ("archer", 962), ("healer", 962)):
            IMG("Bust_" + k, x, 0, 290, 318, "bust_%s.png" % k, 210, visible=False)
        IMG("DlgPill", 826, 118, 150, 40, "pill_name.png", 215, visible=False)
        T("DlgName", 826, 118, 150, 40, "", 0.5, z=220, visible=False)
        T("DlgText", 306, 46, 668, 70, "", 0.48, "#FFFFFF", align="RIGHT", z=220, visible=False)
        T("DlgHint", 306, 22, 200, 22, "انقر للمتابعة", 0.28, "#B8C8E8", align="LEFT", z=220, visible=False)
        IMG("DlgSkip", 580, 664, 118, 40, "pill_skip.png", 225, visible=False)
        IMG("DlgSkipIcon", 668, 672, 24, 24, "icon_skip.png", 226, visible=False)
        T("DlgSkipText", 590, 664, 72, 40, "تخطي", 0.44, z=226, visible=False)
    with sc.grp("EndUi", ui=True):
        IMG("EndShade", 0, 0, 1280, 720, "shade.png", 296, visible=False)
        IMG("EndPanel", 360, 180, 560, 360, "panel_center.png", 300, visible=False)
        T("EndTitle", 360, 420, 560, 90, "", 0.8, GOLDC, z=310, visible=False, font=BIG)
        T("EndText", 380, 340, 520, 60, "", 0.5, z=310, visible=False)
        BTN("RestartBtn", 490, 230, 300, 62, "btn_gold_s.png", z=305, visible=False)
        T("RestartText", 490, 230, 300, 62, "العب مجددًا", 0.55, "#4A2800", z=310, visible=False)
    with sc.grp("PauseUi", ui=True):
        IMG("PauseShade", 0, 0, 1280, 720, "shade.png", 400, visible=False)
        IMG("PausePanel", 360, 180, 560, 360, "panel_center.png", 405, visible=False)
        T("PauseTitle", 360, 460, 560, 60, "إيقاف مؤقت", 0.7, GOLDC, z=410, visible=False, font=BIG)
        BTN("ResumeBtn", 490, 380, 300, 62, "btn_gold_s.png", z=406, visible=False)
        T("ResumeTxt", 490, 380, 300, 62, "متابعة", 0.55, "#4A2800", z=410, visible=False)
        BTN("PauseSetBtn", 490, 304, 300, 62, "btn_glass_s.png", z=406, visible=False)
        T("PauseSetTxt", 490, 304, 300, 62, "الإعدادات", 0.5, z=410, visible=False)
        BTN("PauseMenuBtn", 490, 228, 300, 62, "btn_glass_s.png", z=406, visible=False)
        T("PauseMenuTxt", 490, 228, 300, 62, "القائمة الرئيسية", 0.5, z=410, visible=False)
        IMG("HelpPanel", 360, 180, 560, 360, "panel_center.png", 405, visible=False)
        T("HelpTitle", 360, 470, 560, 50, "طريقة اللعب", 0.6, GOLDC, z=410, visible=False, font=BIG)
        T("HelpText", 392, 270, 496, 190, HELP_TEXT, 0.4, align="RIGHT", z=410, visible=False)
        BTN("HelpCloseBtn", 490, 196, 300, 62, "btn_gold_s.png", z=406, visible=False)
        T("HelpCloseTxt", 490, 196, 300, 62, "حسنًا", 0.55, "#4A2800", z=410, visible=False)
    with sc.grp("MenuUi", ui=True):
        sc.add("BOX", "MenuBlock", x=0, y=0, width=1280, height=720, z=380, P={"image": "/shade_menu.png", "Collider Width": 1280, "Collider Height": 720, "Tint": "#FFFFFF"})
        IMG("MenuSide", 0, 0, 600, 720, "menu_side.png", 385)
        IMG("MenuIcon", 202, 604, 96, 96, "icon_castle.png", 390)
        T("MenuTitle", 40, 470, 420, 130, "مغامرات الأفق", 1.0, "#D8E8FF", z=392, font=BIG)
        T("MenuSub", 30, 436, 440, 34, "انطلق .. واكتشف عالمًا مليئًا بالمغامرات", 0.34, "#EAF1FF", z=392)
        BTN("PlayBtn", 52, 340, 442, 78, "btn_gold.png", z=390)
        T("PlayTxt", 52, 340, 442, 78, "ابدأ اللعب", 0.66, "#4A2800", z=395)
        BTN("SetBtn", 52, 258, 442, 66, "btn_glass.png", z=390)
        T("SetTxt", 52, 258, 442, 66, "الإعدادات", 0.5, z=395)
        BTN("HelpBtn", 52, 180, 442, 66, "btn_glass.png", z=390)
        T("HelpTxt", 52, 180, 442, 66, "طريقة اللعب", 0.5, z=395)
    # the conversation behavior lives on the dialog panel
    dlg = next(e for e in sc.els if e["name"] == "DlgPanel")
    dlg["behaviors"] = json.dumps([{"type": "Dialogue", "enabled": True, "params": {
        "panel": "DlgPanel", "nameText": "DlgName", "bodyText": "DlgText", "portraits": ["Bust_warrior", "Bust_gunner", "Bust_archer", "Bust_healer"],
        "skipButton": "DlgSkip", "extras": ["DlgPill", "DlgHint", "DlgSkipIcon", "DlgSkipText"], "script": story_script(), "speed": 40, "autoNext": 3.2,
        "startSection": ""}}], ensure_ascii=False)

    # ---------------------------------------------------------------- events (blocks)
    proj = PROJ
    G.write_variables(proj, [("stage", "int", "0")])
    eq = lambda a, b: R("___ == ___", a, b)
    and_ = lambda a, b: R("___ and ___", a, b)
    made = lambda s: R("copies made ___", s)
    alive = lambda s: R("copies alive ___", s)
    cur = R("current character ___", "Party")

    BOSS_UI = ["BossFrame", "BossBar", "BossHp", "BossChip", "BossName"]
    MENU_UI = ["MenuBlock", "MenuSide", "MenuIcon", "MenuTitle", "MenuSub", "PlayBtn", "PlayTxt", "SetBtn", "SetTxt", "HelpBtn", "HelpTxt"]
    PAUSE_UI = ["PauseShade", "PausePanel", "PauseTitle", "ResumeBtn", "ResumeTxt", "PauseSetBtn", "PauseSetTxt", "PauseMenuBtn", "PauseMenuTxt"]
    HELP_UI = ["PauseShade", "HelpPanel", "HelpTitle", "HelpText", "HelpCloseBtn", "HelpCloseTxt"]
    END_UI = ["EndShade", "EndPanel", "EndTitle", "EndText", "RestartBtn", "RestartText"]
    vis = lambda names, on: [B("setVisibility", n, "true" if on else "false") for n in names]
    J = lambda r: r.code()
    num = lambda n: R("current character number ___", "Party")

    def objective(text):
        return B("setText", "Objective", q(text))

    def quest_count(txt):
        return B("setText", "QuestCount", q(txt))

    def banner(i):
        return vis(["BannerBack", "BannerSmall", "BannerName"], True) + [B("setText", "BannerName", q(ZONES[i]["name"])), B("waitSeconds", "2.8", yes=vis(["BannerBack", "BannerSmall", "BannerName"], False))]

    def open_barrier(i):
        return [B("setVisible3D", "Barrier%d" % i, "false"), B("setPhysics3D", "Barrier%d" % i, "false"), B("playSound3D", "/barrier.wav", "1"),
                B("cameraShake3D", "0.2")]

    def go_to(k, label):
        return [B("Compass3D.setTarget", "Guide", "Mark" + k), B("setVisible3D", "Beacon" + k, "true"), objective("اتبع السهم الأزرق إلى %s" % label), quest_count("")]

    def arrive(k, i, stage_now, section, starts, text, total):
        return [B("ifThen", eq("stage", stage_now), yes=[
            B("setVariable", "stage", str(stage_now + 1)), B("Compass3D.setTarget", "Guide", "null"), B("setVisible3D", "Beacon" + k, "false"),
            B("Dialogue.start", "DlgPanel", q(section))] + banner(i) + [B("Spawner3D.start", s) for s in starts] + [objective(text), quest_count("0 / %d" % total)])]

    G.write_event(proj, "private/ZoneMeadow/behaviors/Trigger3D/onEnter", arrive("Meadow", 0, 0, "meadow", ["SpawnMeadow"], "اهزم السلايمات في المرج الأخضر", 6))
    G.write_event(proj, "private/ZoneFalls/behaviors/Trigger3D/onEnter", arrive("Falls", 1, 2, "falls", ["SpawnFalls", "SpawnFallsBig"], "اهزم السلايمات حول البحيرة", 8))
    G.write_event(proj, "private/ZoneAutumn/behaviors/Trigger3D/onEnter", arrive("Autumn", 2, 4, "autumn", ["SpawnDrones"], "دمّر الطائرات المسيّرة", 6))
    G.write_event(proj, "private/ZoneArena/behaviors/Trigger3D/onEnter", [B("ifThen", eq("stage", 6), yes=[
        B("setVariable", "stage", "7"), B("Compass3D.setTarget", "Guide", "null"), B("setVisible3D", "BeaconArena", "false"),
        B("Dialogue.start", "DlgPanel", q("boss")), B("Boss3D.dropIn", "Boss", "24")] + vis(BOSS_UI, True) + banner(3) + [
        objective("اهزم العملاق الأزرق! اقفز فوق الموجات الأرضية"), quest_count("")])])
    done = lambda s, n: and_(R("___ >= ___", made(s), str(n)), eq(alive(s), "0"))
    kills = lambda s: "(" + J(made(s)) + " - " + J(alive(s)) + ")"
    G.write_event(proj, "public/OnStep", [
        B("setText", "CoinTxt", '"" + ' + J(R("count of item ___", q("coin")))),
        B("setText", "GemTxt", '"" + ' + J(R("count of item ___", q("gem")))),
        B("ifThen", eq("stage", 1), yes=[B("setText", "QuestCount", kills("SpawnMeadow") + ' + " / 6"')]),
        B("ifThen", eq("stage", 3), yes=[B("setText", "QuestCount", "(" + kills("SpawnFalls") + " + " + kills("SpawnFallsBig") + ') + " / 8"')]),
        B("ifThen", eq("stage", 5), yes=[B("setText", "QuestCount", kills("SpawnDrones") + ' + " / 6"')]),
        B("ifThen", and_(eq("stage", 1), done("SpawnMeadow", 6)), yes=[
            B("setVariable", "stage", "2")] + open_barrier(1) + [B("Dialogue.start", "DlgPanel", q("meadowDone"))] + go_to("Falls", "بحيرة الشلال")),
        B("ifThen", and_(eq("stage", 3), and_(done("SpawnFalls", 7), done("SpawnFallsBig", 1))), yes=[
            B("setVariable", "stage", "4")] + open_barrier(2) + [B("Dialogue.start", "DlgPanel", q("fallsDone"))] + go_to("Autumn", "الغابة الخريفية")),
        B("ifThen", and_(eq("stage", 5), done("SpawnDrones", 6)), yes=[
            B("setVariable", "stage", "6")] + open_barrier(3) + [B("Dialogue.start", "DlgPanel", q("autumnDone"))] + go_to("Arena", "ساحة العملاق")),
    ])
    G.write_event(proj, "private/Boss/behaviors/Health3D/onDeath", [
        B("setVariable", "stage", "8")] + vis(BOSS_UI, False) + [B("playSound3D", "/victory.wav", "1"),
        B("Dialogue.start", "DlgPanel", q("victory")), objective("انتصرت على العملاق الأزرق! استكشف العالم بحرية"), quest_count(""),
        B("waitSeconds", "14", yes=vis(END_UI, True) + [B("setText", "EndTitle", q("انتصار!")), B("setText", "EndText", q("أنقذتم الوادي من العملاق الأزرق"))])])

    def face_images():
        out = []
        for k, idx in (("warrior", 1), ("archer", 2), ("gunner", 3)):
            on = eq(num(0), str(idx))
            out.append(B("setVisibility", "Face" + k.capitalize(), on))
            out.append(B("if", on, yes=[B("setImage", "Party" + k.capitalize(), "/pface_%s_on.png" % k)], no=[B("setImage", "Party" + k.capitalize(), "/pface_%s.png" % k)]))
        return out

    def labels():
        out = []
        for k, idx in (("warrior", 1), ("archer", 2), ("gunner", 3)):
            a, b_, c = NAMES_SKILL[k]
            out.append(B("ifThen", eq(num(0), str(idx)), yes=[B("setText", "HeroName", q(NAMES[k])), B("setText", "AtkLbl", q(a)), B("setText", "ALbl", q(b_)), B("setText", "BLbl", q(c))]))
        return out

    G.write_event(proj, "private/Party/behaviors/CharacterSwitch3D/onSwitch", [
        B("Companion3D.setLeader", "Healer", cur), B("Compass3D.setAnchor", "Guide", cur), B("playSound3D", "/switch.wav", "0.8"),
        B("setProgress", "HpBar", R("health percent ___", cur)),
        B("setText", "HpText", '"" + (int) Math.ceil(' + J(R("health ___", cur)) + ') + " / 120"')] + face_images() + labels())
    G.write_event(proj, "private/Party/behaviors/CharacterSwitch3D/onPartyDefeated", vis(END_UI, True) + [
        B("setText", "EndTitle", q("هُزم الفريق")), B("setText", "EndText", q("حاولوا مرة أخرى، الوادي ينتظركم"))])
    for k in ("warrior", "archer", "gunner"):
        G.write_event(proj, "private/Party%s/onClick" % k.capitalize(), [B("CharacterSwitch3D.switchTo", "Party", "Hero_" + k.capitalize())])
    G.write_event(proj, "private/RestartBtn/onClick", [B("OpenScene", "scene1")])
    # main menu, pause and help
    G.write_event(proj, "public/OnCreate", [B("Pause")])
    G.write_event(proj, "private/PlayBtn/onClick", vis(MENU_UI, False) + [B("Resume"), B("Dialogue.start", "DlgPanel", q("intro"))])
    G.write_event(proj, "private/SetBtn/onClick", [B("showGraphicsMenu3D", "true")])
    G.write_event(proj, "private/HelpBtn/onClick", vis(HELP_UI, True))
    G.write_event(proj, "private/HelpCloseBtn/onClick", vis(HELP_UI, False))
    G.write_event(proj, "private/PauseBtn/onClick", [B("Pause")] + vis(PAUSE_UI, True))
    G.write_event(proj, "private/ResumeBtn/onClick", vis(PAUSE_UI, False) + [B("Resume")])
    G.write_event(proj, "private/PauseSetBtn/onClick", [B("showGraphicsMenu3D", "true")])
    G.write_event(proj, "private/PauseMenuBtn/onClick", [B("OpenScene", "scene1")])

    G.write_project(FOLDER, sc, {"Lighting Preset": "CUSTOM", "Render Mode": "SOFT", "Ambient Color": "#8C97A8", "Ambient Intensity": 1.0, "Sun": True,
                                 "Sun Color": "#FFF2DC", "Sun Intensity": 1.05, "Sun Pitch": -52, "Sun Yaw": 40, "Sky": "GRADIENT", "Sky Top": "#4F8FE6",
                                 "Sky Bottom": "#CFE6FA", "Fog": True, "Fog Color": "#CFE2F2", "Fog Near": 55, "Fog Far": 170, "Shadows": os.environ.get("ADV_SH", "1") == "1",
                                 "Shadow Distance": 35, "Quality": "MEDIUM"},
                    ("Adventure (Horizon Adventures): a 3D action adventure. Switch between the warrior, the archer and the gunner, the healer follows and heals. "
                     "Clear the meadow, the waterfall lake and the autumn wood, then beat the Blue Giant.",
                     "مغامرات الأفق: مغامرة أكشن ثلاثية الأبعاد. بدّل بين المحارب والرامية والمقاتل، والمعالجة تتبعك وتداويك. "
                     "طهّر المرج وبحيرة الشلال والغابة الخريفية ثم اهزم العملاق الأزرق."),
                    "# Adventure - credits\n\nCharacters, props and story voices: the Adventure-Game project (star4droid). Animations: Mesh2Motion (CC0), "
                    "baked into the models. Bow / arrow: Quaternius (CC0). Font: DejaVu Sans (free license). Sound effects and UI images: made for Star2D (CC0). "
                    "Terrain, trees, rocks and grass: Star2D built-ins.\n")
    return PROJ


def main():
    p = build()
    print("generated", p)
    ok = G.validate()
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
