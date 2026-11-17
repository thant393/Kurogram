#!/usr/bin/env python3
"""Burmese review loop for the Theme Builder text.

The Burmese words in docs/skin-editor.catalog.json and BuilderText.java start as a glossary-based draft. A Burmese speaker can fix
them in a spreadsheet:

  python3 Tools/review_my.py export review.tsv    one row per text: kind, key, English, current Burmese, [corrected Burmese]
  (edit the last column of the rows you want to change, save as TSV)
  python3 Tools/review_my.py import review.tsv    writes the corrections back and marks those texts as reviewed
  python3 Tools/review_my.py status               how many texts a native speaker has confirmed

A row with the last column empty is left alone; to confirm a text without changing it, copy the current Burmese into the last column.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, "docs/skin-editor.catalog.json")
TEXT = os.path.join(ROOT, "TMessagesProj/src/main/java/tw/kurocore/kurogram/skin/editor/BuilderText.java")
TABLES = ("groups", "components", "screens", "items")
LINE = re.compile(r'^(\s*t\("([^"]+)", ")((?:[^"\\]|\\.)*)(", ")((?:[^"\\]|\\.)*)("\);)\s*$')


def load():
    return json.load(open(CATALOG, encoding="utf-8"))


def rows(cat):
    for t in TABLES:
        for k, v in cat.get(t, {}).items():
            yield t, k, v["label"]["en"], v["label"].get("my", ""), "my" in v.get("reviewed", [])
    for e in cat["entries"]:
        yield "entry", e["path"], e["label"]["en"], e["label"].get("my", ""), "my" in e.get("reviewed", [])
    for line in open(TEXT, encoding="utf-8"):
        m = LINE.match(line)
        if m:
            yield "ui", m.group(2), m.group(3), m.group(5), False


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    cmd = sys.argv[1]
    cat = load()
    if cmd == "status":
        total = done = 0
        for _, _, _, _, rev in rows(cat):
            total += 1
            done += rev
        print("catalog texts confirmed by a Burmese speaker: %d / %d" % (done, sum(1 for r in rows(cat) if r[0] != "ui")))
        return 0
    if cmd == "export":
        with open(sys.argv[2], "w", encoding="utf-8") as f:
            f.write("kind\tkey\tenglish\tburmese_now\treviewed\tburmese_corrected\n")
            for kind, key, en, my, rev in rows(cat):
                f.write("%s\t%s\t%s\t%s\t%s\t\n" % (kind, key, en.replace("\t", " "), my.replace("\t", " "), "yes" if rev else ""))
        print("written", sys.argv[2])
        return 0
    if cmd == "import":
        changed = 0
        fixes = {}
        for line in open(sys.argv[2], encoding="utf-8").read().splitlines()[1:]:
            parts = line.split("\t")
            if len(parts) >= 6 and parts[5].strip():
                fixes[(parts[0], parts[1])] = parts[5].strip()
        for t in TABLES:
            for k, v in cat.get(t, {}).items():
                if (t, k) in fixes:
                    v["label"]["my"] = fixes[(t, k)]
                    v["reviewed"] = sorted(set(v.get("reviewed", []) + ["my"]))
                    changed += 1
        for e in cat["entries"]:
            if ("entry", e["path"]) in fixes:
                e["label"]["my"] = fixes[("entry", e["path"])]
                e["reviewed"] = sorted(set(e.get("reviewed", []) + ["my"]))
                changed += 1
        json.dump(cat, open(CATALOG, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        open(CATALOG, "a").write("\n")
        out = []
        for line in open(TEXT, encoding="utf-8").read().split("\n"):
            m = LINE.match(line)
            if m and ("ui", m.group(2)) in fixes:
                line = m.group(1) + m.group(3) + m.group(4) + fixes[("ui", m.group(2))].replace('"', '\\"') + m.group(6)
                changed += 1
            out.append(line)
        open(TEXT, "w", encoding="utf-8").write("\n".join(out))
        print("updated", changed, "texts")
        return 0
    print(__doc__)
    return 1


if __name__ == "__main__":
    sys.exit(main())
