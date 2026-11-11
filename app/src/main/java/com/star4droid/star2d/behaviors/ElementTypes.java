package com.star4droid.star2d.behaviors;

import java.util.HashMap;
import java.util.LinkedHashMap;

/** Element classes a variable can reference, and how they map to the editor's element types (BOX, TEXT...). */
public final class ElementTypes {
	private ElementTypes() {}

	/** class simple name -> editor TYPE values it accepts (null = every element that is a PlayerItem). */
	private static final LinkedHashMap<String, String[]> CLASSES = new LinkedHashMap<String, String[]>();
	/** editor TYPE -> short title for the picker. */
	public static final HashMap<String, String> TITLES = new HashMap<String, String>();
	/** Class of the inner elements of a UIHolder, and the pseudo editor TYPE the element picker gives them. */
	public static final String UI_NODE = "UINodeRef", UI_NODE_TYPE = "UINODE";

	/** Editor TYPE values that can be a physical element (they own a Box2D body when their "type" is DYNAMIC / KINEMATIC / STATIC). */
	public static final String[] PHYSICAL_TYPES = {"BOX", "CIRCLE", "CUSTOM", "MAP"};
	/** Editor TYPE values that can be a screen-space element (their "type" property is UI). */
	public static final String[] UI_TYPES = {"BOX", "CIRCLE", "CUSTOM", "MAP", "TEXT", "PROGRESS", "JOYSTICK", "PARTICLE", "UIHOLDER", "GROUP"};

	/** Editor TYPE values of the elements of a 3D scene (3D behaviors sit on these; CAMERAVIEW is a 2D / UI element). */
	public static final String[] THREE_D_TYPES = {"OBJ3D", "LIGHT3D", "CAMERA3D"};

	/** An element of the 3D world (OBJ3D, LIGHT3D, CAMERA3D)? */
	public static boolean is3D(String editorType) {
		if (editorType == null) return false;
		for (String t : THREE_D_TYPES) if (t.equals(editorType)) return true;
		return false;
	}

	static {
		CLASSES.put("PlayerItem", null);
		CLASSES.put("BoxBody", new String[] {"BOX"});
		CLASSES.put("CircleItem", new String[] {"CIRCLE"});
		CLASSES.put("CustomBody", new String[] {"CUSTOM"});
		CLASSES.put("TextItem", new String[] {"TEXT"});
		CLASSES.put("ProgressItem", new String[] {"PROGRESS"});
		CLASSES.put("Joystick", new String[] {"JOYSTICK"});
		CLASSES.put("GroupItem", new String[] {"GROUP"});
		CLASSES.put("ParticleItem", new String[] {"PARTICLE"});
		CLASSES.put("CameraItem", new String[] {"CAMERA"});
		CLASSES.put("MapItem", new String[] {"MAP"});
		CLASSES.put("UIHolder", new String[] {"UIHOLDER"});
		CLASSES.put("CameraView", new String[] {"CAMERAVIEW"});
		// path finding grid / multiplayer settings (packages com.star4droid.template.nav / .net)
		CLASSES.put("NavGridItem", new String[] {"NAVGRID"});
		CLASSES.put("NetworkItem", new String[] {"NETWORK"});
		// 3D elements (package com.star4droid.template.d3)
		CLASSES.put("Object3D", new String[] {"OBJ3D"});
		CLASSES.put("Light3D", new String[] {"LIGHT3D"});
		CLASSES.put("Camera3D", new String[] {"CAMERA3D"});
		// an inner element of a UIHolder ("hud/btnPlay"), see com.star4droid.template.ui.UINodeRef
		CLASSES.put(UI_NODE, new String[] {UI_NODE_TYPE});
	}

	public static boolean isElementClass(String simple) {
		return CLASSES.containsKey(simple);
	}

	/** Editor TYPE names accepted by the class, or null for "any element". */
	public static String[] typesOf(String simple) {
		return CLASSES.get(simple);
	}

	public static boolean accepts(String simple, String editorType) {
		String[] t = CLASSES.get(simple);
		if (t == null) return !"LIGHT".equals(editorType) && !UI_NODE_TYPE.equals(editorType);
		for (String s : t) if (s.equals(editorType)) return true;
		return false;
	}

	/** Fully qualified runtime class (used by the generated code). */
	public static String fqn(String simple) {
		if (simple.equals(UI_NODE)) return "com.star4droid.template.ui.UINodeRef";
		if (simple.equals("PlayerItem")) return "com.star4droid.template.Utils.PlayerItem";
		if (simple.equals("Object3D") || simple.equals("Light3D") || simple.equals("Camera3D")) return "com.star4droid.template.d3." + simple;
		if (simple.equals("NavGridItem")) return "com.star4droid.template.nav.NavGridItem";
		if (simple.equals("NetworkItem")) return "com.star4droid.template.net.NetworkItem";
		return "com.star4droid.template.Items." + simple;
	}
}
