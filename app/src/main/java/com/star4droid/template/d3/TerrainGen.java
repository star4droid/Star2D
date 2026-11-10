package com.star4droid.template.d3;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Random terrain generator (editor "Generate" and the generateTerrain block): shapes the ground (hills, mountains,
 * island, valley, plains, canyon), paints it by slope / height / biome, can carve a winding road and keeps a flat
 * start area, then scatters trees, bushes, rocks and flowers where they make sense (not on the road, the water, steep
 * slopes or the start area).
 */
public final class TerrainGen {
	public static final String[] SHAPES = {"HILLS", "MOUNTAINS", "ISLAND", "VALLEY", "PLAINS", "CANYON"};
	public static final String[] BIOMES = {"MEADOW", "FOREST", "AUTUMN", "DESERT", "SNOW", "TROPICAL"};

	/** What to make. Sizes in meters. */
	public static class Settings {
		public float width = 120, length = 120, height = 14;
		public int resolution = 128;
		public int seed = 1;
		public String shape = "HILLS", biome = "MEADOW";
		/** 0 smooth .. 1 rough */
		public float roughness = 0.5f;
		/** Water: ground below this part of the height (0..1) is shore / lake bed (0 = no water). */
		public float waterLevel = 0;
		public boolean road = true;
		public float roadWidth = 5;
		/** Flat start area at the center (radius m, 0 = none). */
		public float flatCenter = 8;
		public int trees = 120, bushes = 80, rocks = 50, flowers = 150;
		/** Grass tufts per m^2 (0 = none). */
		public float grass = 2.2f;
	}

	/** The data + the properties the element should get (layers, grass...). */
	public static class Result {
		public TerrainData data;
		public final ObjectMap<String, String> props = new ObjectMap<String, String>();
		/** World y of the water surface relative to the terrain (m, NaN = no water). */
		public float waterY = Float.NaN;
		/** Road center line (u, v), may be empty. */
		public final Array<Vector2> road = new Array<Vector2>();
	}

	private TerrainGen() {
	}

	public static Result generate(Settings s) {
		Result r = new Result();
		int res = MathUtils.clamp(s.resolution, 16, TerrainData.MAX_RES);
		int splat = MathUtils.clamp(MathUtils.nextPowerOfTwo((int) (Math.max(s.width, s.length) * 2.2f)), 128, 1024);
		TerrainData d = new TerrainData(res, splat);
		r.data = d;
		int n = d.n();
		int seed = s.seed;
		float rough = MathUtils.clamp(s.roughness, 0, 1);
		float scaleX = s.width / 60f, scaleZ = s.length / 60f; // noise features of ~60 m whatever the size
		// ---- shape
		for (int j = 0; j < n; j++)
			for (int i = 0; i < n; i++) {
				float u = (float) i / res, v = (float) j / res;
				float x = u * scaleX * 2, z = v * scaleZ * 2;
				// domain warp: less grid-like hills
				float wx = x + 0.6f * fbm(x + 3.1f, z + 1.7f, seed + 11, 3, 0.5f), wz = z + 0.6f * fbm(x - 2.3f, z + 5.2f, seed + 13, 3, 0.5f);
				float base = fbm(wx, wz, seed, 3 + (int) (rough * 3), 0.42f + rough * 0.18f);
				float hgt;
				String sh = s.shape == null ? "HILLS" : s.shape;
				float cx = u - 0.5f, cz = v - 0.5f, rad = (float) Math.sqrt(cx * cx + cz * cz) * 2f;
				if ("MOUNTAINS".equals(sh)) {
					float ridge = 1f - Math.abs(fbm(wx * 0.8f, wz * 0.8f, seed + 5, 4, 0.5f) * 2f - 1f);
					hgt = (float) Math.pow(ridge, 2.2f) * 0.8f + base * 0.35f;
				} else if ("ISLAND".equals(sh)) {
					float fall = MathUtils.clamp(1.15f - rad * 1.1f, 0, 1);
					fall = fall * fall * (3 - 2 * fall);
					hgt = (base * 0.7f + 0.3f) * fall;
				} else if ("VALLEY".equals(sh)) {
					float bowl = MathUtils.clamp(rad * 1.25f, 0, 1);
					hgt = bowl * bowl * 0.75f + base * 0.35f;
				} else if ("PLAINS".equals(sh)) {
					hgt = base * 0.35f + 0.1f;
				} else if ("CANYON".equals(sh)) {
					float river = Math.abs(fbm(x * 0.5f, z * 0.15f, seed + 21, 2, 0.5f) - 0.5f) * 4f;
					hgt = MathUtils.clamp(river, 0, 1);
					hgt = (float) Math.round(hgt * 3) / 3f * 0.7f + base * 0.3f;
				} else hgt = base;
				d.h[j * n + i] = hgt;
			}
		normalize(d);
		if ("CANYON".equals(s.shape)) smooth(d, 1);
		// ---- flat start area
		float wx2 = s.width, wz2 = s.length;
		if (s.flatCenter > 0) flatten(d, 0.5f, 0.5f, s.flatCenter / wx2, s.flatCenter / wz2, "ISLAND".equals(s.shape) ? -1 : 0.5f);
		// ---- road
		if (s.road) carveRoad(d, s, r);
		// ---- water
		float water = MathUtils.clamp(s.waterLevel, 0, 0.9f);
		if ("ISLAND".equals(s.shape) && water <= 0) water = 0.12f;
		if (water > 0) r.waterY = water * s.height;
		// ---- paint
		biome(s.biome, r.props, s);
		paint(d, s, water, r);
		// ---- objects
		scatter(d, s, water, r);
		r.props.put("Terrain Height", fmt(s.height));
		r.props.put("Scale X", fmt(s.width));
		r.props.put("Scale Y", "1");
		r.props.put("Scale Z", fmt(s.length));
		r.props.put("Grass Density", fmt(s.grass));
		d.revision++;
		return r;
	}

