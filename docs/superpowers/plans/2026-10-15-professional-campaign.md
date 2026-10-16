# Markdown Desk Professional Campaign Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, or superpowers:subagent-driven-development when execution capacity is available. Execute the ready packets below; detailed future packets are written only when their dependencies are accepted.

**Goal:** Complete the retained M0–M7 workflows and acceptance gates without losing the protected 2.2.5 document behavior or release identity.

**Architecture:** Extend the existing Java/native-View/WebView app through reviewed production helpers and narrow Activity integrations. Finish the current capture/codec/outline boundary before enabling backup; then deliver usable vertical slices, with one integration owner and explicit shared-file ownership.

**Tech stack:** JDK17.0.16+8, javac --release 11, installed android-37.0 revision2, build-tools36.0.0; existing aapt2/D8/zipalign build and separately controlled signing.

**Spec:** [ROADMAP](../../ROADMAP.md), [AGENTS](../../../AGENTS.md), [release gates](../../RELEASE_GATES.md), [CI policy](../../CI_AND_WORKFLOW_POLICY.md), ADR-001 through ADR-005 and the owner's attached whole-roadmap orchestration instructions received2026-10-15. This plan organizes existing scope; it does not replace those authorities.

## Controlling owner scope update — 2026-10-15

[Owner decision6204593523](https://github.com/pemunson90-cmd/Markdown-Desk/pull/24#issuecomment-6204593523)
supersedes portable-first sequencing below. Current-phone local document safety,
revision history, draft/session recovery and dependency-ready M2–M7 are the active
campaign. Cross-installation private-state export/import is deferred as optional
Export App Data / Import App Data. Preserve all existing transport code/contracts;
stop expanding its funding/provenance machinery solely for that future feature.
ADR-005 applies in full when transport resumes, not as a gate for ordinary editing.
All unrelated feature, local migration, signing and final Fold acceptance gates stay.

Immediate execution: verify the real local save/history/new-copy recovery journey,
show the private-history storage limitation, then add explicit native Undo/Redo and
continue ready workspace/reader/performance lanes with exclusive file ownership.
Existing portable packets/checkpoints below are retained evidence, not current work.

## Global constraints

- Protected baseline: 2.2.5/versionCode18, com.example.markdowndesk, minSdk26/targetSdk35. SDK37 compilation does not change API policy.
- Preserve Write/Read/Split, Focus/View, eye/lock,12tabs, all nine themes/25 cleared assets, external-change protection, bridge handoffs and offline canonical Markdown.
- No replacement signer, Kotlin/Compose/Gradle/Room migration, new editor framework, broad storage permission or new paid service.
- Deferred roadmap scope stays deferred. DOCX export does not imply lossless Word round-trip.
- Required application recoverability must pass; raw-provider truncation probes stay separately runnable and genuinely RED. Outline production regressions must become GREEN.
- One primary shared quota and one1MiB/256-entry control allowance. No deletion, imported authority or successful-backup claim from framing alone.
- MainActivity, sensitive store authority, shared bridge/build contracts, aggregate runners and STATUS each have one explicit writer at a time.
- Every persisted workspace/reader/editor feature needs backward-compatible local storage/upgrade coverage before enablement. Future optional private-state export/import must include those features before that export capability is enabled; it does not block current-phone feature work.
- Build.sh deletes its checkout's build directory. Never run concurrent builds in one checkout, even with different output names.

## Initial reconciliation checkpoint — historical

This section records the initial reconciliation snapshot; later current-state updates below and in STATUS supersede it. Planning began at main/origin main `b5796ba9061c365538d3192acdea8f69bb204029` (merged PR22).
Required hosted run38664300293/job116192539275 passed on exact head7a13ac5.
Local committed clean gate05e74ca ran host checks once and two unsigned builds,
both SHA256 `7c52d713859a0824e7034667362735adf3f1b8f9596b05d8386d46e9b5719914`.
The repaired missing-rg launcher failure38663648484 is historical, not an open product defect.

Published continuation is `codex/private-backup-integration` at2bdf43f before this plan.
It contains reviewed pure codec source/contractsf357fe1, approval record4ed9fbb and its required runner.
Codec worker evidence:4,732 functional+7 memory checks; independent16 probes. Combined gate pending.
Outline source93a5cc5 is committed in its isolated worktree; Activity blob13c27c32b0b387e4a45dccf80b5ee06add06f531 was independently reviewed with20 probes and no blocker. Worker reports32cases/74assertions and affected fixtures/whole-Java compile passing. Exact integration carry-forward still requires checking the final artifact against that reviewed source.
Operation accounting checkpointa92a999 is **not approved**; dirty repairs remain in its worktree. Capture producer/activation is not implemented. See blocking probes below.
Open issues2–8 remain the scope references; issue1 and source-baseline PR9 are complete. PR search found no open PR at reconciliation. Local archive/source branches and old untracked historical plan copies are preserved, not assumed active or pushed wholesale.

Worker execution stopped at an account usage limit during initial reconciliation; bounded worker execution became available again during the subsequent continuation. Do not change spending/account limits or pretend workers continue. Lead takes ready work inline; new independent review remains unavailable until execution capacity returns. Prior exact-source review is retained; new risky changes cannot be merged on self-report. This is a capability constraint, not a physical-device gate or permission to waive review.

Status meanings: **planned** = retained scope; **implemented** = source exists; **wired** = actual app entry point; **host** = scoped host evidence; **Android/device/release** = separate actual acceptance. No current feature is claimed release-accepted. Device/API/WebView behavior remains NOT RUN unless a later evidence record says otherwise.

Current checkpoint: main/origin main remains `fdb7bd6` (PR23 merged). Active
`codex/operation-data-capture` has draft PR24. Reviewed A1 portable wire codecs
are integrated at `88fb149`; earlier writer/copy approvals remain scoped. The
full clean gate at `8dcbea5` passed before subsequent C2 source additions; no newer
full suite/build/hosted run is claimed. STATUS records exact gate evidence and
hashes. Earlier failed printing fixture attempt46d8bd8 and correction4321957 remain
historical evidence, not current gate state.

Native fingerprint `a4cb514`, source ownership/budget `e331477`, private-frame
preflight `c3177b8`/integration `2f1d14b`, capture plan `a89b284`, writer `92e41bb`
and copy helper `b4bd0c9` retain approvals scoped to those exact artifacts.
Completion/handoff commit `1fe3c464e3e3f7ceb48dcceba34989dc9b0dc695` is
independently approved after 4 groups. Worker results pass: 87 publication assertions,
7 copy cases and 11 completion cases, including fresh-child JVM checks. It implements
exact BEGIN204/LINK173/CLOSE credit126, typed-stage allowance, preadmitted retained
maps and one-use old-owner-claim handoff. Earlier writer `92e41bb`, copy helper
`b4bd0c9` and private sink `d71dae2` approvals remain point-in-time and scoped to
those artifacts; none approves the complete current source.

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
[ADR-006](../../decisions/ADR-006-terminal-cancelled-capture-admission.md) is ACCEPTED
engineering direction after independent review of exact `e8cb1532d514e1d2e0c46d93c8d5f20e52c44820`,
ADR blob `b54b1629f72f213fb9bf735a6c3a76266ca70cdb`. The first typed historical-cancellation
capture-admission slice is implemented at `af9eebf97174e02c4ab64794a3e80e606179f443`;
scoped independent source review is **APPROVED** after 4 actual probe groups:
classification, immutable old-stage anchor refresh, funded capture→activate/read/drain/CLOSE
preserving old credits, and planner/intent/stale maintenance/retirement refusals with
an ordinary-save positive control. Worker evidence: 12 focused groups
and 871 legacy HistoryManifestCheck checks PASS, recorded in
[terminal admission evidence](../../../verification/terminal_cancelled_admission/README.md).
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
Accepted portable contract `d43dbb17` now has A1 archive3/schema2 wire codecs:
exact `f98ca2913ebc40cbcef0520fba7c91a0a3f4ffe2` has scoped independent APPROVAL,
integrated at `88fb149`. Duplicate owner/stage aliases were corrected in reader and
writer; 8 independent budget/control checks passed. [STATUS](../../STATUS.md) records
the affected worker evidence and prior proofs. The required aggregate registers the
portable wire and operation-record runners once each; only shell syntax was checked for this update, with no test rerun.
A2 exact `2736de7c6105532b62672b5b250d6bfa17a6c72d` has independent spec/quality
APPROVAL and is integrated at `08abce8d`; RecordCheck72 passed Java11/`-Xmx32m`.
Review inspected the five-file diff with no blocker or further probe warranted;
STATUS retains exact affected evidence. Runtime backup completion,
semantic routing/adoption and coordinated export/import remain OPEN.

B0 exact `afc57ec95d0f971325c29b2e9cbfc1ab9233e1f5` has independent spec/quality
APPROVAL and is integrated at `d1303eb`; five new witness groups passed and plan29
ran once for canonical serializer compatibility. STATUS records repaired defects and
exact evidence. The witness runner is registered once; only shell syntax/diff checks
ran for this update. Conservative workspace bounds do not prove whole-coordinator16MiB fit.
Portable baseline exact `cce8a6ee34785b889ff58aa7ea142c8640711347` has independent
spec/quality APPROVAL, integrated at `fb22bff4`; focused59 plus large1 passed without
duplicate reviewer tests. STATUS records scope and bounds. Original provenance,
physical/missing credits and 72 policy rows come through one owned cursor; canonical
hashes/generic admission are unchanged. Read-only `sourceOnlyMissingRoot` inventory
preserves funded credits but B1 must refuse it; no write authority. Strict original
C0/L0/tree checks are PRE-P only; B2 requires authentic exact own transport transitions,
never prefix ignores. The baseline runner is registered once, with syntax/diff checks only.
B1 exact `ec06a26ef30eac1809b6f08f83b81d7687b920ab` is independently APPROVED,
integrated at `f0c6a847`. Actual scalar-binding and partial-witness-close review probes
were repaired and now pass; STATUS records scoped final221/PrivateState848 evidence
and original-parent headroom. Producer only: no P/provider/UI/activation or whole-B2 proof.
The portable capture-plan runner is registered once with syntax/diff checks only.
Next: B2/C contract audit must resolve fixed Writer~7MiB plus Pins6MiB exceeding
headroom through conservative actual-snapshot-sized whole-path funding before P.
Include authentic post-P WriterTransition, domain3 dispatch, outer-ID routing and
actual-close versus verified-success semantics. Test-only estimator `ad9cab4c` is
independently approved: four real B1 cases PASS; tightest candidate residual547,121.
This is not production allocation/heap proof. Current-native cursor bounds, snapshot
non-escape, representation caps and prepaid consumption remain unresolved. Next bounded
leaf `9f58c949` now implements internal v2/domain3 parsing/owner association and is
independently APPROVED and integrated. Worker73 plus affected Record72/Evidence53/baseline59+large1
passed. Partial P is inert; readiness is closed-only and generic/cold refusal remains.
Actual Writer first/second lifecycle, production funding and activation are unproved.
The dispatch runner is registered once; the funding estimator remains diagnostic only.
This update ran syntax/diff checks without repeating tests. Complete independent review,
then prove conservative whole-path funding before P and the remaining authentic joins.
Check producer/consumer contracts before large implementation; run minimum affected tests. Retained-root
absence only remains; adoption/route4/repeated restore→export are still required.

Cold completion contract `28147af8` (plan blob
`9a06e3e0c0a2bcb02c1cdc26565ac61024a236c5`) has independent spec/quality APPROVAL
and is integrated. V1 lacks the original nullable compatibility graph profile; the
contract requires refusing cold completion without writes. No completion API is
implemented; eligible cancellation remains implemented.
Future portable witnesses must retain that exact profile. Source/runtime implementation
remains OPEN. During development use minimum affected
tests; broad regression/performance/migration batteries belong at the feature-complete
candidate. The owner signs with the original key and tests Fold7 then; mandatory
integration/release gates remain required.
Independent substantive campaign plan review is approved at
`73372f50db109c378235406a75ca7abbf4cd85a6`, reviewing plan blob
`029269ba83b6897d4e0148c797db63311ddddda2` against ROADMAP and RELEASE_GATES; it
found no cycles or missing clauses. This approval is for the plan, not source. M1 is
not complete. Physical acceptance remains for feature completion. STATUS records the
latest detailed checkpoint.
## Whole-roadmap coverage and dependency map

Paths below are relative to `apps/MarkdownDesk/src/com/example/markdowndesk/` unless prefixed otherwise. Existing issue IDs are reused; suffixes distinguish obligations, not new GitHub issues.

| Obligation | Current source / real entry point | Evidence and remaining deliverable | Depends on |
| --- | --- | --- | --- |
| M0/#1 baseline/build/provenance | build.sh; provenance2.2.5; immutable baseline tag | Exact source/assets imported; unsigned reproducibility verified. Keep tool hashes/privacy checks; older private Library inputs unavailable. | Preserved prerequisite |
| M0/#2 document lifecycle/modes | MainActivity open/save/recovery/mode paths; DocumentIO,SaveCoordinator,ProtectionStore,SessionStore | Wired+host save/session/recovery/mode fixtures; real process-death/provider/IME round-trip remains device gate. Preserve BOM/CRLF/Unicode/locks/12tabs. | Every candidate |
| M0/#3 representative performance | KineticEditText,SearchEngine,LatestStatistics,LatestPreview; benchmarks and line/syntax/preview fixtures | Matched10k/100k/500k host baselines and bounded-work improvements exist. Complete cold/open/typing/paste/search/switch/render/save/recovery, LF-dense/long-line/boundary allocation/queue measurements; device latency/GC unrun. | Host foundation; device for device claims |
| M0/#4 accessibility/layout | UiTheme; Appearance/toolbar/tab/Split construction | Numeric contrast, semantic labels, live Appearance and keyboard divider host-tested. Remaining measured touch targets, font scaling, disabled/selected states, screenshot/node/TalkBack/Fold verification. | Covered UI before changes |
| M0 architectural seams | HistoryController,DocumentPrint,LatestPreview/Statistics; Activity remains coordinator | Existing bounded extractions tested. Extract another seam only for a demonstrated next integration/test conflict; no wholesale Activity rewrite. | Named consumer/test |
| M1/#5 protected save/conflict recovery | saveInternal,showProtectedSaves,launchProtectedCopy; SaveCoordinator/ProtectionStore | Wired+required host recoverability; original+attempted edit before provider open and explicit new-copy restore. Finish integrated lost-work/device durability rehearsal. | ADR001; always required |
| M1/#5 real history/checkpoints/diff | showDocumentHistory; HistoryController,Catalog,Identity,Index,Diff | Wired+host named versions/paged history/comparison/new-copy restore. Verify cross-feature newer-edit/SaveAs/reopen journey. Evaluate issue5 content-hash reuse without conflating roles/identities or authorizing deletion by equality. | Protected save |
| M1/#5 configurable policy/retention | HistoryPolicy,Capacity,Pruner,MaintenanceRecovery/Executor | Backend host-tested; SELECTED execution only, no automatic runtime retention. Finish reviewed startup replay, intelligible policy controls and configurable automatic scheduling through existing safety contracts. | Accounting/pins complete; deletion review |
| Deferred private-state capture | PrivateStateCodec,HistoryArchiveManifest,HistoryOperations; proposed PrivateOperationStore | Completion/handoff `1fe3c46` is approved with 87 publication assertions, 7 copy cases and 11 completion cases passing. Exact BEGIN/LINK/CLOSE-credit and typed-stage handoff are implemented. Same-process activation/tracked source read/drain/CLOSE at exact 4795c3cdaeacea5a7ef134284929cdecba1c269f has scoped independent APPROVAL; 6 focused groups PASS after retention repair. Earlier activation coordinator59 evidence remains historical; later cold-path results are scoped separately. ADR006 engineering direction is accepted; first typed historical-cancellation admission slice is implemented at af9eebf97174e02c4ab64794a3e80e606179f443, with scoped independent APPROVAL after 4 actual probe groups. Worker evidence: 12 focused groups and 871 HistoryManifestCheck checks PASS. Cold cancellation2078e8e2ceb401ffad4277d6428fac62f3a2e9db has scoped independent APPROVAL: 2 new foreign-audit probes plus 3 earlier scoped groups. Latest worker12 cold groups PASS, including paired fresh JVMs. Activation6 passed at the initial cold slice; coordinator59 RED there→GREENa793960, not rerun2078e8e. Cold completion/reversed-original-graph recovery remains OPEN. Cold recovery, runtime and whole-heap/performance acceptance remain open. | C2 producer gate |
| Deferred optional app-data export | PrivateArchive/PrivateBundleInventory; no backup UI | V1+reviewed V2 framing foundations only. Persist full scoped inventory/active-tab selection; semantic validation, tracked provider output, actual close, fresh readback and durable truthful completion, then accessible app flow. | Capture; reviewed portable binding |
| Deferred optional app-data import | No wired importer; codecs provisional | Durable bounded staging, typed graph checks, preview, deterministic remap, lossless projection, committed visibility and reopening retained history/drafts. Preserve live drafts/12tab capacity; imported policies/URI grants remain inert. | Semantic validation/shared quota; export fixtures |
| M1 migration/rollback | Existing SessionStore/RecoveryStore; ADR005 additive direction | Old native/control bytes preserved; local upgrade, failure/retry and reopening fixtures remain required. Cross-installation private-state adoption fixtures are deferred with optional import. No destructive migration/default cleanup. | Before enabling changed local storage/UI |
| M2/#3 editor work/statistics | LogicalLineIndex,KineticEditText,LatestStatistics | Implemented/wired+host line/statistics scheduling. Remaining allocation/input/IME/paste/undo interactions and measured device regressions. | G0/G1 below |
| M2/#3 syntax/preview | MarkdownSyntaxHighlighter,LatestPreview; Activity scheduler/DOM identity | Affected-range processing and bounded render/delivery host-tested; preserve foreign/search spans and legitimate deferred actions. Finish outline repair; profile renderer/layout/queue costs rather than invent an incremental parser. | N1; matched fixtures |
| M2 size policy/memory budgets | DocumentIO.MAX_DOCUMENT_BYTES=8388608 and error messages | Existing8MiB guards; printing strict guard and metadata budgets are not device budgets. Existing UTF-8/8MiB native policy is visible in Workspace; measure peak allocation/RSS and establish observed regression thresholds. | Measured baseline |
| M3/#6 folder/project navigation | WorkspaceRootStore/TreeReader/DirectoryLoader and actual Activity folder picker/browser | Saved read grants, bounded manual nesting/Up, normal open, partial/revoked/BUSY states independently approved;120 focused UI/backend checks and final SDK compile PASS. Exact confirmation/themed-row and local-autosave ordering repairs included. Physical SAF/grant restart/Fold remain open; no recursive search or write operations claimed. | G2; physical provider acceptance |
| M3/#6 recents/pins/favorites/search | showRecents/RecentStore exists; no cross-file search service | Preserve recents, add explicit favorites/pins and bounded authorized recursive search with cancellation/incomplete results. | Workspace enumeration/identity |
| M3/#6 explicit file operations | DocumentIO single-document save; no workspace operation flow | Capability-aware duplicate/rename/move, conflict consent and non-destructive recovery/revocation tests; never automatic organization or broader grants. | Protected saves+workspace provider contract |
| M4/#7 typography/layout | Validated ReaderTypography/renderer463aab0; existing Appearance/font controls | Renderer independently approved80 focused checks/default-nine-theme byte fixtures; real Appearance persistence/captured-preview/HTML/print-input wiring now independently approved (40UI+6request+3share, affected preview/navigation/outline/mode/print and SDK compile PASS). Existing fonts sizes/themes retained; no source mutation. Visual/device gates remain open. | G2; reader options contract |
| M4/#7 positions/bookmarks | DocumentTab/SessionStore store scroll/selection; renderer UTF16 source offsets | Pixel positions are not durable semantic anchors. Add deterministic anchors/fallback and bookmarks through edits/reflow/reopen/death. | N1; reviewed anchor model |
| M4/#7 highlights/annotations | No persistent annotation layer found | Persist source-bound annotations/highlights, identity/revision mapping and honest uncertain-anchor UI; preserve native Markdown. | Anchor model+non-destructive storage |
| M4 adaptive Focus reading | Existing Focus/View/Split/divider/fold handling | Preserve existing controls; validate content obstruction, keyboard/insets/rotation/font scale/fold/unfold and saved context. | Each reader UI candidate; physical gate |
| M5/#8 undo/redo/durable drafts | Guarded native Undo/Redo3d1104a; SessionStore/RecoveryStore wired | Explicit strip/context/keyboard routes independently approved;32 routing checks and affected mode/line suites PASS. Native history resets on tab loading; platform IME/selection/grouping/device acceptance remains open. No custom persistent undo stack claimed. | M1 recovery; physical input acceptance |
| M5/#8 heading folding/navigation | MarkdownStructure/outline/source links; no editable heading folding found | Finish stale-effect repair; add folding with deterministic source mapping and preserved text/selection/IME. | N1; editor coordinate/input contract |
| M5/#8 CommonMark/GFM | MarkdownRenderer; renderer_golden/search/Unicode fixtures | Existing lists/tables/escapes/code fixtures; authentic conformance corpus, documented deviations and bounded fixes. No parser replacement merely for convenience. | Renderer golden baseline; security |
| M5/#8 review/proposed edits | HistoryDiff wired for immutable versions; no accept/reject workflow | Add source-bound proposed changes, side-by-side review and explicit accept/reject preserving newer edits; reuse annotation/diff contracts. | M1 snapshots+M4 anchors+undo |
| M6/#8 paged PDF | printDocument/DocumentPrint isolated captured WebView, merged PR22 | Print-only page CSS independently approved;13 framing checks,15 actual host PDFs/8 inspected rasters and affected print20cases134+controlled18 PASS. Tables/code/oversized-row/theme content and geometry checked; Android print/spooler acceptance, secure images and all remaining M6 work stay open. | Stable rendering/resources; Android print gate |
| M6/#8 DOCX export/import feasibility | Reviewed MarkdownDocx stream core; [fidelity matrix](../../DOCX_EXPORT_FIDELITY.md) | Core184Java+14regressions+86ZIP/XML/list+CRC+freshOffice11 PASS after resource/fidelity repairs. Private staging, captured Activity flow and verified binary SAF new-copy publication still required before a user-visible feature. Separate bounded import feasibility remains open; no round-trip promise. | Export capture+provider/resource bounds |
| M6/#8 local resources/extensions | Renderer blocks images; preview baseURL/security/link policy | Add grant-scoped local images/relative links without scripts, arbitrary file access or unsolicited networking; then approved footnotes/extensions with fixtures. | Workspace grants/security; renderer conformance |
| M7 accessibility/keyboard/Fold | Existing audits/device matrix; host proxies | Complete all nine-theme nodes/targets/font scale/keyboard/TalkBack/IME/orientation/Fold matrix against coherent candidate. | Candidate features+real device |
| M7 lifecycle/providers/resources | Existing fault/restart/memory host suites | Actual Android app-UID/process death/low-memory/provider revocation/unavailability/battery testing; retain cross-feature journeys and recovery evidence. | Integrated candidate+available Android tools |
| M7 migration/release/provenance | BUILD,RELEASE_GATES,baseline inventory/device checklist | Finish upgrade/rollback playbook, changelog/source/checksum and canonical v2/v3 signed increasing-versionCode update over installed app. No uninstall/replacement key. Owner acceptance remains required. | All retained scope+canonical private signer+physical acceptance |

## Dependency gates and smallest useful schedule

**G0 engineering foundation:** exact baseline provenance, current required host recoverability, reviewed source and reproducible unsigned build. Existing M1/M2 work is authorized by standing decisions; physical acceptance belongs at the feature-complete candidate and does not block helper implementation.

**G1 performance prerequisite:** matched fixture host results and bounded-work/memory evidence for the changed path. During implementation, run the minimum affected tests and essential compile/build checks. At a feature-complete candidate, run broad regression, performance and migration coverage; batch device acceptance then. Do not invent device thresholds or treat host timing as physical evidence.

**G2 next workspace/reader implementation:** accepted local storage/recovery interfaces and a successful host local lost-work/restore-to-new-copy journey. Portable capture/funding/adoption is deferred and is not a dependency. Apply G0/G1 checks to each lane. Do not start multiple consumers against an unfinished authority. Once these safety conditions are met, M3 tree enumeration and M4 typography/anchor work can proceed independently; unfinished configurable-retention UI details do not block them. Activity integration stays serial. Device exit gates remain open until actual tests.

Schedule under the owner decision: local safe-save/history/recovery runtime closure
and focused lost-work/new-copy tests → dependency-ready M2 performance, M3 workspace
and M4 reader work → M5 editor/M6 interoperability → complete M7 acceptance. Local
retention controls/recovery continue alongside independent lanes; portable app-data
capture/funding/export/import/adoption stays deferred. Activity integration remains
serial. Existing print capture and local history are reused, not rebuilt.

Team policy: root owns orchestration, integration and critical review. Use cheaper demonstrably capable agents for bounded implementation, tests and documentation. Sensitive persistence review must remain independent and capable. Keep ownership disjoint and avoid duplicated gates.

| Ownership / worktree | Exact checkpoint | Disposition / next consumer |
| --- | --- | --- |
| Lead; orchestration/integration | draft PR24; completion/handoff1fe3c46 approved | Own integration/accounting, STATUS and coherent boundary. A1 exact f98ca291 approved and integrated at88fb149 on draft PR24; no C2 full gate after8dcbea5. |
| Capture worker; operation-data-capture | Admission exact af9eebf97174e02c4ab64794a3e80e606179f443 implemented; scoped independent APPROVAL after 4 actual probe groups. Activation4795c3c retains scoped APPROVAL; earlier coordinator59 evidence historical; later cold results scoped separately | ADR006 direction accepted; first typed historical-cancellation admission slice implemented at af9eebf97174e02c4ab64794a3e80e606179f443; scoped independent APPROVAL after 4 actual probe groups; worker evidence: 12 groups and 871 HistoryManifestCheck checks PASS. Cold completion contract28147af8 independently approved and integrated: contract requires refusing cold completion without writes when the original nullable profile is absent; no completion API is implemented. Eligible cancellation remains implemented. Cold completion implementation is blocked on an authentic persisted original profile; resolve B2/C funding/interface contract before publication/routing and coordinators/UI. Retain global pending/prune blocks, all bytes/credits, physically present namespace, selected-own completeness and immutable old-stage anchors. Cold cancellation2078e8e2ceb401ffad4277d6428fac62f3a2e9db has scoped independent APPROVAL: 2 new foreign-audit probes plus 3 earlier scoped groups. Latest worker12 cold groups PASS, including paired fresh JVMs. Activation6 passed at the initial cold slice; coordinator59 RED there→GREENa793960, not rerun2078e8e. Cold completion/reversed-original-graph recovery remains OPEN. Cold/runtime/full-heap/performance/migration/device remain open; minimum affected memory bounds precede downstream consumers. |
| Portable codecs | A1 exact f98ca291 approved/integrated88fb149; A2 exact2736de7c independently spec/quality APPROVED/integrated08abce8d | A2 RecordCheck72 Java11/-Xmx32m PASS; no runtime backup completion. Scoped evidence in STATUS; no duplicated gate. |
| B0 canonical witness streams | Exact afc57ec9 independently spec/quality APPROVED; integrated d1303eb | Canonical inputs retain original nullable profile/shared budget; no authority. Witness5 PASS and plan29 once; workspace bounds are not whole-coordinator heap proof. |
| C original-baseline raw inventory/sources | Exact cce8a6ee independently spec/quality APPROVED; integrated fb22bff4 | Owned original source seam, read-only missing-root inventory only; no generic admission/write authority. Focused59 plus large1 PASS. |
| B1 producer | Exact ec06a26e independently APPROVED; integrated f0c6a847 | Final221 plus existing PrivateState848 functional once PASS; original two review probes repaired/PASS. Producer only, no P/provider/UI/activation; whole-B2 overlap unproven. |
| B2/C funding/interface contract; cold worker | Test-only estimator ad9cab4c independently approved; four focused cases PASS; dispatch9f58c949 independently APPROVED and integrated | Fixed Writer~7MiB+Pins6MiB exceeds B1 headroom. Resolve actual-snapshot-sized conservative whole-path funding before P, authentic post-P transition/domain3/outer-ID routing and close/success contract. Then publication/routing → runtime activation → coordinators/UI; all retained feature/release gates remain. |
| Independent reviewer; accounting_repair_review | Completion/handoff1fe3c46 approved after 4 groups | Activation/source read/drain/CLOSE exact4795c3cdaeacea5a7ef134284929cdecba1c269f has scoped APPROVAL, including identity/budget/hashed-skip/resource closure and terminal retention/release checks. This does not approve cold source or runtime integration. |
| Native fingerprint/source ownership/preflight/plan | a4cb514, e331477, c3177b8/2f1d14b, a89b284 | Integrated with scoped exact-artifact approvals. Historical evidence only for changed artifacts. |
| Whole-campaign plan reviewer | `73372f50db109c378235406a75ca7abbf4cd85a6`; reviewed blob `029269ba83b6897d4e0148c797db63311ddddda2` | Substantive plan approved against ROADMAP/RELEASE_GATES; no cycles or missing clauses. Source review remains separately scoped. |

Old worktrees/branches/untracked historical notes remain untouched. No push--all, force-push, broad cleanup or deletion. Shared SDK/JDK/JSON dependency remain read-only inputs; each writer owns its checkout outputs and test temporary roots. One heavy build/gate at a time until resource evidence justifies more.

## Review focus

1. Unknown operation data arriving after startup must block new metadata publication, not merely acknowledgment (C1).
2. An already-closed operation audit must remain source-independent while new terminal mutations still reject uncertain accounting (C1/C2).
3. A valid explicit navigation must survive same-source render/reflow while stale mode/tab ABA and delayed sync lose authority (N1).
4. A framed archive with missing retained content, active-tab selection or typed graph validity must not become a successful backup/import (portable workflow).
5. Provider/device/signing absence must be reported narrowly; source integration cannot be mistaken for Android acceptance or a safe downgrade (integration/M7).

## Ready packet N1 — finish current navigation/codec integration

**Attempt:** N1/1. **Outcome:** outline/source jumps act on the intended document without stale scrolls; V2 inventory framing becomes a required tested foundation, not a backup UI.
**Base/owner:** lead, private-backup-integration2bdf43f; consume outline93a5cc5 and already-reviewed codecf357fe1/4ed9fbb.
**Allowed:** merge exact reviewed MainActivity/authorized AST fixtures; outline plan/README; root runner/STATUS. **Forbidden:** storage/capture mutation, renderer/security/link-policy redesign, release/version/signing changes.
**Contracts:** exact source context/route/explicit-navigation token helpers in93a5cc5; existing `PrivateArchive.readPortable(InputStream,ScopedSink,LegacySpool,PrivateBundleInventory.MetadataBudget)` and `writeV2(UUID,StreamOpener,ArchiveSource,OutputStream,MetadataBudget)`. Results are provisional; borrowed metadata is synchronous or separately reserved, never GC-revoked.

- [x] Verify canonical Activity blob and changed fixtures against recorded review; inspect final diff and preserve baseline RED selector separately.
- [x] Merge93a5cc5 normally; register `bash verification/outline_navigation/run.sh` only, never `--baseline`, in required host runner. Keep scoped archive runner registered.
- [x] Run focused integration checks only for changed/conflicted inputs; expected current outline32cases/74assertions GREEN, legacy baseline15RED preserved as historical diagnostic. Check status/preview/nav/mode/syntax dependency closure without weakening guards.
- [x] At the coherent integration boundary commit intended source, then run `python3 verification/check_clean_rebuild.py` once with configured JDK17 PATH/SDK37.0/tools36.0.0. Do not repeat host immediately outside that wrapper.
- [x] Review exact source/privacy/assets/docs; draft PR while local verification proceeds, deliberate ready transition only after applicable gates. Required hosted success on final head precedes authorized merge. Independent source review is retained only for identical reviewed artifacts; changed risky source needs fresh review.
- [x] Read back main/ancestry, update STATUS once, continue C1/C2. If capture is not ready, do not merge its unsafe source to force a combined PR; choose an independently reviewable boundary and record the scope honestly.

## Ready packet C1 — repair operation accounting before producers

**Attempt:** C1/2 (a92a999 is known-failing checkpoint). **Outcome:** operation evidence cannot escape quota/authority through policy, pin or terminal metadata paths.
**Owner/workspace:** lead inline, operation-data-capture; preserve worker's four dirty tracked repairs before editing. Source basea92a999; approved architecture7a6c72b3/ADR004/005, existing native/control formats unchanged.
**Allowed:** HistoryCapacity,HistoryPins,PrivateOperationStore and necessary existing HistoryControlStore/Storage helpers; `verification/history_operation_data`; direct regression probes. **Forbidden:** MainActivity, PrivateArchive/Inventory, new format/domain/allowance, runtime capture/export/adoption or historical evidence deletion.
**Interfaces:** existing `HistoryCapacity.setPolicy(HistoryPolicy)`, `reconcile()`, `inspect()`; package-private pin begin/resume/terminal/reconcile paths and `PrivateOperationStore.inspect(...)` as implemented in this worktree. No new public producer API is frozen by this packet.

- [x] Preserve tracked diff/known failing probes locally; inspect incomplete dirty changes rather than overwrite them. Convert reviewer AdmissionProbe observations into production-path assertions in the existing runner.
- [x] Reproduce unknown namespace before/after startup across policy publication, policy-stage promotion, domain1 BEGIN/resume, quiesced CANCELLED CLOSE and registration. For preexisting uncertainty, assert rejection before new policy generation/stage promotion/BEGIN/CLOSE publication. If a callback introduces uncertainty after physical publication, refuse acknowledgment and preserve evidence; do not promise rollback of already-published bytes. Cold registration rejection is an existing passing control.
- [x] Reproduce same-byte PREPARE inode replacement at final directory callback; require fresh within-operation identity stability. Keep missing/partial map/descriptor after LINK (active and CLOSED) invalid instead of future credit.
- [x] Repair pre-publication and final-callback barriers under the same primary monitor/lease. Preserve ordinary quota-admitted saves for fully accounted stale/failed preparations and source-independent historical CLOSED audit. No blanket freezing or second authority.
- [x] Run `bash verification/history_operation_data/run.sh`, then affected capacity/pins/operations/maintenance/executor scopes according to actual edits. Record genuine failures, tests and fresh-process results; do not treat old3349 pass count as proof against new probes.
- [x] Commit a coherent repair; independent critical review must clear exact source before integration. If review capacity remains unavailable, retain tested source/precise next steps without promoting it or repeatedly retrying workers.

## Deferred portable packet preparation — retained, not current work

**C2 capture producer:** Completion/handoff commit
`1fe3c464e3e3f7ceb48dcceba34989dc9b0dc695` is independently approved after 4 groups.
Worker results pass 87 publication assertions, 7 copy cases and 11 completion cases,
including fresh-child JVM checks. Implemented: exact BEGIN204/LINK173/CLOSE credit126,
typed-stage allowance, preadmitted retained maps and one-use old-owner-claim handoff.
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
[ADR-006](../../decisions/ADR-006-terminal-cancelled-capture-admission.md) is ACCEPTED
engineering direction after independent review of exact `e8cb1532d514e1d2e0c46d93c8d5f20e52c44820`,
ADR blob `b54b1629f72f213fb9bf735a6c3a76266ca70cdb`. The first typed historical-cancellation
capture-admission slice is implemented at `af9eebf97174e02c4ab64794a3e80e606179f443`;
scoped independent source review is **APPROVED** after 4 actual probe groups:
classification, immutable old-stage anchor refresh, funded capture→activate/read/drain/CLOSE
preserving old credits, and planner/intent/stale maintenance/retirement refusals with
an ordinary-save positive control. Worker evidence: 12 focused groups
and 871 legacy HistoryManifestCheck checks PASS, recorded in
[terminal admission evidence](../../../verification/terminal_cancelled_admission/README.md).
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
Before P, freshly match BOTH original C0 control and L0 ledger hashes. A legacy
PIN BEGIN→CANCELLED CLOSED changes C0/fullGraph while L0 remains unchanged; the
freshness guard must reject that mismatch. Plan authority alone cannot publish P.
Retain this safety contract in future evidence updates.
Earlier writer/copy/sink approvals are point-in-time and do not approve the full current
source. Next: prepare the separate cold completion/reversed-original-graph recovery slice;
source and runtime integration remain OPEN. No full gate/build/CI covers C2.
Portable source contract `d43dbb17e110edb73eaf68d4597f208b4186d5d0` (plan blob
`dab01bedacdafb650120a5d6339e3bde94694f01`) is accepted after independent e7 review
and root's mechanical fix. A1 codec work is active in isolated `portable-wire-codecs`,
owning Archive/Inventory/Evidence and its fixtures only; version3/schema2 source is
NOT implemented or approved until committed and reviewed. No other source writer is active.

**Portable binding/export/import:** review the next opt-in preparation before its first BEGIN; preserve authorized local A/B formats. Existing single-active-pin rule makes a post-LINK second pin a new handoff protocol, not a shortcut. Persist inventory and active-tab selection at capture, prove complete retained-item coverage, validate typed graphs, and share logical metadata ownership. Freeze exact new schema/credits/goldens only in that ready packet. Then implement tracked fresh destination readback/completion evidence and strict additive import/reopening with actual app entry points. Unknown failed staging is not silently omitted; cancellation/time/newer text is not a redundancy proof.

**Policy/runtime:** complete startup recovery, understandable errors and configurable retention only once new operation evidence participates in all admission/pruning/retirement proofs. Preserve safe ordinary saves and optional-history/export failure isolation. Stop adding speculative protection layers once the approved boundary passes; finish its runtime consumer.

**G2 lanes:** prepare workspace tree/search and reader typography/anchors from accepted producer interfaces; one Activity integration writer. Later editor/annotation/DOCX work follows the coverage table. Packets must name signatures, byte-vs-UTF16 coordinates, lifecycle/errors, baseline/format version, UI entry point and meaningful tests before dispatch; no invented future interfaces in this campaign map.

## Verification, publication and completion

- During implementation, run minimum affected tests plus essential compile/build checks. At feature-complete candidate, run broad regression, performance and migration coverage, then required clean committed-source host/two-unsigned gate and owner Fold7 acceptance. Reuse installed inputs. New Android APIs must remain min26-compatible, independent of compiler/platform age. Required merge/release gates remain intact.
- Current C2 focused assertions/cases and registered writer/activation runners do not constitute a full gate; the new aggregate registration revision has only received
  `bash -n verification/run_host_checks.sh`; no new aggregate run is claimed. The
  historical full host gate at `8dcbea5` remains attributed to its earlier source. The prior 8 MiB / 1637 ms observation is functional only; performance acceptance remains open.
- Keep actual journeys: failed save→recover/new copy; checkpoint→diff→restore preserving newer text; private export→import→reopen; workspace search under revocation/partial results; annotate→edit→reflow; print intended document while switching tabs; signed in-place upgrade preserving sessions/settings/docs.
- New UI gets accessible loading/empty/error/partial states, cancellation and recovery. Host AST fixtures must extract real dependencies; no stale copied bodies or always-true guards.
- Physical target is owner-reported Galaxy Z Fold7/Android16/OneUI8.0, installed literal“2.25” unverified. At feature completion, the owner will sign the unsigned APK with the original key and test on Fold7. Device acceptance is batched at that point; do not ask for device work during helper implementation. No replacement signer is allowed.
- Canonical certificate remains `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`. Original private signing inputs/older Library provenance are unavailable. Never generate keys, publish private artifacts, recommend uninstalling or claim a source-only unsigned APK is an upgrade.
- Self-review checked every ROADMAP milestone/exit and open issue2–8 against this map. Independent substantive campaign-plan review is approved at `73372f50db109c378235406a75ca7abbf4cd85a6` for reviewed blob `029269ba83b6897d4e0148c797db63311ddddda2`, against ROADMAP and RELEASE_GATES; it found no cycles or missing clauses. This approval does not replace exact-source review.
- Publish this plan on the continuation branch and link STATUS. Continue ready already-authorized execution inline; when capacity/runtime ends, preserve committed source or explicit patch and exact failing tests. Do not call planning, a PR, a codec or M1 completion the end of the Professional campaign.
