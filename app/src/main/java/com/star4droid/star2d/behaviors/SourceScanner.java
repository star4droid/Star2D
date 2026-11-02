package com.star4droid.star2d.behaviors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small, forgiving scanner for Java source: finds the public instance variables of the first class (these become
 * inspector fields) and the {@code @BehaviorInfo} header. It never throws: anything it does not understand is skipped.
 *
 * Works on text because the project's classes are not compiled yet while the user edits them.
 */
public final class SourceScanner {
	private SourceScanner() {}

	/** Result of one scan. */
	public static final class Result {
		public String pkg = "", className = "", superName = "";
		public Map<String, String> info = new HashMap<String, String>();
		public List<String> events = new ArrayList<String>();
		/** {@code @BehaviorInfo(types = {"OBJ3D", ...})}: editor TYPEs the behavior is made for. */
		public List<String> types = new ArrayList<String>();
		public String exclusive = "", reqType = "", reqNote = "";
		public boolean reqBody, reqUi;
		public List<String> sets = new ArrayList<String>();
		public List<VarSpec> vars = new ArrayList<VarSpec>();
		/** Scanned as a user script (Body / Scene script): more fields qualify, see {@link SourceScanner#scanScript}. */
		public boolean script;
		/** true when a class was found and its braces / parentheses are balanced (a half-typed file is not trustworthy: do not sync from it). */
		public boolean complete;

		public String fqn() {
			return pkg.length() > 0 ? pkg + "." + className : className;
		}
	}

	private static final char MASK = '';

