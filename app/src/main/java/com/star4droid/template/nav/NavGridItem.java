package com.star4droid.template.nav;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Array;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.star2d.ElementDefs.NavGridDef;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;

/**
 * The NAVGRID element at run time: builds the {@link NavGrid} from the painted cells, adds its obstacles (and the static
 * bodies when asked) once every element exists, and draws the grid when "Show In Game" is on. Invisible otherwise.
 */
public class NavGridItem extends Actor implements PlayerItem {
	StageImp stage;
	NavGridDef def;
	ElementEvent elementEvent;
	private final ChildsHolder childsHolder = new ChildsHolder(this);
	private ItemScript itemScript;
	private NavGrid grid;
	private boolean resolved, is3D;
	private static ShapeRenderer shapes;
	private static Object shapesApp;

	public NavGridItem(StageImp s) {
		stage = s;
	}

	public static NavGridItem create(StageImp stage, NavGridDef def, ElementEvent ev) {
		NavGridItem it = new NavGridItem(stage);
		it.def = def;
		it.elementEvent = ev;
		it.setup();
		return it;
	}

	private void setup() {
		setName(def.name);
		setTouchable(Touchable.disabled);
		is3D = stage != null && stage.is3D();
		int shape = "HEX".equalsIgnoreCase(def.Cell_Shape) ? NavGrid.HEX : NavGrid.SQUARE;
		float ox = def.x, oy = is3D ? def.z : def.y;
		grid = new NavGrid(shape, ox, oy, Math.max(1, def.width), Math.max(1, def.height), def.Cell_Width, def.Cell_Height);
		grid.groundY = is3D ? def.y : 0;
		grid.diagonal = def.Diagonal;
		grid.cutCorners = def.Cut_Corners;
		grid.decode(def.Cells);
		grid.setAgentSize(def.Agent_Size);
		float k = is3D ? 1 : StageImp.WORLD_SCALE;
		setBounds(def.x * k, (is3D ? def.z : def.y) * k, def.width * k, def.height * k);
		setVisible(def.Visible);
		if (stage != null) {
			NavWorld w = Nav.world(stage);
			if (w != null) w.addGrid(def.name, grid, this);
			stage.addActor(this);
			if (stage.onCreateCalled()) {
				if (elementEvent != null) elementEvent.onBodyCreated(this);
				if (getScript() != null) getScript().bodyCreated();
			}
		}
	}

	/** The path finding grid of this element. */
	public NavGrid grid() {
		return grid;
	}

	/** Adds the obstacles of the properties (once every element of the scene exists). */
	public void resolve() {
		if (resolved || stage == null) return;
		resolved = true;
		NavWorld w = Nav.world(stage);
		if (w == null) return;
		String obs = def.Obstacles == null ? "" : def.Obstacles;
		for (String part : obs.split("[()]")) {
			String n = part.trim();
			if (n.isEmpty() || n.equals(def.name)) continue;
			PlayerItem it = stage.findItem(n);
			if (it == null && stage.hasWorld3D()) it = stage.world3d().find(n);
			if (it != null) w.addObstacle(grid, it);
		}
		if (def.Use_Static_Bodies) addStaticBodies(w);
	}

	private void addStaticBodies(NavWorld w) {
		if (is3D) {
			if (!stage.hasWorld3D()) return;
			for (com.star4droid.template.d3.Object3D o : stage.world3d().objects) {
				if (o.isStaticBody() && o.terrain() == null && !"WATER".equals(o.getDef().Mesh) && !"PLANE".equals(o.getDef().Mesh)) w.addObstacle(grid, o);
			}
			return;
		}
		if (stage.world == null) return;
		Array<Body> bodies = new Array<Body>();
		stage.world.getBodies(bodies);
		java.util.HashSet<PlayerItem> seen = new java.util.HashSet<PlayerItem>();
		for (Body b : bodies) {
			if (b.getType() != BodyDef.BodyType.StaticBody) continue;
			for (Fixture f : b.getFixtureList()) {
				if (f.isSensor()) continue;
				Object u = f.getUserData();
				if (u instanceof PlayerItem && seen.add((PlayerItem) u)) w.addObstacle(grid, (PlayerItem) u);
			}
		}
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		if (!resolved) resolve();
		if (!def.Show_In_Game || is3D || grid == null) return;
		if (shapes == null || shapesApp != Gdx.app) {
			shapes = new ShapeRenderer();
			shapesApp = Gdx.app;
		}
		batch.end();
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		shapes.setProjectionMatrix(batch.getProjectionMatrix());
		shapes.setTransformMatrix(batch.getTransformMatrix());
		float k = StageImp.WORLD_SCALE;
		drawCells(shapes, grid, k, parentAlpha);
		batch.begin();
	}

