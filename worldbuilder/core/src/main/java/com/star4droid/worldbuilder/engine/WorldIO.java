package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** World files: worlds/NAME.wbw (gzip) and NAME.png (picture for the worlds list). */
public final class WorldIO {
    private WorldIO() {}
    private static final int MAGIC = 0x57425731;   // "WBW1"
    /** 1: first version, 2: element colliders. */
    static final int VERSION = 2;

    /** Folder of the saved worlds (inside Star2D it is set to "worldbuilder/worlds"). */
    public static String folder = "worlds";

    public static FileHandle dir() { FileHandle d = Gdx.files.local(folder); if (!d.exists()) d.mkdirs(); return d; }

    public static String fileName(String worldName) {
        StringBuilder sb = new StringBuilder();
        for (char c : worldName.trim().toCharArray()) sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
        return sb.length() == 0 ? "world" : sb.toString();
    }

    public static FileHandle file(String worldName) { return dir().child(fileName(worldName) + ".wbw"); }
    public static FileHandle picture(String worldName) { return dir().child(fileName(worldName) + ".png"); }
    public static boolean exists(String worldName) { return file(worldName).exists(); }

    /** Names of the saved worlds, newest first. */
    public static Array<String> list() {
        Array<FileHandle> fs = new Array<>(dir().list(".wbw"));
        fs.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        Array<String> out = new Array<>();
        for (FileHandle f : fs) {
            String n = f.nameWithoutExtension();
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(f.read())))) {
                if (in.readInt() == MAGIC) { in.readInt(); n = in.readUTF(); }
            } catch (Exception ignored) { }
            out.add(n);
        }
        return out;
    }

    public static void delete(String worldName) { file(worldName).delete(); picture(worldName).delete(); }

    public static void save(Scene s) throws IOException {
        FileHandle tmp = dir().child(fileName(s.name) + ".tmp");
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(tmp.write(false))))) {
            o.writeInt(MAGIC); o.writeInt(VERSION);
            o.writeUTF(s.name);
            Env e = s.env;
            o.writeFloat(e.time); o.writeFloat(e.fog); o.writeFloat(e.clouds); o.writeFloat(e.wind); o.writeFloat(e.waterLevel); o.writeFloat(e.sunYaw); o.writeBoolean(e.water);
            for (int i = 0; i < 3; i++) o.writeFloat(e.shallow[i]);
            for (int i = 0; i < 3; i++) o.writeFloat(e.deep[i]);
            for (float c : s.camera) o.writeFloat(c);
            o.writeInt(Terrain.N);
            for (float h : s.terrain.h) o.writeShort(Math.round(Math.max(-320, Math.min(320, h)) * 100));
            o.write(s.terrain.splat);
            o.writeInt(s.nextUid);
            o.writeInt(s.objects.size);
            for (SceneObject ob : s.objects) SceneObject.write(o, ob.state());
        }
        FileHandle dst = file(s.name);
        if (dst.exists()) dst.delete();
        tmp.moveTo(dst);
    }

    /** Reads a world (no GPU work: meshes are built afterwards, a few per frame). */
    public static Scene load(String worldName) throws IOException {
        Scene s = new Scene();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(file(worldName).read())))) {
            if (in.readInt() != MAGIC) throw new IOException("Not a world file");
            int version = in.readInt();
            if (version > VERSION) throw new IOException("This world was saved by a newer World Builder");
            s.name = in.readUTF();
            Env e = s.env;
            e.time = in.readFloat(); e.fog = in.readFloat(); e.clouds = in.readFloat(); e.wind = in.readFloat(); e.waterLevel = in.readFloat(); e.sunYaw = in.readFloat(); e.water = in.readBoolean();
            for (int i = 0; i < 3; i++) e.shallow[i] = in.readFloat();
            for (int i = 0; i < 3; i++) e.deep[i] = in.readFloat();
            for (int i = 0; i < s.camera.length; i++) s.camera[i] = in.readFloat();
            if (in.readInt() != Terrain.N) throw new IOException("Unsupported terrain size");
            for (int i = 0; i < s.terrain.h.length; i++) s.terrain.h[i] = in.readShort() / 100f;
            in.readFully(s.terrain.splat);
            int next = in.readInt(), n = in.readInt();
            for (int i = 0; i < n; i++) s.restore(SceneObject.read(in, version));
            s.nextUid = Math.max(s.nextUid, next);
            s.terrain.markAllDirty();
        }
        return s;
    }
}
