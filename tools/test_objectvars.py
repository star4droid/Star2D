#!/usr/bin/env python3
"""Tests of the object variables (Variables section of an element, "object variable" blocks) - no phone needed:
  1. tools/behaviors_test/ObjectVarsTest.java: storage parsing, block markers, merge, literals;
  2. the generated element code (local event class with the variables as fields) is compiled (Java 8) against the template
     classes and RUN: events change the fields, ObjectVars reads / writes them from outside, a clone (fresh()) starts again
     from the initial values, element references are found after the build.
Needs: the template module compiled once (bash gradlew :template:compileDebugJavaWithJavac) and the gradle cache (gdx, gson).
usage: python3 tools/test_objectvars.py        exit code 1 on any failure
"""
import os, subprocess, sys, tempfile, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from test_scriptvars import find, ROOT
import glob

HARNESS = r'''package com.star4droid.Game;

import com.star4droid.star2d.ElementDefs.*;
import com.star4droid.template.Utils.*;
import java.lang.reflect.*;

public class GenScene {
    public static class FakeDef { public float x; public ElementEvent elementEvents; public Object build(Object s) { return item(elementEvents, "hero"); } }
    static int failures = 0;
    static void check(boolean ok, String what) { if (!ok) { failures++; System.out.println("FAIL " + what); } else System.out.println("ok   " + what); }
    static PlayerItem item(final Object events, final String name) {
        return (PlayerItem) Proxy.newProxyInstance(PlayerItem.class.getClassLoader(), new Class<?>[]{PlayerItem.class}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) throws Throwable {
                if (m.getName().equals("getElementEvents")) return events;
                if (m.getName().equals("getName")) return name;
                if (m.getName().equals("hashCode")) return System.identityHashCode(p);
                if (m.getName().equals("equals")) return p == a[0];
                if (m.getName().equals("toString")) return name;
                if (m.isDefault()) return InvocationHandler.invokeDefault(p, m, a);
                Class<?> r = m.getReturnType();
                if (r == boolean.class) return false;
                if (r.isPrimitive() && r != void.class) return r == float.class ? (Object) 0f : r == double.class ? (Object) 0d : r == long.class ? (Object) 0L : (Object) 0;
                return null;
            }
        });
    }
    PlayerItem enemy = item(null, "enemy");
    boolean built = false;
    PlayerItem findItem(String n) { return built && n.equals("enemy") ? enemy : null; }
    PlayerItem hero;

    void create() {
// GENERATED
    }

    public static void main(String[] a) {
        GenScene s = new GenScene();
        s.built = false;
        s.create();
        PlayerItem hero = s.hero;
        ElementEvent ev = hero.getElementEvents();
        check(ObjectVars.getInt(hero, "health") == 3 && ObjectVars.getFloat(hero, "speed") == 2.5f, "initial values");
        check("Hi \"you\"".equals(ObjectVars.getString(hero, "title")) && ObjectVars.getBool(hero, "alive"), "text / yes-no initial values");
        check(ObjectVars.getList(hero, "bag").size() == 3 && ObjectVars.getList(hero, "bag").get(2).equals("apple"), "list initial value");
        check(ObjectVars.getInt(hero, "coins") == 5 && "hello".equals(ObjectVars.getString(hero, "msg")), "block-made variables are fields too");
        check(ObjectVars.getItem(hero, "target") == s.enemy, "element reference set once all elements exist");
        ev.onClick(hero);
        ev.onBodyUpdate(hero);
        check(ObjectVars.getInt(hero, "health") == 12, "two events share the variable (3 - 1 + 10)");
        check(ObjectVars.getInt(hero, "coins") == 6 && "hello!".equals(ObjectVars.getString(hero, "msg")) && "Hi \"you\"?".equals(ObjectVars.getString(hero, "title")), "events change the fields");
        ObjectVars.set(hero, "health", 50.7);
        check(ObjectVars.getInt(hero, "health") == 50, "set from outside converts the number");
        ObjectVars.add(hero, "speed", 1);
        check(ObjectVars.getFloat(hero, "speed") == 4.5f, "add");
        hero.setVar("alive", "false");
        check(!hero.getVarBool("alive"), "PlayerItem.setVar / getVarBool");
        ObjectVars.set(hero, "undeclared", "x");
        check("x".equals(ObjectVars.getString(hero, "undeclared")), "undeclared name kept on the side");
        ElementEvent clone = ElementEventAdapter.copyOf(ev);
        PlayerItem hero2 = item(clone, "hero2");
        check(clone != ev && ObjectVars.getInt(hero2, "health") == 3 && ObjectVars.getInt(hero, "health") == 50, "a clone has its own variables");
        check(java.util.Arrays.asList(ObjectVars.names(hero)).contains("health"), "names");
        check(ev.getName().equals("hero"), "adapter name");
        System.exit(failures == 0 ? 0 : 1);
    }
}
'''


