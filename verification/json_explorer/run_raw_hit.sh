#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -encoding UTF-8 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/search/UnifiedSearchJump.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/JsonNodeLocator.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/JsonRawSearchHit.java \
  verification/json_explorer/JsonNodeLocatorTest.java \
  verification/json_explorer/JsonRawSearchHitCheck.java
"${JAVA:-java}" -Xmx64m -cp "$OUT" com.example.markdowndesk.JsonRawSearchHitCheck

"${JAVA:-java}" -Xmx64m -cp "$OUT" com.example.markdowndesk.JsonNodeLocatorTest
