package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@BehaviorInfo(name = "Endless Road 3D", icon = "road-variant", category = "Vehicles", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onLevelUp(int level)", "onCrash(PlayerItem car)", "onCollect(PlayerItem pickup)"},
		desc = "Builds a never-ending road ahead of the player (toward -Z) from road tile templates, recycling the tiles behind; adds side props, traffic in lanes and pickups, gets harder with the distance and counts distance and score. Put it on an empty object at the road's start.")
public class EndlessRoad3D extends Behavior {
	@Header("Road")
	@Param(desc = "The player's car (or runner) the road is built for.")
	public PlayerItem target;
	@Param(desc = "Road tile templates (a random one each time; they are hidden at start).")
	public List<PlayerItem> roadTiles = new ArrayList<PlayerItem>();
	@Param(desc = "Length of a tile along Z (0 = measured from the first template).", unit = "m", min = 0, max = 1000, step = 0.5)
	public float tileLength = 0;
	@Param(desc = "Tiles kept in front of the player.", min = 2, max = 60, step = 1)
	public int tilesAhead = 10;
	@Param(desc = "Tiles kept behind the player.", min = 1, max = 20, step = 1)
	public int tilesBehind = 2;

	@Header("Side props")
	@Param(desc = "Trees, lamps, buildings, signs placed beside the road (random).")
	public List<PlayerItem> props = new ArrayList<PlayerItem>();
	@Param(desc = "Chance of a prop on each side of a tile.", min = 0, max = 1, step = 0.05)
	public float propChance = 0.6f;
	@Param(desc = "Distance of the props from the road's middle (0 = just outside the road).", unit = "m", min = 0, max = 500, step = 0.5)
	public float propDistance = 0;

	@Header("Traffic")
	@Param(desc = "Car templates driving in the lanes.")
	public List<PlayerItem> traffic = new ArrayList<PlayerItem>();
	@Param(desc = "Chance of a car on each new tile.", min = 0, max = 1, step = 0.05)
	public float trafficChance = 0.35f;
	@Param(desc = "Number of lanes.", min = 1, max = 12, step = 1)
	public int lanes = 3;
	@Param(desc = "Width of a lane (0 = road width / lanes).", unit = "m", min = 0, max = 50, step = 0.1)
	public float laneWidth = 0;
	@Param(desc = "Speed of the traffic.", unit = "km/h", min = 0, max = 100000, step = 1)
	public float trafficSpeed = 45;
	@Param(desc = "The left half of the lanes drives toward the player.")
	public boolean oncoming = false;
	@Param(desc = "Wheel nodes of the traffic models to spin, separated by commas.")
	public String wheelNodes = "wheel-front-left,wheel-front-right,wheel-back-left,wheel-back-right";

	@Header("Pickups")
	@Param(desc = "Coins, fuel, boosts placed in the lanes; taken when the player drives through.")
	public List<PlayerItem> pickups = new ArrayList<PlayerItem>();
	@Param(desc = "Chance of a pickup on each new tile.", min = 0, max = 1, step = 0.05)
	public float pickupChance = 0.3f;
	@Param(desc = "Height of the pickups above the road.", unit = "m", min = 0, max = 20, step = 0.05)
	public float pickupHeight = 0.8f;

	@Header("Difficulty and score")
	@Param(desc = "Next level every this many meters (0 = never).", unit = "m", min = 0, max = 100000, step = 10)
	public float levelDistance = 400;
	@Param(desc = "Traffic chance added per level.", min = 0, max = 1, step = 0.01)
	public float trafficPerLevel = 0.05f;
	@Param(desc = "Top speed added to the player's Car 3D per level.", unit = "km/h", min = 0, max = 100000, step = 1)
	public float speedPerLevel = 6;
	@Param(desc = "Score per meter driven.", min = 0, max = 1000, step = 0.1)
	public float scorePerMeter = 1;
	@Param(desc = "Score per pickup.", min = 0, max = 100000, step = 1)
	public float pickupScore = 10;

	private static final class Slot {
		Object3D tile;
		int index;
		final ArrayList<Object3D> extras = new ArrayList<Object3D>();
	}

	private final ArrayList<Slot> slots = new ArrayList<Slot>();
	private final ArrayList<Object3D> cars = new ArrayList<Object3D>();
	private final HashMap<Object3D, Float> carSpeed = new HashMap<Object3D, Float>();
	private final HashMap<Object3D, Float> carSpin = new HashMap<Object3D, Float>();
	private final HashMap<Object3D, Boolean> crashed = new HashMap<Object3D, Boolean>();
	private final HashMap<String, ArrayList<Object3D>> pool = new HashMap<String, ArrayList<Object3D>>();
	private final HashMap<Object3D, String> kindOf = new HashMap<Object3D, String>();
	private final Vector3 origin = new Vector3(), tp = new Vector3();
	private float len = 10, roadWidth = 8, bonus, best;
	private int nextIndex, level;
	private boolean has, paused;

	@Block(label = "distance driven", desc = "Meters from the start.", labelAr = "المسافة المقطوعة", descAr = "الأمتار من البداية.", category = "Endless road 3D", args = {})
	public float getDistance() {
		return Math.max(0, best);
	}

