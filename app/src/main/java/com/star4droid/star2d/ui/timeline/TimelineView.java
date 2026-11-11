package com.star4droid.star2d.ui.timeline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import com.star4droid.template.anim.Timeline;

/**
 * The timeline strip: a ruler with the playhead and the markers on top, then one row per track (element · property) with
 * its keys as diamonds. Drag on the ruler or an empty row = move the playhead, drag a key = change its time, long-press a
 * key / a row name / the ruler = menu, pinch = zoom the time, two-finger drag = scroll.
 */
public class TimelineView extends View {
	public interface Listener {
		void seek(float t);

		void moveKey(int track, int key, float t);

		void selectKey(int track, int key);

		void keyMenu(int track, int key, float rawX, float rawY);

		void trackMenu(int track, float rawX, float rawY);

		void rulerMenu(float t, float rawX, float rawY);

		void markerMenu(int marker, float rawX, float rawY);
	}

	private final float d;
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path diamond = new Path();
	private final RectF rect = new RectF();
	public Timeline tl;
	public float time;
	public int selTrack = -1, selKey = -1;
	/** Name of the element of each track as shown (the preview element instead of "@self"). */
	public String selfName = "";
	public boolean record;
	public Listener listener;
	private float zoom = 1f, offsetX = 0f, offsetY = 0f;
	private final ScaleGestureDetector scaler;
	private final int slop;
	private final boolean rtl;

	// gesture state
	private static final int NONE = 0, SCRUB = 1, KEY = 2, PAN = 3, LABEL = 4, RULER_HOLD = 5, MARKER = 6;
	private int mode = NONE, downTrack = -1, downKey = -1, downMarker = -1;
	private float downX, downY, lastX, lastY, dragT;
	private boolean moved, longDone;
	private final Runnable longPress = new Runnable() {
		public void run() {
			if (moved || listener == null) return;
			longDone = true;
			int[] loc = new int[2];
			getLocationOnScreen(loc);
			float rx = loc[0] + downX, ry = loc[1] + downY;
			if (mode == KEY && downTrack >= 0) listener.keyMenu(downTrack, downKey, rx, ry);
			else if (mode == LABEL && downTrack >= 0) listener.trackMenu(downTrack, rx, ry);
			else if (mode == MARKER && downMarker >= 0) listener.markerMenu(downMarker, rx, ry);
			else if (mode == RULER_HOLD || mode == SCRUB) listener.rulerMenu(timeAt(downX), rx, ry);
			performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
		}
	};