	static String fmt(float f) {
		if (f == (int) f) return String.valueOf((int) f);
		return String.valueOf(Math.round(f * 1000) / 1000f);
	}

	// ------------------------------------------------------------------ biomes

	/** Kinds of objects of a biome: trees, bushes, rocks, flowers (each a list). */
	static String[][] kinds(String biome) {
		if ("FOREST".equals(biome)) return new String[][]{{"tree_pine", "tree_pine", "tree_round", "tree_birch"}, {"bush", "fern", "fern"}, {"rock", "boulder", "stump", "log"}, {"mushroom", "flower_white", "fern"}};
		if ("AUTUMN".equals(biome)) return new String[][]{{"tree_autumn", "tree_autumn", "tree_round", "tree_birch"}, {"bush", "bush_flowers"}, {"rock", "stump", "log"}, {"mushroom", "flower_yellow", "flower_red"}};
		if ("DESERT".equals(biome)) return new String[][]{{"tree_palm", "tree_dead"}, {"reeds"}, {"rock", "boulder", "rock_flat"}, {"flower_yellow"}};
		if ("SNOW".equals(biome)) return new String[][]{{"tree_pine", "tree_pine", "tree_dead"}, {"bush"}, {"rock", "boulder", "rock_flat"}, {"flower_white"}};
		if ("TROPICAL".equals(biome)) return new String[][]{{"tree_palm", "tree_palm", "tree_round"}, {"fern", "bush", "bush_flowers"}, {"rock", "boulder"}, {"flower_red", "flower_purple", "flower_yellow"}};
		return new String[][]{{"tree_round", "tree_round", "tree_pine", "tree_birch"}, {"bush", "bush_flowers", "fern"}, {"rock", "rock_flat", "boulder"}, {"flower_red", "flower_yellow", "flower_white", "flower_purple", "mushroom"}};
	}

	static void biome(String b, ObjectMap<String, String> p, Settings s) {
		String l1 = "builtin:grass", l2 = "builtin:road", l3 = "builtin:rock", l4 = "builtin:sand";
		String c1 = "#FFFFFF", grass = "#78B83C";
		if ("FOREST".equals(b)) {
			l1 = "builtin:darkgrass";
			l4 = "builtin:moss";
			grass = "#5E9E34";
		} else if ("AUTUMN".equals(b)) {
			c1 = "#E2C979";
			l4 = "builtin:leaves";
			grass = "#B9A644";
		} else if ("DESERT".equals(b)) {
			l1 = "builtin:sand";
			l2 = "builtin:dirt";
			l4 = "builtin:sand";
			grass = "#A7A65A";
			s.grass = Math.min(s.grass, 0.15f);
		} else if ("SNOW".equals(b)) {
			l1 = "builtin:snow";
			l2 = "builtin:stone";
			l4 = "builtin:dirt";
			grass = "#9FB38A";
			s.grass = Math.min(s.grass, 0.3f);
		} else if ("TROPICAL".equals(b)) {
			l1 = "builtin:darkgrass";
			grass = "#4FA83A";
		}
		p.put("Layer 1 Texture", l1);
		p.put("Layer 2 Texture", l2);
		p.put("Layer 3 Texture", l3);
		p.put("Layer 4 Texture", l4);
		p.put("Layer 1 Color", c1);
		p.put("Layer 2 Color", "#FFFFFF");
		p.put("Layer 3 Color", "#FFFFFF");
		p.put("Layer 4 Color", "#FFFFFF");
		float big = Math.max(s.width, s.length);
		p.put("Layer 1 Tiling", fmt(Math.round(big / 5f)));
		p.put("Layer 2 Tiling", fmt(Math.round(big / 4f)));
		p.put("Layer 3 Tiling", fmt(Math.round(big / 7f)));
		p.put("Layer 4 Tiling", fmt(Math.round(big / 5f)));
		p.put("Grass Color", grass);
		p.put("Grass Layer", "1");
		p.put("Color", "#FFFFFF");
	}

