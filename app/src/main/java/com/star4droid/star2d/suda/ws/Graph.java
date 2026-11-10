package com.star4droid.star2d.suda.ws;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.suda.Suda3D;
import com.star4droid.star2d.ui.Tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Workspace graph (saved as workspaces/<id>/graph.json, same shape as the server copy):
 * {name, nodes:[{id,type,x,y,params,state,...}], links:[{from:{node,port},to:{node,port}}]}.
 * Node types and their ports are described by {@link Def}.
 */
public final class Graph {
    // ------------------------------------------------------------ port types

    public static final String IMAGE = "image", IMAGES = "images", MODEL = "model", RIGGED = "rigged", TEXT = "text";

    public static int portColor(String type) {
        switch (type) {
            case IMAGE: return 0xFF4FC3F7;
            case IMAGES: return 0xFF9575CD;
            case MODEL: return 0xFFF5A524;
            case RIGGED: return 0xFFEF5350;
            case TEXT: return 0xFF81C784;
            default: return 0xFFB0B6BF;
        }
    }

    public static String portLabel(String type) {
        switch (type) {
            case IMAGE: return Tr.t("s3d_port_image", "Image");
            case IMAGES: return Tr.t("s3d_port_images", "Multiview");
            case MODEL: return Tr.t("s3d_port_model", "Model");
            case RIGGED: return Tr.t("s3d_port_rigged", "Rigged model");
            default: return Tr.t("s3d_port_text", "Text");
        }
    }

    /** Can an output of type out feed an input of type in? A rigged model is also a model. */
    public static boolean compatible(String out, String in) {
        return out.equals(in) || (MODEL.equals(in) && RIGGED.equals(out));
    }

    public static final class Port {
        public final String id, type;
        public final boolean optional;
        Port(String id, String type, boolean optional) { this.id = id; this.type = type; this.optional = optional; }
    }

    public static final class Def {
        public final String type, title, op;
        public final int icon;
        public final Port[] in, out;
        /** local = no server job (picks a file / holds text / previews). */
        public final boolean local;
        /** Runs on the phone (M2M Motion): free, no server job; its result is a file like a "3D Upload". */
        public boolean onDevice;
        Def device() { onDevice = true; return this; }
        Def(String type, String title, String op, int icon, boolean local, Port[] in, Port[] out) {
            this.type = type; this.title = title; this.op = op; this.icon = icon; this.local = local; this.in = in; this.out = out;
        }
        public String title() { return Tr.t("s3d_node_" + type, title); }
        public Port input(String id) { for (Port p : in) if (p.id.equals(id)) return p; return null; }
        public Port output(String id) { for (Port p : out) if (p.id.equals(id)) return p; return null; }
    }

    static Port p(String id, String t) { return new Port(id, t, false); }
    static Port opt(String id, String t) { return new Port(id, t, true); }
    static final Port[] NONE = {};

