#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT" \
  "$ROOT/verification/capture_native_sources/NativeCaptureSourcesCheck.java"
java -Xmx128m -cp "$OUT" com.example.markdowndesk.NativeCaptureSourcesCheck
