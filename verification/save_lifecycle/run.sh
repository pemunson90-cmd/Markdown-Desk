#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/save-lifecycle"
mkdir -p "$OUT/classes" "$OUT/tmp"
SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk"
javac --release 11 -d "$OUT/classes" "$ROOT/verification/save_lifecycle/ExtractSaveMethods.java"
java -cp "$OUT/classes" ExtractSaveMethods "$SRC/MainActivity.java" "$ROOT/verification/save_lifecycle/MainActivity.template" "$OUT/MainActivity.java"
javac --release 11 -d "$OUT/classes" "$ROOT/verification/ExtractStrictEncoding.java"
java -cp "$OUT/classes" ExtractStrictEncoding "$SRC/DocumentIO.java" "$OUT/MainActivity.java"
javac --release 11 -d "$OUT/classes" "$SRC/DocumentTab.java" "$SRC/EditorTools.java" "$SRC/ProtectionStore.java" "$SRC/SaveCoordinator.java" \
    "$OUT/MainActivity.java" "$ROOT/verification/save_lifecycle/SaveLifecycleCheck.java"
java -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.SaveLifecycleCheck "$@"
