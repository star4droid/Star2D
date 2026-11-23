package com.star4droid.worldbuilder.editor;

import com.badlogic.gdx.utils.Array;
import com.star4droid.worldbuilder.engine.Scene;
import com.star4droid.worldbuilder.engine.SceneObject;
import com.star4droid.worldbuilder.engine.Terrain;

/** Undo / redo history of a world: element changes and terrain strokes. */
public final class Undo {
    public interface Cmd { void apply(Scene s, boolean undo); }

    /** Elements before and after (same index = same element; null = it did not exist). */
    public static final class Objects implements Cmd {
        final SceneObject.State[] before, after;
        public Objects(SceneObject.State[] before, SceneObject.State[] after) { this.before = before; this.after = after; }
        @Override public void apply(Scene s, boolean undo) {
            SceneObject.State[] from = undo ? after : before, to = undo ? before : after;
            for (int i = 0; i < from.length; i++) {
                SceneObject.State f = from[i], t = to[i];
                SceneObject o = f == null ? null : s.find(f.uid);
                if (t == null) { if (o != null) s.remove(o); }
                else if (o == null) s.restore(t);
                else o.apply(t);
            }
        }
    }

    /** A rectangle of the terrain grid before and after a stroke. */
    public static final class Ground implements Cmd {
        final int x0, z0, x1, z1; final float[] hb, ha; final byte[] sb, sa;
        public Ground(Terrain t, float[] h0, byte[] s0, int x0, int z0, int x1, int z1) {
            this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1;
            int w = x1 - x0 + 1, d = z1 - z0 + 1;
            hb = new float[w * d]; ha = new float[w * d]; sb = new byte[w * d * 4]; sa = new byte[w * d * 4];
            for (int z = 0; z < d; z++) {
                int src = (z0 + z) * Terrain.W + x0;
                System.arraycopy(h0, src, hb, z * w, w); System.arraycopy(t.h, src, ha, z * w, w);
                System.arraycopy(s0, src * 4, sb, z * w * 4, w * 4); System.arraycopy(t.splat, src * 4, sa, z * w * 4, w * 4);
            }
        }
        @Override public void apply(Scene s, boolean undo) {
            int w = x1 - x0 + 1, d = z1 - z0 + 1;
            for (int z = 0; z < d; z++) {
                int dst = (z0 + z) * Terrain.W + x0;
                System.arraycopy(undo ? hb : ha, z * w, s.terrain.h, dst, w);
                System.arraycopy(undo ? sb : sa, z * w * 4, s.terrain.splat, dst * 4, w * 4);
            }
            s.terrain.markAllDirty();
        }
    }

    /** Several changes undone together (a terrain stroke and the elements that follow the ground). */
    public static final class Multi implements Cmd {
        final Cmd[] cmds;
        public Multi(Cmd... cmds) { this.cmds = cmds; }
        @Override public void apply(Scene s, boolean undo) { if (undo) for (int i = cmds.length - 1; i >= 0; i--) cmds[i].apply(s, true); else for (Cmd c : cmds) c.apply(s, false); }
    }

    private final Array<Cmd> done = new Array<>(), undone = new Array<>();
    public int changes;

    public void push(Cmd c) { done.add(c); if (done.size > 80) done.removeIndex(0); undone.clear(); changes++; }
    public boolean canUndo() { return done.size > 0; }
    public boolean canRedo() { return undone.size > 0; }
    public void undo(Scene s) { if (done.size == 0) return; Cmd c = done.pop(); c.apply(s, true); undone.add(c); changes++; }
    public void redo(Scene s) { if (undone.size == 0) return; Cmd c = undone.pop(); c.apply(s, false); done.add(c); changes++; }
    public void clear() { done.clear(); undone.clear(); }
}
