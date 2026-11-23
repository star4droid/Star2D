package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Animation file ".animf": clips taken out of a .glb / .gltf model, kept without the model. An element plays them
 * through its Animation Model property (bones are matched by name, so the element's model needs the same skeleton,
 * e.g. Mixamo). One file may hold one clip or several.
 *
 * <p>Format (gzip): int magic "ANF1", int version, int clips; per clip: UTF name, float duration, int tracks; per track:
 * UTF bone name, then translation, rotation and scaling keyframes, each as int count + (time, values) floats.</p>
 */
public final class AnimFile {
	private static final int MAGIC = 0x414E4631, VERSION = 1;
	private static final ObjectMap<String, Array<Animation>> CACHE = new ObjectMap<String, Array<Animation>>();
	private static final ObjectMap<String, String> STAMPS = new ObjectMap<String, String>();

	private AnimFile() {
	}

	public static boolean is(String path) {
		return path != null && path.toLowerCase().endsWith(".animf");
	}

	/** Writes {@code clips} to {@code out}. */
	public static void write(File out, Array<Animation> clips) throws IOException {
		File tmp = new File(out.getPath() + ".tmp");
		DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp))));
		try {
			o.writeInt(MAGIC);
			o.writeInt(VERSION);
			o.writeInt(clips.size);
			for (Animation a : clips) {
				o.writeUTF(a.id == null ? "" : a.id);
				o.writeFloat(a.duration);
				int n = 0;
				for (NodeAnimation na : a.nodeAnimations) if (na.node != null && na.node.id != null) n++;
				o.writeInt(n);
				for (NodeAnimation na : a.nodeAnimations) {
					if (na.node == null || na.node.id == null) continue;
					o.writeUTF(na.node.id);
					writeV(o, na.translation);
					writeQ(o, na.rotation);
					writeV(o, na.scaling);
				}
			}
		} finally {
			o.close();
		}
		if (out.exists() && !out.delete()) throw new IOException("cannot replace " + out.getName());
		if (!tmp.renameTo(out)) throw new IOException("cannot write " + out.getName());
	}

	private static void writeV(DataOutputStream o, Array<NodeKeyframe<Vector3>> k) throws IOException {
		if (k == null) {
			o.writeInt(0);
			return;
		}
		o.writeInt(k.size);
		for (NodeKeyframe<Vector3> f : k) {
			o.writeFloat(f.keytime);
			o.writeFloat(f.value.x);
			o.writeFloat(f.value.y);
			o.writeFloat(f.value.z);
		}
	}

	private static void writeQ(DataOutputStream o, Array<NodeKeyframe<Quaternion>> k) throws IOException {
		if (k == null) {
			o.writeInt(0);
			return;
		}
		o.writeInt(k.size);
		for (NodeKeyframe<Quaternion> f : k) {
			o.writeFloat(f.keytime);
			o.writeFloat(f.value.x);
			o.writeFloat(f.value.y);
			o.writeFloat(f.value.z);
			o.writeFloat(f.value.w);
		}
	}

	/** Reads a file. Each track's node is a stand-in Node that only carries the bone name. */
	public static Array<Animation> read(InputStream in) throws IOException {
		DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)));
		try {
			if (d.readInt() != MAGIC) throw new IOException("not an .animf file");
			int ver = d.readInt();
			if (ver > VERSION) throw new IOException(".animf version " + ver + " is newer than this engine");
			int clips = d.readInt();
			Array<Animation> out = new Array<Animation>(clips);
			for (int c = 0; c < clips; c++) {
				Animation a = new Animation();
				a.id = d.readUTF();
				a.duration = d.readFloat();
				int tracks = d.readInt();
				for (int t = 0; t < tracks; t++) {
					NodeAnimation na = new NodeAnimation();
					Node bone = new Node();
					bone.id = d.readUTF();
					na.node = bone;
					na.translation = readV(d);
					na.rotation = readQ(d);
					na.scaling = readV(d);
					a.nodeAnimations.add(na);
				}
				out.add(a);
			}
			return out;
		} finally {
			d.close();
		}
	}

	private static Array<NodeKeyframe<Vector3>> readV(DataInputStream d) throws IOException {
		int n = d.readInt();
		if (n <= 0) return null;
		Array<NodeKeyframe<Vector3>> k = new Array<NodeKeyframe<Vector3>>(n);
		for (int i = 0; i < n; i++) {
			float t = d.readFloat();
			k.add(new NodeKeyframe<Vector3>(t, new Vector3(d.readFloat(), d.readFloat(), d.readFloat())));
		}
		return k;
	}

	private static Array<NodeKeyframe<Quaternion>> readQ(DataInputStream d) throws IOException {
		int n = d.readInt();
		if (n <= 0) return null;
		Array<NodeKeyframe<Quaternion>> k = new Array<NodeKeyframe<Quaternion>>(n);
		for (int i = 0; i < n; i++) {
			float t = d.readFloat();
			k.add(new NodeKeyframe<Quaternion>(t, new Quaternion(d.readFloat(), d.readFloat(), d.readFloat(), d.readFloat())));
		}
		return k;
	}

	/** The clips of an .animf of the project (models folder, like Model paths), cached; null when it cannot be read. */
	public static Array<Animation> clips(String path) {
		FileHandle f = Assets3D.file(path, "models");
		if (f == null) {
			log("animation file not found: " + path);
			return null;
		}
		String key = Assets3D.projectPath + "|" + path;
		String stamp = f.lastModified() + ":" + f.length();
		Array<Animation> a = CACHE.get(key);
		if (a != null && stamp.equals(STAMPS.get(key))) return a;
		try {
			a = read(f.read());
			CACHE.put(key, a);
			STAMPS.put(key, stamp);
			return a;
		} catch (Throwable t) {
			log("animation file " + path + ": " + t);
			return null;
		}
	}

	public static void forget(String path) {
		String key = Assets3D.projectPath + "|" + path;
		CACHE.remove(key);
		STAMPS.remove(key);
	}

	private static void log(String msg) {
		try {
			if (Gdx.app != null) Gdx.app.error("Star2D-3D", msg);
		} catch (Throwable ignored) {
		}
	}
}
