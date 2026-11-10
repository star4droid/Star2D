package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.star4droid.star2d.ElementDefs.Object3DDef;

import java.nio.FloatBuffer;

/**
 * Terrain element. A 1 x 1 grid (scaled by the element: Scale X / Z = size in meters, Scale Y multiplies the height)
 * built from a {@link TerrainData}: a painted terrain file ("Terrain Data", made by the terrain editor or the
 * generator) or, for older terrains, a gray height image and / or noise.
 *
 * Fast on phones: the grid is cut in chunks of 64 x 64 cells, each a separate mesh hidden when out of view; brush
 * edits rewrite only the chunks they touch. Painted terrains blend 4 ground textures in one shader pass (splat map),
 * collide with a Bullet heightfield (exact, cheap) and carry their trees / rocks / grass ({@link Foliage3D}).
 */
public class Terrain3D {
	public static final int CHUNK = 64;
	public Model model;
	public int res;
	/** heights 0..1 of {@link #maxHeight} (same array as data.h). */
	public float[] h;
	public float maxHeight;
	public TerrainData data;
	/** From a terrain file (painted: splat shader, foliage, saved by the editor). */
	public boolean painted;
	/** File the data came from ("" = made from Heightmap / noise). */
	public String file = "";
	public Texture splatTexture;
	private Pixmap splatPixmap;
	public Foliage3D foliage;
	/** Bullet heightfield samples (meters before Scale Y), kept alive while the shape exists. */
	FloatBuffer fieldBuffer;
	private final Array<Chunk> chunks = new Array<Chunk>();
	private boolean dynamicMeshes;

	static final class Chunk {
		int i0, j0, cw, ch; // first cell, cells
		Mesh mesh;
		float[] verts;
		NodePart part;
		/** Index ranges: full detail, then half detail (edges kept full: no cracks with a full neighbour). */
		int fullCount, lodOffset, lodCount;
		final Vector3 center = new Vector3(), half = new Vector3();
		float radius;
		int nodeIndex;
	}

	/** A terrain from a definition: the "Terrain Data" file when there is one, else the height image / noise. */
	public static Terrain3D build(Object3DDef d) {
		return build(d, false);
	}

	/** editor = meshes that the brushes rewrite often. */
	public static Terrain3D build(Object3DDef d, boolean editor) {
		Terrain3D t = new Terrain3D();
		t.dynamicMeshes = editor;
		t.maxHeight = d.Terrain_Height;
		TerrainData td = null;
		if (d.Terrain_Data != null && d.Terrain_Data.length() > 0) {
			td = TerrainStore.get(d.Terrain_Data);
			if (td != null) {
				t.painted = true;
				t.file = d.Terrain_Data;
			}
		}
		if (td == null) td = legacy(d);
		t.setData(td);
		return t;
	}

	/** Heights from the gray image (white = Terrain Height) and / or noise: terrains made before the terrain editor. */
	public static TerrainData legacy(Object3DDef d) {
		TerrainData td = new TerrainData(MathUtils.clamp((int) d.Terrain_Resolution, 4, 200), 16);
		int n = td.n();
		Pixmap pm = d.Heightmap != null && d.Heightmap.length() > 0 ? Assets3D.pixmap(d.Heightmap) : null;
		Color c = new Color();
		for (int j = 0; j < n; j++)
			for (int i = 0; i < n; i++) {
				float v = 0;
				if (pm != null) {
					int px = MathUtils.clamp((int) ((float) i / td.res * (pm.getWidth() - 1)), 0, pm.getWidth() - 1);
					int py = MathUtils.clamp((int) ((float) j / td.res * (pm.getHeight() - 1)), 0, pm.getHeight() - 1);
					Color.rgba8888ToColor(c, pm.getPixel(px, py));
					v = (c.r + c.g + c.b) / 3f;
				}
				if (d.Terrain_Noise > 0) v += d.Terrain_Noise * fbm(i * 4f / td.res, j * 4f / td.res, (int) d.Terrain_Seed);
				td.h[j * n + i] = v;
			}
		if (pm != null) pm.dispose();
		return td;
	}

