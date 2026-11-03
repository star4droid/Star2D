#!/usr/bin/env python3
"""(Re)generates the four 3D example projects that ship inside the app and checks them.

    python3 tools/gen_3d_examples.py            generate + validate
    python3 tools/gen_3d_examples.py --check    validate only (the projects already in assets)

Output: app/src/main/assets/files/examples/<Folder>/ for City3D, HorrorHouse3D, EndlessRoad3D, SeaDock3D
(configs/scene1.json, scenes/scene1, Events/..., models/, sounds/, images/, CREDITS.md) plus <Folder>.txt / <Folder>.ar.txt.
The editor generates and compiles the Java on the first Play (no java/ or dex/ folders needed).

Assets come from ASSETS3D (default /home/claude/assets3d, license-checked, see its CREDITS.md). Kenney car models face +Z while the
engine's vehicles (Car3D, EndlessRoad3D traffic) drive along local -Z, so the car GLBs are re-packed with a 180 degree turn baked into
their root nodes (geometry untouched); the endless road tile is turned 90 degrees (runs along Z) and the door 90 degrees (panel along X,
hinge at min X, what Door3D HINGE expects).

Validation (no phone): every element has all default keys of its type, unique Java names, behavior parameters exist in the behavior
class (@Param fields) with values of the right type, element references resolve to elements of the right type, every model / sound /
image file exists in the project (and no unused file is shipped), .visual blocks export exactly the .java next to them and use real
block definitions (Blocks3D, nodes.java DSL, BlockCatalog, behavior blocks), and the Java the editor would generate for the scene
compiles (javac against the template classes, tools/examples3d_test/Examples3DCheck.java emulates Helpers/CodeGenerator).
Needs the template classes (bash gradlew :template:compileReleaseJavaWithJavac) and the gradle cache (gdx, gson, bullet, gltf).
"""
import glob, io, json, math, os, re, shutil, struct, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXAMPLES = os.path.join(ROOT, "app/src/main/assets/files/examples")
ITEMS = os.path.join(ROOT, "app/src/main/assets/items")
LIB = os.path.join(ROOT, "app/src/main/java/com/star4droid/template/behaviors/lib")
ASSETS = os.environ.get("ASSETS3D", "/home/claude/assets3d")
I18N_OUT = os.environ.get("I18N_STAGING_EXAMPLES", "/home/claude/i18n_staging/examples3d.json")
SCENE = "scene1"
W, H = 1280, 720  # landscape logic frame of the 2D layer
MAX_BYTES = int(2.5 * 1024 * 1024)

# ====================================================================== element defaults

TYPE_FILE = {"OBJ3D": "obj3d", "LIGHT3D": "light3d", "CAMERA3D": "camera3d", "CAMERAVIEW": "cameraview", "BOX": "box", "TEXT": "text",
             "JOYSTICK": "joystick", "PROGRESS": "progress", "UIHOLDER": "uiholder"}


def defaults(tp):
    base = open(os.path.join(ITEMS, "default.json"), encoding="utf-8").read()
    other = open(os.path.join(ITEMS, TYPE_FILE[tp] + ".json"), encoding="utf-8").read().strip()
    d = json.loads(base.replace("--other--", "," + other))
    return {k: (v if isinstance(v, str) else str(v)) for k, v in d.items()}


def num(v):
    """Number -> compact string ("1.5", "2", "-0.25")."""
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        r = round(float(v), 4)
        if r == int(r):
            return str(int(r))
        return ("%.4f" % r).rstrip("0").rstrip(".")
    return str(v)


class Scene:
    def __init__(self):
        self.els = []
        self.names = set()

    def add(self, tp, name, behaviors=None, **props):
        """props use the property names with '_' for spaces (Rotation_Y) or a dict in props['P'] with the real keys."""
        assert re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", name), name
        assert name not in self.names, "duplicate " + name
        self.names.add(name)
        e = defaults(tp)
        e["name"] = name
        e["Script"] = name
        if tp in ("BOX", "TEXT", "JOYSTICK", "PROGRESS", "UIHOLDER", "CAMERAVIEW"):
            e["type"] = "UI"
        real = props.pop("P", {})
        for k, v in props.items():
            real[k.replace("_", " ")] = v
        for k, v in real.items():
            assert k in e, "%s: unknown property '%s' for %s" % (name, k, tp)
            e[k] = num(v)
        if behaviors:
            e["behaviors"] = json.dumps([{"type": b[0], "params": b[1], "enabled": True} for b in behaviors], ensure_ascii=False)
        self.els.append(e)
        return e

    # ---- shortcuts
    def model(self, name, model, x, y, z, s=1.0, ry=0.0, rx=0.0, rz=0.0, static=True, body="NONE", collider="AUTO", behaviors=None, **kw):
        sx, sy, sz = (s, s, s) if not isinstance(s, (tuple, list)) else s
        return self.add("OBJ3D", name, behaviors, Mesh="MODEL", Model="/" + model, x=x, y=y, z=z, Rotation_X=rx, Rotation_Y=ry, Rotation_Z=rz,
                        Scale_X=sx, Scale_Y=sy, Scale_Z=sz, Static_Object=static, Body=body, Collider=collider, **kw)

    def shape(self, name, mesh, x, y, z, sx, sy, sz, color="#FFFFFF", ry=0.0, rx=0.0, rz=0.0, static=True, body="NONE", behaviors=None, **kw):
        return self.add("OBJ3D", name, behaviors, Mesh=mesh, x=x, y=y, z=z, Rotation_X=rx, Rotation_Y=ry, Rotation_Z=rz, Scale_X=sx,
                        Scale_Y=sy, Scale_Z=sz, Color=color, Static_Object=static, Body=body, **kw)

    def wall(self, name, x, y, z, sx, sy, sz, ry=0.0):
        """Invisible collider (EMPTY mesh = nothing drawn, a 1 m box scaled)."""
        return self.add("OBJ3D", name, None, Mesh="EMPTY", x=x, y=y, z=z, Scale_X=sx, Scale_Y=sy, Scale_Z=sz, Rotation_Y=ry, Body="STATIC",
                        Collider="BOX", Static_Object=True, Cast_Shadows=False)

    def button(self, name, x, y, w, h, image, pressed=True, visible=True, z=100):
        return self.add("BOX", name, image="/" + image, Pressed_Image=("/" + image.replace(".png", "_p.png")) if pressed else "", x=x, y=y,
                        width=w, height=h, Collider_Width=w, Collider_Height=h, z=z, Visible=visible)

    def text(self, name, x, y, w, h, text, scale=1.0, color="#FFFFFF", visible=True, z=110):
        return self.add("TEXT", name, x=x, y=y, width=w, height=h, Text=text, Font_Scale=scale, Text_Color=color, z=z, Visible=visible)


# ====================================================================== blocks (.visual + .java)
# Block definitions used here, copied from the real catalogs (checked against them by validate()):
#   kind "dsl" = assets/java/nodes.java, "cat" = ui/blocks/BlockCatalog.addExtras, "3d" = ui/blocks/Blocks3D, "bhv" = behavior blocks.

def argb(hexs):
    v = int(hexs.lstrip("#"), 16) | 0xFF000000
    return v - (1 << 32) if v >= (1 << 31) else v


BLOCKS = {
    # title: (source, fields, code, color, isIf)
    "if": ("dsl", "boolean(Boolean)", "if(%1$s){\n%2$s\n} else {\n%3$s\n}\n%4$s", argb("018253"), True),
    "OpenScene": ("dsl", "scene(scene)", "openScene(\"%1$s\");\n%2$s", argb("018253"), False),
    "loopSound": ("dsl", "Sound(sound) repeat?", "loopSound(\"%1$s\",%2$s);\n%3$s", argb("BE9333"), False),
    "playSound": ("dsl", "Sound(sound)", "startSound(\"%1$s\");\n%2$s", argb("BE9333"), False),
    "ifThen": ("cat", "boolean(Boolean)", "if(%1$s){\n%2$s\n}\n%4$s", argb("E6A100"), True),
    "waitSeconds": ("cat", "seconds", "Timer.schedule(new Timer.Task(){\n    public void run(){\n        %2$s\n    }\n},(float)(%1$s));\n%4$s", argb("E6A100"), True),
    "everySeconds": ("cat", "seconds", "Timer.schedule(new Timer.Task(){\n    public void run(){\n        %2$s\n    }\n},(float)(%1$s),(float)(%1$s));\n%4$s", argb("E6A100"), True),
    "setVariable": ("cat", "variable value", "%1$s = %2$s;\n%3$s", argb("E65100"), False),
    "changeVariable": ("cat", "variable by", "%1$s += %2$s;\n%3$s", argb("E65100"), False),
    "show": ("cat", "body(Body)", "%1$s.getActor().setVisible(true);\n%2$s", argb("7B1FA2"), False),
    "hide": ("cat", "body(Body)", "%1$s.getActor().setVisible(false);\n%2$s", argb("7B1FA2"), False),
    "showText": ("cat", "ui(Body) text", "%1$s.setItemText(String.valueOf(%2$s));\n%3$s", argb("7B1FA2"), False),
    "playSound3D": ("3d", "sound(sound) volume", "com.star4droid.template.d3.B3.playSound(\"%1$s\", (float)(%2$s));\n%3$s", argb("E53935"), False),
    "postEffect3D": ("3d", "effect(postEffect) amount", "com.star4droid.template.d3.B3.setPostEffect(\"%1$s\", (float)(%2$s));\n%3$s", argb("00ACC1"), False),
    "cameraShake3D": ("3d", "strength", "com.star4droid.template.d3.B3.shake(null, (float)(%1$s));\n%2$s", argb("3949AB"), False),
    "showGraphicsMenu3D": ("3d", "arabic(Boolean)", "com.star4droid.template.d3.GraphicsMenu3D.show(activeStage(), %1$s);\n%2$s", argb("546E7A"), False),
    "setZ3D": ("3d", "body(Body) z", "com.star4droid.template.d3.B3x.setZ(%1$s, (float)(%2$s));\n%3$s", argb("1E88E5"), False),
    "clearInventory": ("3d", "", "com.star4droid.template.d3.B3x.clearInventory();\n%1$s", argb("7CB342"), False),
}


def bhv_statement(fqn_simple, method, args_kinds):
    """Template of a behavior action block (BehaviorBlocks.Spec.statementCode)."""
    fqn = "com.star4droid.template.behaviors.lib." + fqn_simple
    parts = []
    for i, k in enumerate(args_kinds):
        s = "%%%d$s" % (i + 2)
        parts.append({"num": "(float)(" + s + ")", "bool": "(" + s + ")", "text": "String.valueOf(" + s + ")"}[k])
    call = method + "(" + ", ".join(parts) + ")"
    return ("{\n    " + fqn + " __bhv = (" + fqn + ") %1$s.getBehavior(\"" + fqn_simple + "\");\n    if (__bhv != null) __bhv." + call +
            ";\n}\n%" + str(len(args_kinds) + 2) + "$s")


def bhv_reporter(fqn_simple, method, default):
    fqn = "com.star4droid.template.behaviors.lib." + fqn_simple
    get = "((" + fqn + ") %1$s.getBehavior(\"" + fqn_simple + "\"))"
    return "(" + get + " != null ? " + get + "." + method + "() : " + default + ")"


BHV_BLOCKS = {
    "Car3D.setGas": ("body(Body) amount", bhv_statement("Car3D", "setGas", ["num"])),
    "Car3D.setHandbrake": ("body(Body) on(Boolean)", bhv_statement("Car3D", "setHandbrake", ["bool"])),
    "EndlessRoad3D.setPaused": ("body(Body) paused(Boolean)", bhv_statement("EndlessRoad3D", "setPaused", ["bool"])),
}
for _t, (_f, _c) in BHV_BLOCKS.items():
    BLOCKS[_t] = ("bhv", _f, _c, argb("607D8B"), False)

REPORTERS = {
    "join ___ ___": "(String.valueOf(%1$s) + String.valueOf(%2$s))",
    "round ___": "Math.round(%1$s)",
    "___ >= ___": "(%1$s >= %2$s)",
    "3D z of ___": "com.star4droid.template.d3.B3.z(%1$s)",
    "speed text ___": bhv_reporter("Car3D", "getSpeedText", "\"\""),
    "road score ___": bhv_reporter("EndlessRoad3D", "getScore", "0"),
    "distance driven ___": bhv_reporter("EndlessRoad3D", "getDistance", "0f"),
}


class R:
    """A value block inside a field: R("join ___ ___", "\\"Score \\"", R(...))."""

    def __init__(self, display, *inner):
        assert display in REPORTERS, display
        assert display.count("___") == len(inner), display
        self.display, self.inner = display, list(inner)

    def code(self):
        return java_format(REPORTERS[self.display], [v.code() if isinstance(v, R) else str(v) for v in self.inner])

    def json(self):
        return {"template": REPORTERS[self.display], "displayTemplate": self.display,
                "innerFields": [field_json("___", v) for v in self.inner]}


def field_json(name, v):
    o = {"name": name, "value": "" if isinstance(v, R) else str(v)}
    if isinstance(v, R):
        o["block"] = v.json()
    return o


class B:
    """One block: B("showText", "speedText", R(...)), containers: B("if", cond, yes=[...], no=[...])."""

    def __init__(self, title, *values, yes=None, no=None):
        assert title in BLOCKS, title
        self.title, self.values = title, list(values)
        src, fields, code, color, isif = BLOCKS[title]
        self.fields = fields.split(" ") if fields else []
        assert len(self.fields) == len(self.values), "%s needs %s" % (title, fields)
        self.code, self.color, self.isif = code, color, isif
        self.yes, self.no = yes or [], no or []


