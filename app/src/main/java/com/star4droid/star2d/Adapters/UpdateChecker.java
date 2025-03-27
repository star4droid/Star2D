package com.star4droid.star2d.Adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.star4droid.star2d.evo.R;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {

    private static final String CURRENT_VERSION = "0.1.5";
    private static final String CHECK_UPDATE_URL = "https://raw.githubusercontent.com/star4droid/Star2D/refs/heads/master/assets/latest.version"; // Replace with your URL
    private static final String UPDATE_PAGE_URL = "https://github.com/abodinagdat16/Star2D/releases/"; // Replace with your update page URL

    public static void checkForUpdate(Context context) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executorService.execute(() -> {
            String latestVersion = fetchLatestVersion();
            mainHandler.post(() -> {
                if (latestVersion != null && !latestVersion.equals(CURRENT_VERSION)) {
                    showUpdateDialog(context);
                }
            });
        });
    }

    private static String fetchLatestVersion() {
        String response = null;
        try {
            URL url = new URL(CHECK_UPDATE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();
                response = result.toString().trim();
            }
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
           // throw new RuntimeException(e.toString());
        }
        return response;
    }

    private static void showUpdateDialog(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.update_needed))
                .setMessage(context.getString(R.string.update_needed))
                .setCancelable(false)
                .setNegativeButton(context.getString(R.string.close), (dialog, which) -> dialog.dismiss())
                .setPositiveButton(context.getString(R.string.update), (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_PAGE_URL));
                    context.startActivity(browserIntent);
                })
                .show();
    }
}