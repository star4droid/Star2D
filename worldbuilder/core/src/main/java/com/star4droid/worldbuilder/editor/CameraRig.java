package com.star4droid.worldbuilder.editor;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.worldbuilder.engine.Terrain;

/** Orbit camera: a target on the ground, yaw, pitch and distance; movements are smoothed. */
public final class CameraRig {
    public final Vector3 target = new Vector3(), targetGoal = new Vector3();
    public float yaw = 35, pitch = 32, dist = 42, yawGoal = 35, pitchGoal = 32, distGoal = 42;
    private final Vector3 t = new Vector3();

    public void set(float[] c) { target.set(c[0], c[1], c[2]); targetGoal.set(target); yaw = yawGoal = c[3]; pitch = pitchGoal = c[4]; dist = distGoal = c[5]; }
    public void store(float[] c) { c[0] = targetGoal.x; c[1] = targetGoal.y; c[2] = targetGoal.z; c[3] = yawGoal; c[4] = pitchGoal; c[5] = distGoal; }

    public void orbit(float dx, float dy) { yawGoal -= dx; pitchGoal = MathUtils.clamp(pitchGoal + dy, 4, 88); }

    /** Moves the target on the ground plane by screen-space amounts (already scaled to meters). */
    public void pan(float right, float forward) {
        float s = MathUtils.sinDeg(yawGoal), c = MathUtils.cosDeg(yawGoal);
        targetGoal.x += -c * right - s * forward;
        targetGoal.z += s * right - c * forward;
        float lim = Terrain.HALF + 30;
        targetGoal.x = MathUtils.clamp(targetGoal.x, -lim, lim); targetGoal.z = MathUtils.clamp(targetGoal.z, -lim, lim);
    }

    public void zoom(float factor) { distGoal = MathUtils.clamp(distGoal * factor, 2.5f, 260); }

    public void focus(Vector3 p, float radius) { targetGoal.set(p); distGoal = MathUtils.clamp(radius * 3.2f + 3, 4, 120); }

    public void update(float dt, Terrain terrain, PerspectiveCamera cam) {
        float k = 1 - (float) Math.exp(-dt * 14);
        if (terrain != null) targetGoal.y += (Math.max(terrain.height(targetGoal.x, targetGoal.z), -2) - targetGoal.y) * Math.min(1, dt * 6);
        target.lerp(targetGoal, k);
        yaw += (yawGoal - yaw) * k; pitch += (pitchGoal - pitch) * k; dist += (distGoal - dist) * k;
        float cp = MathUtils.cosDeg(pitch);
        t.set(MathUtils.sinDeg(yaw) * cp, MathUtils.sinDeg(pitch), MathUtils.cosDeg(yaw) * cp).scl(dist).add(target);
        // stay above the ground
        if (terrain != null) { float g = terrain.height(t.x, t.z) + 0.6f; if (t.y < g) t.y = g; }
        cam.position.set(t);
        cam.up.set(0, 1, 0);
        cam.lookAt(target);
        cam.up.set(0, 1, 0);
    }
}
