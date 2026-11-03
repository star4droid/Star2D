package com.star4droid.template.d3;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.ContactListener;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseInterface;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btCompoundShape;
import com.badlogic.gdx.physics.bullet.collision.btConeShape;
import com.badlogic.gdx.physics.bullet.collision.btConvexHullShape;
import com.badlogic.gdx.physics.bullet.collision.btCylinderShape;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btSphereShape;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.physics.bullet.dynamics.btTypedConstraint;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.star4droid.star2d.ElementDefs.Object3DDef;

/**
 * Bullet physics of a 3D scene. Started only when an element has a body. 1 unit = 1 meter, gravity -9.81 by default.
 * Elements get collision begin / end events (element + scene events), triggers do not push but report.
 */
public class Physics3D implements Disposable {
	private static boolean inited;

	public static void init() {
		if (inited) return;
		Bullet.init();
		inited = true;
	}

	public final World3D world3d;
	public btDiscreteDynamicsWorld world;
	private btDefaultCollisionConfiguration config;
	private btCollisionDispatcher dispatcher;
	private btBroadphaseInterface broadphase;
	private btSequentialImpulseConstraintSolver solver;
	private Contacts contacts;
	final IntMap<Object3D> byId = new IntMap<Object3D>();
	private int nextId = 1;
	public final Vector3 gravity = new Vector3(0, -9.81f, 0);
	/** Fixed step and the max sub steps per frame. */
	public float fixedStep = 1f / 60f;
	public int maxSubSteps = 4;
	public final Array<btTypedConstraint> constraints = new Array<btTypedConstraint>();
	private final Array<Disposable> owned = new Array<Disposable>();

	// last ray result
	public Object3D hitObject;
	public final Vector3 hitPoint = new Vector3(), hitNormal = new Vector3();
	public float hitDistance;

	public Physics3D(World3D w) {
		world3d = w;
		init();
		config = new btDefaultCollisionConfiguration();
		dispatcher = new btCollisionDispatcher(config);
		broadphase = new btDbvtBroadphase();
		solver = new btSequentialImpulseConstraintSolver();
		world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
		world.setGravity(gravity);
		contacts = new Contacts(this);
	}

	public void setGravity(float x, float y, float z) {
		gravity.set(x, y, z);
		world.setGravity(gravity);
		// btDiscreteDynamicsWorld.setGravity gives every body the plain gravity: put the Gravity Scale back
		for (IntMap.Entry<Object3D> e : byId) {
			Object3D o = e.value;
			if (o.rigid != null && "DYNAMIC".equals(o.d().Body)) {
				o.rigid.setGravity(tmpG.set(gravity).scl(o.d().Gravity_Scale));
				o.rigid.activate();
			}
		}
	}

	private final Vector3 tmpG = new Vector3();

	public void step(float dt) {
		if (dt <= 0) return;
		stepping = true;
		try {
			world.stepSimulation(Math.min(dt, 0.1f), maxSubSteps, fixedStep);
		} finally {
			stepping = false;
		}
		dispatchContacts();
	}

	// Contact callbacks come from inside stepSimulation (native code). Running the game's collision events there would let a
	// script destroy / create / move bodies in the middle of Bullet's step (native crash), so they wait until the step ends.
	private boolean stepping;
	private final com.badlogic.gdx.utils.IntArray pending = new com.badlogic.gdx.utils.IntArray();

	private boolean disposing;

	void contact(int idA, int idB, boolean begin) {
		if (disposing) return; // scene closing: no collision events from the bodies being removed
		if (stepping) {
			pending.add(idA);
			pending.add(idB);
			pending.add(begin ? 1 : 0);
			return;
		}
		Object3D a = byId.get(idA), b = byId.get(idB);
		if (a != null && b != null) {
			if (begin) world3d.contactBegin(a, b);
			else world3d.contactEnd(a, b);
		}
	}

	private void dispatchContacts() {
		if (pending.size == 0) return;
		int[] list = pending.toArray();
		pending.clear();
		for (int i = 0; i + 2 < list.length; i += 3) contact(list[i], list[i + 1], list[i + 2] == 1);
	}

	int register(Object3D o) {
		int id = nextId++;
		byId.put(id, o);
		return id;
	}

	void unregister(int id) {
		byId.remove(id);
	}

	public Object3D objectOf(btCollisionObject co) {
		return co == null ? null : byId.get(co.getUserValue());
	}