	/** Draws the cells (blocked red, costly orange, the rest outlined) with a scale (world units per pixel). */
	public static void drawCells(ShapeRenderer sr, NavGrid g, float k, float alpha) {
		float[] hex = new float[12];
		sr.begin(ShapeRenderer.ShapeType.Filled);
		for (int r = 0; r < g.rows; r++)
			for (int c = 0; c < g.cols; c++) {
				int i = g.index(c, r);
				boolean wall = g.blockedRaw(i), near = !wall && !g.walkable(i);
				int cost = g.costOf(i);
				if (!wall && !near && cost <= 1) continue;
				if (wall) sr.setColor(0.95f, 0.22f, 0.2f, 0.45f * alpha);
				else if (near) sr.setColor(0.95f, 0.45f, 0.35f, 0.18f * alpha);
				else sr.setColor(0.95f, 0.65f, 0.15f, (0.12f + 0.05f * cost) * alpha);
				if (g.shape == NavGrid.SQUARE) {
					sr.rect((g.x0 + c * g.cellW) * k, (g.y0 + r * g.cellH) * k, g.cellW * k, g.cellH * k);
				} else {
					g.hexCorners(c, r, hex);
					float cx = g.centerX(c, r) * k, cy = g.centerY(c, r) * k;
					for (int v = 0; v < 6; v++) {
						int w = (v + 1) % 6;
						sr.triangle(cx, cy, hex[v * 2] * k, hex[v * 2 + 1] * k, hex[w * 2] * k, hex[w * 2 + 1] * k);
					}
				}
			}
		sr.end();
		sr.begin(ShapeRenderer.ShapeType.Line);
		sr.setColor(0.4f, 0.85f, 1f, 0.35f * alpha);
		if (g.shape == NavGrid.SQUARE) {
			for (int c = 0; c <= g.cols; c++) sr.line((g.x0 + c * g.cellW) * k, g.y0 * k, (g.x0 + c * g.cellW) * k, (g.y0 + g.rows * g.cellH) * k);
			for (int r = 0; r <= g.rows; r++) sr.line(g.x0 * k, (g.y0 + r * g.cellH) * k, (g.x0 + g.cols * g.cellW) * k, (g.y0 + r * g.cellH) * k);
		} else {
			for (int r = 0; r < g.rows; r++)
				for (int c = 0; c < g.cols; c++) {
					g.hexCorners(c, r, hex);
					for (int v = 0; v < 6; v++) {
						int w = (v + 1) % 6;
						sr.line(hex[v * 2] * k, hex[v * 2 + 1] * k, hex[w * 2] * k, hex[w * 2 + 1] * k);
					}
				}
		}
		sr.end();
	}

	// ------------------------------------------------------------------ PlayerItem

	@Override
	public void update() {
		if (!resolved) resolve();
	}

	@Override
	public Body getBody() {
		return null;
	}

	@Override
	public ChildsHolder getChildsHolder() {
		return childsHolder;
	}

	@Override
	public void setScript(ItemScript script) {
		itemScript = script;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends ItemScript> T getScript() {
		return (T) itemScript;
	}

	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties() {
		return def;
	}

	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}

	@Override
	public PlayerItem getClone(String newName) {
		return create(stage, def.getClone(newName), elementEvent);
	}

	@Override
	public float getBodyX() {
		return getX() + getWidth() * 0.5f;
	}

	@Override
	public float getBodyY() {
		return getY() + getHeight() * 0.5f;
	}
}
