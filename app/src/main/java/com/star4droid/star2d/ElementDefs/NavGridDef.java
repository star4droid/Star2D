package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.StageImp;
import com.star4droid.template.nav.NavGridItem;

/**
 * A navigation grid (path finding area). 2D scenes: x / y / width / height in editor pixels (bottom-left corner).
 * 3D scenes: a flat area on the ground: x / z = its corner with the smallest x and z, width along x and height along z
 * (meters), y = the height of the ground it lies on. "Cells" holds the painted walls and costs (NavGrid.encode).
 */
public class NavGridDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE = "NAVGRID";
	public String name = "", parentName = "", Script = "", type = "WORLD";
	public float x = 0, y = 0, z = 0, width = 640, height = 480, rotation = 0;
	/** SQUARE or HEX. */
	public String Cell_Shape = "SQUARE";
	public float Cell_Width = 32, Cell_Height = 32;
	/** Diameter of the walking elements: paths keep this far from walls (0 = may touch them). */
	public float Agent_Size = 0;
	public boolean Diagonal = true, Cut_Corners = false;
	/** Every static body of the scene blocks the cells it covers (on top of the painted walls). */
	public boolean Use_Static_Bodies = false;
	/** Draws the grid while the game runs (to check it). */
	public boolean Show_In_Game = false, Visible = true;
	/** Elements (or groups) that block their cells and follow them when they move: "(door)(crate)". */
	public String Obstacles = "";
	/** Painted walls / costs, NavGrid.encode(). */
	public String Cells = "";

	public NavGridDef() {
	}

	public NavGridItem build(StageImp stage) {
		if (name.equals("")) throw new RuntimeException("NavGridDef error : set name to the item..!!");
		if (parentName != null && parentName.equals("")) parentName = name;
		return NavGridItem.create(stage, this, elementEvents);
	}

	public NavGridDef getClone(String newName) {
		NavGridDef c = new NavGridDef();
		for (java.lang.reflect.Field f : NavGridDef.class.getFields()) {
			if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || java.lang.reflect.Modifier.isFinal(f.getModifiers())) continue;
			try {
				f.set(c, f.get(this));
			} catch (Exception ignored) {
			}
		}
		c.name = newName;
		return c;
	}
}
