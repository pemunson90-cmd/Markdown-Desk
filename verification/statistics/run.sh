#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/statistics"
mkdir -p "$out/classes"
"${JAVAC:-javac}" --release 11 -d "$out/classes" \
  apps/MarkdownDesk/src/com/example/markdowndesk/EditorTools.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/LatestStatistics.java \
  verification/statistics/StatisticsBehavior.java
"${JAVA:-java}" -cp "$out/classes" com.example.markdowndesk.StatisticsBehavior
