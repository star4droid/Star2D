package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.physics.box2d.Body;
import com.star4droid.star2d.ElementDefs.BoxDef;
import com.star4droid.star2d.ElementDefs.ItemDef;
import com.star4droid.template.Items.BoxBody;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Image;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Requires;
import com.star4droid.template.behaviors.Sound;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Turret", icon = "cannon", category = "Combat", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onShoot()", "onTargetFound(PlayerItem target)", "onTargetLost()", "onHit(PlayerItem target)"},
		desc = "An automatic gun: finds the nearest target in range, turns toward it and fires bullets that hurt it (targets need a Health behavior, or give them hit points with Auto health). Bullets are reused, so it is cheap.")
@Requires(body = true, note = "Needs a physical element; it rotates and fires from its center.")
public class Turret extends Behavior {
	@Header("Targets")
	@Param(desc = "Elements it shoots at.")
	public List<PlayerItem> targets = new ArrayList<PlayerItem>();
	@Param(desc = "Also elements whose name starts with one of these words (separate with commas), e.g. player, enemy.")
	public String targetPrefix = "player";
	@Param(desc = "It only sees targets this close.", unit = "px", min = 10, max = 10000)
	public float range = 500;

	@Header("Aim")
	@Param(desc = "Turns toward the target this fast. 0 = instantly.", unit = "deg/s", min = 0, max = 100000)
	public float turnSpeed = 240;
	@Param(desc = "Direction the picture looks at rotation 0: 0 = right, 90 = up, 180 = left, -90 = down.", unit = "deg", min = -360, max = 360)
	public float imageAngle = 0;
	@Param(desc = "Rotate the element toward the target. Off: it only fires, in the direction it already points.")
	public boolean rotateToTarget = true;
	@Param(desc = "Fires only when it points this close to the target.", unit = "deg", min = 0, max = 90)
	public float aimTolerance = 8;
	@Param(desc = "Aims where a moving target will be instead of where it is.")
	public boolean leadTarget = false;

	@Header("Fire")
	@Param(desc = "Fires automatically. Off: call fire now yourself.")
	public boolean active = true;
	@Param(desc = "Volleys per second.", min = 0.05, max = 30, step = 0.05)
	public float fireRate = 1.5f;
	@Param(desc = "Bullets in one volley.", min = 1, max = 20)
	public int burst = 1;
	@Param(desc = "Pause between the bullets of a volley.", unit = "s", min = 0.02, max = 2, step = 0.01)
	public float burstGap = 0.1f;
	@Param(desc = "Random sideways error of each bullet.", unit = "deg", min = 0, max = 90, step = 0.5)
	public float spread = 0;
	@Param(desc = "Bullets start this far from the center, in the firing direction (the end of the barrel).", unit = "px", min = 0, max = 1000)
	public float muzzle = 0;

	@Header("Bullet")
	@Image
	@Param(desc = "Picture of a bullet. Empty = a colored square.")
	public String bulletImage = "";
	@Param(desc = "Color of the square (or tint of the picture).")
	public Color bulletColor = Color.valueOf("FF5252");
	@Param(unit = "px", min = 2, max = 300)
	public float bulletSize = 14;
	@Param(unit = "px/s", min = 10, max = 100000)
	public float bulletSpeed = 400;
	@Param(desc = "A bullet that hits nothing disappears after this long.", unit = "s", min = 0.1, max = 30)
	public float bulletLifetime = 2;
	@Param(desc = "Hit points taken by one bullet.", min = 0.1, max = 100000)
	public float damage = 1;
	@Param(desc = "Give a target without Health this many hit points (0 = leave it alone).", min = 0, max = 100000)
	public float autoHealth = 0;
	@Param(desc = "Push the target along the bullet's direction.", unit = "px/s", min = 0, max = 100000)
	public float knockback = 0;
	@Sound
	@Param(desc = "Played at every shot.")
	public String shootSound = "";
	@Sound
	@Param(desc = "Played when a bullet hits.")
	public String hitSound = "";

