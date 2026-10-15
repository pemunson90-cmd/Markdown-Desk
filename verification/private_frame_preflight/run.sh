#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT" \
  "$ROOT/verification/private_frame_preflight/PrivateFramePreflightCheck.java"
java -Xmx96m -cp "$OUT" com.example.markdowndesk.PrivateFramePreflightCheck
