#!/usr/bin/env python3
"""Theme Builder capability-coverage gate.

Every engine rule (skin, skinMinor1, layout) and every structure item must have a catalog entry
(docs/skin-editor.catalog.json), and every entry must point at a real rule. Writes the coverage report
docs/skin-editor.coverage.json and .md.

Statuses per rule:
  EDITABLE_PREVIEWED   built, and drawn by the simulated preview (catalog "previewed": true)
  EDITABLE_REAL_ONLY   built, visible only in the real-app preview
  PLANNED_P1..P5       catalog entry exists, control not built yet
  ENGINE_NOT_WIRED     rule is valid but the engine does not read it yet (skinMinor1 not in implementedMinor1)
  ENGINE_UNSUPPORTED   engine cannot do it (layoutNotYetSupported, null-only, single-option enum)
  UNMAPPED             no catalog entry  -> FAILS the check

  python3 Tools/check_catalog.py            check + print summary
  python3 Tools/check_catalog.py --write    also (re)write the report files
  python3 Tools/check_catalog.py --final    additionally fail on any PLANNED_* (use when the Builder is complete)
"""
import argparse
import collections
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = lambda p: os.path.join(ROOT, p)
LANGS = ("en", "my")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--final", action="store_true")
    a = ap.parse_args()
    man = json.load(open(D("docs/skin-rules.manifest.json"), encoding="utf-8"))
    cat = json.load(open(D("docs/skin-editor.catalog.json"), encoding="utf-8"))
    errors = []
    entries = {}
    for e in cat["entries"]:
        if e["path"] in entries:
            errors.append("duplicate catalog entry: " + e["path"])
        entries[e["path"]] = e
        if not e.get("label", {}).get("en"):
            errors.append("missing English label: " + e["path"])
        if e.get("group") not in cat["groups"]:
            errors.append("unknown group %r: %s" % (e.get("group"), e["path"]))
        if e.get("level") not in ("basic", "advanced"):
            errors.append("bad level: " + e["path"])
        for t in e.get("targets", []):
            if t not in cat["components"]:
                errors.append("unknown target component %r: %s" % (t, e["path"]))
        for s in e.get("screens", []):
            if s != "*" and s not in cat["screens"]:
                errors.append("unknown screen %r: %s" % (s, e["path"]))
    for sec in ("skin", "skinMinor1", "layout"):
        for r in man[sec]:
            kind = r.split("|", 1)[1]
            if kind.startswith("list:"):
                for opt in kind.split(":")[1].split(","):
                    if not cat.get("items", {}).get(opt, {}).get("label", {}).get("en"):
                        errors.append("list item without English label: " + opt)
    for g, v in cat["groups"].items():
        if not v["label"].get("en"):
            errors.append("group without English label: " + g)

    unsupported = set("layout." + p for p in man["layoutNotYetSupported"]) | set(man["layoutNotYetSupported"])
    implemented = set(man["implementedMinor1"])
    rules = []  # (docPath, kind, section)
    for sec, prefix in (("skin", ""), ("skinMinor1", ""), ("layout", "layout.")):
        for r in man[sec]:
            p, k = r.split("|", 1)
            rules.append((prefix + p, k, sec, p))
    for s in man["structure"]:
        rules.append((s.split(":", 1)[1], "structure", "structure", s.split(":", 1)[1]))

    rows = []
    seen = set()
    for doc, kind, sec, raw in rules:
        if doc in seen:
            continue
        seen.add(doc)
        e = entries.get(doc)
        if e is None:
            status = "UNMAPPED"
            errors.append("UNMAPPED rule (no catalog entry): %s [%s]" % (doc, kind))
        elif doc == "schemaMinor":
            status = "ENGINE_UNSUPPORTED"  # informational field, set automatically
        elif sec == "layout" and raw in man["layoutNotYetSupported"] or doc in unsupported:
            status = "ENGINE_UNSUPPORTED"
        elif kind == "null" or (kind.startswith("enum:") and "," not in kind):
            status = "ENGINE_UNSUPPORTED"
        elif sec == "skinMinor1" and doc not in implemented:
            status = "ENGINE_NOT_WIRED"
        elif e.get("built"):
            status = "EDITABLE_PREVIEWED" if e.get("previewed") else "EDITABLE_REAL_ONLY"
        else:
            status = "PLANNED_P%d" % e.get("phase", 5)
        if e is not None and e.get("built") and status.startswith("ENGINE"):
            errors.append("built control for a rule the engine cannot use: " + doc)
        rows.append({"path": doc, "kind": kind, "section": sec, "status": status,
                     "group": e["group"] if e else None, "level": e["level"] if e else None,
                     "phase": e.get("phase") if e else None, "wiring": "unverified"})
    for p, e in entries.items():
        if p not in seen:
            errors.append("catalog entry without a rule: " + p)
    if a.final:
        for r in rows:
            if r["status"].startswith("PLANNED"):
                errors.append("not built yet: " + r["path"])

    count = collections.Counter(r["status"] for r in rows)
    print("rules: %d   catalog entries: %d" % (len(rows), len(entries)))
    for k in sorted(count):
        print("  %-20s %d" % (k, count[k]))
    my = sum(1 for e in cat["entries"] if e["label"].get("my"))
    print("  Burmese labels: %d / %d (not reviewed by a native speaker unless listed in 'reviewed')" % (my, len(cat["entries"])))
    if a.write:
        with open(D("docs/skin-editor.coverage.json"), "w", encoding="utf-8") as f:
            json.dump({"counts": count, "rules": rows}, f, indent=1)
            f.write("\n")
        lines = ["# Theme Builder capability coverage", "", "Generated by `Tools/check_catalog.py --write`. Do not edit.", "",
                 "| Status | Rules |", "|---|---|"] + ["| %s | %d |" % (k, count[k]) for k in sorted(count)]
        lines += ["", "| Rule | Kind | Status | Group | Level |", "|---|---|---|---|---|"]
        for r in rows:
            lines.append("| `%s` | %s | %s | %s | %s |" % (r["path"], r["kind"], r["status"], r["group"], r["level"]))
        with open(D("docs/skin-editor.coverage.md"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
    if errors:
        print("\nFAILED (%d):" % len(errors))
        for e in errors[:60]:
            print("  - " + e)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
