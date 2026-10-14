# Recovery loading and protected reconciliation regression

```bash
export PATH="$JAVA_HOME/bin:$PATH"
bash verification/recovery_loader/run.sh
```

Observed: **15 loader cases / 223 assertions PASS**, plus **7 assertions PASS**
against the actual UI callback and pending-recovery dialog.

The runner uses javac AST source positions to extract exact MainActivity loader,
merge, legacy-protection, recovery replace/clear workers, UI restoration callback,
pruning, draft reconciliation and pending-recovery dialog methods. The loader
harness collects asynchronous delivery; the separate UI harness executes the
actual callback and dialog closure against host widgets. SessionStore,
RecoveryStore, DocumentTab, AtomicCommit, EditorTools, ProtectionStore,
HistoryController and its native shared-capacity/identity/index backends are
production classes. The actual controller accessor is extracted; the raw store
accessor remains a host adapter to the real ProtectionStore. Android Context, handlers, DocumentIO UTF8 encoding and
system durability calls have host doubles; durable protection uses actual host
files with fsync and atomic moves.

Coverage retains independent session/recovery reads, malformed checkpoints,
matching and older drafts, lock/scroll/find preservation, unmatched append and
full 12-tab sessions. Startup protects readable legacy drafts as immutable orphan
attempts even with a malformed session or full tabs. Restart deduplicates the
same URI/title/revision/hash; clearing a protected legacy slot retains immutable
evidence. Protection failures block both replacing and clearing old recovery,
report errors, and retry protection before a later slot mutation after storage
repair. The UI surfaces unmatched recovery independently without creating a
13th tab and links to Protected saves; it reports protection failures even when
the session is valid.

Shared-authority fixtures corrupt the actual enrollment record after native
registration. The loader still delivers independently read session and recovery
content, leaves the controller unpublished, and records failed legacy
protection. Clear/replace attempts preserve exact recovery JSON, session JSON
and existing protected envelope bytes. Raw protected payload inspection remains
readable. Repairing the enrollment permits explicit retry, protecting the old
draft before replacement. A separate registered-bundle fixture verifies the
actual controller accessor reconciles before a new draft snapshot is written;
it models a new Activity in the same host JVM, not process death. These loader
fixtures have no provider-write adapter; the save-lifecycle suite separately
verifies that failed shared startup opens no provider writer.

Before integration, two new loader assertions failed because orphan protection
was absent, and the actual UI callback failed because full-tab recovery was
invisible. A later recovery retry fixture was observed RED before the retry
repair. Original independent-read regressions and storage envelopes remain
preserved.

The pinned host JSON JAR is checked before compilation. Generated files stay
under `verification/build/recovery-loader`. Host widgets are not Android Handler,
SAF, device or physical power-loss acceptance; Samsung Galaxy Z Fold7 checks
remain **NOT RUN**.
