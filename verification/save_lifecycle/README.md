# Save lifecycle host regression

Run with JDK17 on PATH:

```bash
bash verification/save_lifecycle/run.sh
bash verification/save_lifecycle/run.sh --safety
```

The normal suite checks save behavior; `--safety` adds original-identity and
stale-continuation protection. Both must pass before claiming this repair works.

Observed with the repaired handler: normal **10 cases / 80 assertions PASS**;
safety **16 cases / 127 assertions PASS**. The expanded safety suite was RED
before production edits: 16 cases / 92 assertions / 6 failures. Earlier bounded
coverage produced normal 7/65 PASS and safety 11/76 with four failures; untitled
and Save As interleavings were added before the repair.

The runner uses `javac --release 11` and writes generated sources/classes only
under `verification/build/save-lifecycle`. `ExtractSaveMethods.java` parses the
actual MainActivity with javac and extracts exact source-position text for
`onActivityResult`, `captureActiveTab`, `findTabById`, `saveInternal`, save worker
`lambda$saveInternal$35`, success `lambda$saveInternal$33`, failure
`lambda$saveInternal$34`, and five referenced constants. DocumentTab and
EditorTools are actual production source. No handler/worker/callback logic is
reimplemented. Missing targets and duplicate signatures fail loudly; only the
explicit `saveInternal(boolean)` and `saveInternal(boolean,Uri)` signatures are
accepted, and both method bodies are extracted exactly.

The Android boundary is modeled: two explicitly drained worker/main queues,
editor/button/status widgets, Intent/Uri, metadata, RecentStore, Toast and an
in-memory provider. The provider supports successful writes, thrown write
failure and incorrect readback bytes. The harness implements only the
DocumentIO boundary used by the extracted worker; these tests do not validate
actual DocumentIO encoding, SAF providers or provider durability. Switching
tabs uses a small fixture restore adapter; actual capture logic is extracted.
The conflict dialog branch intentionally throws if reached and is not covered.

Normal coverage checks verified save, successful Save As including an untitled
tab with null current URI, newer active edits remaining dirty with recovery
scheduled, ordinary and Save As callbacks after switching tabs, a callback after
closing its tab, ordinary provider write/readback failures and picker
cancellation. It verifies provider target and version bytes, identity
publication, dirty/recovery flags and pending followup handling.

Safety reproductions against the original handler:

- Save As result is delivered but the worker has not run: the origin tab already
  points to the destination and its original baseline is cleared.
- The Save As worker throws during write or fails byte readback: the tab keeps
  the unverified destination identity instead of the original identity,
  baseline and read-only policy.
- The originating tab closes while the picker is pending: delivering its
  result writes the unrelated active tab to the selected destination.
- A picker result arrives while another save is running: it adopts the
  destination identity even though the new save request is rejected.
- A Save As completion arrives after its originating tab closes: the completion
  leaves a pending continuation that a later save could consume.

The bounded repair aborts a create result if the captured tab ID no longer
exists or another save is running, clears the picker continuation, and passes
an explicit pending destination into the save request without mutating
currentUri/tab identity or baseline. The overload checks the supplied
destination so untitled Save As works. The existing verified-success callback
adopts destination metadata and retains newer text as dirty. It now clears a
stale continuation when the originating tab no longer exists.

These are host lifecycle tests, not actual Android process death, UI rendering,
SAF integration, provider atomicity, or crash-recovery durability. They are
separate from the future immutable save-protection contract/model. Passing
these tests cannot prove that the existing save path protects both source and
attempted bytes before a destructive provider write.
