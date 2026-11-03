package com.star4droid.star2d.blocks;

import com.star4droid.template.behaviors.Block;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the {@code @Block} methods of behaviors into block definitions (editor and AI share this; pure Java, no Android).
 *
 * Built-in behaviors are read by reflection, the project's own behaviors (java/com/star4droid/Game/Behaviors/*.java) by a
 * textual scanner ({@link BlockSourceScanner}) because they are not compiled while the user edits them. Both give the same
 * {@link Spec}, which knows its slots, its generated Java and its texts.
 */
public final class BehaviorBlocks {
	private BehaviorBlocks() {}

	public static final String PROJECT_DIR = "java/com/star4droid/Game/Behaviors";
	public static final String PROJECT_PACKAGE = "com.star4droid.Game.Behaviors";
	/** Palette category prefix of the generated blocks (they are shown only when the scene uses the behavior). */
	public static final String CATEGORY_PREFIX = "⚙ ";

	/** One parameter of a block method. kind: num | bool | text | item | color. */
	public static final class Param {
		public String name = "value", kind = "num", javaType = "float";
	}

	/** One block: an {@code @Block} method of a behavior. */
	public static final class Spec {
		public String behaviorId = "", behaviorTitle = "", fqn = "", method = "";
		public String label = "", labelAr = "", desc = "", descAr = "", category = "";
		/** void | bool | num | text | item. */
		public String ret = "void";
		/** Java return type as written (boolean, int, float, String, PlayerItem...). */
		public String retType = "void";
		public List<Param> params = new ArrayList<Param>();
		/** Unique id of the block (saved in the .visual file as its title). */
		public String title = "";

		public boolean isValue() {
			return !"void".equals(ret);
		}

		/** Palette category (with the gear prefix). */
		public String paletteCategory() {
			String c = category.length() > 0 ? category : behaviorTitle;
			return CATEGORY_PREFIX + c;
		}

		/** Field tokens as the block engine reads them: "body(Body)", "speed", "on(Boolean)"... (first = the element owning the behavior). */
		public String[] fields() {
			String[] f = new String[params.size() + 1];
			f[0] = "body(Body)";
			for (int i = 0; i < params.size(); i++) f[i + 1] = token(params.get(i));
			return f;
		}

		public String[] defaults() {
			String[] d = new String[params.size() + 1];
			d[0] = "current";
			for (int i = 0; i < params.size(); i++) d[i + 1] = defaultOf(params.get(i));
			return d;
		}

		private String cast() {
			return "(" + fqn + ")";
		}

		private String call() {
			StringBuilder sb = new StringBuilder(method).append('(');
			for (int i = 0; i < params.size(); i++) {
				if (i > 0) sb.append(", ");
				sb.append(argExpr(params.get(i), i + 2));
			}
			return sb.append(')').toString();
		}

		/** Template of an action block, ready for String.format (the last placeholder is "the blocks below"). */
		public String statementCode() {
			String b = "__bhv";
			return "{\n    " + fqn + " " + b + " = " + cast() + " com.star4droid.template.behaviors.BehaviorOps.of(%1$s, \"" + behaviorId + "\");\n    if (" + b + " != null) " + b + "." + call() + ";\n}\n%" + (params.size() + 2) + "$s";
		}

		/** Template of a value block. */
		public String reporterTemplate() {
			String get = "(" + cast() + " com.star4droid.template.behaviors.BehaviorOps.of(%1$s, \"" + behaviorId + "\"))";
			return "(" + get + " != null ? " + get + "." + call() + " : " + defaultReturn() + ")";
		}

		/** Text of a value block with its slots: "is on the ground ___" (first slot = the element). */
		public String reporterDisplay(boolean ar) {
			StringBuilder sb = new StringBuilder(text(ar));
			for (int i = 0; i <= params.size(); i++) sb.append(" ___");
			return sb.toString();
		}

		/** Shape of a value block: bool (hexagon) | text (rectangle) | num (pill, also elements). */
		public String shape() {
			return ret;
		}

		public String text(boolean ar) {
			return ar && labelAr.length() > 0 ? labelAr : label;
		}

		private String defaultReturn() {
			if ("bool".equals(ret)) return "false";
			if ("text".equals(ret)) return "\"\"";
			if ("item".equals(ret)) return "null";
			if (retType.equals("float")) return "0f";
			if (retType.equals("double")) return "0d";
			if (retType.equals("long")) return "0L";
			return "0";
		}

		/** {"en": desc, "ar": descAr} (only the languages that have text); empty when the block has no description. */
		public Map<String, String> descMap() {
			Map<String, String> m = new LinkedHashMap<String, String>();
			if (desc.length() > 0) m.put("en", desc);
			if (descAr.length() > 0) m.put("ar", descAr);
			return m;
		}

		/** Key under which the block's texts are registered: the title of an action block, the English display of a value block. */
		public String key() {
			return isValue() ? reporterDisplay(false) : title;
		}
	}

