# World Builder model library: runs inside Blender (bpy / bmesh / mathutils). Blender coordinates (Z up) are used while
# modelling; export converts to the engine's Y up.
import bpy, bmesh, math, struct, random
import numpy as np
from mathutils import Vector, Matrix, noise
from mathutils.bvhtree import BVHTree

MAT = dict(flat=0, rock=1, bark=2, planks=3, bricks=4, plaster=5, tiles=6, leaf=7, thatch=8, metal=9, glow=10, glass=11)
F_ALPHA, F_DOUBLE, F_WIND, F_EMISSIVE, F_SLOPE, F_NOSHADOW, F_NOAO = 1, 2, 4, 8, 16, 32, 64
MODE = dict(S=0, L=1, H=2, M=3, R=4)      # stretch, anchor low, anchor high, middle (keeps size), repeat
ATLAS = 4                                  # foliage atlas is ATLAS x ATLAS cells

def rgb(h):
    h = h.lstrip('#'); return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])
def lerp(a, b, t): return a + (b - a) * t
def fnoise(p, scale=1.0, octaves=3, seed=0.0):
    v = Vector((p[0] * scale + seed * 17.3, p[1] * scale - seed * 9.1, p[2] * scale + seed * 4.7))
    return noise.fractal(v, 1.0, 2.0, octaves)          # roughly -1..1

class Part:
    def __init__(self, mat='flat', tint=0, tint2=0, flags=0, modes='SSS', rep=None, slope=0.0, sref=0.0):
        self.mat = MAT[mat]; self.tint = tint; self.tint2 = tint2; self.flags = flags; self.modes = modes
        self.rep = rep or {}          # axis letter -> (a, b, n): the copies are spread over [a, b] (engine axes x, y, z)
        self.slope = slope            # height (engine y) above which a F_SLOPE part follows the depth ratio
        self.sref = sref              # half depth of the walls under a F_SLOPE part (the roof grows with them)
        self.P = []; self.N = []; self.C = []; self.UV = []; self.I = []; self.nv = 0
    def add(self, pos, nrm, col, uv, idx):
        pos = np.asarray(pos, float).reshape(-1, 3); n = len(pos)
        col = np.asarray(col, float)
        if col.ndim == 1: col = np.tile(col, (n, 1))
        if col.shape[1] == 3: col = np.concatenate([col, np.zeros((n, 1))], 1)
        uv = np.zeros((n, 2)) if uv is None else np.asarray(uv, float)
        self.P.append(pos); self.N.append(np.asarray(nrm, float).reshape(-1, 3)); self.C.append(col); self.UV.append(uv)
        self.I.append(np.asarray(idx, int).reshape(-1) + self.nv); self.nv += n
        return self
    def add_bm(self, bm, color, sharp=38.0, a=0.0, uvfn=None):
        """Consumes a bmesh. color: rgb or fn(pos Nx3, nrm Nx3) -> Nx3 (Blender coordinates). a: second-tint weight."""
        me = bpy.data.meshes.new('t'); bmesh.ops.triangulate(bm, faces=bm.faces[:]); bm.to_mesh(me); bm.free()
        for p in me.polygons: p.use_smooth = True
        me.set_sharp_from_angle(angle=math.radians(sharp))
        nl = len(me.loops)
        co = np.zeros(len(me.vertices) * 3); me.vertices.foreach_get('co', co); co = co.reshape(-1, 3)
        li = np.zeros(nl, int); me.loops.foreach_get('vertex_index', li)
        ln = np.zeros(nl * 3); me.corner_normals.foreach_get('vector', ln); ln = ln.reshape(-1, 3)
        pos = co[li]
        key = np.round(np.concatenate([pos, ln], 1), 4)
        uniq, inv = np.unique(key, axis=0, return_inverse=True)
        inv = inv.reshape(-1)
        P = uniq[:, :3]; N = uniq[:, 3:]
        col = color(P, N) if callable(color) else np.tile(np.asarray(color, float)[:3], (len(P), 1))
        if col.shape[1] == 3:
            aa = a(P, N) if callable(a) else np.full(len(P), a)
            col = np.concatenate([col, np.asarray(aa).reshape(-1, 1)], 1)
        tri = np.zeros(len(me.polygons) * 3, int); me.polygons.foreach_get('loop_start', tri[:len(me.polygons)])
        idx = np.array([inv[p.loop_start + k] for p in me.polygons for k in range(3)], int)
        bpy.data.meshes.remove(me)
        return self.add(P, N, col, uvfn(P, N) if uvfn else None, idx)
    def arrays(self):
        return (np.concatenate(self.P), np.concatenate(self.N), np.concatenate(self.C), np.concatenate(self.UV), np.concatenate(self.I))

