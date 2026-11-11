#!/usr/bin/env python3
"""(Re)generates the example projects of the path finding and network systems and checks them (no phone needed).

    python3 tools/gen_systems_examples.py            generate + validate
    python3 tools/gen_systems_examples.py --check    validate only

MazeChase (2D, landscape 1280x720)
    scene1   top-down maze painted on a square NavGrid: tap where to go (the player walks the shortest way), two "Smart Chase"
             ghosts, a guard walking a patrol path drawn as a path variable, a key that opens a door (the door is an obstacle of
             the grid: when it is deleted its cells open and the ghosts find new ways), a star behind the door.
    hexmaze  the same game on a hexagon NavGrid in an open cave of rocks (cells under the rocks blocked by "Use Static Bodies").
WifiStars (2D, landscape 1280x720)
    scene1   lobby made of blocks: host, search, the rooms found, the players, start (host), leave.
    arena    each phone moves its own colored ball with a joystick (copies of the player template "ball"), the host spawns the star
             and counts the points (a synced element variable "score"), first to 10 wins; a player or the host leaving is shown.

Every event is made of real blocks (.visual + the .java they export). Validation: element keys of their type, blocks = their
definitions (Blocks3D / BlocksSystems / nodes.java / BlockCatalog, dumped by tools/examples_sys_test/SystemsCheck.java), the Java the
editor would generate for every scene (object variables, Network cards, the new scene events) compiles against the template classes
and against the phone class path (assets/java/game.zip), every image file exists and is used.
Needs: bash gradlew :template:compileDebugJavaWithJavac (or Release) and the gradle cache (gdx, gson, box2d...).
"""
import glob, json, math, os, random, re, shutil, subprocess, sys, tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_3d_examples as g  # noqa: E402
from PIL import Image, ImageDraw, ImageFilter  # noqa: E402

ROOT, EXAMPLES = g.ROOT, g.EXAMPLES
W, H = 1280, 720
I18N_OUT = os.environ.get("I18N_STAGING_SYS_EXAMPLES", "/home/claude/i18n_staging/examples_systems.json")
g.TYPE_FILE.update({"CIRCLE": "circle", "NAVGRID": "navgrid", "NETWORK": "network"})
# keys the editor keeps out of a NavGrid / Network element (EditorNavGridItem.UNUSED)
SYS_UNUSED = ["rotation", "Pivot", "Pivot X", "Pivot Y", "type", "behaviors", "scriptVars", "objectVars", "net"]
C_BLOCKS = {"if", "ifThen", "ifTouching", "waitSeconds", "everySeconds", "repeatTimes"}
SHARED_USED = ["setGravity", "ifTouching", "ifThen", "if", "setVariable", "changeVariable", "showText", "setVisibility", "hide", "show",
               "OpenScene", "waitSeconds", "everySeconds", "repeatTimes", "setLinearVelocity", "setTint", "destroyAfter", "newVariable", "Toast",
               "changeObjectVariable", "setObjectVariable", "stopMoving"]
SHARED_REPS = ["touch x", "touch y", "screen is touched", "not ___", "___ is named ___", "join ___ ___", "___ + ___", "___ * ___", "___ - ___",
               "___ > ___", "___ >= ___", "___ < ___", "___ == ___", "___ != ___", "___ and ___", "___ equals ___", "random ___ to ___",
               "joystick x ___", "joystick y ___", "number variable ___ of ___", "round ___", "true", "false"]


# ====================================================================== blocks (real definitions)

def tool_classpath():
    """gen_3d_examples' class path, with the newest template classes that have the path finding / timeline / network runtime."""
    classes, jars = g.class_path()
    best = None
    for v in ("debug/compileDebugJavaWithJavac", "release/compileReleaseJavaWithJavac"):
        c = os.path.join(ROOT, "template/build/intermediates/javac", v, "classes")
        mark = os.path.join(c, "com/star4droid/template/nav/NavShapes.class")
        if os.path.isfile(mark) and (best is None or os.path.getmtime(mark) > os.path.getmtime(os.path.join(best, "com/star4droid/template/nav/NavShapes.class"))):
            best = c
    return best, jars


_HELPER = {}


