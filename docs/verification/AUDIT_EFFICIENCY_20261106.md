# Repository efficiency repair, 2026-11-06

## Scope and isolation

Base: `feature/unified-workspaces-20261106` at `6b95d5876caf9d3d1f81bc9455bef4c90a0fdc67` (code38), not the older code32 PR or main. This isolated review branch does not claim the product writer's ownership or change its branch. No merge, APK, signature, installation, deletion, or release was performed.

The requested fixes are source-selection consistency, redundant hosted acceptance work, and bounded harness maintenance. Existing application source, manifest, resources, assets and shared runtime code are unchanged. Full unified-workspaces product delivery remains the existing owner's task.

## Implemented

1. `docs/CURRENT_SOURCE.json` separates the integration source anchor/manifest from the unconfirmed installed identity and unknown worker execution state. `verification/check_current_source.py` checks anchor ancestry, the latest fetched integration ref, manifest identity, evidence links and the generated current STATUS section. It never fetches, checks out, resets, or writes data. Fetch the named branch before source selection; offline remote refs do not prove current remote state.
2. `docs/STATUS.md` has a truthful generated current section. All 71,521 original historical bytes remain verbatim afterward, labeled as historical. `CODEX_START.md` directs normal resumption to the short current section and guards against replacing dirty/active work.
3. JSON unsigned acceptance is manual-dispatch only; compile, focused tests, concurrency, permissions and artifact retention remain. Artifact names use the run's exact SHA instead of the obsolete code32 label. The existing review-ready host PR gate is unchanged. No hosted acceptance run was dispatched.
4. Preview/status AST parsing and exact source slicing share `ParsedJavaSource`; member selection and completeness assertions remain in their own extractors. Production Activity code is untouched. This reduces duplicated harness maintenance; it does not automatically infer missing harness dependencies or complete a broad Activity refactor.
5. The two static layout/appearance guards retain historical identity sets and accept the exact current manifest only after the source guard verifies it. Their old hardcoded code30 ceiling had rejected code38. The full host aggregate no longer runs the same Downloads suite twice; both aggregate suites register the new source/extraction checks.

## Verification

- Test-first RED: nine new source/workflow checks failed before implementation. The separately added current-candidate guard test reproduced the existing code30 ceiling.
- GREEN: all ten source/workflow regression tests; existing release-identity test; current-source check; both existing static layout/appearance guards; parsed YAML confirms manual dispatch only; `git diff --check`.
- The release check with `--require-installed` correctly fails while installed identity is unconfirmed. This is a retained gate, not acceptance.
- Shared extractor checks pass on the available JDK compiler module: exact fields/methods/nested source slices, CRLF, comments, Unicode, unresolved imports and captured source stability.
- Current preview/status generated harnesses and historical preview extraction at `6630609` are byte-identical before/after the extraction refactor.
- Preview behavior: 450 checks PASS; status behavior: 57 assertions PASS using a local test-only JDK21 compiler wrapper with `-source 11 -target 11`. That run is **not** proof of Java11 API compatibility.
- Full required host command was attempted using the unmodified `--release 11` compiler semantics. New checks, baseline inventory (10 tests), exact baseline assets, static layout/appearance and identity checks pass; execution then stops because this environment lacks Java11 release symbols (`error: release version 11 not supported`). No aggregate green claim.
- Android SDK compilation, full host completion with the required JDK, Android/emulator/device tests, signing and Fold7 acceptance: NOT RUN. No application changes were made to compensate for missing tools.

## Integration

Review/apply to the current integration owner at a safe checkpoint. If upstream advances, refresh from that head and rerun the checks rather than overwriting it. The source guard intentionally rejects older/incompletely integrated checkouts; it is not a substitute for feature-parity testing. Confirm the actual installed source/artifact from existing evidence before recording `installed.status = verified`; do not treat candidate metadata as owner acceptance. Refresh the source record and its generated STATUS block together at future integration changes.

The workflow change affects only branches containing it; historical branches keep their own workflow definitions until deliberately reconciled. No old branch or PR is silently closed or discarded.
