package com.star4droid.template.anim;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ObjectVars;
import com.star4droid.template.Utils.PlayerItem;

/**
 * Reads and writes the animatable properties of running elements with the editor's names and units, so a key recorded in
 * the editor lands exactly where it was shown: 2D "x" / "y" = the element's bottom-left corner in editor pixels,
 * "rotation" in editor degrees (clockwise), "Scale X/Y", "Opacity" 0..1, "width" / "height", "Tint", "image", "Text",
 * "Visible", "var:name" (element variables); 3D "x" "y" "z" (meters), "Rotation X/Y/Z", "Scale X/Y/Z", "Color", "Opacity",
 * "Visible".
 */
public final class Props {
	private Props() {
	}

	public static final int NUMBER = 0, COLOR = 1, TEXT = 2, BOOL = 3;

	/** The properties offered by the editor for a 2D element (Timeline > add track). */
	public static final String[] PROPS_2D = {"x", "y", "rotation", "Scale X", "Scale Y", "Opacity", "Tint", "width", "height", "image", "Text", "Visible"};
	/** The properties offered for a 3D element. */
	public static final String[] PROPS_3D = {"x", "y", "z", "Rotation X", "Rotation Y", "Rotation Z", "Scale X", "Scale Y", "Scale Z", "Color", "Opacity", "Visible"};

	public static boolean isVar(String prop) {
		return prop != null && prop.startsWith("var:");
	}

	/** Kind of a property (a "var:" property is a number unless its sample value is not one). */
	public static int kind(String prop, String sample) {
		if (prop == null) return NUMBER;
		if (isVar(prop)) {
			if (sample != null) {
				String s = sample.trim();
				if (s.equals("true") || s.equals("false")) return BOOL;
				try {
					Float.parseFloat(s);
					return NUMBER;
				} catch (NumberFormatException e) {
					return TEXT;
				}
			}
			return NUMBER;
		}
		if (prop.equals("Tint") || prop.equals("Color") || prop.equals("Text Color")) return COLOR;
		if (prop.equals("image") || prop.equals("Text") || prop.equals("animation")) return TEXT;
		if (prop.equals("Visible")) return BOOL;
		return NUMBER;
	}

	/** True for the properties that move a physical element (the element is made kinematic while they play). */
	public static boolean movesBody(String prop) {
		return "x".equals(prop) || "y".equals(prop) || "rotation".equals(prop);
	}

	static boolean is3D(PlayerItem it) {
		return it instanceof com.star4droid.template.d3.Object3D;
	}

	private static boolean isUI(PlayerItem it) {
		try {
			com.star4droid.star2d.ElementDefs.ItemDef d = it.getProperties();
			return d != null && "UI".equals(d.getType());
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Stage units per editor pixel for this element: 1 for screen (UI) elements, 0.25 in the physics world. */
	static float k(PlayerItem it) {
		return isUI(it) ? 1f : StageImp.WORLD_SCALE;
	}

	private static Body body(PlayerItem it) {
		try {
			return it.getBody();
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ numbers

	public static float getNumber(PlayerItem it, String prop) {
		if (it == null) return 0;
		if (isVar(prop)) return ObjectVars.getFloat(it, prop.substring(4));
		if (is3D(it)) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) it;
			if (prop.equals("x")) return o.position.x;
			if (prop.equals("y")) return o.position.y;
			if (prop.equals("z")) return o.position.z;
			if (prop.equals("Rotation X")) return o.euler.x;
			if (prop.equals("Rotation Y") || prop.equals("rotation")) return o.euler.y;
			if (prop.equals("Rotation Z")) return o.euler.z;
			if (prop.equals("Scale X")) return o.scale.x;
			if (prop.equals("Scale Y")) return o.scale.y;
			if (prop.equals("Scale Z")) return o.scale.z;
			if (prop.equals("Opacity")) return o.getOpacity();
			return 0;
		}
		Actor a = it.getActor();
		if (a == null) return 0;
		float k = k(it);
		Body b = body(it);
		if (prop.equals("x")) {
			if (b != null) return (b.getPosition().x - it.getBodyOffset()[0] - a.getWidth() * 0.5f) / k;
			return a.getX() / k;
		}
		if (prop.equals("y")) {
			if (b != null) return (b.getPosition().y - it.getBodyOffset()[1] - a.getHeight() * 0.5f) / k;
			return a.getY() / k;
		}
		if (prop.equals("rotation")) return b != null ? -(float) Math.toDegrees(b.getAngle()) : -a.getRotation();
		if (prop.equals("Scale X")) return a.getScaleX();
		if (prop.equals("Scale Y")) return a.getScaleY();
		if (prop.equals("Opacity") || prop.equals("alpha")) return a.getColor().a;
		if (prop.equals("width")) return a.getWidth() / k;
		if (prop.equals("height")) return a.getHeight() / k;
		return 0;
	}

	public static void setNumber(PlayerItem it, String prop, float v) {
		if (it == null || Float.isNaN(v)) return;
		if (isVar(prop)) {
			ObjectVars.set(it, prop.substring(4), v);
			return;
		}
		if (is3D(it)) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) it;
			if (prop.equals("x")) o.setPosition3(v, o.position.y, o.position.z);
			else if (prop.equals("y")) o.setPosition3(o.position.x, v, o.position.z);
			else if (prop.equals("z")) o.setPosition3(o.position.x, o.position.y, v);
			else if (prop.equals("Rotation X")) o.setRotation3(v, o.euler.y, o.euler.z);
			else if (prop.equals("Rotation Y") || prop.equals("rotation")) o.setRotation3(o.euler.x, v, o.euler.z);
			else if (prop.equals("Rotation Z")) o.setRotation3(o.euler.x, o.euler.y, v);
			else if (prop.startsWith("Scale ")) {
				float sx = o.scale.x, sy = o.scale.y, sz = o.scale.z;
				if (prop.equals("Scale X")) sx = v;
				else if (prop.equals("Scale Y")) sy = v;
				else if (prop.equals("Scale Z")) sz = v;
				// with a physics body the collider is rebuilt only at the end (scale3Done), the picture follows every frame
				if (o.hasBody()) o.scale.set(sx, sy, sz);
				else o.setScale3(sx, sy, sz);
			} else if (prop.equals("Opacity")) o.setOpacity(Math.max(0, Math.min(1, v)));
			return;
		}
		Actor a = it.getActor();
		if (a == null) return;
		float k = k(it);
		Body b = body(it);
		if (prop.equals("x")) {
			if (b != null) {
				float bx = v * k + it.getBodyOffset()[0] + a.getWidth() * 0.5f;
				b.setTransform(bx, b.getPosition().y, b.getAngle());
				b.setAwake(true);
			}
			a.setX(v * k);
		} else if (prop.equals("y")) {
			if (b != null) {
				float by = v * k + it.getBodyOffset()[1] + a.getHeight() * 0.5f;
				b.setTransform(b.getPosition().x, by, b.getAngle());
				b.setAwake(true);
			}
			a.setY(v * k);
		} else if (prop.equals("rotation")) {
			if (b != null) b.setTransform(b.getPosition(), (float) Math.toRadians(-v));
			a.setRotation(-v);
		} else if (prop.equals("Scale X")) a.setScaleX(v);
		else if (prop.equals("Scale Y")) a.setScaleY(v);
		else if (prop.equals("Opacity") || prop.equals("alpha")) a.getColor().a = Math.max(0, Math.min(1, v));
		else if (prop.equals("width")) a.setWidth(Math.max(0, v * k));
		else if (prop.equals("height")) a.setHeight(Math.max(0, v * k));
	}

