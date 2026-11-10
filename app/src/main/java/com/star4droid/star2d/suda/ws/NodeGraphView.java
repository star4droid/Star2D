package com.star4droid.star2d.suda.ws;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.core.content.ContextCompat;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.suda.Ops;
import com.star4droid.star2d.suda.SudaUi;
import com.star4droid.star2d.ui.Tr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blueprint-style node editor: infinite dotted dark canvas (pan, pinch zoom), card nodes with header icon + title +
 * pencil, input ports on the left / outputs on the right, bezier wires dragged from an output to a compatible input
 * (compatible ports highlight; dropping on empty space asks the host for a node menu), tap a wire to delete it,
 * long-press nodes to multi-select, minimap in the corner. All coordinates of nodes are world units (dp).
 */
public class NodeGraphView extends View {

    public interface Host {
        void changed(boolean structural);
        void editParams(Graph.Node n);
        void generate(Graph.Node n);
        void openResult(Graph.Node n);
        void replaceFile(Graph.Node n);
        void dropWire(Graph.Node from, Graph.Port port, float wx, float wy);
        void deleteLink(Graph.Link l);
        void selectionChanged();
        /** Extra status for a node (download progress); fraction < 0 = none. */
        float downloadFraction(Graph.Node n);
        Bitmap thumb(Graph.Node n);
        boolean hasLocalResult(Graph.Node n);
    }

    private Graph graph = new Graph();
    private Host host;
    private final float dp;
    private float zoom = 1f, panX, panY; // screen = world * dp * zoom + pan
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Map<String, Drawable> icons = new HashMap<>();
    public final Set<String> selected = new LinkedHashSet<>();

    // gesture state
    private static final int NONE = 0, PAN = 1, DRAG_NODE = 2, WIRE = 3, PINCH = 4;
    private int mode = NONE;
    private float downX, downY, lastX, lastY;
    private boolean moved;
    private Graph.Node touchNode;
    private Graph.Port wirePort;
    private Graph.Node wireNode;
    private float wireX, wireY;
    private long downTime;
    private final int slop;
    private final Runnable longPress = this::onLongPress;
    // pinch: distance / zoom / world point under the focus at gesture start (absolute, no per-event accumulation)
    private float pinchDist0, pinchZoom0, pinchWX, pinchWY;
    private boolean pinchScaling;
    public static final float MIN_ZOOM = 0.3f, MAX_ZOOM = 2.5f;

    public static final float W = 230, HEADER = 42, PORT_ROW = 30;

