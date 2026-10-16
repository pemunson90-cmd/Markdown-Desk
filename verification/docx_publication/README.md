# Captured DOCX staging and verified binary publication helpers

The guarded publication prerequisite is now wired through the independently reviewed
Activity controls and private picker proxy; see verification/docx_ui. This helper
runner alone is not evidence of the whole user-visible journey. The reviewed MarkdownDocx core and all
editor/save/session/history/renderer code are unchanged. No APK/device/Word gate,
aggregate run or portable app-data feature is claimed.

## API and lifetime

DocumentDocxExport.tryStart(application context, Supplier<Capture>, ready, failure)
returns one Ticket or null BUSY. Admission precedes supplier/source copying. Capture
counts strict UTF8 <=1MiB before toString, caps UTF16/title/URI metadata and snapshots
source plus originally open URIs. Ticket.publish binds one fresh destination and
current-open URI snapshot to the same offered ticket. Duplicate/stale tickets refuse.
Only main-thread control/delivery is allowed. Stage generation and binary provider
work use one process-wide bounded worker, independent of save/session/browser IO.

No Activity is stored; Context normalizes to its application context. Static Handler
callbacks are not swept by Activity destruction. READY retains only length/hash,
warning counts/offsets, captured title and sanitized filename; the source Capture
has been removed and its generation method unwound before READY is posted. READY
callbacks detach before delivery; no UI callback is retained while picker is visible.
Cancel clears UI refs immediately, holds physical ownership through pending I/O and
queued callback consumption/cleanup, and never starts a new worker to bypass an old
blocked provider. Main callbacks that throw run retirement/cleanup finally. User
provider exceptions are not copied into Completion or warning strings.

Caller must route only its exact ACTION_CREATE_DOCUMENT result to its pending Ticket,
check live UI/request identity, cancel on disposal, and drop unbound recreated-process
results. Picker-induced onPause cannot by itself destroy the pending export. These
Activity behaviors are covered separately in verification/docx_ui.

## Stage ownership and resource limits

DocxStage owns only app cache/md-docx-stage-v1/<canonical UUID>.stage. One8MiB stage
or recognized orphan is permitted. Bounded directory listing rejects a second member,
unknown names, symlinks, nonregular/oversized files and changed actual file keys.
Recognized prior-process orphan is removed and directory-synced before new creation;
no recursive parent scan or other private-file deletion. Failed cleanup blocks fresh
staging instead of accumulating files. Cache removal/tamper never authorizes recapture
from a changed editor. Cleanup/namespace uncertainty can leave DOCX admission BUSY.

Exclusive nofollow creation, checked file force/close and Android Os directory
fsync/close precede READY. Writer independently spends attempted bytes BEFORE each
write; partial exceptions do not refund budget during ZIP-finally retries. Core
success counts alone are not used to prove orphan-byte bounds. Reopened stage is
stream-verified by exact key/length/EOF/SHA before offering and before provider writer.
No complete ZIP byte[] is retained by production helpers. Copy/hash buffers <=64KiB;
source/rendering heap remains the reviewed core's separate bounded-input concern.

FileChannel.isOpen(false) is not a physical close witness. Normal close return records
successful disposal; actual close failure marks unknown disposal. Force failure plus
successful final close is disposable but never a successful generation. Private
reader/listing/directory and generic provider stream close failures are tracked.
UNCERTAIN_IO clears UI refs and retains ONLY the DOCX process slot; closing/reopening
an Activity does not reset it. Ordinary editing/save/session remain independent.
There is no claimed provider timeout or automatic proof-recovery after uncertain close.

## Provider boundary

Exact original/current-open URI matches and framework-authentic same-authority opaque
DocumentsContract document IDs refuse before output. Generic/different-provider IDs
are not invented or inferred from paths. Metadata rejects directory/malformed/nonzero
size; actual empty binary EOF AND successful input close are required even if size0.
Exactly one openOutputStream(uri,"w") attempt is allowed, including null/failure;
there is no alternate mode, destructive fallback, saved destination reuse or retry.

Binary copy checks stage identity/length/hash, flush and output close. Fresh binary
readback is bounded to expected length+1 (<=8MiB+1) and must match SHA/length with a
successful input close before verified completion. No UTF8 DocumentIO decoder is used.
Partial/failed/cancelled provider output is never automatically deleted. Source and
open-document bytes/identity/dirty/baseline/history are never changed by these helpers.
Owned resources use finally/try-with-resources, including finite provider Errors;
body Errors propagate after close attempts, with suppressed unknown-close evidence
retaining the DOCX slot. Fixed copy buffers are allocated before writer open.

Generic CREATE_DOCUMENT and empty-preflight are not atomic exclusive-new writes.
Different IDs can alias; a provider/another actor can replace/fill a destination or
lie between probes and open. These guarded semantics require compliant providers;
no universal malicious-provider or alias/race safety is claimed. Real picker result
provenance and permission handling belong to later Activity integration.

## Verification

Run `bash verification/docx_publication/run.sh` with JDK17. Actual3helpers + reviewed
core compile against controlled Android boundaries. Latest final focused result:

