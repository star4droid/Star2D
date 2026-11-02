#!/usr/bin/env python3
"""Merges translation staging files ({key: {en, ar, fr, br, ru, es}}) into app/src/main/assets/i18n/*.properties.
Existing keys are kept (never overwritten). Usage: merge_staging.py file1.json [file2.json ...]"""
import io, json, os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DIR = os.path.join(ROOT, "app/src/main/assets/i18n")
FILES = {"en": ["strings.properties", "strings_en_GB.properties"], "ar": ["strings_ar_SD.properties"], "fr": ["strings_fr_US.properties"],
         "br": ["strings_br_US.properties"], "ru": ["strings_ru_US.properties"], "es": ["strings_es_US.properties"]}

def keys_of(text):
    out = set()
    for line in text.splitlines():
        s = line.strip()
        if not s or s.startswith("#") or s.startswith("!"):
            continue
        for sep in ("=", ":"):
            pass
        i = min([p for p in (s.find("="), s.find(":")) if p >= 0] or [len(s)])
        out.add(s[:i].strip().replace("\\ ", " "))
    return out

def esc_key(k):
    return k.replace(" ", "\\ ").replace(":", "\\:").replace("=", "\\=")

def esc_val(v):
    return v.replace("\\", "\\\\").replace("\n", "\\n")

data = {}
for p in sys.argv[1:]:
    d = json.load(open(p, encoding="utf-8"))
    for k, v in d.items():
        data.setdefault(k, {}).update(v)
for lang, files in FILES.items():
    for fn in files:
        path = os.path.join(DIR, fn)
        if not os.path.exists(path):
            continue
        text = io.open(path, encoding="utf-8").read()
        have = keys_of(text)
        add = []
        for k, v in data.items():
            val = v.get(lang) or (v.get("en") if lang == "en" else None)
            if not val or k in have:
                continue
            add.append("%s = %s" % (esc_key(k), esc_val(val)))
        if add:
            if not text.endswith("\n"):
                text += "\n"
            io.open(path, "w", encoding="utf-8").write(text + "\n".join(add) + "\n")
        print(fn, "+%d" % len(add))
