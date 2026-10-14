#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/recovery-loader"
JSON_JAR="$ROOT/verification/build/deps/android-json-0.0.20131108.vaadin1.jar"
test -f "$JSON_JAR" || { echo 'Run bash verification/setup_host_deps.sh before offline checks' >&2; exit 1; }
echo "dfb7bae2f404cfe0b72b4d23944698cb716b7665171812a0a4d0f5926c0fac79  $JSON_JAR" | sha256sum --check --status
mkdir -p "$OUT/classes" "$OUT/tmp"
SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk"
javac --release 11 -d "$OUT/classes" "$ROOT/verification/recovery_loader/ExtractMethods.java"
java -cp "$OUT/classes" ExtractMethods "$SRC/MainActivity.java" "$ROOT/verification/recovery_loader/MainActivity.template" "$OUT/MainActivity.java"
javac --release 11 -d "$OUT/classes" "$ROOT/verification/ExtractStrictEncoding.java"
java -cp "$OUT/classes" ExtractStrictEncoding "$SRC/DocumentIO.java" "$OUT/MainActivity.java"
javac --release 11 -cp "$JSON_JAR" -d "$OUT/classes" \
    "$ROOT/verification/recovery_loader/stubs/android/content/Context.java" \
    "$SRC/DocumentTab.java" "$SRC/SessionStore.java" "$SRC/RecoveryStore.java" \
    "$SRC/AtomicCommit.java" "$SRC/EditorTools.java" "$SRC/ProtectionStore.java" "$SRC/HistoryStorage.java" "$OUT/MainActivity.java" \
    "$ROOT/verification/recovery_loader/RecoveryLoaderCheck.java"
java -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes:$JSON_JAR" com.example.markdowndesk.RecoveryLoaderCheck

mkdir -p "$OUT/ui-classes"
java -cp "$OUT/classes" ExtractMethods "$SRC/MainActivity.java" "$ROOT/verification/recovery_loader/RecoveryUi.template" "$OUT/RecoveryUi.java" ui
cp "$OUT/RecoveryUi.java" "$OUT/MainActivity.java"
javac --release 11 -cp "$JSON_JAR:$OUT/classes" -d "$OUT/ui-classes" "$OUT/MainActivity.java" "$ROOT/verification/recovery_loader/RecoveryUiCheck.java"
java -cp "$OUT/ui-classes:$OUT/classes:$JSON_JAR" com.example.markdowndesk.RecoveryUiCheck
