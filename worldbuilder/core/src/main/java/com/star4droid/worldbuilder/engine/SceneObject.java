package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** One element of a world. */
public final class SceneObject {
    public int uid;
    public ModelDef def;
    public String name = "", group = "", fx = "";
    public final Vector3 pos = new Vector3(), scale = new Vector3(1, 1, 1);
    public float rotX, rotY, rotZ;
    public final float[] tintA = {1, 1, 1}, tintB = {1, 1, 1};
    public float speed = 1, amount = 1, fxAmount = 0;
    public boolean fxTintOn;
    public final float[] fxTint = {1, 1, 1};
    public boolean hidden, locked, wind = true, shadow = true;
    /** Collision shape (see {@link Collider}). */
    public int colType;
    public boolean colTrigger;
    public final Vector3 colCenter = new Vector3(), colSize = new Vector3(1, 1, 1);
    public float colRadius = 0.5f, colHeight = 1;

    // derived
    public final Matrix4 world = new Matrix4(), inv = new Matrix4();
    public ModelDef.Built built;
    public Mesh ownMesh;
    public final BoundingBox local = new BoundingBox();
    public final Vector3 center = new Vector3();
    public float radius = 1, uniform = 1;
    public boolean dirty = true;
    int terrainVersion = -1;
    private static final Quaternion q = new Quaternion();
    private static final Vector3 t1 = new Vector3(), t2 = new Vector3();
    private static final Ray lray = new Ray();

    public SceneObject(ModelDef def) {
        this.def = def;
        name = def.name;
        if (def.tintDefaults.length > 0) System.arraycopy(def.tintDefaults[0], 0, tintA, 0, 3);
        if (def.tintDefaults.length > 1) System.arraycopy(def.tintDefaults[1], 0, tintB, 0, 3);
    }

    /** Multiplier of the vertex colors for a tint slot (the current color over the color the model was made with). */
    public void tintFactor(int slot, float[] out) {
        if (slot <= 0 || slot > def.tintDefaults.length) { out[0] = out[1] = out[2] = 1; return; }
        float[] d = def.tintDefaults[slot - 1], c = slot == 1 ? tintA : tintB;
        for (int i = 0; i < 3; i++) out[i] = c[i] / Math.max(d[i], 0.04f);
    }

    public void markDirty() { dirty = true; }

    /** Rebuilds matrices, the smart-scaled mesh, or the water mesh when something changed. */
    public void update(Scene scene) {
        boolean water = def.kind == ModelDef.WATER;
        if (!dirty && !(water && terrainVersion != scene.terrain.version)) return;
        q.setEulerAngles(rotY, rotX, rotZ);
        if (def.kind == ModelDef.MESH) {
            float u = Math.max(0.02f, Math.min(scale.x, Math.min(scale.y, scale.z)));
            uniform = u;
            ModelDef.Built nb = def.build(scale.x / u, scale.y / u, scale.z / u);
            if (nb != built) { def.release(built); built = nb; } else def.release(nb == built && nb.refs > 0 ? nb : null);
            world.set(pos, q, t1.set(u, u, u));
            local.set(built.box);
        } else if (water) {
            world.set(pos, q, t1.set(scale.x, 1, scale.z));
            ownMesh = Water.surface(def.id, world, scene.terrain, ownMesh);
            local.set(t1.set(-0.5f, -0.15f, -0.5f), t2.set(0.5f, 0.15f, 0.5f));
            terrainVersion = scene.terrain.version;
        } else if (def.kind == ModelDef.FALL) {
            world.set(pos, q, t1.set(1, 1, 1));
            ownMesh = Water.fall(scale.x, scale.y, ownMesh);
            local.set(t1.set(-scale.x / 2, 0, -Water.LIP), t2.set(scale.x / 2, scale.y + 0.1f, 0.7f));
        } else {
            world.set(pos, q, t1.set(1, 1, 1));
            float r = Math.max(scale.x, scale.z);
            local.set(t1.set(-r, 0, -r), t2.set(r, Math.max(0.6f, scale.y), r));
        }
        inv.set(world).inv();
        local.getCenter(center).mul(world);
        local.getDimensions(t1);
        float s = def.kind == ModelDef.MESH ? uniform : (water ? Math.max(scale.x, scale.z) : 1);
        radius = t1.len() * 0.5f * s + (def.hasWind() ? 0.3f : 0);
        dirty = false;
    }

    /** Distance along the ray to the element's box, or -1. */
    public float hit(Ray ray) {
        lray.set(ray).mul(inv);
        float len = lray.direction.len();
        lray.direction.nor();
        BoundingBox b = local;
        if (b.getHeight() < 0.3f || b.getWidth() < 0.3f || b.getDepth() < 0.3f) {     // thin things are hard to tap: pad them
            b = pad.set(local);
            pad.min.sub(0.15f); pad.max.add(0.15f); pad.update();
        }
        if (!Intersector.intersectRayBounds(lray, b, t1)) return -1;
        t1.mul(world);
        return t1.dst(ray.origin);
    }
    private static final BoundingBox pad = new BoundingBox();

    public void dispose() {
        if (built != null) { def.release(built); built = null; }
        if (ownMesh != null) { ownMesh.dispose(); ownMesh = null; }
    }