def export_chain(chain, indent=0):
    """Same algorithm as BlockModel.gen (4-space indentation, placeholders: fields, [true, false], next)."""
    if not chain:
        return ""
    node, rest = chain[0], chain[1:]
    ind = "    " * indent
    args = [v.code() if isinstance(v, R) else str(v) for v in node.values]
    if node.isif:
        args.append(export_chain(node.yes, indent + 1))
        args.append(export_chain(node.no, indent + 1))
    args.append(export_chain(rest, indent))
    formatted = java_format(node.code, args)
    return ind + formatted.replace("\n", "\n" + ind)


def java_format(tpl, args):
    def rep(m):
        if m.group(0) == "%%":
            return "%"
        return args[int(m.group(1)) - 1]
    return re.sub(r"%(\d+)\$s|%%", rep, tpl)


def visual_json(chain):
    """.visual file: root 'First >' + every node (ids depth first: yes, no, next) with string values like the editor."""
    nodes = []

    def add(n):
        nodes.append(n)
        return len(nodes) - 1

    root_id = add(None)

    def walk(ch):
        """returns id of first node of the chain"""
        if not ch:
            return None
        ids = []
        for n in ch:
            i = add(n)
            ids.append(i)
            n._yes = walk(n.yes) if n.isif else None
            n._no = walk(n.no) if n.isif else None
        for k, n in enumerate(ch):
            n._next = ids[k + 1] if k + 1 < len(ids) else None
        return ids[0]

    first = walk(chain)
    out = []
    y = 0.0
    for i, n in enumerate(nodes):
        if n is None:
            out.append({"id": "0", "title": "First >", "x": "40.0", "y": "0.0", "else": "false", "next": str(first) if first is not None else "null",
                        "next_id": "null", "else_id": "", "nf": "[]", "code": "%1$s", "color": argb("018253"), "close": "false"})
            continue
        y -= 233.33
        o = {"id": str(i), "title": n.title, "x": "40.0", "y": "%.2f" % y, "else": "true" if n.isif else "false"}
        if n.isif:
            o["next"] = str(n._yes) if n._yes is not None else "null"
            o["next_id"] = str(n._next) if n._next is not None else "null"
            o["else_id"] = str(n._no) if n._no is not None else ""
        else:
            o["next"] = str(n._next) if n._next is not None else "null"
            o["next_id"] = "null"
            o["else_id"] = ""
        o["nf"] = json.dumps([field_json(f, v) for f, v in zip(n.fields, n.values)], ensure_ascii=False)
        o["code"] = n.code
        o["color"] = n.color
        o["close"] = "true"
        out.append(o)
    return json.dumps(out, indent=2, ensure_ascii=False)


def write_event(proj, rel, chain):
    """rel: 'public/OnCreate' or 'private/<element>/onClick' or 'private/<element>/behaviors/<Type>/<event>'."""
    base = os.path.join(proj, "Events", SCENE, rel)
    os.makedirs(os.path.dirname(base), exist_ok=True)
    open(base + ".java", "w", encoding="utf-8").write(export_chain(chain))
    open(base + ".visual", "w", encoding="utf-8").write(visual_json(chain))


def write_variables(proj, variables):
    p = os.path.join(proj, "Events", SCENE, "public", "variables.json")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    out = [{"EQUAL": "=", "SEMICOLON": ";", "SPACE": " ", "accessModifer": "", "name": n, "type": t, "value": v} for n, t, v in variables]
    json.dump(out, open(p, "w", encoding="utf-8"))


def q(s):
    """A Java string literal as a block value."""
    return json.dumps(s, ensure_ascii=False)


# ====================================================================== assets

def glb_read(path):
    b = open(path, "rb").read()
    jl = struct.unpack("<I", b[12:16])[0]
    g = json.loads(b[20:20 + jl])
    return g, b[20 + jl:]


def glb_turn(src, dst, yaw_deg):
    """Copies a GLB with a turn around Y baked into its root nodes (nothing else changes)."""
    g, rest = glb_read(src)
    a = math.radians(yaw_deg)
    qy = (0.0, math.sin(a / 2), 0.0, math.cos(a / 2))

    def qmul(p, r):
        px, py, pz, pw = p
        rx, ry, rz, rw = r
        return (pw * rx + px * rw + py * rz - pz * ry, pw * ry - px * rz + py * rw + pz * rx, pw * rz + px * ry - py * rx + pz * rw,
                pw * rw - px * rx - py * ry - pz * rz)

    for i in g["scenes"][g.get("scene", 0)]["nodes"]:
        n = g["nodes"][i]
        assert "matrix" not in n, src
        t = n.get("translation", [0, 0, 0])
        c, s = math.cos(a), math.sin(a)
        n["translation"] = [t[0] * c + t[2] * s, t[1], -t[0] * s + t[2] * c]
        n["rotation"] = list(qmul(qy, tuple(n.get("rotation", [0, 0, 0, 1]))))
    js = json.dumps(g, separators=(",", ":")).encode("utf-8")
    js += b" " * ((4 - len(js) % 4) % 4)
    body = struct.pack("<I", len(js)) + b"JSON" + js + rest
    open(dst, "wb").write(b"glTF" + struct.pack("<II", 2, 12 + len(body)) + body)


def copy_asset(proj, kind, src_rel, name=None, turn=None):
    """kind: models | sounds | images. src_rel: path under ASSETS. turn: yaw baked into a GLB."""
    d = os.path.join(proj, kind)
    os.makedirs(d, exist_ok=True)
    dst = os.path.join(d, name or os.path.basename(src_rel))
    if turn:
        glb_turn(os.path.join(ASSETS, src_rel), dst, turn)
    else:
        shutil.copyfile(os.path.join(ASSETS, src_rel), dst)
    return os.path.basename(dst)


# ---------------------------------------------------------------------- UI images (PIL)

def _canvas(w, h, ss=4):
    from PIL import Image
    return Image.new("RGBA", (w * ss, h * ss), (0, 0, 0, 0)), ss


def _finish(img, w, h, path):
    from PIL import Image
    img.resize((w, h), Image.LANCZOS).save(path, optimize=True)


def make_button(path, w, h, icon, base=(20, 24, 32, 150), ring=(255, 255, 255, 220), accent=(255, 255, 255, 235), shape="circle"):
    """A round (or rounded-rect) translucent button with a white icon; also writes the pressed variant (_p)."""
    from PIL import ImageDraw
    for pressed in (False, True):
        img, ss = _canvas(w, h)
        d = ImageDraw.Draw(img)
        W2, H2 = w * ss, h * ss
        pad = 3 * ss
        fill = tuple(min(255, int(c * 1.6)) for c in base[:3]) + (min(255, base[3] + 50),) if pressed else base
        if shape == "circle":
            d.ellipse([pad, pad, W2 - pad, H2 - pad], fill=fill, outline=ring, width=4 * ss)
        else:
            d.rounded_rectangle([pad, pad, W2 - pad, H2 - pad], radius=min(W2, H2) // 4, fill=fill, outline=ring, width=4 * ss)
        icon(d, W2, H2, ss, accent)
        _finish(img, w, h, path if not pressed else path.replace(".png", "_p.png"))


def ic_arrow(direction):
    def f(d, w, h, ss, c):
        cx, cy, r = w / 2, h / 2, min(w, h) * 0.24
        if direction == "left":
            pts = [(cx - r, cy), (cx + r * 0.7, cy - r), (cx + r * 0.7, cy + r)]
        elif direction == "right":
            pts = [(cx + r, cy), (cx - r * 0.7, cy - r), (cx - r * 0.7, cy + r)]
        else:
            pts = [(cx, cy - r), (cx - r, cy + r * 0.7), (cx + r, cy + r * 0.7)]
        d.polygon(pts, fill=c)
    return f


def ic_pedal(label_color):
    def f(d, w, h, ss, c):
        for k in range(5):
            y = h * (0.22 + k * 0.13)
            d.rounded_rectangle([w * 0.2, y, w * 0.8, y + h * 0.06], radius=4 * ss, fill=(255, 255, 255, 170))
        d.rounded_rectangle([w * 0.3, h * 0.08, w * 0.7, h * 0.14], radius=3 * ss, fill=label_color)
    return f


def ic_use(d, w, h, ss, c):
    cx, cy, r = w / 2, h / 2, min(w, h) * 0.26
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=c, width=6 * ss)
    r2 = r * 0.38
    d.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], fill=c)


def ic_flash(d, w, h, ss, c):
    d.rounded_rectangle([w * 0.2, h * 0.43, w * 0.48, h * 0.57], radius=3 * ss, fill=c)
    d.polygon([(w * 0.48, h * 0.4), (w * 0.6, h * 0.34), (w * 0.6, h * 0.66), (w * 0.48, h * 0.6)], fill=c)
    for a in (-25, 0, 25):
        r = math.radians(a)
        d.line([(w * 0.66, h / 2 + math.sin(r) * h * 0.06), (w * 0.66 + math.cos(r) * w * 0.14, h / 2 + math.sin(r) * h * 0.2)], fill=c, width=4 * ss)


def ic_run(d, w, h, ss, c):
    for k in (0, 1):
        y = h * (0.3 + k * 0.2)
        d.line([(w * 0.32, y + h * 0.16), (w * 0.5, y), (w * 0.68, y + h * 0.16)], fill=c, width=7 * ss, joint="curve")


