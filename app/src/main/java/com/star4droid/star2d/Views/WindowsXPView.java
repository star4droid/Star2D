package com.star4droid.star2d.Views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.star4droid.star2d.evo.R;

public class WindowsXPView extends FrameLayout {
	private static final int MOUSE_SIZE_DP = 25;
	
	private ImageView mouseImageView;
	private int mouseX, mouseY;
	private GestureDetector gestureDetector;
	
	public WindowsXPView(Context context) {
		super(context);
		init();
	}
	
	public WindowsXPView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	public WindowsXPView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}
	
	private void init() {
		// Create and add the mouse ImageView
		mouseImageView = new ImageView(getContext());
		mouseImageView.setImageResource(R.drawable.mouse);
		int mouseSizePx = (int) (MOUSE_SIZE_DP * getResources().getDisplayMetrics().density);
		LayoutParams params = new LayoutParams(mouseSizePx, mouseSizePx);
		addView(mouseImageView, params);
		setBackgroundResource(R.drawable.wallpaper);
		// Initialize gesture detector for double tap handling
		gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onDoubleTap(MotionEvent e) {
				int x = (int) e.getX();
				int y = (int) e.getY();
				checkClick(x, y);
				return true;
			}
		});
	}
	
	private void checkClick(int x, int y) {
		for (int i = 0; i < getChildCount(); i++) {
			View child = getChildAt(i);
			if (child != mouseImageView) {
				int childX = (int) child.getX();
				int childY = (int) child.getY();
				int childWidth = child.getWidth();
				int childHeight = child.getHeight();
				
				if (x >= childX && x <= childX + childWidth && y >= childY && y <= childY + childHeight) {
					// Child view is clicked
					// Do something here
					break;
				}
			}
		}
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		gestureDetector.onTouchEvent(event);
		
		int action = event.getAction();
		switch(action) {
		case MotionEvent.ACTION_DOWN:
		mouseX = (int) event.getX();
		mouseY = (int) event.getY();
		break;
		case MotionEvent.ACTION_MOVE:
		int deltaX = (int) event.getX() - mouseX;
		int deltaY = (int) event.getY() - mouseY;
		int newMouseX = (int) mouseImageView.getX() + deltaX;
		int newMouseY = (int) mouseImageView.getY() + deltaY;
		if(newMouseX>getMeasuredWidth()) newMouseX=getMeasuredWidth();
		if(newMouseY>getMeasuredHeight()) newMouseY=getMeasuredHeight();
		if(newMouseX<0) newMouseX=0;
		if(newMouseY<0) newMouseY=0;
		mouseImageView.setX(newMouseX);
		mouseImageView.setY(newMouseY);
		mouseX = (int) event.getX();
		mouseY = (int) event.getY();
		break;
	}
	
	return true;
}
}