#!/usr/bin/env python3
"""Keeps the skin/layout rule lists identical everywhere they are written down.

The rule lists exist in three places that must never drift:
  1. the app (SkinSpec.java, LayoutSpec.java)         - validates documents on the phone
  2. docs/skin-rules.manifest.json                    - the shared manifest (canonical copy lives in THIS repo)
  3. the backend (theme.py, layout_rules.py) + its vendored copy of the manifest - validates uploads

  python3 Tools/check_rules.py                      app Java == manifest
  python3 Tools/check_rules.py --backend ../backend-api     ... and backend Python == manifest, vendored copies identical
  python3 Tools/check_rules.py --update             rewrite the manifest from the Java sources (after an intended rule change)

Fails closed: a block that cannot be found or is empty is an error, never a silent pass.
It compares the lists as text; it does not prove the two validators behave the same (see docs/skin-rules.vectors.json).
"""
import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "TMessagesProj/src/main/java/tw/nekomimi/nekogram")
SKIN_JAVA = os.path.join(JAVA, "skin/SkinSpec.java")
LAYOUT_JAVA = os.path.join(JAVA, "layout/LayoutSpec.java")
MANIFEST = os.path.join(ROOT, "docs/skin-rules.manifest.json")
VECTORS = os.path.join(ROOT, "docs/skin-rules.vectors.json")

KIND = re.compile(r"^(col|argb|bool|null|int:-?\d+:-?\d+|float:-?[\d.]+:-?[\d.]+|enum:[\w,.\-]+|list:[\w,.\-]+:\d+:\d+)$")
STRINGS = re.compile(r'"((?:[^"\\]|\\.)*)"')

# manifest key -> (Java file, Java array name) / (backend file, Python list name)
JAVA_LISTS = {
    "skin": (SKIN_JAVA, "RULES_V2"),
    "skinMinor1": (SKIN_JAVA, "RULES_V2_MINOR1"),
    "structure": (SKIN_JAVA, "STRUCTURE"),
    "layoutNotYetSupported": (SKIN_JAVA, "LAYOUT_NOT_YET"),
    "layout": (LAYOUT_JAVA, "RULES"),
}
PY_LISTS = {
    "skin": ("app/schemas/theme.py", "RULES_V2"),
    "skinMinor1": ("app/schemas/theme.py", "RULES_V2_MINOR1"),
    "structure": ("app/schemas/theme.py", "STRUCTURE"),
    "layoutNotYetSupported": ("app/schemas/theme.py", "LAYOUT_NOT_YET"),
    "layout": ("app/schemas/layout_rules.py", "RULES_LAYOUT"),
}


def _strip_java_comments(src):
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"(?m)^\s*//.*$", "", src)


def _strip_py_comments(src):
    return re.sub(r"(?m)^\s*#.*$", "", src)


def read_java(path, name):
    with open(path, encoding="utf-8") as f:
        src = _strip_java_comments(f.read())
    m = re.search(r"String\[\]\s+" + re.escape(name) + r"\s*=\s*\{(.*?)\};", src, re.S)
    if not m:
        raise SystemExit(f"FAIL: array {name} not found in {os.path.relpath(path, ROOT)}")
    return STRINGS.findall(m.group(1))


def read_py(path, name):
    with open(path, encoding="utf-8") as f:
        src = _strip_py_comments(f.read())
    m = re.search(r"(?m)^" + re.escape(name) + r"\s*=\s*\[(.*?)\]", src, re.S)
    if not m:
        raise SystemExit(f"FAIL: list {name} not found in {path}")
    return STRINGS.findall(m.group(1))


def diff(label, a_name, a, b_name, b):
    if a == b:
        return []
    out = [f"{label}: {a_name} ({len(a)}) != {b_name} ({len(b)})"]
    for x in a:
        if x not in b:
            out.append(f"   only in {a_name}: {x}")
    for x in b:
        if x not in a:
            out.append(f"   only in {b_name}: {x}")
    if not out[1:]:
        out.append("   same entries, different order or duplicates")
    return out


def check_shape(label, entries, key=None):
    key = key or label
    problems = []
    if not entries:
        problems.append(f"{label}: empty")
    seen = set()
    for e in entries:
        if key == "structure":
            if not re.fullmatch(r"(leaf|open|overlay|layout):\w+", e):
                problems.append(f"{label}: bad entry {e!r}")
            continue
        if key == "layoutNotYetSupported":
            continue
        path, _, kind = e.partition("|")
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9]*(\.[A-Za-z][A-Za-z0-9]*)*", path) or not KIND.match(kind):
            problems.append(f"{label}: malformed rule {e!r}")
        if path in seen:
            problems.append(f"{label}: duplicate path {path}")
        seen.add(path)
    return problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--backend", help="path of a backend-api checkout to compare as well")
    ap.add_argument("--update", action="store_true", help="rewrite the manifest from the Java sources")
    args = ap.parse_args()

    java = {k: read_java(f, n) for k, (f, n) in JAVA_LISTS.items()}
    problems = []
    for k, v in java.items():
        problems += check_shape(k, v)
    if java["layoutNotYetSupported"] and not set(java["layoutNotYetSupported"]) <= {e.split("|")[0] for e in java["layout"]}:
        problems.append("layoutNotYetSupported: lists a path that is not a layout rule")
    overlap = {e.split("|")[0] for e in java["skin"]} & {e.split("|")[0] for e in java["skinMinor1"]}
    if overlap:
        problems.append(f"skinMinor1 repeats RULES_V2 paths: {sorted(overlap)}")

    if args.update:
        if problems:
            print("\n".join(problems))
            return 1
        manifest = {"manifestVersion": 1, **{k: java[k] for k in ("skin", "skinMinor1", "layout", "structure", "layoutNotYetSupported")}}
        with open(MANIFEST, "w", encoding="utf-8") as f:
            json.dump(manifest, f, indent=1, ensure_ascii=False)
            f.write("\n")
        print("manifest rewritten:", {k: len(v) for k, v in manifest.items() if isinstance(v, list)})
        return 0

    if not os.path.exists(MANIFEST):
        print("FAIL: docs/skin-rules.manifest.json is missing")
        return 1
    with open(MANIFEST, encoding="utf-8") as f:
        manifest = json.load(f)
    for k in java:
        problems += diff(k, "Java", java[k], "manifest", manifest.get(k, []))

    if args.backend:
        for k, (rel, name) in PY_LISTS.items():
            py = read_py(os.path.join(args.backend, rel), name)
            problems += check_shape("backend " + k, py, k)
            problems += diff(k, "backend", py, "manifest", manifest.get(k, []))
        for rel_app, rel_backend in (("docs/skin-rules.manifest.json", "app/schemas/skin_rules.manifest.json"),
                                     ("docs/skin-rules.vectors.json", "tests/skin_rules.vectors.json")):
            a, b = os.path.join(ROOT, rel_app), os.path.join(args.backend, rel_backend)
            if not os.path.exists(b):
                problems.append(f"backend copy missing: {rel_backend}")
            elif open(a, "rb").read() != open(b, "rb").read():
                problems.append(f"backend copy differs from app repo: {rel_backend}")

    if problems:
        print("RULE CHECK FAILED")
        print("\n".join(problems))
        return 1
    counts = {k: len(v) for k, v in java.items()}
    print("rule check OK", counts, "(+ backend)" if args.backend else "")
    return 0


if __name__ == "__main__":
    sys.exit(main())
