package com.star4droid.star2d.suda;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Small stand-alone 3D viewer (GLES 2): .glb / .gltf (node transforms, base colour + base colour texture) and .obj
 * (+ .mtl Kd / map_Kd). Orbit with one finger, pinch to zoom, two-finger drag to pan. It does not share the engine's
 * libGDX context, so it can open on top of the editor safely. Skinned / animated models are shown in their bind pose.
 */
public class SimpleModelView extends GLSurfaceView {

    static final class Part {
        float[] data; // x y z nx ny nz u v
        float[] color = {0.8f, 0.8f, 0.8f, 1f};
        Bitmap texture;
        int tex = 0, count;
        FloatBuffer buf;
    }

    private final List<Part> parts = new ArrayList<>();
    private float yaw = 30, pitch = 20, dist = 3, panX, panY;
    private float[] center = {0, 0, 0};
    private float radius = 1;
    private String error;
    private final ScaleGestureDetector scale;
    private float lx, ly;
    private int pointers;

    public SimpleModelView(Context c) {
        super(c);
        setEGLContextClientVersion(2);
        setRenderer(new R());
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        scale = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                dist = Math.max(radius * 0.3f, Math.min(radius * 20, dist / d.getScaleFactor()));
                requestRender();
                return true;
            }
        });
    }

    public String error() { return error; }

    /** Loads the model (call off the UI thread); returns false with error() set when it can't. */
    public boolean load(File f) {
        try {
            List<Part> ps = new ArrayList<>();
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".obj")) loadObj(f, ps);
            else if (n.endsWith(".glb") || n.endsWith(".gltf")) loadGltf(f, ps);
            else throw new Exception(com.star4droid.star2d.ui.Tr.t("s3d_viewer_format", "This format can't be previewed; you can still save it."));
            if (ps.isEmpty()) throw new Exception("No meshes");
            float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (Part p : ps) for (int i = 0; i < p.data.length; i += 8) for (int k = 0; k < 3; k++) {
                min[k] = Math.min(min[k], p.data[i + k]);
                max[k] = Math.max(max[k], p.data[i + k]);
            }
            center = new float[]{(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2};
            radius = Math.max(0.001f, (float) Math.sqrt(sq(max[0] - min[0]) + sq(max[1] - min[1]) + sq(max[2] - min[2])) / 2);
            dist = radius * 2.6f;
            queueEvent(() -> { parts.clear(); parts.addAll(ps); });
            requestRender();
            return true;
        } catch (Throwable t) {
            error = t.getMessage() == null ? t.toString() : t.getMessage();
            return false;
        }
    }

    static float sq(float v) { return v * v; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scale.onTouchEvent(e);
        int pc = e.getPointerCount();
        float x = 0, y = 0;
        for (int i = 0; i < pc; i++) { x += e.getX(i); y += e.getY(i); }
        x /= pc; y /= pc;
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN || pc != pointers) { lx = x; ly = y; pointers = pc; return true; }
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = x - lx, dy = y - ly;
            if (pc == 1) {
                yaw -= dx * 0.4f;
                pitch = Math.max(-89, Math.min(89, pitch + dy * 0.4f));
            } else {
                float k = dist / Math.max(1, getHeight()) * 1.2f;
                panX -= dx * k;
                panY += dy * k;
            }
            lx = x; ly = y;
            requestRender();
        }
        return true;
    }

    // ------------------------------------------------------------ rendering

    private class R implements Renderer {
        int prog, aPos, aNor, aUv, uMvp, uModel, uColor, uTex, uUseTex;
        final float[] proj = new float[16], view = new float[16], mvp = new float[16], model = new float[16];

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            String vs = "uniform mat4 uMvp; attribute vec3 aPos; attribute vec3 aNor; attribute vec2 aUv; varying vec3 vN; varying vec2 vUv;"
                    + "void main(){ vN = aNor; vUv = aUv; gl_Position = uMvp * vec4(aPos,1.0); }";
            String fs = "precision mediump float; varying vec3 vN; varying vec2 vUv; uniform vec4 uColor; uniform sampler2D uTex; uniform float uUseTex;"
                    + "void main(){ vec3 n = normalize(vN); float d = max(dot(n, normalize(vec3(0.4,0.8,0.6))),0.0)*0.75 + max(dot(n, normalize(vec3(-0.5,-0.2,-0.7))),0.0)*0.2 + 0.32;"
                    + "vec4 base = uColor; if (uUseTex > 0.5) base *= texture2D(uTex, vUv); gl_FragColor = vec4(base.rgb * d, 1.0); }";
            prog = GLES20.glCreateProgram();
            GLES20.glAttachShader(prog, shader(GLES20.GL_VERTEX_SHADER, vs));
            GLES20.glAttachShader(prog, shader(GLES20.GL_FRAGMENT_SHADER, fs));
            GLES20.glLinkProgram(prog);
            aPos = GLES20.glGetAttribLocation(prog, "aPos");
            aNor = GLES20.glGetAttribLocation(prog, "aNor");
            aUv = GLES20.glGetAttribLocation(prog, "aUv");
            uMvp = GLES20.glGetUniformLocation(prog, "uMvp");
            uColor = GLES20.glGetUniformLocation(prog, "uColor");
            uTex = GLES20.glGetUniformLocation(prog, "uTex");
            uUseTex = GLES20.glGetUniformLocation(prog, "uUseTex");
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            for (Part p : parts) p.tex = 0; // new context: re-upload
        }

        int shader(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            return s;
        }

        @Override public void onSurfaceChanged(GL10 gl, int w, int h) {
            GLES20.glViewport(0, 0, w, h);
            Matrix.perspectiveM(proj, 0, 45, w / (float) Math.max(1, h), 0.01f, 1000f);
        }

        @Override public void onDrawFrame(GL10 gl) {
            GLES20.glClearColor(0.137f, 0.145f, 0.169f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (parts.isEmpty()) return;
            Matrix.perspectiveM(proj, 0, 45, getWidth() / (float) Math.max(1, getHeight()), radius * 0.01f, radius * 100f);
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            float ex = (float) (Math.cos(pr) * Math.sin(yr)) * dist, ey = (float) Math.sin(pr) * dist, ez = (float) (Math.cos(pr) * Math.cos(yr)) * dist;
            // pan in camera plane
            float rx = (float) Math.cos(yr), rz = (float) -Math.sin(yr);
            float tx = center[0] + rx * panX, ty = center[1] + panY, tz = center[2] + rz * panX;
            Matrix.setLookAtM(view, 0, tx + ex, ty + ey, tz + ez, tx, ty, tz, 0, 1, 0);
            Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);
            GLES20.glUseProgram(prog);
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
            for (Part p : parts) {
                if (p.buf == null) {
                    p.buf = ByteBuffer.allocateDirect(p.data.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    p.buf.put(p.data).position(0);
                    p.count = p.data.length / 8;
                }
                if (p.texture != null && p.tex == 0) {
                    int[] t = new int[1];
                    GLES20.glGenTextures(1, t, 0);
                    p.tex = t[0];
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, p.tex);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, p.texture, 0);
                }
                GLES20.glUniform4fv(uColor, 1, p.color, 0);
                GLES20.glUniform1f(uUseTex, p.tex != 0 ? 1f : 0f);
                if (p.tex != 0) {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, p.tex);
                    GLES20.glUniform1i(uTex, 0);
                }
                p.buf.position(0);
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 32, p.buf);
                GLES20.glEnableVertexAttribArray(aPos);
                p.buf.position(3);
                GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, 32, p.buf);
                GLES20.glEnableVertexAttribArray(aNor);
                p.buf.position(6);
                GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 32, p.buf);
                GLES20.glEnableVertexAttribArray(aUv);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, p.count);
            }
        }
    }

    // ------------------------------------------------------------ glTF

    private void loadGltf(File f, List<Part> out) throws Exception {
        byte[] all = readAll(f);
        JsonObject gl;
        ByteBuffer bin = null;
        ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        if (all.length > 12 && bb.getInt(0) == 0x46546C67) { // "glTF"
            int off = 12;
            String json = null;
            while (off + 8 <= all.length) {
                int len = bb.getInt(off), type = bb.getInt(off + 4);
                if (type == 0x4E4F534A) json = new String(all, off + 8, len, StandardCharsets.UTF_8);
                else if (type == 0x004E4942) bin = ByteBuffer.wrap(all, off + 8, len).slice().order(ByteOrder.LITTLE_ENDIAN);
                off += 8 + len;
            }
            gl = JsonParser.parseString(json).getAsJsonObject();
        } else gl = JsonParser.parseString(new String(all, StandardCharsets.UTF_8)).getAsJsonObject();
        if (gl.has("extensionsRequired")) for (JsonElement e : gl.getAsJsonArray("extensionsRequired"))
            if (e.getAsString().contains("draco") || e.getAsString().contains("meshopt"))
                throw new Exception(com.star4droid.star2d.ui.Tr.t("s3d_viewer_compressed", "Compressed mesh: preview not available, the file itself is fine."));
        List<ByteBuffer> buffers = new ArrayList<>();
        for (JsonElement e : arr(gl, "buffers")) {
            JsonObject b = e.getAsJsonObject();
            String uri = b.has("uri") ? b.get("uri").getAsString() : null;
            if (uri == null) buffers.add(bin);
            else if (uri.startsWith("data:")) buffers.add(ByteBuffer.wrap(android.util.Base64.decode(uri.substring(uri.indexOf(',') + 1), 0)).order(ByteOrder.LITTLE_ENDIAN));
            else buffers.add(ByteBuffer.wrap(readAll(new File(f.getParentFile(), java.net.URLDecoder.decode(uri, "UTF-8")))).order(ByteOrder.LITTLE_ENDIAN));
        }
        G g = new G(gl, buffers, f.getParentFile());
        JsonArray scenes = arr(gl, "scenes");
        JsonArray roots;
        if (scenes.size() > 0) roots = arr(scenes.get(gl.has("scene") ? gl.get("scene").getAsInt() : 0).getAsJsonObject(), "nodes");
        else { roots = new JsonArray(); for (int i = 0; i < arr(gl, "nodes").size(); i++) roots.add(i); }
        float[] id = new float[16];
        Matrix.setIdentityM(id, 0);
        for (JsonElement r : roots) g.node(r.getAsInt(), id, out, 0);
    }

    private static final class G {
        final JsonObject gl;
        final List<ByteBuffer> buffers;
        final File dir;
        final Map<Integer, Bitmap> images = new HashMap<>();
        G(JsonObject gl, List<ByteBuffer> b, File dir) { this.gl = gl; this.buffers = b; this.dir = dir; }

        void node(int i, float[] parent, List<Part> out, int depth) throws Exception {
            if (depth > 64) return;
            JsonObject n = arr(gl, "nodes").get(i).getAsJsonObject();
            float[] local = new float[16];
            Matrix.setIdentityM(local, 0);
            if (n.has("matrix")) { JsonArray m = n.getAsJsonArray("matrix"); for (int k = 0; k < 16; k++) local[k] = m.get(k).getAsFloat(); }
            else {
                float[] t = vec(n, "translation", 0, 0, 0), r = vec(n, "rotation", 0, 0, 0, 1), s = vec(n, "scale", 1, 1, 1);
                float x = r[0], y = r[1], z = r[2], w = r[3];
                float[] rm = {1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0,
                        2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0,
                        2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0, 0, 0, 0, 1};
                for (int k = 0; k < 4; k++) { rm[k] *= s[0]; rm[4 + k] *= s[1]; rm[8 + k] *= s[2]; }
                rm[12] = t[0]; rm[13] = t[1]; rm[14] = t[2];
                local = rm;
            }
            float[] world = new float[16];
            Matrix.multiplyMM(world, 0, parent, 0, local, 0);
            if (n.has("mesh")) mesh(n.get("mesh").getAsInt(), world, out);
            for (JsonElement ch : arr(n, "children")) node(ch.getAsInt(), world, out, depth + 1);
        }

        void mesh(int mi, float[] m, List<Part> out) throws Exception {
            JsonObject mesh = arr(gl, "meshes").get(mi).getAsJsonObject();
            float[] nm = new float[16], inv = new float[16];
            Matrix.invertM(inv, 0, m, 0);
            Matrix.transposeM(nm, 0, inv, 0);
            for (JsonElement pe : arr(mesh, "primitives")) {
                JsonObject p = pe.getAsJsonObject();
                if (p.has("mode") && p.get("mode").getAsInt() != 4) continue;
                JsonObject at = p.getAsJsonObject("attributes");
                if (at == null || !at.has("POSITION")) continue;
                float[] pos = floats(at.get("POSITION").getAsInt());
                float[] nor = at.has("NORMAL") ? floats(at.get("NORMAL").getAsInt()) : null;
                float[] uv = at.has("TEXCOORD_0") ? floats(at.get("TEXCOORD_0").getAsInt()) : null;
                int vc = pos.length / 3;
                int[] idx;
                if (p.has("indices")) idx = ints(p.get("indices").getAsInt());
                else { idx = new int[vc]; for (int k = 0; k < vc; k++) idx[k] = k; }
                Part part = new Part();
                part.data = new float[idx.length * 8];
                float[] v4 = new float[4], r4 = new float[4];
                for (int k = 0; k < idx.length; k++) {
                    int vi = idx[k];
                    if (vi >= vc) continue;
                    v4[0] = pos[vi * 3]; v4[1] = pos[vi * 3 + 1]; v4[2] = pos[vi * 3 + 2]; v4[3] = 1;
                    Matrix.multiplyMV(r4, 0, m, 0, v4, 0);
                    int o = k * 8;
                    part.data[o] = r4[0]; part.data[o + 1] = r4[1]; part.data[o + 2] = r4[2];
                    if (nor != null) {
                        v4[0] = nor[vi * 3]; v4[1] = nor[vi * 3 + 1]; v4[2] = nor[vi * 3 + 2]; v4[3] = 0;
                        Matrix.multiplyMV(r4, 0, nm, 0, v4, 0);
                        part.data[o + 3] = r4[0]; part.data[o + 4] = r4[1]; part.data[o + 5] = r4[2];
                    }
                    if (uv != null) { part.data[o + 6] = uv[vi * 2]; part.data[o + 7] = uv[vi * 2 + 1]; }
                }
                if (nor == null) flatNormals(part.data);
                if (p.has("material")) material(p.get("material").getAsInt(), part);
                out.add(part);
            }
        }

        void material(int i, Part part) {
            try {
                JsonObject mat = arr(gl, "materials").get(i).getAsJsonObject();
                JsonObject pbr = mat.getAsJsonObject("pbrMetallicRoughness");
                if (pbr == null) return;
                if (pbr.has("baseColorFactor")) part.color = vec(pbr, "baseColorFactor", 1, 1, 1, 1);
                if (pbr.has("baseColorTexture")) {
                    int ti = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
                    JsonObject tex = arr(gl, "textures").get(ti).getAsJsonObject();
                    if (tex.has("source")) part.texture = image(tex.get("source").getAsInt());
                }
            } catch (Exception ignored) {}
        }

        Bitmap image(int i) throws Exception {
            if (images.containsKey(i)) return images.get(i);
            JsonObject im = arr(gl, "images").get(i).getAsJsonObject();
            byte[] bytes;
            if (im.has("bufferView")) {
                ByteBuffer v = view(im.get("bufferView").getAsInt());
                bytes = new byte[v.remaining()];
                v.get(bytes);
            } else {
                String uri = im.get("uri").getAsString();
                bytes = uri.startsWith("data:") ? android.util.Base64.decode(uri.substring(uri.indexOf(',') + 1), 0)
                        : readAll(new File(dir, java.net.URLDecoder.decode(uri, "UTF-8")));
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            o.inSampleSize = 1;
            while (Math.max(o.outWidth, o.outHeight) / o.inSampleSize > 2048) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            images.put(i, b);
            return b;
        }

        ByteBuffer view(int i) {
            JsonObject v = arr(gl, "bufferViews").get(i).getAsJsonObject();
            ByteBuffer b = buffers.get(v.get("buffer").getAsInt()).duplicate().order(ByteOrder.LITTLE_ENDIAN);
            int off = v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0;
            b.position(off);
            b.limit(off + v.get("byteLength").getAsInt());
            return b.slice().order(ByteOrder.LITTLE_ENDIAN);
        }

        static int comps(String type) {
            switch (type) { case "SCALAR": return 1; case "VEC2": return 2; case "VEC3": return 3; case "VEC4": return 4; case "MAT4": return 16; default: return 1; }
        }

        static int size(int ct) { return ct == 5126 || ct == 5125 ? 4 : ct == 5123 || ct == 5122 ? 2 : 1; }

        double read(ByteBuffer b, int at, int ct, boolean norm) {
            switch (ct) {
                case 5126: return b.getFloat(at);
                case 5125: return b.getInt(at) & 0xFFFFFFFFL;
                case 5123: return norm ? (b.getShort(at) & 0xFFFF) / 65535.0 : (b.getShort(at) & 0xFFFF);
                case 5122: return norm ? Math.max(b.getShort(at) / 32767.0, -1) : b.getShort(at);
                case 5121: return norm ? (b.get(at) & 0xFF) / 255.0 : (b.get(at) & 0xFF);
                default: return norm ? Math.max(b.get(at) / 127.0, -1) : b.get(at);
            }
        }

        double[] acc(int i) {
            JsonObject a = arr(gl, "accessors").get(i).getAsJsonObject();
            int count = a.get("count").getAsInt(), ct = a.get("componentType").getAsInt(), nc = comps(a.get("type").getAsString());
            boolean norm = a.has("normalized") && a.get("normalized").getAsBoolean();
            double[] outv = new double[count * nc];
            if (!a.has("bufferView")) return outv;
            ByteBuffer b = view(a.get("bufferView").getAsInt());
            int off = a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0;
            JsonObject bv = arr(gl, "bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
            int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : nc * size(ct);
            for (int k = 0; k < count; k++) for (int c = 0; c < nc; c++) outv[k * nc + c] = read(b, off + k * stride + c * size(ct), ct, norm);
            return outv;
        }

        float[] floats(int i) { double[] d = acc(i); float[] f = new float[d.length]; for (int k = 0; k < d.length; k++) f[k] = (float) d[k]; return f; }
        int[] ints(int i) { double[] d = acc(i); int[] r = new int[d.length]; for (int k = 0; k < d.length; k++) r[k] = (int) d[k]; return r; }
    }

    static float[] vec(JsonObject o, String k, float... def) {
        if (o == null || !o.has(k)) return def;
        JsonArray a = o.getAsJsonArray(k);
        float[] r = def.clone();
        for (int i = 0; i < Math.min(r.length, a.size()); i++) r[i] = a.get(i).getAsFloat();
        return r;
    }

    static JsonArray arr(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }

    static void flatNormals(float[] d) {
        for (int t = 0; t + 24 <= d.length; t += 24) {
            float ax = d[t + 8] - d[t], ay = d[t + 9] - d[t + 1], az = d[t + 10] - d[t + 2];
            float bx = d[t + 16] - d[t], by = d[t + 17] - d[t + 1], bz = d[t + 18] - d[t + 2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            for (int v = 0; v < 3; v++) { d[t + v * 8 + 3] = nx; d[t + v * 8 + 4] = ny; d[t + v * 8 + 5] = nz; }
        }
    }

    static byte[] readAll(File f) throws Exception {
        byte[] b = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
        }
        return b;
    }

    // ------------------------------------------------------------ OBJ

    private void loadObj(File f, List<Part> out) throws Exception {
        ArrayList<float[]> v = new ArrayList<>(), vn = new ArrayList<>(), vt = new ArrayList<>();
        Map<String, Object[]> mats = new HashMap<>(); // name -> {color float[], texture File}
        Map<String, ArrayList<Float>> groups = new java.util.LinkedHashMap<>();
        String cur = "";
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) v.add(nums(line));
                else if (line.startsWith("vn ")) vn.add(nums(line));
                else if (line.startsWith("vt ")) vt.add(nums(line));
                else if (line.startsWith("mtllib ")) readMtl(new File(f.getParentFile(), line.substring(7).trim()), mats);
                else if (line.startsWith("usemtl ")) cur = line.substring(7).trim();
                else if (line.startsWith("f ")) {
                    String[] p = line.substring(2).trim().split("\\s+");
                    ArrayList<Float> g = groups.get(cur);
                    if (g == null) groups.put(cur, g = new ArrayList<>());
                    for (int k = 1; k + 1 < p.length; k++) for (String s : new String[]{p[0], p[k], p[k + 1]}) {
                        String[] ix = s.split("/");
                        float[] pv = get(v, ix[0]);
                        float[] t = ix.length > 1 && !ix[1].isEmpty() ? get(vt, ix[1]) : null;
                        float[] n = ix.length > 2 && !ix[2].isEmpty() ? get(vn, ix[2]) : null;
                        g.add(pv[0]); g.add(pv[1]); g.add(pv[2]);
                        g.add(n == null ? 0 : n[0]); g.add(n == null ? 0 : n[1]); g.add(n == null ? 0 : n[2]);
                        g.add(t == null ? 0 : t[0]); g.add(t == null ? 0 : 1 - t[1]);
                    }
                }
            }
        }
        boolean hasN = !vn.isEmpty();
        for (Map.Entry<String, ArrayList<Float>> e : groups.entrySet()) {
            Part p = new Part();
            p.data = new float[e.getValue().size()];
            for (int i = 0; i < p.data.length; i++) p.data[i] = e.getValue().get(i);
            if (!hasN) flatNormals(p.data);
            Object[] m = mats.get(e.getKey());
            if (m != null) {
                p.color = (float[]) m[0];
                File tf = (File) m[1];
                if (tf != null && tf.exists()) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = tf.length() > 8_000_000 ? 2 : 1;
                    p.texture = BitmapFactory.decodeFile(tf.getPath(), o);
                    if (p.texture != null) p.color = new float[]{1, 1, 1, 1};
                }
            }
            out.add(p);
        }
    }

    static float[] get(ArrayList<float[]> l, String s) {
        int i = Integer.parseInt(s);
        i = i < 0 ? l.size() + i : i - 1;
        return i >= 0 && i < l.size() ? l.get(i) : new float[]{0, 0, 0};
    }

    static float[] nums(String line) {
        String[] p = line.split("\\s+");
        float[] r = new float[Math.max(3, p.length - 1)];
        for (int i = 1; i < p.length && i - 1 < r.length; i++) try { r[i - 1] = Float.parseFloat(p[i]); } catch (Exception ignored) {}
        return r;
    }

    static void readMtl(File f, Map<String, Object[]> mats) {
        if (!f.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line, cur = null;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("newmtl ")) { cur = line.substring(7).trim(); mats.put(cur, new Object[]{new float[]{0.8f, 0.8f, 0.8f, 1}, null}); }
                else if (cur == null) continue;
                else if (line.startsWith("Kd ")) { float[] k = nums(line); mats.get(cur)[0] = new float[]{k[0], k[1], k[2], 1}; }
                else if (line.startsWith("map_Kd ")) {
                    String[] p = line.split("\\s+");
                    mats.get(cur)[1] = new File(f.getParentFile(), p[p.length - 1]);
                }
            }
        } catch (Exception ignored) {}
    }
}
