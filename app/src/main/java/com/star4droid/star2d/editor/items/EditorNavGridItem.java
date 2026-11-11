package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.template.nav.NavGrid;

import java.util.ArrayList;

/**
 * Editor element of a NAVGRID (path finding grid). It is invisible in the game: the editor draws its area outline and a
 * small badge (tap the badge to select it), and, while it is selected or painted, every cell: walls red, slow cells orange.
 *
 * <p>2D scenes: x, y, width, height in pixels like any element. 3D scenes: the grid lies on the ground, x / z is its corner
 * and y the ground height (meters), width along x and height along z; {@link com.star4droid.star2d.editor.three.Editor3D}
 * draws it there.</p>
 */
public class EditorNavGridItem extends Actor implements EditorItem {
	public static final String TYPE = "NAVGRID", FILE = "navgrid.json";
	/** Defaults of default.json that mean nothing for a grid: kept out of its properties. */
	private static final String[] UNUSED = {"rotation", "Pivot", "Pivot X", "Pivot Y", "type", "behaviors", "scriptVars", "objectVars", "net"};
	private static final Color OUTLINE = new Color(0.35f, 0.85f, 1f, 1f);

	PropertySet<String, Object> propertySet;
	final LibgdxEditor editor;
	private NavGrid grid;
	private String gridSig = "";
	private static Texture icon;
	private static Object iconApp;
	/** Brush preview (world units) while painting: NaN = none. */
	public float cursorX = Float.NaN, cursorY, cursorR;
	/** Rectangle preview while dragging a box tool (world units), NaN = none. */
	public float boxX0 = Float.NaN, boxY0, boxX1, boxY1;

	public EditorNavGridItem(LibgdxEditor editor) {
		this.editor = editor;
		if (icon == null || iconApp != Gdx.app) {
			icon = new Texture(Gdx.files.internal("images/bodies/navgrid.png"));
			icon.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
			iconApp = Gdx.app;
		}
		setSize(640, 480);
		setTouchable(Touchable.enabled);
		editor.addActor(this);
		addListener(new ItemClickListener(this, editor));
	}

	public EditorNavGridItem setDefault() {
		propertySet = PropertySet.getDefualt(this, FILE);
		if (propertySet == null) return this;
		strip(propertySet);
		if (editor.is3DScene()) {
			// meters on the ground: 24 x 24 m around the origin, 1 m cells
			propertySet.put("x", "-12");
			propertySet.put("y", "0");
			propertySet.put("z", "-12");
			propertySet.put("width", "24");
			propertySet.put("height", "24");
			propertySet.put("Cell Width", "1");
			propertySet.put("Cell Height", "1");
		} else {
			// the whole scene frame, 32 px cells
			propertySet.put("x", "0");
			propertySet.put("y", "0");
			propertySet.put("width", String.valueOf(Math.round(editor.getLogicWidth())));
			propertySet.put("height", String.valueOf(Math.round(editor.getLogicHeight())));
		}
		return this;
	}

	private static void strip(PropertySet<String, Object> ps) {
		for (String k : UNUSED) ps.remove(k);
	}

	@Override
	public PropertySet<String, Object> getPropertySet() {
		return propertySet;
	}

	@Override
	public void setProperties(PropertySet<String, Object> ps) {
		propertySet = ps;
		if (ps == null) return;
		PropertySet<String, Object> temp = PropertySet.getDefualt(this, FILE);
		strip(temp);
		for (String s : temp.keySet()) if (!ps.containsKey(s)) ps.put(s, temp.get(s));
		ArrayList<String> del = new ArrayList<>();
		for (String k : ps.keySet()) if (!temp.containsKey(k)) del.add(k);
		for (String k : del) ps.remove(k);
		update();
	}

	@Override
	public void update() {
		if (propertySet == null) return;
		String name = propertySet.getString("name");
		if (!name.isEmpty()) setName(name);
		if (is3D()) {
			// the 2D canvas never shows it in a 3D scene (the 3D view does)
			setBounds(0, 0, 0, 0);
		} else {
			setBounds(propertySet.getFloat("x"), propertySet.getFloat("y"), Math.max(1, propertySet.getFloat("width")), Math.max(1, propertySet.getFloat("height")));
		}
		if (getStage() != null) setZIndex(Math.max(0, propertySet.getInt("z")));
	}

