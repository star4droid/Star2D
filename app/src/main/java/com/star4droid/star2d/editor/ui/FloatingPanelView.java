package com.star4droid.star2d.editor.ui;

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingPanelView extends FrameLayout {

    private final TextView titleText;
    private final FrameLayout contentContainer;
    private final ImageButton closeButton;
    private final View resizeHandle;
    private final LinearLayout titleBar;

    private float lastTouchX, lastTouchY;
    private float initialX, initialY;
    private float initialWidth, initialHeight;
    private boolean isDragging = false;
    private boolean isResizing = false;
    private static final int MIN_WIDTH = 200;
    private static final int MIN_HEIGHT = 150;
    private static final int RESIZE_HANDLE_SIZE = 32;
    private boolean visible = false;
    private float panelX = -1, panelY = -1;
    private float panelWidth = -1, panelHeight = -1;

    public FloatingPanelView(Context context, String title) {
        super(context);
        setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        setBackgroundResource(R.drawable.floating_panel_bg);
        setElevation(12f);
        setClipChildren(true);

        setLayoutDirection(context.getResources().getConfiguration().getLayoutDirection());

        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        titleBar = new LinearLayout(context);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setBackgroundResource(R.drawable.floating_panel_title_bg);
        titleBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 44));
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(4, 0, 4, 0);

        titleText = new TextView(context);
        titleText.setText(title);
        titleText.setTextAppearance(R.style.FloatingPanelTitle);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        closeButton = new ImageButton(context);
        closeButton.setImageResource(R.drawable.ic_delete_black);
        closeButton.setBackgroundResource(android.R.color.transparent);
        closeButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        closeButton.setPadding(8, 8, 8, 8);
        closeButton.setLayoutParams(new LinearLayout.LayoutParams(44, 44));
        closeButton.setOnClickListener(v -> hide());

        titleBar.addView(titleText);
        titleBar.addView(closeButton);

        contentContainer = new FrameLayout(context);
        contentContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        contentContainer.setPadding(8, 8, 8, 8);

        resizeHandle = new View(context);
        resizeHandle.setLayoutParams(new FrameLayout.LayoutParams(RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE));
        resizeHandle.setBackgroundResource(R.drawable.resize_handle);
        ((FrameLayout.LayoutParams) resizeHandle.getLayoutParams()).gravity =
                Gravity.BOTTOM | Gravity.END;
        resizeHandle.setOnTouchListener(new ResizeTouchListener());

        mainLayout.addView(titleBar);
        mainLayout.addView(contentContainer);
        addView(mainLayout);
        addView(resizeHandle);

        titleBar.setOnTouchListener(new DragTouchListener());
        setOnTouchListener((v, event) -> true);
    }

    public void setContent(View view) {
        contentContainer.removeAllViews();
        contentContainer.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    public View getContentContainer() {
        return contentContainer;
    }

    public void show(FrameLayout parent) {
        if (visible) return;
        visible = true;
        parent.addView(this);
        if (panelWidth > 0 && panelHeight > 0) {
            setLayoutParams(new FrameLayout.LayoutParams(
                    (int) panelWidth, (int) panelHeight));
        } else {
            int defaultW = (int) (parent.getWidth() * 0.4f);
            int defaultH = (int) (parent.getHeight() * 0.4f);
            if (defaultW < MIN_WIDTH) defaultW = MIN_WIDTH;
            if (defaultH < MIN_HEIGHT) defaultH = MIN_HEIGHT;
            setLayoutParams(new FrameLayout.LayoutParams(defaultW, defaultH));
        }
        if (panelX >= 0 && panelY >= 0) {
            setX(panelX);
            setY(panelY);
        } else {
            setX((parent.getWidth() - getLayoutParams().width) / 2f);
            setY((parent.getHeight() - getLayoutParams().height) / 3f);
        }
        requestLayout();
    }

    public void hide() {
        if (!visible) return;
        visible = false;
        panelX = getX();
        panelY = getY();
        panelWidth = getWidth();
        panelHeight = getHeight();
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) parent.removeView(this);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setTitle(String title) {
        titleText.setText(title);
    }

    private class DragTouchListener implements OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging = true;
                    isResizing = false;
                    lastTouchX = event.getRawX();
                    lastTouchY = event.getRawY();
                    initialX = getX();
                    initialY = getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (isDragging) {
                        float dx = event.getRawX() - lastTouchX;
                        float dy = event.getRawY() - lastTouchY;
                        float newX = initialX + dx;
                        float newY = initialY + dy;
                        ViewGroup parent = (ViewGroup) getParent();
                        if (parent != null) {
                            newX = Math.max(0, Math.min(newX, parent.getWidth() - getWidth()));
                            newY = Math.max(0, Math.min(newY, parent.getHeight() - getHeight()));
                        }
                        setX(newX);
                        setY(newY);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    return true;
            }
            return false;
        }
    }

    private class ResizeTouchListener implements OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    isResizing = true;
                    isDragging = false;
                    lastTouchX = event.getRawX();
                    lastTouchY = event.getRawY();
                    initialWidth = getWidth();
                    initialHeight = getHeight();
                    initialX = getX();
                    initialY = getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (isResizing) {
                        float dx = event.getRawX() - lastTouchX;
                        float dy = event.getRawY() - lastTouchY;
                        float newWidth = Math.max(MIN_WIDTH, initialWidth + dx);
                        float newHeight = Math.max(MIN_HEIGHT, initialHeight + dy);
                        ViewGroup parent = (ViewGroup) getParent();
                        if (parent != null) {
                            newWidth = Math.min(newWidth, parent.getWidth() - getX());
                            newHeight = Math.min(newHeight, parent.getHeight() - getY());
                        }
                        ViewGroup.LayoutParams lp = getLayoutParams();
                        lp.width = (int) newWidth;
                        lp.height = (int) newHeight;
                        setLayoutParams(lp);
                        requestLayout();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isResizing = false;
                    return true;
            }
            return false;
        }
    }
}