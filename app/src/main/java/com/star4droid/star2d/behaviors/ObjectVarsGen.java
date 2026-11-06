package com.star4droid.star2d.behaviors;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Object variables of an element (docs/SCRIPTVARS.md "Object variables"). Pure Java (editor + tests).
 *
 * <p>Stored in the element property {@code objectVars} as a JSON array {@code [{"name":"health","type":"int","value":"3"}]};
 * an "object variable" block in any event of the element adds one too (marker comment {@code /*@objvar type|name|value*}{@code /}).
 * The code generator turns them into fields of the element's generated event class, so every event uses them by name.
 */
public final class ObjectVarsGen {
	private ObjectVarsGen() {}

	public static final String PROPERTY = "objectVars";
	/** Types offered by the editor (same names as the Variables dialog + list / element). */
	public static final String[] TYPES = {"int", "float", "double", "String", "boolean", "List", "PlayerItem"};
	private static final String OV = "com.star4droid.template.Utils.ObjectVars";
	private static final Pattern MARKER = Pattern.compile("/\\*@objvar ([^|*]*)\\|([^|*]*)\\|(.*?)\\*/");
	private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	private static final java.util.Set<String> RESERVED = new java.util.HashSet<>(java.util.Arrays.asList(
			"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do",
			"double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
			"int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
			"strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
			"true", "false", "null", "var", "current", "body2", "event"));

	public static final class Var {
		public String name = "", type = "int", value = "";
		/** True when it comes from an "object variable" block (not stored in the properties). */
		public transient boolean fromBlocks;

		public Var() {}

		public Var(String name, String type, String value) {
			this.name = name == null ? "" : name.trim();
			this.type = normType(type);
			this.value = value == null ? "" : value;
		}
	}

	/** A name usable as a Java field. */
	public static boolean validName(String n) {
		return n != null && NAME.matcher(n).matches() && !RESERVED.contains(n);
	}

	public static String normType(String t) {
		if (t == null) return "int";
		String s = t.trim();
		for (String k : TYPES) if (k.equalsIgnoreCase(s)) return k;
		switch (s.toLowerCase(java.util.Locale.ROOT)) {
			case "number": return "float";
			case "text": case "string": return "String";
			case "bool": case "yes/no": return "boolean";
			case "list": case "arraylist": return "List";
			case "element": case "body": return "PlayerItem";
		}
		return "int";
	}

	// ------------------------------------------------------------------ storage

	/** Parses the property (null / empty / broken = no variables). Invalid names are dropped, duplicates keep the first. */
	public static List<Var> parse(String json) {
		List<Var> out = new ArrayList<>();
		if (json == null || json.trim().isEmpty()) return out;
		try {
			com.google.gson.JsonArray a = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
			for (com.google.gson.JsonElement e : a) {
				if (!e.isJsonObject()) continue;
				com.google.gson.JsonObject o = e.getAsJsonObject();
				Var v = new Var(str(o, "name"), str(o, "type"), str(o, "value"));
				if (validName(v.name) && find(out, v.name) == null) out.add(v);
			}
		} catch (Exception ignored) {
		}
		return out;
	}

	private static String str(com.google.gson.JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	/** Property text of a list ("" when empty, so an element without variables stores nothing new). */
	public static String toJson(List<Var> vars) {
		com.google.gson.JsonArray a = new com.google.gson.JsonArray();
		for (Var v : vars) {
			if (v.fromBlocks) continue;
			com.google.gson.JsonObject o = new com.google.gson.JsonObject();
			o.addProperty("name", v.name);
			o.addProperty("type", v.type);
			o.addProperty("value", v.value);
			a.add(o);
		}
		return a.size() == 0 ? "" : a.toString();
	}

	public static Var find(List<Var> vars, String name) {
		for (Var v : vars) if (v.name.equals(name)) return v;
		return null;
	}

	/** Variables declared by "object variable" blocks in event code. */
	public static List<Var> fromCode(String code) {
		List<Var> out = new ArrayList<>();
		if (code == null || code.indexOf("@objvar") < 0) return out;
		Matcher m = MARKER.matcher(code);
		while (m.find()) {
			Var v = new Var(m.group(2), m.group(1), m.group(3).trim());
			v.fromBlocks = true;
			if (validName(v.name) && find(out, v.name) == null) out.add(v);
		}
		return out;
	}

	/** Property variables first, then the block ones not already listed. */
	public static List<Var> merge(List<Var> props, List<Var> blocks) {
		List<Var> out = new ArrayList<>(props);
		for (Var v : blocks) if (find(out, v.name) == null) out.add(v);
		return out;
	}

	// ------------------------------------------------------------------ code

	public static String javaType(String type) {
		switch (normType(type)) {
			case "List": return "java.util.ArrayList<Object>";
			default: return normType(type);
		}
	}

	private static boolean isNumber(String s) {
		try {
			Double.parseDouble(s);
			return !s.endsWith("f") && !s.endsWith("d") && !s.endsWith("F") && !s.endsWith("D");
		} catch (Exception e) {
			return false;
		}
	}

	/** Java expression of the initial value. */
	public static String literal(Var v) {
		String s = v.value == null ? "" : v.value.trim();
		switch (normType(v.type)) {
			case "int":
				if (s.isEmpty()) return "0";
				if (isNumber(s)) return String.valueOf((int) Double.parseDouble(s));
				return "(int)(" + s + ")";
			case "float":
				if (s.isEmpty()) return "0f";
				if (isNumber(s)) return Float.parseFloat(s) + "f";
				return "(float)(" + s + ")";
			case "double":
				if (s.isEmpty()) return "0d";
				if (isNumber(s)) return Double.parseDouble(s) + "d";
				return "(double)(" + s + ")";
			case "boolean":
				return "true".equalsIgnoreCase(s) ? "true" : "false";
			case "String":
				String t = s;
				if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
				return ValueEmit.javaLiteral(t);
			case "List": {
				if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
				StringBuilder b = new StringBuilder(OV + ".list(");
				boolean first = true;
				if (!s.trim().isEmpty())
					for (String item : s.split(",")) {
						String it = item.trim();
						if (!first) b.append(", ");
						first = false;
						if (isNumber(it)) {
							double d = Double.parseDouble(it);
							b.append(d == Math.rint(d) && Math.abs(d) < 1e9 && !it.contains(".") ? String.valueOf((int) d) : d + "d");
						}
						else if (it.equals("true") || it.equals("false")) b.append(it);
						else {
							if (it.length() >= 2 && it.startsWith("\"") && it.endsWith("\"")) it = it.substring(1, it.length() - 1);
							b.append(ValueEmit.javaLiteral(it));
						}
					}
				return b.append(")").toString();
			}
			case "PlayerItem":
				return s.isEmpty() ? "null" : "findItem(" + ValueEmit.javaLiteral(s) + ")";
		}
		return "null";
	}

	/** Field declarations of the generated event class. */
	public static String fields(List<Var> vars) {
		StringBuilder b = new StringBuilder();
		for (Var v : vars)
			b.append("\t\t\tpublic ").append(javaType(v.type)).append(' ').append(v.name).append(" = ").append(literal(v)).append(";\n");
		return b.toString();
	}

	/**
	 * Statements run once every element exists: element references are found again (the field initializer runs while the
	 * scene is still building, so the element it names may not exist yet).
	 */
	public static String afterBuild(String element, List<Var> vars) {
		StringBuilder b = new StringBuilder();
		for (Var v : vars)
			if ("PlayerItem".equals(normType(v.type)) && !v.value.trim().isEmpty())
				b.append(OV).append(".set(").append(element).append(", \"").append(v.name).append("\", ").append(literal(v)).append(");\n");
		return b.toString();
	}

	/**
	 * Local class of an element with object variables: fields + events; {@code fresh()} gives each clone its own variables.
	 * Same placeholders as the other item templates (1 def type, 2 name, 3 property init, 4 events) plus the fields.
	 */
	public static String itemCode(String defType, String name, String initCode, String events, List<Var> vars) {
		String cls = name + "_Events";
		return "\n\t\t" + defType + " " + name + "_def = new " + defType + "();\n" + initCode + "\n"
				+ "\t\tclass " + cls + " extends ElementEventAdapter {\n"
				+ "\t\t\t" + cls + "(){ super(\"" + name + "\"); }\n"
				+ fields(vars)
				+ "\t\t\t@Override public ElementEventAdapter fresh(){ return new " + cls + "(); }\n"
				+ events
				+ "\t\t}\n"
				+ "\t\t" + name + "_def.elementEvents = new " + cls + "();\n"
				+ "\t\t" + name + " = (PlayerItem)(" + name + "_def.build(this));\n";
	}
}
