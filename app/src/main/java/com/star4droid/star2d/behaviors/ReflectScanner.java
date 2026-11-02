package com.star4droid.star2d.behaviors;

import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Hidden;
import com.star4droid.template.behaviors.Param;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Built-in behaviors: metadata from the compiled classes (annotations + a default instance for the default values). */
public final class ReflectScanner {
	private ReflectScanner() {}

	public static BehaviorMeta behavior(Class<?> c) {
		if (!Behavior.class.isAssignableFrom(c) || Modifier.isAbstract(c.getModifiers())) return null;
		BehaviorMeta m = new BehaviorMeta();
		m.id = c.getSimpleName();
		m.fqn = c.getName();
		m.builtin = true;
		BehaviorInfo info = c.getAnnotation(BehaviorInfo.class);
		if (info != null) {
			m.name = info.name();
			m.icon = info.icon();
			m.desc = info.desc();
			m.category = info.category();
			m.version = info.version();
			m.author = info.author();
			m.targets = info.targets().name();
			for (String t : info.types()) if (t != null && t.trim().length() > 0) m.types.add(t.trim().toUpperCase(java.util.Locale.ROOT));
			for (String e : info.events()) m.events.add(BehaviorMeta.EventSpec.parse(e));
		}
		com.star4droid.template.behaviors.Exclusive ex = c.getAnnotation(com.star4droid.template.behaviors.Exclusive.class);
		if (ex != null) m.exclusive = ex.value();
		com.star4droid.template.behaviors.Requires rq = c.getAnnotation(com.star4droid.template.behaviors.Requires.class);
		if (rq != null) {
			m.reqBody = rq.body();
			m.reqUi = rq.ui();
			m.reqType = rq.type().toUpperCase();
			m.reqNote = rq.note();
		}
		com.star4droid.template.behaviors.Sets st = c.getAnnotation(com.star4droid.template.behaviors.Sets.class);
		if (st != null)
			for (String kv : st.value()) {
				int eq = kv.indexOf('=');
				if (eq > 0) m.sets.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
			}
		m.vars.addAll(vars(c));
		return m;
	}

	/** Public instance fields of the class (and its superclasses below Behavior) in declaration order. */
	public static List<VarSpec> vars(Class<?> c) {
		List<VarSpec> out = new ArrayList<VarSpec>();
		Object inst = null;
		try {
			inst = c.newInstance();
		} catch (Throwable ignored) {
		}
		ArrayList<Class<?>> chain = new ArrayList<Class<?>>();
		for (Class<?> k = c; k != null && k != Behavior.class && k != Object.class; k = k.getSuperclass()) chain.add(0, k);
		for (Class<?> k : chain) {
			for (Field f : k.getDeclaredFields()) {
				int mod = f.getModifiers();
				if (!Modifier.isPublic(mod) || Modifier.isStatic(mod) || Modifier.isFinal(mod) || f.isSynthetic()) continue;
				if (f.isAnnotationPresent(Hidden.class)) continue;
				VarSpec v = spec(f, inst);
				if (v != null) out.add(v);
			}
		}
		return out;
	}

	private static VarSpec spec(Field f, Object inst) {
		Class<?> t = f.getType();
		boolean list = false, array = false;
		Class<?> elem = t;
		if (t.isArray()) {
			list = true;
			array = true;
			elem = t.getComponentType();
		} else if (java.util.List.class.isAssignableFrom(t) || java.util.Collection.class == t) {
			Type g = f.getGenericType();
			if (!(g instanceof ParameterizedType)) return null;
			Type a = ((ParameterizedType) g).getActualTypeArguments()[0];
			if (!(a instanceof Class)) return null;
			list = true;
			elem = (Class<?>) a;
		}
		VarSpec v = classify(f, elem);
		if (v == null) return null;
		v.list = list;
		v.array = array;
		Param p = f.getAnnotation(Param.class);
		if (p != null) {
			v.label = p.label();
			v.desc = p.desc();
			v.unit = p.unit();
			v.min = p.min();
			v.max = p.max();
			v.step = p.step();
		}
		Header h = f.getAnnotation(Header.class);
		if (h != null) v.header = h.value();
		if (inst != null) {
			try {
				v.def = toModel(v, f.get(inst));
			} catch (Throwable ignored) {
			}
		}
		return v;
	}

