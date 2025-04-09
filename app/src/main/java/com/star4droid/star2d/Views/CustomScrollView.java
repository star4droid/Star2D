package com.star4droid.star2d.Views;
import android.view.MotionEvent;
import android.util.AttributeSet;
import android.widget.ScrollView;
import android.content.Context;


public class CustomScrollView extends ScrollView {
	
	public CustomScrollView(Context context) {
		super(context);
	}
	
	public CustomScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public CustomScrollView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		// Prevent the ScrollView from intercepting touch events
		// and let the child views handle them instead
		return false;
	}
}
