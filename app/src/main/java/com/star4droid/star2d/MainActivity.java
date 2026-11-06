package com.star4droid.star2d;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;

import android.os.Bundle;
import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.evo.R;
import java.util.Timer;
import java.util.TimerTask;
import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		com.star4droid.star2d.ui.SystemBars.install(this);
		Utils.setLanguage(this);
        setContentView(R.layout.activity_main);
		setupSplash();
		EngineSettings.init(this);
		checkPerms(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE/*, Manifest.permission.ACCESS_MEDIA_LOCATION*/);
		new Timer().schedule(new TimerTask(){
			@Override
			public void run() {
			    open();
			}
		},3000);
		 
    }
    
    /** Splash in the editor's style: the logo card pops in and breathes, the bar fills while the engine starts, touches make ripples and sparks. */
    private void setupSplash(){
        try {
            final com.star4droid.star2d.ui.SplashView splash = findViewById(R.id.splash);
            final android.view.View card = findViewById(R.id.logo_card);
            final android.view.View track = findViewById(R.id.bar_track);
            final android.view.View fill = findViewById(R.id.bar_fill);
            final android.view.View title = findViewById(R.id.title);
            final android.view.View hint = findViewById(R.id.hint);
            final android.view.View credit = findViewById(R.id.credit);
            final float d = getResources().getDisplayMetrics().density;
            android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
            cardBg.setColor(0xFF202328);
            cardBg.setCornerRadius(24 * d);
            cardBg.setStroke((int) Math.max(1, d), 0xFF3A4048);
            card.setBackground(cardBg);
            card.setElevation(10 * d);
            android.graphics.drawable.GradientDrawable trackBg = new android.graphics.drawable.GradientDrawable();
            trackBg.setColor(0xFF333840);
            trackBg.setCornerRadius(2 * d);
            track.setBackground(trackBg);
            android.graphics.drawable.GradientDrawable fillBg = new android.graphics.drawable.GradientDrawable();
            fillBg.setColor(0xFF2196F3);
            fillBg.setCornerRadius(2 * d);
            fill.setBackground(fillBg);
            fill.setPivotX(0);
            fill.setScaleX(0f);
            card.setScaleX(0.5f);
            card.setScaleY(0.5f);
            card.setAlpha(0f);
            card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(520).setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).withEndAction(() -> {
                android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofPropertyValuesHolder(card,
                        android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.05f),
                        android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.05f));
                a.setDuration(1100);
                a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                a.start();
            }).start();
            for (android.view.View v : new android.view.View[]{track, title, hint, credit}) v.setAlpha(0f);
            track.animate().alpha(1f).setStartDelay(250).setDuration(400).start();
            title.animate().alpha(1f).setStartDelay(350).setDuration(500).start();
            hint.animate().alpha(1f).setStartDelay(700).setDuration(500).start();
            credit.animate().alpha(1f).setStartDelay(500).setDuration(500).start();
            // the bar fills while the engine starts (the editor opens after 3 s)
            fill.animate().scaleX(1f).setStartDelay(300).setDuration(2600).setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f)).start();
            card.setOnClickListener(v -> {
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                int[] sl = new int[2];
                splash.getLocationOnScreen(sl);
                splash.burst(loc[0] - sl[0] + v.getWidth() / 2f, loc[1] - sl[1] + v.getHeight() / 2f);
                v.animate().rotationBy(360f).setDuration(520).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            });
        } catch (Throwable t) {
            android.util.Log.e("star2d", "splash : " + android.util.Log.getStackTraceString(t));
        }
    }

    private void open(){
		if(TestApp.getCurrentApp()!=null){
			finish();
			return;
		}
        Intent i = new Intent();
		i.setClass(MainActivity.this,com.star4droid.star2d.EditorActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		startActivity(i);
		finish();
    }
    /*
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1000) {
            open();
        }
    }
    */
    public boolean checkPerms(final String... perms) {
		for(String i : perms)
        if (ContextCompat.checkSelfPermission(this, i) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(
                                MainActivity.this, perms, 1000);
			/*
			final AlertDialog dialog = new AlertDialog.Builder(this).create();
            dialog.setTitle("Warning !");
            dialog.setMessage("Give the the necessary permissions...");
            dialog.setButton(
                    AlertDialog.BUTTON_POSITIVE,
                    "Ok",
                    (arg0, arg1) -> {
                        ActivityCompat.requestPermissions(
                                MainActivity.this, perms, 1000);
                        dialog.dismiss();
                    });
            dialog.setCancelable(false);
            dialog.show();
			*/
            return false;
        }
        return true;
    }
}