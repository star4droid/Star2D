package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;

/** A world: terrain, water, environment and elements. */
public final class Scene {
    public String name = "My World";
    public final Terrain terrain = new Terrain();
    public final Water water = new Water();
    public final Env env = new Env();
    public final Array<SceneObject> objects = new Array<>();
    public int nextUid = 1;
    /** Camera of the editor, saved with the world: target x y z, yaw, pitch, distance. */
    public final float[] camera = {0, 0, 0, 35, 32, 42};

    /** Natural size of the kinds whose scale is a size in meters. */
    public static void defaultScale(ModelDef d, Vector3 out) {
        switch (d.id) {
            case "water_pond": out.set(6, 1, 6); break;
            case "water_lake": out.set(14, 1, 11); break;
            case "water_square": out.set(5, 1, 5); break;
            case "water_river": out.set(5, 1, 18); break;
            case "water_stream": out.set(1.8f, 1, 12); break;
            case "water_fall": out.set(3.2f, 6, 1); break;
            case "water_fall_wide": out.set(7, 5, 1); break;
            case "water_fall_thin": out.set(1.3f, 8, 1); break;
            case "fx_pillar": out.set(1, 9, 1); break;
            case "fx_godrays": out.set(4, 9, 4); break;
            case "fx_orb": out.set(0.6f, 1.6f, 0.6f); break;
            case "fx_area_lights": out.set(4, 1.2f, 4); break;
            case "fx_fireflies": case "fx_sparkles": case "fx_petals": out.set(5, 3.5f, 5); break;
            case "fx_mist": out.set(7, 1, 7); break;
            case "fx_cubes": out.set(2.2f, 3, 2.2f); break;
            case "fx_circle": out.set(2.4f, 2.5f, 2.4f); break;
            case "fx_portal": out.set(1.6f, 3.2f, 1.6f); break;
            case "fx_aura": out.set(1.2f, 3, 1.2f); break;
            case "fx_fire": out.set(0.6f, 1.6f, 0.6f); break;
            case "fx_smoke": out.set(0.7f, 4, 0.7f); break;
            case "fx_embers": out.set(1.5f, 3.5f, 1.5f); break;
            case "fx_fountain": out.set(1.4f, 3, 1.4f); break;
            default: out.set(1, 1, 1);
        }
    }

    public SceneObject create(String defId, float x, float y, float z) {
        ModelDef d = Assets.byId.get(defId);
        if (d == null) return null;
        SceneObject o = new SceneObject(d);
        o.uid = nextUid++;
        o.pos.set(x, y, z);
        defaultScale(d, o.scale);
        Collider.auto(o);
        objects.add(o);
        return o;
    }

    public SceneObject restore(SceneObject.State s) {
        ModelDef d = Assets.byId.get(s.def);
        if (d == null) return null;
        SceneObject o = new SceneObject(d);
        o.apply(s);
        if (o.uid >= nextUid) nextUid = o.uid + 1;
        objects.add(o);
        return o;
    }

    public SceneObject find(int uid) {
        for (SceneObject o : objects) if (o.uid == uid) return o;
        return null;
    }

    public void remove(SceneObject o) {
        if (objects.removeValue(o, true)) o.dispose();
    }

    public SceneObject pick(Ray ray, float maxDist) {
        SceneObject best = null; float bd = maxDist;
        for (SceneObject o : objects) {
            if (o.hidden || o.locked) continue;
            o.update(this);
            float d = o.hit(ray);
            // big boxes (tree crowns, effects) should not swallow small things inside them
            if (d >= 0) { d += Math.min(o.radius * 0.15f, 1.5f); if (d < bd) { bd = d; best = o; } }
        }
        return best;
    }

    public void clear() {
        for (SceneObject o : objects) o.dispose();
        objects.clear();
        nextUid = 1;
    }

    public void dispose() { clear(); terrain.dispose(); water.dispose(); }
}