class Model:
    def __init__(self, id, name, cat, tints=None, flags=0, sub=''):
        self.id = id; self.name = name; self.cat = cat; self.parts = []; self.flags = flags; self.sub = sub
        self.tints = tints or []      # [(label, '#rrggbb'), ...] up to 2
        self.zone = {}                # engine axis letter -> (a, b): only this span stretches when that axis is scaled alone; the rest keeps its size
    def part(self, *a, **k):
        p = Part(*a, **k); self.parts.append(p); return p

M_SMALL, M_SCATTER, M_ALIGN, M_SMART = 1, 2, 4, 8     # model flags: culled early, scatter brush, align to slope, smart axis scale

# ------------------------------------------------------------------ bmesh primitives (Blender space, Z up)
def xf(bm, loc=(0, 0, 0), rot=(0, 0, 0), scale=(1, 1, 1)):
    m = Matrix.Translation(Vector(loc)) @ (Matrix.Rotation(rot[2], 4, 'Z') @ Matrix.Rotation(rot[1], 4, 'Y') @ Matrix.Rotation(rot[0], 4, 'X')) @ Matrix.Diagonal(Vector((*scale, 1)))
    bm.transform(m); return bm
def rbox(size, loc=(0, 0, 0), bevel=0.04, seg=2, rot=(0, 0, 0), base=True):
    """Rounded box. base=True: loc is the center of the bottom face."""
    bm = bmesh.new(); bmesh.ops.create_cube(bm, size=1.0)
    xf(bm, scale=size)
    if bevel > 0:
        b = min(bevel, min(size) * 0.45)
        bmesh.ops.bevel(bm, geom=bm.edges[:], offset=b, segments=seg, affect='EDGES', profile=0.5)
    return xf(bm, loc=(loc[0], loc[1], loc[2] + (size[2] / 2 if base else 0)), rot=rot)
def cyl(r1, r2, h, n=14, loc=(0, 0, 0), bevel=0.0, rot=(0, 0, 0), cap=True):
    bm = bmesh.new(); bmesh.ops.create_cone(bm, cap_ends=cap, cap_tris=False, segments=n, radius1=r1, radius2=max(r2, 1e-4), depth=h)
    if bevel > 0:
        es = [e for e in bm.edges if abs(e.verts[0].co.z - e.verts[1].co.z) < 1e-5]
        bmesh.ops.bevel(bm, geom=es, offset=bevel, segments=2, affect='EDGES', profile=0.5)
    xf(bm, loc=(0, 0, h / 2)); return xf(bm, loc=loc, rot=rot)
def ico(r=1.0, sub=2, loc=(0, 0, 0), scale=(1, 1, 1)):
    bm = bmesh.new(); bmesh.ops.create_icosphere(bm, subdivisions=sub, radius=r); return xf(bm, loc=loc, scale=scale)
def join(*bms):
    out = bmesh.new()
    for b in bms:
        me = bpy.data.meshes.new('j'); b.to_mesh(me); b.free(); out.from_mesh(me); bpy.data.meshes.remove(me)
    return out
def displace(bm, fn):
    for v in bm.verts: v.co = Vector(fn(v.co))
    return bm
def prism(profile, length, loc=(0, 0, 0), axis='x', bevel=0.0):
    """Extrudes a closed 2D profile [(a, b)...] (in the plane perpendicular to `axis`) along the axis, centered."""
    bm = bmesh.new(); h = length / 2
    def P(t, a, b): return (t, a, b) if axis == 'x' else (a, t, b)
    v0 = [bm.verts.new(P(-h, a, b)) for a, b in profile]; v1 = [bm.verts.new(P(h, a, b)) for a, b in profile]
    n = len(profile)
    for i in range(n): bm.faces.new([v0[i], v0[(i + 1) % n], v1[(i + 1) % n], v1[i]])
    bm.faces.new(v0[::-1]); bm.faces.new(v1)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    if bevel > 0: bmesh.ops.bevel(bm, geom=bm.edges[:], offset=bevel, segments=1, affect='EDGES')
    return xf(bm, loc=loc)
