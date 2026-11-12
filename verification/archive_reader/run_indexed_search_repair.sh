#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")/../.."
archive=apps/MarkdownDesk/src/com/example/markdowndesk/workspace/archive
fixture=verification/archive_reader
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
python3 "$fixture/test_indexed_search_repair.py"
javac --release 11 -encoding UTF-8 -d "$tmp" \
  "$archive/ArchiveUnicode61Tables.java" "$archive/ArchiveUnicode61Phrase.java" \
  "$archive/ArchiveSearchOffsets.java" "$fixture/ArchiveUnicode61PhraseCheck.java"
java -cp "$tmp" ArchiveUnicode61PhraseCheck
