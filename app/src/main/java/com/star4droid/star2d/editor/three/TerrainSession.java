package com.star4droid.star2d.editor.three;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.items.Editor3DItem;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.template.d3.Assets3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.Terrain3D;
import com.star4droid.template.d3.TerrainData;
import com.star4droid.template.d3.TerrainEdit;
import com.star4droid.template.d3.TerrainGen;
import com.star4droid.template.d3.TerrainStore;

/**
 * The terrain editor of the 3D viewport (GL thread): brushes under the finger, undo / redo per stroke, saving the
 * terrain file, the random generator. The Android panel ({@code ui.TerrainPanel}) only changes the public settings and
 * calls the methods here through {@code Bridge.gl}.
 *
 * Touch: one finger = the brush (or orbit when {@link #navigate} is on), two fingers = pan / zoom like the editor.
 */
public class TerrainSession {
	public final Editor3D e;
	public Editor3DItem item;
	public int tool = TerrainEdit.RAISE;
	public float radius = 4, strength = 0.5f, falloff = 0.6f;
	public int layer = 1;
	/** Kind for PLACE ("builtin:tree_round" or "/model.glb"). */
	public String kind = "builtin:tree_round";
	public float density = 1.5f, spacing = 2.5f, minScale = 0.8f, maxScale = 1.25f;
	/** ERASE: only the selected kind (else everything). */
	public boolean eraseKindOnly = false;
	/** One finger moves the view instead of painting. */
	public boolean navigate = false;
	public boolean dirty;

	/** UI refresh (undo / redo state, messages); called on the GL thread. */
	public interface Listener {
		void changed(TerrainSession s);
	}

	public Listener listener;

	private static final int MAX_UNDO = 12;
	private final Array<TerrainData> undo = new Array<TerrainData>(), redo = new Array<TerrainData>();
	private boolean stroking;
	private float targetH;
	private final Vector3 cursor = new Vector3();
	private boolean cursorOn;
	private int touchX, touchY;
	private float splatTimer;
	private boolean splatPending;
	private int seed = 1;

	public TerrainSession(Editor3D e) {
		this.e = e;
	}

	public Object3D obj() {
		return item == null ? null : item.obj;
	}

	public Terrain3D terrain() {
		Object3D o = obj();
		return o == null ? null : o.terrain();
	}

	public boolean canUndo() {
		return undo.size > 0;
	}

	public boolean canRedo() {
		return redo.size > 0;
	}

	public String path() {
		return item == null ? "" : item.getPropertySet().getString("Terrain Data");
	}

	/**
	 * Starts editing a TERRAIN element. An older terrain (height image / noise) becomes a painted one: its current
	 * heights go into a new terrain file. Returns null or an error message.
	 */
	public String start(Editor3DItem it) {
		if (it == null || it.obj == null || it.getPropertySet() == null) return "no terrain selected";
		if (!"TERRAIN".equalsIgnoreCase(it.getPropertySet().getString("Mesh"))) return "the selected element is not a terrain";
		item = it;
		undo.clear();
		redo.clear();
		Terrain3D t = terrain();
		if (t == null) return "terrain not ready";
		if (!t.painted) {
			TerrainData d = t.data.copy();
			if (d.res < 64) d.resample(Math.min(128, Math.max(64, d.res * 2)));
			int sr = 256;
			d.splatRes = sr;
			d.splat = new byte[sr * sr * 4];
			d.fillLayer(0);
			String p = newPath(it.getName());
			TerrainStore.put(p, d);
			TerrainStore.save(p);
			setProps(new String[]{"Terrain Data", p, "Color", "#FFFFFF"}, "Terrain editor");
		}
		notifyUi();
		return null;
	}

	/** A free terrain file name for an element. */
	public static String newPath(String name) {
		String base = name.replaceAll("[^A-Za-z0-9_]", "_");
		String p = "/" + base + ".s2t";
		int k = 2;
		while (TerrainStore.fileOf(p).exists() || TerrainStore.get(p) != null) p = "/" + base + "_" + (k++) + ".s2t";
		return p;
	}

	public void stop() {
		save();
		item = null;
		cursorOn = false;
		stroking = false;
		undo.clear();
		redo.clear();
	}

	public boolean save() {
		if (item == null) return false;
		flushSplat();
		boolean ok = TerrainStore.save(path());
		if (ok) dirty = false;
		return ok;
	}

	/** Changes properties of the terrain element as one undo step of the editor, and rebuilds it. */
	public void setProps(String[] kv, String message) {
		LibgdxEditor ed = e.editor;
		PropertySet<String, Object> ps = item.getPropertySet();
		String[] old = new String[kv.length];
		for (int i = 0; i < kv.length; i += 2) {
			old[i] = kv[i];
			old[i + 1] = ps.getString(kv[i]);
			ps.put(kv[i], kv[i + 1]);
		}
		item.setProperties(ps);
		ed.updateProperties();
		ed.getSaveState();
		EditorAction.propertiesChanged(ed, item.getName(), message, kv, old);
	}