	private static VarSpec classify(Field f, Class<?> t) {
		String n = f.getName();
		VarSpec v;
		if (t == int.class || t == Integer.class) v = new VarSpec(n, VarSpec.Kind.INT);
		else if (t == long.class || t == Long.class) v = new VarSpec(n, VarSpec.Kind.LONG);
		else if (t == short.class || t == Short.class) v = new VarSpec(n, VarSpec.Kind.SHORT);
		else if (t == byte.class || t == Byte.class) v = new VarSpec(n, VarSpec.Kind.BYTE);
		else if (t == float.class || t == Float.class) v = new VarSpec(n, VarSpec.Kind.FLOAT);
		else if (t == double.class || t == Double.class) v = new VarSpec(n, VarSpec.Kind.DOUBLE);
		else if (t == boolean.class || t == Boolean.class) v = new VarSpec(n, VarSpec.Kind.BOOL);
		else if (t == String.class) {
			boolean sound = f.isAnnotationPresent(com.star4droid.template.behaviors.Sound.class);
			boolean img = f.isAnnotationPresent(com.star4droid.template.behaviors.Image.class) || n.endsWith("Image") || n.endsWith("Sprite") || n.endsWith("Icon") || n.equals("image") || n.equals("icon") || n.equals("sprite");
			v = new VarSpec(n, sound ? VarSpec.Kind.SOUND : (img ? VarSpec.Kind.IMAGE : VarSpec.Kind.STRING));
		} else if (t == com.badlogic.gdx.graphics.Color.class) v = new VarSpec(n, VarSpec.Kind.COLOR);
		else if (t.isEnum()) {
			v = new VarSpec(n, VarSpec.Kind.ENUM);
			for (Object o : t.getEnumConstants()) v.options.add(((Enum<?>) o).name());
			v.elemType = t.getCanonicalName();
		} else if (ElementTypes.isElementClass(t.getSimpleName())) {
			v = new VarSpec(n, VarSpec.Kind.REF);
			v.elemType = t.getSimpleName();
		} else return null;
		return v;
	}

	private static Object toModel(VarSpec v, Object value) {
		if (value == null) return v.list ? (v.kind == VarSpec.Kind.REF ? new ArrayList<Object>() : null) : null;
		if (v.list) {
			List<Object> out = new ArrayList<Object>();
			if (value.getClass().isArray()) {
				int n = java.lang.reflect.Array.getLength(value);
				for (int i = 0; i < n; i++) {
					Object o = scalar(v, java.lang.reflect.Array.get(value, i));
					if (o != null) out.add(o);
				}
			} else if (value instanceof java.util.Collection) {
				for (Object x : (java.util.Collection<?>) value) {
					Object o = scalar(v, x);
					if (o != null) out.add(o);
				}
			}
			return out;
		}
		return scalar(v, value);
	}

	private static Object scalar(VarSpec v, Object value) {
		if (value == null) return v.kind == VarSpec.Kind.REF ? null : (v.kind == VarSpec.Kind.STRING || v.isNumber() ? null : null);
		if (value instanceof Number) return ((Number) value).doubleValue();
		if (value instanceof Boolean) return value;
		if (value instanceof Enum) return ((Enum<?>) value).name();
		if (value instanceof com.badlogic.gdx.graphics.Color) return Colors.normalize(value.toString());
		if (v.kind == VarSpec.Kind.REF) return null;
		return String.valueOf(value);
	}

	/** Names of every built-in behavior class. */
	public static List<Class<?>> builtInClasses() {
		return Arrays.asList(com.star4droid.template.behaviors.BuiltIns.all());
	}
}
