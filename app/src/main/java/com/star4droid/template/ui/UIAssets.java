package com.star4droid.template.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.star4droid.template.Utils.NinePatchLoader;

import java.nio.ByteBuffer;
import java.util.HashMap;

/**
 * Everything a {@link UIView} needs from the disk or from the built-in look: pictures, 9-patches, fonts, sounds and the generated
 * widget art (see {@link UIArt}). One shared cache for all views, keyed by file / art key; it is dropped without disposing when the
 * GL context object changes (new game), and {@link #clear()} disposes it for good. GL thread only.
 *
 * "Project paths": {@code projectPath} is the project folder (contains images/, sounds/, fonts...). Image values are relative to
 * its images/ folder ("/hero.png" or "hero.png"), sound values to sounds/, font values to the project folder.
 */
public final class UIAssets {
    private UIAssets() {}

    private static Object owner;
    private static final HashMap<String, ArtEntry> ART = new HashMap<>();
    private static final HashMap<String, Texture> IMAGES = new HashMap<>();
    private static final HashMap<String, BitmapFont> FONTS = new HashMap<>();
    private static final HashMap<String, Sound> SOUNDS = new HashMap<>();
    private static BitmapFont defaultFont;

    private static final class ArtEntry {
        Texture texture;
        Pixmap pixmap;
        NinePatch patch;
        TextureRegion region;
    }

    private static void check() {
        Object g = Gdx.graphics;
        if (owner != g) {
            ART.clear();
            IMAGES.clear();
            FONTS.clear();
            SOUNDS.clear();
            SOLID.clear();
            defaultFont = null;
            owner = g;
        }
    }

    // ------------------------------------------------------------------ built-in art

    /** Drawable of a generated picture. 9-patch art stretches; other art is drawn 1:1. Each call returns a NEW drawable object sharing the texture. */
    public static synchronized Drawable art(UIArt.Art a) {
        check();
        ArtEntry e = ART.get(a.key);
        if (e == null) {
            int[] px = a.render();
            Pixmap pm = new Pixmap(a.w, a.h, Pixmap.Format.RGBA8888);
            pm.setBlending(Pixmap.Blending.None);
            ByteBuffer bb = pm.getPixels();
            bb.position(0);
            for (int c : px) {
                bb.put((byte) (c >>> 24));
                bb.put((byte) (c >>> 16));
                bb.put((byte) (c >>> 8));
                bb.put((byte) c);
            }
            bb.position(0);
            e = new ArtEntry();
            e.pixmap = pm;
            // managed = true: rebuilt from the kept pixmap after a GL context loss
            e.texture = new Texture(new PixmapTextureData(pm, null, false, false, true));
            e.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            e.region = new TextureRegion(e.texture);
            if (a.nine) e.patch = new NinePatch(e.region, a.l, a.r, a.t, a.b);
            ART.put(a.key, e);
        }
        if (a.nine) return new NinePatchDrawable(new NinePatch(e.patch));
        return new TextureRegionDrawable(e.region);
    }

    private static final HashMap<Integer, Drawable> SOLID = new HashMap<>();

    /** Flat colored drawable (cursor, selection...). RGBA8888. */
    public static synchronized Drawable solid(int rgba) {
        check();
        Drawable d = SOLID.get(rgba);
        if (d == null) {
            Pixmap pm = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
            pm.setColor(rgba);
            pm.fill();
            Texture t = new Texture(new PixmapTextureData(pm, null, false, false, true));
            d = new TextureRegionDrawable(new TextureRegion(t));
            SOLID.put(rgba, d);
        }
        return d;
    }

    // ------------------------------------------------------------------ project pictures

    private static String clean(String p) {
        if (p == null) return "";
        p = p.replace('\\', '/');
        while (p.contains("//")) p = p.replace("//", "/");
        return p;
    }

    /** File of a project image, or null when it does not exist. */
    public static FileHandle imageFile(String projectPath, String image) {
        if (image == null || image.trim().isEmpty() || projectPath == null) return null;
        String rel = clean(image.trim());
        if (rel.startsWith("/")) rel = rel.substring(1);
        FileHandle f = Gdx.files.absolute(clean(projectPath + "/images/" + rel));
        return f.exists() && !f.isDirectory() ? f : null;
    }

    /** Drawable for a project image (.9.png becomes a stretching 9-patch). Null when missing. */
    public static synchronized Drawable image(String projectPath, String image) {
        check();
        FileHandle f = imageFile(projectPath, image);
        if (f == null) {
            // merged into a tileset page at export
            if (com.star4droid.template.Utils.AtlasIndex.isEmpty() || image == null) return null;
            String rel = clean(image.trim());
            if (rel.startsWith("/")) rel = rel.substring(1);
            return com.star4droid.template.Utils.AtlasIndex.drawable(clean(projectPath + "/images/" + rel));
        }
        try {
            if (NinePatchLoader.is9(f)) return NinePatchLoader.drawable(f);
            String key = f.path() + ":" + f.lastModified() + ":" + f.length();
            Texture t = IMAGES.get(key);
            if (t == null) {
                t = new Texture(f);
                t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                IMAGES.put(key, t);
            }
            return new TextureRegionDrawable(new TextureRegion(t));
        } catch (Throwable ex) {
            return null;
        }
    }