	// ------------------------------------------------------------------ steps

	static void normalize(TerrainData d) {
		float mn = d.minH(), mx = d.maxH();
		float k = mx - mn < 1e-5f ? 0 : 1f / (mx - mn);
		for (int i = 0; i < d.h.length; i++) d.h[i] = (d.h[i] - mn) * k;
	}

	static void smooth(TerrainData d, int passes) {
		int n = d.n();
		float[] o = new float[d.h.length];
		for (int p = 0; p < passes; p++) {
			for (int j = 0; j < n; j++)
				for (int i = 0; i < n; i++)
					o[j * n + i] = (d.at(i - 1, j) + d.at(i + 1, j) + d.at(i, j - 1) + d.at(i, j + 1) + d.at(i, j) * 4) / 8f;
			System.arraycopy(o, 0, d.h, 0, o.length);
		}
	}

	/** Flattens an ellipse (radius in u, v) to the height there (target < 0) or a target, soft edge. */
	static void flatten(TerrainData d, float cu, float cv, float ru, float rv, float target) {
		int n = d.n();
		float th = target < 0 ? d.height(cu, cv) : avg(d, cu, cv, ru, rv);
		for (int j = 0; j < n; j++)
			for (int i = 0; i < n; i++) {
				float u = (float) i / d.res, v = (float) j / d.res;
				float du = (u - cu) / ru, dv = (v - cv) / rv;
				float r = (float) Math.sqrt(du * du + dv * dv);
				if (r > 1.6f) continue;
				float k = MathUtils.clamp((1.6f - r) / 0.6f, 0, 1);
				k = k * k * (3 - 2 * k);
				d.h[j * n + i] = MathUtils.lerp(d.h[j * n + i], th, k);
			}
	}

	static float avg(TerrainData d, float cu, float cv, float ru, float rv) {
		float s = 0;
		int c = 0;
		for (int k = 0; k < 16; k++) {
			float a = k * MathUtils.PI2 / 16;
			s += d.height(cu + MathUtils.cos(a) * ru, cv + MathUtils.sin(a) * rv);
			c++;
		}
		return s / c;
	}

	static void carveRoad(TerrainData d, Settings s, Result r) {
		// from the south edge through the start area to the north edge, wandering
		int seg = 48;
		float phase = Terrain3D.hash(s.seed, 3, 9) * 10f;
		for (int k = 0; k <= seg; k++) {
			float v = 0.02f + 0.96f * k / seg;
			float wander = (float) (Math.sin(v * 6.0 + phase) * 0.18 + Math.sin(v * 13.0 + phase * 2) * 0.06);
			float pull = 1f - MathUtils.clamp(Math.abs(v - 0.5f) * 3.2f, 0, 1); // passes the start area
			float u = 0.5f + wander * (1f - pull);
			r.road.add(new Vector2(MathUtils.clamp(u, 0.06f, 0.94f), v));
		}
		// smooth the heights along the road (each point = average of its neighbours along the path)
		float[] hs = new float[r.road.size];
		for (int k = 0; k < hs.length; k++) hs[k] = d.height(r.road.get(k).x, r.road.get(k).y);
		for (int p = 0; p < 6; p++)
			for (int k = 1; k < hs.length - 1; k++) hs[k] = (hs[k - 1] + hs[k] * 2 + hs[k + 1]) / 4f;
		int n = d.n();
		float half = s.roadWidth * 0.5f;
		for (int j = 0; j < n; j++)
			for (int i = 0; i < n; i++) {
				float u = (float) i / d.res, v = (float) j / d.res;
				float best = Float.MAX_VALUE, bh = 0;
				int k0 = window(r, v);
				for (int k = Math.max(0, k0 - 4); k < Math.min(r.road.size - 1, k0 + 5); k++) {
					Vector2 a = r.road.get(k), b = r.road.get(k + 1);
					float t = segT(u * s.width, v * s.length, a.x * s.width, a.y * s.length, b.x * s.width, b.y * s.length);
					float px = (a.x + (b.x - a.x) * t) * s.width, pz = (a.y + (b.y - a.y) * t) * s.length;
					float dd = (u * s.width - px) * (u * s.width - px) + (v * s.length - pz) * (v * s.length - pz);
					if (dd < best) {
						best = dd;
						bh = hs[k] + (hs[k + 1] - hs[k]) * t;
					}
				}
				float dist = (float) Math.sqrt(best);
				float k = MathUtils.clamp((half + 4f - dist) / 4f, 0, 1);
				k = k * k * (3 - 2 * k);
				d.h[j * n + i] = MathUtils.lerp(d.h[j * n + i], bh, k);
			}
	}

