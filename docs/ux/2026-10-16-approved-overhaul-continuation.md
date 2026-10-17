# Markdown Desk — approved UX overhaul continuation

Date: 2026-10-16. **Status:** staged plan; compact Files navigation and RC3 already implemented, responsive expanded pane not yet implemented. Owner approved the overall editor/navigation direction; this document is an implementation map, not a request for reapproval.

## Product target and constraints
Keep the existing Java/Views app, nine themes, Write/Read/Split, Focus, tabs, editing and recovery semantics, and the current-phone signed identity. The editor owns the screen until navigation is requested. Do not invent a second rendering/editor state machine or rewrite SAF/history. Avoid permanent bottom chrome, giant dashboards, and new migration/backup work. Keep exact source bytes, unsaved drafts, cursor, tab/scroll, selected folder and protected recovery unaffected by navigation.

## Verified present implementation
- `MainActivity.buildInterface`: top-row Files/filename/Save/Guide/More, tabs and mode bar; body is one weighted LinearLayout, with mode-specific horizontal toolbars.
- `showFilesNavigator()`: compact dialog offering Open, New, Tabs, Favorites, saved folders, Add folder, and recent documents. Clicks route through existing `launchOpen`, `showTabs`, `showWorkspaceFavorites`, `showWorkspaceFolders`, and `openDocument`.
- `applyResponsiveChrome()`: width-based compact styling; post-RC3 source forces Files one-line, allocates width using measured text, and hides the duplicate guide icon on narrow layouts. More retains the guide.
- `DocumentIO.metadata`: standard `_display_name` queried separately from optional provider fields. Strict-provider regression passed; actual Samsung My Files Open with acceptance remains unverified.
- RC3 code21 was independently signed and checked at its frozen source commit. Post-RC3 UX source is **not** an installable release.

## Next engineering slices

### A. Reusable Files destination without duplicate logic
Extract the *rendering/interaction of file destinations* from `showFilesNavigator` into a reusable view/container builder. Preserve all original action callbacks and file permission behavior. The existing compact dialog must still own its dismiss lifecycle; the expanded host must never dismiss an unrelated AlertDialog. Dynamic recents/favorites/folder badges refresh only when returning to Files or on known changes, rather than polling or querying provider trees on the UI thread. Use existing asynchronous workspace generation/cancellation mechanisms. Add an Activity test for compact Files -> recent/favorite/open -> editor, dismiss/Back and no draft/cursor loss.

### B. Width-responsive expandable left navigation
At **expanded usable window width (roughly >=840dp)**, make the Files header action toggle a collapsible left browser beside the existing editor body. Build the shared shell once; retain the exact editor and WebView instances across show/hide and configuration changes. At compact/medium widths, use the existing Files dialog; do not force a rail into the keyboard viewport or move permanent formatting rows. In Focus mode, hide the rail without modifying its logical selection. Use width, not a Fold7 model check. Show concise document/folder rows with clear selection; a selected file opens through the existing safe document path.

### C. Adaptive browser depth and search
Let Files expose **Recents, Favorites, Folders, Search** as obvious destinations rather than repeated menu hopping. Reuse existing SAF tree grant, async folder reader and bounded literal-search services; no whole-storage scanner. Search scope must be explicit (selected permitted folder). Keep separate safe removal of favorites (shortcut only) from future file-system operations. One click on a recent/favorite file opens the document; a folder takes the user into the authorized folder browser. Return/Back never closes an unsaved tab.

### D. Visual/behavior polish
Keep title ellipsized, Save status visible, Files at least 48dp high and single-line with enlarged text; do not allow overflow controls to cover the title. Honor all nine themes, contrast and 200% font settings. Test portrait folded/landscape, expanded unfolded, split, keyboard visible, Focus, TalkBack labels, and tabs > 1. No glossy effects or extra dashboard screen.

## Acceptance evidence (do not conflate)
1. **Source/host**: focused extracted Activity/UI regressions, file-provider/SAF fixtures, normal save/recovery/history tests; real behavior tests rather than only static token checks.
2. **Emulator**: source-matched install/launch/rotate/relaunch, Files navigation, recent file open, no data/cursor loss, at least one >=840dp simulated width and keyboard shown/hidden.
3. **Owner Fold7**: closed/open/rotation/keyboard screenshot and hierarchy; Files remains single-line; external Samsung My Files Open with shows real filename; folders/search/Favorites and DOCX flow verified on a throwaway file.
4. **Release**: broad current-phone regression, secure code review, original certificate + versionCode strictly above the installed version, private signing and in-place upgrade/readback. Never uninstall or clear data.

## Boundaries and parallel development
RC3 is frozen at `1f5c65a4f8fa8ed410d3eb483f7b842989f17d5c`. This source continuation lives on `rescue/md-unsigned-build-proof-20261016`; do not modify the separate `codex/operation-data-capture` draft PR24. Portable app-state export/import is deferred. Safe rename/move/duplicate, retention UI, reader annotations, editor diffs and M2–M7 still need their own guarded implementation/tests after navigation. Do not claim their completion from this plan.
