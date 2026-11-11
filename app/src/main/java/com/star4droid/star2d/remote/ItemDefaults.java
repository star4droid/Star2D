package com.star4droid.star2d.remote;

import com.badlogic.gdx.Gdx;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Default property values per element type (assets/items/default.json + the type's own json). GL thread (reads assets). */
final class ItemDefaults {
    private ItemDefaults() {}

    private static final HashMap<String, LinkedHashMap<String, String>> CACHE = new HashMap<>();

    /**
     * type: BOX, CIRCLE, CUSTOM, TEXT, PROGRESS, JOYSTICK, PARTICLE, CAMERA, MAP, GROUP, UIHOLDER or LIGHT (lightType: point|directional|cone);
     * 3D scenes: OBJ3D, LIGHT3D, CAMERA3D, and CAMERAVIEW (2D element of both scene kinds).
     */
    static synchronized LinkedHashMap<String, String> of(String type, String lightType) {
        String t = type == null ? "" : type.toUpperCase();
        String key = t + (t.equals("LIGHT") ? "/" + (lightType == null || lightType.isEmpty() ? "point" : lightType.toLowerCase()) : "");
        LinkedHashMap<String, String> c = CACHE.get(key);
        if (c != null) return c;
        String file;
        switch (t) {
            case "BOX": file = "box.json"; break;
            case "CIRCLE": file = "circle.json"; break;
            case "CUSTOM": file = "custom.json"; break;
            case "TEXT": file = "text.json"; break;
            case "PROGRESS": file = "progress.json"; break;
            case "JOYSTICK": file = "joystick.json"; break;
            case "PARTICLE": file = "particle.json"; break;
            case "CAMERA": file = "camera.json"; break;
            case "MAP": file = "map.json"; break;
            case "GROUP": file = "group.json"; break;
            case "UIHOLDER": file = "uiholder.json"; break;
            case "OBJ3D": file = "obj3d.json"; break;
            case "LIGHT3D": file = "light3d.json"; break;
            case "CAMERA3D": file = "camera3d.json"; break;
            case "CAMERAVIEW": file = "cameraview.json"; break;
            case "NAVGRID": file = "navgrid.json"; break;
            case "NETWORK": file = "network.json"; break;
            case "LIGHT": file = "Lights/" + key.substring(key.indexOf('/') + 1) + ".json"; break;
            default: file = null;
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try {
            String base = Gdx.files.internal("items/default.json").readString();
            String add = file == null ? "" : "," + Gdx.files.internal("items/" + file).readString();
            JsonObject o = JsonParser.parseString(base.replace("--other--", add)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet())
                out.put(e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString());
        } catch (Exception ignored) {
        }
        CACHE.put(key, out);
        return out;
    }

    /** Is this value (as the editor stores it) the default one? Numbers compare numerically. */
    static boolean same(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.equals(b)) return true;
        try {
            return Math.abs(Double.parseDouble(a) - Double.parseDouble(b)) < 1e-6;
        } catch (Exception e) {
            return false;
        }
    }
}
