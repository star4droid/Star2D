package com.star4droid.star2d.Helpers;

import android.content.Context;
import android.view.ScaleGestureDetector;
import com.star4droid.star2d.Items.Editor;
public class ScaleGesture implements ScaleGestureDetector.OnScaleGestureListener {
	Editor editor;
	
	public ScaleGesture setEditor(Editor e){
		editor = e;
		return this;
	}
	
	@Override
	public boolean onScale(ScaleGestureDetector detector) {
		//editor.setScale(editor.getEditorScale()/editor.getRatioScale()*detector.getScaleFactor());
	    return true;
	}

	@Override
	public boolean onScaleBegin(ScaleGestureDetector arg0) {
	    return true;
	}

	@Override
	public void onScaleEnd(ScaleGestureDetector arg0) {
	
	}
}