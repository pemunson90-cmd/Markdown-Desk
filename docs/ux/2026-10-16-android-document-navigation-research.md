# Markdown Desk Android document UX research — proposal, 2026-10-16

**Status: RESEARCH / DESIGN PROPOSAL ONLY. Not owner-ratified and not implemented.**
Current user-reported defect: M Workspace is overburdened and not discoverable; prior Favorites and Reading Places menus required too many clicks. User also reports that opening a markdown file via Android's **Open with → Markdown Desk** displays `Untitled.md` whereas opening through the app retains its title. Keep the existing signed-install preservation policy.

## Direct references (actual guidance / shipping apps)
- Android's own layout/navigation patterns distinguish **top-level destinations** from contextual actions: https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns . 3–5 primary destinations may use a compact navigation bar; drawer/rail is better for large sets.
- Android view-based adaptive navigation: https://developer.android.com/develop/ui/views/layout/build-responsive-navigation . Choose via **available width**, not a physical Fold7 model flag.
- Android canonical list-detail patterns: https://developer.android.com/develop/ui/views/layout/canonical-layouts . Folders/files browser + current editor makes a natural compact single-pane vs unfolded two-pane.
- Android width/height classes: https://developer.android.com/develop/ui/views/layout/use-window-size-classes . Compact <600dp; medium 600–839dp; expanded ≥840dp. Keyboard and landscape may constrain height.
- Obsidian official mobile help https://obsidian.md/help/mobile : navigation bar, quick file switcher, tab controls and configurable mobile actions, file sidebar.
- Markor Android open-source reference https://github.com/gsantner/markor : browse files separately from edit/format actions.
- Android OpenableColumns https://developer.android.com/reference/android/provider/OpenableColumns : only `_display_name` and `_size` are standardized for openable content URIs. Supplemental metadata columns are optional.

## Suggested information architecture
- **Navigation/destinations:** Files, Recents, Favorites, file Search, currently open tabs/documents. They answer 'where do I go?'
- **Current-document actions:** Save, ☆ toggle Favorite, Bookmark place, Undo/Redo, Find-in-current, appearance by mode. They answer 'what do I do to this document?'
- **Rare/system work:** Import/Export, PDF/DOCX, Protected history/storage, preferences and help go under descriptive Overflow/Tools sections. Destructive file operations only in document/folder context controls with explicit scope and confirmations.

## Proposed minimum-change layout (do not implement before owner direction)
Folded/compact: Keep the current editor, tabs and Write/Read/Split modes. Replace cryptic 'M' with a labeled **Files** affordance. Files opens a **dedicated browser** with Recent/Folders/Favorites selectors, integrated search, and a clear back-to-editor affordance. Keep one-tap ☆ favorite and contextual bookmark near the editor. Avoid a second permanently occupied bottom row while the IME is open; preserve writing viewport.

Unfolded/expanded: Files/Favorites/Recents/search in a **navigable left pane** or compact rail; document editor to right with existing tabs. No duplicate UI layer for every M action. Posture transitions preserve draft text, cursor, session, and selected folder; back returns to the editor rather than throwing away work.

Navigation flow:
1. Launch → restore last active document/editor with visible name. Files affordance opens the library; tap list item opens normal safe document route.
2. In editor, ☆ toggles favorite with immediate visual state and brief feedback; long-press/overflow remove only shortcut. No extra add-success dialog.
3. Tap Bookmark at the current text cursor to create a named position; Go to Bookmark shows a direct navigation list. Old anonymous 'reading place' data stays readable without forcing two similarly named save actions.
4. File/item long press/context menu offers rename/copy/move only with tested provider access and non-destructive failure behavior.
5. All windows, especially IME showing and Fold width transitions, retain focus and essential controls.

## Filename defect hypothesis / bounded fix
Incoming VIEW/EDIT/SEND URI uses `DocumentIO.metadata(ContentResolver, uri)` then `DocumentTab.displayName=metadata.displayName` via `lambda$openDocument$29`. The old metadata call queried `_display_name`, `_size`, `last_modified`, `flags` all at once and discarded exceptions. Some sharing/MediaStore providers support standard OpenableColumns but reject extended columns, losing real display name. A narrow code change splits standardized filename/size from optional capabilities, preserves existing edit-grant policy and never writes provider contents. Test with strict `content://` provider rejecting extended projection. **This is a hypothesis, not yet reproduced against the owner's specific Samsung My Files provider**; verify external Open With filename separately on Fold7 after an installable update.

## Acceptance boundaries
- Existing session, saved docs, history/favorites/bookmarks survive normal in-place update. Never uninstall or clear data.
- With provider returning `_display_name=Meeting notes.md` for ACTION_VIEW URI, title/tab/recents show exactly that name. If provider refuses optional projection, name still survives.
- On Fold7 folded/unfolded, keyboard and landscape, Files is discoverable, one-tap actions work, dialogs display complete rows, and returning to editor doesn't lose cursor.
- Preserve canonical signer and strictly increment versionCode for each installable test candidate; this proposal does not authorize a new APK release.
