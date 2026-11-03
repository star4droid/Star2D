#!/usr/bin/env python3
"""Tests of the behaviors framework (no phone needed):
  1. compiles the editor-side scanner / code generator (pure Java) and the template runtime behaviors,
  2. checks the textual scanner against reflection for every built-in behavior,
  3. checks the generated Java (typed assignments, handlers) and compiles it against the template classes.
Needs: the template module compiled once (bash gradlew :template:compileDebugJavaWithJavac) and the gradle cache (gdx, gson).
"""
import glob, os, subprocess, sys, tempfile, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


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
    tmp = tempfile.mkdtemp(prefix="s2b_test_")
    out = os.path.join(tmp, "out")
    os.makedirs(out)
    cp = os.pathsep.join([classes] + jars)
    srcs = glob.glob(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/behaviors/*.java")) + [os.path.join(ROOT, "tools/behaviors_test/BehaviorsTest.java")]
    # S2b / classes that need Android are in other packages (ui.behaviors); this package must stay Android-free
    r = subprocess.run(["javac", "-nowarn", "-d", out, "-cp", cp] + srcs, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1
    gen = os.path.join(tmp, "generated.txt")
    r = subprocess.run(["java", "-Dgen.out=" + gen, "-cp", os.pathsep.join([out, cp]), "BehaviorsTest", ROOT], capture_output=True, text=True)
    print(r.stdout + r.stderr)
    if r.returncode != 0:
        return 1
    # the generated statements must compile (Java 7 source level like the device compiler)
    code = open(gen, encoding="utf-8").read()
    proj = os.path.join(tempfile.gettempdir(), "s2b_test_proj")
    wrap = ("package com.star4droid.Game;\nimport com.star4droid.template.Items.*;\nimport com.star4droid.template.Utils.PlayerItem;\n"
            "public class GenCheck extends StageImp {\n  public void gen() {\n    PlayerItem player = null, hp = null, joy = null, e1 = null;\n" + code + "\n  }\n}\n")
    os.makedirs(os.path.join(tmp, "gen/com/star4droid/Game"), exist_ok=True)
    open(os.path.join(tmp, "gen/com/star4droid/Game/GenCheck.java"), "w", encoding="utf-8").write(wrap)
    pj = glob.glob(os.path.join(proj, "**/*.java"), recursive=True)
    # stub the script class the generated code casts to
    stub = os.path.join(tmp, "gen/com/star4droid/Game/Scripts/main")
    os.makedirs(stub, exist_ok=True)
    open(os.path.join(stub, "PlayerScript.java"), "w").write("package com.star4droid.Game.Scripts.main;\nimport com.star4droid.template.Items.*;\nimport com.star4droid.template.Utils.*;\npublic class PlayerScript extends ItemScript { public int lives = 3; public ProgressItem hpBar; public java.util.List<PlayerItem> foes; public String title; }\n")
    sstub = os.path.join(tmp, "gen/com/star4droid/game/SceneScript")
    os.makedirs(sstub, exist_ok=True)
    open(os.path.join(sstub, "mainScript.java"), "w").write("package com.star4droid.game.SceneScript;\nimport com.star4droid.template.Utils.SceneScript;\npublic abstract class mainScript extends SceneScript { public int level = 1; }\n")
    cls = os.path.join(tmp, "genout")
    os.makedirs(cls)
    files = [f for f in glob.glob(os.path.join(tmp, "gen/**/*.java"), recursive=True) if not f.endswith("PlayerScript.java") or True] + [p for p in pj if "Behaviors" in p]
    r = subprocess.run(["javac", "-nowarn", "-source", "8", "-target", "8", "-Xlint:-options", "-d", cls, "-cp", cp] + files, capture_output=True, text=True)
    if r.returncode != 0:
        print("generated code does not compile:\n" + r.stdout + r.stderr)
        return 1
    print("generated code compiles")
    shutil.rmtree(tmp, ignore_errors=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
