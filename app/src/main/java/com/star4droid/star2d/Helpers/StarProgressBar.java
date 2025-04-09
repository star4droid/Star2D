package com.star4droid.star2d.Helpers;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class StarProgressBar extends View {

    private int progress;
    private int max;
    private int progressColor;
    private int backColor;

    private Paint progressPaint;
    private Paint backPaint;

    public StarProgressBar(Context context) {
        super(context);
        init();
    }

    public StarProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StarProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        progress = 0;
        max = 100;
        progressColor = Color.GREEN;
        backColor = Color.GRAY;

        progressPaint = new Paint();
        progressPaint.setColor(progressColor);

        backPaint = new Paint();
        backPaint.setColor(backColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Draw background
        canvas.drawRect(0, 0, width, height, backPaint);

        // Calculate progress width
        int progressWidth = (int) (((float) progress / max) * width);

        // Draw progress
        canvas.drawRect(0, 0, progressWidth, height, progressPaint);
    }

    public void setProgressColor(int color) {
        progressColor = color;
        progressPaint.setColor(progressColor);
        invalidate();
    }

    public void setBackColor(int color) {
        backColor = color;
        backPaint.setColor(backColor);
        invalidate();
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
        invalidate();
    }

    public void setMax(int max) {
        this.max = max;
        invalidate();
    }
}