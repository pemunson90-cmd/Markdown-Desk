# Split and tab accessibility host seam

Run `PATH="$JAVA_HOME/bin:$PATH" bash verification/split_resize/run.sh` with JDK17.

The extractor uses javac AST source positions to execute the exact production
`updateTabsUi`, `rebuildBody` and `adjustSplitRatio` methods. It also extracts the
actual gallery remove label construction statements. Fully qualified Android
node/Bundle types are mapped to narrow host widget doubles; listeners execute
normally. `SplitResizeController` is compiled unchanged and exercised separately.

Initial red: 8 failures among 16 UI assertions (full tab name, active/refreshed
selection, contextual gallery name, divider focus/inclusion/delegate/key); the
controller test failed explicitly because the adjustment operation did not exist.
Green: 42 UI assertions and 8 controller assertions. Additional passing checks
cover range/actions, +/-0.05 and midpoint operations, complementary layout
weights, immediate adjustment persistence, unchanged drag/release/cancel behavior,
compact vertical Split, Write/Read and all twelve tabs. Unknown keys/actions and
key-up are ignored/delegated.

This is production-method host execution, not an Android accessibility tree,
TalkBack speech, focus-navigation, measured touch-target or phone/Fold test.
The Android method source also type-checks with JDK17/SDK37.0. No touch-target
layout sizes, palette, assets or additional Focus mode are introduced.
