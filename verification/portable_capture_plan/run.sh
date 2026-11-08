#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/portable-capture-plan"
mkdir -p "$out"
javac --release 11 -sourcepath apps/MarkdownDesk/src -d "$out" \
 apps/MarkdownDesk/src/com/example/markdowndesk/PrivateCapturePlan.java \
 verification/portable_capture_plan/PortablePlanCheck.java
java -Xmx64m -cp "$out" com.example.markdowndesk.PortablePlanCheck
