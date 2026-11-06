package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.ColorPickerDialog;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.FilePickerDialog;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.star2d.ui.UiMakerLauncher;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Properties of the selected element, grouped in collapsible sections (same schema files as the
 * LibGDX properties window: map.json, types.json, spinners.json). Every change goes through
 * {@link PropertyEditing} so undo/redo and item refresh behave exactly as before.
 */
public class PropertiesPanel implements PanelHost.Panel {
    private final EditorShell shell;
    private ScrollView root;
    private LinearLayout content;
    private TextView header;

    private static LinkedHashMap<String, String[]> sections, sections3d;
    private static HashMap<String, String> typeOf;
    private static HashMap<String, String[]> spinners;

    // ---- search (the magnifier in the panel header): hides the fields that do not match
    private LinearLayout wrapper;
    private LinearLayout searchBar;
    private EditText searchField;
    private String query = "";
    private static final class Sec { View card, body; String title; ArrayList<View> rows = new ArrayList<>(); ArrayList<String> texts = new ArrayList<>(); }
    private final ArrayList<Sec> searchSecs = new ArrayList<>();
    private int behaviorFirstChild = -1;
    private String selectedName = null;
    private LinkedHashMap<String, String> values = new LinkedHashMap<>();
    private final HashMap<String, View> editors = new HashMap<>();
    private final HashSet<String> collapsed = new HashSet<>();
    private String fieldsSignature = "";
    private com.star4droid.star2d.ui.behaviors.BehaviorSection behaviorSection;
    private final Runnable poll = this::pollValues;
    // prefab instance bar (see updateInstanceBar)
    private LinearLayout instanceBar;
    private final HashSet<String> overridden = new HashSet<>();
    private String instKey = "";
    private int infoSeq;
    private final Runnable infoRefresh = this::refreshInstance;
    private static final java.util.concurrent.ExecutorService INFO_IO = java.util.concurrent.Executors.newSingleThreadExecutor();

