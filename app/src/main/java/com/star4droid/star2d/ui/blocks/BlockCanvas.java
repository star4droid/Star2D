package com.star4droid.star2d.ui.blocks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.star4droid.star2d.evo.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 * Scratch-like block workspace drawn on one canvas (fast with many blocks).
 * Stacks are linked nodes; boolean nodes are C-blocks with "then" / "else" mouths.
 * Fields hold values or nested value (reporter) blocks; reporters have shapes by type
 * (boolean = hexagon, text = rounded rectangle, number / other = pill).
 * Optional mirrored (right-to-left) layout.
 */
@SuppressLint("ViewConstructor")
public class BlockCanvas extends View {

    public interface Listener {
        /** A field (of the node, or inside one of its value blocks) was tapped. */
        void onFieldTap(BlockModel.Node node, BlockModel.Field field);
        void onInfo(BlockModel.Node node);
        void onSelect(BlockModel.Node node);
        /** Long press released without moving. */
        void onLongPress(BlockModel.Node node, float rawX, float rawY);
        /** Structure changed (for undo + dirty flag). Called before the change is applied. */
        void beforeChange();
        void afterChange();
        /** A value block was dropped where it can't go. */
        void onDropHint();
        /** An event parameter chip (current, body2…) on the first block was tapped. */
        void onRootParam(String name);
    }

    public static final int SHAPE_PILL = 0, SHAPE_HEX = 1, SHAPE_RECT = 2;

    private BlockModel model;
    private final Listener listener;
    private final boolean rtl; // language labels
    private boolean mirror;   // right-to-left layout
    private String rootLabel = "";
    private String[] rootParams = {};

    // view transform
    private float scale = 1f, tx, ty;

    // ---- errors found by the compiler check (shown red) and the "show me" fly-to animation
    private final java.util.IdentityHashMap<BlockModel.Node, String> errors = new java.util.IdentityHashMap<>();
    private BlockModel.Node flashNode;
    private float flash;
    private android.animation.ValueAnimator flyAnim;

    private final float d; // density

    // layout
    private boolean dirty = true;
    private final ArrayList<Box> boxes = new ArrayList<>();
    private final ArrayList<Target> targets = new ArrayList<>();
    private final IdentityHashMap<BlockModel.Node, Object[]> parentOf = new IdentityHashMap<>(); // node -> {parent, slot}
    private final HashMap<String, Integer> colorCache = new HashMap<>();

    // block dragging
    private BlockModel.Node drag; // head of the dragged chain
    /** Long press fired on a block: the next move drags it (a move before that pans the canvas). */
    private boolean dragArmed;
    private boolean dragExternal;
    private float dragX, dragY, grabDX, grabDY;
    private final ArrayList<Box> dragBoxes = new ArrayList<>();
    private Target hover;
    private boolean overTrash;
    private BlockModel.Node selected;
    private BlockModel.Field activeField;

    // value block dragging
    private BlockModel.Param dragParam;
    private BlockModel.Field paramOrigin;
    private boolean paramExternal;
    private Slot paramGhost, hoverSlot;

    // paints
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paramText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Drawable trashIcon;

    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;

    // sizes (world px)
    private final float ROW, PAD, CHIP_H, INDENT, MOUTH_MIN, BAR, GAP, MAXW, RADIUS, INFO_R;

    /** A field drawn as a chip; a value block is a chip with text parts and inner slots. */
    static class Slot {
        BlockModel.Field field;
        BlockModel.Node node;
        final RectF r = new RectF();
        float w, h, px;
        String text = "";
        boolean empty, param;
        int shape, color;
        Path path;
        final ArrayList<Object> parts = new ArrayList<>(); // String | Slot
        final ArrayList<RectF> textRects = new ArrayList<>();
    }

    static class Box {
        BlockModel.Node node;
        final RectF header = new RectF(), all = new RectF(), info = new RectF(), labelR = new RectF();
        RectF spine, yesMouth, elseBar, noMouth, bottom;
        String label;
        final ArrayList<Slot> slots = new ArrayList<>();
        final ArrayList<RectF> params = new ArrayList<>(); // event parameters on the root block
        final ArrayList<String> paramNames = new ArrayList<>();
        int color;
        Path shape;
    }

    static class Target {
        BlockModel.Node parent; // null for "before head"
        int slot; // 0 next, 1 yes, 2 no, -1 before head
        BlockModel.Node head;
        float x, y;
    }

