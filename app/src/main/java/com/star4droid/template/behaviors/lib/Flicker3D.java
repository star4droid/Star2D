package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Light3D;
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Flicker 3D", icon = "candle-flame", category = "Lights", targets = BehaviorInfo.Target.THREE_D, types = {"LIGHT3D"},
		events = {"onOut()"},
		desc = "Makes a 3D light come alive: soft candle flicker, lively fire, a broken lamp that buzzes and goes out, a slow pulse or a strobe. Can drive the glow of a bulb or flame object together with the light.")
public class Flicker3D extends Behavior {
	public enum Style { CANDLE, FIRE, BROKEN, PULSE, STROBE }

	@Header("Style")
	@Param(desc = "CANDLE soft, FIRE lively, BROKEN cuts out now and then, PULSE breathes, STROBE blinks.")
	public Style style = Style.CANDLE;
	@Param(desc = "How strong the changes are (0 = steady, 1 = goes fully dark).", min = 0, max = 1, step = 0.05)
	public float amount = 0.35f;
	@Param(desc = "How fast it changes.", unit = "Hz", min = 0.05, max = 40, step = 0.05)
	public float speed = 6;
	@Param(desc = "BROKEN: average seconds between two cuts.", unit = "s", min = 0.1, max = 60, step = 0.1)
	public float outEvery = 3;

	@Header("Glow")
	@Param(desc = "Bulb, flame or screen object whose glow follows the light (optional).")
	public PlayerItem glowObject;
	@Param(desc = "Glow color of that object.")
	public String glowColor = "#FFB050";
	@Param(desc = "Glow strength at full light.", min = 0, max = 20, step = 0.1)
	public float glowStrength = 2;
	@Param(desc = "BROKEN: buzz / spark sound when it cuts out.")
	@Sound
	public String cutSound = "";

	private float base = -1, cur = 1, from = 1, to = 1, t, out, nextOut, phase;
	private boolean paused;

	@Block(label = "set flicker strength", desc = "0 = steady, 1 = goes fully dark.", labelAr = "اضبط قوة الوميض", descAr = "0 = ثابت، 1 = يظلم تماماً.", category = "Flicker 3D", args = {"amount"})
	public void setAmount(float a) {
		amount = clamp(a, 0, 1);
	}

	@Block(label = "set flicker speed", desc = "Changes per second.", labelAr = "اضبط سرعة الوميض", descAr = "تغيرات في الثانية.", category = "Flicker 3D", args = {"speed"})
	public void setSpeed(float s) {
		speed = Math.max(0.05f, s);
	}

	@Block(label = "pause flicker", desc = "true = steady light.", labelAr = "أوقف الوميض", descAr = "صح = ضوء ثابت.", category = "Flicker 3D", args = {"paused"})
	public void setPaused(boolean on) {
		paused = on;
	}

	@Block(label = "brightness now", desc = "0..1 of the full light.", labelAr = "السطوع الآن", descAr = "0..1 من الضوء الكامل.", category = "Flicker 3D", args = {})
	public float getLevel() {
		return cur;
	}

	@Override
	public void onStart() {
		Light3D l = light3d();
		if (l == null) return;
		base = l.intensity;
		l.setFlicker(0, l.flickerSpeed);
		phase = MathUtils.random(10f);
		nextOut = outEvery * MathUtils.random(0.5f, 1.5f);
	}

	@Override
	public void onDisable() {
		Light3D l = light3d();
		if (l != null && base >= 0) l.setIntensity(base);
		glow(1);
	}

	private void glow(float k) {
		Object3D g = Support3D.o(glowObject);
		if (g != null) g.setEmissive(glowColor, glowStrength * k);
	}

	@Override
	public void onUpdate(float dt) {
		Light3D l = light3d();
		if (l == null || base < 0) return;
		if (paused) {
			cur = 1;
		} else {
			t += dt;
			switch (style) {
				case PULSE:
					cur = 1f - amount * (0.5f + 0.5f * MathUtils.sin((t + phase) * speed * 0.5f * MathUtils.PI2));
					break;
				case STROBE:
					cur = ((int) ((t + phase) * speed * 2)) % 2 == 0 ? 1 : 1 - amount;
					break;
				case BROKEN:
					if (out > 0) {
						out -= dt;
						cur = (MathUtils.randomBoolean(0.15f) ? 0.6f : 0f) * (1 - amount) + (1 - amount) * 0.1f;
						if (out <= 0) nextOut = outEvery * MathUtils.random(0.4f, 1.6f);
						break;
					}
					nextOut -= dt;
					if (nextOut <= 0) {
						out = MathUtils.random(0.08f, 0.9f);
						if (cutSound.length() > 0) Support3D.playAt(cutSound, world3d(), l.position.x, l.position.y, l.position.z, 1, 25, MathUtils.random(0.9f, 1.1f));
						fire("onOut");
					}
					noise(dt, 0.08f);
					break;
				case FIRE:
					noise(dt, amount);
					cur = cur * (1f - 0.15f * amount * MathUtils.random());
					break;
				default:
					noise(dt, amount);
			}
		}
		cur = clamp(cur, 0, 1.2f);
		l.setIntensity(base * cur);
		glow(cur);
	}

	/** Smooth random wandering between levels. */
	private void noise(float dt, float amt) {
		nt += dt * speed;
		if (nt >= 1f) {
			nt -= (float) Math.floor(nt);
			from = to;
			to = 1f - amt * MathUtils.random();
		}
		cur = MathUtils.lerp(from, to, nt * nt * (3 - 2 * nt));
	}

	private float nt;
}
