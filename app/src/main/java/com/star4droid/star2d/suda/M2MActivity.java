package com.star4droid.star2d.suda;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.suda.ws.Graph;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * "M2M Motion": rig a model and give it animations with Mesh2Motion running locally in a WebView (free, nothing is
 * sent to a server). The page (app assets m2m/, built by tools/m2m/build_m2m.sh) is served from
 * https://appassets.androidplatform.net/ ; rigs downloaded by {@link M2MAssets} at /m2mdata/ ; the model handed in by
 * the app at /m2minput/. Exports come back through the "M2MHost" JS bridge in base64 chunks.
 *
 * Extras: "model" (file to load, optional), "project" (project folder, optional), "result" (workspace mode: write the
 * result there and finish with RESULT_OK + "path").
 */
public class M2MActivity extends Activity implements OutputView.SafHost {
    public static final String HOST = "appassets.androidplatform.net";
    private static final String ORIGIN = "https://" + HOST;
    private static final int REQ_FILE = 61, REQ_SAVE = 62;

    private WebView web;
    private File model, resultTarget, pendingSave;
    private String project;
    private ValueCallback<Uri[]> fileCallback;
    private final Map<String, OutputStream> outs = new HashMap<>();
    private final Map<String, File> outFiles = new HashMap<>();
    private int seq;

    public static Intent intent(Context c, File model, String project) {
        Intent i = new Intent(c, M2MActivity.class);
        if (model != null) i.putExtra("model", model.getAbsolutePath());
        if (project != null) i.putExtra("project", project);
        return i;
    }

    public static void open(Activity a, File model, String project) { a.startActivity(intent(a, model, project)); }

