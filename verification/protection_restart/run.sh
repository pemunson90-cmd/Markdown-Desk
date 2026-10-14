#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/protection-restart"
mkdir -p "$out"
javac --release 11 -d "$out" apps/MarkdownDesk/src/com/example/markdowndesk/ProtectionStore.java apps/MarkdownDesk/src/com/example/markdowndesk/HistoryStorage.java apps/MarkdownDesk/src/com/example/markdowndesk/SaveCoordinator.java apps/MarkdownDesk/src/com/example/markdowndesk/EditorTools.java verification/protection_restart/RestartProbe.java
python3 verification/protection_restart/run.py "$out"
