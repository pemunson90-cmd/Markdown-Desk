#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
JSON="$ROOT/verification/build/deps/android-json-0.0.20131108.vaadin1.jar"
mapfile -t STUBS < <(find "$ROOT/verification/workspace_roots/stubs" -type f -name '*.java' | LC_ALL=C sort)
(("${#STUBS[@]}" > 0)) || { echo 'Missing workspace-root fixture stubs' >&2; exit 1; }
javac --release 11 -cp "$JSON" -d "$OUT" "${STUBS[@]}" "$ROOT/verification/workspace_tree_reader/stubs/android/net/Uri.java" "$ROOT/verification/workspace_tree_reader/stubs/android/provider/DocumentsContract.java" "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/WorkspaceRootStore.java" "$ROOT/verification/workspace_roots/WorkspaceRootStoreCheck.java"
java -Xmx32m -cp "$OUT:$JSON" com.example.markdowndesk.WorkspaceRootStoreCheck
