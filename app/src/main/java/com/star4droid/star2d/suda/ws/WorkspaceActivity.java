package com.star4droid.star2d.suda.ws;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.suda.Downloads;
import com.star4droid.star2d.suda.Jobs;
import com.star4droid.star2d.suda.ModelViewerActivity;
import com.star4droid.star2d.suda.Ops;
import com.star4droid.star2d.suda.OutputView;
import com.star4droid.star2d.suda.ParamForm;
import com.star4droid.star2d.suda.Paywall;
import com.star4droid.star2d.suda.QuickGen;
import com.star4droid.star2d.suda.Store;
import com.star4droid.star2d.suda.Suda3D;
import com.star4droid.star2d.suda.SudaUi;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Node-graph editor of one AI workspace (workspaces/<id>/graph.json + files/). Runs nodes as SUDA3D jobs (price first,
 * whole upstream chain when needed), tracks them with {@link Jobs}, downloads results into files/ (resumable), and
 * keeps undo / redo snapshots of the graph.
 */
public class WorkspaceActivity extends Activity implements NodeGraphView.Host, Jobs.Listener, Downloads.Listener, OutputView.SafHost, NodePalette.Host {
    private static final int REQ_IMAGE = 51, REQ_MODEL = 52, REQ_SAVE = 53, REQ_M2M = 54;
    private String m2mNode;
    private File dir, files;
    private Graph graph;
    private NodeGraphView view;
    private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private String current;
    private LinearLayout selBar;
    private TextView title;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable saver = this::saveNow;
    private Graph.Node pickFor;
    private File pendingSave;
    /**
     * Chain runs, several at a time: key = the node whose job is running now, value = the nodes to run after it.
     * Kept across activity re-creation (rotation) via the saved instance state.
     */
    private final Map<String, ArrayDeque<String>> chains = new HashMap<>();
    private NodePalette palette;
    private boolean dead;
    private final Map<String, Bitmap> thumbs = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dir = new File(getIntent().getStringExtra("dir"));
        files = new File(dir, "files");
        files.mkdirs();
        File gf = new File(dir, "graph.json");
        graph = gf.exists() ? Graph.parse(FileUtil.readFile(gf.getPath())) : new Graph();
        current = graph.toJson().toString();

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout bar = SudaUi.topBar(this, graph.name.isEmpty() ? Tr.t("s3d_workspace", "AI Workspace") : graph.name,
                SudaUi.balanceChip(this),
                Ui.iconButton(this, R.drawable.ic_s2_undo, Tr.t("undo", "Undo"), v -> undo()),
                Ui.iconButton(this, R.drawable.ic_s2_redo, Tr.t("redo", "Redo"), v -> redo()),
                Ui.iconButton(this, R.drawable.ic_s2_add, Tr.t("s3d_add_node", "Add node"), v -> palette.setOpen(!palette.isOpen())),
                Ui.iconButton(this, R.drawable.ic_s2_more, Tr.t("more", "More"), v -> moreMenu()));
        title = (TextView) bar.getChildAt(1);
        title.setOnClickListener(v -> Dialogs.input(this, Tr.t("rename", "Rename"), null, graph.name, null, n -> {
            graph.name = n.trim();
            title.setText(graph.name);
            changed(false);
        }));
        Ui.fitIconRow(this, bar, 90);
        root.addView(bar, Ui.matchWrap());
        FrameLayout stage = new FrameLayout(this);
        view = new NodeGraphView(this);
        stage.addView(view, Ui.match());
        selBar = Ui.row(this);
        selBar.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(this, 22), Ui.BORDER, 1));
        selBar.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
        selBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        slp.topMargin = Ui.dp(this, 10);
        stage.addView(selBar, slp);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        palette = new NodePalette(this, this);
        root.addView(palette, Ui.matchWrap());
        SudaUi.setup(this, root);
        view.setGraph(graph, this);
        NodePalette.acceptDrops(view, this);
        if (b != null && b.containsKey("zoom")) view.setViewport(b.getFloat("zoom"), b.getFloat("panX"), b.getFloat("panY"));
        else view.post(view::frameAll);
        if (b != null) restoreChains(b.getString("chains", ""));
        // resume jobs that were running when the workspace was closed (each node polls its own job)
        Jobs.init(this);
        for (Graph.Node n : graph.nodes) {
            if (!NodeGraphView.busy(n)) continue;
            if (!n.jobId.isEmpty()) Jobs.resume(this, n.jobId, n.op(), n.def().title(), tag(n));
            else adoptOrReset(n, false);
        }
        if (Suda3D.loggedIn(this)) Suda3D.IO.execute(() -> { try { Suda3D.me(this); } catch (Exception ignored) {} });
        Ops.load(this, () -> view.invalidate());
    }

    String tag(Graph.Node n) { return "ws:" + dir.getName() + ":" + n.id; }

    @Override protected void onResume() {
        super.onResume();
        Jobs.listen(this);
        Downloads.listen(this);
        for (Jobs.Job j : Jobs.all()) onJob(j);
    }

    @Override protected void onPause() {
        super.onPause();
        Jobs.unlisten(this);
        Downloads.unlisten(this);
        saveNow();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putFloat("zoom", view.zoom());
        out.putFloat("panX", view.panX());
        out.putFloat("panY", view.panY());
        JsonObject o = new JsonObject();
        for (Map.Entry<String, ArrayDeque<String>> e : chains.entrySet()) {
            JsonArray a = new JsonArray();
            for (String id : e.getValue()) a.add(id);
            o.add(e.getKey(), a);
        }
        out.putString("chains", o.toString());
    }

    @Override protected void onDestroy() {
        // a re-created activity owns graph.json now: late callbacks of this one must not overwrite it
        saveNow();
        dead = true;
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void restoreChains(String json) {
        try {
            if (json == null || json.isEmpty()) return;
            JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                ArrayDeque<String> q = new ArrayDeque<>();
                for (JsonElement x : e.getValue().getAsJsonArray()) q.add(x.getAsString());
                chains.put(e.getKey(), q);
            }
        } catch (Exception ignored) {}
    }

    /**
     * A node saved as queued without a job id (the job was being created when the activity was re-created): take the
     * job created for it since, or set it back to Ready when none shows up.
     */
    private void adoptOrReset(Graph.Node n, boolean reset) {
        Jobs.Job best = null;
        for (Jobs.Job j : Jobs.all())
            if (tag(n).equals(j.tag) && j.created >= n.queuedAt - 5000 && (best == null || j.created > best.created)) best = j;
        if (best != null) {
            n.jobId = best.id;
            if (best.credits >= 0) n.credits = best.credits;
            onJob(best);
            if (!best.finished()) Jobs.refresh(best.id);
            return;
        }
        if (reset || System.currentTimeMillis() - n.queuedAt > 120_000L) {
            n.state = Graph.IDLE;
            n.progress = 0;
            changed(false);
            return;
        }
        String id = n.id;
        h.postDelayed(() -> {
            Graph.Node x = graph.node(id);
            if (x != null && x.jobId.isEmpty() && Graph.QUEUED.equals(x.state)) adoptOrReset(x, true);
        }, 45_000L);
    }

    // ------------------------------------------------------------ persistence, undo

    @Override
    public void changed(boolean structural) {
        if (dead) return;
        String now = graph.toJson().toString();
        if (!now.equals(current)) {
            undo.push(current);
            while (undo.size() > 60) undo.removeLast();
            redo.clear();
            current = now;
        }
        view.invalidate();
        h.removeCallbacks(saver);
        h.postDelayed(saver, 600);
    }

    private void saveNow() {
        h.removeCallbacks(saver);
        if (dead) return;
        FileUtil.writeFile(new File(dir, "graph.json").getPath(), graph.toJson().toString());
    }

    private void restore(String json) {
        Graph g = Graph.parse(json);
        graph.nodes.clear();
        graph.nodes.addAll(g.nodes);
        graph.links.clear();
        graph.links.addAll(g.links);
        graph.name = g.name;
        current = json;
        view.selected.clear();
        selectionChanged();
        view.invalidate();
        h.postDelayed(saver, 300);
    }

    private void undo() { if (!undo.isEmpty()) { redo.push(current); restore(undo.pop()); } }
    private void redo() { if (!redo.isEmpty()) { undo.push(current); restore(redo.pop()); } }

    // ------------------------------------------------------------ menus

    private void moreMenu() {
        String[] items = {Tr.t("s3d_frame_all", "Show all nodes"), Tr.t("s3d_running", "Running generations"), Tr.t("s3d_history", "History"), Tr.t("s3d_buy", "Buy credits")};
        Dialogs.choose(this, Tr.t("more", "More"), items, i -> {
            if (i == 0) view.frameAll();
            else if (i == 1) QuickGen.showRunning(this);
            else if (i == 2) startActivity(new Intent(this, com.star4droid.star2d.suda.HistoryActivity.class));
            else Paywall.offer(this);
        });
    }

    /** Node menu; with from/port only nodes that accept that type (and the new node gets connected). */
    private void addMenu(Graph.Node from, Graph.Port port, float wx, float wy) {
        List<Graph.Def> defs = new ArrayList<>();
        for (Graph.Def d : Graph.DEFS) {
            if (Graph.hidden(d)) continue;
            if (port == null) { defs.add(d); continue; }
            for (Graph.Port p : d.in) if (Graph.compatible(port.type, p.type)) { defs.add(d); break; }
        }
        if (defs.isEmpty()) { changed(true); return; }
        String[] names = new String[defs.size()];
        for (int i = 0; i < names.length; i++) {
            Ops.Op op = defs.get(i).op == null ? null : Ops.get(defs.get(i).op);
            names[i] = defs.get(i).title() + (op != null && op.baseCredits >= 0 ? "  ·  " + Suda3D.fmtCredits(op.baseCredits) + "+" : "");
        }
        AlertDialog d = new AlertDialog.Builder(this).setTitle(port == null ? Tr.t("s3d_add_node", "Add node") : Graph.portLabel(port.type) + " →")
                .setItems(names, (x, i) -> {
                    Graph.Def def = defs.get(i);
                    PointF c = Float.isNaN(wx) ? view.center() : new PointF(wx, wy - 30);
                    Graph.Node n = graph.add(def.type, c.x, c.y);
                    if (from != null) for (Graph.Port p : def.in) if (Graph.compatible(port.type, p.type)) { graph.connect(from.id, port.id, n.id, p.id); break; }
                    changed(true);
                }).setNegativeButton(Tr.t("cancel", "Cancel"), null).create();
        d.setOnCancelListener(x -> changed(true));
        d.show();
    }

    @Override
    public void addNode(Graph.Def def, float wx, float wy) {
        PointF c = Float.isNaN(wx) ? view.center() : new PointF(wx, wy);
        if (Float.isNaN(wx)) { // stagger nodes added at the same centre
            for (Graph.Node o : graph.nodes) if (Math.abs(o.x - c.x) < 8 && Math.abs(o.y - c.y) < 8) { c.x += 24; c.y += 24; }
        }
        graph.add(def.type, c.x, c.y);
        changed(true);
    }

    @Override
    public void selectionChanged() {
        selBar.removeAllViews();
        if (view.selected.isEmpty()) { selBar.setVisibility(View.GONE); return; }
        selBar.setVisibility(View.VISIBLE);
        TextView cnt = Ui.text(this, String.valueOf(view.selected.size()), 14, Ui.TEXT);
        cnt.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 6), 0);
        selBar.addView(cnt);
        selBar.addView(Ui.iconButton(this, R.drawable.ic_s2_copy, Tr.t("s3d_duplicate", "Duplicate"), v -> {
            List<Graph.Node> copies = new ArrayList<>();
            for (String id : view.selected) {
                Graph.Node n = graph.node(id);
                if (n == null) continue;
                Graph.Node c = graph.add(n.type, n.x + 30, n.y + 30);
                c.params.putAll(n.params);
                c.file = n.file;
                copies.add(c);
            }
            view.selected.clear();
            for (Graph.Node c : copies) view.selected.add(c.id);
            changed(true);
            selectionChanged();
        }));
        selBar.addView(Ui.iconButton(this, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), v -> {
            for (String id : view.selected) { Graph.Node n = graph.node(id); if (n != null) graph.remove(n); }
            view.selected.clear();
            changed(true);
            selectionChanged();
            Ui.toast(this, Tr.t("s3d_deleted_hint", "Deleted. Results stay in the History."));
        }));
        selBar.addView(Ui.iconButton(this, R.drawable.ic_s2_close, Tr.t("cancel", "Cancel"), v -> { view.selected.clear(); selectionChanged(); view.invalidate(); }));
    }

    @Override
    public void dropWire(Graph.Node from, Graph.Port port, float wx, float wy) { addMenu(from, port, wx, wy); }

    @Override
    public void deleteLink(Graph.Link l) {
        graph.links.remove(l);
        changed(true);
        Ui.toast(this, Tr.t("s3d_wire_removed", "Connection removed (Undo brings it back)"));
    }

    // ------------------------------------------------------------ parameters sheet

    private Dialog sheet(String titleText, View content) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout col = Ui.column(this);
        col.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 18)));
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 14));
        View grip = new View(this);
        grip.setBackground(Ui.rounded(Ui.BORDER, Ui.dp(this, 3)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 5));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(grip, glp);
        TextView t = Ui.title(this, titleText);
        t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        col.addView(t);
        // the sheet is as tall as its content (up to ~70% of the screen) so short forms leave no empty area,
        // and it shrinks with the keyboard instead of being pushed off screen
        final int capH = (int) (getResources().getDisplayMetrics().heightPixels * (Ui.isLandscape(this) ? 0.62f : 0.62f));
        ScrollView sv = new ScrollView(this) {
            @Override protected void onMeasure(int wSpec, int hSpec) {
                super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(capH, MeasureSpec.AT_MOST));
            }
        };
        sv.setFillViewport(false);
        sv.addView(content);
        col.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        d.setContentView(col);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            w.setGravity(Gravity.BOTTOM);
            int width = Ui.isLandscape(this) ? Math.min(getResources().getDisplayMetrics().widthPixels, Ui.dp(this, 560)) : ViewGroup.LayoutParams.MATCH_PARENT;
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return d;
    }

    @Override
    public void editParams(Graph.Node n) {
        LinearLayout col = Ui.column(this);
        Dialog[] dlg = new Dialog[1];
        Graph.Def def = n.def();
        if ("text".equals(n.type)) {
            EditText e = Ui.input(this, n.params.containsKey("text") ? n.params.get("text") : "", Tr.t("s3d_prompt_hint", "Describe what to generate…"));
            e.setSingleLine(false);
            e.setMinLines(4);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            col.addView(e, Ui.matchWrap());
            col.addView(Ui.button(this, R.drawable.ic_s2_check, Tr.t("ok", "OK"), true, v -> {
                n.params.put("text", e.getText().toString());
                markStale(n);
                changed(false);
                dlg[0].dismiss();
            }), Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 0));
        } else if (def.onDevice) {
            col.addView(Ui.text(this, Tr.t("m2m_node_hint", "Rig the connected model and add animations with Mesh2Motion on this phone. Free: no credits, nothing is uploaded."), 14, Ui.TEXT_2));
            if (!n.file.isEmpty()) col.addView(Ui.text(this, new File(n.file).getName(), 13, Ui.TEXT_3));
            col.addView(Ui.button(this, R.drawable.ic_s2_play, Tr.t("m2m_open", "Open M2M Motion"), true, v -> { dlg[0].dismiss(); generate(n); }),
                    Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 0));
            if (!n.file.isEmpty() && new File(n.file).exists()) col.addView(Ui.button(this, R.drawable.ic_s2_save, Tr.t("s3d_save", "Save"), false,
                    v -> { dlg[0].dismiss(); OutputView.saveMenu(this, new File(n.file), null, null); }), Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 0));
        } else if (def.local) {
            col.addView(Ui.text(this, "select_image".equals(n.type) || "upload_3d".equals(n.type)
                    ? (n.file.isEmpty() ? Tr.t("s3d_no_file", "No file") : new File(n.file).getName())
                    : Tr.t("s3d_preview_hint", "Connect a model to preview it and save it to a project, the phone or Downloads."), 14, Ui.TEXT_2));
            if (!"preview".equals(n.type)) col.addView(Ui.button(this, R.drawable.ic_s2_folder_open, Tr.t("s3d_replace", "Replace"), true, v -> { dlg[0].dismiss(); replaceFile(n); }),
                    Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 0));
        } else {
            // mode switch (Generate Image: by connection; Refine / Stylize: choice)
            if ("refine".equals(n.type)) {
                String[] modes = {"refine", "stylize"};
                TextView m = Ui.text(this, Tr.t("s3d_mode", "Mode") + ": " + SudaUi.choiceLabel(n.params.getOrDefault("_mode", "refine")) + " ▾", 14, Ui.ACCENT);
                m.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
                m.setOnClickListener(v -> Dialogs.choose(this, Tr.t("s3d_mode", "Mode"), new String[]{SudaUi.choiceLabel("refine"), SudaUi.choiceLabel("stylize")}, i -> {
                    n.params.put("_mode", modes[i]);
                    changed(false);
                    dlg[0].dismiss();
                    editParams(n);
                }));
                col.addView(m);
            }
            if ("gen_image".equals(n.type)) n.params.put("_mode", graph.into(n.id, "image") != null ? "image_to_image" : "text_to_image");
            Ops.Op op = Ops.get(n.op());
            if (op != null) {
                Ops.Param pp = op.param("prompt");
                if (pp != null && graph.into(n.id, "prompt") == null) {
                    EditText e = Ui.input(this, n.params.getOrDefault("prompt", ""), Tr.t("s3d_prompt_hint", "Describe what to generate…"));
                    e.setSingleLine(false);
                    e.setMinLines(2);
                    e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    e.addTextChangedListener(new android.text.TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                        @Override public void afterTextChanged(android.text.Editable s) { n.params.put("prompt", s.toString()); }
                    });
                    col.addView(e, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 6));
                }
                LinearLayout main = Ui.column(this), more = Ui.column(this);
                TextView price = Ui.text(this, "", 14, SudaUi.ORANGE);
                Runnable q = () -> quoteInto(n, price);
                ParamForm.fill(this, op, n.params, main, more, () -> { markStale(n); changed(false); q.run(); });
                col.addView(main, Ui.matchWrap());
                if (more.getChildCount() > 0) {
                    TextView mt = Ui.text(this, "▸ " + Tr.t("s3d_more", "More options"), 13.5f, Ui.ACCENT);
                    mt.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
                    more.setVisibility(View.GONE);
                    mt.setOnClickListener(v -> {
                        boolean open = more.getVisibility() != View.VISIBLE;
                        more.setVisibility(open ? View.VISIBLE : View.GONE);
                        mt.setText((open ? "▾ " : "▸ ") + Tr.t("s3d_more", "More options"));
                    });
                    col.addView(mt);
                    col.addView(more, Ui.matchWrap());
                }
                price.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
                col.addView(price);
                q.run();
            }
            if ("rig".equals(n.type)) {
                col.addView(Ui.text(this, Tr.t("s3d_analyze_hint", "Analyze checks whether the model can be rigged and picks the matching skeleton."), 13, Ui.TEXT_3),
                        Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 0));
                col.addView(Ui.button(this, R.drawable.ic_s2_search, Tr.t("s3d_analyze", "Analyze"), false, v -> { dlg[0].dismiss(); analyze(n); }),
                        Ui.margins(Ui.matchWrap(), this, 0, 6, 0, 0));
            }
            col.addView(Ui.button(this, R.drawable.ic_s2_play, Tr.t("s3d_generate", "Generate"), true, v -> { dlg[0].dismiss(); generate(n); }),
                    Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 0));
            if (!n.outputs.isEmpty() || !n.jobId.isEmpty()) col.addView(Ui.button(this, R.drawable.ic_s2_download, Tr.t("s3d_results", "Results"), false, v -> { dlg[0].dismiss(); showOutputs(n); }),
                    Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 0));
        }
        col.addView(Ui.button(this, R.drawable.ic_s2_delete, Tr.t("s3d_delete_node", "Delete node"), false, v -> {
            graph.remove(n);
            changed(true);
            dlg[0].dismiss();
        }), Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 0));
        dlg[0] = sheet(def.title(), col);
        dlg[0].setOnDismissListener(x -> changed(false));
        dlg[0].show();
    }

    // ------------------------------------------------------------ rig analysis

    /** Rig node → Analyze: runs "rig check" on the connected model; the result sets the Skeleton parameter. */
    private void analyze(Graph.Node n) {
        Graph.Link l = graph.into(n.id, "model");
        Graph.Node u = l == null ? null : graph.node(l.fromNode);
        if (u == null) { Ui.toast(this, Tr.t("s3d_connect_model_first", "Connect a model first")); return; }
        boolean ready = u.def().local || u.def().onDevice || (Graph.DONE.equals(u.state) && !u.outputs.isEmpty());
        if (!ready) { Ui.toast(this, Tr.t("s3d_generate_model_first", "Generate the connected model first")); return; }
        if (!Paywall.gate(this, Tr.t("s3d_analyze", "Analyze"))) return;
        Ui.toast(this, Tr.t("s3d_analyzing", "Analyzing the model…"));
        Suda3D.IO.execute(() -> {
            try {
                JsonObject inputs = new JsonObject();
                inputs.add("model", inputRef(u, "model"));
                JsonObject r = Suda3D.createJob(this, "rig_check", new JsonObject(), inputs, UUID.randomUUID().toString());
                Jobs.track(this, r, "rig_check", Tr.t("s3d_analyze", "Analyze"), "an:" + dir.getName() + ":" + n.id);
            } catch (Exception e) {
                runOnUiThread(() -> { if (!dead) Paywall.handle(this, e, Tr.t("s3d_analyze", "Analyze")); });
            }
        });
    }

    private final java.util.Set<String> analyzed = new java.util.HashSet<>();

    private void onAnalyzed(Jobs.Job j) {
        if (!j.finished() || !analyzed.add(j.id)) return;
        String nodeId = j.tag.substring(j.tag.lastIndexOf(':') + 1);
        Graph.Node n = graph.node(nodeId);
        runOnUiThread(() -> {
            if (dead || n == null) return;
            if (!j.ok()) { Ui.toast(this, j.error.isEmpty() ? com.star4droid.star2d.suda.SudaUi.statusLabel(j.status) : j.error); return; }
            JsonObject r = j.result;
            boolean riggable = r == null || !r.has("riggable") || r.get("riggable").getAsBoolean();
            String type = r == null ? "" : Suda3D.str(r, "rig_type");
            if (type.isEmpty() && r != null) type = Suda3D.str(r, "topology");
            Ops.Param p = Ops.get("rig").param("rig_type");
            String match = null;
            if (p != null && p.choices != null) for (String c : p.choices) if (c.equalsIgnoreCase(type)) match = c;
            if (!riggable) { Ui.toast(this, Tr.t("s3d_not_riggable", "This model can't be rigged automatically")); return; }
            if (match != null) {
                n.params.put("rig_type", match);
                markStale(n);
                changed(false);
                Ui.toast(this, String.format(Locale.US, Tr.t("s3d_analyze_done", "Rigging OK. Skeleton set to %1$s"), com.star4droid.star2d.suda.SudaUi.choiceLabel(match)));
            } else Ui.toast(this, Tr.t("s3d_analyze_ok", "The model can be rigged"));
        });
    }

    private void quoteInto(Graph.Node n, TextView price) {
        price.setText(Tr.t("s3d_price", "Price") + ": …");
        String op = n.op();
        JsonObject params = Ops.toJson(Ops.get(op), n.params);
        Suda3D.IO.execute(() -> {
            String t;
            try {
                double cr = Suda3D.num(Suda3D.quote(this, op, params), "credits", -1);
                t = Tr.t("s3d_price", "Price") + ": " + (cr == 0 ? Tr.t("s3d_free", "Free") : Suda3D.fmtCredits(cr) + " " + Tr.t("s3d_credits", "credits"));
            } catch (Exception e) {
                t = Tr.t("s3d_price", "Price") + ": –";
            }
            String tt = t;
            runOnUiThread(() -> price.setText(tt));
        });
    }

    /** Params changed / re-run: results downstream are outdated. */
    private void markStale(Graph.Node n) {
        if (Graph.DONE.equals(n.state)) n.state = Graph.STALE;
        for (Graph.Node d : graph.downstream(n)) if (Graph.DONE.equals(d.state)) d.state = Graph.STALE;
    }

    // ------------------------------------------------------------ running

    /** Generated nodes upstream of n that have no result yet, in run order (n last). */
    private List<Graph.Node> chainFor(Graph.Node n) {
        List<Graph.Node> order = new ArrayList<>();
        visit(n, order, new ArrayList<>());
        return order;
    }

    private void visit(Graph.Node n, List<Graph.Node> order, List<String> seen) {
        if (seen.contains(n.id)) return;
        seen.add(n.id);
        for (Graph.Port p : n.def().in) {
            Graph.Link l = graph.into(n.id, p.id);
            if (l == null) continue;
            Graph.Node u = graph.node(l.fromNode);
            if (u == null || u.def().local || u.def().onDevice) continue;
            if (Graph.DONE.equals(u.state) && !u.outputs.isEmpty()) continue;
            visit(u, order, seen);
        }
        order.add(n);
    }

    /** First missing required input (message) or null. */
    private String missing(Graph.Node n) {
        for (Graph.Port p : n.def().in) {
            Graph.Link l = graph.into(n.id, p.id);
            if (l == null) {
                if (p.optional) continue;
                return n.def().title() + ": " + Tr.t("s3d_connect_input", "connect an input") + " (" + Graph.portLabel(p.type) + ")";
            }
            Graph.Node u = graph.node(l.fromNode);
            if (u != null && ("select_image".equals(u.type) || "upload_3d".equals(u.type)) && u.file.isEmpty())
                return u.def().title() + ": " + Tr.t("s3d_choose_file", "choose a file");
            if (u != null && u.def().onDevice && (u.file.isEmpty() || !new File(u.file).exists()))
                return u.def().title() + ": " + Tr.t("m2m_run_first", "open M2M Motion and export the model first");
        }
        Ops.Op op = Ops.get(n.op());
        if (op != null && op.param("prompt") != null && op.param("prompt").type == Ops.T.PROMPT && graph.into(n.id, "prompt") == null
                && n.params.getOrDefault("prompt", "").trim().isEmpty() && graph.into(n.id, "image") == null)
            return n.def().title() + ": " + Tr.t("s3d_prompt_hint", "Describe what to generate…");
        return null;
    }

    @Override
    public void generate(Graph.Node n) {
        if ("preview".equals(n.type)) { previewSave(n); return; }
        if (n.def().onDevice) { runM2M(n); return; }
        if (n.def().local) return;
        if (NodeGraphView.busy(n)) { if (!n.jobId.isEmpty()) Jobs.refresh(n.jobId); return; }
        List<Graph.Node> list = chainFor(n);
        for (Graph.Node x : list) if (NodeGraphView.busy(x)) { Ui.toast(this, x.def().title() + ": " + Tr.t("s3d_st_running", "Generating…")); return; }
        for (Graph.Node x : list) {
            String m = missing(x);
            if (m != null) { Ui.toast(this, m); return; }
        }
        if (!Paywall.gate(this, n.def().title())) return;
        Ui.toast(this, Tr.t("s3d_pricing", "Calculating the price…"));
        Suda3D.IO.execute(() -> {
            double total = 0;
            boolean known = true;
            for (Graph.Node x : list) {
                try {
                    if ("gen_image".equals(x.type)) x.params.put("_mode", graph.into(x.id, "image") != null ? "image_to_image" : "text_to_image");
                    total += Suda3D.num(Suda3D.quote(this, x.op(), Ops.toJson(Ops.get(x.op()), x.params)), "credits", 0);
                } catch (Exception e) { known = false; }
            }
            double t = total;
            boolean k = known;
            // not enough credits for the whole chain: nothing starts, straight to Buy Credits
            try { Suda3D.me(this); } catch (Exception ignored) {}
            double have = Suda3D.credits(this);
            if (k && have >= 0 && t > have && Suda3D.paid(this)) {
                runOnUiThread(() -> { if (!dead) Paywall.openBuy(this); });
                return;
            }
            runOnUiThread(() -> {
                String price = k ? (t == 0 ? Tr.t("s3d_free", "Free") : Suda3D.fmtCredits(t) + " " + Tr.t("s3d_credits", "credits")) : "?";
                String msg = list.size() > 1
                        ? String.format(Locale.US, Tr.t("s3d_chain_q", "%1$d nodes before this one have no result yet. Run the whole chain (%2$d nodes)?"), list.size() - 1, list.size())
                        + "\n" + Tr.t("s3d_total", "Total") + ": " + price
                        : Tr.t("s3d_price", "Price") + ": " + price;
                Dialogs.confirm(this, n.def().title(), msg, Tr.t("s3d_generate", "Generate"), () -> {
                    for (Graph.Node x : list) if (NodeGraphView.busy(x)) return; // started meanwhile
                    ArrayDeque<String> q = new ArrayDeque<>();
                    for (Graph.Node x : list) q.add(x.id);
                    runNext(q);
                });
            });
        });
    }

    /** Starts the first node of a chain; the rest waits in {@link #chains} until its job succeeds. */
    private void runNext(ArrayDeque<String> q) {
        String id = q.poll();
        if (id == null) return;
        Graph.Node n = graph.node(id);
        if (n == null) return;
        if (!q.isEmpty()) chains.put(id, q);
        n.state = Graph.QUEUED;
        n.progress = 0;
        n.error = "";
        markStale(n);
        n.state = Graph.QUEUED;
        n.jobId = "";
        n.queuedAt = System.currentTimeMillis();
        changed(false);
        saveNow();
        String op = n.op();
        JsonObject params = Ops.toJson(Ops.get(op), n.params);
        Suda3D.IO.execute(() -> {
            try {
                JsonObject inputs = new JsonObject();
                for (Graph.Port p : n.def().in) {
                    Graph.Link l = graph.into(n.id, p.id);
                    if (l == null) continue;
                    Graph.Node u = graph.node(l.fromNode);
                    if (u == null) continue;
                    if (Graph.TEXT.equals(p.type)) { params.addProperty("prompt", u.params.getOrDefault("text", "")); continue; }
                    String key = Graph.IMAGES.equals(p.type) ? "images" : Graph.IMAGE.equals(p.type) ? "image" : "model";
                    inputs.add(key, inputRef(u, key));
                }
                JsonObject r = Suda3D.createJob(this, op, params, inputs, UUID.randomUUID().toString());
                Jobs.Job j = Jobs.track(this, r, op, n.def().title(), tag(n));
                runOnUiThread(() -> {
                    if (dead) return; // the re-created activity adopts the job by its tag
                    n.jobId = j.id;
                    n.credits = j.credits;
                    n.outputs = new JsonArray();
                    n.params.remove("_mv_sel");
                    changed(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (dead) return;
                    chains.remove(n.id);
                    n.state = Graph.FAILED;
                    n.error = Suda3D.message(e);
                    changed(false);
                    Paywall.handle(this, e, n.def().title());
                });
            }
        });
    }

    /** upload_id for local files (re-used for 50 minutes), {"job_id","output"} refs for generated results. */
    private JsonElement inputRef(Graph.Node u, String key) throws Exception {
        if ("select_image".equals(u.type) || "upload_3d".equals(u.type) || u.def().onDevice) {
            if (u.uploadId.isEmpty() || System.currentTimeMillis() - u.uploadAt > 50 * 60_000L) {
                File f = new File(u.file);
                if (f.getName().toLowerCase(Locale.ROOT).endsWith(".obj")) f = zipObj(f);
                u.uploadId = Suda3D.upload(this, f);
                u.uploadAt = System.currentTimeMillis();
            }
            if ("images".equals(key)) { JsonArray a = new JsonArray(); a.add(u.uploadId); return a; }
            return new com.google.gson.JsonPrimitive(u.uploadId);
        }
        if ("images".equals(key)) {
            JsonArray a = new JsonArray();
            for (int i = 0; i < Math.max(1, u.outputs.size()); i++) {
                if (NodeGraphView.mvState(u, i) != '1') continue; // deselected / deleted views of a multiview
                JsonObject o = new JsonObject();
                o.addProperty("job_id", u.jobId);
                o.addProperty("output", i);
                a.add(o);
            }
            return a;
        }
        JsonObject o = new JsonObject();
        o.addProperty("job_id", u.jobId);
        o.addProperty("output", 0);
        return o;
    }

    /** OBJ + its .mtl + textures (same folder) zipped for upload. */
    private File zipObj(File obj) throws Exception {
        File zip = new File(getCacheDir(), obj.getName().replaceAll("\\.obj$", "") + ".zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            File[] all = obj.getParentFile().listFiles();
            if (all != null) for (File f : all) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (!(n.endsWith(".obj") || n.endsWith(".mtl") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) continue;
                z.putNextEntry(new ZipEntry(f.getName()));
                try (InputStream in = new FileInputStream(f)) {
                    byte[] b = new byte[65536];
                    int r;
                    while ((r = in.read(b)) > 0) z.write(b, 0, r);
                }
                z.closeEntry();
            }
        }
        return zip;
    }

    @Override
    public void onJob(Jobs.Job j) {
        if (dead) return;
        if (j.tag.startsWith("an:" + dir.getName() + ":")) { onAnalyzed(j); return; }
        for (Graph.Node n : new ArrayList<>(graph.nodes)) {
            if (n.jobId.isEmpty() && Graph.QUEUED.equals(n.state) && tag(n).equals(j.tag) && j.created >= n.queuedAt - 5000) n.jobId = j.id;
            if (!j.id.equals(n.jobId)) continue;
            String before = n.state;
            if (j.ok()) {
                n.state = Graph.DONE;
                n.outputs = j.outputs;
                n.progress = 100;
                if (j.credits >= 0) n.credits = j.credits;
                autoDownloadImages(n);
            } else if ("failed".equals(j.status) || "cancelled".equals(j.status)) {
                n.state = Graph.FAILED;
                n.error = j.error.isEmpty() ? com.star4droid.star2d.suda.SudaUi.statusLabel(j.status) : j.error;
            } else {
                if (Graph.DONE.equals(n.state) || Graph.STALE.equals(n.state) || Graph.FAILED.equals(n.state)) continue;
                n.state = "running".equals(j.status) ? Graph.RUNNING : Graph.QUEUED;
                n.progress = j.progress;
            }
            if (!before.equals(n.state) || Graph.RUNNING.equals(n.state)) changed(false);
            if (!before.equals(n.state) && j.ok()) { ArrayDeque<String> q = chains.remove(n.id); if (q != null) runNext(q); }
            if (!before.equals(n.state) && j.finished() && !j.ok()) chains.remove(n.id);
        }
    }

    // ------------------------------------------------------------ results

    File outFile(Graph.Node n, int i) {
        if (i >= n.outputs.size()) return null;
        return new File(files, Store.outputName(n.op(), n.jobId, n.outputs.get(i).getAsJsonObject(), i));
    }

    /** The node whose result a Preview node shows. */
    Graph.Node source(Graph.Node n) {
        if (!"preview".equals(n.type)) return n;
        Graph.Link l = graph.into(n.id, "in");
        return l == null ? null : graph.node(l.fromNode);
    }

    private void autoDownloadImages(Graph.Node n) {
        for (int i = 0; i < n.outputs.size(); i++) {
            JsonObject o = n.outputs.get(i).getAsJsonObject();
            if (!"image".equals(Suda3D.str(o, "kind"))) continue;
            File f = outFile(n, i);
            if (f != null && !f.exists()) Downloads.start(this, Suda3D.str(o, "url"), f, (long) Suda3D.num(o, "size_bytes", -1));
        }
    }

    @Override
    public boolean hasLocalResult(Graph.Node n) {
        Graph.Node s = source(n);
        if (s == null) return false;
        if ("upload_3d".equals(s.type)) return !s.file.isEmpty();
        if (s.def().onDevice) return !s.file.isEmpty() && new File(s.file).exists();
        File f = s.outputs.size() > 0 ? outFile(s, 0) : null;
        return f != null && f.exists();
    }

    @Override
    public float downloadFraction(Graph.Node n) {
        File f = n.outputs.size() > 0 ? outFile(n, 0) : null;
        if (f == null || f.exists() || !Downloads.running(f)) return -1;
        long t = Downloads.total(f);
        return t > 0 ? Downloads.saved(f) / (float) t : 0.02f;
    }

    @Override
    public Bitmap thumb(Graph.Node n) {
        File f = null;
        if ("select_image".equals(n.type)) f = n.file.isEmpty() ? null : new File(n.file);
        else {
            Graph.Node s = source(n);
            if (s != null && s.outputs.size() > 0 && "image".equals(Suda3D.str(s.outputs.get(0).getAsJsonObject(), "kind"))) f = outFile(s, 0);
        }
        return loadThumb(f);
    }

    @Override
    public Bitmap thumbAt(Graph.Node n, int i) {
        if (i >= n.outputs.size() || !"image".equals(Suda3D.str(n.outputs.get(i).getAsJsonObject(), "kind"))) return null;
        return loadThumb(outFile(n, i));
    }

    private Bitmap loadThumb(File f) {
        if (f == null || !f.exists()) return null;
        String k = f.getPath() + f.length();
        Bitmap b = thumbs.get(k);
        if (b == null && !thumbs.containsKey(k)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = Math.max(1, (int) (f.length() / 400_000));
            b = BitmapFactory.decodeFile(f.getPath(), o);
            thumbs.put(k, b);
        }
        return b;
    }

    @Override
    public void outputsEdited(Graph.Node n) {
        for (Graph.Node d : graph.downstream(n)) if (Graph.DONE.equals(d.state)) d.state = Graph.STALE;
    }

    @Override
    public void openResult(Graph.Node n) {
        if ("select_image".equals(n.type)) { replaceFile(n); return; }
        Graph.Node s = source(n);
        if (s == null) { Ui.toast(this, Tr.t("s3d_connect_input", "connect an input")); return; }
        if ("upload_3d".equals(s.type) || s.def().onDevice) { if (!s.file.isEmpty()) ModelViewerActivity.open(this, new File(s.file), "", "", new File(s.file).length(), null); return; }
        if (s.outputs.size() == 0) return;
        JsonObject o = s.outputs.get(0).getAsJsonObject();
        if (s.outputs.size() > 1 || "image".equals(Suda3D.str(o, "kind"))) { showOutputs(s); return; }
        ModelViewerActivity.open(this, outFile(s, 0), Suda3D.str(o, "url"), Suda3D.str(o, "expires_at"), (long) Suda3D.num(o, "size_bytes", -1), null);
    }

    private void showOutputs(Graph.Node n) {
        LinearLayout col = Ui.column(this);
        if (n.outputs.size() == 0) col.addView(Ui.text(this, com.star4droid.star2d.suda.SudaUi.statusLabel(n.state), 14, Ui.TEXT_2));
        for (int i = 0; i < n.outputs.size(); i++) {
            JsonObject o = n.outputs.get(i).getAsJsonObject();
            col.addView(new OutputView(this, o, outFile(n, i), null), Ui.margins(Ui.matchWrap(), this, 0, 6, 0, 0));
        }
        sheet(n.def().title(), col).show();
    }

    private void previewSave(Graph.Node n) {
        Graph.Node s = source(n);
        if (s == null) { Ui.toast(this, Tr.t("s3d_connect_input", "connect an input")); return; }
        if ("upload_3d".equals(s.type) || s.def().onDevice) { if (!s.file.isEmpty()) OutputView.saveMenu(this, new File(s.file), null, null); return; }
        File f = s.outputs.size() > 0 ? outFile(s, 0) : null;
        if (f == null) { generate(s); return; }
        if (!f.exists()) { openResult(n); return; }
        OutputView.saveMenu(this, f, null, null);
    }

    @Override
    public void onProgress(File dest, long done, long total, String state) {
        if (dest.getPath().startsWith(files.getPath())) view.invalidate();
    }

    // ------------------------------------------------------------ M2M Motion (on the phone)

    /** Opens M2M Motion on the model connected to n; the export comes back in onActivityResult (free, local). */
    private void runM2M(Graph.Node n) {
        Graph.Link l = graph.into(n.id, "model");
        Graph.Node u = l == null ? null : graph.node(l.fromNode);
        if (u == null) { Ui.toast(this, n.def().title() + ": " + Tr.t("s3d_connect_input", "connect an input") + " (" + Graph.portLabel(Graph.MODEL) + ")"); return; }
        File in = null;
        if ("upload_3d".equals(u.type) || u.def().onDevice) {
            if (!u.file.isEmpty()) in = new File(u.file);
        } else if (u.outputs.size() > 0) {
            in = outFile(u, 0);
            if (in != null && !in.exists()) {
                JsonObject o = u.outputs.get(0).getAsJsonObject();
                Downloads.start(this, Suda3D.str(o, "url"), in, (long) Suda3D.num(o, "size_bytes", -1));
                Ui.toast(this, Tr.t("m2m_wait_download", "Downloading the input model… try again when it is done"));
                return;
            }
        } else {
            generate(u);
            return;
        }
        if (in == null || !in.exists()) { Ui.toast(this, u.def().title() + ": " + Tr.t("s3d_choose_file", "choose a file")); return; }
        if (!com.star4droid.star2d.suda.M2MActivity.accepts(in.getName())) {
            Ui.toast(this, Tr.t("m2m_formats", "M2M Motion takes .glb or .fbx models"));
            return;
        }
        m2mNode = n.id;
        Intent i = com.star4droid.star2d.suda.M2MActivity.intent(this, in, null)
                .putExtra("result", new File(files, "m2m_" + n.id + ".glb").getAbsolutePath());
        try { startActivityForResult(i, REQ_M2M); } catch (Exception e) { Ui.toast(this, Suda3D.message(e)); }
    }

    // ------------------------------------------------------------ files

    @Override
    public void replaceFile(Graph.Node n) {
        pickFor = n;
        if ("select_image".equals(n.type)) {
            String[] items = {Tr.t("s3d_from_phone", "From the phone"), Tr.t("s3d_from_outputs", "From generated files")};
            Dialogs.choose(this, Tr.t("s3d_pick_image", "Choose a picture"), items, i -> {
                if (i == 0) pick("image/*", false, REQ_IMAGE);
                else QuickGen.pickOutput(this, false, f -> setFile(n, f));
            });
        } else pick("*/*", true, REQ_MODEL);
    }

    private void pick(String type, boolean multiple, int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type);
        if (multiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(i, req); } catch (Exception e) { Ui.toast(this, Suda3D.message(e)); }
    }

    private void setFile(Graph.Node n, File f) {
        n.file = f.getAbsolutePath();
        n.uploadId = "";
        markStale(n);
        thumbs.clear();
        changed(true);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_M2M) {
            Graph.Node n = m2mNode == null ? null : graph.node(m2mNode);
            String path = data.getStringExtra("path");
            if (n == null || path == null) return;
            markStale(n);
            n.file = path;
            n.uploadId = "";
            n.state = Graph.DONE;
            n.error = "";
            n.credits = 0;
            n.progress = 100;
            changed(true);
            return;
        }
        if (req == REQ_SAVE) {
            if (data.getData() != null && pendingSave != null) Store.saveToUri(this, pendingSave, data.getData(), null);
            return;
        }
        Graph.Node n = pickFor;
        if (n == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        File in = new File(files, "inputs/" + n.id);
        Suda3D.IO.execute(() -> {
            FileUtil.deleteFile(in.getPath());
            in.mkdirs();
            File main = null;
            try {
                for (Uri u : uris) {
                    String name = displayName(u);
                    File to = new File(in, name.replace(' ', '_'));
                    try (InputStream is = getContentResolver().openInputStream(u); OutputStream os = new FileOutputStream(to)) {
                        byte[] b = new byte[65536];
                        int r;
                        while ((r = is.read(b)) > 0) os.write(b, 0, r);
                    }
                    String low = to.getName().toLowerCase(Locale.ROOT);
                    if (req == REQ_IMAGE || low.endsWith(".glb") || low.endsWith(".gltf") || low.endsWith(".fbx") || low.endsWith(".obj")) main = to;
                }
                if (main != null && main.getName().toLowerCase(Locale.ROOT).endsWith(".obj"))
                    com.star4droid.star2d.ui.panels.ObjFix.fixFolder(in);
            } catch (Exception e) {
                Ui.toast(this, Suda3D.message(e));
            }
            File m = main;
            runOnUiThread(() -> {
                if (m == null) { Ui.toast(this, Tr.t("s3d_pick_model_hint", "Pick a .glb, .gltf, .fbx or .obj (with its .mtl and textures)")); return; }
                setFile(n, m);
            });
        });
    }

    private String displayName(Uri u) {
        try (android.database.Cursor c = getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        String p = u.getLastPathSegment();
        return p == null ? "file" : p.substring(p.lastIndexOf('/') + 1);
    }

    @Override
    public void createDocument(File f) {
        pendingSave = f;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(Suda3D.mime(f.getName())).putExtra(Intent.EXTRA_TITLE, f.getName());
        try { startActivityForResult(i, REQ_SAVE); } catch (Exception e) { Ui.toast(this, Suda3D.message(e)); }
    }
}
