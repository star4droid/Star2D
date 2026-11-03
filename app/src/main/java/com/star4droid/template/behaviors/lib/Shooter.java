package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.graphics.Color;
import com.star4droid.star2d.ElementDefs.BoxDef;
import com.star4droid.star2d.ElementDefs.ItemDef;
import com.star4droid.template.Items.BoxBody;
import com.star4droid.template.Items.Joystick;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Requires;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Image;
import com.star4droid.template.behaviors.Param;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@BehaviorInfo(name = "Shooter", icon = "target", category = "Combat", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onShoot()", "onEnemyHit(PlayerItem enemy)", "onEnemyKilled(PlayerItem enemy)", "onPowerup(PlayerItem powerup)"},
		desc = "Fires bullets with a joystick, a fire button or automatically at the nearest enemy. Enemies take damage, power-ups boost the fire rate and damage.")
@Exclusive("weapon")
@Requires(body = true, note = "Needs a physical element (bullets start at its center).")
public class Shooter extends Behavior {
	@Header("Controls")
	@Param(desc = "Aim and fire by pushing this joystick (optional).")
	public Joystick aimJoystick;
	@Param(desc = "Fires while this element is held (optional).")
	public PlayerItem fireButton;
	@Param(desc = "Fire by itself while an enemy is in range.")
	public boolean autoFire = false;
	@Param(desc = "Aim at the nearest enemy when the joystick is not used.")
	public boolean autoAim = true;
	@Param(desc = "Direction when there is no target: 0 = right, 90 = up. The image mirrored to the left shoots left.", unit = "deg", min = -360, max = 360)
	public float defaultAngle = 0;

	@Header("Bullets")
	@Image
	public String bulletImage = "";
	public Color bulletColor = Color.valueOf("FFD740");
	@Param(unit = "px", min = 2, max = 300)
	public float bulletSize = 16;
	@Param(unit = "px/s", min = 10, max = 100000)
	public float bulletSpeed = 500;
	@Param(desc = "Shots per second.", min = 0.1, max = 60, step = 0.1)
	public float fireRate = 3;
	@Param(desc = "A bullet disappears after this long.", unit = "s", min = 0.1, max = 30)
	public float bulletLifetime = 1.5f;
	@Param(desc = "Enemies farther than this are ignored by auto aim / auto fire.", unit = "px", min = 10, max = 10000)
	public float range = 600;

	@Header("Enemies")
	@Param(desc = "Elements that bullets hurt.")
	public List<PlayerItem> enemies = new ArrayList<PlayerItem>();
	@Param(desc = "Damage of one bullet.", min = 0.1, max = 1000)
	public float damage = 1;
	@Param(desc = "Hit points every enemy starts with.", min = 1, max = 100000)
	public float enemyHealth = 3;
	@Param(desc = "Remove enemies when their health reaches 0.")
	public boolean destroyKilled = true;

	@Header("Power-ups")
	@Param(desc = "Elements that give a boost when the shooter touches them (they disappear).")
	public List<PlayerItem> powerups = new ArrayList<PlayerItem>();
	@Param(desc = "Fire rate is multiplied by this during a boost.", min = 1, max = 10, step = 0.1)
	public float powerFireRate = 2;
	@Param(desc = "Damage is multiplied by this during a boost.", min = 1, max = 10, step = 0.1)
	public float powerDamage = 2;
	@Param(unit = "s", min = 0.5, max = 120)
	public float powerDuration = 8;

	private static class Bullet {
		BoxBody body;
		float vx, vy, life, dmg;
	}

	private final ArrayList<Bullet> bullets = new ArrayList<Bullet>();
	private final HashMap<PlayerItem, Float> health = new HashMap<PlayerItem, Float>();
	private float cooldown, boost;
	private int counter;
	private float lastAngle;

	public void onStart() {
		lastAngle = defaultAngle;
	}

	@Block(label = "shoot now", desc = "Fires one bullet in the current aim direction.", labelAr = "أطلق الآن", descAr = "يطلق رصاصة واحدة في اتجاه التصويب الحالي.", category = "Shooter")
	public void shoot() {
		if (body() != null && stage != null) spawn(lastAngle);
	}

