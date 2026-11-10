package com.star4droid.star2d.ads;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.star4droid.star2d.evo.BuildConfig;
import com.star4droid.star2d.suda.Suda3D;

/**
 * Ads, kept deliberately gentle (and inside AdMob policy):
 *  - one adaptive banner on the projects screen only (never inside the editor / over the game),
 *  - one interstitial when the user CLOSES a project (a natural pause), at most once per 5 minutes,
 *    never in the first 3 minutes of a session, and only after 2 closes,
 *  - an optional rewarded video (Support page) = 1 hour without ads,
 *  - none of this for users who bought credits and still hold at least $1.
 * Consent (GDPR/UMP) is asked before the first ad request. Ids are Google TEST ids unless real ones are given at build time.
 */
public final class AdsManager {
    private static final String PREFS = "ads_state";
    private static final long INTERSTITIAL_GAP_MS = 5 * 60_000L, SESSION_GRACE_MS = 3 * 60_000L, REWARD_FREE_MS = 60 * 60_000L;
    private static final int MIN_CLOSES = 2;
    /** $1 = 100 credits */
    private static final double AD_FREE_CREDITS = 100;

    private static boolean consentDone, sdkStarted;
    private static InterstitialAd interstitial;
    private static boolean loadingInterstitial;
    private static RewardedAd rewarded;
    private static boolean loadingRewarded;
    private static AdView banner;
    private static final long sessionStart = System.currentTimeMillis();
    private static int closes;
    private static long lastInterstitial;

    private AdsManager() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** Paid user with at least $1 left: no ads at all. Falls back below $1 (credits < 0 means unknown -> keep current rule: ads on). */
    public static boolean adFreeByPurchase(Context c) {
        try { return Suda3D.loggedIn(c) && Suda3D.paid(c) && Suda3D.credits(c) >= AD_FREE_CREDITS; } catch (Throwable t) { return false; }
    }
    public static boolean adFreeByReward(Context c) { return System.currentTimeMillis() < prefs(c).getLong("free_until", 0); }
    public static long rewardFreeUntil(Context c) { return prefs(c).getLong("free_until", 0); }
    public static boolean adsEnabled(Context c) { return BuildConfig.ADS_ENABLED && !adFreeByPurchase(c) && !adFreeByReward(c); }

    /** Ask for consent (EEA/UK get the Google form), then start the SDK. Safe to call repeatedly. */
    public static void init(Activity a) {
        if (consentDone || !BuildConfig.ADS_ENABLED) return;
        try {
            ConsentInformation ci = UserMessagingPlatform.getConsentInformation(a);
            ci.requestConsentInfoUpdate(a, new ConsentRequestParameters.Builder().build(),
                    () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(a, err -> startSdk(a, ci)),
                    err -> startSdk(a, ci));
            if (ci.canRequestAds()) startSdk(a, ci);
        } catch (Throwable t) { /* ads are optional; never break the app */ }
    }

    private static synchronized void startSdk(Activity a, ConsentInformation ci) {
        if (sdkStarted || !ci.canRequestAds()) return;
        sdkStarted = true; consentDone = true;
        Suda3D.IO.execute(() -> {
            try {
                MobileAds.initialize(a.getApplicationContext(), s -> a.runOnUiThread(() -> { preload(a); if (pendingHost != null) attachBanner(a, pendingHost); }));
            } catch (Throwable t) { sdkStarted = false; }
        });
    }

    /** Privacy options entry (required in EEA): true when the user can reopen the consent form. */
    public static boolean privacyOptionsRequired(Context c) {
        try { return UserMessagingPlatform.getConsentInformation(c).getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED; } catch (Throwable t) { return false; }
    }
    public static void showPrivacyOptions(Activity a) {
        try { UserMessagingPlatform.showPrivacyOptionsForm(a, err -> {}); } catch (Throwable ignored) {}
    }

    private static void preload(Activity a) {
        if (!adsEnabled(a)) return;
        loadInterstitial(a);
    }

