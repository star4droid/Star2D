#!/usr/bin/env python3
"""Tests of the 3D behavior library (behaviors/lib/*3D.java). No phone needed.
  1. conventions: THREE_D target + valid types, icon in assets/icons/catalog.json and unique, @Param on every public field with a
     description / sane range / unit, @Block texts (English + Arabic), events fired == events declared, Java 7 (no lambdas,
     no android.*), no Bullet types, text scanner == reflection (types included), listed in BuiltIns.
  2. the editor's 2D / 3D fit rules (BehaviorMeta.misfit / fits3D / type lists) and the scanner reading THREE_D + types.
  3. a headless smoke run (no GL, no Bullet): rotator, oscillator, path follower, trigger, door, first person walk, patrol.
Needs the template classes compiled once:  bash gradlew :template:compileDebugJavaWithJavac
Usage: python3 tools/test_lib_3d.py
"""
import glob, os, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def jar(pattern):
    r = [p for p in glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/" + pattern), recursive=True) if "sources" not in p]
    return sorted(r)[-1] if r else None


def main():
    classes = os.path.join(ROOT, "template/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
    if not os.path.isdir(classes):
        print("template classes missing: run  bash gradlew :template:compileDebugJavaWithJavac")
        return 2
    cp_sh = subprocess.run(["bash", os.path.join(ROOT, "tools/ui/classpath.sh")], capture_output=True, text=True)
    jars = [j for j in cp_sh.stdout.strip().split(":") if j]
    for p in ["com.badlogicgames.gdx/gdx-bullet/1.14.2/*/gdx-bullet-1.14.2.jar", "com.github.mgsx-dev.gdx-gltf/gltf/*/*/gltf-*.jar"]:
        j = jar(p)
        if j:
            jars.append(j)
    if not jars:
        print("gdx jars not found in ~/.gradle")
        return 2
    tmp = tempfile.mkdtemp(prefix="lib3d_test_")
    out = os.path.join(tmp, "out")
    os.makedirs(out)
    cp = os.pathsep.join([classes] + jars)
    srcs = glob.glob(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/behaviors/*.java"))
    srcs += [os.path.join(ROOT, "tools/lib_3d_test/Lib3DTest.java")]
    r = subprocess.run(["javac", "-nowarn", "-source", "8", "-target", "8", "-Xlint:-options", "-d", out, "-cp", cp] + srcs, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + "\n".join(l for l in r.stderr.splitlines() if "Picked up" not in l))
        return 1
    r = subprocess.run(["java", "-cp", os.pathsep.join([out, cp]), "Lib3DTest", ROOT], capture_output=True, text=True)
    print("\n".join(l for l in (r.stdout + r.stderr).splitlines() if "Picked up" not in l))
    return r.returncode


if __name__ == "__main__":
    sys.exit(main())
