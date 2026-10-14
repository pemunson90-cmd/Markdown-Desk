#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/private-archive"
mkdir -p "$out" "$out/tmp"
javac --release 11 -d "$out" \
 apps/MarkdownDesk/src/com/example/markdowndesk/{PrivateArchive,ProtectionStore,HistoryStorage,HistoryCatalog,HistoryMetadataIO,HistoryIdentity,HistoryIndex}.java \
 verification/private_archive/{ArchiveCheck,ArchiveMemoryCheck,ArchiveIndexCheck}.java
case "${1:-all}" in
  codec) java -cp "$out" com.example.markdowndesk.ArchiveCheck
         java -Djava.io.tmpdir="$out/tmp" -cp "$out" com.example.markdowndesk.ArchiveIndexCheck ;;
  memory) java -Xmx128m -Djava.io.tmpdir="$out/tmp" -cp "$out" com.example.markdowndesk.ArchiveMemoryCheck ;;
  all) java -cp "$out" com.example.markdowndesk.ArchiveCheck
       java -Djava.io.tmpdir="$out/tmp" -cp "$out" com.example.markdowndesk.ArchiveIndexCheck
       java -Xmx128m -Djava.io.tmpdir="$out/tmp" -cp "$out" com.example.markdowndesk.ArchiveMemoryCheck ;;
  *) echo 'Usage: run.sh [all|codec|memory]' >&2; exit 2 ;;
esac
