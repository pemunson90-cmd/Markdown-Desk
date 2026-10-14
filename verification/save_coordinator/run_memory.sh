#!/usr/bin/env bash
# Host heap regression only: does not prescribe Android heap/latency budgets.
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/save-coordinator"
mkdir -p "$out" "$out/tmp"
javac --release 11 -d "$out" apps/MarkdownDesk/src/com/example/markdowndesk/ProtectionStore.java apps/MarkdownDesk/src/com/example/markdowndesk/SaveCoordinator.java apps/MarkdownDesk/src/com/example/markdowndesk/EditorTools.java verification/save_coordinator/ProtectionMemoryProbe.java
java -Djava.io.tmpdir="$out/tmp" -Xmx128m -cp "$out" com.example.markdowndesk.ProtectionMemoryProbe 32
