#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/save-coordinator"
mkdir -p "$out"
javac --release 11 -sourcepath apps/MarkdownDesk/src -d "$out" apps/MarkdownDesk/src/com/example/markdowndesk/ProtectionStore.java apps/MarkdownDesk/src/com/example/markdowndesk/HistoryStorage.java apps/MarkdownDesk/src/com/example/markdowndesk/SaveCoordinator.java apps/MarkdownDesk/src/com/example/markdowndesk/EditorTools.java verification/save_coordinator/SaveCoordinatorCheck.java
java -Xmx1g -cp "$out" com.example.markdowndesk.SaveCoordinatorCheck
