#!/usr/bin/env python3
"""Headless tests of the multiplayer core (com.star4droid.template.net): codec, room info, KryoNet on the loopback address and
whole sessions in one JVM (host + clients: join, names, room full, other version, messages and relays, run-for-all,
heartbeat, leaving, host closing). No phone, no emulator. Needs bash gradlew :template:compileDebugJavaWithJavac."""
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
        if os.path.isfile(os.path.join(d, "com/star4droid/template/net/NetSession.class")):
            c = d
            break
    if not c:
        print("template classes missing: bash gradlew :template:compileDebugJavaWithJavac")
        return 2
    jars = [jar("com.badlogicgames.gdx/gdx/*/*/gdx-1*.jar"), jar("com.esotericsoftware/kryonet/*/*/kryonet-*.jar"),
            jar("com.esotericsoftware.kryo/kryo/*/*/kryo-*.jar"), jar("com.esotericsoftware.minlog/minlog/*/*/minlog-*.jar"),
            jar("org.objenesis/objenesis/*/*/objenesis-*.jar"), jar("com.esotericsoftware/jsonbeans/*/*/jsonbeans-*.jar"),
            jar("com.badlogicgames.gdx/gdx-box2d/*/*/gdx-box2d-1*.jar"), jar("com.kotcrab.vis/vis-ui/*/*/vis-ui-*.jar")]
    missing = [p for p, j in zip(["gdx", "kryonet", "kryo", "minlog", "objenesis", "jsonbeans", "box2d", "vis-ui"], jars) if not j]
    if missing:
        print("jars missing from the gradle cache: " + ", ".join(missing) + " (build the template once)")
        return 2
    cp = os.pathsep.join([c] + jars)
    out = tempfile.mkdtemp(prefix="s2_net_")
    src = os.path.join(ROOT, "tools/net_test/com/star4droid/template/net/NetTest.java")
    r = subprocess.run(["javac", "-nowarn", "-encoding", "UTF-8", "-d", out, "-cp", cp, src], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1
    r = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join([out, cp]), "com.star4droid.template.net.NetTest"],
                       capture_output=True, text=True, timeout=180)
    print("\n".join(l for l in (r.stdout + r.stderr).splitlines() if not l.startswith("Picked up")))
    return r.returncode


if __name__ == "__main__":
    sys.exit(main())
