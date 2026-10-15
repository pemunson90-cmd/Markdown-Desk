#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/private-capture-writer"
mkdir -p "$OUT/classes" "$OUT/tmp"
FIXTURES="$(mktemp -d "$OUT/tmp/run-XXXXXXXX")"
trap 'rm -rf -- "$FIXTURES"' EXIT
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$ROOT/verification/private_capture_writer/PreparationPublicationCheck.java" "$ROOT/verification/history_pins/HistoryPinsCheck.java" "$ROOT/verification/private_capture_plan/PrivateCapturePlanCheck.java"
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.PreparationPublicationCheck
