# Protected saves UI host fixture

Run with Java 17: `bash verification/protected_saves_ui/run.sh`.

The javac AST extractor inserts unchanged production protected-list/session/page/action,
new-copy picker, lifecycle, and theme helper members. The Android UI and backend list
read are controlled boundaries; metadata-only snapshots are fixture values, not a
second implementation of storage validation. The existing `save_lifecycle --safety`
suite still extracts this actual list route and tests an orphan snapshot through the
real store and restore-to-copy provider flow, including corrupt snapshot rejection.

82 checks cover 203 sorted versions across first/middle/last pages, empty state,
Previous/Next, explicit selected snapshot ID/title to the existing picker, stale
worker/delivery/request/row/page/confirmation callbacks, dismiss/pause/destroy,
reference clearing and save-before-cancel ordering, busy restore rejection, and actual
colors/background/recycled row rebinding for all nine palettes.

At most 100 version labels are produced per page. The store still reads its full
authoritative inventory; this is not a bounded whole-inventory or heap guarantee.
Host checks and SDK compilation do not replace Android device/SAF/accessibility tests.