    public PropertiesPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("properties", "Properties"); }
    @Override public int icon() { return R.drawable.ic_s2_properties; }

    private static void loadSchema(Context c) {
        if (sections != null) return;
        sections = new LinkedHashMap<>();
        typeOf = new HashMap<>();
        spinners = new HashMap<>();
        try {
            Map<String, String> map = readJson(c, "map.json");
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey().equals("static")) continue;
                ArrayList<String> fields = new ArrayList<>();
                for (String f : e.getValue().split(",")) if (!f.trim().isEmpty()) fields.add(f.trim());
                sections.put(e.getKey(), fields.toArray(new String[0]));
            }
            sections3d = new LinkedHashMap<>();
            Map<String, String> map3 = readJson(c, "map3d.json");
            for (Map.Entry<String, String> e : map3.entrySet()) {
                if (e.getKey().equals("static")) continue;
                ArrayList<String> fields = new ArrayList<>();
                for (String f : e.getValue().split(",")) if (!f.trim().isEmpty()) fields.add(f.trim());
                sections3d.put(e.getKey(), fields.toArray(new String[0]));
            }
            Map<String, String> types = readJson(c, "types.json");
            for (Map.Entry<String, String> e : types.entrySet())
                for (String f : e.getValue().split(",")) if (!f.trim().isEmpty()) typeOf.put(f.trim(), e.getKey());
            Map<String, String> sp = readJson(c, "spinners.json");
            for (Map.Entry<String, String> e : sp.entrySet()) spinners.put(e.getKey(), e.getValue().split(","));
        } catch (Exception e) {
            Ui.toast(c, "schema: " + e);
        }
    }

    private static Map<String, String> readJson(Context c, String name) throws Exception {
        try (InputStream in = c.getAssets().open(name)) {
            byte[] b = new byte[in.available()];
            int r = in.read(b);
            String s = new String(b, 0, r, "UTF-8");
            return new Gson().fromJson(s, new TypeToken<LinkedHashMap<String, String>>() {}.getType());
        }
    }

    @Override
    public View view(Context c) {
        loadSchema(c);
        if (wrapper != null) return wrapper;
        root = new ScrollView(c);
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 20));
        header = Ui.text(c, "", 13, Ui.TEXT_3);
        header.setPadding(Ui.dp(c, 4), Ui.dp(c, 2), 0, Ui.dp(c, 4));
        col.addView(header);
        instanceBar = Ui.column(c);
        instanceBar.setVisibility(View.GONE);
        col.addView(instanceBar, Ui.matchWrap());
        content = Ui.column(c);
        col.addView(content, Ui.matchWrap());
        root.addView(col);

        // search bar on top (hidden until the magnifier in the header is tapped), works in portrait and landscape
        wrapper = Ui.column(c);
        searchBar = Ui.row(c);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(Ui.dp(c, 10), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 6));
        searchBar.setVisibility(View.GONE);
        searchBar.addView(Ui.smallIcon(c, R.drawable.ic_s2_search, Ui.TEXT_3, 18));
        searchField = Ui.input(c, "", Tr.t("ui_prop_search", "Search properties..."));
        searchField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchField.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b, int d, int e) {}
            public void onTextChanged(CharSequence a, int b, int d, int e) {}
            public void afterTextChanged(android.text.Editable e) { query = e.toString().trim().toLowerCase(); applySearch(); }
        });
        searchBar.addView(searchField, Ui.margins(Ui.weight(1), c, 8, 0, 4, 0));
        searchBar.addView(Ui.iconButton(c, R.drawable.ic_s2_close, Tr.t("close", "Close"), v -> setSearchOpen(false)));
        wrapper.addView(searchBar, Ui.matchWrap());
        wrapper.addView(root, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return wrapper;
    }

    @Override public View[] headerActions(Context c) {
        ImageView search = Ui.iconButton(c, R.drawable.ic_s2_search, Tr.t("ui_prop_search_btn", "Search"), v -> setSearchOpen(searchBar == null || searchBar.getVisibility() != View.VISIBLE));
        ImageView toBehaviors = Ui.iconButton(c, R.drawable.ic_s2_layers, Tr.t("ui_prop_to_behaviors", "Go to behaviors"), v -> scrollToBehaviors());
        return new View[]{toBehaviors, search};
    }

    /** The layers icon in the header: scrolls down to the behaviors cards. */
    private void scrollToBehaviors() {
        if (root == null || content == null || behaviorSection == null) return;
        final View target = behaviorFirstChild < content.getChildCount() ? content.getChildAt(behaviorFirstChild) : null;
        if (target == null) return;
        root.post(new Runnable() { public void run() { root.smoothScrollTo(0, Math.max(0, content.getTop() + target.getTop() - 4)); } });
    }

    private void setSearchOpen(boolean open) {
        if (searchBar == null) return;
        searchBar.setVisibility(open ? View.VISIBLE : View.GONE);
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) searchField.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (open) {
            searchField.requestFocus();
            if (imm != null) imm.showSoftInput(searchField, 0);
        } else {
            searchField.setText(""); // also clears the filter
            if (imm != null) imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        }
    }

    /** Show only the fields (and sections) that match the query; an empty query restores everything. */
    private void applySearch() {
        boolean on = !query.isEmpty();
        for (Sec sc : searchSecs) {
            boolean titleHit = on && sc.title.toLowerCase().contains(query);
            int shown = 0;
            for (int i = 0; i < sc.rows.size(); i++) {
                boolean hit = !on || titleHit || sc.texts.get(i).contains(query);
                sc.rows.get(i).setVisibility(hit ? View.VISIBLE : View.GONE);
                if (hit) shown++;
            }
            sc.card.setVisibility(!on || shown > 0 ? View.VISIBLE : View.GONE);
            // while searching, matches are shown even inside a collapsed section
            sc.body.setVisibility(on ? View.VISIBLE : (collapsed.contains(sc.title) ? View.GONE : View.VISIBLE));
        }
        if (behaviorFirstChild >= 0 && content != null)
            for (int i = behaviorFirstChild; i < content.getChildCount(); i++) content.getChildAt(i).setVisibility(on ? View.GONE : View.VISIBLE);
    }

    @Override public void onShown() {
        onSelectionChanged();
        root.removeCallbacks(poll);
        root.postDelayed(poll, 500);
    }

    @Override public void onHidden() {
        if (root != null) root.removeCallbacks(poll);
    }

    public void onSelectionChanged() {
        if (!shell.panels.isShowing(this) || !Bridge.ready()) return;
        Bridge.query(this::snapshot, this::render);
    }

    /** While visible, refresh values (e.g. after dragging on the canvas). */
    private void pollValues() {
        if (root == null || !shell.panels.isShowing(this)) return;
        if (Bridge.ready()) Bridge.query(this::snapshot, snap -> {
            if (snap == null || selectedName == null || fieldsSignature.isEmpty() || !selectedName.equals(snap[0])
                    || !fieldsSignature.endsWith("|" + shapeSignature((LinkedHashMap<String, String>) snap[1]))) render(snap);
            else updateValues((LinkedHashMap<String, String>) snap[1]);
        });
        root.postDelayed(poll, 450);
    }

    /** [name, values, type] or null when nothing is selected. GL thread. */
    private Object[] snapshot() {
        Actor a = Bridge.editor().getSelectedActor();
        if (a == null) return null;
        PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
        String type = "";
        try { type = ((com.star4droid.star2d.editor.items.EditorItem) a).getTypeName(); } catch (Throwable ignored) {}
        return new Object[]{a.getName(), Bridge.snapshot(ps), type};
    }

    private void render(Object[] snap) {
        if (content == null) return;
        Context c = content.getContext();
        if (snap == null) {
            selectedName = null;
            fieldsSignature = "";
            content.removeAllViews();
            searchSecs.clear();
            behaviorFirstChild = -1;
            behaviorSection = null;
            editors.clear();
            header.setText("");
            hideInstanceBar();
            TextView t = Ui.text(c, Tr.t("ui_select_hint", "Select an element on the canvas or in the Elements list to edit its properties."), 14, Ui.TEXT_3);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(c, 10), Ui.dp(c, 30), Ui.dp(c, 10), 0);
            content.addView(t);
            return;
        }
        String name = (String) snap[0];
        LinkedHashMap<String, String> v = (LinkedHashMap<String, String>) snap[1];
        String sig = name + "|" + android.text.TextUtils.join(",", v.keySet()) + "|" + shapeSignature(v);
        selectedName = name;
        updateInstanceBar(name, v, (String) snap[2]);
        if (sig.equals(fieldsSignature)) {
            updateValues(v);
            return;
        }
        fieldsSignature = sig;
        selectedName = name;
        values = v;
        editors.clear();
        content.removeAllViews();
        searchSecs.clear();
        behaviorFirstChild = -1;
        behaviorSection = null;
        String type = (String) snap[2];
        header.setText(Tr.t("ui_type_" + (type == null ? "" : type.toLowerCase()), type) + " · " + name);

        LinkedHashMap<String, String[]> secs = is3dType(type) && sections3d != null && !sections3d.isEmpty() ? sections3d : sections;
        for (Map.Entry<String, String[]> sec : secs.entrySet()) {
            ArrayList<String> present = new ArrayList<>();
            for (String f : sec.getValue()) if (v.containsKey(f) && (secs != sections3d || shows3d(type, f, v))) present.add(f);
            if (present.isEmpty()) continue;
            addSection(c, sec.getKey(), present);
        }
        // fields not covered by the schema (keep them editable as text)
        ArrayList<String> extra = new ArrayList<>();
        HashSet<String> known = new HashSet<>();
        for (String[] fs : secs.values()) for (String f : fs) known.add(f);
        if (secs == sections3d) known.addAll(java.util.Arrays.asList("type", "Pivot", "Pivot X", "Pivot Y", "Version", "TYPE"));
        for (String k : v.keySet())
            if (!known.contains(k) && typeOf.containsKey(k) && !"static".equals(typeOf.get(k))) extra.add(k);
        if (!extra.isEmpty()) addSection(c, "Other", extra);
        // public variables of the Body Script + "Additional behaviors" (stored in the element's scriptVars / behaviors)
        if (!"LIGHT".equals(v.get("TYPE"))) {
            behaviorFirstChild = content.getChildCount();
            behaviorSection = new com.star4droid.star2d.ui.behaviors.BehaviorSection(shell, content, name, v.get("behaviors"), v.get("scriptVars"));
        }
        applySearch();
    }

    private void addSection(Context c, String title, ArrayList<String> fields) {
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), Ui.BORDER, 1));
        LinearLayout.LayoutParams lp = Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 4);
        content.addView(card, lp);

        LinearLayout head = Ui.row(c);
        head.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        head.setBackground(Ui.ripple(0, Ui.dp(c, 10)));
        TextView t = Ui.text(c, Tr.t(title), 14, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(t, Ui.weight(1));
        ImageView arrow = Ui.smallIcon(c, collapsed.contains(title) ? R.drawable.ic_s2_chevron_right : R.drawable.ic_s2_expand_more, Ui.TEXT_2, 20);
        head.addView(arrow);
        card.addView(head);

        LinearLayout body = Ui.column(c);
        body.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 8));
        body.setVisibility(collapsed.contains(title) ? View.GONE : View.VISIBLE);
        card.addView(body);
        final Sec sec = new Sec();
        sec.card = card;
        sec.body = body;
        sec.title = Tr.t(title);
        searchSecs.add(sec);
        head.setOnClickListener(x -> {
            boolean col = body.getVisibility() == View.VISIBLE;
            body.setVisibility(col ? View.GONE : View.VISIBLE);
            Ui.setDirIcon(arrow, col ? R.drawable.ic_s2_chevron_right : R.drawable.ic_s2_expand_more);
            if (col) collapsed.add(title); else collapsed.remove(title);
        });

        // pair x/y style floats on one row
        for (int i = 0; i < fields.size(); i++) {
            String f = fields.get(i);
            String type = typeOf.get(f);
            if ("float".equals(type) && i + 1 < fields.size() && "float".equals(typeOf.get(fields.get(i + 1))) && isPair(f, fields.get(i + 1))) {
                LinearLayout pair = Ui.row(c);
                pair.addView(field(c, f, type), Ui.weight(1));
                View sp = new View(c);
                pair.addView(sp, new LinearLayout.LayoutParams(Ui.dp(c, 10), 1));
                pair.addView(field(c, fields.get(i + 1), type), Ui.weight(1));
                body.addView(pair, Ui.matchWrap());
                sec.rows.add(pair);
                sec.texts.add(searchText(f) + " " + searchText(fields.get(i + 1)));
                i++;
            } else {
                View row = field(c, f, type);
                body.addView(row, Ui.matchWrap());
                sec.rows.add(row);
                sec.texts.add(searchText(f));
            }
        }
        if ("Physics 3D".equals(title) && "OBJ3D".equals(values.get("TYPE")) && values.get("Body") != null && !"NONE".equalsIgnoreCase(values.get("Body"))
                && !"EMPTY".equalsIgnoreCase(String.valueOf(values.get("Mesh")))) {
            // collider editor: handles on the collider faces in the 3D view
            LinearLayout b = Ui.button(c, R.drawable.ic_s2_collider, Tr.t("col3d_edit", "Edit collider"), true, null);
            b.setOnClickListener(x -> Bridge.query(() -> {
                com.star4droid.star2d.editor.three.Editor3D e = Bridge.editor().get3D();
                if (e == null) return null;
                if (e.edit2D) e.setEdit2D(false);
                return e.toggleColliderEdit();
            }, on -> {
                if (on == null) return;
                Ui.toast(c, on ? Tr.t("col3d_on", "Drag the round handles on the sides of the collider. Tap empty space or this button again when done.")
                        : Tr.t("col3d_off", "Collider editing finished"));
                if (on && shell.panels != null) shell.panels.hide();
            }));
            body.addView(b, Ui.margins(Ui.matchWrap(), c, 0, 10, 0, 4));
            sec.rows.add(b);
            sec.texts.add((Tr.t("col3d_edit", "Edit collider") + " collider").toLowerCase());
        }
    }

    /** What a search matches for a field: its raw name and its (translated) label, lower case. */
    private static String searchText(String f) {
        return (f + " " + Tr.t(f)).toLowerCase();
    }

    private boolean isPair(String a, String b) {
        String[][] pairs = {{"x", "y"}, {"width", "height"}, {"Scale X", "Scale Y"}, {"Offset X", "Offset Y"}, {"ColliderX", "ColliderY"},
                {"Collider Width", "Collider Height"}, {"tileX", "tileY"}, {"friction", "restitution"}, {"Progress", "Max"}};
        for (String[] p : pairs) if (p[0].equals(a) && p[1].equals(b)) return true;
        return false;
    }

    private View label(Context c, String f) {
        TextView l = Ui.text(c, Tr.t(f), 12.5f, Ui.TEXT_2);
        l.setPadding(Ui.dp(c, 2), Ui.dp(c, 8), 0, Ui.dp(c, 4));
        String tip = PropertyTips.tip(f, Ui.isRtl(c));
        if (tip != null) {
            l.setText(Tr.t(f) + "  ⓘ");
            l.setOnClickListener(v -> new AlertDialog.Builder(c).setTitle(Tr.t(f)).setMessage(tip).setPositiveButton(Tr.t("ok", "OK"), null).show());
        }
        if (overridden.contains(f)) {
            // differs from the prefab
            l.setText(l.getText() + "  ● " + Tr.t("prefab_modified_mark", "modified"));
            l.setTextColor(com.star4droid.star2d.ui.PrefabUi.COLOR);
            l.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return l;
    }

    static final String SHADER_TEMPLATE = "// Star2D 3D shader. Write the color of every pixel:\n"
            + "//   color = the element color (x texture), uv = texture position, normal, worldPos.\n"
            + "// Ready to use: u_time, u_cameraPos, u_lightDir, u_lightColor, u_ambient, lambert(normal), hasTexture().\n"
            + "// Values of \"Shader Params\" (speed=2;tint=#ff0000) become u_speed (float) / u_tint (vec4).\n"
            + "// \u0634\u064a\u062f\u0631 Star2D: \u0627\u0643\u062a\u0628 \u0644\u0648\u0646 \u0643\u0644 \u0628\u0643\u0633\u0644 \u0641\u064a effect().\n\n"
            + "vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos) {\n"
            + "    float pulse = 0.5 + 0.5 * sin(u_time * 2.0);\n"
            + "    return vec4(color.rgb * lambert(normal) + color.rgb * pulse * 0.2, color.a);\n"
            + "}\n\n"
            + "// Optional: move the vertices\n"
            + "// vec3 displace(vec3 pos, vec3 normal) { return pos + normal * sin(u_time + pos.y * 4.0) * 0.03; }\n";

    /** The values that decide which 3D properties are shown: a change rebuilds the panel. */
    private static String shapeSignature(Map<String, String> v) {
        return v.get("Mesh") + "," + v.get("Light Kind") + "," + v.get("Orthographic") + "," + v.get("Body") + "," + v.get("Collider")
                + "," + v.get("Outline") + "," + v.get("Follow Mode") + "," + (num(v.get("Flicker")) != 0) + "," + v.get("Touch Look");
    }

    private static float num(String s) {
        try { return Float.parseFloat(s); } catch (Throwable e) { return 0; }
    }

    private static boolean in(String v, String... list) {
        for (String s : list) if (s.equalsIgnoreCase(v)) return true;
        return false;
    }

    /** Only the properties that mean something for this element (its Mesh, Light Kind, camera kind, body...). */
    static boolean shows3d(String type, String f, Map<String, String> v) {
        if ("OBJ3D".equals(type)) {
            String mesh = v.get("Mesh") == null ? "CUBE" : v.get("Mesh").toUpperCase();
            boolean model = mesh.equals("MODEL"), empty = mesh.equals("EMPTY");
            switch (f) {
                case "Light Kind": return false;
                case "Model": case "Model Node": case "Keep Model Material":
                case "Animation": case "Animation Loop": case "Animation Speed": return model;
                case "Detail": return in(mesh, "SPHERE", "CYLINDER", "CAPSULE", "CONE", "TORUS", "HEMISPHERE", "TUBE", "DISC");
                case "Steps": return mesh.equals("STAIRS");
                case "Heightmap": case "Terrain Height": case "Terrain Resolution": case "Terrain Noise": case "Terrain Seed": return mesh.equals("TERRAIN");
                case "Water Color": case "Water Deep Color": case "Foam Color": case "Wave Height": case "Wave Speed": case "Wave Length":
                case "Wave Direction": case "Water Clarity": case "Water Resolution": case "Foam": case "Water Quality": case "Buoyancy":
                    return mesh.equals("WATER");
                case "Outline Color": case "Outline Width": return !empty && "true".equals(v.get("Outline"));
            }
            if (empty) {
                // a group / pivot: transform, visibility and physics only
                if (in(f, "Color", "Texture", "Texture Repeat X", "Texture Repeat Y", "Normal Map", "Opacity", "Metallic", "Roughness", "Shininess",
                        "Emissive", "Emissive Intensity", "Unlit", "Double Sided", "Cast Shadows", "Receive Shadows", "Outline", "Billboard",
                        "Render Order", "Shader", "Shader Params", "LOD Distance")) return false;
            }
            String body = v.get("Body") == null ? "NONE" : v.get("Body").toUpperCase();
            if (!f.equals("Body") && in(f, "Collider", "Mass", "Friction", "Restitution", "Linear Damping", "Angular Damping", "Gravity Scale",
                    "Collider Scale X", "Collider Scale Y", "Collider Scale Z", "Collider Offset X", "Collider Offset Y", "Collider Offset Z",
                    "Lock Move X", "Lock Move Y", "Lock Move Z", "Lock Rotation X", "Lock Rotation Y", "Lock Rotation Z", "Continuous Collision",
                    "Sleep", "Layer", "Ignore Layers")) {
                if (body.equals("NONE")) return false;
                boolean dyn = in(body, "DYNAMIC");
                if (in(f, "Mass", "Linear Damping", "Angular Damping", "Gravity Scale", "Lock Move X", "Lock Move Y", "Lock Move Z",
                        "Lock Rotation X", "Lock Rotation Y", "Lock Rotation Z", "Continuous Collision", "Sleep")) return dyn;
                if (in(f, "Friction", "Restitution")) return !body.equals("TRIGGER");
            }
            return true;
        }
        if ("LIGHT3D".equals(type)) {
            String kind = v.get("Light Kind") == null ? "POINT" : v.get("Light Kind").toUpperCase();
            switch (f) {
                case "Mesh": case "Model": case "Model Node": case "Detail": case "Steps": return false;
                case "Cone Angle": case "Cone Softness": return kind.equals("SPOT");
                case "Range": return !kind.equals("DIRECTIONAL");
                case "Flicker Speed": return num(v.get("Flicker")) != 0;
            }
            return true;
        }
        if ("CAMERA3D".equals(type)) {
            boolean ortho = "true".equals(v.get("Orthographic"));
            boolean following = !"NONE".equalsIgnoreCase(String.valueOf(v.get("Follow Mode")));
            switch (f) {
                case "Mesh": case "Model": case "Model Node": case "Detail": case "Steps": case "Light Kind": return false;
                case "Ortho Size": return ortho;
                case "FOV": return !ortho;
                case "Follow Distance": case "Follow Height": case "Follow Smooth": case "Look Height": case "Follow Angle":
                    return following;
                case "Look Sensitivity": return "true".equals(v.get("Touch Look"));
            }
            return true;
        }
        return true;
    }

    /** Element types of a 3D scene (their own property sections, map3d.json). */
    public static boolean is3dType(String type) {
        return "OBJ3D".equals(type) || "LIGHT3D".equals(type) || "CAMERA3D".equals(type);
    }

    private View field(Context c, String f, String type) {
        View v = fieldIn(c, f, type);
        com.star4droid.star2d.ui.tutorial.Tut.tag(v, "prop:" + f);
        View ed = editors.get(f);
        if (ed != null && ed != v) com.star4droid.star2d.ui.tutorial.Tut.tag(ed, "propedit:" + f);
        return v;
    }

    private View fieldIn(Context c, String f, String type) {
        LinearLayout box = Ui.column(c);
        if (type == null) type = "string";
        String val = values.get(f);
        if (val == null) val = "";
        switch (type) {
            case "boolean": {
                SwitchCompat s = new SwitchCompat(c);
                s.setText(Tr.t(f));
                s.setTextColor(Ui.TEXT);
                s.setTextSize(14);
                s.setChecked("true".equals(val));
                s.setMinHeight(Ui.dp(c, 44));
                s.setOnCheckedChangeListener((b, on) -> {
                    if (on == "true".equals(values.get(f))) return;
                    values.put(f, String.valueOf(on));
                    commit(() -> PropertyEditing.applyBoolean(Bridge.app(), f, String.valueOf(on)));
                });
                editors.put(f, s);
                String tip = PropertyTips.tip(f, Ui.isRtl(c));
                if (tip != null) s.setOnLongClickListener(v -> { Ui.toast(c, tip); return true; });
                return s;
            }
            case "float":
            case "string": {
                box.addView(label(c, f));
                EditText e = Ui.input(c, display(type, val), "");
                if (type.equals("float"))
                    e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                else if (f.equals("Text")) {
                    e.setSingleLine(false);
                    e.setMaxLines(4);
                    e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                }
                e.setImeOptions(EditorInfo.IME_ACTION_DONE);
                final String ft = type;
                Runnable apply = () -> {
                    String nv = e.getText().toString();
                    String old = values.get(f);
                    if (nv.equals(display(ft, old == null ? "" : old))) return;
                    if (ft.equals("float")) {
                        float fl;
                        try { fl = Float.parseFloat(nv.trim()); } catch (Exception ex) {
                            e.setError(Tr.t("ui_number", "Enter a number"));
                            return;
                        }
                        values.put(f, String.valueOf(fl));
                        commit(() -> PropertyEditing.applyFloat(Bridge.app(), f, fl));
                    } else {
                        values.put(f, nv);
                        Bridge.query(() -> PropertyEditing.applyString(Bridge.app(), f, nv), err -> {
                            if (err != null) {
                                Ui.toast(c, err);
                                values.put(f, old);
                                e.setText(old);
                            } else if (f.equals("name")) {
                                selectedName = nv;
                                shell.elements.refresh();
                            }
                        });
                    }
                };
                e.setOnEditorActionListener((tv, id, ev) -> { apply.run(); e.clearFocus(); return false; });
                e.setOnFocusChangeListener((v, has) -> { if (!has) apply.run(); });
                if (ft.equals("float")) {
                    // numbers: keypad dialog instead of the keyboard
                    e.setFocusable(false);
                    e.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_s2_dialpad, 0);
                    e.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT_3));
                    e.setOnClickListener(v -> com.star4droid.star2d.ui.NumberPad.show(c, Tr.t(f), e.getText().toString(), nv -> {
                        e.setText(nv);
                        apply.run();
                    }, null));
                }
                if (ft.equals("string") && !f.equals("name")) {
                    // text: a roomy dialog (any length, many lines) instead of a one line field
                    e.setFocusable(false);
                    e.setMaxLines(3);
                    e.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    e.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_s2_edit, 0);
                    e.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT_3));
                    e.setOnClickListener(v -> com.star4droid.star2d.ui.TextDialog.show(c, Tr.t(f), "", values.get(f) == null ? "" : values.get(f), nv -> {
                        e.setText(nv);
                        apply.run();
                    }));
                }
                box.addView(e, Ui.matchWrap());
                editors.put(f, e);
                return box;
            }
            case "spinner": {
                box.addView(label(c, f));
                TextView t = chooser(c, val);
                t.setOnClickListener(v -> {
                    String[] opts = spinners.get(f);
                    if (opts == null) return;
                    com.star4droid.star2d.ui.IconMenu m = new com.star4droid.star2d.ui.IconMenu(c);
                    for (String o : opts) {
                        boolean cur = o.equals(values.get(f));
                        m.add(cur ? R.drawable.ic_s2_check : 0, Ui.ACCENT, Tr.t(o, o), () -> {
                            String nv = o;
                            if (nv.equals(values.get(f))) return;
                            values.put(f, nv);
                            t.setText(Tr.t(nv, nv));
                            commit(() -> PropertyEditing.applySpinner(Bridge.app(), f, nv));
                            if (f.equals("Shape") || f.equals("Light Type")) {
                                fieldsSignature = "";
                                t.postDelayed(this::onSelectionChanged, 250);
                            }
                        }).tagLast("menu:" + o);
                    }
                    m.show(t);
                });
                t.setText(Tr.t(val, val));
                box.addView(t, Ui.matchWrap());
                editors.put(f, t);
                return box;
            }
            case "color": {
                box.addView(label(c, f));
                LinearLayout r = Ui.row(c);
                View sw = new View(c);
                sw.setBackground(Ui.rounded(ColorPickerDialog.parse(val), Ui.dp(c, 6), Ui.BORDER, 1));
                r.addView(sw, new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 30)));
                TextView t = Ui.text(c, val, 14, Ui.TEXT);
                t.setTypeface(Typeface.MONOSPACE);
                t.setPadding(Ui.dp(c, 10), 0, 0, 0);
                r.addView(t, Ui.weight(1));
                r.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
                r.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 7)));
                r.setOnClickListener(v -> ColorPickerDialog.show(c, Tr.t(f), values.get(f), null, hex -> {
                    if (hex == null) return;
                    values.put(f, hex);
                    t.setText(hex);
                    sw.setBackground(Ui.rounded(ColorPickerDialog.parse(hex), Ui.dp(c, 6), Ui.BORDER, 1));
                    commit(() -> PropertyEditing.applyColor(Bridge.app(), f, hex));
                }));
                box.addView(r, Ui.matchWrap());
                editors.put(f, r);
                return box;
            }
            case "image": {
                box.addView(label(c, f));
                LinearLayout r = Ui.row(c);
                r.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
                r.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 7)));
                ImageView iv = Ui.smallIcon(c, R.drawable.ic_s2_image, Ui.TEXT_3, 40);
                r.addView(iv);
                TextView t = Ui.text(c, val.isEmpty() ? "[" + Tr.t("choose", "choose") + "]" : val, 13.5f, Ui.TEXT);
                t.setPadding(Ui.dp(c, 10), 0, 0, 0);
                t.setMaxLines(2);
                r.addView(t, Ui.weight(1));
                // reset (back to the default picture) / clear (touch images): only visible while the value is not the default ("")
                final boolean touchImg = f.equals("Pressed Image") || f.equals("Touch Start Image") || f.equals("Touch End Image");
                final ImageView resetBtn = Ui.iconButton(c, touchImg ? R.drawable.ic_s2_close : R.drawable.ic_s2_refresh,
                        touchImg ? Tr.t("img_clear", "Clear") : Tr.t("img_reset", "Reset image"), null);
                resetBtn.setVisibility(val.isEmpty() ? View.GONE : View.VISIBLE);
                ImageView bmIcon = Ui.iconButton(c, R.drawable.ic_s2_palette, Tr.t("bm_menu", "Button Maker"), v -> Bridge.query(() -> Bridge.editor().getProject().getImagesPath(), imgs ->
                        shell.panels.show(new ButtonMakerPanel(shell, new File(imgs), null, null, new ButtonMakerPanel.OnSaved() {
                            @Override public void on(String rel) {
                                values.put(f, rel);
                                resetBtn.setVisibility(View.VISIBLE);
                                commit(() -> PropertyEditing.applyImage(Bridge.app(), f, rel));
                            }
                            @Override public void on(String rel, String pressedRel) {
                                on(rel);
                                // a BOX button whose image came from the Button Maker with a pressed twin: use the twin as its Pressed Image
                                if (pressedRel != null && f.equals("image") && values.containsKey("Pressed Image")) {
                                    values.put("Pressed Image", pressedRel);
                                    fieldsSignature = "";
                                    commit(() -> PropertyEditing.applyImage(Bridge.app(), "Pressed Image", pressedRel));
                                }
                            }
                        }))));
                r.addView(bmIcon);
                ImageView iconLib = Ui.iconButton(c, R.drawable.ic_s2_widgets, Tr.t("icn_title", "Icons"), v -> Bridge.query(() -> Bridge.editor().getProject().getPath(), projectPath ->
                        com.star4droid.star2d.ui.IconBrowser.show(shell.activity(), new File(projectPath), true, (useAs, file) -> {
                            values.put(f, useAs);
                            t.setText(useAs);
                            FilePickerDialog.thumb(iv, file, Ui.dp(c, 40));
                            resetBtn.setVisibility(View.VISIBLE);
                            commit(() -> PropertyEditing.applyImage(Bridge.app(), f, useAs));
                        })));
                r.addView(iconLib);
                r.addView(resetBtn);
                resetBtn.setOnClickListener(v -> {
                    values.put(f, "");
                    t.setText("[" + Tr.t("choose", "choose") + "]");
                    iv.setImageResource(R.drawable.ic_s2_image);
                    resetBtn.setVisibility(View.GONE);
                    commit(() -> PropertyEditing.applyImage(Bridge.app(), f, ""));
                });
                Bridge.query(() -> Bridge.editor().getProject().getImagesPath(), imgs -> {
                    File img = new File(imgs + values.get(f));
                    if (img.isFile()) FilePickerDialog.thumb(iv, img, Ui.dp(c, 40));
                });
                r.setOnClickListener(v -> Bridge.query(() -> Bridge.editor().getProject().getImagesPath(), imgs ->
                        FilePickerDialog.show(c, Tr.t(f), imgs, new String[]{"jpg", "png", "jpeg"}, true, (file, rel) -> {
                            values.put(f, rel);
                            t.setText(rel);
                            FilePickerDialog.thumb(iv, file, Ui.dp(c, 40));
                            resetBtn.setVisibility(View.VISIBLE);
                            commit(() -> PropertyEditing.applyImage(Bridge.app(), f, rel));
                        })));
                box.addView(r, Ui.matchWrap());
                editors.put(f, r);
                return box;
            }
            case "File": {
                final ImageView[] animReset = new ImageView[1];
                box.addView(label(c, f));
                TextView t = chooser(c, val.isEmpty() ? "[" + Tr.t("choose", "choose") + "]" : val.replace(com.star4droid.template.Utils.Utils.seperator, "/"));
                t.setOnClickListener(v -> Bridge.query(() -> PropertyEditing.fileRoot(Bridge.editor(), f), rootPath ->
                        FilePickerDialog.show(c, Tr.t(f), rootPath, PropertyEditing.fileExtensions(f), false, (file, rel) -> {
                            String low = rel.toLowerCase();
                            if (f.equals("font") && (low.endsWith(".ttf") || low.endsWith(".otf"))) {
                                Dialogs.confirm(c, "TTF/OTF", Tr.t("ui_ttf_note", "TTF/OTF fonts are converted to a bitmap font (s2df). Create it now?"), null, () -> {
                                    shell.panels.hide();
                                    Bridge.gl(() -> PropertyEditing.applyTtfFont(Bridge.app(), f, Gdx.files.absolute(file.getAbsolutePath()), rel));
                                });
                                return;
                            }
                            values.put(f, rel);
                            t.setText(rel);
                            commit(() -> PropertyEditing.applyFile(Bridge.app(), f, rel));
                            if (animReset[0] != null) animReset[0].setVisibility(View.VISIBLE);
                        })));
                if (f.equals("animation")) {
                    // animation: pick from the anims folder (inner folders too); the element shows it live; Reset clears it (the image shows again)
                    LinearLayout ar = Ui.row(c);
                    ar.addView(t, Ui.weight(1));
                    ImageView rb = Ui.iconButton(c, R.drawable.ic_s2_refresh, Tr.t("anim_reset", "Reset animation"), v -> {
                        values.put(f, "");
                        t.setText("[" + Tr.t("choose", "choose") + "]");
                        v.setVisibility(View.GONE);
                        commit(() -> PropertyEditing.applyFile(Bridge.app(), f, ""));
                    });
                    rb.setVisibility(val.isEmpty() ? View.GONE : View.VISIBLE);
                    animReset[0] = rb;
                    ar.addView(rb);
                    box.addView(ar, Ui.matchWrap());
                    editors.put(f, t);
                    return box;
                }
                box.addView(t, Ui.matchWrap());
                editors.put(f, t);
                return box;
            }
            case "model": {
                // glTF / GLB of the project's models folder (import new ones with the Files panel)
                box.addView(label(c, f));
                TextView t = chooser(c, val.isEmpty() ? "[" + Tr.t("choose", "choose") + "]" : val);
                t.setOnClickListener(v -> Bridge.query(() -> {
                            String root = Bridge.editor().getProject().getPath() + "/models";
                            new java.io.File(root).mkdirs();
                            return root;
                        }, rootPath -> FilePickerDialog.show(c, Tr.t(f), rootPath, new String[]{"glb", "gltf", "obj"}, false, (file, rel) -> {
                            String nv = rel.startsWith("/") ? rel : "/" + rel;
                            values.put(f, nv);
                            t.setText(nv);
                            Bridge.query(() -> {
                                com.star4droid.template.d3.Assets3D.reload(nv);
                                if (!"MODEL".equals(values.get("Mesh"))) PropertyEditing.applyString(Bridge.app(), "Mesh", "MODEL");
                                return PropertyEditing.applyString(Bridge.app(), f, nv);
                            }, err -> { if (err != null) Ui.toast(c, err); });
                        })));
                box.addView(t, Ui.matchWrap());
                editors.put(f, t);
                return box;
            }
            case "shader": {
                // none, a ready-made shader (builtin:glow...) or a project file shaders/NAME.glsl (a template is created)
                box.addView(label(c, f));
                TextView t = chooser(c, val.isEmpty() ? "—" : val);
                t.setOnClickListener(v -> Bridge.query(() -> {
                    java.util.ArrayList<String> opts = new java.util.ArrayList<>();
                    opts.add("— " + Tr.t("ui_none", "None"));
                    for (String b : com.star4droid.template.d3.UserShader.BUILTINS) opts.add("builtin:" + b);
                    java.io.File dir = new java.io.File(Bridge.editor().getProject().getPath() + "/shaders");
                    String[] list = dir.list();
                    if (list != null) for (String n : list) if (n.endsWith(".glsl")) opts.add("shaders/" + n.substring(0, n.length() - 5));
                    opts.add("+ " + Tr.t("ui_shader_new", "New shader file..."));
                    return opts;
                }, opts -> Dialogs.choose(c, Tr.t(f), opts.toArray(new String[0]), i -> {
                    if (i == opts.size() - 1) {
                        Dialogs.input(c, Tr.t("ui_shader_new", "New shader file..."), Tr.t("ui_shader_name", "Name (letters, digits, _)"), "myShader", nm -> Dialogs.checkName(nm), nm -> Bridge.query(() -> {
                            java.io.File dir = new java.io.File(Bridge.editor().getProject().getPath() + "/shaders");
                            dir.mkdirs();
                            java.io.File fl = new java.io.File(dir, nm + ".glsl");
                            if (!fl.exists()) com.star4droid.star2d.Helpers.FileUtil.writeFile(fl.getAbsolutePath(), SHADER_TEMPLATE);
                            String nv = "shaders/" + nm;
                            com.star4droid.template.d3.UserShader.forget(nv);
                            return PropertyEditing.applyString(Bridge.app(), f, nv) == null ? nv : null;
                        }, nv -> {
                            if (nv == null) return;
                            values.put(f, nv);
                            t.setText(nv);
                            Ui.toast(c, Tr.t("ui_shader_created", "Shader created: edit it in the Files panel (shaders folder)."));
                        }));
                        return;
                    }
                    String nv = i == 0 ? "" : opts.get(i);
                    Bridge.query(() -> {
                        com.star4droid.template.d3.UserShader.forget(nv);
                        return PropertyEditing.applyString(Bridge.app(), f, nv);
                    }, err -> {
                        if (err != null) { Ui.toast(c, err); return; }
                        values.put(f, nv);
                        t.setText(nv.isEmpty() ? "—" : nv);
                    });
                })));
                box.addView(t, Ui.matchWrap());
                editors.put(f, t);
                return box;
            }
            case "body": {
                box.addView(label(c, f));
                boolean multi = f.equals("Collision");
                TextView t = chooser(c, val.isEmpty() ? "—" : val);
                t.setOnClickListener(v -> Bridge.query(() -> Bridge.editor().getBodiesList(), bodies -> {
                    bodies.remove(selectedName);
                    if (multi) pickMany(c, f, bodies, t);
                    else {
                        String[] opts = new String[bodies.size() + 1];
                        opts[0] = "— " + Tr.t("ui_none", "None");
                        for (int i = 0; i < bodies.size(); i++) opts[i + 1] = bodies.get(i);
                        Dialogs.choose(c, Tr.t(f), opts, i -> {
                            String nv = i == 0 ? "" : bodies.get(i - 1);
                            Bridge.query(() -> PropertyEditing.applyBody(Bridge.app(), f, nv), err -> {
                                if (err != null) { Ui.toast(c, err); return; }
                                values.put(f, nv);
                                t.setText(nv.isEmpty() ? "—" : nv);
                                if (f.equals("parent")) shell.elements.refresh();
                            });
                        });
                    }
                }));
                box.addView(t, Ui.matchWrap());
                editors.put(f, t);
                return box;
            }
            case "ui": {
                box.addView(label(c, f));
                TextView t = chooser(c, val.isEmpty() ? "[" + Tr.t("choose", "choose") + "]" : val);
                final Runnable[] reload = new Runnable[1];
                reload[0] = () -> Bridge.gl(() -> {
                    Object sel = Bridge.editor().getSelectedActor();
                    if (sel instanceof com.star4droid.star2d.editor.items.EditorItem)
                        ((com.star4droid.star2d.editor.items.EditorItem) sel).update();
                });
                java.util.function.Consumer<String> setUi = nv -> {
                    values.put(f, nv);
                    t.setText(nv);
                    commit(() -> PropertyEditing.applyString(Bridge.app(), f, nv));
                };
                java.util.function.Consumer<String> openMaker = nv -> Bridge.query(
                        () -> Bridge.editor().getProject().getPath(),
                        path -> UiMakerLauncher.open(c, path, nv, reload[0]));
                LinearLayout r = Ui.row(c);
                r.addView(Ui.button(c, R.drawable.ic_s2_edit, Tr.t("ui_edit", "Edit"), true, v ->
                        Bridge.query(() -> Bridge.editor().getProject().getPath(), path -> {
                            String cur = values.get(f) == null ? "" : values.get(f);
                            if (UiMakerLauncher.list(path).isEmpty()) {
                                Ui.toast(c, Tr.t("ui_none_yet", "No UI files yet - press New"));
                                return;
                            }
                            // browse UI/ folder by folder
                            com.star4droid.star2d.ui.uimaker.UiFolders.pickUi(c, path, cur, nv -> {
                                if (!nv.equals(cur)) setUi.accept(nv);
                                openMaker.accept(nv);
                            });
                        })), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                r.addView(Ui.button(c, R.drawable.ic_s2_add, Tr.t("ui_new", "New"), false, v ->
                        Bridge.query(() -> Bridge.editor().getProject().getPath(), path -> {
                            String cur = values.get(f) == null ? "" : values.get(f);
                            com.star4droid.star2d.ui.uimaker.UiFolders.newUiDialog(c, path, Tr.t("ui_new_title", "New UI"), "",
                                    com.star4droid.star2d.ui.uimaker.UiFolders.folderOf(cur), full -> {
                                        java.io.File file = new java.io.File(UiMakerLauncher.uiDir(path), full + ".ui");
                                        try {
                                            com.star4droid.star2d.ui.uimaker.UiOps.newDocument(full, 360, 640).write(file);
                                        } catch (Exception e) {
                                            Ui.toast(c, String.valueOf(e.getMessage()));
                                            return;
                                        }
                                        setUi.accept(full);
                                        openMaker.accept(full);
                                    });
                        })), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                box.addView(t, Ui.matchWrap());
                box.addView(r, Ui.matchWrap());
                editors.put(f, t);
                return box;
            }
            case "script": {
                box.addView(label(c, f));
                TextView t = chooser(c, val.isEmpty() ? "[" + Tr.t("choose", "choose") + "]" : val);
                t.setOnClickListener(v -> pickScript(c, f, t, null));
                box.addView(t, Ui.matchWrap());
                editors.put(f, t);
                return box;
            }
            case "points": {
                box.addView(label(c, f));
                LinearLayout b = Ui.button(c, R.drawable.ic_s2_collider, Tr.t("customColliderTitle", "Edit collider shape"), false, v -> {
                    shell.panels.hide();
                    Bridge.gl(() -> {
                        TestApp app = Bridge.app();
                        app.getControlLayer().getCustomColliderEditor().show(app.getUiStage());
                    });
                });
                box.addView(b);
                return box;
            }
            default: {
                box.addView(label(c, f));
                TextView t = Ui.text(c, val, 14, Ui.TEXT_3);
                t.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 4));
                box.addView(t);
                return box;
            }
        }
    }

    private TextView chooser(Context c, String text) {
        TextView t = Ui.text(c, text, 14, Ui.TEXT);
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 9), Ui.dp(c, 10), Ui.dp(c, 9));
        t.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 7)));
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_s2_expand_more, 0);
        t.setMaxLines(2);
        return t;
    }

    private void pickMany(Context c, String f, ArrayList<String> bodies, TextView t) {
        String cur = values.get(f) == null ? "" : values.get(f);
        String[] names = bodies.toArray(new String[0]);
        boolean[] checked = new boolean[names.length];
        for (int i = 0; i < names.length; i++) checked[i] = cur.contains("(" + names[i] + ")");
        new AlertDialog.Builder(c)
                .setTitle(Tr.t(f))
                .setMultiChoiceItems(names, checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton(Tr.t("ok", "OK"), (d, w) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < names.length; i++) if (checked[i]) sb.append("(").append(names[i]).append(")");
                    String nv = sb.toString();
                    values.put(f, nv);
                    t.setText(nv.isEmpty() ? "—" : nv);
                    commit(() -> PropertyEditing.applyBody(Bridge.app(), f, nv));
                })
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    /** Body-script selector: bodies of the current scene, or "scene/body" from another scene. */
    private void pickScript(Context c, String f, TextView t, String sceneToShow) {
        Bridge.query(() -> {
            String scene = Bridge.editor().getScene();
            String target = sceneToShow == null ? scene : sceneToShow;
            ArrayList<String> bodies = new ArrayList<>();
            if (target.equals(scene)) bodies.addAll(Bridge.editor().getBodiesList());
            else {
                ArrayList<com.star4droid.template.Utils.PropertySet<String, Object>> sets = new Gson().fromJson(
                        Gdx.files.absolute(Bridge.editor().getProject().getScenesPath() + target).readString(),
                        new TypeToken<ArrayList<com.star4droid.template.Utils.PropertySet<String, Object>>>() {}.getType());
                if (sets != null) for (com.star4droid.template.Utils.PropertySet<String, Object> ps : sets) bodies.add(ps.getString("name"));
            }
            ArrayList<String> scenes = new ArrayList<>();
            for (FileHandle fh : Gdx.files.absolute(Bridge.editor().getProject().getScenesPath()).list())
                if (!fh.isDirectory() && !fh.name().equals(target) && !com.star4droid.star2d.prefab.PrefabStore.isTempScene(fh.name())) scenes.add(fh.name());
            return new Object[]{scene, target, bodies, scenes};
        }, r -> {
            String scene = (String) r[0], target = (String) r[1];
            ArrayList<String> bodies = (ArrayList<String>) r[2], scenes = (ArrayList<String>) r[3];
            ArrayList<String> opts = new ArrayList<>(bodies);
            for (String s : scenes) opts.add("📁 " + Tr.t("ui_other_scene", "Other scene") + ": " + s);
            Dialogs.choose(c, Tr.t("selectScript", "Select script") + (target.equals(scene) ? "" : " (" + target + ")"), opts.toArray(new String[0]), i -> {
                if (i >= bodies.size()) {
                    pickScript(c, f, t, scenes.get(i - bodies.size()));
                    return;
                }
                String v = target.equals(scene) ? bodies.get(i) : target + "/" + bodies.get(i);
                values.put(f, v);
                t.setText(v);
                commit(() -> PropertyEditing.applyScript(Bridge.app(), f, v));
            });
        });
    }

    private static String display(String type, String v) {
        if (!"float".equals(type)) return v;
        try {
            float f = Float.parseFloat(v);
            if (f == Math.rint(f) && Math.abs(f) < 1e7) return String.valueOf((long) f);
            return String.valueOf(Math.round(f * 1000f) / 1000f);
        } catch (Exception e) {
            return v;
        }
    }

    // ------------------------------------------------------------ prefab instance bar

    private void hideInstanceBar() {
        if (instanceBar == null) return;
        instanceBar.removeAllViews();
        instanceBar.setVisibility(View.GONE);
        instKey = "";
        overridden.clear();
    }

    /** Shows what prefab the selected element belongs to (Apply / Revert / Unpack), or Save-as / Link for a plain group. */
    private void updateInstanceBar(String name, LinkedHashMap<String, String> v, String type) {
        if (instanceBar == null) return;
        if (shell.inPrefabMode()) {
            hideInstanceBar();
            return;
        }
        String pid = v.get("pid") == null ? "" : v.get("pid");
        if (pid.isEmpty()) {
            instKey = "";
            overridden.clear();
            // any element can become a prefab (a Group is wrapped around a plain element); only a Group can also be linked
            showGroupBar(name, "Group".equals(type));
            return;
        }
        String key = name + "|" + pid + "|" + (v.get("prefab") == null ? "" : v.get("prefab"));
        if (key.equals(instKey)) return;
        instKey = key;
        instanceBar.removeAllViews();
        instanceBar.setVisibility(View.GONE);
        overridden.clear();
        refreshInstance();
    }

    private void refreshInstance() {
        final String name = selectedName;
        if (name == null || instKey.isEmpty() || !Bridge.ready()) return;
        final int seq = ++infoSeq;
        Bridge.query(() -> {
            com.star4droid.star2d.editor.LibgdxEditor ed = Bridge.editor();
            ed.getProject().save(ed); // the prefab compare reads the scene file
            return new String[]{ed.getProject().getPath(), ed.getScene()};
        }, r -> INFO_IO.execute(() -> {
            com.star4droid.star2d.prefab.PrefabOps.Info info = null;
            try {
                info = com.star4droid.star2d.prefab.PrefabOps.info(new com.star4droid.star2d.Helpers.editor.Project(r[0]), r[1], name);
            } catch (Throwable ignored) {
            }
            final com.star4droid.star2d.prefab.PrefabOps.Info fi = info;
            Ui.ui(() -> {
                if (seq != infoSeq || !name.equals(selectedName)) return;
                applyInfo(name, fi);
            });
        }));
    }

    private void applyInfo(String name, com.star4droid.star2d.prefab.PrefabOps.Info info) {
        HashSet<String> before = new HashSet<>(overridden);
        overridden.clear();
        if (info == null) {
            instanceBar.removeAllViews();
            instanceBar.setVisibility(View.GONE);
        } else {
            String mine = name + ".";
            for (String m : info.modified) if (m.startsWith(mine)) overridden.add(m.substring(mine.length()));
            buildInstanceBar(instanceBar.getContext(), name, info);
        }
        if (!before.equals(overridden)) {
            fieldsSignature = "";
            Bridge.query(this::snapshot, this::render);
        }
    }

    private LinearLayout barCard(Context c) {
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.rounded(0x224FC3F7, Ui.dp(c, 10), com.star4droid.star2d.ui.PrefabUi.COLOR, Ui.dp(c, 1)));
        card.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        return card;
    }

    private View barButton(Context c, int icon, String label, boolean primary, View.OnClickListener l) {
        LinearLayout b = Ui.button(c, icon, label, primary, l);
        b.setLayoutParams(Ui.margins(new LinearLayout.LayoutParams(0, Ui.dp(c, 38), 1), c, 2, 0, 2, 0));
        b.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 8), 0);
        return b;
    }

    private void buildInstanceBar(Context c, String name, com.star4droid.star2d.prefab.PrefabOps.Info info) {
        instanceBar.removeAllViews();
        instanceBar.setVisibility(View.VISIBLE);
        LinearLayout card = barCard(c);
        LinearLayout top = Ui.row(c);
        top.addView(Ui.smallIcon(c, com.star4droid.star2d.ui.PrefabUi.ICON, com.star4droid.star2d.ui.PrefabUi.COLOR, 22));
        LinearLayout texts = Ui.column(c);
        texts.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 4), 0);
        TextView title = Ui.text(c, Tr.t("prefab", "Prefab") + ": " + com.star4droid.star2d.ui.PrefabUi.shortName(info.prefabPath), 15, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(1);
        String sub;
        if (info.missing) sub = Tr.t("prefab_missing", "The prefab file is missing");
        else if (info.modifiedCount == 0) sub = Tr.t("prefab_no_changes", "No changes");
        else sub = info.modifiedCount + " " + Tr.t("prefab_n_modified", "modified");
        if (!info.isRoot && info.rootName != null && !info.rootName.isEmpty()) sub += "  ·  " + Tr.t("prefab_part_of", "part of") + " " + info.rootName;
        TextView subtitle = Ui.text(c, sub, 12, info.modifiedCount > 0 ? com.star4droid.star2d.ui.PrefabUi.COLOR : Ui.TEXT_3);
        texts.addView(title);
        texts.addView(subtitle);
        top.addView(texts, Ui.weight(1));
        if (!info.missing) {
            top.addView(Ui.iconButton(c, R.drawable.ic_s2_edit, Tr.t("prefab_open", "Open prefab"), v -> shell.openPrefab(info.prefabPath)));
            top.setOnClickListener(v -> shell.openPrefab(info.prefabPath));
        }
        card.addView(top, Ui.matchWrap());
        LinearLayout btns = Ui.row(c);
        btns.setPadding(0, Ui.dp(c, 8), 0, 0);
        if (!info.missing) {
            btns.addView(barButton(c, R.drawable.ic_s2_upload, Tr.t("prefab_apply", "Apply"), true, v -> com.star4droid.star2d.ui.PrefabUi.apply(shell, info.rootName, info.prefabPath)));
            btns.addView(barButton(c, R.drawable.ic_s2_undo, Tr.t("prefab_revert", "Revert"), false, v -> com.star4droid.star2d.ui.PrefabUi.revert(shell, info.rootName)));
        }
        btns.addView(barButton(c, R.drawable.ic_s2_link, Tr.t("prefab_unpack", "Unpack"), false, v -> com.star4droid.star2d.ui.PrefabUi.unpack(shell, info.rootName)));
        card.addView(btns, Ui.matchWrap());
        instanceBar.addView(card, Ui.margins(Ui.matchWrap(), c, 0, 2, 0, 4));
    }

    private void showGroupBar(String name, boolean group) {
        Context c = instanceBar.getContext();
        instanceBar.removeAllViews();
        instanceBar.setVisibility(View.VISIBLE);
        LinearLayout card = barCard(c);
        LinearLayout btns = Ui.row(c);
        btns.addView(barButton(c, com.star4droid.star2d.ui.PrefabUi.ICON, Tr.t("prefab_save_as", "Save as prefab"), false, v -> com.star4droid.star2d.ui.PrefabUi.saveAsPrefab(shell, name)));
        if (group) btns.addView(barButton(c, R.drawable.ic_s2_link, Tr.t("prefab_link", "Link to prefab…"), false, v -> com.star4droid.star2d.ui.PrefabUi.link(shell, name)));
        card.addView(btns, Ui.matchWrap());
        instanceBar.addView(card, Ui.margins(Ui.matchWrap(), c, 0, 2, 0, 4));
    }

    /** A Body Script changed on disk (code editor closed): re-read its variables section. */
    public void refreshScriptVars() {
        if (behaviorSection != null) behaviorSection.rescan();
    }

    private void commit(Runnable glWork) {
        Bridge.gl(glWork);
    }

    /** Update visible values without rebuilding (skips the field being edited). */
    private void updateValues(LinkedHashMap<String, String> nv) {
        for (Map.Entry<String, String> e : nv.entrySet()) {
            String f = e.getKey(), v = e.getValue();
            if (v.equals(values.get(f))) continue;
            values.put(f, v);
            // a changed value of a prefab instance: recount what differs from the prefab (debounced)
            if (!instKey.isEmpty() && !f.equals("x") && !f.equals("y") && !f.equals("width") && !f.equals("height") && !f.equals("rotation") && !f.equals("radius") && !f.equals("z")) {
                root.removeCallbacks(infoRefresh);
                root.postDelayed(infoRefresh, 900);
            }
            if (behaviorSection != null && (f.equals("behaviors") || f.equals("scriptVars"))) behaviorSection.externalChange(f, v);
            View ed = editors.get(f);
            if (ed == null) continue;
            String type = typeOf.get(f);
            if (ed instanceof EditText) {
                if (!ed.hasFocus()) ((EditText) ed).setText(display(type, v));
            } else if (ed instanceof SwitchCompat) {
                ((SwitchCompat) ed).setChecked("true".equals(v));
            } else if (ed instanceof TextView && !"image".equals(type) && !"color".equals(type)) {
                ((TextView) ed).setText(v.isEmpty() ? "—" : ("spinner".equals(type) ? Tr.t(v, v) : v));
            } else {
                // complex rows: rebuild on next selection refresh
                fieldsSignature = "";
            }
        }
    }

    public static ViewGroup.LayoutParams wrap() {
        return new ViewGroup.LayoutParams(-2, -2);
    }
}
