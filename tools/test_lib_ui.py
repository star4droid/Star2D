#!/usr/bin/env python3
"""Tests of the UI / effects / input behavior library (template/behaviors/lib: Pulse, Blink, FadeIn, FadeOut, Shake, SlideIn, Wobble,
Typewriter, CountUp, ButtonSound, OpenSceneOnClick, ToggleOnClick, Draggable, Parallax, LongPress, SwipeDetector, ProgressBind, TintCycle).

  1. metadata through K1's readers (reflection AND the source scanner): icon exists in assets/icons/catalog.json, description, category,
     every public field has @Param with a description and a default inside min/max, enums, events declared == fired, @Block labels in
     English + Arabic, no android.* imports, no lambdas;
  2. headless simulation of every behavior against fake items (no GL, no phone): timing, restore on disable, events, touch handling,
     physical bodies (Box2D natives) and "no allocation per frame".

Compiles everything straight from the sources (it does not need gradle). Usage: python3 tools/test_lib_ui.py [SimulationName]   |   DOCS_OUT=/tmp/t.md python3 tools/test_lib_ui.py --docs  (prints the tables for docs/behaviors/ui-effects.md)
"""
import glob, os, subprocess, sys, tempfile, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def find(pattern):
    r = [p for p in glob.glob(os.path.expanduser(pattern), recursive=True) if "sources" not in p and "javadoc" not in p]
    return sorted(r)[-1] if r else None


def main():
    cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
    jars = [find(cache + "/com.badlogicgames.gdx/gdx/1.14.2/*/gdx-1.14.2.jar"),
            find(cache + "/com.badlogicgames.gdx/gdx-box2d/1.14.2/*/gdx-box2d-1.14.2.jar"), find(cache + "/com.badlogicgames.gdx/gdx-bullet/*/*/gdx-bullet-1*.jar"), find(cache + "/com.github.mgsx-dev.gdx-gltf/gltf/*/*/gltf-*.jar"),
            find(cache + "/com.badlogicgames.gdx/gdx-box2d-platform/1.14.2/*/gdx-box2d-platform-1.14.2-natives-desktop.jar"),
            find(cache + "/com.badlogicgames.gdx/gdx-platform/1.14.2/*/gdx-platform-1.14.2-natives-desktop.jar"),
            find(cache + "/com.badlogicgames.gdx/gdx-jnigen-loader/*/*/gdx-jnigen-loader-*.jar"),
            find(cache + "/com.google.code.gson/gson/*/*/gson-2*.jar"),
            find(cache + "/com.badlogicgames.gdx/gdx-freetype/1.14.2/*/gdx-freetype-1.14.2.jar"),
            find(cache + "/com.kotcrab.vis/vis-ui/*/*/vis-ui-*.jar"),
            os.path.join(ROOT, "app/libs/box2dlights-1.5.jar")]
    android = find("/opt/android-sdk/platforms/android-*/android.jar") or find(os.path.expanduser("~/android-sdk/platforms/android-*/android.jar"))
    if android:
        jars.append(android)
    jars = [j for j in jars if j and os.path.exists(j)]
    tmp = tempfile.mkdtemp(prefix="libui_test_")
    out = os.path.join(tmp, "out")
    os.makedirs(out)
    cp = os.pathsep.join(jars)
    lib = os.path.join(ROOT, "app/src/main/java/com/star4droid/template/behaviors/lib")
    names = ["Pulse", "Blink", "FadeIn", "FadeOut", "Shake", "SlideIn", "Wobble", "Typewriter", "CountUp", "ButtonSound", "OpenSceneOnClick", "ToggleOnClick",
             "Draggable", "Parallax", "LongPress", "SwipeDetector", "ProgressBind", "TintCycle"]
    srcs = [os.path.join(lib, n + ".java") for n in names]
    srcs += glob.glob(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/behaviors/*.java"))
    srcs.append(os.path.join(ROOT, "tools/lib_ui_test/LibUiTest.java"))
    # template-specific sources first (Project), then the shared runtime sources of the editor module; only the classes that are reached are compiled
    sp = os.pathsep.join([os.path.join(ROOT, "template/src/main/java"), os.path.join(ROOT, "app/src/main/java")])
    r = subprocess.run(["javac", "-nowarn", "-proc:none", "-source", "8", "-target", "8", "-Xlint:-options", "-d", out, "-cp", cp, "-sourcepath", sp] + srcs,
                       capture_output=True, text=True)
    if r.returncode != 0:
        print("\n".join(l for l in (r.stdout + r.stderr).splitlines() if not l.startswith("Picked up"))[:6000])
        return 1
    run = subprocess.run(["java", "-cp", os.pathsep.join([out, cp]), "LibUiTest", ROOT] + sys.argv[1:], capture_output=True, text=True, timeout=300)
    text = "\n".join(l for l in (run.stdout + run.stderr).splitlines() if not l.startswith("Picked up"))
    if "--docs" in sys.argv:
        open(os.environ.get("DOCS_OUT", os.path.join(tmp, "tables.md")), "w", encoding="utf-8").write(text + "\n")
        print(os.environ.get("DOCS_OUT", os.path.join(tmp, "tables.md")))
        return run.returncode
    print(text)
    shutil.rmtree(tmp, ignore_errors=True)
    return run.returncode


if __name__ == "__main__":
    sys.exit(main())
