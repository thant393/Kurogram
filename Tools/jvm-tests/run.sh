#!/bin/sh
# Compiles the Theme Builder model classes and runs the plain-main tests. Needs only a JDK.
set -e
cd "$(dirname "$0")/../.."
OUT="${TMPDIR:-/tmp}/kurogram-editor-tests"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" TMessagesProj/src/main/java/tw/kurocore/kurogram/skin/editor/*.java Tools/jvm-tests/EditorTests.java
java -cp "$OUT" EditorTests .
