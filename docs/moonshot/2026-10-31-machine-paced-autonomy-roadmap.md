# Moonshot × Markdown Desk — Machine-paced autonomy roadmap

**Date:** 2026-10-31  
**Status:** Operational design / planning only. No background service is activated by this document.  
**Product:** `pemunson90-cmd/Markdown-Desk`  
**Operating substrate:** Foundry 0.2, presently stored on `pemunson90-cmd/PNB2026` branch `foundry/standalone-20261027` (not Nova Mobiles code).  
**Implementation principle:** Execute by dependency readiness and validated outcomes, never by human sprints, calendar months, token budgets, or arbitrary agent counts.

## Standing mission

Turn Markdown Desk into an excellent local-first, fast, reliable, accessible Android Markdown writing, reading and research workstation for its owner, particularly on Galaxy Fold7. The organization must independently research alternatives, build, test, integrate, document, deliver usable signed updates, learn from failures, and continue the next eligible objective without a new chat prompt. It may make routine technical tradeoffs within the agreed roadmap, but not change product identity, silently alter user documents, broaden permissions, or imply device acceptance without evidence.

Success is **accepted product outcomes per end-to-end autonomous operating cycle**, adjusted for defects and user intervention—not agents spawned, GitHub commits, raw test assertions, generated code, or time visibly spent reasoning.

## Verified starting facts and source reconciliation

