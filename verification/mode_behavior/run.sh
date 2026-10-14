#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/mode-behavior
mkdir -p "$out"
javac --release 11 -d "$out" verification/mode_behavior/ExtractMethods.java
java -cp "$out" ExtractMethods apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/mode_behavior/ModeHarness.template "$out/ModeHarness.java"
javac --release 11 -d "$out" "$out/ModeHarness.java" verification/mode_behavior/ModeBehaviorTest.java
java -cp "$out" ModeBehaviorTest
