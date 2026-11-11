package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Adapters.VisualScriptingDialog;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Scene events, element events, body/scene scripts and custom scripts.
 * Opening an event uses the same VisualScriptingDialog entry points as before,
 * so files and paths are unchanged.
 */
public class EventsPanel implements PanelHost.Panel {
    private final EditorShell shell;
    private ScrollView root;
    private LinearLayout list;

    // name, translate?, english help, arabic help
    private static final String[][] SCENE_EVENTS = {
            {"OnCreate", "Runs once when the scene starts.", "يعمل مرة واحدة عند بدء المشهد."},
            {"OnStep", "Runs every frame (about 60 times per second).", "يعمل كل إطار (حوالي 60 مرة في الثانية)."},
            {"OnPause", "When the game is paused / app goes to background.", "عند إيقاف اللعبة مؤقتاً أو خروج التطبيق للخلفية."},
            {"OnResume", "When the game comes back.", "عند عودة اللعبة."},
            {"onCollisionStart", "When any two bodies start touching (body1, body2).", "عندما يبدأ أي جسمين بالتلامس (body1, body2)."},
            {"onCollisionEnd", "When two bodies stop touching.", "عندما يتوقف جسمان عن التلامس."},
    };
    private static final String[][] BODY_EVENTS = {
            {"onClick", "When this element is tapped.", "عند النقر على هذا العنصر."},
            {"OnBodyCreated", "When this element is created.", "عند إنشاء هذا العنصر."},
            {"OnBodyUpdate", "Every frame, for this element.", "كل إطار، لهذا العنصر."},
            {"onTouchStart", "When a finger starts touching it.", "عند بدء لمس العنصر."},
            {"onTouchEnd", "When the finger is lifted.", "عند رفع الإصبع."},
            {"onBodyCollided", "When it hits another body (body2).", "عند اصطدامه بجسم آخر (body2)."},
            {"onBodyCollideEnd", "When it stops touching another body.", "عند انتهاء تلامسه مع جسم آخر."},
    };

