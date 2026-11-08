#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=${SEARCH_UI_OUT:-verification/build/workspace-search-ui}
SOURCE=${SEARCH_UI_SOURCE:-apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java}
JSON=verification/build/deps/android-json-0.0.20131108.vaadin1.jar
mkdir -p "$OUT"
javac --release 11 -d "$OUT" verification/workspace_search_ui/ExtractSearchUi.java
java -cp "$OUT" ExtractSearchUi "$SOURCE" verification/workspace_search_ui/SearchHarness.template "$OUT/SearchHarness.java"
mapfile -t STUBS < <(rg --files verification/workspace_ui/stubs -g '*.java' | rg -v '/(ContentResolver|Button|EditText|InputFilter)\.java$')
mapfile -t OWN < <(rg --files verification/workspace_search_ui/stubs -g '*.java')
javac --release 11 -cp "$JSON" -d "$OUT" "${STUBS[@]}" "${OWN[@]}" verification/workspace_roots/stubs/android/content/{SharedPreferences,UriPermission}.java verification/workspace_tree_reader/stubs/android/{net/Uri,provider/DocumentsContract,database/Cursor,os/OperationCanceledException}.java verification/workspace_directory_loader/stubs/android/os/Looper.java apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,WorkspaceRootStore,WorkspaceTreeReader,WorkspaceDirectoryLoader,WorkspaceLiteralSearch,DocumentTab,EditorTools}.java "$OUT/SearchHarness.java" verification/workspace_search_ui/SearchUiCheck.java
java -Xmx96m -cp "$OUT:$JSON" com.example.markdowndesk.SearchUiCheck "$@"
