#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac --release 11 -d "$OUT" apps/MarkdownDesk/src/com/example/markdowndesk/JsonTokenReader.java apps/MarkdownDesk/src/com/example/markdowndesk/JsonExplorerEngine.java apps/MarkdownDesk/src/com/example/markdowndesk/JsonUtf8Bom.java verification/json_explorer/JsonStreamingEngineCheck.java
java -Xmx32m -cp "$OUT" com.example.markdowndesk.JsonStreamingEngineCheck
