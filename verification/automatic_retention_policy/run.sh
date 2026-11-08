#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/automatic-retention-policy"
mkdir -p "$OUT/classes" "$OUT/tmp"
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$ROOT/verification/history_capacity/HistoryCapacityCheck.java" "$ROOT/verification/automatic_retention_policy/AutomaticRetentionPolicyCheck.java"
java -Xmx128m -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.AutomaticRetentionPolicyCheck