	@Block(label = "road score", desc = "Distance score + pickups.", labelAr = "نقاط الطريق", descAr = "نقاط المسافة + الالتقاطات.", category = "Endless road 3D", args = {})
	public int getScore() {
		return (int) (getDistance() * scorePerMeter + bonus);
	}

	@Block(label = "road level", desc = "Difficulty level (starts at 1).", labelAr = "مستوى الطريق", descAr = "مستوى الصعوبة (يبدأ من 1).", category = "Endless road 3D", args = {})
	public int getLevel() {
		return level + 1;
	}

	@Block(label = "set traffic chance", desc = "0..1 chance of a car per tile.", labelAr = "اضبط احتمال المرور", descAr = "احتمال 0..1 لسيارة في كل قطعة.", category = "Endless road 3D", args = {"chance"})
	public void setTrafficChance(float c) {
		trafficChance = clamp(c, 0, 1);
	}

	@Block(label = "pause road spawning", desc = "true = no new traffic and pickups.", labelAr = "أوقف إنشاء الطريق", descAr = "صح = لا مرور ولا التقاطات جديدة.", category = "Endless road 3D", args = {"paused"})
	public void setPaused(boolean on) {
		paused = on;
	}

	@Block(label = "add road score", desc = "Adds points (stunts, near misses).", labelAr = "أضف نقاطاً", descAr = "يضيف نقاطاً (حركات، تجاوزات).", category = "Endless road 3D", args = {"points"})
	public void addScore(float points) {
		bonus += points;
	}

	// ------------------------------------------------------------ pool

	private Object3D take(PlayerItem template) {
		if (!(template instanceof Object3D)) return null;
		Object3D t = (Object3D) template;
		ArrayList<Object3D> free = pool.get(t.getName());
		Object3D o = null;
		while (free != null && !free.isEmpty() && (o == null || o.isDestroyed())) o = free.remove(free.size() - 1);
		if (o == null || o.isDestroyed()) {
			PlayerItem c = t.getClone(t.getName() + "_r" + GameplaySupport.nextId());
			if (!(c instanceof Object3D)) return null;
			o = (Object3D) c;
			kindOf.put(o, t.getName());
		}
		o.setVisible(true);
		if (t.d() != null && !"NONE".equals(t.d().Body)) o.setPhysicsEnabled(true);
		return o;
	}

	private void giveBack(Object3D o) {
		if (o == null || o.isDestroyed()) return;
		Support3D.setActive3(o, false);
		String k = kindOf.get(o);
		if (k == null) return;
		ArrayList<Object3D> free = pool.get(k);
		if (free == null) pool.put(k, free = new ArrayList<Object3D>());
		free.add(o);
	}

	private PlayerItem pick(List<PlayerItem> list) {
		return list.isEmpty() ? null : list.get(MathUtils.random(list.size() - 1));
	}

	private float laneX(int lane) {
		float lw = laneWidth > 0 ? laneWidth : roadWidth / Math.max(1, lanes);
		return origin.x + (lane - (lanes - 1) * 0.5f) * lw;
	}

	private float tileZ(int index) {
		return origin.z - index * len;
	}

	// ------------------------------------------------------------ build

	private void setup() {
		Object3D me = obj();
		has = true;
		origin.set(me.position);
		PlayerItem first = roadTiles.isEmpty() ? null : roadTiles.get(0);
		if (first instanceof Object3D) {
			Object3D f = (Object3D) first;
			len = tileLength > 0 ? tileLength : Math.max(1, f.getSizeZ());
			roadWidth = Math.max(2, f.getSizeX());
		} else len = tileLength > 0 ? tileLength : 10;
		for (PlayerItem t : roadTiles) Support3D.setActive3(t, false);
		for (PlayerItem t : props) Support3D.setActive3(t, false);
		for (PlayerItem t : traffic) Support3D.setActive3(t, false);
		for (PlayerItem t : pickups) Support3D.setActive3(t, false);
		for (int i = -tilesBehind; i <= tilesAhead; i++) place(new Slot(), i, i > 2);
		nextIndex = tilesAhead + 1;
	}

