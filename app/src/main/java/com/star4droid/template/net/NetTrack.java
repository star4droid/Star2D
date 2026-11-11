package com.star4droid.template.net;

import com.star4droid.template.Utils.PlayerItem;

import java.util.WeakHashMap;

/**
 * Remembers the last image / sprite animation given to each element (StageImp.setImage / setAnimation call it), so the
 * network can send them: an element does not keep the names itself.
 */
public final class NetTrack {
	private NetTrack() {
	}

	private static final WeakHashMap<Object, String> IMAGES = new WeakHashMap<Object, String>();
	private static final WeakHashMap<Object, String> ANIMS = new WeakHashMap<Object, String>();

	public static void image(PlayerItem item, String image) {
		if (item == null) return;
		synchronized (IMAGES) {
			IMAGES.put(item, image == null ? "" : image);
		}
	}

	public static void anim(PlayerItem item, String anim) {
		if (item == null) return;
		synchronized (ANIMS) {
			ANIMS.put(item, anim == null ? "" : anim);
		}
	}

	public static String image(PlayerItem item) {
		synchronized (IMAGES) {
			String s = IMAGES.get(item);
			if (s != null) return s;
		}
		try {
			Object d = item.getProperties();
			java.lang.reflect.Field f = d.getClass().getField("image");
			Object v = f.get(d);
			return v == null ? "" : v.toString();
		} catch (Exception e) {
			return "";
		}
	}

	public static String anim(PlayerItem item) {
		synchronized (ANIMS) {
			String s = ANIMS.get(item);
			if (s != null) return s;
		}
		try {
			Object d = item.getProperties();
			java.lang.reflect.Field f = d.getClass().getField("animation");
			Object v = f.get(d);
			return v == null ? "" : v.toString();
		} catch (Exception e) {
			return "";
		}
	}
}
