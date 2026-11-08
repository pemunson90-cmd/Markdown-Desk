#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -encoding UTF-8 -d "$OUT" \
 apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveStreamParser.java \
 apps/MarkdownDesk/src/com/example/markdowndesk/JsonUtf8Bom.java \
 apps/MarkdownDesk/src/com/example/markdowndesk/JsonTokenReader.java \
 apps/MarkdownDesk/src/com/example/markdowndesk/JsonDocumentIndexer.java \
 verification/json_explorer/JsonDocumentIndexCheck.java
"${JAVA:-java}" -Xmx64m -cp "$OUT" com.example.markdowndesk.JsonDocumentIndexCheck
