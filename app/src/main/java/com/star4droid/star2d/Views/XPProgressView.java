package com.star4droid.star2d.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import com.star4droid.star2d.evo.R;

public class XPProgressView extends View {
	private static final int NUM_SQUARES = 3;
	private int SQUARE_SIZE = 50;
	private static final int SQUARE_MARGIN = 10;
	
	private Paint squarePaint;
	private float currentX;
	
	public XPProgressView(Context context) {
		super(context);
		init();
	}
	
	public XPProgressView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	public XPProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}
	
	private void init() {
		squarePaint = new Paint();
		squarePaint.setColor(getContext().getColor(R.color.sim_yellow));
		squarePaint.setStyle(Paint.Style.FILL);
		setBackground(new GradientDrawable() { public GradientDrawable getIns(int a, int b, int c, int d) { this.setCornerRadius(a); this.setStroke(b, c); this.setColor(d); return this; } }.getIns(5, 2, getContext().getColor(com.star4droid.star2d.evo.R.color.text_color), Color.TRANSPARENT));
		currentX = 0;
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		SQUARE_SIZE = getMeasuredHeight()-10;
		int startY = (getHeight() - SQUARE_SIZE) / 2;
		
		for (int i = 0; i < NUM_SQUARES; i++) {
			int left = (int)currentX + (i * (SQUARE_SIZE + SQUARE_MARGIN));
			int top = startY;
			int right = left + SQUARE_SIZE;
			int bottom = top + SQUARE_SIZE;
			
			canvas.drawRoundRect(left, top, right, bottom, 10, 10, squarePaint);
		}
		
		// Update the animation
		currentX += (SQUARE_SIZE + SQUARE_MARGIN)*0.08f;
		if (currentX >= getWidth()) {
			currentX = 0;
		}
		
		invalidate();
		
	}
}