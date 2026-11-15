package com.star4droid.star2d.ui.uimaker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.template.ui.UIDocument;
import com.star4droid.template.ui.UINode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * "UI skills": complete, polished game screens from a tiny description, so any model (or a person) gets a professional UI in one
 * call. Every screen uses the same design tokens (spacing, type scale, button heights, card style, icon size) derived from the
 * design size, only anchors / Linear / Grid / ScrollView (works on every screen size), theme colour ROLES (one theme change recolours
 * everything) and built-in click actions (tabs, dialogs, pause, navigation work without code).
 *
 * <pre>
 * UIDocument d = UiKit.build("shop", "shop", spec, 720, 1280, icons, notes);
 * spec = {title:'Shop', currency:'1250', tabs:['Skins','Boosts'], items:[{name:'Red car', price:'300', icon:'car'}]}
 * </pre>
 * Pure Java: icons come from an {@link Icons} source (the app imports library icons into images/ui/).
 */
public final class UiKit {
    private UiKit() {}

    /** Turns a library icon id ("cog", "two-coins"...) into a project image path ("/ui/cog.png"), or "" when not available. */
    public interface Icons {
        String path(String iconId);
    }

    /** Screens: id -> {title, what it contains, spec keys}. */
    public static final LinkedHashMap<String, String[]> SCREENS = new LinkedHashMap<>();

    static {
        s("main_menu", "Main menu", "Game title, big Play button, more buttons, sound / info icons, optional currency and version.",
                "title, subtitle, buttons:[text|{text,id,icon,style,open,action}], currency, version");
        s("hud", "Game HUD", "Health bar, score, coins, pause button (opens a built-in pause card), optional touch controls.",
                "score, coins, health(0..100), lives, timer, level, pause(true), controls: none|platformer|shooter|dpad|racing");
        s("pause", "Pause menu", "Dimmed overlay with a card: Resume (closes it), Restart, Settings, Home.", "title, buttons");
        s("settings", "Settings", "Full screen: header with back, grouped rows (sliders, switches, choices) in a scroll view.",
                "title, groups:[{title, rows:[{type:slider|switch|choice|button, label, id, icon, value, options}]}]");
        s("dialog", "Dialog", "Centered card with an icon, title, message and Cancel / OK.", "title, message, icon, ok, cancel");
        s("game_over", "Game over / result", "Result card: title, stars, score, best, reward and Retry / Home / Next.",
                "title, win(bool), stars(0..3), score, best, reward, buttons");
        s("level_select", "Level select", "Header with back and star total, scrolling grid of level tiles with stars and locks.",
                "title, levels(20), unlocked(3), stars:[3,2,...], columns");
        s("shop", "Shop", "Header with back + currency, tabs, scrolling grid of item cards with price buttons.",
                "title, currency, tabs:[..], items:[{name, price, icon|image, owned, tab}]");
        s("inventory", "Inventory", "Header, scrolling grid of item slots with counts, Use button.", "title, items:[{name, icon|image, count}], columns");
        s("leaderboard", "Leaderboard", "Header, tabs (Today / Week / All), ranked rows with medals, your row highlighted.",
                "title, tabs, rows:[{name, score, me}]");
        s("missions", "Missions / quests", "Rows with icon, title, description, progress bar and Claim button.",
                "title, rows:[{title, desc, icon, progress, goal, reward}]");
        s("daily_reward", "Daily reward", "Card with 7 day tiles (today highlighted) and a Claim button.", "title, day(1..7), rewards:[..]");
        s("character_select", "Character select", "Horizontal scrolling cards with picture, name and stat bars, Select button.",
                "title, characters:[{name, icon|image, stats:{Speed:70,...}}]");
        s("loading", "Loading screen", "Title, animated progress bar (id progress) and a tip.", "title, tip");
        s("login", "Login / form", "Card with text fields and a primary button.", "title, fields:[{label, id, hint, password, numeric}], button, link");
        s("tabs", "Tabbed screen", "Header and tabs, each tab shows its own page (no code needed).", "title, tabs:[{title, text}]");
        s("credits", "Credits / about", "Header with back and scrolling text.", "title, text");
        s("custom", "Custom screen", "Header + your sections stacked (any part below); the scrolling part fills the rest.",
                "title, back, sections:[{part:..., ...part spec}]");
    }

    private static void s(String id, String title, String desc, String keys) {
        SCREENS.put(id, new String[]{title, desc, keys});
    }

    /** Parts usable in screen 'custom' sections and in `ui set` op 'add_part'. */
    public static final String[] PARTS = {"header", "title", "text", "buttons", "icon_buttons", "chips", "tabs", "grid", "list", "rows", "stars",
            "stat_bars", "progress", "fields", "card", "spacer", "controls"};

    public static boolean isScreen(String s) {
        return s != null && SCREENS.containsKey(norm(s));
    }

    static String norm(String s) {
        String n = s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        switch (n) {
            case "menu": case "mainmenu": case "home": case "title": return "main_menu";
            case "pause_menu": return "pause";
            case "settings_panel": case "options": return "settings";
            case "dialog_box": case "confirm": case "popup": case "alert": return "dialog";
            case "gameover": case "result": case "results": case "win": case "lose": case "level_complete": return "game_over";
            case "levels": case "level_map": return "level_select";
            case "store": return "shop";
            case "bag": return "inventory";
            case "ranking": case "scores": case "highscores": return "leaderboard";
            case "quests": case "achievements": case "tasks": return "missions";
            case "daily": case "rewards": return "daily_reward";
            case "characters": case "skins": case "hero_select": return "character_select";
            case "splash": return "loading";
            case "form": case "register": case "signup": return "login";
            case "about": return "credits";
            default: return n;
        }
    }

    // ================================================================== tokens

    /** Design tokens of one document: everything is derived from the short side, so portrait and landscape match. */
    static final class K {
        final float W, H, F;
        final boolean land;
        final Icons icons;
        final List<String> notes;
        final ArrayList<String> interactive = new ArrayList<>();
        int seq;

        K(float w, float h, Icons icons, List<String> notes) {
            W = w;
            H = h;
            F = Math.min(w, h) / 720f;
            land = w > h;
            this.icons = icons;
            this.notes = notes;
        }

        int px(float v) { return Math.round(v * F); }
        // spacing scale
        int xs() { return px(6); }
        int sm() { return px(10); }
        int md() { return px(16); }
        int lg() { return px(24); }
        int xl() { return px(36); }
        // type scale
        int display() { return px(78); }
        int h1() { return px(50); }
        int h2() { return px(36); }
        int body() { return px(28); }
        int small() { return px(22); }
        int tiny() { return px(18); }
        // controls
        int btnBig() { return px(96); }
        int btn() { return px(78); }
        int btnSm() { return px(58); }
        int iconBtn() { return px(76); }
        int icon() { return px(34); }
        int headerH() { return px(110); }
        int rowH() { return px(84); }
        /** Width of a centered card / menu column. */
        int cardW() { return Math.round(Math.min(px(600), W * 0.9f)); }

        /** ASCII id (generated Java field names and event folders): other letters (Arabic text...) become a numbered "item". */
        String id(String base) {
            String src = base == null ? "" : base.toLowerCase(Locale.ROOT);
            StringBuilder b = new StringBuilder();
            for (char c : src.toCharArray()) b.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' ? c : '_');
            String r = b.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
            if (r.isEmpty() || r.replace("_", "").replaceAll("[0-9]", "").isEmpty() && !r.matches(".*[a-z].*")) r = "item" + (++seq) + (r.isEmpty() ? "" : "_" + r);
            if (Character.isDigit(r.charAt(0))) r = "n" + r;
            return r;
        }

        String icon(String id) {
            if (id == null || id.isEmpty() || icons == null) return "";
            try {
                String p = icons.path(id);
                return p == null ? "" : p;
            } catch (Throwable t) {
                return "";
            }
        }
    }

    // ================================================================== entry

    /**
     * Builds a whole document. {@code spec} may be null or empty: every screen has good defaults.
     * Unknown screen -> IllegalArgumentException with the list.
     */
    public static UIDocument build(String uiName, String screen, JsonObject spec, float w, float h, Icons icons, List<String> notes) {
        String sc = norm(screen);
        if (!SCREENS.containsKey(sc)) throw new IllegalArgumentException("Unknown screen '" + screen + "'. Screens: " + String.join(", ", SCREENS.keySet()));
        if (spec == null) spec = new JsonObject();
        UIDocument d = UiOps.newDocument(uiName, w, h);
        K k = new K(w, h, icons, notes == null ? new ArrayList<>() : notes);
        UINode top = screen(k, sc, spec);
        d.root.addChild(top);
        d.makeIdsUnique(top);
        k.notes.addAll(UiOps.sanitize(d.root));
        return d;
    }

    /** The screen container (insert it into an existing document with {@link #insert}). */
    static UINode screen(K k, String sc, JsonObject spec) {
        switch (sc) {
            case "main_menu": return mainMenu(k, spec);
            case "hud": return hud(k, spec);
            case "pause": return pause(k, spec, false);
            case "settings": return settings(k, spec);
            case "dialog": return dialog(k, spec);
            case "game_over": return result(k, spec);
            case "level_select": return levels(k, spec);
            case "shop": return shop(k, spec);
            case "inventory": return inventory(k, spec);
            case "leaderboard": return leaderboard(k, spec);
            case "missions": return missions(k, spec);
            case "daily_reward": return daily(k, spec);
            case "character_select": return characters(k, spec);
            case "loading": return loading(k, spec);
            case "login": return login(k, spec);
            case "tabs": return tabsScreen(k, spec);
            case "credits": return credits(k, spec);
            case "custom": return custom(k, spec);
            default: throw new IllegalArgumentException("Unknown screen " + sc);
        }
    }

    /** Adds a screen into an existing document (UI Maker "insert", ui set op 'add_screen'). */
    public static UINode insert(UIDocument doc, UINode parent, String screen, JsonObject spec, Icons icons, List<String> notes) {
        String sc = norm(screen);
        if (!SCREENS.containsKey(sc)) return null;
        K k = new K(doc.designWidth, doc.designHeight, icons, notes == null ? new ArrayList<>() : notes);
        UINode n = screen(k, sc, spec == null ? new JsonObject() : spec);
        if (parent == null || !parent.isContainer()) parent = doc.root;
        doc.makeIdsUnique(n);
        parent.addChild(n);
        return n;
    }

