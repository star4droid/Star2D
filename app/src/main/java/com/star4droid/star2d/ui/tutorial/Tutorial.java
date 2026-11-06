package com.star4droid.star2d.ui.tutorial;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Interactive lessons. Two small windows sit above the app (and above its dialogs):
 * a full-screen "shade" that dims everything except the target, shows an animated hand and lets
 * touches through only on the target, and a text bubble with Next / Skip / Auto. The engine checks
 * the current step every few hundred milliseconds and moves on when its condition is met.
 */
public final class Tutorial {
    private Tutorial() {}

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Lesson lesson;
    private static int index = -1;
    private static File project;
    private static boolean running, paused, auto, ready, ending, resumed;
    /** Auto explain speed (1, 2 or 3). */
    private static int speed = 1;
    private static Activity act;
    private static Shade shade;
    private static Bubble bubble;
    private static View pauseChip;
    private static boolean shadeAdded, bubbleAdded, chipAdded, shadeTouchable = true;
    private static View lastRoot;
    private static View revealed;
    private static long stepStart, typedAt, autoAt;
    private static boolean autoDone, stepCompleted, skipping;
    private static ArrayList<Bridge.ElemState> elems = new ArrayList<>();
    private static String glProject = "";
    private static boolean glPending;
    private static long glAsked;
    /** Scratch values for lessons (counts before a step, names...). */
    public static final HashMap<String, Object> vars = new HashMap<>();

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) {
                act = a;
                resumed = true;
                if (running) H.post(Tutorial::attach);
            }
            @Override public void onActivityPaused(Activity a) {
                if (a == act) {
                    resumed = false;
                    detach();
                }
            }
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {
                if (a == act) {
                    detach();
                    act = null;
                    shade = null;
                    bubble = null;
                    pauseChip = null;
                }
            }
        });
    }

    public static boolean isRunning() { return running; }

    /** The editor closed its project: a started lesson ends (the project stays). */
    public static void onProjectClosed() {
        if (running && ready) stop(true);
    }
    public static Activity activity() { return act; }
    public static File project() { return project; }
    public static ArrayList<Bridge.ElemState> elements() { return elems; }
    public static String glProjectPath() { return glProject; }

    /** Start a lesson for a project that is being opened. */
    public static void begin(Lesson l, File projectDir) {
        if (running) stop(false);
        lesson = l;
        project = projectDir;
        index = -1;
        running = true;
        paused = false;
        auto = false;
        ready = false;
        ending = false;
        vars.clear();
        elems = new ArrayList<>();
        glProject = "";
        H.removeCallbacks(TICK);
        H.postDelayed(TICK, 300);
        if (act != null) H.post(Tutorial::attach);
    }

    public static void stop(boolean message) {
        if (running && ready) saveScene();
        running = false;
        auto = false;
        H.removeCallbacks(TICK);
        detach();
        if (message) Snack.show(t("end_free", "Lesson closed. The project is yours: keep building!"));
    }

    private static Step step() {
        return lesson == null || index < 0 || index >= lesson.steps.size() ? null : lesson.steps.get(index);
    }

    static String t(String k, String d) { return Tr.t("tut_" + k, d); }

    // ================================================================ windows

    private static WindowManager.LayoutParams params(boolean full, boolean touchable) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                full ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                full ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | (full ? 0 : WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                        | (touchable ? 0 : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.windowAnimations = 0;
        if (android.os.Build.VERSION.SDK_INT >= 28)
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if (android.os.Build.VERSION.SDK_INT >= 30) lp.setFitInsetsTypes(0);
        return lp;
    }

    private static void attach() {
        if (!running || !resumed || act == null || act.isFinishing()) return;
        try {
            WindowManager wm = act.getWindowManager();
            if (paused) {
                // paused: the editor stays locked (only "Continue" / "End" work) so nothing gets broken
                if (bubbleAdded) { wm.removeViewImmediate(bubble); bubbleAdded = false; }
                if (shade == null || shade.getContext() != act) {
                    if (shadeAdded) { wm.removeViewImmediate(shade); shadeAdded = false; }
                    shade = new Shade(act);
                    bubble = new Bubble(act);
                }
                shade.set(null, null, false, false);
                shadeTouchable = true;
                if (!shadeAdded) {
                    wm.addView(shade, params(true, true));
                    shadeAdded = true;
                }
                if (pauseChip == null || pauseChip.getContext() != act) pauseChip = buildChip(act);
                if (!chipAdded) {
                    WindowManager.LayoutParams lp = params(false, true);
                    lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    lp.y = Ui.dp(act, 56);
                    wm.addView(pauseChip, lp);
                    chipAdded = true;
                }
                return;
            }
            if (chipAdded) { wm.removeViewImmediate(pauseChip); chipAdded = false; }
            if (shade == null || shade.getContext() != act) {
                if (shadeAdded) detach();
                shade = new Shade(act);
                bubble = new Bubble(act);
                if (index >= 0) bubble.show(step(), index);
            }
            if (!shadeAdded) {
                wm.addView(shade, params(true, shadeTouchable));
                shadeAdded = true;
            }
            if (!bubbleAdded) {
                wm.addView(bubble, params(false, true));
                bubbleAdded = true;
            }
        } catch (Throwable ignored) {}
    }

    private static void detach() {
        if (act == null) return;
        WindowManager wm = act.getWindowManager();
        try { if (shadeAdded) wm.removeViewImmediate(shade); } catch (Throwable ignored) {}
        try { if (bubbleAdded) wm.removeViewImmediate(bubble); } catch (Throwable ignored) {}
        try { if (chipAdded) wm.removeViewImmediate(pauseChip); } catch (Throwable ignored) {}
        shadeAdded = bubbleAdded = chipAdded = false;
    }

    /** Put our windows above a dialog / menu that was opened after them. */
    private static void raise() {
        if (shade != null && shade.forwarding) return;
        detach();
        attach();
    }

    private static void setTouchable(boolean t) {
        if (t == shadeTouchable || shade == null) return;
        shadeTouchable = t;
        if (shadeAdded) {
            try {
                act.getWindowManager().updateViewLayout(shade, params(true, t));
            } catch (Throwable ignored) {}
        }
    }

    // ================================================================ engine

    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            if (!running) return;
            H.postDelayed(this, 180);
            try {
                tick();
            } catch (Throwable e) {
                // a lesson must never crash the editor
            }
        }
    };

    private static void refreshGl() {
        long now = SystemClock.uptimeMillis();
        if (glPending && now - glAsked < 3000) return;
        if (!Bridge.ready()) return;
        glPending = true;
        glAsked = now;
        Bridge.query(() -> {
            try {
                return new Object[]{Bridge.elements(), Bridge.editor().getProject().getPath()};
            } catch (Throwable t) {
                return null;
            }
        }, r -> {
            glPending = false;
            if (r == null) return;
            //noinspection unchecked
            elems = (ArrayList<Bridge.ElemState>) r[0];
            glProject = (String) r[1];
        });
    }

    private static boolean projectReady() {
        EditorShell sh = EditorShell.current;
        if (sh == null || act == null || !act.getClass().getSimpleName().equals("EditorActivity")) return false;
        if (!sh.isProjectOpen() || sh.isIndexing() || !sh.isShown()) return false;
        return project != null && new File(glProject).getAbsolutePath().equals(project.getAbsolutePath());
    }

    private static void tick() {
        if (act == null || paused || !resumed) return;
        refreshGl();
        if (!shadeAdded) attach();
        if (shade == null || bubble == null) return;
        if (!ready) {
            setTouchable(true);
            shade.set(null, null, false, false);
            bubble.waiting();
            if (projectReady()) {
                ready = true;
                go(0);
            }
            return;
        }
        Step s = step();
        if (s == null) return;
        Step.Found f = safe(s.target), f2 = safe(s.target2);
        if (f != null && f.view != null && f.view != revealed) {
            revealed = f.view;
            try {
                f.view.requestRectangleOnScreen(new android.graphics.Rect(0, 0, f.view.getWidth(), f.view.getHeight()), true);
            } catch (Throwable ignored) {}
            f = safe(s.target);
        }
        // the target lives in a dialog / popup opened after our windows: come on top again
        View root = f != null ? f.root : null;
        View decor = act.getWindow().getDecorView();
        if (root != null && root != decor && root != lastRoot && !shade.forwarding) {
            lastRoot = root;
            raise();
        } else if (root == null || root == decor) lastRoot = null;

        // touches pass everywhere only in steps made for it (playing the game)
        boolean free = s.free;
        setTouchable(!free);
        shade.set(f, f2, free, s.drag);
        bubble.place(f, f2);
        if (s.tick != null) s.tick.run();

        long now = SystemClock.uptimeMillis();
        if (s.done != null && !stepCompleted && ok(s.done)) {
            stepCompleted = true;
            bubble.success();
            H.postDelayed(() -> {
                if (running && step() == s) go(index + 1);
            }, 650);
            return;
        }
        if (auto && !stepCompleted && bubble.typed()) {
            if (typedAt == 0) typedAt = now;
            long read = Math.max(1600, s.text.length() * 38L) / speed;
            if (now - typedAt < read) return;
            if (s.isInfo()) {
                go(index + 1);
                return;
            }
            if (!autoDone) {
                if (autoAt == 0) {
                    autoAt = now;
                    shade.tapNow();
                } else if (now - autoAt > 900 / speed) {
                    autoDone = true;
                    perform(s, f);
                }
            } else if (now - autoAt > 9000) {
                // the action did not complete: give the control back
                auto = false;
                bubble.show(s, index);
            }
        }
    }

    /** No target for a while: don't lock the user out (e.g. a menu closed by mistake). */
    private static boolean waitingTooLong() {
        return SystemClock.uptimeMillis() - stepStart > 4000;
    }

    private static Step.Found safe(Step.Finder f) {
        if (f == null) return null;
        try {
            return f.find();
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean ok(Step.Cond c) {
        try {
            return c.ok();
        } catch (Throwable e) {
            return false;
        }
    }

    private static void perform(Step s, Step.Found f) {
        try {
            if (s.auto != null) s.auto.run();
            else if (f != null && f.view != null) f.view.performClick();
        } catch (Throwable ignored) {}
    }

    private static void go(int i) {
        if (lesson == null) return;
        if (i >= lesson.steps.size()) {
            finish();
            return;
        }
        index = i;
        stepStart = SystemClock.uptimeMillis();
        typedAt = 0;
        autoAt = 0;
        autoDone = false;
        stepCompleted = false;
        skipping = false;
        revealed = null;
        Step s = step();
        if (s.enter != null) {
            try { s.enter.run(); } catch (Throwable ignored) {}
        }
        if (shade != null) shade.reset();
        if (bubble != null) bubble.show(s, index);
    }

    /** Save the open scene (so the lesson's work is kept). */
    private static void saveScene() {
        try {
            if (Bridge.ready()) Bridge.gl(() -> Bridge.controls().actionSave());
        } catch (Throwable ignored) {}
    }

    private static void finish() {
        saveScene();
        running = false;
        H.removeCallbacks(TICK);
        detach();
        if (act == null) return;
        new AlertDialog.Builder(act)
                .setTitle("🎉 " + t("done_title", "Lesson complete!"))
                .setMessage(t("done_msg", "Great job! This project is yours now: change it, add more blocks and make it your own game."))
                .setPositiveButton(Tr.t("ok", "OK"), null)
                .show();
    }

    // ---------------------------------------------------------------- buttons

    static void next() {
        Step s = step();
        if (s == null) return;
        if (s.isInfo()) go(index + 1);
    }

    /** Do this step for the user, then continue. */
    static void skip() {
        Step s = step();
        if (s == null) return;
        if (s.isInfo()) {
            go(index + 1);
            return;
        }
        if (skipping) {
            // the action could not be done automatically: move on anyway
            go(index + 1);
            return;
        }
        skipping = true;
        perform(s, safe(s.target));
        H.postDelayed(() -> {
            if (running && step() == s && !stepCompleted) bubble.show(s, index);
        }, 2500);
    }

    static void toggleAuto() {
        auto = !auto;
        typedAt = 0;
        autoAt = 0;
        autoDone = false;
        if (bubble != null) bubble.show(step(), index);
    }

    static void askEnd() {
        if (act == null) return;
        new AlertDialog.Builder(act)
                .setTitle(t("end_title", "End the lesson?"))
                .setMessage(t("end_msg", "You can keep the project and continue on your own."))
                .setPositiveButton(t("end", "End lesson"), (d, w) -> stop(true))
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    static void pause() {
        paused = true;
        auto = false;
        detach();
        attach();
    }

    static void resume() {
        paused = false;
        detach();
        attach();
        if (bubble != null) bubble.show(step(), index);
    }

    /** Back key: offer to pause. Returns true when consumed. */
    public static boolean onBack(Activity a) {
        if (!running) return false;
        if (paused) {
            // the editor is locked while paused: Back continues the lesson
            resume();
            return true;
        }
        Step s = step();
        if (s != null && s.allowBack) return false;
        if (!ready) return false;
        new AlertDialog.Builder(a)
                .setTitle(t("pause_title", "Pause the lesson?"))
                .setMessage(t("pause_msg", "While paused you can use the editor freely. Tap \"Continue lesson\" at the top to come back."))
                .setPositiveButton(t("pause", "Pause"), (d, w) -> pause())
                .setNegativeButton(t("continue", "Continue"), null)
                .setNeutralButton(t("end", "End lesson"), (d, w) -> stop(true))
                .show();
        return true;
    }

    private static View buildChip(Context c) {
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        row.setBackground(Ui.rounded(0xF0263238, Ui.dp(c, 22), 0x55FFFFFF, 1));
        row.setElevation(Ui.dp(c, 8));
        row.addView(Ui.smallIcon(c, R.drawable.ic_s2_school, 0xFF81D4FA, 18));
        TextView t = Ui.text(c, t("paused", "Lesson paused"), 13, Ui.TEXT);
        t.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 10), 0);
        row.addView(t);
        TextView go = Ui.text(c, "▶ " + t("continue_lesson", "Continue lesson"), 13, 0xFFFFFFFF);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        go.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 16)));
        go.setOnClickListener(v -> resume());
        row.addView(go);
        ImageView end = Ui.smallIcon(c, R.drawable.ic_s2_close, Ui.TEXT_2, 18);
        int p = Ui.dp(c, 8);
        end.setPadding(p, p, p, p);
        end.setBackground(Ui.ripple(0, Ui.dp(c, 17)));
        end.setContentDescription(t("end", "End lesson"));
        end.setOnClickListener(v -> askEnd());
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34));
        el.setMarginStart(Ui.dp(c, 6));
        row.addView(end, el);
        return row;
    }

    // ================================================================ shade (dim + hole + hand)

    @SuppressLint("ViewConstructor")
    static final class Shade extends View {
        final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG), dash = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Drawable hand, handShadow;
        final float d;
        RectF hole, hole2;
        View fwdRoot;
        Step.Found target;
        boolean free, drag, forwarding;
        float hx = -1, hy = -1; // hand position (screen)
        long moveStart, tapStart, nudgeAt;
        float fromX, fromY;

        Shade(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            dim.setColor(0xA6000000);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(3 * d);
            ring.setColor(0xFF4FC3F7);
            dash.setStyle(Paint.Style.STROKE);
            dash.setStrokeWidth(2.5f * d);
            dash.setColor(0xFFFFFFFF);
            dash.setPathEffect(new DashPathEffect(new float[]{9 * d, 7 * d}, 0));
            hand = ContextCompat.getDrawable(c, R.drawable.ic_s2_pointer).mutate();
            hand.setTint(Color.WHITE);
            handShadow = ContextCompat.getDrawable(c, R.drawable.ic_s2_pointer).mutate();
            handShadow.setTint(0xCC000000);
        }

        void reset() {
            hole = hole2 = null;
            moveStart = SystemClock.uptimeMillis();
            fromX = hx;
            fromY = hy;
        }

        void tapNow() {
            tapStart = SystemClock.uptimeMillis();
            invalidate();
        }

        void set(Step.Found f, Step.Found f2, boolean free, boolean drag) {
            target = f;
            this.free = free;
            this.drag = drag && f2 != null;
            RectF nh = f == null ? null : new RectF(f.r);
            if (nh != null) nh.inset(-6 * d, -6 * d);
            if (nh != null && (hole == null || Math.abs(nh.centerX() - hole.centerX()) > 2 || Math.abs(nh.centerY() - hole.centerY()) > 2)) {
                fromX = hx < 0 ? nh.centerX() + 120 * d : hx;
                fromY = hy < 0 ? nh.centerY() + 160 * d : hy;
                moveStart = SystemClock.uptimeMillis();
            }
            hole = nh;
            hole2 = f2 == null ? null : new RectF(f2.r);
            if (hole2 != null) hole2.inset(-4 * d, -4 * d);
            invalidate();
        }

        private int[] loc() {
            int[] l = new int[2];
            getLocationOnScreen(l);
            return l;
        }

        @Override
        protected void onDraw(Canvas c) {
            int[] l = loc();
            c.save();
            c.translate(-l[0], -l[1]);
            long now = SystemClock.uptimeMillis();
            float pulse = (float) (0.5 + 0.5 * Math.sin(now / 260.0));
            if (!free) {
                Path p = new Path();
                p.setFillType(Path.FillType.EVEN_ODD);
                p.addRect(-10000, -10000, 20000, 20000, Path.Direction.CW);
                if (hole != null) p.addRoundRect(hole, 12 * d, 12 * d, Path.Direction.CW);
                if (hole2 != null) p.addRoundRect(hole2, 10 * d, 10 * d, Path.Direction.CW);
                c.drawPath(p, dim);
            }
            if (hole != null) {
                ring.setAlpha((int) (140 + 115 * pulse));
                c.drawRoundRect(hole, 12 * d, 12 * d, ring);
                RectF glow = new RectF(hole);
                glow.inset(-5 * d * pulse, -5 * d * pulse);
                ring.setAlpha((int) (90 * (1 - pulse)));
                c.drawRoundRect(glow, 14 * d, 14 * d, ring);
            }
            if (hole2 != null) c.drawRoundRect(hole2, 10 * d, 10 * d, dash);
            // hand
            if (hole != null) {
                float tx = hole.centerX(), ty = hole.centerY();
                float px, py;
                if (drag && hole2 != null) {
                    // loop: press on target, travel to the drop zone, release
                    float cycle = 2200f, k = ((now - moveStart) % (long) cycle) / cycle;
                    float m = k < 0.25f ? 0 : (k > 0.8f ? 1 : (k - 0.25f) / 0.55f);
                    m = (float) (0.5 - 0.5 * Math.cos(Math.PI * m));
                    px = tx + (hole2.centerX() - tx) * m;
                    py = ty + (hole2.centerY() - ty) * m;
                } else {
                    float k = Math.min(1f, (now - moveStart) / 650f);
                    k = (float) (1 - Math.pow(1 - k, 3));
                    px = fromX + (tx - fromX) * k;
                    py = fromY + (ty - fromY) * k;
                }
                if (now - nudgeAt < 500) px += (float) Math.sin((now - nudgeAt) / 30.0) * 8 * d;
                hx = px;
                hy = py;
                boolean pressing = drag ? true : (now - tapStart < 500 || ((now - moveStart) > 650 && ((now - moveStart) % 1400) < 260));
                float s = 44 * d * (pressing ? 0.86f : 1f);
                if (pressing && !drag) {
                    ring.setAlpha(160);
                    float rr = 10 * d + 26 * d * (((now - moveStart) % 1400) / 260f);
                    c.drawCircle(px, py, Math.min(rr, 30 * d), ring);
                }
                // fingertip of the pointer icon is at about (0.45, 0.18) of its box
                int left = (int) (px - s * 0.48f), top = (int) (py - s * 0.14f);
                handShadow.setBounds(left + (int) (2 * d), top + (int) (3 * d), left + (int) s + (int) (2 * d), top + (int) s + (int) (3 * d));
                handShadow.draw(c);
                hand.setBounds(left, top, left + (int) s, top + (int) s);
                hand.draw(c);
            }
            c.restore();
            postInvalidateOnAnimation();
        }

        private boolean inTarget(float rx, float ry) {
            if (hole == null) return false;
            RectF r = new RectF(hole);
            r.inset(-4 * d, -4 * d);
            return r.contains(rx, ry);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                Step cur = step();
                forwarding = target != null && cur != null && !cur.isInfo() && inTarget(e.getRawX(), e.getRawY());
                fwdRoot = target == null ? null : (target.root != null ? target.root : (act == null ? null : act.getWindow().getDecorView()));
                if (!forwarding) {
                    nudgeAt = SystemClock.uptimeMillis();
                    if (bubble != null) bubble.nudge();
                    return true;
                }
            }
            if (forwarding) {
                forward(e);
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) forwarding = false;
            }
            return true;
        }

        private void forward(MotionEvent e) {
            View root = fwdRoot;
            if (root == null || !root.isAttachedToWindow()) return;
            int[] me = loc(), r = new int[2];
            root.getLocationOnScreen(r);
            MotionEvent c = MotionEvent.obtain(e);
            c.offsetLocation(me[0] - r[0], me[1] - r[1]);
            try {
                root.dispatchTouchEvent(c);
            } catch (Throwable ignored) {}
            c.recycle();
        }
    }

    // ================================================================ bubble

    @SuppressLint("ViewConstructor")
    static final class Bubble extends LinearLayout {
        final TextView progress, text;
        final LinearLayout buttons;
        final ProgressBar spinner;
        final Context c;
        String full = "";
        int shown = 0;
        final Runnable typer = new Runnable() {
            @Override public void run() {
                if (shown >= full.length()) return;
                shown = Math.min(full.length(), shown + (full.length() > 160 ? 2 : 1) * speed);
                text.setText(full.substring(0, shown));
                if (shown < full.length()) H.postDelayed(this, Math.max(8, 24 / speed));
            }
        };

        Bubble(Context c) {
            super(c);
            this.c = c;
            setOrientation(VERTICAL);
            setBackground(Ui.rounded(0xF71E2329, Ui.dp(c, 16), 0x664FC3F7, Ui.dp(c, 1.5f)));
            setElevation(Ui.dp(c, 14));
            setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 8));
            setLayoutDirection(Ui.isRtl(c) ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
            LinearLayout head = Ui.row(c);
            head.addView(Ui.smallIcon(c, R.drawable.ic_s2_school, 0xFF81D4FA, 18));
            progress = Ui.text(c, "", 11.5f, 0xFF81D4FA);
            progress.setTypeface(Typeface.DEFAULT_BOLD);
            progress.setPadding(Ui.dp(c, 8), 0, 0, 0);
            progress.setMaxLines(1);
            head.addView(progress, Ui.weight(1));
            ImageView pause = small(R.drawable.ic_s2_pause, Tr.t("tut_pause", "Pause"));
            pause.setOnClickListener(v -> Tutorial.pause());
            head.addView(pause);
            ImageView close = small(R.drawable.ic_s2_close, Tr.t("tut_end", "End lesson"));
            close.setOnClickListener(v -> askEnd());
            head.addView(close);
            addView(head, Ui.matchWrap());
            text = Ui.text(c, "", 15, Ui.TEXT);
            text.setLineSpacing(0, 1.15f);
            text.setPadding(Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 8));
            text.setOnClickListener(v -> {
                H.removeCallbacks(typer);
                shown = full.length();
                text.setText(full);
            });
            addView(text, Ui.matchWrap());
            spinner = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            spinner.setIndeterminate(true);
            spinner.setVisibility(GONE);
            addView(spinner, Ui.matchWrap());
            buttons = Ui.row(c);
            addView(buttons, Ui.matchWrap());
        }

        private ImageView small(int icon, String label) {
            ImageView i = Ui.smallIcon(c, icon, Ui.TEXT_2, 18);
            int s = Ui.dp(c, 34);
            i.setLayoutParams(new LinearLayout.LayoutParams(s, s));
            int p = Ui.dp(c, 8);
            i.setPadding(p, p, p, p);
            i.setBackground(Ui.ripple(0, Ui.dp(c, 17)));
            i.setContentDescription(label);
            return i;
        }

        private TextView button(String label, boolean primary, View.OnClickListener l) {
            TextView b = Ui.text(c, label, 13, primary ? 0xFFFFFFFF : Ui.TEXT);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setGravity(Gravity.CENTER);
            b.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
            b.setBackground(primary ? Ui.ripple(Ui.ACCENT, Ui.dp(c, 16)) : Ui.ripple(Ui.SURFACE_3, Ui.dp(c, 16)));
            b.setOnClickListener(l);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginStart(Ui.dp(c, 6));
            b.setLayoutParams(lp);
            return b;
        }

        void type(String s) {
            H.removeCallbacks(typer);
            full = s == null ? "" : s;
            shown = 0;
            text.setText("");
            H.post(typer);
        }

        boolean typed() {
            return shown >= full.length();
        }

        void waiting() {
            if (spinner.getVisibility() == VISIBLE) return;
            progress.setText(lesson == null ? "" : lesson.title);
            spinner.setVisibility(VISIBLE);
            buttons.removeAllViews();
            type(t("preparing", "Preparing your lesson project… please wait a moment."));
            place(null, null);
        }

        void show(Step s, int i) {
            if (s == null) return;
            spinner.setVisibility(GONE);
            progress.setText((lesson == null ? "" : lesson.title + "  ·  ") + (i + 1) + "/" + (lesson == null ? 0 : lesson.steps.size()));
            if (!full.equals(s.text)) type(s.text);
            buttons.removeAllViews();
            buttons.addView(button(auto ? "■ " + t("stop", "Stop") : "▶▶ " + t("auto", "Auto explain"), false, v -> toggleAuto()));
            if (auto) {
                // speed of the automatic explanation: ×1 → ×2 → ×3
                TextView sp1 = button("×" + speed, true, v -> {
                    speed = speed >= 3 ? 1 : speed + 1;
                    show(step(), index);
                });
                Tut.tag(sp1, "tut:speed");
                buttons.addView(sp1);
            }
            View sp = new View(c);
            // a plain View measures to the whole available height when WRAP_CONTENT: keep it 1px tall
            buttons.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
            if (!auto) {
                if (s.isInfo()) buttons.addView(button(t("next", "Next") + (Ui.isRtl(c) ? "  ←" : "  →"), true, v -> next()));
                else buttons.addView(button(t("skip", "Skip") + "  ⏭", false, v -> skip()));
            }
            place(shade == null ? null : shade.target, null);
        }

        void success() {
            buttons.removeAllViews();
            TextView ok = Ui.text(c, "✓ " + t("well_done", "Well done!"), 14, 0xFF81C784);
            ok.setTypeface(Typeface.DEFAULT_BOLD);
            buttons.addView(ok);
        }

        void nudge() {
            animate().cancel();
            setTranslationX(0);
            animate().translationX(Ui.dp(c, 6)).setDuration(60).withEndAction(() ->
                    animate().translationX(-Ui.dp(c, 6)).setDuration(60).withEndAction(() ->
                            animate().translationX(0).setDuration(60).start()).start()).start();
        }

        /** Keep the bubble inside the screen and away from the targets. */
        void place(Step.Found f, Step.Found f2) {
            if (!bubbleAdded || act == null) return;
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            act.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            int sw = dm.widthPixels, sh = dm.heightPixels;
            // keep the card out of the navigation / status bars and the display cutout (edge-to-edge, any rotation)
            android.graphics.Rect safe = com.star4droid.star2d.ui.SystemBars.safeInsets(act);
            int m = Ui.dp(c, 12);
            int w = Math.min(sw - safe.left - safe.right - 2 * m, Ui.dp(c, 400));
            measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(sh, MeasureSpec.AT_MOST));
            int h = getMeasuredHeight();
            RectF avoid = f == null ? null : new RectF(f.r);
            if (avoid != null) avoid.inset(-Ui.dp(c, 18), -Ui.dp(c, 18));
            RectF avoid2 = f2 == null ? null : new RectF(f2.r);
            if (avoid2 != null) avoid2.inset(-Ui.dp(c, 12), -Ui.dp(c, 12));
            int top = Math.max(Ui.dp(c, 30), safe.top + m);
            int minX = safe.left + m, maxX = sw - safe.right - w - m;
            int bottomLimit = sh - safe.bottom;
            float cx = avoid == null ? sw / 2f : avoid.centerX();
            int x = (int) Math.max(minX, Math.min(cx - w / 2f, maxX));
            int[] ys = avoid == null ? new int[]{bottomLimit - h - Ui.dp(c, 24), top}
                    : new int[]{(int) avoid.bottom, (int) avoid.top - h, top, bottomLimit - h - Ui.dp(c, 24)};
            int best = ys[0], bestOverlap = Integer.MAX_VALUE;
            for (int y : ys) {
                int yy = Math.max(top, Math.min(y, bottomLimit - h - m));
                RectF b = new RectF(x, yy, x + w, yy + h);
                int ov = overlap(b, avoid) * 3 + overlap(b, avoid2);
                if (ov < bestOverlap) {
                    bestOverlap = ov;
                    best = yy;
                }
                if (ov == 0) break;
            }
            // try the sides in landscape when nothing fits above / below
            if (bestOverlap > 0 && avoid != null && sw > sh) {
                for (int xx : new int[]{minX, maxX}) {
                    int yy = Math.max(top, Math.min((int) (avoid.centerY() - h / 2f), bottomLimit - h - m));
                    RectF b = new RectF(xx, yy, xx + w, yy + h);
                    int ov = overlap(b, avoid) * 3 + overlap(b, avoid2);
                    if (ov < bestOverlap) {
                        bestOverlap = ov;
                        best = yy;
                        x = xx;
                    }
                }
            }
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
            if (lp == null) return;
            if (lp.x == x && lp.y == best && lp.width == w) return;
            lp.x = x;
            lp.y = best;
            lp.width = w;
            try {
                act.getWindowManager().updateViewLayout(this, lp);
            } catch (Throwable ignored) {}
        }

        private static int overlap(RectF a, RectF b) {
            if (b == null) return 0;
            RectF r = new RectF();
            if (!r.setIntersect(a, b)) return 0;
            return (int) (r.width() * r.height());
        }
    }
}
