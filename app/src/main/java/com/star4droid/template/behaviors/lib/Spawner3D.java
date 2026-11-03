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

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Spawner 3D", icon = "egg", category = "Gameplay", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onSpawn(PlayerItem spawned)", "onAllSpawned()"},
		desc = "Makes copies of a template element at this spot every few seconds: enemies, falling crates, coins, cars, raindrops of rocks. Random area, a limit of how many live at once, a total, an initial throw and an automatic lifetime.")
public class Spawner3D extends Behavior {
	@Header("What")
	@Param(desc = "The element to copy (it is hidden at start). Several = a random one each time.")
	public List<PlayerItem> templates = new ArrayList<PlayerItem>();

	@Header("When")
	@Param(desc = "Seconds between two copies.", unit = "s", min = 0.02, max = 3600, step = 0.05)
	public float interval = 2;
	@Param(desc = "Random extra wait added to each interval.", unit = "s", min = 0, max = 3600, step = 0.05)
	public float randomExtra = 0.5f;
	@Param(desc = "Waits this long before the first copy.", unit = "s", min = 0, max = 3600, step = 0.1)
	public float startDelay = 0;
	@Param(desc = "Starts spawning when the game starts (else a block starts it).")
	public boolean autoStart = true;
	@Param(desc = "Most copies alive at the same time (0 = no limit).", min = 0, max = 10000, step = 1)
	public int maxAlive = 10;
	@Param(desc = "Stops after this many copies in total (0 = never).", min = 0, max = 1000000, step = 1)
	public int totalLimit = 0;

	@Header("Where")
	@Param(desc = "Random spot within this distance of the spawner (0 = exactly here).", unit = "m", min = 0, max = 1000, step = 0.1)
	public float areaRadius = 0;
	@Param(desc = "Copies face the same way as the spawner (else they keep the template's heading).")
	public boolean useSpawnerRotation = true;
	@Param(desc = "Throw speed along the spawner's front (DYNAMIC copies; projectiles fly).", unit = "m/s", min = -500, max = 500, step = 0.5)
	public float launchSpeed = 0;
	@Param(desc = "Upward throw speed.", unit = "m/s", min = -500, max = 500, step = 0.5)
	public float launchUp = 0;
	@Param(desc = "Copies are removed after this many seconds (0 = they stay).", unit = "s", min = 0, max = 3600, step = 0.5)
	public float copyLifetime = 0;

	private final ArrayList<PlayerItem> alive = new ArrayList<PlayerItem>();
	private float wait;
	private boolean running, hidden;
	private int total;

	@Block(label = "spawn now", desc = "Makes one copy now (limits apply).", labelAr = "أنشئ الآن", descAr = "ينشئ نسخة الآن (تطبق الحدود).", category = "Spawner 3D", args = {})
	public void spawn() {
		spawnOne();
	}

	@Block(label = "start spawning", desc = "Starts making copies.", labelAr = "ابدأ الإنشاء", descAr = "يبدأ إنشاء النسخ.", category = "Spawner 3D", args = {})
	public void start() {
		running = true;
	}

	@Block(label = "stop spawning", desc = "Stops making copies.", labelAr = "أوقف الإنشاء", descAr = "يوقف إنشاء النسخ.", category = "Spawner 3D", args = {})
	public void stop() {
		running = false;
	}

	@Block(label = "set spawn interval", desc = "Seconds between copies.", labelAr = "اضبط فاصل الإنشاء", descAr = "ثوانٍ بين النسخ.", category = "Spawner 3D", args = {"seconds"})
	public void setInterval(float s) {
		interval = Math.max(0.02f, s);
	}

	@Block(label = "copies alive", desc = "How many copies exist now.", labelAr = "النسخ الحية", descAr = "عدد النسخ الموجودة الآن.", category = "Spawner 3D", args = {})
	public int getAlive() {
		prune();
		return alive.size();
	}

	@Block(label = "copies made", desc = "How many copies in total.", labelAr = "النسخ المنشأة", descAr = "عدد النسخ الكلي.", category = "Spawner 3D", args = {})
	public int getTotal() {
		return total;
	}

	@Block(label = "remove all copies", desc = "Removes every copy it made.", labelAr = "أزل كل النسخ", descAr = "يزيل كل النسخ التي أنشأها.", category = "Spawner 3D", args = {})
	public void clear() {
		for (PlayerItem p : alive) if (p instanceof Object3D) ((Object3D) p).destroy();
		alive.clear();
	}

	private void prune() {
		for (int i = alive.size() - 1; i >= 0; i--) if (!Support3D.alive3(alive.get(i))) alive.remove(i);
	}

	private PlayerItem spawnOne() {
		Object3D here = obj();
		if (here == null || templates.isEmpty()) return null;
		prune();
		if (maxAlive > 0 && alive.size() >= maxAlive) return null;
		if (totalLimit > 0 && total >= totalLimit) return null;
		PlayerItem tp = templates.get(MathUtils.random(templates.size() - 1));
		if (!(tp instanceof Object3D)) return null;
		Object3D t = (Object3D) tp;
		PlayerItem c = t.getClone(t.getName() + "_s" + GameplaySupport.nextId());
		if (!(c instanceof Object3D)) return null;
		Object3D o = (Object3D) c;
		Vector3 p = new Vector3(here.position);
		if (areaRadius > 0) {
			float a = MathUtils.random(360f), r = (float) Math.sqrt(MathUtils.random()) * areaRadius;
			p.add(MathUtils.cosDeg(a) * r, 0, MathUtils.sinDeg(a) * r);
		}
		o.setPosition3(p.x, p.y, p.z);
		if (useSpawnerRotation) o.setRotation3(here.euler.x, here.getYaw3(), here.euler.z);
		o.setVisible(true);
		if (!o.hasBody() && t.d() != null && !"NONE".equals(t.d().Body)) o.setPhysicsEnabled(true);
		if (launchSpeed != 0 || launchUp != 0) {
			Vector3 f = here.forward(new Vector3()).scl(launchSpeed).add(0, launchUp, 0);
			Behavior pb = o.getBehavior("Projectile3D");
			if (pb instanceof Projectile3D) ((Projectile3D) pb).launch(f.x, f.y, f.z, item);
			else o.setVelocity3(f.x, f.y, f.z);
		}
		if (copyLifetime > 0) Lifetime3D.after(o, copyLifetime);
		alive.add(o);
		total++;
		fire("onSpawn", o);
		if (totalLimit > 0 && total >= totalLimit) fire("onAllSpawned");
		return o;
	}

	@Override
	public void onStart() {
		running = autoStart;
		wait = startDelay;
	}

	@Override
	public void onUpdate(float dt) {
		if (!hidden) {
			hidden = true;
			for (PlayerItem t : templates) if (t != item) Support3D.setActive3(t, false);
		}
		if (!running) return;
		wait -= dt;
		if (wait <= 0) {
			spawnOne();
			wait = interval + (randomExtra > 0 ? MathUtils.random(0f, randomExtra) : 0);
		}
	}
}