	@Override
	public String getTypeName() {
		return TYPE;
	}

	public boolean is3D() {
		return editor.is3DScene();
	}

	public LibgdxEditor editor() {
		return editor;
	}

	// ------------------------------------------------------------------ grid

	private float f(String k, float def) {
		try {
			String s = propertySet.getString(k);
			return s == null || s.isEmpty() ? def : Float.parseFloat(s);
		} catch (Exception e) {
			return def;
		}
	}

	/** Corner of the area in grid units: (x, y) in 2D, (x, z) in 3D. */
	public float originX() {
		return f("x", 0);
	}

	public float originY() {
		return is3D() ? f("z", 0) : f("y", 0);
	}

	/** Height of the ground (3D). */
	public float groundY() {
		return is3D() ? f("y", 0) : 0;
	}

	private String signature() {
		return propertySet.getString("Cell Shape") + "|" + originX() + "|" + originY() + "|" + f("width", 1) + "|" + f("height", 1) + "|" + f("Cell Width", 32)
				+ "|" + f("Cell Height", 32) + "|" + f("Agent Size", 0) + "|" + propertySet.getString("Diagonal") + "|" + propertySet.getString("Cut Corners")
				+ "|" + propertySet.getString("Cells") + "|" + is3D();
	}

	/** The grid of the current properties (rebuilt when they change; painting edits it in place). */
	public NavGrid grid() {
		if (propertySet == null) return null;
		String sig = signature();
		if (grid == null || !sig.equals(gridSig)) {
			int shape = "HEX".equalsIgnoreCase(propertySet.getString("Cell Shape")) ? NavGrid.HEX : NavGrid.SQUARE;
			float cw = Math.max(is3D() ? 0.05f : 2f, f("Cell Width", 32)), ch = Math.max(is3D() ? 0.05f : 2f, f("Cell Height", 32));
			// at most 400 x 400 cells in the editor (bigger grids are still saved, only drawn coarser)
			float w = Math.max(cw, f("width", 1)), h = Math.max(ch, f("height", 1));
			NavGrid g = new NavGrid(shape, originX(), originY(), w, h, cw, ch);
			g.diagonal = !"false".equals(propertySet.getString("Diagonal"));
			g.cutCorners = "true".equals(propertySet.getString("Cut Corners"));
			g.groundY = groundY();
			g.decode(propertySet.getString("Cells"));
			g.setAgentSize(f("Agent Size", 0));
			grid = g;
			gridSig = sig;
		}
		return grid;
	}

	/** Stores the painted cells in the "Cells" property (the grid stays as it is). Returns the old value. */
	public String storeCells() {
		String old = propertySet.getString("Cells");
		if (grid == null) return old;
		String v = grid.encode();
		propertySet.put("Cells", v);
		gridSig = signature();
		return old;
	}

	// ------------------------------------------------------------------ 2D canvas

	/** Size of the badge in screen pixels. */
	private float badgeSize() {
		return Math.max(36f, Math.min(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()) * 0.06f);
	}

	private float zoom() {
		return editor.getCamera() instanceof OrthographicCamera ? ((OrthographicCamera) editor.getCamera()).zoom : 1f;
	}

	/** True when a stage point is on the badge (bottom-left corner) of this grid. 2D scenes. */
	public boolean badgeHit(float x, float y) {
		if (is3D() || !isVisible()) return false;
		float s = badgeSize() * zoom();
		float bx = getX(), by = getY() - s * 0.15f;
		return x >= bx - s * 0.2f && x <= bx + s * 1.2f && y >= by - s * 0.2f && y <= by + s * 1.2f;
	}

	@Override
	public Actor hit(float x, float y, boolean touchable) {
		// only the badge selects the grid: taps inside its area reach the elements below
		if (touchable && getTouchable() != Touchable.enabled) return null;
		float s = badgeSize() * zoom();
		float by = -s * 0.15f;
		if (x >= -s * 0.2f && x <= s * 1.2f && y >= by - s * 0.2f && y <= by + s * 1.2f) return this;
		return null;
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		// drawn on top of everything by drawOverlay (called by the editor after the elements)
	}

