# Bounded synchronous selected-folder literal search

With existing JDK17 on PATH:

```sh
bash verification/workspace_search/run.sh
```

Tests-first RED: javac failed because WorkspaceLiteralSearch.java did not exist.
Initial focused result:49 assertions PASS under32MiB heap, using ACTUAL
WorkspaceTreeReader and new production WorkspaceLiteralSearch. Host provider/stream/
Cursor boundaries are controlled; no Android/provider behavior is fabricated as a
real-device result. No files outside the new engine/fixture packet were changed.

Actual SDK37 API compile and diff check also passed:

```sh
/workspace/.command-library-env/jdk-17.0.16+8/bin/javac --release 11 \
  -cp /workspace/.command-library-env/android-sdk/platforms/android-37.0/android.jar \
  -d /tmp/workspace-search-sdk-compile \
  apps/MarkdownDesk/src/com/example/markdowndesk/WorkspaceTreeReader.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/WorkspaceLiteralSearch.java
git diff --check
```

## API for later loader/UI integration

Construct WorkspaceLiteralSearch(applicationContentResolver, exactSelectedTree).
prepareSearch(literal,Limits,CancellationSignal) returns an internally minted
one-use Attempt BEFORE resources can be acquired. The loader must retain it before
calling Attempt.execute(), which synchronously returns immutable Result; result()
accesses only a completed Result. closeDisposition() is CLOSED or UNCERTAIN. Attempt has a private constructor and privately retains any unresolved
owned InputStream and/or reader-minted cursor Listing if actual close threw,
including fatal Error paths. The future process loader MUST hold this
Attempt and fail-stop further search admission on UNCERTAIN; no retries, caller-
supplied clear boolean, generic resource framework, or UI stream escape exists here.

Result: tree,literal,state,reasons,hits,diagnostics and measured counters bytesRead,
directoryAttempts,fileAttempts,scannedRows,metadataUnits,candidateFiles,
admittedDirectories,skippedTypes. Candidate file credit is spent before dedup/open; actual open
attempt counter is separate. FileHit groups URI/documentId/displayName/raw-byte
SHA256/byteLength with immutable Match(offsetUtf16,lengthUtf16,line,snippet). Lines
start at1; offsets index BOM-stripped editor text and preserve raw CRLF/CR/LF.
Diagnostic contains bounded URI and enum reason, never provider error message.

Limits defaults are also hard maxima:128 directories/512 candidate files/4096 rows/
depth16,16MiB total actual bytes/8MiB accepted file,100 matches,128 query UTF16 units,
160 snippet units,262144 global retained metadata units plus one bounded reader Page.
All caller limits must be positive and no greater. Literal must be valid nonempty
Unicode, fit query and snippet allowances, and is case-sensitive literal only.

One work queue, visited opaque IDs and cumulative credits control all directories;
no per-directory reset or fake provider pagination. Directory depth/admission/file
admission overflow skips new work but still processes previously admitted work;
shared row/byte/metadata/match exhaustion stops traversal. Reader partial/failure
never becomes complete later. UTF8 REPORT decoder and SHA stream through fixed
buffers; no full-document String/byte clone. Pending matches/snippets commit only
after full EOF/decoder validation AND successful actual stream close. Only initial
UTF8 BOM is omitted from matching; hash covers all original bytes. KMP allows overlaps
and buffer-crossing matches. Snippet cuts preserve surrogate pairs.

State precedence is CANCELLED, then FAILED, then LIMITED, then COMPLETE. Enum reasons
preserve mixed failures. Nontext MIME/non-.md/.markdown/.txt rows are outside explicit
Markdown/text scope. Repeated opaque IDs are graph-deduplicated and recorded CYCLE;
partial reader listing remains incomplete regardless. Root/child authority checks
and actual provider permissions remain authoritative; no writes/grant mutation.

Global EOF rule: no read at zero remaining byte credit. Exact8MiB valid input accepts
when global EOF-probe headroom remains; a returned oversized sentinel byte is counted
globally and invalidates that file. Two exact8MiB files exhaust16MiB and conservatively
leave the latter incomplete because EOF is unproved; no uncharged lookahead. At100
matches current file still finishes strict decode/hash/close before its hits commit.

## Focused coverage

49 assertions include recursive actual reader traversal; BOM/CRLF/CR/LF/supplementary
Unicode UTF16 offsets+line numbers+raw SHA; overlap/case sensitivity/cross-buffer
matching and surrogate-safe snippet limits; exact128 literal/129/unpaired refusal;
malformed trailing UTF8/late read failure/stalled read drops staged hits; uncertain
close retains owned-resource disposition; close-time/read-time/query-time/pre-query
cancellation; partial directory/revocation distinct from empty; opaque cycle/encoded
slash-colon-percent IDs; hard128 directories (no129 query),512 file opens (no513),
4096 cumulative rows,depth16 (no17 query); hard and smaller caller caps; exact8MiB,
8MiB+1 and two8MiB exact global cutoff;100/101 matches; shared metadata exhaustion
across Pages; immutable results; binary MIME exclusion without opening.

No loader, Activity UI, threads, private app-state archive, writes, APK build, broad
suite, device SAF/power-loss/removable-provider/Fold acceptance or whole-heap bound
is claimed. Matching actual displayed hash/revision before a normal-open jump is
required in the later real UI packet. Engine readiness alone is not M3 completion.


## Owned cursor/Throwable review correction

Actual regression first failed `root cursor uncertain stops before second query/open`
on the original engine/reader. Final consolidated packet: reader52, engine60,
process-directory broker123 total assertions across eight fresh-JVM modes, actual
Workspace UI120 checks PASS, actual SDK compile of all three classes and diff check
PASS. No Activity changes were needed for the owned-reader API adaptation.

Reader now mints one-use Listing before query. Engine retains it before read and
immediately stops on uncertain close, before queueing any partial children. Root
and child cursor failures prove no subsequent query/open; actual scanned rows are
still charged. Attempt retains unresolved cursor as well as stream receipts. After
normal completion or fatal Error, Run/queues/source inputs are released; only
immutable completed Result and actual unresolved resources/failure receipt remain.

Fatal cursor/stream body Error followed by close RuntimeException or Error preserves
original body Error identity and owned uncertainty. Fatal body Error with successful
close is rethrown with CLOSED disposition. Post-open decoder/allocation work remains
inside owned-stream try/finally. No Error is converted to fake empty Result; no
isClosed check, retry/reset, generic resource framework or provider grant change.

Skipped-type scalar counts noncandidate scanned file rows (max4096); extension-
qualified .md with binary MIME is still a candidate and not counted skipped. No
unbounded skipped labels are retained. This adds no binary reading scope.

Terminal cursor-close cancellation follow-up: new empty-root normal-close probe was
RED (`empty root terminal close cancellation has priority and retains receipt/reason`).
Engine now observes the signal once after traversal/cleanup returns normally and
before Result minting, including stop due to cursor uncertainty. Original fatal
Errors still propagate and do not reach this normal-result check. Six new actual-
cursor cases cover empty/root-with-child/child close cancellation, normal and throwing
close, no follow-up query/open, CANCELLED precedence and retained CLOSE_UNCERTAIN
reason/receipt. Final focused66 assertions PASS; SDK compile/diff check PASS.
Reader, broker, Activity and their fixtures were not changed or rerun in this follow-up.
Frozen engine blob: a80fe242fabe5c5951a7c3b81e1d04f3395f72fc.