	/** Comments -> spaces; every char inside string / char literals is shifted into a private range so it cannot be mistaken for code. */
	static String clean(String s) {
		StringBuilder o = new StringBuilder(s.length());
		int n = s.length();
		for (int i = 0; i < n; i++) {
			char c = s.charAt(i);
			if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
				while (i < n && s.charAt(i) != '\n') {
					o.append(' ');
					i++;
				}
				if (i < n) o.append('\n');
				continue;
			}
			if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
				i += 2;
				o.append("  ");
				while (i < n && !(s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/')) {
					o.append(s.charAt(i) == '\n' ? '\n' : ' ');
					i++;
				}
				o.append("  ");
				i++;
				continue;
			}
			if (c == '"' || c == '\'') {
				char q = c;
				o.append(q);
				i++;
				while (i < n && s.charAt(i) != q && s.charAt(i) != '\n') {
					char d = s.charAt(i);
					if (d == '\\' && i + 1 < n) {
						o.append((char) (MASK + '\\')).append((char) (MASK + (s.charAt(i + 1) & 0x7F)));
						i += 2;
						continue;
					}
					o.append(d < 128 ? (char) (MASK + d) : d);
					i++;
				}
				if (i < n && s.charAt(i) == q) o.append(q);
				continue;
			}
			o.append(c);
		}
		return o.toString();
	}

	static String unmask(String s) {
		StringBuilder o = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			o.append(c >= MASK && c < MASK + 128 ? (char) (c - MASK) : c);
		}
		return o.toString();
	}

	/** Java string literal text (with the quotes, masked) -> its value. */
	static String literal(String masked) {
		masked = masked.trim();
		if (masked.length() < 2 || masked.charAt(0) != '"') return null;
		String body = unmask(masked.substring(1, masked.lastIndexOf('"') > 0 ? masked.lastIndexOf('"') : masked.length()));
		StringBuilder o = new StringBuilder();
		for (int i = 0; i < body.length(); i++) {
			char c = body.charAt(i);
			if (c == '\\' && i + 1 < body.length()) {
				char d = body.charAt(++i);
				switch (d) {
					case 'n': o.append('\n'); break;
					case 't': o.append('\t'); break;
					case 'r': o.append('\r'); break;
					case 'u':
						if (i + 4 < body.length()) {
							try {
								o.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16));
								i += 4;
							} catch (NumberFormatException ex) {
								o.append('u');
							}
						}
						break;
					default: o.append(d);
				}
			} else o.append(c);
		}
		return o.toString();
	}

	/**
	 * Fields of a behavior: public instance fields only (see the class comment).
	 */
	public static Result scan(String source) {
		return scan(source, false);
	}

	/**
	 * Fields of a user script (Body Script / Scene Script). Exposed: public, package-private (no modifier) and static fields
	 * (not final, not transient, not {@code @Hidden}); {@code private} / {@code protected} ones only with {@code @Expose}.
	 * Every returned {@link VarSpec} has {@code auto = true}.
	 */
	public static Result scanScript(String source) {
		return scan(source, true);
	}

	private static Result scan(String source, boolean script) {
		Result r = new Result();
		r.script = script;
		if (source == null) return r;
		try {
			String s = clean(source);
			r.complete = balanced(s);
			// package
			java.util.regex.Matcher pm = java.util.regex.Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;").matcher(s);
			if (pm.find()) r.pkg = pm.group(1);
			// first top-level class
			int depth = 0;
			int classStart = -1;
			java.util.regex.Pattern cp = java.util.regex.Pattern.compile("\\bclass\\s+(\\w+)(?:\\s*<[^>{]*>)?(?:\\s+extends\\s+([\\w.]+))?");
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				if (c == '{') depth++;
				else if (c == '}') depth--;
				else if (depth == 0 && c == 'c' && s.startsWith("class", i) && (i == 0 || !Character.isJavaIdentifierPart(s.charAt(i - 1)))) {
					java.util.regex.Matcher m = cp.matcher(s);
					if (m.find(i) && m.start() == i) {
						r.className = m.group(1);
						r.superName = m.group(2) == null ? "" : m.group(2);
						classStart = s.indexOf('{', m.end());
						// annotations right before the class (and their modifiers)
						int from = Math.max(0, lastBoundary(s, i));
						parseClassInfo(s.substring(from, i), r);
						break;
					}
				}
			}
			if (classStart < 0) {
				r.complete = false;
				return r;
			}
			int classEnd = matching(s, classStart);
			String body = s.substring(classStart + 1, classEnd < 0 ? s.length() : classEnd);
			Map<String, List<String>> enums = findEnums(s, r);
			parseMembers(body, r, enums);
		} catch (RuntimeException ex) {
			// best effort
		}
		return r;
	}

	/** {} () [] are balanced (strings and comments are already masked). */
	private static boolean balanced(String s) {
		int b = 0, p = 0, k = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '{') b++;
			else if (c == '}') b--;
			else if (c == '(') p++;
			else if (c == ')') p--;
			else if (c == '[') k++;
			else if (c == ']') k--;
			if (b < 0 || p < 0 || k < 0) return false;
		}
		return b == 0 && p == 0 && k == 0;
	}

	/** Position just after the previous ';' '}' of a depth-0 declaration (start of the annotations of the class). */
	private static int lastBoundary(String s, int pos) {
		int paren = 0, last = 0;
		for (int i = 0; i < pos; i++) {
			char c = s.charAt(i);
			if (c == '(') paren++;
			else if (c == ')') paren--;
			else if (paren <= 0 && (c == ';' || c == '}')) last = i + 1;
		}
		return last;
	}

	private static void parseClassInfo(String head, Result r) {
		for (Annotation a : annotations(head)) {
			if (a.name.equals("Exclusive")) {
				String v = splitArgs(a.args).get("value");
				String lit = v == null ? null : literal(v);
				r.exclusive = lit == null ? "" : lit;
			} else if (a.name.equals("Requires")) {
				Map<String, String> kv = splitArgs(a.args);
				if (kv.containsKey("type")) r.reqType = nz(literal(kv.get("type")));
				if (kv.containsKey("note")) r.reqNote = nz(literal(kv.get("note")));
				r.reqBody = "true".equals(kv.get("body"));
				r.reqUi = "true".equals(kv.get("ui"));
			} else if (a.name.equals("Sets")) {
				String v = splitArgs(a.args).get("value");
				if (v != null)
					for (String x : arrayItems(v)) {
						String lit = literal(x);
						if (lit != null) r.sets.add(lit);
					}
			}
			if (a.name.equals("BehaviorInfo")) {
				Map<String, String> kv = splitArgs(a.args);
				for (Map.Entry<String, String> e : kv.entrySet()) {
					String k = e.getKey(), v = e.getValue();
					if (k.equals("events")) {
						for (String x : arrayItems(v)) {
							String lit = literal(x);
							if (lit != null) r.events.add(lit);
						}
					} else if (k.equals("types")) {
						for (String x : arrayItems(v)) {
							String lit = literal(x);
							if (lit != null && lit.trim().length() > 0) r.types.add(lit.trim().toUpperCase(Locale.ROOT));
						}
					} else if (k.equals("targets")) {
						int d = v.lastIndexOf('.');
						r.info.put(k, (d >= 0 ? v.substring(d + 1) : v).trim());
					} else {
						String lit = literal(v);
						r.info.put(k, lit != null ? lit : v.trim());
					}
				}
			}
		}
	}

	static int matching(String s, int open) {
		char o = s.charAt(open), c = o == '{' ? '}' : (o == '(' ? ')' : (o == '[' ? ']' : '>'));
		int d = 0;
		for (int i = open; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == o) d++;
			else if (ch == c && --d == 0) return i;
		}
		return -1;
	}

	/** enum Name { A, B(1), C; ... } anywhere in the file: simple name -> constants. Qualified type names are put in {@code qualified}. */
	private static Map<String, List<String>> findEnums(String s, Result r) {
		Map<String, List<String>> out = new HashMap<String, List<String>>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\benum\\s+(\\w+)\\s*(?:implements[^{]*)?\\{").matcher(s);
		while (m.find()) {
			// only public enums: the generated scene code lives in another package and must be able to name the constant
			java.util.regex.Matcher mods = java.util.regex.Pattern.compile("((?:\\b(?:public|private|protected|static|final|abstract)\\s+)*)$").matcher(s.substring(Math.max(0, m.start() - 60), m.start()));
			if (mods.find() && !mods.group(1).contains("public")) continue;
			int open = m.end() - 1;
			int close = matching(s, open);
			if (close < 0) continue;
			String inner = s.substring(open + 1, close);
			// constants end at the first ';' at depth 0 (or the end)
			int depth = 0, end = inner.length();
			for (int i = 0; i < inner.length(); i++) {
				char c = inner.charAt(i);
				if (c == '(' || c == '{') depth++;
				else if (c == ')' || c == '}') depth--;
				else if (c == ';' && depth == 0) {
					end = i;
					break;
				}
			}
			List<String> consts = new ArrayList<String>();
			for (String part : splitTop(inner.substring(0, end), ',')) {
				String t = part.trim();
				java.util.regex.Matcher cm = java.util.regex.Pattern.compile("^(?:@\\w+\\s*)*([A-Za-z_]\\w*)").matcher(t);
				if (cm.find()) consts.add(cm.group(1));
			}
			out.put(m.group(1), consts);
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------ members

	private static void parseMembers(String body, Result r, Map<String, List<String>> enums) {
		int depth = 0, parens = 0;
		StringBuilder decl = new StringBuilder();
		boolean inInit = false;
		for (int i = 0; i < body.length(); i++) {
			char c = body.charAt(i);
			if (c == '{') {
				String d = decl.toString();
				boolean initializer = d.indexOf('=') >= 0 && d.indexOf('(') < 0 || (d.indexOf('=') >= 0 && d.indexOf('=') < d.indexOf('(')) || d.trim().endsWith("]");
				int close = matching(body, i);
				if (close < 0) return;
				if (initializer) {
					decl.append(body, i, close + 1);
					i = close;
				} else {
					// method / constructor / initializer block / nested type: skip it entirely
					decl.setLength(0);
					i = close;
				}
				continue;
			}
			if (c == '(') {
				int close = matching(body, i);
				if (close < 0) return;
				decl.append(body, i, close + 1);
				i = close;
				continue;
			}
			if (c == ';') {
				parseField(decl.toString(), r, enums);
				decl.setLength(0);
				continue;
			}
			decl.append(c);
		}
	}

	private static final class Annotation {
		String name, args = "";
	}

	/** All annotations at the start of a declaration text (also removes nothing; use stripAnnotations). */
	private static List<Annotation> annotations(String text) {
		List<Annotation> out = new ArrayList<Annotation>();
		int i = 0, n = text.length();
		while (i < n) {
			int at = text.indexOf('@', i);
			if (at < 0) break;
			int j = at + 1;
			while (j < n && (Character.isJavaIdentifierPart(text.charAt(j)) || text.charAt(j) == '.')) j++;
			Annotation a = new Annotation();
			a.name = text.substring(at + 1, j);
			int dot = a.name.lastIndexOf('.');
			if (dot >= 0) a.name = a.name.substring(dot + 1);
			int k = j;
			while (k < n && Character.isWhitespace(text.charAt(k))) k++;
			if (k < n && text.charAt(k) == '(') {
				int close = matching(text, k);
				if (close > 0) {
					a.args = text.substring(k + 1, close);
					j = close + 1;
				}
			}
			out.add(a);
			i = j;
		}
		return out;
	}

	private static String stripAnnotations(String text) {
		StringBuilder o = new StringBuilder();
		int i = 0, n = text.length();
		while (i < n) {
			char c = text.charAt(i);
			if (c == '@') {
				int j = i + 1;
				while (j < n && (Character.isJavaIdentifierPart(text.charAt(j)) || text.charAt(j) == '.')) j++;
				int k = j;
				while (k < n && Character.isWhitespace(text.charAt(k))) k++;
				if (k < n && text.charAt(k) == '(') {
					int close = matching(text, k);
					if (close > 0) j = close + 1;
				}
				i = j;
				o.append(' ');
				continue;
			}
			o.append(c);
			i++;
		}
		return o.toString();
	}

	/** Top-level split on a separator (outside (), {}, &lt;&gt;, [] and strings). */
	static List<String> splitTop(String s, char sep) {
		List<String> out = new ArrayList<String>();
		int depth = 0, last = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '(' || c == '{' || c == '[' || c == '<') depth++;
			else if (c == ')' || c == '}' || c == ']' || c == '>') depth--;
			else if (c == sep && depth == 0) {
				out.add(s.substring(last, i));
				last = i + 1;
			}
		}
		out.add(s.substring(last));
		return out;
	}

	/** key = value, key = value  (value kept as masked source). A single unnamed value is stored under "value". */
	static Map<String, String> splitArgs(String args) {
		Map<String, String> m = new java.util.LinkedHashMap<String, String>();
		if (args == null || args.trim().length() == 0) return m;
		for (String part : splitTop(args, ',')) {
			int eq = indexOfAssign(part);
			if (eq < 0) m.put("value", part.trim());
			else m.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
		}
		return m;
	}

	private static int indexOfAssign(String s) {
		int depth = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '(' || c == '{' || c == '[') depth++;
			else if (c == ')' || c == '}' || c == ']') depth--;
			else if (c == '=' && depth == 0 && (i + 1 >= s.length() || s.charAt(i + 1) != '=')) return i;
		}
		return -1;
	}

	private static List<String> arrayItems(String v) {
		v = v.trim();
		if (v.startsWith("{") && v.endsWith("}")) v = v.substring(1, v.length() - 1);
		List<String> out = new ArrayList<String>();
		if (v.trim().length() == 0) return out;
		for (String p : splitTop(v, ',')) if (p.trim().length() > 0) out.add(p.trim());
		return out;
	}

	private static void parseField(String text, Result r, Map<String, List<String>> enums) {
		String t = text.trim();
		if (t.length() == 0) return;
		List<Annotation> anns = annotations(t);
		String bare = stripAnnotations(t).trim();
		// modifiers
		boolean isPublic = false, isStatic = false, isFinal = false, isTransient = false;
		String access = "package";
		while (true) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(public|private|protected|static|final|transient|volatile)\\b\\s*").matcher(bare);
			if (!m.find()) break;
			String mod = m.group(1);
			if (mod.equals("public")) {
				isPublic = true;
				access = "public";
			} else if (mod.equals("private") || mod.equals("protected")) access = mod;
			else if (mod.equals("static")) isStatic = true;
			else if (mod.equals("final")) isFinal = true;
			else if (mod.equals("transient")) isTransient = true;
			bare = bare.substring(m.end());
		}
		if (r.script) {
			boolean exposed = false;
			for (Annotation a : anns) if (a.name.equals("Expose")) exposed = true;
			if (isFinal || isTransient) return;
			if ((access.equals("private") || access.equals("protected")) && !exposed) return;
		} else if (!isPublic || isStatic || isFinal) return;
		// type
		int p = 0;
		int n = bare.length();
		while (p < n && (Character.isJavaIdentifierPart(bare.charAt(p)) || bare.charAt(p) == '.')) p++;
		if (p == 0) return;
		String typeName = bare.substring(0, p);
		String generic = "";
		int q = p;
		while (q < n && Character.isWhitespace(bare.charAt(q))) q++;
		if (q < n && bare.charAt(q) == '<') {
			int close = matching(bare, q);
			if (close < 0) return;
			generic = bare.substring(q + 1, close).trim();
			p = close + 1;
		}
		boolean arr = false;
		q = p;
		while (q < n && Character.isWhitespace(bare.charAt(q))) q++;
		while (q + 1 < n && bare.charAt(q) == '[' && bare.charAt(q + 1) == ']') {
			arr = true;
			q += 2;
			p = q;
			while (q < n && Character.isWhitespace(bare.charAt(q))) q++;
		}
		String rest = bare.substring(p).trim();
		if (rest.length() == 0) return;
		if (rest.indexOf('(') >= 0 && (rest.indexOf('=') < 0 || rest.indexOf('(') < rest.indexOf('='))) return; // a method
		// annotations
		String label = "", desc = "", unit = "", header = "";
		double min = -1e9, max = 1e9, step = 0;
		boolean hidden = false, image = false, sound = false;
		for (Annotation a : anns) {
			if (a.name.equals("Hidden")) hidden = true;
			else if (a.name.equals("Image")) image = true;
			else if (a.name.equals("Sound")) sound = true;
			else if (a.name.equals("Header")) {
				Map<String, String> kv = splitArgs(a.args);
				String v = kv.get("value");
				String lit = v == null ? null : literal(v);
				header = lit == null ? "" : lit;
			} else if (a.name.equals("Param")) {
				Map<String, String> kv = splitArgs(a.args);
				for (Map.Entry<String, String> e : kv.entrySet()) {
					String k = e.getKey();
					if (k.equals("desc")) desc = nz(literal(e.getValue()));
					else if (k.equals("label")) label = nz(literal(e.getValue()));
					else if (k.equals("unit")) unit = nz(literal(e.getValue()));
					else if (k.equals("min") || k.equals("max") || k.equals("step")) {
						Double d = Expr.eval(e.getValue());
						if (d != null) {
							if (k.equals("min")) min = d; else if (k.equals("max")) max = d; else step = d;
						}
					}
				}
			}
		}
		if (hidden) return;
		// declarators
		for (String decl : splitTop(rest, ',')) {
			String d = decl.trim();
			if (d.length() == 0) continue;
			int eq = indexOfAssign(d);
			String vname = (eq < 0 ? d : d.substring(0, eq)).trim();
			boolean arr2 = arr;
			while (vname.endsWith("[]")) {
				arr2 = true;
				vname = vname.substring(0, vname.length() - 2).trim();
			}
			if (!isIdent(vname)) continue;
			String init = eq < 0 ? null : d.substring(eq + 1).trim();
			VarSpec v = makeSpec(vname, typeName, generic, arr2, image, sound, enums, r);
			if (v == null) continue;
			v.label = label;
			v.desc = desc;
			v.unit = unit;
			v.header = header;
			v.min = min;
			v.max = max;
			v.step = step;
			v.isStatic = isStatic;
			v.access = access;
			v.auto = r.script;
			if (v.list && !v.array) v.container = containerOf(typeName);
			if (init != null) v.def = parseDefault(v, init);
			r.vars.add(v);
			header = ""; // a header only opens a group at its first field
		}
	}

	private static String containerOf(String typeName) {
		return typeName.substring(typeName.lastIndexOf('.') + 1);
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	private static boolean isIdent(String s) {
		if (s.length() == 0 || !Character.isJavaIdentifierStart(s.charAt(0))) return false;
		for (int i = 1; i < s.length(); i++) if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
		return true;
	}

	private static VarSpec makeSpec(String name, String typeName, String generic, boolean arr, boolean image, boolean sound, Map<String, List<String>> enums, Result r) {
		String simple = typeName.substring(typeName.lastIndexOf('.') + 1);
		boolean list = false;
		String elem = simple;
		if (arr) {
			list = true;
		} else if (simple.equals("List") || simple.equals("ArrayList") || simple.equals("LinkedList") || simple.equals("Collection")) {
			if (generic.length() == 0) return null;
			list = true;
			elem = generic.substring(generic.lastIndexOf('.') + 1).trim();
			if (elem.indexOf('<') >= 0 || elem.indexOf('?') >= 0) return null;
		}
		VarSpec v = classify(name, elem, image, sound, enums, r);
		if (v == null) return null;
		v.list = list;
		v.array = arr;
		return v;
	}

	private static VarSpec classify(String name, String simple, boolean image, boolean sound, Map<String, List<String>> enums, Result r) {
		VarSpec v;
		if (simple.equals("int") || simple.equals("Integer")) v = new VarSpec(name, VarSpec.Kind.INT);
		else if (simple.equals("long") || simple.equals("Long")) v = new VarSpec(name, VarSpec.Kind.LONG);
		else if (simple.equals("short") || simple.equals("Short")) v = new VarSpec(name, VarSpec.Kind.SHORT);
		else if (simple.equals("byte") || simple.equals("Byte")) v = new VarSpec(name, VarSpec.Kind.BYTE);
		else if (simple.equals("float") || simple.equals("Float")) v = new VarSpec(name, VarSpec.Kind.FLOAT);
		else if (simple.equals("double") || simple.equals("Double")) v = new VarSpec(name, VarSpec.Kind.DOUBLE);
		else if (simple.equals("boolean") || simple.equals("Boolean")) v = new VarSpec(name, VarSpec.Kind.BOOL);
		else if (simple.equals("String")) {
			boolean img = image || endsWithWord(name, "Image") || endsWithWord(name, "Sprite") || endsWithWord(name, "Icon");
			v = new VarSpec(name, sound ? VarSpec.Kind.SOUND : (img ? VarSpec.Kind.IMAGE : VarSpec.Kind.STRING));
		} else if (simple.equals("Color")) v = new VarSpec(name, VarSpec.Kind.COLOR);
		else if (enums.containsKey(simple)) {
			v = new VarSpec(name, VarSpec.Kind.ENUM);
			v.options.addAll(enums.get(simple));
			v.elemType = (r.pkg.length() > 0 ? r.pkg + "." : "") + (r.className.length() > 0 ? r.className + "." : "") + simple;
		} else if (ElementTypes.isElementClass(simple)) {
			v = new VarSpec(name, VarSpec.Kind.REF);
			v.elemType = simple;
		} else return null;
		return v;
	}

	private static boolean endsWithWord(String name, String w) {
		return name.equals(w.toLowerCase(Locale.ROOT)) || name.endsWith(w);
	}

	// ------------------------------------------------------------------------------------------------ defaults

	/** Initializer source (masked) -> default value in the VarSpec value model, or null when not understood. */
	static Object parseDefault(VarSpec v, String init) {
		try {
			String s = init.trim();
			if (v.list) {
				List<String> items = listItems(s);
				if (items == null) return null;
				List<Object> out = new ArrayList<Object>();
				if (v.kind == VarSpec.Kind.REF) return out;
				for (String it : items) {
					Object o = scalar(v, it);
					if (o == null) return null;
					out.add(o);
				}
				return out;
			}
			if (v.kind == VarSpec.Kind.REF) return null;
			return scalar(v, s);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private static List<String> listItems(String s) {
		s = s.trim();
		// new T[]{...}, {...}, Arrays.asList(...), new ArrayList<T>(Arrays.asList(...)), new ArrayList<T>(), new T[n]
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^new\\s+[\\w.]+\\s*\\[\\s*\\]\\s*(\\{.*\\})$", java.util.regex.Pattern.DOTALL).matcher(s);
		if (m.find()) return arrayItems(m.group(1));
		if (s.startsWith("{") && s.endsWith("}")) return arrayItems(s);
		m = java.util.regex.Pattern.compile("^new\\s+[\\w.]+\\s*\\[[^\\]]+\\]$").matcher(s);
		if (m.find()) return new ArrayList<String>();
		m = java.util.regex.Pattern.compile("^(?:java\\.util\\.)?Arrays\\.asList\\s*\\((.*)\\)$", java.util.regex.Pattern.DOTALL).matcher(s);
		if (m.find()) return arrayItems(m.group(1));
		m = java.util.regex.Pattern.compile("^new\\s+(?:java\\.util\\.)?\\w+\\s*(?:<[^()]*>)?\\s*\\((.*)\\)$", java.util.regex.Pattern.DOTALL).matcher(s);
		if (m.find()) {
			String inner = m.group(1).trim();
			if (inner.length() == 0) return new ArrayList<String>();
			return listItems(inner);
		}
		m = java.util.regex.Pattern.compile("^(?:java\\.util\\.)?Collections\\.emptyList\\s*\\(\\s*\\)$").matcher(s);
		if (m.find()) return new ArrayList<String>();
		return null;
	}

	private static Object scalar(VarSpec v, String s) {
		s = s.trim();
		switch (v.kind) {
			case BOOL:
				if (s.equals("true")) return Boolean.TRUE;
				if (s.equals("false")) return Boolean.FALSE;
				return null;
			case STRING:
			case IMAGE:
			case SOUND:
				if (s.equals("null")) return "";
				return literal(s);
			case ENUM: {
				String c = s.substring(s.lastIndexOf('.') + 1).trim();
				return v.options.contains(c) ? c : null;
			}
			case COLOR:
				return Colors.parseJava(unmask(s));
			case REF:
				return null;
			default:
				return Expr.eval(s);
		}
	}

	// ------------------------------------------------------------------------------------------------ BehaviorMeta

	/** Source of a project behavior -> metadata (null if the file has no class). */
	public static BehaviorMeta behavior(String source, String file) {
		Result r = scan(source);
		if (r.className.length() == 0) return null;
		BehaviorMeta m = new BehaviorMeta();
		m.id = r.className;
		m.fqn = r.fqn();
		m.file = file == null ? "" : file;
		m.builtin = false;
		m.name = r.info.containsKey("name") ? r.info.get("name") : "";
		if (r.info.containsKey("icon")) m.icon = r.info.get("icon");
		if (r.info.containsKey("desc")) m.desc = r.info.get("desc");
		if (r.info.containsKey("category")) m.category = r.info.get("category");
		if (r.info.containsKey("version")) m.version = r.info.get("version");
		if (r.info.containsKey("author")) m.author = r.info.get("author");
		if (r.info.containsKey("targets")) m.targets = r.info.get("targets").toUpperCase(Locale.ROOT);
		if (m.targets.equals("3D") || m.targets.equals("THREED")) m.targets = BehaviorMeta.THREE_D;
		if (!m.targets.equals(BehaviorMeta.UI) && !m.targets.equals(BehaviorMeta.PHYSICS) && !m.targets.equals(BehaviorMeta.THREE_D)) m.targets = BehaviorMeta.BOTH;
		m.types.addAll(r.types);
		for (String e : r.events) m.events.add(BehaviorMeta.EventSpec.parse(e));
		m.exclusive = r.exclusive;
		m.reqBody = r.reqBody;
		m.reqUi = r.reqUi;
		m.reqType = r.reqType.toUpperCase(Locale.ROOT);
		m.reqNote = r.reqNote;
		for (String kv : r.sets) {
			int eq = kv.indexOf('=');
			if (eq > 0) m.sets.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
		}
		m.vars.addAll(r.vars);
		return m;
	}
}
