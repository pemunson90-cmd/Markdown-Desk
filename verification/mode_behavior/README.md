# Host execution of extracted mode/lock methods

Run `PATH="$JAVA_HOME/bin:$PATH" bash verification/mode_behavior/run.sh`.
`ExtractMethods.java` uses javac's AST and source positions to extract five
actual current MainActivity methods into a generated host harness. Assertions
execute those bodies; mode/lock logic is not reimplemented in the test doubles.

Observed after the selection repair: 126 assertions pass across Write/Read/Split, Focus on/off, chrome/tools
visibility, padding, mode selection/clearing transitions, lock/read-only affordances and simulated
keyboard branch calls. These now assert literal Enter/Exit Focus action names,
Focus selected state, and existing Lock/Unlock labels. Fake widgets retain counters/state; Toast, Android View,
keyboard effects, `rebuildBody`, persistence/status ancillary methods and paint
are simulated. The scroll-field assertions establish only that the extracted
methods do not directly mutate those simulated fields. `rebuildBody` merely
increments a counter, so actual mode-switch scroll retention is **NOT tested**.

`ExtractLabels.java` additionally copies the actual `createTopButton`,
`createPillButton`, and `appearanceButton` helper bodies and the four AST-selected
size-control construction statements from `showAppearance()`. The extraction
copies the actual anonymous click callbacks as well. `LabelBehaviorTest` checks
43 assertions: helper content descriptions, unchanged visible text/tooltips and
listeners, four independently specified source/reading size action names,
dialog button ownership, and callback routing. The recording button factory,
font-change dependencies and parent layouts are simulated; accessible names are
assigned only by the extracted production statements/helper bodies.

Before the production repair, those fixtures compiled and reproduced 16 missing
helper/Appearance label or tooltip assertions; the mode fixture separately failed
the desired Focus exit-action assertion. After the repair both suites pass.
This verifies label routing, not TalkBack speech or accessibility-node behavior.

Actual Android UI, tab lifecycle integration, WebView reflow, IME/keyboard,
TalkBack, orientation and Fold layout remain NOT RUN. This harness complements
provider/session/renderer fixtures and does not close device acceptance gates.
All generated Java/classes remain under ignored verification/build/mode-behavior.

`ExtractAppearance.java` copies the actual `refreshAppearance()` and
`setAppearanceSelection()` method bodies into recording widgets.
`AppearanceBehaviorTest` passes 4,963 assertions over every theme and all eight
boolean-option combinations, including revisiting previous themes. It checks
selected state, stable accessible names, checkable/checked node values by invoking
the production delegate, unchanged visible theme markers, action exclusion,
control/window identity, and live title refresh. The delegate reads the host's
current selection rather than retaining an obsolete value. Simulated dialog
scroll remains unchanged; this establishes no real Android scroll/focus
continuity. Palette backgrounds, window styling, SplashStore and Android node
creation are doubles. No actual Android accessibility events or speech are run.
The initial unchanged production source failed semantic mode selection and
Appearance Paper selection, independently, before implementation.
