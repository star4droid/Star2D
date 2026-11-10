package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.ElementDefs.Object3DDef;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.Terrain3D;
import com.star4droid.template.d3.TerrainData;
import com.star4droid.template.d3.TerrainTextures;
import com.star4droid.template.d3.World3D;

/**
 * A round minimap drawn inside a 2D box: the painted terrain seen from above (its paint layers, shaded hills, water,
 * trees) around the player, turning with the camera, with the player's arrow and dots for quest places and enemies.
 * The map picture is made once from the terrain (no second camera, no extra 3D drawing).
 */
@BehaviorInfo(name = "Minimap 3D", icon = "map", category = "UI",
		desc = "A round minimap in a 2D box: the painted terrain from above (paint, hills, water, trees) around the player, turning with the camera, the player's arrow and dots for quest places and enemies. Made from the terrain itself: no extra camera, cheap to draw.")
public class Minimap3D extends Behavior {
	@Header("Map")
	@Param(desc = "The player in the middle (empty = the main camera).")
	public PlayerItem target;
	@Param(desc = "Or the nearest shown element whose name starts with one of these (comma separated, e.g. Hero). Replaces Target.")
	public String targetNames = "";
	@Param(desc = "Painted terrain shown (empty = the first one of the scene).")
	public PlayerItem terrain;
	@Param(desc = "Meters shown across the minimap.", unit = "m", min = 5, max = 2000, step = 1)
	public float span = 70;
	@Param(desc = "Turns with the camera (up = where the camera looks); off = north up.")
	public boolean rotateWithCamera = true;
	@Param(desc = "Empty border inside the box (the frame of its image).", unit = "px", min = 0, max = 200, step = 1)
	public float inset = 4;
	@Param(desc = "Draws trees and rocks of the terrain as dark dots.")
	public boolean showTrees = true;

	@Header("Dots")
	@Param(desc = "Places shown as dots (name prefixes, comma separated); they stay on the edge when far.")
	public String markerNames = "";
	@Param(desc = "Color of those dots.")
	public String markerColor = "#FFD36A";
	@Param(desc = "Enemies shown as dots when on the map (name prefixes).")
	public String enemyNames = "";
	@Param(desc = "Color of the enemy dots.")
	public String enemyColor = "#FF4B5C";
	@Param(desc = "Color of the player's arrow.")
	public String arrowColor = "#2A6BFF";

	private static final int SLICES = 40;
	private MapActor actor;
	private Texture map, dot, arrow;
	private int mapRev = -1;
	private Object3D terrainObj;
	private final GameplaySupport.NameMatcher tNames = new GameplaySupport.NameMatcher(), mNames = new GameplaySupport.NameMatcher(),
			eNames = new GameplaySupport.NameMatcher();
	private final Vector3 center = new Vector3(), fwd = new Vector3(), tmp = new Vector3();
	private float scanT, heading;
	private final float[] uv = new float[2];
	private final com.badlogic.gdx.math.Matrix4 inv = new com.badlogic.gdx.math.Matrix4();
	private final java.util.ArrayList<Object3D> waters = new java.util.ArrayList<Object3D>();
	private final float[] verts = new float[20];

	@Override
	public void onStart() {
		if (item == null || item.getActor() == null || item.getActor().getParent() == null) return;
		actor = new MapActor();
		actor.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
		item.getActor().getParent().addActorAfter(item.getActor(), actor);
		dot = circle(32, false);
		arrow = circle(48, true);
	}

	@Override
	public void onDestroy() {
		if (actor != null) actor.remove();
		if (map != null) map.dispose();
		if (dot != null) dot.dispose();
		if (arrow != null) arrow.dispose();
		map = dot = arrow = null;
	}

	private static Texture circle(int n, boolean isArrow) {
		Pixmap p = new Pixmap(n, n, Pixmap.Format.RGBA8888);
		p.setColor(0, 0, 0, 0);
		p.fill();
		if (isArrow) {
			// an arrow pointing up (white with a dark outline)
			p.setColor(0.05f, 0.08f, 0.2f, 1);
			p.fillTriangle(n / 2, 0, 2, n - 2, n - 2, n - 2);
			p.setColor(1, 1, 1, 1);
			p.fillTriangle(n / 2, 5, 7, n - 5, n - 7, n - 5);
			p.setColor(0.05f, 0.08f, 0.2f, 1);
			p.fillTriangle(n / 2, n - 13, 8, n - 3, n - 8, n - 3);
		} else {
			p.setColor(0.04f, 0.06f, 0.14f, 1);
			p.fillCircle(n / 2, n / 2, n / 2 - 1);
			p.setColor(1, 1, 1, 1);
			p.fillCircle(n / 2, n / 2, n / 2 - 4);
		}
		Texture t = new Texture(p);
		t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		p.dispose();
		return t;
	}

