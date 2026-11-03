package com.star4droid.star2d.editor.ui.custom;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.CustomItem;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.template.Utils.Utils;

import java.util.ArrayList;

import static com.star4droid.star2d.editor.utils.Lang.getTrans;

/**
 * Custom Collider Editor: edits the polygon of a Custom Body on top of its picture.
 * Tap an empty spot to add a point (on a line it is inserted between its two points, otherwise after the selected one),
 * tap a point to select it, drag it to move it. Works in portrait (buttons under the picture) and landscape (buttons on the side).
 * Original editor by Mutwakil Souliman, improved by Annas Osman (Star4Droid); redesigned later with one touch board.
 */
public class CustomColliderEditor extends VisDialog {
	private static final Color BG = new Color(0x17191dff), PANEL = new Color(0x202328ff), BOARD = new Color(0x0e1013ff);
	private static final Color ACCENT = new Color(0x2196f3ff), DANGER = new Color(0xef5350ff), OK = new Color(0x43a047ff), GREY = new Color(0x3a4048ff);

	private final TestApp app;
	private CustomItem customBody;
	private Drawable picture;
	private final Board board = new Board();
	private final ArrayList<float[]> points = new ArrayList<>(); // normalised: x 0..1 left to right, y 0..1 bottom to top
	private final ArrayList<ArrayList<float[]>> history = new ArrayList<>();
	private int selected = -1;
	private String original = "";
	private final VisLabel countLabel = new VisLabel(""), selLabel = new VisLabel(""), hintLabel = new VisLabel(""), warnLabel = new VisLabel("");
	private VisTextButton addPoint, deletePoint, undo, save, cancel;
	private final Table root = new Table();
	private int builtLandscape = -1;
	private String lastStatus = "";
	private final ShapeRenderer renderer = new ShapeRenderer();
	private final GlyphLayout layout = new GlyphLayout();

