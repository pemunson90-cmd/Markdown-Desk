# Saved SAF roots registry

Run `bash verification/workspace_roots/run.sh` with JDK17 on PATH. Uses the existing
pinned host Android JSON jar and focused Context/SharedPreferences/grant doubles,
plus the reader fixture's URI/DocumentsContract doubles. Production store is compiled
unchanged. Tests first: missing-source RED. An additional malformed-trailing-data
regression was RED against initial parsing, then fixed with complete-input parsing.
Final focused run:43 assertions PASS with32MiB heap; actual SDK37 javac compile and
`git diff --check` PASS. No APK, device or broad suite was run.

Covered: missing/write-only/revoked/read persisted grant; exact URI grant comparison;
8/9 roots; immutable results; exact root dedup/touch order; remove does not release
permissions; label256/257, URI4096/4097 and serialized32768 cap; absent/malformed/
oversize/unknown-version/duplicate/trailing registry; failures preserve raw data;
commit false reported even when preferences process memory changed.

Contract: WorkspaceRootStore(Context), list(ContentResolver),
rememberGranted(Uri,String,ContentResolver), remove(Uri). Lists contain immutable
RootState(root,access); Root has uri,label,selectedAt; Access is CURRENT or
ACCESS_NEEDED. These synchronous local APIs must run on the I/O queue. Labels may
be empty but must be nonnull and at most256 UTF-16 units. Registry does not query
provider metadata, take/release grants, mutate documents, silently evict roots or
silently reset malformed data. Actual persisted grant state is checked on each list
or remember. Commit false cannot promise unchanged Android in-memory state; callers
must not claim durable success or release grants as compensation.

Feature integration still requires actual Activity tree picker/browser; registry
alone is not an M3 user-visible completion. Host grant/JSON doubles do not establish
Android persistence, real provider enforcement, restart/device/Fold acceptance.

Review correction: bounded string/escape-aware delimiter preflight runs BEFORE
JSONTokener, with a fixed8-element stack. It rejects nesting9, unmatched delimiters,
non-double-quoted/bare-string/comment syntax and malformed escapes, while ignoring
brackets/braces/apostrophes inside proper strings. This registry schema only emits
quoted strings, integer numbers, objects and arrays; other literal types are refused.
A24001-unit12000-level array produced genuine StackOverflowError RED before this
fix. It now raises IOException for list/remember/remove with raw preferences exactly
preserved and zero commits. Nesting8 and escaped punctuation labels also pass.
Final focused result:61 assertions PASS; actual SDK37 compile and diff check PASS.
No StackOverflowError catch or parser-depth exception suppression was introduced.
