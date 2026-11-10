package com.star4droid.star2d.suda;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SUDA3D credits through Google Play Billing (one-time consumable products). The app never credits anything itself:
 * every purchase token goes to POST /billing/google/verify, the server credits the account and consumes the purchase.
 * Tokens that could not be sent (no network, server down) stay in the prefs and in Play as unconsumed purchases; they
 * are re-sent on {@link #recover} (app start / Plan tab / Buy page) until the server accepts them.
 */
public final class SudaBilling {
    private SudaBilling() {}

    /** Default products (product id, credit value in USD); the server list (GET /billing/products) replaces it. */
    public static final String[][] DEFAULTS = {{"5_usd", "5"}, {"10_usd", "10"}, {"15_creds", "15"}, {"fiveteeeen", "50"}};

    public static final class Product {
        public final String id;
        public final double creditUsd, credits;
        public Product(String id, double creditUsd, double credits) { this.id = id; this.creditUsd = creditUsd; this.credits = credits; }
    }

    /** Result of a verified purchase, a pending one, or an error. */
    public interface Listener {
        void onCredited(String productId, double usd, double credits);
        void onPending(String productId);
        void onError(String message);
    }

    private static final String K_QUEUE = "play_unsent_purchases";
    private static BillingClient client;
    private static boolean connecting;
    private static final List<Runnable> whenReady = new ArrayList<>();
    private static final CopyOnWriteArrayList<Listener> L = new CopyOnWriteArrayList<>();
    private static final Map<String, ProductDetails> details = new HashMap<>();
    private static Context app;
    private static final java.util.Set<String> sending = Collections.synchronizedSet(new java.util.HashSet<>());

    public static void listen(Listener l) { L.addIfAbsent(l); }
    public static void unlisten(Listener l) { L.remove(l); }

    /** Server products, or the defaults. */
    public static List<Product> parseProducts(JsonObject r) {
        List<Product> out = new ArrayList<>();
        if (r != null) for (JsonElement e : Suda3D.arr(r, "products")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String id = Suda3D.str(o, "product_id");
            if (id.isEmpty()) continue;
            double usd = Suda3D.num(o, "credit_usd", 0);
            out.add(new Product(id, usd, Suda3D.num(o, "credits", usd * 100)));
        }
        if (out.isEmpty()) for (String[] d : DEFAULTS) {
            double usd = Double.parseDouble(d[1]);
            out.add(new Product(d[0], usd, usd * 100));
        }
        return out;
    }

    // ------------------------------------------------------------ connection

    private static synchronized void ensure(Context c, Runnable ready) {
        if (app == null) app = c.getApplicationContext();
        if (client == null) {
            client = BillingClient.newBuilder(app)
                    .setListener((res, purchases) -> onPurchases(res, purchases))
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .build();
        }
        if (client.isReady()) { if (ready != null) ready.run(); return; }
        if (ready != null) whenReady.add(ready);
        if (connecting) return;
        connecting = true;
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult r) {
                List<Runnable> run;
                synchronized (SudaBilling.class) {
                    connecting = false;
                    run = new ArrayList<>(whenReady);
                    whenReady.clear();
                }
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) for (Runnable x : run) x.run();
                else error(friendly(r));
            }
            @Override public void onBillingServiceDisconnected() {
                synchronized (SudaBilling.class) { connecting = false; }
            }
        });
    }

    static String friendly(BillingResult r) {
        switch (r.getResponseCode()) {
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE:
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE:
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED:
                return Tr.t("s3d_billing_unavailable", "Google Play billing is not available on this device right now. Make sure the Play Store is installed and you are signed in to it.");
            case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE:
            case BillingClient.BillingResponseCode.DEVELOPER_ERROR:
                return Tr.t("s3d_billing_not_play", "Purchases work only in the app installed from Google Play.");
            case BillingClient.BillingResponseCode.NETWORK_ERROR:
                return Tr.t("s3d_err_network", "Can't reach the SUDA3D server. Check the connection.");
            default:
                return Tr.t("s3d_billing_failed", "The purchase did not complete.") + (r.getDebugMessage().isEmpty() ? "" : " (" + r.getResponseCode() + ")");
        }
    }

    // ------------------------------------------------------------ prices

    /** Queries Play for the products' local formatted prices; done gets id → formatted price (UI thread). */
    public static void queryPrices(Context c, List<Product> products, java.util.function.Consumer<Map<String, String>> done) {
        ensure(c, () -> {
            List<QueryProductDetailsParams.Product> list = new ArrayList<>();
            for (Product p : products) list.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(p.id).setProductType(BillingClient.ProductType.INAPP).build());
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(list).build(), (r, res) -> {
                Map<String, String> prices = new HashMap<>();
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && res != null) {
                    for (ProductDetails pd : res.getProductDetailsList()) {
                        synchronized (details) { details.put(pd.getProductId(), pd); }
                        ProductDetails.OneTimePurchaseOfferDetails o = pd.getOneTimePurchaseOfferDetails();
                        if (o != null) prices.put(pd.getProductId(), o.getFormattedPrice());
                    }
                }
                Ui.ui(() -> done.accept(prices));
            });
        });
    }

    public static boolean hasDetails(String productId) { synchronized (details) { return details.containsKey(productId); } }

    // ------------------------------------------------------------ buying

    /** Opens the Google Play purchase sheet for a product (details must have been queried). */
    public static void buy(Activity a, String productId) {
        String account = Suda3D.playAccountId(a);
        ensure(a, () -> Ui.ui(() -> {
            ProductDetails pd;
            synchronized (details) { pd = details.get(productId); }
            if (pd == null || pd.getOneTimePurchaseOfferDetails() == null) {
                error(Tr.t("s3d_billing_not_play", "Purchases work only in the app installed from Google Play."));
                return;
            }
            BillingFlowParams.Builder b = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(
                            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build()));
            if (!account.isEmpty()) b.setObfuscatedAccountId(account);
            BillingResult r = client.launchBillingFlow(a, b.build());
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK
                    && r.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) error(friendly(r));
        }));
    }

    private static void onPurchases(BillingResult r, List<Purchase> purchases) {
        int code = r.getResponseCode();
        if (code == BillingClient.BillingResponseCode.USER_CANCELED) return;
        if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) { recover(app); return; }
        if (code != BillingClient.BillingResponseCode.OK) { error(friendly(r)); return; }
        if (purchases != null) for (Purchase p : purchases) handle(p);
    }

    private static void handle(Purchase p) {
        String pid = p.getProducts().isEmpty() ? "" : p.getProducts().get(0);
        if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            for (Listener l : L) Ui.ui(() -> l.onPending(pid));
            return;
        }
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        queue(pid, p.getPurchaseToken(), p.getOrderId());
        send(pid, p.getPurchaseToken(), p.getOrderId(), true);
    }

    /** Sends one token to the server (once at a time); keeps it queued on network / server failure. */
    private static void send(String pid, String token, String orderId, boolean fresh) {
        if (app == null || !Suda3D.loggedIn(app) || !sending.add(token)) return;
        Suda3D.IO.execute(() -> {
            try {
                JsonObject r = Suda3D.verifyGooglePurchase(app, pid, token, orderId);
                unqueue(token);
                JsonObject cr = Suda3D.obj(r, "credited");
                double usd = Suda3D.num(cr, "usd", 0), credits = Suda3D.num(cr, "credits", 0);
                for (Listener l : L) Ui.ui(() -> l.onCredited(pid, usd, credits));
                try { Suda3D.me(app); } catch (Exception ignored) {}
            } catch (Suda3D.ApiException e) {
                if ("purchase_pending".equals(e.code)) {
                    for (Listener l : L) Ui.ui(() -> l.onPending(pid));
                } else if ("purchase_invalid".equals(e.code) || "purchase_not_owned_by_user".equals(e.code)) {
                    unqueue(token); // the server will never accept it: stop retrying
                    if (fresh) error("purchase_not_owned_by_user".equals(e.code)
                            ? Tr.t("s3d_purchase_other_account", "This purchase belongs to another SUDA3D account.")
                            : Tr.t("s3d_purchase_invalid", "Google Play could not confirm this purchase."));
                } else if (e.status >= 500 || e.unauthorized()) {
                    // keep it: retried on the next start / Plan tab
                } else if (fresh) error(e.getMessage());
            } catch (Exception e) {
                // network failure: the purchase stays queued (and unconsumed in Play), retried later
                if (fresh) error(Tr.t("s3d_purchase_saved", "Payment received by Google Play. Your credits will be added as soon as the server can be reached."));
            } finally {
                sending.remove(token);
            }
        });
    }

    /**
     * Re-sends every purchase the server has not confirmed yet: the local queue plus Play's unconsumed purchases
     * (the server consumes them after crediting). Safe to call often; the server is idempotent.
     */
    public static void recover(Context c) {
        if (c == null) return;
        if (app == null) app = c.getApplicationContext();
        if (!Suda3D.loggedIn(app)) return;
        for (String[] q : queued()) send(q[0], q[1], q[2], false);
        ensure(app, () -> client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build(), (r, list) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK || list == null) return;
            for (Purchase p : list) {
                if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                String pid = p.getProducts().isEmpty() ? "" : p.getProducts().get(0);
                send(pid, p.getPurchaseToken(), p.getOrderId(), false);
            }
        }));
    }

    private static void error(String msg) { for (Listener l : L) Ui.ui(() -> l.onError(msg)); }

    // ------------------------------------------------------------ unsent queue (prefs)

    private static synchronized void queue(String pid, String token, String orderId) {
        if (app == null) return;
        JsonArray a = load();
        for (JsonElement e : a) if (token.equals(Suda3D.str(e.getAsJsonObject(), "token"))) return;
        JsonObject o = new JsonObject();
        o.addProperty("product", pid);
        o.addProperty("token", token);
        o.addProperty("order", orderId == null ? "" : orderId);
        a.add(o);
        Suda3D.prefs(app).edit().putString(K_QUEUE, a.toString()).apply();
    }

    private static synchronized void unqueue(String token) {
        if (app == null) return;
        JsonArray a = load(), b = new JsonArray();
        for (JsonElement e : a) if (!token.equals(Suda3D.str(e.getAsJsonObject(), "token"))) b.add(e);
        Suda3D.prefs(app).edit().putString(K_QUEUE, b.toString()).apply();
    }

    private static synchronized List<String[]> queued() {
        List<String[]> out = new ArrayList<>();
        for (JsonElement e : load()) {
            JsonObject o = e.getAsJsonObject();
            out.add(new String[]{Suda3D.str(o, "product"), Suda3D.str(o, "token"), Suda3D.str(o, "order")});
        }
        return out;
    }

    private static JsonArray load() {
        try {
            SharedPreferences p = Suda3D.prefs(app);
            String s = p.getString(K_QUEUE, "");
            if (!s.isEmpty()) return JsonParser.parseString(s).getAsJsonArray();
        } catch (Exception ignored) {}
        return new JsonArray();
    }
}
