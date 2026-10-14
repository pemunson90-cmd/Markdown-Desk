# DocumentIO host behavior and safety probes

These fixtures compile the unchanged production `DocumentIO.java` directly with
four minimal, isolated Android boundary doubles. No Android JSON JAR, app runtime
change, existing SplashStore stub change, or central runner change is required.

Use the existing JDK17 with its bin directory on PATH:

```bash
PATH="$JAVA_HOME/bin:$PATH" bash verification/document_io/run.sh
PATH="$JAVA_HOME/bin:$PATH" bash verification/document_io/run.sh --safety
```

Compilation classes and temporary files stay in
`verification/build/document-io/`. Both commands exit nonzero on failed
assertions. The safety command deliberately remains red against the imported
2.2.5 baseline; it is not an expected-failure wrapper and does not suppress
failures. A green preservation run alone does **not** pass the document safety
gate.

## Observed JDK17 results (2026-10-14)

- Preservation/provider-contract fixtures: **39 passed, 0 failed**, exit 0.
- Separate safety assertions: **0 passed, 5 failed**, exit 1.

The passing fixtures independently expect UTF8 bytes/text, Unicode supplementary
characters, combining characters, CRLF/LF/bare CR, whitespace, BOM and BOM-only
files, empty input, invalid UTF8 rejection, the exact 8 MiB limit (including BOM)
and rejection one byte over. Exact-limit writes/reopens are tested for ASCII
and multibyte Unicode with BOM; oversize write rejection remains a red safety
assertion. They exercise edit/save/reopen through fallback
`wt`, successful truncation, unsupported descriptor fallback, null streams,
revoked permissions, input/write/flush/close exceptions and stream closure.
They also exercise real host temporary-file descriptors for `rwt` writes and
nontruncating `rw` metadata probes, cursor values/defaults/closure, and revoked
metadata access. Golden encoder bytes are a literal independent byte sequence.
There are no source-string guards.

A corrupt simulated provider returns successfully from write and exposes altered
bytes on reopen. The passing fixture verifies that the mismatch is observable;
`DocumentIO.write` itself does not perform verification. Another fixture verifies
that read-back throws after simulated grant revocation. These are API-level
observations, not execution of MainActivity's save workflow, recovery retention,
external-change check, UI state, or mismatch/error handling.

## Reproduced unsafe baseline behavior

Run `--safety` to reproduce all five desired safety assertions without changing
production code:

1. An ASCII payload of 8,388,609 bytes, or a Unicode payload of repeated `é`
   with UTF8 BOM producing the same byte length, is sent to write. Both should
   throw IOException before any provider access. Instead, both perform one
   descriptor open and one output open, return success, and replace the original
   with 8,388,609 bytes. Subsequent `DocumentIO.read` would reject that size.
   Both assertions fail with `rejected=false, descriptor opens=1, output opens=1,
   retained length=8388609`.
2. Provider query returns null and `openFileDescriptor(uri, "rw")` returns null.
   `DocumentIO.metadata` reports `writable=true`. The assertion expects false and
   fails with `null rw descriptor reported writable=true`.
3. Start with literal bytes `original document`. Descriptor open returns null;
   provider opens `wt`, truncates its stored document and accepts only the first
   two bytes of `replacement` before throwing IOException (simulated out of
   storage). `DocumentIO.write` propagates the exception and closes the stream,
   but the original has become `re`. The original-preservation assertion fails
   with `original destroyed: retained bytes=re`.
4. Same fixture with zero bytes accepted after the destructive `wt` open.
   IOException propagates, and the original is now empty. The assertion fails
   with `original destroyed: retained length=0`.

The ordinary mid-write fixture tests only exception propagation and stream
closure. It does not assert that truncation or lost original bytes are safe.
The five failures above remain distinct from passing preservation fixtures and
must be investigated before claiming the non-destructive provider-write gate.
No runtime fixes are part of this bounded test change.

## Limits

These are controlled JVM simulations, not real Android SAF acceptance. URI
parsing, persisted grants, binder, provider-specific flags, virtual documents,
cloud/offline providers, cancellation, storage hardware, process death, Android
FD ownership and MainActivity integration are not represented. The descriptor
fixture uses a real host file and the real JDK `FileOutputStream`/`sync`; it does
not establish Android provider fsync or durability guarantees. Faulting fallback
providers explicitly model destructive `wt` open and partial writes; no result
claims all providers behave that way. Phone/Fold/provider/device acceptance is
**NOT RUN**.

## Current M0 repair checkpoint

The failure list above records the original tests-only baseline. At runtime
checkpoint `8462f79`, oversized UTF-8/BOM/ASCII preflight and null-descriptor
writability are repaired: `--safety` now reports3pass/2fail. The two destructive
partial/zero-byte writes still fail unchanged-original assertions. The ordinary
39assertion preservation suite passes. No provider/device acceptance is implied.