	/** Collision shape of an element (size from its scale). */
	public btCollisionShape shapeFor(Object3D o) {
		return shapeFor(o, o.bodyShapes);
	}

	/** The shape and its parts go to {@code own}: freed when the body is removed (bodies are rebuilt on scale / mass changes). */
	private btCollisionShape shapeFor(Object3D o, Array<Disposable> own) {
		Object3DDef d = o.d();
		String c = d.Collider;
		Vector3 s = new Vector3(Math.abs(o.scale.x) * d.Collider_Scale_X, Math.abs(o.scale.y) * d.Collider_Scale_Y, Math.abs(o.scale.z) * d.Collider_Scale_Z);
		String shape = d.Mesh;
		if ("AUTO".equals(c)) {
			if (Shapes3D.SPHERE.equals(shape) || Shapes3D.HEMISPHERE.equals(shape)) c = "SPHERE";
			else if (Shapes3D.CAPSULE.equals(shape)) c = "CAPSULE";
			else if (Shapes3D.CYLINDER.equals(shape) || Shapes3D.DISC.equals(shape) || Shapes3D.TUBE.equals(shape)) c = "CYLINDER";
			else if (Shapes3D.CONE.equals(shape)) c = "CONE";
			else if (Shapes3D.RAMP.equals(shape) || Shapes3D.STAIRS.equals(shape) || Shapes3D.PYRAMID.equals(shape) || Shapes3D.WEDGE.equals(shape) || Shapes3D.TORUS.equals(shape)) c = o.isStaticBody() ? "MESH" : "CONVEX";
			else if ("TERRAIN".equals(shape)) c = "MESH";
			else if ("MODEL".equals(shape)) c = o.isStaticBody() ? "MESH" : "BOX";
			else c = "BOX";
		}
		btCollisionShape sh;
		BoundingBox bb = o.localBounds();
		Vector3 size = bb.getDimensions(new Vector3());
		Vector3 center = bb.getCenter(new Vector3());
		if ("SPHERE".equals(c)) {
			sh = new btSphereShape(0.5f * Math.max(size.x * s.x, Math.max(size.y * s.y, size.z * s.z)));
		} else if ("CAPSULE".equals(c)) {
			float r = 0.5f * Math.max(size.x * s.x, size.z * s.z);
			sh = new btCapsuleShape(r, Math.max(0.01f, size.y * s.y - 2 * r));
		} else if ("CYLINDER".equals(c)) {
			sh = new btCylinderShape(new Vector3(size.x * s.x * 0.5f, size.y * s.y * 0.5f, size.z * s.z * 0.5f));
		} else if ("CONE".equals(c)) {
			sh = new btConeShape(0.5f * Math.max(size.x * s.x, size.z * s.z), size.y * s.y);
		} else if ("MESH".equals(c) && o.instance != null) {
			btCollisionShape ms = Bullet.obtainStaticNodeShape(o.instance.nodes);
			if (ms == null) return new btBoxShape(new Vector3(Math.max(0.005f, size.x * s.x * 0.5f), Math.max(0.005f, size.y * s.y * 0.5f), Math.max(0.005f, size.z * s.z * 0.5f)));
			ms.setLocalScaling(s);
			own.add(ms);
			return ms;
		} else if ("CONVEX".equals(c) && o.instance != null) {
			btConvexHullShape hull = convexHull(o.instance.model, s);
			sh = hull != null ? hull : new btBoxShape(new Vector3(size.x * s.x * 0.5f, size.y * s.y * 0.5f, size.z * s.z * 0.5f));
		} else {
			sh = new btBoxShape(new Vector3(Math.max(0.005f, size.x * s.x * 0.5f), Math.max(0.005f, size.y * s.y * 0.5f), Math.max(0.005f, size.z * s.z * 0.5f)));
		}
		Vector3 off = new Vector3(center.x * s.x + d.Collider_Offset_X, center.y * s.y + d.Collider_Offset_Y, center.z * s.z + d.Collider_Offset_Z);
		if (!off.isZero(0.0001f)) {
			btCompoundShape comp = new btCompoundShape();
			comp.addChildShape(new Matrix4().setToTranslation(off), sh);
			own.add(sh);
			sh = comp;
		}
		own.add(sh);
		return sh;
	}

