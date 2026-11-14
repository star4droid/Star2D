package com.star4droid.star2d.CodeEditor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.parser.Scanner;
import org.eclipse.jdt.internal.compiler.parser.TerminalTokens;

/**
 * A small Java formatter built on the scanner of ECJ (already in the app, so it costs no APK size).
 * It re-indents lines (4 spaces per level, switch cases, continuation lines), normalises the spaces between tokens
 * (operators, commas, keywords, braces) and keeps at most one blank line. Line breaks the user chose are kept, and
 * strings, text blocks and comments are never changed.
 * Safety: the result is scanned again and must have exactly the same tokens as the input, otherwise the input is
 * returned unchanged, so formatting can never change what the code means.
 */
public final class JavaFormatter {
	private static final String INDENT = "    ";
	private static final int WS = TerminalTokens.TokenNameWHITESPACE, LINE_C = TerminalTokens.TokenNameCOMMENT_LINE,
			BLOCK_C = TerminalTokens.TokenNameCOMMENT_BLOCK, DOC_C = TerminalTokens.TokenNameCOMMENT_JAVADOC, EOF = TerminalTokens.TokenNameEOF;

	private static final Set<String> SPACE_AFTER_KW = new HashSet<>(Arrays.asList(
			"if", "for", "while", "switch", "catch", "synchronized", "try", "return", "throw", "new", "else", "do",
			"case", "assert", "throws", "extends", "implements", "instanceof", "final", "static", "public", "private",
			"protected", "abstract", "native", "transient", "volatile", "strictfp", "class", "interface", "enum",
			"package", "import", "yield", "record", "permits", "sealed", "default", "finally", "var"));
	private static final Set<String> BINARY = new HashSet<>(Arrays.asList(
			"=", "==", "!=", "<=", ">=", "&&", "||", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=",
			"*", "/", "%", "&", "|", "^", "<<", "?", "->", "instanceof"));
	private static final Set<String> MAYBE_UNARY = new HashSet<>(Arrays.asList("+", "-"));
	private static final Set<String> UNARY_ONLY = new HashSet<>(Arrays.asList("!", "~", "++", "--"));

	private JavaFormatter() {}

	private static final class Tok {
		final int kind;
		final String text;
		Tok(int kind, String text) { this.kind = kind; this.text = text; }
		boolean is(String s) { return text.equals(s); }
		boolean comment() { return kind == LINE_C || kind == BLOCK_C || kind == DOC_C; }
		boolean word() { char c = text.isEmpty() ? ' ' : text.charAt(0); return Character.isJavaIdentifierPart(c) || c == '"' || c == '\''; }
	}

	/** Formats Java source. Returns the input unchanged when it cannot be read (unfinished string...) or on any doubt. */
	public static String format(String source) {
		if (source == null || source.isEmpty()) return source;
		try {
			List<Tok> toks = scan(source, true);
			if (toks == null) return source;
			String out = build(toks);
			// never change the meaning: same tokens before and after
			List<Tok> a = scan(source, false), b = scan(out, false);
			if (a == null || b == null || a.size() != b.size()) { if (DEBUG) System.err.println("size " + (a == null ? -1 : a.size()) + " vs " + (b == null ? -1 : b.size()) + "\n" + out); return source; }
			for (int i = 0; i < a.size(); i++) if (!a.get(i).text.equals(b.get(i).text)) { if (DEBUG) System.err.println("token " + i + ": '" + a.get(i).text + "' vs '" + b.get(i).text + "'"); return source; }
			return out;
		} catch (Throwable t) {
			if (DEBUG) t.printStackTrace();
			return source;
		}
	}
	static boolean DEBUG = false; // prints why a format was refused (tests only)

	private static List<Tok> scan(String src, boolean withSpace) {
		Scanner s = new Scanner(true, true, false, ClassFileConstants.JDK16, null, null, true);
		s.setSource(src.toCharArray());
		ArrayList<Tok> out = new ArrayList<>();
		try {
			while (true) {
				int k = s.getNextToken();
				if (k == EOF) break;
				// the exact source text (getCurrentTokenSource() returns string literals with their escapes already resolved)
				String text = src.substring(s.getCurrentTokenStartPosition(), s.getCurrentTokenEndPosition() + 1);
				if (k == WS) { if (withSpace) out.add(new Tok(k, text)); continue; }
				// comments are compared without their spaces (their inner indentation is re-aligned)
				if (!withSpace && (k == LINE_C || k == BLOCK_C || k == DOC_C)) { out.add(new Tok(k, text.replaceAll("\\s+", ""))); continue; }
				out.add(new Tok(k, text));
			}
		} catch (Throwable invalid) {
			if (DEBUG) System.err.println("scan failed at " + s.getCurrentTokenStartPosition() + ": " + invalid + " near: " + src.substring(Math.max(0, s.getCurrentTokenStartPosition() - 80), Math.min(src.length(), s.getCurrentTokenStartPosition() + 40)));
			return null;
		}
		return out;
	}

