package com.star4droid.star2d.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.badlogic.gdx.Gdx;
import com.star4droid.star2d.editor.three.CameraPreview3D;
import com.star4droid.star2d.evo.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The camera preview window of the 3D editor: a frame over the canvas (under the top bars) through which the GL side draws
 * what a scene camera sees (see {@link CameraPreview3D}). Drag inside it to move it, drag an edge or a corner to resize it,
 * tap the name at the top to choose another camera. Position and size are kept (as fractions of the canvas) for next time.
 * This view only draws the border, the name and the resize grip; the picture is under it, in the GL view.
 */
@SuppressLint("ViewConstructor")
public final class CameraPreviewWindow extends FrameLayout {
	private static final String PREFS = "star2d_editor3d";
	private final Activity a;
	private final TextView label;
	private final Paint grip = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final int edge, minSize;
	private List<String> names = new ArrayList<>();
	private String chosen = "";
	private boolean open;
	private final Runnable poll = this::pollCameras;

	// drag state
	private int mode; // bits: 1 left, 2 right, 4 top, 8 bottom; 0 = move
	private float downX, downY;
	private int startL, startT, startW, startH;
	private boolean dragging;

	public CameraPreviewWindow(Activity a) {
		super(a);
		this.a = a;
		edge = Ui.dp(a, 18);
		minSize = Ui.dp(a, 90);
		setWillNotDraw(false);
		setBackground(Ui.rounded(Color.TRANSPARENT, Ui.dp(a, 4), 0xFFFFB74D, Ui.dp(a, 2)));
		setClickable(true);
		setLayoutDirection(LAYOUT_DIRECTION_LTR);

		LinearLayout head = new LinearLayout(a);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setBackground(Ui.rounded(0xCC15171B, Ui.dp(a, 10)));
		head.setPadding(Ui.dp(a, 8), Ui.dp(a, 3), Ui.dp(a, 4), Ui.dp(a, 3));
		ImageView ic = new ImageView(a);
		ic.setImageResource(R.drawable.ic_s2_videocam);
		ic.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFB74D));
		head.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 16), Ui.dp(a, 16)));
		label = Ui.text(a, "", 12, Color.WHITE);
		label.setTypeface(Typeface.DEFAULT_BOLD);
		label.setSingleLine(true);
		label.setEllipsize(TextUtils.TruncateAt.END);
		label.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 2), 0);
		head.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		ImageView more = new ImageView(a);
		more.setImageResource(R.drawable.ic_s2_expand_more);
		more.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
		head.addView(more, new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)));
		head.setOnClickListener(this::chooseCamera);
		LayoutParams hl = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
		hl.setMargins(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), 0);
		addView(head, hl);
		head.setMinimumHeight(Ui.dp(a, 26));

		grip.setColor(0xFFFFB74D);
		grip.setStrokeWidth(Ui.dp(a, 2));
		grip.setStrokeCap(Paint.Cap.ROUND);
		chosen = prefs().getString("previewCamera", "");
		// a new editor starts with the window closed (the GL side keeps its rectangle in static fields)
		CameraPreview3D.w = 0;
		CameraPreview3D.h = 0;
		addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> push());
		setVisibility(GONE);
	}

	private SharedPreferences prefs() {
		return a.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
	}

	public boolean isOpen() {
		return open;
	}

	/** Puts the window into the canvas area (the bars are rebuilt on rotation / language change: called again then). */
	public void attach(FrameLayout middle) {
		if (getParent() == middle) return;
		if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
		middle.addView(this, new FrameLayout.LayoutParams(Ui.dp(a, 160), Ui.dp(a, 120), Gravity.TOP | Gravity.LEFT));
		if (open) middle.post(this::placeFromPrefs);
	}

	public void toggle() {
		setOpen(!open);
	}

	public void setOpen(boolean on) {
		open = on;
		if (!on) {
			setVisibility(GONE);
			removeCallbacks(poll);
			CameraPreview3D.w = 0;
			CameraPreview3D.h = 0;
			return;
		}
		CameraPreview3D.camera = chosen;
		setVisibility(VISIBLE);
		bringToFront();
		View p = (View) getParent();
		if (p != null) p.post(this::placeFromPrefs);
		pollCameras();
	}

	/** Size and place from the saved fractions (first time: 40% of the canvas width, the aspect of the screen, top left). */
	private void placeFromPrefs() {
		View p = (View) getParent();
		if (p == null || p.getWidth() <= 0) return;
		int pw = p.getWidth(), ph = p.getHeight();
		int[] gl = glSize();
		float aspect = gl[1] > 0 ? gl[1] / (float) gl[0] : 16f / 9f;
		SharedPreferences sp = prefs();
		float fw = sp.getFloat("previewW", 0.4f);
		int w = Math.round(pw * fw);
		int h = Math.round(sp.getFloat("previewH", -1f) * ph);
		if (h <= 0) h = Math.round(w * aspect);
		if (h > ph * 0.6f) { h = Math.round(ph * 0.6f); if (sp.getFloat("previewH", -1f) <= 0) w = Math.round(h / aspect); }
		int l = Math.round(sp.getFloat("previewX", 0f) * pw) + (sp.contains("previewX") ? 0 : Ui.dp(a, 8));
		int t = Math.round(sp.getFloat("previewY", 0f) * ph) + (sp.contains("previewY") ? 0 : Ui.dp(a, 8));
		setRect(l, t, w, h);
	}

	private void save() {
		View p = (View) getParent();
		if (p == null || p.getWidth() <= 0) return;
		LayoutParams lp = (LayoutParams) getLayoutParams();
		prefs().edit()
				.putFloat("previewX", lp.leftMargin / (float) p.getWidth())
				.putFloat("previewY", lp.topMargin / (float) p.getHeight())
				.putFloat("previewW", lp.width / (float) p.getWidth())
				.putFloat("previewH", lp.height / (float) p.getHeight())
				.apply();
	}

	/** Keeps the window inside the canvas area and at least minSize big. */
	private void setRect(int l, int t, int w, int h) {
		View p = (View) getParent();
		if (p == null) return;
		int pw = p.getWidth(), ph = p.getHeight();
		w = Math.max(minSize, Math.min(w, pw));
		h = Math.max(minSize, Math.min(h, ph));
		l = Math.max(0, Math.min(l, pw - w));
		t = Math.max(0, Math.min(t, ph - h));
		LayoutParams lp = (LayoutParams) getLayoutParams();
		lp.gravity = Gravity.TOP | Gravity.LEFT;
		lp.width = w;
		lp.height = h;
		lp.leftMargin = l;
		lp.topMargin = t;
		setLayoutParams(lp);
	}

	/** Hands the GL side the rectangle of the window in GL pixels (origin bottom left of the GL view). */
	private void push() {
		if (!open || getWidth() <= 0) return;
		View glv = glView();
		int[] me = new int[2], gl = new int[2];
		getLocationOnScreen(me);
		int glH;
		if (glv != null) {
			glv.getLocationOnScreen(gl);
			glH = glv.getHeight();
		} else {
			View root = getRootView();
			root.getLocationOnScreen(gl);
			glH = root.getHeight();
		}
		// the border is 2 dp: the picture goes inside it
		int b = Ui.dp(a, 2);
		int x = me[0] - gl[0] + b, yTop = me[1] - gl[1] + b, w = getWidth() - 2 * b, h = getHeight() - 2 * b;
		CameraPreview3D.x = x;
		CameraPreview3D.y = glH - yTop - h;
		CameraPreview3D.w = w;
		CameraPreview3D.h = h;
	}

	private static View glView() {
		try {
			if (Gdx.graphics instanceof com.badlogic.gdx.backends.android.AndroidGraphics)
				return ((com.badlogic.gdx.backends.android.AndroidGraphics) Gdx.graphics).getView();
		} catch (Throwable ignored) {
		}
		return null;
	}

	private int[] glSize() {
		View v = glView();
		if (v != null && v.getWidth() > 0) return new int[]{v.getWidth(), v.getHeight()};
		android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
		return new int[]{dm.widthPixels, dm.heightPixels};
	}

	// ------------------------------------------------------------------ cameras

	/** Reads the cameras of the scene (renamed, added, deleted) and updates the name; every second while open. */
	private void pollCameras() {
		removeCallbacks(poll);
		if (!open) return;
		if (Bridge.ready()) {
			Bridge.query(() -> CameraPreview3D.cameras(Bridge.editor() == null ? null : Bridge.editor().get3D()), list -> {
				names = list == null ? new ArrayList<>() : list;
				refreshLabel();
			});
		}
		postDelayed(poll, 1000);
	}

	private void refreshLabel() {
		String shown;
		if (names.isEmpty()) shown = Tr.t("cprev_none", "No camera in this scene");
		else if (!chosen.isEmpty() && names.contains(chosen)) shown = chosen;
		else shown = names.get(0); // the Active camera comes first
		label.setText(shown);
	}

	private void chooseCamera(View anchor) {
		PopupMenu pm = new PopupMenu(a, anchor);
		if (names.isEmpty()) {
			pm.getMenu().add(Tr.t("cprev_none", "No camera in this scene")).setEnabled(false);
		} else {
			for (int i = 0; i < names.size(); i++) {
				String n = names.get(i);
				android.view.MenuItem mi = pm.getMenu().add(0, i, i, i == 0 ? n + "  (" + Tr.t("cprev_active", "Active") + ")" : n);
				mi.setCheckable(true);
				mi.setChecked(n.equals(chosen) || (chosen.isEmpty() || !names.contains(chosen)) && i == 0);
			}
			pm.getMenu().setGroupCheckable(0, true, true);
		}
		pm.setOnMenuItemClickListener(it -> {
			if (it.getItemId() < 0 || it.getItemId() >= names.size()) return false;
			chosen = names.get(it.getItemId());
			CameraPreview3D.camera = chosen;
			prefs().edit().putString("previewCamera", chosen).apply();
			refreshLabel();
			return true;
		});
		pm.show();
	}

	// ------------------------------------------------------------------ move / resize

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		LayoutParams lp = (LayoutParams) getLayoutParams();
		switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				float x = e.getX(), y = e.getY();
				int m = 0;
				if (x < edge) m |= 1;
				else if (x > getWidth() - edge) m |= 2;
				if (y < edge) m |= 4;
				else if (y > getHeight() - edge) m |= 8;
				mode = m;
				downX = e.getRawX();
				downY = e.getRawY();
				startL = lp.leftMargin;
				startT = lp.topMargin;
				startW = lp.width;
				startH = lp.height;
				dragging = true;
				getParent().requestDisallowInterceptTouchEvent(true);
				return true;
			}
			case MotionEvent.ACTION_MOVE: {
				if (!dragging) return true;
				int dx = Math.round(e.getRawX() - downX), dy = Math.round(e.getRawY() - downY);
				int l = startL, t = startT, w = startW, h = startH;
				if (mode == 0) { l += dx; t += dy; }
				else {
					if ((mode & 1) != 0) { int nw = Math.max(minSize, startW - dx); l = startL + startW - nw; w = nw; }
					if ((mode & 2) != 0) w = startW + dx;
					if ((mode & 4) != 0) { int nh = Math.max(minSize, startH - dy); t = startT + startH - nh; h = nh; }
					if ((mode & 8) != 0) h = startH + dy;
				}
				setRect(l, t, w, h);
				return true;
			}
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				if (dragging) save();
				dragging = false;
				return true;
		}
		return super.onTouchEvent(e);
	}

	/** A small grip in the bottom right corner (the edges resize too). */
	@Override
	protected void onDraw(Canvas c) {
		super.onDraw(c);
		int w = getWidth(), h = getHeight(), s = Ui.dp(a, 5);
		for (int i = 1; i <= 3; i++) c.drawLine(w - s * i - s, h - s, w - s, h - s * i - s, grip);
	}
}