	@Header("Obstacles")
	@Param(desc = "Walls that stop the bullets.")
	public List<PlayerItem> obstacles = new ArrayList<PlayerItem>();
	@Param(desc = "Also elements whose name starts with one of these words, e.g. wall, ground.")
	public String obstaclePrefix = "";

	private static final class Bullet {
		BoxBody body;
		float vx, vy, life;
		boolean on;
	}

	private static final int MAX_BULLETS = 64;
	private final ArrayList<Bullet> pool = new ArrayList<Bullet>();
	private final GameplaySupport.NameMatcher tMatch = new GameplaySupport.NameMatcher();
	private final GameplaySupport.NameMatcher oMatch = new GameplaySupport.NameMatcher();
	private final ArrayList<PlayerItem> foundTargets = new ArrayList<PlayerItem>();
	private final ArrayList<PlayerItem> foundWalls = new ArrayList<PlayerItem>();
	private PlayerItem current;
	private float aim, cooldown, burstTimer, scanTimer;
	private int burstLeft, fireQueued;
	private boolean aimInit;

	public void onStart() {
		aim = (float) Math.toDegrees(body() == null ? 0 : body().getAngle()) - imageAngle;
		aimInit = true;
	}

	@Block(label = "fire now", desc = "Fires one bullet in the direction the turret points.", labelAr = "أطلق الآن", descAr = "يطلق رصاصة في الاتجاه الذي يشير إليه المدفع.", category = "Turret")
	public void fireNow() {
		fireQueued++;
	}

	@Block(label = "turret on", desc = "On: the turret works. Off: it stops aiming and firing.", labelAr = "تشغيل المدفع", descAr = "مفعّل: المدفع يعمل. مطفأ: يتوقف عن التصويب والإطلاق.", category = "Turret", args = {"on"})
	public void setActive(boolean on) {
		active = on;
	}

	@Block(label = "add turret target", desc = "The turret will shoot this element too.", labelAr = "أضف هدفاً للمدفع", descAr = "سيطلق المدفع على هذا العنصر أيضاً.", category = "Turret", args = {"target"})
	public void addTarget(PlayerItem t) {
		if (t != null && !targets.contains(t)) targets.add(t);
	}

	@Block(label = "set turret range", desc = "How far it sees (px).", labelAr = "اضبط مدى المدفع", descAr = "مدى الرؤية (بكسل).", category = "Turret", args = {"range"})
	public void setRange(float value) {
		range = value;
	}

	@Block(label = "set turret fire rate", desc = "Volleys per second.", labelAr = "اضبط معدل إطلاق المدفع", descAr = "عدد الدفعات في الثانية.", category = "Turret", args = {"rate"})
	public void setFireRate(float value) {
		fireRate = value;
	}

	@Block(label = "set turret damage", desc = "Damage of one bullet.", labelAr = "اضبط ضرر المدفع", descAr = "ضرر الرصاصة الواحدة.", category = "Turret", args = {"damage"})
	public void setDamage(float value) {
		damage = value;
	}

	@Block(label = "turret has target", desc = "True while a target is in range.", labelAr = "للمدفع هدف", descAr = "صحيح طالما يوجد هدف في المدى.", category = "Turret")
	public boolean hasTarget() {
		return current != null && alive(current);
	}

	@Block(label = "turret target", desc = "The element it is aiming at (or nothing).", labelAr = "هدف المدفع", descAr = "العنصر الذي يصوّب عليه (أو لا شيء).", category = "Turret")
	public PlayerItem getTarget() {
		return hasTarget() ? current : null;
	}

	@Block(label = "turret aim angle", desc = "Direction it points, in degrees (0 = right, 90 = up).", labelAr = "زاوية تصويب المدفع", descAr = "اتجاهه بالدرجات (0 = يمين، 90 = أعلى).", category = "Turret")
	public float getAimAngle() {
		return aim;
	}

