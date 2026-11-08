#!/usr/bin/env bash
# Executable archive core contracts, separate from Android/provider acceptance.
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
APP=apps/MarkdownDesk/src/com/example/markdowndesk
SOURCES=("$APP/archive/ArchiveStreamParser.java" "$APP/archive/ArchiveJournal.java"
 "$APP/archive/ArchiveMarkdownIndexes.java" "$APP/workspace/UnifiedTabIndex.java")
for name in ArchiveViewport ArchiveOverlap ArchiveTextCodec ArchiveSearchSpec ArchiveScrollScript ArchiveAnnotationSearch ArchiveCitations; do
 SOURCES+=("$APP/workspace/archive/$name.java")
done
TESTS=(ArchiveCoreTest ArchiveViewportTest ArchiveOverlapTest ArchiveTextCodecTest
 ArchiveSearchSpecTest ArchiveWorkspaceFeaturesTest ArchiveScrollScriptTest UnifiedTabIndexTest LongIdIndexCheck)
for name in "${TESTS[@]}"; do SOURCES+=("verification/archive_reader/$name.java"); done
"${JAVAC:-javac}" --release 11 -encoding UTF-8 -d "$OUT" "${SOURCES[@]}"
for name in "${TESTS[@]}"; do
 case "$name" in
  ArchiveCoreTest|LongIdIndexCheck) package=com.example.markdowndesk.archive ;;
  UnifiedTabIndexTest) package=com.example.markdowndesk.workspace ;;
  *) package=com.example.markdowndesk.workspace.archive ;;
 esac
 "${JAVA:-java}" -Xmx128m -cp "$OUT" "$package.$name"
done
python3 verification/archive_reader/test_generation_sql.py
python3 verification/archive_reader/test_exact_hit_window.py
