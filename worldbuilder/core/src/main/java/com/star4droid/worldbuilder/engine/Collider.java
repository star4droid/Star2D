package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

/**
 * Collision shapes of the elements (what a game uses for physics). A shape is kept in the element's own units, before
 * its scale, so it follows the element when it is moved, turned or resized:
 * box = center + full size, sphere = center + radius, capsule (upright) = center + radius + total height,
 * mesh = the element's own model.
 */
public final class Collider {
    private Collider() {}
    public static final int NONE = 0, BOX = 1, SPHERE = 2, CAPSULE = 3, MESH = 4;
    public static final String[] NAMES = {"None", "Box", "Sphere", "Capsule", "Mesh"};

    private static final Quaternion q = new Quaternion();
    private static final BoundingBox tmpBox = new BoundingBox();
    private static final Vector3 t = new Vector3();

    /** The element's scale as the collider sees it. */
    public static Vector3 scaleOf(SceneObject o, Vector3 out) {
        if (o.def.kind == ModelDef.MESH) return out.set(o.scale);
        if (o.def.kind == ModelDef.WATER) return out.set(o.scale.x, 1, o.scale.z);
        return out.set(1, 1, 1);
    }

    /** From collider units to the world: position, rotation and the scale above. */
    public static Matrix4 matrix(SceneObject o, Matrix4 out) {
        q.setEulerAngles(o.rotY, o.rotX, o.rotZ);
        return out.set(o.pos, q, scaleOf(o, t));
    }

    /** The element's size in collider units (needs an updated element for water and effects). */
    public static BoundingBox bounds(SceneObject o, BoundingBox out) {
        return out.set(o.def.kind == ModelDef.MESH ? o.def.bounds : o.local);
    }

    /** Sets the type and fits the shape around the element. */
    public static void fit(SceneObject o, int type) {
        o.colType = type;
        if (type == NONE || type == MESH) return;
        BoundingBox b = bounds(o, tmpBox);
        b.getCenter(o.colCenter);
        b.getDimensions(o.colSize);
        o.colRadius = type == SPHERE ? Math.max(o.colSize.x, Math.max(o.colSize.y, o.colSize.z)) / 2 : Math.max(o.colSize.x, o.colSize.z) / 2;
        o.colHeight = o.colSize.y;
    }

    /** The shape a new element gets: boxes for buildings, fences, props and rocks, a trunk capsule for trees. */
    public static void auto(SceneObject o) {
        ModelDef d = o.def;
        o.colTrigger = false;
        if (d.kind != ModelDef.MESH || (d.flags & ModelDef.M_SMALL) != 0) { fit(o, NONE); return; }
        if (d.cat.equals("nature")) {
            if (!d.sub.equals("Trees")) { fit(o, NONE); return; }
            fit(o, CAPSULE);
            BoundingBox b = bounds(o, tmpBox);
            float w = Math.min(b.getWidth(), b.getDepth());
            o.colRadius = Math.max(0.15f, Math.min(0.6f, w * 0.08f));
            o.colCenter.x = 0; o.colCenter.z = 0;
            return;
        }
        fit(o, BOX);
    }

    /** World size of a sphere radius, and of a capsule radius and half height (rounded ends included). */
    public static float worldRadius(SceneObject o) {
        scaleOf(o, t);
        return o.colType == SPHERE ? o.colRadius * Math.max(t.x, Math.max(t.y, t.z)) : o.colRadius * Math.max(t.x, t.z);
    }

    public static float worldHalfHeight(SceneObject o) {
        scaleOf(o, t);
        return Math.max(o.colHeight * t.y / 2, worldRadius(o));
    }
}