def ic_gear(d, w, h, ss, c):
    cx, cy = w / 2, h / 2
    ro, ri = min(w, h) * 0.3, min(w, h) * 0.22
    pts = []
    for k in range(32):
        a = math.pi * 2 * k / 32
        r = ro if (k // 2) % 2 == 0 else ri
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    d.polygon(pts, fill=c)
    r3 = min(w, h) * 0.1
    d.ellipse([cx - r3, cy - r3, cx + r3, cy + r3], fill=(30, 34, 44, 255))


def ic_seat(d, w, h, ss, c):
    d.line([(w * 0.36, h * 0.25), (w * 0.36, h * 0.58), (w * 0.66, h * 0.58), (w * 0.66, h * 0.76)], fill=c, width=7 * ss, joint="curve")
    d.line([(w * 0.36, h * 0.58), (w * 0.36, h * 0.76)], fill=c, width=7 * ss)


def ic_none(d, w, h, ss, c):
    pass


def make_joystick(proj):
    from PIL import ImageDraw
    img, ss = _canvas(256, 256)
    d = ImageDraw.Draw(img)
    d.ellipse([6 * ss, 6 * ss, 250 * ss, 250 * ss], fill=(20, 24, 32, 110), outline=(255, 255, 255, 170), width=5 * ss)
    for a in (0, 90, 180, 270):
        r = math.radians(a)
        cx, cy = 128 * ss + math.cos(r) * 100 * ss, 128 * ss + math.sin(r) * 100 * ss
        t = 10 * ss
        d.polygon([(cx + math.cos(r) * t, cy + math.sin(r) * t), (cx + math.cos(r + 2.2) * t, cy + math.sin(r + 2.2) * t),
                   (cx + math.cos(r - 2.2) * t, cy + math.sin(r - 2.2) * t)], fill=(255, 255, 255, 170))
    _finish(img, 256, 256, os.path.join(proj, "images/joy_pad.png"))
    img, ss = _canvas(120, 120)
    d = ImageDraw.Draw(img)
    d.ellipse([4 * ss, 4 * ss, 116 * ss, 116 * ss], fill=(235, 240, 250, 210), outline=(255, 255, 255, 255), width=3 * ss)
    _finish(img, 120, 120, os.path.join(proj, "images/joy_knob.png"))


def ui_images(proj, names):
    os.makedirs(os.path.join(proj, "images"), exist_ok=True)
    p = lambda n: os.path.join(proj, "images", n)
    spec = {
        "btn_left.png": (160, 160, ic_arrow("left"), {}),
        "btn_right.png": (160, 160, ic_arrow("right"), {}),
        "pedal_gas.png": (150, 220, ic_pedal((90, 220, 120, 255)), {"shape": "rect", "base": (40, 46, 56, 190)}),
        "pedal_brake.png": (130, 170, ic_pedal((235, 80, 70, 255)), {"shape": "rect", "base": (40, 46, 56, 190)}),
        "btn_use.png": (170, 170, ic_use, {"base": (30, 60, 40, 170)}),
        "btn_flash.png": (120, 120, ic_flash, {}),
        "btn_run.png": (120, 120, ic_run, {}),
        "btn_gear.png": (72, 72, ic_gear, {}),
        "btn_seat.png": (160, 160, ic_seat, {"base": (30, 50, 80, 170)}),
        "btn_wide.png": (240, 72, ic_none, {"shape": "rect", "base": (20, 24, 32, 170)}),
        "btn_wide_red.png": (360, 96, ic_none, {"shape": "rect", "base": (170, 40, 40, 220)}),
    }
    for n in names:
        if n == "joystick":
            make_joystick(proj)
            continue
        w, h, icon, kw = spec[n]
        make_button(p(n), w, h, icon, **kw)


# ====================================================================== project writer

def scene_config(settings):
    cfg = {"sceneColor": "#000000FF", "logicWidth": float(W), "logicHeight": float(H), "or": "landscape", "Ambient Light": "#FFFFFF", "sceneType": "3D"}
    base = {"Render Mode": "SIMPLE", "Lighting Preset": "DAY", "Toon Bands": "3", "Sky": "GRADIENT", "Sky Top": "#5C9EF2", "Sky Bottom": "#C7E0FA",
            "Sky Image": "", "Ambient Color": "#737882", "Ambient Intensity": "1", "Sun": "true", "Sun Color": "#FFF5E0", "Sun Intensity": "1",
            "Sun Pitch": "-55", "Sun Yaw": "35", "Shadows": "true", "Shadow Distance": "40", "Fog": "false", "Fog Color": "#C7DBF2", "Fog Near": "40",
            "Fog Far": "160", "Gravity X": "0", "Gravity Y": "-9.81", "Gravity Z": "0", "Quality": "MEDIUM", "Post Effect": "NONE"}
    for k, v in settings.items():
        assert k in base or k == "Exposure", k
        base[k] = num(v)
    cfg.update(base)
    return cfg


def write_project(folder, scene, settings, descs, credits):
    proj = os.path.join(EXAMPLES, folder)
    os.makedirs(os.path.join(proj, "configs"), exist_ok=True)
    os.makedirs(os.path.join(proj, "scenes"), exist_ok=True)
    json.dump(scene_config(settings), open(os.path.join(proj, "configs", SCENE + ".json"), "w"), indent=1)
    json.dump(scene.els, open(os.path.join(proj, "scenes", SCENE), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    open(os.path.join(EXAMPLES, folder + ".txt"), "w", encoding="utf-8").write(descs[0])
    open(os.path.join(EXAMPLES, folder + ".ar.txt"), "w", encoding="utf-8").write(descs[1])
    open(os.path.join(proj, "CREDITS.md"), "w", encoding="utf-8").write(credits)


def fresh(folder):
    proj = os.path.join(EXAMPLES, folder)
    if os.path.isdir(proj):
        shutil.rmtree(proj)
    os.makedirs(proj)
    return proj


CREDIT_ROWS = {
    "car": "| Car Kit 3.1 | Kenney | CC0 1.0 | https://opengameart.org/content/car-kit |",
    "roads": "| City Kit Roads 2.2 | Kenney | CC0 1.0 | https://opengameart.org/content/city-kit-roads |",
    "comm": "| City Kit Commercial 2.1 | Kenney | CC0 1.0 | https://opengameart.org/content/city-kit-commercial |",
    "sub": "| City Kit Suburban 2.0 | Kenney | CC0 1.0 | https://opengameart.org/content/city-kit-suburban |",
    "nature": "| Nature Kit 2.1 | Kenney | CC0 1.0 | https://opengameart.org/content/nature-kit |",
    "plat": "| Platformer Kit 1.1 | Kenney | CC0 1.0 | https://opengameart.org/content/platformer-kit |",
    "build": "| Building Kit 1.0 | Kenney | CC0 1.0 | https://opengameart.org/content/building-kit |",
    "grave": "| Graveyard Kit 5.0 | Kenney | CC0 1.0 | https://opengameart.org/content/graveyard-kit |",
    "furn": "| Furniture Pack 1.0 (OBJ converted to GLB) | Kenney | CC0 1.0 | https://opengameart.org/content/furniture-kit |",
    "pirate": "| Pirate Kit 2.1 | Kenney | CC0 1.0 | https://opengameart.org/content/pirate-kit |",
    "water": "| Watercraft Pack 2.0 | Kenney | CC0 1.0 | https://opengameart.org/content/watercraft-kit |",
    "racing": "| Starter Kit Racing (audio) | Kenney | CC0 1.0 | https://github.com/KenneyNL/Starter-Kit-Racing |",
    "flash": "| Low quality flashlight (OBJ converted to GLB) | neincenets | CC0 | https://opengameart.org/content/low-quality-flashlight |",
    "lock": "| Door Lock Sounds | Cough-E | CC0 | https://opengameart.org/content/door-lock-sounds |",
    "creak": "| Creaky door hinge (spooky), trimmed | w.forster.1 (OGA) | CC0 | https://opengameart.org/content/creaky-door-hinge-spooky |",
    "rpg": "| RPG Sound Pack | artisticdude | CC0 | https://opengameart.org/content/rpg-sound-pack |",
    "steps": "| Footsteps | GboxMikeFozzy | CC0 | https://opengameart.org/content/footsteps-0 |",
    "horror": "| Ambient horror | techiew | CC0 | https://opengameart.org/content/ambient-horror |",
    "wind": "| Wind | Luke.RUSTLTD | CC0 | https://opengameart.org/content/wind1 |",
    "heart": "| Heartbeat | Independent.nu | CC0 | https://opengameart.org/content/heartbeat-single-sound |",
    "wave1": "| Beach ocean waves #1 | jasinski | CC0 | https://opengameart.org/content/beach-ocean-waves |",
    "wave2": "| Water waves #2 | transitking | CC0 | https://opengameart.org/content/water-waves |",
    "gull": "| Solo seagull SFX | Rango Mango | CC0 | https://opengameart.org/content/solo-seagull-sound-effects |",
    "coin": "| 10 8bit coin sounds | Luke.RUSTLTD | CC0 | https://opengameart.org/content/10-8bit-coin-sounds |",
    "kui": "| Interface Sounds 1.0 | Kenney | CC0 1.0 | https://opengameart.org/content/interface-sounds |",
    "sky": "| Kloofendal 48d Partly Cloudy (Pure Sky), 1024x512 | Greg Zaal, Jarod Guest / Poly Haven | CC0 | https://polyhaven.com/a/kloofendal_48d_partly_cloudy_puresky |",
}


def credits_md(title, keys, files, notes=""):
    out = ["# %s - credits" % title, "", "Every asset of this example is free to use, change and ship in your games (CC0, no attribution required;",
           "Kenney asks, but does not require, for a credit to \"Kenney\" / www.kenney.nl).", "",
           "| Pack | Author | License | Page |", "|---|---|---|---|"]
    out += [CREDIT_ROWS[k] for k in keys]
    out += ["", "## Files", "", "| File | From |", "|---|---|"]
    out += ["| %s | %s |" % (f, s) for f, s in files]
    out += ["", "Button, joystick and icon images (images/) were drawn for Star2D by tools/gen_3d_examples.py (CC0).",
            "Kenney car models were re-packed with a 180 degree turn baked into their root nodes so they face -Z (the engine's forward);"
            " the geometry is unchanged."]
    if notes:
        out += ["", notes]
    return "\n".join(out) + "\n"


# ====================================================================== 1. City3D

def build_city():
    folder = "City3D"
    proj = fresh(folder)
    sc = Scene()
    T = 8.0  # one road tile = 8 m (Kenney city tiles are 1 unit)
    files = []
    for f in ["road-straight", "road-bend", "road-crossroad", "road-crossing", "building-a", "building-c", "building-skyscraper-a",
              "building-type-a", "building-type-c", "tree-large", "light-square"]:
        copy_asset(proj, "models", "city/%s.glb" % f)
        files.append(("models/%s.glb" % f, "Kenney City Kits"))
    for f in ["sedan", "taxi", "police", "van", "truck"]:
        copy_asset(proj, "models", "city/%s.glb" % f, turn=180)
        files.append(("models/%s.glb" % f, "Kenney Car Kit (turned to face -Z)"))
    copy_asset(proj, "sounds", "endless/engine_loop.ogg")
    copy_asset(proj, "sounds", "common/ui_click.ogg")
    files += [("sounds/engine_loop.ogg", "Kenney Starter Kit Racing"), ("sounds/ui_click.ogg", "Kenney Interface Sounds")]
    ui_images(proj, ["btn_left.png", "btn_right.png", "pedal_gas.png", "pedal_brake.png", "btn_gear.png", "btn_wide.png", "joystick"])

    # ---- ground and roads: a 7 x 7 grid of 8 m cells, roads on rows / columns 0, 3, 6 (a ring and a cross), stubs leaving the town
    sc.shape("ground", "PLANE", 0, 0, 0, 140, 1, 140, "#6E9E48", body="STATIC", Receive_Shadows=True, Cast_Shadows=False)

    def cell(i, j):
        return (i - 3) * T, (j - 3) * T

    road = lambda i, j: 0 <= i <= 6 and 0 <= j <= 6 and (i in (0, 3, 6) or j in (0, 3, 6))
    n = 0
    for i in range(7):
        for j in range(7):
            if not road(i, j):
                continue
            x, z = cell(i, j)
            ex, wx, sz_, nz = road(i + 1, j), road(i - 1, j), road(i, j - 1), road(i, j + 1)
            if (i, j) in ((3, 0), (3, 6), (0, 3), (6, 3), (3, 3)):
                m, r = "road-crossroad", 0
            elif ex and wx:
                m, r = ("road-crossing" if (i, j) in ((2, 3), (4, 3)) else "road-straight"), 0
            elif sz_ and nz:
                m, r = ("road-crossing" if (i, j) in ((3, 2), (3, 4)) else "road-straight"), 90
            else:  # corners: road-bend joins -X and +Z at 0 deg; +90 turns it to +Z/+X ...
                r = {(True, False, False, True): 90, (False, True, False, True): 0, (False, True, True, False): 270, (True, False, True, False): 180}[(ex, wx, sz_, nz)]
                m = "road-bend"
            n += 1
            sc.model("road%d" % n, m + ".glb", x, 0.002, z, (T, 1, T), ry=r, Cast_Shadows=False)
    for k, (x, z, r) in enumerate([(0, -32, 90), (0, -40, 90), (0, 32, 90), (0, 40, 90), (-32, 0, 0), (-40, 0, 0), (32, 0, 0), (40, 0, 0)]):
        sc.model("stub%d" % (k + 1), "road-straight.glb", x, 0.002, z, (T, 1, T), ry=r, Cast_Shadows=False)

    # ---- buildings: 4 blocks of 2 x 2 cells, each building faces the row road next to it
    kinds = [("building-a.glb", 7.0), ("building-skyscraper-a.glb", 5.4), ("building-c.glb", 7.0), ("building-type-a.glb", 5.6),
             ("building-type-c.glb", 5.6), ("building-a.glb", 7.0), ("building-c.glb", 7.0), ("building-skyscraper-a.glb", 5.4)]
    k = 0
    for bi in (1, 4):
        for bj in (1, 4):
            for di in (0, 1):
                for dj in (0, 1):
                    i, j = bi + di, bj + dj
                    x, z = cell(i, j)
                    face_neg_z = road(i, j - 1)  # the road is on the -Z side
                    m, s = kinds[k % len(kinds)]
                    k += 1
                    sc.model("building%d" % k, m, x, 0, z, s, ry=180 if face_neg_z else 0, body="STATIC", collider="BOX")
    # ---- street lamps along the cross (arms over the road), trees outside the ring
    for k, (x, z, r) in enumerate([(3.6, -20, 90), (3.6, -12, 90), (3.6, 12, 90), (3.6, 20, 90), (-20, 3.6, 0), (-12, 3.6, 0), (12, 3.6, 0), (20, 3.6, 0)]):
        sc.model("lamp%d" % (k + 1), "light-square.glb", x, 0, z, 8, ry=r)
    trees = [(-36, -36), (-36, -20), (-36, -10), (-36, 12), (-36, 24), (-36, 36), (36, -36), (36, -22), (36, -10), (36, 10), (36, 22), (36, 36),
             (-22, -36), (-10, -36), (12, -36), (24, -36), (-22, 36), (-10, 36), (12, 36), (24, 36), (-7.5, -40), (7.5, 40)]
    for k, (x, z) in enumerate(trees):
        sc.model("tree%d" % (k + 1), "tree-large.glb", x, 0, z, 7 + (k % 3) * 0.8, ry=(k * 47) % 360, body="STATIC", collider="CYLINDER",
                 Collider_Scale_X=0.25, Collider_Scale_Z=0.25)
    # invisible borders
    for k, (x, z, sx, sz) in enumerate([(0, -46, 92, 1), (0, 46, 92, 1), (-46, 0, 1, 92), (46, 0, 1, 92)]):
        sc.wall("border%d" % (k + 1), x, 2, z, sx, 4, sz)

    # ---- traffic: 4 cars loop around the ring in the right-hand lane (outer lane, 1.6 m from the middle)
    L = 25.6
    pts = [(-L, -12), (-L, 12), (-24, 24), (-12, L), (12, L), (24, 24), (L, 12), (L, -12), (24, -24), (12, -L), (-12, -L), (-24, -24)]
    wps = []
    for k, (x, z) in enumerate(pts):
        sc.add("OBJ3D", "wp%d" % (k + 1), Mesh="EMPTY", x=x, y=0, z=z, Static_Object=True, Cast_Shadows=False)
        wps.append("wp%d" % (k + 1))
    for name, m, x, z, r in [("taxi", "taxi.glb", -L, 0, 180), ("police", "police.glb", 0, L, 270), ("van", "van.glb", L, 0, 0), ("truck", "truck.glb", 0, -L, 90)]:
        sc.model(name, m, x, 0, z, 1.2, ry=r, static=False, body="KINEMATIC", collider="BOX",
                 behaviors=[("Traffic3D", {"waypoints": wps, "speed": 30, "acceleration": 3, "turnRate": 95, "lookAhead": 11, "safeGap": 3,
                                           "player": "playerCar", "honkAfter": 0, "despawnDistance": 0})])

    # ---- the player's car: Kenney sedan, real wheel nodes, Car3D
    sc.model("playerCar", "sedan.glb", 1.6, 0.35, 14, 1.25, static=False, body="DYNAMIC", collider="BOX", Mass=1200, Friction=0.6,
             behaviors=[("Car3D", {"joystick": "joy", "gasButton": "gasBtn", "brakeButton": "brakeBtn", "leftButton": "leftBtn", "rightButton": "rightBtn",
                                   "maxSpeed": 90, "reverseSpeed": 22, "acceleration": 7, "brakeStrength": 16, "drive": "REAR", "engineSound": "/engine_loop.ogg",
                                   "engineVolume": 0.35, "maxSteerAngle": 32, "steerSpeed": 150, "wheelMode": "MODEL_NODES", "followCamera": True,
                                   "camera": "cam", "cameraDistance": 7, "cameraHeight": 2.6, "flipResetTime": 2.5})])
    sc.add("CAMERA3D", "cam", x=1.6, y=3.2, z=21.5, Rotation_X=-12, Active=True, FOV=62, Near=0.1, Far=300)
    # minimap: a second camera high above the car, shown by a CAMERAVIEW in the corner
    sc.add("CAMERA3D", "mapCam", x=1.6, y=55, z=30, Rotation_X=-75, FOV=50, Far=200, Follow="playerCar", Follow_Mode="TOP_DOWN", Follow_Distance=55,
           Follow_Height=0, Follow_Smooth=8)

    # ---- 2D layer (1280 x 720)
    sc.add("CAMERAVIEW", "minimap", x=1010, y=500, width=250, height=190, Camera="mapCam", Resolution=0.5, Refresh_Rate=15, Border_Color="#FFFFFF",
           Border_Width=3, Corner_Radius=14, z=100)
    sc.text("speedText", 24, 646, 280, 56, "0 km/h", scale=1.6)
    sc.button("ctrlBtn", 24, 566, 240, 72, "btn_wide.png")
    sc.text("ctrlText", 44, 578, 200, 48, "Joystick", scale=1.1)
    sc.button("gearBtn", 1188, 412, 72, 72, "btn_gear.png")
    sc.button("leftBtn", 36, 36, 160, 160, "btn_left.png")
    sc.button("rightBtn", 216, 36, 160, 160, "btn_right.png")
    sc.add("JOYSTICK", "joy", x=50, y=36, width=250, height=250, Pad_Image="/joy_pad.png", Button_Image="/joy_knob.png", z=120, Visible=False)
    sc.button("brakeBtn", 930, 36, 130, 170, "pedal_brake.png")
    sc.button("gasBtn", 1090, 36, 150, 220, "pedal_gas.png")

    # ---- events
    write_variables(proj, [("useJoystick", "boolean", "false")])
    write_event(proj, "public/OnStep", [B("showText", "speedText", R("speed text ___", "playerCar"))])
    write_event(proj, "private/ctrlBtn/onClick", [
        B("playSound3D", "/ui_click.ogg", "0.6"),
        B("if", "useJoystick", yes=[
            B("setVariable", "useJoystick", "false"), B("hide", "joy"), B("show", "leftBtn"), B("show", "rightBtn"), B("showText", "ctrlText", q("Joystick"))
        ], no=[
            B("setVariable", "useJoystick", "true"), B("show", "joy"), B("hide", "leftBtn"), B("hide", "rightBtn"), B("showText", "ctrlText", q("Buttons"))
        ])])
    write_event(proj, "private/gearBtn/onClick", [B("showGraphicsMenu3D", "false")])

    write_project(folder, sc, {"Lighting Preset": "DAY", "Render Mode": "SIMPLE", "Shadows": True, "Shadow Distance": 45, "Quality": "MEDIUM",
                               "Sky": "GRADIENT"},
                  ("3D city drive: a car with real wheels (Car3D), traffic, follow camera, minimap and gas / brake pedals or a joystick.",
                   "قيادة في مدينة ثلاثية الأبعاد: سيارة بعجلات حقيقية (Car3D) وحركة مرور وكاميرا تتبع وخريطة مصغرة ودواسات وقود وفرامل أو عصا تحكم."),
                  credits_md("City3D", ["car", "roads", "comm", "sub", "racing", "kui"], files))
    return proj


# ====================================================================== model bounds (placement of models whose origin is not their center)

def glb_bounds(path, node=None):
    """(min, max) of the mesh in the element's space (node: the 'Model Node' shown alone, drawn at the origin)."""
    g, rest = glb_read(path)

    def mat(n):
        t = n.get("translation", [0, 0, 0]); r = n.get("rotation", [0, 0, 0, 1]); s = n.get("scale", [1, 1, 1])
        x, y, z, w = r
        R = [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)], [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
             [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]
        return [[R[i][j] * s[j] for j in range(3)] + [t[i]] for i in range(3)]

    def mul(A, Bm):
        return [[sum(A[i][k] * Bm[k][j] for k in range(3)) + (A[i][3] if j == 3 else 0) for j in range(4)] for i in range(3)]

    lo, hi = [1e9] * 3, [-1e9] * 3

    def walk(i, P, inside):
        n = g["nodes"][i]
        if node is not None and not inside and n.get("name") == node:
            M = [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0]]
            inside = True
        else:
            M = mul(P, mat(n))
        if "mesh" in n and (node is None or inside):
            for pr in g["meshes"][n["mesh"]]["primitives"]:
                a = g["accessors"][pr["attributes"]["POSITION"]]
                for cx in (a["min"][0], a["max"][0]):
                    for cy in (a["min"][1], a["max"][1]):
                        for cz in (a["min"][2], a["max"][2]):
                            v = [M[r][0] * cx + M[r][1] * cy + M[r][2] * cz + M[r][3] for r in range(3)]
                            for k in range(3):
                                lo[k] = min(lo[k], v[k]); hi[k] = max(hi[k], v[k])
        for c in n.get("children", []):
            walk(c, M, inside)

    for r in g["scenes"][g.get("scene", 0)]["nodes"]:
        walk(r, [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0]], False)
    return lo, hi


def yaw_to(dx, dz):
    """Rotation Y that turns a -Z facing model toward (dx, dz)."""
    return round(math.degrees(math.atan2(-dx, -dz)), 2)


def place(sc, proj, name, model, scale, cx, y, cz, ry=0.0, **kw):
    """Puts a model so the center of its footprint is at (cx, cz) and its bottom at y (whatever its origin)."""
    lo, hi = glb_bounds(os.path.join(proj, "models", model))
    mx, mz = (lo[0] + hi[0]) / 2 * scale, (lo[2] + hi[2]) / 2 * scale
    a = math.radians(ry)
    ox, oz = mx * math.cos(a) + mz * math.sin(a), -mx * math.sin(a) + mz * math.cos(a)
    return sc.model(name, model, round(cx - ox, 4), round(y - lo[1] * scale, 4), round(cz - oz, 4), scale, ry=ry, **kw)


# ====================================================================== 2. HorrorHouse3D

def msg(text, secs="3.5"):
    return [B("showText", "msgText", q(text)), B("show", "msgText"), B("waitSeconds", secs, yes=[B("hide", "msgText")])]


def build_horror():
    folder = "HorrorHouse3D"
    proj = fresh(folder)
    sc = Scene()
    files = []
    for f in ["wall", "wall-doorway-square", "wall-window-square", "stairs-closed", "bedSingle", "table", "chair", "bookcaseClosedDoors",
              "sideTableDrawers", "lampRoundTable", "candle", "lantern-candle", "key", "flashlight"]:
        copy_asset(proj, "models", "horror/%s.glb" % f)
        files.append(("models/%s.glb" % f, "see packs above"))
    copy_asset(proj, "models", "horror/door-rotate-square-a.glb", turn=90)
    files.append(("models/door-rotate-square-a.glb", "Kenney Building Kit (turned 90 degrees: panel along X)"))
    for f in ["door_open_creak", "door_close", "door_locked_rattle", "door_unlock", "key_pickup", "footstep_1", "footstep_2", "footstep_3",
              "ambient_horror_drone", "ambient_wind", "heartbeat"]:
        copy_asset(proj, "sounds", "horror/%s.ogg" % f)
        files.append(("sounds/%s.ogg" % f, "see packs above"))
    copy_asset(proj, "sounds", "common/ui_click.ogg")
    files.append(("sounds/ui_click.ogg", "Kenney Interface Sounds"))
    ui_images(proj, ["btn_use.png", "btn_flash.png", "btn_run.png", "btn_gear.png", "joystick"])

    # ---- outside
    sc.shape("ground", "PLANE", 0, 0, 0, 90, 1, 90, "#2F3B2C", body="STATIC", Cast_Shadows=False)
    sc.shape("path", "CUBE", -1, 0.01, 10, 1.6, 0.02, 12, "#4E463E", Cast_Shadows=False)
    sc.shape("porch", "CUBE", -1, 0.05, 4.1, 3.0, 0.1, 2.0, "#4A4038", body="STATIC")
    for k, (x, z, s, r) in enumerate([(-9, -3, 1.0, 0), (7.5, -8, 1.2, 40), (9.5, 5, 0.9, 120), (-10, 9, 1.1, 200), (-5, -12, 1.0, 300), (11, -1, 0.8, 75)]):
        sc.shape("trunk%d" % (k + 1), "CYLINDER", x, 2.2 * s, z, 0.35 * s, 4.4 * s, 0.35 * s, "#2A211B", body="STATIC")
        a = math.radians(r)
        sc.shape("branch%da" % (k + 1), "CYLINDER", x + 0.55 * s * math.cos(a), 3.5 * s, z - 0.55 * s * math.sin(a), 0.14 * s, 2.0 * s, 0.14 * s, "#2A211B",
                 ry=r, rz=-38)
        sc.shape("branch%db" % (k + 1), "CYLINDER", x - 0.45 * s * math.cos(a), 2.9 * s, z + 0.45 * s * math.sin(a), 0.12 * s, 1.6 * s, 0.12 * s, "#2A211B",
                 ry=r, rz=42)
    for k, (x, z, t) in enumerate([(6.5, 8.5, 6), (7.6, 9.6, -8), (8.8, 8.2, 3), (6.2, 11, -4)]):
        sc.shape("grave%d" % (k + 1), "CUBE", x, 0.42, z, 0.6, 0.9, 0.14, "#6A6B70", ry=-20 + k * 7, rz=t, body="STATIC")
    for k, (x, z, s) in enumerate([(-6, 12, 1.0), (4, -10, 1.4), (-12, -6, 1.2), (12, 12, 0.9)]):
        sc.shape("rock%d" % (k + 1), "SPHERE", x, 0.2 * s, z, 1.2 * s, 0.6 * s, 1.0 * s, "#4A4A50", body="STATIC")
    for k, (x, z, sx, sz) in enumerate([(0, -24, 50, 1), (0, 24, 50, 1), (-24, 0, 1, 50), (24, 0, 1, 50)]):
        sc.wall("border%d" % (k + 1), x, 2, z, sx, 4, sz)

    # ---- the house: 8 x 6 m, two floors of 2.4 m (Kenney building kit walls, 2 m segments)
    WALLS = {"W": "wall.glb", "N": "wall-window-square.glb", "D": "wall-doorway-square.glb"}

    def segs(prefix, y, line):
        for k, (kind, along, x, z) in enumerate(line):
            sc.model("%s%d" % (prefix, k + 1), WALLS[kind], x, y, z, (1, 1, 1.05), ry=90 if along == "x" else 0, body="STATIC",
                     collider="MESH" if kind == "D" else "BOX", Color="#B4ADA5")

    ground_line = [("N", "x", -3, 3), ("D", "x", -1, 3), ("N", "x", 1, 3), ("W", "x", 3, 3),
                   ("W", "x", -3, -3), ("N", "x", -1, -3), ("W", "x", 1, -3), ("W", "x", 3, -3),
                   ("N", "z", -4, -2), ("W", "z", -4, 0), ("N", "z", -4, 2), ("W", "z", 4, -2), ("N", "z", 4, 0), ("W", "z", 4, 2),
                   ("W", "z", 0, -2), ("D", "z", 0, 0), ("W", "z", 0, 2)]
    upper_line = [("N", "x", -3, 3), ("N", "x", -1, 3), ("N", "x", 1, 3), ("W", "x", 3, 3),
                  ("N", "x", -3, -3), ("W", "x", -1, -3), ("W", "x", 1, -3), ("W", "x", 3, -3),
                  ("N", "z", -4, -2), ("W", "z", -4, 0), ("N", "z", -4, 2), ("W", "z", 4, -2), ("N", "z", 4, 0), ("W", "z", 4, 2),
                  ("D", "z", 0, -2), ("W", "z", 0, 0), ("W", "z", 0, 2)]
    segs("wallA", 0, ground_line)
    segs("wallB", 2.4, upper_line)
    sc.shape("floor1", "CUBE", 0, 0.05, 0, 8.1, 0.1, 6.1, "#4B3626", body="STATIC")
    # upper floor with the stair hole (x 2.35..4, z -2..3), its rail, the roof
    sc.shape("floor2a", "CUBE", -0.825, 2.45, 0, 6.35, 0.1, 6.1, "#56402F", body="STATIC")
    sc.shape("floor2b", "CUBE", 3.175, 2.45, -2.5, 1.65, 0.1, 1.0, "#56402F", body="STATIC")
    sc.shape("stairRail", "CUBE", 2.35, 3.0, 0.5, 0.08, 1.0, 4.9, "#3A2A1E", body="STATIC")
    sc.shape("roof", "CUBE", 0, 4.9, 0, 8.5, 0.2, 6.5, "#262022", body="STATIC")
    # stairs: the model (rising toward +Z, turned to rise toward the back) is only drawn; an invisible RAMP (high side at -Z) under it is
    # what the player walks on (smooth, no step bumps)
    sc.model("stairs", "stairs-closed.glb", 3.0, 0, -0.5, 1, ry=180, Color="#A89C90")
    sc.shape("stairRamp", "RAMP", 3.0, 1.25, -0.25, 1.3, 2.5, 3.5, ry=0, body="STATIC", Visible=False, Cast_Shadows=False)

    # ---- doors (Door3D hinge doors with KINEMATIC colliders; the front one needs the key)
    snd = {"openSound": "/door_open_creak.ogg", "closeSound": "/door_close.ogg", "button": "useBtn", "promptText": "useText"}
    sc.model("frontDoor", "door-rotate-square-a.glb", -1.45, 0, 3.0, 1, static=False, body="KINEMATIC", collider="BOX", Color="#8E8378",
             behaviors=[("Door3D", dict(snd, keyItem="key", lockedText="Locked", lockedSound="/door_locked_rattle.ogg", unlockSound="/door_unlock.ogg",
                                        range=2.0, openTime=1.1, hinge="LEFT", openText="Open", closeText="Close"))])
    sc.model("livingDoor", "door-rotate-square-a.glb", 0, 0, -0.45, 1, ry=-90, static=False, body="KINEMATIC", collider="BOX",
             behaviors=[("Door3D", dict(snd, range=2.0, openTime=1.0, hinge="LEFT"))])
    sc.model("bedroomDoor", "door-rotate-square-a.glb", 0, 2.4, -2.45, 1, ry=-90, static=False, body="KINEMATIC", collider="BOX",
             behaviors=[("Door3D", dict(snd, range=2.0, openTime=1.2, hinge="LEFT"))])

    # ---- furniture (furniture pack: ~10 units per meter, front = -Z)
    use = {"button": "useBtn", "promptText": "useText", "range": 2.0, "needLook": True, "lookAngle": 30}
    place(sc, proj, "table", "table.glb", 0.22, -2.3, 0.1, -0.4, body="STATIC", collider="BOX")
    place(sc, proj, "chairA", "chair.glb", 0.22, -2.7, 0.1, 0.45, ry=0, body="STATIC", collider="BOX")
    place(sc, proj, "chairB", "chair.glb", 0.22, -1.9, 0.1, -1.25, ry=180, body="STATIC", collider="BOX")
    place(sc, proj, "candle1", "candle.glb", 1.0, -2.55, 0.82, -0.45, static=False)
    place(sc, proj, "candle2", "candle.glb", 0.8, -2.15, 0.82, -0.3, static=False)
    place(sc, proj, "bookcase1", "bookcaseClosedDoors.glb", 0.22, -3.65, 0.1, 0, ry=yaw_to(1, 0), body="STATIC", collider="BOX",
          behaviors=[("Interactable3D", dict(use, message="Search"))])
    place(sc, proj, "hallTable", "sideTableDrawers.glb", 0.18, 1.2, 0.1, 2.68, ry=0, body="STATIC", collider="BOX")
    place(sc, proj, "hallLampModel", "lampRoundTable.glb", 0.18, 1.2, 0.79, 2.68, static=False)
    place(sc, proj, "hallChair", "chair.glb", 0.22, 0.8, 0.1, -2.3, ry=150, body="STATIC", collider="BOX")
    place(sc, proj, "bed", "bedSingle.glb", 0.18, -2.6, 2.5, -1.85, ry=180, body="STATIC", collider="BOX",
          behaviors=[("Interactable3D", dict(use, message="Look under the bed"))])
    place(sc, proj, "bedTable", "sideTableDrawers.glb", 0.15, -1.25, 2.5, -2.7, ry=180, body="STATIC", collider="BOX")
    place(sc, proj, "bedLantern", "lantern-candle.glb", 1.0, -1.25, 3.08, -2.7, static=False)
    place(sc, proj, "diaryShelf", "bookcaseClosedDoors.glb", 0.22, 1.2, 2.5, 2.65, ry=0, body="STATIC", collider="BOX",
          behaviors=[("Interactable3D", dict(use, message="Read the diary"))])

    # ---- outside props: the flashlight on a crate by the path, the key on a stump behind the house
    sc.shape("crate", "CUBE", 1.6, 0.3, 11, 0.6, 0.6, 0.6, "#5A4632", body="STATIC")
    sc.model("flashlightItem", "flashlight.glb", 1.6, 0.64, 11, 0.12, rz=90, static=False, Emissive="#202020",
          behaviors=[("Pickup3D", {"itemId": "flashlight", "range": 1.6, "sound": "/key_pickup.ogg", "flyToPlayer": True})])
    sc.shape("stump", "CYLINDER", -7, 0.25, -6, 0.7, 0.5, 0.7, "#3B2E24", body="STATIC")
    sc.add("OBJ3D", "keyItem", [("Floater3D", {"height": 0.06, "frequency": 0.5, "spinSpeed": 50}),
                                ("Pickup3D", {"itemId": "key", "range": 1.5, "sound": "/key_pickup.ogg", "flyToPlayer": True})],
           Mesh="MODEL", Model="/key.glb", Model_Node="key", x=-7, y=0.78, z=-6, Rotation_Z=90, Scale_X=1.1, Scale_Y=1.1, Scale_Z=1.1,
           Emissive="#6B5212", Emissive_Intensity=0.8)
    place(sc, proj, "keyLantern", "lantern-candle.glb", 1.0, -6.2, 0, -5.4, static=False)
    place(sc, proj, "porchLantern", "lantern-candle.glb", 1.0, 0.5, 0.1, 3.7, static=False)

    # ---- lights: moonlight comes from the scene (CUSTOM night), warm flickering lamps and candles, the flashlight
    def light(name, x, y, z, color, inten, rng, style, glow=None, **extra):
        p = {"style": style, "amount": 0.3 if style == "CANDLE" else 0.6, "speed": 7}
        if glow:
            p["glowObject"] = glow
            p["glowColor"] = "#FFB050"
        p.update(extra)
        sc.add("LIGHT3D", name, [("Flicker3D", p)], Light_Kind="POINT", x=x, y=y, z=z, Color=color, Intensity=inten, Range=rng)

    light("candleLight", -2.35, 1.25, -0.4, "#FF9A40", 1.5, 6, "CANDLE", "candle1")
    light("hallLight", 1.2, 1.35, 2.5, "#FFD9A0", 1.3, 6.5, "BROKEN", "hallLampModel", outEvery=5)
    light("bedLight", -1.25, 3.55, -2.55, "#FF9A40", 1.3, 5.5, "CANDLE", "bedLantern")
    light("upLight", 1.4, 4.4, 0.3, "#C8A070", 0.8, 5.5, "BROKEN", None, outEvery=3)
    light("porchLight", 0.5, 0.75, 3.7, "#FFA850", 1.4, 7, "CANDLE", "porchLantern")
    light("keyLight", -6.2, 0.7, -5.4, "#FFA040", 1.6, 6, "CANDLE", "keyLantern")
    sc.add("LIGHT3D", "flashlight", [("Flashlight3D", {"toggleButton": "flashBtn", "startOn": False, "clickSound": "/ui_click.ogg", "sway": 0.3})],
           Light_Kind="SPOT", x=-1, y=1.4, z=17, Color="#FFF2D6", Intensity=1.8, Range=16, Cone_Angle=26, Cone_Softness=0.45)

    # ---- the player: a first person camera
    sc.add("CAMERA3D", "cam", [("FirstPersonController3D", {"joystick": "joy", "runButton": "runBtn", "dragToLook": True, "eyeHeight": 1.6,
                                                           "walkSpeed": 2.4, "runSpeed": 4.6, "jumpSpeed": 0, "radius": 0.3, "headBob": 0.04,
                                                           "lookSensitivity": 0.22}),
                               ("Footsteps3D", {"sounds": ["/footstep_1.ogg", "/footstep_2.ogg", "/footstep_3.ogg"], "volume": 0.55, "stepLength": 0.75})],
           x=-1, y=1.6, z=17, Rotation_X=0, Active=True, FOV=66, Near=0.05, Far=120)
    # a zone in the bedroom: heartbeat the first time the player walks in
    sc.add("OBJ3D", "bedroomZone", [("Trigger3D", {"once": True, "enterSound": "/heartbeat.ogg", "watchCamera": True})],
           Mesh="EMPTY", x=-2, y=3.6, z=0, Scale_X=3.6, Scale_Y=2.2, Scale_Z=5.6, Cast_Shadows=False)

    # ---- 2D layer
    sc.add("JOYSTICK", "joy", x=40, y=30, width=240, height=240, Pad_Image="/joy_pad.png", Button_Image="/joy_knob.png", z=120)
    sc.button("runBtn", 1150, 36, 110, 110, "btn_run.png")
    sc.button("useBtn", 1105, 190, 150, 150, "btn_use.png")
    sc.text("useText", 1000, 350, 260, 46, "Open", scale=1.1)
    sc.button("flashBtn", 1150, 520, 110, 110, "btn_flash.png")
    sc.button("gearBtn", 1196, 638, 64, 64, "btn_gear.png")
    sc.text("invText", 24, 664, 420, 44, "Inventory: empty", scale=1.0, color="#E8DCC8")
    sc.text("goalText", 24, 618, 520, 44, "Find a way into the house", scale=1.0, color="#FFCF7A")
    sc.text("msgText", 290, 330, 700, 60, "", scale=1.2, color="#F2E6D8")

    # ---- events
    write_event(proj, "public/OnCreate", [
        B("clearInventory"),
        B("loopSound", "/ambient_wind.ogg", "true"), B("playSound", "/ambient_wind.ogg"),
        B("loopSound", "/ambient_horror_drone.ogg", "true"), B("playSound", "/ambient_horror_drone.ogg"),
        B("postEffect3D", "HORROR", "0.45"),
        B("hide", "flashBtn"), B("hide", "msgText")])
    write_event(proj, "private/flashlightItem/behaviors/Pickup3D/onPickup", [B("show", "flashBtn")] + msg("Flashlight found: tap the light button"))
    write_event(proj, "private/keyItem/behaviors/Pickup3D/onPickup", [B("showText", "invText", q("Inventory: old key")),
                                                                      B("showText", "goalText", q("Open the front door"))])
    write_event(proj, "private/frontDoor/behaviors/Door3D/onLocked", msg("Locked. The key must be somewhere outside..."))
    write_event(proj, "private/frontDoor/behaviors/Door3D/onUnlock", [B("showText", "goalText", q("Explore the house. Go upstairs."))])
    write_event(proj, "private/bookcase1/behaviors/Interactable3D/onInteract", msg("Old books... one is still warm, as if someone just held it."))
    write_event(proj, "private/bed/behaviors/Interactable3D/onInteract", msg("Scratches under the bed... from the inside."))
    write_event(proj, "private/diaryShelf/behaviors/Interactable3D/onInteract", msg("The diary says: 'Never let the candles go out.'", "4.5"))
    write_event(proj, "private/bedroomZone/behaviors/Trigger3D/onEnter", [B("showText", "goalText", q("You are not alone...")), B("cameraShake3D", "0.25")])
    write_event(proj, "private/gearBtn/onClick", [B("showGraphicsMenu3D", "false")])

    write_project(folder, sc, {"Lighting Preset": "CUSTOM", "Render Mode": "SIMPLE", "Ambient Color": "#4A5068", "Ambient Intensity": 1,
                               "Sun": True, "Sun Color": "#9FB0D8", "Sun Intensity": 0.55, "Sun Pitch": -38, "Sun Yaw": -60, "Sky": "GRADIENT",
                               "Sky Top": "#070A14", "Sky Bottom": "#23293A", "Fog": True, "Fog Color": "#1C2130", "Fog Near": 8, "Fog Far": 45,
                               "Shadows": True, "Shadow Distance": 25, "Quality": "HIGH", "Post Effect": "HORROR"},
                  ("3D horror house at night: find the flashlight and the key, open creaking doors, climb the stairs. Inventory, sounds, flickering lights.",
                   "بيت رعب ثلاثي الأبعاد ليلاً: اعثر على المصباح والمفتاح، افتح الأبواب الصريرة واصعد الدرج. حقيبة أغراض وأصوات وأضواء متراقصة."),
                  credits_md("HorrorHouse3D", ["build", "furn", "grave", "plat", "flash", "creak", "lock", "rpg", "steps", "horror", "wind", "heart", "kui"], files))
    return proj


# ====================================================================== 3. EndlessRoad3D

def build_endless():
    folder = "EndlessRoad3D"
    proj = fresh(folder)
    sc = Scene()
    files = []
    for f in ["player-race", "suv", "delivery", "truck-flat", "hatchback-sports"]:
        copy_asset(proj, "models", "endless/%s.glb" % f, turn=180)
        files.append(("models/%s.glb" % f, "Kenney Car Kit (turned to face -Z)"))
    copy_asset(proj, "models", "endless/road-straight.glb", turn=90)
    files.append(("models/road-straight.glb", "Kenney City Kit Roads (turned 90 degrees: runs along Z)"))
    for f in ["cactus_short", "cactus_tall", "rock_largeA", "rock_smallA", "fence_simple", "coinGold"]:
        copy_asset(proj, "models", "endless/%s.glb" % f)
        files.append(("models/%s.glb" % f, "Kenney Nature / Platformer Kit"))
    for f in ["engine_loop", "crash_impact", "coin_pickup"]:
        copy_asset(proj, "sounds", "endless/%s.ogg" % f)
        files.append(("sounds/%s.ogg" % f, "Kenney Starter Kit Racing / Luke.RUSTLTD"))
    ui_images(proj, ["btn_left.png", "btn_right.png", "btn_gear.png", "btn_wide_red.png"])

    # ---- templates (hidden at start; EndlessRoad3D copies and recycles them). They wait far away from the road.
    sc.model("roadTile", "road-straight.glb", 0, 0.004, 0, (16, 1, 16), static=False, Cast_Shadows=False)
    props = []
    for name, m, s in [("cactusTall", "cactus_tall.glb", 4.5), ("cactusShort", "cactus_short.glb", 4.0), ("rockLarge", "rock_largeA.glb", 5.0),
                       ("rockSmall", "rock_smallA.glb", 5.0), ("fence", "fence_simple.glb", 4.0)]:
        sc.model(name, m, -60, 0, 60, s, static=False)
        props.append(name)
    traffic = []
    for k, (name, m) in enumerate([("suv", "suv.glb"), ("delivery", "delivery.glb"), ("truckFlat", "truck-flat.glb"), ("hatch", "hatchback-sports.glb")]):
        sc.model(name, m, 60 + k * 6, 0, 60, 1.35, static=False, body="KINEMATIC", collider="BOX")
        traffic.append(name)
    sc.add("OBJ3D", "coin", Mesh="MODEL", Model="/coinGold.glb", Model_Node="coin", x=0, y=1, z=80, Rotation_Y=90, Scale_X=2.4, Scale_Y=2.4, Scale_Z=2.4,
           Emissive="#5A4300", Emissive_Intensity=0.7, Cast_Shadows=False)
    sc.add("OBJ3D", "road", [("EndlessRoad3D", {"target": "playerCar", "roadTiles": ["roadTile"], "tileLength": 16, "tilesAhead": 12, "tilesBehind": 2,
                                                "props": props, "propChance": 0.7, "propDistance": 11, "traffic": traffic, "trafficChance": 0.4,
                                                "lanes": 4, "laneWidth": 3.2, "trafficSpeed": 50, "oncoming": True, "pickups": ["coin"],
                                                "pickupChance": 0.55, "pickupHeight": 1.0, "levelDistance": 600, "trafficPerLevel": 0.06,
                                                "speedPerLevel": 8, "scorePerMeter": 1, "pickupScore": 25})],
           Mesh="EMPTY", x=0, y=0, z=0, Cast_Shadows=False)
    # desert floor: one big plane that follows the car (OnStep), physics included
    sc.shape("sand", "PLANE", 0, -0.03, 0, 600, 1, 600, "#D8B47A", static=False, body="STATIC", Cast_Shadows=False)

    # ---- the player's race car, always on the gas: steer with the two buttons
    sc.model("playerCar", "player-race.glb", 1.6, 0.4, 0, 1.35, static=False, body="DYNAMIC", collider="BOX", Mass=900,
             behaviors=[("Car3D", {"leftButton": "leftBtn", "rightButton": "rightBtn", "autoGas": True, "maxSpeed": 110, "acceleration": 9,
                                   "maxSteerAngle": 14, "steerSpeed": 90, "steerReduction": 0.5, "grip": 4, "engineSound": "/engine_loop.ogg",
                                   "engineVolume": 0.4, "wheelMode": "MODEL_NODES", "followCamera": True, "camera": "cam", "cameraDistance": 7.5,
                                   "cameraHeight": 2.8, "flipResetTime": 2})])
    sc.add("CAMERA3D", "cam", x=1.6, y=3.2, z=7.5, Rotation_X=-12, Active=True, FOV=64, Near=0.1, Far=320)

    # ---- 2D layer
    sc.button("leftBtn", 36, 36, 170, 170, "btn_left.png")
    sc.button("rightBtn", 1074, 36, 170, 170, "btn_right.png")
    sc.button("gearBtn", 1196, 638, 64, 64, "btn_gear.png")
    sc.text("scoreText", 24, 660, 360, 48, "Score 0", scale=1.4)
    sc.text("distText", 24, 612, 360, 44, "0 m", scale=1.1)
    sc.text("crashText", 24, 566, 360, 44, "Crashes 0 / 3", scale=1.0, color="#FFD0C0")
    sc.text("speedText", 900, 650, 280, 56, "0 km/h", scale=1.5)
    sc.text("levelText", 440, 560, 400, 60, "", scale=1.4, color="#FFE27A")
    sc.text("overText", 390, 420, 500, 80, "GAME OVER", scale=2.2, color="#FF5A4A", visible=False)
    sc.button("restartBtn", 460, 290, 360, 96, "btn_wide_red.png", visible=False)
    sc.text("restartText", 520, 312, 240, 52, "Play again", scale=1.4, visible=False)

    # ---- events
    write_variables(proj, [("crashes", "int", "0")])
    write_event(proj, "public/OnStep", [
        B("setZ3D", "sand", R("3D z of ___", "playerCar")),
        B("showText", "scoreText", R("join ___ ___", q("Score "), R("road score ___", "road"))),
        B("showText", "distText", R("join ___ ___", R("round ___", R("distance driven ___", "road")), q(" m"))),
        B("showText", "speedText", R("speed text ___", "playerCar"))])
    write_event(proj, "private/road/behaviors/EndlessRoad3D/onCollect", [B("playSound3D", "/coin_pickup.ogg", "0.8")])
    write_event(proj, "private/road/behaviors/EndlessRoad3D/onLevelUp", [
        B("showText", "levelText", R("join ___ ___", q("Level "), "level")), B("waitSeconds", "2", yes=[B("showText", "levelText", q(""))])])
    write_event(proj, "private/road/behaviors/EndlessRoad3D/onCrash", [
        B("playSound3D", "/crash_impact.ogg", "1"), B("cameraShake3D", "0.5"), B("changeVariable", "crashes", "1"),
        B("showText", "crashText", R("join ___ ___", R("join ___ ___", q("Crashes "), "crashes"), q(" / 3"))),
        B("ifThen", R("___ >= ___", "crashes", "3"), yes=[
            B("Car3D.setGas", "playerCar", "-0.005"), B("Car3D.setHandbrake", "playerCar", "true"), B("EndlessRoad3D.setPaused", "road", "true"),
            B("show", "overText"), B("show", "restartBtn"), B("show", "restartText")])])
    write_event(proj, "private/restartBtn/onClick", [B("OpenScene", "scene1")])
    write_event(proj, "private/gearBtn/onClick", [B("showGraphicsMenu3D", "false")])

    write_project(folder, sc, {"Lighting Preset": "CUSTOM", "Render Mode": "SIMPLE", "Ambient Color": "#8A8274", "Ambient Intensity": 1, "Sun": True,
                               "Sun Color": "#FFF0D2", "Sun Intensity": 1.05, "Sun Pitch": -50, "Sun Yaw": 30, "Sky": "GRADIENT", "Sky Top": "#4E8EE0",
                               "Sky Bottom": "#F1D7A4", "Fog": True, "Fog Color": "#EBD3A3", "Fog Near": 70, "Fog Far": 190, "Shadows": True,
                               "Shadow Distance": 40, "Quality": "MEDIUM"},
                  ("Endless 3D desert highway: dodge the traffic, collect coins, the speed grows with the distance. Score, distance and speed HUD.",
                   "طريق صحراوي لا نهائي ثلاثي الأبعاد: تفادَ السيارات واجمع العملات، والسرعة تزيد مع المسافة. عدّاد النقاط والمسافة والسرعة."),
                  credits_md("EndlessRoad3D", ["car", "roads", "nature", "plat", "racing", "coin"], files))
    return proj


# ====================================================================== 4. SeaDock3D

def build_sea():
    folder = "SeaDock3D"
    proj = fresh(folder)
    sc = Scene()
    files = []
    for f in ["structure-platform-dock", "platform-planks", "structure-fence", "barrel", "buoy", "boat-row-small", "bench"]:
        copy_asset(proj, "models", "sea/%s.glb" % f)
        files.append(("models/%s.glb" % f, "Kenney Pirate / Watercraft / Graveyard Kit"))
    copy_asset(proj, "models", "city/light-curved.glb")
    files.append(("models/light-curved.glb", "Kenney City Kit Roads"))
    for f in ["ocean_waves", "seagull_1", "seagull_2"]:
        copy_asset(proj, "sounds", "sea/%s.ogg" % f)
        files.append(("sounds/%s.ogg" % f, "see packs above"))
    for f in ["footstep_1", "footstep_2"]:
        copy_asset(proj, "sounds", "horror/%s.ogg" % f)
        files.append(("sounds/%s.ogg" % f, "GboxMikeFozzy"))
    copy_asset(proj, "images", "sea/sky_kloofendal_puresky_1024x512.jpg")
    files.append(("images/sky_kloofendal_puresky_1024x512.jpg", "Poly Haven"))
    ui_images(proj, ["btn_seat.png", "btn_gear.png", "joystick"])

    # ---- the sea (WATER element: waves, colors and quality are properties) and the stone quay
    sc.add("OBJ3D", "sea", Mesh="WATER", x=0, y=0, z=-40, Scale_X=260, Scale_Y=1, Scale_Z=200, Water_Color="#2E8FD8", Water_Deep_Color="#0B3D6B",
           Foam_Color="#FFFFFF", Wave_Height=0.35, Wave_Speed=0.8, Wave_Length=10, Wave_Direction=20, Water_Clarity=0.6, Water_Resolution=64, Foam=0.45,
           Water_Quality="AUTO", Buoyancy=1, Cast_Shadows=False, Receive_Shadows=True)
    sc.shape("quay", "CUBE", 0, -0.5, 32, 80, 3, 40, "#9C9486", body="STATIC")
    sc.shape("quayEdge", "CUBE", 0, 1.05, 12.3, 80, 0.1, 0.6, "#B7AFA0", body="NONE")
    # pier: Kenney dock modules (stretched down into the water), an invisible walkway box, rails
    D = 3.0
    mods = [(0, 10.5), (0, 7.5), (0, 4.5), (0, 1.5), (0, -1.5), (-3, -4.5), (0, -4.5), (3, -4.5)]
    for k, (x, z) in enumerate(mods):
        sc.model("dock%d" % (k + 1), "structure-platform-dock.glb", x, -0.8, z, (1.2, 2.0, 1.2), ry=(k % 2) * 180)
    sc.wall("pierWalk", 0, 0.9, 3.0, 2.8, 0.2, 18.5)
    sc.wall("headWalk", 0, 0.9, -4.5, 8.8, 0.2, 2.8)
    RAIL, Y = "#6B4A2E", 1.75
    for k, (x, z, sx, sz) in enumerate([(-1.42, 4.5, 0.09, 15.0), (1.42, 4.5, 0.09, 15.0), (0, -6.0, 9.0, 0.09), (-2.96, -3.0, 3.1, 0.09),
                                        (2.96, -3.0, 3.1, 0.09), (-4.47, -4.5, 0.09, 3.0), (4.47, -4.5, 0.09, 3.0)]):
        sc.shape("rail%d" % (k + 1), "CUBE", x, Y, z, sx, 0.09, sz, RAIL)
        sc.shape("railLow%d" % (k + 1), "CUBE", x, Y - 0.4, z, sx, 0.07, sz, RAIL)
        sc.wall("railWall%d" % (k + 1), x, 1.6, z, max(sx, 0.2), 1.3, max(sz, 0.2))
    sc.model("canopy", "structure-fence.glb", 0, 1.0, -4.5, (1.1, 1.15, 1.1))
    sc.model("planks1", "platform-planks.glb", -3.0, 1.0, -4.6, 0.6, ry=20)
    sc.model("planks2", "platform-planks.glb", 3.2, 1.0, -4.2, 0.55, ry=-70)
    sc.model("pierLamp1", "light-curved.glb", -4.2, 1.0, -5.8, 4.2, ry=yaw_to(1, 0))
    sc.model("pierLamp2", "light-curved.glb", 4.2, 1.0, -5.8, 4.2, ry=yaw_to(-1, 0))
    # the bench: the player can sit down (Seat3D), the model's front is +Z
    sc.model("bench", "bench.glb", 0, 1.0, -4.4, 1.6, ry=180, static=True, body="STATIC", collider="BOX",
             behaviors=[("Seat3D", {"button": "sitBtn", "promptText": "sitText", "sitText": "Sit", "standText": "Stand up", "seatYawOffset": 180,
                                    "eyeHeight": 1.15, "eyeForward": 0.1, "seatedPitch": -6, "moveTime": 0.8, "range": 2.0, "standDistance": 0.9})])
    # a lifebuoy on the rail (red torus), barrels on the pier and floating ones, buoys, a moored boat
    sc.shape("lifebuoy", "TORUS", 1.5, 1.55, 6.0, 0.8, 0.8, 0.8, "#E53935", rz=90, Cast_Shadows=True)
    for k, (x, z, r) in enumerate([(-1.0, 11.2, 0), (-0.7, 10.3, 40), (3.6, -5.2, 15)]):
        sc.model("barrel%d" % (k + 1), "barrel.glb", x, 1.0, z, 0.55, ry=r, body="STATIC", collider="CYLINDER")
    float_ = lambda depth, tilt=1.0: [("Buoyancy3D", {"water": "sea", "sinkDepth": depth, "tilt": tilt, "smoothing": 4})]
    sc.model("floatBarrel1", "barrel.glb", 6.5, 0, -1.0, 0.55, rx=90, static=False, behaviors=float_(0.25))
    sc.model("floatBarrel2", "barrel.glb", -8.0, 0, 3.0, 0.55, rx=90, ry=60, static=False, behaviors=float_(0.25))
    sc.model("buoy1", "buoy.glb", 9.0, 0, -12.0, 1.0, static=False, behaviors=float_(0.35, 0.8))
    sc.model("buoy2", "buoy.glb", -10.0, 0, -16.0, 1.0, static=False, behaviors=float_(0.35, 0.8))
    sc.shape("floatRing", "TORUS", -4.5, 0, 6.5, 0.9, 0.9, 0.9, "#F4F4F4", static=False, behaviors=float_(0.02, 1.0))
    sc.model("boat", "boat-row-small.glb", 3.7, 0, 6.0, 1.3, static=False, behaviors=float_(0.25, 0.7))
    # seagulls circling over the pier
    sc.add("OBJ3D", "gullCenter", Mesh="EMPTY", x=0, y=13, z=-8, Static_Object=True, Cast_Shadows=False)
    for k, (x, z, sp) in enumerate([(14, -8, 22), (-12, -6, -18), (0, 8, 26)]):
        sc.shape("gull%d" % (k + 1), "CUBE", x, 13 + k * 1.5, z, 0.9, 0.04, 0.22, "#FAFAFA", static=False, Cast_Shadows=False,
                 behaviors=[("Orbit3D", {"center": "gullCenter", "speed": sp, "height": k * 1.5, "wave": 0.6, "facing": "MOVE_DIRECTION"})])
    # quay props and borders
    for k, (x, z) in enumerate([(-6, 16), (6, 16)]):
        sc.model("quayLamp%d" % (k + 1), "light-curved.glb", x, 1.0, z, 4.5, ry=0)
    for k, (x, z, sx, sz) in enumerate([(-21, 12.0, 39, 1.0), (21, 12.0, 39, 1.0), (0, 50, 80, 1), (-40, 31, 1, 40), (40, 31, 1, 40)]):
        sc.wall("border%d" % (k + 1), x, 2.0, z, sx, 2.4, sz)

    # ---- the player: first person on a camera
    sc.add("CAMERA3D", "cam", [("FirstPersonController3D", {"joystick": "joy", "dragToLook": True, "eyeHeight": 1.65, "walkSpeed": 2.8, "runSpeed": 5,
                                                           "jumpSpeed": 0, "radius": 0.3, "headBob": 0.035, "footstepSound": "/footstep_1.ogg",
                                                           "footstepSound2": "/footstep_2.ogg", "stepVolume": 0.4})],
           x=0, y=2.65, z=20, Active=True, FOV=65, Near=0.05, Far=380)

    # ---- 2D layer
    sc.add("JOYSTICK", "joy", x=40, y=30, width=240, height=240, Pad_Image="/joy_pad.png", Button_Image="/joy_knob.png", z=120)
    sc.button("sitBtn", 1090, 170, 160, 160, "btn_seat.png")
    sc.text("sitText", 1060, 340, 220, 46, "Sit", scale=1.2)
    sc.button("gearBtn", 1196, 638, 64, 64, "btn_gear.png")
    sc.text("hintText", 24, 662, 620, 44, "Walk to the bench at the end of the pier", scale=1.0)

    write_event(proj, "public/OnCreate", [
        B("loopSound", "/ocean_waves.ogg", "true"), B("playSound", "/ocean_waves.ogg"),
        B("everySeconds", "9", yes=[B("playSound3D", "/seagull_1.ogg", "0.55")]),
        B("everySeconds", "14", yes=[B("playSound3D", "/seagull_2.ogg", "0.45")])])
    write_event(proj, "private/bench/behaviors/Seat3D/onSit", [B("showText", "hintText", q("Enjoy the view. Tap again to stand up"))])
    write_event(proj, "private/bench/behaviors/Seat3D/onStand", [B("showText", "hintText", q(""))])
    write_event(proj, "private/gearBtn/onClick", [B("showGraphicsMenu3D", "false")])

    write_project(folder, sc, {"Lighting Preset": "DAY", "Render Mode": "SIMPLE", "Sky": "IMAGE", "Sky Image": "/sky_kloofendal_puresky_1024x512.jpg",
                               "Shadows": True, "Shadow Distance": 35, "Quality": "MEDIUM"},
                  ("A wooden pier on a calm sea: customizable water waves, floating buoys and barrels, seagulls. Walk to the bench and tap Sit.",
                   "رصيف خشبي على بحر هادئ: أمواج ماء قابلة للتخصيص، عوامات وبراميل طافية ونوارس. امشِ إلى المقعد واضغط اجلس."),
                  credits_md("SeaDock3D", ["pirate", "water", "grave", "roads", "wave1", "gull", "steps", "sky"], files))
    return proj


# ====================================================================== translations of the template cards

CARDS = {
    "City3D": {
        "title": {"en": "3D City Drive", "ar": "قيادة في المدينة ثلاثية الأبعاد", "fr": "Conduite en ville 3D", "br": "Direção na cidade 3D",
                  "ru": "3D-поездка по городу", "es": "Conducción en la ciudad 3D"},
        "tag": {"en": "3D / Driving", "ar": "ثلاثي الأبعاد / قيادة", "fr": "3D / Conduite", "br": "3D / Direção", "ru": "3D / Вождение", "es": "3D / Conducción"},
        "desc": {"en": "Drive a car with real wheels through a small 3D city: joystick or pedals, follow camera, minimap camera view and traffic.",
                 "ar": "قُد سيارة بعجلات حقيقية في مدينة صغيرة ثلاثية الأبعاد: عصا تحكم أو دواسات، كاميرا تتبع، خريطة مصغرة من كاميرا وحركة مرور.",
                 "fr": "Conduisez une voiture aux vraies roues dans une petite ville 3D : joystick ou pédales, caméra qui suit, mini-carte et circulation.",
                 "br": "Dirija um carro com rodas de verdade por uma pequena cidade 3D: joystick ou pedais, câmera que segue, minimapa e trânsito.",
                 "ru": "Ведите машину с настоящими колёсами по маленькому 3D-городу: джойстик или педали, камера следования, мини-карта и трафик.",
                 "es": "Conduce un coche con ruedas reales por una pequeña ciudad 3D: joystick o pedales, cámara que sigue, minimapa y tráfico."}},
    "HorrorHouse3D": {
        "title": {"en": "3D Horror House", "ar": "بيت الرعب ثلاثي الأبعاد", "fr": "Maison hantée 3D", "br": "Casa do terror 3D", "ru": "3D-дом ужасов",
                  "es": "Casa del terror 3D"},
        "tag": {"en": "3D / Horror", "ar": "ثلاثي الأبعاد / رعب", "fr": "3D / Horreur", "br": "3D / Terror", "ru": "3D / Хоррор", "es": "3D / Terror"},
        "desc": {"en": "Walk around a house at night, find the key, open creaking doors, climb the stairs and explore with a flashlight. Inventory, sounds and screen effects.",
                 "ar": "تجوّل حول بيت في الليل، اعثر على المفتاح، افتح الأبواب الصريرة، اصعد الدرج واستكشف بالمصباح. حقيبة أغراض وأصوات وتأثيرات شاشة.",
                 "fr": "Faites le tour d'une maison la nuit, trouvez la clé, ouvrez les portes qui grincent, montez l'escalier et explorez à la lampe torche. Inventaire, sons et effets d'écran.",
                 "br": "Ande ao redor de uma casa à noite, encontre a chave, abra portas que rangem, suba a escada e explore com uma lanterna. Inventário, sons e efeitos de tela.",
                 "ru": "Обойдите дом ночью, найдите ключ, откройте скрипучие двери, поднимитесь по лестнице и исследуйте с фонариком. Инвентарь, звуки и эффекты экрана.",
                 "es": "Recorre una casa de noche, encuentra la llave, abre puertas que crujen, sube la escalera y explora con una linterna. Inventario, sonidos y efectos de pantalla."}},
    "EndlessRoad3D": {
        "title": {"en": "3D Endless Racer", "ar": "سباق لا نهائي ثلاثي الأبعاد", "fr": "Course infinie 3D", "br": "Corrida infinita 3D",
                  "ru": "Бесконечная 3D-гонка", "es": "Carrera infinita 3D"},
        "tag": {"en": "3D / Arcade", "ar": "ثلاثي الأبعاد / أركيد", "fr": "3D / Arcade", "br": "3D / Arcade", "ru": "3D / Аркада", "es": "3D / Arcade"},
        "desc": {"en": "Endless highway: dodge traffic, collect coins, speed grows with the distance. Score, distance and speed on the HUD.",
                 "ar": "طريق سريع لا نهائي: تفادَ السيارات واجمع العملات والسرعة تزيد مع المسافة. النقاط والمسافة والسرعة على الشاشة.",
                 "fr": "Autoroute sans fin : évitez la circulation, ramassez des pièces, la vitesse augmente avec la distance. Score, distance et vitesse à l'écran.",
                 "br": "Rodovia sem fim: desvie do trânsito, colete moedas, a velocidade cresce com a distância. Pontos, distância e velocidade na tela.",
                 "ru": "Бесконечное шоссе: уворачивайтесь от машин, собирайте монеты, скорость растёт с расстоянием. Очки, дистанция и скорость на экране.",
                 "es": "Autopista sin fin: esquiva el tráfico, recoge monedas, la velocidad crece con la distancia. Puntos, distancia y velocidad en pantalla."}},
    "SeaDock3D": {
        "title": {"en": "3D Sea Dock", "ar": "رصيف بحري ثلاثي الأبعاد", "fr": "Ponton en mer 3D", "br": "Píer no mar 3D", "ru": "3D-причал у моря",
                  "es": "Muelle en el mar 3D"},
        "tag": {"en": "3D / Relax", "ar": "ثلاثي الأبعاد / استرخاء", "fr": "3D / Détente", "br": "3D / Relaxar", "ru": "3D / Отдых", "es": "3D / Relax"},
        "desc": {"en": "A pier on a calm sea with customizable water simulation. Walk to the bench and tap Sit: the camera sits down and stands up again.",
                 "ar": "رصيف على بحر هادئ مع محاكاة ماء قابلة للتخصيص. امشِ إلى المقعد واضغط اجلس: تجلس الكاميرا ثم تقف مجدداً.",
                 "fr": "Un ponton sur une mer calme avec une simulation d'eau personnalisable. Allez jusqu'au banc et touchez S'asseoir : la caméra s'assoit puis se relève.",
                 "br": "Um píer em um mar calmo com simulação de água personalizável. Vá até o banco e toque em Sentar: a câmera senta e levanta de novo.",
                 "ru": "Причал на спокойном море с настраиваемой симуляцией воды. Подойдите к скамейке и нажмите «Сесть»: камера садится и снова встаёт.",
                 "es": "Un muelle en un mar en calma con simulación de agua personalizable. Camina hasta el banco y toca Sentarse: la cámara se sienta y se levanta de nuevo."}},
}


def write_translations():
    out = {}
    for folder, parts in CARDS.items():
        for part, langs in parts.items():
            out["tpl_%s_%s" % (folder, part)] = langs
    os.makedirs(os.path.dirname(I18N_OUT), exist_ok=True)
    json.dump(out, open(I18N_OUT, "w", encoding="utf-8"), indent=1, ensure_ascii=False)


# ====================================================================== validation

FOLDERS = ["City3D", "HorrorHouse3D", "EndlessRoad3D", "SeaDock3D"]
JAVA_WORDS = set("abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for "
                 "goto if implements import instanceof int interface long native new package private protected public return short static strictfp "
                 "super switch synchronized this throw throws transient try void volatile while true false null".split())
# local names inside generated handlers (event arguments, generated code) an element must not shadow
RESERVED = {"current", "args", "event", "body1", "body2", "car", "pickup", "who", "level", "itemId", "count", "on", "index", "laps", "turns", "__b", "__bhv"}


def behavior_params(cls):
    """name -> (java type, enum options or None) of the @Param fields of a library behavior."""
    path = os.path.join(LIB, cls + ".java")
    if not os.path.isfile(path):
        return None
    src = open(path, encoding="utf-8").read()
    enums = {m.group(1): [x.strip() for x in m.group(2).split(",") if x.strip()] for m in re.finditer(r"public enum (\w+)\s*\{([^}]*)\}", src)}
    out, pend = {}, False
    for line in src.split("\n"):
        s = line.strip()
        if s.startswith("@Param"):
            pend = True
            continue
        if pend and s.startswith("@"):
            continue
        if pend:
            m = re.match(r"public\s+(?:static\s+)?([\w<>\[\]\.]+)\s+(\w+)\s*(?:=.*)?;", s)
            if m:
                out[m.group(2)] = (m.group(1), enums.get(m.group(1)))
            pend = False
    return out


def behavior_events(cls):
    src = open(os.path.join(LIB, cls + ".java"), encoding="utf-8").read()
    m = re.search(r"events\s*=\s*\{([^}]*)\}", src)
    return [re.match(r'\s*"(\w+)\(', e).group(1) for e in m.group(1).split('",')] if m and m.group(1).strip() else []


def java_string(lit):
    return json.loads(lit)


def real_block_defs():
    """title -> (fields, code, color) of the shared blocks this script uses (DSL + BlockCatalog), read from the sources."""
    out = {}
    dsl = open(os.path.join(ROOT, "app/src/main/assets/java/nodes.java"), encoding="utf-8").read()
    for chunk in re.split(r"(?m)^\s*split\s*$", dsl):
        lines = [l for l in chunk.split("\n")]
        color, i = None, 0
        while i < len(lines):
            l = lines[i].strip()
            if not l or l.startswith("--"):
                i += 1
                continue
            if l.startswith("-color:"):
                color = argb(l[l.index("#") + 1:l.index("•")])
                i += 1
                continue
            break
        if color is None or i >= len(lines) or "<<=>>" not in chunk:
            continue
        header = lines[i].strip().split()
        sep = [k for k in range(i + 1, len(lines)) if lines[k].strip() == "<<=>>"][0]
        code = "\n".join(lines[sep + 1:]).rstrip("\n")
        title = header[0].replace("__star__if__", "")
        out[title] = (" ".join(header[1:]), code, color)
    cat = open(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/ui/blocks/BlockCatalog.java"), encoding="utf-8").read()
    colors = {m.group(1): int(m.group(2), 16) for m in re.finditer(r"(\w+) = 0x([0-9A-Fa-f]{8})", cat[cat.index("private static void addExtras"):][:400])}
    for m in re.finditer(r'def\("([^"]+)",\s*(\w+),\s*"([^"]+)",\s*"([^"]*)",\s*"((?:[^"\\]|\\.)*)",\s*(true|false),\s*("(?:[^"\\]|\\.)*")\);', cat):
        c = colors.get(m.group(2))
        out[m.group(3)] = (m.group(4), java_string(m.group(7)), (c - (1 << 32)) if c is not None and c >= (1 << 31) else c)
    reps = {}
    for m in re.finditer(r'r\("[^"]+",\s*("(?:[^"\\]|\\.)*"),\s*("(?:[^"\\]|\\.)*")\);', cat):
        reps[java_string(m.group(1))] = java_string(m.group(2))
    return out, reps


def class_path():
    def find(p):
        r = [x for x in glob.glob(os.path.expanduser(p), recursive=True) if "sources" not in x]
        return sorted(r)[-1] if r else None
    cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
    jars = [find(cache + "/com.badlogicgames.gdx/gdx/*/*/gdx-1*.jar"), find(cache + "/com.badlogicgames.gdx/gdx-box2d/*/*/gdx-box2d-1*.jar"),
            find(cache + "/com.badlogicgames.gdx/gdx-bullet/*/*/gdx-bullet-1*.jar"), find(cache + "/com.github.mgsx-dev.gdx-gltf/gltf/*/*/gltf-*.jar"),
            find(cache + "/com.google.code.gson/gson/*/*/gson-2*.jar"), find(cache + "/com.badlogicgames.box2dlights/box2dlights/*/*/box2dlights-*.jar")]
    classes = None
    for v in ("release/compileReleaseJavaWithJavac", "debug/compileDebugJavaWithJavac"):
        c = os.path.join(ROOT, "template/build/intermediates/javac", v, "classes")
        if os.path.isfile(os.path.join(c, "com/star4droid/template/d3/B3x.class")):
            classes = c
            break
    jars += glob.glob(os.path.join(ROOT, "app/libs/box2dlights-*.jar"))[:1]
    return classes, [j for j in jars if j]


def glb_node_names(path):
    g, _ = glb_read(path)
    return {n.get("name") for n in g["nodes"]}


def validate():
    problems, notes = [], []
    P = problems.append
    classes, jars = class_path()
    if not classes:
        print("template classes missing: run  bash gradlew :template:compileReleaseJavaWithJavac")
        return False
    cp = os.pathsep.join([classes] + jars)
    tmp = tempfile.mkdtemp(prefix="s2_examples3d_")
    helper = os.path.join(tmp, "helper")
    os.makedirs(helper)
    J = os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d")
    srcs = (glob.glob(J + "/behaviors/*.java") + glob.glob(J + "/blocks/*.java") +
            [J + "/ui/blocks/Blocks3D.java", J + "/ui/blocks/BlockModel.java", os.path.join(ROOT, "tools/examples3d_test/Examples3DCheck.java")])
    r = subprocess.run(["javac", "-nowarn", "-encoding", "UTF-8", "-d", helper, "-cp", cp] + srcs, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return False
    hcp = os.pathsep.join([helper, cp])
    dump = os.path.join(tmp, "blocks.json")
    r = subprocess.run(["java", "-cp", hcp, "Examples3DCheck", "blocks", dump], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return False
    real = json.load(open(dump, encoding="utf-8"))
    shared, shared_reps = real_block_defs()
    # ---- the block definitions of this script = the real ones
    for title, (src, fields, code, color, isif) in BLOCKS.items():
        if src in ("3d", "bhv"):
            d = real["defs"].get(title)
            if d is None:
                P("block %s is not a real %s block" % (title, src))
                continue
            if d["fields"] != fields or d["code"] != code or d["isIf"] != isif or (src == "3d" and d["color"] != color):
                P("block %s differs from its definition: %r / %r" % (title, d["fields"], d["code"]))
        else:
            d = shared.get(title)
            if d is None:
                P("block %s not found in nodes.java / BlockCatalog" % title)
            elif d[0] != fields or d[1] != code or d[2] != color:
                P("block %s differs from its definition: %r" % (title, d))
    for disp, tpl in REPORTERS.items():
        t = real["reporters"].get(disp, shared_reps.get(disp))
        if t != tpl:
            P("value block '%s' differs: %r" % (disp, t))
    settings_src = open(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/ui/Scene3DSettings.java"), encoding="utf-8").read()
    fields3d = re.findall(r'\{"([^"]+)", "(\w+)", "([^"]*)", "([^"]*)"\}', settings_src)

    player = os.path.join(ROOT, "app/src/main/assets/java/Player.java")
    for folder in FOLDERS:
        proj = os.path.join(EXAMPLES, folder)
        pre = folder + ": "
        els = json.load(open(os.path.join(proj, "scenes", SCENE), encoding="utf-8"))
        cfg = json.load(open(os.path.join(proj, "configs", SCENE + ".json"), encoding="utf-8"))
        names = [e["name"] for e in els]
        types = {e["name"]: e["TYPE"] for e in els}
        if cfg.get("sceneType") != "3D":
            P(pre + "config is not a 3D scene")
        for key, kind, dflt, choices in fields3d:
            if key not in cfg:
                P(pre + "config lacks " + key)
            elif kind == "choice" and cfg[key] not in choices.split(","):
                P(pre + "config %s=%s not in %s" % (key, cfg[key], choices))
        if not any(e["TYPE"] == "CAMERA3D" and e["Active"] == "true" for e in els):
            P(pre + "no active CAMERA3D")
        if sum(1 for e in els if e["TYPE"] == "CAMERA3D" and e["Active"] == "true") > 1:
            P(pre + "more than one active camera")
        if len(set(names)) != len(names):
            P(pre + "duplicate names")
        refs = set()
        for e in els:
            n = e["name"]
            missing = set(defaults(e["TYPE"])) - set(e)
            if missing:
                P(pre + "%s lacks default keys %s" % (n, sorted(missing)))
            if not re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", n) or n in JAVA_WORDS or n in RESERVED:
                P(pre + "bad element name " + n)
            if e["TYPE"] in ("BOX", "TEXT", "JOYSTICK", "CAMERAVIEW"):
                x, y, w, h = (float(e[k]) for k in ("x", "y", "width", "height"))
                if x < 0 or y < 0 or x + w > W or y + h > H:
                    P(pre + "%s is outside the %dx%d frame" % (n, W, H))
                if e["type"] != "UI":
                    P(pre + n + " should be a UI element")
            if e.get("parent") and e["parent"] not in types:
                P(pre + n + " has a missing parent")
            for key in ("Model", "Texture", "Normal Map", "Heightmap", "image", "Pressed Image", "Touch Start Image", "Touch End Image", "Pad Image", "Button Image"):
                v = e.get(key, "")
                if v:
                    folder_ = "models" if key == "Model" else "images"
                    f = os.path.join(proj, folder_, v.lstrip("/"))
                    refs.add(os.path.relpath(f, proj))
                    if not os.path.isfile(f):
                        P(pre + "%s.%s: missing file %s" % (n, key, v))
                    elif key == "Model" and e.get("Model Node") and e["Model Node"] not in glb_node_names(f):
                        P(pre + "%s: model %s has no node %s" % (n, v, e["Model Node"]))
            for k in ("Camera", "Follow", "Attach To"):
                if e.get(k) and e[k] not in types:
                    P(pre + "%s.%s refers to missing %s" % (n, k, e[k]))
            if e["TYPE"] == "CAMERAVIEW" and e["Camera"] and types.get(e["Camera"]) != "CAMERA3D":
                P(pre + n + ": Camera must be a CAMERA3D")
            for b in json.loads(e["behaviors"]) if e.get("behaviors") else []:
                spec = behavior_params(b["type"])
                if spec is None:
                    P(pre + "%s: unknown behavior %s" % (n, b["type"]))
                    continue
                for k, v in b["params"].items():
                    if k not in spec:
                        P(pre + "%s: %s has no @Param %s" % (n, b["type"], k))
                        continue
                    jt, opts = spec[k]
                    is_list = jt.startswith("List<") or jt.endswith("[]")
                    base = re.sub(r"List<(\w+)>|(\w+)\[\]", lambda m: m.group(1) or m.group(2), jt)
                    vals = v if is_list else [v]
                    if is_list != isinstance(v, list):
                        P(pre + "%s: %s.%s list mismatch" % (n, b["type"], k))
                    for x in vals:
                        if base in ("PlayerItem", "Joystick", "ProgressItem", "TextItem"):
                            if x not in types:
                                P(pre + "%s: %s.%s -> missing element %s" % (n, b["type"], k, x))
                            elif base == "Joystick" and types[x] != "JOYSTICK":
                                P(pre + "%s: %s.%s needs a JOYSTICK" % (n, b["type"], k))
                        elif opts is not None:
                            if x not in opts:
                                P(pre + "%s: %s.%s=%s not in %s" % (n, b["type"], k, x, opts))
                        elif base in ("float", "int", "double"):
                            if not isinstance(x, (int, float)) or isinstance(x, bool):
                                P(pre + "%s: %s.%s must be a number" % (n, b["type"], k))
                        elif base == "boolean":
                            if not isinstance(x, bool):
                                P(pre + "%s: %s.%s must be true/false" % (n, b["type"], k))
                        elif base == "String":
                            if not isinstance(x, str):
                                P(pre + "%s: %s.%s must be text" % (n, b["type"], k))
                            elif re.search(r"\.(ogg|mp3|wav)$", x):
                                f = os.path.join(proj, "sounds", x.lstrip("/"))
                                refs.add(os.path.relpath(f, proj))
                                if not os.path.isfile(f):
                                    P(pre + "%s: %s.%s missing sound %s" % (n, b["type"], k, x))
                # events of the behavior
                d = os.path.join(proj, "Events", SCENE, "private", n, "behaviors", b["type"])
                if os.path.isdir(d):
                    evs = behavior_events(b["type"])
                    for f in os.listdir(d):
                        if os.path.splitext(f)[0] not in evs:
                            P(pre + "%s: %s has no event %s" % (n, b["type"], f))
        if cfg.get("Sky Image"):
            f = os.path.join(proj, "images", cfg["Sky Image"].lstrip("/"))
            refs.add(os.path.relpath(f, proj))
            if not os.path.isfile(f):
                P(pre + "missing sky image")
        # event code: sounds and block titles
        for vf in glob.glob(os.path.join(proj, "Events", "**", "*.visual"), recursive=True):
            for node in json.load(open(vf, encoding="utf-8")):
                if node["title"] != "First >" and node["title"] not in BLOCKS:
                    P(pre + "%s uses the unknown block %s" % (vf, node["title"]))
        for jf in glob.glob(os.path.join(proj, "Events", "**", "*.java"), recursive=True):
            code = open(jf, encoding="utf-8").read()
            for m in re.finditer(r'"(/?[\w\-]+\.(?:ogg|mp3|wav))"', code):
                f = os.path.join(proj, "sounds", m.group(1).lstrip("/"))
                refs.add(os.path.relpath(f, proj))
                if not os.path.isfile(f):
                    P(pre + "%s: missing sound %s" % (os.path.relpath(jf, proj), m.group(1)))
            for name in re.findall(r"\b([A-Za-z_]\w*)\b", code):
                pass
        # shipped files that nothing uses
        for kind in ("models", "sounds", "images"):
            for f in glob.glob(os.path.join(proj, kind, "*")):
                rel = os.path.relpath(f, proj)
                if rel not in refs:
                    P(pre + "unused file " + rel)
        size = sum(os.path.getsize(f) for f in glob.glob(os.path.join(proj, "**"), recursive=True) if os.path.isfile(f))
        notes.append("%s: %d elements, %.2f MB" % (folder, len(els), size / 1048576.0))
        if size > MAX_BYTES:
            P(pre + "project is %.2f MB (limit 2.5)" % (size / 1048576.0))
        # ---- Java of the scene: generated like the editor, then compiled
        gen = os.path.join(tmp, folder, "com/star4droid/Game/scene1.java")
        r = subprocess.run(["java", "-cp", hcp, "Examples3DCheck", "project", proj, player, gen], capture_output=True, text=True)
        out = "\n".join(l for l in (r.stdout + r.stderr).split("\n") if l.strip() and not l.startswith("Picked up"))
        if r.returncode != 0:
            P(pre + "project check:\n" + out)
            continue
        cls = os.path.join(tmp, folder, "out")
        os.makedirs(cls, exist_ok=True)
        r = subprocess.run(["javac", "-nowarn", "--release", "8", "-encoding", "UTF-8", "-d", cls, "-cp", cp, gen], capture_output=True, text=True)
        if r.returncode != 0:
            P(pre + "the generated scene does not compile:\n" + r.stdout + r.stderr)
        elif re.search(r"->|::", open(gen, encoding="utf-8").read()):
            P(pre + "lambda / method reference in the scene code (the phone compiles Java 7)")
        else:
            notes.append("%s: scene Java compiles (%d lines)" % (folder, open(gen, encoding="utf-8").read().count("\n")))
    for n in notes:
        print("  " + n)
    if problems:
        print("VALIDATION FAILED (%d):" % len(problems))
        for p in problems:
            print(" - " + p)
        return False
    print("VALIDATION OK")
    return True


def main():
    args = sys.argv[1:]
    if "--check" not in args:
        for build in (build_city, build_horror, build_endless, build_sea):
            p = build()
            print("generated", os.path.relpath(p, ROOT))
        write_translations()
        print("translations ->", I18N_OUT)
    ok = validate()
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