	// ------------------------------------------------------------------ touch

	private boolean pick(int x, int y, Vector3 out) {
		Terrain3D t = terrain();
		if (t == null) return false;
		Ray ray = e.cam.getPickRay(x, y);
		return t.intersect(obj(), ray, out);
	}

	public void touchDown(int x, int y) {
		touchX = x;
		touchY = y;
		Terrain3D t = terrain();
		if (t == null || !pick(x, y, cursor)) {
			cursorOn = false;
			return;
		}
		cursorOn = true;
		pushUndo();
		stroking = true;
		float[] uv = new float[2];
		t.uvAt(obj(), cursor.x, cursor.y, cursor.z, uv);
		targetH = t.data.height(uv[0], uv[1]);
		seed = (int) (System.nanoTime() & 0x7fffffff);
		if (tool == TerrainEdit.PLACE) applyOnce(0.35f);
		if (tool == TerrainEdit.ERASE) applyOnce(0);
	}

	public void touchDragged(int x, int y) {
		touchX = x;
		touchY = y;
		if (pick(x, y, cursor)) cursorOn = true;
	}

	public void touchUp() {
		if (stroking) {
			stroking = false;
			flushSplat();
			dirty = true;
			Terrain3D t = terrain();
			if (t != null && t.foliage != null && (tool == TerrainEdit.PLACE || tool == TerrainEdit.ERASE)) t.foliage.instancesChanged();
			notifyUi();
		}
	}

	/** A second finger came: the stroke so far stays (one undo step). */
	public void cancelStroke() {
		touchUp();
	}

	private void applyOnce(float dt) {
		Terrain3D t = terrain();
		Object3D o = obj();
		if (t == null || o == null || !cursorOn) return;
		float[] uv = new float[2];
		if (!t.uvAt(o, cursor.x, cursor.y, cursor.z, uv)) return;
		float u = uv[0], v = uv[1];
		switch (tool) {
			case TerrainEdit.PAINT:
				if (TerrainEdit.paint(t, o, layer - 1, u, v, radius, strength, falloff, dt)) splatPending = true;
				break;
			case TerrainEdit.PATH:
				TerrainEdit.heights(t, o, TerrainEdit.PATH, u, v, radius * 0.9f, strength, falloff, targetH, dt);
				if (TerrainEdit.paint(t, o, layer - 1, u, v, radius * 0.75f, Math.min(1, strength * 1.5f), Math.min(falloff, 0.5f), dt)) splatPending = true;
				break;
			case TerrainEdit.PLACE: {
				int p = t.data.proto(kind);
				TerrainEdit.place(t, o, p, u, v, radius, density, spacing, minScale, maxScale, dt, seed++);
				break;
			}
			case TerrainEdit.ERASE:
				TerrainEdit.erase(t, o, eraseKindOnly ? indexOf(t.data, kind) : -1, u, v, radius);
				break;
			default:
				TerrainEdit.heights(t, o, tool, u, v, radius, strength, falloff, targetH, dt);
		}
	}

	private static int indexOf(TerrainData d, String kind) {
		for (int i = 0; i < d.protos.size; i++) if (d.protos.get(i).src.equals(kind)) return i;
		return -2;
	}

	/** Every frame (GL thread, before drawing). */
	public void update(float dt) {
		if (item == null) return;
		if (item.obj == null || item.getStage() == null) {
			item = null;
			notifyUi();
			return;
		}
		if (stroking && cursorOn) {
			// the finger may stay still on a moving camera: pick again every frame
			pick(touchX, touchY, cursor);
			applyOnce(Math.min(dt, 0.05f));
		}
		splatTimer += dt;
		if (splatPending && splatTimer > 0.06f) flushSplat();
	}

	private void flushSplat() {
		if (!splatPending) return;
		splatPending = false;
		splatTimer = 0;
		Terrain3D t = terrain();
		if (t != null) t.splatChanged();
	}

	// ------------------------------------------------------------------ undo

	private void pushUndo() {
		Terrain3D t = terrain();
		if (t == null) return;
		undo.add(t.data.copy());
		// big paint maps take megabytes per step: keep fewer
		int max = t.data.splatRes >= 1024 ? 5 : (t.data.splatRes >= 512 ? 8 : MAX_UNDO);
		while (undo.size > max) undo.removeIndex(0);
		redo.clear();
	}

	public void undo() {
		Terrain3D t = terrain();
		if (t == null || undo.size == 0) return;
		redo.add(t.data.copy());
		restore(undo.pop());
	}

	public void redo() {
		Terrain3D t = terrain();
		if (t == null || redo.size == 0) return;
		undo.add(t.data.copy());
		restore(redo.pop());
	}

