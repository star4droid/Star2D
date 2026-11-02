package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.StageImp;
import com.star4droid.template.d3.Light3D;

/** A light of a 3D scene. DIRECTIONAL (sun: only its rotation matters), POINT (bulb), SPOT (flashlight, lamp). */
public class Light3DDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE = "LIGHT3D";
	public String name = "", parentName = "", Script = "", type = "STATIC";
	/** DIRECTIONAL POINT SPOT */
	public String Light_Kind = "POINT";
	public float x = 0, y = 3, z = 0, Rotation_X = -50, Rotation_Y = 30, Rotation_Z = 0;
	public String Color = "#FFFFFF";
	public float Intensity = 1, Range = 10, Cone_Angle = 35, Cone_Softness = 0.3f;
	public boolean Cast_Shadows = false, Visible = true, Active = true;
	/** 0 = steady, 1 = strong flicker (candles, broken lamps). */
	public float Flicker = 0, Flicker_Speed = 8;
	/** Follow this element ("" = none), e.g. a flashlight on the camera. */
	public String Attach_To = "";

	public Light3DDef() {
	}

	public Light3D build(StageImp stage) {
		if (name.equals("")) throw new RuntimeException("Light3DDef error : set name to the item..!!");
		if (parentName != null && parentName.equals("")) parentName = name;
		return Light3D.create(stage, this, elementEvents);
	}

	public Light3DDef getClone(String newName) {
		Light3DDef c = new Light3DDef();
		for (java.lang.reflect.Field f : Light3DDef.class.getFields()) {
			if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || java.lang.reflect.Modifier.isFinal(f.getModifiers())) continue;
			try { f.set(c, f.get(this)); } catch (Exception ignored) { }
		}
		c.name = newName;
		return c;
	}
}