	// ------------------------------------------------------------------ targets

	private void rescan(float dt) {
		scanTimer -= dt;
		if (scanTimer > 0) return;
		scanTimer = 0.25f;
		tMatch.set(targetPrefix);
		oMatch.set(obstaclePrefix);
		GameplaySupport.scan(stage, tMatch, item, foundTargets);
		GameplaySupport.scan(stage, oMatch, item, foundWalls);
	}

	private boolean isBullet(PlayerItem p) {
		for (int i = 0; i < pool.size(); i++) if (pool.get(i).body == p) return true;
		return false;
	}

	private PlayerItem nearest() {
		PlayerItem best = null;
		float bd = range * range, x = centerX(), y = centerY();
		for (int pass = 0; pass < 2; pass++) {
			List<PlayerItem> l = pass == 0 ? targets : foundTargets;
			for (int i = 0; i < l.size(); i++) {
				PlayerItem p = l.get(i);
				if (p == null || p == item || !alive(p) || isBullet(p)) continue;
				float dx = cx(p) - x, dy = cy(p) - y, d = dx * dx + dy * dy;
				if (d <= bd) {
					bd = d;
					best = p;
				}
			}
		}
		return best;
	}

	private static float diff(float a, float b) {
		float d = (a - b) % 360f;
		if (d > 180f) d -= 360f;
		if (d < -180f) d += 360f;
		return d;
	}

	// ------------------------------------------------------------------ bullets

	private Bullet obtain() {
		for (int i = 0; i < pool.size(); i++) if (!pool.get(i).on) return pool.get(i);
		if (pool.size() >= MAX_BULLETS) return null;
		BoxDef d = new BoxDef();
		ItemDef mine = null;
		try {
			mine = item.getProperties();
		} catch (RuntimeException ignored) {
		}
		d.Version = mine != null && mine.Version != null && mine.Version.length() > 0 ? mine.Version : "1";
		d.name = item.getName() + "_bullet_" + GameplaySupport.nextId();
		d.type = "KINEMATIC";
		d.isSensor = true;
		d.Gravity_Scale = 0;
		d.width = bulletSize;
		d.height = bulletSize;
		d.Collider_Width = bulletSize;
		d.Collider_Height = bulletSize;
		d.x = centerX() - bulletSize * 0.5f;
		d.y = centerY() - bulletSize * 0.5f;
		d.z = actor().getZIndex() + 1;
		d.image = bulletImage == null ? "" : bulletImage;
		BoxBody bb = d.build(stage);
		if (d.image.length() == 0) bb.setDrawable(white());
		bb.setColor(bulletColor);
		Bullet b = new Bullet();
		b.body = bb;
		pool.add(b);
		return b;
	}

	private void park(Bullet b) {
		b.on = false;
		if (!alive(b.body)) return;
		b.body.getActor().getColor().a = 0f;
		b.body.getBody().setActive(false);
	}

	private void shootOne() {
		if (body() == null) return;
		Bullet b = obtain();
		if (b == null || !alive(b.body)) return;
		float dir = aim + (spread > 0 ? (float) (Math.random() * 2 - 1) * spread : 0f);
		double r = Math.toRadians(dir);
		float x = centerX() + (float) Math.cos(r) * muzzle, y = centerY() + (float) Math.sin(r) * muzzle;
		Body bd = b.body.getBody();
		bd.setActive(true);
		bd.setTransform(x * W, y * W, (float) r);
		b.body.getActor().getColor().a = bulletColor.a;
		b.vx = (float) Math.cos(r) * bulletSpeed;
		b.vy = (float) Math.sin(r) * bulletSpeed;
		b.life = bulletLifetime;
		b.on = true;
		GameplaySupport.sound(stage, shootSound);
		fire("onShoot");
	}