    /** Model files the app hands to Mesh2Motion (its own picker also takes a GLTF+BIN .zip). */
    public static boolean accepts(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".glb") || n.endsWith(".fbx");
    }

    public static String credits() {
        return Tr.t("m2m_credits", "Powered by Mesh2Motion — MIT License © Scott Petrovic; animations CC0");
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String m = getIntent().getStringExtra("model");
        model = m == null ? null : new File(m);
        project = getIntent().getStringExtra("project");
        String r = getIntent().getStringExtra("result");
        resultTarget = r == null ? null : new File(r);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(SudaUi.topBar(this, Tr.t("m2m_title", "M2M Motion"),
                Ui.iconButton(this, R.drawable.ic_s2_info, Tr.t("m2m_licenses", "Licenses"), v -> licenses(this))), Ui.matchWrap());
        web = new WebView(this);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView cr = Ui.text(this, credits(), 11.5f, Ui.TEXT_3);
        cr.setPadding(Ui.dp(this, 12), Ui.dp(this, 5), Ui.dp(this, 12), Ui.dp(this, 5));
        cr.setBackgroundColor(Ui.SURFACE);
        cr.setOnClickListener(v -> licenses(this));
        root.addView(cr, Ui.matchWrap());
        SudaUi.setup(this, root);
        if (b == null && model != null && glbHasSkeleton(model))
            new android.app.AlertDialog.Builder(this)
                    .setTitle(Tr.t("m2m_rigged_title", "This model already has a skeleton"))
                    .setMessage(Tr.t("m2m_rigged_msg", "M2M Motion builds a new skeleton and replaces the one in this model. To keep its own skeleton, use it directly (Preview or Animation) instead."))
                    .setPositiveButton(Tr.t("m2m_rigged_continue", "Continue"), null)
                    .setNegativeButton(Tr.t("cancel", "Cancel"), (d, w) -> finish())
                    .setOnCancelListener(d -> finish())
                    .show();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true); // files picked in the page's own file inputs (content:// from the picker)
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.addJavascriptInterface(new Bridge(), "M2MHost");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) { return serve(req.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return !HOST.equals(req.getUrl().getHost()); // no external pages inside the tool
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try { startActivityForResult(i, REQ_FILE); } catch (Exception e) { fileCallback = null; return false; }
                return true;
            }
        });
        if (b != null) web.restoreState(b);
        else web.loadUrl(ORIGIN + "/create.html");
    }

    /** True when a GLB declares skins (a rigged model); FBX / unreadable files count as unknown (false). */
    static boolean glbHasSkeleton(File f) {
        try (java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "r")) {
            byte[] h = new byte[20];
            if (r.length() < 20) return false;
            r.readFully(h);
            if (h[0] != 'g' || h[1] != 'l' || h[2] != 'T' || h[3] != 'F') return false;
            int len = (h[12] & 0xFF) | (h[13] & 0xFF) << 8 | (h[14] & 0xFF) << 16 | (h[15] & 0xFF) << 24;
            if (len <= 0 || len > 8_000_000) return false;
            byte[] j = new byte[len];
            r.readFully(j);
            return new String(j, java.nio.charset.StandardCharsets.UTF_8).contains("\"skins\"");
        } catch (Exception e) { return false; }
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onPause() { web.onPause(); super.onPause(); }

    @Override
    protected void onDestroy() {
        for (OutputStream o : outs.values()) try { o.close(); } catch (Exception ignored) {}
        web.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Dialogs.confirm(this, Tr.t("m2m_title", "M2M Motion"), Tr.t("m2m_leave", "Leave M2M Motion? Work that was not exported is lost."),
                Tr.t("m2m_leave_btn", "Leave"), this::finish);
    }

    // ------------------------------------------------------------ local web server

    /** /m2mdata/** → files/m2m, /m2minput/* → the model, everything else → assets/m2m. Other hosts are blocked. */
    WebResourceResponse serve(Uri u) {
        if (!HOST.equals(u.getHost())) return blocked();
        String path = u.getPath() == null ? "/" : u.getPath();
        if (path.contains("..") || path.contains("\\")) return notFound();
        try {
            if (path.startsWith("/m2mdata/")) {
                File base = M2MAssets.dir(this), f = new File(base, path.substring("/m2mdata/".length()));
                if (!f.getCanonicalPath().startsWith(base.getCanonicalPath() + File.separator) || !f.isFile() || f.getName().startsWith("."))
                    return notFound();
                return ok(f.getName(), new FileInputStream(f));
            }
            if (path.startsWith("/m2minput/")) {
                if (model == null || !model.isFile()) return notFound();
                return ok(model.getName(), new FileInputStream(model));
            }
            String rel = path.equals("/") ? "create.html" : path.substring(1);
            return ok(rel, getAssets().open("m2m/" + rel));
        } catch (Exception e) {
            return notFound();
        }
    }

    private static WebResourceResponse ok(String name, InputStream in) {
        WebResourceResponse r = new WebResourceResponse(mime(name), mime(name).startsWith("text/") ? "utf-8" : null, in);
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", ORIGIN);
        h.put("Cache-Control", "no-cache");
        r.setResponseHeaders(h);
        return r;
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
    }

    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", null, new ByteArrayInputStream(new byte[0]));
    }

    static String mime(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".js") || n.endsWith(".mjs")) return "text/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".glb")) return "model/gltf-binary";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".wasm")) return "application/wasm";
        return "application/octet-stream";
    }

    // ------------------------------------------------------------ JS bridge (called on the WebView's JavaBridge thread)

    final class Bridge {
        @JavascriptInterface
        public void ready() {
            if (model == null || !model.isFile()) return;
            String name = model.getName().replace("'", "").replace("\\", "");
            String url = ORIGIN + "/m2minput/" + Uri.encode(name);
            runOnUiThread(() -> web.evaluateJavascript("window.m2m && window.m2m.loadModel('" + url + "','" + name + "')", null));
        }

        @JavascriptInterface
        public void ensureRig(String type, String callbackId) {
            String cb = callbackId == null ? "" : callbackId.replaceAll("[^A-Za-z0-9_]", "");
            runOnUiThread(() -> M2MAssets.ensure(M2MActivity.this, type, ok ->
                    web.evaluateJavascript("window.m2m && window.m2m.rigCallback('" + cb + "'," + ok + ")", null)));
        }

        @JavascriptInterface
        public String beginFile(String name, double size) {
            synchronized (outs) {
                String id = "f" + (++seq);
                try {
                    File dir = new File(getCacheDir(), "m2m_out");
                    dir.mkdirs();
                    File f = new File(dir, id + "_" + resultName(name));
                    outs.put(id, new FileOutputStream(f));
                    outFiles.put(id, f);
                } catch (Exception e) {
                    return "";
                }
                return id;
            }
        }

        @JavascriptInterface
        public boolean appendChunk(String id, String base64) {
            OutputStream o;
            synchronized (outs) { o = outs.get(id); }
            if (o == null) return false;
            try {
                o.write(Base64.decode(base64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void endFile(String id) {
            File f = close(id);
            if (f != null) runOnUiThread(() -> onResult(f));
        }

        @JavascriptInterface
        public void failFile(String id, String message) {
            File f = close(id);
            if (f != null) f.delete();
            runOnUiThread(() -> Ui.toast(M2MActivity.this, Tr.t("m2m_export_failed", "Export failed") + ": " + message));
        }

        private File close(String id) {
            synchronized (outs) {
                OutputStream o = outs.remove(id);
                File f = outFiles.remove(id);
                try { if (o != null) o.close(); } catch (Exception ignored) {}
                return o == null ? null : f;
            }
        }
    }

    /** "<model>_m2m.<ext of the export>" (Mesh2Motion exports GLB or FBX). */
    String resultName(String exported) {
        String ext = exported != null && exported.toLowerCase(Locale.ROOT).endsWith(".fbx") ? ".fbx" : ".glb";
        String base = model != null ? model.getName() : "model";
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base.replaceAll("[^A-Za-z0-9_\\-]", "_") + "_m2m" + ext;
    }

    // ------------------------------------------------------------ result

    private void onResult(File tmp) {
        String name = tmp.getName().substring(tmp.getName().indexOf('_') + 1);
        File f;
        try {
            if (resultTarget != null) { // same name, extension of what was exported (GLB or FBX)
                String t = resultTarget.getName(), ext = name.substring(name.lastIndexOf('.'));
                f = new File(resultTarget.getParentFile(), (t.lastIndexOf('.') > 0 ? t.substring(0, t.lastIndexOf('.')) : t) + ext);
            } else {
                File dir = Store.ext(this, "m2m");
                f = new File(dir, name);
                int dot = name.lastIndexOf('.');
                for (int i = 2; f.exists(); i++) f = new File(dir, name.substring(0, dot) + "_" + i + name.substring(dot));
            }
            Store.copy(tmp, f);
            tmp.delete();
        } catch (Exception e) {
            Ui.toast(this, Suda3D.message(e));
            return;
        }
        if (resultTarget != null) { // workspace node: hand the result back
            setResult(RESULT_OK, new Intent().putExtra("path", f.getAbsolutePath()));
            Ui.toast(this, Tr.t("m2m_done_ws", "Rigged model sent to the workspace node"));
            finish();
            return;
        }
        File res = f;
        String[] items = {Tr.t("m2m_preview", "Preview in the 3D viewer"), Tr.t("s3d_save_project", "Save to project"),
                Tr.t("s3d_save_phone", "Save to phone…"), Tr.t("s3d_save_downloads", "Save to Downloads"), Tr.t("m2m_save_ws", "Add to an AI workspace")};
        Dialogs.choose(this, Tr.t("m2m_result", "Rigged model ready") + ": " + res.getName(), items, i -> {
            switch (i) {
                case 0: ModelViewerActivity.open(this, res, "", "", res.length(), project); break;
                case 1: Store.saveToProject(this, res, project, saved -> ModelViewerActivity.open(this, saved, "", "", saved.length(), project)); break;
                case 2: createDocument(res); break;
                case 3: Store.saveToDownloads(this, res, null); break;
                default: saveToWorkspace(this, res); break;
            }
        });
    }

    /** Adds the file as a "3D Upload" node of a workspace the user picks. */
    static void saveToWorkspace(Activity a, File f) {
        File[] list = Store.workspacesDir(a).listFiles(File::isDirectory);
        ArrayList<File> all = new ArrayList<>();
        if (list != null) for (File d : list) if (new File(d, "graph.json").exists()) all.add(d);
        if (all.isEmpty()) { Ui.toast(a, Tr.t("m2m_no_ws", "No AI workspace yet: create one from the Plan tab")); return; }
        java.util.Collections.sort(all, (x, y) -> Long.compare(new File(y, "graph.json").lastModified(), new File(x, "graph.json").lastModified()));
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) {
            Graph g = Graph.parse(FileUtil.readFile(new File(all.get(i), "graph.json").getPath()));
            names[i] = g.name.isEmpty() ? all.get(i).getName() : g.name;
        }
        Dialogs.choose(a, Tr.t("m2m_pick_ws", "Which workspace?"), names, i -> {
            File d = all.get(i), gf = new File(d, "graph.json");
            try {
                Graph g = Graph.parse(FileUtil.readFile(gf.getPath()));
                float x = 0, y = 0;
                for (Graph.Node n : g.nodes) { x = Math.min(x, n.x); y = Math.max(y, n.y + 260); }
                Graph.Node n = g.add("upload_3d", x, y);
                File to = new File(d, "files/inputs/" + n.id + "/" + f.getName());
                Store.copy(f, to);
                n.file = to.getAbsolutePath();
                FileUtil.writeFile(gf.getPath(), g.toJson().toString());
                Ui.toast(a, Tr.t("s3d_saved", "Saved") + ": " + names[i]);
            } catch (Exception e) {
                Ui.toast(a, Suda3D.message(e));
            }
        });
    }

    /** Credits + full MIT text + CC0 note. */
    public static void licenses(Context c) {
        String mit;
        try (InputStream in = c.getAssets().open("m2m/LICENSE-MIT.md")) {
            mit = new String(readAll(in), "UTF-8");
        } catch (Exception e) {
            mit = MIT;
        }
        Dialogs.longText(c, Tr.t("m2m_licenses", "Licenses"), credits() + "\n\n"
                + "Mesh2Motion — https://github.com/scottpetrovic/mesh2motion-app\n\n" + mit.trim() + "\n\n"
                + Tr.t("m2m_cc0_note", "Rigs, models and animations: CC0 1.0 Universal (public domain dedication) — free to use, change and share, also commercially.")
                + "\n\n" + Tr.t("m2m_local_note", "M2M Motion runs on this phone; your model is not uploaded."));
    }

    static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    static final String MIT = "MIT License\n\nCopyright (c) 2025 Scott Petrovic\n\n"
            + "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\n"
            + "The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\n"
            + "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.";

    // ------------------------------------------------------------ activity results

    @Override
    public void createDocument(File f) {
        pendingSave = f;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(Suda3D.mime(f.getName())).putExtra(Intent.EXTRA_TITLE, f.getName());
        try { startActivityForResult(i, REQ_SAVE); } catch (Exception e) { Ui.toast(this, Suda3D.message(e)); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE) {
            Uri[] uris = null;
            if (res == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    uris = new Uri[data.getClipData().getItemCount()];
                    for (int i = 0; i < uris.length; i++) uris[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) uris = new Uri[]{data.getData()};
            }
            if (fileCallback != null) fileCallback.onReceiveValue(uris);
            fileCallback = null;
        } else if (req == REQ_SAVE && res == RESULT_OK && data != null && data.getData() != null && pendingSave != null) {
            Store.saveToUri(this, pendingSave, data.getData(), null);
        }
    }
}
