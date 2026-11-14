package com.star4droid.star2d.logs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Minimal reader of Android's native tombstone protobuf (ApplicationExitInfo.getTraceInputStream for CRASH_NATIVE). */
final class Tombstone {
	private Tombstone() {}

	/** One protobuf field: number, wire type and either a varint or a byte range. */
	private static final class F {
		int num, type;
		long v;
		int off, len;
	}

	private static List<F> fields(byte[] b, int off, int len) {
		List<F> out = new ArrayList<>();
		int p = off, end = off + len;
		while (p < end) {
			long[] r = varint(b, p);
			p = (int) r[1];
			F f = new F();
			f.num = (int) (r[0] >>> 3);
			f.type = (int) (r[0] & 7);
			switch (f.type) {
				case 0: r = varint(b, p); f.v = r[0]; p = (int) r[1]; break;
				case 1: f.off = p; f.len = 8; p += 8; break;
				case 5: f.off = p; f.len = 4; p += 4; break;
				case 2: r = varint(b, p); p = (int) r[1]; f.off = p; f.len = (int) r[0]; p += f.len; break;
				default: throw new IllegalStateException("wire type " + f.type);
			}
			if (p > end) throw new IllegalStateException("truncated");
			out.add(f);
		}
		return out;
	}

	private static long[] varint(byte[] b, int p) {
		long v = 0;
		int s = 0;
		while (true) {
			int x = b[p++] & 0xff;
			v |= (long) (x & 0x7f) << s;
			if (x < 0x80) return new long[]{v, p};
			s += 7;
		}
	}

	private static String str(byte[] b, F f) {
		return new String(b, f.off, f.len, StandardCharsets.UTF_8);
	}

	static String format(byte[] b) {
		List<F> top = fields(b, 0, b.length);
		long tid = 0;
		StringBuilder head = new StringBuilder("Native crash\n");
		List<F> threads = new ArrayList<>(), logs = new ArrayList<>();
		for (F f : top) {
			switch (f.num) {
				case 2: head.append("Build: ").append(str(b, f)).append('\n'); break;
				case 6: tid = f.v; break;
				case 9: head.append("Command: ").append(str(b, f)).append('\n'); break;
				case 10: head.append(signal(b, f)).append('\n'); break;
				case 14: head.append("Abort message: ").append(str(b, f)).append('\n'); break;
				case 15:
					for (F c : fields(b, f.off, f.len)) if (c.num == 1) head.append("Cause: ").append(str(b, c)).append('\n');
					break;
				case 16: threads.add(f); break;
				case 18: logs.add(f); break;
			}
		}
		StringBuilder crash = new StringBuilder(), others = new StringBuilder();
		for (F entry : threads) {
			long key = -1;
			F value = null;
			for (F kv : fields(b, entry.off, entry.len)) {
				if (kv.num == 1) key = kv.v;
				else if (kv.num == 2) value = kv;
			}
			if (value == null) continue;
			boolean crashed = key == tid;
			thread(b, value, crashed ? crash : others, crashed ? 64 : 6);
		}
		StringBuilder sb = new StringBuilder(head);
		sb.append("\n=== Crashing thread (tid ").append(tid).append(") ===\n").append(crash.length() == 0 ? "(not in the report)\n" : crash);
		StringBuilder log = new StringBuilder();
		for (F buf : logs) logBuffer(b, buf, log);
		if (log.length() > 0) {
			String l = log.toString();
			String[] lines = l.split("\n");
			int from = Math.max(0, lines.length - 150);
			sb.append("\n=== Last log lines of the process ===\n");
			for (int i = from; i < lines.length; i++) sb.append(lines[i]).append('\n');
		}
		sb.append("\n=== Other threads ===\n").append(others);
		return sb.toString();
	}

	private static String signal(byte[] b, F f) {
		String name = "", code = "";
		long addr = 0;
		boolean hasAddr = false;
		for (F s : fields(b, f.off, f.len)) {
			if (s.num == 2) name = str(b, s);
			else if (s.num == 4) code = str(b, s);
			else if (s.num == 8) hasAddr = s.v != 0;
			else if (s.num == 9) addr = s.v;
		}
		return "Signal: " + name + " " + code + (hasAddr ? " fault addr 0x" + Long.toHexString(addr) : "");
	}

	private static void thread(byte[] b, F t, StringBuilder out, int maxFrames) {
		String name = "?";
		long id = 0;
		List<String> frames = new ArrayList<>();
		for (F f : fields(b, t.off, t.len)) {
			if (f.num == 1) id = f.v;
			else if (f.num == 2) name = str(b, f);
			else if (f.num == 4) frames.add(frame(b, f, frames.size()));
		}
		out.append("-- ").append(name).append(" (").append(id).append(")\n");
		for (int i = 0; i < frames.size() && i < maxFrames; i++) out.append("  ").append(frames.get(i)).append('\n');
		if (frames.size() > maxFrames) out.append("  ... ").append(frames.size() - maxFrames).append(" more\n");
	}

	private static String frame(byte[] b, F f, int index) {
		long rel = 0, off = 0;
		String fn = "", file = "";
		for (F x : fields(b, f.off, f.len)) {
			if (x.num == 1) rel = x.v;
			else if (x.num == 4) fn = str(b, x);
			else if (x.num == 5) off = x.v;
			else if (x.num == 6) file = str(b, x);
		}
		String shortFile = file.contains("!") ? file.substring(file.lastIndexOf('!') + 1) : file.substring(file.lastIndexOf('/') + 1);
		return String.format(java.util.Locale.US, "#%02d pc %08x  %s%s", index, rel, shortFile, fn.isEmpty() ? "" : " (" + fn + "+" + off + ")");
	}

	private static void logBuffer(byte[] b, F buf, StringBuilder out) {
		for (F f : fields(b, buf.off, buf.len)) {
			if (f.num != 2) continue;
			String time = "", tag = "", msg = "";
			long prio = 0, tid = 0;
			for (F m : fields(b, f.off, f.len)) {
				if (m.num == 1) time = str(b, m);
				else if (m.num == 3) tid = m.v;
				else if (m.num == 4) prio = m.v;
				else if (m.num == 5) tag = str(b, m);
				else if (m.num == 6) msg = str(b, m);
			}
			char p = "??VDIWEF".charAt((int) Math.min(7, Math.max(0, prio)));
			out.append(time).append(' ').append(tid).append(' ').append(p).append(' ').append(tag).append(": ").append(msg.replace('\n', ' ')).append('\n');
		}
	}
}
