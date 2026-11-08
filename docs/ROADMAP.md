# Markdown Desk Professional — product roadmap (2026-10-13)

## Product thesis
A **local-first, fast, accessible Markdown reading, writing, research and revision workstation** for Android phones and foldables. Goal: displace Word/Docs for Pat's ordinary solo text tasks without reproducing an office suite's latency or complexity.

## Existing capabilities — preserve, not rebuild
Native editing; Markdown rendered Reading mode; Write/Read/Split layouts; **existing Focus/View mode hides most UI**; draggable wide Split divider; tabbed documents; outline and command palette; in-file find/replace including regex; reading/source font sizes; statistics and typewriter mode; Android SAF open/save, external-change detection and read-back verification; session/recovery; HTML/share/PDF print; nine color themes; 25 launch splash assets; landscape/Fold handling and centered eye/lock controls. Confirm details from imported source and phone before changing each feature.

## Current owner scope — 2026-10-15

[PR #24 owner decision](https://github.com/pemunson90-cmd/Markdown-Desk/pull/24#issuecomment-6204593523)
defers cross-installation private app-state export/import from the current-phone
release. Preserve its implemented foundations for a later optional Export App Data /
Import App Data enhancement. It does not block local history or dependency-ready
M2–M7 work. Local document safety, recovery, all other features and final release
gates remain required. A copied Markdown file contains current text, not private
version history; clearing/uninstalling app storage can lose that history.

## Scope and milestones (order matters)
### M0 — Reproducible foundation and audit (no feature promises)
- Import exact 2.2.5 source into Git with public/privacy audit; pin tooling, bootstrap clean unsigned build, host CI, independent test fixtures, baseline package/certificate proof.
- Inventory all UI modes and document lifecycle states, plus benchmark 10k/100k/500k words and largest supported document, folded/unfolded, with cold/open/typing/search/switch/render/save timings.
- Fix verified global theme contrast errors and accessibility labels, retain existing Focus/View mode; add screenshot-based visual regression where runner permits.
- Split `MainActivity` only when covered by regression tests; prioritize storage/palette/dialog boundaries and unidirectional state refresh.
- Exit: repeatable build/test from clean checkout, source and assets identical to baseline; no loss of focus, tabs, preferences, themes or data.

### M1 — Data safety and real history
- Immutable document snapshots independent of crash-recovery drafts. Automatic policy (configurable), named checkpoints, per-document history list, diff preview, explicit restore-to-new-copy before replace.
- Quota/retention with safe pruning; conflict recovery and simulated failed, interrupted, and external-provider writes.
- Preserve app-private session/preferences/history and test non-destructive local upgrade/reopening. Cross-installation private-state export/import is a deferred optional enhancement under the owner decision above; its existing code is preserved.
- Exit: injection tests prove durable byte-exact recoverability of the observed original and submitted edit before destructive provider writes, fail-closed protection, and explicit restore to a new copy (ADR-001). Generic providers may truncate the source URI on failure; universal atomic URI preservation is not promised. Lost-work rehearsal and successful restore across process death remain required.

### M2 — Performance and architecture
- Incremental stats, affected-range syntax processing and throttled/debounced preview updates; avoid all-text scans on each keystroke where possible.
- Profile memory, GC, scroll, cursor accuracy, input latency, and large-file limits. Document size policy visible to user.
- Exit: measurable regression threshold: typing stays responsive on target fixtures; compare against M0 baseline, publish actual device and host measurements rather than invented milliseconds.

### M3 — Workspace
- Scoped SAF folder grants with a navigation tree, recents, pinned/favorite items, folder/project browsing and targeted cross-file full-text search.
- Safe rename/duplicate/move with provider capabilities and conflict/revocation handling; no indiscriminate broad-storage permission.
- Exit: recursive authorized workspace scan/search, unplugged provider/revoked grant and non-destructive conflict tests.

### M4 — Reader Pro
- Adjustable families/line-height/paragraph gaps/measure/margins; saved per-document reading location, headings/bookmarks, text selection/highlights/annotations.
- Refine existing Focus/View mode and Fold behavior rather than inventing a second immersive mode.
- Exit: stable anchor mapping after edits/reflow and zero UI obstruction in folded/unfolded/split scenarios.

### M5 — Editor Pro
- Predictable explicit Undo/Redo UI and persisted drafts; collapsible headings; source-preview bidirectional navigation; improved CommonMark/GFM compatibility and accessibility.
- Comment/annotation layer and side-by-side diffs; proposed edits accept/reject, without automatic rewriting.
- Exit: roundtrip/fuzz fixtures for escaped Markdown, nested lists, links, tables, code, Unicode, and selection/IME.

### M6 — Interoperability & packaging
- Better paged PDF layout and export; DOCX **export** using a fidelity matrix, then bounded import feasibility before promising DOCX round-trip.
- Secure local images/relative links; optional footnotes and extended Markdown features.
- Exit: golden export fixtures (headings/tables/lists/Unicode/images) and byte-stable native Markdown round-trips.

### M7 — Release hardening
- Accessibility audit (contrast, TalkBack, touch targets, font scaling), orientation/Fold and Bluetooth keyboard matrix, storage/providers, low memory, process death, battery.
- Docs, migration/rollback playbook, provenance, release notes and canonical signed update.
- Exit: full release checklist and actual foldable owner acceptance.

## Sequencing/ship policy
M0 must precede parallel M1/M2 work. After local data safety and affected benchmarks pass, M3 and M4 may proceed on nonoverlapping file ownership; M5/M6 only after their local foundations. Portable private-state capture/funding/import/adoption is not a prerequisite for these current-phone features. Do not release milestone placeholders as finished products. Retain 2.2.5 as an installable fallback; avoid destructive downgrade paths.

## Explicitly deferred
Mandatory accounts, realtime collaboration, cloud sync, embedded assistant, full Word-layout clone, automatic file moves, broad storage access, and plugin platform. Revisit only if a specific workflow justifies them.

## Decision discipline
Propose architecture changes as ADRs under `docs/decisions/`. A milestone scope change needs written rationale, tests, compatibility assessment and explicit approval. Claims from history files or AI summaries are not implementation evidence.
