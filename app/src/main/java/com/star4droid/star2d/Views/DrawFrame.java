package com.star4droid.star2d.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;

public class DrawFrame extends FrameLayout {
    public ScaleGestureDetector mScaleGestureDetector;
    public float wx = 0, wy = 0;
    Paint paint = new Paint(), full_paint = new Paint(), LinesPaint;
    Path path, circles_path, LinesPath;
    ArrayList<Path> paths = new ArrayList<>();
    ArrayList<Path> cpaths = new ArrayList<>();
    float scale_value = 1;
    HashMap<View, PointF> StartPT = new HashMap<>();
    PointF DownPT = new PointF();
    boolean twoFingers = false;

    public DrawFrame(Context ctx) {
        super(ctx);
        init();
    }

    public DrawFrame(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
        init();
    }

    public DrawFrame(Context context, AttributeSet attributeSet, int n) {
        super(context, attributeSet, n);
        init();
    }

    public int add(Path p) {
        paths.add(p);
        return paths.size() - 1;
    }

    public void removeAt(int x) {
        paths.set(x, null);
    }

    public void setAt(int x, Path p) {
        paths.set(x, p);
    }

    public float getMoveX() {
        return wx;
    }

    public float getMoveY() {
        return wy;
    }

    public int addToC(Path p) {
        cpaths.add(p);
        return cpaths.size() - 1;
    }

    public void removeAtC(int x) {
        cpaths.set(x, null);
    }

    public void setAtC(int x, Path p) {
        cpaths.set(x, p);
    }

    public void setPath(Path p) {
        path = p;
    }

    public void setCirclesPath(Path p) {
        circles_path = p;
    }

    private void init() {
        setBackgroundColor(0xFF1C1C1C);
        this.paint.setAntiAlias(true);
        this.paint.setDither(true);
        this.paint.setColor(Color.parseColor("#606060"));
        this.paint.setStyle(Paint.Style.STROKE);
        this.paint.setStrokeJoin(Paint.Join.ROUND);
        this.paint.setStrokeCap(Paint.Cap.ROUND);
        this.paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        this.paint.setStrokeWidth(4);
        this.full_paint.setAntiAlias(true);
        this.full_paint.setDither(true);
        this.full_paint.setColor(Color.parseColor("#606060"));
        this.full_paint.setStyle(Paint.Style.FILL);
        this.full_paint.setStrokeJoin(Paint.Join.ROUND);
        this.full_paint.setStrokeCap(Paint.Cap.ROUND);
        full_paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        this.full_paint.setStrokeWidth(2);

        LinesPaint = new Paint();
        LinesPaint.setColor(Color.YELLOW);
        LinesPaint.setStrokeWidth(0.5f);
        LinesPaint.setAlpha(75);
        LinesPaint.setStyle(Paint.Style.FILL);
        LinesPaint.setStrokeCap(Paint.Cap.ROUND);
        LinesPaint.setStrokeJoin(Paint.Join.ROUND);
        LinesPaint.setDither(true);
        LinesPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        LinesPath = new Path();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

		// Draw Dots
		float dotRadius = 3f; // Adjust the radius of the dots as needed
		float dotSpacing = 50 * scale_value; // Same as the spacing between grid lines

		// Starting points for drawing dots
		float startX = wx % dotSpacing;
		float startY = wy % dotSpacing;

		// Draw dots starting from the top-left corner of the canvas
		for (float x = startX; x < this.getMeasuredWidth(); x += dotSpacing) {
			for (float y = startY; y < this.getMeasuredHeight(); y += dotSpacing) {
				canvas.drawCircle(x, y, dotRadius, LinesPaint);
			}
		}

//        //Draw Lines
//        LinesPath.reset();
//        float xx = wx;
//        float yy = wy;
//        float width = 50 * scale_value;
//        float height = 50 * scale_value;
//        float xt = xx;
//        while (xt < this.getMeasuredWidth()) {
//            LinesPath.moveTo(xt, 0);
//            LinesPath.lineTo(xt, getMeasuredHeight());
//            xt += 50 * scale_value;
//        }
//        if ((xx > 0)/*&&(xx<getMeasuredWidth())*/) {
//            xt = xx;
//            while (xt > 0) {
//                LinesPath.moveTo(xt, 0);
//                LinesPath.lineTo(xt, getMeasuredHeight());
//                xt -= 50 * scale_value;
//            }
//        }
//        float yt = yy;
//        while (yt < this.getMeasuredHeight()) {
//            LinesPath.moveTo(0, yt);
//            LinesPath.lineTo(getMeasuredWidth(), yt);
//            yt += 50 * scale_value;
//        }
//        if ((yy > 0)/*&&(yy<getMeasuredHeight())*/) {
//            yt = yy;
//            while (yt > 0) {
//                LinesPath.moveTo(0, yt);
//                LinesPath.lineTo(getMeasuredWidth(), yt);
//                yt -= 50 * scale_value;
//            }
//        }
//
//        canvas.drawPath(LinesPath, LinesPaint);

        if (path != null) canvas.drawPath(path, paint);
        if (circles_path != null) canvas.drawPath(circles_path, full_paint);
        //for(int x=0;x<getChildCount();x++) getChildAt(x).invalidate();
        for (Path p : paths) canvas.drawPath(p, paint);
        for (Path p : cpaths) canvas.drawPath(p, full_paint);

    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        mScaleGestureDetector.onTouchEvent(motionEvent);
        //if(twoFingers) return super.onTouchEvent(motionEvent);
        if (motionEvent.getPointerCount() > 1) {
            twoFingers = true;
            return super.onTouchEvent(motionEvent);
        } else if (twoFingers) {
            DownPT.x = motionEvent.getX();
            DownPT.y = motionEvent.getY();
					/*
					for(int x=0;x<getChildCount();x++){
					View _view = getChildAt(x);
					String tag=(_view.getTag()!=null)?_view.getTag().toString():"";
					if(tag.equals("skip")) continue;
					StartPT.put(_view,new PointF(_view.getX(), _view.getY()));
					    }
					    */
            twoFingers = false;
        }

        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_MOVE:
                PointF mv = new PointF(motionEvent.getX() - DownPT.x, motionEvent.getY() - DownPT.y);
                wx += mv.x;
                wy += mv.y;
                for (int x = 0; x < getChildCount(); x++) {
                    View _view = getChildAt(x);
                    String tag = (_view.getTag() != null) ? _view.getTag().toString() : "";
                    if (tag.equals("skip")) continue;
                    _view.setX(_view.getX() + mv.x);
                    _view.setY(_view.getY() + mv.y);
//StartPT.put(_view,new PointF(_view.getX(), _view.getY()));
                    DownPT.x = motionEvent.getX();
                    DownPT.y = motionEvent.getY();
                }
                break;
            case MotionEvent.ACTION_DOWN:
                twoFingers = false;
                DownPT.x = motionEvent.getX();
                DownPT.y = motionEvent.getY();
/*
for(int x=0;x<getChildCount();x++){
View _view = getChildAt(x);
String tag=(_view.getTag()!=null)?_view.getTag().toString():"";
if(tag.equals("skip")) continue;
StartPT.put(_view,new PointF(_view.getX(), _view.getY()));
}
*/
                break;
            case MotionEvent.ACTION_UP:
                twoFingers = false;
                break;
            default:
                break;
        }
        return super.onTouchEvent(motionEvent);
    }

}