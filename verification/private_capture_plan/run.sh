#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT" "$ROOT/verification/private_capture_plan/PrivateCapturePlanCheck.java"
java -Xmx128m -cp "$OUT" com.example.markdowndesk.PrivateCapturePlanCheck
