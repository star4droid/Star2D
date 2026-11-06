package com.star4droid.template.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Object variables (docs/SCRIPTVARS.md "Object variables"): the variables listed in the Variables section of an element's
 * properties are fields of the element's generated event class, so every event of the element uses them by name. This class
 * reads and writes them from anywhere else (other elements' events, Java scripts, blocks "variable ___ of ___"):
 * {@code ObjectVars.set(enemy, "health", 50)}, {@code ObjectVars.getFloat(enemy, "health")}.
 * A name the element does not declare is kept in a side map, so it can still be written and read back. Errors are logged,
 * never thrown.
 */
public final class ObjectVars {
	private ObjectVars() {}

	private static final Map<PlayerItem, Map<String, Object>> EXTRA = new WeakHashMap<>();

	private static Field field(PlayerItem item, String name) {
		if (item == null || name == null) return null;
		Object ev;
		try {
			ev = item.getElementEvents();
		} catch (Throwable t) {
			return null;
		}
		if (ev == null) return null;
		for (Class<?> c = ev.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) return null;
				f.setAccessible(true);
				return f;
			} catch (NoSuchFieldException ignored) {
			} catch (Throwable t) {
				return null;
			}
		}
		return null;
	}

	/** True when the element declares the variable (or one was set on it from outside). */
	public static boolean has(PlayerItem item, String name) {
		if (field(item, name) != null) return true;
		synchronized (EXTRA) {
			Map<String, Object> m = EXTRA.get(item);
			return m != null && m.containsKey(name);
		}
	}

	/** The value of a variable (null when it does not exist). */
	public static Object get(PlayerItem item, String name) {
		Field f = field(item, name);
		if (f != null) {
			try {
				return f.get(item.getElementEvents());
			} catch (Throwable t) {
				log(name, t);
				return null;
			}
		}
		synchronized (EXTRA) {
			Map<String, Object> m = EXTRA.get(item);
			return m == null ? null : m.get(name);
		}
	}

	/** Sets a variable. Numbers are converted to the variable's type, a text is parsed for number / yes-no variables, an element name finds the element. */
	public static void set(PlayerItem item, String name, Object value) {
		if (item == null || name == null) return;
		Field f = field(item, name);
		if (f != null) {
			try {
				f.set(item.getElementEvents(), convert(item, f.getType(), value));
			} catch (Throwable t) {
				log(name, t);
			}
			return;
		}
		synchronized (EXTRA) {
			Map<String, Object> m = EXTRA.get(item);
			if (m == null) EXTRA.put(item, m = new java.util.HashMap<>());
			m.put(name, value);
		}
	}

	/** Adds to a number variable (texts are appended). */
	public static void add(PlayerItem item, String name, Object by) {
		Object cur = get(item, name);
		if (cur instanceof String && !(by instanceof Number)) set(item, name, cur + String.valueOf(by));
		else set(item, name, toDouble(cur) + toDouble(by));
	}

	public static float getFloat(PlayerItem item, String name) {
		return (float) toDouble(get(item, name));
	}

	public static int getInt(PlayerItem item, String name) {
		return (int) toDouble(get(item, name));
	}

	public static double getDouble(PlayerItem item, String name) {
		return toDouble(get(item, name));
	}

	public static String getString(PlayerItem item, String name) {
		Object v = get(item, name);
		if (v instanceof PlayerItem) return ((PlayerItem) v).getName();
		return v == null ? "" : String.valueOf(v);
	}

	public static boolean getBool(PlayerItem item, String name) {
		Object v = get(item, name);
		if (v instanceof Boolean) return (Boolean) v;
		if (v instanceof Number) return ((Number) v).doubleValue() != 0;
		return v != null && "true".equalsIgnoreCase(String.valueOf(v).trim());
	}

	public static PlayerItem getItem(PlayerItem item, String name) {
		Object v = get(item, name);
		if (v instanceof PlayerItem) return (PlayerItem) v;
		if (v instanceof String && item != null) {
			try {
				return com.star4droid.template.Items.StageImp.of(item).findItem((String) v);
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static ArrayList<Object> getList(PlayerItem item, String name) {
		Object v = get(item, name);
		if (v instanceof ArrayList) return (ArrayList<Object>) v;
		ArrayList<Object> l = new ArrayList<>();
		if (v != null) {
			if (v instanceof java.util.Collection) l.addAll((java.util.Collection<Object>) v);
			else l.add(v);
		}
		set(item, name, l);
		return l;
	}

	/** Names of the variables the element declares. */
	public static String[] names(PlayerItem item) {
		ArrayList<String> out = new ArrayList<>();
		try {
			Object ev = item == null ? null : item.getElementEvents();
			if (ev != null)
				for (Field f : ev.getClass().getDeclaredFields())
					if (!f.isSynthetic() && !Modifier.isStatic(f.getModifiers()) && !f.getName().contains("$")) out.add(f.getName());
		} catch (Throwable ignored) {
		}
		synchronized (EXTRA) {
			Map<String, Object> m = item == null ? null : EXTRA.get(item);
			if (m != null) for (String k : m.keySet()) if (!out.contains(k)) out.add(k);
		}
		return out.toArray(new String[0]);
	}

	/** A list made of values (initial value of a list variable). */
	public static ArrayList<Object> list(Object... values) {
		ArrayList<Object> l = new ArrayList<>();
		if (values != null) for (Object v : values) l.add(v);
		return l;
	}

	static double toDouble(Object v) {
		if (v instanceof Number) return ((Number) v).doubleValue();
		if (v instanceof Boolean) return ((Boolean) v) ? 1 : 0;
		if (v == null) return 0;
		try {
			return Double.parseDouble(String.valueOf(v).trim());
		} catch (Exception e) {
			return 0;
		}
	}

	private static Object convert(PlayerItem owner, Class<?> t, Object v) {
		if (t == int.class || t == Integer.class) return (int) toDouble(v);
		if (t == long.class || t == Long.class) return (long) toDouble(v);
		if (t == float.class || t == Float.class) return (float) toDouble(v);
		if (t == double.class || t == Double.class) return toDouble(v);
		if (t == boolean.class || t == Boolean.class) {
			if (v instanceof Boolean) return v;
			if (v instanceof Number) return ((Number) v).doubleValue() != 0;
			return v != null && "true".equalsIgnoreCase(String.valueOf(v).trim());
		}
		if (t == String.class) return v instanceof PlayerItem ? ((PlayerItem) v).getName() : (v == null ? "" : String.valueOf(v));
		if (PlayerItem.class.isAssignableFrom(t) && v instanceof String) {
			try {
				return com.star4droid.template.Items.StageImp.of(owner).findItem((String) v);
			} catch (Throwable e) {
				return null;
			}
		}
		if (t == ArrayList.class && v != null && !(v instanceof ArrayList)) {
			ArrayList<Object> l = new ArrayList<>();
			if (v instanceof java.util.Collection) l.addAll((java.util.Collection<?>) v);
			else l.add(v);
			return l;
		}
		return v;
	}

	private static void log(String name, Throwable t) {
		try {
			Utils.Log("object variable " + name, Utils.getStackTraceString(t));
		} catch (Throwable ignored) {
		}
	}
}
