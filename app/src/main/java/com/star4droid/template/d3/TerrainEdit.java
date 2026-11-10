package com.star4droid.template.d3;

import com.badlogic.gdx.math.MathUtils;

/**
 * Brushes on a painted terrain: raise, lower, smooth, flatten, noise, plateau, paint a layer, path (flatten + paint),
 * place / erase objects. Used by the terrain editor and by blocks at run time (craters, digging...). Radius in meters,
 * strength 0..1, the brush is soft at its edge (falloff 0 = hard .. 1 = very soft).
 */
public final class TerrainEdit {
	public static final int RAISE = 0, LOWER = 1, SMOOTH = 2, FLATTEN = 3, NOISE = 4, PLATEAU = 5, PAINT = 6, PATH = 7, PLACE = 8, ERASE = 9;
	public static final String[] TOOLS = {"RAISE", "LOWER", "SMOOTH", "FLATTEN", "NOISE", "PLATEAU", "PAINT", "PATH", "PLACE", "ERASE"};

	private TerrainEdit() {
	}

	/** Brush weight at distance r (0..1 of the radius). */
	static float fall(float r, float falloff) {
		if (r >= 1) return 0;
		float inner = 1 - MathUtils.clamp(falloff, 0, 1);
		if (r <= inner) return 1;
		float t = (r - inner) / Math.max(0.0001f, 1 - inner);
		t = 1 - t;
		return t * t * (3 - 2 * t);
	}

	/**
	 * One step of a height brush at terrain u, v (0..1). target = height 0..1 for FLATTEN / PLATEAU / PATH.
	 * Returns false when nothing changed.
	 */
	public static boolean heights(Terrain3D t, Object3D o, int tool, float u, float v, float radius, float strength, float falloff, float target, float dt) {
		TerrainData d = t.data;
		float sx = Math.abs(o.scale.x), sz = Math.abs(o.scale.z), sy = Math.abs(o.scale.y);
		float hm = Math.max(0.01f, t.maxHeight * sy);
		int res = d.res, n = d.n();
		float ru = radius / sx, rv = radius / sz;
		int i0 = Math.max(0, (int) ((u - ru) * res) - 1), i1 = Math.min(res, (int) ((u + ru) * res) + 1);
		int j0 = Math.max(0, (int) ((v - rv) * res) - 1), j1 = Math.min(res, (int) ((v + rv) * res) + 1);
		if (i0 > i1 || j0 > j1) return false;
		float k = MathUtils.clamp(strength, 0, 1);
		float[] src = tool == SMOOTH ? d.h.clone() : d.h;
		boolean any = false;
		for (int j = j0; j <= j1; j++)
			for (int i = i0; i <= i1; i++) {
				float du = ((float) i / res - u) / ru, dv = ((float) j / res - v) / rv;
				float w = fall((float) Math.sqrt(du * du + dv * dv), falloff);
				if (w <= 0) continue;
				int idx = j * n + i;
				float hv = d.h[idx];
				switch (tool) {
					case RAISE:
						hv += w * k * 4f * dt / hm;
						break;
					case LOWER:
						hv -= w * k * 4f * dt / hm;
						break;
					case SMOOTH: {
						float a = (at(src, n, res, i - 1, j) + at(src, n, res, i + 1, j) + at(src, n, res, i, j - 1) + at(src, n, res, i, j + 1)
								+ at(src, n, res, i - 1, j - 1) + at(src, n, res, i + 1, j + 1) + at(src, n, res, i - 1, j + 1) + at(src, n, res, i + 1, j - 1)) / 8f;
						hv = MathUtils.lerp(hv, a, MathUtils.clamp(w * k * 12f * dt, 0, 1));
						break;
					}
					case FLATTEN:
					case PATH:
						hv = MathUtils.lerp(hv, target, MathUtils.clamp(w * k * 10f * dt, 0, 1));
						break;
					case PLATEAU:
						if (w > 0.5f) hv = target;
						else hv = MathUtils.lerp(hv, target, w * 2 * MathUtils.clamp(k * 10f * dt, 0, 1));
						break;
					case NOISE: {
						float nz = Terrain3D.noise(i * 0.35f, j * 0.35f, 99) - 0.5f;
						hv += nz * w * k * 3f * dt / hm;
						break;
					}
					default:
						continue;
				}
				if (hv != d.h[idx]) {
					d.h[idx] = hv;
					any = true;
				}
			}
		if (any) t.heightsChanged(i0, j0, i1, j1);
		return any;
	}

	private static float at(float[] h, int n, int res, int i, int j) {
		i = MathUtils.clamp(i, 0, res);
		j = MathUtils.clamp(j, 0, res);
		return h[j * n + i];
	}

