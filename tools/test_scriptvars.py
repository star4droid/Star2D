#!/usr/bin/env python3
"""Tests of the script variables (auto-detect, sync, lists, generated assignments) - no phone needed:
  1. tools/behaviors_test/ScriptVarsTest.java: scanner for user scripts (public / package-private / static / @Expose, lists, enums),
     sync of the stored variables with the code, shape of the generated assignments;
  2. the generated assignments are compiled (Java 8) against the template classes and RUN against a real script class, including
     private @Expose fields, package-private fields, static fields, lists and arrays through the ScriptVars reflection helper.
Needs: the template module compiled once (bash gradlew :template:compileDebugJavaWithJavac) and the gradle cache (gdx, gson).
usage: python3 tools/test_scriptvars.py        exit code 1 on any failure
"""
import glob, os, subprocess, sys, tempfile, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SCRIPT = r'''package com.star4droid.Game.Scripts.main;

import java.util.*;
import com.badlogic.gdx.graphics.Color;
import com.star4droid.template.Items.*;
import com.star4droid.template.Utils.*;
import com.star4droid.template.behaviors.Hidden;
import com.star4droid.template.ui.UINodeRef;

public class HeroScript extends ItemScript {
    // public int commented = 1;
    /* public int blockComment = 2; */
    public int lives = 3;
    float timer = 1.5f;
    static int count;
    public static float gravity = -9.8f;
    private int hidden = 4;
    protected int prot = 1;
    @Expose private int secret = 7;
    @Expose protected String tag = "x";
    final int K = 5;
    static final int MAX = 10;
    transient int skip;
    @Hidden public int nope;
    public List<Integer> nums = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
    ArrayList<String> names = new ArrayList<>();
    float[] weights = {0.5f, 1};
    public PlayerItem target;
    List<PlayerItem> targets;
    ProgressItem[] bars;
    UINodeRef score;
    List<UINodeRef> labels;
    public Color tint = Color.RED;
    Mode mode = Mode.FLY;
    public enum Mode { WALK, FLY }
    enum Secret { A, B }
    Secret notPublicEnum;
    Object unsupported;
    Map<String, Integer> map;
    String title = "public int fake = 1;";
    LinkedList<Integer> bigList;

    public void onClick() { int local = 5; float inner; }
    class Inner { public int innerField; }
    static { count = 2; }
    { timer = 2; }
}
'''

# executes the generated statements against HeroScript (same package: package-private fields are readable)
RUN = r'''package com.star4droid.Game.Scripts.main;

import java.lang.reflect.Field;
import java.util.*;

public class RunGen {
    static class Holder { Object s; public Object getScript() { return s; } }
    static int failures = 0;
    static void check(boolean ok, String what) { if (!ok) { failures++; System.out.println("FAIL " + what); } else System.out.println("ok   " + what); }
    static Object get(Object o, String n) throws Exception { Field f = HeroScript.class.getDeclaredField(n); f.setAccessible(true); return f.get(o); }

    public static void main(String[] a) throws Exception {
        Holder holder = new Holder();
        HeroScript hs = new HeroScript();
        holder.s = hs;
        HeroScript.count = 0;
        // GENERATED
        check(hs.lives == 5, "public int set");
        check(hs.timer == 2.5f, "package-private float set through ScriptVars");
        check(HeroScript.count == 3, "package-private static int set on the class");
        check(HeroScript.gravity == -1.5f, "public static float set");
        check(((Integer) get(hs, "secret")).intValue() == 9, "@Expose private int set");
        check("yo \"q\"".equals(get(hs, "tag")), "@Expose protected String set");
        check(hs.nums.equals(Arrays.asList(7, 8)), "public List<Integer> set");
        check(hs.names.equals(Arrays.asList("a", "b")), "package-private ArrayList<String> set");
        check(Arrays.equals(hs.weights, new float[]{1.0f, 2.5f}), "float[] set");
        check(hs.tint.equals(com.badlogic.gdx.graphics.Color.valueOf("FF0000")), "Color set");
        check(hs.mode == HeroScript.Mode.WALK, "enum set");
        check(hs.bigList instanceof LinkedList && hs.bigList.get(0) == 1, "LinkedList set");
        check(((Integer) get(hs, "hidden")).intValue() == 4, "private field without @Expose untouched");
        System.exit(failures == 0 ? 0 : 1);
    }
}
'''


