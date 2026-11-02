#!/usr/bin/env python3
"""Generates assets/items/{obj3d,light3d,camera3d,cameraview}.json from the ElementDefs classes (single source of truth)."""
import os, re, json
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFS = os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/ElementDefs")
ITEMS = os.path.join(ROOT, "app/src/main/assets/items")
SKIP = {"name", "parentName", "Script", "type", "x", "y", "z", "Visible", "elementEvents", "TYPE"}
JOBS = [("Object3DDef.java", "obj3d.json", "OBJ3D", "STATIC"), ("Light3DDef.java", "light3d.json", "LIGHT3D", "STATIC"),
        ("Camera3DDef.java", "camera3d.json", "CAMERA3D", "STATIC"), ("CameraViewDef.java", "cameraview.json", "CAMERAVIEW", "UI")]

def fields(src):
    out = []
    body = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    body = re.sub(r"//[^\n]*", "", body)
    for m in re.finditer(r"public\s+(float|String|boolean)\s+([^;(]+);", body):
        typ, decl = m.group(1), m.group(2)
        for part in re.split(r",(?=\s*[A-Za-z_]\w*\s*=)", decl):
            if "=" not in part: continue
            n, v = part.split("=", 1)
            n, v = n.strip(), v.strip()
            if typ == "String": v = v.strip('"')
            elif typ == "float": v = v.rstrip("fF")
            out.append((n, v))
    return out

for java, js, tp, kind in JOBS:
    src = open(os.path.join(DEFS, java), encoding="utf-8").read()
    d = {"TYPE": tp, "type": kind}
    for n, v in fields(src):
        if n in SKIP: continue
        d[n.replace("_", " ")] = v
    if tp == "CAMERAVIEW":
        for k in ("width", "height", "rotation"): pass
    txt = ",".join('"%s":"%s"' % (k, str(v).replace('"', '\\"')) for k, v in d.items())
    open(os.path.join(ITEMS, js), "w", encoding="utf-8").write(txt)
    print(js, len(d), "keys")
