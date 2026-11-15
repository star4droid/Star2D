package com.star4droid.star2d.suda;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.star4droid.star2d.MainActivity;
import com.star4droid.star2d.evo.R;

/** Receives pushes. The text is already in the user's notification language (composed by the site). */
public class PushService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        Social.prefs(this).edit().putString("fcm_token", token).apply();
        Push.sendToken(this);
    }

    @Override public void onMessageReceived(RemoteMessage m) {
        String title = null, body = null;
        if (m.getNotification() != null) { title = m.getNotification().getTitle(); body = m.getNotification().getBody(); }
        if (title == null) title = m.getData().get("title");
        if (body == null) body = m.getData().get("body");
        if (title == null && body == null) return;
        Push.channel(this);
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, Push.CHANNEL)
                .setSmallIcon(R.drawable.icon).setContentTitle(title).setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(pi);
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), b.build()); } catch (Throwable ignored) {}
    }
}