	// ------------------------------------------------------------------ type mapping

	/** Element classes of the engine (all are PlayerItem); a block slot with one of these types takes an element. */
	static final java.util.Set<String> ITEM_TYPES = new HashSet<String>(java.util.Arrays.asList("PlayerItem", "Joystick", "ProgressItem", "TextItem", "BoxBody",
			"CircleItem", "CustomBody", "GroupItem", "MapItem", "CameraItem", "ParticleItem", "UIHolder"));

	static String itemFqn(String javaType) {
		if (javaType.indexOf('.') >= 0) return javaType;
		return javaType.equals("PlayerItem") ? "com.star4droid.template.Utils.PlayerItem" : "com.star4droid.template.Items." + javaType;
	}

	/** num | bool | text | item | color for a parameter type, or null when the block engine cannot fill it. */
	static String paramKind(String type) {
		String t = type.trim();
		int dot = t.lastIndexOf('.');
		String simple = dot >= 0 ? t.substring(dot + 1) : t;
		if (simple.equals("int") || simple.equals("long") || simple.equals("short") || simple.equals("byte") || simple.equals("float") || simple.equals("double")) return "num";
		if (simple.equals("boolean")) return "bool";
		if (simple.equals("String")) return "text";
		if (ITEM_TYPES.contains(simple) && (dot < 0 || t.startsWith("com.star4droid.template."))) return "item";
		if (simple.equals("Color") && (dot < 0 || t.equals("com.badlogic.gdx.graphics.Color"))) return "color";
		return null;
	}

	/** Same, for a return type (void -> "void"); null = not usable as a block. */
	static String returnKind(String type) {
		String t = type.trim();
		if (t.equals("void")) return "void";
		String k = paramKind(t);
		return k == null || k.equals("color") ? null : k;
	}

	private static String simple(String type) {
		int dot = type.lastIndexOf('.');
		return dot >= 0 ? type.substring(dot + 1) : type;
	}

	static String token(Param p) {
		switch (p.kind) {
			case "bool": return p.name + "(Boolean)";
			case "text": return p.name + "(String)";
			case "item": return p.name + "(Body)";
			default: return p.name;
		}
	}

	static String defaultOf(Param p) {
		switch (p.kind) {
			case "bool": return "true";
			case "text": return "\"text\"";
			case "item": return "";
			case "color": return "FFFFFFFF";
			default: return "0";
		}
	}

	static String argExpr(Param p, int slot) {
		String s = "%" + slot + "$s";
		switch (p.kind) {
			case "bool": return "(" + s + ")";
			case "text": return "String.valueOf(" + s + ")";
			case "item": return simple(p.javaType).equals("PlayerItem") ? s : "((" + itemFqn(p.javaType) + ") " + s + ")";
			case "color": return "com.badlogic.gdx.graphics.Color.valueOf(\"" + s + "\")";
			default: return "(" + simple(p.javaType) + ")(" + s + ")";
		}
	}

	/** Default slot name for a parameter without a name, by kind. */
	private static String defaultName(String kind) {
		switch (kind) {
			case "bool": return "on";
			case "text": return "text";
			case "item": return "target";
			case "color": return "color";
			default: return "value";
		}
	}

	/** Makes the slot names valid, different from each other and from the reserved "body" (the element slot). */
	static void fixNames(List<Param> ps, String[] args) {
		HashSet<String> used = new HashSet<String>();
		used.add("body");
		used.add("inside");
		used.add("else");
		for (int i = 0; i < ps.size(); i++) {
			Param p = ps.get(i);
			String n = args != null && i < args.length && args[i].trim().length() > 0 ? args[i].trim() : p.name;
			if (n == null || !n.matches("[A-Za-z_][A-Za-z0-9_]*") || n.matches("arg[0-9]+")) n = defaultName(p.kind);
			String base = n;
			int k = 2;
			while (!used.add(n)) n = base + (k++);
			p.name = n;
		}
	}

	// ------------------------------------------------------------------ reflection (built-ins and compiled classes)

	public static List<Spec> fromClass(Class<?> c) {
		List<Spec> out = new ArrayList<Spec>();
		String id = c.getSimpleName();
		String fqn = c.getName().replace('$', '.');
		for (Method m : c.getMethods()) {
			Block b = m.getAnnotation(Block.class);
			if (b == null || Modifier.isStatic(m.getModifiers()) || m.getDeclaringClass() == Object.class) continue;
			String ret = returnKind(m.getReturnType().getName());
			if (ret == null) continue;
			List<Param> ps = new ArrayList<Param>();
			boolean ok = true;
			for (Class<?> pt : m.getParameterTypes()) {
				String k = paramKind(pt.getName());
				if (k == null) {
					ok = false;
					break;
				}
				Param p = new Param();
				p.kind = k;
				p.javaType = pt.getName();
				p.name = defaultName(k);
				ps.add(p);
			}
			if (!ok) continue;
			fixNames(ps, b.args());
			Spec s = new Spec();
			s.behaviorId = id;
			s.fqn = fqn;
			s.method = m.getName();
			s.label = b.label();
			s.labelAr = b.labelAr();
			s.desc = b.desc();
			s.descAr = b.descAr();
			s.category = b.category();
			s.ret = ret;
			s.retType = simple(m.getReturnType().getName());
			s.params = ps;
			out.add(s);
		}
		finish(out, id, c.getSimpleName());
		return out;
	}