	/** Every NavGrid of the editor: outline + badge, cells of the selected one. 2D canvas, after the elements are drawn. */
	public static void drawAll(LibgdxEditor ed, ShapeRenderer sr, Batch batch) {
		ArrayList<EditorNavGridItem> list = null;
		for (Actor a : ed.getActors())
			if (a instanceof EditorNavGridItem && a.isVisible()) {
				if (list == null) list = new ArrayList<>();
				list.add((EditorNavGridItem) a);
			}
		if (list == null) return;
		OrthographicCamera cam = (OrthographicCamera) ed.getCamera();
		float hw = cam.viewportWidth * cam.zoom * 0.5f, hh = cam.viewportHeight * cam.zoom * 0.5f;
		float vx0 = cam.position.x - hw, vy0 = cam.position.y - hh, vx1 = cam.position.x + hw, vy1 = cam.position.y + hh;
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		sr.setProjectionMatrix(cam.combined);
		for (EditorNavGridItem it : list) {
			boolean sel = ed.getSelectedActor() == it || ed.navPaint.item == it;
			NavGrid g = it.grid();
			if (g == null) continue;
			if (sel) drawCells(sr, g, 1f, vx0, vy0, vx1, vy1, cam.zoom);
			it.drawFrame(sr, g, sel, cam.zoom);
		}
		batch.setProjectionMatrix(cam.combined);
		batch.begin();
		for (EditorNavGridItem it : list) {
			float s = it.badgeSize() * cam.zoom;
			batch.setColor(1, 1, 1, ed.getSelectedActor() == it ? 1f : 0.85f);
			batch.draw(icon, it.getX() + s * 0.12f, it.getY() - s * 0.15f + s * 0.12f, s * 0.76f, s * 0.76f);
		}
		batch.setColor(1, 1, 1, 1);
		batch.end();
	}

	private void drawFrame(ShapeRenderer sr, NavGrid g, boolean sel, float zoom) {
		float x = g.x0, y = g.y0, w = g.areaWidth(), h = g.areaHeight();
		float s = badgeSize() * zoom;
		sr.begin(ShapeRenderer.ShapeType.Filled);
		// badge background
		sr.setColor(sel ? 0.15f : 0.08f, sel ? 0.45f : 0.3f, sel ? 0.6f : 0.4f, 0.92f);
		sr.rect(x, y - s * 0.15f, s, s);
		// brush / box preview
		if (!Float.isNaN(cursorX)) {
			sr.setColor(1f, 1f, 1f, 0.18f);
			sr.circle(cursorX, cursorY, Math.max(cursorR, 2f * zoom), 40);
		}
		if (!Float.isNaN(boxX0)) {
			sr.setColor(1f, 0.85f, 0.2f, 0.22f);
			sr.rect(Math.min(boxX0, boxX1), Math.min(boxY0, boxY1), Math.abs(boxX1 - boxX0), Math.abs(boxY1 - boxY0));
		}
		sr.end();
		Gdx.gl.glLineWidth(sel ? 3f : 2f);
		sr.begin(ShapeRenderer.ShapeType.Line);
		sr.setColor(OUTLINE.r, OUTLINE.g, OUTLINE.b, sel ? 1f : 0.55f);
		// dashed outline when not selected
		if (sel) sr.rect(x, y, w, h);
		else dashedRect(sr, x, y, w, h, 14f * zoom);
		if (!Float.isNaN(cursorX)) {
			sr.setColor(1f, 1f, 1f, 0.8f);
			sr.circle(cursorX, cursorY, Math.max(cursorR, 2f * zoom), 40);
		}
		sr.end();
		Gdx.gl.glLineWidth(1f);
	}

	private static void dashedRect(ShapeRenderer sr, float x, float y, float w, float h, float dash) {
		dashedLine(sr, x, y, x + w, y, dash);
		dashedLine(sr, x + w, y, x + w, y + h, dash);
		dashedLine(sr, x + w, y + h, x, y + h, dash);
		dashedLine(sr, x, y + h, x, y, dash);
	}