	/** Lines of tokens; blank lines kept (at most one in a row). */
	private static String build(List<Tok> all) {
		ArrayList<ArrayList<Tok>> lines = new ArrayList<>();
		ArrayList<Tok> cur = new ArrayList<>();
		int blank = 0;
		for (Tok t : all) {
			if (t.kind == WS) {
				int nl = count(t.text, '\n');
				for (int i = 0; i < nl; i++) {
					if (!cur.isEmpty()) { lines.add(cur); cur = new ArrayList<>(); blank = 0; }
					else if (i > 0 || lines.isEmpty() || !lines.get(lines.size() - 1).isEmpty()) {
						if (blank == 0 && !lines.isEmpty()) lines.add(new ArrayList<>());
						blank++;
					}
				}
				continue;
			}
			cur.add(t);
			if (t.kind == LINE_C) { lines.add(cur); cur = new ArrayList<>(); blank = 0; }
		}
		if (!cur.isEmpty()) lines.add(cur);
		// drop blank lines at the start and the end, and right after "{" or before "}"
		while (!lines.isEmpty() && lines.get(0).isEmpty()) lines.remove(0);
		while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);

		StringBuilder sb = new StringBuilder();
		int depth = 0, paren = 0;
		// switch handling: for each open brace, is it a switch body, and is a "case" open inside it
		ArrayList<Boolean> isSwitch = new ArrayList<>();
		ArrayList<Integer> parenStack = new ArrayList<>();
		ArrayList<Boolean> inCase = new ArrayList<>();
		boolean nextBraceIsSwitch = false, continuation = false, bracelessBody = false;
		Tok prevLineLast = null;
		for (int li = 0; li < lines.size(); li++) {
			ArrayList<Tok> line = lines.get(li);
			if (line.isEmpty()) {
				Tok next = li + 1 < lines.size() && !lines.get(li + 1).isEmpty() ? lines.get(li + 1).get(0) : null;
				if (prevLineLast != null && prevLineLast.is("{")) continue;
				if (next != null && next.is("}")) continue;
				sb.append('\n');
				continue;
			}
			Tok first = line.get(0);
			int level = depth;
			if (first.is("}") || first.is(")") && paren > 0) level = Math.max(0, level - (first.is("}") ? 1 : 0));
			boolean caseLine = (first.is("case") || first.is("default") && line.size() > 1 && (line.get(1).is(":") || line.get(1).is("->")))
					&& !isSwitch.isEmpty() && isSwitch.get(isSwitch.size() - 1);
			if (!first.is("}") && !isSwitch.isEmpty() && isSwitch.get(isSwitch.size() - 1) && inCase.get(inCase.size() - 1) && !caseLine) level++;
			boolean chained = first.is(".") || first.is("::") || first.is("+") || first.is("&&") || first.is("||") || first.is("?") || first.is(":") && !caseLine;
			if (paren > 0 || chained || (continuation && !first.is("{") && !first.is("}") && !first.comment())) level += 2;
			else if (bracelessBody && !first.is("{") && !first.comment()) level++; // if (x)\n    doIt();
			if (first.kind == BLOCK_C || first.kind == DOC_C) sb.append(reindentComment(first.text, level));
			else {
				for (int i = 0; i < level; i++) sb.append(INDENT);
			}
			// tokens of the line
			for (int i = 0; i < line.size(); i++) {
				Tok t = line.get(i);
				if (i > 0) {
					if (space(line, i)) sb.append(' ');
				}
				if (i == 0 && (t.kind == BLOCK_C || t.kind == DOC_C)) { /* already appended */ }
				else if (t.kind == BLOCK_C || t.kind == DOC_C) sb.append(t.text);
				else sb.append(t.kind == LINE_C ? t.text.replaceAll("\\s+$", "") : t.text);
				// structure
				if (t.is("switch")) nextBraceIsSwitch = true;
				if (t.is("(") || t.is("[")) paren++;
				if ((t.is(")") || t.is("]")) && paren > 0) paren--;
				if (t.is("{")) {
					depth++;
					parenStack.add(paren); // a block inside parentheses (anonymous class, lambda) starts fresh
					paren = 0;
					isSwitch.add(nextBraceIsSwitch && paren == 0);
					inCase.add(false);
					nextBraceIsSwitch = false;
				}
				if (t.is("}")) {
					depth = Math.max(0, depth - 1);
					if (!parenStack.isEmpty()) paren = parenStack.remove(parenStack.size() - 1);
					if (!isSwitch.isEmpty()) { isSwitch.remove(isSwitch.size() - 1); inCase.remove(inCase.size() - 1); }
				}
				if (caseLine && (t.is(":") || t.is("->")) && !inCase.isEmpty()) inCase.set(inCase.size() - 1, t.is(":"));
			}
			sb.append('\n');
			Tok last = lastCode(line);
			prevLineLast = line.get(line.size() - 1);
			// "if (x)" / "else" / "for (...)" without "{": the next line is its body
			Tok f0 = line.get(0);
			bracelessBody = last != null && paren == 0 && !last.is("{") && !last.is(";") && !last.is("}")
					&& ((last.is(")") && (f0.is("if") || f0.is("for") || f0.is("while") || (f0.is("}") && line.size() > 1 && line.get(1).is("else"))))
					|| last.is("else") || (last.is(")") && f0.is("else")));
			// a statement that goes on to the next line (no ; { } at the end, not an annotation or a label)
			continuation = last != null && paren == 0 && !last.is(";") && !last.is("{") && !last.is("}") && !last.is(":") && !last.is(",")
					&& !last.is("->") && !(line.get(0).is("@") && line.size() <= 4) && !caseLine
					&& !(first.kind == BLOCK_C || first.kind == DOC_C || first.kind == LINE_C) && !isDeclarationHeader(line);
		}
		String out = sb.toString();
		return out.endsWith("\n") ? out : out + "\n";
	}

	/** "class X extends Y", "void f()", "if (a)" ... lines that are followed by "{" on the next line (Allman style). */
	private static boolean isDeclarationHeader(ArrayList<Tok> line) {
		Tok last = lastCode(line);
		if (last == null) return false;
		for (Tok t : line) if (t.is("class") || t.is("interface") || t.is("enum") || t.is("record")) return true;
		Tok f = line.get(0);
		if (last.is(")") && (f.is("if") || f.is("for") || f.is("while") || f.is("switch") || f.is("catch") || f.is("synchronized")
				|| f.is("}") || f.is("try"))) return true;
		if (last.is(")") || last.is("else") || last.is("try") || last.is("finally") || last.is("do")) return true;
		if (f.is("@")) return true;
		return false;
	}

	private static Tok lastCode(ArrayList<Tok> line) {
		for (int i = line.size() - 1; i >= 0; i--) if (!line.get(i).comment()) return line.get(i);
		return null;
	}

	private static String reindentComment(String text, int level) {
		StringBuilder pad = new StringBuilder();
		for (int i = 0; i < level; i++) pad.append(INDENT);
		String[] parts = text.split("\n", -1);
		StringBuilder sb = new StringBuilder(pad).append(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			String p = parts[i].replaceAll("^\\s+", "");
			// " * text" lines are aligned under the "/*"; other lines keep their own spacing
			if (p.startsWith("*")) sb.append('\n').append(pad).append(' ').append(p);
			else sb.append('\n').append(parts[i]);
		}
		return sb.toString();
	}

	/** Should there be a space between token i-1 and token i of the line? */
	private static boolean space(ArrayList<Tok> line, int i) {
		Tok p = line.get(i - 1), t = line.get(i);
		if (t.comment()) return true;
		if (p.kind == BLOCK_C || p.kind == DOC_C) return true;
		String a = p.text, b = t.text;
		if (b.equals(";")) return false;
		if (a.equals(";")) return true;
		if (b.equals(",")) return false;
		if (a.equals(",")) return true;
		if (a.equals(".") || b.equals(".") || a.equals("::") || b.equals("::")) return false;
		if (a.equals("@")) return false;
		if (b.equals("...")) return false;
		if (a.equals("...")) return true;
		if (a.equals("(") || a.equals("[")) return false;
		if (b.equals(")") || b.equals("]")) return false;
		if (b.equals("[")) return false;
		if (a.equals("]")) return t.word() || b.equals("{") || b.equals("=") || BINARY.contains(b);
		if (generic(line, i - 1) || generic(line, i)) {
			if (b.equals("<") || a.equals("<") || b.equals(">") || b.equals(">>") || b.equals(">>>")) return false;
			if (a.equals(">") || a.equals(">>") || a.equals(">>>")) return !b.equals("(") && !b.equals(">");
		}
		if (b.equals("(")) {
			if (a.equals(")") || SPACE_AFTER_KW.contains(a) || BINARY.contains(a) || a.equals(":")) return true;
			if (MAYBE_UNARY.contains(a)) return !unaryAt(line, i - 1);
			if (a.equals("<") || a.equals(">") || a.equals(">>") || a.equals(">>>")) return true;
			return false; // f(x), new Foo(), this(...)
		}
		if (b.equals("{")) return !a.equals("(") && !a.equals("[");
		if (a.equals("{")) return !b.equals("}");
		if (b.equals("}")) return true;
		if (a.equals("}")) return true;
		if (b.equals(":")) return colonSpaced(line, i);
		if (a.equals(":")) return true;
		if (a.equals("!") || a.equals("~")) return false;            // !done
		if (a.equals("++") || a.equals("--")) return postfix(line, i - 1); // ++i stays together, i++ then a space
		if (b.equals("++") || b.equals("--")) return !postfix(line, i);    // i++ stays together
		if (b.equals("!") || b.equals("~")) return true;
		if (MAYBE_UNARY.contains(a) && unaryAt(line, i - 1)) return false;
		if (MAYBE_UNARY.contains(b)) return true;
		if (MAYBE_UNARY.contains(a)) return true;
		if (BINARY.contains(a) || BINARY.contains(b)) return true;
		if (b.equals("<") || b.equals(">") || a.equals("<") || a.equals(">") || b.equals(">>") || b.equals(">>>") || a.equals(">>") || a.equals(">>>")) return true;
		if (a.equals(")")) return t.word() || b.equals("@") || b.equals("\"");
		return p.word() && t.word() || SPACE_AFTER_KW.contains(a);
	}

	private static boolean isOperator(String s) {
		return BINARY.contains(s) || MAYBE_UNARY.contains(s) || s.equals("<") || s.equals(">") || s.equals("return");
	}

	/** "++" / "--" after a name or ")" / "]" (x++) instead of before it (++x). */
	private static boolean postfix(ArrayList<Tok> line, int i) {
		if (i == 0) return false;
		Tok p = line.get(i - 1);
		return p.kind == TerminalTokens.TokenNameIdentifier || p.is(")") || p.is("]");
	}

	/** "+" / "-" used as a sign (-x, a = -1, f(-2)). */
	private static boolean unaryAt(ArrayList<Tok> line, int i) {
		if (i == 0) return true;
		Tok p = line.get(i - 1);
		if (p.kind == TerminalTokens.TokenNameIdentifier || p.is(")") || p.is("]") || p.is("++") || p.is("--")) return false;
		char c = p.text.isEmpty() ? ' ' : p.text.charAt(0);
		if (Character.isDigit(c) || c == '"' || c == '\'' || p.is("this") || p.is("super") || p.is("null") || p.is("true") || p.is("false")) return false;
		return true;
	}

	/** Is the "<" or ">" at i part of type arguments (List<String>, Map<K, List<V>>, <T> void f())? */
	private static boolean generic(ArrayList<Tok> line, int i) {
		Tok t = line.get(i);
		if (!(t.is("<") || t.is(">") || t.is(">>") || t.is(">>>"))) return false;
		if (t.is("<")) return closes(line, i);
		// find an opening "<" that this one closes
		int need = t.text.length();
		for (int j = i - 1; j >= 0; j--) {
			Tok x = line.get(j);
			if (x.is("<") && closes(line, j)) { need--; if (need <= 0) return true; continue; }
			if (!typeToken(x)) return false;
		}
		return false;
	}

	private static boolean closes(ArrayList<Tok> line, int open) {
		int depth = 0;
		for (int j = open; j < line.size(); j++) {
			Tok x = line.get(j);
			if (x.is("<")) depth++;
			else if (x.is(">")) depth--;
			else if (x.is(">>")) depth -= 2;
			else if (x.is(">>>")) depth -= 3;
			else if (!typeToken(x)) return false;
			if (depth <= 0) return true;
		}
		return false;
	}

	private static boolean typeToken(Tok x) {
		return x.kind == TerminalTokens.TokenNameIdentifier || x.is(".") || x.is(",") || x.is("?") || x.is("extends") || x.is("super")
				|| x.is("[") || x.is("]") || x.is("&") || x.is("@") || isPrimitive(x.text);
	}

	private static boolean isPrimitive(String s) {
		switch (s) {
			case "int": case "long": case "short": case "byte": case "char": case "float": case "double": case "boolean": case "void": return true;
			default: return false;
		}
	}

	/** ":" of a ternary or an enhanced for gets spaces; "case X:", labels and "default:" do not. */
	private static boolean colonSpaced(ArrayList<Tok> line, int i) {
		int q = 0, par = 0;
		for (int j = 0; j < i; j++) {
			Tok x = line.get(j);
			if (x.is("?") && !generic(line, j)) q++;
			if (x.is(":") && q > 0) q--;
			if (x.is("(")) par++;
			if (x.is(")")) par--;
		}
		if (q > 0) return true;
		Tok f = line.get(0);
		if (f.is("case") || f.is("default")) return false;
		if (par > 0 && (f.is("for") || f.is("}"))) return true; // for (Item x : items)
		if (f.is("assert")) return true;
		return false;
	}

	private static int count(String s, char c) {
		int n = 0;
		for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
		return n;
	}
}
