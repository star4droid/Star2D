package com.star4droid.template.anim;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PlayerItem;

import java.util.ArrayList;

/**
 * One timeline playing (on one target element). Every frame it computes the value of each track at the current time and
 * writes it to the element. Physical elements whose position / rotation is animated become kinematic while it plays, so
 * they follow the keys exactly and still push what they touch; they get their body type back when it ends.
 */
public final class TimelinePlayer {
	final Timeline tl;
	final PlayerItem target;
	final StageImp stage;
	float time, speed = 1f;
	int dir = 1;
	boolean paused, finished;
	/** Tweens: how many more times to play (-1 = forever) and whether it is a tween (other end event). */
	int repeatLeft;
	boolean isTween;
	String tweenProperty;
	float delay;
	private final ArrayList<Binding> bindings = new ArrayList<Binding>();
	private boolean bound;
	private final Color c0 = new Color(), c1 = new Color(), cv = new Color();

	static final class Binding {
		Timeline.Track track;
		PlayerItem item;
		int kind;
		float baseNumber;
		Body body;
		BodyDef.BodyType savedType;
		String lastText;
		int lastBool = -1;
		boolean scale3;
	}

	TimelinePlayer(StageImp stage, Timeline tl, PlayerItem target) {
		this.stage = stage;
		this.tl = tl;
		this.target = target;
	}

	/** The element a track moves: the target for tracks made on the timeline's own element, else the named element. */
	PlayerItem resolve(String element) {
		if (target != null && (element == null || element.isEmpty() || element.equals("@self") || element.equals(tl.target))) return target;
		if (element == null || element.isEmpty() || stage == null) return null;
		PlayerItem it = stage.findItem(element);
		if (it == null && stage.hasWorld3D()) it = stage.world3d().find(element);
		return it;
	}

	void bind() {
		if (bound) return;
		bound = true;
		for (Timeline.Track t : tl.tracks) {
			if (t.keys.isEmpty()) continue;
			PlayerItem it = resolve(t.element);
			if (it == null) continue;
			Binding b = new Binding();
			b.track = t;
			b.item = it;
			b.kind = Props.kind(t.property, t.keys.get(0).v);
			if (b.kind == Props.NUMBER) b.baseNumber = Props.getNumber(it, t.property);
			b.scale3 = Props.is3D(it) && t.property.startsWith("Scale ");
			if (Props.movesBody(t.property) && !Props.is3D(it)) {
				Body body = null;
				try {
					body = it.getBody();
				} catch (RuntimeException ignored) {
				}
				if (body != null && body.getType() == BodyDef.BodyType.DynamicBody && !alreadyKinematic(body)) {
					b.body = body;
					b.savedType = body.getType();
					body.setType(BodyDef.BodyType.KinematicBody);
					body.setLinearVelocity(0, 0);
					body.setAngularVelocity(0);
				}
			}
			bindings.add(b);
		}
	}

	private boolean alreadyKinematic(Body body) {
		for (Binding o : bindings) if (o.body == body) return true;
		return false;
	}

	/** Gives the physical elements their body type back (end / stop). */
	void release() {
		for (Binding b : bindings) {
			if (b.body != null && b.savedType != null) {
				try {
					b.body.setType(b.savedType);
					b.body.setLinearVelocity(0, 0);
					b.body.setAngularVelocity(0);
					b.body.setAwake(true);
				} catch (RuntimeException ignored) {
				}
				b.savedType = null;
			}
			if (b.scale3) Props.scale3Done(b.item);
		}
	}

	/** Writes the values of every track at the current time. */
	void apply() {
		bind();
		for (int i = 0; i < bindings.size(); i++) {
			Binding b = bindings.get(i);
			try {
				applyOne(b, time);
			} catch (RuntimeException ignored) {
			}
		}
	}

