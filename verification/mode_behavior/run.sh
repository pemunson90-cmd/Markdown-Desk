#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/mode-behavior
mkdir -p "$out"
javac --release 11 -d "$out" verification/mode_behavior/ExtractMethods.java
java -cp "$out" ExtractMethods apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/mode_behavior/ModeHarness.template "$out/ModeHarness.java"
javac --release 11 -d "$out" verification/mode_behavior/ExtractLabels.java
java -cp "$out" ExtractLabels apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/mode_behavior/LabelHarness.template "$out/LabelHarness.java"
javac --release 11 -d "$out" "$out/ModeHarness.java" "$out/LabelHarness.java" verification/mode_behavior/ModeBehaviorTest.java verification/mode_behavior/LabelBehaviorTest.java
java -cp "$out" LabelBehaviorTest
java -cp "$out" ModeBehaviorTest
javac --release 11 -d "$out" verification/mode_behavior/ExtractAppearance.java
java -cp "$out" ExtractAppearance apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/mode_behavior/AppearanceHarness.template "$out/AppearanceHarness.java"
javac --release 11 -d "$out" apps/MarkdownDesk/src/com/example/markdowndesk/ReaderTypography.java "$out/AppearanceHarness.java" verification/mode_behavior/AppearanceBehaviorTest.java
java -cp "$out" com.example.markdowndesk.AppearanceBehaviorTest
