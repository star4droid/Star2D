package com.star4droid.template.net;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ObjectVars;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A networked element: its id (the same on every phone), its owner, what is synced, and the states it sent / received.
 * Positions are element centers in editor pixels (2D) or meters (3D); rotation in editor degrees (2D) or x/y/z degrees (3D).
 */
final class NetObject {
	static final int POS = 1, ROT = 2, VEL = 4, VIS = 8, IMG = 16, ANIM = 32, TEXT = 64, VARS = 128;
	static final int CONTINUOUS = POS | ROT | VEL, DISCRETE = VIS | IMG | ANIM | TEXT | VARS;
	static final int DEFAULT_MASK = POS | ROT | VEL | VIS;

	final int id;
	final PlayerItem item;
	int owner;
	int mask;
	boolean spawned;
	String template = "";
	final ArrayList<String> syncVars = new ArrayList<String>();

	/** Last state written to the network by this phone (change detection). */
	final State sent = new State();
	boolean sentOnce;
	/** Discrete fields last received (remote objects): only applied when they change. */
	final State applied = new State();
	/** Remote objects: received continuous states with the host time they belong to. */
	final State[] buf = new State[6];
	final double[] bufT = new double[6];
	int bufCount;
	/** Host, client-owned element: the last state its owner reported, when, and the corrections sent. */
	final State ownerState = new State();
	double ownerTime;
	boolean hasOwnerState;
	int correctionSeq, ackedCorrection;
	/** Physics: original body type (remote copies are kinematic). */
	BodyDef.BodyType savedType;
	boolean behaviorsOff;

	NetObject(int id, PlayerItem item, int owner, int mask) {
		this.id = id;
		this.item = item;
		this.owner = owner;
		this.mask = mask == 0 ? DEFAULT_MASK : mask;
		for (int i = 0; i < buf.length; i++) buf[i] = new State();
	}

	static final class State {
		float x, y, z, rx, ry, rz, vx, vy, vz;
		boolean vis = true;
		String img = "", anim = "", text = "";
		final LinkedHashMap<String, String> vars = new LinkedHashMap<String, String>();

		void set(State o) {
			x = o.x;
			y = o.y;
			z = o.z;
			rx = o.rx;
			ry = o.ry;
			rz = o.rz;
			vx = o.vx;
			vy = o.vy;
			vz = o.vz;
			vis = o.vis;
			img = o.img;
			anim = o.anim;
			text = o.text;
			vars.clear();
			vars.putAll(o.vars);
		}
	}

	boolean is3D() {
		return item instanceof com.star4droid.template.d3.Object3D;
	}

	boolean alive() {
		if (is3D()) return !((com.star4droid.template.d3.Object3D) item).isDestroyed();
		return Behavior.alive(item);
	}

	Body body() {
		if (is3D()) return null;
		try {
			return item.getBody();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static float vk(StageImp st) {
		int steps = st == null ? 1 : Math.max(1, st.getSteps());
		return Behavior.W / steps;
	}

	// ------------------------------------------------------------------ read the element

	void capture(State s, StageImp st) {
		if (is3D()) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) item;
			s.x = o.position.x;
			s.y = o.position.y;
			s.z = o.position.z;
			s.rx = o.euler.x;
			s.ry = o.euler.y;
			s.rz = o.euler.z;
			s.vx = o.getVelocityX3();
			s.vy = o.getVelocityY3();
			s.vz = o.getVelocityZ3();
			s.vis = o.isVisible();
			s.anim = nz(o.getAnimation());
			s.img = nz(o.getDef().Texture);
		} else {
			s.x = Behavior.cx(item);
			s.y = Behavior.cy(item);
			Body b = body();
			Actor a = item.getActor();
			s.rx = b != null ? -(float) Math.toDegrees(b.getAngle()) : (a == null ? 0 : -a.getRotation());
			if (b != null) {
				float k = vk(st);
				s.vx = b.getLinearVelocity().x / k;
				s.vy = b.getLinearVelocity().y / k;
			} else {
				s.vx = 0;
				s.vy = 0;
			}
			s.vis = a == null || a.isVisible();
			s.img = NetTrack.image(item);
			s.anim = NetTrack.anim(item);
			s.text = item instanceof com.badlogic.gdx.scenes.scene2d.ui.Label ? String.valueOf(((com.badlogic.gdx.scenes.scene2d.ui.Label) item).getText()) : "";
		}
		s.vars.clear();
		for (String v : syncVars) {
			Object val = ObjectVars.get(item, v);
			s.vars.put(v, val == null ? "" : varText(val));
		}
	}

	static String varText(Object v) {
		if (v instanceof Float || v instanceof Double) {
			double d = ((Number) v).doubleValue();
			if (d == Math.rint(d) && Math.abs(d) < 1e9) return "n" + (long) d;
			return "n" + v;
		}
		if (v instanceof Number) return "n" + v;
		if (v instanceof Boolean) return "b" + v;
		return "s" + v;
	}

