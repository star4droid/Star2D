package com.star4droid.star2d.suda;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/**
 * Purchase flow + account dialogs: the "Subscribe now" summary dialog, the sign-in / create-account dialogs, opening URLs,
 * and the "fake" progress shown to users without credits before telling them they have none.
 */
public final class Paywall {
    private Paywall() {}

    /** Short summary + "Subscribe now" → BuyCreditsActivity. */
    public static void offer(Context c) {
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 4));
        TextView t = Ui.text(c, Tr.t("s3d_offer_text", "Generate images and 3D models with SUDA3D. Buy credits once, pay only for what you generate. Credits never expire."), 14, Ui.TEXT_2);
        col.addView(t);
        double cr = Suda3D.credits(c);
        if (Suda3D.loggedIn(c) && cr >= 0) {
            TextView b = Ui.text(c, Tr.t("s3d_balance", "Balance") + ": " + Suda3D.fmtCredits(cr) + " " + Tr.t("s3d_credits", "credits"), 13, Ui.TEXT_3);
            b.setPadding(0, Ui.dp(c, 10), 0, 0);
            col.addView(b);
        }
        new AlertDialog.Builder(c)
                .setTitle(Tr.t("s3d_offer_title", "SUDA3D credits"))
                .setView(col)
                .setPositiveButton(Tr.t("s3d_subscribe", "Subscribe now"), (d, w) -> openBuy(c))
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    public static void openBuy(Context c) {
        Intent i = new Intent(c, BuyCreditsActivity.class);
        if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** Opens a URL as a Custom Tab when the browser supports it (session extra), else a normal browser view. */
    public static void openUrl(Context c, String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        Bundle extras = new Bundle();
        extras.putBinder("android.support.customtabs.extra.SESSION", null);
        i.putExtras(extras);
        i.putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", Ui.BG);
        if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            try {
                c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                Ui.toast(c, url);
            }
        }
    }

    /** Sign in with email + password; runs done on success. "Create an account" opens {@link #register}. */
    public static void login(Context c, Runnable done) {
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 20), Ui.dp(c, 6), Ui.dp(c, 20), 0);
        EditText email = Ui.input(c, Suda3D.email(c), Tr.t("s3d_email", "Email"));
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass = Ui.input(c, "", Tr.t("s3d_password", "Password"));
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView status = Ui.text(c, "", 12.5f, Ui.DANGER);
        col.addView(email, Ui.matchWrap());
        col.addView(pass, Ui.margins(Ui.matchWrap(), c, 0, 8, 0, 0));
        col.addView(status);
        TextView reg = Ui.text(c, Tr.t("s3d_register", "Create an account"), 13, Ui.ACCENT);
        reg.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 4));
        col.addView(reg);
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(Tr.t("s3d_signin", "Sign in to SUDA3D"))
                .setView(col)
                .setPositiveButton(Tr.t("s3d_signin_btn", "Sign in"), null)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .create();
        reg.setOnClickListener(v -> { d.dismiss(); register(c, email.getText().toString().trim(), done); });
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String em = email.getText().toString().trim(), pw = pass.getText().toString();
            if (em.isEmpty() || pw.isEmpty()) { status.setTextColor(Ui.DANGER); status.setText(Tr.t("s3d_fill_email_pass", "Enter your email and password.")); return; }
            status.setTextColor(Ui.TEXT_3);
            status.setText(Tr.t("s3d_signing_in", "Signing in…"));
            Suda3D.IO.execute(() -> {
                try {
                    Suda3D.login(c, em, pw);
                    SudaBilling.recover(c);
                    Ui.ui(() -> { d.dismiss(); if (done != null) done.run(); });
                } catch (Exception e) {
                    Ui.ui(() -> { status.setTextColor(Ui.DANGER); status.setText(Suda3D.message(e)); });
                }
            });
        }));
        d.show();
    }

    /** Create an account: email + password + confirm password (must match, 8+ characters), then signs in. */
    public static void register(Context c, String emailValue, Runnable done) {
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 20), Ui.dp(c, 6), Ui.dp(c, 20), 0);
        EditText email = Ui.input(c, emailValue == null ? "" : emailValue, Tr.t("s3d_email", "Email"));
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass = Ui.input(c, "", Tr.t("s3d_password", "Password"));
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText pass2 = Ui.input(c, "", Tr.t("s3d_password_confirm", "Confirm password"));
        pass2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView status = Ui.text(c, "", 12.5f, Ui.DANGER);
        col.addView(email, Ui.matchWrap());
        col.addView(pass, Ui.margins(Ui.matchWrap(), c, 0, 8, 0, 0));
        col.addView(pass2, Ui.margins(Ui.matchWrap(), c, 0, 8, 0, 0));
        col.addView(status);
        TextView back = Ui.text(c, Tr.t("s3d_have_account", "I already have an account"), 13, Ui.ACCENT);
        back.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 4));
        col.addView(back);
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(Tr.t("s3d_register", "Create an account"))
                .setView(col)
                .setPositiveButton(Tr.t("s3d_register_btn", "Create account"), null)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .create();
        back.setOnClickListener(v -> { d.dismiss(); login(c, done); });
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String em = email.getText().toString().trim(), pw = pass.getText().toString(), pw2 = pass2.getText().toString();
            status.setTextColor(Ui.DANGER);
            if (em.isEmpty() || !em.contains("@")) { status.setText(Tr.t("s3d_bad_email", "Enter a valid email address.")); return; }
            if (pw.length() < 8) { status.setText(Tr.t("s3d_pass_short", "The password needs at least 8 characters.")); return; }
            if (!pw.equals(pw2)) { status.setText(Tr.t("s3d_pass_mismatch", "The passwords do not match.")); return; }
            status.setTextColor(Ui.TEXT_3);
            status.setText(Tr.t("s3d_registering", "Creating your account…"));
            Suda3D.IO.execute(() -> {
                try {
                    Suda3D.register(c, em, pw);
                    Suda3D.login(c, em, pw);
                    Ui.ui(() -> {
                        Ui.toast(c, Tr.t("s3d_register_done", "Account created. You are signed in."));
                        d.dismiss();
                        if (done != null) done.run();
                    });
                } catch (Exception e) {
                    Ui.ui(() -> { status.setTextColor(Ui.DANGER); status.setText(Suda3D.message(e)); });
                }
            });
        }));
        d.show();
    }

    /** True when this error means "no credits / not a paying user": shows the fake progress and the offer. */
    public static boolean handle(Context c, Throwable e, String title) {
        boolean noBalance = e instanceof Suda3D.ApiException && (((Suda3D.ApiException) e).noBalance() || ((Suda3D.ApiException) e).unauthorized());
        if (!noBalance) return false;
        Ui.ui(() -> fakeProgress(c, title));
        return true;
    }

    /** A normal-looking progress for about 10 seconds, then "You don't have usage credits" + Buy credits. */
    public static void fakeProgress(Context c, String title) {
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 22), Ui.dp(c, 12), Ui.dp(c, 22), Ui.dp(c, 6));
        TextView status = Ui.text(c, Tr.t("s3d_st_queued", "Waiting in queue…"), 14, Ui.TEXT_2);
        ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        TextView pct = Ui.text(c, "0%", 12, Ui.TEXT_3);
        pct.setGravity(Gravity.END);
        col.addView(status);
        col.addView(bar, Ui.margins(Ui.matchWrap(), c, 0, 10, 0, 2));
        col.addView(pct, Ui.matchWrap());
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(title == null ? Tr.t("s3d_generating", "Generating") : title)
                .setView(col)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .create();
        d.show();
        Handler h = new Handler(Looper.getMainLooper());
        final long start = System.currentTimeMillis();
        h.post(new Runnable() {
            @Override public void run() {
                if (!d.isShowing()) return;
                long t = System.currentTimeMillis() - start;
                if (t < 10000) {
                    int p = (int) Math.min(93, Math.pow(t / 10000.0, 0.7) * 93);
                    bar.setProgress(p);
                    pct.setText(p + "%");
                    if (t > 2500) status.setText(Tr.t("s3d_st_running", "Generating…"));
                    h.postDelayed(this, 200);
                    return;
                }
                d.dismiss();
                new AlertDialog.Builder(c)
                        .setTitle(Tr.t("s3d_no_credits", "You don't have usage credits"))
                        .setMessage(Tr.t("s3d_no_credits_msg", "Buy SUDA3D credits to finish this generation and keep creating."))
                        .setPositiveButton(Tr.t("s3d_buy", "Buy credits"), (x, w) -> offer(c))
                        .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                        .show();
            }
        });
    }

    /** Runs a paid action: before any request, a user who is not signed in / never paid gets the fake progress. */
    public static boolean gate(Context c, String title) {
        if (!Suda3D.loggedIn(c) || (!Suda3D.paid(c) && Suda3D.credits(c) == 0)) {
            fakeProgress(c, title);
            return false;
        }
        return true;
    }

    static int icon() { return R.drawable.ic_s2_wand; }
}
