package com.star4droid.star2d.ui.blocks;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/**
 * Shows one block exactly as it looks on the canvas (shape, fields, colors), scaled down only when the
 * space is narrower than the block. Used for the palette rows and for the block that follows the finger.
 */
public class BlockPreviewView extends View {
    private final BlockCanvas painter;
    private BlockCanvas.Prepared block;
    private float scale = 1f;
    private boolean alignEnd;

    public BlockPreviewView(Context c, BlockCanvas painter) {
        super(c);
        this.painter = painter;
    }

    public void setBlock(BlockCanvas.Prepared p, boolean alignEnd) {
        this.block = p;
        this.alignEnd = alignEnd;
        requestLayout();
        invalidate();
    }

    public BlockCanvas.Prepared block() { return block; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        if (block == null) { setMeasuredDimension(0, 0); return; }
        float bw = block.bounds.width() + getPaddingLeft() + getPaddingRight();
        float bh = block.bounds.height() + getPaddingTop() + getPaddingBottom();
        int mode = MeasureSpec.getMode(wSpec), size = MeasureSpec.getSize(wSpec);
        scale = mode != MeasureSpec.UNSPECIFIED && size > 0 && bw > size ? size / bw : 1f;
        int w = mode == MeasureSpec.EXACTLY ? size : (int) Math.ceil(bw * scale);
        setMeasuredDimension(w, (int) Math.ceil(bh * scale));
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (block == null) return;
        c.save();
        float bw = (block.bounds.width() + getPaddingLeft() + getPaddingRight()) * scale;
        if (alignEnd) c.translate(getWidth() - bw, 0);
        c.scale(scale, scale);
        c.translate(getPaddingLeft(), getPaddingTop());
        painter.draw(c, block, isPressed());
        c.restore();
    }
}
