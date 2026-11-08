#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/WorkspaceDeepLinks.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/search/UnifiedSearchJump.java \
  verification/archive_reader/WorkspaceDeepLinksTest.java \
  verification/archive_reader/WorkspaceDocumentLinksTest.java \
  verification/archive_reader/UnifiedSearchJumpCheck.java
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.workspace.WorkspaceDeepLinksTest
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.workspace.WorkspaceDocumentLinksTest
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.workspace.search.UnifiedSearchJumpCheck
