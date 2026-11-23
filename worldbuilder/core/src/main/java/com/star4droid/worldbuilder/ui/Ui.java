package com.star4droid.worldbuilder.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Scaling;
import com.star4droid.worldbuilder.engine.Assets;
import com.star4droid.worldbuilder.engine.Settings;

/** Look of the app, built in code: fonts, rounded shapes, icons, colors and small widget helpers. */
public final class Ui {
    private Ui() {}
    public static float forceDensity;
    public static float scale = 1;
    public static BitmapFont font, small, bold, title;
    public static final Color BG = Color.valueOf("161b27f4"), PANEL = Color.valueOf("1e2534"), CARD = Color.valueOf("283145"), CARD_HI = Color.valueOf("34405a"),
            ACCENT = Color.valueOf("4f9dff"), ACCENT_DIM = Color.valueOf("2b5fa8"), TEXT = Color.valueOf("eaf0fa"), DIM = Color.valueOf("97a4bc"), LINE = Color.valueOf("323c52"),
            DANGER = Color.valueOf("ff6470"), OK = Color.valueOf("56d68b"), SCRIM = Color.valueOf("05080dbb"), FIELD = Color.valueOf("121722");
    private static final Array<Texture> textures = new Array<>();
    private static final ObjectMap<String, TextureRegion> icons = new ObjectMap<>();
    private static NinePatch round, roundSmall, pill, outline;
    public static TextureRegion white, circle, hue, fade;

    public static float dp(float v) { return Math.round(v * scale); }

    public static float density() {
        if (forceDensity > 0) return forceDensity;
        if (Gdx.app.getType() == Application.ApplicationType.Android) return Math.max(1f, Gdx.graphics.getDensity());
        return Math.max(1f, Math.min(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()) / 760f);
    }

