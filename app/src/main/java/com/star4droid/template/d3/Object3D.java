package com.star4droid.template.d3;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.star2d.ElementDefs.Object3DDef;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;

import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * A 3D element. It is an (invisible, size 0) scene2d Actor of the scene so the engine treats it like every element:
 * findItem, scripts, behaviors, element events, children, clones. The 3D world ({@link World3D}) draws it.
 *
 * Every public method here is safe from generated code / blocks (Java 7, no Bullet / glTF types in the signatures).
 */
public class Object3D extends Actor implements PlayerItem {
	public StageImp stage;
	public World3D world;
	Object3DDef def;
	ElementEvent elementEvent;
	ChildsHolder childsHolder = new ChildsHolder(this);
	ItemScript itemScript;

	public ModelInstance instance, lowInstance;
	public AnimationController anim;
	public final Vector3 position = new Vector3(), scale = new Vector3(1, 1, 1);
	public final Quaternion rotation = new Quaternion();
	/** Mesh PARTICLES: the 3D particle effect of this element (null for the other meshes). */
	public Particles3D particles;
	/** Last rotation set in degrees (x, y, z) - what the blocks read back. */
	public final Vector3 euler = new Vector3();
	public final Matrix4 transform = new Matrix4();
	boolean transformDirty = true;
	btRigidBody rigid;
	Physics3D.Motion motion;
	/** Collision shapes made for the current body (freed with it). */
	final com.badlogic.gdx.utils.Array<com.badlogic.gdx.utils.Disposable> bodyShapes = new com.badlogic.gdx.utils.Array<com.badlogic.gdx.utils.Disposable>();
	int bodyId;
	Water3D.WaterAttribute water;
	UserShader.Program program;
	Terrain3D terrain;

	/** Engine / editor: the terrain of a TERRAIN element (null otherwise). */
	public Terrain3D terrain() {
		return terrain;
	}
	public boolean outline;
	public final Color outlineColor = new Color(Color.WHITE);
	public float outlineWidth = 0.03f;
	/** Set by the editor: drawn with the white selection stroke. */
	public boolean selected;
	private final BoundingBox bounds = new BoundingBox();
	private boolean boundsDone;
	/** Radius of the element (meters, for culling). */
	float radius = 1;
	// follow a 3D parent
	private Object3D parent3d;
	private final Matrix4 offsetFromParent = new Matrix4();
	private boolean destroyed;
	private boolean started;
	String currentAnim = "";
	float animSpeed = 1;
	boolean animPaused;

	public Object3D(StageImp stage) {
		this.stage = stage;
	}

	public static Object3D create(StageImp stage, Object3DDef def, ElementEvent ev) {
		Object3D o = new Object3D(stage);
		o.elementEvent = ev;
		o.def = def;
		o.world = stage == null ? null : stage.world3d();
		o.setup();
		return o;
	}

	/** Editor / tools: an element in a given world, without a scene. */
	public static Object3D forWorld(World3D w, Object3DDef def) {
		Object3D o = new Object3D(null);
		o.def = def;
		o.world = w;
		o.setup();
		return o;
	}

	public Object3DDef d() {
		return def;
	}

	// ------------------------------------------------------------------ particles (Mesh PARTICLES)

	/** Shows another .p3d effect ("/fx3d/fire/campfire.p3d"; "" = built in fire). Other meshes become a particle effect. */
	public void setParticleFile(String file) {
		def.Particle_File = file == null ? "" : file;
		if (!"PARTICLES".equals(def.Mesh)) {
			def.Mesh = "PARTICLES";
			buildModel();
		} else if (particles != null) particles.setFile(def.Particle_File);
	}

	/** Starts / stops new particles (the ones alive finish their life). */
	public void setEmitting(boolean on) {
		def.Emitting = on;
		if (particles != null) particles.emitting = on;
	}

	public boolean isEmitting() {
		return particles != null && particles.emitting;
	}

	/** Plays the effect again from its start (bursts, explosions). */
	public void restartParticles() {
		if (particles != null) {
			particles.emitting = true;
			def.Emitting = true;
			particles.restart();
		}
	}

	/** Size of the whole effect (1 = as made) and its time speed. */
	public void setParticleScale(float s) {
		def.Particle_Scale = s;
		if (particles != null) particles.scale = s;
	}

	public void setParticleSpeed(float s) {
		def.Particle_Speed = s;
		if (particles != null) particles.speed = s;
	}

	/** A one shot effect ended (no looping emitter left and no particle alive). */
	public boolean particlesFinished() {
		return particles == null || particles.isComplete();
	}

	// ------------------------------------------------------------------ build

	private void setup() {
		setName(def.name);
		setSize(0, 0);
		position.set(def.x, def.y, def.z);
		scale.set(def.Scale_X, def.Scale_Y, def.Scale_Z);
		setEuler(def.Rotation_X, def.Rotation_Y, def.Rotation_Z);
		setVisible(def.Visible);
		outline = def.Outline;
		outlineColor.set(Materials3D.color(def.Outline_Color, Color.WHITE));
		outlineWidth = def.Outline_Width;
		buildModel();
		if (world != null) world.add(this);
		if (stage != null) stage.addActor(this);
		if (world != null && world.physicsAllowed() && !"NONE".equals(def.Body) && def.Active) createBody();
		if (stage != null && stage.onCreateCalled()) {
			if (elementEvent != null) elementEvent.onBodyCreated(this);
			if (getScript() != null) getScript().bodyCreated();
		}
	}

