# Native Undo / Redo action routing

Run with the existing JDK17:

    PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH bash verification/editor_undo/run.sh

The AST extractor copies actual KineticEditText history routing and MainActivity
configuration, action, shortcut, canMutate, ensureEditor and Markdown-continuation
bodies into minimal host widget surfaces. Missing APIs fail before harness compile.
The test double records native calls; it contains NO pretend undo history stack.

32 checks cover visible action callbacks/names, listener routing without recursion,
unrelated context actions unchanged, invalid/locked routes consumed without native
mutation or focus, provider read-only semantics unchanged, exact Ctrl+Z/Shift+Z/Y
routing and unsupported modifiers, selection ownership, smartEditing restoration
on success/native exception, and replayed newlines not generating list prefixes.
suppressChanges remains false, allowing the ordinary production watcher pipeline.
The installed listener is used by native context actions and TextView's native
Ctrl+Z/Ctrl+Shift+Z dispatch, so they share the activity's lock check.

Production delegates to TextView.onTextContextMenuItem(android.R.id.undo/redo),
public API3 with IDs public sinceAPI23 (app min26). No hidden canUndo/canRedo,
custom stack, text copies or InputConnection replacement. Empty history is a
native no-op; return true does not prove a change occurred. Native visit history
resets when restoreActiveTab loads another document through setText; history does
not persist across tab switches/restart. Android owns edit grouping/composition/
selection. No extra history availability promise is made by enabled buttons.

Additional focused checks ran: existing mode/lock/labels/Appearance host fixtures,
line-index behavior/memory fixtures, and actual changed Android source javac with
SDK37/aapt2-generated resources (--release11). latest-output.txt retains evidence.
This is not an APK/package/signing test and no device/IME/native-undo execution
has been performed. Current-phone acceptance should cover actual keyboard
composition, Unicode/selection replacement, lock/shortcut/context actions, empty
history, branching redo, switching A/B tabs and returning without cross-tab text,
and unchanged recovery/status updates. No portable backup prerequisite applies.
