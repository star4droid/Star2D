package com.star4droid.star2d.suda;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.SystemBars;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** Small shared pieces of the SUDA3D screens (top bar, balance chip, cards), built on the app's Ui helpers. */
public final class SudaUi {
    private SudaUi() {}

    public static final int ORANGE = 0xFFF5A524;

    /** Common activity setup: translations, dark window, edge-to-edge insets. */
    public static void setup(Activity a, View content) {
        Tr.init(a);
        Jobs.init(a);
        a.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
        a.setContentView(content);
        SystemBars.apply(a);
    }

    /** Back arrow + title + trailing views. */
    public static LinearLayout topBar(Activity a, String title, View... actions) {
        Tr.init(a);
        LinearLayout bar = Ui.row(a);
        bar.setBackgroundColor(Ui.SURFACE);
        bar.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4));
        bar.addView(Ui.iconButton(a, R.drawable.ic_s2_arrow_back, Tr.t("back", "Back"), v -> a.finish()));
        TextView t = Ui.title(a, title);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 6), 0);
        bar.addView(t, Ui.weight(1));
        for (View v : actions) bar.addView(v);
        return bar;
    }

    /** "123 credits" chip that follows the stored balance; tap = buy. */
    public static TextView balanceChip(Context c) {
        TextView t = Ui.text(c, "", 13, ORANGE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
        t.setBackground(Ui.rounded(0x22F5A524, Ui.dp(c, 14), 0x66F5A524, 1));
        Runnable upd = () -> {
            double cr = Suda3D.credits(c);
            t.setText(Suda3D.loggedIn(c) ? Suda3D.fmtCredits(cr) + " " + Tr.t("s3d_credits", "credits") : Tr.t("s3d_signin_btn", "Sign in"));
        };
        upd.run();
        Suda3D.Balance.listen(upd);
        t.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { Suda3D.Balance.listen(upd); upd.run(); }
            @Override public void onViewDetachedFromWindow(View v) { Suda3D.Balance.unlisten(upd); }
        });
        t.setOnClickListener(v -> {
            if (!Suda3D.loggedIn(c)) Paywall.login(c, upd);
            else Paywall.offer(c);
        });
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = Ui.column(c);
        l.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 12), Ui.BORDER, 1));
        l.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        return l;
    }

    public static String choiceLabel(String v) { return Tr.t("s3d_c_" + v.replace(':', '_'), v.replace("preset:", "")); }

    public static String statusLabel(String st) {
        switch (st == null ? "" : st) {
            case "queued": return Tr.t("s3d_st_queued", "Waiting in queue…");
            case "running": return Tr.t("s3d_st_running", "Generating…");
            case "success": return Tr.t("s3d_st_success", "Done");
            case "failed": return Tr.t("s3d_st_failed", "Failed");
            case "cancelled": return Tr.t("s3d_st_cancelled", "Cancelled");
            default: return st == null ? "" : st;
        }
    }

    /** "expires in 2 d 5 h" from an ISO-8601 time; "" when unknown. */
    public static String expiry(String iso) {
        long t = parseTime(iso);
        if (t <= 0) return "";
        long left = t - System.currentTimeMillis();
        if (left <= 0) return Tr.t("s3d_expired_short", "expired");
        long h = left / 3600000L;
        String when = h >= 24 ? (h / 24) + Tr.t("s3d_d", "d") + " " + (h % 24) + Tr.t("s3d_h", "h") : Math.max(1, h) + Tr.t("s3d_h", "h");
        return Tr.t("s3d_expires_in", "expires in") + " " + when;
    }

    public static long parseTime(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        String[] fmts = {"yyyy-MM-dd'T'HH:mm:ss.SSSSSSX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd HH:mm:ss"};
        for (String f : fmts) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(f, java.util.Locale.US);
                if (!f.contains("X")) sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return sdf.parse(iso).getTime();
            } catch (Exception ignored) {}
        }
        return 0;
    }
}
