package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.StageImp;
import com.star4droid.template.d3.Camera3D;

/**
 * A camera of a 3D scene. The Active one draws the screen; any camera can also feed a CAMERAVIEW element.
 * Follow modes move it after a target element every frame.
 */
public class Camera3DDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE = "CAMERA3D";
	public String name = "", parentName = "", Script = "", type = "STATIC";
	public float x = 0, y = 3, z = 8, Rotation_X = -15, Rotation_Y = 0, Rotation_Z = 0;
	public float FOV = 60, Near = 0.1f, Far = 400, Ortho_Size = 10;
	public boolean Orthographic = false, Active = false, Visible = true;
	/** Target element of the follow mode. */
	public String Follow = "";
	/** NONE THIRD_PERSON FIRST_PERSON ORBIT TOP_DOWN SIDE LOOK_AT */
	public String Follow_Mode = "NONE";
	public float Follow_Distance = 6, Follow_Height = 2.5f, Follow_Smooth = 6, Look_Height = 1, Follow_Angle = 0;
	/** Orbit / first person: drag on the screen turns the camera. */
	public boolean Touch_Look = false;
	public float Look_Sensitivity = 0.25f;
	/** Camera shake strength now (0 = none). */
	public float Shake = 0;

	public Camera3DDef() {
	}

	public Camera3D build(StageImp stage) {
		if (name.equals("")) throw new RuntimeException("Camera3DDef error : set name to the item..!!");
		if (parentName != null && parentName.equals("")) parentName = name;
		return Camera3D.create(stage, this, elementEvents);
	}

	public Camera3DDef getClone(String newName) {
		Camera3DDef c = new Camera3DDef();
		for (java.lang.reflect.Field f : Camera3DDef.class.getFields()) {
			if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || java.lang.reflect.Modifier.isFinal(f.getModifiers())) continue;
			try { f.set(c, f.get(this)); } catch (Exception ignored) { }
		}
		c.name = newName;
		c.Active = false;
		return c;
	}
}