    /** {width, height} of a project image (9-patch: its content size) or null. */
    public static float[] imageSize(String projectPath, String image) {
        Drawable d = image(projectPath, image);
        if (d == null) return null;
        return new float[]{d.getMinWidth(), d.getMinHeight()};
    }

    /** "name.png" -> "name_pressed.png" style twin check: returns the pressed twin of an image or null. */
    public static String pressedTwin(String projectPath, String image) {
        if (image == null || image.isEmpty()) return null;
        String low = image.toLowerCase();
        String twin;
        if (low.endsWith(".9.png")) twin = image.substring(0, image.length() - 6) + "_pressed.9.png";
        else {
            int dot = image.lastIndexOf('.');
            if (dot < 0) return null;
            twin = image.substring(0, dot) + "_pressed" + image.substring(dot);
        }
        return imageFile(projectPath, twin) != null ? twin : null;
    }

    // ------------------------------------------------------------------ fonts

    /** The engine default font (shared: never dispose, never change its scale). */
    public static synchronized BitmapFont defaultFont() {
        check();
        if (defaultFont == null) defaultFont = new BitmapFont(Gdx.files.internal("files/default.fnt"));
        return defaultFont;
    }

    /** A project font (json made by the font creator, path relative to the project folder) or the default font. */
    public static synchronized BitmapFont font(String projectPath, String fontPath) {
        check();
        if (fontPath == null || fontPath.trim().isEmpty() || projectPath == null) return defaultFont();
        String path = clean(projectPath + "/" + fontPath.trim());
        BitmapFont f = FONTS.get(path);
        if (f == null) {
            try {
                FileHandle fh = Gdx.files.absolute(path);
                f = fh.exists() && !fh.isDirectory() ? com.star4droid.template.Utils.Utils.generateFontFrom(fh) : defaultFont();
            } catch (Throwable t) {
                f = defaultFont();
            }
            FONTS.put(path, f);
        }
        return f;
    }

    private static final GlyphLayout LAYOUT = new GlyphLayout();

    /** Scale that makes {@code font} draw text of {@code size} px (line height = 1.2 x size). */
    public static float fontScale(BitmapFont font, float size) {
        float lh = font.getData().lineHeight / Math.max(0.0001f, font.getScaleY());
        return Math.max(0.01f, size * 1.2f / Math.max(1f, lh));
    }

    /** Size of text at {@code size} px, wrapping at wrapWidth (&lt;= 0: no wrap). */
    public static synchronized float[] measure(BitmapFont font, String text, float size, float wrapWidth) {
        float ox = font.getScaleX(), oy = font.getScaleY();
        float sc = fontScale(font, size);
        font.getData().setScale(sc);
        try {
            if (wrapWidth > 0) LAYOUT.setText(font, text == null ? "" : text, com.badlogic.gdx.graphics.Color.WHITE, wrapWidth, com.badlogic.gdx.utils.Align.left, true);
            else LAYOUT.setText(font, text == null ? "" : text);
            return new float[]{LAYOUT.width, Math.max(LAYOUT.height, font.getData().lineHeight)};
        } finally {
            font.getData().setScale(ox, oy);
        }
    }

    // ------------------------------------------------------------------ sounds

    /** Sound file of the project's sounds/ folder, null when missing. Cached. */
    public static synchronized Sound sound(String projectPath, String name) {
        check();
        if (name == null || name.trim().isEmpty() || projectPath == null) return null;
        String rel = clean(name.trim());
        if (rel.startsWith("/")) rel = rel.substring(1);
        String path = clean(projectPath + "/sounds/" + rel);
        Sound s = SOUNDS.get(path);
        if (s == null && !SOUNDS.containsKey(path)) {
            try {
                FileHandle f = Gdx.files.absolute(path);
                s = f.exists() ? Gdx.audio.newSound(f) : null;
            } catch (Throwable t) {
                s = null;
            }
            SOUNDS.put(path, s);
        }
        return s;
    }

    /** Disposes everything (the game / project is closed for good). */
    public static synchronized void clear() {
        for (ArtEntry e : ART.values()) {
            try { e.texture.dispose(); } catch (Throwable ignored) {}
            try { e.pixmap.dispose(); } catch (Throwable ignored) {}
        }
        for (Texture t : IMAGES.values()) try { t.dispose(); } catch (Throwable ignored) {}
        for (BitmapFont f : FONTS.values()) if (f != defaultFont) try { f.dispose(); } catch (Throwable ignored) {}
        for (Sound s : SOUNDS.values()) if (s != null) try { s.dispose(); } catch (Throwable ignored) {}
        if (defaultFont != null) try { defaultFont.dispose(); } catch (Throwable ignored) {}
        ART.clear();
        IMAGES.clear();
        FONTS.clear();
        SOUNDS.clear();
        defaultFont = null;
    }

    /** UILayout measurer backed by real fonts and images. */
    public static UILayout.Measurer measurer(final String projectPath, final UITheme theme) {
        return new UILayout.Measurer() {
            @Override
            public float[] text(String text, float fs, float wrap, UINode node) {
                String fp = node != null && node.has("font") ? node.str("font", "") : theme.font;
                return measure(font(projectPath, fp), text, fs, wrap);
            }

            @Override
            public float[] image(String path) {
                return imageSize(projectPath, path);
            }
        };
    }
}