	static Object varValue(String t) {
		if (t == null || t.isEmpty()) return "";
		char k = t.charAt(0);
		String v = t.substring(1);
		try {
			if (k == 'n') return v.contains(".") || v.contains("E") ? (Object) Float.valueOf(v) : (Object) Integer.valueOf(v);
		} catch (NumberFormatException e) {
			try {
				return Float.valueOf(v);
			} catch (NumberFormatException e2) {
				return 0;
			}
		}
		if (k == 'b') return Boolean.valueOf(v);
		return v;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	// ------------------------------------------------------------------ write the element

	/** Continuous fields (position / rotation / velocity). */
	void applyContinuous(State s, int fields, StageImp st, boolean kinematicVelocity) {
		if (is3D()) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) item;
			if ((fields & POS) != 0) o.setPosition3(s.x, s.y, s.z);
			if ((fields & ROT) != 0) o.setRotation3(s.rx, s.ry, s.rz);
			if ((fields & VEL) != 0 && o.isDynamic()) o.setVelocity3(s.vx, s.vy, s.vz);
			return;
		}
		Body b = body();
		Actor a = item.getActor();
		if (b != null) {
			float x = (fields & POS) != 0 ? s.x * Behavior.W : b.getPosition().x;
			float y = (fields & POS) != 0 ? s.y * Behavior.W : b.getPosition().y;
			float ang = (fields & ROT) != 0 ? (float) Math.toRadians(-s.rx) : b.getAngle();
			b.setTransform(x, y, ang);
			if ((fields & VEL) != 0 || kinematicVelocity) {
				float k = vk(st);
				b.setLinearVelocity(s.vx * k, s.vy * k);
			}
			b.setAwake(true);
		} else if (a != null) {
			if ((fields & POS) != 0) a.setPosition(s.x - a.getWidth() * 0.5f, s.y - a.getHeight() * 0.5f);
			if ((fields & ROT) != 0) a.setRotation(-s.rx);
		}
	}

	/** Discrete fields (visible, image, animation, text, variables): only what changed since the last time. */
	void applyDiscrete(State s, int fields, StageImp st) {
		if ((fields & VIS) != 0 && s.vis != applied.vis) {
			applied.vis = s.vis;
			if (is3D()) ((com.star4droid.template.d3.Object3D) item).setVisible3(s.vis);
			else if (item.getActor() != null) item.getActor().setVisible(s.vis);
		}
		if ((fields & IMG) != 0 && !s.img.equals(applied.img)) {
			applied.img = s.img;
			if (!s.img.isEmpty()) {
				if (is3D()) ((com.star4droid.template.d3.Object3D) item).setTexture(s.img);
				else if (st != null) st.setImage(item, s.img);
			}
		}
		if ((fields & ANIM) != 0 && !s.anim.equals(applied.anim)) {
			applied.anim = s.anim;
			if (is3D()) {
				if (!s.anim.isEmpty()) ((com.star4droid.template.d3.Object3D) item).playAnimation(s.anim, true, 1f);
			} else if (s.anim.isEmpty()) item.removeAnimation();
			else item.setAnimation(s.anim);
		}
		if ((fields & TEXT) != 0 && !s.text.equals(applied.text)) {
			applied.text = s.text;
			item.setItemText(s.text);
		}
		if ((fields & VARS) != 0) {
			for (Map.Entry<String, String> e : s.vars.entrySet()) {
				String old = applied.vars.get(e.getKey());
				if (e.getValue().equals(old)) continue;
				applied.vars.put(e.getKey(), e.getValue());
				ObjectVars.set(item, e.getKey(), varValue(e.getValue()));
			}
		}
	}

	// ------------------------------------------------------------------ encoding

	/** Writes the fields of {@code fields} of a state. */
	void write(NetCodec.Out o, State s, int fields) {
		boolean d3 = is3D();
		o.var(id).var(fields);
		if ((fields & POS) != 0) {
			o.f32(s.x).f32(s.y);
			if (d3) o.f32(s.z);
		}
		if ((fields & ROT) != 0) {
			o.f32(s.rx);
			if (d3) o.f32(s.ry).f32(s.rz);
		}
		if ((fields & VEL) != 0) {
			o.f32(s.vx).f32(s.vy);
			if (d3) o.f32(s.vz);
		}
		if ((fields & VIS) != 0) o.bool(s.vis);
		if ((fields & IMG) != 0) o.str(s.img);
		if ((fields & ANIM) != 0) o.str(s.anim);
		if ((fields & TEXT) != 0) o.str(s.text);
		if ((fields & VARS) != 0) {
			o.var(s.vars.size());
			for (Map.Entry<String, String> e : s.vars.entrySet()) o.str(e.getKey()).str(e.getValue());
		}
	}

	/** Reads the fields written by {@link #write} (after the id and the fields were read). */
	static void read(NetCodec.In in, State s, int fields, boolean d3) {
		if ((fields & POS) != 0) {
			s.x = in.f32();
			s.y = in.f32();
			if (d3) s.z = in.f32();
		}
		if ((fields & ROT) != 0) {
			s.rx = in.f32();
			if (d3) {
				s.ry = in.f32();
				s.rz = in.f32();
			}
		}
		if ((fields & VEL) != 0) {
			s.vx = in.f32();
			s.vy = in.f32();
			if (d3) s.vz = in.f32();
		}
		if ((fields & VIS) != 0) s.vis = in.bool();
		if ((fields & IMG) != 0) s.img = in.str();
		if ((fields & ANIM) != 0) s.anim = in.str();
		if ((fields & TEXT) != 0) s.text = in.str();
		if ((fields & VARS) != 0) {
			int n = in.var();
			s.vars.clear();
			for (int i = 0; i < n; i++) s.vars.put(in.str(), in.str());
		}
	}

	/** Fields of {@code fields} that differ between two states (continuous ones beyond a small threshold). */
	int diff(State a, State b, int fields) {
		int out = 0;
		float ep = is3D() ? 0.002f : 0.05f, epv = is3D() ? 0.01f : 0.5f;
		if ((fields & POS) != 0 && (Math.abs(a.x - b.x) > ep || Math.abs(a.y - b.y) > ep || Math.abs(a.z - b.z) > ep)) out |= POS;
		if ((fields & ROT) != 0 && (Math.abs(a.rx - b.rx) > 0.05f || Math.abs(a.ry - b.ry) > 0.05f || Math.abs(a.rz - b.rz) > 0.05f)) out |= ROT;
		if ((fields & VEL) != 0 && (Math.abs(a.vx - b.vx) > epv || Math.abs(a.vy - b.vy) > epv || Math.abs(a.vz - b.vz) > epv)) out |= VEL;
		if ((fields & VIS) != 0 && a.vis != b.vis) out |= VIS;
		if ((fields & IMG) != 0 && !a.img.equals(b.img)) out |= IMG;
		if ((fields & ANIM) != 0 && !a.anim.equals(b.anim)) out |= ANIM;
		if ((fields & TEXT) != 0 && !a.text.equals(b.text)) out |= TEXT;
		if ((fields & VARS) != 0 && !a.vars.equals(b.vars)) out |= VARS;
		return out;
	}

	// ------------------------------------------------------------------ interpolation buffer (remote copies)

	void push(State s, double hostTime) {
		// keep the buffer ordered by time; drop old / duplicate entries
		if (bufCount > 0 && hostTime <= bufT[bufCount - 1]) {
			if (hostTime == bufT[bufCount - 1]) buf[bufCount - 1].set(s);
			return;
		}
		if (bufCount == buf.length) {
			State first = buf[0];
			System.arraycopy(buf, 1, buf, 0, buf.length - 1);
			System.arraycopy(bufT, 1, bufT, 0, bufT.length - 1);
			buf[buf.length - 1] = first;
			bufCount--;
		}
		buf[bufCount].set(s);
		bufT[bufCount] = hostTime;
		bufCount++;
	}

	/** The state at {@code t} (host time) into out: between the two states around it, or a short guess ahead. False when empty. */
	boolean sample(double t, State out) {
		if (bufCount == 0) return false;
		if (t <= bufT[0] || bufCount == 1) {
			out.set(buf[0]);
			if (bufCount == 1 && t > bufT[0]) extrapolate(out, (float) Math.min(0.25, t - bufT[0]));
			return true;
		}
		for (int i = 1; i < bufCount; i++) {
			if (t <= bufT[i]) {
				State a = buf[i - 1], b = buf[i];
				float k = (float) ((t - bufT[i - 1]) / Math.max(1e-6, bufT[i] - bufT[i - 1]));
				out.set(b);
				float far = is3D() ? 6f : 250f;
				boolean teleport = Math.abs(a.x - b.x) > far || Math.abs(a.y - b.y) > far || Math.abs(a.z - b.z) > far;
				if (!teleport) {
					out.x = a.x + (b.x - a.x) * k;
					out.y = a.y + (b.y - a.y) * k;
					out.z = a.z + (b.z - a.z) * k;
					out.rx = angle(a.rx, b.rx, k);
					out.ry = angle(a.ry, b.ry, k);
					out.rz = angle(a.rz, b.rz, k);
					out.vx = a.vx + (b.vx - a.vx) * k;
					out.vy = a.vy + (b.vy - a.vy) * k;
					out.vz = a.vz + (b.vz - a.vz) * k;
				}
				return true;
			}
		}
		out.set(buf[bufCount - 1]);
		extrapolate(out, (float) Math.min(0.25, t - bufT[bufCount - 1]));
		return true;
	}

	private static void extrapolate(State s, float dt) {
		if (dt <= 0) return;
		s.x += s.vx * dt;
		s.y += s.vy * dt;
		s.z += s.vz * dt;
	}

	static float angle(float a, float b, float k) {
		float d = ((b - a) % 360 + 540) % 360 - 180;
		return a + d * k;
	}
}
