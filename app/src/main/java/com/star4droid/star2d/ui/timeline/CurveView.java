package com.star4droid.star2d.ui.timeline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import com.star4droid.template.anim.Interp;

/** Preview of a transition: how the value goes from the previous key (bottom left) to the next one (top right). */
public class CurveView extends View {
	private final String interp;
	private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path path = new Path();
	private final boolean selected;

	public CurveView(Context c, String interp, boolean selected) {
		super(c);
		this.interp = interp;
		this.selected = selected;
		float d = c.getResources().getDisplayMetrics().density;
		line.setStyle(Paint.Style.STROKE);
		line.setStrokeWidth(2.2f * d);
		line.setStrokeCap(Paint.Cap.ROUND);
		line.setStrokeJoin(Paint.Join.ROUND);
		line.setColor(selected ? 0xFF64B5F6 : 0xFFE0E4EA);
		grid.setStyle(Paint.Style.STROKE);
		grid.setStrokeWidth(1 * d);
		grid.setColor(0x33FFFFFF);
	}

	@Override
	protected void onDraw(Canvas cv) {
		float w = getWidth(), h = getHeight(), pad = Math.min(w, h) * 0.14f;
		float x0 = pad, y0 = h - pad, x1 = w - pad, y1 = pad;
		cv.drawRect(x0, y1, x1, y0, grid);
		// bounce / elastic / back go past the target: the box shows 0..1, the curve may leave it a little
		path.reset();
		int n = 48;
		for (int i = 0; i <= n; i++) {
			float t = i / (float) n;
			float v = Interp.apply(interp, t);
			float x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * v;
			if (i == 0) path.moveTo(x, y);
			else path.lineTo(x, y);
		}
		cv.drawPath(path, line);
		if (selected) {
			grid.setColor(0x6664B5F6);
			cv.drawRect(1, 1, w - 1, h - 1, grid);
			grid.setColor(0x33FFFFFF);
		}
	}
}
