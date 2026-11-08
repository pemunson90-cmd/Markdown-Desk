#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
mkdir -p "$OUT/classes" "$OUT/tmp"
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$ROOT/verification/control_inspection_bounds/ControlInspectionBoundsCheck.java"
java -Xmx32m -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.ControlInspectionBoundsCheck
