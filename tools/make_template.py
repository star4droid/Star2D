#!/usr/bin/env python3
"""
Builds the game template that the editor uses when exporting a game to an APK.

Input : the APK produced by `./gradlew :template:assembleRelease` (module `template`).
Output: (all under app/src/main/assets, committed to the repo)
    files/template.zip        the APK without AndroidManifest.xml / signature  (the editor adds the manifest,
                              assets/project.zip and the icon, then signs it)
    java/manifest.xml         the APK's real manifest as text with %1$s..%5$s placeholders
                              (versionCode, versionName, package, label, screenOrientation)
    java/game.zip             compiled runtime classes; the on-phone compiler uses it as class path
    java/template.version     hash of everything the template is made of (to detect a stale template)

Usage:
    python3 tools/make_template.py build [--apk PATH] [--assets DIR]
    python3 tools/make_template.py hash        # prints the current source hash
    python3 tools/make_template.py check       # exit 1 when template.version is stale

Only the Python standard library and the Android SDK's aapt2 are needed.
"""
import argparse
import fnmatch
import glob
import hashlib
import os
import re
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_JAVA = os.path.join(ROOT, "app", "src", "main", "java")
TEMPLATE = os.path.join(ROOT, "template")
DEFAULT_APK = os.path.join(TEMPLATE, "build", "outputs", "apk", "release", "template-release-unsigned.apk")
DEFAULT_ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
CLASSES_GLOB = os.path.join(TEMPLATE, "build", "intermediates", "javac", "release", "*", "classes")

TEMPLATE_PACKAGE = "com.star4droid.template"
LABEL_MARK = "TPL_APP_LABEL"
# Attributes the on-device AXML encoder may not know, or that must not be copied (set by the Gradle build only).
DROP_ATTRS = {"compileSdkVersion", "compileSdkVersionCodename"}
# Entries of the APK that must not go into template.zip.
def keep_in_template_zip(name):
    if name == "AndroidManifest.xml":
        return False
    if name.startswith("META-INF/"):
        return name.startswith("META-INF/services/")
    if name.startswith("assets/dexopt/"):
        return False
    return True


# ---------------------------------------------------------------- runtime source patterns

def read_patterns():
    pats = []
    with open(os.path.join(TEMPLATE, "runtime-sources.txt")) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                pats.append(line)
    return pats


def pattern_regex(pat):
    """Ant style (** crosses folders, * does not) to regex."""
    out, i = "", 0
    while i < len(pat):
        c = pat[i]
        if pat.startswith("**/", i):
            out += "(?:.*/)?"
            i += 3
        elif pat.startswith("**", i):
            out += ".*"
            i += 2
        elif c == "*":
            out += "[^/]*"
            i += 1
        elif c == "?":
            out += "[^/]"
            i += 1
        else:
            out += re.escape(c)
            i += 1
    return re.compile("^" + out + "$")


def matches(patterns, rel_path):
    return any(pattern_regex(p).match(rel_path) for p in patterns)


def runtime_sources():
    pats = read_patterns()
    found = []
    for dp, _, files in os.walk(APP_JAVA):
        for fn in files:
            full = os.path.join(dp, fn)
            rel = os.path.relpath(full, APP_JAVA).replace(os.sep, "/")
            if matches(pats, rel):
                found.append((rel, full))
    found.sort()
    return found


# ---------------------------------------------------------------- hash

def source_hash():
    h = hashlib.sha256()

    def add(label, path):
        h.update(label.encode())
        h.update(b"\0")
        with open(path, "rb") as f:
            h.update(f.read())
        h.update(b"\0")

    for rel, full in runtime_sources():
        add("rt:" + rel, full)
    for dp, dn, files in os.walk(TEMPLATE):
        dn[:] = sorted(d for d in dn if d not in ("build", "libs"))
        for fn in sorted(files):
            full = os.path.join(dp, fn)
            add("tpl:" + os.path.relpath(full, TEMPLATE).replace(os.sep, "/"), full)
    for extra in ("tools/make_template.py", "gradle.properties", "build.gradle"):
        p = os.path.join(ROOT, extra)
        if os.path.exists(p):
            add("x:" + extra, p)
    return h.hexdigest()[:16]


# ---------------------------------------------------------------- aapt2

def find_aapt2():
    cands = []
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(env)
        if sdk:
            cands += sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "aapt2")), reverse=True)
    for sdk in ("/opt/android-sdk", os.path.expanduser("~/Android/Sdk")):
        cands += sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "aapt2")), reverse=True)
    for c in cands:
        if os.path.exists(c):
            return c
    from shutil import which
    return which("aapt2")


