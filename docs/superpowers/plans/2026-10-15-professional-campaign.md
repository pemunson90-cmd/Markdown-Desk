# Markdown Desk Professional Campaign Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, or superpowers:subagent-driven-development when execution capacity is available. Execute the ready packets below; detailed future packets are written only when their dependencies are accepted.

**Goal:** Complete the retained M0–M7 workflows and acceptance gates without losing the protected 2.2.5 document behavior or release identity.

**Architecture:** Extend the existing Java/native-View/WebView app through reviewed production helpers and narrow Activity integrations. Finish the current capture/codec/outline boundary before enabling backup; then deliver usable vertical slices, with one integration owner and explicit shared-file ownership.

**Tech stack:** JDK17.0.16+8, javac --release 11, installed android-37.0 revision2, build-tools36.0.0; existing aapt2/D8/zipalign build and separately controlled signing.

**Spec:** [ROADMAP](../../ROADMAP.md), [AGENTS](../../../AGENTS.md), [release gates](../../RELEASE_GATES.md), [CI policy](../../CI_AND_WORKFLOW_POLICY.md), ADR-001 through ADR-005 and the owner's attached whole-roadmap orchestration instructions received2026-10-15. This plan organizes existing scope; it does not replace those authorities.

## Global constraints

- Protected baseline: 2.2.5/versionCode18, com.example.markdowndesk, minSdk26/targetSdk35. SDK37 compilation does not change API policy.
- Preserve Write/Read/Split, Focus/View, eye/lock,12tabs, all nine themes/25 cleared assets, external-change protection, bridge handoffs and offline canonical Markdown.
- No replacement signer, Kotlin/Compose/Gradle/Room migration, new editor framework, broad storage permission or new paid service.
- Deferred roadmap scope stays deferred. DOCX export does not imply lossless Word round-trip.
- Required application recoverability must pass; raw-provider truncation probes stay separately runnable and genuinely RED. Outline production regressions must become GREEN.
- One primary shared quota and one1MiB/256-entry control allowance. No deletion, imported authority or successful-backup claim from framing alone.
- MainActivity, sensitive store authority, shared bridge/build contracts, aggregate runners and STATUS each have one explicit writer at a time.
- Every later persisted workspace/reader/editor feature must update compatible backup/import/migration coverage before enablement; the current legacy allowlist is not permission to omit new private state.
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
`codex/operation-data-capture` has draft PR24. Approved writer `92e41bb` and copy
helper `b4bd0c9` are being batch-published on that active draft PR. The
full clean gate at `8dcbea5` passed before subsequent C2 source additions; no newer
full suite/build/hosted run is claimed. STATUS records exact gate evidence and
hashes. Earlier failed printing fixture attempt46d8bd8 and correction4321957 remain
historical evidence, not current gate state.

Native fingerprint `a4cb514`, source ownership/budget `e331477`, private-frame
preflight `c3177b8`/integration `2f1d14b`, and private capture plan `a89b284` retain
scoped approvals for those exact artifacts. Writer `92e41bb5827cf7d1d553bbe720744f20f239538e`
is independently approved; its targeted 87 assertions and independent 4 scenarios pass. Review
fixed cached installed-capacity/readiness using a narrow provenance getter and fresh
identity/readiness after every callback. This approval excludes production sink,
BEGIN/LINK, handoff, recovery and heap proof.

