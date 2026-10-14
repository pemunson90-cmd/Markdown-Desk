#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/session-recovery"
JSON_JAR="$ROOT/verification/build/deps/android-json-0.0.20131108.vaadin1.jar"
test -f "$JSON_JAR" || { echo 'Run bash verification/setup_host_deps.sh once before offline checks' >&2; exit 1; }
echo "dfb7bae2f404cfe0b72b4d23944698cb716b7665171812a0a4d0f5926c0fac79  $JSON_JAR" | sha256sum --check --status
mkdir -p "$OUT/classes" "$OUT/tmp"
SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk"
javac --release 11 -cp "$JSON_JAR" -d "$OUT/classes" \
    "$ROOT/verification/session_recovery/stubs/android/content/Context.java" \
    "$SRC/DocumentTab.java" "$SRC/SessionStore.java" "$SRC/RecoveryStore.java" \
    "$SRC/AtomicCommit.java" "$SRC/EditorTools.java" \
    "$ROOT/verification/session_recovery/SessionRecoveryCheck.java"
java -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes:$JSON_JAR" \
    com.example.markdowndesk.SessionRecoveryCheck