-131 main assertions PASS: real durable core staging, strict namespace/actual-key
  checks, outside-private-file preservation, unknown/symlink/duplicate/oversized orphan
  refusal,8MiB exact/one-over attempted cap, partial-write budget nonrefund, tamper
  refusal, provider7-part ZIP, aliases/nonempty/lying-size before zero writers, null/
  write/flush/close/hash/short/extra/read/query failure paths and one output open.
- Physical/queued ownership, source-free READY, pre-capture BUSY, stale publication,
  callback exception/finally, cancellation during latch-blocked writer and queued
  READY/DONE, independent safety executor, fsync failure and Handler rejection PASS.
- Fresh JVM groups: worker rejection2; directory close uncertainty3; provider close
  uncertainty4; fatal writer with normal close3; fatal writer+failed close3 PASS.
- Finite getter/empty-read/readback/write AssertionErrors prove cursor/stream closure,
  no success and retirement/uncertainty distinction. Fake FileChannel whose logical
  state closes BEFORE throwing proves isOpen(false) is insufficient.
- Independent Python zipfile/XML/CRC checks inspect actual provider-produced bytes
  for all7 fixed parts, correct content, XML safety and aggregate8MiB bound PASS.

Latches/futures use finite5s fixture deadlines; JVM heap64MiB. Those are test controls,
not provider timeouts or measured Android peak budgets. Test reflection/fault boundaries
remain entirely in verification; production has no reset/test hook. Initial TDD test
failed for absent stage API. Root source inspection additionally caught exceptional
provider closure paths; finally ownership and finite-Error regressions were added.

Actual Android37 canonical compiler strategy PASS after final source changes:

```
javac --release 11 -encoding UTF-8 -classpath <android-37.0/android.jar> \
  -d <temporary-output> MarkdownDocx.java DocxStage.java DocxNewCopy.java DocumentDocxExport.java
```

This matches apps/MarkdownDesk/build.sh:67. A separately attempted stricter stub-only
`-source8 -target8 -bootclasspath android.jar` FAILED because SDK37's LambdaMetafactory
stub omits javac's metafactory bridge. No product change/new compiler shim was made;
that failed attempt is not a new gate. Source API scan found no new JDK9+ methods:
NIO file APIs require Android26, functional/stream APIs24, suppressed exceptions19;
app minSdk26 covers these. No APK build was run.

Frozen source SHA256 for independent review:

- DocxStage.java:9fb87f4e44aa8165a7bf2c6cb608c75d0ec01cee7e087192c2c8c440babb7cee
- DocxNewCopy.java:e8b7f80f064dae14e299b5b3eea06824ce52412a9a84f16efe6cf9b98c7ff80c
- DocumentDocxExport.java:f62774193353f4042de85839270e74654ede0aa5bc6dba1324842ff4e5fa025a

No source edits are underway pending independent review. No commits made.


## Unified independent-review fixes (current frozen packet)

Both initial independent reviews requested changes. All three reported regressions
were observed RED before the unified source fix: finite verifier AssertionError left
an actual stage descriptor open (/proc/self/fd); supplier AssertionError retained the
unused owner/UI callbacks; checked writer IOException produced GENERATION instead of
WRITE. Earlier green tests did not cover these paths and do not waive the findings.

Stage now protects the entire post-open transfer, including constructor and new
ReadHandle allocation, across IOException/RuntimeException/Error. Until successful
return, the channel is closed; a failed close remains suppressed uncertainty on the
original failure, and a returned stage records uncertainty before risky cleanup.
Private takeCreated is the production channel-transfer boundary and is exercised
reflectively with finite constructor Error and a logically closed channel whose
physical close throws. No general test hook, arbitrary-file API or new framework.
Coordinator generation Error handling preserves pre-assignment suppressed uncertainty.

Supplier/Capture errors propagate unchanged while clearing only proven-unused
admission/source/UI state. Rejected worker submission clears unused admission;
ambiguous submission RuntimeException/Error retires UI but conservatively retains
physical reservation until an accepted worker can dispose it. Checked write/flush
and readback IOException now receive WRITE/VERIFY phase wrappers; existing typed
cancellation/close failures and cause/suppressed-close evidence stay intact.

Final unified focused run PASS:131 main +15 prior fresh-JVM checks, plus30 new
review checks (stage5, supplier5, phase9, read-close uncertainty4, checked-write+close
uncertainty4, unwrapped typed cancellation3), total176 assertions. Actual FD-count
checks do not invoke GC. Next admission/generation succeeds after unused supplier
Error; unknown stage read close keeps the file and DOCX owner, even though logical
channel state is closed. Combined primary checked-I/O and secondary close failures
retain UNCERTAIN_IO rather than losing suppressed evidence. Independent actual
provider ZIP/XML/CRC checks PASS. Canonical actual SDK37 compilation repeated PASS
after fixes; exact flags remain documented above. No Office/aggregate/APK/device
pipeline was duplicated. Current source hashes above supersede the initial rejected
freeze. Ready for scoped re-review; no source edits/commits underway.
