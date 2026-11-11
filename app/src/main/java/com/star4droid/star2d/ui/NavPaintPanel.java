package com.star4droid.star2d.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.NavPaint;
import com.star4droid.star2d.editor.items.EditorNavGridItem;
import com.star4droid.star2d.evo.R;

import java.util.ArrayList;

/**
 * Painter of a NavGrid (path finding grid), over the canvas or the 3D view: walls, open cells, slow ground (cost) and boxes.
 * Opened from the "Paint cells" button of the grid's properties. One finger paints, two fingers move / zoom the view (or the
 * hand button: one finger moves the view). Each stroke is one undo step.
 */
public class NavPaintPanel extends FrameLayout {
	public static NavPaintPanel current;

	final EditorShell shell;
	final Activity a;
	int tool = NavPaint.WALL, brush = 0, cost = 3;
	boolean navigate;
	String name = "";
	private ImageView undoBtn, redoBtn, navBtn;
	private LinearLayout options;
	private final ArrayList<View> toolButtons = new ArrayList<View>();
	private boolean builtLand;

	static final int[] TOOL_ICON = {R.drawable.ic_t_paint, R.drawable.ic_t_erase, R.drawable.ic_s2_water, R.drawable.ic_s2_square, R.drawable.ic_s2_select_all};
	static final String[] TOOL_KEY = {"nav_tool_wall", "nav_tool_erase", "nav_tool_slow", "nav_tool_box", "nav_tool_open_box"};
	static final String[] TOOL_EN = {"Wall", "Open", "Slow", "Wall box", "Open box"};

	/** Opens the painter for the selected NavGrid element. */
	public static void open(EditorShell shell) {
		if (current != null) return;
		final Activity c = shell.activity();
		Bridge.query(() -> {
			LibgdxEditor ed = Bridge.editor();
			Object sel = ed.getSelectedActor();
			if (!(sel instanceof EditorNavGridItem)) return null;
			if (ed.get3D() != null) {
				if (ed.get3D().edit2D) ed.get3D().setEdit2D(false);
				if (ed.get3D().terrainMode()) return null;
			}
			String err = ed.navPaint.start((EditorNavGridItem) sel);
			return err == null ? ((EditorNavGridItem) sel).getName() : null;
		}, n -> {
			if (n == null) {
				Ui.toast(c, Tr.t("nav_select_first", "Select a NavGrid element first"));
				return;
			}
			NavPaintPanel p = new NavPaintPanel(shell, n);
			current = p;
			shell.enterOverlay(p);
			Ui.toast(c, Tr.t("nav_paint_hint", "One finger paints, two fingers move and zoom the view."));
		});
	}

	NavPaintPanel(EditorShell shell, String name) {
		super(shell.activity());
		this.shell = shell;
		this.a = shell.activity();
		this.name = name;
		Bridge.gl(() -> Bridge.editor().navPaint.listener = p -> {
			final boolean gone = p.item == null;
			Ui.ui(() -> {
				if (gone) {
					if (current == NavPaintPanel.this) closeUi();
					return;
				}
				refreshTop();
			});
		});
		build();
		push();
	}

	private int dp(float v) {
		return Ui.dp(a, v);
	}

	private GradientDrawable glass(float r) {
		return Ui.rounded(0xE6202328, dp(r), 0x553A4048, dp(1));
	}

	@Override
	protected void onSizeChanged(int w, int h, int ow, int oh) {
		super.onSizeChanged(w, h, ow, oh);
		boolean land = w > h;
		if (ow > 0 && land != builtLand) post(this::build);
	}

