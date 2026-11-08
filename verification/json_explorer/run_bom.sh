#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -encoding UTF-8 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/JsonUtf8Bom.java \
  verification/json_explorer/JsonUtf8BomCheck.java
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.JsonUtf8BomCheck
