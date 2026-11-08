#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac --release 11 -d "$OUT" apps/MarkdownDesk/src/com/example/markdowndesk/JsonReaderSelection.java apps/MarkdownDesk/src/com/example/markdowndesk/workspace/annotations/VerifiedTextAnchor.java verification/json_explorer/JsonReaderSelectionCheck.java
java -Xmx32m -cp "$OUT" com.example.markdowndesk.JsonReaderSelectionCheck
