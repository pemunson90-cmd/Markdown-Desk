#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/portable-internal-dispatch"
mkdir -p "$out"
javac --release 11 -sourcepath apps/MarkdownDesk/src -d "$out" \
 apps/MarkdownDesk/src/com/example/markdowndesk/{HistoryControlStore,HistoryPinRecord,PrivateOperationStore,PrivateOperationEvidence}.java \
 verification/portable_baseline/BaselineCheck.java \
 verification/portable_wire_codecs/EvidenceCheck.java \
 verification/portable_internal_dispatch/DispatchCheck.java
java -Xmx48m -cp "$out" com.example.markdowndesk.DispatchCheck