def find(pattern):
    r = glob.glob(os.path.expanduser(pattern), recursive=True)
    return sorted(r)[-1] if r else None


def main():
    cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
    jars = [find(cache + "/com.badlogicgames.gdx/gdx/*/*/gdx-1*.jar"), find(cache + "/com.badlogicgames.gdx/gdx-box2d/*/*/gdx-box2d-1*.jar"), find(cache + "/com.badlogicgames.gdx/gdx-bullet/*/*/gdx-bullet-1*.jar"), find(cache + "/com.github.mgsx-dev.gdx-gltf/gltf/*/*/gltf-*.jar"),
            find(cache + "/com.google.code.gson/gson/*/*/gson-2*.jar"), find(cache + "/com.badlogicgames.box2dlights/box2dlights/*/*/box2dlights-*.jar")]
    jars = [j for j in jars if j and "sources" not in j]
    classes = os.path.join(ROOT, "template/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
    if not os.path.isdir(classes):
        print("template classes missing: run  bash gradlew :template:compileDebugJavaWithJavac")
        return 2
    tmp = tempfile.mkdtemp(prefix="s2v_test_")
    out = os.path.join(tmp, "out")
    os.makedirs(out)
    cp = os.pathsep.join([classes] + jars)
    srcs = glob.glob(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/behaviors/*.java")) + [os.path.join(ROOT, "tools/behaviors_test/ScriptVarsTest.java")]
    r = subprocess.run(["javac", "-nowarn", "-d", out, "-cp", cp] + srcs, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1
    pkg = os.path.join(tmp, "gen/com/star4droid/Game/Scripts/main")
    os.makedirs(pkg)
    script = os.path.join(pkg, "HeroScript.java")
    open(script, "w", encoding="utf-8").write(SCRIPT)
    gen = os.path.join(tmp, "generated.txt")
    r = subprocess.run(["java", "-cp", os.pathsep.join([out, cp]), "ScriptVarsTest", script, gen], capture_output=True, text=True)
    print(r.stdout + r.stderr)
    if r.returncode != 0:
        return 1
    code = open(gen, encoding="utf-8").read()
    run = RUN.replace("// GENERATED", code)
    open(os.path.join(pkg, "RunGen.java"), "w", encoding="utf-8").write(run)
    # the statements with references use `this` (a scene): they only need to compile inside a scene class
    refs = open(gen + ".refs", encoding="utf-8").read().replace("holder.getScript()", "player.getScript()")
    wrap = ("package com.star4droid.Game;\nimport com.star4droid.template.Items.*;\nimport com.star4droid.template.Utils.PlayerItem;\n"
            "public class GenRefs extends StageImp {\n  public void gen() {\n    PlayerItem player = null;\n" + refs + "\n  }\n}\n")
    os.makedirs(os.path.join(tmp, "gen/com/star4droid/Game"), exist_ok=True)
    open(os.path.join(tmp, "gen/com/star4droid/Game/GenRefs.java"), "w", encoding="utf-8").write(wrap)
    cls = os.path.join(tmp, "genout")
    os.makedirs(cls)
    files = glob.glob(os.path.join(tmp, "gen/**/*.java"), recursive=True)
    r = subprocess.run(["javac", "-nowarn", "-source", "8", "-target", "8", "-Xlint:-options", "-d", cls, "-cp", cp] + files, capture_output=True, text=True)
    if r.returncode != 0:
        print("generated code does not compile:\n" + r.stdout + r.stderr)
        return 1
    print("generated code compiles")
    r = subprocess.run(["java", "-cp", os.pathsep.join([cls, cp]), "com.star4droid.Game.Scripts.main.RunGen"], capture_output=True, text=True)
    print(r.stdout + r.stderr)
    if r.returncode != 0:
        return 1
    shutil.rmtree(tmp, ignore_errors=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