def helper():
    """Compiles SystemsCheck (+ the Android-free editor classes it needs) once; returns (class path, temp dir)."""
    if _HELPER:
        return _HELPER["cp"], _HELPER["tmp"]
    classes, jars = tool_classpath()
    if not classes:
        raise SystemExit("template classes missing: run  bash gradlew :template:compileDebugJavaWithJavac")
    cp = os.pathsep.join([classes] + jars)
    tmp = tempfile.mkdtemp(prefix="s2_sys_examples_")
    out = os.path.join(tmp, "helper")
    os.makedirs(out)
    J = os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d")
    srcs = (glob.glob(J + "/behaviors/*.java") + glob.glob(J + "/blocks/*.java") +
            [J + "/ui/blocks/Blocks3D.java", J + "/ui/blocks/BlocksSystems.java", J + "/ui/blocks/BlockModel.java",
             os.path.join(ROOT, "tools/examples_sys_test/SystemsCheck.java")])
    r = subprocess.run(["javac", "-nowarn", "-encoding", "UTF-8", "-d", out, "-cp", cp] + srcs, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        raise SystemExit("SystemsCheck does not compile")
    _HELPER.update(cp=os.pathsep.join([out, cp]), tmp=tmp, rawcp=cp)
    return _HELPER["cp"], tmp


def load_blocks():
    """Adds the real definitions of every block / value block the examples use to gen_3d_examples' tables."""
    hcp, tmp = helper()
    dump = os.path.join(tmp, "blocks.json")
    r = subprocess.run(["java", "-cp", hcp, "SystemsCheck", "blocks", dump], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        raise SystemExit("block dump failed")
    real = json.load(open(dump, encoding="utf-8"))
    shared, shared_reps = g.real_block_defs()
    for t in SHARED_USED:
        fields, code, color = shared[t]
        g.BLOCKS[t] = ("dsl", fields, code, color, t in C_BLOCKS)
    for t, d in real["defs"].items():
        if d.get("category") in ("Navigation", "Timeline", "Network"):
            g.BLOCKS[t] = ("sys", d["fields"], d["code"], d["color"], d["isIf"])
    ws = "com.star4droid.template.Items.StageImp.WORLD_SCALE"
    shared_reps.setdefault("touch x", "(getTouch().x/" + ws + ")")  # BlockCatalog builds these two from a constant
    shared_reps.setdefault("touch y", "(getTouch().y/" + ws + ")")
    cat = open(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/ui/blocks/BlockCatalog.java"), encoding="utf-8").read()
    assert 'r("Input", "touch x", "(getTouch().x/" + WS + ")");' in cat and 'String WS = "%s"' % ws in cat, "touch x / y changed"
    for disp in SHARED_REPS:
        g.REPORTERS[disp] = shared_reps[disp]
    for disp, tpl in real["reporters"].items():
        if "com.star4droid.template.nav." in tpl or "com.star4droid.template.anim." in tpl or "com.star4droid.template.net." in tpl:
            g.REPORTERS[disp] = tpl
    return real


B, R, q = g.B, g.R, g.q


def C(display, *inner):
    return R(display, *inner)


# ====================================================================== images (drawn here, CC0)

def save(img, path, size=None):
    if size:
        img = img.resize(size, Image.LANCZOS)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


def canvas(w, h, ss=4):
    return Image.new("RGBA", (w * ss, h * ss), (0, 0, 0, 0)), ss


def img_wall(path):
    """40 x 40 stone tile (seamless): blue-grey bricks."""
    s = 4
    im = Image.new("RGBA", (40 * s, 40 * s), (58, 66, 84, 255))
    d = ImageDraw.Draw(im)
    rnd = random.Random(7)
    rows = [(0, 20), (20, 40)]
    for i, (y0, y1) in enumerate(rows):
        off = 0 if i == 0 else 10
        for x in range(-20 + off, 40, 20):
            c = 72 + rnd.randint(-6, 8)
            d.rounded_rectangle([(x + 1) * s, (y0 + 1) * s, (x + 19) * s, (y1 - 1) * s], radius=3 * s, fill=(c, c + 8, c + 26, 255))
            d.line([(x + 2) * s, (y0 + 2) * s, (x + 17) * s, (y0 + 2) * s], fill=(110, 120, 145, 255), width=s)
    save(im, path, (40, 40))


def img_ball(path, color, eyes=True, size=64):
    im, s = canvas(size, size)
    d = ImageDraw.Draw(im)
    r = size * s / 2 - 2 * s
    cx = cy = size * s / 2
    d.ellipse([cx - r, cy - r + 2 * s, cx + r, cy + r + 2 * s], fill=(0, 0, 0, 70))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (255,))
    hl = tuple(min(255, c + 60) for c in color)
    d.ellipse([cx - r * 0.55, cy - r * 0.7, cx + r * 0.1, cy - r * 0.15], fill=hl + (150,))
    if eyes:
        for ex in (-0.32, 0.32):
            d.ellipse([cx + r * ex - r * 0.2, cy - r * 0.15 - r * 0.2, cx + r * ex + r * 0.2, cy - r * 0.15 + r * 0.2], fill=(255, 255, 255, 255))
            d.ellipse([cx + r * ex - r * 0.09, cy - r * 0.13 - r * 0.09, cx + r * ex + r * 0.09, cy - r * 0.13 + r * 0.09], fill=(20, 24, 32, 255))
    save(im, path, (size, size))


def img_ghost(path, color):
    im, s = canvas(64, 64)
    d = ImageDraw.Draw(im)
    w = 64 * s
    d.pieslice([6 * s, 4 * s, w - 6 * s, 58 * s], 180, 360, fill=color + (255,))
    d.rectangle([6 * s, 31 * s, w - 6 * s, 52 * s], fill=color + (255,))
    for i in range(4):
        x0 = 6 * s + i * (w - 12 * s) / 4
        d.pieslice([x0, 44 * s, x0 + (w - 12 * s) / 4, 60 * s], 0, 180, fill=color + (255,))
    for ex in (22, 42):
        d.ellipse([(ex - 7) * s, 20 * s, (ex + 7) * s, 36 * s], fill=(255, 255, 255, 255))
        d.ellipse([(ex - 3) * s, 26 * s, (ex + 4) * s, 34 * s], fill=(30, 50, 140, 255))
    save(im, path, (64, 64))


def img_star(path, color=(255, 210, 60), size=64):
    im, s = canvas(size, size)
    d = ImageDraw.Draw(im)
    c = size * s / 2
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = (size * s * 0.46) if i % 2 == 0 else (size * s * 0.2)
        pts.append((c + math.cos(a) * r, c + math.sin(a) * r + 2 * s))
    d.polygon(pts, fill=(0, 0, 0, 60))
    pts = [(x, y - 2 * s) for x, y in pts]
    d.polygon(pts, fill=color + (255,), outline=(255, 245, 200, 255))
    save(im, path, (size, size))


def img_key(path):
    im, s = canvas(64, 64)
    d = ImageDraw.Draw(im)
    gold = (255, 196, 40, 255)
    d.ellipse([6 * s, 20 * s, 30 * s, 44 * s], outline=gold, width=7 * s)
    d.rectangle([28 * s, 29 * s, 58 * s, 35 * s], fill=gold)
    d.rectangle([46 * s, 35 * s, 51 * s, 44 * s], fill=gold)
    d.rectangle([54 * s, 35 * s, 58 * s, 42 * s], fill=gold)
    save(im, path, (64, 64))


def img_door(path):
    im, s = canvas(40, 40)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 40 * s, 40 * s], fill=(120, 72, 36, 255))
    for x in (10, 20, 30):
        d.line([x * s, 0, x * s, 40 * s], fill=(90, 52, 24, 255), width=2 * s)
    d.rectangle([2 * s, 2 * s, 38 * s, 38 * s], outline=(200, 160, 60, 255), width=2 * s)
    d.ellipse([27 * s, 18 * s, 33 * s, 24 * s], fill=(240, 200, 70, 255))
    save(im, path, (40, 40))


def img_ring(path, color=(120, 220, 255)):
    im, s = canvas(48, 48)
    d = ImageDraw.Draw(im)
    d.ellipse([4 * s, 4 * s, 44 * s, 44 * s], outline=color + (230,), width=4 * s)
    d.ellipse([18 * s, 18 * s, 30 * s, 30 * s], fill=color + (200,))
    save(im, path, (48, 48))


def img_rock(path, seed):
    rnd = random.Random(seed)
    im, s = canvas(96, 96)
    d = ImageDraw.Draw(im)
    c = 48 * s
    pts = []
    for i in range(11):
        a = i * 2 * math.pi / 11
        r = (40 + rnd.uniform(-4, 4)) * s
        pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
    d.polygon([(x + 3 * s, y + 4 * s) for x, y in pts], fill=(0, 0, 0, 80))
    d.polygon(pts, fill=(104, 96, 88, 255))
    d.polygon([(x * 0.7 + c * 0.3 - 6 * s, y * 0.7 + c * 0.3 - 8 * s) for x, y in pts], fill=(134, 124, 114, 255))
    save(im, path, (96, 96))


def img_button(path, w, h, color):
    im, s = canvas(w, h)
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 3 * s, w * s, h * s], radius=16 * s, fill=tuple(max(0, c - 60) for c in color) + (255,))
    d.rounded_rectangle([0, 0, w * s, (h - 4) * s], radius=16 * s, fill=color + (255,))
    d.rounded_rectangle([6 * s, 4 * s, w * s - 6 * s, (h * 0.45) * s], radius=12 * s, fill=(255, 255, 255, 40))
    save(im, path, (w, h))


def img_panel(path, w, h, color=(20, 26, 38), alpha=200):
    im, s = canvas(w, h)
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, w * s, h * s], radius=18 * s, fill=color + (alpha,), outline=(255, 255, 255, 50), width=2 * s)
    save(im, path, (w, h))


def img_floor(path, w, h, base=(28, 32, 44)):
    """A dark floor with soft dots (top-down)."""
    im = Image.new("RGBA", (w, h), base + (255,))
    d = ImageDraw.Draw(im)
    rnd = random.Random(3)
    for _ in range(int(w * h / 900)):
        x, y = rnd.randint(0, w), rnd.randint(0, h)
        c = base[0] + rnd.randint(4, 12)
        d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(c, c + 4, c + 14, 255))
    im = im.filter(ImageFilter.GaussianBlur(0.6))
    save(im, path)


def img_arena(path, w, h):
    im = Image.new("RGBA", (w, h), (18, 40, 52, 255))
    d = ImageDraw.Draw(im)
    for x in range(0, w, 64):
        d.line([x, 0, x, h], fill=(26, 58, 72, 255), width=2)
    for y in range(0, h, 64):
        d.line([0, y, w, y], fill=(26, 58, 72, 255), width=2)
    d.rectangle([4, 4, w - 5, h - 5], outline=(80, 200, 220, 255), width=6)
    save(im, path)


def img_joy_pad(path):
    im, s = canvas(200, 200)
    d = ImageDraw.Draw(im)
    d.ellipse([4 * s, 4 * s, 196 * s, 196 * s], fill=(255, 255, 255, 40), outline=(255, 255, 255, 140), width=5 * s)
    save(im, path, (200, 200))


