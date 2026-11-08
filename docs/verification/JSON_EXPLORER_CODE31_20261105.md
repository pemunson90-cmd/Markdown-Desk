# JSON Explorer / Claude export — code31 acceptance and handoff

**Implementation branch:** `feature/json-claude-export-explorer-20261105`
**Base:** `release/2.3.2-emaillinks` @ `2f1bf26feb4c58b629582aac4a0c194ea86574c2`
**Version:** 2.3.3-json-explorer1 / versionCode 31; package unchanged.

## Owner request
Open `.json` files in Markdown Desk and provide useful actions to explore Claude account exports, without losing existing Markdown/Files/editor capabilities.

## Implemented
- Existing Files hub gains **Explore JSON / Claude export**, using Android SAF; general Browse files also accepts JSON.
- Android JSON VIEW intent handles `application/json` and `text/json` and existing text intents check `.json` names.
- Dedicated read-only native JSON Explorer, separate from the editor's 8 MiB edit safety limit.
- Forward-only JSON parser, at most 100 entries per displayed page; random branch navigation reopens original SAF stream. No whole-export deserialization.
- Object and array drilldown; JSON-path copy; per-page filtering, copy/share displayed entries.
- Claude `chat_messages` / `messages` arrays recognized on conversation objects. Transcript preview reads up to 200 messages; Copy or Save Markdown into a **new** user-selected document. Original source is never opened for writing.
- Preserved package, prior release line, original Markdown editor/file features, themes, splash assets, and source files.

## Honest limits and known next improvements
- **Search filters the loaded 100-entry page only.** No full archive search or index; large offsets are streamed from the beginning on every navigation.
- Transcript preview/export is capped at **200 messages**, with an explicit incomplete-preview warning when more remain; it is not a full archive converter.
- Some Claude message blocks are non-text or nested in changing export schemas. The preview represents known text blocks only and should be tested against a real owner export.
- The streaming parser doesn't validate the unread suffix of a huge document merely because its first page loaded.
- A single pathological very long JSON string may still consume substantial memory when explicitly selected; unlimited data-size claims are not appropriate.
- No JSON editing, import into Claude, privacy-cloud sync, or `.jsonl` browsing is claimed.

## Verification
- Workflow `JSON Explorer unsigned acceptance`, run 42133929528: Android compilation and the selected host regressions PASS (GitHub logs). GitHub holds only an **unsigned** artifact.
- Operator retrieved the exact run artifact and signed it privately from the original Library PKCS#12 keystore; local `apksigner verify` PASS, ZIP alignment PASS, and package/version check PASS.
- Canonical signing certificate fingerprint: `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`.
- Signing materials and passwords were **never** committed to GitHub or used in hosted runners.
- Full current-phone gate, native emulator JSON interaction and physical Samsung Fold7 acceptance **NOT RUN**. Do not claim device acceptance.

## Fold7 owner acceptance
1. Install **over** existing Markdown Desk; do not uninstall or clear app storage.
2. Check existing tabs, original files, Favorites, bookmarks, themes and saved documents remain intact.
3. Files > Explore JSON / Claude export > select `conversations.json`. Confirm titles display and next-page navigation works.
4. Open a chat object, tap Read as Claude conversation; verify speaker names and text content match the raw source.
5. Save Markdown into a fresh file; check source `conversations.json` unchanged.
6. Test a JSON object (`memories.json` or `projects.json`) and ordinary Markdown editing.
7. Fold/unfold or rotate during navigation; verify current path and scroll usability.

## Safety
The APK must keep the canonical certificate and increasing versionCode for normal upgrades. Never commit the private release key/passwords, personal Claude exports, or signed production APK to this public repo.
