# Operation-data verification

Run `bash verification/history_operation_data/run.sh` with JDK17. The runner uses
actual storage/control/capacity code, an independent MDOP wire encoder, and a
128 MiB host heap. Optional modes: `unknown`, `prepare`, `domain`, `codec`,
`prefix`, `slots`, `retained`, `loss`, `admission`, `active`, `replacement`,
`policy-stage`, `recovery-accounting`.

The first implementation checkpoint covers read-only MDOP discovery, shared
physical/future accounting, every BEGIN/LINK prefix, full control-slot admission,
known-evidence retention, callback mutation, and linked artifact loss. The golden
vectors independently encode the approved local schema. Missing or partial linked
map/descriptor artifacts block admission even for historical CLOSED operations;
this does not dereference their retired native sources.

The checkpoint has no capture producer or consumer activation. Domain2 BEGIN
publication and stage promotion require the forthcoming prepared capability;
ordinary pin APIs reject them. Native recovery reads remain independent of an
invalid operation inventory. Pending funded preparations permit normal native
writes/policy advancement but block pruning/new captures.

Capture lifecycle, copied PrivateStateCodec frames, process-death/ENOSPC capture
faults, full-byte-limit and simultaneous metadata-budget tests are still pending.
This is not whole-private backup, output eligibility, provider I/O or runtime UI.

## C1 repair checkpoint (2026-10-15; independent review pending)

The interrupted a92a999 source admitted pin resume beside unknown operation data
and acknowledged same-byte PREPARE inode replacement during final reconciliation.
Both actual assertion failures are retained under ignored verification/build.
Publication guards now validate complete operation data before mutation and after
callbacks; resume and terminal recovery revalidate their data proof. Operation
control inode identity participates in fresh ledger fingerprints. Historical
committed CLOSED audits remain source independent and grant no new writer authority.

Current focused runner: 3,374 checks PASS, including policy-stage refusal before
promotion, manifest callback mutation, unchanged partial CLOSE on refusal and
historical audit controls. Affected capacity, pins, operations, maintenance and
executor suites passed: pins20 kill boundaries/42 reopens, operations48 fault
cases/96 reopens, executor44/88. These are host results, not capture producer,
Android durability, complete integration or independent source approval.
The interrupted tracked patch is preserved at
`verification/build/handoff/a92a999-interrupted-accounting-repairs.patch`.
