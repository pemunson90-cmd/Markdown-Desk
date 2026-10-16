#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
JSON="$ROOT/verification/build/deps/android-json-0.0.20131108.vaadin1.jar"
javac --release 11 -cp "$JSON" -d "$OUT" "$ROOT/verification/workspace_favorites/stubs/android/content/Context.java" "$ROOT/verification/workspace_roots/stubs/android/content/SharedPreferences.java" "$ROOT/verification/workspace_tree_reader/stubs/android/"{net/Uri.java,provider/DocumentsContract.java} "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/WorkspaceFavoritesStore.java" "$ROOT/verification/workspace_favorites/FavoritesCheck.java"
java -Xmx32m -cp "$OUT:$JSON" com.example.markdowndesk.FavoritesCheck
