#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/fixtures"
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT" "$ROOT/verification/native_terminal_validation/NativeTerminalValidationCheck.java"
java -Djava.io.tmpdir="$OUT/fixtures" -cp "$OUT" com.example.markdowndesk.NativeTerminalValidationCheck
java -Djava.io.tmpdir="$OUT/fixtures" -Xmx40m -cp "$OUT" com.example.markdowndesk.NativeTerminalValidationCheck large "$OUT/root"
java -Djava.io.tmpdir="$OUT/fixtures" -Xmx8m -cp "$OUT" com.example.markdowndesk.NativeTerminalValidationCheck probe "$(cat "$OUT/root")"
java -Djava.io.tmpdir="$OUT/fixtures" -Xmx8m -cp "$OUT" com.example.markdowndesk.NativeTerminalValidationCheck race
java -Djava.io.tmpdir="$OUT/fixtures" -cp "$OUT" com.example.markdowndesk.NativeTerminalValidationCheck reentry
