#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -encoding UTF-8 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveJournal.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveMarkdownIndexes.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/WorkspaceDeepLinks.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/annotations/JsonPathCodec.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/annotations/VerifiedTextAnchor.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/annotations/JsonInspectorNoteIdentity.java \
  verification/json_explorer/JsonInspectorNoteIdentityCheck.java
"${JAVA:-java}" -Xmx64m -cp "$OUT" \
  com.example.markdowndesk.workspace.annotations.JsonInspectorNoteIdentityCheck
