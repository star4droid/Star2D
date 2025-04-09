package com.star4droid.star2d.editor.utils;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

public class StarApp extends Application {
  private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
  private static Context mApplicationContext;

  public static Context getContext() {
    return mApplicationContext;
  }

  public void onCreate() {
    mApplicationContext = this.getApplicationContext();
    this.uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        new Thread.UncaughtExceptionHandler() {

          public void uncaughtException(Thread thread, Throwable throwable) {
            // final Intent intent = new Intent(mApplicationContext, DebugActivity.class);
            // intent.putExtra("error", Log.getStackTraceString(throwable));
            // intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            // PendingIntent pendingIntent =
                // PendingIntent.getActivity(
                    // getApplicationContext(), 11111, intent, PendingIntent.FLAG_ONE_SHOT);
            int x = 0;
            String str =
                getExternalFilesDir(null)
                    .getAbsolutePath()
                    .concat("/logs/log")
                    .concat("%1$s")
                    .concat(".txt");
            while (FileUtil.isExistFile(String.format(str,x+""))) {
              x++;
            }
            String log = Log.getStackTraceString(throwable);
            // if(!FileUtil.readFile(String.format(str,(x-1)+"")).equals(log))
            FileUtil.writeFile(String.format(str, String.valueOf(x)), log);
            // AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            // am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 1000, pendingIntent);
            // //Process.killProcess(Process.myPid());
            // mApplicationContext.startActivity(intent);
            // System.exit(1);
            // Logger.initialize(this);
            StarApp.this.uncaughtExceptionHandler.uncaughtException(thread, throwable);
          }
        });

    super.onCreate();
  }
}
