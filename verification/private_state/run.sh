#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/private-state
mkdir -p "$out/classes"
"${JAVAC:-javac}" --release 11 -d "$out/classes" \
  apps/MarkdownDesk/src/com/example/markdowndesk/PrivateStateCodec.java \
  verification/private_state/PrivateStateCheck.java \
  verification/private_state/PrivateStateMemoryCheck.java
"${JAVA:-java}" -Xmx128m -cp "$out/classes" com.example.markdowndesk.PrivateStateCheck
"${JAVA:-java}" -Xmx128m -cp "$out/classes" com.example.markdowndesk.PrivateStateMemoryCheck "$out/data"
