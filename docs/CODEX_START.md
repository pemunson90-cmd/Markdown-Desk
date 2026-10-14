# Codex kickoff — task 1 only

**Goal:** Initialize the live Android application repository from the exact supplied 2.2.5 source and establish green clean-room unsigned builds. **Do not begin feature work yet.**

## Instructions to Codex (paste verbatim if useful)

You are the primary implementation agent for `pemunson90-cmd/Markdown-Desk`. Read `AGENTS.md`, `docs/ROADMAP.md`, `docs/BASELINE_AND_IMPORT.md`, and `docs/RELEASE_GATES.md` before doing anything else.

Work on issue [#1](https://github.com/pemunson90-cmd/Markdown-Desk/issues/1) ONLY. Use the attached `MarkdownDesk-2.2.5-source.zip` as the source of truth. The ZIP has a root folder `MarkdownDesk-2.2.5/` with `apps/`, `shared/`, `verification/`. Inspect it, verify contents and hashes, then import the exact baseline into Git without source-formatting changes.

This is a **public** GitHub repository. Do not publish release private signing keys or credentials, personal notes, recovery archives, or artwork that has not been explicitly cleared for public distribution. Treat assets as an explicit publication gate; if a needed asset is private, do not forge a placeholder and claim parity. Raise the issue and report your safe options. No key generation.

Set up an unsigned build and host-test runner for a fresh runner, pin tool versions, and record reproducible commands. Ensure the included project `apps/MarkdownDesk/build.sh` can use the specified SDK paths; add an explicit non-signing build path rather than fake keystore material. Prove package name `com.example.markdowndesk`, version `2.2.5`/code `18`, minimum SDK `26` and target `35`. Keep the current modes, including the existing Focus/View mode, nine themes and 25 splashes.

Create one PR for the import and toolchain scaffolding with SHA-256 provenance, actually executed checks, outstanding blockers and tests not run. Update `docs/STATUS.md`. Do not ship a new APK, change app behavior, migrate architecture, or start issue #2.

If you cannot access the source archive, STOP and request it rather than reconstructing code.

## Inputs and secrets policy
- 2.2.5 ZIP: supplied by Pat in ChatGPT release conversation/Library; must be attached to the Codex job if Codex cannot access Library.
- Android SDK: Library includes `build-tools_r36.1_linux.zip` and a recovery source archive containing Android API jar + D8; alternatively provision official Android SDK 35+/build-tools 36.1 on the runner.
- Signed APK digest and public certificate fingerprint are in `docs/BASELINE_AND_IMPORT.md`.
- Signing files are private and **not** needed for M0.1.

## Completion report required
- Base commit and PR URL.
- Exact source artifact digest and clean extracted inventory.
- File/asset publication decision and what remains excluded.
- Host test log, unsigned compile log, Android manifest inspection.
- Source diff (should be none beyond explicitly documented build/test scaffolding).
- Failures/not-run conditions; do not describe future tests as passed.
