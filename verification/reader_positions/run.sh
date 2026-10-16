#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'python3 -c "import shutil,sys;shutil.rmtree(sys.argv[1])" "$out"' EXIT
json=verification/build/deps/android-json-0.0.20131108.vaadin1.jar
javac --release 11 -cp "$json" -d "$out" verification/workspace_roots/stubs/android/content/{Context,SharedPreferences}.java verification/workspace_tree_reader/stubs/android/net/Uri.java apps/MarkdownDesk/src/com/example/markdowndesk/{ReaderAnchor,ReaderPositionStore}.java verification/reader_positions/ReaderPositionCheck.java
java -Xmx64m -cp "$out:$json" com.example.markdowndesk.ReaderPositionCheck