	private void place(Slot s, int index, boolean extras) {
		s.index = index;
		if (s.tile == null) {
			s.tile = take(pick(roadTiles));
			if (!slots.contains(s)) slots.add(s);
		}
		for (Object3D e : s.extras) giveBack(e);
		s.extras.clear();
		float z = tileZ(index);
		if (s.tile != null) s.tile.setPosition3(origin.x, origin.y, z);
		if (!extras) return;
		// side props
		float side = propDistance > 0 ? propDistance : roadWidth * 0.5f + 2f;
		for (int k = -1; k <= 1; k += 2) {
			if (props.isEmpty() || MathUtils.random() > propChance) continue;
			Object3D p = take(pick(props));
			if (p == null) continue;
			p.setPosition3(origin.x + k * side, origin.y, z + MathUtils.random(-len * 0.4f, len * 0.4f));
			p.setYaw3(k < 0 ? 90 : -90);
			s.extras.add(p);
		}
		if (paused) return;
		float chance = Math.min(0.95f, trafficChance + level * trafficPerLevel);
		if (!traffic.isEmpty() && MathUtils.random() < chance) {
			int lane = MathUtils.random(lanes - 1);
			Object3D c = take(pick(traffic));
			if (c != null) {
				boolean toward = oncoming && lanes > 1 && lane < lanes / 2;
				c.setPosition3(laneX(lane), origin.y, z);
				c.setYaw3(toward ? 180 : 0);
				cars.add(c);
				carSpeed.put(c, (toward ? -1 : 1) * trafficSpeed / 3.6f * MathUtils.random(0.85f, 1.15f));
				crashed.remove(c);
			}
		}
		if (!pickups.isEmpty() && MathUtils.random() < pickupChance) {
			Object3D p = take(pick(pickups));
			if (p != null) {
				p.setPosition3(laneX(MathUtils.random(lanes - 1)), origin.y + pickupHeight, z + len * 0.25f);
				s.extras.add(p);
			}
		}
	}

	@Override
	public void onUpdate(float dt) {
		Object3D me = obj();
		World3D w = world3d();
		if (me == null || w == null || dt <= 0 || target == null) return;
		if (!has) setup();
		if (!Behavior.pos3(target, tp)) return;
		float dist = origin.z - tp.z;
		if (dist > best) best = dist;
		// level up
		if (levelDistance > 0) {
			int lv = (int) (Math.max(0, best) / levelDistance);
			while (level < lv) {
				level++;
				Behavior car = target.getBehavior("Car3D");
				if (car instanceof Car3D && speedPerLevel > 0) {
					// grow from the car's real speed (never below its top speed) and give the engine the power to reach the new top
					Car3D c3 = (Car3D) car;
					float old = Math.max(1f, c3.maxSpeed), now = Math.max(old, Math.abs(c3.getSpeedKmh())), top = now + speedPerLevel;
					c3.setMaxSpeed(top);
					c3.setAcceleration(c3.acceleration * top / old);
				}
				fire("onLevelUp", level + 1);
			}
		}
		// recycle tiles that fell behind
		int playerTile = (int) Math.floor(dist / len + 0.5f);
		for (int i = 0; i < slots.size(); i++) {
			Slot s = slots.get(i);
			if (s.index < playerTile - tilesBehind) place(s, nextIndex++, true);
		}
		// pickups taken?
		Object3D tgt = Support3D.o(target);
		float reach = tgt == null ? 1.5f : Math.max(1.2f, Math.max(tgt.getSizeX(), tgt.getSizeZ()) * 0.6f);
		for (int i = 0; i < slots.size(); i++) {
			ArrayList<Object3D> ex = slots.get(i).extras;
			for (int j = ex.size() - 1; j >= 0; j--) {
				Object3D p = ex.get(j);
				String k = kindOf.get(p);
				boolean isPickup = false;
				for (PlayerItem t : pickups) if (t != null && t.getName().equals(k)) isPickup = true;
				if (!isPickup) continue;
				if (Vector3.dst(p.position.x, 0, p.position.z, tp.x, 0, tp.z) < reach && Math.abs(p.position.y - tp.y) < 3f) {
					ex.remove(j);
					bonus += pickupScore;
					fire("onCollect", p);
					giveBack(p);
				}
			}
		}
		// traffic
		float behind = origin.z - (playerTile - tilesBehind - 1) * len, ahead = origin.z - (nextIndex + 1) * len;
		String[] nodes = wheelNodes.split(",");
		for (int i = cars.size() - 1; i >= 0; i--) {
			Object3D c = cars.get(i);
			if (c.isDestroyed()) {
				cars.remove(i);
				continue;
			}
			Float sp = carSpeed.get(c);
			float v = sp == null ? 0 : sp;
			if (!Boolean.TRUE.equals(crashed.get(c))) {
				c.setPosition3(c.position.x, c.position.y, c.position.z - v * dt);
				Float spin = carSpin.get(c);
				float s = ((spin == null ? 0 : spin) + Math.abs(v) * dt / 0.35f * MathUtils.radiansToDegrees) % 360f;
				carSpin.put(c, s);
				for (String n : nodes) if (n.trim().length() > 0) c.setNodeOffset(n.trim(), 0, 0, 0, -s, 0, 0);
			}
			boolean hit = w.touching(target, c) || tgt != null && Math.abs(c.position.x - tp.x) < (c.getSizeX() + tgt.getSizeX()) * 0.45f
					&& Math.abs(c.position.z - tp.z) < (c.getSizeZ() + tgt.getSizeZ()) * 0.45f && Math.abs(c.position.y - tp.y) < 2f;
			if (hit && !Boolean.TRUE.equals(crashed.get(c))) {
				crashed.put(c, Boolean.TRUE);
				fire("onCrash", c);
			}
			if (c.position.z > behind + len || c.position.z < ahead - len) {
				cars.remove(i);
				carSpeed.remove(c);
				crashed.remove(c);
				giveBack(c);
			}
		}
	}
}
