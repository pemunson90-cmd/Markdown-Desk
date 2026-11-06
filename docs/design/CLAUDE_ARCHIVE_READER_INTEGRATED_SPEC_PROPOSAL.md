# Claude Archive Reader & Research Index — integrated product specification (proposal)

**Status:** Proposed for owner review, not authorized implementation or release sign-off.
**Date:** November 5, 2026. **Repository:** pemunson90-cmd/Markdown-Desk.
**Source baseline:** current code33 JSON recovery branch, `fix/json-recovery-local-20261105`. Keep PR #36 and historic reader behavior; do not merge blindly with stale main.
**Delivery intent:** one integrated owner-facing candidate after bounded internal build/test milestones, not successive incomplete APKs. No hosted GitHub Actions for ordinary work; build/test locally with the canonical signing identity isolated in the owner's Library.

## Product promise

The archive reader is a first-class, read-only way to search and read **every available message** from imported Claude exports, annotate selected text, create explicit source and cross-reference links, and maintain durable **Markdown indexes per chat and across all user-created links**. Reading, searching, highlighting and bookmarking never require conversion. **Export full chat as Markdown** is an explicit optional archival action, not a viewing prerequisite. Original Claude JSON is never modified.

This is an archive reader and research notebook, not another editable text editor or generic JSON tree. Keep the generic JSON inspector for schema debugging.

## Confirmed inherited Markdown Desk capabilities

Source inspected on current code33 development branch:
- Existing themes: Paper, Midnight, Sepia, Forest, Bubblegum, Amethyst, Black Rose, Ocean, Arcade (`UiTheme`).
- Reading font size and A−/A+ controls, serif/sans/monospace font family, line/paragraph spacing, margins, reading measure, theme picker (`ReaderTypography` and MainActivity).
- Focus/View reduces interface chrome without making a second editor; reader preview uses `PreviewWebView` and `MarkdownRenderer`.
- Document outline, favorites, reader places, recent documents, tappable Markdown links, bookmarks and preview-position support.
- Existing annotation/bookmark flow has a UX limitation: `MainActivity.saveReaderAnchor()` currently requires Write/Split to capture its source-offset anchor. The archive reader must support annotations directly in Read/Focus. It should not copy that requirement.
- Preserve all current Markdown editing, save/recovery, protected history, 12 tabs, responsive Fold7 layout, all 25 splash assets and external file safety invariants.

Parity contract: all **reader-facing** controls available to Markdown documents should be usable while viewing chats, through shared preferences and shared reader rendering where technically sensible. No edit mode for imported Claude JSON.

## UX structure

**Archive Home:** Search; Continue Reading; Saved Links; All Conversations; Saved/Bookmarked; source/import management. Archive home is separate from the existing Files hub but reachable through it and the command palette.

**All Conversations:** list (title, created, last update, message count when known, source, saved-state), instant sort/date filters and searchable title; tap opens full conversation. No 100-item *archive* browsing cap.

**Reader:** speaker and date labels; chat header; streaming message content in chronological order; jump to top/latest/date/message; in-chat outline, in-chat search; find next/previous; follow links; text selection; A−/A+; theme/typography; bookmarks; notes and highlights; direct link creation; annotation list; optional message compression/collapse without dropping source content. Every message is reachable; no 200-message, 24k-character, or whole-preview cap. Long messages/blocks load in bounded chunks without truncation. Distinguish actual missing or unsupported content from intentionally collapsed visuals.

**Focus:** true distraction-free reading with full-screen option (hide app chrome with a recoverable gesture or compact affordance, respect cutouts/nav bar, restore system controls). Preserve the same font and reading layout; entering/leaving Focus must preserve place and selection. Consider "book-like" transcript layout and "chat bubble" layout as two *views of the same messages*, not two storage or search implementations.

**Annotations:** select phrase/message -> Highlight, Note, Link to passage, Cross-link, Copy quote with citation. An annotation can have free-text note, tags, optional link label/destination, created/edited timestamps and source quote context. User notes and selected source text are visually distinct. The currently selected text must remain selected while the action sheet opens.

**Fold:** compact phone uses a minimal reader and a collapsible "Notes / Links / Outline" drawer. Unfolded mode can keep reader and notebook side-by-side. Rotation/fold/unfold, background and process-recreation must restore the same message, character anchor and view mode.