	/** 3D scale tracks: the collider follows the new size once the animation ends. */
	static void scale3Done(PlayerItem it) {
		if (is3D(it)) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) it;
			if (o.hasBody()) o.setScale3(o.scale.x, o.scale.y, o.scale.z);
		}
	}

	// ------------------------------------------------------------------ colors

	public static Color getColor(PlayerItem it, String prop, Color out) {
		if (out == null) out = new Color();
		if (it == null) return out.set(Color.WHITE);
		if (is3D(it)) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) it;
			if (o.lastColor != null) return out.set(o.lastColor);
			return out.set(com.star4droid.template.d3.Materials3D.color(o.getDef().Color, Color.WHITE));
		}
		Actor a = it.getActor();
		return a == null ? out.set(Color.WHITE) : out.set(a.getColor());
	}

	public static void setColor(PlayerItem it, String prop, Color c) {
		if (it == null || c == null) return;
		if (is3D(it)) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) it;
			if (o.lastColor == null) o.lastColor = new Color();
			o.lastColor.set(c);
			o.setColor3(c);
			return;
		}
		Actor a = it.getActor();
		if (a == null) return;
		// the alpha belongs to "Opacity": the tint changes red / green / blue only
		float alpha = a.getColor().a;
		a.setColor(c.r, c.g, c.b, alpha);
	}

	public static Color parseColor(String s, Color out) {
		if (out == null) out = new Color();
		if (s == null) return out.set(Color.WHITE);
		String h = s.trim().replace("#", "");
		try {
			if (h.length() == 6) h += "FF";
			return out.set(Color.valueOf(h));
		} catch (RuntimeException e) {
			return out.set(Color.WHITE);
		}
	}

	// ------------------------------------------------------------------ text / yes-no

	public static String getText(PlayerItem it, String prop) {
		if (it == null) return "";
		if (isVar(prop)) return ObjectVars.getString(it, prop.substring(4));
		return "";
	}

	public static void setText(StageImp stage, PlayerItem it, String prop, String v) {
		if (it == null || v == null) return;
		if (isVar(prop)) {
			ObjectVars.set(it, prop.substring(4), v);
			return;
		}
		if (prop.equals("Text")) it.setItemText(v);
		else if (prop.equals("image")) {
			if (is3D(it)) ((com.star4droid.template.d3.Object3D) it).setTexture(v);
			else if (stage != null) stage.setImage(it, v);
		} else if (prop.equals("animation")) {
			if (is3D(it)) ((com.star4droid.template.d3.Object3D) it).playAnimation(v, true, 1f);
			else it.setAnimation(v);
		}
	}

	public static boolean getBool(PlayerItem it, String prop) {
		if (it == null) return false;
		if (isVar(prop)) return ObjectVars.getBool(it, prop.substring(4));
		if (is3D(it)) return ((com.star4droid.template.d3.Object3D) it).isVisible();
		Actor a = it.getActor();
		return a != null && a.isVisible();
	}

	public static void setBool(PlayerItem it, String prop, boolean v) {
		if (it == null) return;
		if (isVar(prop)) {
			ObjectVars.set(it, prop.substring(4), v);
			return;
		}
		if (prop.equals("Visible")) {
			if (is3D(it)) ((com.star4droid.template.d3.Object3D) it).setVisible3(v);
			else if (it.getActor() != null) it.getActor().setVisible(v);
		}
	}
}
