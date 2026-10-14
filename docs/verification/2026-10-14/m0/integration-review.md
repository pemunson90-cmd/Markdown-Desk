# Independent Task 3 integration review

Reviewed 2026-10-14 against e05baac, using actual current MainActivity, extracted-method harnesses, ADR-001, Task 3, AGENTS, roadmap/status/release gates and /tmp/md-integration-review.diff. Source changes remained uncommitted. No repository source edits or commits made. Core store internals intentionally excluded from this integration review.

## Verdict

**Spec: changes requested.** One reproducible mismatch remains with ADR-001's requirement to bind overwrite consent to document ID AND captured revision. The current continuation binds ID, destination and observed provider hash correctly, but discards the captured edit revision and text.

**Quality: otherwise strong within host-tested boundaries.** The save routes both use the actual coordinator; immutable bytes are captured before dispatch; generic force cannot bypass conflict checks; missing baselines conflict; completed saves update only surviving origin tabs and retain later edits. Store creation canonicalizes the trusted Android parent. Protected recovery listing is asynchronous, validates records, operates independently of session parsing/tab capacity, and uses an explicit separate copy picker. Legacy startup drafts are protected before the old slot can be replaced or cleared; failed protection guards both operations and can retry after repair. No additional blocker found in these paths. Real Android/provider/durability/power-loss acceptance remains unproven.

## Finding

### P2 — Conflict approval recaptures a different edit revision

Location: `MainActivity.java:2087`, `2157`, `2175`–`2180`, `2067`–`2071`.

The worker posts only document ID, destination, observed hash and new-destination flag to the conflict dialog. Overwrite calls `resumeConflictSave`, which switches to the tab and calls `saveInternal` again. That creates a fresh request from the current editor and increments `capturedSaveRevision`. Thus edits made while the original save was running are silently added to the approved request and marked clean, rather than remaining dirty after the captured revision saves. This contradicts ADR-001's explicit captured-revision consent and latest-edits boundary. Correct tab/hash binding does not solve revision binding.

Executable reproduction is `/tmp/IntegrationReviewProbe.java`; it uses the actual extracted activity methods and actual coordinator/store, with existing Android/provider doubles:

1. Start ordinary save with an externally changed provider source.
2. Before draining the worker, replace editor text with `newer edits after save capture`.
3. Deliver the conflict dialog, choose Overwrite, and drain worker/UI callbacks.
4. Observed initial request revision **1**, consent-save revision **2**, provider bytes **newer edits after save capture**, editor dirty **false**.

The probe exits 1 with `Consent was not bound to the immutable captured revision; newer edit was written and marked clean`.

Run after the lifecycle suite:

```sh
"$JAVA_HOME/bin/javac" --release 11 -cp verification/build/save-lifecycle/classes -d /tmp /tmp/IntegrationReviewProbe.java
"$JAVA_HOME/bin/java" -cp /tmp:verification/build/save-lifecycle/classes com.example.markdowndesk.IntegrationReviewProbe
```

Recommended fix: carry the original immutable request/captured text into the dialog closure and create the consent-bound retry from those bytes and that revision, changing only consent hash as necessary. A later editor revision should remain dirty. Alternatively invalidate the old request and require an explicit new save decision when revision changes; do not silently treat recapture as consent to the original request. Add an extracted-method regression for edits during the initial conflict worker and repeated external changes.

## Independently executed evidence

With `PATH="$JAVA_HOME/bin:$PATH"`:

- `bash verification/save_lifecycle/run.sh --safety`: **32 cases, 219 assertions, zero failures**. Includes corrupt snapshot, full quota, canceled/busy picker, same original URI rejection, 12-tab restore, closed/switched origin, stale provider consent, immutable worker capture and failure identity preservation.
- `bash verification/recovery_loader/run.sh`: **13 cases, 179 assertions, zero failures**, plus **7 assertions** on actual UI callback for full-session unmatched recovery and failed protection messaging/action.
- `bash verification/split_resize/run.sh`: **42 UI assertions and 8 controller assertions pass**.
- `bash verification/mode_behavior/run.sh`: **117 actual-method mode/lock assertions and 43 label assertions pass**. Spot-check found no change to existing Focus behavior; divider resizing adjusts pane parameters without rebuilding focus ownership.
- Additional independent consent-revision probe: **FAIL**, as detailed above.

Initial lifecycle invocation lacked javac on PATH; reran with existing JAVA_HOME successfully, without installing tools. No unsigned rebuild, real device, provider hardware, Android directory-sync or power-loss tests run by this reviewer. No release or absolute unchanged-provider-byte guarantee claimed.

## R1 follow-up — captured revision fix independently verified

Reviewed the scoped fix in `/tmp/md-integration-review-fixed.diff` and current source: `Request.withConsent`, `submitCapturedSave`, `resumeConflictSave`, conflict closure arguments, and the added consent revision regression. **Spec approval: PASS. Quality approval: PASS. The P2 above is resolved; no blocking findings remain in the reviewed integration scope.**

`withConsent` returns a new request preserving all captured identity/revision/bytes fields while changing only the observed hash; its constructor clones the bytes. The conflict worker and dialog carry the original request and captured text. Retry no longer captures current editor contents or switches the active tab. Closed-origin and busy guards remain in place. Subsequent conflicts carry the same immutable request forward with a newly observed source hash. Completion compares against the captured text, preserving later edits as dirty. The conflict Save As alternative still resolves the captured tab ID before switching and launching its picker, so it does not retarget whichever tab happens to be active.

Independent rerun: `PATH="$JAVA_HOME/bin:$PATH" bash verification/save_lifecycle/run.sh --safety` passed **33 cases / 227 assertions / zero failures**. The new regression explicitly checks provider A, retained editor B, dirty B, and unchanged transaction revision. Existing stale-consent, switched/closed-origin, and Save As lifecycle checks also pass.

Recompiled and reran the original independent `/tmp/IntegrationReviewProbe.java` unchanged against regenerated extracted methods. It now exits **0**: initial revision **1**, consent revision **1**, provider bytes **edited café 😀\r\n** (captured A), editor dirty **true** (newer B). The original probe exercises editing before the first worker completes, complementing the added fixture's edit after the conflict callback.

This follow-up is scoped to the consent fix; it does not claim new device, Android durability, or power-loss evidence.
