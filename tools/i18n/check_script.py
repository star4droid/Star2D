#!/usr/bin/env python3
"""Flags translations written in the wrong script (e.g. Russian text in the Spanish file).

  python3 tools/i18n/check_script.py        -> lists offenders, exit 1 when any
Rules: es/fr/br/en values must not contain Cyrillic or Arabic letters; ru values need Cyrillic when the
English value has 2+ real words; ar values need Arabic letters under the same condition. Short/technical
values (single words, identifiers, placeholders) are ignored.
"""
import os, re, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
I18N = os.path.join(ROOT, "app/src/main/assets/i18n")
FILES = {"en": "strings_en_GB", "ar": "strings_ar_SD", "fr": "strings_fr_US", "br": "strings_br_US", "ru": "strings_ru_US", "es": "strings_es_US"}
CYR = re.compile(r"[Ѐ-ӿ]")
ARA = re.compile(r"[؀-ۿ]")


def load(path):
    d = {}
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            m = re.match(r"\s*([^#=:\s][^=:\s]*)\s*[=:]\s*(.*)$", line.rstrip("\n"))
            if m:
                d[m.group(1)] = m.group(2)
    return d


def words(s):
    s = re.sub(r"\{[^}]*\}|%\w|\\n|<[^>]*>|\([^)]*\)", " ", s)
    return [w for w in re.findall(r"[A-Za-z]{3,}", s)]


def problems():
    base = load(os.path.join(I18N, "strings.properties"))
    base.update(load(os.path.join(I18N, FILES["en"] + ".properties")))
    out = []
    for lang, f in FILES.items():
        for k, v in load(os.path.join(I18N, f + ".properties")).items():
            if lang in ("en", "es", "fr", "br"):
                if CYR.search(v) or ARA.search(v):
                    out.append((lang, k, v))
            else:
                en = base.get(k, "")
                if len(words(en)) < 2 or len(words(v)) < 2:
                    continue
                if lang == "ru" and not CYR.search(v):
                    out.append((lang, k, v))
                if lang == "ar" and not ARA.search(v):
                    out.append((lang, k, v))
    return out


if __name__ == "__main__":
    p = problems()
    for lang, k, v in p:
        print("%s\t%s\t%s" % (lang, k, v))
    print("wrong-script translations:", len(p))
    sys.exit(1 if p else 0)
