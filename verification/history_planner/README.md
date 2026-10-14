# Pure history retention planner

Run with the installed JDK17 on PATH:

```sh
bash verification/history_planner/run.sh
```

The runner compiles actual production store, catalog, identity, index, graph and
planner classes, then runs native disk fixtures at `-Xmx128m`. It removes only its
newly created per-run fixture directory. No parser/graph doubles or synthetic
valid graph constructors are used. Groups originate from actual snapshot,
prepare, completion, identity and ownership-index commits. Corruption/duplicate
ownership tests alter copies of those native envelopes deliberately.

`HistoryPruner` returns a `Result` containing either an immutable `DeletePlan` or
an explicit `BlockedReason`; blocked results never contain a partial plan. Each
valid request obtains a fresh complete graph. Scan validation failures are
reported as `INCOMPLETE_GRAPH`. Policy must be supplied by the graph; production
default policy is the existing 64MiB/20 descriptor. The planner does not configure
or persist quota policy. This supplied preview profile is not durable effective
admission authority. A future executor must require a registered effective policy
and bind the mode, durable policy generation, current live pins and actual required
charge. A preview profile cannot grant mutation or override the selected quota.

`planAutomatic` preserves the newest indexed group per history and the
configured count as a retention floor. It protects all names and external pins,
then chooses oldest eligible persisted index sequences until both requested
capacities are met. Count zero disables automatic planning after safety validation;
explicit selection still obeys every safety rule. Explicit selection removes
whole named groups only when every checkpoint pin is selected and the fixed
newest validated indexed group of exactly the same history survives. Equal
content, timestamps, provenance, and another history never supply that anchor.

`planAutomaticPressure` is a separate explicit mode. It treats the configured
count as a desired target and may select oldest eligible automatic groups below
that count, only down to each history's fixed newest indexed anchor, until both
caller-supplied requested capacities are met. Count zero disables both automatic
modes. The caller must compute the actual reclaim demand; the planner neither
checks admission nor triggers pressure cleanup. Names, external pins, shared,
pending, orphan and unindexed sources retain the same protection. `DeletePlan.mode`
identifies floor, pressure or selected previews. Legacy floor and selected proof
serialization remains byte-compatible; pressure uses its own canonical proof
domain with explicit mode binding and binds the exact requested capacity.

Bytes are full serialized member envelope lengths. Entries are the sum of actual
members across roots, including selected checkpoint and ownership-index records.
Plans expose byte/entry counts by root. Eligible capacity covers all qualifying
groups before the bounded plan is chosen; protected capacity includes outstanding
completion bytes/slots. Invalid/incomplete requests report zero capacities because
there is no trustworthy complete eligibility proof. Capacity numbers do not
promise shared-quota admission, per-root free slots or physical free space.
`requiredEntries` requests aggregate reclaimed entries across roots. A future
admission controller must use the per-root maps and fresh live accounting to
verify each affected root's slot limit; aggregate capacity cannot replace that check.

A plan binds the complete fresh graph fingerprint, sorted external pin snapshot,
policy, requested capacities, exact member UUIDs/kinds/root roles/filenames/lengths/
full-envelope SHA-256, ownership sequences, directed reference closure, selected
checkpoint IDs, same-history anchor envelopes and immutable identity/provenance.
The complete graph fingerprint also binds every identity/FORK envelope and all
unaffected metadata. Identity records never become deletion members. Bounds are
256 members, a 64KiB canonical streamed proof, and 256 canonical UUID external
pins. Unknown external snapshot IDs block the preview. Constructor pin snapshots
are copied and immutable. A capacity shortfall or member/proof limit returns a
blocked result even when a smaller partial selection would fit.

Fixtures cover immutable/deterministic proof, exact serialized hashes/lengths,
named groups/duplicate labels/partial selections, external pin validity and bounds,
pending/unindexed/orphan protection, shared sources, duplicate ownership,
newest explicit rejection, cross-history anchors, persisted sequence versus
creation order, changed names/pins/identity, every root's retained stages,
corruption/unknown input, source mutation during a scan, large requested capacities,
and member/proof limits. The memory fixture uses a native near-8MiB original and
64 checkpoint labels with 32MiB retained headroom; 128 labels exercise the proof
limit. A deliberately exhausted graph metadata budget blocks planning.

The unchanged planner fixtures report 81 regular and 5 memory checks. Separate
pressure fixtures report 60 regular and 9 memory checks, including independent
legacy v1 proof fingerprint/size compatibility, count zero, names/pins,
no-eligible and partial-capacity shortfall, every root's stages, shared ownership,
fixed anchors and both member/proof bounds. The near-64MiB pressure fixture commits
three paired near-8MiB indexed versions, then near-8MiB fourth sources whose native
preparation fails its existing store quota. A count20 floor preview is blocked;
an explicit pressure preview returns the oldest whole pair and retains the newest
indexed pair and failed-save orphans. It verifies exact serialized accounting and
unchanged disk hashes at `-Xmx128m` with 32MiB retained headroom. This demonstrates
pure pressure planning, without performing deletion or retrying admission.

This is host evidence for pure previews. There is no executor, resume, unlink,
journal, live operation-pin acquisition, maintenance state, mutable policy,
shared admission or UI integration. A future executor must rescan under the common
storage lock, acquire/validate live pins and maintenance state, compare the exact
preview, and commit a durable bounded intent before deletion. These plans grant
no deletion authority. Monotonic allocation after actual deletion and crash/replay
behavior remain executor evidence; Android/SAF/app-UID/power-loss/device/release
acceptance is not claimed.
