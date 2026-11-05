# Original capture hash-input witnesses

Run `bash verification/capture_witnesses/run.sh` with the existing JDK17 on PATH.
The five focused groups use real registered backends and independent byte encodings:

- Null and nonnull original constructor profiles, including a profile different
  from effective policy; unchanged graph/C0/native digests and absent-ledger golden.
- Populated native records, typed transaction edges and completion reservations.
- Terminal-cancelled history with partial stage, missing final and retained credits;
  actual original ledger/control/budget identity, absent/present substitution refusal.
- Shared-budget exhaustion, prepaid page boundary/reader ownership, no refunds,
  bounded output failure invalidation and real stream closure.

Production emission uses each existing canonical serializer. `HashInput` is a
prepaid internal metadata source, with pages no larger than 65,536 bytes and no
external output callbacks or filesystem spool. It grants no capture/publication,
recovery or activation authority. An empty ledger witness is valid only for a real
canonical absent ledger/control snapshot; its raw empty SHA is not the binding.

The producer-only Graph scan charges the original budget before control inspection
and graph allocations. Existing public scans retain their byte/hash behavior.
These conservative admission charges do not establish the whole simultaneous
portable producer heap bound or successful backup/app flow.
