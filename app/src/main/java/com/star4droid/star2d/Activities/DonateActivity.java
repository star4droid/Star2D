package com.star4droid.star2d.Activities;

import android.animation.ObjectAnimator;
import java.util.Locale;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.ProductDetails;
import com.star4droid.star2d.ui.CommunityLinks;
import com.star4droid.star2d.ui.StarfieldView;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/** Support page: USDT (Optimism) donation, Google Play donation, developer contact, community. */
public class DonateActivity extends AppCompatActivity implements PurchasesUpdatedListener {

    private static final String TAG = "DonateActivity";
    private static final int PINK = 0xFFFF5C8D, PURPLE = 0xFF7C4DFF, CARD = 0x33FFFFFF;

    private BillingClient billingClient;
    private TextView tvStatus;
    private View playCard;
    private StarfieldView sky;
    private final Handler main = new Handler(Looper.getMainLooper());
    private float d;

    private final String ID_1 = "donation_1_usd";
    private final String ID_3 = "donation_3_usd";
    private final String ID_5 = "donation_5_usd";

    private final List<String> productIds = new ArrayList<>();
    private final HashMap<String, ProductDetails> productDetailsMap = new HashMap<>();

    private static String T(String k, String def) { return Tr.t(k, def); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tr.init(this);
        d = getResources().getDisplayMetrics().density;
        buildUi();

        productIds.add(ID_1);
        productIds.add(ID_3);
        productIds.add(ID_5);
        setupBillingClient();
    }

    @Override protected void onPause() { if (sky != null) sky.pause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (sky != null) sky.resume(); }

    // ------------------------------------------------------------------ UI

    private int dp(float v) { return Math.round(v * d); }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(Ui.isRtl(this) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        sky = new StarfieldView(this);
        root.addView(sky, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(12), dp(16), dp(28));
        scroll.addView(col, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, in) -> {
            Insets bars = in.getInsets(WindowInsetsCompat.Type.systemBars());
            scroll.setPadding(0, bars.top, 0, bars.bottom);
            return in;
        });

        List<View> animated = new ArrayList<>();

        // top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("\u2190", 24, Color.WHITE, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(ripple(0x22FFFFFF, 22));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        col.addView(bar);

