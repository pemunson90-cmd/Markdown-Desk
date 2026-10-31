# Moonshot execution record — Foundry × Markdown Desk

**2026-10-31.** Actual execution, not a hypothetical work plan.

## Evidence frozen this session

- Reviewed Foundry 0.2.0 zipapp restored from the owner's private Library artifact. Review SHA-256: `6cfc75e425d481f2d1c69f96fe426553ac8232bf9c83d82af7708d7d12e5c26b`.
- Exact Markdown Desk code27 complete-history Git bundle restored locally. Enrolled checkout HEAD `6d6be39ce15b3facf2ac92bd6685777ee4e6530f`; this is a CI bootstrap commit whose *application source* is unchanged from code27 source `877facadce54247f38aa29d8b7c1cb5f055f813b`. The source is **not** the newer code28 candidate, which remains draft PR #31.
- Foundry state initialized in a fresh schema-4 home, `markdown-desk` project enrolled with roadmap source and charter, `audit` result `{"ok":true,"errors":[]}`.
- Locally reran the real Java `verification/downloads_browser/run.sh`: **19 conditions PASS**.
- Locally reran the real Java `verification/workspace_favorites/run.sh`: **107 assertions PASS**, using the existing pinned Android JSON jar from the verified GitHub Actions build-input archive.
- Created a *bounded scripted integration probe*, explicitly `scripted=true`, not an AI/model run. It executed via Foundry worker on the real code27 repository and wrote only `docs/moonshot/FOUNDRY_SOURCE_PROBE.json` in an isolated candidate clone. Independent operator-owned acceptance checked exact baseline Git identity and Android manifest. Foundry mission `be550a65a6a64be89ad484ec48a9068b` reached `released`; candidate `f1255488aa14e394ef6662954d6968d42412bdfd`; artifact digest `a8736b646f70a8a29b66e2bc0eec693ee4b0c1cbf9ff2235b3ec840a12bd4d65`.
- Foundry `audit` passed again after release. New Foundry state backup `MarkdownDesk-Foundry-G1-probe-backup.zip`, SHA-256 `f81c46e82439aeb6720758fedbfd4a94383455be5ad1e4dc9852355d1d60235e` (7,658,218 bytes), passed `backup verify`. This archive includes runtime state and source evidence; it is not an Android release.
- The local test checkout and the Foundry state home are sandbox-local. The backup is a preserved conversation attachment, not a persistent service on the owner's Ubuntu WSL2 host. Paths embedded in the records refer to this sandbox and must be reconciled before activation elsewhere.

## What this proves and does not prove

**Proved:** Foundry 0.2 can register and audit an actual Markdown Desk repo, dispatch an isolated deterministic worker, independently evaluate its source change, retain release evidence, and produce a verified restorable state archive.

**Not proved:** live AI implementation against Markdown Desk; access to the owner's WSL2 Ubuntu host from this chat; automatic restart/supervisor deployment; a user-visible new feature or a signed APK through Foundry; physical Fold7 acceptance; a completed G1–G4 autonomous campaign. The October 28 private integration report independently documented a Codex-authenticated *demo* in Ubuntu at `/home/archi/foundry/integration-codex-20261028/`, but that demonstration did not enroll Markdown Desk.

## Critical-path continuation

1. Run Foundry **on the actual WSL2 Ubuntu host**, using the existing native authenticated `/home/archi/.local/bin/codex` and the reviewed immutable 0.2 zipapp, in a fresh home and isolated worktree; verify that the Codex CLI remains authenticated.
2. Enroll actual current Markdown Desk candidate `911a439282a426fbe56b1192935a5fc8a311003c` (code28, versionCode28); acknowledge that owner device acceptance of this candidate remains unknown.
3. Commission a **bounded real coding agent task** with independent operator-owned acceptance and retain the source result *before* running broader tests. Prove in Foundry's execution evidence `scripted=false` and an actual Codex completion event; a deterministic probe cannot substitute.
4. Build and independently verify the current product using existing SDK37/JDK17 toolchain; preserve safe save, Recents, Favorites, native DocumentsUI, nine themes and signer. Choose and measure a genuinely missing product capability, never reimplement the already-working Favorites pilot.
5. Demonstrate interruption/restart and a second autonomously selected real user-visible improvement, then expand implementation workers under distinct ownership.

**Hard execution limitation:** This conversation's shell is a separate sandbox and cannot remotely execute commands on the owner's Linux WSL2 host. It also does not carry the owner's ChatGPT Codex CLI login. Therefore no unattended user-host process has been started from this chat. This is the remaining *specific connection boundary*, not a claim that credentials or the existing Foundry integration do not exist.

## Source pointers

- Markdown Desk roadmap: `docs/ROADMAP.md`; deployment requirements: `docs/RELEASE_GATES.md`; source/evidence manifest: `docs/moonshot/PRODUCT_MANIFEST.json`.
- Foundry: `pemunson90-cmd/PNB2026` branch `foundry/standalone-20261027`, `docs/ACTIVE_PLAN.md`, `docs/OPERATIONS.md`.
- Owner private integration report: `2026-10-28_codex_foundry-live-integration.md`.
