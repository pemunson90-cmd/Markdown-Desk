# Markdown Desk — Codex engineering contract

## Mission
Build a fast, reliable, offline-first Android Markdown reader/editor and document workspace. Preserve what already works. The approved baseline is **2.2.5 / versionCode 18**, package `com.example.markdowndesk`, minSdk 26, targetSdk 35.

## First action
Read `docs/ROADMAP.md`, `docs/BASELINE_AND_IMPORT.md`, and `docs/RELEASE_GATES.md`. Treat the **actual 2.2.5 source archive** as the authoritative import. The repository may initially contain only planning files; do not reconstruct app classes from descriptions or invent source.

## Non-negotiables
- Native content remains user-owned Markdown/plaintext via Android's Storage Access Framework (SAF). No mandatory account, background sync, telemetry, or cloud requirement.
- **Focus/View mode already exists** and hides much of the chrome. Preserve its behavior and all existing Write/Read/Split modes, tabs, scrolling, orientation, and Fold layouts.
- Preserve all nine themes and 25 splash options; do not lose bundled assets or unexpectedly publish private assets.
- Do not alter, delete, rewrite, or migrate a document without an explicit user action or a thoroughly tested, opt-in policy. Retain byte fidelity for unedited files.
- Canonical release signing key and credentials must **never** enter Git, logs, source archives, issues, PR descriptions, or GitHub Actions. Public certificate fingerprint only is safe. Never generate a replacement release key.
- Every existing public app behavior must be listed as preserved, changed, or deliberately deferred in PR notes. No opportunistic redesigns.
- Release builds must use `com.example.markdowndesk`, strictly increasing versionCode and the original certificate, and pass all release gates. No APK should be presented as ready merely because it compiled.
- Maintain `docs/DECISIONS.md` (append-only decision entries) and `docs/STATUS.md` (current tasks and verified state). Source and Git history beat recollections and plan claims.
- For every feature: write acceptance tests before (or with) implementation. Tests must exercise semantics, not only string-search source guards.
- Prefer focused, bounded changes (one PR per milestone subtask); no giant rewrites. Backwards-compatible migrations and reversible changes first.
- Do not conflate host tests, emulator tests, and actual Fold-device acceptance.

## Work protocol
1. Reconfirm baseline SHA-256s and source inventory before first code change.
2. Establish a reproducible clean-room unsigned build/test command (offline after toolchain setup).
3. Implement approved issue slice on a feature branch; report changed files, risks, metrics, tests actually run, and upgrade effects.
4. Seek architecture review for changes to storage, history, crash recovery, or API boundaries.
5. Merge only after release gates; create a canonical signed APK outside hosted CI with the original private signer.
6. Do not ship a partial or knowingly regressed app; maintain 2.2.5 rollback package.

## Build tooling
The source uses a small non-Gradle Android `build.sh`, `javac`, `aapt2`, D8 and `apksigner`. Do not introduce Gradle without an ADR and a reproducibility benefit. Private signing variables in the existing script are `MARKDOWN_DESK_KEYSTORE`, `MARKDOWN_DESK_KEY_ALIAS`, `MARKDOWN_DESK_STORE_PASSWORD`, `MARKDOWN_DESK_KEY_PASSWORD`; never print values.
