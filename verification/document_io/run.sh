#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/document-io"
mkdir -p "$OUT/classes" "$OUT/tmp"
mapfile -t STUBS < <(find "$ROOT/verification/document_io/stubs" -name '*.java' | sort)
javac --release 11 -d "$OUT/classes" "${STUBS[@]}" \
  "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/DocumentIO.java" \
  "$ROOT/verification/document_io/DocumentIOCheck.java"
java -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.DocumentIOCheck "$@"