def tube(points, radii, sides=8, cap=True, lobes=0.0, twist=0.0):
    """Tube through points (list of xyz) with a radius per point; parallel-transport frames. Root flare via lobes."""
    bm = bmesh.new(); pts = [Vector(p) for p in points]; rings = []
    t0 = (pts[1] - pts[0]).normalized(); ref = Vector((1, 0, 0)) if abs(t0.x) < 0.9 else Vector((0, 1, 0))
    u = t0.cross(ref).normalized(); w = t0.cross(u).normalized()
    for i, p in enumerate(pts):
        t = ((pts[min(i + 1, len(pts) - 1)] - pts[max(i - 1, 0)])).normalized()
        u = (u - t * u.dot(t)).normalized(); w = t.cross(u).normalized()
        ring = []
        for s in range(sides):
            a = s / sides * 2 * math.pi + twist * i
            r = radii[i] * (1 + (lobes * math.cos(3 * a + 1.3) if i == 0 else (lobes * 0.45 * math.cos(3 * a + 1.3) if i == 1 else 0)))
            ring.append(bm.verts.new(p + (u * math.cos(a) + w * math.sin(a)) * r))
        rings.append(ring)
    for i in range(len(rings) - 1):
        for s in range(sides):
            bm.faces.new([rings[i][s], rings[i][(s + 1) % sides], rings[i + 1][(s + 1) % sides], rings[i + 1][s]])
    if cap:
        bm.faces.new(rings[-1]); bm.faces.new(rings[0][::-1])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    return bm

# ------------------------------------------------------------------ foliage cards (numpy, custom normals)
def cell_uv(cell):
    cx = cell % ATLAS; cy = cell // ATLAS; e = 0.004
    u0 = cx / ATLAS + e; u1 = (cx + 1) / ATLAS - e; v0 = cy / ATLAS + e; v1 = (cy + 1) / ATLAS - e
    return np.array([[u0, v1], [u1, v1], [u1, v0], [u0, v0]])        # bottom-left, bottom-right, top-right, top-left
def card(part, center, right, up, w, h, cell, col_bot, col_top, nrm=None, wind=(0.3, 1.0), origin_bottom=False):
    """One quad. right/up are unit vectors; origin_bottom: center is the middle of the bottom edge."""
    c = np.asarray(center, float); r = np.asarray(right, float) * w / 2; u = np.asarray(up, float) * h
    if not origin_bottom: c = c - u / 2
    P = np.array([c - r, c + r, c + r + u, c - r + u])
    if nrm is None:
        n = np.cross(r, u); n = n / (np.linalg.norm(n) + 1e-9); N = np.tile(n, (4, 1))
    else:
        N = np.asarray(nrm, float); N = np.tile(N, (4, 1)) if N.ndim == 1 else N
    C = np.array([[*col_bot, wind[0]], [*col_bot, wind[0]], [*col_top, wind[1]], [*col_top, wind[1]]])
    part.add(P, N, C, cell_uv(cell), [0, 1, 2, 0, 2, 3])
def clump(part, rnd, center, radius, n, size, cell, col_dark, col_light, canopy_c=None, canopy_r=1.0, skip_bottom=0.35, wind=1.0):
    """Leaf cards over an ellipsoid: each card lies roughly tangent to the surface, normals point away from the canopy."""
    center = np.asarray(center, float); radius = np.asarray(radius, float) * np.ones(3)
    cc = center if canopy_c is None else np.asarray(canopy_c, float)
    for i in range(n):
        z = 1 - (i + 0.5) / n * (2 - skip_bottom); a = i * 2.39996 + rnd.uniform(-0.3, 0.3)
        r = math.sqrt(max(0, 1 - z * z)); d = np.array([math.cos(a) * r, math.sin(a) * r, z])
        p = center + d * radius * rnd.uniform(0.82, 1.04)
        out = d / (np.linalg.norm(d) + 1e-9)
        # card plane: tilted between tangent and facing outward
        t1 = np.cross(out, [0, 0, 1.0]);  t1 = t1 / (np.linalg.norm(t1) + 1e-9) if np.linalg.norm(t1) > 1e-3 else np.array([1.0, 0, 0])
        t2 = np.cross(out, t1)
        roll = rnd.uniform(0, 2 * math.pi); right = t1 * math.cos(roll) + t2 * math.sin(roll); up = np.cross(out, right)
        tilt = rnd.uniform(-0.6, 0.6); up = up * math.cos(tilt) + out * math.sin(tilt)
        s = size * rnd.uniform(0.8, 1.25)
        nv = p - cc; nv = nv / (np.linalg.norm(nv) + 1e-9); nv = nv * 0.75 + np.array([0, 0, 0.45]); nv /= np.linalg.norm(nv)
        hgt = np.clip(((p[2] - cc[2]) / (canopy_r + 1e-6)) * 0.5 + 0.5, 0, 1); k = np.clip(hgt * 0.9 + rnd.uniform(-0.18, 0.18), 0, 1)
        col = lerp(np.asarray(col_dark), np.asarray(col_light), k)
        w = wind * (0.55 + 0.45 * hgt)
        card(part, p, right, up, s, s, cell, col * 0.94, col, nrm=nv, wind=(w, w))

