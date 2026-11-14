package com.star4droid.template.Utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Log files of the game engine runtime (works in exported games and in games played inside the editor):
 * logs/game/&lt;name&gt;.txt in the app's own external folder. The folder is made only when something is written,
 * each entry is time stamped and each file is capped.
 */
public final class GameLog {
	private static final long MAX = 128 * 1024;

	private GameLog() {}

	public static FileHandle file(String name) {
		return Gdx.files.external("logs/game/" + name.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "_") + ".txt");
	}

	public static synchronized void write(String name, String text) {
		try {
			if (Gdx.files == null) return;
			FileHandle f = file(name);
			String entry = "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] " + text + "\n\n";
			if (f.exists() && f.length() + entry.length() > MAX) {
				String keep = f.readString();
				keep = keep.substring(Math.max(0, keep.length() - (int) (MAX / 2)));
				f.writeString("... (older entries removed)\n" + keep, false);
			}
			f.writeString(entry, true);
		} catch (Throwable ignored) {
		}
	}

	public static void error(String name, String message, Throwable t) {
		write(name, (message == null ? "" : message + "\n") + (t == null ? "" : Utils.getStackTraceString(t)));
	}

	/** Deletes a log file, then its folders if they became empty. */
	public static void delete(FileHandle f) {
		try {
			f.delete();
			FileHandle p = f.parent();
			while (p != null && p.isDirectory() && p.list().length == 0 && p.path().contains("logs")) {
				FileHandle up = p.parent();
				p.delete();
				if (p.name().equals("logs")) break;
				p = up;
			}
		} catch (Throwable ignored) {
		}
	}
}
