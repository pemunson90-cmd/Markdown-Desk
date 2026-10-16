# Process-wide selected-folder search broker

Helper packet only, not a user-visible search feature. Engine/reader freeze5c93ce2
is consumed unchanged. No Activity, editor, save/session/history, tree permission,
registry or directory-broker source edits. No Office/full-suite/APK/device work.

API: main-only `WorkspaceSearchLoader.trySearch(Context, selectedSavedTree, literal,
WorkspaceLiteralSearch.Limits, Consumer<Delivery>)` returns Ticket or null BUSY.
BUSY prepares no engine/Attempt and does not resolve/retain Activity context. Context
normalizes to getApplicationContext and only its resolver reaches the engine.
Delivery has authentic immutable engine Result or bounded ENGINE_FAILURE enum; no
raw exception/provider text, stream, cursor or caller-supplied CLOSED flag.
`Ticket.cancel()` drops callback/receipt first and calls signal.cancel outside guard.
Provider cancellation may block/throw; it never proves physical resource closure.

The exact private-constructor engine Attempt is stored BEFORE worker submission/I/O.
One static dedicated bounded worker and main Handler own one process slot through
physical execute/finally AND one queued/consuming receipt. No replacement queue,
per-Activity worker, timeout, forced cancellation or reset hook. Definite rejected
submission releases unused admission. Ambiguous accepted submission/post retires UI
and retains reservation until actual completion/receipt can establish disposition.
Fatal Error propagates after finally inspects that SAME Attempt's closeDisposition;
it never fabricates COMPLETE. Ordinary CLOSED failure can retire after receipt.
UNCERTAIN cursor/stream ownership retains exactly the engine-minted Attempt and
process slot until restart, even if Result is CANCELLED, callback throws, UI cancels,
or Handler rejects. Permanent owner retains no Context/Activity/UI callback/receipt;
Attempt contains only engine-owned bounded data/resources. No close retries occur.
No caller enum, Result state, isOpen flag or cancellation can reset this state.

Handler receipt closure captures only Ticket. Callback/receipt clear before consumer
invocation; reentrant admission stays BUSY until its finally consumes the receipt.
Exact owner comparison prevents stale disposal from clearing another Ticket. UI must
still check its own root/search generation/dialog when integrating results. Later
normal-open/hash-bound result navigation belongs to the approved integration design,
not this helper packet.

Run with installed JDK17:

```
PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH bash verification/workspace_search_loader/run.sh
```

Test-first initial runner failed for absent WorkspaceSearchLoader.java. Tests compile
actual reviewed engine+reader, actual new broker and existing directory broker.
Controlled application Context, static main Handler and provider boundaries live only
in verification; no production fault/reset hook or extra dependency. Reuses existing
engine Provider fixture without rerunning its unchanged suite.

Focused final evidence (32MiB host heap):87 assertions PASS:
- normal31: application resolver normalization, real hit, one queued receipt/BUSY,
  BUSY-before-prepare, preowned Attempt while latch-blocked query/open/read, ignored
  cancellation, callback clear before listener/outside monitor, release only after
  physical return, ordinary all-closed failure release, queued cancellation, callback
  throw/reentrant BUSY, Handler rejection and pre-I/O validation cleanup;
- fresh JVM worker rejection2; uncertain root cursor5/cancel5/Handler rejection5;
  uncertain file stream5/cancel5/Handler rejection5; uncertain consumer throw6;
  fatal file+close failure5, fatal cursor+runtime close failure5, fatal cursor+Error
  close failure5, and fatal file with normal close3.
- During each blocked query/open/read, an ACTUAL WorkspaceDirectoryLoader query and
  independent single-thread safety-executor task complete. These prove executor
  separation, not device/provider latency. Dedicated worker's bounded queue is
  handled by finite test-only completion-barrier retries, not product retry logic.
- Test latches/futures use5s deadlines and are not production timeouts. Fatal tests
  verify original Error identity, zero callback and CLOSED versus UNCERTAIN owner.

Actual SDK37 canonical compile PASS (same strategy as app build):

```
javac --release 11 -encoding UTF-8 -classpath <android-37.0/android.jar> \
  -d <temporary-output> WorkspaceTreeReader.java WorkspaceLiteralSearch.java WorkspaceSearchLoader.java
```

Scoped whitespace/diff check PASS. Search/browse/safe-save/session/device acceptance
remain separate. Root will integrate actual selected-root query/results and safe
normal-open/hash/location UI serially; no helper-only M3 completion claim.
