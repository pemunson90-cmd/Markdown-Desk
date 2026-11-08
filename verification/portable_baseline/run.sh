#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/portable-baseline"
mkdir -p "$out"
javac --release 11 -sourcepath apps/MarkdownDesk/src -d "$out" \
 apps/MarkdownDesk/src/com/example/markdowndesk/PrivateOperationStore.java \
 verification/portable_baseline/BaselineCheck.java
java -Xmx48m -cp "$out" com.example.markdowndesk.BaselineCheck
java -Xmx48m -cp "$out" com.example.markdowndesk.BaselineCheck large