Copy helper `b4bd0c9b8f8eb110ca0a9eda7f15569d45c315c2` is independently approved
after 3 scenario groups: exact identity binding; retained handles, Error and ordered
cleanup; and overlength rejection before forwarding. Preflight 30 and copy 16 cases pass.
The source worker owns Writer and its fixtures for provisional sink, live-prefix and
promotion; root owns orchestration/integration. Production sink, final BEGIN/LINK,
handoff, recovery and heap proof remain OPEN. No newer full gate/build/CI covers C2.
Independent whole-campaign plan review remains NOT RUN; M1 is not complete. Physical
acceptance remains for feature completion. STATUS records the latest detailed
checkpoint.
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
| M1/#5 captured private data | PrivateStateCodec,HistoryArchiveManifest,HistoryOperations; proposed PrivateOperationStore | Codecs/pins and C1 accounting are integrated; C2 producer is incomplete. The worker owns Writer and its fixtures for provisional sink, live-prefix and promotion. Finish production sink, typed transitions, handoff, recovery and fault/reopen evidence before activation. | C2 producer gate |
| M1/#5 complete backup export | PrivateArchive/PrivateBundleInventory; no backup UI | V1+reviewed V2 framing foundations only. Persist full scoped inventory/active-tab selection; semantic validation, tracked provider output, actual close, fresh readback and durable truthful completion, then accessible app flow. | Capture; reviewed portable binding |
| M1/#5 strict import/additive restore | No wired importer; codecs provisional | Durable bounded staging, typed graph checks, preview, deterministic remap, lossless projection, committed visibility and reopening retained history/drafts. Preserve live drafts/12tab capacity; imported policies/URI grants remain inert. | Semantic validation/shared quota; export fixtures |
| M1 migration/rollback | Existing SessionStore/RecoveryStore; ADR005 additive direction | Old native/control bytes preserved; complete fixtures for old/new private-state adoption, failure/retry and reopening. No destructive migration/default cleanup. | Before enabling new storage/UI |
| M2/#3 editor work/statistics | LogicalLineIndex,KineticEditText,LatestStatistics | Implemented/wired+host line/statistics scheduling. Remaining allocation/input/IME/paste/undo interactions and measured device regressions. | G0/G1 below |
| M2/#3 syntax/preview | MarkdownSyntaxHighlighter,LatestPreview; Activity scheduler/DOM identity | Affected-range processing and bounded render/delivery host-tested; preserve foreign/search spans and legitimate deferred actions. Finish outline repair; profile renderer/layout/queue costs rather than invent an incremental parser. | N1; matched fixtures |
| M2 size policy/memory budgets | DocumentIO.MAX_DOCUMENT_BYTES=8388608 and error messages | Existing8MiB guards; printing strict guard and metadata budgets are not device budgets. Make supported policy discoverable in normal UI/docs; measure peak allocation/RSS and establish observed regression thresholds. | Measured baseline |
| M3/#6 folder/project navigation | showWorkspace currently templates/single-file opening/history/recents; RecentStore | Existing desk is not a granted folder tree. Add SAF tree grant lifecycle, bounded lazy enumeration, navigation/empty/partial/revoked states and persisted project identity. | G2; narrow provider contract |
| M3/#6 recents/pins/favorites/search | showRecents/RecentStore exists; no cross-file search service | Preserve recents, add explicit favorites/pins and bounded authorized recursive search with cancellation/incomplete results. | Workspace enumeration/identity |
| M3/#6 explicit file operations | DocumentIO single-document save; no workspace operation flow | Capability-aware duplicate/rename/move, conflict consent and non-destructive recovery/revocation tests; never automatic organization or broader grants. | Protected saves+workspace provider contract |
| M4/#7 typography/layout | showAppearance/font controls; MarkdownRenderer fixed CSS | Existing fonts sizes/themes retained; add families/line/paragraph spacing/measure/margins with persistence, live reflow and no source mutation. | G2; reader options contract |
| M4/#7 positions/bookmarks | DocumentTab/SessionStore store scroll/selection; renderer UTF16 source offsets | Pixel positions are not durable semantic anchors. Add deterministic anchors/fallback and bookmarks through edits/reflow/reopen/death. | N1; reviewed anchor model |
| M4/#7 highlights/annotations | No persistent annotation layer found | Persist source-bound annotations/highlights, identity/revision mapping and honest uncertain-anchor UI; preserve native Markdown. | Anchor model+non-destructive storage |
| M4 adaptive Focus reading | Existing Focus/View/Split/divider/fold handling | Preserve existing controls; validate content obstruction, keyboard/insets/rotation/font scale/fold/unfold and saved context. | Each reader UI candidate; physical gate |
| M5/#8 undo/redo/durable drafts | Native editor; SessionStore/RecoveryStore wired; no explicit undo/redo commands found | Audit platform editing history first; add predictable explicit commands/grouping and durable drafts without composition/selection loss. | M1 recovery; input regression seam |
| M5/#8 heading folding/navigation | MarkdownStructure/outline/source links; no editable heading folding found | Finish stale-effect repair; add folding with deterministic source mapping and preserved text/selection/IME. | N1; editor coordinate/input contract |
| M5/#8 CommonMark/GFM | MarkdownRenderer; renderer_golden/search/Unicode fixtures | Existing lists/tables/escapes/code fixtures; authentic conformance corpus, documented deviations and bounded fixes. No parser replacement merely for convenience. | Renderer golden baseline; security |
| M5/#8 review/proposed edits | HistoryDiff wired for immutable versions; no accept/reject workflow | Add source-bound proposed changes, side-by-side review and explicit accept/reject preserving newer edits; reuse annotation/diff contracts. | M1 snapshots+M4 anchors+undo |
| M6/#8 paged PDF | printDocument/DocumentPrint isolated captured WebView, merged PR22 | Wired+host ownership/readiness, not actual PDF correctness. Add paged styles/layout and inspect real Unicode/table/list/image PDF content/pagination independently from submission. | Stable rendering/resources; Android print gate |
| M6/#8 DOCX export/import feasibility | No DOCX implementation found | Define fidelity matrix; bounded export from canonical Markdown, independent ZIP/XML/office reopening. Separate bounded import feasibility; no promised round-trip. | Renderer structure+export capture+resource bounds |
| M6/#8 local resources/extensions | Renderer blocks images; preview baseURL/security/link policy | Add grant-scoped local images/relative links without scripts, arbitrary file access or unsolicited networking; then approved footnotes/extensions with fixtures. | Workspace grants/security; renderer conformance |
| M7 accessibility/keyboard/Fold | Existing audits/device matrix; host proxies | Complete all nine-theme nodes/targets/font scale/keyboard/TalkBack/IME/orientation/Fold matrix against coherent candidate. | Candidate features+real device |
| M7 lifecycle/providers/resources | Existing fault/restart/memory host suites | Actual Android app-UID/process death/low-memory/provider revocation/unavailability/battery testing; retain cross-feature journeys and recovery evidence. | Integrated candidate+available Android tools |
| M7 migration/release/provenance | BUILD,RELEASE_GATES,baseline inventory/device checklist | Finish upgrade/rollback playbook, changelog/source/checksum and canonical v2/v3 signed increasing-versionCode update over installed app. No uninstall/replacement key. Owner acceptance remains required. | All retained scope+canonical private signer+physical acceptance |