- Owner-confirmed device-working baseline: Markdown Desk `2.2.12`, versionCode **27**, package `com.example.markdowndesk`. Owner reported that older Recents files reopened; preserve this observation without inventing a mechanism.
- `2.3.0-navigation1`, versionCode **28**, is a signed, Android-emulator-verified candidate at Markdown Desk [PR #31](https://github.com/pemunson90-cmd/Markdown-Desk/pull/31), **not yet owner-reported as device accepted**. The PR is a draft, and its base is PR #30's branch. Do not declare the default `main` branch canonical for the installed app.
- Other draft branches include the Downloads/contrast/Android picker PR chain and separate current-phone data-protection work (PR #24). Reconcile all ancestry and ownership before merging. Preserve deferred cross-install private-state import/export from the 2026-10-15 owner decision; do not silently reopen it.
- Full Android app uses Java/Views/WebView and custom `apps/MarkdownDesk/build.sh` with JDK17, Android SDK37.0, build-tools36.0.0, `javac` (Java11 target), `aapt2`, D8 and zipalign. Build script deletes its checkout-local build directory; each simultaneous builder requires a separate checkout/worktree.
- The original canonical signing certificate fingerprint is `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`. Private signing credentials stay solely in authorized local/private handling, never GitHub, test logs or worker contexts. APK delivery and actual on-device install remain distinct.
- The current Foundry 0.2 continuity branch implements SQLite memory, fenced leases, scoped actor work, source/evidence archives, scripted model-protocol demonstrations, independent evaluator, release packages and restore. A separate Oct 28 demonstration reports a bounded live Linux Codex CLI implementer, but the general real-application autonomous campaign has **not** been demonstrated.
- Foundry's `docs/ACTIVE_PLAN.md` still points to a pilot for Markdown Desk Favorites that became obsolete when its behavior was confirmed working. The obsolete attempt/PR was closed without merging. Correct that pilot before scheduling anything. The unaccepted Foundry schema-5 discovery changes must not be treated as the reviewed 0.2 runtime.
- Source authority to consult: Markdown Desk `AGENTS.md`, `docs/ROADMAP.md` (M0–M7), `docs/STATUS.md`, `docs/RELEASE_GATES.md`, `docs/CI_AND_WORKFLOW_POLICY.md`, `docs/DECISIONS.md`, ADRs, `docs/MOONSHOT_RECOVERY_CHECKPOINT_20261031.md`; Foundry `README.md`, `docs/STATUS.md`, `docs/ACTIVE_PLAN.md`, `docs/CONTINUITY.md`, `docs/OPERATIONS.md`, and verified Linux integration record. Read live heads: dates above are snapshots.

## Operating rule: readiness graph, not schedule

A gate opens immediately when its required evidence exists. Independent tasks start concurrently, including research, evaluator construction, product reconnaissance, toolchain verification, acceptance scenario generation and release-channel testing. Do not serialize these just because a calendar document lists them in order.

There are six logically serial transitions on the shortest credible path:
1. **G0: Know what is real.** Pin installed baseline, current candidate, active ownership and authoritative intent.
2. **G1: Real worker.** Authenticate a supported provider route, use isolated Git checkout and existing Android toolchain, run two independent bounded real worker attempts, and prove recoverable ownership.
3. **G2: Real product experiment.** Identify a *currently failing or measurably inferior* user journey. Prove the baseline problem with independent reproducible tests; evaluate maintained/licensed reference implementations and competitor pain; generate two genuinely distinct solutions only when uncertainty warrants.
4. **G3: Release-quality result.** Integrate selected solution, independently verify real Android behavior and document safety, produce original-certificate-signed APK and acceptance evidence, with owner/device acceptance truthfully separated.
5. **G4: Durable recurrence.** Kill the active worker/supervisor at deliberately selected points. Recover source ownership and uncertain effects. Then autonomously select and complete a second different real feature without user relay.
6. **G5: Scaled institution.** Expand to many independent research, testing and scoped implementation workers with integration control, then run held-out measurements and promote genuinely beneficial harness changes.

G1, evaluator setup, research dossiers and infrastructure probes can overlap after a minimal G0 source manifest. G2 exploration can overlap G3 release packaging for *different* completed candidates. Never let a second writer modify shared `MainActivity`, document identity, release policy or data-protection contracts without coordinated ownership. G5 exploratory research can start immediately but cannot certify claims from unfinished gates.

## G0 deliverables: correct truth first

Create one source-linked `PRODUCT_MANIFEST.json` (location chosen to avoid duplicate trackers) with baseline APK/signature identity, branch/commit ancestry, source tree, all nine themes, 25 splash assets, current M0–M7 obligation statuses, active leases/worktrees, test commands and accepted user observations. Track `planned`, `source-present`, `wired`, `host-passed`, `Android-passed`, `physical-Fold-passed`, `release-accepted` separately. Reconcile stale docs without rewriting archived history.

**Gate:** a fresh agent with no chat history can answer what source is installed, which PR can be safely built on, which features already work, what truly needs repair, and which requirements are intentionally deferred. Inject a deliberately stale README and confirm the agent rejects it in favor of the source/evidence manifest.

## G1 deliverables: actual resident Foundry

Reuse reviewed Foundry 0.2 in a *fresh schema-compatible home* on the existing authorized Ubuntu WSL2 host. Verify native Codex CLI authentication and tool access from the actual service account; an old login report is not a fresh proof. Prefer a supported Codex process/app-server route over building a new agent harness solely to call a model. Do not assume ChatGPT or Claude subscription capacity implies paid API credentials.

Create a product adapter for `git`, `javac`, Android packaging, emulator/UI automation, branch/PR read/write, and evidence hashing. Configure the Foundry supervisor as a restartable user service with durable SQLite state, source bundles, receipts and **no signing secrets in agent scope**. Capture every external action and reconcile ambiguous success by reading the external source before retrying.

**Gate:** one actual live task crosses planner/implementer/evaluator boundaries using a real Markdown Desk checkout; a worker dies and the next instance resumes from recorded state rather than restarting or falsely declaring success. No manual prompt or copy-paste between agents.

## G2 deliverables: first product pilot

**First, falsify the existing problem statement.** Do not reimplement Favorites, old-recents recovery, or already-existing native picker functions. Conduct parallel read-only reconnaissance of current candidate's UI/code, issues, Android behavior, large-file performance, and independently written user journeys. Rank a small set of demonstrably missing/defective outcomes by user value, confidence, integration risk and testability.

A good pilot is a complete user-visible vertical slice such as a measured typing/preview responsiveness bottleneck, a reproducible document identity/recovery flaw, or an unimplemented roadmap item that source and device evidence confirm. Choose the *smallest high-impact authentic outcome*; do not manufacture a bug just to validate Foundry.

Research mature Android patterns/libraries, prior art and license fitness before writing new code. Run small proof experiments before choosing a substantial architectural replacement.

**Gate:** frozen failing baseline, clear user-visible pass behavior, candidate diff and independent green acceptance on the *actual integrated app*, all retained. A passing isolated helper is not a product improvement.

## G3 deliverables: product assurance and release

Build separate evaluation pools: host/unit/property/fuzz checks; emulator-native journeys; image/contrast and large-font/layout inspection; storage provider failure injection; update and process-death recovery; performance/battery/memory profiling. Run nine-palette, folded/unfolded, portrait/landscape, keyboard/IME, 12-tab, Unicode, 10k/100k/500k-word within the current 8 MiB file-policy matrix as applicable. Some real Fold7 and true provider behavior requires device access and remains an explicit gate.

Preserve original UTF-8/BOM/newline bytes for unedited Markdown. Before destructive provider writers, preserve exact original and attempted edit under existing ADR-001. Keep the documented generic-provider atomicity hazard separate from required *application-level* recoverability. Do not weaken acceptance to make a test green. Test derived integration, not merely worker branches.

Canonical `versionCode` must increase strictly. Test immutable APK hashes, package identity, zipalign and v2/v3 signature, canonical certificate and safe in-place upgrade. Never recommend uninstall. Keep private signing agent/host isolated from worker credentials and hosted CI; publish signed output, checksum, exact git revision and real coverage evidence. Do not silently install on Pat's phone.

**Gate:** a signed usable candidate plus independent technical acceptance and explicitly recorded remaining physical acceptance. Declaring product accepted waits for whatever actual device evidence the agreed release scope requires.

## G4 deliverables: autonomous continuation under fault

Kill worker in mid-edit, worker during external GitHub response, controller during release artifact preparation, and dashboard during restart. Test fenced lease expiry, idempotent reconcile, retained failed attempts, root-source continuity, stale candidate rejection and source/archive restoration. Repeat one product experiment *without any fresh operator task*. Track the exact count of user technical interventions.

**Gate:** after deliberately interrupted mission A, Foundry recovers and completes it and autonomously selects mission B. Independent evaluation sees actual user-facing improvements, not just Git commits.

## G5 deliverables: scale and institutional learning

After a real closed loop, expand concurrent **independent** lanes:
- Product/evidence reconnaissance and research, including competing architectures and maintained licensed alternatives.
- Document integrity, storage/lifecycle and migration checks.
- Editor responsiveness, IME/selection, statistics and syntax incrementalism.
- Workspace / SAF / search / favorite and file operations.
- Reader typography, bookmarks, anchors, annotations and Fold layouts.
- Interoperability: PDF layout, DOCX export fidelity, local resources, format compatibility.
- Independent host, Android, physical device and adversarial evaluator pool.
- Release/integration/promotions and Foundry self-optimization.

Implementation concurrency is **adaptive**: start with disjoint slices; increase only when merge conflict rate, error rates and evaluation backlog permit. Scale independent read-only research and test generation much further than shared-source editing. Build/test/inspect existing solutions across many candidates while a single promotion authority maintains coherent app state.

Set up matched mission-family experiments comparing (1) strong single agent, (2) small specialist group, (3) broader research/evaluator swarm. Score accepted functional outcomes, defects found after promotion, human intervention count, recomputation after failure, and end-to-end critical-path time. Track compute spend diagnostically rather than imposing a human-hours budget. Candidate organizational improvements undergo held-out tests, shadow mode and rollback before replacing the incumbent.

## AI-machine compression model

**Not a forecast:** logical lower bound is the depth of true dependencies, not the sum of task durations. More workers reduce the width of independent branches; they cannot eliminate experimental observation, signing authority, device behavior, or causal ordering between two acceptance trials.

For a mission:
`critical_path = source_truth + max(live_worker_route, independent_evaluator_preparation, research) + implementation_selected + integrated_evaluation + release_verification + interruption-and-repeat`.
Resource ceilings and service quotas are real even when a design assumes unlimited compute. Track actual observed service time and critical-path delay from logs; never convert human SWE person-month estimates into AI runtimes.

Completing **this roadmap design** requires one focused analysis/reconciliation pass, not a twelve-month project. Completing the **first real autonomous release** requires actual execution of G0–G4. Completing **the whole institutional moonshot** requires sustained empirical evidence of multiple accepted releases and self-improvement—not a persuasive prompt or theoretical agent count.

## Product-wide exit criteria, not vanity metrics

- Source provenance for every promoted feature and signed artifact; no undefined ownership on shared state.
- Two independent real product missions delivered through Foundry with no new owner prompt between them, including one kill/restart fault.
- End-to-end recoverability verified on the declared scope and independent testing; no silent user-document destruction.
- Meaningful positive performance and UX comparisons with baselines; existing 2.2.12 functionality, themes, files and data preserved.
- Actual Android and applicable Fold7 evidence, with unsupported claims marked NOT RUN.
- Measured improvement of useful throughput versus a matched direct-agent workflow, without hidden extra human supervision.
- Foundry's charter, memory, release control and evaluator can be independently restarted/replaced and still accurately explain the chosen release.

## What to ask Pat, and what not to ask

Do **not** request approval for each routine reversible task, branch, test, experiment, repair, role choice, or implementation detail already inside the product charter. There is no mandatory kickoff questionnaire.

Bring an actual material decision only after researching options and narrowing consequences: change of product direction; revised data-loss or privacy/permission contract; new third-party paid service or spend; consequential irreversible operation; disclosure or movement of signing credentials; real-device installation outside prior authority; an underdetermined design choice where alternatives are comparably credible. Batch device checks into a single owner acceptance session. Pat need not copy and paste handoffs between agents.

## Next machine actions (at start of an authorized real run)

1. Read the latest Foundry realignment and Markdown Desk repo heads and installed-build facts. Confirm existing active operators/worktrees; do not trample them.
2. Update Foundry's obsolete Favorites pilot to a read-only opportunity selection exercise based on code28/current device evidence. Select one verified missing product behavior with an independently created baseline test.
3. Deploy reviewed 0.2 with a schema-compatible fresh home and current live Codex CLI worker, own test runner, source-based candidate promotion and durable receipts. Demonstrate the execution route; do not call a scripted fixture a live model.
4. In parallel, prepare emulator/service/CI and evaluator packets, source manifest, research dossier and non-destructive release test. Acquire exclusive MainActivity writer ownership before any edit there.
5. Implement selected vertical slice; run focused RED→GREEN, whole app build, current-phone host suite and independent Android tests on combined source. Review with separate evidence.
6. Archive source and tests **before** lengthy runs. Release signed verified candidate through private signer, then conduct one deliberate interruption and second autonomous improvement.
7. Only then expand coding concurrency, research tournaments and self-improvement; keep a high standard of evidence. Never stop at a planning checkpoint if eligible real implementation remains and an active authorized worker is genuinely running.

## What this document is not

It is not a new application version, activation of a background service, a grant to spend money, an automated merge, or approval for unattended installation. The separate planning branch preserves the user-facing product code and other workers' ownership. The implementation should update actual authoritative control docs as each gate passes.