## Formal link model and semantics

Support three explicit actions (all qualify as **user-created links**):
1. **Link to source**: a link to a selected message/phrase in the current conversation.
2. **Cross-reference**: link selected phrase or note to a specific passage in another imported conversation (and backlink).
3. **Attach link**: associate a selected passage/note with a user-supplied external URL or local document.

All links use one stable record identity, unique `link_id`, source `archive_id/conversation_id/message_id`, optional selected phrase/offset, optional destination, user label, note/tags and timestamps. Annotations without links are **not** silently included in the master links-only index. Automatically detected URLs in Claude's content are navigable but are **not** indexed as user-created links without an explicit save action.

A source deep link can use a registered, versioned app URI such as
`markdowndesk://archive/v1/<archive-id>/<conversation-id>/<message-id>?link=<link-id>`.
Do not claim that this URI works in every external Markdown viewer. Companion Markdown index rows include *human-readable context and IDs* even without the app installed. Optional Markdown full-chat exports can include stable heading/block anchors so links can point to an exported file when one exists.

**Anchor stability:** Prefer original Claude conversation and message IDs. For selected text capture exact quote, local start/end offsets, a short prefix/suffix, a normalized-text digest, and source archive identity/revision. When reimporting, resolve by ID first, then verified quote/context. Never silently retarget to a merely similar phrase; mark anchors "needs review" with original quoted evidence when ambiguous. Preserve timestamps in their source timezone/offset when present; display local timezone as a preference.

## Durable Markdown per-chat and master indexes

The user selects a companion notes folder by SAF `ACTION_OPEN_DOCUMENT_TREE` when enabling durable annotations. **Never write into the Claude export.** The per-chat Markdown file is the durable portable annotation/links artifact; the SQLite catalogue/search index is derived, rebuildable data. Keep stable machine IDs in modest front matter or HTML comments alongside ordinary readable Markdown; preserve unknown user prose when possible.

Proposed files:
`Claude Archive/Chat Indexes/<sanitized-chat-title>--<chat-id>.md`
`Claude Archive/MASTER-LINKS.md`
`Claude Archive/INDEX-STATUS.md` (optional human-readable synchronization/rebuild status).

**Per-chat Markdown** groups Notes, Highlights, Saved Links, Related Chats and last-read position if enabled. Each link entry includes created date, original message date when known, label, quote excerpt, attached note, source deep link and external/cross destination if present. A chat may have zero links but notes/highlights.

**Master Markdown** includes **only links explicitly created/saved by the user**, with chat title, message timestamp/date, date link created, label, source excerpt and clickable return link. Default newest-created-first; include clear grouping by chat and optional chronological view. No automatic dump of every hyperlink found in the source. Every entry has one stable `link_id`. It is a generated view of per-chat records, not independently editable competing authority.

Example structure:

```md
# Saved Links — Claude Archive
_Updated 2026-11-05 20:00 America/New_York_

- **2026-11-05 saved** · **2026-08-17 message** · **Character continuity notes**
  [Aleksei's second introduction](markdowndesk://archive/v1/archive-id/chat-id/message-id?link=link-id)
  — "The decision was already made when he reached the door."
  — Tags: #NSTL #continuity
```

**Write guarantees:** Before replacing any sidecar, preserve the previous verifiable copy and use a durable journal/staging + recovery step. SAF providers do not universally support atomic rename or truncation safety: no false "Saved" message if a stage, rename, backup or master materialization failed. Write the per-chat canonical record, then refresh derived master/cache deterministically; resume incomplete projection on restart. Source conversation bytes never opened for writing. If the folder is unavailable, keep explicitly labeled unsynced local changes and offer retry/relink/export; never discard. If the user manually edits Markdown index externally, detect version changes and reconcile or preserve both versions rather than overwriting.

The app should provide explicit backup of all annotation sidecars; warn that app-private metadata/cache alone would be lost on uninstall/clear-data. A chat remains readable if notes folder is unavailable.

## Search and import

Import one or multiple `conversations.json` exports using persisted SAF access when available. Support temporary picker sources with explicit copy-to-local or reselect semantics; do not silently promise persistence from `ACTION_GET_CONTENT`. Detect supported schema by content/field shape, not only filename; generic unknown JSON remains in raw explorer. Preserve source IDs, exact source fields and content order; report unsupported blocks/attachments as metadata or accessible references rather than inventing text.