	/** Uses this data (editor: after the generator / an undo) and rebuilds the meshes. */
	public void setData(TerrainData td) {
		data = td;
		res = td.res;
		h = td.h;
		buildMeshes();
		if (painted) {
			buildSplatTexture();
			if (foliage == null) foliage = new Foliage3D(this);
			foliage.invalidate();
		}
	}

	// ------------------------------------------------------------------ meshes

	private static final VertexAttributes ATTRS = new VertexAttributes(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0));
	private static final int STRIDE = 8;

	private void buildMeshes() {
		Model old = model;
		chunks.clear();
		model = new Model();
		Material mat = new Material();
		model.materials.add(mat);
		int n = res + 1;
		for (int j0 = 0; j0 < res; j0 += CHUNK)
			for (int i0 = 0; i0 < res; i0 += CHUNK) {
				Chunk c = new Chunk();
				c.i0 = i0;
				c.j0 = j0;
				c.cw = Math.min(CHUNK, res - i0);
				c.ch = Math.min(CHUNK, res - j0);
				int vw = c.cw + 1, vh = c.ch + 1;
				c.verts = new float[vw * vh * STRIDE];
				com.badlogic.gdx.utils.ShortArray ia = new com.badlogic.gdx.utils.ShortArray(c.cw * c.ch * 9);
				for (int j = 0; j < c.ch; j++)
					for (int i = 0; i < c.cw; i++) quad(ia, vw, i, j, 1);
				c.fullCount = ia.size;
				c.lodOffset = ia.size;
				for (int j = 0; j < c.ch; j += 2)
					for (int i = 0; i < c.cw; i += 2) {
						boolean edge = i == 0 || j == 0 || i + 2 >= c.cw || j + 2 >= c.ch;
						if (edge || i + 1 >= c.cw || j + 1 >= c.ch) {
							for (int b = j; b < Math.min(j + 2, c.ch); b++)
								for (int a = i; a < Math.min(i + 2, c.cw); a++) quad(ia, vw, a, b, 1);
						} else quad(ia, vw, i, j, 2);
					}
				c.lodCount = ia.size - c.lodOffset;
				short[] idx = ia.toArray();
				c.mesh = new Mesh(!dynamicMeshes, vw * vh, idx.length, ATTRS);
				c.mesh.setIndices(idx);
				fillChunk(c, 0, 0, res, res);
				c.mesh.setVertices(c.verts);
				MeshPart mp = new MeshPart("t" + chunks.size, c.mesh, 0, c.fullCount, GL20.GL_TRIANGLES);
				Node node = new Node();
				node.id = "chunk" + chunks.size;
				NodePart np = new NodePart(mp, mat);
				node.parts.add(np);
				model.nodes.add(node);
				model.meshes.add(c.mesh);
				model.meshParts.add(mp);
				model.manageDisposable(c.mesh);
				c.nodeIndex = chunks.size;
				chunkBounds(c);
				chunks.add(c);
			}
		if (old != null) {
			final Model o = old;
			// the old meshes may still be in this frame's render list: free them a bit later
			Gdx.app.postRunnable(new Runnable() {
				public void run() {
					try {
						o.dispose();
					} catch (Throwable ignored) {
					}
				}
			});
		}
	}

	/** Two triangles of a cell (or of a 2 x 2 block, step 2), diagonal (i + s, j) - (i, j + s) like the heightfield. */
	private static void quad(com.badlogic.gdx.utils.ShortArray ia, int vw, int i, int j, int st) {
		short a = (short) (j * vw + i), b = (short) (j * vw + i + st), cc = (short) ((j + st) * vw + i), dd = (short) ((j + st) * vw + i + st);
		ia.add(a);
		ia.add(cc);
		ia.add(b);
		ia.add(b);
		ia.add(cc);
		ia.add(dd);
	}

	private void chunkBounds(Chunk c) {
		float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
		for (int j = c.j0; j <= c.j0 + c.ch; j++)
			for (int i = c.i0; i <= c.i0 + c.cw; i++) {
				float v = at(i, j);
				lo = Math.min(lo, v);
				hi = Math.max(hi, v);
			}
		float x0 = (float) c.i0 / res - 0.5f, x1 = (float) (c.i0 + c.cw) / res - 0.5f;
		float z0 = (float) c.j0 / res - 0.5f, z1 = (float) (c.j0 + c.ch) / res - 0.5f;
		c.center.set((x0 + x1) / 2, (lo + hi) / 2 * maxHeight, (z0 + z1) / 2);
		c.half.set((x1 - x0) * 0.5f, (hi - lo) * maxHeight * 0.5f, (z1 - z0) * 0.5f);
		c.radius = c.half.len();
	}

	/** Writes the vertices of the grid points of a chunk inside cells i0..i1, j0..j1 (normals need one more ring). */
	private void fillChunk(Chunk c, int i0, int j0, int i1, int j1) {
		int vw = c.cw + 1;
		int a0 = Math.max(c.i0, i0), a1 = Math.min(c.i0 + c.cw, i1), b0 = Math.max(c.j0, j0), b1 = Math.min(c.j0 + c.ch, j1);
		float k = maxHeight * res * 0.5f;
		for (int j = b0; j <= b1; j++)
			for (int i = a0; i <= a1; i++) {
				int o = ((j - c.j0) * vw + (i - c.i0)) * STRIDE;
				float hl = at(i - 1, j), hr = at(i + 1, j), hd = at(i, j - 1), hu = at(i, j + 1);
				float nx = (hl - hr) * k, nz = (hd - hu) * k, inv = 1f / (float) Math.sqrt(nx * nx + 1 + nz * nz);
				c.verts[o] = (float) i / res - 0.5f;
				c.verts[o + 1] = at(i, j) * maxHeight;
				c.verts[o + 2] = (float) j / res - 0.5f;
				c.verts[o + 3] = nx * inv;
				c.verts[o + 4] = inv;
				c.verts[o + 5] = nz * inv;
				c.verts[o + 6] = (float) i / res;
				c.verts[o + 7] = (float) j / res;
			}
	}

	/** Heights changed in grid points i0..i1, j0..j1: rewrites only the chunks that show them. */
	public void heightsChanged(int i0, int j0, int i1, int j1) {
		i0 = Math.max(0, i0 - 1);
		j0 = Math.max(0, j0 - 1);
		i1 = Math.min(res, i1 + 1);
		j1 = Math.min(res, j1 + 1);
		for (Chunk c : chunks) {
			if (c.i0 > i1 || c.i0 + c.cw < i0 || c.j0 > j1 || c.j0 + c.ch < j0) continue;
			fillChunk(c, i0, j0, i1, j1);
			c.mesh.setVertices(c.verts);
			chunkBounds(c);
		}
		data.revision++;
		// the Bullet heightfield reads this buffer: update it in place (a new buffer would leave the shape on freed memory)
		if (fieldBuffer != null && fieldBuffer.capacity() == h.length) for (int k = 0; k < h.length; k++) fieldBuffer.put(k, h[k] * maxHeight);
		if (foliage != null) foliage.heightsChanged(i0, j0, i1, j1);
	}

	float at(int i, int j) {
		int n = res + 1;
		i = MathUtils.clamp(i, 0, res);
		j = MathUtils.clamp(j, 0, res);
		return h[j * n + i];
	}

	// ------------------------------------------------------------------ paint

	private void buildSplatTexture() {
		if (splatPixmap != null && (splatPixmap.getWidth() != data.splatRes)) {
			splatPixmap.dispose();
			splatPixmap = null;
		}
		if (splatPixmap == null) splatPixmap = new Pixmap(data.splatRes, data.splatRes, Pixmap.Format.RGBA8888);
		copySplat();
		if (splatTexture != null && splatTexture.getWidth() == data.splatRes) {
			splatTexture.draw(splatPixmap, 0, 0);
		} else {
			if (splatTexture != null) splatTexture.dispose();
			splatTexture = new Texture(splatPixmap, false);
			splatTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
			splatTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
		}
	}

	private void copySplat() {
		java.nio.ByteBuffer b = splatPixmap.getPixels();
		b.clear();
		b.put(data.splat, 0, Math.min(data.splat.length, b.capacity()));
		b.flip();
	}

	/** The paint changed: uploads it (call at most a few times per second while painting). */
	public void splatChanged() {
		if (!painted) return;
		if (splatPixmap == null || splatTexture == null) buildSplatTexture();
		else {
			copySplat();
			splatTexture.draw(splatPixmap, 0, 0);
		}
		data.revision++;
		if (foliage != null) foliage.grassChanged();
	}

	/** Material of a painted terrain: 4 layers blended by the paint map. */
	public void applyMaterial(ModelInstance inst, Object3DDef d) {
		if (inst == null) return;
		TerrainAttribute ta = new TerrainAttribute();
		String[] tex = {d.Layer_1_Texture, d.Layer_2_Texture, d.Layer_3_Texture, d.Layer_4_Texture};
		String[] col = {d.Layer_1_Color, d.Layer_2_Color, d.Layer_3_Color, d.Layer_4_Color};
		float[] til = {d.Layer_1_Tiling, d.Layer_2_Tiling, d.Layer_3_Tiling, d.Layer_4_Tiling};
		for (int i = 0; i < 4; i++) {
			ta.layers[i] = TerrainTextures.get(tex[i], i);
			ta.colors[i].set(Materials3D.color(col[i], Color.WHITE));
			ta.tiling[i] = til[i] <= 0 ? 16 : til[i];
		}
		Color base = Materials3D.color(d.Color, Color.WHITE);
		for (Material m : inst.materials) {
			m.clear();
			m.set(ColorAttribute.createDiffuse(base));
			if (splatTexture != null) m.set(TextureAttribute.createDiffuse(splatTexture));
			m.set(ta);
		}
	}

	// ------------------------------------------------------------------ view

	private final Vector3 tmp = new Vector3();

	/** Chunks drawn with half detail in the last cull (tests). */
	public int lastFar;

	/** Chunks farther than this use half detail (m); 0 = always full. */
	public static float lodDistance = 55;

	/** Hides the chunks out of the camera's view and picks their detail (once per frame before drawing). */
	public void cull(Camera cam, ModelInstance inst, Matrix4 transform, Vector3 scale) {
		if (inst == null || cam == null) return;
		float sx = Math.abs(scale.x), sy = Math.abs(scale.y), sz = Math.abs(scale.z);
		float lod = lodDistance * Math.max(0.3f, Quality3D.lodBias);
		lastFar = 0;
		for (int k = 0; k < chunks.size && k < inst.nodes.size; k++) {
			Chunk c = chunks.get(k);
			tmp.set(c.center).mul(transform);
			Node node = inst.nodes.get(k);
			if (node.parts.size == 0) continue;
			NodePart np = node.parts.get(0);
			float hx = c.half.x * sx, hy = c.half.y * sy, hz = c.half.z * sz;
			float r = (float) Math.sqrt(hx * hx + hy * hy + hz * hz) + 0.5f;
			np.enabled = cam.frustum.sphereInFrustum(tmp, r);
			boolean far = lod > 0 && c.lodCount > 0 && tmp.dst(cam.position) - r > lod;
			np.meshPart.offset = far ? c.lodOffset : 0;
			np.meshPart.size = far ? c.lodCount : c.fullCount;
			if (far && np.enabled) lastFar++;
		}
	}

	/** Every chunk visible again (shadow pass / editor picking). */
	public void uncull(ModelInstance inst) {
		if (inst == null) return;
		for (int k = 0; k < inst.nodes.size; k++)
			for (NodePart p : inst.nodes.get(k).parts) {
				p.enabled = true;
				if (k < chunks.size) {
					p.meshPart.offset = 0;
					p.meshPart.size = chunks.get(k).fullCount;
				}
			}
	}

	// ------------------------------------------------------------------ queries

	/** World height at x, z (meters). */
	public float heightAt(Object3D o, float x, float z) {
		Vector3 p = new Vector3(x, 0, z);
		Matrix4 inv = new Matrix4(o.transform).inv();
		p.mul(inv);
		float hl = data.height(p.x + 0.5f, p.z + 0.5f) * maxHeight;
		Vector3 w = new Vector3(p.x, hl, p.z).mul(o.transform);
		return w.y;
	}

	/** Local u, v (0..1) of a world point, or false when it is outside the terrain. */
	public boolean uvAt(Object3D o, float x, float y, float z, float[] uv) {
		Vector3 p = new Vector3(x, y, z).mul(new Matrix4(o.transform).inv());
		uv[0] = p.x + 0.5f;
		uv[1] = p.z + 0.5f;
		return uv[0] >= 0 && uv[0] <= 1 && uv[1] >= 0 && uv[1] <= 1;
	}

	/** World point of terrain u, v. */
	public Vector3 worldAt(Object3D o, float u, float v, Vector3 out) {
		return out.set(u - 0.5f, data.height(u, v) * maxHeight, v - 0.5f).mul(o.transform);
	}

	/** Ray hit with the surface (editor brushes), world point in out. */
	public boolean intersect(Object3D o, com.badlogic.gdx.math.collision.Ray ray, Vector3 out) {
		Matrix4 inv = new Matrix4(o.transform).inv();
		Vector3 org = new Vector3(ray.origin).mul(inv);
		Vector3 end = new Vector3(ray.direction).scl(5000).add(ray.origin).mul(inv);
		Vector3 dir = new Vector3(end).sub(org);
		float len = dir.len();
		if (len < 1e-6f) return false;
		dir.scl(1f / len);
		// march in steps of a cell, then refine
		float step = 0.5f / res;
		float t = 0;
		float prev = Float.NaN;
		float maxT = Math.min(len, 4f);
		Vector3 p = new Vector3();
		for (; t <= maxT; t += step) {
			p.set(dir).scl(t).add(org);
			if (p.x < -0.6f || p.x > 0.6f || p.z < -0.6f || p.z > 0.6f) {
				prev = Float.NaN;
				continue;
			}
			float d = p.y - data.height(p.x + 0.5f, p.z + 0.5f) * maxHeight;
			if (d <= 0 && !Float.isNaN(prev)) {
				float a = t - step, b = t;
				for (int k = 0; k < 12; k++) {
					float m = (a + b) / 2;
					p.set(dir).scl(m).add(org);
					float dm = p.y - data.height(p.x + 0.5f, p.z + 0.5f) * maxHeight;
					if (dm > 0) a = m;
					else b = m;
				}
				p.set(dir).scl((a + b) / 2).add(org);
				if (p.x < -0.5f || p.x > 0.5f || p.z < -0.5f || p.z > 0.5f) return false;
				out.set(p).mul(o.transform);
				return true;
			}
			prev = d;
		}
		return false;
	}

	/** Heights for Bullet (meters, before Scale Y), rebuilt after edits. */
	FloatBuffer field() {
		if (fieldBuffer == null) {
			fieldBuffer = BufferUtils.newFloatBuffer(h.length);
			for (float v : h) fieldBuffer.put(v * maxHeight);
			fieldBuffer.flip();
		}
		return fieldBuffer;
	}

	public void dispose() {
		if (model != null) {
			try {
				model.dispose();
			} catch (Throwable ignored) {
			}
			model = null;
		}
		if (splatTexture != null) {
			splatTexture.dispose();
			splatTexture = null;
		}
		if (splatPixmap != null) {
			splatPixmap.dispose();
			splatPixmap = null;
		}
		if (foliage != null) {
			foliage.dispose();
			foliage = null;
		}
	}

	// ------------------------------------------------------------------ noise (legacy terrains, generator)

	static float hash(int x, int y, int s) {
		int n = x * 374761393 + y * 668265263 + s * 1442695041;
		n = (n ^ (n >> 13)) * 1274126177;
		return ((n ^ (n >> 16)) & 0x7fffffff) / (float) 0x7fffffff;
	}

	static float noise(float x, float y, int s) {
		int xi = MathUtils.floor(x), yi = MathUtils.floor(y);
		float fx = x - xi, fy = y - yi;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		float a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
		return MathUtils.lerp(MathUtils.lerp(a, b, fx), MathUtils.lerp(c, d, fx), fy);
	}

	static float fbm(float x, float y, int s) {
		float v = 0, a = 0.5f, f = 1;
		for (int o = 0; o < 4; o++) {
			v += a * noise(x * f, y * f, s + o * 17);
			a *= 0.5f;
			f *= 2;
		}
		return v;
	}

	// ------------------------------------------------------------------ shader

	/** Layers of a painted terrain (textures, tints, repeats over the whole terrain). */
	public static class TerrainAttribute extends Attribute {
		public static final long Type = register("s2dTerrain");
		public final Texture[] layers = new Texture[4];
		public final Color[] colors = {new Color(Color.WHITE), new Color(Color.WHITE), new Color(Color.WHITE), new Color(Color.WHITE)};
		public final float[] tiling = {24, 20, 12, 20};

		public TerrainAttribute() {
			super(Type);
		}

		@Override
		public Attribute copy() {
			TerrainAttribute t = new TerrainAttribute();
			for (int i = 0; i < 4; i++) {
				t.layers[i] = layers[i];
				t.colors[i].set(colors[i]);
				t.tiling[i] = tiling[i];
			}
			return t;
		}

		@Override
		public int compareTo(Attribute o) {
			return type == o.type ? 0 : (type < o.type ? -1 : 1);
		}
	}

	private static String terrainFragment(String f) {
		String decl = "\n#if defined(GL_ES) && defined(GL_FRAGMENT_PRECISION_HIGH)\n#define TP highp\n#else\n#define TP\n#endif\n"
				+ "uniform sampler2D u_layer0;\nuniform sampler2D u_layer1;\nuniform sampler2D u_layer2;\nuniform sampler2D u_layer3;\n"
				+ "uniform TP vec4 u_tiling;\nuniform vec4 u_lc0;\nuniform vec4 u_lc1;\nuniform vec4 u_lc2;\nuniform vec4 u_lc3;\n"
				+ "varying TP vec2 v_terrainUV;\n";
		f = f.replace("void main() {", decl + "void main() {");
		String anchor = "\t#if defined(emissiveTextureFlag) && defined(emissiveColorFlag)";
		int at = f.indexOf(anchor);
		if (at < 0) return null;
		String blk = "\t{\n"
				+ "\t\tvec4 sw = texture2D(u_diffuseTexture, v_terrainUV);\n"
				+ "\t\tfloat tot = sw.r + sw.g + sw.b + sw.a;\n"
				+ "\t\tsw = tot > 0.004 ? sw / tot : vec4(1.0, 0.0, 0.0, 0.0);\n"
				+ "\t\tTP vec2 uv = v_terrainUV;\n"
				+ "\t\tvec3 c = vec3(0.0);\n"
				+ "\t\tif (sw.r > 0.004) c += texture2D(u_layer0, uv * u_tiling.x).rgb * u_lc0.rgb * sw.r;\n"
				+ "\t\tif (sw.g > 0.004) c += texture2D(u_layer1, uv * u_tiling.y).rgb * u_lc1.rgb * sw.g;\n"
				+ "\t\tif (sw.b > 0.004) c += texture2D(u_layer2, uv * u_tiling.z).rgb * u_lc2.rgb * sw.b;\n"
				+ "\t\tif (sw.a > 0.004) c += texture2D(u_layer3, uv * u_tiling.w).rgb * u_lc3.rgb * sw.a;\n"
				// large, calm brightness changes hide the repetition of the textures
				+ "\t\tc *= 0.92 + 0.16 * sin(uv.x * 9.0 + sin(uv.y * 7.0) * 1.7) * sin(uv.y * 8.0 + 0.6);\n"
				+ "\t\t#ifdef diffuseColorFlag\n\t\tdiffuse = vec4(c, 1.0) * u_diffuseColor;\n\t\t#else\n\t\tdiffuse = vec4(c, 1.0);\n\t\t#endif\n"
				+ "\t}\n";
		return f.substring(0, at) + blk + f.substring(at);
	}

	private static String terrainVertex(String v) {
		v = v.replace("void main() {", "varying vec2 v_terrainUV;\nvoid main() {");
		String anchor = "gl_Position = u_projViewTrans * pos;";
		if (!v.contains(anchor)) return null;
		return v.replace(anchor, anchor + "\n\t#ifdef textureFlag\n\tv_terrainUV = a_texCoord0;\n\t#endif");
	}

	/** Shader of painted terrains (the render mode's lighting, fog, shadows + the splat blend). */
	public static class TerrainShader extends Shaders3D.Lit {
		public TerrainShader(Renderable r, DefaultShader.Config c) {
			super(r, c);
			for (int i = 0; i < 4; i++) {
				final int k = i;
				register(new Uniform("u_layer" + i), new LocalSetter() {
					@Override
					public void set(BaseShader shader, int inputID, Renderable renderable, com.badlogic.gdx.graphics.g3d.Attributes combined) {
						TerrainAttribute ta = (TerrainAttribute) combined.get(TerrainAttribute.Type);
						Texture t = ta == null || ta.layers[k] == null ? TerrainTextures.builtin("grass") : ta.layers[k];
						shader.set(inputID, shader.context.textureBinder.bind(t));
					}
				});
				register(new Uniform("u_lc" + i), new LocalSetter() {
					@Override
					public void set(BaseShader shader, int inputID, Renderable renderable, com.badlogic.gdx.graphics.g3d.Attributes combined) {
						TerrainAttribute ta = (TerrainAttribute) combined.get(TerrainAttribute.Type);
						shader.set(inputID, ta == null ? Color.WHITE : ta.colors[k]);
					}
				});
			}
			register(new Uniform("u_tiling"), new LocalSetter() {
				@Override
				public void set(BaseShader shader, int inputID, Renderable renderable, com.badlogic.gdx.graphics.g3d.Attributes combined) {
					TerrainAttribute ta = (TerrainAttribute) combined.get(TerrainAttribute.Type);
					if (ta == null) shader.set(inputID, 16, 16, 16, 16);
					else shader.set(inputID, ta.tiling[0], ta.tiling[1], ta.tiling[2], ta.tiling[3]);
				}
			});
		}

		@Override
		public boolean canRender(Renderable r) {
			return r.material != null && r.material.has(TerrainAttribute.Type) && super.canRender(r);
		}
	}

	/** Shader for a renderable of a painted terrain, null when the sources can't be patched (then the normal shader draws it). */
	static Shader shader(Renderable r, String mode) {
		try {
			boolean pixel = Quality3D.pixelLighting;
			DefaultShader.Config c = Shaders3D.config(Shaders3D.PBR.equals(mode) || Shaders3D.UNLIT.equals(mode) ? Shaders3D.SIMPLE : mode, pixel);
			String f = terrainFragment(c.fragmentShader), v = terrainVertex(c.vertexShader);
			if (f == null || v == null) return null;
			c.fragmentShader = f;
			c.vertexShader = v;
			TerrainShader s = new TerrainShader(r, c);
			return s;
		} catch (Throwable t) {
			Gdx.app.error("Star2D-3D", "terrain shader: " + t);
			return null;
		}
	}

}
