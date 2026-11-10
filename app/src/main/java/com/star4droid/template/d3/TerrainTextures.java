package com.star4droid.template.d3;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Ground textures made in code (no files needed): "builtin:grass", "builtin:dirt", "builtin:rock", "builtin:sand",
 * "builtin:snow", "builtin:road", "builtin:mud", "builtin:moss", "builtin:leaves", "builtin:stone", "builtin:darkgrass".
 * They tile seamlessly (periodic noise) and have mip maps, so a big terrain stays sharp near and calm far.
 */
public final class TerrainTextures {
	public static final String[] NAMES = {"grass", "darkgrass", "dirt", "rock", "sand", "snow", "road", "mud", "moss", "leaves", "stone"};
	/** Default texture of each of the 4 layers. */
	public static final String[] DEFAULT_LAYERS = {"builtin:grass", "builtin:dirt", "builtin:rock", "builtin:sand"};
	private static final ObjectMap<String, Texture> CACHE = new ObjectMap<String, Texture>();
	private static final int SIZE = 256;

	private TerrainTextures() {
	}

	public static boolean isBuiltin(String s) {
		return s != null && s.startsWith("builtin:");
	}

	/** Texture of a layer value: "builtin:x", a project image ("/ground.png") or "" = the default of that layer. */
	public static Texture get(String value, int layer) {
		if (value == null || value.trim().length() == 0) value = DEFAULT_LAYERS[MathUtils.clamp(layer, 0, 3)];
		if (isBuiltin(value)) return builtin(value.substring(8));
		Texture t = Assets3D.texture(value, true);
		return t != null ? t : builtin(DEFAULT_LAYERS[MathUtils.clamp(layer, 0, 3)].substring(8));
	}

	public static Texture builtin(String name) {
		Texture t = CACHE.get(name);
		if (t != null) return t;
		Pixmap p = make(name);
		t = new Texture(p, true);
		p.dispose();
		t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
		t.setFilter(Texture.TextureFilter.MipMapLinearNearest, Texture.TextureFilter.Linear);
		CACHE.put(name, t);
		return t;
	}

	/** GL context lost (app resumed): textures are made again on the next use. */
	public static void invalidate() {
		CACHE.clear();
	}

	/** Average color of a built-in texture (the editor's layer buttons, far LOD). */
	public static Color average(String value, int layer) {
		if (value == null || value.length() == 0) value = DEFAULT_LAYERS[MathUtils.clamp(layer, 0, 3)];
		String n = isBuiltin(value) ? value.substring(8) : "grass";
		float[][] c = palette(n);
		return new Color((c[0][0] + c[1][0]) / 2, (c[0][1] + c[1][1]) / 2, (c[0][2] + c[1][2]) / 2, 1);
	}

	// ------------------------------------------------------------------ generation

	/** Dark / light color and look (detail frequency, speckles, stripes). */
	static float[][] palette(String n) {
		if ("darkgrass".equals(n)) return new float[][]{{0.16f, 0.36f, 0.12f}, {0.30f, 0.52f, 0.18f}, {5, 0.25f, 0}};
		if ("dirt".equals(n)) return new float[][]{{0.40f, 0.29f, 0.18f}, {0.60f, 0.46f, 0.30f}, {6, 0.35f, 0}};
		if ("rock".equals(n)) return new float[][]{{0.38f, 0.37f, 0.36f}, {0.62f, 0.61f, 0.58f}, {3, 0.15f, 1}};
		if ("sand".equals(n)) return new float[][]{{0.80f, 0.70f, 0.48f}, {0.94f, 0.86f, 0.64f}, {8, 0.3f, 0}};
		if ("snow".equals(n)) return new float[][]{{0.82f, 0.86f, 0.92f}, {0.98f, 0.99f, 1.0f}, {4, 0.1f, 0}};
		if ("road".equals(n)) return new float[][]{{0.50f, 0.44f, 0.36f}, {0.72f, 0.65f, 0.54f}, {5, 0.5f, 2}};
		if ("mud".equals(n)) return new float[][]{{0.25f, 0.19f, 0.13f}, {0.40f, 0.31f, 0.21f}, {4, 0.2f, 0}};
		if ("moss".equals(n)) return new float[][]{{0.25f, 0.36f, 0.15f}, {0.45f, 0.55f, 0.26f}, {6, 0.3f, 0}};
		if ("leaves".equals(n)) return new float[][]{{0.55f, 0.30f, 0.10f}, {0.86f, 0.60f, 0.22f}, {7, 0.6f, 0}};
		if ("stone".equals(n)) return new float[][]{{0.42f, 0.42f, 0.44f}, {0.66f, 0.66f, 0.68f}, {4, 0.2f, 3}};
		return new float[][]{{0.27f, 0.52f, 0.17f}, {0.47f, 0.72f, 0.27f}, {6, 0.35f, 0}}; // grass
	}

