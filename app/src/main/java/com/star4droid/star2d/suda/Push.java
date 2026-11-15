package com.star4droid.star2d.suda;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

/** Firebase push registration. Every call is safe when Firebase is not set up (no google-services.json in the build). */
public final class Push {
    private Push() {}

    public static final String CHANNEL = "community";

    static boolean available(Context c) {
        try { return !FirebaseApp.getApps(c.getApplicationContext()).isEmpty(); } catch (Throwable t) { return false; }
    }

    public static void channel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Community", NotificationManager.IMPORTANCE_DEFAULT));
    }

    /** After sign-in / on start: ask permission (Android 13+), then send the device token to the site. */
    public static void register(Activity a) {
        if (!available(a) || !Suda3D.loggedIn(a)) return;
        channel(a);
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !Social.prefs(a).getBoolean("asked_push", false)) {
            Social.prefs(a).edit().putBoolean("asked_push", true).apply();
            try { a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7311); } catch (Throwable ignored) {}
        }
        sendToken(a.getApplicationContext());
    }

    static void sendToken(Context c) {
        if (!available(c) || !Suda3D.loggedIn(c)) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(tok -> {
                if (tok == null || tok.isEmpty()) return;
                Social.prefs(c).edit().putString("fcm_token", tok).apply();
                Suda3D.IO.execute(() -> { try { CommunityApi.addDevice(c, tok); } catch (Exception ignored) {} });
            });
        } catch (Throwable ignored) {}
    }

    /** Sign-out: stop pushes for this account on this device. */
    public static void unregister(Context c) {
        String tok = Social.prefs(c).getString("fcm_token", "");
        Social.prefs(c).edit().remove("fcm_token").remove("seen_" + Suda3D.userId(c)).apply();
        if (tok.isEmpty() || !Suda3D.loggedIn(c)) return;
        try {
            // the request (with the key) is built now; it is sent in the background after the key is gone
            com.google.gson.JsonObject b = new com.google.gson.JsonObject();
            b.addProperty("token", tok);
            okhttp3.Request req = Suda3D.base(c, "/community/devices", true)
                    .method("DELETE", okhttp3.RequestBody.create(b.toString(), Suda3D.JSON)).build();
            Suda3D.IO.execute(() -> { try { Suda3D.exec(req); } catch (Throwable ignored) {} });
        } catch (Throwable ignored) {}
    }
}
