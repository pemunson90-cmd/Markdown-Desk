#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$PWD/verification/build/recents-integrity"
mkdir -p "$OUT"
JSON="$PWD/verification/build/deps/android-json-0.0.20131108.vaadin1.jar"
test -f "$JSON" || { echo "Run bash verification/setup_host_deps.sh before offline Recents checks" >&2; exit 1; }
"${JAVAC:-javac}" --release 11 -cp "$JSON" -d "$OUT" \
  verification/recents_integrity/stubs/android/content/SharedPreferences.java \
  verification/recents_integrity/stubs/android/content/Context.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/RecentStore.java \
  verification/recents_integrity/RecentStoreIntegrityCheck.java
"${JAVA:-java}" -Xmx64m -cp "$OUT:$JSON" com.example.markdowndesk.RecentStoreIntegrityCheck
