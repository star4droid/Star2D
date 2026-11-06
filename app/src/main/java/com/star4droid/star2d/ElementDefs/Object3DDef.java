package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.StageImp;
import com.star4droid.template.d3.Object3D;

/**
 * A 3D element (scene type 3D): a ready-made shape, an imported glTF / GLB model, a terrain or a water surface.
 * Units are meters, Y is up, rotations are degrees (applied Y, then X, then Z). Generated code fills the public fields
 * like every other element definition.
 */
public class Object3DDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE = "OBJ3D";
	public String name = "", parentName = "", Script = "", type = "STATIC";

	// ---- what it is
	/** CUBE SPHERE CYLINDER CAPSULE CONE PLANE TORUS RAMP STAIRS PYRAMID HEMISPHERE TUBE WEDGE QUAD DISC, MODEL, TERRAIN, WATER, EMPTY */
	public String Mesh = "CUBE";
	/** MODEL: project path of the .glb / .gltf (e.g. "models/car.glb"). */
	public String Model = "";
	/** MODEL: only this node of the model ("" = all). */
	public String Model_Node = "";
	/** Round shapes: 0 low, 1 normal, 2 high. STAIRS: number of steps. */
	public float Detail = 1, Steps = 6;

	// ---- transform
	public float x = 0, y = 0, z = 0, Rotation_X = 0, Rotation_Y = 0, Rotation_Z = 0, Scale_X = 1, Scale_Y = 1, Scale_Z = 1;
	public boolean Visible = true, Active = true;

	// ---- material
	public String Color = "#FFFFFF", Texture = "", Normal_Map = "", Emissive = "#000000";
	public float Texture_Repeat_X = 1, Texture_Repeat_Y = 1, Opacity = 1, Metallic = 0, Roughness = 0.8f, Emissive_Intensity = 1, Shininess = 16;
	/** Keep the colors / textures of the imported model (false = replace them with Color / Texture). */
	public boolean Keep_Model_Material = true;
	public boolean Unlit = false, Double_Sided = false;

	// ---- rendering
	public boolean Cast_Shadows = true, Receive_Shadows = true, Outline = false, Static_Object = false, Billboard = false;
	public String Outline_Color = "#FFFFFF";
	public float Outline_Width = 0.03f;
	/** Not drawn farther than this (meters, 0 = the quality's view distance). */
	public float Draw_Distance = 0;
	/** Round shapes switch to low detail farther than this (meters, 0 = never). */
	public float LOD_Distance = 0;
	/** Custom shader of the project ("shaders/name", "" = the scene's render mode) and its values "a=1;tint=#ff0000". */
	public String Shader = "", Shader_Params = "";
	/** Drawing order of transparent objects (higher = later). */
	public float Render_Order = 0;

	// ---- physics (Bullet)
	/** NONE STATIC DYNAMIC KINEMATIC TRIGGER */
	public String Body = "NONE";
	/** AUTO BOX SPHERE CAPSULE CYLINDER CONE MESH CONVEX */
	public String Collider = "AUTO";
	public float Mass = 1, Friction = 0.5f, Restitution = 0.1f, Linear_Damping = 0.05f, Angular_Damping = 0.1f, Gravity_Scale = 1;
	public float Collider_Scale_X = 1, Collider_Scale_Y = 1, Collider_Scale_Z = 1, Collider_Offset_X = 0, Collider_Offset_Y = 0, Collider_Offset_Z = 0;
	public boolean Lock_Rotation_X = false, Lock_Rotation_Y = false, Lock_Rotation_Z = false, Lock_Move_X = false, Lock_Move_Y = false, Lock_Move_Z = false;
	public boolean Continuous_Collision = false, Sleep = true;
	/** Collision layer name ("" = default) and the layers it ignores, comma separated. */
	public String Layer = "", Ignore_Layers = "";

	// ---- animation (models)
	/** Clip played at start ("" = none, "*" = the first clip). */
	public String Animation = "";
	public boolean Animation_Loop = true;
	public float Animation_Speed = 1;

	// ---- terrain
	public String Heightmap = "";
	public float Terrain_Height = 4, Terrain_Resolution = 64, Terrain_Noise = 0, Terrain_Seed = 1;

	// ---- water
	public String Water_Color = "#2E8FD8", Water_Deep_Color = "#0B3D6B", Foam_Color = "#FFFFFF";
	public float Wave_Height = 0.25f, Wave_Speed = 1, Wave_Length = 8, Wave_Direction = 30, Water_Clarity = 0.6f, Water_Resolution = 48, Foam = 0.5f;
	/** AUTO (follow the graphics quality), LOW, MEDIUM, HIGH */
	public String Water_Quality = "AUTO";
	/** Elements floating on this water get this much lift (0 = none). */
	public float Buoyancy = 1;

	public Object3DDef() {
	}

	public Object3D build(StageImp stage) {
		if (name.equals("")) throw new RuntimeException("Object3DDef error : set name to the item..!!");
		if (parentName != null && parentName.equals("")) parentName = name;
		return Object3D.create(stage, this, elementEvents);
	}

	public Object3DDef getClone(String newName) {
		Object3DDef c = new Object3DDef();
		copyInto(c);
		c.name = newName;
		c.parentName = (parentName != null && parentName.equals("")) ? parentName : name;
		return c;
	}

	/** Copies every public field (reflection: keeps the clone in sync with new fields). */
	public void copyInto(Object3DDef c) {
		for (java.lang.reflect.Field f : Object3DDef.class.getFields()) {
			if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || java.lang.reflect.Modifier.isFinal(f.getModifiers())) continue;
			try {
				f.set(c, f.get(this));
			} catch (Exception ignored) {
			}
		}
		c.Pivot = Pivot;
		c.Pivot_X = Pivot_X;
		c.Pivot_Y = Pivot_Y;
		c.Version = Version;
		c.elementEvents = ElementEventAdapter.copyOf(elementEvents);
	}
}