def img_joy_knob(path):
    im, s = canvas(96, 96)
    d = ImageDraw.Draw(im)
    d.ellipse([4 * s, 4 * s, 92 * s, 92 * s], fill=(255, 255, 255, 170), outline=(255, 255, 255, 230), width=4 * s)
    save(im, path, (96, 96))


# ====================================================================== helpers

def sys_element(sc, tp, name, **props):
    e = sc.add(tp, name, **props)
    for k in SYS_UNUSED:
        e.pop(k, None)
    return e


def scene_config(color, orientation="landscape"):
    return {"sceneColor": color, "logicWidth": float(W), "logicHeight": float(H), "or": "" if orientation == "landscape" else "portrait",
            "Ambient Light": "#FFFFFF"}


def write_scene(proj, scene, sc, color):
    sc.finish()
    os.makedirs(os.path.join(proj, "configs"), exist_ok=True)
    os.makedirs(os.path.join(proj, "scenes"), exist_ok=True)
    json.dump(scene_config(color), open(os.path.join(proj, "configs", scene + ".json"), "w"))
    json.dump(sc.els, open(os.path.join(proj, "scenes", scene), "w", encoding="utf-8"), indent=1, ensure_ascii=False)


def event(proj, scene, rel, chain):
    g.SCENE = scene
    g.write_event(proj, rel, chain)


def variables(proj, scene, vs):
    g.SCENE = scene
    g.write_variables(proj, vs)


def text(sc, name, x, y, w, h, t, scale=1.0, color="#FFFFFF", z=110, align="CENTER", visible=True):
    return sc.add("TEXT", name, x=x, y=y, width=w, height=h, z=z, Text=t, Font_Scale=scale, Text_Color=color, Text_Align=align,
                  Auto_Size="false", Visible="true" if visible else "false")


def button(sc, name, x, y, w, h, image, label, scale=1.0, z=100, visible=True):
    sc.add("BOX", name, x=x, y=y, width=w, height=h, z=z, image=image, Collider_Width=w, Collider_Height=h, Visible="true" if visible else "false")
    text(sc, name + "Text", x, y + 2, w, h - 4, label, scale=scale, z=z + 10, visible=visible)


def encode_cells(cols, rows, wall):
    """NavGrid.encode: 'v1:cols,rows:' + runs (o open, x wall), cells from the bottom row up."""
    out, run, count, any_wall = [], None, 0, False
    for r in range(rows):
        for c in range(cols):
            ch = "x" if wall(c, r) else "o"
            any_wall |= ch == "x"
            if ch == run:
                count += 1
            else:
                if count:
                    out.append("%d%s" % (count, run))
                run, count = ch, 1
    if count:
        out.append("%d%s" % (count, run))
    return ("v1:%d,%d:" % (cols, rows) + "".join(out)) if any_wall else ""


# ====================================================================== MazeChase

CELL, COLS, ROWS = 40, 32, 18


