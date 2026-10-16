#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT=$(mktemp -d)
trap 'rm -rf -- "$OUT"' EXIT
mapfile -t STUBS < <(find "$ROOT/verification/workspace_tree_reader/stubs" -name '*.java' ! -name CancellationSignal.java -print
 find "$ROOT/verification/workspace_directory_loader/stubs" -name '*.java' -print)
javac --release 11 -d "$OUT" "${STUBS[@]}" "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/WorkspaceTreeReader.java" "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/WorkspaceDirectoryLoader.java" "$ROOT/verification/workspace_directory_loader/WorkspaceDirectoryLoaderCheck.java"
java -Xmx32m -cp "$OUT" com.example.markdowndesk.WorkspaceDirectoryLoaderCheck
java -Xmx32m -cp "$OUT" com.example.markdowndesk.WorkspaceDirectoryLoaderCheck worker-reject
for mode in poison poison-cancel poison-reject fatal-runtime fatal-close-error fatal-closed; do
 java -Xmx32m -cp "$OUT" com.example.markdowndesk.WorkspaceDirectoryLoaderCheck "$mode"
done
