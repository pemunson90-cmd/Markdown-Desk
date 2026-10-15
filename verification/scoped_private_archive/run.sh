#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/scoped-private-archive"
mkdir -p "$out"
javac --release 11 -sourcepath apps/MarkdownDesk/src -d "$out" \
 apps/MarkdownDesk/src/com/example/markdowndesk/{PrivateArchive,PrivateBundleInventory}.java \
 verification/scoped_private_archive/*.java
java -Xmx64m -cp "$out" com.example.markdowndesk.ScopedArchiveCheck
java -Xmx48m -cp "$out" com.example.markdowndesk.MemoryCheck
