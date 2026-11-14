package com.star4droid.star2d.evo;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.app.Application;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.elevation.SurfaceColors;
import com.star4droid.star2d.DebugActivity;
import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Utils;

public class star2dApp extends Application {
	private static Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
	private static Context mApplicationContext;
	public static Context getContext() {
		return mApplicationContext;
	}
	public static Thread.UncaughtExceptionHandler getUncaughtExceptionHandler(){
		return uncaughtExceptionHandler;
	}
	public void onCreate() {
		com.star4droid.star2d.logs.Logs.init(this);
		com.star4droid.star2d.logs.Logs.recordLastExit(this);
		EngineSettings.init(this);
		Utils.setLanguage(this);
		com.star4droid.star2d.ui.Snack.install(this);
		com.star4droid.star2d.ui.tutorial.Tutorial.install(this);
		
		new com.github.anrwatchdog.ANRWatchDog(7000).setANRListener(new ANRWatchDog.ANRListener() {
    		@Override
    		public void onAppNotResponding(ANRError error) {
        		// record it only: killing the app here closed it silently (lost work) whenever the UI was busy for a few seconds
				com.star4droid.star2d.logs.Logs.editorError("not_responding", "The UI thread did not respond for 7 seconds", error);
				//ExceptionHandler.saveException(error, new CrashManager());
    		}
		}).start();
		mApplicationContext = this.getApplicationContext();
		if(uncaughtExceptionHandler==null)
			uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
			
			public void uncaughtException(Thread thread, Throwable throwable) {
				final Intent intent = new Intent(mApplicationContext, DebugActivity.class);
				intent.putExtra("error", Log.getStackTraceString(throwable));
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
				//PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 11111, intent, PendingIntent.FLAG_ONE_SHOT);
				com.star4droid.star2d.logs.Logs.crash(thread, throwable);
				//AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
				//am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 1000, pendingIntent);
				new Handler(Looper.getMainLooper()).postDelayed(()->mApplicationContext.startActivity(intent),1500);
				//Process.killProcess(Process.myPid());
				//System.exit(1);
				//Logger.initialize(this);
				star2dApp.uncaughtExceptionHandler.uncaughtException(thread, throwable);
			}
		});
        
           
		super.onCreate();
		// SUDA3D: re-send Google Play purchases the server has not confirmed yet (it credits + consumes them)
		try { com.star4droid.star2d.suda.SudaBilling.recover(this); } catch (Throwable ignored) {}
	}
}