# Protected save lifecycle host regression

Run with JDK17 on PATH:

```bash
bash verification/save_lifecycle/run.sh
bash verification/save_lifecycle/run.sh --safety
```

Current observed safety coverage: **40 cases / 323 assertions PASS**. Baseline
mode retains all ordinary lifecycle cases; safety mode adds six Save As identity
and continuation checks. The initial protected-save additions were RED with
five failures: no original/attempted protection, generic force bypassing changed
or unknown baselines, and an unsupported recovery-draft promise. A later actual
dialog regression was RED when edits made after the conflict were saved under
old consent; retry now retains the original captured bytes/revision and leaves
newer edits dirty. Restore-to-copy
additions separately failed twice before integration.

The runner extracts unchanged production MainActivity method text through javac
AST source positions. It exercises request capture, picker results, actual
conflict dialog closures, protected worker, success/failure callbacks, async
version listing and explicit restore picker, and disk-revalidated copy worker.
The capture fixture now models explicit acknowledged-tab preview ownership;
pending or foreign DOM state cannot replace stored scroll, while acknowledged
current scroll can. The full production navigation predicate runs separately in
`verification/preview_navigation`; this suite models that boundary rather than
returning unconditional readiness. Existing save-route assertions remain intact.

The controller accessor and history invalidation method are also extracted
unchanged. DocumentTab, EditorTools, ProtectionStore, SaveCoordinator,
HistoryController, HistoryCapacity, HistoryIdentity and HistoryIndex are actual
production classes. Only Android widgets, URI/Intent/picker plumbing,
DocumentIO/provider operations and durability system calls have host doubles.
The host durability adapter performs real file and directory sync and atomic
moves; it does not establish Android durability.

Coverage includes exact original/captured attempted bytes, newer edits before
worker execution, durable verified completion, failed protection/read/write/
readback, missing baseline, generic-force rejection, consent after tab switching,
stale source consent, closed origin, pending Save As identity, and recovery
retention. Protected versions list asynchronously, including orphan snapshots.
Restore requires an explicit copy picker, revalidates disk snapshots, refuses the
original URI, uses snapshot bytes rather than active editor text, preserves all
12 tabs, retains evidence, and fails closed on corruption or quota exhaustion.
Canceled and busy copy picker results cannot write.

History integration checks prove shared initialization stays on the I/O queue,
corrupt enrollment publishes no controller and prevents additional provider
writes while keeping dirty edits and exact protected bytes. Raw inspection and
explicit retry after authority repair remain available. Actual identity records
prove same-URI, existing-destination Save As and unsaved TAB-origin provenance;
failed writes/readback and cancellation create no aliases. Consent after tab
switching retains the captured route and revision.

Real corrupt identity/index files exercise optional indexing failure after a
verified provider save: completion, URI adoption and newer edits remain intact,
and the failure callback is not used. A successful continuation may switch tabs
or change URI; the optional warning is checked afterward and suppressed for a
changed destination, different tab, closed origin or finishing Activity. The
actual success callback invalidates and dismisses history only when an active
saving tab adopts a changed URI. Tab restoration itself is a host boundary in
this harness; these assertions cover callback invalidation, not the complete
history UI lifecycle.

The quota fixture fills the acknowledged shared data quota with bounded native
stage files and first verifies complete accounting and unblocked authority. It
then proves restore admission fails before any provider write. The former tiny
ProtectionStore constructor hint ceased to constrain the registered 64 MiB
shared policy; its observed failing assertion led to this explicit quota setup,
not a relaxation of the no-write requirement. Recovery file fidelity and startup
protection flags are checked by the separate recovery-loader suite.

Generated files stay under `verification/build/save-lifecycle`. Separate provider
loss probes remain red: retained recoverable bytes do not imply atomic provider
writes. Real Android/SAF, Samsung Galaxy Z Fold7, process death and physical
power-loss acceptance are **NOT RUN**.
