#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
javac --release 11 -d "$OUT" "$ROOT/verification/workspace_search/stubs/android/content/ContentResolver.java" "$ROOT/verification/workspace_tree_reader/stubs/android/"{net/Uri.java,provider/DocumentsContract.java,database/Cursor.java,os/CancellationSignal.java,os/OperationCanceledException.java} "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/"{WorkspaceTreeReader.java,WorkspaceLiteralSearch.java} "$ROOT/verification/workspace_search/WorkspaceSearchCheck.java"
java -Xmx32m -cp "$OUT" com.example.markdowndesk.WorkspaceSearchCheck