    public BlockCanvas(Context c, Listener listener, boolean rtl) {
        super(c);
        this.listener = listener;
        this.rtl = rtl;
        this.mirror = rtl;
        d = c.getResources().getDisplayMetrics().density;
        ROW = 36 * d; PAD = 8 * d; CHIP_H = 24 * d; INDENT = 18 * d; MOUTH_MIN = 26 * d; BAR = 20 * d;
        GAP = 2 * d; MAXW = 330 * d; RADIUS = 7 * d; INFO_R = 8 * d;
        text.setColor(Color.WHITE);
        text.setTextSize(13.5f * d);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        chipText.setTextSize(12.5f * d);
        chipText.setColor(0xFF1B1B1B);
        paramText.setTextSize(12.5f * d);
        paramText.setColor(Color.WHITE);
        paramText.setTypeface(Typeface.DEFAULT_BOLD);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        grid.setColor(0x14FFFFFF);
        grid.setStrokeWidth(1);
        Drawable t = ContextCompat.getDrawable(c, R.drawable.ic_s2_delete);
        if (t != null) {
            t = t.mutate();
            t.setTint(Color.WHITE);
        }
        trashIcon = t;
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector det) {
                float ns = Math.max(0.3f, Math.min(2.5f, scale * det.getScaleFactor()));
                float fx = det.getFocusX(), fy = det.getFocusY();
                tx = fx - (fx - tx) * (ns / scale);
                ty = fy - (fy - ty) * (ns / scale);
                scale = ns;
                invalidate();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (!multi) tap(e.getX(), e.getY());
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                // options are shown on release, and only if the finger didn't start a drag
                if (isDragging() || !pendingDrag || downBox == null || multi) return;
                // blocks move only after a long press: a plain swipe pans the canvas
                dragArmed = true;
                panning = false;
                if (downParam != null) {
                    // long-press on an event parameter: dragging now makes a copy of it
                    paramArmed = true;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    return;
                }
                pendingMenu = downBox;
                selected = downBox.node;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                invalidate();
            }
        });
        tx = 12 * d;
        ty = 12 * d;
    }

    public void setModel(BlockModel m, String rootLabel) {
        this.model = m;
        this.rootLabel = rootLabel;
        selected = null;
        activeField = null;
        relayout();
    }

    public BlockModel model() { return model; }

    /** Parameters of the event (current, body2, event…), shown as draggable chips on the first block. */
    public void setRootParams(String... names) {
        rootParams = names == null ? new String[0] : names;
        relayout();
    }
    public BlockModel.Node selected() { return selected; }
    public void select(BlockModel.Node n) { selected = n; invalidate(); }
    public BlockModel.Field activeField() { return activeField; }
    public void setActiveField(BlockModel.Field f) { activeField = f; invalidate(); }

    public boolean isMirror() { return mirror; }

    public void setMirror(boolean m) {
        if (m == mirror) return;
        mirror = m;
        relayout();
        home();
    }

    public void relayout() {
        dirty = true;
        invalidate();
    }

    public float zoom() { return scale; }

    public void zoomBy(float f) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float ns = Math.max(0.3f, Math.min(2.5f, scale * f));
        tx = cx - (cx - tx) * (ns / scale);
        ty = cy - (cy - ty) * (ns / scale);
        scale = ns;
        invalidate();
    }

    /** Scroll so the first stack is visible at the top start corner. */
    public void home() {
        BlockModel.Node h = model != null && !model.heads.isEmpty() ? model.heads.get(0) : null;
        if (h != null) {
            if (mirror) tx = getWidth() - 12 * d - h.x * scale;
            else tx = 12 * d - h.x * scale;
            ty = 12 * d - h.y * scale;
        }
        invalidate();
    }

    // ================================================================ coords

    private float wx(float sx) { return (sx - tx) / scale; }
    private float wy(float sy) { return (sy - ty) / scale; }

    /** World point near the visible start corner (for new stacks). */
    public float[] centerWorld() {
        return new float[]{wx(getWidth() * (mirror ? 0.7f : 0.3f)), wy(getHeight() * 0.3f)};
    }

    // ================================================================ layout

    private int colorFor(BlockModel.Node n) {
        if (n.isRoot()) return 0xFFFFAB00;
        Integer c = colorCache.get(n.title);
        if (c != null) return c;
        String cat = BlockCatalog.categoryOfTitle(n.title);
        int col;
        if (cat != null) col = BlockCatalog.colorOf(cat);
        else if (n.color != -1 && n.color != 0 && (n.color & 0xFF000000) == 0xFF000000) col = n.color;
        else col = 0xFF546E7A;
        colorCache.put(n.title, col);
        return col;
    }

    private void doLayout() {
        boxes.clear();
        targets.clear();
        parentOf.clear();
        if (model == null) return;
        for (BlockModel.Node h : model.heads) {
            if (!h.isRoot()) {
                Target t = new Target();
                t.slot = -1;
                t.head = h;
                t.x = h.x;
                t.y = h.y;
                targets.add(t);
            }
            int b0 = boxes.size(), t0 = targets.size();
            layoutChain(h, h.x, h.y, null, -1, boxes, true);
            if (mirror) mirrorRange(boxes, b0, targets, t0, h.x);
        }
        for (Box b : boxes) finalizeBox(b);
        dirty = false;
    }

    /** Lays out a chain (left to right); returns bottom y. */
    private float layoutChain(BlockModel.Node n, float x, float y, BlockModel.Node parent, int slot, ArrayList<Box> out, boolean addTargets) {
        BlockModel.Node prev = parent;
        int prevSlot = slot;
        float cy = y;
        while (n != null) {
            if (prev != null) parentOf.put(n, new Object[]{prev, prevSlot});
            Box b = layoutBlock(n, x, cy, out, addTargets);
            cy = b.all.bottom + GAP;
            if (addTargets) {
                Target t = new Target();
                t.parent = n;
                t.slot = 0;
                t.x = x;
                t.y = b.all.bottom;
                targets.add(t);
            }
            prev = n;
            prevSlot = 0;
            n = n.next;
        }
        return cy;
    }

    private Box layoutBlock(BlockModel.Node n, float x, float y, ArrayList<Box> out, boolean addTargets) {
        Box b = new Box();
        b.node = n;
        b.color = colorFor(n);
        b.label = n.isRoot() ? rootLabel : BlockInfo.label(n.title, rtl);
        out.add(b);
        float labelW = text.measureText(b.label);
        for (BlockModel.Field f : n.fields) b.slots.add(makeSlot(n, f, true));
        // wrap chips into rows
        ArrayList<ArrayList<Slot>> rows = new ArrayList<>();
        rows.add(new ArrayList<>());
        float paramsW = 0;
        if (n.isRoot())
            for (String pn : rootParams) {
                float w = paramText.measureText(BlockInfo.reporter(pn)) + 22 * d;
                b.params.add(new RectF(x + PAD + labelW + PAD + paramsW, 0, x + PAD + labelW + PAD + paramsW + w, 0));
                b.paramNames.add(pn);
                paramsW += w + 6 * d;
            }
        labelW += paramsW;
        float firstStart = x + PAD + labelW + PAD;
        float rx = firstStart, width = PAD + labelW + PAD;
        for (Slot s : b.slots) {
            ArrayList<Slot> row = rows.get(rows.size() - 1);
            float start = rows.size() == 1 ? firstStart : x + PAD;
            if (rx + s.w - x > MAXW && (rx > start + 1 || rows.size() == 1)) {
                rows.add(row = new ArrayList<>());
                rx = x + PAD;
            }
            s.px = rx;
            row.add(s);
            rx += s.w + 6 * d;
            width = Math.max(width, rx - x + PAD - 6 * d);
        }
        if (rows.size() > 1 && rows.get(0).isEmpty()) {
            // label alone on the first line: keep it
        }
        width = Math.max(width + 2 * INFO_R + 10 * d, 120 * d);
        float ry = y;
        float row0H = ROW;
        for (int i = 0; i < rows.size(); i++) {
            float rh = ROW;
            for (Slot s : rows.get(i)) rh = Math.max(rh, s.h + 10 * d);
            if (i == 0) row0H = rh;
            for (Slot s : rows.get(i)) placeSlot(s, s.px, ry + rh / 2);
            ry += rh;
        }
        b.labelR.set(x + PAD, y, x + PAD + labelW - paramsW, y + row0H);
        for (RectF pr : b.params) {
            pr.top = y + (row0H - CHIP_H) / 2;
            pr.bottom = pr.top + CHIP_H;
        }
        float headerH = ry - y;
        b.header.set(x, y, x + width, y + headerH);
        float icy = y + row0H / 2;
        b.info.set(x + width - 2 * INFO_R - 6 * d, icy - INFO_R, x + width - 6 * d, icy + INFO_R);
        float bottom = y + headerH;
        float maxW = width;
        if (n.isIf) {
            float my = bottom;
            int startYes = out.size();
            float innerBottom = n.yes != null ? layoutChain(n.yes, x + INDENT, my, n, 1, out, addTargets) - GAP : my;
            if (addTargets) addSlotTarget(n, 1, x + INDENT, my);
            float yh = Math.max(MOUTH_MIN, innerBottom - my);
            b.yesMouth = new RectF(x + INDENT, my, x + width, my + yh);
            bottom = my + yh;
            maxW = Math.max(maxW, rightOf(out, startYes) - x);
            if (n.hasElse()) {
                b.elseBar = new RectF(x, bottom, x + width, bottom + BAR);
                bottom += BAR;
                float ny = bottom;
                int startNo = out.size();
                float nb = n.no != null ? layoutChain(n.no, x + INDENT, ny, n, 2, out, addTargets) - GAP : ny;
                if (addTargets) addSlotTarget(n, 2, x + INDENT, ny);
                float nh = Math.max(MOUTH_MIN, nb - ny);
                b.noMouth = new RectF(x + INDENT, ny, x + width, ny + nh);
                bottom = ny + nh;
                maxW = Math.max(maxW, rightOf(out, startNo) - x);
            }
            b.bottom = new RectF(x, bottom, x + width, bottom + BAR * 0.8f);
            b.spine = new RectF(x, b.header.bottom - RADIUS, x + INDENT, b.bottom.top + RADIUS);
            bottom += BAR * 0.8f;
        }
        b.all.set(x, y, x + Math.max(width, maxW), bottom);
        return b;
    }

    private static float rightOf(ArrayList<Box> out, int from) {
        float r = 0;
        for (int i = from; i < out.size(); i++) r = Math.max(r, out.get(i).all.right);
        return r;
    }

    private void addSlotTarget(BlockModel.Node n, int slot, float x, float y) {
        Target t = new Target();
        t.parent = n;
        t.slot = slot;
        t.x = x;
        t.y = y;
        targets.add(t);
    }

    // ---------------------------------------------------------------- slots (fields + value blocks)

    private float padFor(int shape, float h) {
        return shape == SHAPE_HEX ? h * 0.42f + 3 * d : 8 * d;
    }

    private static String oneLine(String s) {
        return s.replace('\n', ' ');
    }

    private static boolean textField(BlockModel.Field f) {
        String t = f.type().toLowerCase(java.util.Locale.ROOT), l = f.label().toLowerCase(java.util.Locale.ROOT);
        return t.equals("string") || l.equals("text") || l.equals("string") || l.equals("key") || l.equals("message");
    }

    private Slot makeSlot(BlockModel.Node n, BlockModel.Field f, boolean top) {
        Slot s = new Slot();
        s.field = f;
        s.node = n;
        if (f.block != null) {
            s.param = true;
            String tpl = f.block.template;
            s.shape = BlockCatalog.isBooleanTemplate(tpl) ? SHAPE_HEX : (BlockCatalog.isTextTemplate(tpl) ? SHAPE_RECT : SHAPE_PILL);
            s.color = BlockCatalog.reporterColor(tpl);
            String[] pieces = BlockInfo.reporter(f.block.display).split("___", -1);
            float content = 0, h = CHIP_H;
            int k = 0;
            for (int i = 0; i < pieces.length; i++) {
                String p = pieces[i].trim();
                if (!p.isEmpty()) {
                    s.parts.add(p);
                    content += paramText.measureText(p) + 5 * d;
                }
                if (i < pieces.length - 1 && k < f.block.inner.size()) {
                    Slot c = makeSlot(n, f.block.inner.get(k++), false);
                    s.parts.add(c);
                    content += c.w + 5 * d;
                    h = Math.max(h, c.h + 6 * d);
                }
            }
            content = Math.max(0, content - 5 * d);
            s.h = h;
            s.w = content + 2 * padFor(s.shape, h);
        } else {
            String lbl = BlockInfo.field(f.label(), rtl);
            s.empty = f.value.isEmpty();
            if (top) s.text = s.empty ? lbl : lbl + ": " + oneLine(f.value);
            else s.text = s.empty ? "" : oneLine(f.value);
            s.text = TextUtils.ellipsize(s.text, new android.text.TextPaint(chipText), 160 * d, TextUtils.TruncateAt.END).toString();
            s.shape = "boolean".equalsIgnoreCase(f.type()) ? SHAPE_HEX : (textField(f) ? SHAPE_RECT : SHAPE_PILL);
            s.h = top ? CHIP_H : CHIP_H - 4 * d;
            float tw = s.text.isEmpty() ? 12 * d : chipText.measureText(s.text);
            s.w = tw + 2 * padFor(s.shape, s.h);
        }
        return s;
    }

    private void placeSlot(Slot s, float x, float cy) {
        s.r.set(x, cy - s.h / 2, x + s.w, cy + s.h / 2);
        if (!s.param) return;
        s.textRects.clear();
        float px = x + padFor(s.shape, s.h);
        for (Object o : s.parts) {
            if (o instanceof String) {
                float tw = paramText.measureText((String) o);
                s.textRects.add(new RectF(px, cy - 8 * d, px + tw, cy + 8 * d));
                px += tw + 5 * d;
            } else {
                Slot c = (Slot) o;
                placeSlot(c, px, cy);
                px += c.w + 5 * d;
            }
        }
    }

    // ---------------------------------------------------------------- mirroring

    private static void mirror(RectF r, float ax) {
        if (r == null) return;
        float l = 2 * ax - r.right, rr = 2 * ax - r.left;
        r.left = l;
        r.right = rr;
    }

    private static void mirrorSlot(Slot s, float ax) {
        mirror(s.r, ax);
        for (RectF t : s.textRects) mirror(t, ax);
        for (Object o : s.parts) if (o instanceof Slot) mirrorSlot((Slot) o, ax);
    }

    private static void mirrorRange(ArrayList<Box> list, int from, ArrayList<Target> tl, int tfrom, float ax) {
        for (int i = from; i < list.size(); i++) {
            Box b = list.get(i);
            mirror(b.header, ax);
            mirror(b.all, ax);
            mirror(b.info, ax);
            mirror(b.labelR, ax);
            mirror(b.spine, ax);
            mirror(b.yesMouth, ax);
            mirror(b.elseBar, ax);
            mirror(b.noMouth, ax);
            mirror(b.bottom, ax);
            for (Slot s : b.slots) mirrorSlot(s, ax);
            for (RectF pr : b.params) mirror(pr, ax);
        }
        if (tl != null) for (int i = tfrom; i < tl.size(); i++) tl.get(i).x = 2 * ax - tl.get(i).x;
    }

    // ---------------------------------------------------------------- shapes

    private Path slotPath(RectF r, int shape) {
        Path p = new Path();
        float h = r.height();
        if (shape == SHAPE_HEX) {
            float k = Math.min(h * 0.42f, r.width() / 2);
            float cy = r.centerY();
            p.moveTo(r.left, cy);
            p.lineTo(r.left + k, r.top);
            p.lineTo(r.right - k, r.top);
            p.lineTo(r.right, cy);
            p.lineTo(r.right - k, r.bottom);
            p.lineTo(r.left + k, r.bottom);
            p.close();
        } else if (shape == SHAPE_RECT) {
            p.addRoundRect(r, 5 * d, 5 * d, Path.Direction.CW);
        } else {
            p.addRoundRect(r, h / 2, h / 2, Path.Direction.CW);
        }
        return p;
    }

    private void finalizeSlot(Slot s) {
        s.path = slotPath(s.r, s.shape);
        for (Object o : s.parts) if (o instanceof Slot) finalizeSlot((Slot) o);
    }

    private void finalizeBox(Box b) {
        for (Slot s : b.slots) finalizeSlot(s);
        Path p;
        if (b.node.isRoot()) p = hatPath(b.header);
        else {
            p = new Path();
            p.addRoundRect(b.header, RADIUS, RADIUS, Path.Direction.CW);
        }
        if (b.node.isIf && b.spine != null) {
            Path q = new Path();
            q.addRect(b.spine, Path.Direction.CW);
            if (b.elseBar != null) q.addRect(b.elseBar, Path.Direction.CW);
            q.addRoundRect(b.bottom, RADIUS, RADIUS, Path.Direction.CW);
            Path u = new Path();
            if (u.op(p, q, Path.Op.UNION)) p = u;
            else p.addPath(q);
        }
        b.shape = p;
    }

    private Path hatPath(RectF h) {
        Path p = new Path();
        float s = mirror ? -1 : 1;
        float start = mirror ? h.right : h.left, end = mirror ? h.left : h.right;
        p.moveTo(start, h.top + 10 * d);
        p.quadTo(start + s * 40 * d, h.top - 8 * d, start + s * 90 * d, h.top + 4 * d);
        p.lineTo(end - s * RADIUS, h.top + 4 * d);
        p.quadTo(end, h.top + 4 * d, end, h.top + 4 * d + RADIUS);
        p.lineTo(end, h.bottom - RADIUS);
        p.quadTo(end, h.bottom, end - s * RADIUS, h.bottom);
        p.lineTo(start + s * RADIUS, h.bottom);
        p.quadTo(start, h.bottom, start, h.bottom - RADIUS);
        p.close();
        return p;
    }

    // ================================================================ draw

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF15181C);
        if (dirty) doLayout();
        // grid
        float step = 24 * d * scale;
        if (step > 6) {
            float ox = ((tx % step) + step) % step, oy = ((ty % step) + step) % step;
            for (float gx = ox; gx < getWidth(); gx += step) c.drawLine(gx, 0, gx, getHeight(), grid);
            for (float gy = oy; gy < getHeight(); gy += step) c.drawLine(0, gy, getWidth(), gy, grid);
        }
        c.save();
        c.translate(tx, ty);
        c.scale(scale, scale);
        float l = wx(0), t = wy(0), r = wx(getWidth()), btm = wy(getHeight());
        for (Box b : boxes) {
            if (b.all.right < l || b.all.left > r || b.all.bottom < t || b.all.top > btm) continue;
            drawBox(c, b, false);
        }
        // drop indicator
        if (drag != null && hover != null && !overTrash) {
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(4 * d);
            stroke.setPathEffect(null);
            float len = 110 * d * (mirror ? -1 : 1);
            c.drawLine(hover.x, hover.y, hover.x + len, hover.y, stroke);
        }
        if (drag != null) for (Box b : dragBoxes) drawBox(c, b, true);
        if (paramGhost != null) drawSlot(c, paramGhost, true);
        c.restore();
        if (drag != null || dragParam != null) drawTrash(c);
    }

    private void drawBox(Canvas c, Box b, boolean ghost) {
        int col = b.color;
        boolean bad = !ghost && errors.containsKey(b.node);
        if (bad) col = 0xFFD32F2F;
        // soft bottom shadow for depth
        if (!ghost) {
            c.save();
            c.translate(0, 2.2f * d);
            fill.setColor(0x55000000);
            c.drawPath(b.shape, fill);
            c.restore();
        }
        fill.setColor(ghost ? (col & 0x00FFFFFF) | 0xD0000000 : col);
        c.drawPath(b.shape, fill);
        if (b.node.isIf) {
            text.setTextSize(11.5f * d);
            text.setAlpha(210);
            if (b.elseBar != null) {
                String el = rtl ? "وإلا" : "else";
                float ex = mirror ? b.elseBar.right - PAD - text.measureText(el) : b.elseBar.left + PAD;
                c.drawText(el, ex, b.elseBar.centerY() + 4 * d, text);
            }
            text.setAlpha(255);
            text.setTextSize(13.5f * d);
            stroke.setStrokeWidth(1.2f * d);
            stroke.setColor(0x33FFFFFF);
            stroke.setPathEffect(new DashPathEffect(new float[]{6 * d, 5 * d}, 0));
            if (b.node.yes == null && b.yesMouth != null) c.drawRoundRect(emptyMouth(b.yesMouth), RADIUS, RADIUS, stroke);
            if (b.node.no == null && b.noMouth != null) c.drawRoundRect(emptyMouth(b.noMouth), RADIUS, RADIUS, stroke);
            stroke.setPathEffect(null);
        }
        if (bad) {
            stroke.setColor(0xFFFFCDD2);
            stroke.setStrokeWidth(2f * d);
            c.drawPath(b.shape, stroke);
        }
        if (!ghost && b.node == flashNode && flash > 0f) {
            stroke.setColor(Color.WHITE);
            stroke.setAlpha((int) (255 * flash));
            stroke.setStrokeWidth((3f + 5f * flash) * d);
            c.drawPath(b.shape, stroke);
            stroke.setAlpha(255);
        }
        // selection outline: whole block shape (the full C for if / else blocks)
        // armed by a long press (ready to drag): thick bright outline so the block looks lifted
        if (!ghost && dragArmed && pendingDrag && downBox != null && b.node == downBox.node) {
            stroke.setColor(0xFFFFEB3B);
            stroke.setStrokeWidth(4.5f * d);
            c.drawPath(b.shape, stroke);
        }
        if (b.node == selected && !ghost) {
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(2.5f * d);
            c.drawPath(b.shape, stroke);
        }
        c.drawText(b.label, b.labelR.left, b.labelR.centerY() + 5 * d, text);
        for (int i = 0; i < b.params.size(); i++) {
            RectF pr = b.params.get(i);
            fill.setColor(0xFF00897B);
            c.drawRoundRect(pr, pr.height() / 2, pr.height() / 2, fill);
            stroke.setColor(0x99FFFFFF);
            stroke.setStrokeWidth(1.2f * d);
            c.drawRoundRect(pr, pr.height() / 2, pr.height() / 2, stroke);
            c.drawText(BlockInfo.reporter(b.paramNames.get(i)), pr.left + 11 * d, pr.centerY() + 4.3f * d, paramText);
        }
        for (Slot s : b.slots) drawSlot(c, s, ghost);
        if (!ghost) {
            fill.setColor(bad ? 0xFFFFFFFF : 0x40000000);
            c.drawCircle(b.info.centerX(), b.info.centerY(), INFO_R, fill);
            if (bad) text.setColor(0xFFD32F2F);
            text.setTextSize(12 * d);
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText("!", b.info.centerX(), b.info.centerY() + 4.5f * d, text);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(13.5f * d);
            if (bad) text.setColor(Color.WHITE);
        }
    }

    private RectF emptyMouth(RectF m) {
        float w = Math.min(120 * d, m.width() - 8 * d);
        if (mirror) return new RectF(m.right - 4 * d - w, m.top + 4 * d, m.right - 4 * d, m.bottom - 4 * d);
        return new RectF(m.left + 4 * d, m.top + 4 * d, m.left + 4 * d + w, m.bottom - 4 * d);
    }

    private void drawSlot(Canvas c, Slot s, boolean ghost) {
        if (s.path == null) s.path = slotPath(s.r, s.shape);
        if (s.param) {
            fill.setColor(ghost ? (s.color & 0x00FFFFFF) | 0xE6000000 : s.color);
            c.drawPath(s.path, fill);
            stroke.setColor(0x55000000);
            stroke.setStrokeWidth(1 * d);
            c.drawPath(s.path, stroke);
            int ti = 0;
            for (Object o : s.parts) {
                if (o instanceof String) {
                    RectF tr = s.textRects.get(ti++);
                    c.drawText((String) o, tr.left, s.r.centerY() + 4.3f * d, paramText);
                } else drawSlot(c, (Slot) o, ghost);
            }
        } else {
            fill.setColor(s.empty ? 0x59FFFFFF : 0xF5FFFFFF);
            c.drawPath(s.path, fill);
            if (!s.text.isEmpty()) {
                chipText.setColor(s.empty ? 0xCC1B1B1B : 0xFF1B1B1B);
                c.drawText(s.text, s.r.left + padFor(s.shape, s.h), s.r.centerY() + 4.3f * d, chipText);
            }
        }
        if (!ghost && activeField != null && s.field == activeField) {
            stroke.setColor(0xFF64B5F6);
            stroke.setStrokeWidth(2.5f * d);
            c.drawPath(s.path, stroke);
        }
        if (s == hoverSlot) {
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(3 * d);
            c.drawPath(s.path, stroke);
        }
    }

    private RectF trashRect() {
        float s = 64 * d;
        if (mirror) return new RectF(16 * d, getHeight() - s - 16 * d, 16 * d + s, getHeight() - 16 * d);
        return new RectF(getWidth() - s - 16 * d, getHeight() - s - 16 * d, getWidth() - 16 * d, getHeight() - 16 * d);
    }

    private void drawTrash(Canvas c) {
        RectF r = trashRect();
        fill.setColor(overTrash ? 0xFFE53935 : 0xAA37474F);
        c.drawRoundRect(r, 16 * d, 16 * d, fill);
        if (trashIcon != null) {
            int s = (int) (28 * d);
            int cx = (int) r.centerX(), cy = (int) r.centerY();
            trashIcon.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
            trashIcon.draw(c);
        }
    }

    // ================================================================ hit testing

    private Box boxAt(float x, float y) {
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box b = boxes.get(i);
            if (b.header.contains(x, y)) return b;
            if (b.node.isIf && (inRect(b.elseBar, x, y) || inRect(b.bottom, x, y) || inRect(b.spine, x, y))) return b;
        }
        return null;
    }

    private static boolean inRect(RectF r, float x, float y) {
        return r != null && r.contains(x, y);
    }

    private String rootParamAt(Box b, float x, float y) {
        if (b == null) return null;
        for (int i = 0; i < b.params.size(); i++) {
            RectF r = new RectF(b.params.get(i));
            r.inset(-3 * d, -4 * d);
            if (r.contains(x, y)) return b.paramNames.get(i);
        }
        return null;
    }

    private Slot deepest(Slot s, float x, float y) {
        RectF r = new RectF(s.r);
        r.inset(-3 * d, -4 * d);
        if (!r.contains(x, y)) return null;
        if (s.param) for (Object o : s.parts)
            if (o instanceof Slot) {
                Slot in = deepest((Slot) o, x, y);
                if (in != null) return in;
            }
        return s;
    }

    private Slot slotAt(Box b, float x, float y) {
        if (b == null) return null;
        for (Slot s : b.slots) {
            Slot r = deepest(s, x, y);
            if (r != null) return r;
        }
        return null;
    }

    private Slot slotAtWorld(float x, float y) {
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box b = boxes.get(i);
            RectF h = new RectF(b.header);
            h.inset(-4 * d, -4 * d);
            if (!h.contains(x, y)) continue;
            Slot s = slotAt(b, x, y);
            if (s != null) return s;
        }
        return null;
    }

    private void tap(float sx, float sy) {
        if (dirty) doLayout();
        float x = wx(sx), y = wy(sy);
        Box b = boxAt(x, y);
        if (b == null) {
            selected = null;
            activeField = null;
            invalidate();
            listener.onSelect(null);
            return;
        }
        RectF inf = new RectF(b.info);
        inf.inset(-6 * d, -6 * d);
        if (inf.contains(x, y)) {
            listener.onInfo(b.node);
            return;
        }
        String rp = rootParamAt(b, x, y);
        if (rp != null) {
            listener.onRootParam(rp);
            return;
        }
        Slot s = slotAt(b, x, y);
        if (s != null) {
            selected = b.node;
            activeField = s.field;
            invalidate();
            listener.onFieldTap(b.node, s.field);
            return;
        }
        selected = b.node;
        invalidate();
        listener.onSelect(b.node);
    }

    // ================================================================ touch

    private float lastX, lastY, downX, downY, lastFX, lastFY;
    private boolean panning, pendingDrag, multi;
    private Box downBox, pendingMenu;
    private String downParam;
    private boolean paramArmed;
    private Slot downSlot;

    private void focus(MotionEvent e) {
        float sx = 0, sy = 0;
        int n = e.getPointerCount(), skip = e.getActionMasked() == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1, cnt = 0;
        for (int i = 0; i < n; i++) {
            if (i == skip) continue;
            sx += e.getX(i);
            sy += e.getY(i);
            cnt++;
        }
        if (cnt == 0) return;
        lastFX = sx / cnt;
        lastFY = sy / cnt;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        if (!isDragging()) gestures.onTouchEvent(e);
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                downX = lastX = x;
                downY = lastY = y;
                multi = false;
                pendingMenu = null;
                if (dirty) doLayout();
                downBox = isDragging() ? null : boxAt(wx(x), wy(y));
                downSlot = downBox != null ? slotAt(downBox, wx(x), wy(y)) : null;
                downParam = downBox != null ? rootParamAt(downBox, wx(x), wy(y)) : null;
                paramArmed = false;
                dragArmed = false;
                pendingDrag = downBox != null;
                // a swipe pans even when it starts on a block; the block is dragged only after a long press
                panning = !isDragging();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                pendingDrag = false;
                pendingMenu = null;
                panning = false;
                multi = true;
                focus(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() > 1) {
                    // two-finger pan alongside pinch
                    float ofx = lastFX, ofy = lastFY;
                    focus(e);
                    if (!isDragging()) {
                        tx += lastFX - ofx;
                        ty += lastFY - ofy;
                        invalidate();
                    }
                    return true;
                }
                if (drag != null) {
                    moveDrag(x, y);
                    return true;
                }
                if (dragParam != null) {
                    moveParam(x, y);
                    return true;
                }
                float slop = 10 * d;
                if (pendingDrag && !multi && !dragArmed && (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop)) {
                    // moved before the long press fired: this is a pan, not a drag
                    pendingDrag = false;
                    pendingMenu = null;
                }
                if (pendingDrag && dragArmed && !multi && (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop)) {
                    pendingMenu = null;
                    pendingDrag = false;
                    dragArmed = false;
                    if (paramArmed && downParam != null) beginExternalParamDrag(new BlockModel.Param(downParam, downParam), x, y);
                    else if (downSlot != null && downSlot.field.block != null) startParamDrag(downSlot, x, y);
                    else startDrag(downBox, x, y);
                    return true;
                }
                if (panning && !multi) {
                    tx += x - lastX;
                    ty += y - lastY;
                    invalidate();
                }
                lastX = x;
                lastY = y;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean cancel = e.getActionMasked() == MotionEvent.ACTION_CANCEL;
                if (drag != null) endDrag(cancel);
                else if (dragParam != null) endParam(cancel);
                else if (paramArmed && !cancel && downParam != null) listener.onRootParam(downParam);
                else if (pendingMenu != null && !cancel) {
                    Box b = pendingMenu;
                    listener.onLongPress(b.node, e.getRawX(), e.getRawY());
                }
                pendingMenu = null;
                pendingDrag = false;
                dragArmed = false;
                panning = false;
                invalidate();
                return true;
            }
        }
        return true;
    }

    private void autoScroll(float sx, float sy) {
        float edge = 36 * d;
        if (sx < 0 || sy < 0 || sx > getWidth() || sy > getHeight()) return;
        if (sx < edge) tx += 12 * d; else if (sx > getWidth() - edge) tx -= 12 * d;
        if (sy < edge) ty += 12 * d; else if (sy > getHeight() - edge) ty -= 12 * d;
    }

    // ---------------------------------------------------------------- block drag

    private void startDrag(Box b, float sx, float sy) {
        BlockModel.Node n = b.node;
        listener.beforeChange();
        boolean isHead = model.heads.contains(n);
        if (isHead) {
            model.heads.remove(n);
        } else {
            Object[] p = parentOf.get(n);
            if (p != null) {
                BlockModel.Node par = (BlockModel.Node) p[0];
                int slot = (Integer) p[1];
                if (slot == 0) par.next = null;
                else if (slot == 1) par.yes = null;
                else par.no = null;
            }
        }
        drag = n;
        dragExternal = false;
        grabDX = wx(sx) - (mirror ? b.header.right : b.header.left);
        grabDY = wy(sy) - b.header.top;
        dirty = true;
        moveDrag(sx, sy);
    }

    /** Start dragging a new chain coming from the palette (coords relative to this view). */
    public void beginExternalDrag(BlockModel.Node chain, float sx, float sy) {
        drag = chain;
        dragExternal = true;
        grabDX = (mirror ? -20 : 20) * d / scale;
        grabDY = 14 * d / scale;
        dirty = true;
        moveDrag(sx, sy);
    }

    /** Start dragging a new value block from the palette. */
    public void beginExternalParamDrag(BlockModel.Param p, float sx, float sy) {
        dragParam = p;
        paramOrigin = null;
        paramExternal = true;
        dirty = true;
        moveParam(sx, sy);
    }

    public boolean isDragging() { return drag != null || dragParam != null; }

    /** For drags forwarded from outside the canvas. */
    public void externalMove(float sx, float sy) {
        if (drag != null) moveDrag(sx, sy);
        else if (dragParam != null) moveParam(sx, sy);
    }

    public void externalUp(float sx, float sy, boolean cancel) {
        boolean inside = sx >= 0 && sy >= 0 && sx <= getWidth() && sy <= getHeight();
        if (drag != null) {
            moveDrag(sx, sy);
            endDrag(cancel || !inside);
        } else if (dragParam != null) {
            moveParam(sx, sy);
            endParam(cancel || !inside);
        }
    }

    private void moveDrag(float sx, float sy) {
        dragX = wx(sx) - grabDX;
        dragY = wy(sy) - grabDY;
        if (dirty) doLayout();
        dragBoxes.clear();
        layoutChainForDrag();
        overTrash = trashRect().contains(sx, sy) && !drag.isRoot();
        hover = null;
        if (!drag.isRoot() && !overTrash) {
            float best = 44 * d / scale;
            for (Target t : targets) {
                float dd = (float) Math.hypot(t.x - dragX, t.y - dragY);
                if (dd < best) { best = dd; hover = t; }
            }
            if (hover == null) {
                // finger over the drop zone just after a target (where the tutorial hand points)
                float fx = wx(sx), fy = wy(sy), dir = mirror ? -1 : 1;
                float best2 = 52 * d / scale;
                for (Target t : targets) {
                    if (t.slot == -1) continue;
                    float dd = (float) Math.hypot(t.x + dir * 60 * d - fx, t.y + 15 * d - fy);
                    if (dd < best2) { best2 = dd; hover = t; }
                }
            }
        }
        autoScroll(sx, sy);
        invalidate();
    }

    private void layoutChainForDrag() {
        ArrayList<Box> tmp = new ArrayList<>();
        IdentityHashMap<BlockModel.Node, Object[]> saved = new IdentityHashMap<>(parentOf);
        ArrayList<Target> savedTargets = new ArrayList<>(targets);
        layoutChain(drag, dragX, dragY, null, -1, tmp, false);
        if (mirror) mirrorRange(tmp, 0, null, 0, dragX);
        for (Box b : tmp) finalizeBox(b);
        parentOf.clear();
        parentOf.putAll(saved);
        targets.clear();
        targets.addAll(savedTargets);
        dragBoxes.addAll(tmp);
    }

    private void endDrag(boolean cancel) {
        BlockModel.Node chain = drag;
        drag = null;
        boolean changedModel = true;
        if (dragExternal && (cancel || overTrash)) {
            changedModel = false; // dropped back on the palette / trash: nothing added
        } else if (overTrash && !chain.isRoot()) {
            if (selected != null) selected = null;
        } else if (hover != null && !chain.isRoot() && !cancel) {
            if (dragExternal) listener.beforeChange();
            BlockModel.Node tail = chain.tail();
            if (hover.slot == -1) {
                int idx = model.heads.indexOf(hover.head);
                tail.next = hover.head;
                chain.x = hover.head.x;
                chain.y = hover.head.y;
                if (idx >= 0) model.heads.set(idx, chain); else model.heads.add(chain);
            } else {
                BlockModel.Node p = hover.parent;
                BlockModel.Node old = hover.slot == 0 ? p.next : (hover.slot == 1 ? p.yes : p.no);
                tail.next = old;
                if (hover.slot == 0) p.next = chain;
                else if (hover.slot == 1) p.yes = chain;
                else p.no = chain;
            }
            selected = chain;
        } else {
            if (dragExternal) listener.beforeChange();
            chain.x = dragX;
            chain.y = dragY;
            if (chain.isRoot()) model.heads.add(0, chain); else model.heads.add(chain);
            selected = chain;
        }
        hover = null;
        overTrash = false;
        dragBoxes.clear();
        dirty = true;
        invalidate();
        if (changedModel) listener.afterChange();
    }

    // ---------------------------------------------------------------- value block drag

    private void startParamDrag(Slot s, float sx, float sy) {
        listener.beforeChange();
        dragParam = s.field.block;
        paramOrigin = s.field;
        paramExternal = false;
        s.field.block = null;
        if (activeField == s.field) activeField = null;
        dirty = true;
        moveParam(sx, sy);
    }

    private void moveParam(float sx, float sy) {
        if (dirty) doLayout();
        float fx = wx(sx), fy = wy(sy);
        BlockModel.Field gf = new BlockModel.Field("___", "");
        gf.block = dragParam;
        Slot g = makeSlot(null, gf, false);
        float off = 10 * d / scale;
        float gx = mirror ? fx + off - g.w : fx - off;
        float gy = fy - 30 * d / scale;
        placeSlot(g, gx, gy);
        if (mirror) mirrorSlot(g, g.r.centerX());
        finalizeSlot(g);
        paramGhost = g;
        overTrash = trashRect().contains(sx, sy);
        hoverSlot = null;
        if (!overTrash) {
            float px = mirror ? g.r.right - 6 * d : g.r.left + 6 * d;
            hoverSlot = slotAtWorld(px, g.r.centerY());
            if (hoverSlot == null) hoverSlot = slotAtWorld(fx, fy);
        }
        autoScroll(sx, sy);
        invalidate();
    }

    private void endParam(boolean cancel) {
        BlockModel.Param p = dragParam;
        dragParam = null;
        paramGhost = null;
        boolean changedModel = false;
        if (cancel) {
            if (!paramExternal && paramOrigin != null) {
                paramOrigin.block = p;
                changedModel = true;
            }
        } else if (overTrash) {
            changedModel = !paramExternal; // already detached = deleted
        } else if (hoverSlot != null) {
            if (paramExternal) listener.beforeChange();
            BlockModel.Field t = hoverSlot.field;
            BlockModel.Param old = t.block;
            t.block = p;
            // swap: the replaced value block goes back where the dragged one came from
            if (old != null && paramOrigin != null && paramOrigin != t && paramOrigin.block == null) paramOrigin.block = old;
            selected = hoverSlot.node != null ? hoverSlot.node : selected;
            changedModel = true;
        } else {
            if (!paramExternal && paramOrigin != null) {
                paramOrigin.block = p;
                changedModel = true;
            }
            listener.onDropHint();
        }
        hoverSlot = null;
        overTrash = false;
        paramOrigin = null;
        dirty = true;
        invalidate();
        if (changedModel) listener.afterChange();
    }

    // ================================================================ editing helpers (used by the editor)

    /** Insert a chain after the selected block, or at the end of the root stack. */
    public void insert(BlockModel.Node chain) {
        listener.beforeChange();
        BlockModel.Node anchor = selected;
        if (anchor != null) {
            BlockModel.Node tail = chain.tail();
            tail.next = anchor.next;
            anchor.next = chain;
        } else {
            BlockModel.Node root = model.root();
            if (root != null) root.tail().next = chain;
            else {
                float[] w = centerWorld();
                chain.x = w[0];
                chain.y = w[1];
                model.heads.add(chain);
            }
        }
        selected = chain;
        dirty = true;
        invalidate();
        listener.afterChange();
    }

    /** Remove a single block (keeps the blocks after it, and lifts its inner blocks out). */
    public void deleteSingle(BlockModel.Node n) {
        if (n.isRoot()) return;
        listener.beforeChange();
        if (dirty) doLayout();
        BlockModel.Node replacement = n.next;
        if (n.yes != null || n.no != null) {
            BlockModel.Node inner = null;
            for (BlockModel.Node part : new BlockModel.Node[]{n.yes, n.no}) {
                if (part == null) continue;
                if (inner == null) inner = part; else inner.tail().next = part;
            }
            if (inner != null) {
                inner.tail().next = n.next;
                replacement = inner;
            }
        }
        Object[] p = parentOf.get(n);
        if (p != null) {
            BlockModel.Node par = (BlockModel.Node) p[0];
            int slot = (Integer) p[1];
            if (slot == 0) par.next = replacement;
            else if (slot == 1) par.yes = replacement;
            else par.no = replacement;
        } else {
            int idx = model.heads.indexOf(n);
            if (idx >= 0) {
                if (replacement != null) {
                    replacement.x = n.x;
                    replacement.y = n.y;
                    model.heads.set(idx, replacement);
                } else model.heads.remove(idx);
            }
        }
        if (selected == n) selected = null;
        activeField = null;
        dirty = true;
        invalidate();
        listener.afterChange();
    }

    /** Duplicate one block (with its inner blocks) right after itself. */
    public void duplicate(BlockModel.Node n) {
        if (n.isRoot()) return;
        listener.beforeChange();
        BlockModel.Node copy = n.copySingle();
        if (n.yes != null) copy.yes = n.yes.copyChain();
        if (n.no != null) copy.no = n.no.copyChain();
        copy.next = n.next;
        n.next = copy;
        selected = copy;
        dirty = true;
        invalidate();
        listener.afterChange();
    }

    public void changed() {
        dirty = true;
        invalidate();
    }

    // ================================================================ tutorial helpers

    /** First node with this title (depth first from the root stack). */
    public BlockModel.Node findNode(String title) {
        if (model == null) return null;
        java.util.ArrayList<BlockModel.Node> all = new java.util.ArrayList<>();
        for (BlockModel.Node h : model.heads) collect(h, all);
        for (BlockModel.Node n : all) if (title.equals(n.title)) return n;
        return null;
    }

    private static void collect(BlockModel.Node n, java.util.ArrayList<BlockModel.Node> out) {
        while (n != null) {
            out.add(n);
            collect(n.yes, out);
            collect(n.no, out);
            n = n.next;
        }
    }

    private Box boxOf(BlockModel.Node n) {
        if (dirty) doLayout();
        for (Box b : boxes) if (b.node == n) return b;
        return null;
    }

    private RectF toView(RectF w) {
        if (w == null) return null;
        return new RectF(w.left * scale + tx, w.top * scale + ty, w.right * scale + tx, w.bottom * scale + ty);
    }

    /** An event parameter chip on the root block, in this view's pixels. */
    public RectF viewRectOfRootParam(String name) {
        if (dirty) doLayout();
        for (Box b : boxes)
            if (b.node.isRoot())
                for (int i = 0; i < b.paramNames.size(); i++)
                    if (b.paramNames.get(i).equals(name)) return toView(b.params.get(i));
        return null;
    }

    /** Header of a block, in this view's pixels. */
    public RectF viewRectOfNode(BlockModel.Node n) {
        Box b = n == null ? null : boxOf(n);
        return b == null ? null : toView(b.header);
    }

    /** A field chip of a block (index in node.fields), in this view's pixels. */
    public RectF viewRectOfField(BlockModel.Node n, int index) {
        Box b = n == null ? null : boxOf(n);
        if (b == null || index < 0 || index >= b.slots.size()) return null;
        return toView(b.slots.get(index).r);
    }

    /** First empty inner field of the value block in a field. */
    public RectF viewRectOfInner(BlockModel.Node n, int index, int inner) {
        Box b = n == null ? null : boxOf(n);
        if (b == null || index < 0 || index >= b.slots.size()) return null;
        Slot s = b.slots.get(index);
        int k = 0;
        for (Object o : s.parts)
            if (o instanceof Slot) {
                if (k == inner) return toView(((Slot) o).r);
                k++;
            }
        return null;
    }

    /** A field nested in value blocks: path = inner indexes, level by level. */
    public RectF viewRectOfPath(BlockModel.Node n, int index, int[] path) {
        Box b = n == null ? null : boxOf(n);
        if (b == null || index < 0 || index >= b.slots.size()) return null;
        Slot s = b.slots.get(index);
        for (int want : path) {
            Slot next = null;
            int k = 0;
            for (Object o : s.parts)
                if (o instanceof Slot) {
                    if (k == want) { next = (Slot) o; break; }
                    k++;
                }
            if (next == null) return null;
            s = next;
        }
        return toView(s.r);
    }

    /** Where a block dropped after the last block of the root stack goes. */
    public RectF viewDropAfter(BlockModel.Node n) {
        Box b = n == null ? null : boxOf(n.tail());
        if (b == null) return null;
        float w = 120 * d;
        RectF r = mirror ? new RectF(b.header.right - w, b.all.bottom, b.header.right, b.all.bottom + 30 * d)
                : new RectF(b.header.left, b.all.bottom, b.header.left + w, b.all.bottom + 30 * d);
        return toView(r);
    }

    /** The "then" (1) or "else" (2) opening of a C-block. */
    public RectF viewMouth(BlockModel.Node n, int slot) {
        Box b = n == null ? null : boxOf(n);
        if (b == null) return null;
        RectF m = slot == 2 ? b.noMouth : b.yesMouth;
        if (m == null) return null;
        float w = Math.min(120 * d, m.width());
        RectF r = mirror ? new RectF(m.right - w, m.top, m.right, m.top + 30 * d) : new RectF(m.left, m.top, m.left + w, m.top + 30 * d);
        return toView(r);
    }

    /** Scroll so this view rectangle is inside the canvas. */
    public void reveal(RectF v) {
        if (v == null || getWidth() == 0) return;
        float m = 24 * d, side = 72 * d, bottom = 72 * d; // keep clear of the zoom column and selection bar
        float left = mirror ? side : m, right = mirror ? m : side;
        float dx = 0, dy = 0;
        if (v.right > getWidth() - right) dx = getWidth() - right - v.right;
        if (v.left + dx < left) dx = left - v.left;
        if (v.bottom > getHeight() - bottom) dy = getHeight() - bottom - v.bottom;
        if (v.top + dy < m) dy = m - v.top;
        if (dx != 0 || dy != 0) {
            tx += dx;
            ty += dy;
            invalidate();
        }
    }

    // ================================================================ errors

    /** Blocks (by identity) that have compile errors, with the message to show. */
    public void setErrors(java.util.Map<BlockModel.Node, String> map) {
        errors.clear();
        if (map != null) errors.putAll(map);
        invalidate();
    }

    public String errorOf(BlockModel.Node n) {
        return n == null ? null : errors.get(n);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /** Smoothly scrolls so the block is centered, then pulses its outline. */
    public void animateTo(final BlockModel.Node n) {
        final Box b = n == null ? null : boxOf(n);
        if (b == null || getWidth() == 0) return;
        if (flyAnim != null) flyAnim.cancel();
        selected = n;
        final float sx = tx, sy = ty;
        final float ex = getWidth() / 2f - (b.header.centerX()) * scale;
        float ey = getHeight() / 2.6f - (b.header.centerY()) * scale;
        final float fey = ey;
        flashNode = n;
        flyAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        flyAnim.setDuration(650);
        flyAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        flyAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                tx = sx + (ex - sx) * f;
                ty = sy + (fey - sy) * f;
                invalidate();
            }
        });
        flyAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                pulse();
            }
        });
        flyAnim.start();
        listener.onSelect(n);
    }

    private void pulse() {
        android.animation.ValueAnimator p = android.animation.ValueAnimator.ofFloat(1f, 0f, 1f, 0f, 1f, 0f);
        p.setDuration(1400);
        p.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                flash = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        p.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                flash = 0f;
                flashNode = null;
                invalidate();
            }
        });
        p.start();
    }
}
