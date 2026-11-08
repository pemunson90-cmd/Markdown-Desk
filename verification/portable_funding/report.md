# Funding estimator checkpoint

Original estimator base48126b3a1fb3e737dea04000a3ea424ef76fd8a0.
Read-only input seam added at worktree base3649461; no production funding consumer.
Existing JDK17.0.16, --release11, -Xmx64m: seam behavior and4 real B1 cases PASS.
Diff check PASS. Table below preserves the earlier estimator checkpoint; current
path/time-dependent counts are in latest-output.txt.

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

The estimator now reads actual frozen inputs through package-private production
methods `capsuleMetadataBytes(originalBudget)`, `capsuleRowCount(originalBudget)`
and `sourceCoordinateAt(originalBudget, ordinal)`. They require identity with the
original Budget and measured/nonfailed/nonclosed/nonmeasuring inputs with no
pending evidence cursor. Indexed immutable source coordinates confer no routing
or writer authority. No whole mutable list, caller-count DTO, budget accessor or
reflected capsule/evidence/item state is exposed. The B1 helper's remaining-budget
test reflection is unchanged.

Dedicated real-input fixtures check equal-limit/equal-spent foreign and null
budgets, unmeasured/closed/failed/pending inputs, invalid ordinals, exact serialized
source rows, actual capsule H/Q, and no debit by these reads. These extra cursor
and test-codec checks run separately from the four estimator checkpoints.

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
