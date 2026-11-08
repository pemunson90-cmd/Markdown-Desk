# Search beta code40 verification and acceptance

Branch: beta/search-index-v3. Baseline: 80cbd77d795e07e4bbca18d387ca54fbea312b5c / released V2.5.0 code39.

## Implemented scope

- Index schema v3 adds per-source search format metadata only. Existing data is not deleted on upgrade.
- Older indexes offer an explicit Yes/No rebuild dialog, reached from library/search. No/cancel preserves the old index. Yes reuses the existing streaming importer, source URI/mode identity, progress/cancellation and atomic generation publication. Original JSON and annotation journal are untouched. If file access or storage is unavailable, the previous completed index remains usable.
- New/rebuilt indexes retain 1,024 UTF-16 units of overlap, aligned with the accepted query limit. FTS4 offsets resolve actual punctuation/diacritic-matched source text and the correct starting part.
- Navigation validates generation and message/part, carries actual matched text, restores pending state, and highlights after annotation decoration. Stale/missing/ambiguous passages refuse false exact highlighting.
- Manifest: com.example.markdowndesk, versionCode40, 2.5.1-beta1-search, min35/target37. No stable promotion or release publication.

## Verification decision and stop condition

The change selector proposes 48 conservative candidates because Activity and archive-family ownership is broad. Those are not 48 distinct unanswered questions. Relevant production changes are confined to search SQL/format metadata, reader search navigation, consent UI, and a query bound. The importer, original-file parsing, annotation journal writes, editor/DOCX and save/history engines are unchanged; reuse their code39 evidence.

Selected checks and actual results:

| Check | Question | Result |
| --- | --- | --- |
| archive_reader/run_search_v3.sh | Does production SQL find the reproduced boundary phrase, preserve reconstructed source/legacy metadata, and decode normalized Unicode spans? | PASS |
| archive_reader/run_search_hit_dom.sh | Does Java-emitted JS highlight/scroll the actual quote in its own message/part and refuse ambiguity? | PASS |
| archive_navigation/run.sh | Do extracted production navigation methods preserve source/generation and consume actual hit payload correctly? | PASS after updating harness fields for the new payload |
| test_search_result_return.py | Does changed Android routing retain return-to-results and verified span wiring? | 5 PASS; static wiring evidence only |
| compile_native_sources_only.sh | Do the complete manifest/resources and production Java compile through SDK37 AAPT2/javac/D8? | PASS on frozen local source 27197f7; mirrored remote source 3e34f115c2cc0d15422f870a4a990c9276775128 |
| original certificate preflight | Is the original authorized signer available before packaging? | PASS |

The manifest is an explicit manual mapping in this plan: final source compilation and APK package/version inspection verify it. The selector's unmapped warning is not a test pass. No full host/stress rerun, dependency install, Actions, or APK before these gates complete.

Stop local checks once these questions pass and signed artifact identity/alignment are verified. Real consent/progress/rotation behavior and Android WebView scroll/selection need phone acceptance, not additional host suites.

## Explicit beta limits

Token-normalized phrases whose actual source text spans more than 1,024 units of punctuation/whitespace across a segment boundary can still exceed retained overlap. A general unbounded token-boundary search is separate work.

Markdown rendering can remove raw syntax or repeat text. If the exact returned span cannot be uniquely located within the targeted rendered part, the app opens the message and explains that it cannot highlight the passage; Inspect displays the original text. It never substitutes the differently normalized query as a supposedly exact highlight.

Seamless old-index upgrades remain deferred by owner decision. Rebuild requires permission to reread the original file and enough temporary storage. Existing notes are retained; if the external source itself changed, existing source-identity/quote validation still governs whether an old annotation resolves.

## Phone acceptance

1. Install over code39 without uninstalling. Open an old source; decline rebuild and confirm reading/notes remain.
2. Choose rebuild, check progress and cancel once; confirm the previous index remains. Restart and complete; compare notes/bookmarks.
3. Search a known boundary-spanning quotation and accent/punctuation variants. Open hit, return to results, rotate/fold, and inspect any explicit highlight warning.

Original-signed APK delivery metadata is recorded in the live ledger after the final gates.

## Signed delivery

Original-signed code40 APK completed after the selected gates. Signature, 16 KiB alignment check, package/version/min/target identity, ZIP CRC, DEX and 25 splash entries verified. See [artifact identity](SEARCH_BETA_CODE40.json). Native verification log is [retained here](SEARCH_BETA_CODE40_NATIVE.txt). No main promotion or GitHub Release.
