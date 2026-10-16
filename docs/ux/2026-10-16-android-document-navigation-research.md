# Markdown Desk Android document UX research — proposal, 2026-10-16

**Status: OWNER-APPROVED STAGED NAVIGATION.** First compact Files-hub implementation is committed on the isolated rescue branch; unfolded rail and broader workspace redesign remain unimplemented. Signed owner RC3 candidate is under verification.
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

## Minimum-change layout — owner-approved direction, staged implementation
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

## Validated research and staged UI work

Sources checked October 16, 2026:

- Android layout/navigation patterns: https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns . Separate **destinations** from **actions**. Top app bars carry contextual actions; overflow holds infrequent commands.
- Android Views responsive navigation: https://developer.android.com/develop/ui/views/layout/build-responsive-navigation and window size classes: https://developer.android.com/develop/ui/views/layout/use-window-size-classes . Design against available window width, not Fold7 hardware posture. Compact under 600dp, medium 600-839dp, expanded 840dp and up; short landscape height matters too.
- Obsidian mobile: https://obsidian.md/help/mobile . Separate quick switcher/navigation from the editing toolbar; offer custom short-reach document actions rather than hiding everything in a single menu.
- Markor: https://github.com/gsantner/markor . Offline-first files/notebook navigation and editing functions remain distinct.
- Android external URI contracts: https://developer.android.com/reference/android/provider/OpenableColumns and https://developer.android.com/guide/components/intents-filters . Provider-supplied display name is authoritative where available; extra metadata may be absent.
- Android accessibility: https://developer.android.com/design/ui/mobile/guides/foundations/accessibility . Minimum 48dp targets, icon content descriptions, usable IME and screen reader paths.

### Minimal-release UI redesign sequence (stage 1 committed; later stages still gated)

1. Rename the top-left unexplained M affordance to a discoverable **Files** label/icon, and ensure >=48dp hit bounds. Keep title/tabs/Save visible on compact width.
2. Files opens a dedicated, compact **Recent / Folders / Favorites** chooser with search and a plain Back to editor. Existing safe SAF/open-document routes and tab/source identities remain unchanged. These are places to navigate, not editing actions.
3. Keep one-tap Save, Favorite toggle, Bookmark, Find, Undo/Redo close to the active editor or mode-specific toolbar, with a selected-state star and brief nonblocking confirmation. Named bookmark rows jump directly to verified text, with a separate remove path. Preserve old anonymous saved reading places as readable legacy data.
4. Group rare commands under **More → Export, History/recovery, Protected storage, Appearance, Help**. Destructive operations demand explicit scope, capabilities and confirmation.
5. For larger available widths, use a left Files pane or rail adjacent to the same editor. Do not infer fold state from hardware; preserve current cursor, selected tab, dirty draft and responsive keyboard space.

Acceptance test matrix: external Open with → title and unchanged bytes; Files→recent/folder/favorite→document→Back; compact keyboard and split viewport; folded/unfolded and landscape; large text and TalkBack; navigation/accessibility targets and theme contrast; in-place signed upgrade preserving history.

### Filename bug: evidence and remaining uncertainty

The incoming ACTION_VIEW/EDIT path calls DocumentIO.metadata() before assigning DocumentTab.displayName. Previously a single provider query combined standard OpenableColumns and optional fields. The source at commit 3f85227b24c7d87694419eef4e5d9308e62a4434 separated standard filename/size inspection from optional capabilities; a strict fake provider test rejects optional columns but retains the original title and document bytes. Targeted source/build tests PASS at https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/38957658363 .

The exact Samsung My Files provider/intent remains untested on Fold7 after this fix. Treat the user-reported Untitled.md behavior as unresolved until the next signed test candidate is opened via external **Open with** and the original title survives. Do not silently rename any real file or use URI identifiers as guessed names.

Status (2026-10-16): Stage 1 is implemented on the rescue branch: Files replaces M on compact top toolbar, opens a focused file chooser with Open/New/Tabs/Favorites/Folders/inline Recents and returns to the editor; Tools and recovery remains available via More, and bookmark actions are placed in the existing mode toolbar. The external provider filename/optional-projection fix is independently committed. Wider unfolded left rail, safe file rename/move/duplicate and broader M2–M7 are still outstanding. The currently installed signed app is unchanged until a tested newer candidate is intentionally installed.