def aapt2(*args):
    exe = find_aapt2()
    if not exe:
        sys.exit("aapt2 not found (set ANDROID_HOME)")
    r = subprocess.run([exe] + list(args), capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("aapt2 failed: " + " ".join(args) + "\n" + r.stderr)
    return r.stdout


# ---------------------------------------------------------------- manifest

ANDROID_NS = "http://schemas.android.com/apk/res/android:"


class Node:
    def __init__(self, name):
        self.name = name
        self.attrs = []  # (qualified name, text value)
        self.children = []


def parse_xmltree(text):
    root, stack = None, []
    for line in text.splitlines():
        m = re.match(r"^(\s*)([NEA]): (.*)$", line)
        if not m:
            continue
        indent, kind, rest = len(m.group(1)), m.group(2), m.group(3)
        if kind == "E":
            name = re.match(r"(\S+)", rest).group(1)
            node = Node(name)
            while stack and stack[-1][0] >= indent:
                stack.pop()
            if stack:
                stack[-1][1].children.append(node)
            else:
                root = node
            stack.append((indent, node))
        elif kind == "A":
            node = stack[-1][1]
            am = re.match(r"^(?P<name>[^=(]+?)(?:\(0x[0-9a-f]+\))?=(?P<val>.*)$", rest)
            name, val = am.group("name"), am.group("val")
            node.attrs.append((name, convert_value(val)))
    return root


def convert_value(val):
    sm = re.match(r'^"(.*)" \(Raw: ".*"\)$', val)
    if sm:
        return sm.group(1)
    sm = re.match(r'^"(.*)"$', val)
    if sm:
        return sm.group(1)
    rm = re.match(r"^@0x([0-9a-fA-F]+)$", val)
    if rm:
        return "@" + rm.group(1).upper().rjust(8, "0")
    return val  # true / false / 0x00020000 / 21 ...


def xml_escape(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def short(name):
    return "android:" + name[len(ANDROID_NS):] if name.startswith(ANDROID_NS) else name


def template_manifest(root):
    """Walks the manifest tree and puts the placeholders in. Returns (text, set of placeholders used)."""
    used = set()

    def transform(node, parent):
        out = []
        for qname, value in node.attrs:
            name = short(qname)
            base = name.split(":")[-1]
            if base in DROP_ATTRS:
                continue
            if node.name == "manifest":
                if name == "android:versionCode":
                    value = "%1$s"
                elif name == "android:versionName":
                    value = "%2$s"
                elif name == "package":
                    value = "%3$s"
            if node.name == "application" and name == "android:label":
                if value != LABEL_MARK:
                    sys.exit("application label must be '%s' in template/src/main/AndroidManifest.xml (got %r)" % (LABEL_MARK, value))
                value = "%4$s"
            if node.name == "activity" and name == "android:screenOrientation":
                value = "%5$s"
            # Names that must be unique per installed game: permission and provider authority. Without this two
            # exported games would conflict at install time (same authority / same permission declared twice).
            if name == "android:authorities" and value.startswith(TEMPLATE_PACKAGE + "."):
                value = "%3$s" + value[len(TEMPLATE_PACKAGE):]
            if node.name in ("permission", "uses-permission") and name == "android:name" and value.startswith(TEMPLATE_PACKAGE + "."):
                value = "%3$s" + value[len(TEMPLATE_PACKAGE):]
            for m in re.findall(r"%(\d)\$s", value):
                used.add(int(m))
            if "%" in value and not re.fullmatch(r"(?:[^%]|%\d\$s)*", value):
                sys.exit("unexpected %% in manifest attribute %s=%r" % (name, value))
            out.append((name, value))
        return out

    lines = ['<?xml version="1.0" encoding="utf-8"?>']

    def emit(node, depth, parent):
        pad = "    " * depth
        attrs = transform(node, parent)
        if node.name == "manifest":
            attrs = [("xmlns:android", "http://schemas.android.com/apk/res/android")] + attrs
        head = pad + "<" + node.name
        if attrs:
            for n, v in attrs:
                head += "\n" + pad + "    " + n + '="' + xml_escape(v).replace("%", "%") + '"'
        if node.children:
            lines.append(head + ">")
            for c in node.children:
                emit(c, depth + 1, node)
            lines.append(pad + "</" + node.name + ">")
        else:
            lines.append(head + "/>")

    emit(root, 0, None)
    return "\n".join(lines) + "\n", used


# ---------------------------------------------------------------- build

def build(apk, assets):
    if not os.path.exists(apk):
        sys.exit("APK not found: %s\nrun: ./gradlew :template:assembleRelease" % apk)
    out_zip = os.path.join(assets, "files", "template.zip")
    out_manifest = os.path.join(assets, "java", "manifest.xml")
    out_game = os.path.join(assets, "java", "game.zip")
    out_version = os.path.join(assets, "java", "template.version")

    # 1) manifest
    tree = parse_xmltree(aapt2("dump", "xmltree", "--file", "AndroidManifest.xml", apk))
    text, used = template_manifest(tree)
    if used != {1, 2, 3, 4, 5}:
        sys.exit("manifest placeholders found %s, expected 1..5" % sorted(used))
    os.makedirs(os.path.dirname(out_manifest), exist_ok=True)
    with open(out_manifest, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)

    # 2) template.zip
    os.makedirs(os.path.dirname(out_zip), exist_ok=True)
    kept = 0
    with zipfile.ZipFile(apk) as zin, zipfile.ZipFile(out_zip, "w") as zout:
        for info in zin.infolist():
            if info.is_dir() or not keep_in_template_zip(info.filename):
                continue
            zi = zipfile.ZipInfo(info.filename, (1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_STORED if info.filename == "resources.arsc" else zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zout.writestr(zi, zin.read(info.filename))
            kept += 1

    # 3) game.zip (class path for the on-phone compiler)
    class_dirs = glob.glob(CLASSES_GLOB)
    if not class_dirs:
        sys.exit("compiled classes not found (%s)" % CLASSES_GLOB)
    cdir = class_dirs[0]
    pats = read_patterns()
    names = []
    for dp, _, files in os.walk(cdir):
        for fn in files:
            if not fn.endswith(".class"):
                continue
            rel = os.path.relpath(os.path.join(dp, fn), cdir).replace(os.sep, "/")
            stem = rel[:-6].split("$")[0]
            if stem.endswith("/R") or "/R$" in rel or rel.startswith("com/star4droid/star2d/evo/"):
                continue
            if matches(pats, stem + ".java") or stem == "com/star4droid/star2d/Helpers/Project":
                names.append((rel, os.path.join(dp, fn)))
    names.sort()
    # 3D: Bullet and gdx-gltf classes too, so scripts / behaviors compiled on the phone may use them
    extra_jars = []
    for pat in ("com.badlogicgames.gdx/gdx-bullet/*/*/gdx-bullet-*.jar", "com.github.mgsx-dev.gdx-gltf/gltf/*/*/gltf-*.jar"):
        found = sorted(glob.glob(os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2", "files-2.1", pat)))
        found = [j for j in found if not j.endswith("-sources.jar") and not j.endswith("-javadoc.jar")]
        if found:
            extra_jars.append(found[-1])
    have = set(rel for rel, _ in names)
    with zipfile.ZipFile(out_game, "w", zipfile.ZIP_DEFLATED) as z:
        for rel, full in names:
            zi = zipfile.ZipInfo(rel, (1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            with open(full, "rb") as f:
                z.writestr(zi, f.read())
        for jar in extra_jars:
            with zipfile.ZipFile(jar) as zj:
                for info in zj.infolist():
                    if not info.filename.endswith(".class") or info.filename in have:
                        continue
                    have.add(info.filename)
                    zi = zipfile.ZipInfo(info.filename, (1980, 1, 1, 0, 0, 0))
                    zi.compress_type = zipfile.ZIP_DEFLATED
                    z.writestr(zi, zj.read(info.filename))
                    names.append((info.filename, jar))

    # 4) version
    with open(out_version, "w") as f:
        f.write(source_hash() + "\n")

    print("manifest.xml : %d bytes" % len(text.encode()))
    print("template.zip : %d entries, %d KB" % (kept, os.path.getsize(out_zip) // 1024))
    print("game.zip     : %d classes, %d KB" % (len(names), os.path.getsize(out_game) // 1024))
    print("version      : " + open(out_version).read().strip())


def check(assets):
    p = os.path.join(assets, "java", "template.version")
    cur = source_hash()
    have = open(p).read().strip() if os.path.exists(p) else "(missing)"
    if cur == have:
        print("template is up to date (%s)" % cur)
        return 0
    print("template is STALE: committed %s, sources %s" % (have, cur))
    return 1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["build", "hash", "check"])
    ap.add_argument("--apk", default=DEFAULT_APK)
    ap.add_argument("--assets", default=DEFAULT_ASSETS)
    a = ap.parse_args()
    if a.cmd == "hash":
        print(source_hash())
    elif a.cmd == "check":
        sys.exit(check(a.assets))
    else:
        build(a.apk, a.assets)


if __name__ == "__main__":
    main()
