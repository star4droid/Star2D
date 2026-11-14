package com.star4droid.template;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Writes an unhandled exception of the game to Android/data/<package>/files/crash/ and, the next time the game opens,
 * shows a dialog with the previous errors (copy, or ignore and delete them).
 */
public class CrashLog {
	private static boolean installed;

	public static File dir(Context c) {
		File base = c.getExternalFilesDir(null);
		if (base == null) base = c.getFilesDir();
		// made only when a crash is written (dir(c).mkdirs() there), so a game that never crashed leaves no empty folder
		return new File(base, "crash");
	}

	public static void install(final Context ctx) {
		if (installed) return;
		installed = true;
		final Context app = ctx.getApplicationContext();
		final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				try {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					String ver = "";
					try { ver = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName; } catch (Throwable ignored) { }
					String text = "Time: " + new Date() + "\nPackage: " + app.getPackageName() + " " + ver + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
							+ " (Android " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + ")\nThread: " + t.getName() + "\n\n" + sw;
					dir(app).mkdirs();
					File f = new File(dir(app), new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt");
					FileOutputStream out = new FileOutputStream(f);
					out.write(text.getBytes("UTF-8"));
					out.close();
				} catch (Throwable ignored) {
				}
				if (old != null) old.uncaughtException(t, e);
			}
		});
	}

	private static String readAll(File f) {
		try {
			java.io.FileInputStream in = new java.io.FileInputStream(f);
			byte[] b = new byte[(int) Math.min(f.length(), 200000)];
			int n = in.read(b);
			in.close();
			return n <= 0 ? "" : new String(b, 0, n, "UTF-8");
		} catch (Throwable t) {
			return "";
		}
	}

	/** Shows the previous errors if there are any. {@code done} runs when the dialog is closed (or at once when there are none). */
	public static void showPrevious(final Activity a, final Runnable done) {
		final File[] files = dir(a).listFiles();
		if (files == null || files.length == 0) {
			done.run();
			return;
		}
		java.util.Arrays.sort(files);
		final StringBuilder sb = new StringBuilder();
		for (int i = files.length - 1; i >= 0; i--) {
			sb.append("=== ").append(files[i].getName()).append(" ===\n").append(readAll(files[i])).append("\n\n");
		}
		TextView tv = new TextView(a);
		tv.setText(sb);
		tv.setTextIsSelectable(true);
		tv.setTextSize(11);
		tv.setPadding(32, 16, 32, 16);
		android.widget.ScrollView sv = new android.widget.ScrollView(a);
		sv.addView(tv);
		AlertDialog d = new AlertDialog.Builder(a).setTitle("Previous errors (" + files.length + ")").setView(sv)
				.setPositiveButton("Ignore and delete", new android.content.DialogInterface.OnClickListener() {
					public void onClick(android.content.DialogInterface di, int w) {
						for (File f : files) f.delete();
						dir(a).delete(); // remove the folder too when it is empty
					}
				})
				.setNeutralButton("Copy error", null)
				.setNegativeButton("Close", null)
				.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
					public void onDismiss(android.content.DialogInterface di) {
						done.run();
					}
				}).create();
		d.show();
		d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new android.view.View.OnClickListener() {
			public void onClick(android.view.View v) {
				ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
				if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("crash", sb.toString()));
				Toast.makeText(a, "Copied", Toast.LENGTH_SHORT).show();
			}
		});
	}
}