	static float segT(float px, float pz, float ax, float az, float bx, float bz) {
		float dx = bx - ax, dz = bz - az, l = dx * dx + dz * dz;
		if (l < 1e-6f) return 0;
		return MathUtils.clamp(((px - ax) * dx + (pz - az) * dz) / l, 0, 1);
	}

	/** Road segment near v (the road runs along v, one point per step of v). */
	static int window(Result r, float v) {
		if (r.road.size < 2) return 0;
		float v0 = r.road.first().y, v1 = r.road.peek().y;
		return MathUtils.clamp((int) ((v - v0) / (v1 - v0) * (r.road.size - 1)), 0, r.road.size - 2);
	}

	/** Distance (m) of u, v to the road line. */
	static float roadDist(Result r, Settings s, float u, float v) {
		float best = Float.MAX_VALUE;
		int k0 = window(r, v);
		for (int k = Math.max(0, k0 - 4); k < Math.min(r.road.size - 1, k0 + 5); k++) {
			Vector2 a = r.road.get(k), b = r.road.get(k + 1);
			float t = segT(u * s.width, v * s.length, a.x * s.width, a.y * s.length, b.x * s.width, b.y * s.length);
			float px = (a.x + (b.x - a.x) * t) * s.width, pz = (a.y + (b.y - a.y) * t) * s.length;
			best = Math.min(best, (u * s.width - px) * (u * s.width - px) + (v * s.length - pz) * (v * s.length - pz));
		}
		return (float) Math.sqrt(best);
	}

	static void paint(TerrainData d, Settings s, float water, Result r) {
		int sr = d.splatRes;
		boolean snowTops = "SNOW".equals(s.biome) || "MOUNTAINS".equals(s.shape);
		for (int y = 0; y < sr; y++)
			for (int x = 0; x < sr; x++) {
				float u = (x + 0.5f) / sr, v = (y + 0.5f) / sr;
				float hh = d.height(u, v);
				float sl = d.slope(u, v, s.width, s.length, s.height);
				float w1 = 1, w2 = 0, w3 = 0, w4 = 0;
				// patches of the 4th layer (moss / leaves / sand) and bare earth in the grass
				float patch = fbm(u * s.width / 18f, v * s.length / 18f, s.seed + 31, 3, 0.5f);
				if (patch > 0.62f) w4 = MathUtils.clamp((patch - 0.62f) * 6f, 0, 0.8f);
				float rock = MathUtils.clamp((sl - 0.42f) * 5f, 0, 1);
				w3 = rock;
				if (water > 0) {
					float shore = MathUtils.clamp((water + 0.05f - hh) * 14f, 0, 1);
					w4 = Math.max(w4, shore);
					if ("DESERT".equals(s.biome) || "TROPICAL".equals(s.biome) || "ISLAND".equals(s.shape)) w4 = Math.max(w4, MathUtils.clamp((water + 0.09f - hh) * 10f, 0, 1));
				}
				if (snowTops && "MOUNTAINS".equals(s.shape) && !"SNOW".equals(s.biome)) {
					float snow = MathUtils.clamp((hh - 0.72f) * 6f, 0, 1) * (1 - rock * 0.6f);
					if (snow > 0) {
						w1 *= 1 - snow;
						w4 = Math.max(w4 * (1 - snow), snow); // layer 4 shows as light ground on the tops
					}
				}
				if (r.road.size > 1) {
					float rd = roadDist(r, s, u, v);
					float edge = s.roadWidth * 0.5f;
					float jag = (fbm(u * s.width / 3f, v * s.length / 3f, s.seed + 41, 2, 0.5f) - 0.5f) * 1.2f;
					w2 = MathUtils.clamp((edge + jag - rd) / 1.2f, 0, 1);
				}
				w1 = Math.max(0, w1 - w3 - w4 - w2);
				float sum = w1 + w2 + w3 + w4;
				if (sum < 1e-4f) {
					w1 = 1;
					sum = 1;
				}
				int k = (y * sr + x) * 4;
				d.splat[k] = (byte) (w1 / sum * 255);
				d.splat[k + 1] = (byte) (w2 / sum * 255);
				d.splat[k + 2] = (byte) (w3 / sum * 255);
				d.splat[k + 3] = (byte) (w4 / sum * 255);
			}
	}

