# Funding estimator checkpoint

Base48126b3a1fb3e737dea04000a3ea424ef76fd8a0. Test-only files; no production edits.
JDK17.0.16, --release11, -Xmx64m:4 real B1 cases PASS. Diff check PASS.

| Real B1 fixture | Original spent | Original headroom | Candidate extra | Residual |
| --- | ---: | ---: | ---: | ---: |
| Minimum prefs | 10,973,139 | 5,804,077 | 2,967,262 | 2,836,815 |
| Two8MiB tabs | 11,155,421 | 5,621,795 | 3,136,966 | 2,484,829 |
| 12tabs+recovery+prefs | 12,157,223 | 4,619,993 | 4,072,872 | 547,121 |

A fourth minimum fixture proves actual original-parent debit refusal one byte
below the estimate, no debit change on rejection, no P/artifact creation and no
refund on input closure. Used totals are at actual post-plan checkpoint; unlike
B1 assertion helpers, this probe does not reparse I/catalog with independent test
codec budgets. Runtime path/time lengths explain further small byte variation.

Actual private fields are observed via test-only reflection: capsuleMetadata,
evidence rows and frozen item.source rows; existing B1 test helper observes Budget
remaining. Minimum production access is three original-input-bound read methods:
capsuleMetadataBytes(), capsuleRowCount(), sourceCoordinateAt(ordinal). No caller
count DTO or reflected production state may confer funding/source authority.

Five-control/two-ledger slots remain a current-call-graph upper-count proposal,
not an executable consumer overlap proof. Native cursor256KiB and12B/8192 object
multipliers need allocation validation. At14 frames the maximum modeled CURRENT
native cursor allowance is809,265 bytes, with other candidate components unchanged.
Future key/time representation caps, finite two-per-outer source-open tickets,
provider wrapper tracking and snapshot non-escape must be enforced before consumer
acceptance. Current methods charge parent directly: prepaid admission must replace
those later debits through authentic consumed tickets/borrowers in Preflight/Sources,
Operations and codec budget adapters; otherwise the same costs would debit twice.
No generic child Budget getter, reset, refund or old-credit conversion is valid.

C must also own internal version/domain dispatch in PrivateOperationEvidence's
committed-control parser and any hardcoded C1 baseline classifier. After a REAL
portable v2/domain3 P→BEGIN→LINK→CLOSE, the next capture must retain those exact
owner-associated P/BEGIN/LINK/CLOSE and artifact bytes in its evidence capsule.
Only the new active transport is omitted. Unowned legacy pairs remain domain1-only;
generic public decoders remain blocked. This second-capture contract is recorded,
not executable here because portable publication/activation remains unimplemented.