	@Block(label = "add enemy", desc = "Bullets will hurt this element too.", labelAr = "أضف عدواً", descAr = "الرصاص سيؤذي هذا العنصر أيضاً.", category = "Shooter", args = {"enemy"})
	public void addEnemy(PlayerItem enemy) {
		if (enemy != null && !enemies.contains(enemy)) enemies.add(enemy);
	}

	@Block(label = "remove enemy", desc = "Bullets stop hurting this element.", labelAr = "أزل عدواً", descAr = "الرصاص يتوقف عن إيذاء هذا العنصر.", category = "Shooter", args = {"enemy"})
	public void removeEnemy(PlayerItem enemy) {
		enemies.remove(enemy);
	}

	@Block(label = "set fire rate", desc = "Shots per second.", labelAr = "اضبط معدل الإطلاق", descAr = "عدد الطلقات في الثانية.", category = "Shooter", args = {"shots"})
	public void setFireRate(float shotsPerSecond) {
		fireRate = shotsPerSecond;
	}

	@Block(label = "set damage", desc = "Damage of one bullet.", labelAr = "اضبط الضرر", descAr = "ضرر الرصاصة الواحدة.", category = "Shooter", args = {"damage"})
	public void setDamage(float value) {
		damage = value;
	}

	@Block(label = "add power-up", desc = "Touching this element gives the shooter a boost (it disappears).", labelAr = "أضف تعزيزاً", descAr = "لمس هذا العنصر يمنح المطلق تعزيزاً (ثم يختفي).", category = "Shooter", args = {"powerup"})
	public void addPowerUp(PlayerItem powerup) {
		if (powerup != null && !powerups.contains(powerup)) powerups.add(powerup);
	}

	@Block(label = "remove all enemies", desc = "Empties the list of enemies bullets hurt.", labelAr = "أزل كل الأعداء", descAr = "يفرّغ قائمة الأعداء الذين يؤذيهم الرصاص.", category = "Shooter")
	public void clearEnemies() {
		enemies.clear();
	}

	@Block(label = "set bullet speed", desc = "Bullet speed (px/s).", labelAr = "اضبط سرعة الرصاصة", descAr = "سرعة الرصاصة (بكسل/ثانية).", category = "Shooter", args = {"speed"})
	public void setBulletSpeed(float value) {
		bulletSpeed = value;
	}

	@Block(label = "set bullet size", desc = "Bullet size (px).", labelAr = "اضبط حجم الرصاصة", descAr = "حجم الرصاصة (بكسل).", category = "Shooter", args = {"size"})
	public void setBulletSize(float value) {
		bulletSize = value;
	}

	@Block(label = "set shooting range", desc = "Enemies farther than this are ignored by auto aim (px).", labelAr = "اضبط مدى الإطلاق", descAr = "الأعداء الأبعد من هذه المسافة يتجاهلهم التصويب التلقائي (بكسل).", category = "Shooter", args = {"range"})
	public void setRange(float value) {
		range = value;
	}

	@Block(label = "auto fire", desc = "On: fires by itself while an enemy is in range.", labelAr = "إطلاق تلقائي", descAr = "مفعّل: يطلق تلقائياً عند وجود عدو في المدى.", category = "Shooter", args = {"on"})
	public void setAutoFire(boolean on) {
		autoFire = on;
	}

	@Block(label = "auto aim", desc = "On: aims at the nearest enemy.", labelAr = "تصويب تلقائي", descAr = "مفعّل: يصوّب نحو أقرب عدو.", category = "Shooter", args = {"on"})
	public void setAutoAim(boolean on) {
		autoAim = on;
	}

	@Block(label = "fire rate", desc = "Current shots per second.", labelAr = "معدل الإطلاق", descAr = "عدد الطلقات الحالي في الثانية.", category = "Shooter")
	public float getFireRate() {
		return fireRate;
	}

	@Block(label = "damage", desc = "Current damage of one bullet.", labelAr = "الضرر", descAr = "ضرر الرصاصة الواحدة حالياً.", category = "Shooter")
	public float getDamage() {
		return damage;
	}