def main():
    cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
    jars = [find(cache + "/com.badlogicgames.gdx/gdx/*/*/gdx-1*.jar"), find(cache + "/com.badlogicgames.gdx/gdx-box2d/*/*/gdx-box2d-1*.jar"),
            find(cache + "/com.badlogicgames.gdx/gdx-bullet/*/*/gdx-bullet-1*.jar"), find(cache + "/com.github.mgsx-dev.gdx-gltf/gltf/*/*/gltf-*.jar"),
            find(cache + "/com.google.code.gson/gson/*/*/gson-2*.jar"), find(cache + "/com.badlogicgames.box2dlights/box2dlights/*/*/box2dlights-*.jar")]
    jars = [j for j in jars if j and "sources" not in j]
    classes = os.path.join(ROOT, "template/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
    if not os.path.isdir(classes):
        print("template classes missing: run  bash gradlew :template:compileDebugJavaWithJavac")
        return 2
    tmp = tempfile.mkdtemp(prefix="s2ov_test_")
    out = os.path.join(tmp, "out")
    os.makedirs(out)
    cp = os.pathsep.join([classes] + jars)
    srcs = glob.glob(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/behaviors/*.java")) + [os.path.join(ROOT, "tools/behaviors_test/ObjectVarsTest.java")]
    r = subprocess.run(["javac", "-nowarn", "-d", out, "-cp", cp] + srcs, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1
    gen = os.path.join(tmp, "generated.txt")
    r = subprocess.run(["java", "-cp", os.pathsep.join([out, cp]), "ObjectVarsTest", gen], capture_output=True, text=True)
    print(r.stdout + r.stderr)
    if r.returncode != 0:
        return 1
    code = open(gen, encoding="utf-8").read().replace("hero = (PlayerItem)(hero_def.build(this));", "hero = (PlayerItem)(hero_def.build(this));\nbuilt = true;")
    pkg = os.path.join(tmp, "gen/com/star4droid/Game")
    os.makedirs(pkg)
    open(os.path.join(pkg, "GenScene.java"), "w", encoding="utf-8").write(HARNESS.replace("// GENERATED", code))
    cls = os.path.join(tmp, "genout")
    os.makedirs(cls)
    r = subprocess.run(["javac", "-nowarn", "-source", "8", "-target", "8", "-Xlint:-options", "-d", cls, "-cp", cp, os.path.join(pkg, "GenScene.java")], capture_output=True, text=True)
    if r.returncode != 0:
        print("generated code does not compile:\n" + r.stdout + r.stderr + "\n" + code)
        return 1
    print("generated code compiles")
    r = subprocess.run(["java", "-cp", os.pathsep.join([cls, cp]), "com.star4droid.Game.GenScene"], capture_output=True, text=True)
    print(r.stdout + r.stderr)
    if r.returncode != 0:
        return 1
    shutil.rmtree(tmp, ignore_errors=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
