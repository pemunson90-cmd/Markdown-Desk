# Process-wide directory query broker

`WorkspaceDirectoryLoader.tryLoad(reader, directory, mainDelivery, onPage)` returns
one Ticket or null BUSY. It uses defaults-only WorkspaceTreeReader limits and one
process-wide fixed worker (bounded queue capacity1), independent of save/session I/O.
There is no pending replacement request, per-Activity worker, recursive query or
provider timeout. Main-thread admission/cancel/delivery are enforced through Looper.

Reader must be constructed from the application ContentResolver. mainDelivery must
be a nonblocking main Handler.post wrapper: if post returns false, throw
RejectedExecutionException; never deliver inline or throw after enqueueing. The
callback still checks its Activity/generation/root/reader/frame/dialog token. A
foreign opaque Directory is programmer misuse, not a provider error: it retires
without a Page/callback, so integration must not request invalid handles and leave
a spinner awaiting an impossible result. Actual provider errors are reader FAILED
Pages and are delivered normally.

Ticket cancellation first clears callback and delivery-executor fields under the
broker guard, then invokes CancellationSignal.cancel outside that guard. Cancellation
can itself enter/block provider code. It does not release the physical slot. A
provider ignoring cancellation keeps later Activities BUSY until query AND cursor
close return. An already queued result keeps its single Page/slot until the main
runnable is consumed and discards it. Queued/worker closures capture only Ticket;
no separate consumer or Activity closure survives retirement. One accepted operation
can hold one bounded Page; there is no unbounded request/result queue.

Delivery detaches Page/callback on main and holds admission through callback return.
Its finally clears fields and releases only its own owner, even if the consumer
throws. Reentrant query from inside a callback is BUSY. A callback already executing
cannot be revoked. Cancel after finish is harmless. A worker rejection clears all
fields/releases admission and throws to the caller. A completion rejection after
query closure drops the Page without queuing or resurrecting UI and releases the
slot. The helper never fabricates empty success, releases grants or writes state.

## Focused tests

Run `bash verification/workspace_directory_loader/run.sh` with JDK17. It compiles
the actual broker and actual reader with controlled Android boundaries, then runs
bounded latch-based scenarios in32MiB JVMs. All latches/futures terminate within
finite5s test limits; these are fixture failure deadlines, not provider timeouts.

Latest: **87 assertions PASS** plus **3 worker-rejection assertions PASS** in a
separate fresh JVM. Coverage:

- Ignored query cancellation,40 new-Activity-style BUSY attempts, one real query,
  actual CancellationSignal forwarding and cursor closure before disposal.
- Independent save-executor task completes while the browser provider is blocked.
- Worker handoff and queued-callback cancellation retain the slot until consumption;
  callback/executor refs clear immediately, Page clears after discarded delivery.
- Blocked cursor.close retains admission; cancelled pre-query worker task retains
  admission until consumed, then skips query.
- Provider cancellation listener observes already-cleared UI refs and waits for
  worker disposal successfully, proving signal.cancel does not hold broker guard.
- Real101-row cursor yields default-limited100-entry Page; main-thread cancel and
  admission violations refuse without mutating valid work.
- Normal delivery, consumer exception/finally, callback reentrant BUSY, completion
  rejection followed by successful retry, foreign handle no-Page retirement and
  worker refusal with no query or delivery.

Tests reflect only private state/executor for lifecycle evidence and a final fresh-
JVM shutdown rejection seam; production has no test hook/reset/shutdown method.
A test barrier briefly retries rejection while a deliberately bounded worker queue
is occupied by the real query; it never expands the production queue.

Initial TDD run failed because the loader API was absent. The existing reader,
Activity and other source files were not changed. SDK37 compilation of actual
WorkspaceTreeReader + WorkspaceDirectoryLoader also passed with JDK17:

```sh
javac --release 11 -cp /workspace/.command-library-env/android-sdk/platforms/android-37.0/android.jar \
  -d <temporary-output> apps/MarkdownDesk/src/com/example/markdowndesk/WorkspaceTreeReader.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/WorkspaceDirectoryLoader.java
```

No aggregate run, APK build, browser Activity integration or physical SAF/device
acceptance is claimed. Application-resolver/Handler executor policy and UI token
validation must be enforced by that later integration. This prerequisite alone is
not the folder-browser feature.


Owned-cursor correction: Ticket retains reader-minted Listing BEFORE read(), then
only a proven normal close may retire physical admission. Uncertain FAILED Page
may notify once; after delivery/cancel/rejected delivery/fatal Error, static owner
and stripped Listing/cursor receipt remain process-lifetime BUSY. Page, reader,
directory, signal inputs, callback and delivery executor references are dropped;
Listing has no Page/entry graph. Original fatal body Error is rethrown even if close
also fails; fatal body with successful close releases normally. No retry/isClosed
heuristic/reset or Activity edits. Existing87+3 assertions and six new fresh-JVM
modes8/5/5/6/6/3 assertions all PASS (123 total). Search root-cursor regression was
RED before source correction; broker compilation was RED while its removed-list
callsite awaited adaptation. Final actualUI120 and actual SDK compile PASS.