	private void moveBullets(float dt) {
		for (int i = 0; i < pool.size(); i++) {
			Bullet b = pool.get(i);
			if (!b.on) continue;
			b.life -= dt;
			if (b.life <= 0 || !alive(b.body)) {
				park(b);
				continue;
			}
			float x = cx(b.body) + b.vx * dt, y = cy(b.body) + b.vy * dt;
			b.body.getBody().setTransform(x * W, y * W, b.body.getBody().getAngle());
			boolean gone = false;
			for (int pass = 0; pass < 2 && !gone; pass++) {
				List<PlayerItem> l = pass == 0 ? obstacles : foundWalls;
				for (int k = 0; k < l.size() && !gone; k++) {
					PlayerItem o = l.get(k);
					if (o != null && o != item && alive(o) && !isBullet(o) && overlaps(b.body, o)) gone = true;
				}
			}
			for (int pass = 0; pass < 2 && !gone; pass++) {
				List<PlayerItem> l = pass == 0 ? targets : foundTargets;
				for (int k = 0; k < l.size() && !gone; k++) {
					PlayerItem t = l.get(k);
					if (t == null || t == item || !alive(t) || isBullet(t) || !overlaps(b.body, t)) continue;
					float len = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
					GameplaySupport.hit(t, damage, item, len > 0 ? b.vx / len * knockback : 0, len > 0 ? b.vy / len * knockback : 0, 0, autoHealth);
					GameplaySupport.sound(stage, hitSound);
					fire("onHit", t);
					gone = true;
				}
			}
			if (gone) park(b);
		}
	}

	// ------------------------------------------------------------------ frame

	public void onUpdate(float dt) {
		Body body = body();
		if (body == null) return;
		if (!aimInit) onStart();
		rescan(dt);
		while (fireQueued > 0) {
			fireQueued--;
			shootOne();
		}
		moveBullets(dt);
		if (!active) {
			if (current != null) {
				current = null;
				fire("onTargetLost");
			}
			return;
		}
		PlayerItem t = nearest();
		if (t != current) {
			boolean had = current != null;
			current = t;
			if (t != null) fire("onTargetFound", t);
			else if (had) fire("onTargetLost");
		}
		cooldown -= dt;
		if (t != null) {
			float tx = cx(t), ty = cy(t);
			if (leadTarget && bulletSpeed > 0) {
				float dist = (float) Math.sqrt((tx - centerX()) * (tx - centerX()) + (ty - centerY()) * (ty - centerY()));
				float tt = dist / bulletSpeed;
				tx += GameplaySupport.velX(stage, t) * tt;
				ty += GameplaySupport.velY(stage, t) * tt;
			}
			float want = (float) Math.toDegrees(Math.atan2(ty - centerY(), tx - centerX()));
			if (rotateToTarget) {
				float d = diff(want, aim);
				float step = turnSpeed * dt;
				if (turnSpeed <= 0 || Math.abs(d) <= step) aim = want;
				else aim += Math.signum(d) * step;
				body.setTransform(body.getPosition(), (float) Math.toRadians(aim + imageAngle));
				body.setAngularVelocity(0);
			} else {
				aim = (float) Math.toDegrees(body.getAngle()) - imageAngle;
			}
			boolean aimed = !rotateToTarget || Math.abs(diff(want, aim)) <= Math.max(aimTolerance, 0.01f);
			if (burstLeft <= 0 && aimed && cooldown <= 0) {
				burstLeft = Math.max(1, burst);
				burstTimer = 0;
				cooldown = 1f / Math.max(0.05f, fireRate);
			}
		}
		if (burstLeft > 0) {
			burstTimer -= dt;
			if (burstTimer <= 0) {
				shootOne();
				burstLeft--;
				burstTimer = burstGap;
			}
		}
	}

	public void onDestroy() {
		for (int i = 0; i < pool.size(); i++) if (alive(pool.get(i).body)) pool.get(i).body.destroy();
		pool.clear();
	}
}
