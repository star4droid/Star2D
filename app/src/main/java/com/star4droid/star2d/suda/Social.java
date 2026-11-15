package com.star4droid.star2d.suda;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared pieces of the community screens: names, avatars, badges, links, @project mentions, errors, pictures, startup. */
public final class Social {
    private Social() {}

    private static final String PREFS = "social";
    public static final Pattern MENTION = Pattern.compile("@\\[project:(\\d+)\\]");
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);

    /** key, emoji, colour. Titles come from the translations (bd_<key>). */
    public static final String[][] BADGES = {
            {"first_upload", "📦", "#43A047"}, {"first_post", "📝", "#1E88E5"}, {"first_comment", "💬", "#00ACC1"},
            {"first_star", "🌟", "#FDD835"}, {"editor_choice", "🏆", "#FB8C00"},
            {"helper", "🤝", "#7CB342"}, {"creative", "🎨", "#EC407A"}, {"top_creator", "👑", "#FFB300"}, {"bug_hunter", "🐞", "#E53935"},
            {"early_supporter", "🚀", "#7E57C2"}, {"translator", "🌍", "#00897B"}, {"champion", "🥇", "#FF7043"}, {"legend", "🔥", "#F4511E"}};
    /** The ones a moderator can give. */
    public static final String[] MANUAL = {"helper", "creative", "top_creator", "bug_hunter", "early_supporter", "translator", "champion", "legend"};
    private static final String[] BADGE_EN = {"First upload", "First post", "First comment", "First star", "Editor's Choice", "Helper", "Creative",
            "Top creator", "Bug hunter", "Early supporter", "Translator", "Champion", "Legend"};

    public static final String[] NOTIF_CODES = {"ar", "en", "es", "fr", "pt", "ru"};
    public static final String[] NOTIF_NAMES = {"العربية", "English", "Español", "Français", "Português", "Русский"};

    public static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    // ------------------------------------------------------------------ language
    /** Language of the phone, as one of the server's notification languages. */
    public static String deviceNotifLang() {
        String l = Locale.getDefault().getLanguage();
        for (String c : NOTIF_CODES) if (c.equals(l)) return c;
        return "en";
    }

    // ------------------------------------------------------------------ badges
    public static String badgeTitle(String key) {
        for (int i = 0; i < BADGES.length; i++) if (BADGES[i][0].equals(key)) return Tr.t("bd_" + key, BADGE_EN[i]);
        return key;
    }

    public static TextView badgeView(Context c, String key, int sizeDp) {
        String emoji = "🏅";
        int col = 0xFF90A4AE;
        for (String[] b : BADGES) if (b[0].equals(key)) { emoji = b[1]; col = Color.parseColor(b[2]); }
        TextView t = new TextView(c);
        t.setText(emoji);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(sizeDp * 0.46f);
        t.setIncludeFontPadding(false);
        t.setBackground(Ui.rounded((col & 0x00FFFFFF) | 0x33000000, Ui.dp(c, sizeDp), col, Ui.dp(c, 2)));
        t.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp)));
        return t;
    }

    /** Icon drawn in its own colours (no tint). */
    public static ImageView plainIcon(Context c, int res, int sizeDp) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp)));
        return i;
    }

    // ------------------------------------------------------------------ people
    public static void avatar(Context c, ImageView v, JsonObject user, int sizeDp) {
        String url = user == null ? "" : Suda3D.str(user, "avatar");
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp)));
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (!url.isEmpty()) {
            Glide.with(c).load(Suda3D.absolute(c, url)).transform(new CircleCrop()).into(v);
        } else {
            v.setImageResource(R.drawable.ic_s2_person);
            v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int p = Ui.dp(c, sizeDp / 5f);
            v.setPadding(p, p, p, p);
            v.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT_3));
            v.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(c, sizeDp)));
        }
    }

    /** Bold name that opens the profile; a small shield after it for moderators. */
    public static LinearLayout nameRow(Context c, JsonObject user, float sp) {
        LinearLayout r = Ui.row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = Ui.text(c, Suda3D.str(user, "name"), sp, Ui.TEXT);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(n, new LinearLayout.LayoutParams(-2, -2));
        if (Suda3D.bool(user, "mod")) {
            ImageView s = new ImageView(c);
            s.setImageResource(R.drawable.ic_s2_shield);
            s.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF42A5F5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, sp + 2), Ui.dp(c, sp + 2));
            lp.setMarginStart(Ui.dp(c, 4));
            r.addView(s, lp);
        }
        long id = (long) Suda3D.num(user, "id", 0);
        if (id > 0) r.setOnClickListener(v -> openProfile(c, id));
        return r;
    }

    public static void openProfile(Context c, long id) {
        Intent i = new Intent(c, ProfileActivity.class).putExtra("id", id);
        if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** Opens a store item by id; shows "not available" when it is gone or hidden. */
    public static void openProject(Context c, long id) {
        Suda3D.IO.execute(() -> {
            try {
                JsonObject m = MarketApi.get(c, id);
                Ui.ui(() -> {
                    Intent i = new Intent(c, MarketDetailActivity.class).putExtra("json", m.toString());
                    if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(i);
                });
            } catch (Suda3D.ApiException e) {
                Ui.ui(() -> Ui.toast(c, e.status == 404 ? Tr.t("sc_project_gone", "This project is not available.") : err(e)));
            } catch (Exception e) {
                Ui.ui(() -> Ui.toast(c, err(e)));
            }
        });
    }

    /** Runs {@code then} when signed in, else asks to sign in first. */
    public static void needLogin(Context c, Runnable then) {
        if (Suda3D.loggedIn(c)) then.run(); else Paywall.login(c, () -> { if (Suda3D.loggedIn(c)) then.run(); });
    }

    // ------------------------------------------------------------------ errors
    public static String err(Throwable t) {
        if (t instanceof Suda3D.ApiException) {
            Suda3D.ApiException e = (Suda3D.ApiException) t;
            String m = e.getMessage();
            String code = e.code;
            String def = Tr.t("err_" + code, null);
            if (def != null && !def.isEmpty()) {
                return def.replace("%d", String.valueOf(limitFor(code)));
            }
            return m;
        }
        return Suda3D.message(t);
    }

    private static int limitFor(String code) {
        Context c = null;
        switch (code) {
            case "post_limit": return cfgInt("daily_post_limit", 3);
            case "too_many_images": return cfgInt("max_images", 5);
            case "image_too_big": return cfgInt("max_image_kb", 1024);
            case "name_wait": return cfgInt("name_change_days", 1);
            case "pin_limit": return cfgInt("max_pinned", 10);
            default: return 0;
        }
    }

    // ------------------------------------------------------------------ server limits (cached)
    private static volatile JsonObject cfg;

    public static int cfgInt(String key, int def) { return cfg == null ? def : (int) Suda3D.num(cfg, key, def); }
    public static boolean cfgBool(String key) { return cfg != null && Suda3D.bool(cfg, key); }
    public static void setConfig(JsonObject o) { if (o != null && o.has("max_images")) cfg = o; }

    // ------------------------------------------------------------------ time
    public static String timeAgo(String iso) {
        long t = SudaUi.parseTime(iso);
        if (t <= 0) return "";
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 60) return Tr.t("sc_now", "now");
        if (s < 3600) return (s / 60) + Tr.t("sc_min", "m");
        if (s < 86400) return (s / 3600) + Tr.t("s3d_h", "h");
        if (s < 86400L * 30) return (s / 86400) + Tr.t("s3d_d", "d");
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date(t));
    }

    // ------------------------------------------------------------------ text with links and @project mentions
    /** Makes links shaded + clickable (with a confirmation) and @[project:N] a tappable "@Name". */
    public static void linkify(TextView tv, String body, JsonObject projects) {
        Context c = tv.getContext();
        String text = body == null ? "" : body;
        SpannableStringBuilder sb = new SpannableStringBuilder();
        Matcher m = MENTION.matcher(text);
        int last = 0;
        while (m.find()) {
            sb.append(text, last, m.start());
            final long id = Long.parseLong(m.group(1));
            JsonObject info = projects == null ? null : Suda3D.obj(projects, String.valueOf(id));
            boolean ok = info != null && Suda3D.bool(info, "ok");
            String name = info == null ? "" : Suda3D.str(info, "name");
            String shown = "@" + (name.isEmpty() ? Tr.t("sc_unavailable_name", "project") : name);
            int s = sb.length();
            sb.append(shown);
            sb.setSpan(new Tap(0xFF64B5F6, 0x2264B5F6, false) {
                @Override void tap() { if (ok) openProject(c, id); else Ui.toast(c, Tr.t("sc_project_gone", "This project is not available.")); }
            }, s, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            last = m.end();
        }
        sb.append(text, last, text.length());
        Matcher u = URL.matcher(sb);
        while (u.find()) {
            String url = trimUrl(u.group());
            int end = u.start() + url.length();
            sb.setSpan(new Tap(0xFF4FC3F7, 0x334FC3F7, true) {
                @Override void tap() { confirmLink(c, url); }
            }, u.start(), end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tv.setText(sb);
        tv.setMovementMethod(PassThrough.INSTANCE);
        tv.setClickable(false);
        tv.setLongClickable(false);
        tv.setFocusable(false);
    }

    private static String trimUrl(String u) {
        while (u.length() > 8 && ".,;:!?)]}»\"".indexOf(u.charAt(u.length() - 1)) >= 0) u = u.substring(0, u.length() - 1);
        return u;
    }

    public static void confirmLink(Context c, String url) {
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 22), Ui.dp(c, 6), Ui.dp(c, 22), 0);
        TextView t = Ui.text(c, url, 13, 0xFF4FC3F7);
        t.setTextIsSelectable(true);
        col.addView(t);
        new AlertDialog.Builder(c).setTitle(Tr.t("sc_open_link", "Do you want to go to the link?")).setView(col)
                .setPositiveButton(Tr.t("sc_go", "Go"), (d, w) -> Paywall.openUrl(c, url))
                .setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
    }

    /** Shaded, optionally underlined clickable piece of text. */
    private abstract static class Tap extends ClickableSpan {
        final int fg, bg;
        final boolean underline;
        Tap(int fg, int bg, boolean underline) { this.fg = fg; this.bg = bg; this.underline = underline; }
        abstract void tap();
        @Override public void onClick(android.view.View v) { tap(); }
        @Override public void updateDrawState(TextPaint ds) { ds.setColor(fg); ds.bgColor = bg; ds.setUnderlineText(underline); }
    }

    /** Only touches on a link are taken; everything else reaches the parent (so a post card stays tappable). */
    private static final class PassThrough extends LinkMovementMethod {
        static final PassThrough INSTANCE = new PassThrough();
        @Override public boolean onTouchEvent(TextView w, Spannable b, MotionEvent e) {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_DOWN) {
                int x = (int) e.getX() - w.getTotalPaddingLeft() + w.getScrollX();
                int y = (int) e.getY() - w.getTotalPaddingTop() + w.getScrollY();
                Layout l = w.getLayout();
                if (l == null) return false;
                int line = l.getLineForVertical(y);
                if (x < l.getLineLeft(line) || x > l.getLineRight(line)) return false;
                int off = l.getOffsetForHorizontal(line, x);
                ClickableSpan[] s = b.getSpans(off, off, ClickableSpan.class);
                if (s.length != 0) {
                    if (a == MotionEvent.ACTION_UP) s[0].onClick(w);
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ pictures
    /** Decodes, rotates and compresses a picked image to a JPEG of at most maxKb (smaller dimensions if needed). */
    public static File compress(Context c, Uri uri, int maxKb, int maxEdge, String name) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > maxEdge * 2) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) { bmp = BitmapFactory.decodeStream(in, null, o); }
        if (bmp == null) throw new java.io.IOException("decode");
        int rot = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            int ori = new androidx.exifinterface.media.ExifInterface(in).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1);
            rot = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
        } catch (Exception ignored) {}
        int edge = maxEdge;
        File out = new File(c.getCacheDir(), "social/" + name + ".jpg");
        out.getParentFile().mkdirs();
        for (int round = 0; round < 6; round++) {
            float sc = Math.min(1f, edge / (float) Math.max(bmp.getWidth(), bmp.getHeight()));
            Matrix mx = new Matrix();
            mx.postScale(sc, sc);
            if (rot != 0) mx.postRotate(rot);
            Bitmap b = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mx, true);
            for (int q = 88; q >= 45; q -= 11) {
                try (FileOutputStream fo = new FileOutputStream(out)) { b.compress(Bitmap.CompressFormat.JPEG, q, fo); }
                if (out.length() <= maxKb * 1000L * 0.96) { if (b != bmp) b.recycle(); bmp.recycle(); return out; }
            }
            if (b != bmp) b.recycle();
            edge = (int) (edge * 0.75f);
        }
        return out; // the server answers "too big" if even this is over
    }

    // ------------------------------------------------------------------ startup (account identity, notification language, push)
    private static boolean started;

    /** Once per run, when signed in: refresh identity + limits, ask the notification language of old accounts, register for push. */
    public static void startup(Activity a) {
        if (!Suda3D.loggedIn(a) || started) return;
        started = true;
        Suda3D.IO.execute(() -> {
            try {
                setConfig(CommunityApi.config(a));
                JsonObject me = CommunityApi.me(a);
                Suda3D.setIdentity(a, me);
                Ui.ui(() -> {
                    if (a.isFinishing()) return;
                    if (Suda3D.notifLang(a).isEmpty()) askNotifLang(a, null);
                    Push.register(a);
                });
            } catch (Exception e) { started = false; }
        });
    }

    /** Sign-in happened: allow the startup work to run again for the new account. */
    public static void resetStartup() { started = false; }

    /** Dialog to choose the language of push notifications (first entry of an old account, and the setting). */
    public static void askNotifLang(Activity a, Runnable done) {
        int sel = 1;
        String cur = Suda3D.notifLang(a).isEmpty() ? deviceNotifLang() : Suda3D.notifLang(a);
        for (int i = 0; i < NOTIF_CODES.length; i++) if (NOTIF_CODES[i].equals(cur)) sel = i;
        final int[] pick = {sel};
        new AlertDialog.Builder(a).setTitle(Tr.t("sc_notif_lang", "Notification language"))
                .setSingleChoiceItems(NOTIF_NAMES, sel, (d, w) -> pick[0] = w)
                .setCancelable(Suda3D.notifLang(a).isEmpty() ? false : true)
                .setPositiveButton(Tr.t("ok", "OK"), (d, w) -> Suda3D.IO.execute(() -> {
                    try {
                        JsonObject me = CommunityApi.updateMe(a, null, NOTIF_CODES[pick[0]]);
                        Suda3D.setIdentity(a, me);
                        Ui.ui(() -> { if (done != null) done.run(); });
                    } catch (Exception e) { Ui.ui(() -> Ui.toast(a, err(e))); }
                })).show();
    }

    // ------------------------------------------------------------------ notifications badge (read state lives on the device)
    private static String seenKey(Context c) { return "seen_" + Suda3D.userId(c); }
    public static long lastSeen(Context c) { return prefs(c).getLong(seenKey(c), 0); }
    public static void setSeen(Context c, long serverMs) { if (serverMs > lastSeen(c)) prefs(c).edit().putLong(seenKey(c), serverMs).apply(); }

    /** Number of notifications newer than this device's last visit (blocking). First run on a device starts from "now". */
    public static int unread(Context c) {
        try {
            if (!Suda3D.loggedIn(c)) return 0;
            long since = lastSeen(c);
            JsonObject r = CommunityApi.notifCount(c, since);
            long now = (long) Suda3D.num(r, "now", 0);
            if (since == 0) { setSeen(c, now); return 0; }
            return (int) Suda3D.num(r, "count", 0);
        } catch (Exception e) { return 0; }
    }
}
