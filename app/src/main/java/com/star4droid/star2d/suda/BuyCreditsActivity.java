package com.star4droid.star2d.suda;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * "Buy credits" page: horizontally scrolling cards with the credit value ($5 / $10 / $15 / $50), credits under each, on
 * the first card also how many models it pays for; feature bullets below. A card asks for confirmation (credit value vs
 * the Google Play price, which includes Play's fee) and opens the Google Play purchase sheet. The server credits the
 * account ({@link SudaBilling}); nothing is credited locally.
 */
public class BuyCreditsActivity extends Activity implements SudaBilling.Listener {
    private LinearLayout cards;
    private TextView balance, note;
    private double cheapestCredits = 20, cheapestUsd = 0.21;
    private int feePercent = 15;
    private List<SudaBilling.Product> products = new ArrayList<>();
    private Map<String, String> prices = new HashMap<>();

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
        note = Ui.text(this, "", 12, Ui.TEXT_3);
        col.addView(note, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
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
        readPricing(Suda3D.cachedPricing(this));
        setProducts(SudaBilling.parseProducts(null));
        Suda3D.IO.execute(() -> {
            try {
                JsonObject p = Suda3D.pricing(this);
                runOnUiThread(() -> { readPricing(p); render(); });
            } catch (Exception ignored) {}
        });
        Suda3D.IO.execute(() -> {
            try {
                JsonObject r = Suda3D.billingProducts(this);
                runOnUiThread(() -> {
                    feePercent = (int) Suda3D.num(r, "fee_note_percent", feePercent);
                    setProducts(SudaBilling.parseProducts(r));
                });
            } catch (Exception ignored) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SudaBilling.listen(this);
        refreshBalance();
        SudaBilling.recover(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SudaBilling.unlisten(this);
    }

    private void refreshBalance() {
        if (!Suda3D.loggedIn(this)) { balance.setText(Tr.t("s3d_err_login", "Sign in to your SUDA3D account first")); return; }
        Suda3D.IO.execute(() -> {
            try { Suda3D.me(this); } catch (Exception ignored) {}
            runOnUiThread(() -> balance.setText(Tr.t("s3d_balance", "Balance") + ": " + Suda3D.fmtCredits(Suda3D.credits(this)) + " " + Tr.t("s3d_credits", "credits")));
        });
    }

    private void readPricing(JsonObject p) {
        JsonObject ch = Suda3D.obj(p, "cheapest_model");
        if (ch != null) {
            cheapestCredits = Math.max(0.01, Suda3D.num(ch, "credits", cheapestCredits));
            cheapestUsd = Suda3D.num(ch, "usd", cheapestCredits / 100);
        }
    }

    private void setProducts(List<SudaBilling.Product> list) {
        products = list;
        render();
        SudaBilling.queryPrices(this, list, p -> { prices = p; render(); });
    }

    private void render() {
        cards.removeAllViews();
        for (int i = 0; i < products.size(); i++) cards.addView(packCard(products.get(i), i == 0), cardLp());
        note.setText(String.format(Locale.US, Tr.t("s3d_play_fee_note", "Prices in Google Play include its %1$d%% fee. You always get the full credit value shown on the card."), feePercent));
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

    private LinearLayout packCard(SudaBilling.Product p, boolean first) {
        LinearLayout c = SudaUi.card(this);
        if (first) c.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(this, 12), SudaUi.ORANGE, Ui.dp(this, 2)));
        TextView price = Ui.text(this, "$" + money(p.creditUsd), 30, Ui.TEXT);
        price.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(price);
        TextView cr = Ui.text(this, Suda3D.fmtCredits(p.credits) + " " + Tr.t("s3d_credits", "credits"), 15, SudaUi.ORANGE);
        cr.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(cr);
        if (first) {
            TextView m = Ui.text(this, modelsLine(p.credits), 12.5f, Ui.TEXT_2);
            m.setPadding(0, Ui.dp(this, 6), 0, 0);
            c.addView(m);
        }
        String play = prices.get(p.id);
        if (play != null) {
            TextView pay = Ui.text(this, Tr.t("s3d_you_pay", "You pay") + " " + play, 12, Ui.TEXT_3);
            pay.setPadding(0, Ui.dp(this, 4), 0, 0);
            c.addView(pay);
        }
        View sp = new View(this);
        c.addView(sp, new LinearLayout.LayoutParams(1, Ui.dp(this, 14)));
        c.addView(Ui.button(this, 0, Tr.t("s3d_buy_btn", "Buy"), true, v -> confirm(p)), Ui.matchWrap());
        c.setOnClickListener(v -> confirm(p));
        return c;
    }

    private void confirm(SudaBilling.Product p) {
        if (!Suda3D.loggedIn(this)) { Paywall.login(this, () -> confirm(p)); return; }
        String play = prices.get(p.id);
        if (play == null || !SudaBilling.hasDetails(p.id)) {
            Ui.toast(this, Tr.t("s3d_billing_not_play", "Purchases work only in the app installed from Google Play."));
            SudaBilling.queryPrices(this, products, x -> { prices = x; render(); });
            return;
        }
        String msg = String.format(Locale.US, Tr.t("s3d_buy_confirm", "$%1$s credits → you pay %2$s (includes Google Play's %3$d%% fee)"), money(p.creditUsd), play, feePercent);
        new AlertDialog.Builder(this)
                .setTitle(Tr.t("s3d_buy", "Buy credits"))
                .setMessage(msg + "\n\n" + Suda3D.fmtCredits(p.credits) + " " + Tr.t("s3d_credits", "credits"))
                .setPositiveButton(Tr.t("s3d_buy_btn", "Buy"), (d, w) -> {
                    if (Suda3D.playAccountId(this).isEmpty()) {
                        // play_account_id comes with GET /me; fetch it first so the purchase is tied to this account
                        Suda3D.IO.execute(() -> {
                            try { Suda3D.me(this); } catch (Exception ignored) {}
                            runOnUiThread(() -> SudaBilling.buy(this, p.id));
                        });
                    } else SudaBilling.buy(this, p.id);
                })
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    @Override
    public void onCredited(String productId, double usd, double credits) {
        refreshBalance();
        new AlertDialog.Builder(this)
                .setTitle(Tr.t("s3d_credited_title", "Credits added"))
                .setMessage(String.format(Locale.US, Tr.t("s3d_credited_msg", "$%1$s added to your balance (%2$s credits)."), money(usd), Suda3D.fmtCredits(credits)))
                .setPositiveButton(Tr.t("ok", "OK"), null)
                .show();
    }

    @Override
    public void onPending(String productId) {
        Ui.toast(this, Tr.t("s3d_payment_pending", "Payment pending. Your credits are added when Google Play confirms the payment."));
    }

    @Override
    public void onError(String message) { Ui.toast(this, message); }
}
