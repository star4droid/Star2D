package com.star4droid.template.net;

import java.nio.charset.Charset;

/** Small binary writer / reader for the network messages (big-endian, strings as UTF-8 with a length). */
final class NetCodec {
	private NetCodec() {
	}

	static final Charset UTF8 = Charset.forName("UTF-8");

	static final class Out {
		byte[] b;
		int n;

		Out() {
			this(64);
		}

		Out(int size) {
			b = new byte[Math.max(16, size)];
		}

		private void need(int k) {
			if (n + k > b.length) b = java.util.Arrays.copyOf(b, Math.max(b.length * 2, n + k));
		}

		Out u8(int v) {
			need(1);
			b[n++] = (byte) v;
			return this;
		}

		Out bool(boolean v) {
			return u8(v ? 1 : 0);
		}

		Out i16(int v) {
			need(2);
			b[n++] = (byte) (v >> 8);
			b[n++] = (byte) v;
			return this;
		}

		Out i32(int v) {
			need(4);
			b[n++] = (byte) (v >> 24);
			b[n++] = (byte) (v >> 16);
			b[n++] = (byte) (v >> 8);
			b[n++] = (byte) v;
			return this;
		}

		Out i64(long v) {
			i32((int) (v >> 32));
			return i32((int) v);
		}

		Out f32(float v) {
			return i32(Float.floatToIntBits(v));
		}

		/** Variable-length unsigned int (1 byte under 128). */
		Out var(int v) {
			while ((v & ~0x7F) != 0) {
				u8((v & 0x7F) | 0x80);
				v >>>= 7;
			}
			return u8(v);
		}

		Out str(String s) {
			byte[] d = (s == null ? "" : s).getBytes(UTF8);
			var(d.length);
			need(d.length);
			System.arraycopy(d, 0, b, n, d.length);
			n += d.length;
			return this;
		}

		int size() {
			return n;
		}

		byte[] bytes() {
			return java.util.Arrays.copyOf(b, n);
		}
	}

	static final class In {
		final byte[] b;
		int p;

		In(byte[] b) {
			this.b = b == null ? new byte[0] : b;
		}

		boolean more() {
			return p < b.length;
		}

		int u8() {
			if (p >= b.length) throw new IndexOutOfBoundsException("message too short");
			return b[p++] & 0xFF;
		}

		boolean bool() {
			return u8() != 0;
		}

		int i16() {
			int v = (u8() << 8) | u8();
			return (short) v;
		}

		int i32() {
			return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8();
		}

		long i64() {
			return ((long) i32() << 32) | (i32() & 0xFFFFFFFFL);
		}

		float f32() {
			return Float.intBitsToFloat(i32());
		}

		int var() {
			int v = 0, shift = 0;
			for (int i = 0; i < 5; i++) {
				int c = u8();
				v |= (c & 0x7F) << shift;
				if ((c & 0x80) == 0) return v;
				shift += 7;
			}
			return v;
		}

		String str() {
			int len = var();
			if (len < 0 || p + len > b.length) throw new IndexOutOfBoundsException("bad string");
			String s = new String(b, p, len, UTF8);
			p += len;
			return s;
		}
	}
}
