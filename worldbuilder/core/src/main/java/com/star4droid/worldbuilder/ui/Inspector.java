package com.star4droid.worldbuilder.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.star4droid.worldbuilder.editor.Editor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.star4droid.worldbuilder.engine.Assets;
import com.star4droid.worldbuilder.engine.Collider;
import com.star4droid.worldbuilder.engine.ModelDef;
import com.star4droid.worldbuilder.engine.Scene;
import com.star4droid.worldbuilder.engine.SceneObject;

import static com.star4droid.worldbuilder.ui.Ui.dp;

/** The properties of the selected element(s): transform, colors, effect values, shader. */
final class Inspector {
    final EditorUI ui;
    final Editor ed;
    private final Array<Runnable> updaters = new Array<>();
    private static final Color[] AX = {Color.valueOf("ff6b6e"), Color.valueOf("62d96b"), Color.valueOf("5aa2ff"), Color.valueOf("ffd65a")};

    Inspector(EditorUI ui) { this.ui = ui; ed = ui.ed; }

    void refreshValues() { for (Runnable r : updaters) r.run(); }

    interface Get { float get(SceneObject o); }
    interface Set { void set(SceneObject o, float v); }

    /** A value box: tap to type, drag sideways to change. */
    private Table number(String title, String letter, Color c, float step, Get get, Set set) {
        Table t = new Table();
        t.setBackground(Ui.roundSmall(Ui.FIELD)); t.pad(dp(6), dp(6), dp(6), dp(6));
        t.setTouchable(Touchable.enabled);
        Label l = new Label(letter, new Label.LabelStyle(Ui.small, c)), v = new Label("", new Label.LabelStyle(Ui.font, Ui.TEXT));
        v.setEllipsis(true); v.setAlignment(Align.right);
        t.add(l).padRight(dp(3)); t.add(v).growX().minWidth(0);
        Runnable up = () -> { SceneObject o = ed.single(); if (o != null) v.setText(format(get.get(o))); };
        up.run(); updaters.add(up);
        t.addListener(new InputListener() {
            float x0, y0, v0; boolean scrub, dead;
            @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
                SceneObject o = ed.single(); if (o == null || p != 0) return false;
                x0 = e.getStageX(); y0 = e.getStageY(); v0 = get.get(o); scrub = false; dead = false; return true;
            }
            @Override public void touchDragged(InputEvent e, float x, float y, int p) {
                SceneObject o = ed.single(); if (o == null || dead) return;
                float dx = e.getStageX() - x0, dy = e.getStageY() - y0;
                if (!scrub) {
                    if (Math.abs(dy) > dp(9) && Math.abs(dy) >= Math.abs(dx)) { dead = true; return; }       // a vertical drag scrolls the list
                    if (Math.abs(dx) > dp(10) && Math.abs(dx) > Math.abs(dy) * 1.6f) {
                        scrub = true; ed.beginEdit(); x0 = e.getStageX(); dx = 0;
                        ui.stage.cancelTouchFocusExcept(this, t);                                           // the list stays still while the value changes
                    } else return;
                }
                set.set(o, Math.round((v0 + dx / dp(1) * step) / step) * step); o.markDirty(); refreshValues();
            }
            @Override public void touchUp(InputEvent e, float x, float y, int p, int b) {
                if (scrub) { scrub = false; ed.endEdit(); return; }
                if (dead || e.getStageX() == Integer.MIN_VALUE) return;                                     // scrolled, or the list took the touch
                if (Math.abs(e.getStageX() - x0) > dp(9) || Math.abs(e.getStageY() - y0) > dp(9)) return;
                SceneObject o = ed.single(); if (o == null) return;
                ui.dialogs.number(title + " " + letter, get.get(o), f -> { if (ed.single() == o) { ed.edit(() -> set.set(o, f)); refreshValues(); } });
            }
        });
        return t;
    }

    static String format(float f) {
        float r = Math.round(f * 100) / 100f;
        return r == Math.round(r) ? String.valueOf(Math.round(r)) : String.valueOf(r);
    }

    private Table triple(String title, Table... boxes) {
        Table t = new Table();
        Label l = Ui.dim(title); l.setAlignment(Ui.start());
        t.add(l).growX().padBottom(dp(3)).row();
        Table r = new Table(); r.defaults().growX().uniformX().space(dp(4)).minWidth(0);
        for (Table b : boxes) r.add(b);          // X Y Z keep their order in every language
        t.add(r).growX();
        return t;
    }

    /** Label at the start and a color swatch at the end; opens the color picker. */
    Table colorRow(String text, float[] rgb, float[] reset, Runnable changed, Runnable begin, Runnable done) {
        Table t = new Table();
        t.setTouchable(Touchable.enabled);
        Label l = Ui.label(text); l.setEllipsis(true); l.setAlignment(Ui.start());
        Button[] sw = new Button[1];
        Runnable openPicker = () -> ui.dialogs.color(text, rgb, reset, (r, g, b) -> {
            rgb[0] = r; rgb[1] = g; rgb[2] = b;
            ((Image) sw[0].getUserObject()).setColor(r, g, b, 1);
            if (changed != null) changed.run();
        }, begin, done);
        sw[0] = Ui.swatch(new Color(rgb[0], rgb[1], rgb[2], 1), 30, null);
        Ui.click(t, openPicker);
        if (L.rtl) { t.add(sw[0]); t.add(l).growX().minWidth(0).padLeft(dp(8)); } else { t.add(l).growX().minWidth(0).padRight(dp(8)); t.add(sw[0]); }
        return t;
    }

    void build(Table b) {
        updaters.clear();
        Array<SceneObject> sel = ed.selection;
        if (sel.size == 0) {
            b.add(Ui.icon("near_me", 30, Ui.LINE)).size(dp(30)).padTop(dp(26)).padBottom(dp(8)).row();
            Label a = Ui.bold(L.t("Nothing selected")); a.setAlignment(Align.center);
            Label d = Ui.dim(ed.mode == Editor.OBJECTS ? L.t("Tap an element in the world to edit it, or pick one from the library and tap the ground to place it.") : L.t("Switch to Objects to select elements."));
            d.setWrap(true); d.setAlignment(Align.center);
            b.add(a).padBottom(dp(4)).row(); b.add(d).row();
            return;
        }
        SceneObject o = sel.first();
        ModelDef d = o.def;
        boolean one = sel.size == 1, same = true;
        for (SceneObject x : sel) if (x.def != d) same = false;

        // header
        Table head = new Table();
        TextureRegion tr = ui.app.renderer.thumb(d.id);
        Image im = tr == null ? new Image() : new Image(new TextureRegionDrawable(tr), Scaling.fit);
        Table names = new Table();
        Label name = Ui.bold(one ? o.name : L.t("{0} elements", sel.size)); name.setEllipsis(true); name.setAlignment(Ui.start());
        Label sub = Ui.dim(one ? L.t(d.name) + " • " + L.t(d.sub) : same ? L.t(d.name) : L.t("Mixed")); sub.setEllipsis(true); sub.setAlignment(Ui.start());
        names.add(name).growX().minWidth(0).row(); names.add(sub).growX().minWidth(0);
        if (one) { names.setTouchable(Touchable.enabled); Ui.click(names, () -> ui.dialogs.prompt(L.t("Element name"), o.name, false, s -> { ed.edit(() -> o.name = s); ui.rebuildTab(); })); }
        Ui.row(head, im, names);
        head.getCell(im).size(dp(44)); head.getCell(names).growX().minWidth(0).pad(0, dp(8), 0, dp(8));
        b.add(head).padTop(dp(6)).padBottom(dp(6)).row();

        Table acts = new Table(); acts.defaults().growX().uniformX().space(dp(4)).height(dp(34));
        Ui.row(acts, Ui.iconButton("center_focus_strong", 34, 18, true, ed::focusSelection), Ui.iconButton("content_copy", 34, 18, true, ed::duplicateSelection),
                Ui.iconButton("vertical_align_bottom", 34, 18, true, ed::dropToGround),
                Ui.iconButton("visibility_off", 34, 18, true, () -> { for (SceneObject x : sel) x.hidden = true; ed.dirty = true; ed.clearSelection(); ui.toast(L.t("Hidden. Show it again from the Scene tab.")); }),
                Ui.iconButton("delete", 34, 18, true, ed::deleteSelection));
        b.add(acts).padBottom(dp(10)).row();

        if (one) {
            b.add(Ui.section(L.t("Transform"))).padBottom(dp(4)).row();
            b.add(triple(L.t("Position"), number(L.t("Position"), "X", AX[0], 0.05f, x -> x.pos.x, (x, v) -> x.pos.x = v), number(L.t("Position"), "Y", AX[1], 0.05f, x -> x.pos.y, (x, v) -> x.pos.y = v), number(L.t("Position"), "Z", AX[2], 0.05f, x -> x.pos.z, (x, v) -> x.pos.z = v))).padBottom(dp(6)).row();
            b.add(triple(L.t("Rotation"), number(L.t("Rotation"), "X", AX[0], 1, x -> x.rotX, (x, v) -> x.rotX = v), number(L.t("Rotation"), "Y", AX[1], 1, x -> x.rotY, (x, v) -> x.rotY = v), number(L.t("Rotation"), "Z", AX[2], 1, x -> x.rotZ, (x, v) -> x.rotZ = v))).padBottom(dp(6)).row();
            boolean sized = d.kind != ModelDef.MESH;
            Set all = (x, v) -> { float u = Math.min(x.scale.x, Math.min(x.scale.y, x.scale.z)); if (u > 0.0001f && v > 0.01f) x.scale.scl(v / u); };
            Set sx = (x, v) -> x.scale.x = clampScale(v), sy = (x, v) -> x.scale.y = clampScale(v), sz = (x, v) -> x.scale.z = clampScale(v);
            if (sized) b.add(triple(d.kind == ModelDef.FALL ? L.t("Size in meters (width, height)") : L.t("Size in meters"), number(L.t("Size"), "X", AX[0], 0.05f, x -> x.scale.x, sx), number(L.t("Size"), "Y", AX[1], 0.05f, x -> x.scale.y, sy), number(L.t("Size"), "Z", AX[2], 0.05f, x -> x.scale.z, sz))).padBottom(dp(6)).row();
            else b.add(triple(L.t("Scale"), number(L.t("Scale"), L.t("All"), AX[3], 0.01f, x -> Math.min(x.scale.x, Math.min(x.scale.y, x.scale.z)), all), number(L.t("Scale"), "X", AX[0], 0.01f, x -> x.scale.x, sx), number(L.t("Scale"), "Y", AX[1], 0.01f, x -> x.scale.y, sy), number(L.t("Scale"), "Z", AX[2], 0.01f, x -> x.scale.z, sz))).padBottom(dp(6)).row();
            if (d.smart()) {
                Table tip = new Table(); tip.setBackground(Ui.round(Ui.CARD)); tip.pad(dp(8));
                Label t = Ui.dim(L.t("Smart size: change X, Y or Z alone and the design adapts (more windows, posts, planks) instead of stretching. \"All\" simply makes it bigger."));
                t.setWrap(true); t.setAlignment(Ui.start());
                if (L.rtl) { tip.add(t).growX().padRight(dp(8)); tip.add(Ui.icon("auto_awesome", 18, Ui.ACCENT)).size(dp(18)).top(); }
                else { tip.add(Ui.icon("auto_awesome", 18, Ui.ACCENT)).size(dp(18)).top(); tip.add(t).growX().padLeft(dp(8)); }
                b.add(tip).padBottom(dp(6)).row();
            }
            Table rs = new Table(); rs.defaults().growX().uniformX().space(dp(5)).minWidth(0);
            Ui.row(rs, Ui.button(L.t("Reset size"), () -> { ed.edit(() -> { if (sized) Scene.defaultScale(d, o.scale); else o.scale.set(1, 1, 1); }); refreshValues(); }),
                    Ui.button(L.t("Reset rotation"), () -> { ed.edit(() -> { o.rotX = 0; o.rotY = 0; o.rotZ = 0; }); refreshValues(); }));
            b.add(rs).padBottom(dp(12)).row();
        }

        if (same && d.tintNames.length > 0) {
            b.add(Ui.section(L.t("Colors"))).padBottom(dp(4)).row();
            for (int i = 0; i < d.tintNames.length && i < 2; i++) {
                int slot = i;
                float[] rgb = (slot == 0 ? o.tintA : o.tintB).clone();
                b.add(colorRow(L.t(d.tintNames[i]), rgb, d.tintDefaults[i], () -> {
                    for (SceneObject x : sel) System.arraycopy(rgb, 0, slot == 0 ? x.tintA : x.tintB, 0, 3);
                }, ed::beginEdit, ed::endEdit)).padBottom(dp(5)).row();
            }
            b.add().padBottom(dp(7)).row();
        }

        if (same && d.kind != ModelDef.MESH) {
            b.add(Ui.section(d.kind == ModelDef.EFFECT ? L.t("Effect") : L.t("Water"))).padBottom(dp(4)).row();
            b.add(Ui.sliderRow(L.t("Speed"), 0, 3, 0.05f, o.speed, "×", v -> { for (SceneObject x : sel) x.speed = v; }, ed::beginEdit, ed::endEdit)).row();
            if (d.kind != ModelDef.WATER) b.add(Ui.sliderRow(d.kind == ModelDef.FALL ? L.t("Mist") : L.t("Amount"), 0, 3, 0.05f, o.amount, "×", v -> { for (SceneObject x : sel) x.amount = v; }, ed::beginEdit, ed::endEdit)).row();
            b.add().padBottom(dp(8)).row();
        }

        boolean allMesh = true;
        for (SceneObject x : sel) if (x.def.kind != ModelDef.MESH) allMesh = false;
        if (allMesh) {
            b.add(Ui.section(L.t("Look"))).padBottom(dp(4)).row();
            // shader: the name, and beside it the button that opens the list of ready shaders
            Table sh = new Table();
            Label sl = Ui.label(L.t("Shader")); sl.setAlignment(Ui.start());
            String[] info = o.fx.isEmpty() ? null : Assets.fxInfo.get(o.fx);
            Button nameB = Ui.button(info == null ? L.t("None") : L.t(info[0]), null, Ui.FIELD, null);
            Runnable pick = () -> {
                ed.beginEdit();
                ui.dialogs.shaders(o.fx, n -> { for (SceneObject x : sel) x.fx = n; });
                ui.dialogs.open.peek().setUserObject((Runnable) () -> { ed.endEdit(); ui.rebuildTab(); });
            };
            Button pickB = Ui.iconButton("auto_awesome", 36, 19, true, pick);
            pickB.getStyle().up = Ui.round(Ui.ACCENT_DIM);
            Ui.click(nameB, pick);
            Ui.row(sh, sl, nameB, pickB);
            sh.getCell(sl).padRight(L.rtl ? 0 : dp(8)).padLeft(L.rtl ? dp(8) : 0); sh.getCell(nameB).growX().minWidth(0); sh.getCell(pickB).size(dp(36)).padLeft(L.rtl ? 0 : dp(5)).padRight(L.rtl ? dp(5) : 0);
            b.add(sh).padBottom(dp(6)).row();
            if (info != null) {
                Label ds = Ui.dim(L.t(info[2])); ds.setWrap(true); ds.setAlignment(Ui.start());
                b.add(ds).padBottom(dp(4)).row();
                b.add(Ui.sliderRow(L.t("Progress (0 = plays by itself)"), 0, 1, 0.01f, o.fxAmount, "", v -> { for (SceneObject x : sel) x.fxAmount = v; }, ed::beginEdit, ed::endEdit)).row();
                b.add(Ui.sliderRow(L.t("Shader speed"), 0.1f, 3, 0.05f, o.speed, "×", v -> { for (SceneObject x : sel) x.speed = v; }, ed::beginEdit, ed::endEdit)).row();
                b.add(Ui.toggle(L.t("Own shader color"), o.fxTintOn, v -> { ed.edit(() -> { for (SceneObject x : sel) x.fxTintOn = v; }); ui.rebuildTab(); })).padBottom(dp(5)).row();
                if (o.fxTintOn) {
                    float[] rgb = o.fxTint.clone();
                    b.add(colorRow(L.t("Shader color"), rgb, null, () -> { for (SceneObject x : sel) System.arraycopy(rgb, 0, x.fxTint, 0, 3); }, ed::beginEdit, ed::endEdit)).padBottom(dp(5)).row();
                }
            }
            boolean wind = false;
            for (SceneObject x : sel) if (x.def.hasWind()) wind = true;
            if (wind) b.add(Ui.toggle(L.t("Moves in the wind"), o.wind, v -> ed.edit(() -> { for (SceneObject x : sel) x.wind = v; }))).padBottom(dp(5)).row();
            b.add(Ui.toggle(L.t("Casts a shadow"), o.shadow, v -> ed.edit(() -> { for (SceneObject x : sel) x.shadow = v; }))).padBottom(dp(5)).row();
        }
        collider(b, sel, one);
    }

    /** Collision shape: type, trigger, center and size (the collider tool edits it by dragging). */
    private void collider(Table b, Array<SceneObject> sel, boolean one) {
        SceneObject o = sel.first();
        int type = o.colType;
        boolean mixed = false, trig = o.colTrigger;
        for (SceneObject x : sel) if (x.colType != type) mixed = true;
        b.add(Ui.section(L.t("Collider"))).padTop(dp(8)).padBottom(dp(4)).row();
        Table types = new Table(); types.defaults().growX().uniformX().space(dp(3)).minWidth(0);
        ButtonGroup<Button> g = new ButtonGroup<>(); g.setMinCheckCount(0);
        Array<Button> chips = new Array<>();
        for (int i = 0; i < Collider.NAMES.length; i++) {
            int k = i;
            Button c = Ui.chip("", null);
            c.pad(dp(6), dp(1), dp(6), dp(1));
            Label l = new Label(L.t(Collider.NAMES[i]), new Label.LabelStyle(Ui.small, Ui.TEXT)); l.setAlignment(Align.center); l.setEllipsis(true);
            c.add(l).growX().minWidth(0);
            c.setChecked(!mixed && type == i); g.add(c);
            Ui.click(c, () -> { ed.edit(() -> { for (SceneObject x : sel) { x.update(ed.scene); Collider.fit(x, k); } }); ui.rebuildTab(); });
            chips.add(c);
        }
        Ui.row(types, chips.toArray(Button.class));
        b.add(types).padBottom(dp(6)).row();
        if (mixed || type == Collider.NONE) {
            Label n = Ui.dim(mixed ? L.t("The selected elements have different colliders.") : L.t("No collider: things pass through this element."));
            n.setWrap(true); n.setAlignment(Ui.start());
            b.add(n).padBottom(dp(6)).row();
            if (type == Collider.NONE && !mixed) return;
        }
        b.add(Ui.toggle(L.t("Trigger (detects, does not block)"), trig, v -> ed.edit(() -> { for (SceneObject x : sel) x.colTrigger = v; }))).padBottom(dp(6)).row();
        if (!one || mixed) return;
        if (type == Collider.MESH) {
            Label n = Ui.dim(L.t("Uses the element's own shape. The outline shows its size."));
            n.setWrap(true); n.setAlignment(Ui.start());
            b.add(n).padBottom(dp(6)).row();
            return;
        }
        String cName = L.t("Collider center");
        b.add(triple(cName, number(cName, "X", AX[0], 0.05f, x -> x.colCenter.x, (x, v) -> x.colCenter.x = v), number(cName, "Y", AX[1], 0.05f, x -> x.colCenter.y, (x, v) -> x.colCenter.y = v), number(cName, "Z", AX[2], 0.05f, x -> x.colCenter.z, (x, v) -> x.colCenter.z = v))).padBottom(dp(6)).row();
        if (type == Collider.BOX) {
            String n = L.t("Collider size");
            b.add(triple(n, number(n, "X", AX[0], 0.05f, x -> x.colSize.x, (x, v) -> x.colSize.x = Math.max(0.05f, v)), number(n, "Y", AX[1], 0.05f, x -> x.colSize.y, (x, v) -> x.colSize.y = Math.max(0.05f, v)), number(n, "Z", AX[2], 0.05f, x -> x.colSize.z, (x, v) -> x.colSize.z = Math.max(0.05f, v)))).padBottom(dp(6)).row();
        } else if (type == Collider.SPHERE) {
            String n = L.t("Radius");
            b.add(triple(n, number(n, "R", AX[3], 0.05f, x -> x.colRadius, (x, v) -> x.colRadius = Math.max(0.05f, v)))).padBottom(dp(6)).row();
        } else {
            String n = L.t("Radius and height");
            b.add(triple(n, number(L.t("Radius"), "R", AX[3], 0.05f, x -> x.colRadius, (x, v) -> x.colRadius = Math.max(0.05f, v)), number(L.t("Height"), "H", AX[1], 0.05f, x -> x.colHeight, (x, v) -> x.colHeight = Math.max(0.1f, v)))).padBottom(dp(6)).row();
        }
        Table acts = new Table(); acts.defaults().growX().uniformX().space(dp(5)).minWidth(0);
        Button fitB = Ui.button(L.t("Fit to element"), () -> { ed.edit(() -> { o.update(ed.scene); Collider.fit(o, type); }); refreshValues(); });
        Button editB = Ui.button(L.t("Edit shape"), "crop_free", ed.tool == Editor.COLLIDER ? Ui.ACCENT_DIM : Ui.CARD, () -> ed.setTool(ed.tool == Editor.COLLIDER ? Editor.MOVE : Editor.COLLIDER));
        Ui.row(acts, fitB, editB);
        b.add(acts).padBottom(dp(6)).row();
        Label tip = Ui.dim(L.t("Sizes are in the element's own units, so the collider grows with the element. With the collider tool, drag the dots on its faces."));
        tip.setWrap(true); tip.setAlignment(Ui.start());
        b.add(tip).row();
    }

    static float clampScale(float v) { return Math.max(0.05f, Math.min(80, v)); }
}
