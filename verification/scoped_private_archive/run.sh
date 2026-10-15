#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/scoped-private-archive"
mkdir -p "$out"
files=(apps/MarkdownDesk/src/com/example/markdowndesk/PrivateArchive.java)
if [[ -f apps/MarkdownDesk/src/com/example/markdowndesk/PrivateBundleInventory.java ]]; then
 files+=(apps/MarkdownDesk/src/com/example/markdowndesk/PrivateBundleInventory.java)
fi
javac --release 11 -sourcepath apps/MarkdownDesk/src -d "$out" "${files[@]}" verification/scoped_private_archive/*.java
java -Xmx64m -cp "$out" com.example.markdowndesk.ScopedArchiveCheck
