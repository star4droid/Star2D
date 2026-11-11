package com.star4droid.star2d.Helpers;

import java.io.File;

/** Export: whether a project plays over the network (it then needs the Wi-Fi multicast permission). Pure Java. */
public final class NetExport {
	private NetExport() {
	}

	public static final String MULTICAST = "android.permission.CHANGE_WIFI_MULTICAST_STATE";

	/** True when a scene has a NETWORK element or some code uses the Network blocks / classes. */
	public static boolean usesNetwork(String projectPath) {
		if (projectPath == null) return false;
		File[] scenes = new File(projectPath, "scenes").listFiles();
		if (scenes != null)
			for (File f : scenes)
				if (f.isFile() && contains(f, "\"NETWORK\"")) return true;
		return scan(new File(projectPath, "Events"), 0) || scan(new File(projectPath, "scripts"), 0) || scan(new File(projectPath, "java"), 0);
	}

	private static boolean scan(File dir, int depth) {
		File[] fs = dir.listFiles();
		if (fs == null || depth > 8) return false;
		for (File f : fs) {
			if (f.isDirectory()) {
				if (scan(f, depth + 1)) return true;
			} else if ((f.getName().endsWith(".java") || f.getName().endsWith(".code")) && contains(f, "template.net.Net")) return true;
		}
		return false;
	}

	private static boolean contains(File f, String what) {
		if (f.length() > 8L * 1024 * 1024) return false;
		try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
			byte[] b = new byte[(int) f.length()];
			int n = 0, r;
			while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
			return new String(b, 0, n, "UTF-8").contains(what);
		} catch (Exception e) {
			return false;
		}
	}

	/** The manifest text with the multicast permission (once). */
	public static String addMulticastPermission(String manifest) {
		if (manifest == null || manifest.contains(MULTICAST)) return manifest;
		String perm = "    <uses-permission\n        android:name=\"" + MULTICAST + "\"/>\n";
		int i = manifest.indexOf("<uses-feature");
		if (i < 0) i = manifest.indexOf("<application");
		if (i < 0) return manifest;
		return manifest.substring(0, i) + perm.trim() + "\n    " + manifest.substring(i);
	}
}
