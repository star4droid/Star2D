package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.CameraView;
import com.star4droid.template.Items.StageImp;

/**
 * Screen element (2D and 3D scenes) that shows live what a camera sees: a CAMERA (2D) or CAMERA3D element.
 * Touch: touches on the view reach the filmed place (buttons, elements there react).
 */
public class CameraViewDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE = "CAMERAVIEW";
	public String name = "", parentName = "", Script = "", type = "UI";
	public float x = 0, y = 0, z = 100, width = 320, height = 180, rotation = 0;
	/** Name of the camera element to show ("" = the main camera). */
	public String Camera = "";
	/** Extra zoom of the picture (1 = what the camera sees). */
	public float Zoom = 1;
	/** Quality of the picture (0.25..1 of the view size), and how many times per second it is refreshed (0 = every frame). */
	public float Resolution = 1, Refresh_Rate = 0;
	public boolean Touch = false, Visible = true, Active = true;
	public String Border_Color = "#FFFFFF", Background_Color = "#000000";
	public float Border_Width = 2, Corner_Radius = 0, Opacity = 1;

	public CameraViewDef() {
	}

	public CameraView build(StageImp stage) {
		if (name.equals("")) throw new RuntimeException("CameraViewDef error : set name to the item..!!");
		if (parentName != null && parentName.equals("")) parentName = name;
		return CameraView.create(stage, this, elementEvents);
	}

	public CameraViewDef getClone(String newName) {
		CameraViewDef c = new CameraViewDef();
		for (java.lang.reflect.Field f : CameraViewDef.class.getFields()) {
			if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || java.lang.reflect.Modifier.isFinal(f.getModifiers())) continue;
			try { f.set(c, f.get(this)); } catch (Exception ignored) { }
		}
		c.name = newName;
		return c;
	}
}
