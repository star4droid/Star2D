package com.star4droid.template.d3;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDefaultVehicleRaycaster;
import com.badlogic.gdx.physics.bullet.dynamics.btRaycastVehicle;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btWheelInfo;

/**
 * A Bullet raycast vehicle on a DYNAMIC 3D element (the chassis). Used by the Car3D behavior; no Bullet type in the public
 * API. The chassis' front is its local -Z, wheels hang down (-Y), the axle is local X. Units: meters, Newtons, radians.
 * Positive engine force drives forward (-Z), positive steering turns left.
 */
public final class Vehicle3D {
	private final Object3D chassis;
	private final Physics3D physics;
	private final btRigidBody body;
	private btRaycastVehicle vehicle;
	private btDefaultVehicleRaycaster raycaster;
	private btRaycastVehicle.btVehicleTuning tuning;
	private int wheels;
	private boolean disposed;

	private Vehicle3D(Object3D chassis, Physics3D p, btRigidBody body) {
		this.chassis = chassis;
		this.physics = p;
		this.body = body;
	}

	/**
	 * @param points wheel attachment points in the chassis' space (meters, scale included): x, y, z per wheel
	 * @param front  front (steering) wheels
	 * @return null when the element has no DYNAMIC body or physics is not running
	 */
	public static Vehicle3D create(Object3D chassis, float[] points, boolean[] front, float radius, float restLength, float stiffness,
			float dampingRelaxation, float dampingCompression, float frictionSlip, float rollInfluence, float maxTravel) {
		if (chassis == null || chassis.world == null || !chassis.isDynamic() || !chassis.world.hasPhysics()) return null;
		Physics3D p = chassis.world.physics();
		btRigidBody b = chassis.rigidBody();
		Vehicle3D v = new Vehicle3D(chassis, p, b);
		try {
			v.tuning = new btRaycastVehicle.btVehicleTuning();
			v.tuning.setSuspensionStiffness(stiffness);
			v.tuning.setSuspensionCompression(dampingCompression);
			v.tuning.setSuspensionDamping(dampingRelaxation);
			v.raycaster = new btDefaultVehicleRaycaster(p.world);
			v.vehicle = new btRaycastVehicle(v.tuning, b, v.raycaster);
			b.setActivationState(4); // DISABLE_DEACTIVATION
			v.vehicle.setCoordinateSystem(0, 1, 2);
			Vector3 dir = new Vector3(0, -1, 0), axle = new Vector3(-1, 0, 0), cp = new Vector3();
			int n = points.length / 3;
			for (int i = 0; i < n; i++) {
				cp.set(points[i * 3], points[i * 3 + 1], points[i * 3 + 2]);
				btWheelInfo w = v.vehicle.addWheel(cp, dir, axle, restLength, radius, v.tuning, front[i]);
				w.setSuspensionStiffness(stiffness);
				w.setWheelsDampingRelaxation(dampingRelaxation);
				w.setWheelsDampingCompression(dampingCompression);
				w.setFrictionSlip(frictionSlip);
				w.setRollInfluence(rollInfluence);
				w.setMaxSuspensionTravelCm(maxTravel * 100f);
				w.setMaxSuspensionForce(Math.max(6000f, 40f * 9.81f / Math.max(0.0001f, b.getInvMass())));
			}
			v.wheels = n;
			p.world.addVehicle(v.vehicle);
			p.vehicles.add(v);
			return v;
		} catch (Throwable t) {
			com.badlogic.gdx.Gdx.app.error("Star2D-3D", "vehicle of " + chassis.getName() + ": " + t);
			v.dispose();
			return null;
		}
	}

	/** Still usable: the physics world and the chassis body are the ones it was built on. */
	public boolean valid() {
		return !disposed && vehicle != null && chassis.world != null && chassis.world.hasPhysics() && chassis.world.physics() == physics
				&& chassis.rigidBody() == body;
	}

	public int wheelCount() {
		return wheels;
	}

	public void setEngineForce(int wheel, float newtons) {
		if (valid() && wheel >= 0 && wheel < wheels) vehicle.applyEngineForce(newtons, wheel);
	}

	public void setBrake(int wheel, float value) {
		if (valid() && wheel >= 0 && wheel < wheels) vehicle.setBrake(value, wheel);
	}

	/** Radians, positive = left. */
	public void setSteering(int wheel, float radians) {
		if (valid() && wheel >= 0 && wheel < wheels) vehicle.setSteeringValue(radians, wheel);
	}

	public void setFrictionSlip(int wheel, float value) {
		if (valid() && wheel >= 0 && wheel < wheels) vehicle.getWheelInfo(wheel).setFrictionSlip(value);
	}

	public boolean inContact(int wheel) {
		return valid() && wheel >= 0 && wheel < wheels && vehicle.getWheelInfo(wheel).getRaycastInfo().getIsInContact();
	}

	/** Current spring length (meters): the wheel hangs this far below its attachment point. */
	public float suspensionLength(int wheel) {
		if (!valid() || wheel < 0 || wheel >= wheels) return 0;
		return vehicle.getWheelInfo(wheel).getRaycastInfo().getSuspensionLength();
	}

	/** Sideways sliding of a wheel: 1 = full grip, smaller = skidding (drift, smoke, tire sound). */
	public float skid(int wheel) {
		if (!valid() || wheel < 0 || wheel >= wheels) return 1;
		return vehicle.getWheelInfo(wheel).getSkidInfo();
	}

	/** Speed along the chassis' front in km/h (negative = reversing). */
	public float speedKmh() {
		if (!valid()) return 0;
		Vector3 v = body.getLinearVelocity();
		Vector3 f = chassis.forward(new Vector3());
		return v.dot(f) * 3.6f;
	}

	/** Physics3D: this body is being removed (rebuilt or deleted); the vehicle can't outlive it. */
	void bodyRemoved(btRigidBody b) {
		if (b == body) dispose();
	}

	public void dispose() {
		if (disposed) return;
		disposed = true;
		physics.vehicles.removeValue(this, true);
		try {
			if (vehicle != null && chassis.world != null && chassis.world.hasPhysics() && chassis.world.physics() == physics) physics.world.removeVehicle(vehicle);
		} catch (Throwable ignored) {
		}
		try {
			if (vehicle != null) vehicle.dispose();
			if (raycaster != null) raycaster.dispose();
			if (tuning != null) tuning.dispose();
		} catch (Throwable ignored) {
		}
		vehicle = null;
		raycaster = null;
		tuning = null;
	}
}
