# Markdown Desk 2.5.0 – Unified Workspaces (code 38)

One installed Android package (`com.example.markdowndesk`) with two launcher shortcuts:
**Markdown Desk** opens the existing editor; **Archive Reader** opens the archive research workspace. Both share a new context-aware header, workspace switching, and a preference to use the original Archive presentation. The archive retains Read, Inspect and Split modes; Split displays conversation and notes together.

## Install

Extract the included APK and install over the previously signed Markdown Desk app. Keep the old app and its data—**do not uninstall or clear data**. This update has versionCode 38 and is signed with the original release certificate (SHA-256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`).

## First checks on Fold7

1. Open Markdown Desk from the Markdown launcher; confirm prior tabs, editing, Save, Files, themes, Focus, and Write/Read/Split remain intact.
2. Tap the Archive shortcut in the shared header; open a previously indexed conversation without reimporting. Check its A+/A−, notes, Find, and source links.
3. In Archive, try Read, Inspect, Split. Fold/unfold, confirm the notebook pane and controls remain accessible, and return to Read.
4. Open the header menu and choose **Use original workspace chrome**. Check that Archive's previous interface remains available, and switch back to unified mode.
5. Test **Switch** (keeps existing workspace) and **Switch & close this workspace**. On a dirty Markdown tab, follow Markdown Desk's existing save/recovery prompt—the close command must not discard an unsaved document. The current switch-and-close operation may require a second switch after a pending save confirmation; this is an acknowledged UX limitation.
6. Use Search Archive from Markdown, and check the global Files link in Archive. Try reopening the archive after rotating/folding the device.

## Verified locally

- Original Markdown editor `classes.dex` unchanged; all 25 splash assets unchanged.
- Java sources compiled against Android API36 with API26+ DEX; `dexdump` parsed new archive DEX.
- No unresolved app-local cross-DEX methods/fields; signature v2/v3 and ZIP alignment PASS; two launchers present in manifest and original certificate preserved.
- Existing archive test: 305-message conversations, >120,000-character messages, malformed JSON rejection, notes/journal/indexes PASS.
- Shared annotation search, link citation, and tombstone regression PASS.
- SQLite migration, generation publication, partial-import isolation, search and bounded cleanup PASS.
- Synthetic 700 MB-scale workload: 70,000 messages, 210,000 text segments, ~958 MB resulting compact index, host-only SQLite test PASS.
- No GitHub Actions were used.

## Boundaries – NOT YET FEATURE-COMPLETE

**The previous Markdown editor is preserved, but comprehensive one-to-one feature parity is not complete.** Archive currently has its own 12-message windows and long-message navigation rather than truly seamless conversation scrolling; inline visual highlights and shared annotations inside ordinary Markdown Read mode have not been integrated. Universal cross-type tabs, all-document search, and a unified print/export surface also remain incomplete. The new shared header and reader functions were not exercised on an Android emulator or the owner's Fold7 in this session. Keep this APK labeled a test candidate until a real-device check is completed.

The original imported JSON remains read-only; annotations live in the app-private journal and opt-in Markdown sidecars. **Do not clear app data** while retaining unsynced annotations.

## Provenance

Code 38 was locally assembled from the signed code37 APK, replacing only its Android manifest and archive `classes2.dex`; compiled original editor `classes.dex` and all other runtime entries are byte-identical. The source of the new workspace modules and archive mode changes is captured on the dedicated GitHub development branch. Signed APK and private signing key are never committed to public GitHub.