        // hero
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(0, dp(6), 0, dp(14));
        TextView heart = text("\u2764\uFE0F", 58, PINK, false);
        heart.setGravity(Gravity.CENTER);
        hero.addView(heart, new LinearLayout.LayoutParams(-2, -2));
        ObjectAnimator sx = ObjectAnimator.ofFloat(heart, View.SCALE_X, 1f, 1.18f, 1f, 1.1f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(heart, View.SCALE_Y, 1f, 1.18f, 1f, 1.1f, 1f);
        for (ObjectAnimator o : new ObjectAnimator[]{sx, sy}) {
            o.setDuration(1500); o.setRepeatCount(ObjectAnimator.INFINITE); o.start();
        }
        TextView title = text(T("support_title", "Support Star2D"), 26, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        hero.addView(title);
        TextView sub = text(T("support_sub", "Your support keeps the engine growing. Thank you for being part of the journey!"), 14, 0xFFC9C9E6, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(dp(12), dp(6), dp(12), 0);
        hero.addView(sub);
        col.addView(hero);
        animated.add(hero);

        // USDT card
        LinearLayout usdt = card();
        usdt.addView(sectionTitle("\uD83D\uDCB0  " + T("support_usdt_title", "Donate with USDT")));
        TextView chip = text("\u25CF  " + T("support_network", "Network: Optimism (OP Mainnet)"), 12, 0xFFFF9AA2, true);
        chip.setBackground(rounded(0x33FF5C5C, 20, 0));
        chip.setPadding(dp(12), dp(5), dp(12), dp(5));
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
        chipLp.topMargin = dp(8);
        usdt.addView(chip, chipLp);

        TextView addr = text(CommunityLinks.USDT_OPTIMISM_ADDRESS, 13, Color.WHITE, false);
        addr.setTypeface(Typeface.MONOSPACE);
        addr.setTextDirection(View.TEXT_DIRECTION_LTR);
        addr.setGravity(Gravity.CENTER);
        addr.setTextIsSelectable(true);
        addr.setBackground(rounded(0x55000000, 12, 0x33FFFFFF));
        addr.setPadding(dp(10), dp(14), dp(10), dp(14));
        LinearLayout.LayoutParams addrLp = new LinearLayout.LayoutParams(-1, -2);
        addrLp.topMargin = dp(12);
        usdt.addView(addr, addrLp);

        TextView copy = button(T("support_copy", "Copy address"), PINK, PURPLE);
        copy.setOnClickListener(v -> copyAddress(copy));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(-1, dp(48));
        copyLp.topMargin = dp(10);
        usdt.addView(copy, copyLp);

        TextView warn = text("\u26A0\uFE0F  " + T("support_warn", "Please make sure the network matches: send USDT on the Optimism network only. Sending on a different network may cause your funds to be lost permanently."), 13, 0xFFFFE0A3, false);
        warn.setLineSpacing(0, 1.15f);
        warn.setBackground(rounded(0x33FFB300, 12, 0x88FFB300));
        warn.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams warnLp = new LinearLayout.LayoutParams(-1, -2);
        warnLp.topMargin = dp(12);
        usdt.addView(warn, warnLp);
        col.addView(usdt, cardLp());
        animated.add(usdt);

        // Google Play card (hidden if billing is unavailable, e.g. APK installed outside Play)
        LinearLayout play = card();
        playCard = play;
        play.setVisibility(View.GONE);
        play.addView(sectionTitle("\uD83C\uDF81  " + T("support_play_title", "Donate via Google Play")));
        LinearLayout amounts = new LinearLayout(this);
        amounts.setOrientation(LinearLayout.HORIZONTAL);
        String[] ids = {ID_1, ID_3, ID_5};
        String[] labels = {"$1", "$3", "$5"};
        for (int i = 0; i < 3; i++) {
            final String id = ids[i];
            TextView b = button(labels[i], 0xFF4C6FFF, 0xFF7C4DFF);
            b.setOnClickListener(v -> purchaseProduct(id));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            lp.setMarginEnd(i < 2 ? dp(8) : 0);
            amounts.addView(b, lp);
        }
        LinearLayout.LayoutParams amLp = new LinearLayout.LayoutParams(-1, -2);
        amLp.topMargin = dp(12);
        play.addView(amounts, amLp);
        tvStatus = text("", 12, 0xFFC9C9E6, false);
        tvStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, -2);
        stLp.topMargin = dp(8);
        play.addView(tvStatus, stLp);
        col.addView(play, cardLp());
        animated.add(play);

        // contact developer
        // optional rewarded video: 1 hour without ads
        if (com.star4droid.star2d.evo.BuildConfig.ADS_ENABLED && !com.star4droid.star2d.ads.AdsManager.adFreeByPurchase(this)) {
            LinearLayout ad = card();
            ad.addView(sectionTitle("\uD83C\uDFAC  " + T("ads_reward_title", "Watch a video to support me")));
            TextView adSub = text(T("ads_reward_sub", "Optional. Watch one short video ad, it helps fund Star2D, and you get 1 hour without ads."), 13, 0xFFC9C9E6, false);
            adSub.setPadding(0, dp(4), 0, dp(10));
            ad.addView(adSub);
            TextView adBtn = text("", 15, Color.WHITE, true);
            adBtn.setGravity(Gravity.CENTER);
            adBtn.setBackground(ripple(0x33FFFFFF, 12));
            Runnable[] upd = new Runnable[1];
            upd[0] = () -> runOnUiThread(() -> {
                long until = com.star4droid.star2d.ads.AdsManager.rewardFreeUntil(this);
                boolean ready = com.star4droid.star2d.ads.AdsManager.rewardedReady();
                if (until > System.currentTimeMillis()) adBtn.setText(String.format(Locale.US, T("ads_free_left", "No ads for %d more min"), Math.max(1, (until - System.currentTimeMillis()) / 60000)) + (ready ? "  \u2022  " + T("ads_reward_more", "watch another") : ""));
                else adBtn.setText(ready ? T("ads_reward_btn", "Watch video") : T("ads_reward_loading", "Loading video..."));
                adBtn.setAlpha(ready ? 1f : 0.6f);
            });
            adBtn.setOnClickListener(v -> com.star4droid.star2d.ads.AdsManager.showRewarded(this, () -> Toast.makeText(this, T("ads_thanks", "Thank you! No ads for 1 hour."), Toast.LENGTH_LONG).show(), upd[0]));
            ad.addView(adBtn, new LinearLayout.LayoutParams(-1, dp(48)));
            col.addView(ad, cardLp());
            animated.add(ad);
            com.star4droid.star2d.ads.AdsManager.init(this);
            com.star4droid.star2d.ads.AdsManager.loadRewarded(this, upd[0]);
            upd[0].run();
        }

        LinearLayout other = card();
        other.addView(sectionTitle("\uD83E\uDD1D  " + T("support_other_title", "Other ways to support")));
        TextView otherSub = text(T("support_other_sub", "Contact the developer for other support methods"), 13, 0xFFC9C9E6, false);
        otherSub.setPadding(0, dp(4), 0, dp(10));
        other.addView(otherSub);
        LinearLayout contacts = new LinearLayout(this);
        TextView wa = button("WhatsApp", 0xFF25D366, 0xFF128C7E);
        wa.setOnClickListener(v -> openUrl(CommunityLinks.DEV_WHATSAPP));
        TextView tg = button("Telegram", 0xFF2AABEE, 0xFF229ED9);
        tg.setOnClickListener(v -> openUrl(CommunityLinks.DEV_TELEGRAM));
        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        c1.setMarginEnd(dp(8));
        contacts.addView(wa, c1);
        contacts.addView(tg, new LinearLayout.LayoutParams(0, dp(48), 1f));
        other.addView(contacts);
        col.addView(other, cardLp());
        animated.add(other);

        // community
        LinearLayout comm = card();
        comm.addView(sectionTitle("\uD83C\uDF0D  " + T("home_tab_community", "Community")));
        for (String[] link : CommunityLinks.all()) {
            TextView row = text(link[4] + "   " + T("home_c_" + link[0], link[1]), 15, Color.WHITE, true);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(ripple(0x22FFFFFF, 12));
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setOnClickListener(v -> openUrl(link[3]));
            attachPress(row);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
            lp.topMargin = dp(8);
            comm.addView(row, lp);
        }
        col.addView(comm, cardLp());
        animated.add(comm);

        // staggered entrance
        for (int i = 0; i < animated.size(); i++) {
            View v = animated.get(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(50));
            v.animate().alpha(1f).translationY(0).setStartDelay(120L + i * 110L).setDuration(520)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    private void copyAddress(TextView btn) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("USDT (Optimism)", CommunityLinks.USDT_OPTIMISM_ADDRESS));
        } catch (Exception e) {
            Toast.makeText(this, String.valueOf(e), Toast.LENGTH_SHORT).show();
            return;
        }
        btn.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        final String normal = T("support_copy", "Copy address");
        btn.setText("\u2713  " + T("support_copied", "Copied"));
        btn.animate().cancel();
        btn.setScaleX(0.94f);
        btn.setScaleY(0.94f);
        btn.animate().scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator(3f)).setDuration(350).start();
        main.removeCallbacksAndMessages(btn);
        main.postAtTime(() -> btn.setText(normal), btn, android.os.SystemClock.uptimeMillis() + 1800);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, T("home_no_app", "No app found to open the link."), Toast.LENGTH_SHORT).show();
        }
    }

    // -- small view helpers

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private TextView sectionTitle(String s) { return text(s, 17, Color.WHITE, true); }

    private GradientDrawable rounded(int color, float radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius * d);
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private RippleDrawable ripple(int color, float radius) {
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x44FFFFFF), rounded(color, radius, 0), null);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(CARD, 18, 0x22FFFFFF));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        return lp;
    }

    private TextView button(String label, int c1, int c2) {
        TextView t = text(label, 15, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        g.setCornerRadius(14 * d);
        t.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x55FFFFFF), g, null));
        attachPress(t);
        return t;
    }

    /** Subtle press-down scale so every control feels alive (does not consume the touch). */
    private void attachPress(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    break;
            }
            return false;
        });
    }

    private void setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases(com.android.billingclient.api.PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .setListener(this)
            .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    log("Billing setup OK");
                    queryProducts();
                } else {
                    log("Billing setup failed: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                log("Billing service disconnected. Try reconnect.");
            }
        });
    }

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        for (String id : productIds) {
            productList.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build());
        }

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, queryProductDetailsResult) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            List<ProductDetails> productDetailsList = queryProductDetailsResult.getProductDetailsList();
            if (productDetailsList != null) {
                for (ProductDetails pd : productDetailsList) {
                    productDetailsMap.put(pd.getProductId(), pd);
                }
                runOnUiThread(() -> {
                    if (!productDetailsMap.isEmpty()) playCard.setVisibility(View.VISIBLE);
                    tvStatus.setText("");
                });
            }
        } else log("Failed to fetch product details");
    });
    }

    private void purchaseProduct(String productId) {
        ProductDetails pd = productDetailsMap.get(productId);
        if (pd == null) {
            Toast.makeText(this, "Product details are not available now.", Toast.LENGTH_SHORT).show();
            return;
        }

        ProductDetails.OneTimePurchaseOfferDetails offer = pd.getOneTimePurchaseOfferDetails();
        if (offer == null) {
            Toast.makeText(this, "Purchase offer is not available for this product.", Toast.LENGTH_SHORT).show();
            return;
        }

        BillingFlowParams.ProductDetailsParams productDetailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .setOfferToken(offer.getOfferToken())
                        .build();

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
                .build();

        BillingResult result = billingClient.launchBillingFlow(this, flowParams);
        log("launchBillingFlow result: " + result.getDebugMessage());
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "Purchase cancelled", Toast.LENGTH_SHORT).show();
        } else {
            log("onPurchasesUpdated error: " + billingResult.getDebugMessage());
            Toast.makeText(this, "Purchase error: " + billingResult.getDebugMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handlePurchase(Purchase purchase) {
        // 1) Verify the purchase status locally, then, preferably, verify it on the server using the Google Play Developer API.
        // 2) When the verification is valid, award the user the benefit or record the donation on the server.
        // 3) Consume the product.
        consumePurchase(purchase.getPurchaseToken());
    }

    private void consumePurchase(String purchaseToken) {
        ConsumeParams consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build();

        ConsumeResponseListener listener = (billingResult, outToken) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                log("Consume OK");
                runOnUiThread(() -> tvStatus.setText(T("support_thanks", "Thank you for your donation \u2764\uFE0F")));
            } else {
                log("Consume failed: " + billingResult.getDebugMessage());
                runOnUiThread(() -> tvStatus.setText("Purchase consumption failure: " + billingResult.getDebugMessage()));
            }
        };

        billingClient.consumeAsync(consumeParams, listener);
    }

    private void log(String s) {
        Log.d(TAG, s);
    }

    @Override
    protected void onDestroy() {
        if (billingClient != null && billingClient.isReady()) {
            billingClient.endConnection();
        }
        super.onDestroy();
    }
}