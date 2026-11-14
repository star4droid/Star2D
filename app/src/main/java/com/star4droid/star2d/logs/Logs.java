package com.star4droid.star2d.logs;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The one place that writes the editor's log files, under Android/data/&lt;package&gt;/files/logs/:
 * <pre>
 *   logs/editor/&lt;topic&gt;.txt       editor problems (properties, loading, undo, drawables...)
 *   logs/build/&lt;project&gt;/...       code generation, compile errors, dex
 *   logs/game/&lt;topic&gt;.txt         errors of a game played inside the editor
 *   logs/crash/&lt;time&gt;.txt          unhandled exceptions (one file per crash)
 *   logs/crash/last_exit.txt       why the app was closed last time (native crash, out of memory, ANR...)
 * </pre>
 * Folders are made only when a file is written, and deleting a log also removes the folders it leaves empty.
 * Every entry is time stamped and every file is capped, so the logs never grow without limit.
 */
public final class Logs {
	public static final String EDITOR = "editor", BUILD = "build", GAME = "game", CRASH = "crash";
	private static final long MAX_FILE = 256 * 1024;
	private static final int MAX_CRASH_FILES = 20;
	private static volatile File root;

	private Logs() {}

	/** Called once from the Application (works without it too, through LibGDX's external folder). */
	public static void init(Context c) {
		try {
			File base = c.getExternalFilesDir(null);
			if (base == null) base = c.getFilesDir();
			root = new File(base, "logs");
		} catch (Throwable ignored) {
		}
		migrateOld();
	}

	/** logs/ folder (not created). */
	public static File root() {
		File r = root;
		if (r != null) return r;
		try {
			if (com.badlogic.gdx.Gdx.files != null) return root = new File(com.badlogic.gdx.Gdx.files.getExternalStoragePath(), "logs");
		} catch (Throwable ignored) {
		}
		try {
			Context c = com.star4droid.star2d.evo.star2dApp.getContext();
			if (c != null) init(c);
		} catch (Throwable ignored) {
		}
		return root != null ? root : new File("/sdcard/Android/data/com.star4droid.star2d.evo/files/logs");
	}

	public static File file(String category, String name) {
		return new File(new File(root(), category), clean(name) + ".txt");
	}

	/** Appends a time stamped entry to logs/&lt;category&gt;/&lt;name&gt;.txt. */
	public static void write(String category, String name, String text) {
		append(file(category, name), stamp() + text + "\n\n");
	}

	public static void error(String category, String name, String message, Throwable t) {
		write(category, name, (message == null || message.isEmpty() ? "" : message + "\n") + trace(t));
	}

	public static void editor(String name, String text) { write(EDITOR, name, text); }

	public static void editorError(String name, String message, Throwable t) { error(EDITOR, name, message, t); }

	public static void game(String name, String text) { write(GAME, name, text); }

	/** logs/build/&lt;project&gt;/&lt;name&gt;.txt */
	public static File buildFile(String project, String name) {
		return new File(new File(new File(root(), BUILD), clean(project)), clean(name) + ".txt");
	}

	public static void build(String project, String name, String text) {
		append(buildFile(project, name), stamp() + text + "\n\n");
	}

	/** Replaces the whole file (for "last result" files such as the latest compile errors). */
	public static void put(File f, String text) {
		try {
			mkParent(f);
			FileOutputStream out = new FileOutputStream(f, false);
			out.write(text.getBytes("UTF-8"));
			out.close();
		} catch (Throwable ignored) {
		}
	}

	public static String read(File f) {
		try {
			if (!f.exists()) return "";
			FileInputStream in = new FileInputStream(f);
			byte[] b = new byte[(int) Math.min(f.length(), MAX_FILE)];
			int n = in.read(b);
			in.close();
			return n <= 0 ? "" : new String(b, 0, n, "UTF-8");
		} catch (Throwable t) {
			return "";
		}
	}

	/** Deletes a log file and then every folder it leaves empty (up to logs/). */
	public static void delete(File f) {
		try {
			if (f.isDirectory()) {
				File[] fs = f.listFiles();
				if (fs != null) for (File x : fs) delete(x);
			}
			f.delete();
			removeEmptyParents(f.getParentFile());
		} catch (Throwable ignored) {
		}
	}

