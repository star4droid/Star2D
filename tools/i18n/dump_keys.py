#!/usr/bin/env python3
"""Lists every translation key of the app with its English default and tells which are missing per language.

  python3 tools/i18n/dump_keys.py            -> prints a summary, writes /tmp/i18n_keys.tsv (all keys) and /tmp/i18n_missing_<lang>.tsv
  python3 tools/i18n/dump_keys.py --check    -> exit 1 when a key has no translation in some language (CI / test use)

Static keys come from a scan of  Tr.t("key", "default")  calls; dynamic keys (UiTr, BehaviorText) from tools/i18n/KeyDump.java run with a recording Tr.
"""
import glob, os, re, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
LANGS = {"ar": "strings_ar_SD", "fr": "strings_fr_US", "br": "strings_br_US", "ru": "strings_ru_US", "es": "strings_es_US"}
I18N = os.path.join(ROOT, "app/src/main/assets/i18n")


def jar(pat):
    r = sorted(x for x in glob.glob(CACHE + "/" + pat, recursive=True) if "sources" not in x)
    return r[-1] if r else None


def load_props(path):
    d = {}
    if not os.path.exists(path):
        return d
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        m = re.match(r"\s*([^=:\s]+)\s*[=:]\s*(.*)$", line)
        if m:
            d[m.group(1)] = m.group(2)
    return d


def static_keys():
    keys = {}
    pat = re.compile(r'Tr\.t\(\s*"((?:[^"\\]|\\.)+)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)', re.S)
    for f in glob.glob(ROOT + "/app/src/main/java/**/*.java", recursive=True):
        txt = open(f, encoding="utf-8").read()
        for k, d in pat.findall(txt):
            if k not in keys:
                keys[k] = bytes(d, "utf-8").decode("unicode_escape").encode("latin-1", "ignore").decode("utf-8", "ignore") if "\\" in d and False else d.replace('\\"', '"').replace("\\n", "\\n")
    # font editor: keys built as "font_p_" + field, "font_d_" + field, "font_o_" + value, "fontp_" + preset id
    try:
        form = open(ROOT + "/app/src/main/java/com/star4droid/star2d/editor/utils/FontMakerForm.java", encoding="utf-8").read()
        pres = open(ROOT + "/app/src/main/java/com/star4droid/star2d/fonts/FontPresets.java", encoding="utf-8").read()
        for k, d in re.findall(r'\{"(\w+)", "([^"]+)"\}', form[form.index("LABELS"):form.index("private static String labelOf")]):
            keys.setdefault("font_p_" + k, d)
        for k, d in re.findall(r'spec\("(\w+)", "\w+", "[^"]*", [^,]+, [^,]+, [^,]+, "([^"]+)"\)', pres):
            for q in (["padTop", "padLeft", "padBottom", "padRight"] if k.startswith("pad") else [k]):
                keys.setdefault("font_d_" + q, d)
        for arr in re.findall(r'String\[\] (?:HINTING|FILTER) = \{([^}]*)\}', pres):
            for v in re.findall(r'"(\w+)"', arr):
                keys.setdefault("font_o_" + v, v)
        import json
        for pr in json.load(open(ROOT + "/app/src/main/assets/fonts/presets.json", encoding="utf-8"))["presets"]:
            keys.setdefault("fontp_" + pr["id"], pr["name"])
    except Exception as e:
        print("font keys skipped:", e)
    return keys


def dynamic_keys():
    out = "/tmp/i18n_build"
    os.makedirs(out + "/stub", exist_ok=True)
    os.makedirs(out + "/dump", exist_ok=True)
    android = glob.glob("/opt/android-sdk/platforms/*/android.jar")[0]
    app = os.path.join(ROOT, "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
    tpl = os.path.join(ROOT, "template/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
    libs = [jar("com.badlogicgames.gdx/gdx/*/*/gdx-1*.jar"), jar("com.google.code.gson/gson/*/*/gson-2*.jar")]
    r = subprocess.run(["javac", "-nowarn", "-cp", android, "-d", out + "/stub", ROOT + "/tools/i18n/stub/com/star4droid/star2d/ui/Tr.java"], capture_output=True, text=True)
    if r.returncode:
        sys.exit(r.stderr)
    cp = os.pathsep.join([out + "/stub", app, tpl, android] + [l for l in libs if l])
    r = subprocess.run(["javac", "-nowarn", "-cp", cp, "-d", out + "/dump", ROOT + "/tools/i18n/KeyDump.java"], capture_output=True, text=True)
    if r.returncode:
        sys.exit(r.stderr)
    r = subprocess.run(["java", "-cp", out + "/dump" + os.pathsep + cp, "KeyDump", out + "/dyn.tsv"], capture_output=True, text=True)
    if r.returncode:
        sys.exit(r.stdout + r.stderr)
    keys = {}
    for line in open(out + "/dyn.tsv", encoding="utf-8"):
        k, _, d = line.rstrip("\n").partition("\t")
        keys[k] = d
    return keys


def main():
    keys = static_keys()
    dyn = dynamic_keys()
    for k, v in dyn.items():
        keys.setdefault(k, v)
    with open("/tmp/i18n_keys.tsv", "w", encoding="utf-8") as f:
        for k, v in keys.items():
            f.write(k + "\t" + v + "\n")
    en = load_props(os.path.join(I18N, "strings.properties"))
    bad = False
    print("total keys:", len(keys), "(static %d, dynamic %d)" % (len(keys) - len(dyn), len(dyn)))
    for lang, base in LANGS.items():
        props = load_props(os.path.join(I18N, base + ".properties"))
        miss = [(k, v) for k, v in keys.items() if v.strip() and (k not in props or not props[k].strip())]
        with open("/tmp/i18n_missing_%s.tsv" % lang, "w", encoding="utf-8") as f:
            for k, v in miss:
                f.write(k + "\t" + v + "\n")
        print("%s: missing %d" % (lang, len(miss)))
        bad = bad or bool(miss)
    # translations written in the wrong script (e.g. Russian in the Spanish file)
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import check_script
    wrong = check_script.problems()
    for lang, k, v in wrong:
        print("wrong script %s\t%s\t%s" % (lang, k, v))
    print("wrong-script translations: %d" % len(wrong))
    bad = bad or bool(wrong)
    if "--check" in sys.argv and bad:
        sys.exit(1)


main()
