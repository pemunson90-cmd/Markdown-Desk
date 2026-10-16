# Single captured displayed-text hash

New SearchHitVerifier helper only. Actual selected-hit navigation UI owns exact
FileHit/tab/URI/loadToken/content revision/BOM association; no source/history/dirty
mutation or navigation is implemented here. No provider/resources/context/URI/hit
parameter, full encoded document clone, per-keystroke hashing or portable machinery.

Approved API:

```
SearchHitVerifier.Ticket tryVerify(Supplier<Capture> captureAfterAdmission,
                                 Consumer<Outcome> callback);
new SearchHitVerifier.Capture(CharSequence displayedText, boolean utf8Bom);
Ticket.cancel();
Outcome.result.byteLength / Outcome.result.sha256 // OR Outcome.problem == HASH_FAILED
```

Controls/Capture and callback delivery are main-thread-only. Actual supplier must
read/capture the current Editable lazily after process-wide admission. BUSY returns
null without invoking or retaining supplier/callback. Capture premeasures valid
UTF16 and strict UTF8 including actual initial BOM bytes BEFORE toString. Limits:
UTF16<=DocumentIO.MAX_DOCUMENT_BYTES (8Mi units), strict UTF8<=8MiB including3BOM bytes.
Neither mutable Editable nor supplier is retained after tryVerify returns.
Worker independently uses strict REPORT CharsetEncoder and checks actual bytes,
without trusting measured metadata or assuming normalization. Only one immutable
String can be operation-owned; worker consumes Capture field before hashing and
its source-containing stack scope unwinds before queuing source-free Result/enum.
Actual raw CRLF/bareCR/LF/middle U+FEFF are preserved; BOM true prefixes3bytes only.

Static dedicated bounded worker/static main Handler/one owner spans actual hash AND
queued/consuming receipt. Callback drops immediately on cancel. Replacements cannot
queue retired8MiB snapshots while old hashes still execute or receipts remain queued.
Cancellation checks between8192-byte encoding operations and after terminal digest
and before result/post; it cannot force blocked cryptographic/provider calls to end.
No I/O/unknown-close state is involved. Fatal Errors propagate after finally clears
capture/UI and releases physically finished admission. Rejected submission/delivery
retire proven-unused ownership; ambiguous accepted submission/post keeps reservation
until actual task/receipt settles rather than inventing physical completion. Consumer
throw/reentrant admission are protected by delivery finally. No reset/test hooks.

Peak additional operation retention is conservatively one source String<=8MiUTF16
(up to16MiB backing under2bytes/unit accounting; canonical editor memory excluded),
8192-byte encoding buffer plus constant encoder/digest/CharBuffer state, and one
64hex+length Result or bounded enum receipt/callback. No full UTF8 output array.
Capture may retain source only until worker takes it. Main receipt/terminal fields
contain no source. These are allocation/retention caps, not measured whole-app heap.
A failing pre-copy source method/Error releases unused owner without normal callback.

Run with installed JDK17:

```
PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH bash verification/search_hit_verifier/run.sh
```

Test-first missing-helper RED observed. Focused final76 checks PASS:
- ordinary64: actual DocumentIO.encodeStrict oracle (length+SHA) for empty, BOM-only,
  middle BOM, BOM policy both ways, raw CRLF/bareCR/LF, supplementary/combining/RTL/CJK,
  encoder-boundary surrogate pair and exact8MiB includingBOM. Unicode/UTF16/UTF8/BOM
  over-bound before-copy rejection. Immutable one-copy snapshot and worker independent
  malformed/over-byte rejection. BUSY-before-supplier, queued capture removal,
  queued cancellation, consumer throw/reentrant BUSY, Handler rejection, main-only
  admission and finite Errors before/during toString capture.
- fresh JVM: worker rejection2, supplier Error2, blocked physical hash cancellation3,
  terminal digest cancellation3, bounded runtime hash failure1, original worker fatal
  Error identity/clear source+callback+owner1. Test-only JCA provider delegates real
  SHA256 except finite latches/faults; production has no digest injection mechanism.
  Latches/futures use5s test deadlines; no production timeout claim.

Ordinary oracle JVM96MiB accommodates actual DocumentIO's independent whole-output
oracle allocations; lifecycle/fault JVMs32MiB. This does not claim measured Android
memory. Existing DocumentIO source and all its provider boundaries compile unchanged;
its full suite is not repeated. Actual SDK37 canonical --release11 Android classpath
compile of DocumentIO+SearchHitVerifier PASS. Scoped diff check PASS. No broad suite,
Office/APK/device or actual Activity selection journey is claimed by this packet.
