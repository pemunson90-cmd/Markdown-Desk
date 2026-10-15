# Current project status

## Current checkpoint — protected capture and scoped private backup, 2026-10-15

The [whole-roadmap campaign plan](superpowers/plans/2026-10-15-professional-campaign.md)
maps retained M0–M7 obligations and dependencies. Independent substantive campaign
plan review is approved at `73372f50db109c378235406a75ca7abbf4cd85a6` against
ROADMAP and RELEASE_GATES; reviewed plan blob `029269ba83b6897d4e0148c797db63311ddddda2`.
Main/origin main remains
`fdb7bd66c0078cf7b88b53924ee3a16c24d194e3` (PR #23 merged). The active branch is
`codex/operation-data-capture`, draft [PR #24](https://github.com/pemunson90-cmd/Markdown-Desk/pull/24);
Reviewed A1 portable wire codecs are integrated at `88fb149` on the active draft
PR #24; earlier writer/copy approvals retain their exact-artifact scope.

The last full clean integration gate is **PASS** at `8dcbea538e67e5bfb588f81d895457aa3a85a472`.
It ran the required host suite once and audited two fresh unsigned builds, both
SHA-256 `eea09f21460790581908dd38b0971226b00fa9475dc3202ad5a2d2973a975bb8`.
Evidence includes 3434 operation-data assertions, operation48 fault fixtures/96
reopens, pins20 kill fixtures/42 reopens and executor44/88. Identity/assets,
alignment and unsigned audits passed. This gate predates the later C2 sources and
does not cover them. No newer full suite, build or hosted run is claimed.

Native fingerprint `a4cb514` and source ownership/budget `e331477` have scoped
independent approvals and are integrated. Private-frame preflight `c3177b8` has
independent approval (8 groups; worker30 scenarios), is integrated at `2f1d14b`,
and its runner passed30 cases. These approvals apply to those exact artifacts.
Private capture plan `a89b284` also has scoped independent approval (9 groups;
worker29 groups) and normal merge `16280fc`. It requires fresh matches for both
original C0 control and L0 ledger hashes before P. A legacy PIN BEGIN→CANCELLED
CLOSED can change C0/fullGraph while leaving L0 unchanged; this counterprobe
reproduced and requires a guard. Plan authority alone does not authorize P.

PrivateCaptureWriter exact commit `92e41bb5827cf7d1d553bbe720744f20f239538e` is
independently APPROVED. Its targeted runner passed 87 assertions; independent review
passed 4 scenarios. Review found and fixed a cached installed-capacity/readiness bug
with a narrow provenance getter and fresh identity/readiness checks after each
callback. Approval remains scoped to this exact writer; the production sink,
BEGIN/LINK, handoff, recovery and whole-heap proof are excluded.

Completion/handoff commit `1fe3c464e3e3f7ceb48dcceba34989dc9b0dc695` is
independently APPROVED after 4 groups. Worker results pass: 87 publication
assertions, 7 copy cases and 11 completion cases, including fresh-child JVM checks.
It implements exact BEGIN204/LINK173/CLOSE credit126, typed-stage allowance,
preadmitted retained maps and one-use old-owner-claim handoff. Earlier writer `92e41bb`,
copy helper `b4bd0c9` and private sink `d71dae2` approvals remain point-in-time and
scoped to those artifacts; none approves the complete current source.

Same-process activation and tracked declared-source read/drain/CLOSE are implemented
at exact `4795c3cdaeacea5a7ef134284929cdecba1c269f`; scoped independent review is
APPROVED. Worker evidence: 6 focused groups PASS after the retention RED repair.
The earlier activation coordinator59 checkpoint remains historical; later cold-path
coordinator results are scoped separately below.
Independent groups prove identity, budget, hashed skip and resource closure, plus
`terminalRetry` retaining `sourceTable` versus TERMINAL releasing the full table
and budget while retaining bounded D for historical CLOSE.
The implementation requires authentic returned binding and the exact original Budget,
uses declared nofollow bounded EOF/SHA readers, and freezes/drains before exact D→CLOSE.
Actual throwing close supports retry; generic domain2 activation/factories stay rejected.
[ADR-006](decisions/ADR-006-terminal-cancelled-capture-admission.md) is ACCEPTED
engineering direction after independent review of exact `e8cb1532d514e1d2e0c46d93c8d5f20e52c44820`,
ADR blob `b54b1629f72f213fb9bf735a6c3a76266ca70cdb`. The first typed historical-cancellation
capture-admission slice is implemented at `af9eebf97174e02c4ab64794a3e80e606179f443`;
scoped independent source review is **APPROVED** after 4 actual probe groups:
classification, immutable old-stage anchor refresh, funded capture→activate/read/drain/CLOSE
preserving old credits, and planner/intent/stale maintenance/retirement refusals with
an ordinary-save positive control. Worker evidence: 12 focused groups
and 871 legacy HistoryManifestCheck checks PASS, recorded in
[terminal admission evidence](../verification/terminal_cancelled_admission/README.md).
This slice retains global pending/prune blocks and all bytes/credits,
a physically present verified namespace, selected activation's own completeness and
immutable old-stage anchors. ADR-006 supersedes only the old plan's new-capture-block
clause for its fully typed eligible state; all other contracts remain.
Narrow cold cancellation at `2078e8e2ceb401ffad4277d6428fac62f3a2e9db` has scoped
independent **APPROVAL**. Two new actual probes passed: foreign uncertain audit
RED→GREEN with bytes unchanged, and legitimate activated/drained audit positive.
Earlier owner-exclusion, stale-final strict-ledger and callback groups retain their
exact scope. The cold-only callback marker (a793960) and foreign-audit guard before
publication/every refresh (2078e8e) fix both blockers. Latest worker evidence: 12 cold
groups PASS, including paired fresh JVMs. Activation6 passed at the initial cold slice;
coordinator59 was RED there, GREEN at a793960 and not rerun at 2078e8e.
Cold completion/reversed-original-graph recovery, portable backup, runtime integration,
full-heap and performance, migration and device acceptance remain OPEN. Minimum affected memory bounds are
required before downstream storage consumers; broad batteries remain for feature completion.
Writer, activation, terminal-admission and cold-cancellation runners are registered in
the aggregate host script; only shell syntax was checked for this registration revision. No 48-fault matrix, new full
suite, APK or CI is claimed. Root owns orchestration and integration.
Portable source contract `d43dbb17e110edb73eaf68d4597f208b4186d5d0` (plan blob
`dab01bedacdafb650120a5d6339e3bde94694f01`) is accepted after independent e7 review
and root's mechanical fix. A1 archive3/schema2 inventory/evidence wire codecs at exact
`f98ca2913ebc40cbcef0520fba7c91a0a3f4ffe2` have scoped independent APPROVAL and
are integrated at `88fb149`. Worker affected evidence: Wire51, archive memory3,
legacy selector1403 and Evidence53 final checks (39 before repair plus 14).
Review exposed duplicate owner/stage aliases; reader and writer are corrected,
and 8 independent budget/control checks passed. Archive462 plus INDEX68 remain
previous affected proof, not new runs. See the [affected codec notes](../verification/portable_wire_codecs/README.md).
A2 record codecs at exact `2736de7c6105532b62672b5b250d6bfa17a6c72d` have independent
spec/quality APPROVAL and are integrated at `08abce8d`. Worker RecordCheck72 passed
with Java11 under `-Xmx32m`. Review inspected the actual five-file diff with no blocker
or further probe warranted; no duplicate run. Root verified exact integrated artifacts
with `git diff --quiet`. See the [record codec notes](../verification/portable_operation_records/README.md).
Runtime backup completion, semantic routing/adoption and coordinated export/import remain OPEN.
The portable wire and operation-record runners are each registered once in the required host aggregate;
only `bash -n verification/run_host_checks.sh` ran for this registration change.
No aggregate or codec tests were rerun for this documentation/runner update.

During implementation, run minimum affected tests and essential compile/build checks;
reserve broad regression/performance/migration coverage for the feature-complete
candidate. Avoid duplicating full gates; mandatory integration/release gates remain required.

B0 canonical witness streams at exact `afc57ec95d0f971325c29b2e9cbfc1ab9233e1f5`
have independent spec/quality APPROVAL and are integrated at `d1303eba1cbae823749761493d945292bf4ab4b9`.
Root verified the four source files and fixture match exactly. Worker evidence: five
new witness groups PASS; existing plan29 ran once for shared canonical serializer
compatibility. Partial-prefix seal and foreign-Budget defects were caught and fixed
RED→GREEN; review required no extra tests. Conservative B0 workspace bounds do not
prove the whole coordinator fits16MiB. Canonical inputs preserve the original nullable
profile and shared budget; witnesses confer no authority. The witness runner is
registered once; this update ran shell syntax/diff checks only.

Portable baseline sources at exact `cce8a6ee34785b889ff58aa7ea142c8640711347` have
independent spec/quality APPROVAL and are integrated at `fb22bff4`; root verified exact
Store/fixture matches. Worker focused59 plus large1 PASS: a 9,437,187-byte UNKNOWN stage
with 36MiB held under `-Xmx48m`. Review duplicated no tests. Canonical hashes and generic
admission predicates are unchanged. One owned cursor exposes 72 policy rows, original
provenance, exact physical/missing credits and canonical `private_retained` absence only.
Additive read-only funded missing-root inventory preserves credits via
`sourceOnlyMissingRoot`; generic historical capture/Graph/native admission still rejects
that state and B1 must refuse it. A snapshot grants no write authority. Strict original
C0/L0/tree checks apply PRE-P only; B2 must compose authentic exact own transport
transitions, never ignore filename prefixes. The baseline runner is registered once;
this documentation/runner update ran only shell syntax and diff checks.

B1 producer exact `ec06a26ef30eac1809b6f08f83b81d7687b920ab` is independently
APPROVED and integrated at `f0c6a847`; root verified exact files. Two actual review
probes blocked original `32379`: selected 8 versus actual-empty preferences, and partial
witness cleanup requiring EOF. Exact canonical actual scalar binding for all tabs,
recovery/preferences and whole-cursor abandonment with actual close repaired both;
genuine uncertainty remains retained. Both original probes now PASS, with no others
added. Worker final 221 plus existing PrivateState848 functional checks PASS; the latter
ran once for changed serialization, preserving legacy wire bytes (no memory/full run).
Original parent used/remaining bytes: minimum 10,973,585/5,803,631; two 8MiB tabs 11,155,999/5,621,217;
12 tabs+recovery+preferences 12,157,289/4,619,927.
This is producer-only evidence: no P/provider/UI/activation and no whole-B2 overlap proof.
The portable capture-plan runner is registered once; this update ran syntax/diff checks only.

Funding-input observations at `aaa852b` have independent spec/quality approval.
The estimator now uses three read-only methods bound to the actual measured inputs
and exact original Budget instead of reflecting capsule/evidence/item fields.
Focused provenance/state/coordinate checks and four real B1 estimator cases PASS
with the installed JDK17/Java11/64MiB heap. The test-only catalog reader now actually
closes before its reopen check. This is a prerequisite, not production funding,
heap proof or portable Writer lifecycle implementation. No broad suite/APK/CI ran.

The continuation environment lacked the prior worktree/private scratch. The exact
pushed `3649461` branch was recovered from Git; remote main remains `fdb7bd6` and
is already an ancestor. No original private files or untracked notes were changed.
The missing local native-footprint investigation was reconstructed narrowly:
current native membership needs actual per-member file-key anchors across callbacks;
original capture cardinality and the estimator's256KiB candidate do not bound later
native appends. The three scoped resource prerequisites implemented below preserve
ordinary append semantics, actual fileKey equality and exact reservations.
Whole-path consumer integration remains the next task.

Three resource prerequisites now have scoped independent spec/quality approval:

- `baf3348`: bounded control inspection enforces physical byte/entry caps before
  record allocation, including parent enrollment, stages and unknown entries.
  New30 checks and existing control14 cases/209 assertions PASS.
- `574b1f6`: sequential current-store validation reuses real snapshot/transaction/
  completion parsers and exact reservations with a prepaid512KiB reader workspace.
  Review caught the second-stat allocation race and second-workspace reentry;
  both reproduced RED and passed after repair. Focused79/74 checks, fresh8MiB
  snapshot and allocation-race child probes under8MiB heap, plus affected protection
  31 cases/272 assertions, completion7/86 and its restart/write-failure probes PASS.
- `32a00b4`: one reusable current-native identity table, separately prepaid on the
  supplied parent, preserves actual key equality and current-record validation.
  Owner lease expiry now clears retained evidence after a reviewed RED→GREEN fix;
  final189 focused checks PASS. Original capture count is not its capacity bound.

These APIs are not yet wired into portable publication or HistoryPins. The table's
supported-provider accounting convention is explicit; it is not a provider-independent
heap proof or a256KiB whole-native envelope. Over-capacity terminal evidence must
refuse before terminal writes and preserve retry authority; ordinary appends remain
allowed. All three runners are registered once in the required aggregate; only shell
syntax/diff checks ran for registration. No aggregate, APK or hosted run is claimed.
Next, bind actual-snapshot-sized whole-path funding to the original measured inputs
before P; consume prepaid allowances in the real writer/activation/terminal paths
without later direct double debits, and keep bounded inspection snapshots scoped.
Then finish authentic post-P baseline transition, outer-ID routing and actual-close/
verified-readback coordination. Actual portable Writer lifecycle is still unimplemented.

B2/C preimplementation audit found fixed Writer~7MiB plus Pins6MiB cannot fit
remaining headroom. Test-only funding estimator `ad9cab4c` has independent APPROVAL;
four real B1 fixture cases passed with JDK17/Java11 under `-Xmx64m`. Candidate extra
charges leave 2,836,815 bytes (minimum), 2,484,829 (two 8MiB tabs), and 547,121
(14 frames). Actual-parent one-byte-under refusal leaves its failed debit unchanged
and creates no P/artifacts. This is an estimator, not production funding or heap proof;
see [its limits and results](../verification/portable_funding/report.md). The current-native
cursor bound, snapshot non-escape, representation caps and prepaid consumption replacing
later direct debits remain unproved. This diagnostic is not a required production gate;
no aggregate run, APK or hosted check ran.
Internal dispatch parser leaf `9f58c949` has scoped independent **APPROVAL** and
is integrated on the active branch. Changed production files are HistoryControlStore,
HistoryPinRecord, PrivateOperationEvidence and PrivateOperationStore. Worker73 and
affected Record72/Evidence53/baseline59+large1 PASS. Partial P remains inert; readiness
is closed-only, with generic/cold refusal preserved. This proves structural retention
and owner joins, not an actual Writer first/second backup lifecycle, production funding
or activation. The required aggregate registers `portable_internal_dispatch/run.sh`
once; the diagnostic funding estimator remains unregistered. This docs/runner update
ran shell syntax and diff checks only; no tests were repeated.
Independent review found no blocker and duplicated no tests; root verified exact
integrated source/fixture matches. Next: finish conservative whole-path
funding before P, authentic post-P baseline WriterTransition, outer-ID routing and
actual-close versus verified-success semantics. Before large implementation,
check producer/consumer contracts briefly and run minimum affected tests. The retained-receipt
root is not implemented: verify shared canonical namespace ABSENCE first and fail closed
if present/unclassified. Actual adoption/route4 and repeated restore→export remain
required before feature completion. Cold completion implementation remains blocked on an authentic persisted
original profile. Contract `28147af8`
(plan blob `9a06e3e0c0a2bcb02c1cdc26565ac61024a236c5`) has independent spec/quality
APPROVAL and is integrated. V1 lacks the original nullable compatibility graph profile:
the contract requires refusing cold completion without writes; no completion API is
implemented. Eligible cancellation remains implemented.
A future portable witness must retain that exact profile. Source/runtime implementation
remains OPEN. Preserve global
pending/prune blocks, all bytes/credits, physical namespace eligibility, selected-own
completeness and immutable old-stage anchors. Runtime integration and lifecycle/error
closure remain before downstream features.
Prove minimum affected memory bounds before downstream storage consumers. Broad full-heap,
performance, regression, migration and device batteries remain for feature completion.
Continue M0–M7 directly; M1 is not complete. Source approvals remain exact-artifact scoped.
Physical Galaxy Z Fold7 / Android16 / One UI8.0, WebView/PDF/SAF/IME/TalkBack,
app-UID durability and in-place upgrade remain NOT RUN. At feature completion, the
owner will sign the unsigned APK with the original key and test the Fold7; no device
work is needed before that candidate is ready. Never create a replacement key or
publish private files. Keep PR #24 as one draft; do not fragment helper PRs.

## Merged PR #14 integration evidence — historical checkpoint

## Current integration checkpoint — shared authority and syntax, 2026-10-14

Main is `2ae2930`: PR #15 merged after required hosted run38635569038 passed on
`57124d7`. Its combined completion/editor/control/planning source passed the full
clean host suite and two unsigned builds (SHA-256
`2957c32061cb072fda8bb3bcbf640ba2768113f2cfef2cbb663c5fc77f16f216`).

Current local branch `codex/capacity-syntax-integration` reconciles existing draft
[PR #14](https://github.com/pemunson90-cmd/Markdown-Desk/pull/14). Both environments'
published histories are preserved; the alternate quota authority is replaced as
one coherent unit after independent production probes exposed policy uncertainty,
reentrancy, inode-durability and fresh-process fallback defects. No alternate
protocol is enabled in the app, and no existing native records are rewritten.
Superseded fixtures remain retrievable from their original commits; current
executable coverage lives in `verification/history_capacity`/`history_planner`.

Reviewed authority `1fa6d80` (test follow-up `7e474cb`) binds four existing sibling
roots, acknowledged generation/hash policy and a durable parent enrollment marker.
The marker, registration, policy and future maintenance share ONE1MiB/256-entry
control allowance. Every writer uses a typed role/dependency check and common
mutation lease before callbacks. Missing/uncertain authority blocks writes;
complete accounting charges optional damaged metadata without pretending it is
safe to prune. Only freshly validated zero-growth completion may proceed above
quota, including legitimate second handles. No completion proceeds under uncertain
policy. Controlled startup is read-only until explicit reconciliation.

Scoped worker evidence: codec14cases/209assertions; policy49, admission61,
integration42checks;10real SIGKILL boundaries and7additional fresh-process cases,
each reopened twice. Independent review approved exact source and reproduced
missing-root, second-handle, wrong-bundle and wrong-role repairs. First combined gate at `34ff18c` stopped at the archive runner dependency closure.
The sourcepath repair passed460+68 scoped archive assertions. Final coherent
gate **PASS** at `b67995ddad152c27bc4f095aa810c9f04bc3abcb`: required full host
suite once from clean committed source, two audited unsigned builds matching
SHA-256 `2a7e843fa00802789874a577083b18b17a336f0c28c9992cecbb2839cf632e69`.
No downloads, signatures, keys or device claims; package2.2.5/code18/min26/target35
and all25assets preserved. This evidence supersedes the failed integration
attempt without relabeling its failure.

Reviewed syntax fix `4c2fda6` preserves equal-priority search/foreign style
precedence, with baseline full fallback whenever a foreign CharacterStyle exists.
Worker39,137assertions +90ms scheduler and SDK37 compilation passed; independent
review56focused checks passed. The global span query costs O(spans) work/storage;
physical IME latency is NOT RUN. Historical pre-fix timings remain labeled.
Reviewed pressure mode `8baa6eb`: existing81+5 and new60+9checks pass; floor/selected
proof encoding remains compatible. Profile previews grant no execution/admission;
newest same-history anchors, labels, pins, stages and unresolved data stay protected.

Hosted run38643656351 on `016f7a6` failed: the syntax runner used `rg`, absent
on the independent runner, so stub discovery returned no files and compilation
stopped. No application source changed. PR14 returned to draft; portable `find`
with an explicit nonempty-stub guard passed all39,137syntax and scheduler checks
in a restricted PATH with `rg` absent. The first restricted-PATH probe lacked
`dirname` and failed its setup; the corrected probe above completed. Production
source/unsigned APK inputs are unchanged from `b67995d`.
Next: publish this scoped portability repair, then one new required hosted check
for changed inputs; merge only the verified exact head. No force-push. An inert bounded intent
codec is reviewed at `7c422a2`:73 native and103 bounded-heap checks plus planner
compatibility pass. It performs no filesystem operations and the control parser
still treats intent files as unknown until separately reviewed integration. No
deletion, durable live pins, maintenance projection/replay, runtime history,
export/import or M1 completion is claimed.
Continue those next slices with explicit authority and complete framing bounds.
Physical Fold7/Android16/OneUI8, SAF/IME/TalkBack, power-loss, canonical signing and
upgrade gates remain NOT RUN. Raw-provider hazard diagnostics are unchanged RED.

## Parallel-environment checkpoint — historical, before reconciliation

## Current checkpoint — quota/syntax/planner integration

- Verified main: `6afd3a1`, PR #13 merged after hosted run38630847829 PASS.
- Active `codex/m1-capacity-syntax`, [draft PR #14](https://github.com/pemunson90-cmd/Markdown-Desk/pull/14).
  Committed syntax `c2cd90c`, shared quota `1171338`, pure planner `6d55d9a`.
- Targeted checks PASS: syntax39,081 + production watcher/scheduler; shared quota
  18cases/91checks +17actual host SIGKILL boundaries; planner427regular +19
  constrained-memory assertions. Reviews found and repaired initial-registration
  parent-sync, fresh-stage retry, graph-root binding and planner batching/ordering
  gaps. Host Editable relocation is a model, not Android IME proof.
- First combined gate at `6d55d9a` failed in the statistics-only host adapter:
  the extracted watcher gained syntax coordination dependencies. The adapter
  boundary was updated without changing product behavior; targeted status
  checks PASS. Rerun at `7bc928dc4db28cefd80bc1437204a6633417d667` **PASS**:
  full required host suite once from clean committed source, two audited unsigned
  builds with matching SHA-256 `b1176663dbe9cb44e54548d551cf1bb904625702368e07f37d88d76af1c34557`.
  All25assets and package2.2.5/code18/min26/target35 preserved. Hosted review-ready
  validation is next; no hardware/signing/upgrade acceptance is implied.
  Earlier `90f258e` full evidence remains attributed to that exact revision.
- Shared capacity is opt-in only; legacy constructor paths remain unchanged.
  Pure planner performs no deletion. No runtime quota/history control acceptance,
  private-backup adoption or M1 completion is claimed.
- Exact next action: complete the current combined gate, record its actual result,
  update PR #14 and run hosted validation when ready. Then implement explicit
  maintenance registration/accounting from [ADR-003](decisions/ADR-003-retention-maintenance.md),
  durable pins and journaled projection/replay before wiring runtime pruning.
  Explicit v2 maintenance enrollment/accounting is implemented locally and under
  independent review:12cases/101checks +11host SIGKILL boundaries reported by
  the implementer. Root integration is pending; it is outside `7bc928d` proof.
- Four initial merged remote branches and merged retention branch were cleaned
  safely; current draft branch/main and all committed history are preserved.
  Main remains unprotected; connector administration is unavailable (403).
- No connected device or signing inputs: Android/SAF/IME/Fold/TalkBack/canonical
  signed upgrade remain NOT RUN. Use the single candidate matrix already prepared.


## Independent-environment snapshot — historical evidence, 2026-10-14

- Fresh independent checkout verified remote main at `3652218` (PR #12 merged),
  and preserved draft PR #13 at `0918e76` on `codex/m1-safe-retention`.
  The earlier audited `c42de9d` remains its ancestor. One local worktree;
  no pre-existing Markdown Desk checkout or uncommitted work was available
  in this attached environment. Other agents/environments may hold unpublished
  work; this observation does not establish their status or why an agent stopped.
- This separately observed snapshot predates the combined reconciliation below. That snapshot resumed PR #13. Existing graph tests rerun with JDK17:
  `bash verification/history_retention/run.sh` PASS, 81 functional and
  6 constrained-memory checks. Historical full-suite/build evidence below is
  attributed to its original commits, not this fresh run.
- Completion reservation/staging safety (`809d41b`) and incremental logical-line
  indexing (`90f258e`) are implemented and independently reviewed. Shared quota/policy admission,
  safe planner/journal/executor and runtime controls remain incomplete.
- Fresh integration at `90f258ee05ddaadf58739327f4d4399ed0153cc4`:
  `python3 verification/check_clean_rebuild.py` **PASS**, required full host
  suite once from clean committed source and two audited unsigned builds with
  matching SHA-256 `e45934a0a12a5f1787fdd66ffd31b64bb6595b2dce32885c507980f9767bba28`.
  Package 2.2.5/code18, min26/target35 and all25assets preserved; no signing.
- New completion coverage: 7cases/86assertions and six actual retry SIGKILL
  boundaries, each inspected/retried in fresh JVMs; immutable v1 bytes retained.
  One exact transaction-bound stage spends its byte/slot reservation once.
  Partial/changed/foreign/duplicate stages fail closed; no automatic removal,
  reduced-quota exemption, dynamic quota or shared admission claimed yet.
- New line indexing: 46,564assertions/3,000seeded edits and matched benchmark
  of exact extracted callbacks on existing 10k/100k/500k-word fixtures.
  Valid insertion reads inserted chars only; deletion reads none. Remaining
  offset shifts cost O(following lines); full resets scan once.
  See `verification/line_index/README.md`; Android typing/IME latency NOT RUN.
- Exact next work: finish opt-in persisted four-root policy/shared admission
  and tested reduced-quota completion, then pure safe planner and journaled
  executor from ADR-002. Independent affected-line syntax implementation is
  underway with conservative full fallback for multiline marker documents.
  Neither ongoing slice is included in the `90f258e` integration evidence.
- GitHub branch API reports main `protected=false`; rulesets list is empty.
  Administration mutation is unavailable through the installed GitHub connector;
  the shell GitHub API call is denied by the current proxy. No protection claim.
  Retain review-ready hosted required checks and do not merge without them.
- Four fully merged historical remote branches were removed after verifying
  their tips are reachable from main: baseline audit, M0 safety, M0 performance
  and M1 foundation. Their commits remain in main. Active retention branch
  and all work here are preserved; no worktree was removed.
- Targeted tracked-content scan: 282 files, zero private-key/GitHub-token/AWS-key
  pattern matches; no tracked key/archive/APK names. This is a scoped scan,
  not proof that every historical artifact is public-safe.
- Installed platform-tools reports zero connected devices. Physical Android,
  SAF, IME, Fold, TalkBack and canonical signed upgrade remain **NOT RUN**.

The independent `90f258e` build above does not cover the latest combined source.
Reconciliation retains the more thoroughly reviewed `b511fee` completion path
(inode checks, read-only stage retry and same-target recursion guard), plus all
independent completion fixtures and line-index work. Root review reproduced a
bounded-heap regressions in the new line index; the reviewed correction above
requires fresh combined validation before the follow-up becomes review-ready. No work or
historical evidence is discarded; no force-push is used.

## M0.1 / issue #1 — imported and reproducible; baseline signed artifact independently verified; merged/tagged

As of **2026-10-14**, baseline merged at `d1288a80d4fc174c2b2a62805642218f5fa2d516`, source tag `v2.2.5-baseline`; completed
[PR #9](https://github.com/pemunson90-cmd/Markdown-Desk/pull/9):

- Exact 2.2.5 archive received; SHA-256
  `f57fe34ac489eb1f651fd1f6fe840ffddf189f4322c4bdc32b3ad285c187e6ef` verified.
- 89 files audited/imported; all runtime Java/shared bridge/resources/manifest
  and 25 splash images byte-identical. Four documented build/test scaffold edits;
  their archive originals retained. No feature/runtime/version changes.
- **Artwork publication separately cleared by owner** for all 25 bundled images.
  Private signing files, archive ZIP, generated binaries and caches excluded.
- PM correction applied: SDK37.0/build-tools36.0.0 are compatible and tested;
  36.1.0 was historical release tooling, not a hard minimum. Reused installed
  JDK17 with its bin directory on PATH; no SDK/JDK/key replacement.
- Genuinely unsigned build/audit PASS, with all signing inputs removed.
- Host suite PASS: supplied guards/syntax, 27 renderer cases, 49 SplashStore
  assertions, 15 independent functional fixtures. Original stale SplashStore
  expectation failure and missing host JSON dependency are documented/resolved
  in test scaffolding; production source unchanged.
- Two fresh committed-source builds produced byte-identical unsigned APKs:
  `bd9f99cb99acfe8b584f47c2f6edb1418c7deb70148d5f5087c3f6bb3f638d57`.
  Compiled package `com.example.markdowndesk`, 2.2.5/code18, min26/target35.
- Focus/View and other existing modes retained exactly; runtime phone/Fold and
  provider/upgrade acceptance **NOT RUN**. No connected device.
- **Independent PM signed-baseline verification completed** using the archived original 2.2.5 APK (no signing secrets): APK SHA-256 `ec5e790343b45a56f2580f3f5ec97eae3fb3096e10e012e58eb76bc89e683601`, canonical signer SHA-256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`, v2/v3 verified, manifest 2.2.5/code18, ZIP/alignment and 25 splashes passed. Evidence is attributed to the PM's separate check in [PR #9](https://github.com/pemunson90-cmd/Markdown-Desk/pull/9#issuecomment-6164996417), **not** to the Codex host run.
- PM approved merging the **unchanged-source baseline** after final document reconciliation; physical Fold/SAF/IME/TalkBack/upgrade acceptance remains **NOT RUN** and must gate future production releases. PR #9 is merged and source-only tag `v2.2.5-baseline` is pushed; issue #1 is closed. No new signed release or hosted CI run.

See [audit/evidence](M0_1_AUDIT.md) and [reproducible commands](BUILD.md).

## Active campaign — M1 backend foundation; runtime history still pending

Protection [PR #10](https://github.com/pemunson90-cmd/Markdown-Desk/pull/10) merged
at `eea037763259e37537b24f402142e860b095b768`. Active local branch:
`codex/maintenance-integration`, collecting the independently owned recovery projection. PRs #14/#15 are merged; current evidence is recorded above. Draft skipped CI is not passing hosted evidence. The separately reviewed issue #3/#4 slice
[PR #11](https://github.com/pemunson90-cmd/Markdown-Desk/pull/11) merged at
`c42de9d84c223feba773c849da7c7a21cac724d0` after required CI run38595113245 passed.
The owner renewed the instruction to finish the entire app continuously; do not
hand back after a successful task or PR checkpoint. Remote PM changes were
fast-forwarded through `d8d595a`; the updated AGENTS/CI policy and
[PR #10 engineering instruction](https://github.com/pemunson90-cmd/Markdown-Desk/pull/10#issuecomment-6171465179) govern this checkpoint.

Implemented locally, independently reviewed:

- Immutable private snapshot/preparation/completion records with integrity hashes,
  exact bytes, file sync, atomic same-directory commit and directory sync. Quota
  is64MiB, fails closed, counts staged records and reserves completion bytes and
  entry slots. No automatic deletion or retention/history acceptance claimed.
- Actual save coordinator protects the captured edit before reading the source,
  protects the observed original, binds conflict consent to its hash and captured
  revision, rechecks both source and stored protection, writes once and verifies.
  Failed/uncertain writes retain both versions; no automatic rollback/retry.
- Ordinary saves, pending Save As and protected-copy restoration use the
  coordinator. Identity changes only after verified success. Newer edits remain
  dirty. Workspace exposes protected versions and restore to a new copy; same
  source URI is rejected and existing tabs/records are retained.
- Legacy draft is protected independently of session parsing/full12-tab state.
  If protection fails, legacy replacement/clear fails closed. Storage repair can
  retry without a restart. Unmatched recovery is explicitly surfaced.
- Streaming envelopes/lazy disk-backed snapshot handles remove the reviewed
  memory failure. Three paired8MiB saves (~48MiB persisted) and six complete
  restores pass at128MiB host heap with32MiB retained headroom. This is a host
  regression criterion, not an Android memory/latency budget.
- Strict captured UTF8 rejects unmatched surrogate text before queuing a save;
  valid Unicode/BOM/CRLF retains exact bytes. Legacy encode API remains intact.
- Previously reviewed preflight/metadata, nested lists, wide tables, palette
  foregrounds, independent recovery loading and status snapshots remain intact.
  Tabs expose full accessible names/selection; gallery removal is named; the
  existing Split divider supports accessible/keyboard adjustment with unchanged
  drag bounds. Nine palettes and25owner-cleared assets remain unchanged.

Merged foundation evidence (production save/UI paths unchanged by the new backends):

- Full host suite PASS.42DocumentIO cases;568session/recovery assertions;
  467renderer/palette +12list +14,020divider assertions;126mode +43labels
  +4,963Appearance assertions;57status +93latest-statistics assertions;
  42Split UI +8ratio assertions;10inventory checker tests. New issue #3/#4
  scoped suites PASS. Combined required host suite PASS from clean committed
  source at `727112133d556f44c68cdb11b95afade8a4880c6`.
- Production store31cases/272assertions plus3real OS snapshot write-limit assertions and11zero-prefix/11partial-prefix completion assertions;
  Android adapter double27assertions. Coordinator35cases/157assertions.
- Activity save lifecycle34cases/233assertions including actual strict codec (previously six Save As cases required the separate `--safety` invocation; the current runner now includes all34);
  recovery13cases/179assertions plus7actual UI callback/dialog assertions.
- Production SIGKILL/restart suite PASS at16 actual store/coordinator boundaries:
  snapshot/preparation/completion file-sync, move and directory-sync; provider
  truncation/partial/full write and read-back. A fresh JVM validates exact original
  and attempted bytes, transaction state and retained staging. This proves host
  process-interruption behavior, not Android app-UID or sudden-power-loss durability.
- The raw-provider diagnostic remains genuinely RED on two destructive-write
  assertions, unchanged. The aggregate safety command mixes contracts and is
  not the selected product recoverability gate. Required production recoverability
  tests must be GREEN; no universal atomic-URI guarantee is claimed.
- Historical independent CI run38581669811 passed preservation and failed the
  old mixed safety step on those primitive hazards. The PM workflow now runs
  required production host checks only when review-ready; skipped draft jobs
  are not evidence of a passing hosted check. Required independent hosted
  validation **PASS** at `3cc9542`: run38593771364/job115955100750, including
  the16restart cases. PR #10 merged only after that required check succeeded.
- Unsigned JDK17/SDK37.0/build-tools36.0.0 build PASS; package2.2.5/code18,
  min26/target35 and25asset bytes preserved. Two fresh committed-source builds at `c326b61` PASS and match SHA-256
  `059587cbf2feb5df1b3a4183a20b7ae5a993aaabbc8d38b56a9d489f06e28b23`. No credentials, replacement key or signed release produced.

New bounded repairs, independently reviewed with no blocking findings:

- Statistics updates no longer copy/scan the full text per keystroke. Immediate
  characters/title/Save remain current; exact locale/Unicode word and LF line
  counts run after a250ms debounce on a dedicated bounded worker. Identity,
  revision, locale and shutdown reject stale results. Pending counts are explicit.
- Write/Read/Split selection and nine Appearance themes/three boolean options
  expose selected/checkable/checked state. Refresh retains existing controls;
  no Save action, preference, layout or protection behavior changed.
- Matched10k/100k/500k host immediate-status medians changed from
  5.752/7.893/27.252ms to0.167/0.110/0.092ms; full exact scans remain deferred.
  These are statistics-path host measurements, not Android typing latency.
  See [measurement](benchmarks/M0_3_DEBOUNCED_STATISTICS.md).

Current backend implementation, independently reviewed:

- Immutable checkpoint names over existing validated snapshots; strict bounded
  checksummed records and fail-closed complete pin enumeration. No source rewrite.
  15cases/87assertions;6actual SIGKILL boundaries, each reopened twice.
- Exact URI/unsaved-tab identities, immutable Save As provenance and verified
  group ownership/sequence index. Missing optional records still expose actual
  completions; pending/orphan versions remain protected. 19cases/275assertions;
  two independent clean reopens and12SIGKILL boundaries, each reopened twice.
  Review fixed forged TAB provenance and false-green fault/quota fixtures.
- Read-only exact bounded diff preserving BOM/CRLF/Unicode; explicit incomplete
  summaries at byte/line/work/output limits. 10cases/522assertions plus six paired
  8MiB comparisons at128MiB heap with32MiB retained headroom.
- Streaming private archive framing/integrity codec:459assertions and constrained
  memory target,168,427,380 generated payload bytes checked, both1GiB bounds.
  Codec callbacks are provisional; typed import validation, adoption, export pins
  and provider readback receipts are not implemented. Review fixed an index-limit
  false-green fixture and a reproduced zero-read hang.

All four backends are registered in the required host runner. They are not wired
into Activity actions and do not implement pruning, combined quota, private-state
import/adoption or the full M1 user flow. Retained stages/corruption fail visibly;
no implicit cleanup or partial pin set. Calls must remain off the UI thread;
shared graph locking and operation-scoped source validation are implemented in the next reviewed slice below. Shared admission and runtime integration remain pending.

Current retention slice, independently reviewed at `f244e0e` (Task3A1):

- One canonical-private-parent monitor coordinates store/catalog/identity/index;
  mixed/aliased roots are rejected. Existing constructor quotas, native record
  formats and write admission remain unchanged.
- Read-only inventory and complete-envelope hashes bind exact metadata, edges,
  staged/corrupt inputs, byte/slot completion reservations and policy choices.
  All snapshots remain protected; there is no pruning or shared-admission API.
- A bounded operation-scoped validated-source cache avoids repeated checkpoint
  payload hashing; generation checks before and after callbacks reject mutation.
  Independent review caught and fixed a stale proof and an API33-only sink.
- Scoped tests PASS:94 functional +6 constrained-memory checks (including completion staging); affected native
  store/coordinator/restart/catalog/index/diff/recovery paths PASS. All34 lifecycle
  cases/233assertions PASS locally after required-runner registration. Clean committed-source host suite PASS at `904d7bfb9dcf8a9721930aae745c79df4eef2814`; two genuinely unsigned builds PASS and match SHA-256 `4caf117b90159dbbbb9ba1585551769d0a95c77d42f44cd29366195edecc6856`. No signatures/credentials or device acceptance.

Additional local framing prerequisite: explicit INDEX wire ID9 preserves IDs1–8
and framing version;68 assertions pass using a byte-identical real production
ownership envelope plus category/size/integrity faults. This does not implement
full history export/import. Completion-stage tests observed RED on the old anonymous-stage implementation,
then GREEN against the reviewed prerequisite `b511fee` (integrated `a53127e`).
One deterministic stage conserves its exact completion byte/slot reservation;
full matching stages may retry without rewriting, partial/changed stages cannot.
Current constructor quotas and v1 envelope bytes remain unchanged; no reduced-
quota exemption. Review reproduced and fixed fresh-stage inode substitution before
move; same-target recursion and generation invalidation are checked. Original16
restart boundaries plus3full-stage retry SIGKILL boundaries (each reopened twice)
are now required. Independent pure planner review approved `6f02af3` (integrated
`11fdf74`):81 functional +5 memory checks, bounded exact proofs with no partial
plans or deletion. Current count remains a protected floor; future pressure mode
requires its own tests. Read-only control codec `786745c` (integrated `85d2bc2`)
passes14cases/209assertions at64MiB; it publishes/adopts no policy. Combined clean committed-source verification PASS at `a18e148912eb2b2c62876886a922999758fbba40`: full required host suite once (including all19restart boundaries), two audited unsigned builds matching SHA-256 `52996f927fc40f73a5b70cb7e09011ddd6aa477be81fe65e1b949b41537f99d0`. A subsequent runner-only log-transport fix passed all3/11/11physical assertions with redirected output; production bytecode is unchanged. This historical local evidence predates the independent PR13 merge and current reconciliation above.

Integration evidence and next executable work:

1. Merged UI source `727112133d556f44c68cdb11b95afade8a4880c6` passed required
   clean-checkout host tests and two unsigned builds, matching SHA-256
   `d07d660df8438f5f0ff16a6b5b2439c12bffe7bb7b0986b3e7e79fee968ecee9`.
   Combined backend verification **PASS** at
   `e612e3b0e875ce07fbfe1f69980458f2c7ebdaa6`: required host suite once from
   clean source; two unsigned builds match SHA-256
   `f2ba08a92d781f9e6dd8f1aaa1e3efd7f2625910ed64cafdbde6cdbfb311ab1f`.
   Independent integration review approved; deliberate hosted run38610866540 PASS. [PR #12](https://github.com/pemunson90-cmd/Markdown-Desk/pull/12) merged at `3652218c1a764dc8393280e53b1202091224f92d`.
2. Continue the [history/retention plan](superpowers/plans/2026-10-14-history-retention.md):
   finish intent-aware recovery projection, then durable pins and journaled maintenance before any destructive executor. Next local branch `codex/shared-history-capacity` is implementing that opt-in backend; partial policy tests are not all-writer acceptance. Inert M2 range-API preparation is separately reviewed on `codex/m2-syntax-range`; it has no runtime caller or physical acceptance. Crash-safe deletion, verified export, strict import and
   history UI remain next tasks. [ADR-002](decisions/ADR-002-retention-journal.md)
   defines the reviewed retention protocol. Its first implementation is deliberately
   narrowed to shared monitoring, validated inventory/read-only graph and bounded
   per-operation validation cache; dynamic quota, reservation-bound staging and
   deletion follow separately. No source reconstruction or coordinator redesign.
3. Reconcile local M0 prerequisites before optional runtime enablement. Keep
   physical milestone acceptance and user-facing release gates open. Preserve
   canonical signing, migration/rollback and actual Fold/SAF/IME/TalkBack/upgrade
   requirements; no replacement key or credentials in Git/CI.

Owner-reported target: **Samsung Galaxy Z Fold7, Android16, One UI8.0**.
The owner reported installed app version “2.25”; the exact installed package
version/code remains unverified (protected source is2.2.5/code18). No ADB access
or owner-run test results supplied yet; Codex has no connected device.
See [device checklist](M0_DEVICE_ACCEPTANCE.md) and `verification/device_context.py`.
Matched synthetic fixtures remain local under ignored
`verification/build/Galaxy-Z-Fold7-baseline-fixtures.zip`. Physical Fold/SAF/IME/
TalkBack/upgrade and Android filesystem durability remain distinct NOT RUN gates.

Named history/diff/configurable retention, private backup/restore and M2–M7
features are not complete. Candidate phone/Fold/SAF/TalkBack/upgrade/process-death
and actual Android durability checks remain NOT RUN. Canonical signing inputs
will be required only for authorized release preparation, never in Git/logs/CI.

Exact2.2.5 source/provenance and owner-approved artwork are already accessible
in Git. The local Library directory contains0files; older version/SDK/private
archives were unavailable there and not published. Installed SDK remains local
tooling. Preserve private recovery/signing material in any later provenance work.