    public static final List<Def> DEFS = new ArrayList<>(Arrays.asList(
            new Def("select_image", "Select Image", null, R.drawable.ic_s2_image, true, NONE, new Port[]{p("image", IMAGE)}),
            new Def("upload_3d", "3D Upload", null, R.drawable.ic_s2_upload, true, NONE, new Port[]{p("model", MODEL)}),
            new Def("text", "Text", null, R.drawable.ic_s2_text, true, NONE, new Port[]{p("text", TEXT)}),
            new Def("gen_image", "Generate Image", "text_to_image", R.drawable.ic_s3d_auto_awesome, false, new Port[]{opt("prompt", TEXT), opt("image", IMAGE)}, new Port[]{p("image", IMAGE)}),
            new Def("multiview", "Image → Multiview", "image_to_multiview", R.drawable.ic_s2_view_grid, false, new Port[]{p("image", IMAGE)}, new Port[]{p("images", IMAGES)}),
            new Def("text_to_3d", "Text → 3D", "text_to_model", R.drawable.ic_s2_cube, false, new Port[]{opt("prompt", TEXT)}, new Port[]{p("model", MODEL)}),
            new Def("image_to_3d", "Image → 3D", "image_to_model", R.drawable.ic_s2_view3d, false, new Port[]{p("image", IMAGE)}, new Port[]{p("model", MODEL)}),
            new Def("multiview_to_3d", "Multiview → 3D", "multiview_to_model", R.drawable.ic_s2_layers, false, new Port[]{p("images", IMAGES)}, new Port[]{p("model", MODEL)}),
            new Def("texture", "Texture", "texture_model", R.drawable.ic_s3d_format_paint, false, new Port[]{p("model", MODEL), opt("prompt", TEXT)}, new Port[]{p("model", MODEL)}),
            new Def("refine", "Refine / Stylize", "refine_model", R.drawable.ic_s2_wand, false, new Port[]{p("model", MODEL)}, new Port[]{p("model", MODEL)}),
            new Def("retopology", "Retopology / Low poly", "retopology", R.drawable.ic_s2_grid, false, new Port[]{p("model", MODEL)}, new Port[]{p("model", MODEL)}),
            new Def("smart_uv", "Smart UV", "smart_uv", R.drawable.ic_s2_map, false, new Port[]{p("model", MODEL)}, new Port[]{p("model", MODEL)}),
            new Def("segment", "Segment", "segment", R.drawable.ic_s2_group, false, new Port[]{p("model", MODEL)}, new Port[]{p("model", MODEL)}),
            new Def("rig_check", "Analyze for Rigging", "rig_check", R.drawable.ic_s2_search, false, new Port[]{p("model", MODEL)}, new Port[]{p("model", MODEL)}),
            new Def("rig", "Rig Model", "rig", R.drawable.ic_s2_joint, false, new Port[]{p("model", MODEL)}, new Port[]{p("rigged", RIGGED)}),
            new Def("animate", "Animation", "animate", R.drawable.ic_s2_animation, false, new Port[]{p("rigged", RIGGED)}, new Port[]{p("rigged", RIGGED)}),
            new Def("m2m", "M2M Motion", null, R.drawable.ic_s3d_run, false, new Port[]{p("model", MODEL)}, new Port[]{p("rigged", RIGGED)}).device(),
            new Def("convert", "Convert format", "convert", R.drawable.ic_s2_export, false, new Port[]{p("model", MODEL)}, new Port[]{p("model", MODEL)}),
            new Def("preview", "Preview / Save", null, R.drawable.ic_s2_save, true, new Port[]{p("in", MODEL)}, NONE)
    ));

    public static Def def(String type) {
        for (Def d : DEFS) if (d.type.equals(type)) return d;
        return DEFS.get(0);
    }

    // ------------------------------------------------------------ model

    public static final String IDLE = "idle", QUEUED = "queued", RUNNING = "running", DONE = "done", FAILED = "failed", STALE = "stale";

    public static final class Node {
        public String id, type;
        public float x, y;
        public Map<String, String> params = new LinkedHashMap<>();
        public String state = IDLE, jobId = "", error = "", file = "", uploadId = "";
        public long uploadAt;
        /** When the node was queued to run (ms); a job created after it with this node's tag belongs to it. */
        public long queuedAt;
        public int progress;
        public double credits = -1;
        public JsonArray outputs = new JsonArray();
        public boolean paramsOpen;
        public Def def() { return Graph.def(type); }

        /** Server op of this node (Generate Image switches to image → image, Refine to Stylize). */
        public String op() {
            Def d = def();
            if ("gen_image".equals(type) && "image_to_image".equals(params.get("_mode"))) return "image_to_image";
            if ("refine".equals(type) && "stylize".equals(params.get("_mode"))) return "stylize_model";
            return d.op;
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id); o.addProperty("type", type); o.addProperty("x", x); o.addProperty("y", y);
            JsonObject p = new JsonObject();
            for (Map.Entry<String, String> e : params.entrySet()) p.addProperty(e.getKey(), e.getValue());
            o.add("params", p);
            o.addProperty("state", state); o.addProperty("job_id", jobId); o.addProperty("error", error); o.addProperty("file", file);
            o.addProperty("upload_id", uploadId); o.addProperty("upload_at", uploadAt); o.addProperty("queued_at", queuedAt); o.addProperty("progress", progress);
            o.addProperty("credits", credits); o.add("outputs", outputs); o.addProperty("params_open", paramsOpen);
            return o;
        }