	static void scatter(TerrainData d, Settings s, float water, Result r) {
		String[][] kinds = kinds(s.biome);
		int[] counts = {s.trees, s.bushes, s.rocks, s.flowers};
		float[] spacing = {3.2f, 1.4f, 2.0f, 0.5f};
		float[] maxSlope = {0.45f, 0.5f, 0.85f, 0.45f};
		float[][] size = {{0.8f, 1.35f}, {0.7f, 1.3f}, {0.5f, 1.6f}, {0.8f, 1.3f}};
		java.util.Random rnd = new java.util.Random(s.seed * 7919L + 17);
		// occupancy grid (1 m cells) for spacing
		int gw = Math.max(1, (int) s.width), gh = Math.max(1, (int) s.length);
		float[] occ = new float[gw * gh];
		for (int g = 0; g < 4; g++) {
			int want = Math.max(0, counts[g]);
			int tries = want * 12;
			// trees and bushes grow in groups: sample around a few cluster centers half of the time
			Array<Vector2> centers = new Array<Vector2>();
			for (int c = 0; c < Math.max(3, want / 14); c++) centers.add(new Vector2(rnd.nextFloat(), rnd.nextFloat()));
			int placed = 0;
			for (int t = 0; t < tries && placed < want; t++) {
				float u, v;
				if (g < 2 && rnd.nextFloat() < 0.6f && centers.size > 0) {
					Vector2 c = centers.get(rnd.nextInt(centers.size));
					u = c.x + (float) rnd.nextGaussian() * 14f / s.width;
					v = c.y + (float) rnd.nextGaussian() * 14f / s.length;
				} else {
					u = rnd.nextFloat();
					v = rnd.nextFloat();
				}
				if (u < 0.02f || u > 0.98f || v < 0.02f || v > 0.98f) continue;
				float hh = d.height(u, v);
				if (water > 0 && hh < water + 0.03f && !(g == 1 && kinds[g][0].equals("reeds"))) continue;
				if (d.slope(u, v, s.width, s.length, s.height) > maxSlope[g]) continue;
				if (r.road.size > 1 && roadDist(r, s, u, v) < s.roadWidth * 0.5f + (g == 0 ? 2.5f : 0.8f)) continue;
				float cu = (u - 0.5f) * s.width, cv = (v - 0.5f) * s.length;
				if (s.flatCenter > 0 && cu * cu + cv * cv < (s.flatCenter + (g == 0 ? 3 : 0)) * (s.flatCenter + (g == 0 ? 3 : 0))) continue;
				int gx = MathUtils.clamp((int) (u * gw), 0, gw - 1), gy = MathUtils.clamp((int) (v * gh), 0, gh - 1);
				int rr = (int) Math.ceil(spacing[g]);
				boolean free = true;
				for (int y = Math.max(0, gy - rr); y <= Math.min(gh - 1, gy + rr) && free; y++)
					for (int x = Math.max(0, gx - rr); x <= Math.min(gw - 1, gx + rr); x++) {
						float need = Math.max(occ[y * gw + x], spacing[g]);
						if (occ[y * gw + x] > 0 && (x - gx) * (x - gx) + (y - gy) * (y - gy) < need * need) {
							free = false;
							break;
						}
					}
				if (!free) continue;
				occ[gy * gw + gx] = spacing[g];
				String kind = kinds[g][rnd.nextInt(kinds[g].length)];
				int p = d.proto("builtin:" + kind);
				float sc = size[g][0] + rnd.nextFloat() * (size[g][1] - size[g][0]);
				d.inst.add(new TerrainData.Inst(p, u, v, rnd.nextFloat() * 360f, sc, rnd.nextInt()));
				placed++;
			}
		}
	}

	// ------------------------------------------------------------------ noise

	static float fbm(float x, float y, int s, int oct, float pers) {
		float v = 0, a = 0.5f, f = 1, norm = 0;
		for (int o = 0; o < oct; o++) {
			v += a * Terrain3D.noise(x * f, y * f, s + o * 17);
			norm += a;
			a *= pers;
			f *= 2.03f;
		}
		return v / norm;
	}
}
