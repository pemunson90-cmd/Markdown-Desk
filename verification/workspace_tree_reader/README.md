# Bounded SAF directory query helper

Run with JDK17 on PATH:

```sh
bash verification/workspace_tree_reader/run.sh
```

The runner compiles actual production WorkspaceTreeReader.java against small Android
boundary doubles and executes47 behavioral assertions under a32MiB heap. RED before
implementation: javac failed because the production reader file was absent. GREEN:
47 assertions PASS. `git diff --check` also passed.

Actual Android SDK API compilation also passed:

```sh
/workspace/.command-library-env/jdk-17.0.16+8/bin/javac --release 11 \
  -cp /workspace/.command-library-env/android-sdk/platforms/android-37.0/android.jar \
  -d /tmp/workspace-tree-sdk-compile \
  apps/MarkdownDesk/src/com/example/markdowndesk/WorkspaceTreeReader.java
```

Behavior covered: exact tree/root/nested query construction, fixed projection, same
CancellationSignal, foreign handles/invalid trees, caller/hard limits, conservative
row/result/text cap without lookahead, exact/one-under retained text,100 results with
101st unread,4096/4097 field boundaries, duplicate/missing IDs, missing fields/columns,
known/unknown numeric metadata, immutable result, null cursor, revoked provider/query/
move failures, unavailable cause, getter failure, cancellation before query/during
query/between rows/at EOF, partial retention, and cursor close failure overriding any
otherwise complete/cancelled/revoked result. Each call owns at most one query/cursor
and one immutable page; no content streams, writes or grant mutation APIs exist.

Maximums/defaults:256 scanned rows,100 retained entries,65536 retained UTF-16 units,
4096 units per field (including built document URI). Smaller positive caller limits
are allowed. Retained text includes ID, name, MIME and document URI string units.
Size/modified null or negative means unknown; flags remain raw metadata, not write
authority. Duplicate required IDs make the page FAILED/MALFORMED_PROVIDER. Close
failure makes it FAILED/PROVIDER_FAILURE, so partial results never silently complete.
COMPLETE requires observed EOF; exhausting a row/result budget first is LIMITED even
if a provider happened to contain exactly that many rows. There is no continuation.

The DocumentsContract stub models Android document/children path segment layout
and UTF-8 percent encoding for opaque IDs. Three fixtures assert exact Android-style
URIs for tree/document IDs containing colon, slash and literal percent. The stubs do
not model grant enforcement, Binder/provider/CursorWindow allocation or device SAF
behavior. The actual SDK compile proves type/API compatibility only. Client retained
metadata caps do not bound provider-created string/URI temporaries or whole-process
heap. Cancellation is cooperative: blocked/ignoring providers have no hard deadline.

No broad suite, APK build, device, actual provider, folder-picker/UI integration,
recursive traversal, cross-file search or M3 completion is claimed. This is a new
read-only helper awaiting a future real navigation adapter.

Review correction evidence: corrected URI expectations first failed the old stub
(`exact root query`); after fixing the stub the exact-text-cap regression failed
(`exact text cap LIMITED without lookahead`). Production now checks exhausted text
credit before moveToNext; the regression requires LIMITED and exactly one move.
Final focused run:47 assertions PASS; actual SDK compile and diff check PASS.
