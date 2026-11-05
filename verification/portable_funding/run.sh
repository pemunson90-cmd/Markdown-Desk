#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT" \
 "$ROOT/verification/portable_capture_plan/PortablePlanCheck.java" \
 "$ROOT/verification/portable_funding/PortableFundingCheck.java"
java -Xmx64m -cp "$OUT" com.example.markdowndesk.PortableFundingCheck
