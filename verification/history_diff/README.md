# Bounded history diff host checks

Run with the existing JDK17 `bin` directory on `PATH`:

```sh
bash verification/history_diff/run.sh
```

The runner compiles the actual `ProtectionStore` and `HistoryDiff` with
`javac --release 11`. Fixtures commit real snapshots in temporary directories
with file sync, atomic moves and directory sync. No Android runtime integration,
provider writes, snapshot mutations, pruning or history catalog dependency is
introduced. The integrating host runner must register this suite separately.

`new HistoryDiff().compare(left, right)` returns an immutable `Preview` with
`leftByteCount`, `rightByteCount`, `leftHash`, `rightHash`, `leftBom`, `rightBom`,
`complete`, `reason` and `lines`. Each `DisplayLine` has `kind` (`UNCHANGED`,
`REMOVE`, `ADD`), exact decoded `text`, and `ending` (`LF`, `CRLF`, `CR`, `NONE`).
`Ending.bytes` is the original terminator. `display()` adds an operation prefix,
escapes backslashes, controls, BOM and Unicode line separators, and labels the
terminator. Unicode supplementary characters remain intact. BOM remains in the
first line's text as well as explicit per-side metadata, so complete operations
reconstruct both inputs exactly.

Rulings for the bounded backend slice:

- Empty input has zero logical lines. A trailing newline terminates the preceding
  line without creating a phantom empty line. A lone CR is a distinct terminator.
  This keeps exact byte reconstruction possible without newline normalization.
- Use an exact prefix/suffix-trimmed LCS grid, permitted by the task's bounded
  algorithm requirement. One work unit is charged per grid/control step, plus
  an upper bound of the larger text length for every equality comparison. Reject
  a grid before allocation when its cell count already exceeds remaining work.
  Conservative charging can summarize inputs that another algorithm could finish;
  no result claims a complete approximate diff. The grid is bounded to roughly
  4MiB, and character comparison work cannot hide giant repeated-line costs.
- Any missing, corrupt, changed-provenance or malformed UTF-8 source throws
  `IOException`, even when a size or line cap would otherwise yield a summary.
  Exact counts and hashes are exposed only after both fresh `Snapshot.bytes()`
  validations succeed. Metadata from a stale handle cannot yield a preview.
- Each side is limited to 262,144 bytes and 10,000 logical lines. Strict UTF-8
  validation of larger stored payloads uses a fixed 4,096-character scratch
  buffer; no large String is created. Each validated payload is loaded in a
  separate helper call, so two oversized arrays are not retained together.
- Algorithm work is limited to 1,000,000 units and output to 2,000 lines.
  Byte, line and work limits return summary-only output. Output limits retain
  the first 2,000 exact operations and explicitly label the preview incomplete.
  Every incomplete reason identifies the limit and points to restore-to-new-copy
  or export for full content; those operations are future integration paths.

Observed verification on 2026-10-14: initial RED was the missing `HistoryDiff.java`
implementation (runner exit2). With the implementation, the JDK17 runner exits0:
10 cases / 522 assertions, plus a separate `-Xmx128m` JVM retaining 32MiB headroom
through six comparisons of real paired 8MiB snapshots. Tests cover exact hunks,
independent byte reconstruction, a separate LCS oracle on 60 repeated-line
fixtures, BOM/CRLF/CR/no-final-newline fidelity, supplementary Unicode at a
4096-character decoding boundary, display escapes, giant lines, size/line/output
boundaries, adversarial work, malformed UTF-8 (including oversized payloads),
corrupt/missing sources, internally valid changed metadata and stale provenance,
immutable output, and source envelope byte preservation. The constrained JVM
must finish without `OutOfMemoryError`; failure propagates as a nonzero exit.

These are host checks, not Android heap/latency, physical-device, SAF, power-loss
or M0/M1 acceptance evidence. Full unchanged host and APK checks are deferred to
integration; runtime history/diff enablement remains outside this change.