    public static void load() {
        dispose();
        scale = density() * Settings.uiScale;
        FreeTypeFontGenerator reg = new FreeTypeFontGenerator(Assets.file("fonts/Inter-Regular.otf")), semi = new FreeTypeFontGenerator(Assets.file("fonts/Inter-SemiBold.otf"));
        font = make(reg, 13.5f); small = make(reg, 11.5f); bold = make(semi, 13.5f); title = make(semi, 16.5f);
        reg.dispose(); semi.dispose();
        Pixmap p = new Pixmap(4, 4, Pixmap.Format.RGBA8888); p.setColor(Color.WHITE); p.fill();
        white = new TextureRegion(tex(p, false), 1, 1, 2, 2);
        round = patch(dp(9), 0); roundSmall = patch(dp(5), 0); pill = patch(dp(15), 0); outline = patch(dp(9), Math.max(1.2f, dp(1.5f)));
        int c = (int) dp(32);
        Pixmap cp = new Pixmap(c, c, Pixmap.Format.RGBA8888); cp.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < c; y++) for (int x = 0; x < c; x++) { float d = (float) Math.hypot(x + 0.5f - c / 2f, y + 0.5f - c / 2f); cp.drawPixel(x, y, 0xffffff00 | (int) (255 * clamp01(c / 2f - 0.5f - d + 0.5f))); }
        circle = new TextureRegion(tex(cp, true));
        Pixmap hp = new Pixmap(256, 1, Pixmap.Format.RGBA8888); Color tmp = new Color();
        for (int x = 0; x < 256; x++) { tmp.fromHsv(x / 255f * 360f, 1, 1); tmp.a = 1; hp.drawPixel(x, 0, Color.rgba8888(tmp)); }
        hue = new TextureRegion(tex(hp, true));
        Pixmap fp = new Pixmap(256, 1, Pixmap.Format.RGBA8888); fp.setBlending(Pixmap.Blending.None);
        for (int x = 0; x < 256; x++) fp.drawPixel(x, 0, 0xffffff00 | x);
        fade = new TextureRegion(tex(fp, true));
        Texture it = new Texture(Assets.file("ui/icons.png"));      // not a power of two: no mipmaps (GLES 2)
        it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        textures.add(it);
        String[] names = Assets.file("ui/icons.txt").readString().split("\n");
        for (int i = 0; i < names.length; i++) if (!names[i].trim().isEmpty()) icons.put(names[i].trim(), new TextureRegion(it, (i % 12) * 64 + 4, (i / 12) * 64 + 4, 56, 56));
    }

    private static float clamp01(float v) { return Math.max(0, Math.min(1, v)); }

    private static BitmapFont make(FreeTypeFontGenerator g, float size) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = Math.max(8, Math.round(size * scale));
        p.minFilter = Texture.TextureFilter.Linear; p.magFilter = Texture.TextureFilter.Linear;
        p.hinting = FreeTypeFontGenerator.Hinting.Slight;
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "×°•…–−";
        BitmapFont f = g.generateFont(p);
        f.setUseIntegerPositions(true);
        return f;
    }

    private static Texture tex(Pixmap p, boolean linear) {
        Texture t = new Texture(p); p.dispose();
        if (linear) t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        textures.add(t);
        return t;
    }

    /** White rounded rectangle (filled, or a ring when stroke > 0) as a stretchable patch. */
    private static NinePatch patch(float radius, float stroke) {
        int r = Math.max(2, Math.round(radius)), n = r * 2 + 4;
        Pixmap p = new Pixmap(n, n, Pixmap.Format.RGBA8888); p.setBlending(Pixmap.Blending.None);
        float half = n / 2f;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            float qx = Math.abs(x + 0.5f - half), qy = Math.abs(y + 0.5f - half);
            float ox = Math.max(qx - (half - r), 0), oy = Math.max(qy - (half - r), 0), sdo = (float) Math.hypot(ox, oy) - r;
            float a = clamp01(0.5f - sdo);
            if (stroke > 0) {
                float ri = Math.max(0.5f, r - stroke), hi = half - stroke;
                float ix = Math.max(qx - (hi - ri), 0), iy = Math.max(qy - (hi - ri), 0), sdi = (float) Math.hypot(ix, iy) - ri;
                if (qx > hi || qy > hi) sdi = Math.max(sdi, Math.max(qx - hi, qy - hi));
                a *= clamp01(0.5f + sdi);
            }
            p.drawPixel(x, y, 0xffffff00 | (int) (255 * a));
        }
        return new NinePatch(new TextureRegion(tex(p, true)), r + 1, r + 1, r + 1, r + 1);
    }

    public static void dispose() {
        for (Texture t : textures) t.dispose();
        textures.clear(); icons.clear(); patches.clear();
        for (BitmapFont f : new BitmapFont[]{font, small, bold, title}) if (f != null) f.dispose();
        font = small = bold = title = null;
    }

    // ------------------------------------------------------------------ drawables
    private static final com.badlogic.gdx.utils.IntMap<NinePatch> patches = new com.badlogic.gdx.utils.IntMap<>();
    /** Rounded rectangle with a radius in dp (a stadium when the height is 2 x radius). */
    public static Drawable rr(float radiusDp, Color c) {
        int key = Math.round(radiusDp * 10);
        NinePatch p = patches.get(key);
        if (p == null) { p = patch(dp(radiusDp), 0); patches.put(key, p); }
        NinePatchDrawable d = new NinePatchDrawable(new NinePatch(p, c));
        d.setMinWidth(0); d.setMinHeight(0);
        return d;
    }
    public static Drawable round(Color c) { return new NinePatchDrawable(new NinePatch(round, c)); }
    public static Drawable roundSmall(Color c) { return new NinePatchDrawable(new NinePatch(roundSmall, c)); }
    public static Drawable pill(Color c) { return new NinePatchDrawable(new NinePatch(pill, c)); }
    public static Drawable outline(Color c) { return new NinePatchDrawable(new NinePatch(outline, c)); }
    public static Drawable flat(Color c) { return new TextureRegionDrawable(white).tint(c); }
    public static Drawable iconDrawable(String name, Color c) { TextureRegion r = icons.get(name); return r == null ? flat(c) : new TextureRegionDrawable(r).tint(c); }
    public static Drawable pad(Drawable d, float h, float v) { d.setLeftWidth(dp(h)); d.setRightWidth(dp(h)); d.setTopHeight(dp(v)); d.setBottomHeight(dp(v)); return d; }

    // ------------------------------------------------------------------ direction helpers (ready for right-to-left)
    public static int start() { return L.rtl ? Align.right : Align.left; }
    public static int end() { return L.rtl ? Align.left : Align.right; }

    /** Adds actors to a row in reading order (reversed for right-to-left). */
    public static void row(Table t, Actor... actors) {
        if (L.rtl) for (int i = actors.length - 1; i >= 0; i--) t.add(actors[i]); else for (Actor a : actors) t.add(a);
    }

    // ------------------------------------------------------------------ widgets
    public static Label label(String text) { return new Label(text, new Label.LabelStyle(font, TEXT)); }
    public static Label dim(String text) { return new Label(text, new Label.LabelStyle(small, DIM)); }
    public static Label bold(String text) { return new Label(text, new Label.LabelStyle(bold, TEXT)); }
    public static Label title(String text) { return new Label(text, new Label.LabelStyle(title, TEXT)); }

    public static Image icon(String name, float size, Color c) {
        Image im = new Image(iconDrawable(name, c), Scaling.fit);
        im.setSize(dp(size), dp(size));
        im.setTouchable(Touchable.disabled);
        return im;
    }

    private static Button.ButtonStyle style(Drawable up, Drawable down, Drawable checked) {
        Button.ButtonStyle s = new Button.ButtonStyle(); s.up = up; s.down = down; s.over = down; s.checked = checked; return s;
    }

    public static void click(Actor a, Runnable r) {
        a.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { if (a instanceof Button && ((Button) a).isDisabled()) return; r.run(); } });
    }

    /** Square icon button. */
    public static Button iconButton(String icon, Runnable r) { return iconButton(icon, 38, 21, false, r); }

    public static Button iconButton(String icon, float size, float iconSize, boolean card, Runnable r) {
        Button b = new Button(style(card ? round(CARD) : null, round(CARD_HI), null));      // set style.checked for a toggle
        b.add(icon(icon, iconSize, TEXT)).size(dp(iconSize));
        b.setSize(dp(size), dp(size));
        if (r != null) click(b, r);
        return b;
    }

    public static Button button(String text, Runnable r) { return button(text, null, CARD, r); }
    public static Button primary(String text, Runnable r) { return button(text, null, ACCENT_DIM, r); }

    public static Button button(String text, String icon, Color bg, Runnable r) {
        Button b = new Button(style(round(bg), round(bg.cpy().lerp(Color.WHITE, 0.14f)), null));
        b.pad(dp(7), dp(12), dp(7), dp(12));
        if (icon != null) b.add(icon(icon, 18, TEXT)).size(dp(18)).padRight(text.isEmpty() ? 0 : dp(6));
        if (!text.isEmpty()) { Label l = bold(text); l.setEllipsis(true); l.setAlignment(Align.center); b.add(l).minWidth(0); }
        if (r != null) click(b, r);
        return b;
    }

    /** Small rounded chip that can be checked. */
    public static Button chip(String text, String icon) {
        Button b = new Button(style(pill(CARD), pill(CARD_HI), pill(ACCENT)));
        b.pad(dp(5), dp(11), dp(5), dp(11));
        if (icon != null) b.add(icon(icon, 16, TEXT)).size(dp(16)).padRight(text.isEmpty() ? 0 : dp(5));
        if (!text.isEmpty()) b.add(label(text));
        return b;
    }

    /** One line: label at the start, a switch at the end. */
    public static Table toggle(String text, boolean value, java.util.function.Consumer<Boolean> change) {
        Table t = new Table();
        Label l = label(text); l.setEllipsis(true);
        Button sw = new Button(style(rr(11, LINE), rr(11, LINE), rr(11, ACCENT)));
        Image knob = new Image(new TextureRegionDrawable(circle));
        sw.setChecked(value);
        sw.pad(dp(3));
        sw.add(knob).size(dp(16)).expandX().align(value ? Align.right : Align.left);
        sw.addListener(new ChangeListener() { @Override public void changed(ChangeEvent e, Actor a) { sw.getCell(knob).align(sw.isChecked() ? Align.right : Align.left); sw.invalidate(); change.accept(sw.isChecked()); } });
        if (L.rtl) { t.add(sw).size(dp(40), dp(22)); t.add(l).expandX().fillX().minWidth(0).padLeft(dp(8)); l.setAlignment(Align.right); }
        else { t.add(l).expandX().fillX().minWidth(0).padRight(dp(8)); t.add(sw).size(dp(40), dp(22)); }
        t.setTouchable(Touchable.enabled);
        return t;
    }

    public static Slider slider(float min, float max, float step, float value) {
        Slider.SliderStyle s = new Slider.SliderStyle();
        Drawable bg = rr(2.5f, LINE); bg.setMinHeight(dp(5)); bg.setMinWidth(0);
        Drawable before = rr(2.5f, ACCENT); before.setMinHeight(dp(5)); before.setMinWidth(0);
        TextureRegionDrawable knob = new TextureRegionDrawable(circle); knob.setMinWidth(dp(19)); knob.setMinHeight(dp(19));
        s.background = bg; s.knobBefore = before; s.knob = knob;
        Slider sl = new Slider(min, max, step, false, s);
        sl.setValue(value);
        // sliders live in scrolling panels: keep the panel from taking the drag
        sl.addListener(new InputListener() { @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) { e.stop(); return false; } });
        return sl;
    }

    public interface FloatChange { void set(float v); }

    /** Label, value text and a slider under them. done runs when the finger leaves the slider. */
    public static Table sliderRow(String text, float min, float max, float step, float value, String unit, FloatChange change, Runnable begin, Runnable done) {
        Table t = new Table();
        Label l = label(text), v = dim(fmt(value, step) + unit);
        Slider s = slider(min, max, step, value);
        boolean[] dragging = {false};
        s.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                boolean instant = !s.isDragging() && !dragging[0];
                if (!dragging[0] && s.isDragging()) { dragging[0] = true; if (begin != null) begin.run(); }
                if (instant && begin != null) begin.run();
                v.setText(fmt(s.getValue(), step) + unit); change.set(s.getValue());
                if (instant && done != null) done.run();
            }
        });
        s.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) { return true; }
            @Override public void touchUp(InputEvent e, float x, float y, int p, int b) { if (dragging[0]) { dragging[0] = false; if (done != null) done.run(); } }
        });
        Table head = new Table();
        if (L.rtl) { head.add(v); head.add(l).expandX().right(); } else { head.add(l).expandX().left(); head.add(v); }
        t.add(head).growX().row();
        t.add(s).growX().height(dp(26));
        t.setUserObject(s);
        return t;
    }

    public static String fmt(float v, float step) {
        if (step >= 1) return String.valueOf(Math.round(v));
        if (step >= 0.1f) return String.valueOf(Math.round(v * 10) / 10f);
        return String.valueOf(Math.round(v * 100) / 100f);
    }

    public static Label section(String text) {
        Label l = new Label(text.toUpperCase(), new Label.LabelStyle(small, DIM));
        l.setAlignment(start());
        return l;
    }

    public static ScrollPane scroll(Actor content, boolean horizontal) {
        ScrollPane.ScrollPaneStyle st = new ScrollPane.ScrollPaneStyle();
        Drawable knob = rr(1.5f, new Color(1, 1, 1, 0.2f)); knob.setMinWidth(dp(3)); knob.setMinHeight(dp(3));
        if (horizontal) st.hScrollKnob = knob; else st.vScrollKnob = knob;
        ScrollPane sp = new ScrollPane(content, st);
        sp.setScrollingDisabled(!horizontal, horizontal);
        sp.setFadeScrollBars(true); sp.setOverscroll(false, false); sp.setSmoothScrolling(true);
        sp.setScrollbarsOnTop(true);
        return sp;
    }

    public static TextField field(String text) {
        TextField.TextFieldStyle s = new TextField.TextFieldStyle();
        s.font = font; s.fontColor = TEXT; s.background = pad(round(FIELD), 10, 8); s.cursor = flat(ACCENT); s.cursor.setMinWidth(dp(1.5f)); s.selection = flat(ACCENT_DIM);
        s.messageFont = font; s.messageFontColor = DIM;
        return new TextField(text, s);
    }

    /** Color swatch (rounded, with a thin border). */
    public static Button swatch(Color c, float size, Runnable r) {
        Button b = new Button(style(null, null, null));
        Image fill = new Image(round(Color.WHITE)); fill.setColor(c);
        Image ring = new Image(outline(new Color(1, 1, 1, 0.35f)));
        com.badlogic.gdx.scenes.scene2d.ui.Stack st = new com.badlogic.gdx.scenes.scene2d.ui.Stack(fill, ring);
        b.add(st).size(dp(size));
        b.setUserObject(fill);
        if (r != null) click(b, r);
        return b;
    }
}
