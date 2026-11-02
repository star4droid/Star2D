package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.editor.ui.ItemActions;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.Locale;

/** Scene hierarchy: select, show/hide, lock, rename, duplicate, reorder, delete. */
public class ElementsPanel implements PanelHost.Panel {
    private final EditorShell shell;
    private View root;
    private RecyclerView list;
    private TextView empty;
    private EditText search;
    private final ArrayList<Bridge.ItemInfo> all = new ArrayList<>();
    private final ArrayList<Bridge.ItemInfo> shown = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private boolean pending;
    /** Expanded parents per scene (kept while the app runs): every parent starts COLLAPSED. Searching shows everything. */
    private static final java.util.HashMap<String, java.util.HashSet<String>> EXPANDED = new java.util.HashMap<>();
    /** Last selected element seen: when the selection moves into a collapsed parent, its ancestors open. */
    private String lastSelected = "";
    private static final long ANIM_MS = 200;

    private static java.util.HashSet<String> expandedOf(String scene) {
        String k = scene == null ? "" : scene;
        java.util.HashSet<String> h = EXPANDED.get(k);
        if (h == null) { h = new java.util.HashSet<>(); EXPANDED.put(k, h); }
        return h;
    }

    private static boolean isCollapsed(Bridge.ItemInfo i) {
        return i.children > 0 && !expandedOf(i.scene).contains(i.name);
    }

