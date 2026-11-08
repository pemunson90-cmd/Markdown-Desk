#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
javac --release 11 -d "$OUT" verification/source_extraction/ParsedJavaSource.java verification/archive_navigation/ExtractNavigation.java
java -cp "$OUT" ExtractNavigation apps/MarkdownDesk/src/com/example/markdowndesk/ArchiveWorkspaceActivity.java verification/archive_navigation/NavigationHarness.java.template "$OUT/NavigationHarness.java"
javac --release 11 -d "$OUT" "$OUT/NavigationHarness.java" apps/MarkdownDesk/src/com/example/markdowndesk/workspace/WorkspaceDeepLinks.java
java -cp "$OUT" NavigationHarness