	/** Paints layer 0..3 (adds weight, the others shrink). Returns false when nothing changed. Upload with splatChanged(). */
	public static boolean paint(Terrain3D t, Object3D o, int layer, float u, float v, float radius, float strength, float falloff, float dt) {
		TerrainData d = t.data;
		float sx = Math.abs(o.scale.x), sz = Math.abs(o.scale.z);
		int sr = d.splatRes;
		float ru = radius / sx, rv = radius / sz;
		int x0 = Math.max(0, (int) ((u - ru) * sr) - 1), x1 = Math.min(sr - 1, (int) ((u + ru) * sr) + 1);
		int y0 = Math.max(0, (int) ((v - rv) * sr) - 1), y1 = Math.min(sr - 1, (int) ((v + rv) * sr) + 1);
		layer = MathUtils.clamp(layer, 0, 3);
		boolean any = false;
		float k = MathUtils.clamp(strength, 0, 1) * 6f * dt;
		for (int y = y0; y <= y1; y++)
			for (int x = x0; x <= x1; x++) {
				float du = ((x + 0.5f) / sr - u) / ru, dv = ((y + 0.5f) / sr - v) / rv;
				float w = fall((float) Math.sqrt(du * du + dv * dv), falloff) * k;
				if (w <= 0) continue;
				int p = (y * sr + x) * 4;
				float[] c = {(d.splat[p] & 255) / 255f, (d.splat[p + 1] & 255) / 255f, (d.splat[p + 2] & 255) / 255f, (d.splat[p + 3] & 255) / 255f};
				float sum = c[0] + c[1] + c[2] + c[3];
				if (sum < 0.001f) {
					c[0] = 1;
					sum = 1;
				}
				for (int q = 0; q < 4; q++) c[q] /= sum;
				float add = Math.min(w, 1 - c[layer]);
				if (add <= 0.0005f) continue;
				float rest = 1 - c[layer];
				for (int q = 0; q < 4; q++) {
					if (q == layer) c[q] += add;
					else if (rest > 0) c[q] -= add * c[q] / rest;
				}
				for (int q = 0; q < 4; q++) d.splat[p + q] = (byte) MathUtils.clamp(Math.round(c[q] * 255), 0, 255);
				any = true;
			}
		if (any) d.revision++;
		return any;
	}

	/** Adds objects of kind p around u, v: about density per 10 m^2 per second, keeping spacing (m). */
	public static int place(Terrain3D t, Object3D o, int p, float u, float v, float radius, float density, float spacing, float minScale, float maxScale, float dt, int seed) {
		TerrainData d = t.data;
		float sx = Math.abs(o.scale.x), sz = Math.abs(o.scale.z);
		float area = MathUtils.PI * radius * radius;
		float want = density * area / 10f * dt;
		int count = (int) want + (Foliage3D.hashf(seed) < want - (int) want ? 1 : 0);
		int added = 0;
		for (int c = 0; c < count * 4 && added < count; c++) {
			float a = Foliage3D.hashf(seed + c * 3) * MathUtils.PI2, r = (float) Math.sqrt(Foliage3D.hashf(seed + c * 3 + 1)) * radius;
			float pu = u + MathUtils.cos(a) * r / sx, pv = v + MathUtils.sin(a) * r / sz;
			if (pu < 0.005f || pu > 0.995f || pv < 0.005f || pv > 0.995f) continue;
			boolean ok = true;
			for (TerrainData.Inst in : d.inst) {
				float dx = (in.u - pu) * sx, dz = (in.v - pv) * sz;
				if (dx * dx + dz * dz < spacing * spacing) {
					ok = false;
					break;
				}
			}
			if (!ok) continue;
			float sc = minScale + Foliage3D.hashf(seed + c * 3 + 2) * (maxScale - minScale);
			d.inst.add(new TerrainData.Inst(p, pu, pv, Foliage3D.hashf(seed * 7 + c) * 360f, sc, seed * 31 + c));
			added++;
		}
		if (added > 0) {
			d.revision++;
			if (t.foliage != null) t.foliage.instancesChanged();
		}
		return added;
	}

	/** Removes the objects (of kind p, or all with p < 0) within radius of u, v. */
	public static int erase(Terrain3D t, Object3D o, int p, float u, float v, float radius) {
		TerrainData d = t.data;
		float sx = Math.abs(o.scale.x), sz = Math.abs(o.scale.z);
		int removed = 0;
		for (int i = d.inst.size - 1; i >= 0; i--) {
			TerrainData.Inst in = d.inst.get(i);
			if (p >= 0 && in.p != p) continue;
			float dx = (in.u - u) * sx, dz = (in.v - v) * sz;
			if (dx * dx + dz * dz <= radius * radius) {
				d.inst.removeIndex(i);
				removed++;
			}
		}
		if (removed > 0) {
			d.revision++;
			if (t.foliage != null) t.foliage.instancesChanged();
		}
		return removed;
	}
}
