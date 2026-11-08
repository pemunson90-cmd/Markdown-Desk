# Markdown Desk — complete Unified Workspaces integration contract

**Owner authorization:** Finish ALL requested functionality as one integrated upgrade. **Do not produce an APK, staged feature release, test installer, or request device acceptance while requirements are incomplete.** Internal commits, modules and tests are not releases. Repo-only progress reports at meaningful milestones. No routine GitHub Actions.

**Authoritative source:** Latest Unified Workspaces feature branch (post 2026-11-06 owner directive), plus code-37 importer and reader-safety follow-up. Do not rebuild from stale main, replace owner-accepted features, or silently drop the Markdown editor, JSON Inspector or signed-update identity. Existing Read/Inspect/Split Archive layouts remain selectable.

## Arbitrary JSON clarification (owner, 2026-11-07)

JSON is first-class regardless of provider. The Inspector reads arbitrary JSON trees and complete scalar windows without mutating source. Explicit **Index JSON** maps every node to a path/type record using the existing streaming index, full search, annotations, reader and exports. This generic representation has a distinct source identity from conversation interpretation. Known Claude/linear-message and ChatGPT graph formats retain coherent conversation structure; selected ChatGPT branch is first and alternate branches remain separately readable. Unknown packages do not silently become empty conversations. Existing private `claude-archive` storage paths stay unchanged for upgrade compatibility; they are not a product format restriction.

## Definition of done

One installed native Android package, two optional launcher shortcuts to the same app, optional original Archive UI, fully consistent appearance/navigation/commands/files/settings, working source-specific modes, all applicable Markdown reader functionality in JSON/Archive, and no outstanding owner-requested feature gap. Source JSON immutable. No data loss, silent truncation, or misleading success state. Clean local compilation/dependencies/behavioral regressions and repeat 700 MB-class tests before preparing ONE signed APK for phone acceptance.

## Work, in dependency order (internal only — not release stages)

1. **Foundations and identity.** Establish immutable source identities for Markdown/JSON/Claude conversation/message/text selection. Reconcile existing app tabs and archive tabs into one shared session registry with closed/restored/dirty behaviors, without forcibly rearchitecting document editors. Ensure the existing editor save/close guards remain authoritative. Shared shell, workspace routing, Files/Recents/Favorites, command palette and keyboard shortcuts are common services, not separate UI clones. Preserve both launcher entries as shortcuts, and the ability to switch or switch-and-close safely.

2. **Continuous reading.** Replace 12-message page replacement with bounded, bidirectional in-place virtualized scrolling. An empty/short chat and a multi-thousand-message chat must be equally navigable; the 12-message count is an internal fetch size, never a content cap. Stream long message content by stable chunks without silently cutting text, preserve formatting/Unicode, exact selected text, font size, source anchor, scroll position, search hit and Focus transitions. Support jump first/last/message/date/speaker and reverse navigation.

3. **Reader/visual parity.** Reuse the same nine palettes, scaling, families, margins, line and paragraph spacing, Focus, accessibility, search highlighting, in-reader bookmarks/notes/links, keyboard selection, copy/share and non-destructive reading behavior. Archive Read/Inspect/Split have the same chrome and user settings while preserving original Archive presentation as an explicit option. Inspect is still general JSON, not limited to Claude export.

4. **Annotations and links.** One stable record model across Markdown and Archive; interactive highlights, colors/tags, click-to-reopen, notes, edit/delete/history, quote+context+source-ID verification and explicit ambiguous/unresolved states. Links to selected passage, other conversations, Markdown documents and external URLs; backlink navigation and navigation history. Automatic source URLs do not become saved links. Per-chat `.md` notes and master links-only `.md` index are portable, deterministic, staged/synced safely, and recoverable from partial/revoked provider writes.

5. **Unified search.** Search current document/conversation, saved annotation, all indexed archives, allowed Markdown/JSON files/folders and all current sources. Query scope, phrase/speaker/date/tag/notes/links/source filters; sensible snippets, stable result IDs, virtualized exact jump, dedup and back-to-results. Avoid rereading entire 700 MB JSON on each query. Keep search query/navigation semantics consistent with Markdown Desk's existing Find/Replace where applicable; no Replace for immutable source.

6. **Output and interop.** One copy/quote-with-citation/share/print/export interface, preserving Markdown editor capabilities. Complete transcript-to-Markdown/PDF only on explicit request; output includes all messages, metadata and readable source references, never preview caps. Respect the owner-approved DOCX and print safety gates. No unapproved external service or mandatory account.

7. **Fold7 and app lifecycle.** Compact and expanded pane layouts based on available width (including split-screen), central status/navigation bar insets, no clipped buttons, saved selection/scroll/tabs/unsaved changes through Fold rotation and process recreation, correct TalkBack/large fonts/touch targets and keyboard. The 700 MB import continues while the Activity recreates; show real counters, storage headroom, cancel, safe resume/restart and cleanup.

8. **Reliability, dependencies, final acceptance.** Preserve original signer/package, private data, 25 splash assets and all editor functions. Test 700 MB-class archive indexing/reimport, Unicode/emoji overlap, long messages, malformed JSON, FTS4 query correctness, low storage, staging cancellation, SAF denial, changed export and journal backup/recovery. Verify all compile/class references, module dependency graph (no cyclic giant Activity files), signed package alignment and in-place upgrade. **Only then produce an APK** and request the single appropriate Fold7 acceptance run. If a genuinely unavailable external input stops implementation, report precise blockers instead of releasing a partial APK.

## Current first code changes

- Repair a confirmed v2 compact-index reconstruction defect: `parts.overlap_len` is Java UTF-16 units but SQLite `substr()` counts Unicode code points. This silently loses characters after overlap containing emoji. Decode indexed segments with Java UTF-16 semantics (including existing v2 databases) and test surrogate pairs and malformed offsets; no database migration or data erasure.
- Add an independently testable bounded bidirectional viewport model for continuous reading of arbitrary-length conversations, including explicit rejection of stale adjacent-page responses. Connect it to the WebView/reader without synthetic user-visible pagination, with incremental DOM and exact anchors.

## Forbidden shortcuts

- Do not create/release intermediate APKs, declare the project done after isolated tests, drop working Markdown-only features, replace old markup with plain text, add a separate incompatible annotation store, trust a missing source anchor, or use GitHub Actions just to bypass local setup.
- Do not count a synthetic host pass as Fold7 acceptance; do not overstate current work. Don't generate duplicate plans or abandon feature work for release paperwork.