    /** One part (header, grid, list...) added to a container of an existing document. */
    public static UINode addPart(UIDocument doc, UINode parent, String part, JsonObject spec, Icons icons, List<String> notes) {
        K k = new K(doc.designWidth, doc.designHeight, icons, notes == null ? new ArrayList<>() : notes);
        UINode n = part(k, part, spec == null ? new JsonObject() : spec);
        if (n == null) return null;
        if (parent == null || !parent.isContainer()) parent = doc.root;
        doc.makeIdsUnique(n);
        parent.addChild(n);
        k.notes.addAll(UiOps.sanitize(n));
        return n;
    }

    // ================================================================== spec helpers

    static String str(JsonObject o, String key, String def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive()) return e.getAsString();
        return def;
    }

    static int num(JsonObject o, String key, int def) {
        try {
            if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) return Math.round(o.get(key).getAsFloat());
        } catch (Exception ignored) {
        }
        return def;
    }

    static boolean bool(JsonObject o, String key, boolean def) {
        try {
            if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) {
                JsonElement e = o.get(key);
                if (e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
                String s = e.getAsString().trim().toLowerCase(Locale.ROOT);
                return s.equals("true") || s.equals("yes") || s.equals("1") || s.equals("on");
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    static boolean has(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull();
    }

    /** Items of a list key: objects stay, plain strings become {text: s, name: s, title: s}. */
    static ArrayList<JsonObject> list(JsonObject o, String key) {
        ArrayList<JsonObject> out = new ArrayList<>();
        if (o == null || !o.has(key)) return out;
        JsonElement e = o.get(key);
        if (!e.isJsonArray()) return out;
        for (JsonElement x : e.getAsJsonArray()) {
            if (x.isJsonObject()) out.add(x.getAsJsonObject());
            else if (x.isJsonPrimitive()) {
                JsonObject j = new JsonObject();
                j.addProperty("text", x.getAsString());
                j.addProperty("name", x.getAsString());
                j.addProperty("title", x.getAsString());
                out.add(j);
            }
        }
        return out;
    }

    static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof Number) o.addProperty((String) kv[i], (Number) v);
            else if (v instanceof Boolean) o.addProperty((String) kv[i], (Boolean) v);
            else if (v instanceof JsonElement) o.add((String) kv[i], (JsonElement) v);
            else if (v != null) o.addProperty((String) kv[i], String.valueOf(v));
        }
        return o;
    }

    static JsonArray arr(Object... items) {
        JsonArray a = new JsonArray();
        for (Object v : items) {
            if (v instanceof JsonElement) a.add((JsonElement) v);
            else if (v instanceof Number) a.add((Number) v);
            else a.add(String.valueOf(v));
        }
        return a;
    }

    // ================================================================== atoms

    static UINode stack(K k, String id) {
        UINode n = UINode.create(UINode.STACK, k.id(id));
        n.anchor = "stretch";
        return n;
    }

    /** Full screen background (theme background colour). */
    static UINode screenRoot(K k, String id) {
        UINode n = stack(k, id);
        n.set("bgColor", "background");
        n.touchable = "enabled";
        return n;
    }

    static UINode col(K k, String id, int spacing, int pad) {
        UINode n = UINode.create(UINode.LINEAR, k.id(id));
        n.set("orientation", "vertical");
        n.set("spacing", spacing);
        n.set("padding", pad);
        n.set("align", "stretch");
        n.w = "fill";
        n.h = "wrap";
        return n;
    }

    static UINode row(K k, String id, int spacing, int pad) {
        UINode n = UINode.create(UINode.LINEAR, k.id(id));
        n.set("orientation", "horizontal");
        n.set("spacing", spacing);
        n.set("padding", pad);
        n.set("align", "center");
        n.w = "fill";
        n.h = "wrap";
        return n;
    }

    static UINode label(K k, String id, String text, int size, String color) {
        UINode n = UINode.create(UINode.LABEL, k.id(id));
        n.set("text", text);
        n.set("fontSize", size);
        if (color != null) n.set("color", color);
        return n;
    }

    static UINode title(K k, String id, String text, int size) {
        UINode n = label(k, id, text, size, "text");
        n.set("shadow", true);
        n.set("textAlign", "center");
        return n;
    }

    static UINode wrapText(K k, String id, String text, int size, String color, String align) {
        UINode n = label(k, id, text, size, color);
        n.set("wrap", true);
        n.set("textAlign", align);
        n.w = "fill";
        return n;
    }

    static UINode button(K k, String id, String text, String style, String icon, int h) {
        UINode n = UINode.create(UINode.BUTTON, k.id(id));
        n.set("text", text == null ? "" : text);
        n.set("style", style == null ? "primary" : style);
        n.set("fontSize", h >= k.btnBig() ? k.h2() : h >= k.btn() ? k.body() + k.px(2) : k.small() + k.px(2));
        String ip = k.icon(icon);
        if (!ip.isEmpty()) n.set("icon", ip);
        n.w = "fill";
        n.h = String.valueOf(h);
        k.interactive.add(n.id);
        return n;
    }

    /** Square round button with only an icon (falls back to a short text when the icon is missing). */
    static UINode iconButton(K k, String id, String icon, String fallback, String style, int size) {
        UINode n = UINode.create(UINode.BUTTON, k.id(id));
        String ip = k.icon(icon);
        n.set("style", style == null ? "secondary" : style);
        if (!ip.isEmpty()) {
            n.set("icon", ip);
            n.set("text", "");
            n.set("fontSize", Math.round(size * 0.46f));
        } else {
            n.set("text", fallback);
            n.set("fontSize", Math.round(size * 0.42f));
        }
        n.set("shape", "round");
        n.set("radius", size / 2f);
        n.w = String.valueOf(size);
        n.h = String.valueOf(size);
        k.interactive.add(n.id);
        return n;
    }

    static UINode icon(K k, String id, String icon, String color, int size) {
        String ip = k.icon(icon);
        if (ip.isEmpty()) return null;
        UINode n = UINode.create(UINode.ICON, k.id(id));
        n.set("image", ip);
        n.set("color", color == null ? "text" : color);
        n.w = String.valueOf(size);
        n.h = String.valueOf(size);
        return n;
    }

    static UINode image(K k, String id, String path, int size) {
        UINode n = UINode.create(UINode.IMAGE, k.id(id));
        n.set("image", path);
        n.set("scaling", "fit");
        n.w = String.valueOf(size);
        n.h = String.valueOf(size);
        return n;
    }

    /** A picture for an item: its image, or a library icon (accent colour), or the first letter in a circle. */
    static UINode picture(K k, String id, JsonObject item, int size, String color) {
        String img = str(item, "image", "");
        if (!img.isEmpty()) return image(k, id, img.startsWith("/") ? img : "/" + img, size);
        UINode ic = icon(k, id, str(item, "icon", ""), color, size);
        if (ic != null) return ic;
        String name = str(item, "name", str(item, "title", "?"));
        UINode p = UINode.create(UINode.PANEL, k.id(id));
        p.set("style", "solid");
        p.set("panelColor", color == null ? "accent" : color);
        p.set("radius", size / 2f);
        p.w = String.valueOf(size);
        p.h = String.valueOf(size);
        UINode l = label(k, id + "_letter", name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT), Math.round(size * 0.5f), "#ffffff");
        l.anchor = "center";
        p.addChild(l);
        return p;
    }

    /** Card panel that wraps its content height: Panel + vertical Linear (anchored top). */
    static UINode[] card(K k, String id, String w, int pad, int spacing) {
        UINode p = UINode.create(UINode.PANEL, k.id(id));
        p.set("style", "card");
        p.w = w;
        p.h = "wrap";
        UINode c = col(k, id + "_col", spacing, pad);
        c.anchor = "topleft";
        p.addChild(c);
        return new UINode[]{p, c};
    }

    /** Pill with an icon and a value (coins, gems, stars, lives). */
    static UINode chip(K k, String id, String iconId, String value, String color) {
        UINode p = UINode.create(UINode.PANEL, k.id(id));
        p.set("style", "solid");
        p.set("panelColor", "surface");
        p.set("radius", k.px(30));
        p.set("border", 2);
        p.set("borderColor", "border");
        p.w = "wrap";
        p.h = String.valueOf(k.px(60));
        UINode r = row(k, id + "_row", k.sm(), 0);
        r.set("padding", arr(k.md(), 0, k.lg(), 0));
        r.w = "wrap";
        r.anchor = "stretchy";
        r.h = "fill";
        UINode ic = icon(k, id + "_icon", iconId, color, k.px(40));
        if (ic != null) r.addChild(ic);
        UINode v = label(k, id + "_value", value, k.body(), "text");
        v.set("shadow", true);
        r.addChild(v);
        p.addChild(r);
        return p;
    }

    static UINode stars(K k, String id, int filled, int size) {
        UINode r = row(k, id, k.xs(), 0);
        r.w = "wrap";
        for (int i = 0; i < 3; i++) {
            UINode s = icon(k, id + "_" + (i + 1), i < filled ? "star" : "star-outline", i < filled ? "#ffd23f" : "textDim", size);
            if (s == null) {
                s = label(k, id + "_" + (i + 1), i < filled ? "★" : "☆", size, i < filled ? "#ffd23f" : "textDim");
            }
            r.addChild(s);
        }
        return r;
    }

    static UINode spacer(K k, String id, int h) {
        UINode n = UINode.create(UINode.STACK, k.id(id));
        n.w = "fill";
        n.h = String.valueOf(h);
        n.touchable = "disabled";
        return n;
    }

    /** Dimmed full screen overlay that swallows touches (dialogs, pause, results). */
    static UINode overlay(K k, String id) {
        UINode n = stack(k, id);
        n.set("bgColor", "#000000b0");
        n.touchable = "enabled";
        return n;
    }

    /** What a button entry of a spec does: open / show / hide / page / action keys. */
    static void wire(UINode b, JsonObject e) {
        StringBuilder a = new StringBuilder();
        if (has(e, "action")) a.append(str(e, "action", ""));
        for (String key : new String[]{"open", "show", "hide", "toggle", "page", "tab"}) {
            if (has(e, key)) {
                if (a.length() > 0) a.append("; ");
                a.append(key).append(':').append(str(e, key, ""));
            }
        }
        if (has(e, "back") && bool(e, "back", false)) {
            if (a.length() > 0) a.append("; ");
            a.append("back");
        }
        if (a.length() > 0) b.set("action", a.toString());
    }

    static UINode buttonFrom(K k, JsonObject e, String defStyle, int h, String defIcon) {
        String text = str(e, "text", str(e, "title", str(e, "name", "Button")));
        UINode b = button(k, str(e, "id", text), text, str(e, "style", defStyle), str(e, "icon", defIcon), h);
        wire(b, e);
        return b;
    }

    /** Guesses a nice icon for common button words (English). */
    static String guessIcon(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (t.contains("restart") || t.contains("retry") || t.contains("again") || t.contains("replay")) return "refresh";
        if (t.contains("play") || t.contains("start") || t.contains("resume") || t.contains("continue")) return "play";
        if (t.contains("setting") || t.contains("option")) return "cog";
        if (t.contains("shop") || t.contains("store")) return "cart";
        if (t.contains("level")) return "gamepad-variant";
        if (t.contains("restart") || t.contains("retry") || t.contains("again")) return "refresh";
        if (t.contains("home") || t.contains("menu")) return "home";
        if (t.contains("quit") || t.contains("exit")) return "logout";
        if (t.contains("next")) return "skip-next";
        if (t.contains("rank") || t.contains("leader") || t.contains("score")) return "trophy";
        if (t.contains("mission") || t.contains("quest")) return "flag";
        if (t.contains("reward") || t.contains("gift") || t.contains("daily")) return "gift";
        if (t.contains("character") || t.contains("hero") || t.contains("skin") || t.contains("profile")) return "account";
        if (t.contains("help") || t.contains("how")) return "help-circle";
        if (t.contains("share")) return "share-variant";
        if (t.contains("credit") || t.contains("about")) return "information";
        if (t.contains("back")) return "arrow-left";
        return "";
    }

    // ================================================================== page skeleton

    /**
     * Header bar: back button (action back unless spec.back is false or a custom action), centered title, right side chips.
     * Returns the bar (a Panel, full width).
     */
    static UINode header(K k, String id, String titleText, JsonObject spec, List<UINode> right) {
        UINode bar = UINode.create(UINode.PANEL, k.id(id));
        bar.set("style", "solid");
        bar.set("panelColor", "surface");
        bar.set("radius", 0);
        bar.set("shadow", k.px(4));
        bar.w = "fill";
        bar.h = String.valueOf(k.headerH());
        UINode r = row(k, id + "_row", k.md(), 0);
        r.set("padding", arr(k.lg(), 0, k.lg(), 0));
        r.anchor = "stretch";
        r.h = "fill";
        if (!has(spec, "back") || bool(spec, "back", true)) {
            UINode back = iconButton(k, "back", "arrow-left", "<", "secondary", k.px(72));
            String act = str(spec, "backAction", "");
            back.set("action", act.isEmpty() ? "back" : act);
            r.addChild(back);
        }
        UINode t = label(k, id + "_title", titleText, k.h2(), "text");
        t.set("shadow", true);
        t.w = "fill";
        t.weight = 1;
        t.set("textAlign", "center");
        r.addChild(t);
        if (right != null) for (UINode n : right) r.addChild(n);
        if (right == null || right.isEmpty()) {
            // keeps the title centered when only the back button is there
            if (!has(spec, "back") || bool(spec, "back", true)) {
                UINode pad = spacer(k, id + "_balance", 1);
                pad.w = String.valueOf(k.px(72));
                r.addChild(pad);
            }
        }
        bar.addChild(r);
        return bar;
    }

    /** Full screen page: background + column [header, (tabs), body that fills, (footer)]. Returns {root, column}. */
    static UINode[] page(K k, String id) {
        UINode root = screenRoot(k, id);
        UINode c = UINode.create(UINode.LINEAR, k.id(id + "_layout"));
        c.anchor = "stretch";
        c.set("orientation", "vertical");
        c.set("spacing", 0);
        c.set("padding", 0);
        c.set("align", "stretch");
        root.addChild(c);
        return new UINode[]{root, c};
    }

    /** A ScrollView that takes the remaining height, with a content column (or grid) inside. Returns {scroll, content}. */
    static UINode[] scrollBody(K k, String id, UINode content) {
        UINode sc = UINode.create(UINode.SCROLL, k.id(id));
        sc.set("direction", "vertical");
        sc.w = "fill";
        sc.h = "fill";
        sc.weight = 1;
        content.w = "fill";
        content.h = "wrap";
        sc.addChild(content);
        return new UINode[]{sc, content};
    }

    static int columns(K k, int portrait, int landscape) {
        return k.land ? landscape : portrait;
    }

    /** Tab buttons row; each tab shows its page (tab action). pages = the Stack whose children are the pages. */
    static UINode tabBar(K k, String id, List<String> titles, List<String> pageIds) {
        UINode bar = row(k, id, k.sm(), 0);
        bar.set("padding", arr(k.lg(), k.md(), k.lg(), k.sm()));
        for (int i = 0; i < titles.size(); i++) {
            UINode b = button(k, "tab_" + titles.get(i), titles.get(i), i == 0 ? "primary" : "ghost", "", k.btnSm());
            b.weight = 1;
            b.set("action", "tab:" + pageIds.get(i));
            b.set("shape", "pill");
            bar.addChild(b);
        }
        return bar;
    }

    // ================================================================== screens

    static UINode mainMenu(K k, JsonObject sp) {
        UINode root = screenRoot(k, "main_menu");
        // top bar: profile chip left, currency right
        if (has(sp, "currency") || has(sp, "coins")) {
            UINode cur = chip(k, "currency", str(sp, "currencyIcon", "two-coins"), str(sp, "currency", str(sp, "coins", "0")), "#ffd23f");
            cur.anchor = "topright";
            cur.margin(0, k.lg(), k.lg(), 0);
            root.addChild(cur);
        }
        UINode column = col(k, "menu", k.md(), 0);
        column.anchor = "center";
        column.w = String.valueOf(Math.round(Math.min(k.px(560), k.W * 0.86f)));
        column.set("align", "stretch");
        UINode t = title(k, "game_title", str(sp, "title", "My Game"), k.display());
        t.w = "fill";
        t.set("wrap", true);
        column.addChild(t);
        if (!str(sp, "subtitle", "").isEmpty()) {
            UINode st = wrapText(k, "subtitle", str(sp, "subtitle", ""), k.body(), "textDim", "center");
            column.addChild(st);
        }
        column.addChild(spacer(k, "menu_gap", k.lg()));
        ArrayList<JsonObject> btns = list(sp, "buttons");
        if (btns.isEmpty()) {
            btns.add(obj("text", "Play", "id", "play"));
            btns.add(obj("text", "Levels", "id", "levels"));
            btns.add(obj("text", "Shop", "id", "shop"));
            btns.add(obj("text", "Settings", "id", "settings"));
        }
        for (int i = 0; i < btns.size(); i++) {
            JsonObject e = btns.get(i);
            String text = str(e, "text", "Button");
            UINode b = buttonFrom(k, e, i == 0 ? "primary" : "secondary", i == 0 ? k.btnBig() : k.btn(), guessIcon(text));
            column.addChild(b);
        }
        root.addChild(column);
        // bottom corners: sound + info, version
        UINode corner = row(k, "corner_buttons", k.md(), 0);
        corner.anchor = "bottomleft";
        corner.w = "wrap";
        corner.margin(k.lg(), 0, 0, k.lg());
        corner.addChild(iconButton(k, "sound", "volume-high", "♪", "secondary", k.iconBtn()));
        corner.addChild(iconButton(k, "info", "information", "i", "secondary", k.iconBtn()));
        root.addChild(corner);
        String ver = str(sp, "version", "v1.0");
        if (!ver.isEmpty()) {
            UINode v = label(k, "version", ver, k.tiny(), "textDim");
            v.anchor = "bottomright";
            v.margin(0, 0, k.lg(), k.lg());
            root.addChild(v);
        }
        return root;
    }

    static UINode hud(K k, JsonObject sp) {
        UINode hud = stack(k, "hud");
        hud.set("padding", k.lg());
        // left: health / lives / level
        UINode left = col(k, "hud_left", k.sm(), 0);
        left.anchor = "topleft";
        left.w = String.valueOf(Math.round(Math.min(k.px(330), k.W * 0.42f)));
        left.set("align", "start");
        if (!has(sp, "health") || num(sp, "health", 100) >= 0) {
            UINode hr = row(k, "health_row", k.sm(), 0);
            UINode hi = icon(k, "health_icon", "heart", "danger", k.px(40));
            if (hi != null) hr.addChild(hi);
            UINode bar = UINode.create(UINode.PROGRESS, "health");
            bar.set("style", "health");
            bar.set("value", num(sp, "health", 100));
            bar.set("max", 100);
            bar.w = "fill";
            bar.weight = 1;
            bar.h = String.valueOf(k.px(30));
            hr.addChild(bar);
            left.addChild(hr);
        }
        if (has(sp, "lives")) left.addChild(chip(k, "lives", "heart", "x " + str(sp, "lives", "3"), "danger"));
        if (has(sp, "level")) left.addChild(label(k, "level", str(sp, "level", "Level 1"), k.small(), "textDim"));
        hud.addChild(left);
        // center: score (+ timer)
        UINode mid = col(k, "hud_center", 0, 0);
        mid.anchor = "top";
        mid.w = "wrap";
        mid.set("align", "center");
        UINode score = title(k, "score", str(sp, "score", "0"), k.h1());
        mid.addChild(score);
        if (has(sp, "timer")) mid.addChild(chip(k, "timer", "timer-outline", str(sp, "timer", "01:30"), "text"));
        hud.addChild(mid);
        // right: coins + pause
        UINode right = row(k, "hud_right", k.md(), 0);
        right.anchor = "topright";
        right.w = "wrap";
        if (!has(sp, "coins") || !str(sp, "coins", "").equals("false")) right.addChild(chip(k, "coins", "two-coins", str(sp, "coins", "0"), "#ffd23f"));
        boolean pause = bool(sp, "pause", true);
        if (pause) {
            UINode pb = iconButton(k, "pause_button", "pause", "II", "secondary", k.iconBtn());
            pb.set("action", "show:pause_overlay");
            right.addChild(pb);
        }
        hud.addChild(right);
        // touch controls
        String controls = str(sp, "controls", "none").toLowerCase(Locale.ROOT);
        if (!controls.equals("none") && !controls.isEmpty()) hud.addChild(controls(k, controls));
        // the pause card lives in the HUD, hidden; the pause button shows it, Resume hides it (pause the game itself in code)
        if (pause) {
            UINode p = pause(k, obj("title", str(sp, "pauseTitle", "Paused")), true);
            p.visible = false;
            hud.addChild(p);
            k.notes.add("HUD: the pause button shows the built-in pause card (pause_overlay) and Resume hides it. To freeze the game add code to pause_button onClick / resume onClick.");
        }
        return hud;
    }

    static UINode controls(K k, String kind) {
        UINode st = stack(k, "controls");
        st.touchable = "childrenOnly";
        int big = k.px(130), mid = k.px(112);
        UINode leftPad = row(k, "move_pad", k.lg(), 0);
        leftPad.anchor = "bottomleft";
        leftPad.w = "wrap";
        UINode rightPad = row(k, "action_pad", k.lg(), 0);
        rightPad.anchor = "bottomright";
        rightPad.w = "wrap";
        rightPad.set("align", "end");
        switch (kind) {
            case "dpad": {
                UINode grid = UINode.create(UINode.GRID, "dpad");
                grid.set("columns", 3);
                grid.set("spacing", k.xs());
                grid.set("padding", 0);
                grid.anchor = "bottomleft";
                grid.w = String.valueOf(mid * 3 + k.xs() * 2);
                grid.h = "wrap";
                String[][] cells = {{"", ""}, {"up", "chevron-up"}, {"", ""}, {"left", "chevron-left"}, {"", ""}, {"right", "chevron-right"}, {"", ""}, {"down", "chevron-down"}, {"", ""}};
                for (int i = 0; i < cells.length; i++) {
                    if (cells[i][0].isEmpty()) grid.addChild(spacer(k, "dpad_gap" + i, mid));
                    else grid.addChild(iconButton(k, cells[i][0], cells[i][1], cells[i][0].substring(0, 1).toUpperCase(Locale.ROOT), "glass", mid));
                }
                st.addChild(grid);
                UINode a = iconButton(k, "action", "flash", "A", "primary", big);
                rightPad.addChild(a);
                st.addChild(rightPad);
                return st;
            }
            case "racing":
                leftPad.addChild(iconButton(k, "left", "chevron-left", "<", "glass", big));
                leftPad.addChild(iconButton(k, "right", "chevron-right", ">", "glass", big));
                rightPad.addChild(iconButton(k, "brake", "chevron-double-down", "B", "danger", mid));
                rightPad.addChild(iconButton(k, "gas", "chevron-double-up", "G", "success", big));
                break;
            case "shooter":
                leftPad.addChild(iconButton(k, "left", "chevron-left", "<", "glass", big));
                leftPad.addChild(iconButton(k, "right", "chevron-right", ">", "glass", big));
                rightPad.addChild(iconButton(k, "fire", "target", "F", "danger", mid));
                rightPad.addChild(iconButton(k, "jump", "arrow-up-bold", "J", "primary", big));
                break;
            default: // platformer
                leftPad.addChild(iconButton(k, "left", "chevron-left", "<", "glass", big));
                leftPad.addChild(iconButton(k, "right", "chevron-right", ">", "glass", big));
                rightPad.addChild(iconButton(k, "jump", "arrow-up-bold", "J", "primary", big));
                break;
        }
        st.addChild(leftPad);
        st.addChild(rightPad);
        k.notes.add("controls: hold events are onTouchStart / onTouchEnd of the buttons (" + kind + ").");
        return st;
    }

    static UINode pause(K k, JsonObject sp, boolean embedded) {
        String rootId = embedded ? "pause_overlay" : "pause";
        UINode ov = overlay(k, rootId);
        UINode[] cd = card(k, "pause_card", String.valueOf(Math.round(Math.min(k.px(520), k.W * 0.86f))), k.xl(), k.md());
        cd[0].anchor = "center";
        UINode t = title(k, "pause_title", str(sp, "title", "Paused"), k.h1());
        t.w = "fill";
        cd[1].addChild(t);
        cd[1].addChild(spacer(k, "pause_gap", k.sm()));
        ArrayList<JsonObject> btns = list(sp, "buttons");
        if (btns.isEmpty()) {
            btns.add(obj("text", "Resume", "id", "resume", "hide", rootId));
            btns.add(obj("text", "Restart", "id", "restart"));
            btns.add(obj("text", "Settings", "id", "settings"));
            btns.add(obj("text", "Home", "id", "home"));
        }
        for (int i = 0; i < btns.size(); i++) {
            JsonObject e = btns.get(i);
            cd[1].addChild(buttonFrom(k, e, i == 0 ? "primary" : "secondary", i == 0 ? k.btn() + k.px(6) : k.btn(), guessIcon(str(e, "text", ""))));
        }
        ov.addChild(cd[0]);
        return ov;
    }

    static UINode settings(K k, JsonObject sp) {
        UINode[] pg = page(k, "settings");
        pg[1].addChild(header(k, "settings_header", str(sp, "title", "Settings"), sp, null));
        UINode content = col(k, "settings_content", k.lg(), k.lg());
        ArrayList<JsonObject> groups = list(sp, "groups");
        if (groups.isEmpty() && has(sp, "rows")) groups.add(obj("title", "", "rows", sp.get("rows")));
        if (groups.isEmpty()) {
            groups.add(obj("title", "Audio", "rows", arr(
                    obj("type", "slider", "label", "Music", "id", "music", "icon", "music", "value", 0.8),
                    obj("type", "slider", "label", "Sound effects", "id", "sound", "icon", "volume-high", "value", 1))));
            groups.add(obj("title", "Game", "rows", arr(
                    obj("type", "switch", "label", "Vibration", "id", "vibration", "icon", "vibrate", "value", true),
                    obj("type", "switch", "label", "Show hints", "id", "hints", "icon", "lightbulb-on", "value", true),
                    obj("type", "choice", "label", "Graphics", "id", "quality", "icon", "monitor", "options", arr("Low", "Medium", "High"), "value", "High"))));
            groups.add(obj("title", "", "rows", arr(obj("type", "button", "label", "Restore purchases", "id", "restore", "icon", "refresh"))));
        }
        int gi = 0;
        for (JsonObject g : groups) {
            gi++;
            String gt = str(g, "title", "");
            if (!gt.isEmpty()) {
                UINode h = label(k, "group_" + gt, gt.toUpperCase(Locale.ROOT), k.small(), "primary");
                h.mb = -k.sm();
                content.addChild(h);
            }
            UINode[] cd = card(k, "group" + gi, "fill", k.md(), k.sm());
            for (JsonObject r : list(g, "rows")) cd[1].addChild(settingRow(k, r));
            content.addChild(cd[0]);
        }
        pg[1].addChild(scrollBody(k, "settings_scroll", content)[0]);
        return pg[0];
    }

    static UINode settingRow(K k, JsonObject r) {
        String type = str(r, "type", "switch").toLowerCase(Locale.ROOT);
        String lab = str(r, "label", str(r, "text", "Option"));
        String id = str(r, "id", lab);
        UINode row = row(k, id + "_row", k.md(), 0);
        row.set("padding", arr(k.xs(), k.xs(), k.xs(), k.xs()));
        row.h = String.valueOf(k.rowH());
        UINode ic = icon(k, id + "_icon", str(r, "icon", ""), "primary", k.icon());
        if (ic != null) row.addChild(ic);
        if (type.equals("button")) {
            UINode b = button(k, id, lab, str(r, "style", "secondary"), "", k.btnSm());
            b.weight = 1;
            wire(b, r);
            row.addChild(b);
            return row;
        }
        UINode l = label(k, id + "_label", lab, k.body(), "text");
        if (type.equals("slider")) {
            l.w = String.valueOf(Math.round(k.px(k.land ? 260 : 210)));
            row.addChild(l);
            UINode s = UINode.create(UINode.SLIDER, k.id(id));
            s.set("style", "jelly");
            s.set("value", has(r, "value") ? r.get("value").getAsFloat() : 0.8f);
            s.set("min", num(r, "min", 0));
            s.set("max", has(r, "max") ? r.get("max").getAsFloat() : 1f);
            s.w = "fill";
            s.weight = 1;
            s.h = String.valueOf(k.px(52));
            k.interactive.add(s.id);
            row.addChild(s);
        } else if (type.equals("choice") || type.equals("radio") || type.equals("select")) {
            l.w = "fill";
            l.weight = 1;
            row.addChild(l);
            UINode opts = row(k, id, k.xs(), 0);
            opts.w = "wrap";
            String cur = str(r, "value", "");
            ArrayList<JsonObject> os = list(r, "options");
            if (os.isEmpty()) {
                os.add(obj("text", "Off"));
                os.add(obj("text", "On"));
            }
            ArrayList<String> ids = new ArrayList<>();
            for (int i = 0; i < os.size(); i++) {
                String t = str(os.get(i), "text", "?");
                boolean on = cur.isEmpty() ? i == os.size() - 1 : t.equals(cur);
                UINode b = button(k, id + "_" + t, t, on ? "primary" : "ghost", "", k.btnSm() - k.px(6));
                b.w = "wrap";
                b.set("fontSize", k.small());
                b.set("shape", "pill");
                b.set("action", "select");
                ids.add(b.id);
                opts.addChild(b);
            }
            // "select" moves the highlight to the tapped option (tabOn / tabOff styles); read the choice with ui.getTag / the button ids
            row.addChild(opts);
        } else { // switch
            l.w = "fill";
            l.weight = 1;
            row.addChild(l);
            UINode s = UINode.create(UINode.SWITCH, k.id(id));
            s.set("style", "jelly");
            s.set("checked", bool(r, "value", true));
            s.w = String.valueOf(k.px(92));
            s.h = String.valueOf(k.px(50));
            k.interactive.add(s.id);
            row.addChild(s);
        }
        return row;
    }

    static UINode dialog(K k, JsonObject sp) {
        UINode ov = overlay(k, "dialog");
        UINode[] cd = card(k, "dialog_card", String.valueOf(Math.round(Math.min(k.px(560), k.W * 0.88f))), k.xl(), k.md());
        cd[0].anchor = "center";
        cd[1].set("align", "center");
        UINode ic = icon(k, "dialog_icon", str(sp, "icon", "help-circle"), str(sp, "iconColor", "accent"), k.px(84));
        if (ic != null) cd[1].addChild(ic);
        UINode t = title(k, "dialog_title", str(sp, "title", "Are you sure?"), k.h2());
        t.w = "fill";
        t.set("wrap", true);
        cd[1].addChild(t);
        cd[1].addChild(wrapText(k, "dialog_message", str(sp, "message", "Your progress in this level will be lost."), k.body(), "textDim", "center"));
        cd[1].addChild(spacer(k, "dialog_gap", k.sm()));
        UINode btns = row(k, "dialog_buttons", k.md(), 0);
        String cancel = str(sp, "cancel", "Cancel");
        if (!cancel.isEmpty()) {
            UINode no = button(k, "cancel", cancel, "ghost", "", k.btn());
            no.weight = 1;
            no.set("action", "hide:dialog");
            btns.addChild(no);
        }
        UINode yes = button(k, "ok", str(sp, "ok", "OK"), str(sp, "okStyle", "primary"), "", k.btn());
        yes.weight = 1;
        yes.set("action", "hide:dialog");
        btns.addChild(yes);
        cd[1].addChild(btns);
        ov.addChild(cd[0]);
        k.notes.add("dialog: OK and Cancel hide the dialog by themselves; add your code to ok onClick. Show it from code with ui.setVisible(\"dialog\", true) or a button action show:dialog.");
        return ov;
    }

    static UINode result(K k, JsonObject sp) {
        boolean win = bool(sp, "win", has(sp, "stars") && num(sp, "stars", 0) > 0);
        UINode ov = overlay(k, win ? "level_complete" : "game_over");
        UINode[] cd = card(k, "result_card", String.valueOf(Math.round(Math.min(k.px(580), k.W * 0.9f))), k.xl(), k.md());
        cd[0].anchor = "center";
        cd[1].set("align", "center");
        UINode t = title(k, "result_title", str(sp, "title", win ? "Level Complete!" : "Game Over"), k.h1());
        t.set("color", win ? "success" : "danger");
        t.w = "fill";
        t.set("wrap", true);
        cd[1].addChild(t);
        if (win || has(sp, "stars")) {
            UINode st = stars(k, "result_stars", Math.max(0, Math.min(3, num(sp, "stars", 3))), k.px(78));
            cd[1].addChild(st);
        }
        cd[1].addChild(label(k, "score_caption", str(sp, "scoreLabel", "SCORE"), k.small(), "textDim"));
        UINode sc = title(k, "final_score", str(sp, "score", "0"), k.display());
        cd[1].addChild(sc);
        cd[1].addChild(label(k, "best_score", str(sp, "best", "Best: 0"), k.body(), "textDim"));
        if (has(sp, "reward")) cd[1].addChild(chip(k, "reward", "two-coins", "+" + str(sp, "reward", "50"), "#ffd23f"));
        cd[1].addChild(spacer(k, "result_gap", k.sm()));
        ArrayList<JsonObject> btns = list(sp, "buttons");
        if (btns.isEmpty()) {
            if (win) btns.add(obj("text", "Next", "id", "next", "style", "success"));
            btns.add(obj("text", win ? "Replay" : "Retry", "id", "retry"));
            btns.add(obj("text", "Home", "id", "home", "style", "secondary"));
        }
        UINode first = buttonFrom(k, btns.get(0), "primary", k.btn() + k.px(8), guessIcon(str(btns.get(0), "text", "")));
        cd[1].addChild(first);
        if (btns.size() > 1) {
            UINode r = row(k, "result_buttons", k.md(), 0);
            for (int i = 1; i < btns.size(); i++) {
                UINode b = buttonFrom(k, btns.get(i), "secondary", k.btn(), guessIcon(str(btns.get(i), "text", "")));
                b.weight = 1;
                r.addChild(b);
            }
            cd[1].addChild(r);
        }
        ov.addChild(cd[0]);
        return ov;
    }

    static UINode levels(K k, JsonObject sp) {
        UINode[] pg = page(k, "level_select");
        int count = Math.max(1, Math.min(200, num(sp, "levels", 20)));
        int unlocked = Math.max(0, num(sp, "unlocked", 3));
        int[] st = new int[count];
        ArrayList<JsonObject> raw = new ArrayList<>();
        if (has(sp, "stars") && sp.get("stars").isJsonArray()) {
            JsonArray a = sp.getAsJsonArray("stars");
            for (int i = 0; i < count && i < a.size(); i++) try { st[i] = a.get(i).getAsInt(); } catch (Exception ignored) {}
        } else for (int i = 0; i < Math.max(0, unlocked - 1) && i < count; i++) st[i] = 3 - (i % 2);
        int total = 0;
        for (int s : st) total += s;
        ArrayList<UINode> right = new ArrayList<>();
        right.add(chip(k, "star_total", "star", total + "/" + (count * 3), "#ffd23f"));
        pg[1].addChild(header(k, "levels_header", str(sp, "title", "Select Level"), sp, right));
        UINode grid = UINode.create(UINode.GRID, "levels_grid");
        int cols = num(sp, "columns", columns(k, 4, 6));
        grid.set("columns", cols);
        grid.set("spacing", k.lg());
        grid.set("padding", k.lg());
        grid.set("align", "stretch");
        int tile = Math.round(Math.min(k.px(150), (k.W - k.lg() * (cols + 1)) / cols));
        for (int i = 1; i <= count; i++) {
            boolean open = i <= unlocked;
            UINode cell = UINode.create(UINode.STACK, "level" + i + "_cell");
            cell.w = "fill";
            cell.h = String.valueOf(tile + k.px(10));
            cell.touchable = "childrenOnly";
            UINode b = UINode.create(UINode.BUTTON, "level" + i);
            b.anchor = "stretch";
            b.set("style", open ? (i == unlocked ? "accent" : "primary") : "flat");
            b.set("radius", k.px(24));
            if (open) {
                b.set("text", String.valueOf(i));
                b.set("fontSize", k.h2());
                b.mb = k.px(14);
            } else {
                String lock = k.icon("lock");
                b.set("text", lock.isEmpty() ? "🔒" : "");
                if (!lock.isEmpty()) b.set("icon", lock);
                b.set("fontSize", k.h2());
                b.enabled = false;
            }
            k.interactive.add(b.id);
            cell.addChild(b);
            if (open && i < unlocked) {
                UINode s = stars(k, "level" + i + "_stars", st[i - 1], k.px(26));
                s.anchor = "bottom";
                s.touchable = "disabled";
                cell.addChild(s);
            }
            grid.addChild(cell);
        }
        pg[1].addChild(scrollBody(k, "levels_scroll", grid)[0]);
        k.notes.add("level_select: tiles level1..level" + count + " (locked ones are enabled=false: unlock from code with ui.setEnabled(\"levelN\", true)).");
        return pg[0];
    }

    static UINode itemCard(K k, JsonObject it, int index, String tabKey) {
        String name = str(it, "name", str(it, "title", "Item " + index));
        String base = k.id((tabKey.isEmpty() ? "" : tabKey + "_") + "item" + index);
        UINode[] cd = card(k, base, "fill", k.md(), k.sm());
        cd[1].set("align", "center");
        UINode pic = picture(k, base + "_pic", it, k.px(110), "accent");
        cd[1].addChild(pic);
        UINode n = label(k, base + "_name", name, k.body(), "text");
        n.set("textAlign", "center");
        n.w = "fill";
        n.set("wrap", true);
        cd[1].addChild(n);
        if (has(it, "desc")) cd[1].addChild(wrapText(k, base + "_desc", str(it, "desc", ""), k.tiny(), "textDim", "center"));
        boolean owned = bool(it, "owned", false);
        UINode b = button(k, base + "_buy", owned ? str(it, "ownedText", "Owned") : str(it, "price", "100"), owned ? "ghost" : "success",
                owned ? "check" : str(it, "priceIcon", "two-coins"), k.btnSm());
        if (owned) b.enabled = false;
        cd[1].addChild(b);
        return cd[0];
    }

    static UINode shop(K k, JsonObject sp) {
        UINode[] pg = page(k, "shop");
        ArrayList<UINode> right = new ArrayList<>();
        right.add(chip(k, "currency", str(sp, "currencyIcon", "two-coins"), str(sp, "currency", str(sp, "coins", "1,250")), "#ffd23f"));
        pg[1].addChild(header(k, "shop_header", str(sp, "title", "Shop"), sp, right));
        ArrayList<JsonObject> items = list(sp, "items");
        if (items.isEmpty()) {
            String[][] def = {{"Red Car", "300", "car-sports"}, {"Rocket", "900", "rocket-launch"}, {"Shield", "150", "shield"}, {"Magnet", "200", "magnet"},
                    {"Extra Life", "250", "heart"}, {"Speed Boost", "120", "flash"}, {"Gem Pack", "500", "diamond-stone"}, {"Crown", "1000", "crown"}};
            for (int i = 0; i < def.length; i++) items.add(obj("name", def[i][0], "price", def[i][1], "icon", def[i][2], "owned", i == 2));
        }
        ArrayList<String> tabs = new ArrayList<>();
        for (JsonObject t : list(sp, "tabs")) tabs.add(str(t, "text", "Tab"));
        int cols = num(sp, "columns", columns(k, 2, 4));
        if (tabs.isEmpty()) {
            pg[1].addChild(scrollBody(k, "shop_scroll", itemGrid(k, "shop_grid", items, cols, ""))[0]);
            return pg[0];
        }
        ArrayList<String> pageIds = new ArrayList<>();
        for (String t : tabs) pageIds.add(k.id("page_" + t));
        pg[1].addChild(tabBar(k, "shop_tabs", tabs, pageIds));
        UINode pages = UINode.create(UINode.STACK, "shop_pages");
        pages.w = "fill";
        pages.h = "fill";
        pages.weight = 1;
        for (int ti = 0; ti < tabs.size(); ti++) {
            ArrayList<JsonObject> mine = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                JsonObject it = items.get(i);
                String tab = str(it, "tab", "");
                if (tab.equalsIgnoreCase(tabs.get(ti)) || (tab.isEmpty() && i % tabs.size() == ti)) mine.add(it);
            }
            UINode sc = scrollBody(k, pageIds.get(ti), itemGrid(k, pageIds.get(ti) + "_grid", mine, cols, pageIds.get(ti)))[0];
            sc.anchor = "stretch";
            sc.visible = ti == 0;
            pages.addChild(sc);
        }
        pg[1].addChild(pages);
        return pg[0];
    }

    static UINode itemGrid(K k, String id, List<JsonObject> items, int cols, String tabKey) {
        UINode grid = UINode.create(UINode.GRID, k.id(id));
        grid.set("columns", cols);
        grid.set("spacing", k.lg());
        grid.set("padding", k.lg());
        grid.set("align", "stretch");
        for (int i = 0; i < items.size(); i++) grid.addChild(itemCard(k, items.get(i), i + 1, tabKey));
        return grid;
    }

    static UINode inventory(K k, JsonObject sp) {
        UINode[] pg = page(k, "inventory");
        pg[1].addChild(header(k, "inventory_header", str(sp, "title", "Inventory"), sp, null));
        ArrayList<JsonObject> items = list(sp, "items");
        if (items.isEmpty()) {
            String[][] def = {{"Potion", "flask", "5"}, {"Sword", "sword", "1"}, {"Shield", "shield", "1"}, {"Key", "key", "3"}, {"Gem", "diamond-stone", "12"},
                    {"Bomb", "bomb", "4"}, {"Map", "map", "1"}, {"Apple", "food-apple", "9"}};
            for (String[] d : def) items.add(obj("name", d[0], "icon", d[1], "count", d[2]));
        }
        int cols = num(sp, "columns", columns(k, 4, 7));
        UINode grid = UINode.create(UINode.GRID, "inventory_grid");
        grid.set("columns", cols);
        grid.set("spacing", k.md());
        grid.set("padding", k.lg());
        grid.set("align", "stretch");
        int slot = Math.round(Math.min(k.px(140), (k.W - k.md() * (cols + 1) - k.lg() * 2) / cols));
        int total = Math.max(items.size(), num(sp, "slots", ((items.size() + cols - 1) / cols + 1) * cols));
        for (int i = 0; i < total; i++) {
            JsonObject it = i < items.size() ? items.get(i) : null;
            UINode p = UINode.create(UINode.PANEL, "slot" + (i + 1));
            p.set("style", it == null ? "outline" : "card");
            p.w = "fill";
            p.h = String.valueOf(slot);
            p.touchable = "enabled";
            if (it != null) {
                k.interactive.add(p.id);
                UINode pic = picture(k, "slot" + (i + 1) + "_pic", it, Math.round(slot * 0.56f), "accent");
                pic.anchor = "center";
                p.addChild(pic);
                String cnt = str(it, "count", "");
                if (!cnt.isEmpty() && !cnt.equals("1")) {
                    UINode c = label(k, "slot" + (i + 1) + "_count", "x" + cnt, k.small(), "text");
                    c.set("shadow", true);
                    c.anchor = "bottomright";
                    c.margin(0, 0, k.sm(), k.xs());
                    p.addChild(c);
                }
            }
            grid.addChild(p);
        }
        pg[1].addChild(scrollBody(k, "inventory_scroll", grid)[0]);
        UINode foot = row(k, "inventory_footer", k.md(), k.lg());
        UINode use = button(k, "use", str(sp, "button", "Use"), "primary", "check", k.btn());
        use.weight = 1;
        foot.addChild(use);
        pg[1].addChild(foot);
        return pg[0];
    }

    static UINode leaderboard(K k, JsonObject sp) {
        UINode[] pg = page(k, "leaderboard");
        pg[1].addChild(header(k, "leaderboard_header", str(sp, "title", "Leaderboard"), sp, null));
        ArrayList<String> tabs = new ArrayList<>();
        for (JsonObject t : list(sp, "tabs")) tabs.add(str(t, "text", "Tab"));
        if (tabs.isEmpty() && !has(sp, "tabs")) {
            tabs.add("Today");
            tabs.add("Week");
            tabs.add("All time");
        }
        ArrayList<JsonObject> rows = list(sp, "rows");
        if (rows.isEmpty()) {
            String[] names = {"Nova", "Blaze", "Kira", "You", "Atlas", "Mira", "Zed", "Luna", "Orion", "Ivy"};
            int score = 98500;
            for (int i = 0; i < names.length; i++) {
                rows.add(obj("name", names[i], "score", String.format(Locale.ENGLISH, "%,d", score), "me", names[i].equals("You")));
                score -= 4300 + i * 900;
            }
        }
        if (!tabs.isEmpty()) {
            ArrayList<String> pageIds = new ArrayList<>();
            for (String t : tabs) pageIds.add(k.id("board_" + t));
            pg[1].addChild(tabBar(k, "leaderboard_tabs", tabs, pageIds));
            UINode pages = UINode.create(UINode.STACK, "leaderboard_pages");
            pages.w = "fill";
            pages.h = "fill";
            pages.weight = 1;
            for (int ti = 0; ti < tabs.size(); ti++) {
                UINode sc = scrollBody(k, pageIds.get(ti), rankList(k, pageIds.get(ti) + "_list", rows, pageIds.get(ti)))[0];
                sc.anchor = "stretch";
                sc.visible = ti == 0;
                pages.addChild(sc);
            }
            pg[1].addChild(pages);
        } else pg[1].addChild(scrollBody(k, "leaderboard_scroll", rankList(k, "leaderboard_list", rows, ""))[0]);
        return pg[0];
    }

    static UINode rankList(K k, String id, List<JsonObject> rows, String key) {
        UINode c = col(k, id, k.sm(), k.lg());
        String[] medal = {"#ffd23f", "#c7d0dc", "#d9905a"};
        for (int i = 0; i < rows.size(); i++) {
            JsonObject r = rows.get(i);
            boolean me = bool(r, "me", false);
            String base = k.id((key.isEmpty() ? "" : key + "_") + "rank" + (i + 1));
            UINode p = UINode.create(UINode.PANEL, base);
            p.set("style", me ? "solid" : "card");
            if (me) p.set("panelColor", "primary");
            p.w = "fill";
            p.h = String.valueOf(k.rowH());
            UINode rw = row(k, base + "_row", k.md(), 0);
            rw.set("padding", arr(k.md(), 0, k.lg(), 0));
            rw.anchor = "stretch";
            rw.h = "fill";
            // rank: medal for the top 3
            UINode badge = UINode.create(UINode.PANEL, base + "_badge");
            badge.set("style", "solid");
            badge.set("panelColor", i < 3 ? medal[i] : "surfaceAlt");
            badge.set("radius", k.px(26));
            badge.w = String.valueOf(k.px(52));
            badge.h = String.valueOf(k.px(52));
            UINode num = label(k, base + "_num", String.valueOf(i + 1), k.small(), i < 3 ? "#3a2a00" : "text");
            num.anchor = "center";
            badge.addChild(num);
            rw.addChild(badge);
            UINode av = icon(k, base + "_avatar", "account-circle", me ? "#ffffff" : "textDim", k.px(48));
            if (av != null) rw.addChild(av);
            UINode n = label(k, base + "_name", str(r, "name", "Player"), k.body(), me ? "#ffffff" : "text");
            n.w = "fill";
            n.weight = 1;
            rw.addChild(n);
            UINode s = label(k, base + "_score", str(r, "score", "0"), k.body(), me ? "#ffffff" : "primary");
            s.set("shadow", true);
            rw.addChild(s);
            p.addChild(rw);
            c.addChild(p);
        }
        return c;
    }

    static UINode missions(K k, JsonObject sp) {
        UINode[] pg = page(k, "missions");
        pg[1].addChild(header(k, "missions_header", str(sp, "title", "Missions"), sp, null));
        ArrayList<JsonObject> rows = list(sp, "rows");
        if (rows.isEmpty()) {
            rows.add(obj("title", "Collect 100 coins", "desc", "Coins from any level count", "icon", "two-coins", "progress", 100, "goal", 100, "reward", "50"));
            rows.add(obj("title", "Defeat 20 enemies", "desc", "Stomp or shoot them", "icon", "sword", "progress", 12, "goal", 20, "reward", "80"));
            rows.add(obj("title", "Finish 3 levels", "desc", "Without losing a life", "icon", "flag", "progress", 1, "goal", 3, "reward", "120"));
            rows.add(obj("title", "Play 7 days", "desc", "Log in every day", "icon", "clock-outline", "progress", 4, "goal", 7, "reward", "300"));
        }
        UINode list = col(k, "missions_list", k.md(), k.lg());
        for (int i = 0; i < rows.size(); i++) list.addChild(missionRow(k, rows.get(i), i + 1));
        pg[1].addChild(scrollBody(k, "missions_scroll", list)[0]);
        return pg[0];
    }

    static UINode missionRow(K k, JsonObject r, int index) {
        String base = "mission" + index;
        UINode p = UINode.create(UINode.PANEL, base);
        p.set("style", "card");
        p.w = "fill";
        p.h = "wrap";
        UINode rw = row(k, base + "_row", k.md(), k.md());
        rw.anchor = "topleft";
        UINode pic = picture(k, base + "_icon", r, k.px(64), "accent");
        rw.addChild(pic);
        UINode mid = col(k, base + "_text", k.xs(), 0);
        mid.weight = 1;
        mid.set("align", "stretch");
        mid.addChild(label(k, base + "_title", str(r, "title", "Mission"), k.body(), "text"));
        if (has(r, "desc")) mid.addChild(label(k, base + "_desc", str(r, "desc", ""), k.tiny(), "textDim"));
        int goal = Math.max(1, num(r, "goal", 1)), prog = Math.max(0, Math.min(goal, num(r, "progress", 0)));
        UINode bar = UINode.create(UINode.PROGRESS, base + "_progress");
        bar.set("style", "xp");
        bar.set("max", goal);
        bar.set("value", prog);
        bar.set("showText", true);
        bar.set("textFormat", "fraction");
        bar.w = "fill";
        bar.h = String.valueOf(k.px(28));
        mid.addChild(bar);
        rw.addChild(mid);
        boolean done = prog >= goal;
        String reward = str(r, "reward", "");
        UINode b = button(k, base + "_claim", done ? str(r, "claimText", "Claim") : (reward.isEmpty() ? "…" : "+" + reward), done ? "success" : "flat",
                done ? "gift" : (reward.isEmpty() ? "" : "two-coins"), k.btnSm());
        b.w = String.valueOf(k.px(170));
        if (!done) b.enabled = false;
        rw.addChild(b);
        p.addChild(rw);
        return p;
    }

    static UINode daily(K k, JsonObject sp) {
        UINode ov = overlay(k, "daily_reward");
        UINode[] cd = card(k, "daily_card", String.valueOf(Math.round(Math.min(k.px(660), k.W * 0.92f))), k.xl(), k.md());
        cd[0].anchor = "center";
        cd[1].set("align", "stretch");
        UINode t = title(k, "daily_title", str(sp, "title", "Daily Reward"), k.h1());
        t.w = "fill";
        cd[1].addChild(t);
        cd[1].addChild(wrapText(k, "daily_hint", str(sp, "subtitle", "Come back every day for bigger rewards!"), k.small(), "textDim", "center"));
        int today = Math.max(1, Math.min(7, num(sp, "day", 3)));
        ArrayList<JsonObject> rewards = list(sp, "rewards");
        String[] def = {"50", "80", "120", "150", "200", "300", "1000"};
        UINode grid = UINode.create(UINode.GRID, "daily_grid");
        grid.set("columns", 4);
        grid.set("spacing", k.sm());
        grid.set("padding", 0);
        grid.set("align", "stretch");
        int cell = k.px(150);
        for (int d = 1; d <= 7; d++) {
            String amount = d - 1 < rewards.size() ? str(rewards.get(d - 1), "text", def[d - 1]) : def[d - 1];
            UINode p = UINode.create(UINode.PANEL, "day" + d);
            p.set("style", d == today ? "solid" : "card");
            if (d == today) p.set("panelColor", "primary");
            if (d < today) p.alpha = 0.55f;
            p.w = "fill";
            p.h = String.valueOf(cell);
            UINode c = col(k, "day" + d + "_col", k.xs(), k.sm());
            c.anchor = "stretch";
            c.set("align", "center");
            c.set("gravity", "center");
            c.addChild(label(k, "day" + d + "_label", str(sp, "dayLabel", "Day") + " " + d, k.tiny(), d == today ? "#ffffff" : "textDim"));
            UINode ic = icon(k, "day" + d + "_icon", d < today ? "check-circle" : d == 7 ? "treasure-chest" : "two-coins", d < today ? "success" : "#ffd23f", k.px(d == 7 ? 54 : 46));
            if (ic != null) c.addChild(ic);
            c.addChild(label(k, "day" + d + "_amount", amount, k.small(), d == today ? "#ffffff" : "text"));
            p.addChild(c);
            grid.addChild(p);
        }
        cd[1].addChild(grid);
        UINode claim = button(k, "claim", str(sp, "button", "Claim"), "success", "gift", k.btn());
        claim.set("action", "hide:daily_reward");
        cd[1].addChild(claim);
        ov.addChild(cd[0]);
        return ov;
    }

    static UINode characters(K k, JsonObject sp) {
        UINode[] pg = page(k, "character_select");
        pg[1].addChild(header(k, "characters_header", str(sp, "title", "Choose Hero"), sp, null));
        ArrayList<JsonObject> chars = list(sp, "characters");
        if (chars.isEmpty()) {
            chars.add(obj("name", "Knight", "icon", "shield", "stats", obj("Power", 70, "Speed", 40, "Defense", 90)));
            chars.add(obj("name", "Ninja", "icon", "sword", "stats", obj("Power", 60, "Speed", 95, "Defense", 35)));
            chars.add(obj("name", "Wizard", "icon", "wizard-hat", "stats", obj("Power", 95, "Speed", 50, "Defense", 30)));
            chars.add(obj("name", "Robot", "icon", "robot", "stats", obj("Power", 75, "Speed", 60, "Defense", 75)));
        }
        UINode sc = UINode.create(UINode.SCROLL, "characters_scroll");
        sc.set("direction", "horizontal");
        sc.w = "fill";
        sc.h = "fill";
        sc.weight = 1;
        UINode strip = row(k, "characters_row", k.lg(), k.lg());
        strip.w = "wrap";
        strip.h = "fill";
        strip.set("align", "center");
        int cw = Math.round(Math.min(k.px(340), k.W * (k.land ? 0.3f : 0.62f)));
        for (int i = 0; i < chars.size(); i++) {
            JsonObject c = chars.get(i);
            String base = "hero" + (i + 1);
            UINode[] cd = card(k, base, String.valueOf(cw), k.lg(), k.sm());
            cd[1].set("align", "center");
            cd[1].addChild(picture(k, base + "_pic", c, k.px(150), i % 2 == 0 ? "primary" : "accent"));
            cd[1].addChild(title(k, base + "_name", str(c, "name", "Hero"), k.h2()));
            if (has(c, "stats") && c.get("stats").isJsonObject()) {
                for (java.util.Map.Entry<String, JsonElement> e : c.getAsJsonObject("stats").entrySet()) {
                    UINode r = row(k, base + "_" + e.getKey(), k.sm(), 0);
                    UINode l = label(k, base + "_" + e.getKey() + "_label", e.getKey(), k.tiny(), "textDim");
                    l.w = String.valueOf(k.px(100));
                    r.addChild(l);
                    UINode bar = UINode.create(UINode.PROGRESS, k.id(base + "_" + e.getKey() + "_bar"));
                    bar.set("style", "xp");
                    bar.set("fillColor", STAT_COLORS[cd[1].children.size() % STAT_COLORS.length]);
                    bar.set("max", 100);
                    try { bar.set("value", e.getValue().getAsFloat()); } catch (Exception ignored) { bar.set("value", 50); }
                    bar.w = "fill";
                    bar.weight = 1;
                    bar.h = String.valueOf(k.px(20));
                    r.addChild(bar);
                    cd[1].addChild(r);
                }
            }
            UINode sel = button(k, base + "_select", str(sp, "button", "Select"), i == 0 ? "success" : "primary", i == 0 ? "check" : "", k.btnSm());
            cd[1].addChild(sel);
            cd[0].anchor = "left";
            strip.addChild(cd[0]);
        }
        sc.addChild(strip);
        pg[1].addChild(sc);
        return pg[0];
    }

    static final String[] STAT_COLORS = {"success", "accent", "primary", "secondary", "danger"};

    static UINode loading(K k, JsonObject sp) {
        UINode root = screenRoot(k, "loading");
        UINode c = col(k, "loading_column", k.lg(), 0);
        c.anchor = "center";
        c.w = String.valueOf(Math.round(Math.min(k.px(620), k.W * 0.8f)));
        c.set("align", "center");
        UINode ic = icon(k, "loading_logo", str(sp, "icon", "gamepad-variant"), "primary", k.px(120));
        if (ic != null) c.addChild(ic);
        UINode t = title(k, "loading_title", str(sp, "title", "My Game"), k.display());
        t.set("wrap", true);
        t.w = "fill";
        c.addChild(t);
        UINode bar = UINode.create(UINode.PROGRESS, "progress");
        bar.set("style", "thick_candy");
        bar.set("value", 35);
        bar.set("showText", true);
        bar.w = "fill";
        bar.h = String.valueOf(k.px(40));
        c.addChild(bar);
        c.addChild(label(k, "loading_text", str(sp, "text", "Loading..."), k.small(), "textDim"));
        root.addChild(c);
        UINode tip = wrapText(k, "tip", str(sp, "tip", "Tip: collect coins to unlock new heroes."), k.small(), "textDim", "center");
        tip.anchor = "bottom";
        tip.w = String.valueOf(Math.round(k.W * 0.84f));
        tip.mb = k.xl();
        root.addChild(tip);
        k.notes.add("loading: set the bar from code with ui.setValue(\"progress\", 0..100).");
        return root;
    }

    static UINode login(K k, JsonObject sp) {
        UINode root = screenRoot(k, "login");
        UINode[] cd = card(k, "login_card", String.valueOf(Math.round(Math.min(k.px(600), k.W * 0.9f))), k.xl(), k.md());
        cd[0].anchor = "center";
        UINode t = title(k, "login_title", str(sp, "title", "Welcome"), k.h1());
        t.w = "fill";
        cd[1].addChild(t);
        if (has(sp, "subtitle")) cd[1].addChild(wrapText(k, "login_subtitle", str(sp, "subtitle", ""), k.small(), "textDim", "center"));
        ArrayList<JsonObject> fields = list(sp, "fields");
        if (fields.isEmpty()) {
            fields.add(obj("label", "Player name", "id", "name", "hint", "Your name"));
            fields.add(obj("label", "Password", "id", "password", "hint", "Password", "password", true));
        }
        for (JsonObject f : fields) cd[1].addChild(field(k, f));
        cd[1].addChild(spacer(k, "login_gap", k.xs()));
        cd[1].addChild(button(k, "submit", str(sp, "button", "Continue"), "primary", "arrow-right", k.btn()));
        String link = str(sp, "link", "Play as guest");
        if (!link.isEmpty()) cd[1].addChild(button(k, "secondary_action", link, "ghost", "", k.btnSm()));
        root.addChild(cd[0]);
        return root;
    }

    static UINode field(K k, JsonObject f) {
        String lab = str(f, "label", str(f, "text", "Field"));
        String id = str(f, "id", lab);
        UINode c = col(k, id + "_box", k.xs(), 0);
        c.addChild(label(k, id + "_label", lab, k.small(), "textDim"));
        UINode tf = UINode.create(UINode.TEXTFIELD, k.id(id));
        tf.set("style", "jelly");
        tf.set("hint", str(f, "hint", ""));
        tf.set("fontSize", k.body());
        if (bool(f, "password", false)) tf.set("password", true);
        if (bool(f, "numeric", false)) tf.set("numeric", true);
        tf.w = "fill";
        tf.h = String.valueOf(k.px(72));
        k.interactive.add(tf.id);
        c.addChild(tf);
        return c;
    }

    static UINode tabsScreen(K k, JsonObject sp) {
        UINode[] pg = page(k, "tabs_screen");
        pg[1].addChild(header(k, "tabs_header", str(sp, "title", "Info"), sp, null));
        ArrayList<JsonObject> tabs = list(sp, "tabs");
        if (tabs.isEmpty()) {
            tabs.add(obj("title", "How to play", "text", "Tap to jump. Collect coins and avoid the enemies."));
            tabs.add(obj("title", "Tips", "text", "Jump on enemies to defeat them."));
            tabs.add(obj("title", "About", "text", "Made with Star3D."));
        }
        ArrayList<String> titles = new ArrayList<>(), ids = new ArrayList<>();
        for (JsonObject t : tabs) {
            titles.add(str(t, "title", str(t, "text", "Tab")));
            ids.add(k.id("page_" + str(t, "title", "tab")));
        }
        pg[1].addChild(tabBar(k, "tabs_bar", titles, ids));
        UINode pages = UINode.create(UINode.STACK, "tab_pages");
        pages.w = "fill";
        pages.h = "fill";
        pages.weight = 1;
        for (int i = 0; i < tabs.size(); i++) {
            UINode content = col(k, ids.get(i) + "_content", k.md(), k.lg());
            JsonObject t = tabs.get(i);
            if (has(t, "sections")) {
                for (JsonObject s : list(t, "sections")) {
                    UINode part = part(k, str(s, "part", "text"), s);
                    if (part != null) content.addChild(part);
                }
            } else content.addChild(wrapText(k, ids.get(i) + "_text", str(t, "text", ""), k.body(), "text", "left"));
            UINode sc = scrollBody(k, ids.get(i), content)[0];
            sc.anchor = "stretch";
            sc.visible = i == 0;
            pages.addChild(sc);
        }
        pg[1].addChild(pages);
        return pg[0];
    }

    static UINode credits(K k, JsonObject sp) {
        UINode[] pg = page(k, "credits");
        pg[1].addChild(header(k, "credits_header", str(sp, "title", "Credits"), sp, null));
        UINode c = col(k, "credits_content", k.lg(), k.xl());
        c.set("align", "center");
        UINode ic = icon(k, "credits_logo", str(sp, "icon", "star-circle"), "accent", k.px(110));
        if (ic != null) c.addChild(ic);
        c.addChild(wrapText(k, "credits_text", str(sp, "text", "Game design: You\nArt: You\nMusic: You\n\nMade with Star3D"), k.body(), "text", "center"));
        pg[1].addChild(scrollBody(k, "credits_scroll", c)[0]);
        return pg[0];
    }

    /** Header + sections stacked; a part that scrolls (grid / list / rows / text) takes the remaining height. */
    static UINode custom(K k, JsonObject sp) {
        UINode[] pg = page(k, str(sp, "id", "screen"));
        if (!str(sp, "title", "").isEmpty() || has(sp, "back")) pg[1].addChild(header(k, "header", str(sp, "title", ""), sp, chipsOf(k, sp)));
        UINode body = col(k, "body", k.lg(), k.lg());
        for (JsonObject s : list(sp, "sections")) {
            UINode n = part(k, str(s, "part", str(s, "type", "text")), s);
            if (n != null) body.addChild(n);
            else k.notes.add("unknown part '" + str(s, "part", "") + "'. Parts: " + String.join(", ", PARTS));
        }
        pg[1].addChild(scrollBody(k, "body_scroll", body)[0]);
        return pg[0];
    }

    static List<UINode> chipsOf(K k, JsonObject sp) {
        ArrayList<UINode> out = new ArrayList<>();
        if (has(sp, "currency") || has(sp, "coins")) out.add(chip(k, "currency", str(sp, "currencyIcon", "two-coins"), str(sp, "currency", str(sp, "coins", "0")), "#ffd23f"));
        for (JsonObject c : list(sp, "chips")) out.add(chip(k, str(c, "id", "chip"), str(c, "icon", "star"), str(c, "value", str(c, "text", "0")), str(c, "color", "accent")));
        return out;
    }

    // ================================================================== parts

    /** One building block, from a spec: {part:'grid', items:[...]} etc. Null for an unknown part. */
    static UINode part(K k, String part, JsonObject s) {
        String p = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
        String id = str(s, "id", p);
        switch (p) {
            case "header": return header(k, id, str(s, "title", ""), s, chipsOf(k, s));
            case "title": {
                UINode t = title(k, id, str(s, "text", str(s, "title", "Title")), num(s, "size", k.h1()));
                t.w = "fill";
                t.set("wrap", true);
                return t;
            }
            case "text": return wrapText(k, id, str(s, "text", ""), num(s, "size", k.body()), str(s, "color", "text"), str(s, "align", "left"));
            case "buttons": {
                UINode c = bool(s, "row", false) ? row(k, id, k.md(), 0) : col(k, id, k.md(), 0);
                ArrayList<JsonObject> bs = list(s, "buttons");
                if (bs.isEmpty()) bs = list(s, "items");
                for (int i = 0; i < bs.size(); i++) {
                    UINode b = buttonFrom(k, bs.get(i), i == 0 ? "primary" : "secondary", i == 0 ? k.btnBig() - k.px(8) : k.btn(), guessIcon(str(bs.get(i), "text", "")));
                    if (bool(s, "row", false)) b.weight = 1;
                    c.addChild(b);
                }
                return c;
            }
            case "icon_buttons": {
                UINode r = row(k, id, k.md(), 0);
                r.set("gravity", str(s, "gravity", "center"));
                for (JsonObject b : list(s, "buttons")) {
                    UINode ib = iconButton(k, str(b, "id", str(b, "icon", "b")), str(b, "icon", "star"), str(b, "text", "?"), str(b, "style", "secondary"), num(b, "size", k.iconBtn()));
                    wire(ib, b);
                    r.addChild(ib);
                }
                return r;
            }
            case "chips": {
                UINode r = row(k, id, k.md(), 0);
                r.set("gravity", str(s, "gravity", "center"));
                for (UINode c : chipsOf(k, s)) r.addChild(c);
                return r;
            }
            case "tabs": {
                ArrayList<JsonObject> tabs = list(s, "tabs");
                ArrayList<String> titles = new ArrayList<>(), ids = new ArrayList<>();
                for (JsonObject t : tabs) {
                    titles.add(str(t, "title", str(t, "text", "Tab")));
                    ids.add(k.id(id + "_" + str(t, "title", "tab")));
                }
                UINode c = col(k, id, 0, 0);
                c.addChild(tabBar(k, id + "_bar", titles, ids));
                UINode pages = UINode.create(UINode.STACK, k.id(id + "_pages"));
                pages.w = "fill";
                pages.h = "wrap";
                for (int i = 0; i < tabs.size(); i++) {
                    UINode pc = col(k, ids.get(i), k.md(), k.md());
                    pc.visible = i == 0;
                    for (JsonObject sec : list(tabs.get(i), "sections")) {
                        UINode n = part(k, str(sec, "part", "text"), sec);
                        if (n != null) pc.addChild(n);
                    }
                    if (pc.children.isEmpty()) pc.addChild(wrapText(k, ids.get(i) + "_text", str(tabs.get(i), "text", ""), k.body(), "text", "left"));
                    pages.addChild(pc);
                }
                c.addChild(pages);
                return c;
            }
            case "grid": {
                ArrayList<JsonObject> items = list(s, "items");
                return itemGrid(k, id, items, num(s, "columns", columns(k, 2, 4)), id);
            }
            case "list": {
                UINode c = col(k, id, k.sm(), 0);
                ArrayList<JsonObject> rows = list(s, "rows");
                if (rows.isEmpty()) rows = list(s, "items");
                for (int i = 0; i < rows.size(); i++) c.addChild(listRow(k, rows.get(i), id + "_" + (i + 1)));
                return c;
            }
            case "rows": {
                UINode[] cd = card(k, id, "fill", k.md(), k.sm());
                for (JsonObject r : list(s, "rows")) cd[1].addChild(settingRow(k, r));
                return cd[0];
            }
            case "stars": return stars(k, id, num(s, "value", 3), num(s, "size", k.px(60)));
            case "stat_bars": {
                UINode c = col(k, id, k.sm(), 0);
                for (JsonObject b : list(s, "bars")) {
                    UINode r = row(k, id + "_" + str(b, "label", "stat"), k.sm(), 0);
                    UINode l = label(k, id + "_" + str(b, "label", "stat") + "_label", str(b, "label", "Stat"), k.small(), "textDim");
                    l.w = String.valueOf(k.px(150));
                    r.addChild(l);
                    UINode bar = UINode.create(UINode.PROGRESS, k.id(str(b, "id", id + "_" + str(b, "label", "stat") + "_bar")));
                    bar.set("style", str(b, "style", "xp"));
                    bar.set("fillColor", str(b, "color", STAT_COLORS[c.children.size() % STAT_COLORS.length]));
                    bar.set("max", num(b, "max", 100));
                    bar.set("value", num(b, "value", 50));
                    bar.w = "fill";
                    bar.weight = 1;
                    bar.h = String.valueOf(k.px(24));
                    r.addChild(bar);
                    c.addChild(r);
                }
                return c;
            }
            case "progress": {
                UINode bar = UINode.create(UINode.PROGRESS, k.id(id));
                bar.set("style", str(s, "style", "thick_candy"));
                bar.set("max", num(s, "max", 100));
                bar.set("value", num(s, "value", 50));
                bar.set("showText", bool(s, "showText", true));
                bar.w = "fill";
                bar.h = String.valueOf(num(s, "height", k.px(36)));
                return bar;
            }
            case "fields": {
                UINode c = col(k, id, k.md(), 0);
                for (JsonObject f : list(s, "fields")) c.addChild(field(k, f));
                return c;
            }
            case "card": {
                UINode[] cd = card(k, id, "fill", k.lg(), k.md());
                if (!str(s, "title", "").isEmpty()) cd[1].addChild(label(k, id + "_title", str(s, "title", ""), k.h2(), "text"));
                if (!str(s, "text", "").isEmpty()) cd[1].addChild(wrapText(k, id + "_text", str(s, "text", ""), k.body(), "textDim", "left"));
                for (JsonObject sec : list(s, "sections")) {
                    UINode n = part(k, str(sec, "part", "text"), sec);
                    if (n != null) cd[1].addChild(n);
                }
                return cd[0];
            }
            case "spacer": return spacer(k, id, num(s, "height", k.lg()));
            case "controls": return controls(k, str(s, "kind", "platformer"));
            default: return null;
        }
    }

    /** A tappable list row: picture, title + subtitle, value or chevron. */
    static UINode listRow(K k, JsonObject r, String base) {
        base = k.id(str(r, "id", base));
        UINode p = UINode.create(UINode.PANEL, base);
        p.set("style", "card");
        p.w = "fill";
        p.h = String.valueOf(k.rowH() + k.px(8));
        p.touchable = "enabled";
        wire(p, r);
        k.interactive.add(p.id);
        UINode rw = row(k, base + "_row", k.md(), 0);
        rw.set("padding", arr(k.md(), 0, k.md(), 0));
        rw.anchor = "stretch";
        rw.h = "fill";
        rw.touchable = "disabled";
        if (has(r, "icon") || has(r, "image")) rw.addChild(picture(k, base + "_pic", r, k.px(56), str(r, "color", "accent")));
        UINode mid = col(k, base + "_text", k.xs() / 2, 0);
        mid.weight = 1;
        mid.addChild(label(k, base + "_title", str(r, "title", str(r, "text", str(r, "name", "Item"))), k.body(), "text"));
        if (has(r, "subtitle") || has(r, "desc")) mid.addChild(label(k, base + "_subtitle", str(r, "subtitle", str(r, "desc", "")), k.tiny(), "textDim"));
        rw.addChild(mid);
        if (has(r, "value")) rw.addChild(label(k, base + "_value", str(r, "value", ""), k.body(), "accent"));
        else {
            UINode ch = icon(k, base + "_chevron", "chevron-right", "textDim", k.px(36));
            if (ch != null) rw.addChild(ch);
        }
        p.addChild(rw);
        return p;
    }

    // ================================================================== describe (AI / docs)

    public static JsonObject describe() {
        JsonObject o = new JsonObject();
        JsonArray screens = new JsonArray();
        for (java.util.Map.Entry<String, String[]> e : SCREENS.entrySet()) {
            JsonObject j = new JsonObject();
            j.addProperty("screen", e.getKey());
            j.addProperty("what", e.getValue()[1]);
            j.addProperty("spec", e.getValue()[2]);
            screens.add(j);
        }
        o.add("screens", screens);
        o.add("parts", arr((Object[]) PARTS));
        o.addProperty("button_entry", "a string or {text, id?, icon?, style?: primary|secondary|accent|success|danger|ghost, open?:'otherUi', show?/hide?/page?/tab?:'nodeId', action?:'raw actions'}");
        o.addProperty("actions", "no-code click actions (prop action): show:id hide:id toggle:id page:id tab:id open:uiName back set:id.key=value (';' between several)");
        return o;
    }
}