    private static void loadInterstitial(Context c) {
        if (!sdkStarted || interstitial != null || loadingInterstitial) return;
        loadingInterstitial = true;
        InterstitialAd.load(c, BuildConfig.ADMOB_INTERSTITIAL_ID, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(InterstitialAd ad) { interstitial = ad; loadingInterstitial = false; }
            @Override public void onAdFailedToLoad(LoadAdError e) { interstitial = null; loadingInterstitial = false; }
        });
    }

    // ---- banner (projects screen) ----
    private static FrameLayout pendingHost;

    /** Adds the adaptive banner at the bottom of {@code host}; {@code content} gets bottom margin equal to the banner height. */
    public static void attachBanner(Activity a, FrameLayout host) {
        pendingHost = host;
        if (!BuildConfig.ADS_ENABLED) { showNotice(a, host); return; }
        if (!sdkStarted || !adsEnabled(a)) { removeBanner(host); return; }
        if (banner != null && banner.getParent() == host) return;
        removeBanner(host);
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int w = Math.max(320, (int) (host.getWidth() > 0 ? host.getWidth() / dm.density : dm.widthPixels / dm.density));
        AdView v = new AdView(a);
        v.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
        v.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(a, w));
        v.setVisibility(View.GONE);
        v.setAdListener(new AdListener() {
            @Override public void onAdLoaded() {
                v.setVisibility(View.VISIBLE);
                int h = v.getAdSize() != null ? v.getAdSize().getHeightInPixels(a) : 0;
                padContent(host, h);
            }
            @Override public void onAdFailedToLoad(LoadAdError e) { padContent(host, 0); }
        });
        banner = v;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        host.addView(v, lp);
        v.loadAd(new AdRequest.Builder().build());
    }

    private static void padContent(FrameLayout host, int h) {
        for (int i = 0; i < host.getChildCount(); i++) {
            View c = host.getChildAt(i);
            if (c == banner || c == notice) continue;
            ViewGroup.LayoutParams lp = c.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) { ((FrameLayout.LayoutParams) lp).bottomMargin = h; c.setLayoutParams(lp); }
        }
    }

    // ---- "this build has no ads" notice: one slim line, dismissible, remembered per version ----
    private static View notice;

    private static void showNotice(Activity a, FrameLayout host) {
        if (notice != null && notice.getParent() == host) return;
        if (prefs(a).getInt("notice_closed_v", 0) == BuildConfig.VERSION_CODE) return;
        float d = a.getResources().getDisplayMetrics().density;
        android.widget.LinearLayout row = new android.widget.LinearLayout(a);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(0xEE1B2230);
        row.setPadding((int) (12 * d), (int) (4 * d), (int) (2 * d), (int) (4 * d));
        android.widget.TextView t = new android.widget.TextView(a);
        t.setText(com.star4droid.star2d.ui.Tr.t("ads_none_notice", "This version has no ads. Check for the latest version with ads to support the engine."));
        t.setTextColor(0xFFB0B6BF);
        t.setTextSize(11.5f);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(t, new android.widget.LinearLayout.LayoutParams(0, -2, 1));
        android.widget.TextView x = new android.widget.TextView(a);
        x.setText("\u00D7");
        x.setTextColor(0xFFB0B6BF);
        x.setTextSize(20);
        x.setGravity(Gravity.CENTER);
        x.setContentDescription(com.star4droid.star2d.ui.Tr.t("ads_hide", "Hide"));
        x.setOnClickListener(v -> {
            prefs(a).edit().putInt("notice_closed_v", BuildConfig.VERSION_CODE).apply();
            if (notice != null && notice.getParent() instanceof ViewGroup) ((ViewGroup) notice.getParent()).removeView(notice);
            notice = null;
            padContent(host, 0);
        });
        row.addView(x, new android.widget.LinearLayout.LayoutParams((int) (40 * d), (int) (36 * d)));
        notice = row;
        host.addView(row, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        row.post(() -> padContent(host, row.getHeight()));
    }

    public static void removeBanner(FrameLayout host) {
        if (banner != null) { try { if (banner.getParent() instanceof ViewGroup) ((ViewGroup) banner.getParent()).removeView(banner); banner.destroy(); } catch (Throwable ignored) {} banner = null; }
        padContent(host, 0);
    }

    /** Re-evaluate after the balance changed (bought credits -> remove; fell under $1 -> back). */
    public static void refresh(Activity a, FrameLayout host) {
        if (host == null) return;
        if (!BuildConfig.ADS_ENABLED || adsEnabled(a)) attachBanner(a, host); else removeBanner(host);
    }

    // ---- interstitial (after closing a project) ----
    public static void onProjectClosed(Activity a) {
        closes++;
        if (!adsEnabled(a) || !sdkStarted) return;
        long now = System.currentTimeMillis();
        if (closes < MIN_CLOSES || now - sessionStart < SESSION_GRACE_MS || now - lastInterstitial < INTERSTITIAL_GAP_MS) { loadInterstitial(a); return; }
        InterstitialAd ad = interstitial;
        if (ad == null) { loadInterstitial(a); return; }
        interstitial = null;
        lastInterstitial = now;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { loadInterstitial(a); }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) { loadInterstitial(a); }
        });
        ad.show(a);
    }

    // ---- rewarded (optional, Support page) ----
    public static boolean rewardedReady() { return rewarded != null; }

    public static void loadRewarded(Context c, Runnable onChange) {
        if (!sdkStarted || rewarded != null || loadingRewarded) return;
        loadingRewarded = true;
        RewardedAd.load(c, BuildConfig.ADMOB_REWARDED_ID, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(RewardedAd ad) { rewarded = ad; loadingRewarded = false; if (onChange != null) onChange.run(); }
            @Override public void onAdFailedToLoad(LoadAdError e) { rewarded = null; loadingRewarded = false; if (onChange != null) onChange.run(); }
        });
    }

    /** Shows the rewarded video the user asked for; the reward is one hour without ads. */
    public static void showRewarded(Activity a, Runnable onRewarded, Runnable onChange) {
        RewardedAd ad = rewarded;
        if (ad == null) { loadRewarded(a, onChange); return; }
        rewarded = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { loadRewarded(a, onChange); if (onChange != null) onChange.run(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) { loadRewarded(a, onChange); }
        });
        ad.show(a, item -> {
            long base = Math.max(System.currentTimeMillis(), prefs(a).getLong("free_until", 0));
            prefs(a).edit().putLong("free_until", base + REWARD_FREE_MS).apply();
            if (onRewarded != null) onRewarded.run();
        });
    }
}
