package com.star4droid.worldbuilder.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.star4droid.worldbuilder.engine.Assets;
import com.star4droid.worldbuilder.engine.Settings;
import com.star4droid.worldbuilder.engine.WorldIO;

import static com.star4droid.worldbuilder.ui.Ui.dp;

/** Pop-ups: menu, text and number entry, confirmations, color and shader pickers, settings, worlds list, help. */
public final class Dialogs {
    final EditorUI ui;
    final Stage stage;
    final Array<Table> open = new Array<>();
    final Array<Texture> pictures = new Array<>();

    Dialogs(EditorUI ui) { this.ui = ui; stage = ui.stage; }

    public boolean any() { return open.size > 0; }

    public void closeAll() { while (open.size > 0) close(open.peek()); }

    /** Closes the top pop-up (Back / Escape). */
    public boolean back() { if (open.size == 0) return false; close(open.peek()); return true; }

    void close(Table layer) {
        if (!open.removeValue(layer, true)) return;
        Object after = layer.getUserObject();
        layer.remove();
        stage.setKeyboardFocus(null); stage.unfocusAll();
        Gdx.input.setOnscreenKeyboardVisible(false);
        syncEditor();
        if (open.size == 0) { for (Texture t : pictures) t.dispose(); pictures.clear(); }
        if (after instanceof Runnable) ((Runnable) after).run();
    }

    /**
     * A layer over the whole screen holding one card.
     * @param align where the card sits (Align.center, Align.top, Align.right...); dim darkens the rest; tapping outside closes
     */
    /** Side pickers (not dimmed) leave the view free to move; real dialogs block the editor. */
    void syncEditor() {
        boolean block = false;
        for (Table t : open) if (t.getTouchable() == Touchable.enabled) block = true;
        ui.ed.enabled = !block && !ui.loading;
        ui.ed.navOnly = open.size > 0;
    }