    public ElementsPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("bodiesList", "Elements"); }
    @Override public int icon() { return R.drawable.ic_s2_layers; }

    @Override
    public View[] headerActions(Context c) {
        return new View[]{Ui.iconButton(c, R.drawable.ic_s2_add, Tr.t("ui_add_element", "Add element"), v -> shell.panels.show(shell.addItem))};
    }

    @Override
    public View view(Context c) {
        if (root != null) return root;
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), 0);
        search = Ui.input(c, "", Tr.t("ui_search", "Search..."));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { filter(); }
        });
        col.addView(search, Ui.matchWrap());
        empty = Ui.text(c, Tr.t("ui_no_elements", "No elements yet.\nTap + to add your first one."), 14, Ui.TEXT_3);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(c, 30), 0, 0);
        col.addView(empty, Ui.matchWrap());
        list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setAdapter(adapter);
        // expand / collapse: the rows fade in / out and the ones below slide (insert / remove range, no full rebuild)
        androidx.recyclerview.widget.DefaultItemAnimator anim = new androidx.recyclerview.widget.DefaultItemAnimator();
        anim.setSupportsChangeAnimations(false);
        anim.setAddDuration(ANIM_MS);
        anim.setRemoveDuration(ANIM_MS * 3 / 4);
        anim.setMoveDuration(ANIM_MS);
        anim.setChangeDuration(0);
        list.setItemAnimator(anim);
        col.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root = col;
        return root;
    }

    @Override public void onShown() { refresh(); }

    /** Selection / undo / scene changed. */
    public void onEditorChanged() {
        if (shell.panels.isShowing(this)) refresh();
    }

    private String imagesPath;

    public void refresh() {
        if (!Bridge.ready() || pending) return;
        pending = true;
        if (imagesPath == null) Bridge.query(() -> Bridge.editor().getProject().getImagesPath(), p -> {
            imagesPath = p;
            adapter.notifyDataSetChanged();
        });
        Bridge.query(Bridge::items, items -> {
            pending = false;
            all.clear();
            all.addAll(items);
            boolean reveal = revealSelection();
            filter();
            if (reveal) scrollToSelected();
        });
    }

    private void filter() {
        if (list == null) return;
        buildShown();
        adapter.notifyDataSetChanged();
        empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean searching() {
        return search != null && !search.getText().toString().trim().isEmpty();
    }

    /** Fills `shown` (+ tree guides) from `all`: collapsed parents hide their children, a search shows every match. */
    private void buildShown() {
        String q = search.getText().toString().toLowerCase(Locale.ROOT).trim();
        shown.clear();
        if (q.isEmpty()) {
            int skipBelow = -1;
            for (Bridge.ItemInfo i : all) {
                if (skipBelow >= 0 && i.depth > skipBelow) continue;
                skipBelow = -1;
                shown.add(i);
                if (isCollapsed(i)) skipBelow = i.depth;
            }
        } else {
            for (Bridge.ItemInfo i : all)
                if (i.name.toLowerCase(Locale.ROOT).contains(q)) shown.add(i);
        }
        computeTree(q.isEmpty());
    }

    /** Expand / collapse one parent with an animation: only the rows below it are inserted or removed. */
    private void toggle(Bridge.ItemInfo info, Holder h) {
        if (searching()) return;
        int pos = h.getAdapterPosition();
        if (pos < 0 || pos >= shown.size() || shown.get(pos) != info) { filter(); return; }
        java.util.HashSet<String> set = expandedOf(info.scene);
        boolean open = set.contains(info.name);
        if (open) set.remove(info.name); else set.add(info.name);
        int before = shown.size();
        buildShown();
        int diff = shown.size() - before;
        h.chevron.animate().cancel();
        h.chevron.animate().rotation(chevronRotation(!open)).setDuration(ANIM_MS)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        if (diff > 0) adapter.notifyItemRangeInserted(pos + 1, diff);
        else if (diff < 0) adapter.notifyItemRangeRemoved(pos + 1, -diff);
    }

    /** Down arrow when open; turned toward the reading direction when closed (mirrored for Arabic). */
    private float chevronRotation(boolean open) {
        if (open) return 0f;
        return Ui.isRtl(root != null ? root.getContext() : list.getContext()) ? 90f : -90f;
    }

    /** A new selection inside collapsed parents opens them (true when the selection changed and is now reachable). */
    private boolean revealSelection() {
        String sel = "";
        Bridge.ItemInfo selInfo = null;
        for (Bridge.ItemInfo i : all) if (i.selected) { sel = i.name; selInfo = i; break; }
        if (sel.equals(lastSelected)) return false;
        lastSelected = sel;
        if (selInfo == null) return false;
        java.util.HashMap<String, Bridge.ItemInfo> byName = new java.util.HashMap<>();
        for (Bridge.ItemInfo i : all) byName.put(i.name, i);
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        Bridge.ItemInfo p = byName.get(selInfo.parent);
        while (p != null && seen.add(p.name)) {
            expandedOf(p.scene).add(p.name);
            p = byName.get(p.parent);
        }
        return true;
    }

    private void scrollToSelected() {
        for (int k = 0; k < shown.size(); k++) {
            if (!shown.get(k).selected) continue;
            final int idx = k;
            list.post(() -> {
                androidx.recyclerview.widget.LinearLayoutManager lm = (androidx.recyclerview.widget.LinearLayoutManager) list.getLayoutManager();
                if (lm == null) return;
                if (idx < lm.findFirstCompletelyVisibleItemPosition() || idx > lm.findLastCompletelyVisibleItemPosition()) lm.scrollToPositionWithOffset(idx, Ui.dp(list.getContext(), 60));
            });
            return;
        }
    }

    /** Per shown row: bit L set = draw a vertical guide in column L; bit 31 = last child. */
    private int[] guides = new int[0];

    private void computeTree(boolean tree) {
        int n = shown.size();
        guides = new int[n];
        if (!tree) return;
        boolean[] last = new boolean[n];
        for (int k = 0; k < n; k++) {
            int d = shown.get(k).depth;
            last[k] = true;
            for (int j = k + 1; j < n; j++) {
                int dj = shown.get(j).depth;
                if (dj < d) break;
                if (dj == d) { last[k] = false; break; }
            }
        }
        boolean[] open = new boolean[64]; // open[L] = ancestor at depth L+1 still has siblings below
        for (int k = 0; k < n; k++) {
            int d = Math.min(shown.get(k).depth, 30);
            int g = 0;
            for (int L = 0; L < d - 1; L++) if (open[L]) g |= 1 << L;
            if (d > 0) {
                if (last[k]) g |= 1 << 31;
                open[d - 1] = !last[k];
            }
            guides[k] = g;
        }
    }

    /** Draws "│ ├── └──" connectors from parents to children. */
    static class TreeLines extends View {
        int depth, bits;
        final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final int step;
        TreeLines(Context c) {
            super(c);
            step = Ui.dp(c, 18);
            p.setColor(0x66A0A8B4);
            p.setStrokeWidth(Ui.dp(c, 1.5f));
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        }
        void set(int depth, int bits) {
            this.depth = depth;
            this.bits = bits;
            getLayoutParams().width = step * depth;
            requestLayout();
            invalidate();
        }
        @Override
        protected void onDraw(android.graphics.Canvas cv) {
            if (depth == 0) return;
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            float h = getHeight(), mid = h / 2f, r = Ui.dp(getContext(), 5);
            float top = 0, bottom = h;
            int w = getWidth();
            for (int L = 0; L < depth; L++) {
                float x = L * step + step / 2f;
                if (rtl) x = w - x;
                if (L < depth - 1) {
                    if ((bits & (1 << L)) != 0) cv.drawLine(x, top, x, bottom, p);
                    continue;
                }
                boolean isLast = (bits & (1 << 31)) != 0;
                float end = rtl ? 0 : w;
                float dir = rtl ? -1 : 1;
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(x, top);
                path.lineTo(x, mid - r);
                path.quadTo(x, mid, x + dir * r, mid);
                path.lineTo(end - dir * Ui.dp(getContext(), 2), mid);
                cv.drawPath(path, p);
                if (!isLast) cv.drawLine(x, mid - r, x, bottom, p);
            }
        }
    }

    public static int typeIcon(String type) {
        if (type == null) return R.drawable.ic_s2_square;
        switch (type) {
            case "Box": return R.drawable.ic_s2_square;
            case "Circle": return R.drawable.ic_s2_circle;
            case "Text": return R.drawable.ic_s2_text;
            case "Joystick": return R.drawable.ic_s2_gamepad;
            case "Progress": return R.drawable.ic_s2_progress;
            case "Custom": return R.drawable.ic_s2_collider;
            case "Particle": return R.drawable.ic_s2_particle;
            case "Camera": return R.drawable.ic_s2_videocam;
            case "Map": return R.drawable.ic_s2_map;
            case "Group": return R.drawable.ic_s2_container;
            case "UIHolder": return R.drawable.ic_s2_widgets;
            case "Light": return R.drawable.ic_s2_light;
            case "OBJ3D": case "Cube": case "Obj3d": return R.drawable.ic_s2_cube;
            case "Sphere": return R.drawable.ic_s2_sphere;
            case "Cylinder": return R.drawable.ic_s2_cylinder;
            case "Terrain": return R.drawable.ic_s2_terrain;
            case "Water": return R.drawable.ic_s2_water;
            case "Model": return R.drawable.ic_s2_view3d;
            case "LIGHT3D": case "Light3d": return R.drawable.ic_s2_light;
            case "CAMERA3D": case "Camera3d": return R.drawable.ic_s2_videocam;
            case "CAMERAVIEW": case "Cameraview": return R.drawable.ic_s2_camera;
        }
        return R.drawable.ic_s2_square;
    }

    public static int typeColor(String type) {
        if (type == null) return Ui.ACCENT;
        switch (type) {
            case "Circle": return 0xFF26C6DA;
            case "Text": return 0xFFFFCA28;
            case "Joystick": return 0xFFAB47BC;
            case "Progress": return 0xFF66BB6A;
            case "Custom": return 0xFFFF7043;
            case "Particle": return 0xFFEC407A;
            case "Camera": return 0xFF78909C;
            case "Map": return 0xFF8D6E63;
            case "Group": return 0xFFFFC107;
            case "UIHolder": return 0xFFFF9800;
            case "Light": return 0xFFFFEE58;
            case "OBJ3D": case "Cube": case "Obj3d": return 0xFF42A5F5;
            case "Sphere": return 0xFF26A69A;
            case "Cylinder": return 0xFF7E57C2;
            case "Terrain": return 0xFF9CCC65;
            case "Water": return 0xFF29B6F6;
            case "Model": return 0xFFFF8A65;
            case "LIGHT3D": case "Light3d": return 0xFFFFD54F;
            case "CAMERA3D": case "Camera3d": return 0xFFB0BEC5;
            case "CAMERAVIEW": case "Cameraview": return 0xFF90A4AE;
        }
        return Ui.ACCENT;
    }

    private void select(String name) {
        Bridge.gl(() -> {
            Actor a = Bridge.actor(name);
            if (a != null) Bridge.editor().selectActor(a);
        });
    }

    private void menu(View anchor, Bridge.ItemInfo info) {
        Context c = anchor.getContext();
        com.star4droid.star2d.ui.IconMenu m = new com.star4droid.star2d.ui.IconMenu(c);
        m.add(R.drawable.ic_s2_properties, Ui.ACCENT, Tr.t("properties", "Properties"), () -> { select(info.name); shell.panels.show(shell.properties); });
        m.add(R.drawable.ic_s2_code, 0xFFAB47BC, Tr.t("events", "Events"), () -> { select(info.name); shell.panels.show(shell.events); });
        m.add(R.drawable.ic_s2_edit, 0xFFFFB74D, Tr.t("rename", "Rename"), () ->
                Dialogs.input(c, Tr.t("rename", "Rename"), Tr.t("name", "Name"), info.name, Dialogs::checkName, v ->
                        Bridge.query(() -> {
                            Actor a = Bridge.actor(info.name);
                            return a == null ? "?" : ItemActions.rename(Bridge.app(), a, v);
                        }, err -> { if (err != null) Ui.toast(c, err); refresh(); })));
        final boolean fixedRoot = shell.inPrefabMode() && "root".equals(info.pid);
        if (!fixedRoot) m.add(R.drawable.ic_s2_copy, Tr.t("ui_duplicate", "Duplicate"), () -> {
            Bridge.gl(() -> {
                try { ItemActions.duplicate(Bridge.app(), Bridge.actor(info.name)); } catch (Exception e) { throw new RuntimeException(e); }
            });
            refreshLater();
        });
        if (!shell.inPrefabMode()) {
            if (info.prefab != null && !info.prefab.isEmpty()) {
                m.add(com.star4droid.star2d.ui.PrefabUi.ICON, com.star4droid.star2d.ui.PrefabUi.COLOR, Tr.t("prefab_open", "Open prefab"), () -> shell.openPrefab(info.prefab));
                m.add(R.drawable.ic_s2_link, Ui.TEXT_2, Tr.t("prefab_unpack", "Unpack"), () -> com.star4droid.star2d.ui.PrefabUi.unpack(shell, info.name));
            } else if (info.pid == null || info.pid.isEmpty()) {
                m.add(com.star4droid.star2d.ui.PrefabUi.ICON, com.star4droid.star2d.ui.PrefabUi.COLOR, Tr.t("prefab_save_as", "Save as prefab"), () -> com.star4droid.star2d.ui.PrefabUi.saveAsPrefab(shell, info.name));
            }
        }
        m.add(R.drawable.ic_s2_link, Tr.t("ui_move_into", "Move into..."), () -> moveInto(c, info));
        if ("Group".equals(info.type)) {
            m.add(R.drawable.ic_s2_merge, Tr.t("ui_ungroup", "Ungroup"), () -> {
                Bridge.gl(() -> {
                    Actor a = Bridge.actor(info.name);
                    if (a != null) ItemActions.ungroup(Bridge.app(), a);
                });
                refreshLater();
            });
        } else {
            m.add(R.drawable.ic_s2_container, Tr.t("ui_wrap_group", "Wrap in new group"), () -> {
                Bridge.gl(() -> {
                    Actor a = Bridge.actor(info.name);
                    if (a != null) ItemActions.wrapInGroup(Bridge.app(), a);
                });
                refreshLater();
            });
        }
        m.add(R.drawable.ic_s2_arrow_up, Tr.t("ui_bring_forward", "Bring forward"), () -> { Bridge.gl(() -> ItemActions.changeZ(Bridge.app(), Bridge.actor(info.name), 1)); refreshLater(); });
        m.add(R.drawable.ic_s2_arrow_down, Tr.t("ui_send_backward", "Send backward"), () -> { Bridge.gl(() -> ItemActions.changeZ(Bridge.app(), Bridge.actor(info.name), -1)); refreshLater(); });
        m.divider();
        if (!fixedRoot) m.add(R.drawable.ic_s2_delete, 0xFFFF8A80, Tr.t("delete", "Delete"), () ->
                Dialogs.confirm(c, Tr.t("delete", "Delete"), Tr.t("areYouSure", "Are you sure?") + "\n" + info.name, Tr.t("delete", "Delete"),
                        () -> { Bridge.gl(() -> {
                            Actor a = Bridge.actor(info.name);
                            if (a != null) ItemActions.delete(Bridge.app(), a);
                        }); refreshLater(); }));
        m.show(anchor);
    }

    /** Picker: put the element inside another one (or back to the top level). */
    private void moveInto(Context c, Bridge.ItemInfo info) {
        Bridge.query(() -> {
            com.star4droid.star2d.editor.LibgdxEditor ed = Bridge.editor();
            ArrayList<String> names = new ArrayList<>();
            java.util.HashSet<String> banned = new java.util.HashSet<>();
            banned.add(info.name);
            for (Actor a : com.star4droid.star2d.editor.Hierarchy.descendantsOf(ed, info.name)) banned.add(a.getName());
            for (Bridge.ItemInfo it : Bridge.items())
                if (!banned.contains(it.name) && !it.isLight) names.add(it.name);
            return names;
        }, names -> {
            String[] items = new String[names.size() + 1];
            items[0] = Tr.t("ui_parent_none", "None (root)");
            for (int k = 0; k < names.size(); k++) items[k + 1] = names.get(k);
            Dialogs.choose(c, Tr.t("ui_move_into", "Move into...") + " - " + info.name, items, which -> {
                String parent = which == 0 ? "" : names.get(which - 1);
                Bridge.query(() -> ItemActions.moveInto(Bridge.app(), info.name, parent), err -> {
                    if (err != null) Ui.toast(c, err);
                    refresh();
                });
            });
        });
    }

    private void refreshLater() {
        if (list != null) list.postDelayed(this::refresh, 150);
    }

    // ------------------------------------------------------------------ adapter

    private class Holder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final ImageView icon, eye, lock, more, chevron;
        final TextView name, type;
        final TreeLines indent;
        Holder(LinearLayout row, TreeLines indent, ImageView chevron, ImageView icon, TextView name, TextView type, ImageView eye, ImageView lock, ImageView more) {
            super(row);
            this.row = row; this.indent = indent; this.chevron = chevron; this.icon = icon; this.name = name; this.type = type;
            this.eye = eye; this.lock = lock; this.more = more;
        }
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 2), 0);
            row.setMinimumHeight(Ui.dp(c, 50));
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TreeLines indent = new TreeLines(c);
            row.addView(indent, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
            ImageView chevron = Ui.smallIcon(c, R.drawable.ic_s2_expand_more, Ui.TEXT_2, 18);
            chevron.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 38)));
            chevron.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
            chevron.setBackground(Ui.ripple(0, Ui.dp(c, 10)));
            row.addView(chevron);
            ImageView icon = Ui.smallIcon(c, R.drawable.ic_s2_square, Ui.ACCENT, 22);
            icon.setBackground(Ui.rounded(0x22FFFFFF, Ui.dp(c, 7)));
            icon.setPadding(Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5));
            icon.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)));
            row.addView(icon);
            LinearLayout texts = Ui.column(c);
            texts.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 4), 0);
            TextView name = Ui.text(c, "", 15, Ui.TEXT);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setMaxLines(1);
            TextView type = Ui.text(c, "", 11.5f, Ui.TEXT_3);
            texts.addView(name);
            texts.addView(type);
            row.addView(texts, Ui.weight(1));
            ImageView eye = small(c, R.drawable.ic_s2_visible);
            ImageView lock = small(c, R.drawable.ic_s2_lock_open);
            ImageView more = small(c, R.drawable.ic_s2_more);
            row.addView(eye);
            row.addView(lock);
            row.addView(more);
            return new Holder(row, indent, chevron, icon, name, type, eye, lock, more);
        }

        private ImageView small(Context c, int res) {
            ImageView i = Ui.smallIcon(c, res, Ui.TEXT_2, 20);
            int s = Ui.dp(c, 38);
            i.setLayoutParams(new LinearLayout.LayoutParams(s, s));
            int p = Ui.dp(c, 9);
            i.setPadding(p, p, p, p);
            i.setBackground(Ui.ripple(0, Ui.dp(c, 19)));
            return i;
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Bridge.ItemInfo i = shown.get(position);
            Context c = h.row.getContext();
            h.indent.set(i.depth, position < guides.length ? guides[position] : 0);
            h.icon.setTag(null);
            h.icon.setImageResource(typeIcon(i.type));
            h.icon.setImageTintList(android.content.res.ColorStateList.valueOf(typeColor(i.type)));
            // elements with a picture show it
            if (imagesPath != null && i.image != null && !i.image.isEmpty()) {
                java.io.File img = new java.io.File((imagesPath + "/" + i.image).replace("//", "/"));
                if (img.isFile()) com.star4droid.star2d.ui.FilePickerDialog.thumb(h.icon, img, Ui.dp(c, 32));
            }
            h.name.setText(i.name);
            boolean searching = searching();
            boolean hiddenNow = !i.visible || i.ancHidden;
            h.name.setAlpha(hiddenNow ? 0.5f : 1f);
            String typeLabel = i.type == null ? "" : Tr.t("ui_type_" + i.type.toLowerCase(Locale.ROOT), i.type);
            if (i.children > 0) typeLabel += " · " + String.format(Locale.US, Tr.t("ui_n_children", "%d inside"), i.children);
            // group chevron: open / closed
            h.chevron.animate().cancel();
            if (i.children > 0 && !searching) {
                h.chevron.setVisibility(View.VISIBLE);
                h.chevron.setImageResource(R.drawable.ic_s2_expand_more);
                h.chevron.setRotation(chevronRotation(!isCollapsed(i)));
                h.chevron.setOnClickListener(v -> toggle(i, h));
            } else {
                h.chevron.setVisibility(View.INVISIBLE);
                h.chevron.setOnClickListener(null);
            }
            // prefab instances: the name is tinted, the root also shows which prefab it is
            boolean inst = i.pid != null && !i.pid.isEmpty();
            h.name.setTextColor(inst ? com.star4droid.star2d.ui.PrefabUi.COLOR : Ui.TEXT);
            if (i.prefab != null && !i.prefab.isEmpty()) typeLabel += "  ·  " + Tr.t("prefab", "Prefab") + ": " + com.star4droid.star2d.ui.PrefabUi.shortName(i.prefab);
            else if (inst && "root".equals(i.pid)) typeLabel += "  ·  " + Tr.t("prefab_root", "prefab root");
            h.type.setText(typeLabel);
            h.row.setBackground(i.selected ? Ui.rounded(Ui.ACCENT_SOFT, Ui.dp(c, 8), Ui.ACCENT, Ui.dp(c, 1)) : Ui.ripple(0, Ui.dp(c, 8)));
            // inside a hidden / locked group the element is hidden / locked too (its own flag is untouched): shown dimmed
            boolean lockedNow = i.locked || i.ancLocked;
            h.eye.setImageResource(hiddenNow ? R.drawable.ic_s2_visible_off : R.drawable.ic_s2_visible);
            h.eye.setAlpha(i.visible && i.ancHidden ? 0.45f : 1f);
            h.lock.setImageResource(lockedNow ? R.drawable.ic_s2_lock : R.drawable.ic_s2_lock_open);
            h.lock.setImageTintList(android.content.res.ColorStateList.valueOf(lockedNow ? Ui.WARNING : Ui.TEXT_3));
            h.lock.setAlpha(!i.locked && i.ancLocked ? 0.45f : 1f);
            h.row.setOnClickListener(v -> select(i.name));
            com.star4droid.star2d.ui.tutorial.Tut.tag(h.row, "elem:" + i.name);
            h.row.setOnLongClickListener(v -> { menu(h.more, i); return true; });
            h.more.setOnClickListener(v -> menu(h.more, i));
            h.eye.setOnClickListener(v -> {
                Bridge.gl(() -> {
                    Actor a = Bridge.actor(i.name);
                    if (a != null) ItemActions.toggleVisible(Bridge.app(), a, i.name);
                });
                refreshLater();
            });
            h.lock.setOnClickListener(v -> {
                Bridge.gl(() -> {
                    Actor a = Bridge.actor(i.name);
                    if (a != null) ItemActions.toggleLock(Bridge.app(), a, i.name);
                });
                refreshLater();
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
