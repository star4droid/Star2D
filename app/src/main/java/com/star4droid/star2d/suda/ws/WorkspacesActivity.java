package com.star4droid.star2d.suda.ws;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.suda.HistoryActivity;
import com.star4droid.star2d.suda.Store;
import com.star4droid.star2d.suda.SudaUi;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/** AI Workspace list: workspaces/<id>/graph.json under the app's external files; create, open, rename, duplicate, delete. */
public class WorkspacesActivity extends Activity {
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(SudaUi.topBar(this, Tr.t("s3d_workspace", "AI Workspace"), SudaUi.balanceChip(this),
                Ui.iconButton(this, R.drawable.ic_s2_history, Tr.t("s3d_history", "History"), v -> startActivity(new Intent(this, HistoryActivity.class)))), Ui.matchWrap());
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 16));
        LinearLayout create = Ui.button(this, R.drawable.ic_s2_add, Tr.t("s3d_ws_new", "New workspace"), true, v -> create());
        col.addView(create, new LinearLayout.LayoutParams(-1, Ui.dp(this, 46)));
        list = Ui.column(this);
        col.addView(list, Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 0));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    static String nameOf(File dir) {
        File g = new File(dir, "graph.json");
        String n = g.exists() ? Graph.parse(FileUtil.readFile(g.getPath())).name : "";
        return n.isEmpty() ? dir.getName() : n;
    }

    private void refresh() {
        list.removeAllViews();
        File[] dirs = Store.workspacesDir(this).listFiles(File::isDirectory);
        ArrayList<File> all = dirs == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(dirs));
        java.util.Collections.sort(all, (x, y) -> Long.compare(new File(y, "graph.json").lastModified(), new File(x, "graph.json").lastModified()));
        if (all.isEmpty()) {
            TextView t = Ui.text(this, Tr.t("s3d_ws_empty", "No workspaces yet. Create one and connect nodes: picture → 3D → texture → rig → save."), 14, Ui.TEXT_3);
            t.setPadding(Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6), 0);
            list.addView(t);
        }
        java.text.DateFormat fmt = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        for (File d : all) {
            Graph g = Graph.parse(FileUtil.readFile(new File(d, "graph.json").getPath()));
            String sub = g.nodes.size() + " " + Tr.t("s3d_nodes", "nodes") + " · " + fmt.format(new java.util.Date(new File(d, "graph.json").lastModified()));
            LinearLayout r = PanelHost.listRow(this, R.drawable.ic_s2_nodes, SudaUi.ORANGE, nameOf(d), sub,
                    Ui.iconButton(this, R.drawable.ic_s2_more, Tr.t("more", "More"), v -> menu(d)));
            r.setOnClickListener(v -> open(d));
            list.addView(r);
        }
    }

    private void open(File d) {
        startActivity(new Intent(this, WorkspaceActivity.class).putExtra("dir", d.getAbsolutePath()));
    }

    private void create() {
        Dialogs.input(this, Tr.t("s3d_ws_new", "New workspace"), null, Tr.t("s3d_workspace", "AI Workspace"), v -> v.trim().isEmpty() ? "!" : null, name -> {
            File d = new File(Store.workspacesDir(this), "ws" + System.currentTimeMillis());
            d.mkdirs();
            Graph g = new Graph();
            g.name = name.trim();
            Graph.Node img = g.add("select_image", 40, 80);
            Graph.Node to3d = g.add("image_to_3d", 340, 60);
            g.connect(img.id, "image", to3d.id, "image");
            FileUtil.writeFile(new File(d, "graph.json").getPath(), g.toJson().toString());
            open(d);
        });
    }

    private void menu(File d) {
        String[] items = {Tr.t("rename", "Rename"), Tr.t("s3d_duplicate", "Duplicate"), Tr.t("delete", "Delete")};
        Dialogs.choose(this, nameOf(d), items, i -> {
            File gf = new File(d, "graph.json");
            Graph g = Graph.parse(FileUtil.readFile(gf.getPath()));
            if (i == 0) Dialogs.input(this, Tr.t("rename", "Rename"), null, nameOf(d), null, n -> {
                g.name = n.trim();
                FileUtil.writeFile(gf.getPath(), g.toJson().toString());
                refresh();
            });
            else if (i == 1) {
                File copy = new File(Store.workspacesDir(this), "ws" + System.currentTimeMillis());
                FileUtil.copyDir(d.getPath(), copy.getPath());
                Graph c = Graph.parse(FileUtil.readFile(new File(copy, "graph.json").getPath()));
                c.name = nameOf(d) + " 2";
                FileUtil.writeFile(new File(copy, "graph.json").getPath(), c.toJson().toString());
                refresh();
            } else Dialogs.confirm(this, Tr.t("delete", "Delete"), nameOf(d) + "\n" + Tr.t("s3d_ws_delete_msg", "The workspace and its downloaded files are deleted from the phone. Results stay in the History while the server keeps them."),
                    Tr.t("delete", "Delete"), () -> { FileUtil.deleteFile(d.getPath()); refresh(); });
        });
    }
}