    Table layer(Table card, int align, boolean dim, boolean outsideCloses) {
        Table layer = new Table();
        layer.setFillParent(true);
        layer.setTouchable(dim ? Touchable.enabled : Touchable.childrenOnly);
        if (dim) layer.setBackground(Ui.flat(Ui.SCRIM));
        layer.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
                if (e.getTarget() == layer && outsideCloses) close(layer);
                return dim || e.getTarget() != layer;
            }
        });
        float m = dp(12);
        layer.add(card).align(align).expand().pad(m).maxWidth(stage.getWidth() - m * 2).maxHeight(stage.getHeight() - m * 2);
        stage.addActor(layer);
        open.add(layer);
        syncEditor();
        return layer;
    }

    Table card(String title) {
        Table c = new Table();
        c.setBackground(Ui.round(Ui.PANEL));
        c.setTouchable(Touchable.enabled);
        c.addListener(new InputListener() { @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) { return true; } });
        c.pad(dp(14));
        c.defaults().growX();
        if (title != null) { Label t = Ui.title(title); t.setAlignment(Ui.start()); c.add(t).padBottom(dp(10)).row(); }
        return c;
    }

    Table buttons(Button... bs) {
        Table t = new Table();
        t.defaults().growX().uniformX().space(dp(8)).minWidth(dp(84));
        Ui.row(t, bs);
        return t;
    }

    // ------------------------------------------------------------------ simple ones
    public void message(String title, String text) {
        Table c = card(title);
        Label l = Ui.label(text); l.setWrap(true);
        c.add(l).width(Math.min(dp(330), stage.getWidth() - dp(60))).padBottom(dp(12)).row();
        Table[] layer = new Table[1];
        c.add(buttons(Ui.primary(L.t("OK"), () -> close(layer[0]))));
        layer[0] = layer(c, Align.center, true, true);
    }

    public void confirm(String title, String text, String yes, boolean danger, Runnable onYes) {
        Table c = card(title);
        Label l = Ui.label(text); l.setWrap(true);
        c.add(l).width(Math.min(dp(330), stage.getWidth() - dp(60))).padBottom(dp(12)).row();
        Table[] layer = new Table[1];
        c.add(buttons(Ui.button(L.t("Cancel"), () -> close(layer[0])), Ui.button(yes, null, danger ? Ui.DANGER.cpy().mul(0.8f, 0.8f, 0.8f, 1) : Ui.ACCENT_DIM, () -> { close(layer[0]); onYes.run(); })));
        layer[0] = layer(c, Align.center, true, true);
    }

    public interface Text { void set(String s); }

    /** Text or number entry. The card sits at the top of the screen so the phone keyboard never covers it. */
    public void prompt(String title, String initial, boolean number, Text done) {
        Table c = card(title);
        TextField f = Ui.field(initial);
        if (number) f.setTextFieldFilter((tf, ch) -> Character.isDigit(ch) || ch == '.' || ch == '-');
        f.setMaxLength(number ? 10 : 40);
        Table[] layer = new Table[1];
        Runnable ok = () -> { String s = f.getText().trim(); close(layer[0]); if (!s.isEmpty()) done.set(s); };
        f.setTextFieldListener((tf, ch) -> { if (ch == '\n' || ch == '\r') ok.run(); });
        c.add(f).width(Math.min(dp(300), stage.getWidth() - dp(60))).padBottom(dp(12)).row();
        c.add(buttons(Ui.button(L.t("Cancel"), () -> close(layer[0])), Ui.primary(L.t("OK"), ok)));
        layer[0] = layer(c, Align.top, true, true);
        layer[0].getCell(c).padTop(dp(54));
        stage.setKeyboardFocus(f);
        f.selectAll();
        Gdx.input.setOnscreenKeyboardVisible(true, number ? Input.OnscreenKeyboardType.NumberPad : Input.OnscreenKeyboardType.Default);
    }

    public interface Num { void set(float v); }

    /**
     * Number entry with its own keys (no phone keyboard): digits, sign, point, delete and confirm.
     * Portrait: at the bottom; landscape: at the end side, so the element stays in view.
     */
    public void number(String title, float value, Num done) {
        Table c = card(null);
        boolean small = stage.getHeight() / Ui.scale < 520;
        float key = dp(small ? 40 : 48), gap = dp(5), w = Math.min(dp(small ? 250 : 290), stage.getWidth() - dp(40));
        float kw = (float) Math.floor((w - gap * 3) / 4);
        StringBuilder text = new StringBuilder(Inspector.format(value));
        boolean[] fresh = {true};
        Table[] layer = new Table[1];
        Label name = Ui.dim(title); name.setEllipsis(true); name.setAlignment(Ui.start());
        Label shown = new Label(text, new Label.LabelStyle(Ui.title, Ui.TEXT)); shown.setAlignment(Align.right); shown.setEllipsis(true);
        Table disp = new Table(); disp.setBackground(Ui.pad(Ui.round(Ui.FIELD), 12, 0));
        disp.add(shown).growX().minWidth(0);
        Runnable show = () -> { shown.setText(text.length() == 0 ? "0" : text.toString()); shown.setColor(fresh[0] ? Ui.DIM : Ui.TEXT); };
        Runnable ok = () -> {
            float v = value;
            try { v = Float.parseFloat(text.length() == 0 || text.toString().equals("-") ? "0" : text.toString()); } catch (NumberFormatException ignored) { }
            close(layer[0]);
            if (!Float.isNaN(v) && !Float.isInfinite(v)) done.set(v);
        };
        java.util.function.Consumer<String> press = k -> {
            switch (k) {
                case "del": if (fresh[0]) text.setLength(0); else if (text.length() > 0) text.setLength(text.length() - 1); fresh[0] = false; break;
                case "clr": text.setLength(0); fresh[0] = false; break;
                case "+-": if (fresh[0]) fresh[0] = false; if (text.length() > 0 && text.charAt(0) == '-') text.deleteCharAt(0); else text.insert(0, '-'); break;
                case ".": if (fresh[0]) { text.setLength(0); fresh[0] = false; } if (text.indexOf(".") < 0) text.append(text.length() == 0 || text.toString().equals("-") ? "0." : "."); break;
                default: if (fresh[0]) { text.setLength(0); fresh[0] = false; } if (text.length() < 10) text.append(k);
            }
            show.run();
        };
        Table head = new Table();
        Button x = Ui.iconButton("close", 32, 18, false, () -> close(layer[0]));
        Ui.row(head, name, x);
        head.getCell(name).growX().minWidth(0); head.getCell(x).size(dp(32));
        c.add(head).width(w).padBottom(dp(4)).row();
        c.add(disp).width(w).height(key).padBottom(gap * 1.6f).row();
        String[][] rows = {{"7", "8", "9", "del"}, {"4", "5", "6", "clr"}, {"1", "2", "3", "+-"}, {".", "0", "ok", "ok"}};
        Table keys = new Table();
        for (String[] r : rows) {
            for (int i = 0; i < r.length; i++) {
                String k = r[i];
                if (k.equals("ok") && i == 3) continue;
                Button b;
                if (k.equals("del")) b = Ui.button("", "backspace", Ui.CARD, null);
                else if (k.equals("ok")) b = Ui.button("", "check", Ui.ACCENT_DIM, null);
                else b = Ui.button(k.equals("clr") ? "C" : k.equals("+-") ? "\u00b1" : k, null, k.equals("clr") || k.equals("+-") ? Ui.CARD : Ui.CARD_HI, null);
                b.pad(0);
                Ui.click(b, k.equals("ok") ? ok : () -> press.accept(k));
                if (k.equals("ok")) keys.add(b).colspan(2).size(kw * 2 + gap, key).pad(0, 0, gap, 0);
                else keys.add(b).size(kw, key).pad(0, 0, gap, i == 3 ? 0 : gap);
            }
            keys.row();
        }
        c.add(keys).width(w);
        show.run();
        layer[0] = layer(c, ui.portrait ? Align.bottom : Align.center | Ui.end(), false, true);
        // a real keyboard works too
        c.addListener(new InputListener() {
            @Override public boolean keyTyped(InputEvent e, char ch) {
                if (ch >= '0' && ch <= '9') press.accept(String.valueOf(ch)); else if (ch == '.' || ch == ',') press.accept("."); else if (ch == '-') press.accept("+-"); else if (ch == '\n' || ch == '\r') ok.run(); else if (ch == '\b') press.accept("del"); else return false;
                return true;
            }
        });
        stage.setKeyboardFocus(c);
    }

    /** A column of actions (the main menu). */
    public void menu(String title, String[] icons, String[] texts, Runnable[] actions) {
        Table c = card(title);
        Table[] layer = new Table[1];
        for (int i = 0; i < texts.length; i++) {
            int k = i;
            Button b = new Button(new Button.ButtonStyle(null, Ui.round(Ui.CARD_HI), null));
            b.getStyle().over = Ui.round(Ui.CARD);
            b.pad(dp(9), dp(8), dp(9), dp(8));
            Label l = Ui.label(texts[i]);
            if (L.rtl) { l.setAlignment(Align.right); b.add(l).growX().padRight(dp(12)); b.add(Ui.icon(icons[i], 20, Ui.TEXT)).size(dp(20)); }
            else { b.add(Ui.icon(icons[i], 20, Ui.TEXT)).size(dp(20)); b.add(l).growX().padLeft(dp(12)); }
            Ui.click(b, () -> { close(layer[0]); actions[k].run(); });
            c.add(b).width(Math.min(dp(250), stage.getWidth() - dp(60))).row();
        }
        layer[0] = layer(c, Align.top | Ui.start(), true, true);
        layer[0].getCell(c).padTop(dp(50));
    }

    // ------------------------------------------------------------------ color picker
    public interface ColorChange { void set(float r, float g, float b); }
    static final String[] SWATCHES = {"ffffff", "d9d9d9", "8e8e93", "3a3a3c", "f5e6c8", "c9a26b", "8b5a2b", "5c3a1e", "ff5a5f", "ff8c42", "ffd23f", "c5e063", "5fd068", "2e8b57", "2ec4b6", "4cc9f0", "4f9dff", "3a56d4", "8a5cff", "c77dff", "ff6fb5", "ffb3c6", "9b2226", "264653"};

    public void color(String title, float[] rgb, float[] reset, ColorChange change, Runnable begin, Runnable done) {
        Table c = card(title);
        float w0 = Math.min(dp(300), stage.getWidth() - dp(60));
        float[] hsv = new float[3];
        Color col = new Color(rgb[0], rgb[1], rgb[2], 1);
        col.toHsv(hsv);
        Image preview = new Image(Ui.round(Color.WHITE)); preview.setColor(col);
        Image satOver = new Image(new TextureRegionDrawable(Ui.fade)), valOver = new Image(new TextureRegionDrawable(Ui.fade));
        Slider sh = bare(0, 360, hsv[0]), ss = bare(0, 1, hsv[1]), sv = bare(0, 1, hsv[2]);
        Label hex = Ui.dim("");
        boolean[] started = {false}, quiet = {false};
        Runnable apply = () -> {
            col.fromHsv(sh.getValue(), ss.getValue(), sv.getValue()); col.a = 1;
            preview.setColor(col);
            Color full = new Color().fromHsv(sh.getValue(), 1, 1); full.a = 1;
            satOver.setColor(full); Color vc = new Color().fromHsv(sh.getValue(), ss.getValue(), 1); vc.a = 1; valOver.setColor(vc);
            hex.setText("#" + col.toString().substring(0, 6).toUpperCase());
            if (quiet[0]) return;
            if (!started[0]) { started[0] = true; if (begin != null) begin.run(); }
            change.set(col.r, col.g, col.b);
        };
        ChangeListener cl = new ChangeListener() { @Override public void changed(ChangeEvent e, Actor a) { apply.run(); } };
        sh.addListener(cl); ss.addListener(cl); sv.addListener(cl);
        boolean wide = !ui.portrait && stage.getHeight() / Ui.scale < 520;       // short screens: two columns
        float w = w0;
        if (wide) w = Math.min(dp(230), (stage.getWidth() - dp(90)) / 2);
        Table top = new Table();
        top.add(preview).size(dp(54), dp(wide ? 30 : 38));
        top.add(hex).padLeft(dp(10)).expandX().left();
        Table sw = new Table();
        int cols = 8;
        for (int i = 0; i < SWATCHES.length; i++) {
            Color sc = Color.valueOf(SWATCHES[i]);
            Button b = Ui.swatch(sc, (w / Ui.scale - 7 * 5) / cols, () -> { float[] h = new float[3]; sc.toHsv(h); quiet[0] = true; sh.setValue(h[0]); ss.setValue(h[1]); sv.setValue(h[2]); quiet[0] = false; apply.run(); });
            sw.add(b).pad(dp(2.5f));
            if (i % cols == cols - 1) sw.row();
        }
        Image satBase = new Image(Ui.flat(Color.WHITE)), valBase = new Image(Ui.flat(Color.BLACK));
        Table a = new Table(), bcol = new Table();
        a.defaults().growX(); bcol.defaults().growX();
        a.add(top).padBottom(dp(10)).row();
        a.add(sw).row();
        bcol.add(strip(L.t("Hue"), new Image(new TextureRegionDrawable(Ui.hue)), null, sh, w)).row();
        bcol.add(strip(L.t("Saturation"), satBase, satOver, ss, w)).row();
        bcol.add(strip(L.t("Brightness"), valBase, valOver, sv, w)).padBottom(dp(10)).row();
        quiet[0] = true; apply.run(); quiet[0] = false;
        Table[] layer = new Table[1];
        Button resetB = Ui.button(L.t("Reset"), () -> { if (reset == null) return; float[] h = new float[3]; new Color(reset[0], reset[1], reset[2], 1).toHsv(h); quiet[0] = true; sh.setValue(h[0]); ss.setValue(h[1]); sv.setValue(h[2]); quiet[0] = false; apply.run(); });
        bcol.add(reset == null ? buttons(Ui.primary(L.t("Done"), () -> close(layer[0]))) : buttons(resetB, Ui.primary(L.t("Done"), () -> close(layer[0]))));
        if (wide) { Table two = new Table(); two.add(a).top().padRight(dp(14)); two.add(bcol).top(); c.add(two); }
        else { c.add(a).padBottom(dp(8)).row(); c.add(bcol); }
        // the card sits at the side so the element stays visible while its color changes
        layer[0] = layer(c, ui.portrait ? Align.bottom : Align.center | Ui.end(), false, true);
        layer[0].setUserObject((Runnable) () -> { if (started[0] && done != null) done.run(); });
    }

    private Slider bare(float min, float max, float v) {
        Slider.SliderStyle s = new Slider.SliderStyle();
        TextureRegionDrawable knob = new TextureRegionDrawable(Ui.circle); knob.setMinWidth(dp(20)); knob.setMinHeight(dp(20));
        s.knob = knob;
        Slider sl = new Slider(min, max, (max - min) / 360f, false, s);
        sl.setValue(v);
        return sl;
    }

    private Table strip(String name, Image base, Image over, Slider s, float w) {
        Table t = new Table();
        Label l = Ui.dim(name); l.setAlignment(Ui.start());
        t.add(l).growX().row();
        Stack st = new Stack();
        Table bg = new Table(); Stack bars = new Stack(base); if (over != null) bars.add(over);
        base.setScaling(Scaling.stretch); if (over != null) over.setScaling(Scaling.stretch);
        bg.add(bars).growX().height(dp(12)).pad(0, dp(9), 0, dp(9));
        st.add(bg); st.add(s);
        t.add(st).width(w).height(dp(30));
        return t;
    }

    // ------------------------------------------------------------------ shader picker
    /** Lists the element shaders by group; a tap applies it at once so the result shows behind the list. */
    public void shaders(String current, Text pick) {
        Table c = card(L.t("Element shader"));
        float w = Math.min(dp(300), stage.getWidth() - dp(40));
        Table list = new Table(); list.top().defaults().growX();
        ButtonGroup<Button> group = new ButtonGroup<>(); group.setMinCheckCount(0);
        Button none = entry(L.t("None"), L.t("Normal look"), "close");
        none.setChecked(current.isEmpty()); group.add(none);
        Ui.click(none, () -> pick.set(""));
        list.add(none).padBottom(dp(4)).row();
        String last = "";
        for (String n : Assets.fxNames) {
            String[] info = Assets.fxInfo.get(n);
            if (!info[1].equals(last)) { last = info[1]; list.add(Ui.section(L.t(info[1]))).padTop(dp(8)).padBottom(dp(4)).row(); }
            Button b = entry(info[0], info[2], "auto_awesome");
            b.setChecked(n.equals(current)); group.add(b);
            Ui.click(b, () -> pick.set(n));
            list.add(b).padBottom(dp(4)).row();
        }
        float h = ui.portrait ? stage.getHeight() * 0.42f : stage.getHeight() - dp(150);
        c.add(Ui.scroll(list, false)).width(w).height(h).padBottom(dp(10)).row();
        ui.hoverScroll(c);
        Table[] layer = new Table[1];
        c.add(buttons(Ui.primary(L.t("Done"), () -> close(layer[0]))));
        layer[0] = layer(c, ui.portrait ? Align.bottom : Align.center | Ui.end(), false, true);
    }

    private Button entry(String name, String desc, String icon) {
        Button.ButtonStyle st = new Button.ButtonStyle(Ui.round(Ui.CARD), Ui.round(Ui.CARD_HI), Ui.round(Ui.ACCENT_DIM));
        Button b = new Button(st);
        b.pad(dp(7), dp(9), dp(7), dp(9));
        Table text = new Table();
        Label a = Ui.bold(name), d = Ui.dim(desc);
        a.setAlignment(Ui.start()); d.setAlignment(Ui.start()); d.setWrap(true);
        text.add(a).growX().row(); text.add(d).growX();
        if (L.rtl) { b.add(text).growX().padRight(dp(9)); b.add(Ui.icon(icon, 18, Ui.TEXT)).size(dp(18)); }
        else { b.add(Ui.icon(icon, 18, Ui.TEXT)).size(dp(18)); b.add(text).growX().padLeft(dp(9)); }
        return b;
    }

    // ------------------------------------------------------------------ settings
    public void settings() {
        Table c = card(L.t("Settings"));
        float w = Math.min(dp(340), stage.getWidth() - dp(40));
        Table b = new Table(); b.top().defaults().growX().padBottom(dp(8));
        Table[] layer = new Table[1];
        Runnable reopen = () -> { close(layer[0]); settings(); };

        b.add(Ui.section(L.t("Scene quality"))).row();
        Table q = new Table(); q.defaults().growX().uniformX().space(dp(5));
        ButtonGroup<Button> qg = new ButtonGroup<>();
        for (int i = 0; i < 4; i++) {
            int k = i;
            Button chip = Ui.chip(L.t(Settings.QUALITY[i]), null);
            chip.setChecked(Settings.quality == i); qg.add(chip);
            Ui.click(chip, () -> { Settings.preset(k); Settings.save(); reopen.run(); });
            q.add(chip);
        }
        b.add(q).row();
        if (Settings.quality == 4) b.add(Ui.dim(L.t("Custom quality"))).row();
        Runnable custom = () -> { Settings.quality = 4; Settings.save(); };
        b.add(Ui.sliderRow(L.t("Render distance"), 60, 320, 10, Settings.renderDistance, " m", v -> { Settings.renderDistance = v; custom.run(); }, null, null)).row();
        b.add(Ui.sliderRow(L.t("Resolution"), 50, 100, 5, Math.round(Settings.resScale * 100), " %", v -> { Settings.resScale = v / 100f; custom.run(); }, null, null)).row();
        Table sh = new Table(); sh.defaults().growX().uniformX().space(dp(5));
        ButtonGroup<Button> sg = new ButtonGroup<>();
        String[] sn = {"Off", "Normal", "Soft"};
        for (int i = 0; i < 3; i++) { int k = i; Button chip = Ui.chip(L.t(sn[i]), null); chip.setChecked(Settings.shadows == i); sg.add(chip); Ui.click(chip, () -> { Settings.shadows = k; custom.run(); }); sh.add(chip); }
        Label sl = Ui.label(L.t("Shadows")); sl.setAlignment(Ui.start());
        b.add(sl).padBottom(dp(3)).row();
        b.add(sh).row();
        b.add(Ui.toggle(L.t("Detail textures"), Settings.detail, v -> { Settings.detail = v; custom.run(); })).row();
        b.add(Ui.toggle(L.t("Water reflections and caustics"), Settings.waterFull, v -> { Settings.waterFull = v; custom.run(); })).row();
        b.add(Ui.toggle(L.t("Wind animation"), Settings.wind, v -> { Settings.wind = v; custom.run(); })).row();
        b.add(Ui.toggle(L.t("Show FPS"), Settings.showFps, v -> { Settings.showFps = v; Settings.save(); })).row();

        b.add(Ui.section(L.t("Editing"))).padTop(dp(6)).row();
        b.add(Ui.toggle(L.t("Grid on the ground"), Settings.grid, v -> { Settings.grid = v; Settings.save(); })).row();
        b.add(Ui.toggle(L.t("Snap to grid"), Settings.snap, v -> { Settings.snap = v; Settings.save(); ui.syncTools(); })).row();
        b.add(Ui.sliderRow(L.t("Grid step"), 0.25f, 4, 0.25f, Settings.snapStep, " m", v -> { Settings.snapStep = v; Settings.save(); }, null, null)).row();
        b.add(Ui.toggle(L.t("Keep elements on the ground when moving"), Settings.groundSnap, v -> { Settings.groundSnap = v; Settings.save(); ui.syncTools(); })).row();
        b.add(Ui.toggle(L.t("Show the colliders of all elements"), Settings.showColliders, v -> { Settings.showColliders = v; Settings.save(); })).row();
        b.add(Ui.toggle(L.t("Autosave"), Settings.autosave, v -> { Settings.autosave = v; Settings.save(); })).row();

        b.add(Ui.section(L.t("Interface"))).padTop(dp(6)).row();
        float[] newScale = {Settings.uiScale};
        b.add(Ui.sliderRow(L.t("Interface size"), 80, 140, 5, Math.round(Settings.uiScale * 100), " %", v -> newScale[0] = v / 100f, null, null)).row();

        c.add(Ui.scroll(b, false)).width(w).height(Math.min(dp(430), stage.getHeight() - dp(140))).padBottom(dp(10)).row();
        ui.hoverScroll(c);
        c.add(buttons(Ui.primary(L.t("Done"), () -> close(layer[0]))));
        layer[0] = layer(c, Align.center, true, true);
        layer[0].setUserObject((Runnable) () -> {
            Settings.save();
            if (Math.abs(newScale[0] - Settings.uiScale) > 0.01f) { Settings.uiScale = newScale[0]; Settings.save(); ui.app.rebuildUi(); }
        });
    }

    // ------------------------------------------------------------------ worlds
    public void newWorld() {
        Table c = card(L.t("New world"));
        Table[] layer = new Table[1];
        float w = Math.min(dp(320), stage.getWidth() - dp(40));
        Label l = Ui.dim(L.t("Choose how to start. You can change everything afterwards.")); l.setWrap(true); l.setAlignment(Ui.start());
        c.add(l).width(w).padBottom(dp(10)).row();
        String[][] opts = {{"Island village", "A finished island: plateau, waterfall, river, lake, village, forest and effects.", "filter_hdr"}, {"Empty meadow", "A flat green island by the sea, ready for your own design.", "grass"}};
        for (int i = 0; i < opts.length; i++) {
            int k = i;
            Button b = entry(L.t(opts[i][0]), L.t(opts[i][1]), opts[i][2]);
            Ui.click(b, () -> { close(layer[0]); ui.app.newWorld(k); });
            c.add(b).width(w).padBottom(dp(6)).row();
        }
        c.add(buttons(Ui.button(L.t("Cancel"), () -> close(layer[0])))).padTop(dp(6));
        layer[0] = layer(c, Align.center, true, true);
    }

    public void worlds() {
        Table c = card(L.t("My worlds"));
        Table[] layer = new Table[1];
        float w = Math.min(dp(380), stage.getWidth() - dp(40));
        Table list = new Table(); list.top().defaults().growX().padBottom(dp(6));
        Array<String> names = WorldIO.list();
        if (names.size == 0) { Label e = Ui.dim(L.t("No saved worlds yet. Use Save in the menu.")); e.setWrap(true); list.add(e).width(w - dp(20)).pad(dp(20)); }
        for (String n : names) {
            Table row = new Table(); row.setBackground(Ui.round(Ui.CARD)); row.pad(dp(7));
            Image pic;
            try {
                if (WorldIO.picture(n).exists()) { Texture t = new Texture(WorldIO.picture(n)); t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear); pictures.add(t); pic = new Image(new TextureRegionDrawable(t), Scaling.fill); }
                else pic = new Image(Ui.round(Ui.LINE));
            } catch (Exception ex) { pic = new Image(Ui.round(Ui.LINE)); }
            Label name = Ui.bold(n); name.setEllipsis(true); name.setAlignment(Ui.start());
            Label cur = Ui.dim(n.equals(ui.ed.scene.name) ? L.t("Open now") : "");
            Table text = new Table(); text.add(name).growX().minWidth(0).row(); text.add(cur).growX();
            Button openB = Ui.button(L.t("Open"), null, Ui.ACCENT_DIM, () -> { close(layer[0]); ui.app.openWorld(n); });
            Button del = Ui.iconButton("delete", 36, 19, true, () -> confirm(L.t("Delete world"), L.t("Delete \"{0}\"? This cannot be undone.", n), L.t("Delete"), true, () -> { WorldIO.delete(n); close(layer[0]); worlds(); }));
            Ui.row(row, pic, text, openB, del);
            row.getCell(pic).size(dp(76), dp(46));
            row.getCell(text).growX().minWidth(0).pad(0, dp(9), 0, dp(6));
            row.getCell(del).size(dp(36)).padLeft(L.rtl ? 0 : dp(6)).padRight(L.rtl ? dp(6) : 0);
            list.add(row).row();
        }
        c.add(Ui.scroll(list, false)).width(w).height(Math.min(dp(330), stage.getHeight() - dp(150))).padBottom(dp(10)).row();
        ui.hoverScroll(c);
        c.add(buttons(Ui.button(L.t("New world"), "add", Ui.CARD, () -> { close(layer[0]); ui.askNew(); }), Ui.primary(L.t("Close"), () -> close(layer[0]))));
        layer[0] = layer(c, Align.center, true, true);
    }

    public void help() {
        Table c = card(L.t("How to use"));
        float w = Math.min(dp(380), stage.getWidth() - dp(40));
        String[][] rows = {
                {"pan_tool", "Look around", "Drag with one finger to turn the view. Use two fingers to move, pinch to zoom and twist to turn.\nMouse: right button turns, middle button moves, wheel zooms, arrow keys move."},
                {"add_circle", "Place", "Pick an element in the library, then tap the ground. Tap the element again in the library to stop placing."},
                {"open_with", "Edit", "Tap an element to select it. Drag the arrows, rings or boxes to move, turn or resize it; drag the element itself to slide it on the ground."},
                {"open_in_full", "Smart size", "Stretch houses, walls, fences and bridges on one axis: the design adapts (more windows, posts and planks) instead of deforming. The middle handle resizes everything together."},
                {"terrain", "Terrain", "Raise, lower, smooth, flatten and paint the ground. Dig below the sea level to make lakes and rivers."},
                {"grass", "Scatter", "Choose plants or rocks and paint them over the ground."},
                {"crop_free", "Colliders", "Every element can have a collision shape for games: a box, a sphere, a capsule or its own mesh. Choose it in Properties, or pick the collider tool and drag the dots on its faces to resize it."},
                {"auto_awesome", "Effects and shaders", "Add light pillars, lights, fire and magic from the Effects category. Give any element a shader from its properties."},
        };
        Table b = new Table(); b.top().defaults().growX().padBottom(dp(10));
        for (String[] r : rows) {
            Table row = new Table();
            Table text = new Table();
            Label a = Ui.bold(L.t(r[1])), d = Ui.dim(L.t(r[2])); d.setWrap(true); a.setAlignment(Ui.start()); d.setAlignment(Ui.start());
            text.add(a).growX().row(); text.add(d).growX();
            if (L.rtl) { row.add(text).growX().padRight(dp(10)); row.add(Ui.icon(r[0], 22, Ui.ACCENT)).size(dp(22)).top(); }
            else { row.add(Ui.icon(r[0], 22, Ui.ACCENT)).size(dp(22)).top(); row.add(text).growX().padLeft(dp(10)); }
            b.add(row).row();
        }
        c.add(Ui.scroll(b, false)).width(w).height(Math.min(dp(380), stage.getHeight() - dp(140))).padBottom(dp(10)).row();
        ui.hoverScroll(c);
        Table[] layer = new Table[1];
        c.add(buttons(Ui.primary(L.t("Got it"), () -> close(layer[0]))));
        layer[0] = layer(c, Align.center, true, true);
    }
}