## Dependency gates and smallest useful schedule

**G0 engineering foundation:** exact baseline provenance, current required host recoverability, reviewed source and reproducible unsigned build. Existing M1/M2 work is authorized by standing decisions; physical acceptance belongs at the feature-complete candidate and does not block helper implementation.

**G1 performance prerequisite:** matched fixture host results and bounded-work/memory evidence for the changed path. During implementation, run the minimum affected tests and essential compile/build checks. At a feature-complete candidate, run broad regression, performance and migration coverage; batch device acceptance then. Do not invent device thresholds or treat host timing as physical evidence.

**G2 next workspace/reader implementation:** G0/G1 plus accepted M1 storage/capture/adoption interfaces and successful host lost-work/restore journey. Do not start multiple consumers against an unfinished authority. M3 enumeration and M4 typography/anchor work can then run independently behind accepted contracts; Activity integration stays serial. Their device exit gates remain open until actual tests.

Critical path: C1 accounting repair → C2 native/private capture → portable binding/semantic validation → verified export/import/adoption journey → configurable retention/runtime recovery → G2 → workspace/reader → editor/interoperability → complete M7 acceptance. N1 outline repair is independently ready and joins the next source integration. Existing print capture is reused, not rebuilt.

Team policy: root owns orchestration, integration and critical review. Use cheaper demonstrably capable agents for bounded implementation, tests and documentation. Sensitive persistence review must remain independent and capable. Keep ownership disjoint and avoid duplicated gates.

| Ownership / worktree | Exact checkpoint | Disposition / next consumer |
| --- | --- | --- |
| Lead; orchestration/integration | draft PR24; writer92e41bb approved; copy helperb4bd0c9 committed | Own integration/accounting, STATUS and coherent boundary. Approved writer/copy checkpoints batch-published on active draft PR24; no C2 full gate after8dcbea5. |
| Capture worker; operation-data-capture | Approved writer92e41bb; copy helperb4bd0c9 committed | Own source/tests through coherent producer; writer87 assertions and PreparationSources181 pass. Copy16 cases pass and independent copy review is approved. Worker owns Writer and its fixtures for provisional sink, live-prefix and promotion; continue actual production sink, typed transitions, final BEGIN/LINK, handoff, cold recovery and fault/heap proof. |
| Independent reviewer; accounting_repair_review | Copy helperb4bd0c9 independently approved after 3 scenario groups | Writer92e41bb and copy helperb4bd0c9 approvals are scoped to those exact artifacts; production sink and campaign review remain outstanding. |
| Native fingerprint/source ownership/preflight/plan | a4cb514, e331477, c3177b8/2f1d14b, a89b284 | Integrated with scoped exact-artifact approvals. Historical evidence only for changed artifacts. |
| Whole-campaign plan reviewer | Not run | Independent campaign review remains outstanding; do not treat source approvals as satisfying it. |

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

