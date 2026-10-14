# Host execution of extracted mode/lock methods

Run `PATH="$JAVA_HOME/bin:$PATH" bash verification/mode_behavior/run.sh`.
`ExtractMethods.java` uses javac's AST and source positions to extract five
actual current MainActivity methods into a generated host harness. Assertions
execute those bodies; mode/lock logic is not reimplemented in the test doubles.

Observed: 99 assertions pass across Write/Read/Split, Focus on/off, chrome/tools
visibility, padding, selected controls, lock/read-only affordances and simulated
keyboard branch calls. Fake widgets retain counters/state; Toast, Android View,
keyboard effects, `rebuildBody`, persistence/status ancillary methods and paint
are simulated. The scroll-field assertions establish only that the extracted
methods do not directly mutate those simulated fields. `rebuildBody` merely
increments a counter, so actual mode-switch scroll retention is **NOT tested**.

Actual Android UI, tab lifecycle integration, WebView reflow, IME/keyboard,
TalkBack, orientation and Fold layout remain NOT RUN. This harness complements
provider/session/renderer fixtures and does not close device acceptance gates.
All generated Java/classes remain under ignored verification/build/mode-behavior.
