# Protected save lifecycle host regression

Run with JDK17 on PATH:

```bash
bash verification/save_lifecycle/run.sh
bash verification/save_lifecycle/run.sh --safety
```

Current observed safety coverage: **33 cases / 227 assertions PASS**. Baseline
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
DocumentTab, EditorTools, ProtectionStore and SaveCoordinator are actual
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

Generated files stay under `verification/build/save-lifecycle`. Separate provider
loss probes remain red: retained recoverable bytes do not imply atomic provider
writes. Real Android/SAF, Samsung Galaxy Z Fold7, process death and physical
power-loss acceptance are **NOT RUN**.
