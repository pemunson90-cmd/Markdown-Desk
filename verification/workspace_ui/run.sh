#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=verification/build/workspace-ui
JSON=verification/build/deps/android-json-0.0.20131108.vaadin1.jar
mkdir -p "$OUT"
javac --release 11 -d "$OUT" verification/workspace_ui/ExtractWorkspace.java
java -cp "$OUT" ExtractWorkspace apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/workspace_ui/WorkspaceHarness.template "$OUT/WorkspaceHarness.java"
mapfile -t STUBS < <(rg --files verification/workspace_ui/stubs -g '*.java')
javac --release 11 -cp "$JSON" -d "$OUT" "${STUBS[@]}" verification/workspace_roots/stubs/android/content/{SharedPreferences,UriPermission}.java verification/workspace_tree_reader/stubs/android/{net/Uri,provider/DocumentsContract,database/Cursor,os/OperationCanceledException}.java verification/workspace_directory_loader/stubs/android/os/Looper.java apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,WorkspaceRootStore,WorkspaceTreeReader,WorkspaceDirectoryLoader}.java "$OUT/WorkspaceHarness.java" verification/workspace_ui/WorkspaceUiCheck.java
java -Xmx64m -cp "$OUT:$JSON" com.example.markdowndesk.WorkspaceUiCheck "$@"
