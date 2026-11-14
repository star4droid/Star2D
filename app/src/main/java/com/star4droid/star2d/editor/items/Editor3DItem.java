package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.ElementDefs.Camera3DDef;
import com.star4droid.star2d.ElementDefs.Light3DDef;
import com.star4droid.star2d.ElementDefs.Object3DDef;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.three.Editor3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Light3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

/**
 * Editor element of a 3D scene (OBJ3D, LIGHT3D, CAMERA3D). It is a size-0 actor of the editor (so lists, selection,
 * undo, copy and save work like for 2D elements) holding the PropertySet; the picture is the real runtime object drawn
 * by the editor's 3D viewport ({@link Editor3D}).
 */
public class Editor3DItem extends Actor implements EditorItem {
	PropertySet<String, Object> propertySet;
	final LibgdxEditor editor;
	public String kind;
	public Object3D obj;
	public Light3D light;
	public Camera3D camera;
	private String meshSig = "";

	/** Used by duplicate / copy (the kind comes from the properties' TYPE). */
	public Editor3DItem(LibgdxEditor editor) {
		this(editor, "OBJ3D");
	}

	public Editor3DItem(LibgdxEditor editor, String kind) {
		this.editor = editor;
		this.kind = kind;
		setSize(0, 0);
		editor.addActor(this);
	}

	public static String defaultsFile(String kind) {
		if ("LIGHT3D".equals(kind)) return "light3d.json";
		if ("CAMERA3D".equals(kind)) return "camera3d.json";
		return "obj3d.json";
	}

	@Override
	public PropertySet<String, Object> getPropertySet() {
		return propertySet;
	}

	public Editor3DItem setDefault() {
		propertySet = PropertySet.getDefualt(this, defaultsFile(kind));
		return this;
	}

	@Override
	public void setProperties(PropertySet<String, Object> ps) {
		propertySet = ps;
		if (ps == null) return;
		String t = ps.getString("TYPE");
		if (("OBJ3D".equals(t) || "LIGHT3D".equals(t) || "CAMERA3D".equals(t)) && !t.equals(kind)) {
			detachForKind();
			kind = t;
		}
		PropertySet<String, Object> temp = PropertySet.getDefualt(this, defaultsFile(kind));
		for (String s : temp.keySet()) if (!ps.containsKey(s)) ps.put(s, temp.get(s));
		ArrayList<String> del = new ArrayList<>();
		for (String k : ps.keySet()) if (!temp.containsKey(k)) del.add(k);
		for (String k : del) ps.remove(k);
		update();
	}

	@Override
	public String getTypeName() {
		return kind;
	}

	/** Copies the PropertySet into a definition object (same rules as the code generator). */
	public static void fill(Object def, PropertySet<String, Object> ps) {
		for (Field f : def.getClass().getFields()) {
			if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
			String key = f.getName().replace('_', ' ');
			Object v = ps.get(key);
			if (v == null) v = ps.get(f.getName());
			if (v == null) continue;
			String s = v.toString();
			try {
				Class<?> t = f.getType();
				if (t == float.class) f.setFloat(def, s.isEmpty() ? 0 : Float.parseFloat(s));
				else if (t == boolean.class) f.setBoolean(def, "true".equals(s));
				else if (t == String.class) f.set(def, s.replace(com.star4droid.template.Utils.Utils.seperator, "/"));
			} catch (Exception ignored) {
			}
		}
	}

	private World3D world() {
		Editor3D e = editor.get3D();
		return e == null ? null : e.world;
	}

	@Override
	public void update() {
		if (propertySet == null) return;
		String name = propertySet.getString("name");
		if (!name.isEmpty()) setName(name);
		World3D w = world();
		if (w == null) return;
		try {
			if ("LIGHT3D".equals(kind)) {
				Light3DDef d = light != null ? light.getDef() : new Light3DDef();
				fill(d, propertySet);
				d.name = getName();
				if (light == null) light = Light3D.forWorld(w, d);
				else light.apply();
				light.setVisible(d.Visible && !com.star4droid.star2d.editor.Hierarchy.isHidden(propertySet));
			} else if ("CAMERA3D".equals(kind)) {
				Camera3DDef d = camera != null ? camera.getDef() : new Camera3DDef();
				fill(d, propertySet);
				d.name = getName();
				if (camera == null) camera = Camera3D.forWorld(w, d);
				else camera.apply();
			} else {
				Object3DDef d = obj != null ? obj.getDef() : new Object3DDef();
				fill(d, propertySet);
				d.name = getName();
				String sig = d.Mesh + "|" + d.Model + "|" + d.Model_Node + "|" + d.Detail + "|" + d.Steps + "|" + d.Heightmap + "|" + d.Terrain_Height + "|" + d.Terrain_Resolution
						+ "|" + d.Terrain_Noise + "|" + d.Terrain_Seed + "|" + d.Terrain_Data + "|" + d.Grass_Density + "|" + d.Grass_Height + "|" + d.Grass_Layer + "|" + d.Grass_Color + "|" + d.Grass_Distance + "|" + d.Foliage_Distance + "|" + d.Water_Resolution + "|" + d.Water_Quality + "|" + d.LOD_Distance + "|" + d.Animation + "|" + com.star4droid.template.d3.Assets3D.version(d.Model);
				if (obj == null) {
					obj = Object3D.forWorld(w, d);
					meshSig = sig;
				} else if (!sig.equals(meshSig)) {
					meshSig = sig;
					obj.buildModel();
				} else {
					obj.applyLook();
					if (obj.isWater()) obj.buildModel();
				}
				if (obj.particles != null) {
					obj.particles.setFile(d.Particle_File);
					obj.particles.scale = d.Particle_Scale;
					obj.particles.speed = d.Particle_Speed;
					obj.particles.emitting = d.Emitting;
				}
				obj.setPosition3(d.x, d.y, d.z);
				obj.setRotation3(d.Rotation_X, d.Rotation_Y, d.Rotation_Z);
				obj.setScale3(d.Scale_X, d.Scale_Y, d.Scale_Z);
				obj.setOutline(d.Outline, d.Outline_Color, d.Outline_Width);
				obj.setVisible(d.Visible && !com.star4droid.star2d.editor.Hierarchy.isHidden(propertySet));
			}
		} catch (Throwable t) {
			com.badlogic.gdx.Gdx.app.error("Star2D-3D", "editor item " + getName() + ": " + t);
		}
	}

	/** Position of the element (meters). */
	public float px() {
		return propertySet == null ? 0 : propertySet.getFloat("x");
	}

	public float py() {
		return propertySet == null ? 0 : propertySet.getFloat("y");
	}

	public float pz() {
		return propertySet == null ? 0 : propertySet.getFloat("z");
	}

	private void detachForKind() {
		detach();
	}

	private void detach() {
		World3D w = world();
		if (w != null) {
			if (obj != null) w.remove(obj);
			if (light != null) w.removeLight(light);
			if (camera != null) w.cameras.removeValue(camera, true);
		}
		obj = null;
		light = null;
		camera = null;
		meshSig = "";
	}

	@Override
	protected void setStage(com.badlogic.gdx.scenes.scene2d.Stage stage) {
		super.setStage(stage);
		if (stage == null) detach();
		// back in the editor (undo of a delete): rebuild the runtime object
		else if (obj == null && light == null && camera == null && propertySet != null) update();
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		// drawn by the 3D viewport
	}
}
