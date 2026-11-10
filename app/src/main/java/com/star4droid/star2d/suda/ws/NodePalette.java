package com.star4droid.star2d.suda.ws;

import android.app.Activity;
import android.content.ClipData;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.suda.SudaUi;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/**
 * Collapsible bottom panel of the workspace with a scrollable grid of node cards (icon + localized name + category
 * tint). Tap a card: the node is added at the visible canvas centre. Drag a card upwards onto the canvas: the node is
 * placed where it is dropped.
 */
public class NodePalette extends LinearLayout {
    public interface Host {
        /** Add a node of this type; wx is NaN for "visible centre", else the world position. */
        void addNode(Graph.Def def, float wx, float wy);
    }

    private static final String PREF_OPEN = "s3d_ws_palette_open";
    private final Activity a;
    private final Host host;
    private final LinearLayout body;
    private final ImageView chevron;
    private boolean open;

    public NodePalette(Activity a, Host host) {
        super(a);
        this.a = a;
        this.host = host;
        setOrientation(VERTICAL);
        setBackground(Ui.rounded(Ui.SURFACE, 0));
        open = Ui.prefs(a).getBoolean(PREF_OPEN, true);

        LinearLayout head = Ui.row(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4));
        head.addView(Ui.smallIcon(a, R.drawable.ic_s2_nodes, SudaUi.ORANGE, 18));
        TextView t = Ui.text(a, Tr.t("s3d_palette_title", "Nodes"), 14, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(a, 8), 0, 0, 0);
        head.addView(t, Ui.weight(1));
        TextView hint = Ui.text(a, Tr.t("s3d_palette_hint", "Tap to add · drag up to place"), 11.5f, Ui.TEXT_3);
        hint.setPadding(0, 0, Ui.dp(a, 6), 0);
        head.addView(hint);
        chevron = Ui.smallIcon(a, R.drawable.ic_s2_expand_more, Ui.TEXT_2, 24);
        head.addView(chevron);
        head.setOnClickListener(v -> setOpen(!open));
        addView(head, Ui.matchWrap());

        body = Ui.column(a);
        addView(body, Ui.matchWrap());
        build();
        setOpen(open);
    }

    public void setOpen(boolean o) {
        open = o;
        body.setVisibility(o ? VISIBLE : GONE);
        chevron.setImageResource(o ? R.drawable.ic_s2_expand_more : R.drawable.ic_s2_expand_less);
        try { Ui.prefs(a).edit().putBoolean(PREF_OPEN, o).apply(); } catch (Exception ignored) {}
    }

    public boolean isOpen() { return open; }

    private void build() {
        body.removeAllViews();
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        GridLayout grid = new GridLayout(a);
        int rows = Ui.isLandscape(a) ? 1 : 2;
        int n = Graph.DEFS.size();
        grid.setRowCount(rows);
        grid.setColumnCount((n + rows - 1) / rows);
        grid.setOrientation(GridLayout.VERTICAL);
        grid.setPadding(Ui.dp(a, 8), Ui.dp(a, 2), Ui.dp(a, 8), Ui.dp(a, 8));
        for (Graph.Def d : Graph.DEFS) {
            View card = card(d);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.dp(a, 92);
            lp.height = Ui.dp(a, 76);
            lp.setMargins(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
            grid.addView(card, lp);
        }
        hs.addView(grid);
        body.addView(hs, Ui.matchWrap());
    }

    /** Category tint: inputs blue, generators orange, model tools purple, rig / animation red, output green. */
    public static int tint(Graph.Def d) {
        if ("preview".equals(d.type)) return 0xFF66BB6A;
        if (d.local && d.in.length == 0) return 0xFF4FC3F7;
        boolean rig = "rig_check".equals(d.type) || d.onDevice;
        for (Graph.Port p : d.in) if (Graph.RIGGED.equals(p.type)) rig = true;
        for (Graph.Port p : d.out) if (Graph.RIGGED.equals(p.type)) rig = true;
        if (rig) return 0xFFEF5350;
        for (Graph.Port p : d.in) if (Graph.MODEL.equals(p.type)) return 0xFF9575CD;
        return SudaUi.ORANGE;
    }

    private View card(Graph.Def d) {
        int tint = tint(d);
        LinearLayout c = Ui.column(a);
        c.setGravity(Gravity.CENTER);
        c.setPadding(Ui.dp(a, 4), Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 4));
        c.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(a, 12), (tint & 0x00FFFFFF) | 0x66000000, Ui.dp(a, 1)));
        ImageView ic = Ui.smallIcon(a, d.icon, tint, 22);
        ic.setBackground(Ui.rounded((tint & 0x00FFFFFF) | 0x2A000000, Ui.dp(a, 10)));
        int pad = Ui.dp(a, 6);
        ic.setPadding(pad, pad, pad, pad);
        c.addView(ic, new LayoutParams(Ui.dp(a, 34), Ui.dp(a, 34)));
        TextView name = Ui.text(a, d.title(), 11.5f, Ui.TEXT);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, Ui.dp(a, 4), 0, 0);
        c.addView(name, Ui.matchWrap());
        c.setContentDescription(d.title());
        c.setOnClickListener(v -> host.addNode(d, Float.NaN, 0));
        c.setOnLongClickListener(v -> { startDrag(v, d); return true; });
        int slop = ViewConfiguration.get(a).getScaledTouchSlop();
        float[] down = new float[2];
        c.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: down[0] = e.getRawX(); down[1] = e.getRawY(); break;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - down[0], dy = e.getRawY() - down[1];
                    // an upward drag (towards the canvas) picks the card up; sideways drags scroll the grid
                    if (dy < -slop * 1.5f && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                        v.cancelLongPress();
                        v.setPressed(false);
                        startDrag(v, d);
                        return true;
                    }
                    break;
                }
            }
            return false;
        });
        return c;
    }

    private void startDrag(View v, Graph.Def d) {
        ClipData data = ClipData.newPlainText("s3d_node", d.type);
        try {
            v.startDragAndDrop(data, new View.DragShadowBuilder(v), d, 0);
        } catch (Exception ignored) {}
    }

    /** Makes the canvas accept cards dropped from the palette. */
    public static void acceptDrops(NodeGraphView view, Host host) {
        view.setOnDragListener((v, e) -> {
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED: return e.getLocalState() instanceof Graph.Def;
                case DragEvent.ACTION_DROP:
                    if (e.getLocalState() instanceof Graph.Def) {
                        host.addNode((Graph.Def) e.getLocalState(), view.wx(e.getX()) - NodeGraphView.W / 2, view.wy(e.getY()) - 20);
                        return true;
                    }
                    return false;
                default: return true;
            }
        });
    }
}