Build a **single-pass, cancellable and resumable local catalogue**: conversation title, timestamps, message ID, speaker, content blocks and text search segments. Use bounded parsing/memory, disk-backed message chunks, checkpoints and verified source identity, not repeated O(total archive) rescans for every list page. The raw JSON remains the source of provenance, and locally materialized message content/index is cache, not a substitute for the raw file's fidelity. Provide index progress, errors, storage footprint, pause/resume, reindex, remove source (without deleting original), and cache-clear controls.

Prefer platform SQLite full-text search (**FTS5 if supported by the actual device build; FTS4 fallback**), ranking/snippets, phrase search, and structured relational metadata for message IDs, timestamps, source provenance and annotations. Verify support in local environment first rather than assuming an extension is shipped. Search spans all indexed messages, titles, user notes and user-created links; distinguish searched scope and show exact highlighted match and deep jump. Filters: specific archive/conversation, speaker, date range, tags, bookmarked/annotated, and project if source metadata exists or user assigns one. Sort by relevance or chronology. Keep user query syntax approachable; optional expert search operators are a later addition. Search indexing never silently truncates messages or drops unknown fields that should be preserved in the source view.

Separate **Find in conversation** (with next/previous and counts) from **Search all archives**. Offer "search within this conversation" from global result context.

Source updates/reexports: avoid duplicate chats by stable IDs/content fingerprints; detect changes and retain old annotation identities. If a message disappears or moves, preserve orphaned annotation text and mark link unresolved. No bulk deletion upon reindex.

## Reader and rendering implementation

**Reuse, don't fork:** Extract a shared reader appearance controller and safe Markdown content renderer from current Markdown Desk `ReaderTypography`, `PreviewWebView`, `MarkdownRenderer` and existing Focus/outline/navigation code. Both Markdown preview and archive reader should update when user changes shared reading settings. An archive message may contain Markdown, code fences, tables, links, footnotes and quoted multi-line text. Treat markup as untrusted; escape/sanitize any HTML, block remote loads and arbitrary scripting, and dispatch safe links to Android.

Use a virtualized window of visible messages with small overscan; if one message is huge, paginate its render blocks without imposing truncation. Message IDs/offsets remain attached to rendered spans for selection and jump-to-hit. Preserve scroll anchor across incremental render, font/theme changes and configuration changes. Never render an entire enormous archive as one WebView document.

Make accessible speaker labels and timestamps, proper touch/selection targets, TalkBack semantics, contrast across nine themes, color-independent highlighting and keyboard navigation.

## Additional high-value ideas

**Default for first release:**
- Continue reading / true last position per chat, bookmarks and history.
- Navigation timeline or scrubber of messages/dates; jump to first/last, speaker and known heading; "back to previous location" stack.
- Custom highlight tags, saved searches and project collections.
- Copy with provenance: selected quote + chat title + message date + source deep link, in plain text or Markdown.
- Backlinks: from an annotated passage, show notes and other chats that link to it.
- Toggling annotation markings without hiding text.
- Clearly distinguish the speaker's original text, assistant message, tool metadata and your own notes; never flatten them together or pass notes off as original testimony.

**Later if cost justified, not a condition for first full release:**
- Read aloud using user-selected system TTS (engine may require network depending on device).
- Unfolded side-by-side comparison of two conversations.
- Revision comparison across repeated exports (new/deleted/changed messages), saved diff links.
- Graph of user-created cross-links, topic clustering, optional private local semantic search, automatic categorization (must remain optional and provenance-labeled).
- Print/chat-sharing, other AI export adapters after Claude schema stabilizes.

Do **not** bundle AI summarization, automatic unsolicited linking, cloud upload, remote analytics or full transcript conversion into ordinary reading.

## Technical contracts and safety gates

Model entities: `ArchiveSource`, `Conversation`, `Message`, `ContentBlock`, `MessageAnchor`, `Annotation`, `UserLink`, `ReadingPosition`, `IndexProjection`. Document exact persistence and revision behavior. A user-created link is one record rendered into both per-chat and master views; avoid contradictory independent copies.

One source parsing layer serves reading, search, indexes, deep links and export. Reader UI does not parse raw JSON independently. Avoid giant edits to 6k+-line MainActivity: use new isolated archive modules, with small integration surfaces.

