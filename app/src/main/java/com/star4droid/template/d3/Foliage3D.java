package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.ShortArray;
import com.star4droid.star2d.ElementDefs.Object3DDef;

/**
 * Trees, rocks, bushes, flowers... scattered on a painted terrain, plus the grass that grows on its grass layer.
 *
 * Built for phones: the objects of a 32 m square are merged into one mesh per kind (a few draw calls for thousands of
 * trees), squares out of view or farther than their draw distance are skipped, grass is made only around the camera
 * (12 m squares, a couple per frame) and thins out with the distance. Vertex alpha = sway in the wind (shader).
 */
public class Foliage3D implements RenderableProvider {
	public static final float CELL = 40f, GRASS_CELL = 12f;
	/** Something walking through the grass (x, y, z, radius m): the blades bend away. Set by the player controllers. */
	public static final Vector3 bendPos = new Vector3(0, -9999, 0);
	public static float bendRadius = 0.9f;
	/** Element that bends the grass (its feet), null = bendPos as set. */
	public static Object3D bender;
	public static float wind = 1f;

	final Terrain3D t;
	Object3D owner;
	private final Array<Cell> cells = new Array<Cell>();
	private boolean cellsDirty = true;
	private final LongMap<GCell> grass = new LongMap<GCell>();
	private final Matrix4 space = new Matrix4(), inv = new Matrix4();
	private float sx = 1, sy = 1, sz = 1, mh = 1;
	private int frame;

	static final class Part {
		Mesh mesh;
		Material mat;
		int count;
		boolean shadows;
		float distance;
	}

	static final class Cell {
		final Vector3 center = new Vector3();
		float radius;
		final Array<Part> parts = new Array<Part>();
	}

	static final class GCell {
		Mesh mesh;
		int count;
		final Vector3 center = new Vector3();
		int used;
	}

	public Foliage3D(Terrain3D t) {
		this.t = t;
	}

	/** Usual settings of a kind (collider, shadows) when it is added. */
	static void defaults(TerrainData.Proto p) {
		if (FoliageMeshes.isBuiltin(p.src)) {
			float[] d = FoliageMeshes.defaults(p.src);
			p.collide = d[0];
			p.collideHeight = d[1];
			p.shadows = d[2] > 0 || p.src.contains("bush");
			p.tint = 0.12f;
		} else {
			p.collide = 0.4f;
			p.collideHeight = 3;
			p.shadows = true;
			p.tint = 0;
		}
	}

	public void invalidate() {
		cellsDirty = true;
		clearGrass();
	}

	public void instancesChanged() {
		cellsDirty = true;
	}

	void heightsChanged(int i0, int j0, int i1, int j1) {
		cellsDirty = true;
		clearGrass();
	}

	void grassChanged() {
		clearGrass();
	}

	private void clearGrass() {
		for (GCell g : grass.values()) if (g.mesh != null) g.mesh.dispose();
		grass.clear();
	}

	public void dispose() {
		for (Cell c : cells) for (Part p : c.parts) if (p.mesh != null) p.mesh.dispose();
		cells.clear();
		clearGrass();
	}

	private Object3DDef def() {
		return owner == null ? null : owner.d();
	}

	// ------------------------------------------------------------------ space

	/** The foliage is built in the terrain's space without its scale (trees keep their shape on a stretched terrain). */
	private boolean updateSpace(Object3D o) {
		owner = o;
		space.set(o.position, o.rotation);
		inv.set(space).inv();
		float nx = Math.abs(o.scale.x), ny = Math.abs(o.scale.y), nz = Math.abs(o.scale.z);
		if (nx != sx || ny != sy || nz != sz || mh != t.maxHeight) {
			sx = nx;
			sy = ny;
			sz = nz;
			mh = t.maxHeight;
			cellsDirty = true;
			clearGrass();
		}
		return true;
	}

	/** Point of terrain u, v in foliage space. */
	private Vector3 local(float u, float v, Vector3 out) {
		return out.set((u - 0.5f) * sx, t.data.height(u, v) * mh * sy, (v - 0.5f) * sz);
	}

	// ------------------------------------------------------------------ objects

