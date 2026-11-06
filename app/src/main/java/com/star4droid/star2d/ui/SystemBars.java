package com.star4droid.star2d.ui;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge (targetSdk 35+) draws every activity under the status bar, the navigation bar and the display cutout.
 * {@link #install} pads the content view of EVERY activity of the app by those insets (+ the keyboard for adjustResize
 * windows), in portrait and landscape, so bottom buttons are never hidden under the navigation bar.
 */
public final class SystemBars {
    private static boolean installed;
    private static final int TAG = 0x5b4a11e7;

    private SystemBars() {}

    /** Registers the insets handling for all activities (idempotent) and applies it to {@code current} right away. */
    public static void install(Activity current) {
        if (current == null) return;
        if (!installed) {
            installed = true;
            current.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity a, Bundle b) {}
                @Override public void onActivityStarted(Activity a) { apply(a); }
                @Override public void onActivityResumed(Activity a) {}
                @Override public void onActivityPaused(Activity a) {}
                @Override public void onActivityStopped(Activity a) {}
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                @Override public void onActivityDestroyed(Activity a) {}
            });
        }
        apply(current);
    }

    /** Pads the activity's content view by system bars + display cutout (+ IME when the window resizes for it). */
    public static void apply(Activity a) {
        try {
            View content = a.findViewById(android.R.id.content);
            if (content == null || content.getTag(TAG) != null) return;
            content.setTag(TAG, Boolean.TRUE);
            int mode = a.getWindow().getAttributes().softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
            boolean padIme = mode != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN && mode != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                int bottom = bars.bottom;
                if (padIme) bottom = Math.max(bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
                // some vendors report absurd insets (huge top bar): never pad a side by more than 96dp (keyboard excluded)
                int cap = (int) (96 * v.getResources().getDisplayMetrics().density);
                int barB = Math.min(bars.bottom, cap);
                v.setPadding(Math.min(bars.left, cap), Math.min(bars.top, cap), Math.min(bars.right, cap), Math.max(barB, bottom == bars.bottom ? barB : bottom));
                return WindowInsetsCompat.CONSUMED;
            });
            ViewCompat.requestApplyInsets(content);
        } catch (Throwable ignored) {}
    }

    /** Current system bar + cutout insets of the activity window, in px (empty when unknown). */
    public static Rect safeInsets(Activity a) {
        Rect r = new Rect();
        try {
            WindowInsetsCompat in = ViewCompat.getRootWindowInsets(a.getWindow().getDecorView());
            if (in != null) {
                Insets i = in.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                r.set(i.left, i.top, i.right, i.bottom);
            }
        } catch (Throwable ignored) {}
        return r;
    }
}
