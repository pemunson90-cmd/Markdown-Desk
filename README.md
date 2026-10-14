# Markdown Desk

A local-first Markdown writing and reading app for Android, phones and foldables.

**Current installed release reference:** 2.2.5 (`com.example.markdowndesk`, versionCode 18). **Professional Edition is a roadmap**, not an existing release.

> Exact 2.2.5 source is imported on the baseline branch with verified unsigned builds and host checks. [PR #9](https://github.com/pemunson90-cmd/Markdown-Desk/pull/9) remains draft pending remaining gates; no new release or device acceptance is claimed. See [status](docs/STATUS.md) and [build commands](docs/BUILD.md).

## For Codex
1. Read [AGENTS.md](AGENTS.md) and [the roadmap](docs/ROADMAP.md).
2. Read [exact baseline import protocol](docs/BASELINE_AND_IMPORT.md) and [release gates](docs/RELEASE_GATES.md).
3. Begin with [issue #1: exact baseline import and unsigned build](https://github.com/pemunson90-cmd/Markdown-Desk/issues/1). Then tackle [tests](https://github.com/pemunson90-cmd/Markdown-Desk/issues/2), [performance](https://github.com/pemunson90-cmd/Markdown-Desk/issues/3), and [theme/accessibility](https://github.com/pemunson90-cmd/Markdown-Desk/issues/4) in bounded changes.
4. Record decisions in [the decision ledger](docs/DECISIONS.md) and verified progress in [STATUS](docs/STATUS.md).

## Architecture priorities
Preserve existing Write/Read/Split and **Focus/View** modes, tab behavior, quick local SAF documents, nine color themes, 25 splash options and original canonical signing identity. Add safe version history, workspace navigation, performance and professional reading/revision features through tested milestones.

## Public repository warning
Private release-signing keys, credentials, personal document content and recovery archives do not belong in GitHub or CI. Art/assets require an explicit publication review before source import. Signing and APK release happen only with the authorized private release identity.