	private Object3D findTerrain(World3D w) {
		if (terrain instanceof Object3D && ((Object3D) terrain).terrain() != null) return (Object3D) terrain;
		for (int i = 0; i < w.objects.size; i++) {
			Object3D o = w.objects.get(i);
			if (o.terrain() != null && o.terrain().data != null) return o;
		}
		return null;
	}

	/** The picture of the terrain from above. */
	private void makeMap(Object3D t) {
		Terrain3D tr = t.terrain();
		TerrainData d = tr.data;
		Object3DDef od = t.d();
		Color[] lc = new Color[4];
		String[] tex = {od.Layer_1_Texture, od.Layer_2_Texture, od.Layer_3_Texture, od.Layer_4_Texture};
		String[] col = {od.Layer_1_Color, od.Layer_2_Color, od.Layer_3_Color, od.Layer_4_Color};
		for (int i = 0; i < 4; i++) {
			lc[i] = TerrainTextures.average(tex[i], i);
			lc[i].mul(com.star4droid.template.d3.Materials3D.color(col[i], Color.WHITE));
		}
		int n = 256;
		float sx = Math.max(0.01f, t.getSizeX()), sz = Math.max(0.01f, t.getSizeZ());
		// keep square meters square
		int w = sx >= sz ? n : Math.max(16, Math.round(n * sx / sz)), h = sz >= sx ? n : Math.max(16, Math.round(n * sz / sx));
		Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
		World3D wd = world3d();
		waters.clear();
		if (wd != null) for (int i = 0; i < wd.objects.size; i++) {
			Object3D o = wd.objects.get(i);
			if (o.d() != null && "WATER".equalsIgnoreCase(o.d().Mesh) && o.isVisible()) waters.add(o);
		}
		float hScale = tr.maxHeight * Math.abs(t.scale.y);
		Color c = new Color();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float u = (x + 0.5f) / w, v = (y + 0.5f) / h;
				c.set(0, 0, 0, 1);
				for (int k = 0; k < 4; k++) {
					float wt = d.weight(k, u, v);
					c.r += lc[k].r * wt;
					c.g += lc[k].g * wt;
					c.b += lc[k].b * wt;
				}
				// hill shading (light from the top left)
				float e = 1f / w;
				float dx = (d.height(u + e, v) - d.height(u - e, v)) * hScale / (2 * e * sx);
				float dz = (d.height(u, v + e) - d.height(u, v - e)) * hScale / (2 * e * sz);
				float shade = MathUtils.clamp(1f - (dx + dz) * 0.35f, 0.55f, 1.25f);
				c.r *= shade;
				c.g *= shade;
				c.b *= shade;
				// water elements cover what is under their surface
				if (waters.size() > 0) {
					tmp.set(u - 0.5f, d.height(u, v) * tr.maxHeight, v - 0.5f).mul(t.transform);
					for (int i = 0; i < waters.size(); i++) {
						Object3D o = waters.get(i);
						if (Math.abs(tmp.x - o.position.x) > o.getSizeX() * 0.5f || Math.abs(tmp.z - o.position.z) > o.getSizeZ() * 0.5f) continue;
						if (tmp.y < o.position.y + 0.05f) {
							c.set(0.36f, 0.68f, 0.86f, 1);
							break;
						}
					}
				}
				c.clamp();
				c.a = 1;
				p.drawPixel(x, y, Color.rgba8888(c));
			}
		}
		if (showTrees && tr.foliage != null) {
			com.badlogic.gdx.utils.FloatArray cs = tr.foliage.colliders(t);
			p.setColor(0.13f, 0.32f, 0.12f, 1);
			for (int i = 0; i + 4 < cs.size; i += 5) {
				tmp.set(cs.get(i), cs.get(i + 1), cs.get(i + 2)).mul(t.transform);
				if (!tr.uvAt(t, tmp.x, tmp.y, tmp.z, uv)) continue;
				int r = Math.max(1, Math.round(cs.get(i + 3) * 2.2f * w / sx));
				p.fillCircle(Math.round(uv[0] * w), Math.round(uv[1] * h), r);
			}
		}
		if (map != null) map.dispose();
		map = new Texture(p);
		map.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		p.dispose();
		mapRev = d.revision;
	}

	@Override
	public void onUpdate(float dt) {
		World3D w = world3d();
		if (w == null || actor == null) return;
		if (terrainObj == null || terrainObj.terrain() == null) terrainObj = findTerrain(w);
		if (terrainObj != null && terrainObj.terrain().data != null && (map == null || mapRev != terrainObj.terrain().data.revision)) makeMap(terrainObj);
		if (targetNames.length() > 0) {
			scanT -= dt;
			if (scanT <= 0 || target == null || !(target instanceof Object3D) || !((Object3D) target).isVisible()) {
				scanT = 0.3f;
				tNames.set(targetNames);
				for (int i = 0; i < w.objects.size; i++) {
					Object3D o = w.objects.get(i);
					if (o.isVisible() && tNames.matches(o.getName())) {
						target = o;
						break;
					}
				}
			}
		}
		if (target instanceof Object3D) {
			Object3D o = (Object3D) target;
			center.set(o.position);
			o.forward(tmp);
			heading = MathUtils.atan2(tmp.x, -tmp.z);
		} else if (w.camera != null) {
			center.set(w.camera.position);
			heading = MathUtils.atan2(w.camera.direction.x, -w.camera.direction.z);
		}
		if (rotateWithCamera && w.camera != null) fwd.set(w.camera.direction.x, 0, w.camera.direction.z);
		else fwd.set(0, 0, -1);
		if (fwd.isZero(0.0001f)) fwd.set(0, 0, -1);
		fwd.nor();
	}

	private final class MapActor extends Actor {
		@Override
		public void draw(Batch batch, float parentAlpha) {
			Actor box = item == null ? null : item.getActor();
			if (box == null || !box.isVisible() || map == null || terrainObj == null) return;
			float bx = box.getX() + inset, by = box.getY() + inset, bw = box.getWidth() - 2 * inset, bh = box.getHeight() - 2 * inset;
			float cx = bx + bw / 2, cy = by + bh / 2, r = Math.min(bw, bh) / 2;
			float mpp = span / Math.max(1f, 2 * r); // meters per pixel
			float rx = -fwd.z, rz = fwd.x; // screen right on the ground
			inv.set(terrainObj.transform).inv();
			float a = parentAlpha * box.getColor().a;
			float white = Color.toFloatBits(1f, 1f, 1f, a);
			// the map: a fan of triangles (quads with a repeated corner), texture coordinates from the ground under each corner
			for (int i = 0; i < SLICES; i++) {
				float a0 = i * MathUtils.PI2 / SLICES, a1 = (i + 1) * MathUtils.PI2 / SLICES;
				corner(0, cx, cy, 0, 0, mpp, rx, rz, white);
				corner(5, cx, cy, MathUtils.cos(a0) * r, MathUtils.sin(a0) * r, mpp, rx, rz, white);
				corner(10, cx, cy, MathUtils.cos(a1) * r, MathUtils.sin(a1) * r, mpp, rx, rz, white);
				corner(15, cx, cy, MathUtils.cos(a1) * r, MathUtils.sin(a1) * r, mpp, rx, rz, white);
				batch.draw(map, verts, 0, 20);
			}
			World3D w = world3d();
			float ds = Math.max(6, r * 0.13f);
			if (w != null) {
				mNames.set(markerNames);
				eNames.set(enemyNames);
				Color mc = com.star4droid.template.d3.Materials3D.color(markerColor, Color.YELLOW), ec = com.star4droid.template.d3.Materials3D.color(enemyColor, Color.RED);
				for (int i = 0; i < w.objects.size; i++) {
					Object3D o = w.objects.get(i);
					if (!o.isVisible() || o == target) continue;
					boolean mark = markerNames.length() > 0 && mNames.matches(o.getName());
					boolean enemy = !mark && enemyNames.length() > 0 && eNames.matches(o.getName()) && Behavior.alive(o);
					if (!mark && !enemy) continue;
					float dx = o.position.x - center.x, dz = o.position.z - center.z;
					float px = (dx * rx + dz * rz) / mpp, py = (dx * fwd.x + dz * fwd.z) / mpp;
					float len = (float) Math.sqrt(px * px + py * py), lim = r - ds * 0.6f;
					if (len > lim) {
						if (!mark) continue;
						px *= lim / len;
						py *= lim / len;
					}
					Color k = mark ? mc : ec;
					float s = mark ? ds * 1.15f : ds * 0.8f;
					batch.setColor(k.r, k.g, k.b, a);
					batch.draw(dot, cx + px - s / 2, cy + py - s / 2, s, s);
				}
			}
			// the player's arrow, turned to its heading relative to the map's up
			float up = MathUtils.atan2(fwd.x, -fwd.z);
			float rot = (heading - up) * MathUtils.radiansToDegrees;
			Color ac = com.star4droid.template.d3.Materials3D.color(arrowColor, Color.BLUE);
			float as = Math.max(10, r * 0.3f);
			batch.setColor(ac.r, ac.g, ac.b, a);
			batch.draw(arrow, cx - as / 2, cy - as / 2, as / 2, as / 2, as, as, 1, 1, -rot, 0, 0, arrow.getWidth(), arrow.getHeight(), false, false);
			batch.setColor(1, 1, 1, 1);
		}

		private void corner(int k, float cx, float cy, float px, float py, float mpp, float rx, float rz, float color) {
			float wx = center.x + (px * rx + py * fwd.x) * mpp, wz = center.z + (px * rz + py * fwd.z) * mpp;
			tmp.set(wx, center.y, wz).mul(inv);
			uv[0] = tmp.x + 0.5f;
			uv[1] = tmp.z + 0.5f;
			verts[k] = cx + px;
			verts[k + 1] = cy + py;
			verts[k + 2] = color;
			verts[k + 3] = uv[0];
			verts[k + 4] = uv[1];
		}
	}
}