	static Pixmap make(String n) {
		float[][] pal = palette(n);
		float[] a = pal[0], b = pal[1];
		int freq = (int) pal[2][0];
		float speck = pal[2][1];
		int style = (int) pal[2][2];
		int seed = n.hashCode();
		Pixmap p = new Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888);
		for (int y = 0; y < SIZE; y++)
			for (int x = 0; x < SIZE; x++) {
				float u = (float) x / SIZE, v = (float) y / SIZE;
				float t = fbm(u, v, 4, seed, 4) * 0.6f + fbm(u, v, freq * 4, seed + 7, 3) * 0.4f;
				if (style == 1) { // rock: cracks
					float r = Math.abs(fbm(u, v, 6, seed + 3, 3) - 0.5f) * 2f;
					t = t * 0.75f + 0.25f * MathUtils.clamp(r * 3f, 0, 1);
				} else if (style == 2) { // road: packed earth + small pebbles
					float pb = cells(u, v, 24, seed);
					t = t * 0.7f + (pb < 0.18f ? 0.35f : 0f);
				} else if (style == 3) { // stone tiles
					float c = cells(u, v, 8, seed);
					t = t * 0.65f + MathUtils.clamp(c * 2.5f, 0, 1) * 0.35f;
				}
				float s = hash(x, y, seed);
				if (s > 1f - speck * 0.08f) t += 0.18f;
				else if (s < speck * 0.08f) t -= 0.18f;
				t = MathUtils.clamp(t, 0, 1);
				p.drawPixel(x, y, Color.rgba8888(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, 1f));
			}
		return p;
	}

	static float hash(int x, int y, int s) {
		int h = x * 374761393 + y * 668265263 + s * 1442695041;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0x7fffffff) / (float) 0x7fffffff;
	}

	/** Periodic value noise: period cells across the texture, wraps at u, v = 1. */
	static float noise(float u, float v, int period, int s) {
		float x = u * period, y = v * period;
		int xi = MathUtils.floor(x), yi = MathUtils.floor(y);
		float fx = x - xi, fy = y - yi;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		int x0 = ((xi % period) + period) % period, y0 = ((yi % period) + period) % period;
		int x1 = (x0 + 1) % period, y1 = (y0 + 1) % period;
		float a = hash(x0, y0, s), b = hash(x1, y0, s), c = hash(x0, y1, s), d = hash(x1, y1, s);
		return MathUtils.lerp(MathUtils.lerp(a, b, fx), MathUtils.lerp(c, d, fx), fy);
	}

	static float fbm(float u, float v, int period, int s, int oct) {
		float sum = 0, amp = 0.5f, norm = 0;
		for (int o = 0; o < oct; o++) {
			sum += amp * noise(u, v, period << o, s + o * 31);
			norm += amp;
			amp *= 0.5f;
		}
		return sum / norm;
	}

	/** Distance to the nearest of a periodic grid of jittered points (0..~0.7, in cell units). */
	static float cells(float u, float v, int period, int s) {
		float x = u * period, y = v * period;
		int xi = MathUtils.floor(x), yi = MathUtils.floor(y);
		float best = 9;
		for (int j = -1; j <= 1; j++)
			for (int i = -1; i <= 1; i++) {
				int cx = xi + i, cy = yi + j;
				int wx = ((cx % period) + period) % period, wy = ((cy % period) + period) % period;
				float px = cx + hash(wx, wy, s), py = cy + hash(wx, wy, s + 1);
				float d = (px - x) * (px - x) + (py - y) * (py - y);
				if (d < best) best = d;
			}
		return (float) Math.sqrt(best);
	}
}
