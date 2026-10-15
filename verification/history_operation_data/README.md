# Operation-data verification

Run `bash verification/history_operation_data/run.sh` with JDK17. The runner uses
actual storage/control/capacity code, an independent MDOP wire encoder, and a
128 MiB host heap. Optional modes: `unknown`, `prepare`, `domain`, `codec`,
`prefix`, `slots`, `retained`, `loss`.

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
