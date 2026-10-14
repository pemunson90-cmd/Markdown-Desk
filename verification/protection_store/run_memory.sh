#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/protection-store"
mkdir -p "$OUT/memory" "$OUT/tmp"
javac --release 11 -d "$OUT/memory" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/ProtectionStore.java" "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryStorage.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/SaveCoordinator.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/EditorTools.java" \
 "$ROOT/verification/protection_store/ProtectionMemoryProbe.java"
java -Xmx128m -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/memory" com.example.markdowndesk.ProtectionMemoryProbe
