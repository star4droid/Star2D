package com.star4droid.template.d3;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.star2d.ElementDefs.Light3DDef;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;

import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;
import net.mgsx.gltf.scene3d.lights.PointLightEx;
import net.mgsx.gltf.scene3d.lights.SpotLightEx;

/** A light of the 3D scene (sun, bulb, flashlight). Works in every render mode. */
public class Light3D extends Actor implements PlayerItem {
	public StageImp stage;
	public World3D world;
	Light3DDef def;
	ElementEvent elementEvent;
	ChildsHolder childsHolder = new ChildsHolder(this);
	ItemScript itemScript;

	public final Vector3 position = new Vector3(), direction = new Vector3(0, -1, 0);
	public final Color color = new Color(Color.WHITE);
	public float intensity = 1, range = 10, cone = 35, softness = 0.3f, flicker, flickerSpeed = 8;
	public String type = "POINT";
	public boolean shadows, on = true;
	DirectionalLightEx dir;
	PointLightEx point;
	SpotLightEx spot;
	PlayerItem attach;
	private float flickNow = 1, flickT;
	public final Vector3 euler = new Vector3();

	public Light3D(StageImp s) {
		stage = s;
	}

	public static Light3D create(StageImp stage, Light3DDef def, ElementEvent ev) {
		Light3D l = new Light3D(stage);
		l.def = def;
		l.elementEvent = ev;
		l.world = stage == null ? null : stage.world3d();
		l.setup();
		return l;
	}

	public static Light3D forWorld(World3D w, Light3DDef def) {
		Light3D l = new Light3D(null);
		l.def = def;
		l.world = w;
		l.setup();
		return l;
	}

	private void setup() {
		setName(def.name);
		setSize(0, 0);
		apply();
		if (world != null) world.addLight(this);
		if (stage != null) stage.addActor(this);
		if (stage != null && stage.onCreateCalled()) {
			if (elementEvent != null) elementEvent.onBodyCreated(this);
			if (getScript() != null) getScript().bodyCreated();
		}
	}

	/** Reads the definition again (editor). */
	public void apply() {
		type = def.Light_Type == null ? "POINT" : def.Light_Type.toUpperCase();
		position.set(def.x, def.y, def.z);
		setRotation3(def.Rotation_X, def.Rotation_Y, def.Rotation_Z);
		color.set(Materials3D.color(def.Color, Color.WHITE));
		intensity = def.Intensity;
		range = def.Range;
		cone = def.Cone_Angle;
		softness = def.Cone_Softness;
		shadows = def.Cast_Shadows;
		flicker = def.Flicker;
		flickerSpeed = def.Flicker_Speed;
		on = def.Active;
		setVisible(def.Visible);
	}

	public void setRotation3(float x, float y, float z) {
		euler.set(x, y, z);
		direction.set(0, 0, -1).mul(new Quaternion().setEulerAngles(y, x, z)).nor();
	}

	/** Called every frame by the world; returns the effective intensity (flicker). */
	float frame(float dt) {
		if (attach == null && def.Attach_To != null && def.Attach_To.length() > 0 && stage != null) attach = stage.findItem(def.Attach_To);
		if (attach instanceof Object3D) {
			Object3D o = (Object3D) attach;
			position.set(o.position);
			o.forward(direction);
		} else if (attach instanceof Camera3D) {
			Camera3D c = (Camera3D) attach;
			position.set(c.cam.position);
			direction.set(c.cam.direction);
		}
		if (flicker > 0) {
			flickT -= dt;
			if (flickT <= 0) {
				flickT = 1f / Math.max(0.5f, flickerSpeed) * MathUtils.random(0.4f, 1.4f);
				flickNow = 1f - flicker * MathUtils.random(0f, 1f);
			}
		} else flickNow = 1;
		return intensity * flickNow;
	}

	// ------------------------------------------------------------------ API

	public void setOn(boolean v) {
		on = v;
	}

	public boolean isOn() {
		return on;
	}

	public void toggle() {
		on = !on;
	}

	public void setIntensity(float v) {
		intensity = v;
	}

	public float getIntensity() {
		return intensity;
	}

	public void setLightColor(String hex) {
		color.set(Materials3D.color(hex, Color.WHITE));
	}

	public void setRange(float r) {
		range = r;
	}

	public void setConeAngle(float a) {
		cone = a;
	}

	public void setFlicker(float amount, float speed) {
		flicker = amount;
		flickerSpeed = speed;
	}

	public void setPosition3(float x, float y, float z) {
		position.set(x, y, z);
	}

	public float getX3() {
		return position.x;
	}

	public float getY3() {
		return position.y;
	}

	public float getZ3() {
		return position.z;
	}

	public void attachTo(PlayerItem item) {
		attach = item;
	}

	public void pointAt(float x, float y, float z) {
		direction.set(x, y, z).sub(position).nor();
	}

	// ------------------------------------------------------------------ PlayerItem

	@Override
	public void update() {
		if (getScript() != null) getScript().bodyUpdate();
		else if (elementEvent != null) elementEvent.onBodyUpdate(this);
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		if (!StageImp.secondaryPass) update();
	}

	@Override
	public Body getBody() {
		return null;
	}

	@Override
	public ChildsHolder getChildsHolder() {
		if (childsHolder == null) childsHolder = new ChildsHolder(this);
		return childsHolder;
	}

	@Override
	public void setScript(ItemScript script) {
		itemScript = script;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends ItemScript> T getScript() {
		return (T) itemScript;
	}

	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties() {
		return def;
	}

	public ElementEvent getElementEvents() {
		return elementEvent;
	}

	@Override
	public PlayerItem getClone(String newName) {
		Light3DDef nd = def.getClone(newName);
		nd.x = position.x;
		nd.y = position.y;
		nd.z = position.z;
		return create(stage, nd, elementEvent);
	}

	@Override
	public float getActorX() {
		return position.x;
	}

	@Override
	public float getActorY() {
		return position.y;
	}

	public Light3DDef getDef() {
		return def;
	}

	public void destroy() {
		if (world != null) world.removeLight(this);
		remove();
	}
}