	private static btConvexHullShape convexHull(Model m, Vector3 scale) {
		try {
			btConvexHullShape hull = new btConvexHullShape();
			Vector3 v = new Vector3();
			for (com.badlogic.gdx.graphics.Mesh mesh : m.meshes) {
				int stride = mesh.getVertexSize() / 4;
				float[] verts = new float[mesh.getNumVertices() * stride];
				mesh.getVertices(verts);
				int step = Math.max(1, mesh.getNumVertices() / 200);
				for (int i = 0; i < mesh.getNumVertices(); i += step) {
					v.set(verts[i * stride], verts[i * stride + 1], verts[i * stride + 2]).scl(scale);
					hull.addPoint(v, false);
				}
			}
			hull.recalcLocalAabb();
			return hull;
		} catch (Throwable t) {
			return null;
		}
	}

	/** Motion state: Bullet moves the element. */
	static class Motion extends btMotionState {
		final Object3D o;

		Motion(Object3D o) {
			this.o = o;
		}

		@Override
		public void getWorldTransform(Matrix4 worldTrans) {
			worldTrans.set(o.position, o.rotation);
		}

		@Override
		public void setWorldTransform(Matrix4 worldTrans) {
			worldTrans.getTranslation(o.position);
			worldTrans.getRotation(o.rotation, true);
			o.transformDirty = true;
		}
	}

	public btRigidBody createBody(Object3D o) {
		Object3DDef d = o.d();
		freeShapes(o);
		btCollisionShape shape = shapeFor(o, o.bodyShapes);
		boolean dynamic = "DYNAMIC".equals(d.Body);
		float mass = dynamic ? Math.max(0.001f, d.Mass) : 0f;
		Vector3 inertia = new Vector3();
		if (dynamic) shape.calculateLocalInertia(mass, inertia);
		Motion ms = new Motion(o);
		btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(mass, ms, shape, inertia);
		info.setFriction(d.Friction);
		info.setRestitution(d.Restitution);
		info.setLinearDamping(d.Linear_Damping);
		info.setAngularDamping(d.Angular_Damping);
		btRigidBody b = new btRigidBody(info);
		info.dispose();
		o.motion = ms;
		int flags = b.getCollisionFlags();
		if ("KINEMATIC".equals(d.Body)) {
			flags |= btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT;
			b.setActivationState(4); // DISABLE_DEACTIVATION
		}
		if ("STATIC".equals(d.Body)) flags |= btCollisionObject.CollisionFlags.CF_STATIC_OBJECT;
		if ("TRIGGER".equals(d.Body)) flags |= btCollisionObject.CollisionFlags.CF_NO_CONTACT_RESPONSE | btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT;
		flags |= btCollisionObject.CollisionFlags.CF_CUSTOM_MATERIAL_CALLBACK;
		b.setCollisionFlags(flags);
		if (dynamic) {
			b.setLinearFactor(new Vector3(d.Lock_Move_X ? 0 : 1, d.Lock_Move_Y ? 0 : 1, d.Lock_Move_Z ? 0 : 1));
			b.setAngularFactor(new Vector3(d.Lock_Rotation_X ? 0 : 1, d.Lock_Rotation_Y ? 0 : 1, d.Lock_Rotation_Z ? 0 : 1));
			if (d.Continuous_Collision) {
				Vector3 dim = o.localBounds().getDimensions(new Vector3()).scl(o.scale);
				float r = Math.max(0.01f, Math.min(dim.x, Math.min(dim.y, dim.z)) * 0.4f);
				b.setCcdMotionThreshold(r);
				b.setCcdSweptSphereRadius(r * 0.8f);
			}
			if (!d.Sleep) b.setActivationState(4);
		}
		int id = register(o);
		b.setUserValue(id);
		b.setContactCallbackFlag(1);
		b.setContactCallbackFilter(1);
		short group = (short) layerBit(d.Layer);
		short mask = (short) ~layerMask(d.Ignore_Layers);
		world.addRigidBody(b, group, mask);
		if (dynamic) b.setGravity(new Vector3(gravity).scl(d.Gravity_Scale));
		o.bodyId = id;
		return b;
	}

	/** Raycast vehicles of this world (a vehicle must leave the world before its chassis body is deleted). */
	final Array<Vehicle3D> vehicles = new Array<Vehicle3D>();

	public void removeBody(Object3D o) {
		if (o.rigid == null) return;
		// a vehicle on this body would keep updating a deleted chassis in the next step (native crash)
		for (int i = vehicles.size - 1; i >= 0; i--) if (i < vehicles.size) vehicles.get(i).bodyRemoved(o.rigid);
		world.removeRigidBody(o.rigid);
		unregister(o.bodyId);
		o.rigid.dispose();
		if (o.motion != null) o.motion.dispose();
		o.rigid = null;
		o.motion = null;
		freeShapes(o);
	}