	/** Removes empty folders from {@code dir} upwards, stopping at logs/ (kept only if not empty either). */
	public static void removeEmptyParents(File dir) {
		File r = root();
		try {
			while (dir != null && dir.isDirectory() && dir.getAbsolutePath().startsWith(r.getAbsolutePath())) {
				String[] left = dir.list();
				if (left != null && left.length > 0) break;
				if (!dir.delete()) break;
				if (dir.getAbsolutePath().equals(r.getAbsolutePath())) break;
				dir = dir.getParentFile();
			}
		} catch (Throwable ignored) {
		}
	}

	/** Removes every empty folder under logs/ (old versions left one per project). */
	public static void cleanEmptyFolders() {
		cleanEmpty(root(), true);
	}

	private static boolean cleanEmpty(File dir, boolean isRoot) {
		if (dir == null || !dir.isDirectory()) return false;
		File[] fs = dir.listFiles();
		boolean empty = true;
		if (fs != null) for (File f : fs) {
			if (f.isDirectory()) { if (!cleanEmpty(f, false)) empty = false; }
			else empty = false;
		}
		if (empty && !isRoot) return dir.delete();
		return empty;
	}

	/** Writes an unhandled exception to logs/crash/&lt;time&gt;.txt and returns the text. */
	public static String crash(Thread thread, Throwable t) {
		String text = header() + "Thread: " + (thread == null ? "?" : thread.getName()) + "\n\n" + trace(t);
		File dir = new File(root(), CRASH);
		append(new File(dir, new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt"), text);
		trimFolder(dir, MAX_CRASH_FILES);
		return text;
	}

	/**
	 * Records why the app process ended the previous time (Android 11+): native crash (with its tombstone),
	 * killed for memory, ANR, crash... These leave no Java log, so this is the only trace of them.
	 */
	public static void recordLastExit(Context c) {
		if (Build.VERSION.SDK_INT < 30) return;
		try {
			android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
			java.util.List<android.app.ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(null, 0, 5);
			if (list == null || list.isEmpty()) return;
			android.content.SharedPreferences p = c.getSharedPreferences("star2d_logs", Context.MODE_PRIVATE);
			long seen = p.getLong("last_exit_seen", 0), newest = seen;
			StringBuilder sb = new StringBuilder();
			for (android.app.ApplicationExitInfo e : list) {
				if (e.getTimestamp() <= seen) continue;
				newest = Math.max(newest, e.getTimestamp());
				int r = e.getReason();
				// a normal close is not worth a log
				if (r == android.app.ApplicationExitInfo.REASON_USER_REQUESTED || r == android.app.ApplicationExitInfo.REASON_EXIT_SELF
						|| r == android.app.ApplicationExitInfo.REASON_USER_STOPPED || r == android.app.ApplicationExitInfo.REASON_PACKAGE_UPDATED) continue;
				sb.append("Time: ").append(new Date(e.getTimestamp())).append("\nProcess: ").append(e.getProcessName())
						.append("\nReason: ").append(reasonName(r)).append(" (").append(r).append(")")
						.append("\nDescription: ").append(e.getDescription())
						.append("\nMemory: pss ").append(e.getPss() / 1024).append(" MB, rss ").append(e.getRss() / 1024).append(" MB\n");
				if (r == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE || r == android.app.ApplicationExitInfo.REASON_ANR) {
					try {
						java.io.InputStream in = e.getTraceInputStream();
						if (in != null) {
							java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
							byte[] b = new byte[8192];
							int n, total = 0;
							while ((n = in.read(b)) > 0 && total < 4000000) { bo.write(b, 0, n); total += n; }
							in.close();
							sb.append("\n").append(r == android.app.ApplicationExitInfo.REASON_ANR ? new String(bo.toByteArray(), 0, Math.min(bo.size(), 400000), "UTF-8") : nativeSummary(bo.toByteArray()));
						}
					} catch (Throwable ignored) {
					}
				}
				sb.append("\n").append("-".repeat(30)).append("\n");
			}
			p.edit().putLong("last_exit_seen", newest).apply();
			if (sb.length() > 0) put(new File(new File(root(), CRASH), "last_exit.txt"), header() + "\n" + sb);
		} catch (Throwable ignored) {
		}
	}

	/**
	 * The native tombstone is a protobuf (frameworks tombstone.proto). It is decoded here: signal, abort message, causes,
	 * the full backtrace of the crashing thread first, short backtraces of the other threads, then the last log lines
	 * of the process. Falls back to the readable strings when the decoding fails.
	 */
	static String nativeSummary(byte[] proto) {
		try {
			return Tombstone.format(proto);
		} catch (Throwable t) {
			StringBuilder sb = new StringBuilder("Native trace (readable parts, decode failed: " + t + "):\n");
			StringBuilder cur = new StringBuilder();
			int lines = 0;
			for (byte x : proto) {
				char ch = (char) (x & 0xff);
				if (ch >= 32 && ch < 127) cur.append(ch);
				else {
					if (cur.length() >= 6) { sb.append(cur).append('\n'); if (++lines > 1500) break; }
					cur.setLength(0);
				}
			}
			return sb.toString();
		}
	}

	private static String reasonName(int r) {
		switch (r) {
			case 1: return "EXIT_SELF";
			case 2: return "SIGNALED";
			case 3: return "LOW_MEMORY";
			case 4: return "CRASH (Java)";
			case 5: return "CRASH_NATIVE";
			case 6: return "ANR";
			case 7: return "INITIALIZATION_FAILURE";
			case 8: return "PERMISSION_CHANGE";
			case 9: return "EXCESSIVE_RESOURCE_USAGE";
			case 10: return "USER_REQUESTED";
			case 11: return "USER_STOPPED";
			case 12: return "DEPENDENCY_DIED";
			case 13: return "OTHER";
			case 14: return "FREEZER";
			case 15: return "PACKAGE_STATE_CHANGE";
			case 16: return "PACKAGE_UPDATED";
			default: return "UNKNOWN";
		}
	}

	/** Old versions wrote ~50 loose files straight into logs/: move them into logs/old/ once and drop empty folders. */
	private static void migrateOld() {
		try {
			File r = root;
			if (r == null || !r.isDirectory()) return;
			File marker = new File(r, ".organized");
			if (marker.exists()) return;
			File[] fs = r.listFiles();
			if (fs != null) for (File f : fs) {
				if (f.isFile() && !f.getName().startsWith(".")) {
					File old = new File(r, "old");
					old.mkdirs();
					f.renameTo(new File(old, f.getName()));
				}
			}
			cleanEmptyFolders();
			r.mkdirs();
			new FileOutputStream(marker).close();
		} catch (Throwable ignored) {
		}
	}

	// ------------------------------------------------------------------

	public static String trace(Throwable t) {
		if (t == null) return "";
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}

	private static String header() {
		String ver = "";
		try {
			Context c = com.star4droid.star2d.evo.star2dApp.getContext();
			ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
		} catch (Throwable ignored) {
		}
		return "Time: " + new Date() + "\nStar2D: " + ver + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
				+ " (Android " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + ")\n";
	}

	private static String stamp() {
		return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
	}

	private static String clean(String name) {
		if (name == null || name.isEmpty()) return "log";
		String s = name.trim().replaceAll("[\\\\/:*?\"<>|]+", "_").replaceAll("\\s+", "_");
		while (s.startsWith("_") || s.startsWith(".")) s = s.substring(1);
		return s.isEmpty() ? "log" : s;
	}

	private static void mkParent(File f) {
		File p = f.getParentFile();
		if (p != null && !p.exists()) p.mkdirs();
	}

	/** Appends, keeping each file under MAX_FILE (the older half is dropped when it gets too big). */
	private static synchronized void append(File f, String text) {
		try {
			mkParent(f);
			if (f.length() + text.length() > MAX_FILE) {
				String keep = read(f);
				keep = keep.length() > MAX_FILE / 2 ? keep.substring(keep.length() - (int) (MAX_FILE / 2)) : keep;
				put(f, "... (older entries removed)\n" + keep);
			}
			FileOutputStream out = new FileOutputStream(f, true);
			out.write(text.getBytes("UTF-8"));
			out.close();
		} catch (Throwable ignored) {
		}
	}

	private static void trimFolder(File dir, int keep) {
		File[] fs = dir.listFiles((d, n) -> n.endsWith(".txt") && !n.equals("last_exit.txt"));
		if (fs == null || fs.length <= keep) return;
		java.util.Arrays.sort(fs, (a, b) -> a.getName().compareTo(b.getName()));
		for (int i = 0; i < fs.length - keep; i++) fs[i].delete();
	}
}
