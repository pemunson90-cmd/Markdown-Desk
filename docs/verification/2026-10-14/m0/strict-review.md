# Strict capture critical review

Verdict: PASS for the bounded strict-encoding change. No concrete correctness or compatibility defects found.

Reviewed AGENTS.md, current status/roadmap/release gates, /tmp/md-strict-review.diff, actual DocumentIO/MainActivity call sites, ExtractStrictEncoding, and save/recovery harness integration.

- encodeStrict uses the real UTF-8 encoder with REPORT; CharacterCodingException is an IOException and is caught by saveInternal before submitCapturedSave, worker scheduling, or provider access. Pending continuation is cleared while captured editor/tab text remains available and dirty state is retained.
- UTF-16 code-unit preflight cannot reject valid UTF-8 text within the byte limit: UTF-8 requires at least as many bytes as UTF-16 code units. The encoded-byte check also includes the three BOM bytes and accepts the exact 8 MiB boundary.
- Valid surrogate pairs, CRLF/whitespace, embedded U+FEFF, and requested leading BOM are preserved without text normalization. Unpaired surrogates fail rather than silently becoming replacement bytes.
- Legacy recovery protection uses the same strict encoder before protected snapshot creation. Failures flow through the existing recovery-protection failure gate. The legacy encode helper remains unchanged for baseline API compatibility.
- ExtractStrictEncoding parses actual DocumentIO.java and copies its encodeStrict method, MAX_DOCUMENT_BYTES, and UTF8_BOM declarations into the harness. It rejects missing/duplicate source members. Both save_lifecycle/run.sh and recovery_loader/run.sh invoke it after extracting actual MainActivity methods; the strict codec is not mirrored in a test implementation.

Independent verification performed: compiled actual DocumentIO.java against the existing Android boundary stubs into an isolated /tmp directory and ran 15 independent checks. PASS: ASCII exact limit; BOM exact limit and one-byte overflow; two-byte Unicode exact limit and overflow; code-unit over-limit; supplementary pair/embedded BOM/CRLF/whitespace byte equality; empty BOM/non-BOM; unmatched high/low/repeated-high surrogates; null text. Source: /tmp/md-strict-independent.JYNh6y/StrictReview.java. First compilation found javac absent from default PATH; rerun using existing JAVA_HOME/bin succeeded.

The coordinator-reported 42 DocumentIO fixtures and UI RED/GREEN counts were inspected as context, not independently rerun here. Full host/build verification remains the coordinator's integration gate. No Android provider, device, Fold, power-loss, or release acceptance claim is made.