    // ------------------------------------------------------------------ state (save files, undo)
    public static final class State {
        public int uid; public String def, name, group, fx;
        public float px, py, pz, rx, ry, rz, sx, sy, sz, speed, amount, fxAmount;
        public float[] ft = new float[3]; public boolean fxTintOn;
        public float[] a = new float[3], b = new float[3];
        public boolean hidden, locked, wind, shadow;
        public int colType; public boolean colTrigger, colUnknown;
        public float[] col = new float[8];      // center xyz, size xyz, radius, height
    }

    public State state() {
        State s = new State();
        s.uid = uid; s.def = def.id; s.name = name; s.group = group; s.fx = fx;
        s.px = pos.x; s.py = pos.y; s.pz = pos.z; s.rx = rotX; s.ry = rotY; s.rz = rotZ; s.sx = scale.x; s.sy = scale.y; s.sz = scale.z; s.speed = speed; s.amount = amount; s.fxAmount = fxAmount; s.fxTintOn = fxTintOn; System.arraycopy(fxTint, 0, s.ft, 0, 3);
        System.arraycopy(tintA, 0, s.a, 0, 3); System.arraycopy(tintB, 0, s.b, 0, 3);
        s.hidden = hidden; s.locked = locked; s.wind = wind; s.shadow = shadow;
        s.colType = colType; s.colTrigger = colTrigger;
        s.col[0] = colCenter.x; s.col[1] = colCenter.y; s.col[2] = colCenter.z; s.col[3] = colSize.x; s.col[4] = colSize.y; s.col[5] = colSize.z; s.col[6] = colRadius; s.col[7] = colHeight;
        return s;
    }

    public void apply(State s) {
        uid = s.uid; name = s.name; group = s.group; fx = s.fx;
        pos.set(s.px, s.py, s.pz); rotX = s.rx; rotY = s.ry; rotZ = s.rz; scale.set(s.sx, s.sy, s.sz); speed = s.speed; amount = s.amount; fxAmount = s.fxAmount; fxTintOn = s.fxTintOn; System.arraycopy(s.ft, 0, fxTint, 0, 3);
        System.arraycopy(s.a, 0, tintA, 0, 3); System.arraycopy(s.b, 0, tintB, 0, 3);
        hidden = s.hidden; locked = s.locked; wind = s.wind; shadow = s.shadow;
        if (s.colUnknown) Collider.auto(this);          // a world saved before colliders existed
        else {
            colType = s.colType; colTrigger = s.colTrigger;
            colCenter.set(s.col[0], s.col[1], s.col[2]); colSize.set(s.col[3], s.col[4], s.col[5]); colRadius = s.col[6]; colHeight = s.col[7];
        }
        dirty = true;
    }

    public static void write(DataOutputStream o, State s) throws IOException {
        o.writeInt(s.uid); o.writeUTF(s.def); o.writeUTF(s.name); o.writeUTF(s.group); o.writeUTF(s.fx);
        o.writeFloat(s.px); o.writeFloat(s.py); o.writeFloat(s.pz); o.writeFloat(s.rx); o.writeFloat(s.ry); o.writeFloat(s.rz);
        o.writeFloat(s.sx); o.writeFloat(s.sy); o.writeFloat(s.sz); o.writeFloat(s.speed); o.writeFloat(s.amount);
        for (int i = 0; i < 3; i++) o.writeFloat(s.a[i]);
        for (int i = 0; i < 3; i++) o.writeFloat(s.b[i]);
        o.writeByte((s.hidden ? 1 : 0) | (s.locked ? 2 : 0) | (s.wind ? 4 : 0) | (s.shadow ? 8 : 0) | (s.fxTintOn ? 16 : 0));
        o.writeFloat(s.fxAmount); for (int i = 0; i < 3; i++) o.writeFloat(s.ft[i]);
        o.writeByte(s.colType | (s.colTrigger ? 0x80 : 0));
        for (float v : s.col) o.writeFloat(v);
    }

    /** @param version the world file version (colliders exist from version 2) */
    public static State read(DataInputStream in, int version) throws IOException {
        State s = new State();
        s.uid = in.readInt(); s.def = in.readUTF(); s.name = in.readUTF(); s.group = in.readUTF(); s.fx = in.readUTF();
        s.px = in.readFloat(); s.py = in.readFloat(); s.pz = in.readFloat(); s.rx = in.readFloat(); s.ry = in.readFloat(); s.rz = in.readFloat();
        s.sx = in.readFloat(); s.sy = in.readFloat(); s.sz = in.readFloat(); s.speed = in.readFloat(); s.amount = in.readFloat();
        for (int i = 0; i < 3; i++) s.a[i] = in.readFloat();
        for (int i = 0; i < 3; i++) s.b[i] = in.readFloat();
        int f = in.readByte();
        s.hidden = (f & 1) != 0; s.locked = (f & 2) != 0; s.wind = (f & 4) != 0; s.shadow = (f & 8) != 0; s.fxTintOn = (f & 16) != 0;
        s.fxAmount = in.readFloat(); for (int i = 0; i < 3; i++) s.ft[i] = in.readFloat();
        if (version >= 2) {
            int c = in.readByte() & 0xff;
            s.colType = c & 0x7f; s.colTrigger = (c & 0x80) != 0;
            for (int i = 0; i < 8; i++) s.col[i] = in.readFloat();
        } else s.colUnknown = true;
        return s;
    }
}
