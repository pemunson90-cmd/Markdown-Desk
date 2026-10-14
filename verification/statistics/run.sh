#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/statistics"
mkdir -p "$out/classes"
"${JAVAC:-javac}" --release 11 -d "$out/classes" \
  apps/MarkdownDesk/src/com/example/markdowndesk/EditorTools.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/LatestStatistics.java \
  verification/statistics/StatisticsBehavior.java \
  verification/statistics/StatisticsDeliveryCheck.java
"${JAVA:-java}" -Xmx128m -cp "$out/classes" com.example.markdowndesk.StatisticsDeliveryCheck
"${JAVA:-java}" -cp "$out/classes" com.example.markdowndesk.StatisticsBehavior
