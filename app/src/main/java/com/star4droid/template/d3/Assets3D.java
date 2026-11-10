package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Loads and caches the files of a 3D scene: glTF / GLB / OBJ models (with their animations) and textures.
 * Paths are project relative ("/car.glb" = models/car.glb, "/wood.png" = images/wood.png); absolute paths work too.
 * One cache for the whole game: two elements using the same model share the meshes and the textures.
 */
public final class Assets3D {
	private static final ObjectMap<String, SceneAsset> MODELS = new ObjectMap<String, SceneAsset>();
	private static final ObjectMap<String, Texture> TEXTURES = new ObjectMap<String, Texture>();
	private static final ObjectMap<String, String> ERRORS = new ObjectMap<String, String>();
	/** Folder of the project (set by the scene / the editor). */
	public static String projectPath = "";

	private Assets3D() {
	}

	public static FileHandle file(String path, String folder) {
		if (path == null || path.length() == 0) return null;
		path = path.replace(com.star4droid.template.Utils.Utils.seperator, "/");
		FileHandle f;
		if (path.startsWith("/") && Gdx.files.absolute(path).exists() && path.length() > 1 && path.indexOf('/', 1) > 0 && new java.io.File(path).isAbsolute() && path.startsWith(projectPath.length() > 0 ? projectPath : "/\u0000"))
			return Gdx.files.absolute(path);
		String rel = path.startsWith("/") ? path.substring(1) : path;
		if (projectPath != null && projectPath.length() > 0) {
			f = Gdx.files.absolute(projectPath + "/" + folder + "/" + rel);
			if (f.exists()) return f;
			f = Gdx.files.absolute(projectPath + "/" + rel);
			if (f.exists()) return f;
		}
		f = Gdx.files.absolute(path);
		if (f.exists()) return f;
		f = Gdx.files.internal(folder + "/" + rel);
		return f.exists() ? f : null;
	}

	/** Last modified time + size of the file each cached model was read from (a changed file is read again). */
	private static final ObjectMap<String, String> STAMPS = new ObjectMap<String, String>();
	/** Models replaced by a newer copy of their file: elements may still draw them, freed only with {@link #disposeAll}. */
	private static final Array<SceneAsset> RETIRED = new Array<SceneAsset>();
	private static final ObjectMap<String, Integer> VERSIONS = new ObjectMap<String, Integer>();

	private static String stamp(FileHandle f) {
		try {
			return f.lastModified() + ":" + f.length();
		} catch (Throwable t) {
			return "";
		}
	}

	/** Changes each time the file of a model is read again (the editor rebuilds the elements that show it). */
	public static int version(String path) {
		if (path == null) return 0;
		Integer v = VERSIONS.get(cacheKey(path));
		return v == null ? 0 : v;
	}

	/** The model file (null + an error message in {@link #error} when it cannot be read). */
	public static SceneAsset model(String path) {
		if (path == null || path.length() == 0) return null;
		// cached per project: two projects (or an example and a game) may have different files with the same name
		final String k = cacheKey(path);
		SceneAsset a = MODELS.get(k);
		if (a != null) return a;
		if (ERRORS.containsKey(k)) return null;
		FileHandle f = file(path, "models");
		if (f == null) {
			ERRORS.put(k, "model not found: " + path);
			log(ERRORS.get(k));
			return null;
		}
		try {
			String ext = f.extension().toLowerCase();
			if (ext.equals("glb")) a = new GLBLoader().load(f, true);
			else if (ext.equals("gltf")) a = new GLTFLoader().load(f, true);
			else if (ext.equals("obj")) a = loadObj(f);
			else {
				ERRORS.put(k, "unsupported model format: " + ext + " (use .glb, .gltf or .obj)");
				log(ERRORS.get(k));
				return null;
			}
			if (a == null || a.scene == null || a.scene.model == null) throw new RuntimeException("empty model");
			try {
				addMissingNormals(a);
			} catch (Throwable t) {
				log("normals of " + path + ": " + t);
			}
			Materials3D.fixImported(a.scene.model);
			MODELS.put(k, a);
			STAMPS.put(k, stamp(f));
			return a;
		} catch (Throwable t) {
			ERRORS.put(k, "model error " + path + ": " + t);
			log(ERRORS.get(k));
			if (a != null) {
				try {
					a.dispose();
				} catch (Throwable ignored) {
				}
			}
			return null;
		}
	}