	private static void dashedLine(ShapeRenderer sr, float x0, float y0, float x1, float y1, float dash) {
		float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1e-3f) return;
		int n = Math.max(1, Math.min(400, (int) (len / dash)));
		for (int i = 0; i < n; i += 2) {
			float a = i / (float) n, b = Math.min(1f, (i + 1) / (float) n);
			sr.line(x0 + dx * a, y0 + dy * a, x0 + dx * b, y0 + dy * b);
		}
	}

	/**
	 * Draws the cells inside a view rectangle (grid units): walls red, cells too close to a wall for the agent size light
	 * red, slow cells orange (stronger = slower), then the cell lines. Lines are skipped when the cells are tiny on screen.
	 */
	public static void drawCells(ShapeRenderer sr, NavGrid g, float alpha, float vx0, float vy0, float vx1, float vy1, float unitsPerPixel) {
		float[] hex = new float[12];
		int c0 = Math.max(0, (int) Math.floor((vx0 - g.x0) / g.cellW) - 1), c1 = Math.min(g.cols - 1, (int) Math.ceil((vx1 - g.x0) / g.cellW) + 1);
		int r0, r1;
		if (g.shape == NavGrid.SQUARE) {
			r0 = Math.max(0, (int) Math.floor((vy0 - g.y0) / g.cellH) - 1);
			r1 = Math.min(g.rows - 1, (int) Math.ceil((vy1 - g.y0) / g.cellH) + 1);
		} else {
			r0 = Math.max(0, (int) Math.floor((vy0 - g.y0 - g.hexRv()) / g.cellH) - 1);
			r1 = Math.min(g.rows - 1, (int) Math.ceil((vy1 - g.y0) / g.cellH) + 1);
		}
		if (c0 > c1 || r0 > r1) return;
		sr.begin(ShapeRenderer.ShapeType.Filled);
		for (int r = r0; r <= r1; r++)
			for (int c = c0; c <= c1; c++) {
				int i = g.index(c, r);
				boolean wall = g.isWall(i), near = !wall && !g.walkable(i);
				int cost = g.costOf(i);
				if (!wall && !near && cost <= 1) continue;
				if (wall) sr.setColor(0.95f, 0.2f, 0.2f, 0.55f * alpha);
				else if (near) sr.setColor(0.95f, 0.4f, 0.35f, 0.2f * alpha);
				else sr.setColor(0.98f, 0.62f, 0.12f, (0.12f + 0.06f * cost) * alpha);
				if (g.shape == NavGrid.SQUARE) {
					sr.rect(g.x0 + c * g.cellW, g.y0 + r * g.cellH, g.cellW, g.cellH);
				} else {
					g.hexCorners(c, r, hex);
					float cx = g.centerX(c, r), cy = g.centerY(c, r);
					for (int v = 0; v < 6; v++) {
						int w = (v + 1) % 6;
						sr.triangle(cx, cy, hex[v * 2], hex[v * 2 + 1], hex[w * 2], hex[w * 2 + 1]);
					}
				}
			}
		sr.end();
		// lines only when a cell is at least ~6 px on screen
		if (Math.min(g.cellW, g.cellH) / Math.max(1e-6f, unitsPerPixel) < 6f) return;
		sr.begin(ShapeRenderer.ShapeType.Line);
		sr.setColor(0.4f, 0.85f, 1f, 0.32f * alpha);
		if (g.shape == NavGrid.SQUARE) {
			float ya = g.y0 + r0 * g.cellH, yb = g.y0 + (r1 + 1) * g.cellH, xa = g.x0 + c0 * g.cellW, xb = g.x0 + (c1 + 1) * g.cellW;
			for (int c = c0; c <= c1 + 1; c++) sr.line(g.x0 + c * g.cellW, ya, g.x0 + c * g.cellW, yb);
			for (int r = r0; r <= r1 + 1; r++) sr.line(xa, g.y0 + r * g.cellH, xb, g.y0 + r * g.cellH);
		} else {
			for (int r = r0; r <= r1; r++)
				for (int c = c0; c <= c1; c++) {
					g.hexCorners(c, r, hex);
					// three edges per cell are enough (the neighbours draw the others), plus the borders
					int edges = (c == c0 || r == r0 || c == c1 || r == r1) ? 6 : 3;
					for (int v = 0; v < edges; v++) {
						int w = (v + 1) % 6;
						sr.line(hex[v * 2], hex[v * 2 + 1], hex[w * 2], hex[w * 2 + 1]);
					}
				}
		}
		sr.end();
	}

	/** The NavGrid element whose badge is under a stage point (2D canvas), or null. */
	public static EditorNavGridItem badgeAt(LibgdxEditor ed, float x, float y) {
		for (Actor a : ed.getActors())
			if (a instanceof EditorNavGridItem && ((EditorNavGridItem) a).badgeHit(x, y)) return (EditorNavGridItem) a;
		return null;
	}
}
