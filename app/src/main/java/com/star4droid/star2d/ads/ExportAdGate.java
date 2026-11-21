package com.star4droid.star2d.ads;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/**
 * The short wait before an APK export: a 5 second countdown while the full screen (video) ad loads. The ad opens as soon as
 * it is ready and the export starts when it closes. If the countdown ends without an ad, a message asks for support and
 * offers "wait again" (5 more seconds) or "export without an ad": nobody is ever blocked (no internet, no fill).
 * Users without ads (no ad ids in the build, credits of $1 or more, active reward) export at once.
 */
public final class ExportAdGate {
    private static final int SECONDS = 5;
    private static final long MIN_SHOW_MS = 900;

    private final Activity a;
    private final Runnable export;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Dialog dlg;
    private final LinearLayout waitBox, askBox;
    private final TextView count, waitText;
    private boolean finished;
    private long started;
    private int left;

    /** Runs {@code export} after the ad (or right away when this user has no ads). */
    public static void run(Activity a, Runnable export) {
        boolean ads;
        try { ads = AdsManager.adsEnabled(a); } catch (Throwable t) { ads = false; }
        if (!ads || a.isFinishing()) { export.run(); return; }
        try {
            new ExportAdGate(a, export).start();
        } catch (Throwable t) {
            export.run(); // the gate must never be the reason an export fails
        }
    }

