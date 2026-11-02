package com.star4droid.template.d3;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.star2d.ElementDefs.Object3DDef;

/**
 * Terrain: a 1 x 1 grid (scaled by the element) whose heights come from a gray image (white = Terrain Height) and / or
 * smooth noise. Height at any point is available for walking, placing and the camera.
 */
public class Terrain3D {
	public Model model;
	public int res;
	/** heights 0..1 (res+1)^2 */
	public float[] h;
	public float maxHeight;

	public static Terrain3D build(Object3DDef d) {
		Terrain3D t = new Terrain3D();
		t.res = MathUtils.clamp((int) d.Terrain_Resolution, 4, 200);
		t.maxHeight = d.Terrain_Height;
		int n = t.res + 1;
		t.h = new float[n * n];
		Pixmap pm = d.Heightmap != null && d.Heightmap.length() > 0 ? Assets3D.pixmap(d.Heightmap) : null;
		Color c = new Color();
		for (int j = 0; j < n; j++)
			for (int i = 0; i < n; i++) {
				float v = 0;
				if (pm != null) {
					int px = MathUtils.clamp((int) ((float) i / t.res * (pm.getWidth() - 1)), 0, pm.getWidth() - 1);
					int py = MathUtils.clamp((int) ((float) j / t.res * (pm.getHeight() - 1)), 0, pm.getHeight() - 1);
					Color.rgba8888ToColor(c, pm.getPixel(px, py));
					v = (c.r + c.g + c.b) / 3f;
				}
				if (d.Terrain_Noise > 0) v += d.Terrain_Noise * fbm(i * 4f / t.res, j * 4f / t.res, (int) d.Terrain_Seed);
				t.h[j * n + i] = v;
			}
		if (pm != null) pm.dispose();
		ModelBuilder mb = new ModelBuilder();
		mb.begin();
		MeshPartBuilder b = mb.part("terrain", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, new Material());
		MeshPartBuilder.VertexInfo[] vs = new MeshPartBuilder.VertexInfo[n * n];
		Vector3 nor = new Vector3();
		for (int j = 0; j < n; j++)
			for (int i = 0; i < n; i++) {
				float hl = t.at(i - 1, j), hr = t.at(i + 1, j), hd = t.at(i, j - 1), hu = t.at(i, j + 1);
				nor.set((hl - hr) * t.maxHeight * t.res, 2f, (hd - hu) * t.maxHeight * t.res).nor();
				vs[j * n + i] = new MeshPartBuilder.VertexInfo().setPos((float) i / t.res - 0.5f, t.h[j * n + i] * t.maxHeight, (float) j / t.res - 0.5f).setNor(nor).setUV((float) i / t.res, (float) j / t.res);
			}
		for (int j = 0; j < t.res; j++)
			for (int i = 0; i < t.res; i++)
				b.rect(vs[(j + 1) * n + i], vs[(j + 1) * n + i + 1], vs[j * n + i + 1], vs[j * n + i]);
		t.model = mb.end();
		return t;
	}

	float at(int i, int j) {
		int n = res + 1;
		i = MathUtils.clamp(i, 0, res);
		j = MathUtils.clamp(j, 0, res);
		return h[j * n + i];
	}

	/** World height at x, z (meters). */
	public float heightAt(Object3D o, float x, float z) {
		Vector3 p = new Vector3(x, 0, z);
		com.badlogic.gdx.math.Matrix4 inv = new com.badlogic.gdx.math.Matrix4(o.transform).inv();
		p.mul(inv);
		float u = (p.x + 0.5f) * res, v = (p.z + 0.5f) * res;
		int i = MathUtils.floor(u), j = MathUtils.floor(v);
		float fu = u - i, fv = v - j;
		float h00 = at(i, j), h10 = at(i + 1, j), h01 = at(i, j + 1), h11 = at(i + 1, j + 1);
		float hl = MathUtils.lerp(MathUtils.lerp(h00, h10, fu), MathUtils.lerp(h01, h11, fu), fv) * maxHeight;
		Vector3 w = new Vector3(p.x, hl, p.z).mul(o.transform);
		return w.y;
	}

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
}
