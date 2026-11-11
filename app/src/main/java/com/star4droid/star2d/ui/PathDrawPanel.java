package com.star4droid.star2d.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.PathDraw;
import com.star4droid.star2d.evo.R;

/**
 * Draws the points of a path variable on the canvas (or on the ground of the 3D view): tap to add a point, drag a point to
 * move it. Done gives "x1,y1,x2,y2..." to the caller (the variable's initial value).
 */
public class PathDrawPanel extends FrameLayout {
	public interface Done {
		/** The points as text (UI thread). */
		void done(String points);
	}

	public static PathDrawPanel current;
	final EditorShell shell;
	final Activity a;
	final Done done;
	boolean navigate;
	private TextView info;
	private ImageView undoBtn, navBtn;

	/** Opens the drawing over the editor with the points of {@code text}; {@code done} gets the new points. */
	public static void open(EditorShell shell, String title, String text, Done done) {
		if (current != null || NavPaintPanel.current != null) return;
		Bridge.gl(() -> {
			LibgdxEditor ed = Bridge.editor();
			if (ed.navPaint.active()) ed.navPaint.stop();
			if (ed.get3D() != null && ed.get3D().edit2D) ed.get3D().setEdit2D(false);
			ed.pathDraw.start(text, title);
		});
		PathDrawPanel p = new PathDrawPanel(shell, title, done);
		current = p;
		shell.enterOverlay(p);
	}

	PathDrawPanel(EditorShell shell, String title, Done done) {
		super(shell.activity());
		this.shell = shell;
		this.a = shell.activity();
		this.done = done;
		Bridge.gl(() -> Bridge.editor().pathDraw.listener = p -> {
			final int n = p.count();
			final boolean u = p.canUndo();
			Ui.ui(() -> refresh(n, u));
		});
		build(title);
	}

	private int dp(float v) {
		return Ui.dp(a, v);
	}

	private GradientDrawable glass(float r) {
		return Ui.rounded(0xE6202328, dp(r), 0x553A4048, dp(1));
	}

	private void build(String title) {
		LinearLayout top = Ui.row(a);
		top.setGravity(Gravity.CENTER_VERTICAL);
		top.setPadding(dp(6), dp(4), dp(6), dp(4));
		top.setBackground(glass(14));
		top.setClickable(true);
		top.addView(Ui.button(a, R.drawable.ic_s2_check, Tr.t("ter_done", "Done"), true, v -> finish(true)));
		TextView t = Ui.text(a, title, 15, Ui.TEXT);
		t.setTypeface(Typeface.DEFAULT_BOLD);
		t.setMaxLines(1);
		t.setPadding(dp(10), 0, dp(6), 0);
		t.setEllipsize(android.text.TextUtils.TruncateAt.END);
		top.addView(t, Ui.weight(1));
		navBtn = Ui.iconButton(a, R.drawable.ic_s2_hand, Tr.t("ter_view", "Move the view (one finger)"), v -> {
			navigate = !navigate;
			final boolean nav = navigate;
			Bridge.gl(() -> Bridge.editor().pathDraw.navigate = nav);
			navBtn.setImageTintList(ColorStateList.valueOf(navigate ? Ui.ACCENT : Ui.TEXT));
		});
		undoBtn = Ui.iconButton(a, R.drawable.ic_s2_undo, Tr.t("undo", "Undo"), v -> Bridge.gl(() -> Bridge.editor().pathDraw.undoStep()));
		top.addView(navBtn);
		top.addView(undoBtn);
		top.addView(Ui.iconButton(a, R.drawable.ic_s2_more, Tr.t("more", "More"), v -> more()));
		top.addView(Ui.iconButton(a, R.drawable.ic_s2_close, Tr.t("cancel", "Cancel"), v -> finish(false)));
		Ui.fitIconRow(a, top, 150);
		FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
		tlp.setMargins(dp(8), dp(6), dp(8), 0);
		addView(top, tlp);
		info = Ui.text(a, "", 12.5f, Ui.TEXT_2);
		info.setPadding(dp(12), dp(8), dp(12), dp(8));
		info.setBackground(glass(12));
		FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
		ilp.setMargins(dp(8), 0, dp(8), dp(10));
		addView(info, ilp);
		refresh(0, false);
	}

	private void refresh(int n, boolean canUndo) {
		info.setText(Tr.t("path_hint", "Tap to add a point, drag a point to move it.") + "  ·  " + n + " " + Tr.t("path_points", "points"));
		undoBtn.setEnabled(canUndo);
		undoBtn.setAlpha(canUndo ? 1f : 0.35f);
	}

	private void more() {
		String[] items = {Tr.t("path_reverse", "Reverse the direction"), Tr.t("path_loop", "Show the way back (loop)"), Tr.t("path_clear", "Remove all points")};
		Dialogs.choose(a, Tr.t("more", "More"), items, i -> Bridge.gl(() -> {
			PathDraw p = Bridge.editor().pathDraw;
			if (i == 0) p.reverse();
			else if (i == 1) p.loop = !p.loop;
			else p.clear();
		}));
	}

	private void finish(boolean save) {
		Bridge.query(() -> {
			PathDraw p = Bridge.editor().pathDraw;
			String text = p.text();
			p.listener = null;
			p.stop();
			return text;
		}, text -> {
			if (current == this) current = null;
			shell.exitOverlay(this);
			if (save && done != null) done.done(text == null ? "" : text);
		});
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		return false;
	}
}
