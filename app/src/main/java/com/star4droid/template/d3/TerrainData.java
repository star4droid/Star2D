package com.star4droid.template.d3;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Everything a painted terrain owns, saved in one file of the project's "terrains" folder (.s2t):
 * the height grid (0..1, times Terrain Height), the paint (splat) map of the 4 ground layers and the scattered objects
 * (trees, rocks, bushes, flowers or project models). Grass is not stored: it grows from the paint of its layer.
 *
 * Grid: (res + 1) x (res + 1) heights, index j * (res + 1) + i, i along local X (-0.5 .. 0.5), j along local Z.
 * Each cell is split by the diagonal from (i + 1, j) to (i, j + 1), the same triangles the mesh and the Bullet
 * heightfield use, so {@link #height(float, float)} is exactly the surface that is drawn and collided with.
 */
public class TerrainData {
	public static final int MAGIC = 0x53325432; // "S2T2"
	public static final int MAX_RES = 512, MAX_SPLAT = 1024;

	public int res = 128;
	public float[] h;
	public int splatRes = 256;
	/** RGBA weights (layer 1 = r ... layer 4 = a), 0..255. */
	public byte[] splat;
	public final Array<Proto> protos = new Array<Proto>();
	public final Array<Inst> inst = new Array<Inst>();
	/** Changes on every edit (renderers / bodies compare it). */
	public int revision;

	/** A kind of scattered object. */
	public static class Proto {
		/** "builtin:tree_round" ... or a project model "/tree.glb". */
		public String src = "builtin:tree_round";
		/** Collider radius in meters at scale 1 (0 = walk through). */
		public float collide = 0;
		/** Collider height (m, scale 1). */
		public float collideHeight = 3;
		/** Not drawn farther than this (m, 0 = the foliage distance of the terrain). */
		public float distance = 0;
		public boolean shadows = true;
		/** Tilt with the slope of the ground. */
		public boolean alignToGround = false;
		/** Random tint amount (built-in objects). */
		public float tint = 0.12f;

		public Proto() {
		}

		public Proto(String src, float collide, float collideHeight) {
			this.src = src;
			this.collide = collide;
			this.collideHeight = collideHeight;
		}

		public Proto copy() {
			Proto p = new Proto(src, collide, collideHeight);
			p.distance = distance;
			p.shadows = shadows;
			p.alignToGround = alignToGround;
			p.tint = tint;
			return p;
		}
	}

	/** One scattered object: kind, position on the terrain (0..1), turn (degrees), size. */
	public static class Inst {
		public int p;
		public float u, v, rot, s = 1;
		public int seed;

		public Inst() {
		}

		public Inst(int p, float u, float v, float rot, float s, int seed) {
			this.p = p;
			this.u = u;
			this.v = v;
			this.rot = rot;
			this.s = s;
			this.seed = seed;
		}
	}

	public TerrainData() {
	}

	public TerrainData(int res, int splatRes) {
		this.res = MathUtils.clamp(res, 4, MAX_RES);
		this.splatRes = MathUtils.clamp(splatRes, 16, MAX_SPLAT);
		h = new float[(this.res + 1) * (this.res + 1)];
		splat = new byte[this.splatRes * this.splatRes * 4];
		fillLayer(0);
	}

	public int n() {
		return res + 1;
	}

	/** Whole terrain painted with one layer (0..3). */
	public void fillLayer(int layer) {
		for (int i = 0; i < splat.length; i += 4) {
			splat[i] = splat[i + 1] = splat[i + 2] = splat[i + 3] = 0;
			splat[i + layer] = (byte) 255;
		}
	}

	public float at(int i, int j) {
		int n = res + 1;
		if (i < 0) i = 0;
		else if (i > res) i = res;
		if (j < 0) j = 0;
		else if (j > res) j = res;
		return h[j * n + i];
	}

	/** Height (0..1 of Terrain Height) at u, v in 0..1, on the drawn triangles. */
	public float height(float u, float v) {
		float x = MathUtils.clamp(u, 0, 1) * res, z = MathUtils.clamp(v, 0, 1) * res;
		int i = Math.min((int) x, res - 1), j = Math.min((int) z, res - 1);
		float fu = x - i, fv = z - j;
		float h00 = at(i, j), h10 = at(i + 1, j), h01 = at(i, j + 1), h11 = at(i + 1, j + 1);
		if (fu + fv <= 1f) return h00 + fu * (h10 - h00) + fv * (h01 - h00);
		return h11 + (1f - fu) * (h01 - h11) + (1f - fv) * (h10 - h11);
	}

	/** Slope 0 (flat) .. 1 (vertical) at u, v for a terrain of this size (m). */
	public float slope(float u, float v, float sizeX, float sizeZ, float height) {
		float e = 1f / res;
		float dx = (height(u + e, v) - height(u - e, v)) * height / (2 * e * sizeX);
		float dz = (height(u, v + e) - height(u, v - e)) * height / (2 * e * sizeZ);
		float g = (float) Math.sqrt(dx * dx + dz * dz);
		return g / (float) Math.sqrt(1 + g * g);
	}

	/** Paint weight 0..1 of a layer at u, v (nearest texel). */
	public float weight(int layer, float u, float v) {
		int x = MathUtils.clamp((int) (u * splatRes), 0, splatRes - 1), y = MathUtils.clamp((int) (v * splatRes), 0, splatRes - 1);
		int k = (y * splatRes + x) * 4;
		int s = (splat[k] & 255) + (splat[k + 1] & 255) + (splat[k + 2] & 255) + (splat[k + 3] & 255);
		return s == 0 ? (layer == 0 ? 1 : 0) : (splat[k + layer] & 255) / (float) s;
	}

	public float minH() {
		float m = Float.MAX_VALUE;
		for (float v : h) m = Math.min(m, v);
		return m;
	}

	public float maxH() {
		float m = -Float.MAX_VALUE;
		for (float v : h) m = Math.max(m, v);
		return m;
	}

	/** Grid resampled to another resolution (bilinear), the paint and the objects stay. */
	public void resample(int newRes) {
		newRes = MathUtils.clamp(newRes, 4, MAX_RES);
		if (newRes == res) return;
		int n = newRes + 1;
		float[] nh = new float[n * n];
		for (int j = 0; j < n; j++)
			for (int i = 0; i < n; i++)
				nh[j * n + i] = height((float) i / newRes, (float) j / newRes);
		h = nh;
		res = newRes;
		revision++;
	}

	public TerrainData copy() {
		TerrainData d = new TerrainData();
		d.res = res;
		d.h = h.clone();
		d.splatRes = splatRes;
		d.splat = splat.clone();
		for (Proto p : protos) d.protos.add(p.copy());
		for (Inst i : inst) d.inst.add(new Inst(i.p, i.u, i.v, i.rot, i.s, i.seed));
		d.revision = revision;
		return d;
	}

	/** Index of a kind of object (added when missing). */
	public int proto(String src) {
		for (int i = 0; i < protos.size; i++) if (protos.get(i).src.equals(src)) return i;
		Proto p = new Proto(src, 0, 3);
		Foliage3D.defaults(p);
		protos.add(p);
		return protos.size - 1;
	}

	// ------------------------------------------------------------------ file

	public static TerrainData load(FileHandle f) throws Exception {
		InputStream in = f.read();
		try {
			return read(in);
		} finally {
			in.close();
		}
	}

	public static TerrainData read(InputStream raw) throws Exception {
		DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
		if (in.readInt() != MAGIC) throw new Exception("not a Star2D terrain file");
		int version = in.readInt();
		DataInputStream z = new DataInputStream(new BufferedInputStream(new InflaterInputStream(in)));
		TerrainData d = new TerrainData();
		d.res = MathUtils.clamp(z.readInt(), 4, MAX_RES);
		int n = d.res + 1;
		d.h = new float[n * n];
		for (int i = 0; i < d.h.length; i++) d.h[i] = z.readFloat();
		d.splatRes = MathUtils.clamp(z.readInt(), 16, MAX_SPLAT);
		d.splat = new byte[d.splatRes * d.splatRes * 4];
		z.readFully(d.splat);
		int pc = z.readInt();
		for (int i = 0; i < pc; i++) {
			Proto p = new Proto();
			p.src = z.readUTF();
			p.collide = z.readFloat();
			p.collideHeight = z.readFloat();
			p.distance = z.readFloat();
			int flags = z.readInt();
			p.shadows = (flags & 1) != 0;
			p.alignToGround = (flags & 2) != 0;
			p.tint = z.readFloat();
			d.protos.add(p);
		}
		int ic = z.readInt();
		d.inst.ensureCapacity(ic);
		for (int i = 0; i < ic; i++) {
			Inst t = new Inst();
			t.p = z.readShort();
			t.u = z.readFloat();
			t.v = z.readFloat();
			t.rot = z.readFloat();
			t.s = z.readFloat();
			t.seed = z.readInt();
			if (t.p >= 0 && t.p < d.protos.size) d.inst.add(t);
		}
		if (version < 1) throw new Exception("bad terrain version");
		return d;
	}

	public void save(File f) throws Exception {
		File dir = f.getParentFile();
		if (dir != null && !dir.exists()) dir.mkdirs();
		File tmp = new File(f.getPath() + ".tmp");
		FileOutputStream fo = new FileOutputStream(tmp);
		try {
			write(fo);
		} finally {
			fo.close();
		}
		if (f.exists() && !f.delete()) throw new Exception("can't replace " + f);
		if (!tmp.renameTo(f)) throw new Exception("can't write " + f);
	}

	public void write(java.io.OutputStream raw) throws Exception {
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(raw));
		out.writeInt(MAGIC);
		out.writeInt(1);
		java.util.zip.Deflater def = new java.util.zip.Deflater(6);
		DeflaterOutputStream dz = new DeflaterOutputStream(out, def);
		DataOutputStream z = new DataOutputStream(new BufferedOutputStream(dz));
		z.writeInt(res);
		for (float v : h) z.writeFloat(v);
		z.writeInt(splatRes);
		z.write(splat);
		z.writeInt(protos.size);
		for (Proto p : protos) {
			z.writeUTF(p.src);
			z.writeFloat(p.collide);
			z.writeFloat(p.collideHeight);
			z.writeFloat(p.distance);
			z.writeInt((p.shadows ? 1 : 0) | (p.alignToGround ? 2 : 0));
			z.writeFloat(p.tint);
		}
		z.writeInt(inst.size);
		for (Inst t : inst) {
			z.writeShort(t.p);
			z.writeFloat(t.u);
			z.writeFloat(t.v);
			z.writeFloat(t.rot);
			z.writeFloat(t.s);
			z.writeInt(t.seed);
		}
		z.flush();
		dz.finish();
		def.end();
		out.flush();
	}
}