	private void build() {
		removeAllViews();
		toolButtons.clear();
		boolean land = Ui.isLandscape(a);
		builtLand = land;
		// ---- top bar
		LinearLayout top = Ui.row(a);
		top.setGravity(Gravity.CENTER_VERTICAL);
		top.setPadding(dp(6), dp(4), dp(6), dp(4));
		top.setBackground(glass(14));
		top.setClickable(true);
		top.addView(Ui.button(a, R.drawable.ic_s2_check, Tr.t("ter_done", "Done"), true, v -> close()));
		TextView title = Ui.text(a, name, 15, Ui.TEXT);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setMaxLines(1);
		title.setPadding(dp(10), 0, dp(6), 0);
		title.setEllipsize(android.text.TextUtils.TruncateAt.END);
		top.addView(title, Ui.weight(1));
		navBtn = Ui.iconButton(a, R.drawable.ic_s2_hand, Tr.t("ter_view", "Move the view (one finger)"), v -> {
			navigate = !navigate;
			push();
			refreshTop();
			Ui.toast(a, navigate ? Tr.t("ter_view_on", "One finger moves the view") : Tr.t("nav_view_off", "One finger paints"));
		});
		undoBtn = Ui.iconButton(a, R.drawable.ic_s2_undo, Tr.t("undo", "Undo"), v -> undo());
		redoBtn = Ui.iconButton(a, R.drawable.ic_s2_redo, Tr.t("redo", "Redo"), v -> redo());
		top.addView(navBtn);
		top.addView(undoBtn);
		top.addView(redoBtn);
		top.addView(Ui.iconButton(a, R.drawable.ic_s2_more, Tr.t("more", "More"), v -> more()));
		Ui.fitIconRow(a, top, 150);
		FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
		tlp.setMargins(dp(8), dp(6), dp(8), 0);
		if (land) tlp.rightMargin = dp(8 + 300);
		addView(top, tlp);
		// ---- dock: tools + options
		LinearLayout dock = Ui.column(a);
		dock.setBackground(glass(16));
		dock.setPadding(dp(8), dp(6), dp(8), dp(8));
		dock.setClickable(true);
		dock.setOnTouchListener((v, ev) -> true);
		HorizontalScrollView hs = new HorizontalScrollView(a);
		hs.setHorizontalScrollBarEnabled(false);
		LinearLayout row = Ui.row(a);
		for (int i = 0; i < TOOL_ICON.length; i++) row.addView(toolButton(i), new LinearLayout.LayoutParams(land ? dp(56) : dp(66), ViewGroup.LayoutParams.WRAP_CONTENT));
		hs.addView(row);
		dock.addView(hs, Ui.matchWrap());
		View sep = new View(a);
		sep.setBackgroundColor(0x333A4048);
		dock.addView(sep, Ui.margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)), a, 0, 6, 0, 4));
		options = Ui.column(a);
		dock.addView(options, Ui.matchWrap());
		if (land) {
			FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
			dlp.setMargins(0, dp(6), dp(6), dp(6));
			addView(dock, dlp);
		} else {
			FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
			dlp.setMargins(dp(6), 0, dp(6), dp(6));
			addView(dock, dlp);
		}
		buildOptions();
		refreshTop();
	}

	private View toolButton(int i) {
		LinearLayout b = Ui.column(a);
		b.setGravity(Gravity.CENTER);
		b.setPadding(dp(2), dp(6), dp(2), dp(5));
		b.setBackground(Ui.toggleBg(a));
		b.addView(Ui.smallIcon(a, TOOL_ICON[i], i == NavPaint.WALL || i == NavPaint.BOX_WALL ? 0xFFEF5350 : (i == NavPaint.SLOW ? 0xFFFFA726 : Ui.TEXT), 24));
		TextView t = Ui.text(a, Tr.t(TOOL_KEY[i], TOOL_EN[i]), 11, Ui.TEXT_2);
		t.setGravity(Gravity.CENTER);
		t.setMaxLines(1);
		b.addView(t);
		b.setSelected(i == tool);
		final int k = i;
		b.setOnClickListener(v -> {
			tool = k;
			for (int j = 0; j < toolButtons.size(); j++) toolButtons.get(j).setSelected(j == tool);
			if (navigate) {
				navigate = false;
				refreshTop();
			}
			push();
			buildOptions();
		});
		toolButtons.add(b);
		return b;
	}

	private void buildOptions() {
		options.removeAllViews();
		boolean box = tool == NavPaint.BOX_WALL || tool == NavPaint.BOX_ERASE;
		if (!box) options.addView(slider(Tr.t("nav_brush", "Brush"), 0, 8, brush, v -> brush = v, v -> v == 0 ? Tr.t("nav_one_cell", "1 cell") : (v * 2 + 1) + "×"));
		if (tool == NavPaint.SLOW) options.addView(slider(Tr.t("nav_cost", "Slowness"), 2, 9, cost, v -> cost = v, v -> "×" + v));
		String hint;
		switch (tool) {
			case NavPaint.ERASE: hint = Tr.t("nav_hint_erase", "Opens cells: walkers may pass again (also removes slow ground)."); break;
			case NavPaint.SLOW: hint = Tr.t("nav_hint_slow", "Slow ground (mud, water, grass): paths go around it unless the detour is longer."); break;
			case NavPaint.BOX_WALL: hint = Tr.t("nav_hint_box", "Drag a rectangle: every cell inside becomes a wall."); break;
			case NavPaint.BOX_ERASE: hint = Tr.t("nav_hint_open_box", "Drag a rectangle: every cell inside opens."); break;
			default: hint = Tr.t("nav_hint_wall", "Paint the walls: walkers never enter red cells.");
		}
		TextView t = Ui.text(a, hint, 12, Ui.TEXT_3);
		t.setPadding(dp(4), dp(4), dp(4), dp(2));
		options.addView(t);
	}

	interface I {
		void set(int v);
	}

	interface L {
		String label(int v);
	}

	private View slider(String label, int min, int max, int value, I f, L text) {
		LinearLayout row = Ui.row(a);
		row.setGravity(Gravity.CENTER_VERTICAL);
		TextView l = Ui.text(a, label, 12, Ui.TEXT_2);
		l.setMaxLines(1);
		row.addView(l, new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
		SeekBar sb = new SeekBar(a);
		sb.setMax(max - min);
		sb.setProgress(value - min);
		sb.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
		sb.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
		row.addView(sb, Ui.weight(1));
		TextView vt = Ui.text(a, text.label(value), 12, Ui.TEXT);
		vt.setGravity(Gravity.END);
		row.addView(vt, new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT));
		sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar s, int p, boolean user) {
				if (!user) return;
				f.set(min + p);
				vt.setText(text.label(min + p));
				push();
			}

			@Override
			public void onStartTrackingTouch(SeekBar s) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar s) {
			}
		});
		row.setPadding(dp(2), dp(2), dp(2), dp(2));
		return row;
	}

	private void refreshTop() {
		if (undoBtn == null) return;
		refreshCounts();
		navBtn.setSelected(navigate);
		navBtn.setImageTintList(ColorStateList.valueOf(navigate ? Ui.ACCENT : Ui.TEXT));
	}

	/** Sends the settings to the painter (GL thread). */
	void push() {
		final int t = tool, b = brush, c = cost;
		final boolean nav = navigate;
		Bridge.gl(() -> {
			NavPaint p = Bridge.editor().navPaint;
			p.tool = t;
			p.brush = b;
			p.cost = c;
			p.navigate = nav;
		});
	}

	private void undo() {
		Bridge.query(() -> {
			LibgdxEditor ed = Bridge.editor();
			NavPaint p = ed.navPaint;
			if (p.strokes <= 0 || !ed.canUndo()) return false;
			ed.undo();
			p.strokes--;
			p.undone++;
			ed.afterEdit3D();
			return true;
		}, ok -> refreshCounts());
	}

	private void redo() {
		Bridge.query(() -> {
			LibgdxEditor ed = Bridge.editor();
			NavPaint p = ed.navPaint;
			if (p.undone <= 0 || !ed.canRedo()) return false;
			ed.redo();
			p.undone--;
			p.strokes++;
			ed.afterEdit3D();
			return true;
		}, ok -> refreshCounts());
	}

	private void refreshCounts() {
		Bridge.query(() -> new int[]{Bridge.editor().navPaint.strokes, Bridge.editor().navPaint.undone}, r -> {
			if (r == null || undoBtn == null) return;
			undoBtn.setEnabled(r[0] > 0);
			undoBtn.setAlpha(r[0] > 0 ? 1f : 0.35f);
			redoBtn.setEnabled(r[1] > 0);
			redoBtn.setAlpha(r[1] > 0 ? 1f : 0.35f);
		});
	}

	private void more() {
		String[] items = {Tr.t("nav_from_static", "Walls from the static elements"), Tr.t("nav_border", "Walls around the edge"),
				Tr.t("nav_invert", "Swap walls and open cells"), Tr.t("nav_clear", "Clear all cells"), Tr.t("nav_grid_props", "Grid properties")};
		Dialogs.choose(a, Tr.t("more", "More"), items, i -> {
			if (i == 0) {
				Bridge.query(() -> Bridge.editor().navPaint.wallsFromStatic(), n -> {
					if (n != null) Ui.toast(a, Tr.t("nav_from_static_done", "New walls") + ": " + n);
				});
			} else if (i == 1) {
				Bridge.gl(() -> Bridge.editor().navPaint.borderWalls());
			} else if (i == 2) {
				Bridge.gl(() -> Bridge.editor().navPaint.invert());
			} else if (i == 3) {
				Dialogs.confirm(a, Tr.t("nav_clear", "Clear all cells"), Tr.t("nav_clear_msg", "Open every cell of this grid? (Undo is available.)"), Tr.t("ok", "OK"),
						() -> Bridge.gl(() -> Bridge.editor().navPaint.clearAll()));
			} else if (i == 4) {
				close();
			}
		});
	}

	/** Leaves the painter and shows the grid's properties. */
	public void close() {
		Bridge.query(() -> {
			NavPaint p = Bridge.editor().navPaint;
			p.listener = null;
			p.stop();
			return Boolean.TRUE;
		}, ok -> {
			closeUi();
			shell.panels.show(shell.properties);
		});
	}

	private void closeUi() {
		if (current == this) current = null;
		shell.exitOverlay(this);
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		return false; // empty parts: the canvas / 3D view gets the touch
	}
}