    public NodeGraphView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        text.setColor(0xFFE8EAED);
    }

    public float zoom() { return zoom; }
    public float panX() { return panX; }
    public float panY() { return panY; }
    public void setViewport(float z, float px, float py) { zoom = clamp(z); panX = px; panY = py; invalidate(); }

    public void setGraph(Graph g, Host h) { this.graph = g; this.host = h; invalidate(); }
    public Graph graph() { return graph; }

    public float sx(float wx) { return wx * dp * zoom + panX; }
    public float sy(float wy) { return wy * dp * zoom + panY; }
    public float wx(float sx) { return (sx - panX) / (dp * zoom); }
    public float wy(float sy) { return (sy - panY) / (dp * zoom); }
    float s(float v) { return v * dp * zoom; }

    /** World point at the center of the view (where new nodes go). */
    public PointF center() { return new PointF(wx(getWidth() / 2f) - W / 2, wy(getHeight() / 2f) - 60); }

    public void frameAll() {
        if (graph.nodes.isEmpty() || getWidth() == 0) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Graph.Node n : graph.nodes) {
            minX = Math.min(minX, n.x); minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, n.x + W); maxY = Math.max(maxY, n.y + height(n));
        }
        float w = (maxX - minX + 60) * dp, h = (maxY - minY + 60) * dp;
        zoom = Math.max(MIN_ZOOM, Math.min(1.2f, Math.min(getWidth() / w, getHeight() / h)));
        panX = getWidth() / 2f - (minX + maxX) / 2 * dp * zoom;
        panY = getHeight() / 2f - (minY + maxY) / 2 * dp * zoom;
        invalidate();
    }

    // ------------------------------------------------------------ layout of a node (world units)

    boolean generated(Graph.Node n) { return !n.def().local; }

    int rows(Graph.Node n) { return Math.max(n.def().in.length, n.def().out.length); }

    float bodyTop(Graph.Node n) { return HEADER + rows(n) * PORT_ROW + 4; }

    List<String[]> paramLines(Graph.Node n) {
        List<String[]> l = new ArrayList<>();
        String op = n.op();
        Ops.Op o = op == null ? null : Ops.get(op);
        if (o == null) return l;
        for (Ops.Param p : o.params) {
            if (p.type == Ops.T.PROMPT) continue;
            String v = n.params.containsKey(p.key) ? n.params.get(p.key) : p.def;
            if (v == null || v.isEmpty()) continue;
            l.add(new String[]{p.label(), p.type == Ops.T.CHOICE ? SudaUi.choiceLabel(v) : v});
        }
        return l;
    }

    float height(Graph.Node n) {
        float h = bodyTop(n);
        switch (n.type) {
            case "select_image": return h + (n.file.isEmpty() ? 34 : 120);
            case "upload_3d": return h + 34 + 8;
            case "text": return h + 60;
            case "preview": return h + 96 + 44;
        }
        h += 24 + 10; // status + progress
        if (hasPreview(n)) h += 96;
        h += 44; // generate
        h += 30; // parameters toggle
        if (n.paramsOpen) h += Math.max(1, paramLines(n).size()) * 20 + 6;
        return h + 6;
    }

    boolean hasPreview(Graph.Node n) { return Graph.DONE.equals(n.state) || Graph.STALE.equals(n.state) || (host != null && host.hasLocalResult(n)); }

    float portY(Graph.Node n, int index) { return n.y + HEADER + PORT_ROW * index + PORT_ROW / 2f; }

    PointF inPos(Graph.Node n, String port) {
        Graph.Port[] ps = n.def().in;
        for (int i = 0; i < ps.length; i++) if (ps[i].id.equals(port)) return new PointF(n.x, portY(n, i));
        return new PointF(n.x, n.y + HEADER / 2);
    }

    PointF outPos(Graph.Node n, String port) {
        Graph.Port[] ps = n.def().out;
        for (int i = 0; i < ps.length; i++) if (ps[i].id.equals(port)) return new PointF(n.x + W, portY(n, i));
        return new PointF(n.x + W, n.y + HEADER / 2);
    }

    RectF pencil(Graph.Node n) { return new RectF(n.x + W - 38, n.y + 4, n.x + W - 4, n.y + HEADER - 4); }
    RectF previewRect(Graph.Node n) {
        float t = n.y + bodyTop(n) + ("preview".equals(n.type) || "select_image".equals(n.type) ? 0 : 34);
        return new RectF(n.x + 12, t, n.x + W - 12, t + ("select_image".equals(n.type) ? 84 : 88));
    }
    RectF generateRect(Graph.Node n) {
        float t = n.y + bodyTop(n) + 34 + (hasPreview(n) ? 96 : 0);
        if ("preview".equals(n.type)) t = n.y + bodyTop(n) + 96;
        return new RectF(n.x + 12, t, n.x + W - 12, t + 36);
    }
    RectF paramsToggle(Graph.Node n) { RectF g = generateRect(n); return new RectF(n.x + 8, g.bottom + 4, n.x + W - 8, g.bottom + 30); }
    /** Pencil in the Parameters header (opens the parameters sheet), left of the expand chevron. */
    RectF paramsPencil(Graph.Node n) { RectF t = paramsToggle(n); return new RectF(t.right - 62, t.top, t.right - 32, t.bottom); }
    RectF paramsChevron(Graph.Node n) { RectF t = paramsToggle(n); return new RectF(t.right - 30, t.top, t.right, t.bottom); }

    static boolean busy(Graph.Node n) { return Graph.RUNNING.equals(n.state) || Graph.QUEUED.equals(n.state); }
    RectF replaceRect(Graph.Node n) {
        float t = n.y + bodyTop(n) + ("select_image".equals(n.type) && !n.file.isEmpty() ? 88 : 0);
        return new RectF(n.x + 8, t, n.x + W - 8, t + 30);
    }

    // ------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF23252B);
        drawDots(c);
        // wires
        for (Graph.Link l : graph.links) {
            Graph.Node a = graph.node(l.fromNode), b = graph.node(l.toNode);
            if (a == null || b == null) continue;
            Graph.Port p = a.def().output(l.fromPort);
            PointF f = outPos(a, l.fromPort), t = inPos(b, l.toPort);
            drawWire(c, sx(f.x), sy(f.y), sx(t.x), sy(t.y), Graph.portColor(p == null ? "" : p.type), false);
        }
        if (mode == WIRE && wireNode != null) {
            PointF f = outPos(wireNode, wirePort.id);
            drawWire(c, sx(f.x), sy(f.y), wireX, wireY, Graph.portColor(wirePort.type), true);
        }
        for (Graph.Node n : graph.nodes) drawNode(c, n);
        drawMinimap(c);
    }

    void drawDots(Canvas c) {
        float step = 24;
        while (s(step) < 14) step *= 2;
        fill.setColor(0xFF3A3D45);
        float r = Math.max(1f, 1.3f * dp * Math.min(1f, zoom));
        float x0 = (float) Math.floor(wx(0) / step) * step, y0 = (float) Math.floor(wy(0) / step) * step;
        for (float x = x0; sx(x) < getWidth(); x += step)
            for (float y = y0; sy(y) < getHeight(); y += step) c.drawCircle(sx(x), sy(y), r, fill);
    }

    void drawWire(Canvas c, float x1, float y1, float x2, float y2, int color, boolean dashed) {
        float dx = Math.max(Math.abs(x2 - x1) * 0.5f, s(40));
        path.reset();
        path.moveTo(x1, y1);
        path.cubicTo(x1 + dx, y1, x2 - dx, y2, x2, y2);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(2f, s(2.6f)));
        stroke.setColor(color);
        stroke.setPathEffect(dashed ? new DashPathEffect(new float[]{s(8), s(5)}, 0) : null);
        c.drawPath(path, stroke);
        stroke.setPathEffect(null);
    }

    void drawNode(Canvas c, Graph.Node n) {
        Graph.Def d = n.def();
        float h = height(n);
        RectF r = new RectF(sx(n.x), sy(n.y), sx(n.x + W), sy(n.y + h));
        if (r.right < 0 || r.left > getWidth() || r.bottom < 0 || r.top > getHeight()) return;
        float rad = s(12);
        boolean sel = selected.contains(n.id);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x55000000);
        c.drawRoundRect(new RectF(r.left + s(2), r.top + s(4), r.right + s(2), r.bottom + s(4)), rad, rad, fill);
        fill.setColor(0xFF2E3138);
        c.drawRoundRect(r, rad, rad, fill);
        // orange top border
        c.save();
        c.clipRect(r.left, r.top, r.right, r.top + s(4));
        fill.setColor(stateColor(n));
        c.drawRoundRect(r, rad, rad, fill);
        c.restore();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(sel ? s(2.5f) : Math.max(1, s(1)));
        stroke.setColor(sel ? 0xFF4FC3F7 : 0xFF3F434C);
        c.drawRoundRect(r, rad, rad, stroke);
        // header
        drawIcon(c, d.icon, sx(n.x + 12), sy(n.y + 11), s(20), SudaUi.ORANGE);
        text.setTextSize(s(14.5f));
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setColor(0xFFF2F4F7);
        c.drawText(ellipsize(d.title(), W - 82), sx(n.x + 40), sy(n.y + 26), text);
        drawIcon(c, R.drawable.ic_s2_edit, sx(n.x + W - 31), sy(n.y + 12), s(18), 0xFFB0B6BF);
        stroke.setStrokeWidth(Math.max(1, s(0.8f)));
        stroke.setColor(0xFF3F434C);
        c.drawLine(r.left, sy(n.y + HEADER), r.right, sy(n.y + HEADER), stroke);
        // ports
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(s(12));
        for (int i = 0; i < d.in.length; i++) {
            Graph.Port p = d.in[i];
            boolean hi = mode == WIRE && wirePort != null && wireNode != n && Graph.compatible(wirePort.type, p.type);
            drawPort(c, n.x, portY(n, i), p.type, graph.into(n.id, p.id) != null, hi);
            text.setColor(0xFFB0B6BF);
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText(Graph.portLabel(p.type) + (p.optional ? " " + Tr.t("s3d_optional", "(optional)") : ""), sx(n.x + 14), sy(portY(n, i) + 4), text);
        }
        for (int i = 0; i < d.out.length; i++) {
            Graph.Port p = d.out[i];
            drawPort(c, n.x + W, portY(n, i), p.type, true, false);
            text.setColor(0xFFB0B6BF);
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(Graph.portLabel(p.type), sx(n.x + W - 14), sy(portY(n, i) + 4), text);
        }
        text.setTextAlign(Paint.Align.LEFT);
        float top = n.y + bodyTop(n);
        switch (n.type) {
            case "select_image":
                if (!n.file.isEmpty()) drawPreview(c, n, previewRect(n));
                drawFileRow(c, n, replaceRect(n));
                return;
            case "upload_3d":
                drawFileRow(c, n, replaceRect(n));
                return;
            case "text": {
                text.setColor(0xFFE8EAED);
                text.setTextSize(s(12.5f));
                String t = n.params.containsKey("text") ? n.params.get("text") : "";
                if (t.isEmpty()) { text.setColor(0xFF7D8590); t = Tr.t("s3d_text_empty", "Tap ✎ to write the prompt"); }
                String[] lines = wrap(t, W - 24, 3);
                for (int i = 0; i < lines.length; i++) c.drawText(lines[i], sx(n.x + 12), sy(top + 16 + i * 17), text);
                return;
            }
            case "preview":
                drawPreview(c, n, previewRect(n));
                drawButton(c, generateRect(n), Tr.t("s3d_save", "Save"), true);
                return;
        }
        // status line
        text.setTextSize(s(12));
        text.setColor(stateColor(n));
        String st = stateLabel(n);
        c.drawText(ellipsize(st, W - 24), sx(n.x + 12), sy(top + 15), text);
        // progress bar (job progress, or download progress)
        float frac = host == null ? -1 : host.downloadFraction(n);
        float prog = busy(n) ? -1 : frac; // job progress is shown inside the Generate button
        if (prog >= 0) {
            RectF bar = new RectF(sx(n.x + 12), sy(top + 22), sx(n.x + W - 12), sy(top + 27));
            fill.setColor(0xFF3A3D45);
            c.drawRoundRect(bar, s(3), s(3), fill);
            bar.right = bar.left + bar.width() * Math.max(0.02f, Math.min(1, prog));
            fill.setColor(frac >= 0 && !Graph.RUNNING.equals(n.state) ? 0xFF4FC3F7 : SudaUi.ORANGE);
            c.drawRoundRect(bar, s(3), s(3), fill);
        }
        if (hasPreview(n)) drawPreview(c, n, previewRect(n));
        boolean busy = busy(n);
        if (n.def().onDevice) drawButton(c, generateRect(n), Tr.t("m2m_open", "Open M2M Motion"), true);
        else if (busy) drawProgressButton(c, generateRect(n), n);
        else drawButton(c, generateRect(n), Graph.DONE.equals(n.state) || Graph.STALE.equals(n.state) || Graph.FAILED.equals(n.state)
                ? Tr.t("s3d_regenerate", "Regenerate") : Tr.t("s3d_generate", "Generate"), true);
        RectF pt = paramsToggle(n);
        text.setColor(0xFFB0B6BF);
        text.setTextSize(s(12.5f));
        c.drawText(Tr.t("s3d_parameters", "Parameters"), sx(pt.left + 4), sy(pt.top + 18), text);
        RectF pe = paramsPencil(n), ch = paramsChevron(n);
        drawIcon(c, R.drawable.ic_s2_edit, sx(pe.centerX() - 8), sy(pe.centerY() - 8), s(16), 0xFFB0B6BF);
        drawIcon(c, n.paramsOpen ? R.drawable.ic_s2_expand_less : R.drawable.ic_s2_expand_more, sx(ch.centerX() - 10), sy(ch.centerY() - 10), s(20), 0xFFB0B6BF);
        if (n.paramsOpen) {
            List<String[]> lines = paramLines(n);
            text.setTextSize(s(11.5f));
            if (lines.isEmpty()) { text.setColor(0xFF7D8590); c.drawText(Tr.t("s3d_defaults", "Defaults"), sx(n.x + 16), sy(pt.bottom + 14), text); }
            for (int i = 0; i < lines.size(); i++) {
                text.setColor(0xFF7D8590);
                c.drawText(ellipsize(lines.get(i)[0], 110), sx(n.x + 16), sy(pt.bottom + 14 + i * 20), text);
                text.setColor(0xFFE8EAED);
                text.setTextAlign(Paint.Align.RIGHT);
                c.drawText(ellipsize(lines.get(i)[1], 90), sx(n.x + W - 16), sy(pt.bottom + 14 + i * 20), text);
                text.setTextAlign(Paint.Align.LEFT);
            }
        }
    }

    void drawFileRow(Canvas c, Graph.Node n, RectF w) {
        text.setTextSize(s(12.5f));
        text.setColor(n.file.isEmpty() ? 0xFF7D8590 : 0xFFE8EAED);
        String name = n.file.isEmpty() ? Tr.t("s3d_no_file", "No file") : new java.io.File(n.file).getName();
        c.drawText(ellipsize(name, W - 100), sx(w.left + 6), sy(w.top + 20), text);
        text.setColor(SudaUi.ORANGE);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(n.file.isEmpty() ? Tr.t("s3d_choose", "Choose") : Tr.t("s3d_replace", "Replace"), sx(w.right - 6), sy(w.top + 20), text);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.DEFAULT);
    }

    void drawPreview(Canvas c, Graph.Node n, RectF w) {
        RectF r = new RectF(sx(w.left), sy(w.top), sx(w.right), sy(w.bottom));
        fill.setColor(0xFF23252B);
        c.drawRoundRect(r, s(8), s(8), fill);
        Bitmap b = host == null ? null : host.thumb(n);
        if (b != null) {
            float k = Math.min(r.width() / b.getWidth(), r.height() / b.getHeight());
            float bw = b.getWidth() * k, bh = b.getHeight() * k;
            c.drawBitmap(b, null, new RectF(r.centerX() - bw / 2, r.centerY() - bh / 2, r.centerX() + bw / 2, r.centerY() + bh / 2), null);
            return;
        }
        boolean local = host != null && host.hasLocalResult(n);
        drawIcon(c, R.drawable.ic_s2_view3d, r.centerX() - s(18), r.centerY() - s(26), s(36), local ? SudaUi.ORANGE : 0xFF7D8590);
        text.setTextSize(s(11.5f));
        text.setColor(0xFFB0B6BF);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(local ? Tr.t("s3d_tap_view", "Tap to view") : Tr.t("s3d_tap_download", "Tap to download & view"), r.centerX(), r.centerY() + s(26), text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    void drawButton(Canvas c, RectF w, String label, boolean enabled) {
        RectF r = new RectF(sx(w.left), sy(w.top), sx(w.right), sy(w.bottom));
        fill.setColor(enabled ? SudaUi.ORANGE : 0xFF4A4D55);
        c.drawRoundRect(r, s(8), s(8), fill);
        text.setColor(enabled ? 0xFF1B1300 : 0xFFB0B6BF);
        text.setTextSize(s(13.5f));
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(label, r.centerX(), r.centerY() + s(5), text);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.DEFAULT);
    }

    /** The Generate button of a running node: a progress bar filling the button + "Generating… 42%" (disabled). */
    void drawProgressButton(Canvas c, RectF w, Graph.Node n) {
        RectF r = new RectF(sx(w.left), sy(w.top), sx(w.right), sy(w.bottom));
        fill.setColor(0xFF3A3D45);
        c.drawRoundRect(r, s(8), s(8), fill);
        boolean queued = Graph.QUEUED.equals(n.state);
        float f = queued ? 0.04f : Math.max(0.04f, Math.min(1f, n.progress / 100f));
        c.save();
        c.clipRect(r.left, r.top, r.left + r.width() * f, r.bottom);
        fill.setColor(0xCCF5A524);
        c.drawRoundRect(r, s(8), s(8), fill);
        c.restore();
        text.setColor(0xFFF2F4F7);
        text.setTextSize(s(13f));
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        String label = queued ? Tr.t("s3d_st_queued", "Waiting in queue…") : Tr.t("s3d_st_running", "Generating…") + " " + n.progress + "%";
        c.drawText(ellipsize(label, W - 40), r.centerX(), r.centerY() + s(5), text);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.DEFAULT);
    }

    void drawPort(Canvas c, float x, float y, String type, boolean filled, boolean highlight) {
        float r = s(highlight ? 8 : 6);
        fill.setColor(Graph.portColor(type));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(Graph.portColor(type));
        stroke.setStrokeWidth(Math.max(1.5f, s(2)));
        if (highlight) {
            fill.setAlpha(80);
            c.drawCircle(sx(x), sy(y), r * 1.8f, fill);
            fill.setAlpha(255);
        }
        if (filled) c.drawCircle(sx(x), sy(y), r, fill);
        else {
            fill.setColor(0xFF2E3138);
            c.drawCircle(sx(x), sy(y), r, fill);
            c.drawCircle(sx(x), sy(y), r, stroke);
        }
    }

    void drawIcon(Canvas c, int res, float x, float y, float size, int color) {
        String k = res + ":" + color;
        Drawable d = icons.get(k);
        if (d == null) {
            Drawable base = ContextCompat.getDrawable(getContext(), res);
            if (base == null) return;
            d = base.mutate();
            d.setTint(color);
            icons.put(k, d);
        }
        d.setBounds((int) x, (int) y, (int) (x + size), (int) (y + size));
        d.draw(c);
    }

    void drawMinimap(Canvas c) {
        if (graph.nodes.size() < 2) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Graph.Node n : graph.nodes) {
            minX = Math.min(minX, n.x); minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, n.x + W); maxY = Math.max(maxY, n.y + height(n));
        }
        float vx0 = wx(0), vy0 = wy(0), vx1 = wx(getWidth()), vy1 = wy(getHeight());
        minX = Math.min(minX, vx0); minY = Math.min(minY, vy0); maxX = Math.max(maxX, vx1); maxY = Math.max(maxY, vy1);
        float mw = 120 * dp, mh = 80 * dp;
        float k = Math.min(mw / (maxX - minX), mh / (maxY - minY));
        float ox = getWidth() - mw - 10 * dp, oy = getHeight() - mh - 10 * dp;
        fill.setColor(0xCC1A1C21);
        c.drawRoundRect(new RectF(ox - 4 * dp, oy - 4 * dp, ox + mw + 4 * dp, oy + mh + 4 * dp), 6 * dp, 6 * dp, fill);
        for (Graph.Node n : graph.nodes) {
            fill.setColor(stateColor(n));
            c.drawRect(ox + (n.x - minX) * k, oy + (n.y - minY) * k, ox + (n.x + W - minX) * k, oy + (n.y + height(n) - minY) * k, fill);
        }
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(0xFFE8EAED);
        stroke.setStrokeWidth(dp);
        c.drawRect(ox + (vx0 - minX) * k, oy + (vy0 - minY) * k, ox + (vx1 - minX) * k, oy + (vy1 - minY) * k, stroke);
    }

    int stateColor(Graph.Node n) {
        switch (n.state) {
            case Graph.RUNNING: case Graph.QUEUED: return 0xFF4FC3F7;
            case Graph.DONE: return 0xFF66BB6A;
            case Graph.FAILED: return 0xFFEF5350;
            case Graph.STALE: return 0xFFB0B6BF;
            default: return SudaUi.ORANGE;
        }
    }

    String stateLabel(Graph.Node n) {
        String cr = n.credits >= 0 ? " · " + com.star4droid.star2d.suda.Suda3D.fmtCredits(n.credits) + " " + Tr.t("s3d_credits", "credits") : "";
        if (n.def().onDevice) cr = " · " + Tr.t("m2m_free_local", "Free · on this phone");
        switch (n.state) {
            case Graph.QUEUED: return Tr.t("s3d_st_queued", "Waiting in queue…");
            case Graph.RUNNING: return Tr.t("s3d_st_running", "Generating…") + " " + n.progress + "%";
            case Graph.DONE: return Tr.t("s3d_st_success", "Done") + cr;
            case Graph.FAILED: return Tr.t("s3d_st_failed", "Failed") + (n.error.isEmpty() ? "" : ": " + n.error);
            case Graph.STALE: return Tr.t("s3d_st_stale", "Outdated: inputs changed") + cr;
            default: return Tr.t("s3d_st_idle", "Ready") + (n.def().onDevice ? cr : "");
        }
    }

    String ellipsize(String s, float maxWorld) {
        return TextUtils.ellipsize(s, new android.text.TextPaint(text), s(maxWorld), TextUtils.TruncateAt.END).toString();
    }

    String[] wrap(String s, float maxWorld, int maxLines) {
        ArrayList<String> out = new ArrayList<>();
        String rest = s.replace('\n', ' ');
        while (!rest.isEmpty() && out.size() < maxLines) {
            int n = text.breakText(rest, true, s(maxWorld), null);
            if (n <= 0) break;
            if (n < rest.length()) {
                int sp = rest.lastIndexOf(' ', n);
                if (sp > n / 2) n = sp + 1;
            }
            String line = rest.substring(0, n);
            rest = rest.substring(n);
            if (out.size() == maxLines - 1 && !rest.isEmpty()) line = ellipsize(line + rest, maxWorld);
            out.add(line.trim());
        }
        return out.toArray(new String[0]);
    }

    // ------------------------------------------------------------ touch

    Graph.Node nodeAt(float wxp, float wyp) {
        for (int i = graph.nodes.size() - 1; i >= 0; i--) {
            Graph.Node n = graph.nodes.get(i);
            if (wxp >= n.x - 10 && wxp <= n.x + W + 10 && wyp >= n.y && wyp <= n.y + height(n)) return n;
        }
        return null;
    }

    float portHit() { return Math.max(16, 22 / zoom); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = lastX = x; downY = lastY = y; moved = false; downTime = System.currentTimeMillis();
                float px = wx(x), py = wy(y);
                touchNode = nodeAt(px, py);
                mode = PAN;
                if (touchNode != null) {
                    mode = DRAG_NODE;
                    Graph.Def d = touchNode.def();
                    for (int i = 0; i < d.out.length; i++) if (dist(px, py, touchNode.x + W, portY(touchNode, i)) < portHit()) {
                        mode = WIRE; wireNode = touchNode; wirePort = d.out[i]; wireX = x; wireY = y;
                    }
                    for (int i = 0; i < d.in.length && mode != WIRE; i++) if (dist(px, py, touchNode.x, portY(touchNode, i)) < portHit()) {
                        Graph.Link l = graph.into(touchNode.id, d.in[i].id);
                        if (l != null) { // pick up an existing wire from its input end
                            Graph.Node from = graph.node(l.fromNode);
                            if (from != null) {
                                graph.links.remove(l);
                                mode = WIRE; wireNode = from; wirePort = from.def().output(l.fromPort); wireX = x; wireY = y;
                                if (wirePort == null) mode = PAN;
                            }
                        }
                    }
                    if (mode == DRAG_NODE) postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                removeCallbacks(longPress);
                if (mode == WIRE) { // a second finger cancels the wire being dragged
                    if (host != null) host.changed(true);
                    wireNode = null;
                }
                if (mode == DRAG_NODE && moved && host != null) host.changed(false);
                mode = PINCH;
                moved = true;
                startPinch(e, -1);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (mode == PINCH) {
                    if (e.getPointerCount() < 2) return true;
                    float d = spread(e, -1), fx = avgX(e, -1), fy = avgY(e, -1);
                    // ignore tiny distance jitter until the fingers really spread / pinch
                    if (!pinchScaling && Math.abs(d - pinchDist0) > slop * 1.5f) { pinchScaling = true; pinchDist0 = d; pinchZoom0 = zoom; }
                    float nz = pinchScaling && pinchDist0 > 1 ? clamp(pinchZoom0 * d / pinchDist0) : zoom;
                    zoom = nz;
                    // the world point that was under the focus at start stays under the current focus (pan + zoom)
                    panX = fx - pinchWX * dp * zoom;
                    panY = fy - pinchWY * dp * zoom;
                    invalidate();
                    return true;
                }
                if (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) { moved = true; removeCallbacks(longPress); }
                if (mode == PAN && moved) {
                    panX += x - lastX; panY += y - lastY;
                } else if (mode == DRAG_NODE && moved) {
                    float dx = (x - lastX) / (dp * zoom), dy = (y - lastY) / (dp * zoom);
                    if (selected.contains(touchNode.id)) {
                        for (Graph.Node n : graph.nodes) if (selected.contains(n.id)) { n.x += dx; n.y += dy; }
                    } else { touchNode.x += dx; touchNode.y += dy; }
                } else if (mode == WIRE) { wireX = x; wireY = y; }
                lastX = x; lastY = y;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (mode != PINCH) return true;
                int up = e.getActionIndex();
                if (e.getPointerCount() - 1 >= 2) { startPinch(e, up); return true; }
                // one finger left: keep panning with it, no jump
                int keep = up == 0 ? 1 : 0;
                mode = PAN;
                moved = true;
                lastX = downX = e.getX(keep);
                lastY = downY = e.getY(keep);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(longPress);
                int m = mode;
                mode = NONE;
                if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) { invalidate(); return true; }
                if (m == WIRE && wireNode != null) finishWire(x, y);
                else if (m == DRAG_NODE && moved) { if (host != null) host.changed(false); }
                else if (m == DRAG_NODE && touchNode != null) tapNode(touchNode, wx(x), wy(y));
                else if (m == PAN && !moved) tapEmpty(wx(x), wy(y));
                invalidate();
                return true;
            }
        }
        return true;
    }

    /** Pinch start (or restart when a finger is added / removed): skip = pointer index going up, -1 none. */
    private void startPinch(MotionEvent e, int skip) {
        pinchDist0 = spread(e, skip);
        pinchZoom0 = zoom;
        pinchScaling = false;
        pinchWX = wx(avgX(e, skip));
        pinchWY = wy(avgY(e, skip));
    }

    static float clamp(float z) { return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z)); }

    /** Average distance of the pointers from their centre. */
    static float spread(MotionEvent e, int skip) {
        float cx = avgX(e, skip), cy = avgY(e, skip), s = 0;
        int n = 0;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skip) continue;
            s += (float) Math.hypot(e.getX(i) - cx, e.getY(i) - cy);
            n++;
        }
        return n == 0 ? 0 : s / n;
    }

    static float avgX(MotionEvent e, int skip) { float s = 0; int n = 0; for (int i = 0; i < e.getPointerCount(); i++) if (i != skip) { s += e.getX(i); n++; } return n == 0 ? 0 : s / n; }
    static float avgY(MotionEvent e, int skip) { float s = 0; int n = 0; for (int i = 0; i < e.getPointerCount(); i++) if (i != skip) { s += e.getY(i); n++; } return n == 0 ? 0 : s / n; }

    static float dist(float x1, float y1, float x2, float y2) { return (float) Math.hypot(x1 - x2, y1 - y2); }

    private void onLongPress() {
        if (mode != DRAG_NODE || moved || touchNode == null) return;
        if (!selected.remove(touchNode.id)) selected.add(touchNode.id);
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        mode = NONE;
        if (host != null) host.selectionChanged();
        invalidate();
    }

    private void finishWire(float x, float y) {
        float px = wx(x), py = wy(y);
        Graph.Node target = nodeAt(px, py);
        if (target != null && target != wireNode) {
            Graph.Def d = target.def();
            Graph.Port best = null;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < d.in.length; i++) {
                if (!Graph.compatible(wirePort.type, d.in[i].type)) continue;
                float dd = Math.abs(py - portY(target, i));
                if (dd < bestD) { bestD = dd; best = d.in[i]; }
            }
            if (best != null && !graph.cycle(wireNode.id, target.id)) {
                graph.connect(wireNode.id, wirePort.id, target.id, best.id);
                if (host != null) host.changed(true);
                return;
            }
            if (host != null) host.changed(true);
            return;
        }
        if (target == null && moved && host != null) { host.dropWire(wireNode, wirePort, px, py); return; }
        if (host != null) host.changed(true);
    }

    private void tapNode(Graph.Node n, float px, float py) {
        if (!selected.isEmpty()) { // selection mode: taps toggle
            if (!selected.remove(n.id)) selected.add(n.id);
            if (host != null) host.selectionChanged();
            return;
        }
        if (host == null) return;
        if (pencil(n).contains(px, py) || (py < n.y + HEADER)) { host.editParams(n); return; }
        switch (n.type) {
            case "select_image":
                if (replaceRect(n).contains(px, py) || n.file.isEmpty()) host.replaceFile(n);
                else if (previewRect(n).contains(px, py)) host.openResult(n);
                return;
            case "upload_3d":
                if (n.file.isEmpty() || replaceRect(n).contains(px, py)) host.replaceFile(n);
                return;
            case "text": host.editParams(n); return;
            case "preview":
                if (generateRect(n).contains(px, py)) host.generate(n);
                else if (previewRect(n).contains(px, py)) host.openResult(n);
                return;
        }
        if (generateRect(n).contains(px, py)) { if (n.def().onDevice || !busy(n)) host.generate(n); }
        else if (hasPreview(n) && previewRect(n).contains(px, py)) host.openResult(n);
        else if (paramsPencil(n).contains(px, py)) host.editParams(n);
        else if (paramsToggle(n).contains(px, py) || (n.paramsOpen && py > paramsToggle(n).bottom)) {
            n.paramsOpen = !n.paramsOpen;
            host.changed(false);
        }
    }

    private void tapEmpty(float px, float py) {
        if (!selected.isEmpty()) { selected.clear(); if (host != null) host.selectionChanged(); return; }
        // tap a wire: delete it
        float best = Math.max(10, 16 / zoom);
        Graph.Link hit = null;
        PathMeasure pm = new PathMeasure();
        float[] pos = new float[2];
        for (Graph.Link l : graph.links) {
            Graph.Node a = graph.node(l.fromNode), b = graph.node(l.toNode);
            if (a == null || b == null) continue;
            PointF f = outPos(a, l.fromPort), t = inPos(b, l.toPort);
            float dx = Math.max(Math.abs(t.x - f.x) * 0.5f, 40);
            Path p = new Path();
            p.moveTo(f.x, f.y);
            p.cubicTo(f.x + dx, f.y, t.x - dx, t.y, t.x, t.y);
            pm.setPath(p, false);
            float len = pm.getLength();
            for (int i = 0; i <= 40; i++) {
                pm.getPosTan(len * i / 40f, pos, null);
                float d = dist(px, py, pos[0], pos[1]);
                if (d < best) { best = d; hit = l; }
            }
        }
        if (hit != null && host != null) host.deleteLink(hit);
    }
}