    public EventsPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("events", "Events"); }
    @Override public int icon() { return R.drawable.ic_s2_events; }

    @Override
    public View[] headerActions(Context c) {
        return new View[]{Ui.iconButton(c, R.drawable.ic_s2_help, Tr.t("ui_help", "Help"), v -> com.star4droid.star2d.ui.Help.show(c))};
    }

    @Override
    public View view(Context c) {
        if (root != null) return root;
        root = new ScrollView(c);
        list = Ui.column(c);
        list.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), Ui.dp(c, 16));
        root.addView(list);
        return root;
    }

    @Override public void onShown() { refresh(); }

    public void onSelectionChanged() {
        if (shell.panels.isShowing(this)) refresh();
    }

    /** [scene, selectedName or null, bodyKey, scripts, eventsWithCode] (GL thread). */
    private Object[] state() {
        Project p = Bridge.editor().getProject();
        String scene = Bridge.editor().getScene();
        Actor sel = Bridge.editor().getSelectedActor();
        String selName = null, bodyScene = scene, body = "";
        if (sel != null) {
            PropertySet ps = PropertySet.getPropertySet(sel);
            selName = ps.getString("name");
            body = ps.getString(ps.containsKey("Script") ? "Script" : "name");
            if (body.contains("/")) {
                String[] v = body.split("/");
                bodyScene = v[0];
                body = v[1];
            }
        }
        HashSet<String> withCode = new HashSet<>();
        for (String[] e : SCENE_EVENTS) if (hasCode(p.getEventPath(scene, "", e[0]))) withCode.add(e[0]);
        for (com.star4droid.star2d.behaviors.SceneEventsExt.Ev e : com.star4droid.star2d.behaviors.SceneEventsExt.ALL)
            if (hasCode(p.getEventPath(scene, "", e.name))) withCode.add(e.name);
        if (sel != null) for (String[] e : BODY_EVENTS) if (hasCode(p.getEventPath(bodyScene, body, e[0]))) withCode.add("body:" + e[0]);
        if (sel != null && Gdx.files.absolute(p.getBodyScriptPath(selName, scene)).exists()) withCode.add("Body Script");
        if (Gdx.files.absolute(p.getSceneScript(scene)).exists()) withCode.add("Scene Script");
        ArrayList<String> scripts = new ArrayList<>();
        FileHandle dir = Gdx.files.absolute(p.get("scripts"));
        if (dir.exists())
            for (FileHandle f : dir.list()) if (f.name().endsWith(".java")) scripts.add(f.name().replace(".java", ""));
        String bodyInfo = sel == null ? null : (bodyScene.equals(scene) ? body : bodyScene + "/" + body);
        // events raised by the behaviors added to the element ("Additional behaviors")
        java.util.List<String[]> behaviorEvents = new ArrayList<>();
        if (sel != null) {
            PropertySet ps2 = PropertySet.getPropertySet(sel);
            if (ps2.containsKey("behaviors")) behaviorEvents = com.star4droid.star2d.ui.behaviors.BehaviorEvents.listFor(selName, ps2.getString("behaviors"));
        }
        return new Object[]{scene, selName, bodyInfo, scripts, withCode, uiEntries(p, scene, sel), behaviorEvents};
    }

    /**
     * UIHolder selected: its inner elements that can have events.
     * Entries: {holder, nodeId, nodeType, comma-separated events, "1" when some event has code, ui name}.
     */
    private static ArrayList<String[]> uiEntries(Project p, String scene, Actor sel) {
        ArrayList<String[]> out = new ArrayList<>();
        try {
            if (sel == null) return out;
            PropertySet ps = PropertySet.getPropertySet(sel);
            if (!"UIHOLDER".equals(ps.getString("TYPE"))) return out;
            String ui = ps.getString("ui");
            if (ui == null || ui.isEmpty()) return out;
            java.io.File f = com.star4droid.template.ui.UIView.uiFile(p.getPath(), ui); // "folder/name" and a trailing ".ui" are fine
            if (!f.exists()) return out;
            com.star4droid.template.ui.UIDocument doc = com.star4droid.template.ui.UIDocument.read(f);
            if (doc == null) return out;
            String holder = ps.getString("name");
            for (com.star4droid.template.ui.UINode n : doc.allNodes()) {
                String[] evs = com.star4droid.template.ui.UIWidgetRegistry.eventsOf(n);
                if (evs.length == 0) continue;
                boolean any = false;
                StringBuilder shown = new StringBuilder(); // "onClick ● · onTouchStart" (● = has code)
                for (String ev : evs) {
                    boolean has = hasCode(p.getEventPath(scene, com.star4droid.star2d.Helpers.UiEventsGen.body(holder, n.id), ev));
                    any |= has;
                    shown.append(shown.length() == 0 ? "" : " · ").append(ev).append(has ? " ●" : "");
                }
                String t = n.type;
                boolean interactive = t.equals("Button") || t.equals("Slider") || t.equals("Checkbox") || t.equals("Radio")
                        || t.equals("Switch") || t.equals("TextField") || t.equals("ScrollView");
                if (!interactive && !any) continue;
                out.add(new String[]{holder, n.id, t, String.join(",", evs), any ? "1" : "0", ui, shown.toString()});
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static final HashSet<String> openGroups = new HashSet<>();

    private void extraGroup(Context c, boolean ar, HashSet<String> withCode, String group, int icon, int color, String title) {
        int used = 0, total = 0;
        for (com.star4droid.star2d.behaviors.SceneEventsExt.Ev e : com.star4droid.star2d.behaviors.SceneEventsExt.ALL) {
            if (!e.group.equals(group)) continue;
            total++;
            if (withCode.contains(e.name)) used++;
        }
        boolean open = openGroups.contains(group);
        LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 6));
        head.setBackground(Ui.ripple(0, Ui.dp(c, 8)));
        head.addView(Ui.smallIcon(c, icon, color, 18));
        TextView t = Ui.text(c, title + (used > 0 ? "  ·  " + used + "/" + total : ""), 13, Ui.TEXT_2);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(c, 8), 0, 0, 0);
        head.addView(t, Ui.weight(1));
        head.addView(Ui.smallIcon(c, open ? R.drawable.ic_s2_expand_more : R.drawable.ic_s2_chevron_right, Ui.TEXT_3, 18));
        head.setOnClickListener(v -> {
            if (openGroups.contains(group)) openGroups.remove(group);
            else openGroups.add(group);
            refresh();
        });
        list.addView(head, Ui.matchWrap());
        for (com.star4droid.star2d.behaviors.SceneEventsExt.Ev e : com.star4droid.star2d.behaviors.SceneEventsExt.ALL) {
            if (!e.group.equals(group) || (!open && !withCode.contains(e.name))) continue;
            View r = row(c, icon, color, Tr.t(e.name), Tr.t("ui_ev_desc_" + e.name, ar ? e.ar : e.en), withCode.contains(e.name),
                    v -> VisualScriptingDialog.showFor(e.name, false, false));
            com.star4droid.star2d.ui.tutorial.Tut.tag(r, "scene_event:" + e.name);
            list.addView(r);
        }
    }

    private static boolean hasCode(String path) {
        FileHandle j = Gdx.files.absolute(path + ".java"), v = Gdx.files.absolute(path + ".visual");
        return (j.exists() && j.length() > 0) || (v.exists() && v.length() > 2);
    }

    public void refresh() {
        if (list == null || !Bridge.ready()) return;
        Bridge.query(this::state, this::render);
    }

    private void render(Object[] st) {
        Context c = list.getContext();
        boolean ar = Ui.isRtl(c);
        list.removeAllViews();
        String scene = (String) st[0];
        String sel = (String) st[1];
        String bodyInfo = (String) st[2];
        ArrayList<String> scripts = (ArrayList<String>) st[3];
        HashSet<String> withCode = (HashSet<String>) st[4];
        ArrayList<String[]> uiNodes = (ArrayList<String[]>) st[5];

        // scene
        list.addView(Ui.sectionLabel(c, Tr.t("ui_scene_events", "Scene events") + " · " + scene));
        list.addView(row(c, R.drawable.ic_s2_code, 0xFF26A69A, Tr.t("sceneScript", "Scene Script"),
                ar ? "كود جافا للمشهد كاملاً (للمحترفين)." : "Java code for the whole scene (advanced).", withCode.contains("Scene Script"),
                v -> Bridge.gl(() -> {
                    String sc = Bridge.editor().getScene();
                    String path = Bridge.editor().getProject().getSceneScript(sc);
                    VisualScriptingDialog.openSceneScript(sc, path);
                })));
        for (String[] e : SCENE_EVENTS) {
            View r = row(c, R.drawable.ic_s2_events, 0xFFFFB300, Tr.t(e[0]), Tr.t("ui_ev_desc_" + e[0], ar ? e[2] : e[1]), withCode.contains(e[0]),
                    v -> VisualScriptingDialog.showFor(e[0], false, false));
            com.star4droid.star2d.ui.tutorial.Tut.tag(r, "scene_event:" + e[0]);
            list.addView(r);
        }
        // path finding / animation / network events (groups open when tapped or when one has code)
        extraGroup(c, ar, withCode, com.star4droid.star2d.behaviors.SceneEventsExt.NAV, R.drawable.ic_s2_navgrid, 0xFF4DD0E1, Tr.t("ev_group_nav", "Path finding events"));
        extraGroup(c, ar, withCode, com.star4droid.star2d.behaviors.SceneEventsExt.ANIM, R.drawable.ic_s2_timeline, 0xFFBA68C8, Tr.t("ev_group_anim", "Animation events"));
        extraGroup(c, ar, withCode, com.star4droid.star2d.behaviors.SceneEventsExt.NET, R.drawable.ic_s2_network, 0xFF81C784, Tr.t("ev_group_net", "Network events"));

        // element
        list.addView(Ui.sectionLabel(c, Tr.t("ui_element_events", "Element events") + (sel == null ? "" : " · " + sel)));
        if (sel == null) {
            TextView t = Ui.text(c, Tr.t("ui_select_for_events", "Select an element to add events to it (tap it on the canvas)."), 13, Ui.TEXT_3);
            t.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 8));
            list.addView(t);
        } else {
            if (bodyInfo != null && !bodyInfo.equals(sel)) {
                TextView t = Ui.text(c, Tr.t("ui_shared_events", "Uses the events of") + ": " + bodyInfo, 12, Ui.ACCENT);
                t.setPadding(Ui.dp(c, 6), 0, 0, Ui.dp(c, 4));
                list.addView(t);
            }
            for (String[] e : BODY_EVENTS) {
                View r = row(c, R.drawable.ic_s2_touch, 0xFF42A5F5, Tr.t(e[0]), Tr.t("ui_ev_desc_" + e[0], ar ? e[2] : e[1]), withCode.contains("body:" + e[0]),
                        v -> VisualScriptingDialog.showFor(e[0], true, false));
                com.star4droid.star2d.ui.tutorial.Tut.tag(r, "event:" + e[0]);
                list.addView(r);
            }
            java.util.List<String[]> behaviorEvents = (java.util.List<String[]>) st[6];
            if (!behaviorEvents.isEmpty()) {
                list.addView(Ui.sectionLabel(c, Tr.t("bhv_events_group", "Behavior events") + " · " + sel));
                for (String[] be : behaviorEvents) {
                    list.addView(row(c, R.drawable.ic_s2_events, 0xFFAB47BC, be[3], be[1], !be[4].isEmpty(),
                            v -> com.star4droid.star2d.ui.behaviors.BehaviorEvents.open(sel, be[0], be[2])));
                }
            }
            View bs = row(c, R.drawable.ic_s2_script, 0xFF7E57C2, Tr.t("bodyScript", "Body Script"),
                    ar ? "كود جافا خاص بهذا العنصر (للمحترفين)." : "Java code just for this element (advanced).", withCode.contains("Body Script"),
                    v -> VisualScriptingDialog.openCodeEditor());
            bs.setOnLongClickListener(v -> {
                Dialogs.confirm(c, Tr.t("delete", "Delete"), Tr.t("bodyScript", "Body Script") + " · " + sel + "\n" + Tr.t("areYouSure", "Are you sure?"),
                        Tr.t("delete", "Delete"), () -> Bridge.gl(() -> {
                            FileHandle fh = Gdx.files.absolute(Bridge.editor().getProject().getBodyScriptPath(sel, Bridge.editor().getScene()));
                            if (fh.exists()) fh.delete();
                            com.star4droid.star2d.ui.Ui.ui(this::refresh);
                        }));
                return true;
            });
            list.addView(bs);
        }

        // inner elements of a UIHolder
        if (!uiNodes.isEmpty()) {
            list.addView(Ui.sectionLabel(c, Tr.t("ui_inner_events", "UI elements") + " · " + uiNodes.get(0)[5]));
            TextView codeHint = Ui.text(c, Tr.t("sv_ui_code_hint", "From code") + ": " + uiNodes.get(0)[0] + ".onClick(\"" + uiNodes.get(0)[1] + "\", runnable)  ·  " + uiNodes.get(0)[0] + "_" + uiNodes.get(0)[1] + ".setText(\"..\")", 11, Ui.TEXT_3);
            codeHint.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), Ui.dp(c, 4));
            list.addView(codeHint);
            for (String[] n : uiNodes) {
                String[] evs = n[3].split(",");
                View r = row(c, R.drawable.ic_s2_widgets, 0xFFFF9800, n[1] + "  (" + n[2] + ")", n[6], "1".equals(n[4]), v -> {
                    if (evs.length == 1) VisualScriptingDialog.showForUi(n[0], n[1], evs[0]);
                    else Dialogs.choose(c, n[1], evs, i -> VisualScriptingDialog.showForUi(n[0], n[1], evs[i]));
                });
                list.addView(r);
            }
        }

        // custom scripts
        list.addView(Ui.sectionLabel(c, Tr.t("ui_custom_scripts", "Custom scripts")));
        for (String s : scripts) {
            View r = row(c, R.drawable.ic_s2_blocks, 0xFF66BB6A, s, ar ? "دوال ومتغيرات مشتركة للمشهد." : "Shared functions & variables for the scene.", true,
                    v -> VisualScriptingDialog.showFor(s, false, true));
            r.setOnLongClickListener(v -> { scriptMenu(c, v, s); return true; });
            list.addView(r);
        }
        LinearLayout add = Ui.button(c, R.drawable.ic_s2_add, Tr.t("addScript", "Add script"), false, v -> addScript(c));
        list.addView(add, Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)), c, 4, 6, 4, 0));
    }

    private View row(Context c, int icon, int color, String title, String sub, boolean hasCode, View.OnClickListener l) {
        TextView dot = Ui.text(c, hasCode ? "●" : "", 12, Ui.SUCCESS);
        dot.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        ImageView open = Ui.smallIcon(c, R.drawable.ic_s2_chevron_right, Ui.TEXT_3, 20);
        open.setScaleX(Ui.isRtl(c) ? -1 : 1);
        LinearLayout r = PanelHost.listRow(c, icon, color, title, sub, dot, open);
        ((TextView) ((LinearLayout) r.getChildAt(1)).getChildAt(0)).setTypeface(Typeface.DEFAULT_BOLD);
        r.setOnClickListener(l);
        return r;
    }

    private void scriptMenu(Context c, View anchor, String name) {
        PopupMenu m = new PopupMenu(c, anchor);
        m.getMenu().add(0, 1, 0, Tr.t("open", "Open"));
        m.getMenu().add(0, 2, 1, Tr.t("delete", "Delete"));
        m.setOnMenuItemClickListener(it -> {
            if (it.getItemId() == 1) VisualScriptingDialog.showFor(name, false, true);
            else Dialogs.confirm(c, Tr.t("delete", "Delete"), name + "\n" + Tr.t("areYouSure", "Are you sure?"), Tr.t("delete", "Delete"),
                    () -> Bridge.gl(() -> {
                        String path = Bridge.editor().getProject().get("scripts") + name;
                        Gdx.files.absolute(path + ".java").delete();
                        Gdx.files.absolute(path + ".visual").delete();
                        Ui.ui(this::refresh);
                    }));
            return true;
        });
        m.show();
    }

    private void addScript(Context c) {
        Dialogs.input(c, Tr.t("addScript", "Add script"), Tr.t("name", "Name"), "Example", v -> {
            String e = Dialogs.checkName(v);
            if (e != null) return e;
            if (v.equals("Body Script")) return Tr.t("notAllowedName", "Not allowed name");
            return null;
        }, name -> Bridge.query(() -> {
            FileHandle path = Gdx.files.absolute(Bridge.editor().getProject().get("scripts") + name + ".java");
            if (path.exists()) return Tr.t("scriptNameConflict", "A script with this name already exists");
            path.writeString("", false);
            return null;
        }, err -> {
            if (err != null) Ui.toast(c, err);
            refresh();
        }));
    }

    static ColorStateList tint(int c) { return ColorStateList.valueOf(c); }
}