	private static void log(String msg) {
		try {
			if (Gdx.app != null) Gdx.app.error("Star2D-3D", msg);
		} catch (Throwable ignored) {
		}
	}

	/** Wavefront .obj (+ its .mtl and the pictures next to it) wrapped like a glTF asset. */
	private static SceneAsset loadObj(final FileHandle src) {
		// libGDX's ObjLoader keeps one material per group ("g"): files that only switch "usemtl" (Blender's default export)
		// would get the last material everywhere. Start a group at each usemtl that has none.
		FileHandle f = src;
		try {
			String[] lines = src.readString().split("\n");
			StringBuilder sb = new StringBuilder();
			boolean groupSinceMtl = false;
			int n = 0;
			for (String line : lines) {
				String t = line.trim();
				if (t.startsWith("g ") || t.startsWith("o ")) {
					groupSinceMtl = true;
					sb.append("g ").append(t.substring(2).trim()).append('_').append(n++).append('\n');
					continue;
				}
				if (t.startsWith("usemtl ")) {
					if (!groupSinceMtl) sb.append("g mat_").append(n++).append('\n');
					groupSinceMtl = false;
				} else if (t.startsWith("f ")) groupSinceMtl = false;
				sb.append(line).append('\n');
			}
			final byte[] data = sb.toString().getBytes("UTF-8");
			f = new FileHandle(src.file()) {
				@Override
				public java.io.InputStream read() {
					return new java.io.ByteArrayInputStream(data);
				}

				@Override
				public FileHandle parent() {
					return src.parent();
				}
			};
		} catch (Throwable t) {
			f = src;
		}
		Model m = new com.badlogic.gdx.graphics.g3d.loader.ObjLoader(new com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver() {
			@Override
			public FileHandle resolve(String fileName) {
				FileHandle h = Gdx.files.absolute(fileName);
				return h.exists() ? h : Gdx.files.internal(fileName);
			}
		}).loadModel(f, true);
		SceneAsset a = new SceneAsset();
		a.scene = new net.mgsx.gltf.scene3d.scene.SceneModel();
		a.scene.model = m;
		a.scene.name = src.nameWithoutExtension();
		a.scenes = new Array<net.mgsx.gltf.scene3d.scene.SceneModel>();
		a.scenes.add(a.scene);
		a.animations = new Array<com.badlogic.gdx.graphics.g3d.model.Animation>();
		a.textures = new Array<Texture>();
		a.pixmaps = new Array<Pixmap>();
		a.meshes = new Array<com.badlogic.gdx.graphics.Mesh>();
		return a;
	}

