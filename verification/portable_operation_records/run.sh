#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/portable-operation-records"
mkdir -p "$out"
javac --release 11 -sourcepath apps/MarkdownDesk/src -d "$out" \
 apps/MarkdownDesk/src/com/example/markdowndesk/{PrivateOperationRecord,HistoryPinRecord}.java \
 verification/portable_operation_records/RecordCheck.java
java -Xmx32m -cp "$out" com.example.markdowndesk.RecordCheck