    private ExportAdGate(Activity a, Runnable export) {
        this.a = a;
        this.export = export;
        Tr.init(a);
        dlg = new Dialog(a);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setCancelable(true);
        dlg.setCanceledOnTouchOutside(false);
        dlg.setOnCancelListener(d -> { finished = true; h.removeCallbacksAndMessages(null); }); // back = no export

        LinearLayout root = Ui.column(a);
        root.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(a, 18), Ui.BORDER, Ui.dp(a, 1)));
        root.setPadding(Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 16));

        // ---- state 1: countdown
        waitBox = Ui.column(a);
        waitBox.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout ring = new FrameLayout(a);
        ProgressBar pb = new ProgressBar(a);
        ring.addView(pb, new FrameLayout.LayoutParams(Ui.dp(a, 76), Ui.dp(a, 76), Gravity.CENTER));
        count = Ui.text(a, String.valueOf(SECONDS), 26, Ui.TEXT);
        count.setTypeface(Typeface.DEFAULT_BOLD);
        count.setGravity(Gravity.CENTER);
        ring.addView(count, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        waitBox.addView(ring, new LinearLayout.LayoutParams(Ui.dp(a, 84), Ui.dp(a, 84)));
        TextView wt = Ui.title(a, Tr.t("exad_title", "A short ad before the export"));
        wt.setGravity(Gravity.CENTER);
        wt.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 4));
        waitBox.addView(wt, Ui.matchWrap());
        waitText = Ui.text(a, "", 13.5f, Ui.TEXT_2);
        waitText.setGravity(Gravity.CENTER);
        waitText.setLineSpacing(0, 1.15f);
        waitBox.addView(waitText, Ui.matchWrap());
        root.addView(waitBox, Ui.matchWrap());

        // ---- state 2: no ad came
        askBox = Ui.column(a);
        askBox.setVisibility(View.GONE);
        TextView at = Ui.title(a, Tr.t("exad_ask_title", "Support the engine"));
        at.setGravity(Gravity.CENTER);
        at.setPadding(0, 0, 0, Ui.dp(a, 8));
        askBox.addView(at, Ui.matchWrap());
        TextView msg = Ui.text(a, Tr.t("exad_ask_msg", "To support the development of the engine, we hope you will watch an ad. We know ads are annoying, but developing the engine takes time and effort. If you have no internet, you are free to export: just press the button below. Thank you!"), 14, Ui.TEXT_2);
        msg.setLineSpacing(0, 1.2f);
        msg.setGravity(Gravity.CENTER);
        ScrollView sc = new ScrollView(a); // long translations / small screens: the text scrolls, the buttons stay
        sc.addView(msg, new FrameLayout.LayoutParams(-1, -2));
        askBox.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView again = button(Tr.t("exad_wait_again", "Wait for the ad again"), true);
        again.setOnClickListener(v -> start());
        TextView skip = button(Tr.t("exad_no_ad", "Export without an ad"), false);
        skip.setOnClickListener(v -> finish());
        askBox.addView(again, Ui.margins(Ui.matchWrap(), a, 0, 14, 0, 0));
        askBox.addView(skip, Ui.margins(Ui.matchWrap(), a, 0, 8, 0, 0));
        root.addView(askBox, new LinearLayout.LayoutParams(-1, 0, 1));

        dlg.setContentView(root);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
            w.setLayout(Math.min(dm.widthPixels - Ui.dp(a, 40), Ui.dp(a, 420)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Full width button whose text wraps (two lines at most) instead of being squeezed or cut in long languages. */
    private TextView button(String label, boolean primary) {
        TextView t = Ui.text(a, label, 14.5f, primary ? Color.WHITE : Ui.TEXT);
        t.setTypeface(primary ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setMinHeight(Ui.dp(a, 46));
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
        t.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                primary ? Ui.rounded(Ui.ACCENT, Ui.dp(a, 10)) : Ui.rounded(Ui.SURFACE_2, Ui.dp(a, 10), Ui.BORDER, Ui.dp(a, 1)), null));
        t.setClickable(true);
        return t;
    }

    private void start() {
        if (finished) return;
        askBox.setVisibility(View.GONE);
        waitBox.setVisibility(View.VISIBLE);
        sizeFor(false);
        left = SECONDS;
        started = System.currentTimeMillis();
        paint();
        if (!dlg.isShowing()) dlg.show();
        AdsManager.requestInterstitial(a);
        h.removeCallbacksAndMessages(null);
        h.postDelayed(this::poll, 200);
        h.postDelayed(this::tick, 1000);
    }

    private void paint() {
        count.setText(String.valueOf(Math.max(left, 0)));
        waitText.setText(Tr.t("exad_wait", "The ad is loading. Your export starts right after it.\nThank you for supporting Star3D."));
    }

    private void tick() {
        if (finished || waitBox.getVisibility() != View.VISIBLE) return;
        left--;
        paint();
        if (left <= 0) { if (!tryShow()) ask(); return; }
        h.postDelayed(this::tick, 1000);
    }

    /** The ad opens as soon as it is loaded (after a moment, so the user sees what is coming). */
    private void poll() {
        if (finished || waitBox.getVisibility() != View.VISIBLE) return;
        if (System.currentTimeMillis() - started >= MIN_SHOW_MS && tryShow()) return;
        AdsManager.requestInterstitial(a); // a failed load is asked again
        h.postDelayed(this::poll, 250);
    }

    private boolean tryShow() {
        if (finished || !AdsManager.interstitialReady() || a.isFinishing()) return false;
        h.removeCallbacksAndMessages(null);
        try { dlg.dismiss(); } catch (Throwable ignored) { }
        return AdsManager.showInterstitialNow(a, this::finish) || failShow();
    }

    private boolean failShow() { finish(); return true; }

    private void ask() {
        h.removeCallbacksAndMessages(null);
        waitBox.setVisibility(View.GONE);
        askBox.setVisibility(View.VISIBLE);
        sizeFor(true);
    }

    /** The message state has a height (so its text can scroll above the two buttons); the countdown wraps its content. */
    private void sizeFor(boolean ask) {
        Window w = dlg.getWindow();
        if (w == null) return;
        android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int width = Math.min(dm.widthPixels - Ui.dp(a, 40), Ui.dp(a, 420));
        w.setLayout(width, ask ? Math.min(dm.heightPixels - Ui.dp(a, 48), Ui.dp(a, 400)) : ViewGroup.LayoutParams.WRAP_CONTENT);
        View root = (View) askBox.getParent();
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (lp != null) { lp.height = ask ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT; root.setLayoutParams(lp); }
    }

    private void finish() {
        if (finished) return;
        finished = true;
        h.removeCallbacksAndMessages(null);
        try { if (dlg.isShowing()) dlg.dismiss(); } catch (Throwable ignored) { }
        Ui.ui(export);
    }
}