**Preservation gates:** all existing Markdown Desk features, themes, 25 splashes, original package/signing identity, in-place upgrade and previously working JSON access retained. Code33 owner-confirmed ability to open JSON is the starting point; do not regress to the code32 crashing path or reintroduce speculative custom window insets.

**Functional acceptance (run before owner build):**
1. Load nested arrays, mixed message blocks, escape sequences, Unicode/emoji, fenced code, links, empty messages, invalid JSON and multi-gigabyte synthetic fixtures without UI OOM or silent content clipping.
2. Read the exact last message of >200-message conversations and the last characters of >24k-character messages. Confirm no omitted content; display explicit placeholders only for genuinely non-text blocks.
3. Compare theme/typography/Focus parity to current Markdown Reader in all nine palettes, compact/unfolded/landscape/large-text/TalkBack. Maintain selected passage and scroll through rotation/fold.
4. Add/edit/delete notes, highlights and each link type; confirm exact source anchor, backlinks, date attribution and no accidental source-JSON write.
5. Automatically generate per-chat Markdown and master **links-only** Markdown from same records; compare generated output with truth, verify one entry per link, deterministic order and no phantom URLs. Simulate mid-write failure, out of storage, revoked SAF folder, external edits, app process death and reindex.
6. Search every message including last conversation, exact phrase, filter combinations and notes. Deep-link into off-screen/virtualized message and return to originating search state.
7. Import same archive twice, changed export, renamed source, missing source, duplicate titles, missing timestamps, malformed/missing IDs. Reattach safe anchors or visibly mark uncertain ones.
8. Preserve and exercise existing Markdown editing, reader, bookmarks, tab/session, favorites, protected history, DOCX-related flows, external link behavior and prior JSON Explorer functionality.
9. No personal archive, private key or signed release in public GitHub/CI. Verify local unsigned APK, isolated original-signer v2/v3 signature, zipalign, version increment and in-place data continuity.

## Engineering delivery as **one coordinated release**

- **A. Architecture/schema gate:** inspect exact current accepted source and real *redacted* Claude schema fixture; contract-test importer, anchor model, immutable source boundaries and index Markdown format before UI integration. Decide file permissions/storage up front.
- **B. Offline core:** one-pass ingestion and persistent catalogue, bounded message access, search, links/annotations, source revision and deterministic Markdown projections. Functional tests and recovery drills locally.
- **C. Shared reader UI:** same fonts/themes/Focus/link rendering; message virtualization and selection; chat library, global/in-chat search, annotation tools and Fold navigation. Test representative layouts.
- **D. Integrated acceptance:** full-reader parity matrix, large synthetic archives, fault injection, exact original signer, one candidate APK for owner Fold7 validation. Do not repeatedly publish partial per-feature APKs.
- **E. Owner acceptance & merge:** one focused real-device sweep; fix only observed defects locally and re-run affected regressions. Keep draft until accepted; then reconcile branch/PR/docs. No silent feature deletion, no auto-merge into stale main.

## Research influences and provenance

- Readwise Reader: inline selection/highlights, notebook, tags and contextual return navigation (official docs).
- Zotero: annotation-to-note links that navigate back to original source context (official PDF Reader docs).
- Obsidian: readable Markdown links and stable block references; app-specific block links are not universal across Markdown viewers (official help).
- W3C Web Annotation: exact text + prefix/suffix contextual selection and positional anchors, not just fragile character offsets.
- SQLite FTS5: ranked full-text queries, snippets and derived indexes; platform availability must be confirmed.
All specific Claude-export schema assumptions remain provisional until validated against the actual import schema; no personal exports are required in a public repo.

## Decisions to ratify before coding
1. Does "link" mean **source bookmarks, external URL associations and cross-chat links** (recommended as one explicit model), or a narrower subset?
2. Should per-chat Markdown index files be canonical and portable, with SQLite merely derived (recommended), or should the app database be canonical with Markdown as generated backup?
3. Keep shared reader typography/theming global and allow optional per-chat overrides later (recommended).
4. Scope first integrated release to offline reader/search/annotations/link indexes, with graph/AI/TTS/split-compare deferred (recommended).

Do not label this proposal as approved canon or completed code until owner ratifies it.