	private static final VertexAttributes BUILTIN_ATTRS = new VertexAttributes(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.ColorUnpacked());
	private static final VertexAttributes MODEL_ATTRS = new VertexAttributes(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0));

	/** Geometry of a kind: built-in or the parts of a project model (moved to their node place, one material each). */
	static final class Src {
		Array<float[]> verts = new Array<float[]>();
		Array<short[]> idx = new Array<short[]>();
		Array<Material> mats = new Array<Material>();
		boolean builtin;
	}

	private Src source(TerrainData.Proto p) {
		Src s = new Src();
		if (FoliageMeshes.isBuiltin(p.src)) {
			FoliageMeshes.ProtoMesh m = FoliageMeshes.get(p.src);
			s.builtin = true;
			s.verts.add(m.verts);
			s.idx.add(m.idx);
			Material mat = new Material(ColorAttribute.createDiffuse(Color.WHITE), new FoliageAttribute(1f, false));
			s.mats.add(mat);
			return s;
		}
		Model model = null;
		try {
			model = Assets3D.modelOf(p.src);
		} catch (Throwable e) {
			Gdx.app.error("Star2D-3D", "foliage model " + p.src + ": " + e);
		}
		if (model == null) {
			FoliageMeshes.ProtoMesh m = FoliageMeshes.get("rock");
			s.builtin = true;
			s.verts.add(m.verts);
			s.idx.add(m.idx);
			s.mats.add(new Material(ColorAttribute.createDiffuse(Color.MAGENTA), new FoliageAttribute(0f, false)));
			return s;
		}
		for (Node n : model.nodes) collect(n, s);
		return s;
	}

	private static void collect(Node n, Src s) {
		Matrix4 g = n.globalTransform;
		for (NodePart np : n.parts) {
			if (np.meshPart == null || np.meshPart.mesh == null || np.meshPart.primitiveType != GL20.GL_TRIANGLES) continue;
			if (np.bones != null) continue;
			Mesh mesh = np.meshPart.mesh;
			VertexAttributes va = mesh.getVertexAttributes();
			int stride = mesh.getVertexSize() / 4;
			VertexAttribute pa = va.findByUsage(VertexAttributes.Usage.Position), na = va.findByUsage(VertexAttributes.Usage.Normal);
			VertexAttribute ta = va.findByUsage(VertexAttributes.Usage.TextureCoordinates);
			if (pa == null) continue;
			float[] all = new float[mesh.getNumVertices() * stride];
			mesh.getVertices(all);
			int nIdx = mesh.getNumIndices();
			short[] src;
			if (nIdx > 0) {
				short[] ai = new short[nIdx];
				mesh.getIndices(ai);
				src = new short[np.meshPart.size];
				System.arraycopy(ai, np.meshPart.offset, src, 0, np.meshPart.size);
			} else {
				src = new short[np.meshPart.size];
				for (int k = 0; k < src.length; k++) src[k] = (short) (np.meshPart.offset + k);
			}
			// only the vertices this part uses, renumbered
			com.badlogic.gdx.utils.IntIntMap remap = new com.badlogic.gdx.utils.IntIntMap();
			FloatArray out = new FloatArray();
			short[] idx = new short[src.length];
			Vector3 v = new Vector3(), nn = new Vector3();
			Matrix4 nm = new Matrix4(g).inv().tra();
			for (int k = 0; k < src.length; k++) {
				int vi = src[k] & 0xffff;
				int m = remap.get(vi, -1);
				if (m < 0) {
					m = out.size / 8;
					remap.put(vi, m);
					int o = vi * stride;
					v.set(all[o + pa.offset / 4], all[o + pa.offset / 4 + 1], all[o + pa.offset / 4 + 2]).mul(g);
					if (na != null) nn.set(all[o + na.offset / 4], all[o + na.offset / 4 + 1], all[o + na.offset / 4 + 2]).mul(nm).nor();
					else nn.set(0, 1, 0);
					float tu = ta != null ? all[o + ta.offset / 4] : 0, tv = ta != null ? all[o + ta.offset / 4 + 1] : 0;
					out.addAll(v.x, v.y, v.z, nn.x, nn.y, nn.z, tu, tv);
				}
				idx[k] = (short) m;
			}
			s.verts.add(out.toArray());
			s.idx.add(idx);
			Material mat = new Material(np.material);
			mat.set(new FoliageAttribute(0f, false));
			s.mats.add(mat);
		}
		for (Node c : n.getChildren()) collect(c, s);
	}

	private void buildCells() {
		cellsDirty = false;
		for (Cell c : cells) for (Part p : c.parts) if (p.mesh != null) p.mesh.dispose();
		cells.clear();
		TerrainData d = t.data;
		if (d == null || d.inst.size == 0) return;
		int cx = Math.max(1, MathUtils.ceil(sx / CELL)), cz = Math.max(1, MathUtils.ceil(sz / CELL));
		// instances per cell
		@SuppressWarnings("unchecked")
		Array<TerrainData.Inst>[] per = new Array[cx * cz];
		for (TerrainData.Inst in : d.inst) {
			int i = MathUtils.clamp((int) (in.u * cx), 0, cx - 1), j = MathUtils.clamp((int) (in.v * cz), 0, cz - 1);
			Array<TerrainData.Inst> a = per[j * cx + i];
			if (a == null) a = per[j * cx + i] = new Array<TerrainData.Inst>();
			a.add(in);
		}
		Src[] srcs = new Src[d.protos.size];
		for (int k = 0; k < srcs.length; k++) srcs[k] = source(d.protos.get(k));
		Vector3 p = new Vector3(), q = new Vector3(), nrm = new Vector3();
		Matrix4 m = new Matrix4();
		Object3DDef od = def();
		float defDist = od != null && od.Foliage_Distance > 0 ? od.Foliage_Distance : 0;
		for (int c = 0; c < per.length; c++) {
			Array<TerrainData.Inst> list = per[c];
			if (list == null) continue;
			Cell cell = new Cell();
			float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
			// built-in kinds share one vertex format and material: one mesh per cell for all of them (per shadow / distance
			// setting), project models one per model part
			com.badlogic.gdx.utils.ObjectMap<String, Acc> accs = new com.badlogic.gdx.utils.ObjectMap<String, Acc>();
			for (TerrainData.Inst in : list) {
				int k = in.p;
				TerrainData.Proto proto = d.protos.get(k);
				Src s = srcs[k];
				local(in.u, in.v, p);
				lo = Math.min(lo, p.y);
				hi = Math.max(hi, p.y + 4 * in.s);
				m.idt().translate(p);
				if (proto.alignToGround) {
					float e = 1f / Math.max(4, d.res);
					float hx = (d.height(in.u + e, in.v) - d.height(in.u - e, in.v)) * mh * sy / (2 * e * sx);
					float hz = (d.height(in.u, in.v + e) - d.height(in.u, in.v - e)) * mh * sy / (2 * e * sz);
					nrm.set(-hx, 1, -hz).nor();
					m.rotate(new com.badlogic.gdx.math.Quaternion().setFromCross(Vector3.Y, nrm));
				}
				m.rotate(Vector3.Y, in.rot).scale(in.s, in.s, in.s);
				float tint = 1f + (hashf(in.seed) - 0.5f) * 2f * proto.tint;
				float hueShift = (hashf(in.seed * 31 + 7) - 0.5f) * proto.tint;
				float dist = proto.distance > 0 ? proto.distance : defDist;
				for (int part = 0; part < s.verts.size; part++) {
					float[] sv = s.verts.get(part);
					short[] si = s.idx.get(part);
					int stride = s.builtin ? FoliageMeshes.STRIDE : 8;
					int nv = sv.length / stride;
					if (nv == 0 || nv > 60000) continue;
					String key = s.builtin ? ("B" + proto.shadows + "|" + dist) : ("M" + k + "|" + part);
					Acc acc = accs.get(key);
					if (acc != null && acc.v.size / stride + nv > 60000) {
						acc.flush(this, cell);
						acc = null;
					}
					if (acc == null) {
						acc = new Acc(s, part, proto.shadows, dist);
						accs.put(key, acc);
					}
					int base = acc.v.size / stride;
					for (int vtx = 0; vtx < nv; vtx++) {
						int o = vtx * stride;
						q.set(sv[o], sv[o + 1], sv[o + 2]).mul(m);
						nrm.set(sv[o + 3], sv[o + 4], sv[o + 5]).rot(m).nor();
						acc.v.addAll(q.x, q.y, q.z, nrm.x, nrm.y, nrm.z);
						if (s.builtin) acc.v.addAll(Math.min(1, sv[o + 6] * tint * (1 + hueShift)), Math.min(1, sv[o + 7] * tint), Math.min(1, sv[o + 8] * tint * (1 - hueShift)), sv[o + 9]);
						else acc.v.addAll(sv[o + 6], sv[o + 7]);
					}
					for (short ix : si) acc.i.add((short) (base + (ix & 0xffff)));
				}
			}
			for (Acc acc : accs.values()) acc.flush(this, cell);
			if (cell.parts.size == 0) continue;
			int ci = c % cx, cj = c / cx;
			float x0 = (ci / (float) cx - 0.5f) * sx, x1 = ((ci + 1) / (float) cx - 0.5f) * sx;
			float z0 = (cj / (float) cz - 0.5f) * sz, z1 = ((cj + 1) / (float) cz - 0.5f) * sz;
			cell.center.set((x0 + x1) / 2, (lo + hi) / 2, (z0 + z1) / 2);
			cell.radius = 0.5f * (float) Math.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0) + (hi - lo) * (hi - lo)) + 3f;
			cells.add(cell);
		}
	}

	/** Vertices / indices being merged for one mesh of a cell. */
	static final class Acc {
		final FloatArray v = new FloatArray();
		final ShortArray i = new ShortArray();
		final Src s;
		final int part;
		final boolean shadows;
		final float distance;

		Acc(Src s, int part, boolean shadows, float distance) {
			this.s = s;
			this.part = part;
			this.shadows = shadows;
			this.distance = distance;
		}

		void flush(Foliage3D f, Cell cell) {
			if (i.size == 0) return;
			Part pt = new Part();
			pt.mesh = new Mesh(true, v.size / (s.builtin ? FoliageMeshes.STRIDE : 8), i.size, s.builtin ? BUILTIN_ATTRS : MODEL_ATTRS);
			pt.mesh.setVertices(v.items, 0, v.size);
			pt.mesh.setIndices(i.items, 0, i.size);
			pt.count = i.size;
			pt.mat = s.mats.get(part);
			pt.shadows = shadows;
			pt.distance = distance;
			cell.parts.add(pt);
			v.clear();
			i.clear();
		}
	}

	static float hashf(int s) {
		s = s * 1103515245 + 12345;
		s ^= s >>> 15;
		s *= 0x2c1b3c6d;
		s ^= s >>> 12;
		return (s & 0xffffff) / (float) 0xffffff;
	}

	// ------------------------------------------------------------------ grass

	private float grassDensity() {
		Object3DDef d = def();
		if (d == null || d.Grass_Density <= 0) return 0;
		String q = Quality3D.level;
		float k = Quality3D.LOW.equals(q) ? 0.3f : (Quality3D.HIGH.equals(q) ? 1f : (Quality3D.ULTRA.equals(q) ? 1.25f : 0.65f));
		return d.Grass_Density * k;
	}

	private float grassDistance() {
		Object3DDef d = def();
		float g = d == null || d.Grass_Distance <= 0 ? 28f : d.Grass_Distance;
		return g * (Quality3D.LOW.equals(Quality3D.level) ? 0.6f : 1f);
	}

	private GCell buildGrass(int gx, int gz) {
		GCell g = new GCell();
		Object3DDef d = def();
		float dens = grassDensity();
		int layer = MathUtils.clamp((int) d.Grass_Layer - 1, 0, 3);
		float hgt = d.Grass_Height <= 0 ? 0.45f : d.Grass_Height;
		Color col = Materials3D.color(d.Grass_Color, new Color(0.45f, 0.72f, 0.25f, 1));
		int n = Math.min(900, (int) (dens * GRASS_CELL * GRASS_CELL));
		FoliageMeshes.ProtoMesh tuft = FoliageMeshes.grassTuft();
		int nv = tuft.vertexCount();
		FloatArray ov = new FloatArray(n * nv * FoliageMeshes.STRIDE / 2);
		ShortArray oi = new ShortArray();
		Vector3 p = new Vector3(), q = new Vector3();
		Matrix4 m = new Matrix4();
		int seed = gx * 73856093 ^ gz * 19349663;
		float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
		for (int k = 0; k < n && ov.size / FoliageMeshes.STRIDE + nv < 30000; k++) {
			float lx = (gx + hashf(seed + k * 3)) * GRASS_CELL, lz = (gz + hashf(seed + k * 3 + 1)) * GRASS_CELL;
			float u = lx / sx + 0.5f, v = lz / sz + 0.5f;
			if (u < 0.002f || u > 0.998f || v < 0.002f || v > 0.998f) continue;
			float w = t.data.weight(layer, u, v);
			float r = hashf(seed + k * 3 + 2);
			if (r > w * 1.15f - 0.1f) continue;
			if (t.data.slope(u, v, sx, sz, mh * sy) > 0.62f) continue;
			local(u, v, p);
			lo = Math.min(lo, p.y);
			hi = Math.max(hi, p.y + hgt);
			float s = hgt * (0.7f + r * 0.6f);
			m.idt().translate(p).rotate(Vector3.Y, hashf(seed + k * 5) * 360f).scale(1.6f + r, s, 1.6f + r);
			float tone = 0.86f + hashf(seed + k * 7) * 0.24f;
			int base = ov.size / FoliageMeshes.STRIDE;
			for (int vtx = 0; vtx < nv; vtx++) {
				int o = vtx * FoliageMeshes.STRIDE;
				q.set(tuft.verts[o], tuft.verts[o + 1], tuft.verts[o + 2]).mul(m);
				float c = tuft.verts[o + 6] * tone;
				ov.addAll(q.x, q.y, q.z, 0, 1, 0, Math.min(1, col.r * c), Math.min(1, col.g * c), Math.min(1, col.b * c), tuft.verts[o + 9]);
			}
			for (short ix : tuft.idx) oi.add((short) (base + ix));
		}
		g.center.set((gx + 0.5f) * GRASS_CELL, lo == Float.MAX_VALUE ? 0 : (lo + hi) / 2, (gz + 0.5f) * GRASS_CELL);
		if (oi.size > 0) {
			g.mesh = new Mesh(true, ov.size / FoliageMeshes.STRIDE, oi.size, BUILTIN_ATTRS);
			g.mesh.setVertices(ov.items, 0, ov.size);
			g.mesh.setIndices(oi.items, 0, oi.size);
			g.count = oi.size;
		}
		return g;
	}

	private static Material grassMat;

	private static Material grassMaterial() {
		if (grassMat == null) grassMat = new Material(ColorAttribute.createDiffuse(Color.WHITE), new FoliageAttribute(1f, true), IntAttribute.createCullFace(GL20.GL_NONE));
		return grassMat;
	}

	static void forgetGL() {
		grassMat = null;
	}

	// ------------------------------------------------------------------ drawing

	private Camera cam;
	private boolean shadowPass;
	private float shadowDist;
	private final Vector3 cpos = new Vector3(), tmp = new Vector3();

	/** Draws the foliage of the terrain element o (inside the batch's begin / end). */
	public void render(ModelBatch batch, Camera cam, Environment env, Object3D o) {
		if (t.data == null) return;
		updateSpace(o);
		if (cellsDirty) buildCells();
		this.cam = cam;
		shadowPass = false;
		frame++;
		if (bender != null) {
			if (bender.world == null || !bender.isVisible()) bender = null;
			else bendPos.set(bender.position.x, bender.position.y, bender.position.z);
		}
		batch.render(this, env);
	}

	/** Shadow casters near the shadow center (trees, rocks: no grass). */
	public void renderShadows(ModelBatch batch, Camera cam, Vector3 center, float dist, Object3D o) {
		if (t.data == null) return;
		updateSpace(o);
		if (cellsDirty) buildCells();
		this.cam = cam;
		shadowPass = true;
		shadowDist = dist;
		cpos.set(center);
		batch.render(this);
	}

	@Override
	public void getRenderables(Array<Renderable> out, Pool<Renderable> pool) {
		if (!shadowPass) cpos.set(cam.position);
		float view = Quality3D.viewDistance * Math.max(0.3f, Quality3D.lodBias);
		for (Cell c : cells) {
			tmp.set(c.center).mul(space);
			float d = tmp.dst(cpos) - c.radius;
			if (shadowPass) {
				if (d > shadowDist) continue;
			} else if (!cam.frustum.sphereInFrustum(tmp, c.radius)) continue;
			for (Part p : c.parts) {
				if (shadowPass && !p.shadows) continue;
				float maxD = p.distance > 0 ? p.distance : view;
				if (d > maxD) continue;
				Renderable r = pool.obtain();
				r.worldTransform.set(space);
				r.meshPart.set("f", p.mesh, 0, p.count, GL20.GL_TRIANGLES);
				r.material = p.mat;
				r.bones = null;
				r.shader = null;
				r.userData = null;
				r.environment = null;
				out.add(r);
			}
		}
		if (shadowPass) return;
		float dens = grassDensity();
		if (dens <= 0) return;
		float gd = grassDistance();
		// camera in foliage space
		tmp.set(cam.position).mul(inv);
		int g0x = MathUtils.floor((tmp.x - gd) / GRASS_CELL), g1x = MathUtils.floor((tmp.x + gd) / GRASS_CELL);
		int g0z = MathUtils.floor((tmp.z - gd) / GRASS_CELL), g1z = MathUtils.floor((tmp.z + gd) / GRASS_CELL);
		int built = 0;
		for (int gz = g0z; gz <= g1z; gz++)
			for (int gx = g0x; gx <= g1x; gx++) {
				if ((gx + 1) * GRASS_CELL < -sx / 2 || gx * GRASS_CELL > sx / 2 || (gz + 1) * GRASS_CELL < -sz / 2 || gz * GRASS_CELL > sz / 2) continue;
				float cxw = (gx + 0.5f) * GRASS_CELL - tmp.x, czw = (gz + 0.5f) * GRASS_CELL - tmp.z;
				float dist = (float) Math.sqrt(cxw * cxw + czw * czw) - GRASS_CELL * 0.71f;
				if (dist > gd) continue;
				long key = ((long) gx << 32) ^ (gz & 0xffffffffL);
				GCell g = grass.get(key);
				if (g == null) {
					if (built >= 2) continue; // a couple per frame: no hitch when walking
					g = buildGrass(gx, gz);
					grass.put(key, g);
					built++;
				}
				g.used = frame;
				if (g.mesh == null) continue;
				Vector3 wc = cpos.set(g.center).mul(space);
				if (!cam.frustum.sphereInFrustum(wc, GRASS_CELL * 0.75f + 1f)) continue;
				// thinner far away: the tufts of a square are in random order, draw the first part only
				float keep = MathUtils.clamp(1.25f - Math.max(0, dist) / gd, 0.15f, 1f);
				int per = FoliageMeshes.grassTuft().idx.length;
				int count = (int) (g.count * keep) / per * per;
				if (count <= 0) continue;
				Renderable r = pool.obtain();
				r.worldTransform.set(space);
				r.meshPart.set("g", g.mesh, 0, count, GL20.GL_TRIANGLES);
				r.material = grassMaterial();
				r.bones = null;
				r.shader = null;
				r.userData = null;
				r.environment = null;
				out.add(r);
			}
		cpos.set(cam.position);
		// forget squares not drawn for a while
		if (frame % 60 == 0 && grass.size > 48) {
			com.badlogic.gdx.utils.LongArray drop = new com.badlogic.gdx.utils.LongArray();
			for (LongMap.Entry<GCell> e : grass.entries()) if (frame - e.value.used > 120) drop.add(e.key);
			for (int k = 0; k < drop.size; k++) {
				GCell g = grass.remove(drop.get(k));
				if (g != null && g.mesh != null) g.mesh.dispose();
			}
		}
	}

	// ------------------------------------------------------------------ colliders

	/** Colliders of the objects that block (x, z in the terrain's unscaled space, radius, height). */
	public FloatArray colliders(Object3D o) {
		updateSpace(o);
		FloatArray out = new FloatArray();
		TerrainData d = t.data;
		if (d == null) return out;
		Vector3 p = new Vector3();
		for (TerrainData.Inst in : d.inst) {
			TerrainData.Proto proto = d.protos.get(in.p);
			if (proto.collide <= 0) continue;
			local(in.u, in.v, p);
			out.addAll(p.x, p.y, p.z, proto.collide * in.s, Math.max(0.2f, proto.collideHeight * in.s));
		}
		return out;
	}

	// ------------------------------------------------------------------ shader

	/** Sway in the wind (1 = the vertex alpha decides), grass = 2 sided + bending. */
	public static class FoliageAttribute extends Attribute {
		public static final long Type = register("s2dFoliage");
		public float sway;
		public boolean grass;

		public FoliageAttribute(float sway, boolean grass) {
			super(Type);
			this.sway = sway;
			this.grass = grass;
		}

		@Override
		public Attribute copy() {
			return new FoliageAttribute(sway, grass);
		}

		@Override
		public int compareTo(Attribute o) {
			return type == o.type ? 0 : (type < o.type ? -1 : 1);
		}
	}

	private static String foliageVertex(String v) {
		String anchor = "vec4 pos = u_worldTrans * vec4(a_position, 1.0);";
		if (!v.contains(anchor)) return null;
		v = v.replace("void main() {", "uniform vec4 u_sway;\nuniform vec4 u_bend;\nvoid main() {");
		String blk = anchor + "\n\t#ifdef colorFlag\n"
				+ "\tfloat swk = a_color.a * u_sway.x;\n"
				+ "\tif (swk > 0.0) {\n"
				+ "\t\tfloat ph = pos.x * 0.35 + pos.z * 0.27;\n"
				+ "\t\tpos.x += sin(u_sway.z * 1.7 + ph) * 0.09 * swk * u_sway.y;\n"
				+ "\t\tpos.z += cos(u_sway.z * 1.3 + ph * 1.3) * 0.07 * swk * u_sway.y;\n"
				+ "\t\tvec2 dd = pos.xz - u_bend.xz;\n"
				+ "\t\tfloat dl = length(dd);\n"
				+ "\t\tif (dl < u_bend.w && abs(pos.y - u_bend.y) < 2.0) {\n"
				+ "\t\t\tfloat k = (u_bend.w - dl) * swk;\n"
				+ "\t\t\tpos.xz += dd / max(dl, 0.001) * k * 0.7;\n"
				+ "\t\t\tpos.y -= k * 0.35;\n"
				+ "\t\t}\n"
				+ "\t}\n"
				+ "\t#endif\n";
		v = v.replace(anchor, blk);
		v = v.replace("v_color = a_color;", "v_color = vec4(a_color.rgb, 1.0);");
		return v;
	}

	public static class FoliageShader extends Shaders3D.Lit {
		public FoliageShader(Renderable r, DefaultShader.Config c) {
			super(r, c);
			register(new Uniform("u_sway"), new LocalSetter() {
				@Override
				public void set(BaseShader shader, int inputID, Renderable renderable, com.badlogic.gdx.graphics.g3d.Attributes combined) {
					FoliageAttribute fa = (FoliageAttribute) combined.get(FoliageAttribute.Type);
					shader.set(inputID, fa == null ? 0 : fa.sway, wind, World3D.time, 0);
				}
			});
			register(new Uniform("u_bend"), new GlobalSetter() {
				@Override
				public void set(BaseShader shader, int inputID, Renderable renderable, com.badlogic.gdx.graphics.g3d.Attributes combined) {
					shader.set(inputID, bendPos.x, bendPos.y, bendPos.z, bendRadius);
				}
			});
		}

		@Override
		public boolean canRender(Renderable r) {
			return r.material != null && r.material.has(FoliageAttribute.Type) && super.canRender(r);
		}
	}

	static Shader shader(Renderable r, String mode) {
		try {
			DefaultShader.Config c = Shaders3D.config(Shaders3D.PBR.equals(mode) || Shaders3D.UNLIT.equals(mode) ? Shaders3D.SIMPLE : mode, Quality3D.pixelLighting);
			String v = foliageVertex(c.vertexShader);
			if (v == null) return null;
			c.vertexShader = v;
			return new FoliageShader(r, c);
		} catch (Throwable t) {
			Gdx.app.error("Star2D-3D", "foliage shader: " + t);
			return null;
		}
	}
}