	/** Titles, behavior titles and a stable order (by label). */
	static void finish(List<Spec> specs, String id, String behaviorTitle) {
		Collections.sort(specs, new Comparator<Spec>() {
			@Override
			public int compare(Spec a, Spec b) {
				int c = a.label.compareToIgnoreCase(b.label);
				return c != 0 ? c : a.method.compareTo(b.method);
			}
		});
		HashMap<String, Integer> count = new HashMap<String, Integer>();
		for (Spec s : specs) count.put(s.method, count.containsKey(s.method) ? count.get(s.method) + 1 : 1);
		String pretty = prettify(behaviorTitle);
		for (Spec s : specs) {
			s.behaviorTitle = pretty;
			String t = id + "." + s.method;
			if (count.get(s.method) > 1) {
				// overloads: the parameter types make the title stable (reflection order and label changes do not matter)
				StringBuilder sb = new StringBuilder(t).append('(');
				for (int i = 0; i < s.params.size(); i++) sb.append(i > 0 ? "," : "").append(simple(s.params.get(i).javaType));
				t = sb.append(')').toString();
			}
			s.title = t;
		}
	}

	/** "PlatformerPlayer" -> "Platformer Player". */
	static String prettify(String id) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < id.length(); i++) {
			char ch = id.charAt(i);
			if (i > 0 && Character.isUpperCase(ch) && !Character.isUpperCase(id.charAt(i - 1))) sb.append(' ');
			sb.append(ch);
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------ the project / the built-in library

	private static final Map<String, Object[]> sourceCache = new HashMap<String, Object[]>(); // path -> {stamp, List<Spec>}

	/** Blocks of the built-in library, by behavior id. */
	public static synchronized Map<String, List<Spec>> builtIns() {
		Map<String, List<Spec>> out = new LinkedHashMap<String, List<Spec>>();
		Class<?>[] all = com.star4droid.template.behaviors.BuiltIns.all();
		for (Class<?> c : all) {
			try {
				List<Spec> l = fromClass(c);
				if (!l.isEmpty()) out.put(c.getSimpleName(), l);
			} catch (Throwable ignored) {
			}
		}
		return out;
	}

	/** Blocks of the project's own behaviors, by behavior id ({@code projectPath}/java/com/star4droid/Game/Behaviors/*.java). */
	public static synchronized Map<String, List<Spec>> project(String projectPath) {
		Map<String, List<Spec>> out = new LinkedHashMap<String, List<Spec>>();
		if (projectPath == null) return out;
		File[] files = new File(projectPath, PROJECT_DIR).listFiles();
		if (files == null) return out;
		java.util.Arrays.sort(files);
		for (File f : files) {
			if (!f.isFile() || !f.getName().endsWith(".java")) continue;
			Object[] c = sourceCache.get(f.getPath());
			long stamp = f.lastModified() + f.length();
			List<Spec> l;
			if (c != null && ((Long) c[0]) == stamp) {
				@SuppressWarnings("unchecked")
				List<Spec> cached = (List<Spec>) c[1];
				l = cached;
			} else {
				l = BlockSourceScanner.scan(readFile(f), PROJECT_PACKAGE);
				sourceCache.put(f.getPath(), new Object[] {stamp, l});
			}
			if (!l.isEmpty()) out.put(l.get(0).behaviorId, l);
		}
		return out;
	}

	/** Every behavior's blocks: the project's own win over a built-in with the same id (a copy the user edited). */
	public static Map<String, List<Spec>> all(String projectPath) {
		Map<String, List<Spec>> out = new LinkedHashMap<String, List<Spec>>(builtIns());
		out.putAll(project(projectPath));
		return out;
	}

	public static List<Spec> flat(String projectPath) {
		List<Spec> out = new ArrayList<Spec>();
		for (List<Spec> l : all(projectPath).values()) out.addAll(l);
		return out;
	}

	static String readFile(File f) {
		try {
			java.io.FileInputStream in = new java.io.FileInputStream(f);
			try {
				java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
				byte[] b = new byte[8192];
				int r;
				while ((r = in.read(b)) != -1) bo.write(b, 0, r);
				return new String(bo.toByteArray(), "UTF-8");
			} finally {
				in.close();
			}
		} catch (java.io.IOException ex) {
			return "";
		}
	}
}