	public TimelineView(Context c, boolean rtl) {
		super(c);
		this.rtl = rtl;
		d = c.getResources().getDisplayMetrics().density;
		text.setTextSize(11.5f * d);
		text.setColor(0xFFB0B6BF);
		stroke.setStyle(Paint.Style.STROKE);
		slop = ViewConfiguration.get(c).getScaledTouchSlop();
		scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			@Override
			public boolean onScale(ScaleGestureDetector g) {
				float focusT = timeAt(g.getFocusX());
				zoom = Math.max(1f, Math.min(24f, zoom * g.getScaleFactor()));
				// keep the time under the fingers in place
				float x = xAt(focusT);
				offsetX += x - g.getFocusX();
				clampOffset();
				invalidate();
				return true;
			}
		});
	}

	// ------------------------------------------------------------------ geometry

	private float labelW() {
		return Math.min(130 * d, getWidth() * 0.34f);
	}

	private float rulerH() {
		return 30 * d;
	}

	private float rowH() {
		return 30 * d;
	}

	private float axisLeft() {
		return labelW() + 10 * d;
	}

	private float axisWidth() {
		return Math.max(10, getWidth() - axisLeft() - 14 * d);
	}

	private float duration() {
		return tl == null ? 1f : Math.max(0.2f, tl.duration);
	}

	private float pxPerSec() {
		return axisWidth() / duration() * zoom;
	}

	public float xAt(float t) {
		return axisLeft() + t * pxPerSec() - offsetX;
	}

	public float timeAt(float x) {
		return Math.max(0, Math.min(duration(), (x - axisLeft() + offsetX) / pxPerSec()));
	}

	private void clampOffset() {
		float max = Math.max(0, duration() * pxPerSec() - axisWidth());
		offsetX = Math.max(0, Math.min(max, offsetX));
		float content = (tl == null ? 0 : tl.tracks.size()) * rowH(), room = getHeight() - rulerH();
		offsetY = Math.max(0, Math.min(Math.max(0, content - room), offsetY));
	}

	public void resetZoom() {
		zoom = 1f;
		offsetX = 0;
		invalidate();
	}

	/** Keeps the playhead visible while playing. */
	public void follow() {
		float x = xAt(time);
		if (x > getWidth() - 20 * d) offsetX += x - (getWidth() - 20 * d);
		else if (x < axisLeft()) offsetX -= axisLeft() - x;
		clampOffset();
	}

	// ------------------------------------------------------------------ draw

	private float step() {
		float pps = pxPerSec();
		float[] steps = {0.05f, 0.1f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 30f, 60f};
		for (float s : steps) if (s * pps >= 46 * d) return s;
		return 120f;
	}

	@Override
	protected void onDraw(Canvas cv) {
		float w = getWidth(), h = getHeight();
		fill.setColor(0xFF15171B);
		cv.drawRect(0, 0, w, h, fill);
		if (tl == null) return;
		float L = labelW(), top = rulerH(), rh = rowH();
		// ---- rows
		cv.save();
		cv.clipRect(0, top, w, h);
		for (int i = 0; i < tl.tracks.size(); i++) {
			float y = top + i * rh - offsetY;
			if (y > h || y + rh < top) continue;
			Timeline.Track tr = tl.tracks.get(i);
			fill.setColor(i == selTrack ? 0xFF223040 : (i % 2 == 0 ? 0xFF1B1E23 : 0xFF181A1F));
			cv.drawRect(0, y, w, y + rh, fill);
			// label
			String el = tr.element.equals("@self") || tr.element.isEmpty() ? (selfName.isEmpty() ? "@self" : selfName + " (@self)") : tr.element;
			String label = el + " · " + tr.property;
			text.setColor(i == selTrack ? 0xFFF2F4F7 : 0xFFB0B6BF);
			text.setTextAlign(Paint.Align.LEFT);
			CharSequence cut = TextUtils.ellipsize(label, new android.text.TextPaint(text), L - 14 * d, TextUtils.TruncateAt.END);
			cv.drawText(cut.toString(), 8 * d, y + rh * 0.5f + text.getTextSize() * 0.35f, text);
			// key-to-key segments
			cv.save();
			cv.clipRect(axisLeft() - 8 * d, y, w, y + rh);
			stroke.setStrokeWidth(2 * d);
			stroke.setColor(colorOf(tr.property) & 0x88FFFFFF);
			for (int k = 0; k + 1 < tr.keys.size(); k++) {
				float xa = xAt(tr.keys.get(k).t), xb = xAt(tr.keys.get(k + 1).t);
				cv.drawLine(xa, y + rh * 0.5f, xb, y + rh * 0.5f, stroke);
			}
			for (int k = 0; k < tr.keys.size(); k++) {
				float t = (mode == KEY && i == downTrack && k == downKey && moved) ? dragT : tr.keys.get(k).t;
				drawKey(cv, xAt(t), y + rh * 0.5f, colorOf(tr.property), i == selTrack && k == selKey);
			}
			cv.restore();
		}
		cv.restore();
		// separator of the names
		fill.setColor(0xFF2A2F36);
		cv.drawRect(L, top, L + 1 * d, h, fill);
		// end of the timeline: shade after the duration
		float xe = xAt(duration());
		if (xe < w) {
			fill.setColor(0x55000000);
			cv.drawRect(Math.max(xe, axisLeft()), 0, w, h, fill);
		}
		// ---- ruler
		fill.setColor(0xFF20242A);
		cv.drawRect(0, 0, w, top, fill);
		float st = step();
		text.setTextAlign(Paint.Align.CENTER);
		text.setColor(0xFF7D8590);
		stroke.setStrokeWidth(1 * d);
		stroke.setColor(0xFF3A4048);
		float t0 = timeAt(axisLeft());
		int i0 = (int) Math.floor(t0 / st);
		for (int i = i0; ; i++) {
			float t = i * st;
			float x = xAt(t);
			if (x > w) break;
			if (x < axisLeft() - 1) continue;
			cv.drawLine(x, top * 0.55f, x, top, stroke);
			cv.drawText(fmtTime(t, st), x, top * 0.45f, text);
			// half ticks
			float xh = xAt(t + st * 0.5f);
			if (xh < w) cv.drawLine(xh, top * 0.78f, xh, top, stroke);
		}
		// markers
		for (int i = 0; i < tl.markers.size(); i++) {
			Timeline.Marker m = tl.markers.get(i);
			float x = xAt(m.t);
			if (x < axisLeft() - 2 || x > w) continue;
			fill.setColor(0xFFFFB300);
			Path p = new Path();
			p.moveTo(x, top);
			p.lineTo(x, top * 0.35f);
			p.lineTo(x + 9 * d, top * 0.48f);
			p.lineTo(x, top * 0.62f);
			cv.drawPath(p, fill);
			text.setTextAlign(Paint.Align.LEFT);
			text.setColor(0xFFFFCA28);
			text.setTextSize(9.5f * d);
			cv.drawText(m.name, x + 11 * d, top * 0.62f, text);
			text.setTextSize(11.5f * d);
			text.setTextAlign(Paint.Align.CENTER);
		}
		// names column header
		text.setTextAlign(Paint.Align.LEFT);
		text.setColor(record ? 0xFFEF5350 : 0xFF7D8590);
		cv.drawText(record ? "● REC" : (tl.tracks.isEmpty() ? "" : tl.tracks.size() + " ▸"), 8 * d, top * 0.62f, text);
		// ---- playhead
		float xp = xAt(time);
		if (xp >= axisLeft() - 1 && xp <= w) {
			int pc = record ? 0xFFEF5350 : 0xFF2196F3;
			stroke.setColor(pc);
			stroke.setStrokeWidth(2 * d);
			cv.drawLine(xp, top * 0.2f, xp, h, stroke);
			fill.setColor(pc);
			rect.set(xp - 6 * d, 0, xp + 6 * d, top * 0.3f);
			cv.drawRoundRect(rect, 3 * d, 3 * d, fill);
		}
	}

	private void drawKey(Canvas cv, float x, float y, int color, boolean sel) {
		float r = (sel ? 8f : 6.5f) * d;
		diamond.reset();
		diamond.moveTo(x, y - r);
		diamond.lineTo(x + r, y);
		diamond.lineTo(x, y + r);
		diamond.lineTo(x - r, y);
		diamond.close();
		fill.setColor(color);
		cv.drawPath(diamond, fill);
		stroke.setStrokeWidth((sel ? 2.5f : 1.2f) * d);
		stroke.setColor(sel ? 0xFFFFFFFF : 0xFF101214);
		cv.drawPath(diamond, stroke);
	}

	static int colorOf(String prop) {
		if (prop.equals("x") || prop.equals("y") || prop.equals("z")) return 0xFF4FC3F7;
		if (prop.toLowerCase().contains("rotation")) return 0xFF81C784;
		if (prop.startsWith("Scale") || prop.equals("width") || prop.equals("height") || prop.equals("radius")) return 0xFFFFB74D;
		if (prop.equals("Tint") || prop.equals("Color") || prop.equals("Opacity")) return 0xFFF06292;
		if (prop.startsWith("var:")) return 0xFFBA68C8;
		return 0xFFE0E0E0;
	}

	static String fmtTime(float t, float step) {
		if (step >= 1f) return ((int) Math.round(t)) + "s";
		if (step >= 0.1f) return String.valueOf(Math.round(t * 10f) / 10f);
		return String.valueOf(Math.round(t * 100f) / 100f);
	}

	// ------------------------------------------------------------------ touch

	private int trackAt(float y) {
		if (tl == null || y < rulerH()) return -1;
		int i = (int) ((y - rulerH() + offsetY) / rowH());
		return i >= 0 && i < tl.tracks.size() ? i : -1;
	}

	private int keyAt(int track, float x) {
		if (track < 0) return -1;
		Timeline.Track tr = tl.tracks.get(track);
		int best = -1;
		float bd = 16 * d;
		for (int k = 0; k < tr.keys.size(); k++) {
			float dx = Math.abs(xAt(tr.keys.get(k).t) - x);
			if (dx < bd) {
				bd = dx;
				best = k;
			}
		}
		return best;
	}

	private int markerAt(float x) {
		if (tl == null) return -1;
		for (int i = 0; i < tl.markers.size(); i++) if (Math.abs(xAt(tl.markers.get(i).t) + 4 * d - x) < 12 * d) return i;
		return -1;
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (tl == null) return false;
		scaler.onTouchEvent(e);
		float x = e.getX(), y = e.getY();
		switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				getParent().requestDisallowInterceptTouchEvent(true);
				downX = lastX = x;
				downY = lastY = y;
				moved = false;
				longDone = false;
				downTrack = -1;
				downKey = -1;
				downMarker = -1;
				if (y < rulerH()) {
					downMarker = markerAt(x);
					if (downMarker >= 0) mode = MARKER;
					else if (x >= axisLeft() - 6 * d) {
						mode = SCRUB;
						if (listener != null) listener.seek(timeAt(x));
					} else mode = RULER_HOLD;
				} else {
					downTrack = trackAt(y);
					if (x < labelW()) mode = LABEL;
					else {
						downKey = keyAt(downTrack, x);
						mode = downKey >= 0 ? KEY : PAN;
						if (downKey >= 0) {
							dragT = tl.tracks.get(downTrack).keys.get(downKey).t;
							if (listener != null) listener.selectKey(downTrack, downKey);
						}
					}
				}
				postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
				return true;
			}
			case MotionEvent.ACTION_POINTER_DOWN:
				// two fingers: zoom / scroll only
				removeCallbacks(longPress);
				if (mode == KEY) moved = false;
				mode = PAN;
				return true;
			case MotionEvent.ACTION_MOVE: {
				if (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) {
					if (!moved) removeCallbacks(longPress);
					moved = true;
				}
				if (longDone) return true;
				float dx = x - lastX, dy = y - lastY;
				lastX = x;
				lastY = y;
				if (mode == SCRUB && listener != null) listener.seek(timeAt(x));
				else if (mode == KEY && moved) {
					dragT = Math.round(timeAt(x) * 100f) / 100f;
					invalidate();
				} else if (mode == PAN && moved && !scaler.isInProgress()) {
					if (e.getPointerCount() > 1 || Math.abs(dy) > Math.abs(dx)) {
						offsetY -= dy;
						if (e.getPointerCount() > 1) offsetX -= dx;
					} else if (zoom > 1.01f && e.getPointerCount() > 1) offsetX -= dx;
					else if (listener != null && e.getPointerCount() == 1) listener.seek(timeAt(x));
					clampOffset();
					invalidate();
				} else if (mode == LABEL && moved) {
					offsetY -= dy;
					clampOffset();
					invalidate();
				}
				return true;
			}
			case MotionEvent.ACTION_UP: {
				removeCallbacks(longPress);
				if (!longDone && listener != null) {
					if (mode == KEY && moved && downTrack >= 0) listener.moveKey(downTrack, downKey, dragT);
					else if (mode == PAN && !moved) listener.seek(timeAt(x));
					else if (mode == LABEL && !moved && downTrack >= 0) listener.selectKey(downTrack, -1);
					else if (mode == MARKER && !moved) listener.seek(tl.markers.get(downMarker).t);
				}
				mode = NONE;
				moved = false;
				invalidate();
				return true;
			}
			case MotionEvent.ACTION_CANCEL:
				removeCallbacks(longPress);
				mode = NONE;
				moved = false;
				invalidate();
				return true;
		}
		return true;
	}
}