	private void restore(TerrainData snap) {
		Terrain3D t = terrain();
		TerrainData d = t.data;
		// in place: the store and every view keep the same object
		d.res = snap.res;
		d.h = snap.h;
		d.splatRes = snap.splatRes;
		d.splat = snap.splat;
		d.protos.clear();
		d.protos.addAll(snap.protos);
		d.inst.clear();
		d.inst.addAll(snap.inst);
		d.revision++;
		obj().buildModel();
		dirty = true;
		notifyUi();
	}

	// ------------------------------------------------------------------ whole terrain

	/** Replaces the terrain with a generated one (one undo step for the terrain + the element's properties). */
	public void generate(TerrainGen.Settings s) {
		Terrain3D t = terrain();
		if (t == null) return;
		pushUndo();
		TerrainGen.Result r = TerrainGen.generate(s);
		TerrainData d = t.data;
		d.res = r.data.res;
		d.h = r.data.h;
		d.splatRes = r.data.splatRes;
		d.splat = r.data.splat;
		d.protos.clear();
		d.protos.addAll(r.data.protos);
		d.inst.clear();
		d.inst.addAll(r.data.inst);
		d.revision++;
		Array<String> kv = new Array<String>();
		for (com.badlogic.gdx.utils.ObjectMap.Entry<String, String> en : r.props.entries()) {
			kv.add(en.key);
			kv.add(en.value);
		}
		setProps(kv.toArray(String.class), "Generate terrain");
		item.obj.buildModel();
		save();
		lastWaterY = r.waterY;
		notifyUi();
	}

	/** Water height (m above the terrain's position) of the last generation, NaN = none. */
	public float lastWaterY = Float.NaN;

	/** Grid resolution change (cells per side). */
	public void setResolution(int res) {
		Terrain3D t = terrain();
		if (t == null) return;
		pushUndo();
		t.data.resample(res);
		obj().buildModel();
		dirty = true;
		notifyUi();
	}

	/** Paints the whole terrain with one layer. */
	public void fillLayer(int l) {
		Terrain3D t = terrain();
		if (t == null) return;
		pushUndo();
		t.data.fillLayer(MathUtils.clamp(l - 1, 0, 3));
		t.splatChanged();
		dirty = true;
		notifyUi();
	}

	/** Flattens the whole terrain to a height (0..1). */
	public void flattenAll(float h) {
		Terrain3D t = terrain();
		if (t == null) return;
		pushUndo();
		java.util.Arrays.fill(t.data.h, h);
		t.heightsChanged(0, 0, t.res, t.res);
		dirty = true;
		notifyUi();
	}

	/** Removes every object (or every object of the selected kind). */
	public void clearObjects(boolean kindOnly) {
		Terrain3D t = terrain();
		if (t == null) return;
		pushUndo();
		int k = kindOnly ? indexOf(t.data, kind) : -1;
		for (int i = t.data.inst.size - 1; i >= 0; i--) if (k < 0 || t.data.inst.get(i).p == k) t.data.inst.removeIndex(i);
		t.data.revision++;
		if (t.foliage != null) t.foliage.instancesChanged();
		dirty = true;
		notifyUi();
	}

	// ------------------------------------------------------------------ drawing

	/** The brush circle on the ground. */
	public void drawCursor(com.badlogic.gdx.graphics.glutils.ShapeRenderer shapes) {
		Terrain3D t = terrain();
		Object3D o = obj();
		if (t == null || o == null || !cursorOn || navigate) return;
		float[] uv = new float[2];
		if (!t.uvAt(o, cursor.x, cursor.y, cursor.z, uv)) return;
		float sx = Math.abs(o.scale.x), sz = Math.abs(o.scale.z);
		Vector3 a = new Vector3(), b = new Vector3();
		Gdx.gl.glLineWidth(3f);
		shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line);
		for (int ring = 0; ring < 2; ring++) {
			float r = ring == 0 ? radius : radius * (1 - falloff * 0.85f);
			shapes.setColor(ring == 0 ? 1f : 1f, ring == 0 ? 1f : 0.75f, ring == 0 ? 1f : 0.2f, 0.95f);
			int seg = 40;
			for (int i = 0; i <= seg; i++) {
				float ang = i * MathUtils.PI2 / seg;
				float u = uv[0] + MathUtils.cos(ang) * r / sx, v = uv[1] + MathUtils.sin(ang) * r / sz;
				t.worldAt(o, MathUtils.clamp(u, 0, 1), MathUtils.clamp(v, 0, 1), b);
				b.y += 0.08f;
				if (i > 0) shapes.line(a, b);
				a.set(b);
			}
		}
		shapes.end();
		Gdx.gl.glLineWidth(1f);
	}

	private void notifyUi() {
		if (listener != null) listener.changed(this);
	}

	@SuppressWarnings("unused")
	private static String projectPath() {
		return Assets3D.projectPath;
	}
}
