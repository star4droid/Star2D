package com.star4droid.template.behaviors;

/** Small conversions used by the behavior blocks ("setting ___ of behavior ___"); no android / libgdx imports. */
public final class BehaviorOps {
	private BehaviorOps() {}

	/** A behavior setting read with getBehaviorParam as a number (0 when missing or not a number). */
	/** Behavior of an element (null when it has none). Static on purpose: scene code is compiled as Java 7, which does not see interface default methods. */
	public static com.star4droid.template.behaviors.Behavior of(Object item, String id) {
		if (!(item instanceof com.star4droid.template.Utils.PlayerItem)) return null;
		com.star4droid.template.Utils.ItemScript s = ((com.star4droid.template.Utils.PlayerItem) item).getScript();
		return s == null ? null : s.getBehavior(id);
	}

	private static com.star4droid.template.Utils.PlayerItem pi(Object item) {
		return item instanceof com.star4droid.template.Utils.PlayerItem ? (com.star4droid.template.Utils.PlayerItem) item : null;
	}

	public static com.star4droid.template.behaviors.Behavior add(Object item, String id) {
		com.star4droid.template.Utils.PlayerItem p = pi(item);
		return p == null ? null : p.addBehavior(id);
	}

	public static void remove(Object item, String id) {
		com.star4droid.template.Utils.PlayerItem p = pi(item);
		if (p != null) p.removeBehavior(id);
	}

	public static boolean has(Object item, String id) {
		return of(item, id) != null;
	}

	public static boolean enabled(Object item, String id) {
		com.star4droid.template.Utils.PlayerItem p = pi(item);
		return p != null && p.isBehaviorEnabled(id);
	}

	public static void setEnabled(Object item, String id, boolean on) {
		com.star4droid.template.Utils.PlayerItem p = pi(item);
		if (p != null) p.setBehaviorEnabled(id, on);
	}

	public static boolean setParam(Object item, String id, String field, Object value) {
		com.star4droid.template.Utils.PlayerItem p = pi(item);
		return p != null && p.setBehaviorParam(id, field, value);
	}

	public static Object param(Object item, String id, String field) {
		com.star4droid.template.Utils.PlayerItem p = pi(item);
		return p == null ? null : p.getBehaviorParam(id, field);
	}

	public static float num(Object v) {
		if (v instanceof Number) return ((Number) v).floatValue();
		if (v instanceof Boolean) return ((Boolean) v).booleanValue() ? 1f : 0f;
		if (v instanceof CharSequence) {
			try {
				return Float.parseFloat(v.toString().trim());
			} catch (NumberFormatException ignored) {
			}
		}
		return 0f;
	}

	/** A behavior setting as yes/no (false when missing). */
	public static boolean bool(Object v) {
		if (v instanceof Boolean) return ((Boolean) v).booleanValue();
		if (v instanceof Number) return ((Number) v).floatValue() != 0f;
		if (v instanceof CharSequence) return v.toString().trim().equalsIgnoreCase("true");
		return false;
	}

	/** A behavior setting as text ("" when missing). */
	public static String text(Object v) {
		return v == null ? "" : String.valueOf(v);
	}
}
