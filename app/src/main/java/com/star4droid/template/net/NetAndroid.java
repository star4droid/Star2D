package com.star4droid.template.net;

import com.badlogic.gdx.Gdx;

import java.lang.reflect.Method;

/**
 * Android bits through reflection, so the same runtime also runs on the desktop templates: the Wi-Fi multicast lock
 * (some phones drop the broadcast search packets without it) and the device name. Everything is a no-op off Android.
 */
final class NetAndroid {
	private NetAndroid() {
	}

	private static Object context() {
		try {
			Object app = Gdx.app;
			if (app == null) return null;
			Class<?> ctx = Class.forName("android.content.Context");
			if (ctx.isInstance(app)) return app;
			Method m = app.getClass().getMethod("getContext");
			return m.invoke(app);
		} catch (Throwable t) {
			return null;
		}
	}

	/** Acquires a Wi-Fi multicast lock (needs CHANGE_WIFI_MULTICAST_STATE), null when not possible. */
	static Object multicastLock() {
		try {
			Object ctx = context();
			if (ctx == null) return null;
			Object appCtx = ctx.getClass().getMethod("getApplicationContext").invoke(ctx);
			Object wifi = appCtx.getClass().getMethod("getSystemService", String.class).invoke(appCtx, "wifi");
			if (wifi == null) return null;
			Object lock = wifi.getClass().getMethod("createMulticastLock", String.class).invoke(wifi, "star2d-net");
			lock.getClass().getMethod("setReferenceCounted", boolean.class).invoke(lock, true);
			lock.getClass().getMethod("acquire").invoke(lock);
			return lock;
		} catch (Throwable t) {
			return null;
		}
	}

	static void release(Object lock) {
		if (lock == null) return;
		try {
			Method held = lock.getClass().getMethod("isHeld");
			if (Boolean.TRUE.equals(held.invoke(lock))) lock.getClass().getMethod("release").invoke(lock);
		} catch (Throwable ignored) {
		}
	}

	/** The phone model ("Poco X5 Pro"), or the computer name, as a default player name. */
	static String deviceName() {
		try {
			Class<?> b = Class.forName("android.os.Build");
			Object model = b.getField("MODEL").get(null);
			if (model != null && !model.toString().trim().isEmpty()) return model.toString().trim();
		} catch (Throwable ignored) {
		}
		try {
			String n = java.net.InetAddress.getLocalHost().getHostName();
			if (n != null && !n.isEmpty()) return n;
		} catch (Throwable ignored) {
		}
		return "Player";
	}

	/** This device's address on the local network (192.168.x.x), "" when unknown. */
	static String localAddress() {
		try {
			java.util.Enumeration<java.net.NetworkInterface> ifs = java.net.NetworkInterface.getNetworkInterfaces();
			while (ifs != null && ifs.hasMoreElements()) {
				java.net.NetworkInterface ni = ifs.nextElement();
				if (!ni.isUp() || ni.isLoopback()) continue;
				java.util.Enumeration<java.net.InetAddress> as = ni.getInetAddresses();
				while (as.hasMoreElements()) {
					java.net.InetAddress a = as.nextElement();
					if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
				}
			}
		} catch (Throwable ignored) {
		}
		return "";
	}
}