	/** (Re)builds the model and its look from the definition (editor: after a property change). */
	public void buildModel() {
		Terrain3D oldTerrain = terrain;
		instance = null;
		lowInstance = null;
		anim = null;
		water = null;
		terrain = null;
		boundsDone = false;
		String shape = def.Mesh == null ? Shapes3D.CUBE : def.Mesh.toUpperCase();
		if (!"PARTICLES".equals(shape)) particles = null;
		int detail = (int) def.Detail;
		if ("MODEL".equals(shape)) {
			SceneAsset a = null;
			try {
				a = Assets3D.model(def.Model);
			} catch (Throwable t) {
				com.badlogic.gdx.Gdx.app.error("Star2D-3D", "model " + def.Model + ": " + t);
			}
			if (a != null) {
				Model m = a.scene.model;
				if (def.Model_Node != null && def.Model_Node.length() > 0 && m.getNode(def.Model_Node, true) != null)
					instance = new ModelInstance(m, def.Model_Node, true, true, true);
				else instance = new ModelInstance(m);
				if (m.animations.size > 0) {
					anim = new AnimationController(instance);
					anim.allowSameAnimation = true;
				}
			} else instance = new ModelInstance(Shapes3D.get(Shapes3D.CUBE, 0, 0)); // missing file: a placeholder cube
		} else if ("TERRAIN".equals(shape)) {
			terrain = Terrain3D.build(def, world != null && world.editor);
			instance = new ModelInstance(terrain.model);
			if (terrain.foliage != null) terrain.foliage.owner = this;
		} else if ("WATER".equals(shape)) {
			int res = (int) def.Water_Resolution;
			String q = def.Water_Quality == null ? "AUTO" : def.Water_Quality;
			if ("LOW".equals(q)) res = Math.min(res, 8);
			instance = new ModelInstance(Water3D.grid(res));
			water = new Water3D.WaterAttribute();
			water.shallow.set(Materials3D.color(def.Water_Color, water.shallow));
			water.deep.set(Materials3D.color(def.Water_Deep_Color, water.deep));
			water.foam.set(Materials3D.color(def.Foam_Color, Color.WHITE));
			water.height = def.Wave_Height;
			water.speed = def.Wave_Speed;
			water.length = def.Wave_Length;
			water.direction = def.Wave_Direction;
			water.clarity = def.Water_Clarity;
			water.foamAmount = def.Foam;
			water.quality = "LOW".equals(q) ? 0 : ("MEDIUM".equals(q) ? 1 : ("HIGH".equals(q) ? 2 : -1));
			for (Material m : instance.materials) {
				m.set(water);
				m.set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, 0.9f)); // drawn after the solid elements
			}
		} else if ("EMPTY".equals(shape)) {
			instance = null;
		} else if ("PARTICLES".equals(shape)) {
			instance = null;
			if (particles == null) particles = new Particles3D();
			particles.setFile(def.Particle_File);
			particles.scale = def.Particle_Scale;
			particles.speed = def.Particle_Speed;
			particles.emitting = def.Emitting;
		} else {
			if (!Shapes3D.isShape(shape)) shape = Shapes3D.CUBE;
			instance = new ModelInstance(Shapes3D.get(shape, detail, (int) def.Steps));
			if (def.LOD_Distance > 0 && detail > 0) lowInstance = new ModelInstance(Shapes3D.get(shape, 0, (int) def.Steps));
		}
		if (oldTerrain != null && oldTerrain.model != null) {
			// a MESH body reads the old terrain's vertices: remove it before freeing them (setShape / setModel rebuild it)
			if (rigid != null && world != null) world.physics().removeBody(this);
			oldTerrain.dispose();
		}
		applyLook();
		if (anim != null && def.Animation != null && def.Animation.length() > 0)
			playAnimation(def.Animation, def.Animation_Loop, def.Animation_Speed);
		transformDirty = true;
	}

	/** The model could not be drawn (shader / mesh error): show a plain box instead, once (no rebuild every frame). */
	public void markBroken() {
		try {
			instance = new ModelInstance(Shapes3D.get(Shapes3D.CUBE, 0, 0));
			lowInstance = null;
			anim = null;
			boundsDone = false;
			transformDirty = true;
		} catch (Throwable t) {
			instance = null;
		}
	}

	/** Frees what this element owns alone (the terrain mesh). Its body must be gone already. */
	void disposeOwned() {
		if (terrain != null) {
			try {
				terrain.dispose();
			} catch (Throwable ignored) {
			}
		}
	}

	/** Material from the definition (color, texture, opacity...). */
	public void applyLook() {
		if (instance == null || water != null) return;
		if (terrain != null && terrain.painted) {
			terrain.applyMaterial(instance, def);
			program = null;
			return;
		}
		Materials3D.Look look = new Materials3D.Look();
		look.color = Materials3D.color(def.Color, Color.WHITE);
		look.texture = Assets3D.texture(def.Texture, def.Texture_Repeat_X != 1 || def.Texture_Repeat_Y != 1);
		look.normalMap = Assets3D.texture(def.Normal_Map, def.Texture_Repeat_X != 1 || def.Texture_Repeat_Y != 1);
		look.repeatX = def.Texture_Repeat_X;
		look.repeatY = def.Texture_Repeat_Y;
		look.opacity = def.Opacity;
		look.metallic = def.Metallic;
		look.roughness = def.Roughness;
		look.emissive = Materials3D.color(def.Emissive, Color.BLACK);
		look.emissiveIntensity = def.Emissive_Intensity;
		look.shininess = def.Shininess;
		String shape = def.Mesh == null ? "" : def.Mesh.toUpperCase();
		look.doubleSided = def.Double_Sided || Shapes3D.TORUS.equals(shape) || Shapes3D.QUAD.equals(shape) || Shapes3D.DISC.equals(shape) || Shapes3D.PLANE.equals(shape);
		boolean model = "MODEL".equals(shape);
		look.tintModel = model && !"#FFFFFF".equalsIgnoreCase(def.Color) && !"#FFFFFFFF".equalsIgnoreCase(def.Color);
		Materials3D.apply(instance, look, !model || !def.Keep_Model_Material);
		if (lowInstance != null) Materials3D.apply(lowInstance, look, true);
		if (def.Shader != null && def.Shader.length() > 0) setShader(def.Shader, def.Shader_Params);
		else program = null;
	}

	// ------------------------------------------------------------------ physics

	public boolean isStaticBody() {
		return "STATIC".equals(def.Body) || "TRIGGER".equals(def.Body) || "KINEMATIC".equals(def.Body);
	}

	void createBody() {
		if (world == null || instance == null && !"EMPTY".equals(def.Mesh)) {
			if (world == null) return;
		}
		try {
			Physics3D p = world.physics();
			if (rigid != null) p.removeBody(this);
			sync();
			rigid = p.createBody(this);
		} catch (Throwable t) {
			com.badlogic.gdx.Gdx.app.error("Star2D-3D", "body of " + def.name + ": " + t);
		}
	}

	public boolean hasBody() {
		return rigid != null;
	}

	/** Engine: bullet rigid body (null when none). */
	public btRigidBody rigidBody() {
		return rigid;
	}

	// ------------------------------------------------------------------ frame

	/** Local bounds of the model (before scale). */
	public BoundingBox localBounds() {
		if (!boundsDone) {
			if (instance != null) {
				// skinned characters: measured as drawn (through the bones), not in their bind space
				if (!(SkinBounds.skinned(instance) && SkinBounds.calculate(instance, bounds))) instance.calculateBoundingBox(bounds);
			} else bounds.set(new Vector3(-0.5f, -0.5f, -0.5f), new Vector3(0.5f, 0.5f, 0.5f));
			if (!bounds.isValid() || bounds.getDimensions(new Vector3()).isZero()) bounds.set(new Vector3(-0.5f, -0.5f, -0.5f), new Vector3(0.5f, 0.5f, 0.5f));
			boundsDone = true;
		}
		return bounds;
	}

	/** Collider shape after AUTO: BOX, SPHERE, CAPSULE, CYLINDER, CONE, MESH or CONVEX (same rule as the physics). */
	public String colliderKind() {
		String c = def.Collider == null ? "AUTO" : def.Collider;
		if (!"AUTO".equals(c)) return c;
		String shape = def.Mesh;
		if (Shapes3D.SPHERE.equals(shape) || Shapes3D.HEMISPHERE.equals(shape)) return "SPHERE";
		if (Shapes3D.CAPSULE.equals(shape)) return "CAPSULE";
		if (Shapes3D.CYLINDER.equals(shape) || Shapes3D.DISC.equals(shape) || Shapes3D.TUBE.equals(shape)) return "CYLINDER";
		if (Shapes3D.CONE.equals(shape)) return "CONE";
		if (Shapes3D.RAMP.equals(shape) || Shapes3D.STAIRS.equals(shape) || Shapes3D.PYRAMID.equals(shape) || Shapes3D.WEDGE.equals(shape) || Shapes3D.TORUS.equals(shape))
			return isStaticBody() ? "MESH" : "CONVEX";
		if ("TERRAIN".equals(shape)) return "MESH";
		if ("MODEL".equals(shape)) return isStaticBody() ? "MESH" : "BOX";
		return "BOX";
	}

	/**
	 * Collider box in the element's local (rotated, unscaled) space, meters: half sizes and center. Spheres / capsules /
	 * cylinders / cones fill it the way the physics builds them (editor drawing and the collider editor).
	 */
	public void colliderBox(Vector3 half, Vector3 center) {
		BoundingBox bb = localBounds();
		float sx = Math.abs(scale.x) * def.Collider_Scale_X, sy = Math.abs(scale.y) * def.Collider_Scale_Y, sz = Math.abs(scale.z) * def.Collider_Scale_Z;
		half.set(bb.getWidth() * sx * 0.5f, bb.getHeight() * sy * 0.5f, bb.getDepth() * sz * 0.5f);
		bb.getCenter(center);
		center.scl(sx, sy, sz);
		center.add(def.Collider_Offset_X, def.Collider_Offset_Y, def.Collider_Offset_Z);
	}

	private final Vector3 tmp = new Vector3();

	/** Called by the world before drawing: transform, parent, animation. */
	void sync() {
		// Static Object: scenery that never moves keeps its matrices (no work per frame) until something moves it
		if (started && !transformDirty && def.Static_Object && (rigid == null || isStaticBody())) {
			if (parent3d == null) return;
			// static scenery in a static group: only when the group itself moved
			if (!parent3d.destroyed && parent3d.def.Static_Object) {
				parent3d.sync();
				if (parent3d.syncVersion == seenParentVersion) return;
			}
		}
		if (!started && world != null) {
			started = true;
			PlayerItem p = getParentItem();
			if (p instanceof Object3D && p != this) attachTo((Object3D) p);
		}
		if (parent3d != null && !parent3d.destroyed) {
			parent3d.sync();
			seenParentVersion = parent3d.syncVersion;
			transform.set(parent3d.transform).mul(offsetFromParent);
			transform.getTranslation(position);
			transform.getRotation(rotation, true);
			transform.getScale(scale);
			if (rigid != null && isStaticBody()) {
				rigid.setWorldTransform(tmpM.set(position, rotation));
				rigid.activate();
			}
		} else {
			// a 0 scale (pop-in tweens, a flat 0 axis) makes the matrix singular: the shaders' normal matrix and
			// inv() throw "Can't invert a singular matrix" / "non-invertible matrix"
			transform.set(position, rotation, safeScale(scale, tmpScale));
		}
		if (instance != null) instance.transform.set(transform);
		if (lowInstance != null) lowInstance.transform.set(transform);
		BoundingBox b = localBounds();
		float sx = Math.abs(scale.x), sy = Math.abs(scale.y), sz = Math.abs(scale.z);
		radius = 0.5f * b.getDimensions(tmp).scl(sx, sy, sz).len() + 0.01f;
		transformDirty = false;
		syncVersion++;
	}

	/** Grows each time the matrices were recomputed; children of a static group compare it to skip their own work. */
	private int syncVersion, seenParentVersion = -1;

	private final Matrix4 tmpM = new Matrix4();
	private final Vector3 tmpScale = new Vector3();

	static Vector3 safeScale(Vector3 s, Vector3 out) {
		return out.set(safe(s.x), safe(s.y), safe(s.z));
	}

	private static float safe(float v) {
		return Math.abs(v) >= 1e-4f ? v : (v < 0 ? -1e-4f : 1e-4f);
	}

	/** Keeps following another element (keeping the current offset). null = stop. */
	public void attachTo(Object3D parent) {
		if (parent == null || parent == this) {
			parent3d = null;
			return;
		}
		parent.sync();
		transform.set(position, rotation, safeScale(scale, tmpScale));
		offsetFromParent.set(parent.transform).inv().mul(transform);
		parent3d = parent;
	}

	public void detach3D() {
		parent3d = null;
	}

	void updateAnimation(float dt) {
		if (anim != null && !animPaused) {
			try {
				anim.update(dt * animSpeed);
			} catch (Throwable ignored) {
			}
		}
	}

	/** Center of the element in the world. */
	public Vector3 center(Vector3 out) {
		localBounds().getCenter(out);
		out.mul(transform);
		return out;
	}

	// ------------------------------------------------------------------ PlayerItem

	@Override
	public void update() {
		if (getScript() != null) getScript().bodyUpdate();
		else if (elementEvent != null) elementEvent.onBodyUpdate(this);
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		// drawn by World3D; the 2D pass only runs the element's update (scripts, behaviors), like every element
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

	public Object3D setElementEvent(ElementEvent e) {
		elementEvent = e;
		return this;
	}

	@Override
	public PlayerItem getClone(String newName) {
		Object3DDef nd = def.getClone(newName);
		nd.x = position.x;
		nd.y = position.y;
		nd.z = position.z;
		nd.Rotation_X = euler.x;
		nd.Rotation_Y = euler.y;
		nd.Rotation_Z = euler.z;
		nd.Scale_X = scale.x;
		nd.Scale_Y = scale.y;
		nd.Scale_Z = scale.z;
		Object3D item = create(stage, nd, elementEvent);
		if (getScript() != null) {
			try {
				ItemScript script = (ItemScript) (getScript().getClass().getConstructor(PlayerItem.class).newInstance(item));
				script.setItem(item).setStage(stage);
				item.setScript(script);
				script.cloneBehaviorsFrom(getScript());
			} catch (Exception ignored) {
			}
		}
		return item;
	}

	@Override
	public float getActorX() {
		return position.x;
	}

	@Override
	public float getActorY() {
		return position.y;
	}

	@Override
	public float getBodyX() {
		return position.x;
	}

	@Override
	public float getBodyY() {
		return position.y;
	}

	@Override
	public void setTint(Color color) {
		setColor3(color);
	}

	@Override
	public void setTint(String hex) {
		setColor(hex);
	}

	@Override
	public float distToPoint(float x, float y) {
		return Vector3.dst(position.x, position.y, 0, x, y, 0);
	}

	@Override
	public float distTo(PlayerItem item) {
		if (item instanceof Object3D) return position.dst(((Object3D) item).position);
		return distToPoint(item.getActorX(), item.getActorY());
	}

	// ------------------------------------------------------------------ API: transform

	public float getX3() {
		return position.x;
	}

	public float getY3() {
		return position.y;
	}

	public float getZ3() {
		return position.z;
	}

	public void setPosition3(float x, float y, float z) {
		position.set(x, y, z);
		pushTransform();
	}

	public void setX3(float v) {
		setPosition3(v, position.y, position.z);
	}

	public void setY3(float v) {
		setPosition3(position.x, v, position.z);
	}

	public void setZ3(float v) {
		setPosition3(position.x, position.y, v);
	}

	public void move(float dx, float dy, float dz) {
		setPosition3(position.x + dx, position.y + dy, position.z + dz);
	}

	/** Moves along the element's own axes: forward (-Z of the element), right (+X), up (+Y). */
	public void moveLocal(float forward, float right, float up) {
		Vector3 f = forward(new Vector3()).scl(forward);
		Vector3 r = right(new Vector3()).scl(right);
		Vector3 u = up(new Vector3()).scl(up);
		setPosition3(position.x + f.x + r.x + u.x, position.y + f.y + r.y + u.y, position.z + f.z + r.z + u.z);
	}

	public Vector3 forward(Vector3 out) {
		return out.set(0, 0, -1).mul(rotation);
	}

	public Vector3 right(Vector3 out) {
		return out.set(1, 0, 0).mul(rotation);
	}

	public Vector3 up(Vector3 out) {
		return out.set(0, 1, 0).mul(rotation);
	}

	public float getForwardX() {
		return forward(tmp).x;
	}

	public float getForwardY() {
		return forward(tmp).y;
	}

	public float getForwardZ() {
		return forward(tmp).z;
	}

	private void setEuler(float x, float y, float z) {
		euler.set(x, y, z);
		rotation.setEulerAngles(y, x, z);
	}

	public void setRotation3(float x, float y, float z) {
		setEuler(x, y, z);
		pushTransform();
	}

	public float getRotationX3() {
		return euler.x;
	}

	public float getRotationY3() {
		return euler.y;
	}

	public float getRotationZ3() {
		return euler.z;
	}

	/** Adds degrees to the rotation (world axes Y then X then Z, like the properties). */
	public void rotate3(float dx, float dy, float dz) {
		setRotation3(euler.x + dx, euler.y + dy, euler.z + dz);
	}

	/** Turns around its own axes (degrees). */
	public void turnLocal(float pitch, float yaw, float roll) {
		Quaternion q = new Quaternion().setEulerAngles(yaw, pitch, roll);
		rotation.mul(q);
		syncEulerFromRotation();
		pushTransform();
	}

	void syncEulerFromRotation() {
		euler.set(rotation.getPitch(), rotation.getYaw(), rotation.getRoll());
	}

	/** Turns so its front (-Z) looks at a point. */
	public void lookAt(float x, float y, float z) {
		Vector3 dir = new Vector3(x, y, z).sub(position);
		if (dir.isZero(0.0001f)) return;
		dir.nor();
		// straight up / down: Y can't be the up vector (NaN rotation)
		Matrix4 m = new Matrix4().setToLookAt(dir, Math.abs(dir.y) > 0.999f ? Vector3.Z : Vector3.Y);
		m.inv();
		m.getRotation(rotation, true);
		syncEulerFromRotation();
		pushTransform();
	}

	/** Only turns around Y (stays upright) to face a point. */
	public void faceTowards(float x, float z) {
		float yaw = MathUtils.atan2(-(x - position.x), -(z - position.z)) * MathUtils.radiansToDegrees;
		setRotation3(0, yaw, 0);
	}

	public void lookAtItem(PlayerItem item) {
		if (item instanceof Object3D) {
			Object3D o = (Object3D) item;
			lookAt(o.position.x, o.position.y, o.position.z);
		}
	}

	public void setScale3(float x, float y, float z) {
		scale.set(x, y, z);
		boundsDone = false;
		transformDirty = true;
		if (rigid != null) createBody();
	}

	public float getScaleX3() {
		return scale.x;
	}

	public float getScaleY3() {
		return scale.y;
	}

	public float getScaleZ3() {
		return scale.z;
	}

	/** Size in meters (bounds x scale). */
	public float getSizeX() {
		return localBounds().getWidth() * Math.abs(scale.x);
	}

	public float getSizeY() {
		return localBounds().getHeight() * Math.abs(scale.y);
	}

	public float getSizeZ() {
		return localBounds().getDepth() * Math.abs(scale.z);
	}

	/** The new transform reaches the physics body (teleport). */
	void pushTransform() {
		transformDirty = true;
		if (rigid != null) {
			Matrix4 m = new Matrix4().set(position, rotation);
			rigid.setWorldTransform(m);
			if (motion != null) motion.setWorldTransform(m);
			rigid.activate();
		}
	}

	public float distanceTo(PlayerItem item) {
		if (item instanceof Object3D) return position.dst(((Object3D) item).position);
		return Float.MAX_VALUE;
	}

	public float distanceToPoint(float x, float y, float z) {
		return position.dst(x, y, z);
	}

	// ------------------------------------------------------------------ API: look

	public void setColor(String hex) {
		def.Color = hex;
		setColor3(Materials3D.color(hex, Color.WHITE));
	}

	/** Last color set by a timeline / tween (null = the one of the properties). */
	public Color lastColor;

	public void setColor3(Color c) {
		if (instance == null) return;
		for (Material m : instance.materials) {
			ColorAttribute d = (ColorAttribute) m.get(ColorAttribute.Diffuse);
			if (d != null) d.color.set(c.r, c.g, c.b, d.color.a);
			else m.set(ColorAttribute.createDiffuse(c));
			net.mgsx.gltf.scene3d.attributes.PBRColorAttribute b = (net.mgsx.gltf.scene3d.attributes.PBRColorAttribute) m.get(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.BaseColorFactor);
			if (b != null) b.color.set(c.r, c.g, c.b, b.color.a);
		}
	}

	public void setTexture(String image) {
		def.Texture = image;
		applyLook();
	}

	public void setOpacity(float a) {
		def.Opacity = MathUtils.clamp(a, 0, 1);
		if (instance != null) for (Material m : instance.materials) Materials3D.setOpacity(m, def.Opacity);
	}

	public float getOpacity() {
		return def.Opacity;
	}

	public void setEmissive(String hex, float intensity) {
		def.Emissive = hex;
		def.Emissive_Intensity = intensity;
		applyLook();
	}

	public void setOutline(boolean on, String hex, float width) {
		outline = on;
		outlineColor.set(Materials3D.color(hex, Color.WHITE));
		outlineWidth = width <= 0 ? 0.03f : width;
	}

	public void setVisible3(boolean v) {
		setVisible(v);
	}

	/** Changes the shape (CUBE, SPHERE...) or loads another model ("/car.glb"). */
	public void setShape(String shape) {
		def.Mesh = shape;
		boolean hadBody = rigid != null;
		buildModel();
		if (hadBody) createBody();
	}

	public void setModel(String path) {
		def.Mesh = "MODEL";
		def.Model = path;
		boolean hadBody = rigid != null;
		buildModel();
		if (hadBody) createBody();
	}

	/** Project shader ("shaders/x" or "builtin:glow"), "" = none. */
	public void setShader(String name, String params) {
		def.Shader = name;
		def.Shader_Params = params == null ? "" : params;
		if (instance == null) return;
		if (name == null || name.length() == 0) {
			program = null;
			for (Material m : instance.materials) m.remove(UserShader.Attr.Type);
			return;
		}
		program = UserShader.program(name, def.Shader_Params);
		if (program == null) return;
		UserShader.Attr a = new UserShader.Attr(program);
		for (Material m : instance.materials) m.set(a);
	}

	/** Shader written in a block / script (see UserShader for the format). */
	public void setShaderCode(String code) {
		String id = "code:" + def.name + ":" + code.hashCode();
		program = UserShader.fromCode(id, code, def.Shader_Params);
		if (instance == null) return;
		UserShader.Attr a = new UserShader.Attr(program);
		for (Material m : instance.materials) m.set(a);
	}

	public void setShaderParam(String name, String value) {
		if (program != null) program.setParam(name, value);
	}

	public void setShaderParamFloat(String name, float value) {
		if (program != null) program.params.put(name, new float[]{value});
	}

	public String getShaderError() {
		return program == null || program.error == null ? "" : program.error;
	}

	// ------------------------------------------------------------------ API: animation

	public void playAnimation(String name, boolean loop, float speed) {
		if (anim == null) return;
		String id = resolveAnim(name);
		if (id == null) return;
		animSpeed = speed;
		animPaused = false;
		currentAnim = id;
		try {
			anim.setAnimation(id, loop ? -1 : 1, 1f, null);
		} catch (Throwable ignored) {
		}
	}

	/** Smoothly blends into another clip over the given seconds. */
	public void crossFadeAnimation(String name, boolean loop, float speed, float seconds) {
		if (anim == null) return;
		String id = resolveAnim(name);
		if (id == null) return;
		animSpeed = speed;
		animPaused = false;
		currentAnim = id;
		try {
			anim.animate(id, loop ? -1 : 1, 1f, null, Math.max(0f, seconds));
		} catch (Throwable ignored) {
		}
	}

	/** Plays a clip once on top of the current one, then goes back (attack, wave...). */
	public void playAnimationOnce(String name, float speed) {
		if (anim == null) return;
		String id = resolveAnim(name);
		if (id == null) return;
		try {
			anim.action(id, 1, speed, null, 0.15f);
		} catch (Throwable ignored) {
		}
	}

	public void stopAnimation() {
		if (anim == null) return;
		try {
			anim.setAnimation(null);
		} catch (Throwable ignored) {
		}
		currentAnim = "";
	}

	public void pauseAnimation() {
		animPaused = true;
	}

	public void resumeAnimation() {
		animPaused = false;
	}

	public void setAnimationSpeed(float s) {
		animSpeed = s;
	}

	public boolean isAnimationPlaying() {
		return anim != null && anim.current != null && !animPaused && (anim.current.loopCount != 0);
	}

	public String getAnimation() {
		return currentAnim;
	}

	public int getAnimationCount() {
		return instance == null ? 0 : instance.animations.size;
	}

	public String getAnimationName(int i) {
		if (instance == null || i < 0 || i >= instance.animations.size) return "";
		return instance.animations.get(i).id;
	}

	/** Comma separated names of the clips. */
	public String getAnimationNames() {
		StringBuilder sb = new StringBuilder();
		if (instance != null) for (Animation a : instance.animations) {
			if (sb.length() > 0) sb.append(',');
			sb.append(a.id);
		}
		return sb.toString();
	}

	private String resolveAnim(String name) {
		if (instance == null || instance.animations.size == 0) return null;
		if (name == null || name.length() == 0 || "*".equals(name)) return instance.animations.first().id;
		for (Animation a : instance.animations) if (a.id.equals(name)) return a.id;
		for (Animation a : instance.animations) if (a.id.equalsIgnoreCase(name)) return a.id;
		for (Animation a : instance.animations) if (a.id.toLowerCase().contains(name.toLowerCase())) return a.id;
		try {
			int i = Integer.parseInt(name.trim());
			if (i >= 0 && i < instance.animations.size) return instance.animations.get(i).id;
		} catch (Exception ignored) {
		}
		return null;
	}

	/** Turns a node of the model (e.g. a wheel) around its own axis, degrees. */
	public void rotateNode(String node, float x, float y, float z) {
		if (instance == null) return;
		Node n = instance.getNode(node, true);
		if (n == null) return;
		n.rotation.mul(new Quaternion().setEulerAngles(y, x, z));
		n.isAnimated = true;
		instance.calculateTransforms();
	}

	public void setNodeRotation(String node, float x, float y, float z) {
		if (instance == null) return;
		Node n = instance.getNode(node, true);
		if (n == null) return;
		n.rotation.setEulerAngles(y, x, z);
		n.isAnimated = true;
		instance.calculateTransforms();
	}

	public void setNodeVisible(String node, boolean visible) {
		if (instance == null) return;
		Node n = instance.getNode(node, true);
		if (n == null) return;
		for (int i = 0; i < n.parts.size; i++) n.parts.get(i).enabled = visible;
	}

	public boolean hasNode(String node) {
		return instance != null && instance.getNode(node, true) != null;
	}

	// ------------------------------------------------------------------ API: physics

	private final Vector3 v3 = new Vector3();

	public void applyForce(float x, float y, float z) {
		if (rigid == null) return;
		rigid.activate();
		rigid.applyCentralForce(v3.set(x, y, z));
	}

	public void applyImpulse(float x, float y, float z) {
		if (rigid == null) return;
		rigid.activate();
		rigid.applyCentralImpulse(v3.set(x, y, z));
	}

	/** Push in the element's own directions (forward = its front). */
	public void applyLocalForce(float forward, float right, float up) {
		Vector3 f = forward(new Vector3()).scl(forward).add(right(new Vector3()).scl(right)).add(up(new Vector3()).scl(up));
		applyForce(f.x, f.y, f.z);
	}

	public void applyTorque(float x, float y, float z) {
		if (rigid == null) return;
		rigid.activate();
		rigid.applyTorque(v3.set(x, y, z));
	}

	public void setVelocity3(float x, float y, float z) {
		if (rigid == null) return;
		rigid.activate();
		rigid.setLinearVelocity(v3.set(x, y, z));
	}

	public void setVelocityKeepY(float x, float z) {
		if (rigid == null) return;
		Vector3 v = rigid.getLinearVelocity();
		setVelocity3(x, v.y, z);
	}

	public float getVelocityX3() {
		return rigid == null ? 0 : rigid.getLinearVelocity().x;
	}

	public float getVelocityY3() {
		return rigid == null ? 0 : rigid.getLinearVelocity().y;
	}

	public float getVelocityZ3() {
		return rigid == null ? 0 : rigid.getLinearVelocity().z;
	}

	public float getSpeed3() {
		return rigid == null ? 0 : rigid.getLinearVelocity().len();
	}

	public void setAngularVelocity3(float x, float y, float z) {
		if (rigid == null) return;
		rigid.activate();
		rigid.setAngularVelocity(v3.set(x, y, z));
	}

	public void setMass(float m) {
		def.Mass = m;
		if (rigid != null) createBody();
	}

	/** NONE STATIC DYNAMIC KINEMATIC TRIGGER */
	public void setBodyType(String type) {
		def.Body = type == null ? "NONE" : type.toUpperCase();
		if (world == null) return;
		if ("NONE".equals(def.Body)) {
			if (rigid != null) world.physics().removeBody(this);
		} else createBody();
	}

	public void setGravityScale3(float g) {
		def.Gravity_Scale = g;
		if (rigid != null && world != null) rigid.setGravity(new Vector3(world.physics().gravity).scl(g));
	}

	public void setFriction3(float f) {
		def.Friction = f;
		if (rigid != null) rigid.setFriction(f);
	}

	public void setBounce3(float r) {
		def.Restitution = r;
		if (rigid != null) rigid.setRestitution(r);
	}

	public void setPhysicsEnabled(boolean on) {
		if (world == null) return;
		if (!on && rigid != null) world.physics().removeBody(this);
		else if (on && rigid == null && !"NONE".equals(def.Body)) createBody();
	}

	/** Something solid right under the element (within dist meters)? */
	public boolean isGrounded(float dist) {
		if (world == null) return false;
		float half = getSizeY() * 0.5f;
		Vector3 c = center(new Vector3());
		return world.physics().raycast(c.x, c.y, c.z, 0, -1, 0, half + Math.max(0.02f, dist), this);
	}

	public boolean isTouching(PlayerItem other) {
		return world != null && world.touching(this, other);
	}

	// ------------------------------------------------------------------ API: terrain / water

	/** Terrain: ground height (meters) at a world x, z. Water: surface height there. */
	public float heightAt(float x, float z) {
		if (terrain != null) return terrain.heightAt(this, x, z);
		if (water != null) return position.y + Water3D.heightAt(water, transform, x, z, World3D.time);
		return position.y + getSizeY() * 0.5f;
	}

	public boolean isWater() {
		return water != null;
	}

	public void setWaves(float height, float speed, float length) {
		if (water == null) return;
		water.height = height;
		water.speed = speed;
		water.length = length;
	}

	public void setWaterColors(String shallow, String deep) {
		if (water == null) return;
		water.shallow.set(Materials3D.color(shallow, water.shallow));
		water.deep.set(Materials3D.color(deep, water.deep));
	}

	// ------------------------------------------------------------------ lifecycle

	/** Removes the element from the scene (physics, drawing, actors). */
	public void destroy() {
		if (destroyed) return;
		destroyed = true;
		try {
			if (getScript() != null) getScript().destroyBehaviors();
		} catch (Throwable ignored) {
		}
		if (world != null) {
			if (rigid != null) world.physics().removeBody(this);
			world.remove(this);
		}
		disposeOwned();
		remove();
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	public Object3DDef getDef() {
		return def;
	}

	// ------------------------------------------------------------------ API used by the 3D behaviors (appended)

	/** Heading in degrees around Y, read from the real orientation (also right for elements moved by physics). */
	public float getYaw3() {
		forward(tmp);
		return MathUtils.atan2(-tmp.x, -tmp.z) * MathUtils.radiansToDegrees;
	}

	/** Stands upright and turns to a heading (degrees around Y). */
	public void setYaw3(float yaw) {
		setRotation3(0, yaw, 0);
	}

	/** Height (meters) of the lowest point of the element (bounds x scale, rotation ignored). */
	public float getBottomY() {
		return position.y + Math.min(localBounds().min.y * scale.y, localBounds().max.y * scale.y);
	}

	/** Height (meters) of the highest point of the element (bounds x scale, rotation ignored). */
	public float getTopY() {
		return position.y + Math.max(localBounds().min.y * scale.y, localBounds().max.y * scale.y);
	}

	/** True when physics moves it (a DYNAMIC body). */
	public boolean isDynamic() {
		return rigid != null && "DYNAMIC".equals(def.Body);
	}

	/** Physics may (false) or may not (true) turn the element around each axis: characters stay upright. */
	public void lockRotation3(boolean x, boolean y, boolean z) {
		if (rigid == null) return;
		rigid.setAngularFactor(v3.set(x ? 0 : 1, y ? 0 : 1, z ? 0 : 1));
		rigid.setAngularVelocity(v3.set(x ? 0 : rigid.getAngularVelocity().x, y ? 0 : rigid.getAngularVelocity().y, z ? 0 : rigid.getAngularVelocity().z));
	}

	/** Pushes at a point of the world (meters): it also turns the element (boats on waves, hits). */
	public void applyForceAt(float fx, float fy, float fz, float px, float py, float pz) {
		if (rigid == null) return;
		rigid.activate();
		Vector3 rel = new Vector3(px, py, pz).sub(rigid.getCenterOfMassTransform().getTranslation(new Vector3()));
		rigid.applyForce(new Vector3(fx, fy, fz), rel);
	}

	public void applyImpulseAt(float ix, float iy, float iz, float px, float py, float pz) {
		if (rigid == null) return;
		rigid.activate();
		Vector3 rel = new Vector3(px, py, pz).sub(rigid.getCenterOfMassTransform().getTranslation(new Vector3()));
		rigid.applyImpulse(new Vector3(ix, iy, iz), rel);
	}

	/** Velocity (m/s) of the body at a point of the world (0 when it has no body). */
	public Vector3 velocityAt(float px, float py, float pz, Vector3 out) {
		if (rigid == null) return out.setZero();
		Vector3 rel = new Vector3(px, py, pz).sub(rigid.getCenterOfMassTransform().getTranslation(new Vector3()));
		return out.set(rigid.getVelocityInLocalPoint(rel));
	}

	public float getAngularVelocityY3() {
		return rigid == null ? 0 : rigid.getAngularVelocity().y;
	}

	private com.badlogic.gdx.utils.ObjectMap<String, Quaternion> nodeBaseRot;
	private com.badlogic.gdx.utils.ObjectMap<String, Vector3> nodeBasePos;

	/**
	 * Moves / turns a node of the model ON TOP of its pose in the file: rotation (degrees, Y then X then Z) is applied in the
	 * parent's axes before the node's own rotation, so a mirrored wheel still spins the right way. Wheels, doors, lids, turrets.
	 */
	public void setNodeOffset(String node, float dx, float dy, float dz, float rx, float ry, float rz) {
		if (instance == null) return;
		Node n = instance.getNode(node, true);
		if (n == null) return;
		if (nodeBaseRot == null) {
			nodeBaseRot = new com.badlogic.gdx.utils.ObjectMap<String, Quaternion>();
			nodeBasePos = new com.badlogic.gdx.utils.ObjectMap<String, Vector3>();
		}
		Quaternion base = nodeBaseRot.get(node);
		if (base == null) {
			base = new Quaternion(n.rotation);
			nodeBaseRot.put(node, base);
			nodeBasePos.put(node, new Vector3(n.translation));
		}
		n.rotation.setEulerAngles(ry, rx, rz).mul(base);
		n.translation.set(nodeBasePos.get(node)).add(dx, dy, dz);
		n.isAnimated = true;
		instance.calculateTransforms();
	}

	/** Puts a node back where the model file has it. */
	public void resetNode(String node) {
		if (instance == null || nodeBaseRot == null || !nodeBaseRot.containsKey(node)) return;
		Node n = instance.getNode(node, true);
		if (n == null) return;
		n.rotation.set(nodeBaseRot.get(node));
		n.translation.set(nodeBasePos.get(node));
		instance.calculateTransforms();
	}

	/** Center of a node of the model in the element's own space (meters before the element's scale); false when there is no such node. */
	public boolean getNodeCenter(String node, Vector3 out) {
		if (instance == null) return false;
		Node n = instance.getNode(node, true);
		if (n == null) return false;
		BoundingBox b = n.calculateBoundingBox(new BoundingBox(), true);
		if (b.isValid() && !b.getDimensions(new Vector3()).isZero()) b.getCenter(out);
		else n.globalTransform.getTranslation(out);
		return true;
	}

	/** Size of a node of the model in the element's own space (before the element's scale); false when there is no such node. */
	public boolean getNodeSize(String node, Vector3 out) {
		if (instance == null) return false;
		Node n = instance.getNode(node, true);
		if (n == null) return false;
		BoundingBox b = n.calculateBoundingBox(new BoundingBox(), true);
		if (!b.isValid()) return false;
		b.getDimensions(out);
		return true;
	}

	/** A point of the element's own space (meters, before scale) -> the world. */
	public Vector3 localToWorld(float x, float y, float z, Vector3 out) {
		if (parent3d != null) return out.set(x, y, z).mul(transform);
		return out.set(x, y, z).scl(scale).mul(rotation).add(position);
	}
}
