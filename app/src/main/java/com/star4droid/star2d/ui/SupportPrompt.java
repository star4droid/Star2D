package com.star4droid.star2d.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Random;

/**
 * Occasionally asks the user to support the engine. Designed not to be annoying:
 * at most once per app launch, never within MIN_GAP of the last time, the first ever
 * appearance is likelier, afterwards a low constant probability, and "Don't show again"
 * silences it for a week.
 */
public final class SupportPrompt {
    private SupportPrompt() {}

    static final double FIRST_CHANCE = 0.65;
    static final double LATER_CHANCE = 0.12;
    static final long MIN_GAP = 2L * 24 * 3600 * 1000;
    static final long SNOOZE = 7L * 24 * 3600 * 1000;
    private static final String P = "support_prompt_";
    private static boolean decidedThisLaunch = false;

    /** Pure decision logic (testable): should the dialog be shown now? */
    public static boolean shouldShow(long now, long snoozeUntil, long lastShown, boolean everShown, double roll) {
        if (now < snoozeUntil) return false;
        if (everShown && now - lastShown < MIN_GAP) return false;
        return roll < (everShown ? LATER_CHANCE : FIRST_CHANCE);
    }

    /** Call when the home screen is visible. Rolls the dice once per app launch. */
    public static void maybeShow(Activity a, Runnable openSupportPage) {
        if (decidedThisLaunch || a.isFinishing()) return;
        decidedThisLaunch = true;
        SharedPreferences sp = a.getSharedPreferences("support_prompt", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        boolean ever = sp.getBoolean(P + "ever", false);
        if (!shouldShow(now, sp.getLong(P + "snooze", 0), sp.getLong(P + "last", 0), ever, new Random().nextDouble())) return;
        show(a, sp, openSupportPage);
    }

    private static String T(String k, String d) { return Tr.t(k, d); }

    private static void show(Activity a, SharedPreferences sp, Runnable openSupportPage) {
        Tr.init(a);
        sp.edit().putBoolean(P + "ever", true).putLong(P + "last", System.currentTimeMillis()).apply();
        float d = a.getResources().getDisplayMetrics().density;
        boolean rtl = Ui.isRtl(a);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        int pad = (int) (20 * d);
        box.setPadding(pad, pad, pad, (int) (12 * d));
        GradientDrawable bgd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1B1446, 0xFF2B1248});
        bgd.setCornerRadius(18 * d);
        bgd.setStroke((int) (1.5f * d), 0xFFFF5C8D);
        box.setBackground(bgd);

        TextView title = new TextView(a);
        title.setText(T("support_dlg_title", "Support Star3D development ❤️"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        // gentle pulse on the title
        title.animate().scaleX(1.05f).scaleY(1.05f).setDuration(900).withEndAction(new Runnable() {
            boolean up = false;
            @Override public void run() {
                if (!title.isAttachedToWindow()) return;
                up = !up;
                float s = up ? 1f : 1.05f;
                title.animate().scaleX(s).scaleY(s).setDuration(900).withEndAction(this).start();
            }
        }).start();

        TextView msg = new TextView(a);
        msg.setText(T("support_dlg_p1", "The Star3D engine aims to provide an easy, suitable environment for mobile game development, and keeps improving its tools and adding more features and capabilities that help developers turn their ideas into real games.")
                + "\n\n" + T("support_dlg_p2", "If you believe in the idea of the engine and want to see it grow and continue, your donation, whatever its size, helps fund development and gives the time and resources needed to add new features and improve performance and the user experience.")
                + "\n\n" + T("support_dlg_p3", "With your support, you help Star3D keep going, grow and reach better capabilities. Thank you to everyone who supports the engine and becomes part of its journey! ❤️"));
        msg.setTextColor(0xFFE6E6F5);
        msg.setTextSize(14);
        msg.setLineSpacing(0, 1.2f);
        ScrollView sv = new ScrollView(a);
        sv.setPadding(0, (int) (12 * d), 0, (int) (12 * d));
        sv.addView(msg);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog dlg = new AlertDialog.Builder(a).setView(box).create();

        TextView support = btn(a, T("support_dlg_donate", "Support now"), 0xFFFF5C8D, Color.WHITE, d);
        support.setOnClickListener(v -> { dlg.dismiss(); if (openSupportPage != null) openSupportPage.run(); });
        TextView never = btn(a, T("support_dlg_never", "Don't show again"), 0x22FFFFFF, 0xFFD0D0E8, d);
        never.setOnClickListener(v -> {
            sp.edit().putLong(P + "snooze", System.currentTimeMillis() + SNOOZE).apply();
            dlg.dismiss();
        });
        TextView later = btn(a, T("support_dlg_later", "Later"), 0x22FFFFFF, 0xFFD0D0E8, d);
        later.setOnClickListener(v -> dlg.dismiss());

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.VERTICAL);
        row.addView(support, lp(d));
        LinearLayout second = new LinearLayout(a);
        second.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, (int) (44 * d), 1f);
        half.topMargin = (int) (8 * d);
        half.setMarginEnd((int) (4 * d));
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, (int) (44 * d), 1f);
        half2.topMargin = (int) (8 * d);
        half2.setMarginStart((int) (4 * d));
        second.addView(never, half);
        second.addView(later, half2);
        row.addView(second);
        box.addView(row);

        dlg.setCanceledOnTouchOutside(true);
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int w = Math.min((int) (a.getResources().getDisplayMetrics().widthPixels * 0.92f), (int) (420 * d));
            int h = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.85f);
            dlg.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            box.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
            if (box.getMeasuredHeight() > h) dlg.getWindow().setLayout(w, h);
        }
    }

    private static LinearLayout.LayoutParams lp(float d) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (46 * d));
    }

    private static TextView btn(Context c, String text, int bg, int fg, float d) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(fg);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(12 * d);
        t.setBackground(g);
        t.setPadding((int) (8 * d), 0, (int) (8 * d), 0);
        return t;
    }
}
