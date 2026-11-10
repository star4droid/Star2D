package com.star4droid.star2d.suda;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.ui.Tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Client description of every SUDA3D operation: what inputs it needs and which parameters it takes (type, choices,
 * default, main or "More"). Model / version lists and extra options come from GET /pricing (see {@link #merge}).
 */
public final class Ops {
    private Ops() {}

    public enum In { NONE, IMAGE, IMAGES, MODEL }
    public enum T { BOOL, CHOICE, INT, TEXT, PROMPT }

    public static final class Param {
        public final String key, label;
        public final T type;
        public String[] choices;
        public final String def;
        public final boolean main;
        Param(String key, String label, T type, String def, boolean main, String... choices) {
            this.key = key; this.label = label; this.type = type; this.def = def; this.main = main;
            this.choices = choices;
        }
        public String label() { return Tr.t("s3d_p_" + key, label); }
    }

    public static final class Op {
        public final String id, label;
        public final In input;
        public final List<Param> params = new ArrayList<>();
        public double baseCredits = -1;
        Op(String id, String label, In input, Param... p) {
            this.id = id; this.label = label; this.input = input;
            params.addAll(Arrays.asList(p));
        }
        public String label() { return Tr.t("s3d_op_" + id, label); }
        public Param param(String key) {
            for (Param p : params) if (p.key.equals(key)) return p;
            return null;
        }
        /** Does the op produce a 3D model (long job, polled every minute)? */
        public boolean makesModel() {
            return !(id.equals("text_to_image") || id.equals("image_to_image") || id.equals("image_to_multiview")
                    || id.equals("edit_multiview") || id.equals("rig_check"));
        }
        public boolean makesImages() { return id.equals("image_to_multiview") || id.equals("edit_multiview"); }
    }

    static Param prompt() { return new Param("prompt", "Prompt", T.PROMPT, "", true); }
    static Param neg() { return new Param("negative_prompt", "Negative prompt", T.TEXT, "", false); }
    static Param seed() { return new Param("seed", "Seed", T.INT, "", false); }
    static Param version() { return new Param("model_version", "Model version", T.CHOICE, "", true); }
    static Param imgModel() { return new Param("model", "Image model", T.CHOICE, "", true); }
    static Param texture() { return new Param("texture", "Texture quality", T.CHOICE, "standard", true, "none", "standard", "hd", "ultra"); }
    static Param pbr() { return new Param("pbr", "PBR materials", T.BOOL, "true", true); }
    static Param faces() { return new Param("face_limit", "Polycount", T.INT, "", true); }
    static Param lowPoly() { return new Param("smart_low_poly", "Low poly", T.BOOL, "false", true); }
    static Param quad() { return new Param("quad", "Quad mesh", T.BOOL, "false", true); }
    static Param geometry() { return new Param("geometry_quality", "Geometry quality", T.CHOICE, "standard", false, "standard", "detailed"); }
    static Param parts() { return new Param("generate_parts", "Generate parts", T.BOOL, "false", false); }
    static Param align() { return new Param("texture_alignment", "Texture alignment", T.CHOICE, "original_image", false, "original_image", "geometry"); }
    static Param orient() { return new Param("orientation", "Orientation", T.CHOICE, "default", false, "default", "align_image"); }
    static Param size() { return new Param("size", "Image size", T.CHOICE, "1024x1024", true, "1024x1024", "1024x1536", "1536x1024", "2048x2048"); }
    static Param quality() { return new Param("quality", "Quality", T.CHOICE, "standard", false, "standard", "high"); }

    public static final List<Op> ALL = new ArrayList<>(Arrays.asList(
            new Op("text_to_image", "Text → Image", In.NONE, prompt(), imgModel(), size(), quality(), neg(), seed()),
            new Op("image_to_image", "Image → Image", In.IMAGE, prompt(), imgModel(), size(), quality(), seed()),
            new Op("image_to_multiview", "Image → Multiview", In.IMAGE, seed()),
            new Op("edit_multiview", "Edit multiview", In.IMAGES, prompt()),
            new Op("text_to_model", "Text → 3D", In.NONE, prompt(), version(), texture(), pbr(), lowPoly(), quad(), faces(), geometry(), parts(), neg(), seed()),
            new Op("image_to_model", "Image → 3D", In.IMAGE, version(), texture(), pbr(), lowPoly(), quad(), faces(), geometry(), parts(), align(), orient(), seed()),
            new Op("multiview_to_model", "Multiview → 3D", In.IMAGES, version(), texture(), pbr(), lowPoly(), quad(), faces(), geometry(), parts(), align(), orient(), seed()),
            new Op("texture_model", "Texture", In.MODEL, new Param("prompt", "Prompt", T.TEXT, "", true), version(), texture(), pbr(), align(), seed()),
            new Op("refine_model", "Refine", In.MODEL),
            new Op("stylize_model", "Stylize", In.MODEL, new Param("style", "Style", T.CHOICE, "cartoon", true, "cartoon", "clay", "steampunk", "voxel", "lego", "christmas")),
            new Op("retopology", "Retopology / Low poly", In.MODEL, version(), quad(), faces()),
            new Op("smart_uv", "Smart UV", In.MODEL),
            new Op("segment", "Segment", In.MODEL, version()),
            new Op("smart_segment", "Smart segment", In.MODEL),
            new Op("complete_parts", "Complete parts", In.MODEL, new Param("quick", "Quick", T.BOOL, "false", true)),
            new Op("convert", "Convert format", In.MODEL, new Param("format", "Format", T.CHOICE, "glb", true, "glb", "fbx", "obj", "usdz", "stl"),
                    quad(), faces(), new Param("texture_size", "Texture size", T.CHOICE, "2048", false, "512", "1024", "2048", "4096")),
            new Op("rig_check", "Analyze for rigging", In.MODEL),
            new Op("rig", "Rig", In.MODEL, version(), new Param("rig_type", "Skeleton", T.CHOICE, "biped", true, "biped", "quadruped", "hexapod", "octopod", "avian", "serpentine", "aquatic")),
            new Op("animate", "Animation", In.MODEL, new Param("animation", "Animation", T.CHOICE, "preset:idle", true,
                    "preset:idle", "preset:walk", "preset:run", "preset:jump", "preset:climb", "preset:dive", "preset:slash", "preset:shoot", "preset:hurt", "preset:fall", "preset:turn"))
    ));

    public static Op get(String id) {
        for (Op o : ALL) if (o.id.equals(id)) return o;
        return null;
    }

    /** Fills model / version lists, base credits and server-only options from GET /pricing. */
    public static void merge(JsonObject pricing) {
        if (pricing == null) return;
        for (JsonElement e : Suda3D.arr(pricing, "operations")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            Op op = get(Suda3D.str(o, "op"));
            if (op == null) continue;
            op.baseCredits = Suda3D.num(o, "base_credits", -1);
            JsonArray models = Suda3D.arr(o, "models");
            if (models.size() > 0) {
                String[] list = new String[models.size()];
                for (int i = 0; i < list.length; i++) {
                    JsonElement m = models.get(i);
                    list[i] = m.isJsonObject() ? first(m.getAsJsonObject(), "id", "value", "name") : m.getAsString();
                }
                Param p = op.param("model_version");
                if (p == null) p = op.param("model");
                if (p != null) p.choices = list;
            }
            for (JsonElement oe : Suda3D.arr(o, "options")) {
                if (!oe.isJsonObject()) continue;
                JsonObject opt = oe.getAsJsonObject();
                String key = Suda3D.str(opt, "key");
                if (key.isEmpty() || op.param(key) != null) {
                    Param p = op.param(key);
                    JsonArray ch = Suda3D.arr(opt, "choices");
                    if (p != null && ch.size() > 0) {
                        String[] list = new String[ch.size()];
                        for (int i = 0; i < list.length; i++) list[i] = ch.get(i).isJsonObject() ? first(ch.get(i).getAsJsonObject(), "value", "id", "key") : ch.get(i).getAsString();
                        p.choices = list;
                    }
                    continue;
                }
                // option known only by the server: a boolean add-on (e.g. "+HD geometry")
                String type = Suda3D.str(opt, "type");
                T t = "choice".equals(type) ? T.CHOICE : "int".equals(type) ? T.INT : "text".equals(type) ? T.TEXT : T.BOOL;
                Param p = new Param(key, Suda3D.str(opt, "label").isEmpty() ? key : Suda3D.str(opt, "label"), t, t == T.BOOL ? "false" : Suda3D.str(opt, "default"), false);
                if (t == T.CHOICE) {
                    JsonArray ch = Suda3D.arr(opt, "choices");
                    p.choices = new String[ch.size()];
                    for (int i = 0; i < ch.size(); i++) p.choices[i] = ch.get(i).isJsonObject() ? first(ch.get(i).getAsJsonObject(), "value", "id", "key") : ch.get(i).getAsString();
                }
                op.params.add(p);
            }
        }
    }

    static String first(JsonObject o, String... keys) {
        for (String k : keys) {
            String v = Suda3D.str(o, k);
            if (!v.isEmpty()) return v;
        }
        return o.toString();
    }

    /** Merges cached pricing now and refreshes it in the background. */
    public static void load(Context c, Runnable done) {
        merge(Suda3D.cachedPricing(c));
        Suda3D.IO.execute(() -> {
            try { merge(Suda3D.pricing(c)); } catch (Exception ignored) {}
            if (done != null) com.star4droid.star2d.ui.Ui.ui(done);
        });
    }

    /** Params JSON from a key → value map (empty values are left out; booleans / ints typed). */
    public static JsonObject toJson(Op op, java.util.Map<String, String> values) {
        JsonObject o = new JsonObject();
        for (Param p : op.params) {
            String v = values.containsKey(p.key) ? values.get(p.key) : p.def;
            if (v == null || v.isEmpty()) continue;
            if (p.type == T.BOOL) o.addProperty(p.key, Boolean.parseBoolean(v));
            else if (p.type == T.INT) {
                try { o.addProperty(p.key, Long.parseLong(v.trim())); } catch (Exception ignored) {}
            } else o.addProperty(p.key, v);
        }
        return o;
    }
}