	private void applyOne(Binding b, float t) {
		java.util.List<Timeline.Key> keys = b.track.keys;
		int n = keys.size();
		// keys around t: k0 at or before, k1 after
		int i1 = 0;
		while (i1 < n && keys.get(i1).t <= t) i1++;
		Timeline.Key k0 = i1 == 0 ? keys.get(0) : keys.get(i1 - 1);
		Timeline.Key k1 = i1 >= n ? keys.get(n - 1) : keys.get(i1);
		float a = 0;
		if (k0 != k1 && k1.t > k0.t) a = Interp.apply(k1.interp, (t - k0.t) / (k1.t - k0.t));
		String prop = b.track.property;
		switch (b.kind) {
			case Props.NUMBER: {
				float v0 = num(k0.v), v1 = num(k1.v);
				float v = v0 + (v1 - v0) * a;
				if (tl.relative) v += b.baseNumber;
				Props.setNumber(b.item, prop, v);
				break;
			}
			case Props.COLOR: {
				Props.parseColor(k0.v, c0);
				Props.parseColor(k1.v, c1);
				cv.set(c0).lerp(c1, a);
				Props.setColor(b.item, prop, cv);
				break;
			}
			case Props.TEXT: {
				// texts / images jump at the key
				String v = (a >= 1f || k0 == k1) ? k1.v : k0.v;
				if (t < keys.get(0).t) v = keys.get(0).v;
				if (!v.equals(b.lastText)) {
					b.lastText = v;
					Props.setText(stage, b.item, prop, v);
				}
				break;
			}
			case Props.BOOL: {
				String v = (a >= 1f || k0 == k1) ? k1.v : k0.v;
				int on = "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim()) ? 1 : 0;
				if (on != b.lastBool) {
					b.lastBool = on;
					Props.setBool(b.item, prop, on == 1);
				}
				break;
			}
		}
	}

	static float num(String s) {
		if (s == null) return 0;
		try {
			return Float.parseFloat(s.trim().replace("f", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Advances; returns false when it finished (and was released). */
	boolean update(float dt) {
		if (finished) return false;
		if (paused) return true;
		if (delay > 0) {
			delay -= dt;
			if (delay > 0) return true;
			dt = -delay;
			delay = 0;
			if (isTween) Anim.captureTweenStart(this);
		}
		float prev = time;
		float dur = Math.max(0.0001f, tl.duration);
		float step = dt * speed * dir;
		time += step;
		boolean forward = step > 0, ended = false;
		if ((forward && time >= dur) || (!forward && step != 0 && time <= 0)) {
			int loop = tl.loop;
			if (isTween) loop = repeatLeft != 0 ? (tl.loop == Timeline.PING_PONG ? Timeline.PING_PONG : Timeline.LOOP) : Timeline.ONCE;
			float end = forward ? dur : 0, start = forward ? 0 : dur;
			float over = forward ? time - dur : -time;
			if (over > dur) over = over % dur;
			if (loop == Timeline.ONCE) {
				time = end;
				fireMarkers(prev, time);
				ended = true;
			} else if (loop == Timeline.LOOP) {
				fireMarkers(prev, end);
				time = forward ? over : dur - over;
				fireMarkers(start, time);
				if (isTween && repeatLeft > 0) repeatLeft--;
			} else {
				// ping-pong: bounce at the ends
				fireMarkers(prev, end);
				dir = -dir;
				time = forward ? dur - over : over;
				fireMarkers(end, time);
				if (isTween && repeatLeft > 0) repeatLeft--;
			}
		} else fireMarkers(prev, time);
		apply();
		if (ended) {
			finished = true;
			release();
			Anim.fireEnd(this);
			return false;
		}
		return true;
	}

	private void fireMarkers(float from, float to) {
		if (tl.markers.isEmpty() || from == to) return;
		float lo = Math.min(from, to), hi = Math.max(from, to);
		for (Timeline.Marker m : tl.markers) {
			boolean hit = from < to ? (m.t > lo && m.t <= hi) || (from == 0 && m.t == 0) : (m.t >= lo && m.t < hi);
			if (hit) Anim.fireMarker(this, m.name);
		}
	}

	/** Jumps to a time (seconds) and shows it at once. */
	void seek(float t) {
		time = Math.max(0, Math.min(tl.duration, t));
		apply();
	}

	void stop() {
		if (finished) return;
		finished = true;
		release();
	}

	public String name() {
		return tl.name;
	}

	public PlayerItem target() {
		return target;
	}
}
