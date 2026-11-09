package com.star4droid.star2d.suda;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.Locale;

/**
 * "Buy credits" page: horizontally scrolling cards $5 / $10 / $15 / Custom (last), credits under each, on the first
 * card also how many models it pays for; feature bullets below. Buy opens Stripe Checkout in the browser; on return
 * the balance is refreshed.
 */
public class BuyCreditsActivity extends Activity {
    private LinearLayout cards;
    private TextView balance;
    private double cheapestCredits = 20, cheapestUsd = 0.21, minUsd = 5, maxUsd = 500;
    private final ArrayList<double[]> packs = new ArrayList<>();
    private boolean leftForCheckout;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(SudaUi.topBar(this, Tr.t("s3d_buy", "Buy credits"), SudaUi.balanceChip(this)), Ui.matchWrap());
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 20));
        TextView head = Ui.title(this, Tr.t("s3d_buy_head", "Choose a credits pack"));
        col.addView(head);
        balance = Ui.text(this, "", 13, Ui.TEXT_3);
        col.addView(balance);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        cards = Ui.row(this);
        cards.setGravity(Gravity.TOP);
        cards.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        hs.addView(cards);
        col.addView(hs, Ui.matchWrap());
        col.addView(Ui.sectionLabel(this, Tr.t("s3d_features", "What you get")));
        String[] bullets = {
                Tr.t("s3d_feat_1", "Image → 3D, Text → 3D and Text → Image from the editor"),
                Tr.t("s3d_feat_2", "AI Workspace: chain generation, texturing, low poly, rigging and animation"),
                Tr.t("s3d_feat_3", "The in-editor AI assistant without your own API key"),
                Tr.t("s3d_feat_4", "Pay per generation, price shown before every run; credits never expire"),
                Tr.t("s3d_feat_5", "Results stay downloadable for 3 days, saved straight into your project")};
        for (String s : bullets) {
            LinearLayout r = Ui.row(this);
            r.setGravity(Gravity.TOP);
            r.addView(Ui.smallIcon(this, R.drawable.ic_s2_check, Ui.SUCCESS, 18));
            TextView t = Ui.text(this, s, 14, Ui.TEXT_2);
            t.setPadding(Ui.dp(this, 10), 0, 0, 0);
            r.addView(t, Ui.weight(1));
            col.addView(r, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 6));
        }
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
        load(Suda3D.cachedPricing(this));
        Suda3D.IO.execute(() -> {
            try {
                JsonObject p = Suda3D.pricing(this);
                runOnUiThread(() -> load(p));
            } catch (Exception e) {
                runOnUiThread(() -> { if (packs.isEmpty()) load(null); });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBalance();
        if (leftForCheckout) {
            leftForCheckout = false;
            Ui.toast(this, Tr.t("s3d_checking_payment", "Checking your payment…"));
        }
    }

    private void refreshBalance() {
        if (!Suda3D.loggedIn(this)) { balance.setText(Tr.t("s3d_err_login", "Sign in to your SUDA3D account first")); return; }
        Suda3D.IO.execute(() -> {
            try { Suda3D.me(this); } catch (Exception ignored) {}
            runOnUiThread(() -> balance.setText(Tr.t("s3d_balance", "Balance") + ": " + Suda3D.fmtCredits(Suda3D.credits(this)) + " " + Tr.t("s3d_credits", "credits")));
        });
    }

    private void load(JsonObject p) {
        packs.clear();
        if (p != null) {
            for (JsonElement e : Suda3D.arr(p, "packs")) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                double usd = Suda3D.num(o, "usd", 0);
                packs.add(new double[]{usd, Suda3D.num(o, "credits", usd * 100)});
            }
            JsonObject ch = Suda3D.obj(p, "cheapest_model");
            if (ch != null) {
                cheapestCredits = Math.max(0.01, Suda3D.num(ch, "credits", cheapestCredits));
                cheapestUsd = Suda3D.num(ch, "usd", cheapestCredits / 100);
            }
            JsonObject cu = Suda3D.obj(p, "custom");
            if (cu != null) { minUsd = Suda3D.num(cu, "min_usd", minUsd); maxUsd = Suda3D.num(cu, "max_usd", maxUsd); }
        }
        if (packs.isEmpty()) for (int u : new int[]{5, 10, 15}) packs.add(new double[]{u, u * 100});
        cards.removeAllViews();
        for (int i = 0; i < packs.size(); i++) cards.addView(packCard(packs.get(i)[0], packs.get(i)[1], i == 0), cardLp());
        cards.addView(customCard(), cardLp());
    }

    private LinearLayout.LayoutParams cardLp() {
        int w = Ui.dp(this, Ui.isLandscape(this) ? 200 : 170);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, -2);
        lp.setMarginEnd(Ui.dp(this, 12));
        return lp;
    }

    private String modelsLine(double credits) {
        long n = (long) Math.floor(credits / cheapestCredits + 1e-9);
        return String.format(Locale.US, Tr.t("s3d_models_line", "%1$d models, $%2$s per model"), n, money(cheapestUsd));
    }

    static String money(double usd) {
        String s = String.format(Locale.US, "%.2f", usd);
        return s.endsWith(".00") ? s.substring(0, s.length() - 3) : s;
    }

    private LinearLayout packCard(double usd, double credits, boolean first) {
        LinearLayout c = SudaUi.card(this);
        if (first) c.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(this, 12), SudaUi.ORANGE, Ui.dp(this, 2)));
        TextView price = Ui.text(this, "$" + money(usd), 30, Ui.TEXT);
        price.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(price);
        TextView cr = Ui.text(this, Suda3D.fmtCredits(credits) + " " + Tr.t("s3d_credits", "credits"), 15, SudaUi.ORANGE);
        cr.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(cr);
        if (first) {
            TextView m = Ui.text(this, modelsLine(credits), 12.5f, Ui.TEXT_2);
            m.setPadding(0, Ui.dp(this, 6), 0, 0);
            c.addView(m);
        }
        View sp = new View(this);
        c.addView(sp, new LinearLayout.LayoutParams(1, Ui.dp(this, 14)));
        c.addView(Ui.button(this, 0, Tr.t("s3d_buy_btn", "Buy"), true, v -> buy(usd)), Ui.matchWrap());
        return c;
    }

    private LinearLayout customCard() {
        LinearLayout c = SudaUi.card(this);
        TextView t = Ui.text(this, Tr.t("s3d_custom", "Custom"), 22, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(t);
        EditText amount = Ui.input(this, "20", "$");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        c.addView(amount, Ui.margins(Ui.matchWrap(), this, 0, 6, 0, 4));
        TextView cr = Ui.text(this, "", 14, SudaUi.ORANGE);
        cr.setTypeface(Typeface.DEFAULT_BOLD);
        TextView m = Ui.text(this, "", 12.5f, Ui.TEXT_2);
        c.addView(cr);
        c.addView(m);
        Runnable upd = () -> {
            double usd = parse(amount.getText().toString());
            if (usd < minUsd || usd > maxUsd) {
                cr.setText(String.format(Locale.US, Tr.t("s3d_custom_range", "From $%1$s to $%2$s"), money(minUsd), money(maxUsd)));
                m.setText("");
            } else {
                cr.setText(Suda3D.fmtCredits(usd * 100) + " " + Tr.t("s3d_credits", "credits"));
                m.setText(modelsLine(usd * 100));
            }
        };
        amount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { upd.run(); }
        });
        upd.run();
        View sp = new View(this);
        c.addView(sp, new LinearLayout.LayoutParams(1, Ui.dp(this, 10)));
        c.addView(Ui.button(this, 0, Tr.t("s3d_buy_btn", "Buy"), true, v -> {
            double usd = parse(amount.getText().toString());
            if (usd < minUsd || usd > maxUsd) { amount.setError(cr.getText()); return; }
            buy(usd);
        }), Ui.matchWrap());
        return c;
    }

    static double parse(String s) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return 0; }
    }

    private void buy(double usd) {
        if (!Suda3D.loggedIn(this)) { Paywall.login(this, () -> buy(usd)); return; }
        Ui.toast(this, Tr.t("s3d_opening_checkout", "Opening secure checkout…"));
        Suda3D.IO.execute(() -> {
            try {
                String url = Suda3D.checkout(this, usd);
                runOnUiThread(() -> { leftForCheckout = true; Paywall.openUrl(this, url); });
            } catch (Exception e) {
                runOnUiThread(() -> Ui.toast(this, Suda3D.message(e)));
            }
        });
    }
}
