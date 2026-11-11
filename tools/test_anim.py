#!/usr/bin/env python3
"""Headless tests of the timelines and tweens (com.star4droid.template.anim): file format, interpolation, keys, loops,
markers, relative values, colors / yes-no / text tracks, tweens with delay and yoyo. No phone, no emulator.
Needs bash gradlew :template:compileDebugJavaWithJavac."""
import glob, os, subprocess, sys, tempfile
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")


def jar(pattern):
    r = [j for j in glob.glob(CACHE + "/" + pattern) if "sources" not in j and "javadoc" not in j]
    return sorted(r)[-1] if r else None


def main():
    c = None
    for v in ("debug/compileDebugJavaWithJavac", "release/compileReleaseJavaWithJavac"):
        d = os.path.join(ROOT, "template/build/intermediates/javac", v, "classes")
        if os.path.isfile(os.path.join(d, "com/star4droid/template/anim/Anim.class")):
            c = d
            break
    if not c:
        print("template classes missing: bash gradlew :template:compileDebugJavaWithJavac")
        return 2
    jars = [j for j in [jar("com.badlogicgames.gdx/gdx/*/*/gdx-1*.jar"), jar("com.badlogicgames.gdx/gdx-box2d/*/*/gdx-box2d-1*.jar"),
                        jar("com.kotcrab.vis/vis-ui/*/*/vis-ui-*.jar"), jar("com.google.code.gson/gson/*/*/gson-2*.jar")] if j]
    cp = os.pathsep.join([c] + jars)
    out = tempfile.mkdtemp(prefix="s2_anim_")
    src = os.path.join(ROOT, "tools/anim_test/com/star4droid/template/anim/AnimTest.java")
    r = subprocess.run(["javac", "-nowarn", "-encoding", "UTF-8", "-d", out, "-cp", cp, src], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1
    r = subprocess.run(["java", "-cp", os.pathsep.join([out, cp]), "com.star4droid.template.anim.AnimTest"], capture_output=True, text=True, timeout=120)
    print("\n".join(l for l in (r.stdout + r.stderr).splitlines() if not l.startswith("Picked up")))
    return r.returncode


if __name__ == "__main__":
    sys.exit(main())