	/**
	 * A model exported without normals is drawn black by the lit shaders (only the ambient light reaches it): add smooth
	 * normals made from its triangles.
	 */
	static void addMissingNormals(SceneAsset a) {
		Model model = a.scene.model;
		ObjectMap<com.badlogic.gdx.graphics.Mesh, com.badlogic.gdx.graphics.Mesh> swap = new ObjectMap<com.badlogic.gdx.graphics.Mesh, com.badlogic.gdx.graphics.Mesh>();
		for (com.badlogic.gdx.graphics.Mesh mesh : model.meshes) {
			com.badlogic.gdx.graphics.VertexAttributes va = mesh.getVertexAttributes();
			if (va.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal) != null) continue;
			com.badlogic.gdx.graphics.VertexAttribute pos = va.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position);
			if (pos == null || pos.numComponents < 3) continue;
			int stride = va.vertexSize / 4, nv = mesh.getNumVertices(), po = pos.offset / 4;
			float[] src = new float[nv * stride];
			mesh.getVertices(src);
			int ni = mesh.getNumIndices();
			short[] idx = new short[ni];
			if (ni > 0) mesh.getIndices(idx);
			float[] nrm = new float[nv * 3];
			int tris = ni > 0 ? ni / 3 : nv / 3;
			for (int t = 0; t < tris; t++) {
				int i0 = ni > 0 ? (idx[t * 3] & 0xFFFF) : t * 3, i1 = ni > 0 ? (idx[t * 3 + 1] & 0xFFFF) : t * 3 + 1, i2 = ni > 0 ? (idx[t * 3 + 2] & 0xFFFF) : t * 3 + 2;
				if (i0 >= nv || i1 >= nv || i2 >= nv) continue;
				float ax = src[i0 * stride + po], ay = src[i0 * stride + po + 1], az = src[i0 * stride + po + 2];
				float ux = src[i1 * stride + po] - ax, uy = src[i1 * stride + po + 1] - ay, uz = src[i1 * stride + po + 2] - az;
				float vx = src[i2 * stride + po] - ax, vy = src[i2 * stride + po + 1] - ay, vz = src[i2 * stride + po + 2] - az;
				float cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
				int[] ids = {i0, i1, i2};
				for (int id : ids) {
					nrm[id * 3] += cx;
					nrm[id * 3 + 1] += cy;
					nrm[id * 3 + 2] += cz;
				}
			}
			com.badlogic.gdx.graphics.VertexAttribute[] attrs = new com.badlogic.gdx.graphics.VertexAttribute[va.size() + 1];
			for (int i = 0; i < va.size(); i++) attrs[i] = va.get(i).copy();
			attrs[va.size()] = com.badlogic.gdx.graphics.VertexAttribute.Normal();
			com.badlogic.gdx.graphics.Mesh out = new com.badlogic.gdx.graphics.Mesh(true, nv, ni, attrs);
			int ns = stride + 3, nOff = out.getVertexAttributes().findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal).offset / 4;
			float[] dst = new float[nv * ns];
			for (int v = 0; v < nv; v++) {
				// the old attributes keep their offsets, the normal comes last
				System.arraycopy(src, v * stride, dst, v * ns, stride);
				float x = nrm[v * 3], y = nrm[v * 3 + 1], z = nrm[v * 3 + 2];
				float len = (float) Math.sqrt(x * x + y * y + z * z);
				if (len < 1e-12f) {
					x = 0;
					y = 1;
					z = 0;
					len = 1;
				}
				dst[v * ns + nOff] = x / len;
				dst[v * ns + nOff + 1] = y / len;
				dst[v * ns + nOff + 2] = z / len;
			}
			out.setVertices(dst);
			if (ni > 0) out.setIndices(idx);
			swap.put(mesh, out);
		}
		if (swap.size == 0) return;
		for (com.badlogic.gdx.graphics.g3d.model.MeshPart mp : model.meshParts) {
			com.badlogic.gdx.graphics.Mesh n = swap.get(mp.mesh);
			if (n != null) mp.mesh = n;
		}
		swapNodes(model.nodes, swap);
		for (ObjectMap.Entry<com.badlogic.gdx.graphics.Mesh, com.badlogic.gdx.graphics.Mesh> e : swap) {
			int i = model.meshes.indexOf(e.key, true);
			if (i >= 0) model.meshes.set(i, e.value);
			if (a.meshes != null) {
				int j = a.meshes.indexOf(e.key, true);
				if (j >= 0) a.meshes.set(j, e.value);
			}
			e.key.dispose();
		}
	}

	private static void swapNodes(Iterable<com.badlogic.gdx.graphics.g3d.model.Node> nodes, ObjectMap<com.badlogic.gdx.graphics.Mesh, com.badlogic.gdx.graphics.Mesh> swap) {
		for (com.badlogic.gdx.graphics.g3d.model.Node n : nodes) {
			for (com.badlogic.gdx.graphics.g3d.model.NodePart p : n.parts) {
				com.badlogic.gdx.graphics.Mesh m = p.meshPart == null ? null : swap.get(p.meshPart.mesh);
				if (m != null) p.meshPart.mesh = m;
			}
			swapNodes(n.getChildren(), swap);
		}
	}

	public static Model modelOf(String path) {
		SceneAsset a = model(path);
		return a == null ? null : a.scene.model;
	}

	/** Names of the animations of a model. */
	public static Array<String> animations(String path) {
		Array<String> out = new Array<String>();
		Model m = modelOf(path);
		if (m == null) return out;
		for (int i = 0; i < m.animations.size; i++) out.add(m.animations.get(i).id);
		return out;
	}

	private static String cacheKey(String path) {
		return (projectPath == null ? "" : projectPath) + "|" + path;
	}

	public static String error(String path) {
		return ERRORS.get(cacheKey(path));
	}

	public static Texture texture(String path, boolean repeat) {
		if (path == null || path.length() == 0) return null;
		String key = cacheKey(path) + (repeat ? "|r" : "");
		Texture t = TEXTURES.get(key);
		if (t != null) return t;
		FileHandle f = file(path, "images");
		Pixmap merged = f == null ? mergedPixmap(path) : null;
		if (f == null && merged == null) return null;
		try {
			t = merged != null ? new Texture(merged, true) : new Texture(f, true);
			if (merged != null) merged.dispose();
			t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
			if (repeat) t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
			TEXTURES.put(key, t);
			return t;
		} catch (Throwable e) {
			return null;
		}
	}

	/** A picture merged into a tileset page at export ("Merge images into tilesets"), or null. */
	private static Pixmap mergedPixmap(String path) {
		if (com.star4droid.template.Utils.AtlasIndex.isEmpty() || path == null) return null;
		String rel = path.replace('\\', '/');
		while (rel.startsWith("/")) rel = rel.substring(1);
		if (rel.startsWith("images/")) rel = rel.substring(7);
		return com.star4droid.template.Utils.AtlasIndex.pixmap("images/" + rel);
	}

	public static Pixmap pixmap(String path) {
		FileHandle f = file(path, "images");
		if (f == null) return mergedPixmap(path);
		try {
			return new Pixmap(f);
		} catch (Throwable e) {
			return null;
		}
	}

	private static Object glOwner;

	/**
	 * The caches here (and the shared shapes / water grids / shader programs) hold GL objects of one GL context. A new
	 * activity (editor reopened, the game started again in the same process) has a new context where those handles mean
	 * nothing: forget them (no dispose, their context is gone) and build again.
	 */
	public static void checkContext() {
		Object app = Gdx.app;
		if (app == null || app == glOwner) return;
		boolean first = glOwner == null;
		glOwner = app;
		if (first) return;
		MODELS.clear();
		RETIRED.clear();
		STAMPS.clear();
		TEXTURES.clear();
		ERRORS.clear();
		Shapes3D.forgetGL();
		Water3D.forgetGL();
		TerrainTextures.invalidate();
		Foliage3D.forgetGL();
		FoliageMeshes.class.getName();
		Outline3D.OutlineShader.program = null;
		B3.forgetSounds();
	}

	/** Frees everything (game exit / editor project change). */
	public static void disposeAll() {
		for (SceneAsset a : MODELS.values()) {
			try {
				a.dispose();
			} catch (Throwable ignored) {
			}
		}
		for (Texture t : TEXTURES.values()) {
			try {
				t.dispose();
			} catch (Throwable ignored) {
			}
		}
		for (SceneAsset a : RETIRED) {
			try {
				a.dispose();
			} catch (Throwable ignored) {
			}
		}
		RETIRED.clear();
		STAMPS.clear();
		MODELS.clear();
		TEXTURES.clear();
		ERRORS.clear();
	}

	/**
	 * The file of a model may have changed (imported again): read it again on next use when its date or size differ. The
	 * old copy is NOT freed here, elements still draw it (freeing it made the viewport flicker and crash).
	 */
	public static void reload(String path) {
		String k = cacheKey(path);
		ERRORS.remove(k);
		SceneAsset a = MODELS.get(k);
		if (a == null) return;
		FileHandle f = file(path, "models");
		if (f != null && stamp(f).equals(STAMPS.get(k))) return; // same file: keep the loaded one
		MODELS.remove(k);
		STAMPS.remove(k);
		RETIRED.add(a);
		VERSIONS.put(k, version(path) + 1);
	}
}
