#!/usr/bin/env python3
"""Tests of the 3D blocks (no phone needed):
  1. compiles the Android-free block core (star2d/blocks/*.java) + ui/blocks/Blocks3D.java + tools/blocks3d_test/Blocks3DTest.java,
  2. Blocks3DTest: every 3D block / value block has English + Arabic texts, slot / placeholder counts match, no lambdas,
     no Bullet / glTF classes, the 2D / 3D filter (SceneDims: sceneType of configs/<scene>.json, "dims" of custom blocks),
  3. compiles the Java of every 3D block filled with its default values (value blocks with sample values) against the
     template classes (Java 8 release level, no lambdas checked above = Java 7 code),
  4. the translations of the new keys (I18N_STAGING, default /home/claude/i18n_staging/blocks3d.json, or the language
     files once merged) exist in en, ar, fr, br, ru, es.
Needs: bash gradlew :template:compileReleaseJavaWithJavac (or Debug) once, and the gradle cache (gdx, gson, bullet, gltf).
"""
import glob, json, os, re, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANGS = ["en", "ar", "fr", "br", "ru", "es"]
# Tr.t(...) keys added with the 3D blocks (block creator selector, value chips)
EXTRA_KEYS = ["blk_options", "bc_dims", "bc_dims_2d", "bc_dims_3d", "bc_dims_both"]


def find(pattern):
    r = [x for x in glob.glob(os.path.expanduser(pattern), recursive=True) if "sources" not in x]
    return sorted(r)[-1] if r else None


def main():
    cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
    jars = [find(cache + "/com.badlogicgames.gdx/gdx/*/*/gdx-1*.jar"), find(cache + "/com.badlogicgames.gdx/gdx-box2d/*/*/gdx-box2d-1*.jar"),
            find(cache + "/com.badlogicgames.gdx/gdx-bullet/*/*/gdx-bullet-1*.jar"), find(cache + "/com.github.mgsx-dev.gdx-gltf/gltf/*/*/gltf-*.jar"),
            find(cache + "/com.google.code.gson/gson/*/*/gson-2*.jar"), find(cache + "/com.badlogicgames.box2dlights/box2dlights/*/*/box2dlights-*.jar")]
    jars = [j for j in jars if j]
    classes = None
    for v in ("release/compileReleaseJavaWithJavac", "debug/compileDebugJavaWithJavac"):
        c = os.path.join(ROOT, "template/build/intermediates/javac", v, "classes")
        if os.path.isdir(os.path.join(c, "com/star4droid/template/d3")):
            classes = c
            break
    if not classes:
        print("template classes missing: run  bash gradlew :template:compileReleaseJavaWithJavac")
        return 2
    if not os.path.isfile(os.path.join(classes, "com/star4droid/template/d3/B3x.class")):
        print("template classes are old (no B3x): run  bash gradlew :template:compileReleaseJavaWithJavac")
        return 2
    tmp = tempfile.mkdtemp(prefix="s2_blocks3d_")
    out = os.path.join(tmp, "out")
    os.makedirs(out)
    cp = os.pathsep.join([classes] + jars)
    srcs = glob.glob(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/blocks/*.java")) + [
        os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/ui/blocks/Blocks3D.java"),
        os.path.join(ROOT, "tools/blocks3d_test/Blocks3DTest.java")]
    r = subprocess.run(["javac", "-nowarn", "-encoding", "UTF-8", "-d", out, "-cp", cp] + srcs, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1
    gen, keysf = os.path.join(tmp, "gen.txt"), os.path.join(tmp, "keys.json")
    r = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join([out, cp]), "Blocks3DTest", gen, keysf], capture_output=True, text=True)
    print(r.stdout + r.stderr)
    if r.returncode != 0:
        return 1

    code = open(gen, encoding="utf-8").read()
    wrap = ("package com.star4droid.Game;\nimport com.star4droid.template.Items.*;\nimport com.star4droid.template.Utils.PlayerItem;\n"
            "public class Gen3DCheck extends StageImp {\n  public void gen(PlayerItem current) {\n" + code + "\n  }\n}\n")
    gdir = os.path.join(tmp, "gen/com/star4droid/Game")
    os.makedirs(gdir)
    open(os.path.join(gdir, "Gen3DCheck.java"), "w", encoding="utf-8").write(wrap)
    cls = os.path.join(tmp, "genout")
    os.makedirs(cls)
    r = subprocess.run(["javac", "-nowarn", "--release", "8", "-encoding", "UTF-8", "-d", cls, "-cp", cp, os.path.join(gdir, "Gen3DCheck.java")],
                       capture_output=True, text=True)
    if r.returncode != 0:
        print("FAIL: 3D block code does not compile")
        print(r.stdout + r.stderr)
        if os.environ.get("BLOCKS_VERBOSE"):
            print(wrap)
        return 1
    print("3D block code of %d lines compiles" % wrap.count("\n"))

    # ---- translations
    keys = json.load(open(keysf, encoding="utf-8"))
    if os.environ.get("BLOCKS3D_KEYS_OUT"):
        import shutil
        shutil.copy(keysf, os.environ["BLOCKS3D_KEYS_OUT"])
    existing = {}
    for f in glob.glob(os.path.join(ROOT, "app/src/main/assets/i18n/strings_fr_US.properties")):
        for line in open(f, encoding="utf-8"):
            if "=" in line:
                existing[line.split("=", 1)[0].strip()] = True
    staging = os.environ.get("I18N_STAGING", "/home/claude/i18n_staging/blocks3d.json")
    tr = json.load(open(staging, encoding="utf-8")) if os.path.isfile(staging) else {}
    missing = []
    for k in list(keys.keys()) + EXTRA_KEYS:
        if k in existing:
            continue
        e = tr.get(k)
        if not e or any(not str(e.get(l, "")).strip() for l in LANGS):
            missing.append(k)
            continue
        if k.startswith("blk_r_"):
            for l in LANGS:
                if e[l].count("___") != keys[k][0].count("___"):
                    missing.append(k + " (" + l + " ___ count)")
    if missing:
        print("FAIL: %d translation keys missing / incomplete in %s, e.g. %s" % (len(missing), staging, ", ".join(missing[:12])))
        return 1
    print("translations: %d keys in %s" % (len(keys) + len(EXTRA_KEYS), ", ".join(LANGS)))
    print("ALL OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
