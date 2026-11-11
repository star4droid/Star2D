#!/usr/bin/env python3
"""Headless tests of the path finding core (com.star4droid.template.nav): square / hex grids, walls, diagonal rules,
costs, agent size, partial paths, painted data, PathHolder helpers and speed. No phone, no emulator.
Needs the template classes: bash gradlew :template:compileDebugJavaWithJavac (or Release)."""
import glob, os, subprocess, sys, tempfile
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def classes():
    for v in ("debug/compileDebugJavaWithJavac", "release/compileReleaseJavaWithJavac"):
        c = os.path.join(ROOT, "template/build/intermediates/javac", v, "classes")
        if os.path.isfile(os.path.join(c, "com/star4droid/template/nav/NavGrid.class")):
            return c
    return None


def main():
    c = classes()
    if not c:
        print("template classes missing: bash gradlew :template:compileDebugJavaWithJavac")
        return 2
    gdx = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/com.badlogicgames.gdx/gdx/*/*/gdx-1*.jar")))
    gdx = [g for g in gdx if "sources" not in g]
    cp = os.pathsep.join([c] + gdx[-1:])
    out = tempfile.mkdtemp(prefix="s2_nav_")
    r = subprocess.run(["javac", "-nowarn", "-d", out, "-cp", cp, os.path.join(ROOT, "tools/nav_test/NavTest.java")], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1
    r = subprocess.run(["java", "-cp", os.pathsep.join([out, cp]), "NavTest"], capture_output=True, text=True)
    print("\n".join(l for l in (r.stdout + r.stderr).splitlines() if not l.startswith("Picked up")))
    return r.returncode


if __name__ == "__main__":
    sys.exit(main())
