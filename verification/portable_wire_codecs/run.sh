#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/portable-wire-codecs"
mkdir -p "$out"
javac --release 11 -sourcepath apps/MarkdownDesk/src -d "$out" \
 apps/MarkdownDesk/src/com/example/markdowndesk/{PrivateArchive,PrivateBundleInventory,PrivateOperationEvidence}.java \
 verification/scoped_private_archive/{ScopedArchiveCheck,FramingCheck,InventoryCheck}.java \
 verification/portable_wire_codecs/{WireCheck,ArchiveMemoryCheck,EvidenceCheck}.java
java -Xmx32m -cp "$out" com.example.markdowndesk.WireCheck
java -Xmx32m -cp "$out" com.example.markdowndesk.ArchiveMemoryCheck
java -Xmx32m -cp "$out" com.example.markdowndesk.FramingCheck legacy
java -Xmx32m -cp "$out" com.example.markdowndesk.EvidenceCheck