	public CustomColliderEditor(TestApp app) {
		super(getTrans("customColliderTitle"));
		this.app = app;
		setFillParent(true);
		reset(); // no default cell padding of VisDialog
		hintLabel.setText(txt("cc_hint", "Tap an empty spot to add a point, tap a point to select it, drag it to move it"));
		hintLabel.setWrap(true);
		hintLabel.setColor(0.69f, 0.71f, 0.75f, 1f);
		warnLabel.setColor(DANGER);
		warnLabel.setWrap(true);
		selLabel.setColor(0.69f, 0.71f, 0.75f, 1f);
		addPoint = button(getTrans("addPoint"), ACCENT, () -> addAtDefault());
		deletePoint = button(getTrans("deletePoint"), DANGER, () -> deleteSelected());
		undo = button(txt("cc_undo", "Undo"), GREY, () -> undo());
		save = button(getTrans("save"), OK, () -> {
			if (points.size() > 0 && points.size() < 3) return; // the status line says why
			save();
			hide();
		});
		cancel = button(getTrans("cancel"), GREY, () -> {
			if (!changed()) { hide(); return; }
			new ConfirmDialog(getTrans("cancel"), getTrans("cancelConfirm"), ok -> { if (ok) hide(); }).show(getStage());
		});
		board.addListener(new InputListener() {
			private int dragging = -1;
			private float offX, offY;

			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				if (pointer != 0) return false;
				float[] r = board.rect();
				int hit = nearest(x, y, 30f * unit());
				if (hit >= 0) {
					pushHistory();
					selected = hit;
					dragging = hit;
					float[] p = points.get(hit);
					offX = r[0] + p[0] * r[2] - x;
					offY = r[1] + p[1] * r[3] - y;
					return true;
				}
				if (x < r[0] || y < r[1] || x > r[0] + r[2] || y > r[1] + r[3]) return true; // outside the picture: nothing
				pushHistory();
				float nx = (x - r[0]) / r[2], ny = (y - r[1]) / r[3];
				int at = insertionIndex(x, y);
				points.add(at, new float[]{clamp(nx), clamp(ny)});
				selected = at;
				dragging = at;
				offX = 0;
				offY = 0;
				return true;
			}

			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer) {
				if (dragging < 0 || dragging >= points.size()) return;
				float[] r = board.rect();
				float[] p = points.get(dragging);
				p[0] = clamp((x + offX - r[0]) / r[2]);
				p[1] = clamp((y + offY - r[1]) / r[3]);
			}

			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
				dragging = -1;
			}
		});
		add(root).grow();
	}

	// -------------------------------------------------------------------- ui

	private static String txt(String key, String def) {
		String s = getTrans(key);
		return s == null || s.equals(key) ? def : s;
	}

	private static float unit() {
		return Math.max(1f, Gdx.graphics.getDensity());
	}

	private VisTextButton button(String text, Color color, Runnable action) {
		VisTextButton.VisTextButtonStyle base = VisUI.getSkin().get(VisTextButton.VisTextButtonStyle.class);
		VisTextButton.VisTextButtonStyle style = new VisTextButton.VisTextButtonStyle(base);
		style.up = VisUI.getSkin().newDrawable("white", color);
		style.over = style.up;
		style.down = VisUI.getSkin().newDrawable("white", new Color(color).mul(0.65f, 0.65f, 0.65f, 1f));
		style.disabled = VisUI.getSkin().newDrawable("white", new Color(0.2f, 0.22f, 0.25f, 1f));
		style.fontColor = Color.WHITE;
		VisTextButton b = new VisTextButton(text, style);
		b.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				action.run();
			}
		});
		return b;
	}

	private void rebuild(boolean land) {
		builtLandscape = land ? 1 : 0;
		root.clear();
		root.setBackground(VisUI.getSkin().newDrawable("white", BG));
		float u = unit(), pad = 8f * u, bh = 46f * u;
		Table info = new Table();
		info.add(countLabel).left().growX().row();
		info.add(selLabel).left().growX().row();
		info.add(warnLabel).left().growX().row();
		if (land) {
			Table side = new Table();
			side.setBackground(VisUI.getSkin().newDrawable("white", PANEL));
			side.pad(pad);
			side.add(info).growX().row();
			side.add(hintLabel).growX().padTop(pad).row();
			side.add().grow().row();
			side.defaults().growX().height(bh).padTop(pad);
			side.add(addPoint).row();
			side.add(deletePoint).row();
			side.add(undo).row();
			side.add(save).row();
			side.add(cancel).row();
			root.add(board).grow().pad(pad);
			root.add(side).width(Math.max(190f * u, Gdx.graphics.getWidth() * 0.26f)).growY();
		} else {
			Table top = new Table();
			top.setBackground(VisUI.getSkin().newDrawable("white", PANEL));
			top.pad(pad);
			top.add(info).growX().row();
			top.add(hintLabel).growX().padTop(pad / 2f).row();
			Table bottom = new Table();
			bottom.setBackground(VisUI.getSkin().newDrawable("white", PANEL));
			bottom.pad(pad);
			bottom.defaults().growX().height(bh).pad(pad / 2f);
			bottom.add(addPoint);
			bottom.add(deletePoint);
			bottom.add(undo).row();
			bottom.add(cancel).colspan(1);
			bottom.add(save).colspan(2).row();
			root.add(top).growX().row();
			root.add(board).grow().pad(pad).row();
			root.add(bottom).growX();
		}
		root.invalidateHierarchy();
	}

	@Override
	public void act(float delta) {
		super.act(delta);
		Stage st = getStage();
		if (st != null) {
			boolean land = st.getWidth() > st.getHeight();
			if (builtLandscape != (land ? 1 : 0)) rebuild(land);
		}
		String status = points.size() + "|" + selected + "|" + (selected >= 0 && selected < points.size() ? points.get(selected)[0] + "," + points.get(selected)[1] : "") + "|" + history.size();
		if (!status.equals(lastStatus)) {
			lastStatus = status;
			countLabel.setText(txt("cc_points", "Points") + " : " + points.size());
			if (selected >= 0 && selected < points.size()) {
				float[] p = points.get(selected);
				selLabel.setText(txt("cc_selected", "Selected") + " #" + (selected + 1) + "   x " + Math.round(p[0] * 100) + "%   y " + Math.round((1 - p[1]) * 100) + "%");
			} else selLabel.setText(txt("cc_none_selected", "No point selected"));
			boolean few = points.size() > 0 && points.size() < 3;
			warnLabel.setText(few ? txt("cc_need_three", "A shape needs at least 3 points") : "");
			deletePoint.setDisabled(selected < 0);
			undo.setDisabled(history.isEmpty());
			save.setDisabled(few);
		}
	}

	// ------------------------------------------------------------------ points

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private int nearest(float x, float y, float radius) {
		float[] r = board.rect();
		int best = -1;
		float bd = radius * radius;
		for (int i = 0; i < points.size(); i++) {
			float dx = r[0] + points.get(i)[0] * r[2] - x, dy = r[1] + points.get(i)[1] * r[3] - y;
			float d = dx * dx + dy * dy;
			if (d <= bd) { bd = d; best = i; }
		}
		return best;
	}

	/** Where a new point goes: between the two points of the line that was tapped, otherwise after the selected point (or at the end). */
	private int insertionIndex(float x, float y) {
		float[] r = board.rect();
		int n = points.size();
		if (n >= 2) {
			float best = (18f * unit()) * (18f * unit());
			int at = -1;
			for (int i = 0; i < n; i++) {
				float[] a = points.get(i), b = points.get((i + 1) % n);
				float d = segDist2(x, y, r[0] + a[0] * r[2], r[1] + a[1] * r[3], r[0] + b[0] * r[2], r[1] + b[1] * r[3]);
				if (d < best) { best = d; at = i + 1; }
			}
			if (at >= 0) return at;
		}
		return selected >= 0 && selected < n ? selected + 1 : n;
	}

	private static float segDist2(float px, float py, float ax, float ay, float bx, float by) {
		float dx = bx - ax, dy = by - ay;
		float len2 = dx * dx + dy * dy;
		float t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
		float cx = ax + t * dx - px, cy = ay + t * dy - py;
		return cx * cx + cy * cy;
	}

	private void pushHistory() {
		ArrayList<float[]> copy = new ArrayList<>();
		for (float[] p : points) copy.add(new float[]{p[0], p[1]});
		history.add(copy);
		if (history.size() > 40) history.remove(0);
	}

	private void undo() {
		if (history.isEmpty()) return;
		ArrayList<float[]> last = history.remove(history.size() - 1);
		points.clear();
		points.addAll(last);
		selected = Math.min(selected, points.size() - 1);
	}

	/** The Add button: a new point in the middle of the line after the selected point (or in the middle of the picture). */
	private void addAtDefault() {
		pushHistory();
		int n = points.size();
		float[] np;
		int at;
		if (n >= 2 && selected >= 0) {
			float[] a = points.get(selected), b = points.get((selected + 1) % n);
			np = new float[]{(a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f};
			at = selected + 1;
		} else {
			float o = Math.min(0.4f, 0.1f * n);
			np = new float[]{clamp(0.5f + (n % 2 == 0 ? o : -o)), clamp(0.5f + (n % 3 == 0 ? o : -o))};
			at = n;
		}
		points.add(at, np);
		selected = at;
	}

	private void deleteSelected() {
		if (selected < 0 || selected >= points.size()) return;
		pushHistory();
		points.remove(selected);
		selected = points.isEmpty() ? -1 : Math.min(selected, points.size() - 1);
	}

	// ------------------------------------------------------------------ load / save

	private void load() {
		customBody = (CustomItem) app.getEditor().getSelectedActor();
		picture = null;
		try {
			picture = customBody.getDrawable() != null ? customBody.getDrawable() : VisUI.getSkin().getDrawable("logo");
		} catch (Exception e) {
			picture = VisUI.getSkin().getDrawable("logo");
		}
		points.clear();
		history.clear();
		selected = -1;
		lastStatus = "";
		original = customBody.getPropertySet().getString("Points");
		setPointsFromString(original);
		if (!points.isEmpty()) selected = 0;
		original = getPointsStr();
		builtLandscape = -1;
	}

	private boolean changed() {
		return !original.equals(getPointsStr());
	}

	public void save() {
		String pointsStr = getPointsStr();
		EditorAction.propertiesChanged(
				app.getEditor(),
				app.getEditor().getSelectedActor().getName(),
				getTrans("customColliderMessage"),
				new String[]{"Points", pointsStr},
				new String[]{"Points", customBody.getPropertySet().getString("Points")});
		customBody.getPropertySet().put("Points", pointsStr);
		customBody.update();
	}

	@Override
	public VisDialog show(Stage stage) {
		toFront();
		load();
		return super.show(stage);
	}

	/** "x,y-x,y..." with x, y between 0 and 1 and y measured from the top (the format the game reads). */
	public String getPointsStr() {
		StringBuilder result = new StringBuilder();
		for (float[] p : points) {
			if (result.length() > 0) result.append("-");
			result.append(Math.max(p[0], 0)).append(",").append(Math.max(1 - p[1], 0));
		}
		return result.toString();
	}

	public void setPointsFromString(String pointsStr) {
		points.clear();
		if (pointsStr == null) return;
		for (String s : pointsStr.split("-")) {
			try {
				String[] xy = s.split(",");
				points.add(new float[]{clamp(Utils.getFloat(xy[0])), clamp(1 - Utils.getFloat(xy[1]))});
			} catch (Exception ignored) {
			}
		}
	}

	public void dispose() {
		renderer.dispose();
	}

	public static interface SizeObserver {
		public void onImageUpdated(float width, float height);
	}

	// ------------------------------------------------------------------ the touch board

	private class Board extends Widget {
		/** [x, y, w, h] of the picture inside the board (local coordinates), fitted with its aspect ratio. */
		float[] rect() {
			float pw = picture != null && picture.getMinWidth() > 0 ? picture.getMinWidth() : 1f,
					ph = picture != null && picture.getMinHeight() > 0 ? picture.getMinHeight() : 1f;
			float m = 10f * unit();
			float aw = Math.max(1f, getWidth() - 2 * m), ah = Math.max(1f, getHeight() - 2 * m);
			float s = Math.min(aw / pw, ah / ph);
			float w = pw * s, h = ph * s;
			return new float[]{(getWidth() - w) / 2f, (getHeight() - h) / 2f, w, h};
		}

		@Override
		public void draw(Batch batch, float parentAlpha) {
			validate();
			Vector2 o = localToStageCoordinates(new Vector2(0, 0));
			float[] r = rect();
			float u = unit();
			Matrix4 old = new Matrix4(batch.getTransformMatrix());
			batch.setTransformMatrix(new Matrix4());
			float ix = o.x + r[0], iy = o.y + r[1];
			batch.end();
			Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
			renderer.setProjectionMatrix(batch.getProjectionMatrix());
			renderer.setTransformMatrix(new Matrix4());
			renderer.begin(ShapeRenderer.ShapeType.Filled);
			renderer.setColor(BOARD);
			renderer.rect(o.x, o.y, getWidth(), getHeight());
			// checkerboard under transparent pictures
			float cell = 12f * u;
			for (int cy = 0; cy * cell < r[3]; cy++)
				for (int cx = 0; cx * cell < r[2]; cx++) {
					renderer.setColor(((cx + cy) & 1) == 0 ? 0.16f : 0.2f, ((cx + cy) & 1) == 0 ? 0.17f : 0.21f, ((cx + cy) & 1) == 0 ? 0.19f : 0.24f, 1f);
					renderer.rect(ix + cx * cell, iy + cy * cell, Math.min(cell, r[2] - cx * cell), Math.min(cell, r[3] - cy * cell));
				}
			renderer.end();
			batch.begin();
			if (picture != null) {
				batch.setColor(1, 1, 1, parentAlpha);
				picture.draw(batch, ix, iy, r[2], r[3]);
			}
			batch.end();
			Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
			int n = points.size();
			// the shape: translucent fill + closed outline
			if (n >= 3) {
				float[] poly = new float[n * 2];
				for (int i = 0; i < n; i++) {
					poly[i * 2] = ix + points.get(i)[0] * r[2];
					poly[i * 2 + 1] = iy + points.get(i)[1] * r[3];
				}
				try {
					short[] tri = new EarClippingTriangulator().computeTriangles(poly).toArray();
					renderer.begin(ShapeRenderer.ShapeType.Filled);
					renderer.setColor(0.13f, 0.59f, 0.95f, 0.28f);
					for (int i = 0; i + 2 < tri.length; i += 3)
						renderer.triangle(poly[tri[i] * 2], poly[tri[i] * 2 + 1], poly[tri[i + 1] * 2], poly[tri[i + 1] * 2 + 1], poly[tri[i + 2] * 2], poly[tri[i + 2] * 2 + 1]);
					renderer.end();
				} catch (Throwable ignored) {
				}
			}
			renderer.begin(ShapeRenderer.ShapeType.Filled);
			renderer.setColor(1f, 1f, 1f, 0.25f);
			renderer.rect(ix, iy, r[2], 1.5f * u);
			renderer.rect(ix, iy + r[3] - 1.5f * u, r[2], 1.5f * u);
			renderer.rect(ix, iy, 1.5f * u, r[3]);
			renderer.rect(ix + r[2] - 1.5f * u, iy, 1.5f * u, r[3]);
			for (int i = 0; n >= 2 && i < n; i++) {
				if (n == 2 && i == 1) break;
				float[] a = points.get(i), b = points.get((i + 1) % n);
				boolean closing = i == n - 1;
				renderer.setColor(closing ? 1f : 0.94f, closing ? 0.6f : 0.26f, closing ? 0.2f : 0.21f, closing ? 0.75f : 1f);
				renderer.rectLine(ix + a[0] * r[2], iy + a[1] * r[3], ix + b[0] * r[2], iy + b[1] * r[3], (closing ? 2f : 3f) * u);
			}
			float rad = 15f * u;
			for (int i = 0; i < n; i++) {
				float[] p = points.get(i);
				float cx = ix + p[0] * r[2], cy = iy + p[1] * r[3];
				boolean sel = i == selected;
				if (sel) {
					renderer.setColor(1f, 1f, 1f, 0.22f);
					renderer.circle(cx, cy, rad * 1.9f, 28);
				}
				renderer.setColor(1f, 1f, 1f, 1f);
				renderer.circle(cx, cy, rad + 2f * u, 24);
				if (sel) renderer.setColor(ACCENT);
				else if (i == 0) renderer.setColor(OK);
				else renderer.setColor(DANGER);
				renderer.circle(cx, cy, rad, 24);
			}
			renderer.end();
			batch.begin();
			BitmapFont font = VisUI.getSkin().getFont("default-font");
			Color fc = new Color(font.getColor());
			font.setColor(Color.WHITE);
			for (int i = 0; i < n; i++) {
				float[] p = points.get(i);
				layout.setText(font, String.valueOf(i + 1));
				font.draw(batch, layout, ix + p[0] * r[2] - layout.width / 2f, iy + p[1] * r[3] + layout.height / 2f);
			}
			font.setColor(fc);
			batch.setColor(Color.WHITE);
			batch.setTransformMatrix(old);
		}
	}
}