def make_maze(seed):
    """Rows (top first) of '#' walls and '.' floor, 32 x 18: a random maze with a few loops (so chasers can be avoided)."""
    rnd = random.Random(seed)
    nw, nh = 15, 8
    grid = [["#"] * 31 for _ in range(17)]
    seen = {(0, 0)}
    grid[1][1] = "."
    stack = [(0, 0)]
    while stack:
        x, y = stack[-1]
        nb = [(x + dx, y + dy, dx, dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
              if 0 <= x + dx < nw and 0 <= y + dy < nh and (x + dx, y + dy) not in seen]
        if not nb:
            stack.pop()
            continue
        nx, ny, dx, dy = rnd.choice(nb)
        grid[y * 2 + 1 + dy][x * 2 + 1 + dx] = "."
        grid[ny * 2 + 1][nx * 2 + 1] = "."
        seen.add((nx, ny))
        stack.append((nx, ny))
    cand = []
    for r in range(1, 16):
        for c in range(1, 30):
            if grid[r][c] != "#":
                continue
            h = grid[r][c - 1] == "." and grid[r][c + 1] == "." and grid[r - 1][c] == "#" and grid[r + 1][c] == "#"
            v = grid[r - 1][c] == "." and grid[r + 1][c] == "." and grid[r][c - 1] == "#" and grid[r][c + 1] == "#"
            if h or v:
                cand.append((r, c))
    rnd.shuffle(cand)
    for r, c in cand[:16]:
        grid[r][c] = "."
    return ["".join(row) + "#" for row in grid] + ["#" * 32]


def open_cells(rows):
    return {(c, ROWS - 1 - r) for r, line in enumerate(rows) for c, ch in enumerate(line) if ch != "#"}


def neighbours(cell, cells):
    c, r = cell
    return [n for n in ((c + 1, r), (c - 1, r), (c, r + 1), (c, r - 1)) if n in cells]


def bfs(start, cells, blocked=()):
    dist = {start: 0}
    prev = {start: None}
    todo = [start]
    for cur in todo:
        for n in neighbours(cur, cells):
            if n in dist or n in blocked:
                continue
            dist[n] = dist[cur] + 1
            prev[n] = cur
            todo.append(n)
    return dist, prev


def path_to(prev, goal):
    out = []
    while goal is not None:
        out.append(goal)
        goal = prev[goal]
    return out[::-1]


def wall_rects(rows):
    """Wall cells merged into rectangles (runs per row, then equal runs of the next rows)."""
    walls = {(c, ROWS - 1 - r) for r, line in enumerate(rows) for c, ch in enumerate(line) if ch == "#"}
    rects, used = [], set()
    for r in range(ROWS):
        c = 0
        while c < COLS:
            if (c, r) not in walls or (c, r) in used:
                c += 1
                continue
            c1 = c
            while c1 + 1 < COLS and (c1 + 1, r) in walls and (c1 + 1, r) not in used:
                c1 += 1
            r1 = r
            while r1 + 1 < ROWS and all((x, r1 + 1) in walls and (x, r1 + 1) not in used for x in range(c, c1 + 1)):
                r1 += 1
            for y in range(r, r1 + 1):
                for x in range(c, c1 + 1):
                    used.add((x, y))
            rects.append((c, r, c1 - c + 1, r1 - r + 1))
            c = c1 + 1
    return rects, walls


def center(cell):
    return cell[0] * CELL + CELL / 2, cell[1] * CELL + CELL / 2


def plan_maze():
    """The maze, the places of the player, ghosts, key, door, star and the guard's patrol route (all checked by searches)."""
    for seed in range(11, 200):
        rows = make_maze(seed)
        cells = open_cells(rows)
        start = (1, ROWS - 2)
        dist, prev = bfs(start, cells)
        if len(dist) != len(cells):
            continue
        # a branch: dead end ... junction; the door is its cell next to the junction, the star its dead end
        best = None
        for dead in cells:
            if len(neighbours(dead, cells)) != 1 or dist[dead] < 28:
                continue
            branch, cur, last = [dead], dead, None
            while True:
                nb = [n for n in neighbours(cur, cells) if n != last]
                if len(nb) != 1:
                    break
                last, cur = cur, nb[0]
                if len(neighbours(cur, cells)) >= 3:
                    break
                branch.append(cur)
            if len(branch) < 3:
                continue
            door = branch[-1]
            d2, _ = bfs(start, cells, blocked={door})
            behind = [c for c in cells if c not in d2 and c != door]
            if set(behind) != set(branch[:-1]):
                continue
            score = dist[dead] + len(branch) * 2
            if best is None or score > best[0]:
                best = (score, dead, door, branch)
        if best is None:
            continue
        _, star, door, branch = best
        free = [c for c in cells if c not in branch and c != start]
        d_from_star, _ = bfs(star, cells)
        key = max((c for c in free if len(neighbours(c, cells)) == 1 and dist[c] > 16), key=lambda c: dist[c] + d_from_star.get(c, 0) * 0.5, default=None)
        if key is None:
            continue
        far = sorted((c for c in free if c != key), key=lambda c: -dist[c])
        ghost1 = far[0]
        ghost2 = next(c for c in far if abs(c[0] - ghost1[0]) + abs(c[1] - ghost1[1]) > 14)
        # guard: back and forth between two cells about 12 steps apart, in the middle of the maze
        mid = min(free, key=lambda c: abs(c[0] - COLS / 2) + abs(c[1] - ROWS / 2))
        dm, pm = bfs(mid, cells, blocked=set(branch) | {door})
        end = max((c for c in dm if 9 <= dm[c] <= 13), key=lambda c: abs(c[0] - mid[0]) + abs(c[1] - mid[1]), default=None)
        if end is None:
            continue
        route = path_to(pm, end)
        # corners only (the path finder walks straight between them)
        pts = [route[0]]
        for i in range(1, len(route) - 1):
            a, b, c = route[i - 1], route[i], route[i + 1]
            if (b[0] - a[0], b[1] - a[1]) != (c[0] - b[0], c[1] - b[1]):
                pts.append(b)
        pts.append(route[-1])
        if any(abs(p[0] - start[0]) + abs(p[1] - start[1]) < 8 for p in route):
            continue
        return dict(seed=seed, rows=rows, cells=cells, start=start, star=star, door=door, key=key, ghost1=ghost1, ghost2=ghost2, guard=pts)
    raise SystemExit("no maze found")


def build_maze_chase():
    folder = "MazeChase"
    proj = g.fresh(folder)
    img = lambda n: os.path.join(proj, "images", n)  # noqa: E731
    img_wall(img("wall.png"))
    img_ball(img("player.png"), (66, 165, 245))
    img_ghost(img("ghost_red.png"), (239, 83, 80))
    img_ghost(img("ghost_pink.png"), (240, 98, 146))
    img_ghost(img("guard.png"), (255, 167, 38))
    img_key(img("key.png"))
    img_door(img("door.png"))
    img_star(img("star.png"))
    img_ring(img("marker.png"))
    img_rock(img("rock1.png"), 1)
    img_rock(img("rock2.png"), 2)
    img_button(img("btn_blue.png"), 220, 64, (33, 150, 243))
    img_panel(img("panel.png"), 520, 70)
    img_floor(img("floor.png"), 640, 360)
    plan = plan_maze()
    build_square(proj, plan)
    build_hex(proj)
    open(os.path.join(EXAMPLES, folder + ".txt"), "w", encoding="utf-8").write(CARDS[folder]["desc"]["en"])
    open(os.path.join(EXAMPLES, folder + ".ar.txt"), "w", encoding="utf-8").write(CARDS[folder]["desc"]["ar"])
    open(os.path.join(proj, "CREDITS.md"), "w", encoding="utf-8").write(
        "# MazeChase - credits\n\nEvery image of this example was drawn for Star2D by tools/gen_systems_examples.py (CC0: free to use, change and ship).\n")
    return proj


def hud(sc, hint):
    """Floor, info panel, hint text and the scene button (shared by both scenes)."""
    text(sc, "info", 380, 640, 520, 60, hint, scale=1.0, z=120)
    sc.add("BOX", "infoPanel", x=380, y=640, width=520, height=60, z=115, image="/panel.png", Collider_Width=520, Collider_Height=60)


def maze_events(proj, scene, other_scene, other_label, speed_player, door_name="door"):
    """Blocks of a MazeChase scene: tap to walk, the key opens the door, ghosts catch, the star wins."""
    goal_msg = q("You found the star! Again in 3 s...")
    caught = q("Caught! Again in 2 s...")
    event(proj, scene, "public/OnCreate", [
        B("setGravity", "0", "0"),
        B("hide", "marker"),
    ])
    # tap: walk to the finger (only when the finger touches the screen, not every frame while it is held)
    event(proj, scene, "public/OnStep", [
        B("ifTouching", yes=[
            B("ifThen", C("not ___", "held"), yes=[
                B("setVariable", "held", "true"),
                B("ifThen", C("not ___", "over"), yes=[
                    B("navGoTo", "player", C("touch x"), C("touch y"), str(speed_player), q("")),
                    B("navPlace", "marker", C("touch x"), C("touch y")),
                    B("show", "marker"),
                ]),
            ]),
        ], no=[B("setVariable", "held", "false")]),
    ])
    event(proj, scene, "public/onPathEnd", [
        B("ifThen", C("___ is named ___", "current", "player"), yes=[B("hide", "marker")]),
    ])
    event(proj, scene, "public/onNoPath", [
        B("ifThen", C("___ is named ___", "current", "player"), yes=[B("showText", "info", q("No way there!")), B("hide", "marker")]),
    ])
    variables(proj, scene, [("held", "boolean", "false"), ("over", "boolean", "false")])
    # key: the door goes away, its cells open, the ghosts find the new ways by themselves
    event(proj, scene, "private/key/onBodyCollided", [
        B("ifThen", C("___ is named ___", "body2", "player"), yes=[
            B("hide", "key"),
            # removed a moment later: a body must not be deleted while the physics step reports the collision
            B("destroyAfter", "key", "0.05"),
            B("destroyAfter", door_name, "0.05"),
            B("showText", "info", q("The door is open: find the star!")),
        ]),
    ])
    event(proj, scene, "private/star/onBodyCollided", [
        B("ifThen", C("___ and ___", C("___ is named ___", "body2", "player"), C("not ___", "over")), yes=[
            B("setVariable", "over", "true"),
            B("showText", "info", goal_msg),
            B("navStop", "player"),
            B("stopMoving", "player"),
            B("waitSeconds", "3", yes=[B("OpenScene", scene)]),
        ]),
    ])
    for enemy in ("ghost1", "ghost2", "guard"):
        event(proj, scene, "private/%s/onBodyCollided" % enemy, [
            B("ifThen", C("___ and ___", C("___ is named ___", "body2", "player"), C("not ___", "over")), yes=[
                B("setVariable", "over", "true"),
                B("showText", "info", caught),
                B("navStop", "player"),
                B("stopMoving", "player"),
                B("waitSeconds", "2", yes=[B("OpenScene", scene)]),
            ]),
        ])
    # the guard walks its patrol path (a path variable drawn on the canvas: Variables > Draw on the canvas)
    event(proj, scene, "private/guard/OnBodyCreated", [
        B("navFollow", "current", "route", "90", "PING_PONG"),
    ])
    event(proj, scene, "private/switchBtn/onClick", [B("OpenScene", other_scene)])


def enemy(sc, name, image, cell_center, speed, radius=16, grid="grid", sight=0):
    x, y = cell_center
    sc.add("CIRCLE", name, x=x - radius, y=y - radius, radius=radius, Collider_Radius=radius, z=30, image=image, type="DYNAMIC",
           Gravity_Scale=0, Fixed_Rotation="true", friction=0, restitution=0,
           behaviors=[("SmartChase", {"target": "player", "navGrid": grid, "speed": speed, "facing": "NONE", "repathTime": 0.3,
                                      "sightRange": sight})] if speed else None)


def build_square(proj, plan):
    scene = "scene1"
    sc = g.Scene()
    sc.add("BOX", "floor", x=0, y=0, width=W, height=H, z=0, image="/floor.png", Collider_Width=W, Collider_Height=H)
    rects, walls = wall_rects(plan["rows"])
    door = plan["door"]
    for i, (c, r, w, h) in enumerate(rects):
        sc.add("BOX", "wall%d" % (i + 1), x=c * CELL, y=r * CELL, width=w * CELL, height=h * CELL, z=10, type="STATIC", image="/wall.png",
               tileX=w, tileY=h, Collider_Width=w * CELL, Collider_Height=h * CELL, friction=0, restitution=0)
    # the path finding grid: walls painted on its cells; the door is an obstacle (its cells open when it is deleted)
    sys_element(sc, "NAVGRID", "grid", x=0, y=0, z=1, width=W, height=H, Cell_Width=CELL, Cell_Height=CELL, Agent_Size=30,
                Diagonal="true", Cut_Corners="false", Obstacles="(door)",
                Cells=encode_cells(COLS, ROWS, lambda c, r: (c, r) in walls))
    dx, dy = center(door)
    sc.add("BOX", "door", x=dx - CELL / 2, y=dy - CELL / 2, width=CELL, height=CELL, z=12, type="STATIC", image="/door.png",
           Collider_Width=CELL, Collider_Height=CELL)
    kx, ky = center(plan["key"])
    sc.add("BOX", "key", x=kx - 18, y=ky - 18, width=36, height=36, z=14, type="STATIC", isSensor="true", image="/key.png",
           Collider_Width=30, Collider_Height=30)
    sx, sy = center(plan["star"])
    sc.add("BOX", "star", x=sx - 18, y=sy - 18, width=36, height=36, z=14, type="STATIC", isSensor="true", image="/star.png",
           Collider_Width=30, Collider_Height=30)
    sc.add("BOX", "marker", x=0, y=0, width=32, height=32, z=13, type="UI", image="/marker.png", Collider_Width=32, Collider_Height=32)
    px, py = center(plan["start"])
    sc.add("CIRCLE", "player", x=px - 15, y=py - 15, radius=15, Collider_Radius=15, z=31, image="/player.png", type="DYNAMIC",
           Gravity_Scale=0, Fixed_Rotation="true", friction=0, restitution=0)
    enemy(sc, "ghost1", "/ghost_red.png", center(plan["ghost1"]), 125)
    enemy(sc, "ghost2", "/ghost_pink.png", center(plan["ghost2"]), 105)
    gx, gy = center(plan["guard"][0])
    route = ",".join("%d,%d" % center(p) for p in plan["guard"])
    sc.add("CIRCLE", "guard", x=gx - 16, y=gy - 16, radius=16, Collider_Radius=16, z=30, image="/guard.png", type="DYNAMIC", Gravity_Scale=0,
           Fixed_Rotation="true", friction=0, restitution=0,
           objectVars=json.dumps([{"name": "route", "type": "PathHolder", "value": route}]))
    hud(sc, "Tap where to go. Take the key, open the door, reach the star!")
    button(sc, "switchBtn", 1040, 652, 220, 56, "/btn_blue.png", "Hexagon cave >", scale=0.9, z=125)
    write_scene(proj, scene, sc, "#14161CFF")
    maze_events(proj, scene, "hexmaze", "Hexagon cave", 210)


# ---- the hexagon scene: an open cave with rocks (paths go around them on 6 sides)

HEX_W = 40.0
HEX_H = round(HEX_W * 0.866, 2)
ROCKS = [  # x, y (center), radius
    (250, 560, 46), (330, 470, 34), (180, 330, 52), (420, 300, 40), (520, 520, 46), (600, 380, 36), (690, 250, 50), (760, 520, 44),
    (860, 400, 40), (940, 240, 46), (1010, 520, 50), (1120, 360, 44), (420, 110, 40), (820, 110, 40), (1180, 160, 36), (120, 160, 40),
    (560, 160, 30), (300, 200, 28), (1100, 620, 30), (680, 620, 32), (980, 640, 26)]


def build_hex(proj):
    scene = "hexmaze"
    sc = g.Scene()
    sc.add("BOX", "floor", x=0, y=0, width=W, height=H, z=0, image="/floor.png", Collider_Width=W, Collider_Height=H)
    # the walls of the screen
    for name, x, y, w, h in (("wallL", 0, 0, 20, H), ("wallR", W - 20, 0, 20, H), ("wallB", 0, 0, W, 20), ("wallT", 0, H - 20, W, 20)):
        sc.add("BOX", name, x=x, y=y, width=w, height=h, z=10, type="STATIC", image="/wall.png", tileX=max(1, round(w / 40)),
               tileY=max(1, round(h / 40)), Collider_Width=w, Collider_Height=h, friction=0, restitution=0)
    for i, (x, y, r) in enumerate(ROCKS):
        sc.add("CIRCLE", "rock%d" % (i + 1), x=x - r, y=y - r, radius=r, Collider_Radius=r, z=11, type="STATIC",
               image="/rock%d.png" % (1 + i % 2), friction=0, restitution=0)
    # a gate of two rocks and a door between them; the star waits behind it
    for name, x, y, r in (("gateA", 1188, 470, 30), ("gateB", 1188, 590, 30)):
        sc.add("CIRCLE", name, x=x - r, y=y - r, radius=r, Collider_Radius=r, z=11, type="STATIC", image="/rock1.png", friction=0, restitution=0)
    sc.add("BOX", "wallGate", x=1100, y=440, width=40, height=200, z=10, type="STATIC", image="/wall.png", tileX=1, tileY=5,
           Collider_Width=40, Collider_Height=200, friction=0, restitution=0)
    sc.add("BOX", "door", x=1170, y=500, width=36, height=60, z=12, type="STATIC", image="/door.png", Collider_Width=36, Collider_Height=60)
    sc.add("BOX", "star", x=1212, y=512, width=36, height=36, z=14, type="STATIC", isSensor="true", image="/star.png", Collider_Width=30,
           Collider_Height=30)
    sc.add("BOX", "key", x=62, y=602, width=36, height=36, z=14, type="STATIC", isSensor="true", image="/key.png", Collider_Width=30,
           Collider_Height=30)
    # hexagon grid: the static elements block their cells by themselves; the door is an obstacle (deleted = open)
    sys_element(sc, "NAVGRID", "grid", x=0, y=0, z=1, width=W, height=H, Cell_Shape="HEX", Cell_Width=HEX_W, Cell_Height=HEX_H, Agent_Size=30,
                Use_Static_Bodies="true", Obstacles="(door)")
    sc.add("BOX", "marker", x=0, y=0, width=32, height=32, z=13, type="UI", image="/marker.png", Collider_Width=32, Collider_Height=32)
    sc.add("CIRCLE", "player", x=60 - 15, y=60 - 15, radius=15, Collider_Radius=15, z=31, image="/player.png", type="DYNAMIC",
           Gravity_Scale=0, Fixed_Rotation="true", friction=0, restitution=0)
    enemy(sc, "ghost1", "/ghost_red.png", (1000, 100), 120, sight=520)
    enemy(sc, "ghost2", "/ghost_pink.png", (760, 660), 100, sight=520)
    route = "470,400,640,470,800,300,640,200"
    sc.add("CIRCLE", "guard", x=470 - 16, y=400 - 16, radius=16, Collider_Radius=16, z=30, image="/guard.png", type="DYNAMIC", Gravity_Scale=0,
           Fixed_Rotation="true", friction=0, restitution=0, objectVars=json.dumps([{"name": "route", "type": "PathHolder", "value": route}]))
    hud(sc, "Hexagon grid: paths go around the rocks on 6 sides. Key, door, star!")
    button(sc, "switchBtn", 1040, 652, 220, 56, "/btn_blue.png", "< Square maze", scale=0.9, z=125)
    write_scene(proj, scene, sc, "#14161CFF")
    maze_events(proj, scene, "scene1", "Square maze", 210)


# ====================================================================== WifiStars

def build_wifi_stars():
    folder = "WifiStars"
    proj = g.fresh(folder)
    img = lambda n: os.path.join(proj, "images", n)  # noqa: E731
    img_button(img("btn_green.png"), 280, 72, (67, 160, 71))
    img_button(img("btn_blue.png"), 280, 72, (30, 136, 229))
    img_button(img("btn_orange.png"), 280, 72, (245, 124, 0))
    img_button(img("btn_red.png"), 200, 64, (229, 57, 53))
    img_panel(img("panel.png"), 560, 420)
    img_panel(img("row.png"), 520, 64, color=(40, 60, 90), alpha=230)
    img_arena(img("arena.png"), W, H)
    img_ball(img("ball.png"), (235, 235, 235), size=64)
    img_star(img("star.png"), size=56)
    img_joy_pad(img("joy_pad.png"))
    img_joy_knob(img("joy_knob.png"))
    build_lobby(proj)
    build_arena(proj)
    open(os.path.join(EXAMPLES, folder + ".txt"), "w", encoding="utf-8").write(CARDS[folder]["desc"]["en"])
    open(os.path.join(EXAMPLES, folder + ".ar.txt"), "w", encoding="utf-8").write(CARDS[folder]["desc"]["ar"])
    open(os.path.join(proj, "CREDITS.md"), "w", encoding="utf-8").write(
        "# WifiStars - credits\n\nEvery image of this example was drawn for Star2D by tools/gen_systems_examples.py (CC0: free to use, change and ship).\n")
    return proj


def network_element(sc, template, scene_game, max_players=4):
    sys_element(sc, "NETWORK", "network", x=24, y=H - 120, z=100, Max_Players=max_players, Send_Rate=20, Player_Template=template,
                Spawn_Radius=140 if template else 0, Game_Version="1", Game_Scene=scene_game)


def build_lobby(proj):
    scene = "scene1"
    sc = g.Scene()
    network_element(sc, "", "arena")
    text(sc, "title", 0, 610, W, 90, "Wi-Fi Stars", scale=2.4, color="#FFD54F")
    text(sc, "subtitle", 0, 570, W, 40, "Play on the same Wi-Fi, or turn on a phone's hotspot and join it.", scale=0.9, color="#B0BEC5")
    button(sc, "hostBtn", 80, 450, 280, 72, "/btn_green.png", "Host a game", scale=1.1)
    button(sc, "searchBtn", 80, 360, 280, 72, "/btn_blue.png", "Search games", scale=1.1)
    button(sc, "startBtn", 80, 200, 280, 72, "/btn_orange.png", "Start!", scale=1.2, visible=False)
    button(sc, "leaveBtn", 80, 110, 280, 64, "/btn_red.png", "Leave", scale=1.0, visible=False)
    sc.add("BOX", "roomsPanel", x=430, y=120, width=380, height=420, z=90, image="/panel.png", Collider_Width=380, Collider_Height=420)
    text(sc, "roomsTitle", 430, 480, 380, 50, "Games found", scale=1.1, color="#90CAF9", z=95)
    for i in range(3):
        button(sc, "room%d" % i, 445, 390 - i * 80, 350, 64, "/row.png", "", scale=0.95, z=96, visible=False)
    sc.add("BOX", "playersPanel", x=840, y=120, width=380, height=420, z=90, image="/panel.png", Collider_Width=380, Collider_Height=420)
    text(sc, "playersTitle", 840, 480, 380, 50, "Players", scale=1.1, color="#A5D6A7", z=95)
    text(sc, "players", 860, 140, 340, 330, "-", scale=1.0, z=96, align="LEFT")
    text(sc, "status", 0, 30, W, 50, "", scale=1.0, color="#FFFFFF", z=110)
    write_scene(proj, scene, sc, "#101820FF")
    variables(proj, scene, [("list", "String", "\"\"")])
    S = lambda t: B("showText", "status", q(t))  # noqa: E731
    event(proj, scene, "public/OnCreate", [
        S("Host a game, or search for the games of your friends."),
        # refresh the lists twice a second (games found, players, buttons of the host)
        B("everySeconds", "0.5", yes=[
            B("setVisibility", "room0", C("___ > ___", C("games found"), "0")),
            B("setVisibility", "room0Text", C("___ > ___", C("games found"), "0")),
            B("setVisibility", "room1", C("___ > ___", C("games found"), "1")),
            B("setVisibility", "room1Text", C("___ > ___", C("games found"), "1")),
            B("setVisibility", "room2", C("___ > ___", C("games found"), "2")),
            B("setVisibility", "room2Text", C("___ > ___", C("games found"), "2")),
            B("ifThen", C("___ > ___", C("games found"), "0"), yes=[
                B("showText", "room0Text", C("join ___ ___", C("name of game ___", "0"),
                                             C("join ___ ___", q("  ("), C("join ___ ___", C("players in game ___", "0"), q(" players)"))))),
            ]),
            B("ifThen", C("___ > ___", C("games found"), "1"), yes=[
                B("showText", "room1Text", C("join ___ ___", C("name of game ___", "1"),
                                             C("join ___ ___", q("  ("), C("join ___ ___", C("players in game ___", "1"), q(" players)"))))),
            ]),
            B("ifThen", C("___ > ___", C("games found"), "2"), yes=[
                B("showText", "room2Text", C("join ___ ___", C("name of game ___", "2"),
                                             C("join ___ ___", q("  ("), C("join ___ ___", C("players in game ___", "2"), q(" players)"))))),
            ]),
            # the players of the room, one per line
            B("setVariable", "list", q("")),
            B("repeatTimes", C("player count"), "i", yes=[
                B("setVariable", "list", C("join ___ ___", "list", C("join ___ ___", C("name of player ___", C("player number at ___", "i")), q("\n")))),
            ]),
            B("ifThen", C("not ___", C("I am online")), yes=[B("setVariable", "list", q("-"))]),
            B("showText", "players", "list"),
            B("setVisibility", "startBtn", C("I am the host")),
            B("setVisibility", "startBtnText", C("I am the host")),
            B("setVisibility", "leaveBtn", C("I am online")),
            B("setVisibility", "leaveBtnText", C("I am online")),
            B("setVisibility", "hostBtn", C("not ___", C("I am online"))),
            B("setVisibility", "hostBtnText", C("not ___", C("I am online"))),
        ]),
    ])
    event(proj, scene, "private/hostBtn/onClick", [B("netHost", q("")), S("Waiting for players... Press Start when everybody is in.")])
    event(proj, scene, "private/searchBtn/onClick", [B("netSearch"), S("Searching the Wi-Fi for games...")])
    for i in range(3):
        event(proj, scene, "private/room%d/onClick" % i, [B("netJoin", str(i)), S("Joining...")])
    event(proj, scene, "private/startBtn/onClick", [B("netStart", "arena")])
    event(proj, scene, "private/leaveBtn/onClick", [B("netLeave"), S("You left the game.")])
    event(proj, scene, "public/onConnected", [
        B("if", C("I am the host"), yes=[S("Your game is open: friends can find it now.")], no=[S("Joined! Waiting for the host to start...")]),
    ])
    event(proj, scene, "public/onPlayerJoined", [B("Toast", C("join ___ ___", "name", q(" joined")))])
    event(proj, scene, "public/onPlayerLeft", [B("Toast", C("join ___ ___", "name", q(" left")))])
    event(proj, scene, "public/onJoinFailed", [B("showText", "status", C("join ___ ___", q("Could not join: "), "reason"))])
    event(proj, scene, "public/onDisconnected", [B("showText", "status", C("join ___ ___", q("Disconnected: "), "reason"))])
    event(proj, scene, "public/onHostLeft", [S("The host closed the game.")])


PLAYER_COLORS = ["#4FC3F7", "#FF8A65", "#AED581", "#F06292"]


def build_arena(proj):
    scene = "arena"
    sc = g.Scene()
    sc.add("BOX", "bg", x=0, y=0, width=W, height=H, z=0, image="/arena.png", Collider_Width=W, Collider_Height=H)
    for name, x, y, w, h in (("wallL", -40, 0, 50, H), ("wallR", W - 10, 0, 50, H), ("wallB", 0, -40, W, 50), ("wallT", 0, H - 10, W, 50)):
        sc.add("BOX", name, x=x, y=y, width=w, height=h, z=1, type="STATIC", Visible="false", Collider_Width=w, Collider_Height=h, friction=0)
    network_element(sc, "ball", "arena")
    # the player template: copied for every player (each phone moves its own copy); its score is the same on every phone
    sc.add("CIRCLE", "ball", x=W / 2 - 28, y=H / 2 - 28, radius=28, Collider_Radius=28, z=30, image="/ball.png", type="DYNAMIC",
           Gravity_Scale=0, Fixed_Rotation="true", friction=0, restitution=0.4,
           objectVars=json.dumps([{"name": "score", "type": "int", "value": "0", "sync": True}]))
    # the star: moved by the host, shown on every phone
    star = sc.add("BOX", "star", x=W / 2 - 28, y=H - 200, width=56, height=56, z=20, type="STATIC", isSensor="true", image="/star.png",
                  Collider_Width=48, Collider_Height=48)
    star["net"] = json.dumps({"on": True, "owner": "host", "sync": "pos,vis"}, separators=(",", ":"))
    sc.add("JOYSTICK", "joy", x=60, y=50, width=200, height=200, z=120, P={"Pad Image": "/joy_pad.png", "Button Image": "/joy_knob.png"})
    text(sc, "scores", 30, 470, 360, 220, "", scale=1.0, z=110, align="LEFT")
    text(sc, "info", 0, 320, W, 80, "", scale=1.6, color="#FFD54F", z=111)
    button(sc, "backBtn", W - 230, H - 84, 200, 64, "/btn_red.png", "Leave", scale=1.0, z=125)
    write_scene(proj, scene, sc, "#0B1A22FF")
    variables(proj, scene, [("list", "String", "\"\""), ("winner", "String", "\"\""), ("over", "boolean", "false")])
    tint_chain = [B("ifThen", C("___ == ___", C("owner of ___", "p"), str(i + 1)), yes=[B("setTint", "p", c.lstrip("#"))])
                  for i, c in enumerate(PLAYER_COLORS)]
    event(proj, scene, "public/OnCreate", [
        B("setGravity", "0", "0"),
        B("if", C("I am online"), yes=[
            B("showText", "info", q("Collect the stars! First to 10 wins.")),
            B("waitSeconds", "2.5", yes=[B("ifThen", C("not ___", "over"), yes=[B("showText", "info", q(""))])]),
        ], no=[B("showText", "info", q("Play from the lobby (scene1): host or join a game first."))]),
    ])
    event(proj, scene, "public/OnStep", [
        # my ball follows my joystick (the other phones see it move)
        B("newVariable", "PlayerItem", "me", C("my element")),
        B("ifThen", C("___ != ___", "me", "null"), yes=[
            B("setLinearVelocity", "me", C("___ * ___", C("joystick x ___", "joy"), "70"), C("___ * ___", C("joystick y ___", "joy"), "70")),
        ]),
        # every ball gets the color of its player; the scores, one line per player
        B("setVariable", "list", q("")),
        B("repeatTimes", C("player count"), "i", yes=[
            B("newVariable", "PlayerItem", "p", C("element of player ___", C("player number at ___", "i"))),
            B("ifThen", C("___ != ___", "p", "null"), yes=tint_chain + [
                B("setVariable", "list", C("join ___ ___", "list", C("join ___ ___", C("name of player ___", C("owner of ___", "p")),
                                          C("join ___ ___", q(": "), C("join ___ ___", C("round ___", C("number variable ___ of ___", "score", "p")), q("\n")))))),
                # the host counts the stars: a ball touching the star scores, the star jumps away
                B("ifThen", C("___ and ___", C("I am the host"), C("___ < ___", C("distance between ___ and ___", "p", "star"), "52")), yes=[
                    B("changeObjectVariable", "p", "score", "1"),
                    B("navPlace", "star", C("random ___ to ___", "90", "1190"), C("random ___ to ___", "90", "560")),
                    B("ifThen", C("___ >= ___", C("number variable ___ of ___", "score", "p"), "10"), yes=[
                        B("netRunAll", q("win"), C("name of player ___", C("owner of ___", "p"))),
                    ]),
                ]),
            ]),
        ]),
        B("showText", "scores", "list"),
    ])
    event(proj, scene, "public/onNetMessage", [
        B("ifThen", C("___ and ___", C("___ equals ___", "name", q("win")), C("not ___", "over")), yes=[
            B("setVariable", "over", "true"),
            B("setVariable", "winner", "data"),
            B("showText", "info", C("join ___ ___", "winner", q(" wins!"))),
            # the host starts a new round for everybody
            B("waitSeconds", "4", yes=[B("ifThen", C("I am the host"), yes=[B("netLoadScene", "arena")])]),
        ]),
    ])
    event(proj, scene, "public/onPlayerJoined", [B("Toast", C("join ___ ___", "name", q(" joined")))])
    event(proj, scene, "public/onPlayerLeft", [B("Toast", C("join ___ ___", "name", q(" left the game")))])
    event(proj, scene, "public/onHostLeft", [
        B("setVariable", "over", "true"),
        B("showText", "info", q("The host left the game")),
        B("waitSeconds", "2.5", yes=[B("OpenScene", "scene1")]),
    ])
    event(proj, scene, "private/backBtn/onClick", [B("netLeave"), B("OpenScene", "scene1")])


# ====================================================================== texts of the template cards

CARDS = {
    "MazeChase": {
        "title": {"en": "Maze Chase", "ar": "مطاردة المتاهة", "fr": "Poursuite dans le labyrinthe", "br": "Perseguição no Labirinto",
                  "ru": "Погоня в лабиринте", "es": "Persecución en el laberinto"},
        "tag": {"en": "Path finding", "ar": "إيجاد المسار", "fr": "Recherche de chemin", "br": "Busca de caminho", "ru": "Поиск пути",
                "es": "Búsqueda de rutas"},
        "desc": {
            "en": "Tap where to go: the player walks the shortest way through the maze. Smart Chase ghosts find their way around the walls, a guard walks a drawn patrol path, and the key opens a door so the ghosts find new ways. A second scene plays on a hexagon grid.",
            "ar": "اضغط حيث تريد الذهاب: يمشي اللاعب في أقصر طريق عبر المتاهة. أشباح المطاردة الذكية تجد طريقها حول الجدران، وحارس يمشي على مسار دورية مرسوم، والمفتاح يفتح باباً فتجد الأشباح طرقاً جديدة. ومشهد ثانٍ على شبكة سداسية.",
            "fr": "Touchez où aller : le joueur prend le chemin le plus court dans le labyrinthe. Des fantômes Smart Chase contournent les murs, un garde suit une ronde dessinée et la clé ouvre une porte pour que les fantômes trouvent de nouveaux chemins. Une deuxième scène se joue sur une grille hexagonale.",
            "br": "Toque onde quer ir: o jogador segue o caminho mais curto pelo labirinto. Fantasmas Smart Chase contornam as paredes, um guarda segue uma ronda desenhada e a chave abre uma porta para os fantasmas acharem novos caminhos. Uma segunda cena usa uma grade hexagonal.",
            "ru": "Коснитесь, куда идти: игрок идёт кратчайшим путём через лабиринт. Призраки Smart Chase обходят стены, охранник ходит по нарисованному маршруту, а ключ открывает дверь, и призраки находят новые пути. Вторая сцена — на шестиугольной сетке.",
            "es": "Toca a dónde ir: el jugador sigue el camino más corto por el laberinto. Fantasmas Smart Chase rodean las paredes, un guardia recorre una ruta dibujada y la llave abre una puerta para que los fantasmas encuentren nuevos caminos. Una segunda escena usa una cuadrícula hexagonal."},
    },
    "WifiStars": {
        "title": {"en": "Wi-Fi Stars", "ar": "نجوم الواي فاي", "fr": "Étoiles Wi-Fi", "br": "Estrelas Wi-Fi", "ru": "Звёзды по Wi-Fi", "es": "Estrellas Wi-Fi"},
        "tag": {"en": "Multiplayer / Wi-Fi", "ar": "لعب جماعي / واي فاي", "fr": "Multijoueur / Wi-Fi", "br": "Multijogador / Wi-Fi",
                "ru": "Мультиплеер / Wi-Fi", "es": "Multijugador / Wi-Fi"},
        "desc": {
            "en": "Local multiplayer made of blocks: host or search a game on the same Wi-Fi (or a phone's hotspot), see the rooms and players, start for everybody. Each phone moves its own ball with a joystick; the host spawns the stars and counts the points, first to 10 wins.",
            "ar": "لعب جماعي محلي مصنوع بالبلوكات: استضف أو ابحث عن لعبة على نفس الواي فاي (أو نقطة اتصال هاتف)، شاهد الغرف واللاعبين، وابدأ للجميع. كل هاتف يحرك كرته بعصا التحكم؛ المضيف يُظهر النجوم ويعدّ النقاط، والأول إلى 10 يفوز.",
            "fr": "Multijoueur local fait de blocs : hébergez ou cherchez une partie sur le même Wi-Fi (ou le partage de connexion d'un téléphone), voyez les salons et les joueurs, lancez pour tous. Chaque téléphone déplace sa balle au joystick ; l'hôte fait apparaître les étoiles et compte les points, le premier à 10 gagne.",
            "br": "Multijogador local feito com blocos: hospede ou procure um jogo no mesmo Wi-Fi (ou no ponto de acesso de um celular), veja as salas e os jogadores, comece para todos. Cada celular move sua bola com o joystick; o anfitrião cria as estrelas e conta os pontos, o primeiro a 10 vence.",
            "ru": "Локальный мультиплеер из блоков: создайте или найдите игру в той же сети Wi-Fi (или точке доступа телефона), смотрите комнаты и игроков, начните для всех. Каждый телефон двигает свой шар джойстиком; хост создаёт звёзды и считает очки, кто первым наберёт 10 — побеждает.",
            "es": "Multijugador local hecho con bloques: crea o busca una partida en el mismo Wi-Fi (o el punto de acceso de un teléfono), mira las salas y los jugadores, empieza para todos. Cada teléfono mueve su bola con un joystick; el anfitrión crea las estrellas y cuenta los puntos, el primero en llegar a 10 gana."},
    },
}


def write_translations():
    out = {}
    for folder, parts in CARDS.items():
        for part, langs in parts.items():
            out["tpl_%s_%s" % (folder, part)] = langs
    os.makedirs(os.path.dirname(I18N_OUT), exist_ok=True)
    json.dump(out, open(I18N_OUT, "w", encoding="utf-8"), indent=1, ensure_ascii=False)


# ====================================================================== validation

PROJECTS = {"MazeChase": ["scene1", "hexmaze"], "WifiStars": ["scene1", "arena"]}


def validate():
    problems, notes = [], []
    P = problems.append
    hcp, tmp = helper()
    rawcp = _HELPER["rawcp"]
    player = os.path.join(ROOT, "app/src/main/assets/java/Player.java")
    phone_cp = os.path.join(ROOT, "app/src/main/assets/java/game.zip") + os.pathsep + os.path.join(ROOT, "app/src/main/assets/cp.zip")
    for folder, scenes in PROJECTS.items():
        proj = os.path.join(EXAMPLES, folder)
        refs = set()
        for scene in scenes:
            pre = "%s/%s: " % (folder, scene)
            els = json.load(open(os.path.join(proj, "scenes", scene), encoding="utf-8"))
            names = [e["name"] for e in els]
            if len(set(names)) != len(names):
                P(pre + "duplicate names")
            for e in els:
                want = set(g.defaults(e["TYPE"])) - (set(SYS_UNUSED) if e["TYPE"] in ("NAVGRID", "NETWORK") else set())
                missing = want - set(e)
                if missing:
                    P(pre + "%s lacks keys %s" % (e["name"], sorted(missing)))
                for key in ("image", "Pad Image", "Button Image"):
                    v = e.get(key, "")
                    if v:
                        f = os.path.join(proj, "images", v.lstrip("/"))
                        refs.add(os.path.relpath(f, proj))
                        if not os.path.isfile(f):
                            P(pre + "%s.%s: missing %s" % (e["name"], key, v))
                if e["TYPE"] in ("TEXT", "JOYSTICK") or (e["TYPE"] == "BOX" and e.get("type") == "UI"):
                    x, y, w, h = (float(e[k]) for k in ("x", "y", "width", "height"))
                    if x < -1 or y < -1 or x + w > W + 1 or y + h > H + 1:
                        P(pre + "%s is outside the frame" % e["name"])
            for vf in glob.glob(os.path.join(proj, "Events", scene, "**", "*.visual"), recursive=True):
                for node in json.load(open(vf, encoding="utf-8")):
                    if node["title"] != "First >" and node["title"] not in g.BLOCKS:
                        P(pre + "unknown block " + node["title"])
            gen = os.path.join(tmp, folder, scene, "com/star4droid/Game/%s.java" % scene)
            r = subprocess.run(["java", "-cp", hcp, "SystemsCheck", "project", proj, scene, player, gen], capture_output=True, text=True)
            out = "\n".join(l for l in (r.stdout + r.stderr).split("\n") if l.strip() and not l.startswith("Picked up"))
            if r.returncode != 0:
                P(pre + "project check:\n" + out)
                continue
            code = open(gen, encoding="utf-8").read()
            if re.search(r"->|::", code):
                P(pre + "lambda / method reference in the scene code (the phone compiles Java 7)")
            for label, cp in (("template classes", rawcp), ("phone class path", phone_cp)):
                cls = os.path.join(tmp, folder, scene, "out_" + label.split()[0])
                os.makedirs(cls, exist_ok=True)
                r = subprocess.run(["javac", "-nowarn", "--release", "8", "-encoding", "UTF-8", "-d", cls, "-cp", cp, gen], capture_output=True, text=True)
                if r.returncode != 0:
                    P(pre + "the generated scene does not compile with the %s:\n%s" % (label, (r.stdout + r.stderr)[:3000]))
                    break
            else:
                notes.append(pre + "%d elements, scene Java compiles (%d lines)" % (len(els), code.count("\n")))
        for f in glob.glob(os.path.join(proj, "images", "*")):
            if os.path.relpath(f, proj) not in refs:
                P(folder + ": unused image " + os.path.relpath(f, proj))
        size = sum(os.path.getsize(f) for f in glob.glob(os.path.join(proj, "**"), recursive=True) if os.path.isfile(f))
        notes.append("%s: %.2f MB" % (folder, size / 1048576.0))
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
    load_blocks()
    if "--check" not in sys.argv[1:]:
        for build in (build_maze_chase, build_wifi_stars):
            print("generated", os.path.relpath(build(), ROOT))
        write_translations()
        print("translations ->", I18N_OUT)
    sys.exit(0 if validate() else 1)


if __name__ == "__main__":
    main()
