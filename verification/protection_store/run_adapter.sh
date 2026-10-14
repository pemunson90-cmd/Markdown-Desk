#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/protection-store"
mkdir -p "$OUT/adapter" "$OUT/tmp"
mapfile -t STUBS < <(find "$ROOT/verification/protection_store/stubs" -name '*.java' | sort)
javac --release 11 -d "$OUT/adapter" "${STUBS[@]}" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/ProtectionStore.java" "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryStorage.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/AndroidDurability.java" \
 "$ROOT/verification/protection_store/AndroidDurabilityCheck.java"
java -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/adapter" com.example.markdowndesk.AndroidDurabilityCheck