	/** Children first (a compound does not delete its children). */
	private static void freeShapes(Object3D o) {
		for (int i = 0; i < o.bodyShapes.size; i++) {
			try {
				o.bodyShapes.get(i).dispose();
			} catch (Throwable ignored) {
			}
		}
		o.bodyShapes.clear();
	}

	// ------------------------------------------------------------------ layers

	private final com.badlogic.gdx.utils.ObjectIntMap<String> layers = new com.badlogic.gdx.utils.ObjectIntMap<String>();

	int layerBit(String name) {
		if (name == null || name.trim().length() == 0) return 1;
		name = name.trim();
		int i = layers.get(name, -1);
		if (i < 0) {
			i = Math.min(14, layers.size + 1);
			layers.put(name, i);
		}
		return 1 << i;
	}

	int layerMask(String list) {
		if (list == null || list.trim().length() == 0) return 0;
		int m = 0;
		for (String n : list.split(",")) if (n.trim().length() > 0) m |= layerBit(n);
		return m;
	}

	// ------------------------------------------------------------------ queries

	private final Vector3 rf = new Vector3(), rt = new Vector3();

	/** Ray from a point in a direction (max distance). true when something was hit (see hitObject / hitPoint...). */
	public boolean raycast(float x, float y, float z, float dx, float dy, float dz, float dist, Object3D ignore) {
		rf.set(x, y, z);
		rt.set(dx, dy, dz).nor().scl(dist).add(rf);
		ClosestRayResultCallback cb = new ClosestRayResultCallback(rf, rt);
		try {
			world.rayTest(rf, rt, cb);
			hitObject = null;
			if (!cb.hasHit()) return false;
			btCollisionObject co = cb.getCollisionObject();
			Object3D o = objectOf(co);
			if (o != null && o == ignore) {
				// try again from just after the ignored element
				Vector3 p = new Vector3();
				cb.getHitPointWorld(p);
				float used = p.dst(rf) + 0.02f;
				if (used >= dist) return false;
				Vector3 dir = new Vector3(dx, dy, dz).nor();
				return raycast(p.x + dir.x * 0.02f, p.y + dir.y * 0.02f, p.z + dir.z * 0.02f, dx, dy, dz, dist - used, null) && sameAfter(used);
			}
			hitObject = o;
			cb.getHitPointWorld(hitPoint);
			cb.getHitNormalWorld(hitNormal);
			hitDistance = hitPoint.dst(rf);
			return true;
		} finally {
			cb.dispose();
		}
	}

	private boolean sameAfter(float used) {
		hitDistance += used;
		return true;
	}

	// ------------------------------------------------------------------ contacts

	static class Contacts extends ContactListener {
		final Physics3D p;

		Contacts(Physics3D p) {
			this.p = p;
		}

		@Override
		public void onContactStarted(int userValue0, boolean match0, int userValue1, boolean match1) {
			p.contact(userValue0, userValue1, true);
		}

		@Override
		public void onContactEnded(int userValue0, boolean match0, int userValue1, boolean match1) {
			p.contact(userValue0, userValue1, false);
		}
	}

	public void track(Disposable d) {
		owned.add(d);
	}

	@Override
	public void dispose() {
		for (btTypedConstraint c : constraints) {
			try {
				world.removeConstraint(c);
				c.dispose();
			} catch (Throwable ignored) {
			}
		}
		constraints.clear();
		disposing = true;
		// the bodies too: an element destroyed later must not reach a deleted world (its rigid is null after this)
		Array<Object3D> withBodies = new Array<Object3D>();
		for (IntMap.Entry<Object3D> e : byId) withBodies.add(e.value);
		for (Object3D o : withBodies) {
			try {
				removeBody(o);
			} catch (Throwable ignored) {
			}
		}
		pending.clear();
		if (contacts != null) contacts.dispose();
		world.dispose();
		solver.dispose();
		broadphase.dispose();
		dispatcher.dispose();
		config.dispose();
		for (Disposable d : owned) {
			try {
				d.dispose();
			} catch (Throwable ignored) {
			}
		}
		owned.clear();
	}

	/** Unused helper kept for vehicles (wheel rotation). */
	static final Quaternion TMPQ = new Quaternion();
	static final ModelInstance NONE = null;
}