        static Node from(JsonObject o) {
            Node n = new Node();
            n.id = Suda3D.str(o, "id"); n.type = Suda3D.str(o, "type");
            n.x = (float) Suda3D.num(o, "x", 0); n.y = (float) Suda3D.num(o, "y", 0);
            JsonObject p = Suda3D.obj(o, "params");
            if (p != null) for (Map.Entry<String, JsonElement> e : p.entrySet()) n.params.put(e.getKey(), e.getValue().isJsonNull() ? "" : e.getValue().getAsString());
            n.state = Suda3D.str(o, "state").isEmpty() ? IDLE : Suda3D.str(o, "state");
            n.jobId = Suda3D.str(o, "job_id"); n.error = Suda3D.str(o, "error"); n.file = Suda3D.str(o, "file");
            n.uploadId = Suda3D.str(o, "upload_id"); n.uploadAt = (long) Suda3D.num(o, "upload_at", 0); n.queuedAt = (long) Suda3D.num(o, "queued_at", 0);
            n.progress = (int) Suda3D.num(o, "progress", 0); n.credits = Suda3D.num(o, "credits", -1);
            n.outputs = Suda3D.arr(o, "outputs"); n.paramsOpen = Suda3D.bool(o, "params_open");
            return n;
        }
    }

    public static final class Link {
        public String fromNode, fromPort, toNode, toPort;
        public Link(String fn, String fp, String tn, String tp) { fromNode = fn; fromPort = fp; toNode = tn; toPort = tp; }
    }

    public String name = "";
    public final List<Node> nodes = new ArrayList<>();
    public final List<Link> links = new ArrayList<>();
    private int seq;

    public Node node(String id) {
        for (Node n : nodes) if (n.id.equals(id)) return n;
        return null;
    }

    public Node add(String type, float x, float y) {
        Node n = new Node();
        n.id = "n" + System.currentTimeMillis() % 100000000 + "_" + (seq++);
        n.type = type;
        n.x = x;
        n.y = y;
        nodes.add(n);
        return n;
    }

    public void remove(Node n) {
        nodes.remove(n);
        links.removeIf(l -> l.fromNode.equals(n.id) || l.toNode.equals(n.id));
    }

    /** The link feeding an input, or null. */
    public Link into(String node, String port) {
        for (Link l : links) if (l.toNode.equals(node) && l.toPort.equals(port)) return l;
        return null;
    }

    /** Connects (an input takes one link: an older one is replaced). */
    public void connect(String fn, String fp, String tn, String tp) {
        links.removeIf(l -> l.toNode.equals(tn) && l.toPort.equals(tp));
        links.add(new Link(fn, fp, tn, tp));
    }

    /** Nodes fed by n (directly or not). */
    public List<Node> downstream(Node n) {
        List<Node> out = new ArrayList<>();
        ArrayList<String> todo = new ArrayList<>();
        todo.add(n.id);
        while (!todo.isEmpty()) {
            String id = todo.remove(0);
            for (Link l : links) if (l.fromNode.equals(id)) {
                Node d = node(l.toNode);
                if (d != null && !out.contains(d)) { out.add(d); todo.add(d.id); }
            }
        }
        return out;
    }

    /** Would linking from → to make a cycle? */
    public boolean cycle(String from, String to) {
        if (from.equals(to)) return true;
        Node t = node(to);
        return t != null && downstream(t).contains(node(from));
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        JsonArray ns = new JsonArray();
        for (Node n : nodes) ns.add(n.toJson());
        o.add("nodes", ns);
        JsonArray ls = new JsonArray();
        for (Link l : links) {
            JsonObject lo = new JsonObject(), f = new JsonObject(), t = new JsonObject();
            f.addProperty("node", l.fromNode); f.addProperty("port", l.fromPort);
            t.addProperty("node", l.toNode); t.addProperty("port", l.toPort);
            lo.add("from", f); lo.add("to", t);
            ls.add(lo);
        }
        o.add("links", ls);
        return o;
    }

    public static Graph parse(String json) {
        Graph g = new Graph();
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            g.name = Suda3D.str(o, "name");
            for (JsonElement e : Suda3D.arr(o, "nodes")) g.nodes.add(Node.from(e.getAsJsonObject()));
            for (JsonElement e : Suda3D.arr(o, "links")) {
                JsonObject l = e.getAsJsonObject(), f = Suda3D.obj(l, "from"), t = Suda3D.obj(l, "to");
                if (f != null && t != null) g.links.add(new Link(Suda3D.str(f, "node"), Suda3D.str(f, "port"), Suda3D.str(t, "node"), Suda3D.str(t, "port")));
            }
        } catch (Exception ignored) {}
        return g;
    }
}