# ------------------------------------------------------------------ shading helpers
def shade(base, vary=0.08, ground=0.0, top=0.0, scale=1.6, seed=0.0):
    """Color function: base color with noise variation, darker near the ground, lighter on up-facing surfaces."""
    base = np.asarray(base, float)
    def fn(P, N):
        n = np.array([fnoise(p, scale, 3, seed) for p in P])
        c = base[None, :] * (1 + n[:, None] * vary)
        if ground > 0: c = c * (1 - ground * np.clip(1 - P[:, 2] / 0.5, 0, 1))[:, None]
        if top > 0: c = c * (1 + top * np.clip(N[:, 2], 0, 1))[:, None]
        return np.clip(c, 0, 1)
    return fn

def bake_ao(model, samples=14, strength=0.5):
    """Ambient occlusion from the solid parts into the vertex colors (Blender space arrays)."""
    solids = [p for p in model.parts if not (p.flags & F_ALPHA) and not (p.flags & F_NOAO) and p.nv]
    if not solids: return
    V = []; T = []; off = 0
    for p in solids:
        P, N, C, UV, I = p.arrays(); V.append(P); T.append(I.reshape(-1, 3) + off); off += len(P)
    V = np.concatenate(V); T = np.concatenate(T)
    size = float(np.linalg.norm(V.max(0) - V.min(0)))
    bvh = BVHTree.FromPolygons([Vector(v) for v in V], [tuple(int(i) for i in t) for t in T])
    rnd = random.Random(5); dirs = []
    for i in range(samples):
        z = (i + 0.5) / samples; a = i * 2.39996; r = math.sqrt(1 - z * z); dirs.append((math.cos(a) * r, math.sin(a) * r, z))
    maxd = size * 0.22
    for p in solids:
        P, N, C, UV, I = p.arrays(); occ = np.zeros(len(P))
        for vi in range(len(P)):
            n = Vector(N[vi]); n.normalize()
            t = n.cross(Vector((0, 0, 1))) if abs(n.z) < 0.95 else n.cross(Vector((1, 0, 0)))
            t.normalize(); b = n.cross(t); o = Vector(P[vi]) + n * (size * 0.004 + 0.004); hit = 0.0
            for dx, dy, dz in dirs:
                d = t * dx + b * dy + n * dz
                h = bvh.ray_cast(o, d, maxd)
                if h[0] is not None: hit += 1 - h[3] / maxd
            occ[vi] = hit / samples
        C[:, :3] *= (1 - strength * np.clip(occ * 1.6, 0, 1))[:, None]
        p.P = [P]; p.N = [N]; p.C = [C]; p.UV = [UV]; p.I = [I]

# ------------------------------------------------------------------ export
def to_engine(a): return np.stack([a[:, 0], a[:, 2], -a[:, 1]], 1)
def wstr(f, s):
    b = s.encode('utf-8'); f.write(struct.pack('<H', len(b))); f.write(b)
def export(models, path):
    with open(path, 'wb') as f:
        f.write(b'WBM3'); f.write(struct.pack('<H', len(models)))
        tv = tt = 0
        for m in models:
            wstr(f, m.id); wstr(f, m.name); wstr(f, m.cat); wstr(f, m.sub)
            f.write(struct.pack('<B', m.flags)); f.write(struct.pack('<B', len(m.tints)))
            for label, c in m.tints:
                wstr(f, label); f.write(struct.pack('<3B', *[int(round(x * 255)) for x in rgb(c)]))
            for ax in 'xyz':
                za, zb = m.zone.get(ax, (float('nan'), float('nan'))); f.write(struct.pack('<ff', za, zb))
            parts = [p for p in m.parts if p.nv]
            f.write(struct.pack('<H', len(parts)))
            for p in parts:
                P, N, C, UV, I = p.arrays(); P = to_engine(P); N = to_engine(N)
                N = N / (np.linalg.norm(N, axis=1, keepdims=True) + 1e-9)
                assert len(P) < 65536, (m.id, len(P))
                f.write(struct.pack('<6B', p.mat, p.flags, p.tint, p.tint2, 0, 0))
                f.write(struct.pack('<3B', *[MODE[c] for c in p.modes]))
                for ax in 'xyz':
                    a, b, n = p.rep.get(ax, (0.0, 0.0, 1)); f.write(struct.pack('<ffB', a, b, n))
                f.write(struct.pack('<ff', p.slope, p.sref))
                f.write(struct.pack('<II', len(P), len(I)))
                data = np.zeros(len(P), dtype=[('p', '<f4', 3), ('n', '<f4', 3), ('c', 'u1', 4), ('uv', '<f4', 2)])
                data['p'] = P; data['n'] = N; data['c'] = np.clip(C * 255, 0, 255).astype(np.uint8); data['uv'] = UV
                f.write(data.tobytes()); f.write(I.astype('<u2').tobytes())
                tv += len(P); tt += len(I) // 3
    print('exported', len(models), 'models,', tv, 'vertices,', tt, 'triangles ->', path)
