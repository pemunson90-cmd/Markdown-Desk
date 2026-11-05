#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
mapfile -t STUBS < <(find "$ROOT/verification/workspace_tree_reader/stubs" -name '*.java' -print)
javac --release 11 -d "$OUT" "${STUBS[@]}" "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/WorkspaceTreeReader.java" "$ROOT/verification/workspace_tree_reader/WorkspaceTreeReaderCheck.java"
java -Xmx32m -cp "$OUT" com.example.markdowndesk.WorkspaceTreeReaderCheck