	private PlayerItem nearest() {
		PlayerItem best = null;
		float bestD = range * range;
		if (enemies == null) return null;
		for (int i = 0; i < enemies.size(); i++) {
			PlayerItem e = enemies.get(i);
			if (!alive(e)) continue;
			float dx = cx(e) - centerX(), dy = cy(e) - centerY(), d = dx * dx + dy * dy;
			if (d <= bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	public void onUpdate(float dt) {
		if (body() == null) return;
		if (boost > 0) boost -= dt;
		collectPowerups();

		// what do we shoot at?
		boolean fire = false;
		float angle = lastAngle;
		boolean pushed = false;
		if (aimJoystick != null) {
			float jx = aimJoystick.getJoyStickX(), jy = aimJoystick.getJoyStickY();
			if (jx * jx + jy * jy > 0.04f) {
				pushed = true;
				fire = true;
				angle = (float) Math.toDegrees(Math.atan2(jy, jx));
			}
		}
		if (isPressed(fireButton)) fire = true;
		PlayerItem target = (!pushed && (autoAim || autoFire)) ? nearest() : null;
		if (target != null) {
			angle = (float) Math.toDegrees(Math.atan2(cy(target) - centerY(), cx(target) - centerX()));
			if (autoFire) fire = true;
		} else if (!pushed) {
			angle = actor().getScaleX() < 0 ? 180 - defaultAngle : defaultAngle;
		}
		lastAngle = angle;

		cooldown -= dt;
		float rate = fireRate * (boost > 0 ? powerFireRate : 1f);
		if (fire && cooldown <= 0 && rate > 0) {
			cooldown = 1f / rate;
			spawn(angle);
		}
		moveBullets(dt);
	}

	private void collectPowerups() {
		if (powerups == null) return;
		for (int i = powerups.size() - 1; i >= 0; i--) {
			PlayerItem p = powerups.get(i);
			if (!alive(p)) continue;
			if (overlaps(item, p)) {
				boost = powerDuration;
				p.getActor().setVisible(false);
				p.destroy();
				fire("onPowerup", p);
			}
		}
	}

	private void spawn(float angle) {
		BoxDef d = new BoxDef();
		ItemDef mine = null;
		try {
			mine = item.getProperties();
		} catch (RuntimeException ignored) {
		}
		d.Version = mine != null && mine.Version != null && mine.Version.length() > 0 ? mine.Version : "1";
		d.name = (item.getName() + "_bullet_" + (counter++));
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
		d.rotation = -angle;
		BoxBody bb = d.build(stage);
		if (d.image.length() == 0) {
			bb.setDrawable(white());
			bb.setColor(bulletColor);
		}
		Bullet b = new Bullet();
		b.body = bb;
		float rad = (float) Math.toRadians(angle);
		b.vx = (float) Math.cos(rad) * bulletSpeed;
		b.vy = (float) Math.sin(rad) * bulletSpeed;
		b.life = bulletLifetime;
		b.dmg = damage * (boost > 0 ? powerDamage : 1f);
		bullets.add(b);
		fire("onShoot");
	}

	private void moveBullets(float dt) {
		for (int i = bullets.size() - 1; i >= 0; i--) {
			Bullet b = bullets.get(i);
			b.life -= dt;
			boolean remove = b.life <= 0 || !alive(b.body);
			if (!remove) {
				float x = cx(b.body) + b.vx * dt, y = cy(b.body) + b.vy * dt;
				b.body.getBody().setTransform(x * W, y * W, b.body.getBody().getAngle());
				if (enemies != null)
					for (int k = 0; k < enemies.size() && !remove; k++) {
						PlayerItem e = enemies.get(k);
						if (!alive(e) || !overlaps(b.body, e)) continue;
						remove = true;
						Float hp = health.get(e);
						float left = (hp == null ? enemyHealth : hp) - b.dmg;
						health.put(e, left);
						fire("onEnemyHit", e);
						if (left <= 0) {
							health.remove(e);
							fire("onEnemyKilled", e);
							if (destroyKilled) e.destroy();
						}
					}
			}
			if (remove) {
				bullets.remove(i);
				if (alive(b.body)) b.body.destroy();
			}
		}
	}

	public void onDestroy() {
		for (int i = 0; i < bullets.size(); i++)
			if (alive(bullets.get(i).body)) bullets.get(i).body.destroy();
		bullets.clear();
	}
}
