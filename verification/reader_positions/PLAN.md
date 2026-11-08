# Reader position helper implementation plan

Goal: implement the approved helper-only contract at .superpowers/campaign-resume/reader-position-contract.md, followed by root-owned real UI integration. Existing isolated worktree; no commits or existing production edits.

1. Write actual-helper behavioral fixtures for anchor fingerprint/context mapping, Unicode/cancellation/comparison bounds, native maxima; store URI identity, limits/LRU/no bookmark eviction, strict/corrupt parsing and checked commits. Witness absent-source RED.
2. Implement ReaderAnchor with one streaming strict UTF8 hash pass and fixed8192 buffer; codepoint-safe <=288-unit context; combined-context KMP, actual search comparison counters, immutable metadata/results. No source copies/fields.
3. Implement ReaderPositionStore with the approved fixed caps, bounded strict JSON grammar/depth/duplicate validation, immutable hydration, checked commits and location-only LRU. No provider/session/history operations.
4. Run focused runner; expand any uncovered material boundary regressions before fixes. Compile actual new helpers against installed SDK37 using repository --release11 strategy. Record exact scoped evidence/interfaces and limitations; freeze for independent review without commit.