## Next packet preparation, not speculative implementation

**C2 capture producer:** Native fingerprint, source ownership/budget, private-frame
preflight, and pure capture plan are integrated with scoped exact-artifact approvals.
Writer `92e41bb` is independently approved (87 assertions; independent 4 scenarios).
Copy helper `b4bd0c9` is independently approved after 3 scenario groups; its 16 cases pass. The worker now owns Writer and its fixtures for provisional sink, live-prefix and promotion.
Require both original C0 control and L0 ledger hashes freshly matched before P, including
the reproduced legacy PIN BEGIN→CANCELLED CLOSED case that changes C0/fullGraph while
L0 stays unchanged. Plan authority alone cannot publish P. Continue through Writer-owned provisional
sink, live-prefix and promotion, then production sink, exact typed transitions, final BEGIN/LINK and handoff, cold recovery,
and crash/ENOSPC/reopen/heap evidence. Do not promote helper approvals into whole-campaign
review.

**Portable binding/export/import:** review the next opt-in preparation before its first BEGIN; preserve authorized local A/B formats. Existing single-active-pin rule makes a post-LINK second pin a new handoff protocol, not a shortcut. Persist inventory and active-tab selection at capture, prove complete retained-item coverage, validate typed graphs, and share logical metadata ownership. Freeze exact new schema/credits/goldens only in that ready packet. Then implement tracked fresh destination readback/completion evidence and strict additive import/reopening with actual app entry points. Unknown failed staging is not silently omitted; cancellation/time/newer text is not a redundancy proof.

**Policy/runtime:** complete startup recovery, understandable errors and configurable retention only once new operation evidence participates in all admission/pruning/retirement proofs. Preserve safe ordinary saves and optional-history/export failure isolation. Stop adding speculative protection layers once the approved boundary passes; finish its runtime consumer.

**G2 lanes:** prepare workspace tree/search and reader typography/anchors from accepted producer interfaces; one Activity integration writer. Later editor/annotation/DOCX work follows the coverage table. Packets must name signatures, byte-vs-UTF16 coordinates, lifecycle/errors, baseline/format version, UI entry point and meaningful tests before dispatch; no invented future interfaces in this campaign map.

## Verification, publication and completion

- During implementation, run minimum affected tests plus essential compile/build checks. At feature-complete candidate, run broad regression, performance and migration coverage, then required clean committed-source host/two-unsigned gate and owner Fold7 acceptance. Reuse installed inputs. New Android APIs must remain min26-compatible, independent of compiler/platform age. Required merge/release gates remain intact.
- Keep actual journeys: failed save→recover/new copy; checkpoint→diff→restore preserving newer text; private export→import→reopen; workspace search under revocation/partial results; annotate→edit→reflow; print intended document while switching tabs; signed in-place upgrade preserving sessions/settings/docs.
- New UI gets accessible loading/empty/error/partial states, cancellation and recovery. Host AST fixtures must extract real dependencies; no stale copied bodies or always-true guards.
- Physical target is owner-reported Galaxy Z Fold7/Android16/OneUI8.0, installed literal“2.25” unverified. At feature completion, the owner will sign the unsigned APK with the original key and test on Fold7. Device acceptance is batched at that point; do not ask for device work during helper implementation. No replacement signer is allowed.
- Canonical certificate remains `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`. Original private signing inputs/older Library provenance are unavailable. Never generate keys, publish private artifacts, recommend uninstalling or claim a source-only unsigned APK is an upgrade.
- Self-review checked every ROADMAP milestone/exit and open issue2–8 against this map. Lead reviewed dependencies/ownership/current artifacts; **new independent campaign-plan review is NOT RUN because worker execution hit usage limits**. Existing architecture/source approvals are scoped above. Obtain available PM/independent review at its applicable gate; do not repeatedly ask Pat for routine plan confirmation.
- Publish this plan on the continuation branch and link STATUS. Continue ready already-authorized execution inline; when capacity/runtime ends, preserve committed source or explicit patch and exact failing tests. Do not call planning, a PR, a codec or M1 completion the end of the Professional campaign.
