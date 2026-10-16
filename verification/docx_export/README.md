# Bounded DOCX export core

Run with installed JDK17 prepended to PATH:

```
PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH bash verification/docx_export/run.sh
```

Uses installed Python, unzip, LibreOffice (`soffice`) and pdftotext; no downloads.
Core is not wired to an Activity/SAF destination. This is no claim of durable output,
provider safety, Word/Android acceptance, DOCX import or all M6 completion.

API: `MarkdownDocx.export(source,title,stagedOutput,Limits.defaults(),cancellation)`
returns immutable `Result` only after ZIP finish and output close. It owns the
supplied stream on every outcome, closing the actual underlying stream once.
Result exposes compressed length, compressed SHA256, source UTF8 SHA256, immutable
warning category counts and at most16 source UTF16 locations. Callers must stage
privately and discard incomplete bytes on any failure. It does not flush/fsync a
filesystem or publish content. Cancellation checks are cooperative, not deadlines.
`ExportException.failure` distinguishes invalid text, limits, cancellation and
runtime generation errors; direct output IOExceptions propagate without fabricated
success. Invalid required arguments close an otherwise supplied stream.

`Limits` is immutable, `with(Budget,value)` permits positive values no greater than
the approved hard maximum; defaults: source1MiB strict UTF8/1,048,576UTF16,
line16,384UTF16,10,000blocks,50,000runs,16,777,216scanner work units,9list levels,
8inline levels (implemented inline subset is flat),100tables,32columns,10,000cells,
2048UTF16 hyperlink target,1000relationships,8MiB aggregate XML and8MiB compressed
ZIP. List/table shape beyond supported depth/columns falls back visibly instead of
silently dropping content; actual resource overflows fail explicitly. XML and ZIP
counters are checked before output; fixed package paths only. Source/title XML1.0
scalars are validated before parsing. Header title<=4096UTF16.

Supported semantics: ATX H1–H6 styles/outline levels, paragraphs/newlines, simple
bold/italic/strike and code runs, lowercase http/https/mailto hyperlinks, real
bullet/decimal numbering with contiguous-list starts and nested4-column indents,
static task boxes, rectangular tables/escaped pipes/repeated header, fenced code
with whitespace/tabs, quotes, rules and Unicode logical text. Neutral fixed styles;
reader themes are not exported. Deterministic ZIP dates, no creator/device paths.

Deliberate deviations: no full CommonMark; ambiguous/nested inline markup or
parenthesized link targets becomes literal whole paragraph with warning. Raw HTML
and footnotes stay literal. Unsupported list indent/depth/continuation becomes a
literal source block; unsupported huge ordered marker stays literal. Ragged/overwide
table becomes a literal whole source block including divider and surplus cells.
No content is discarded to force rectangularity. Escaped punctuation uses renderer
subset rules; MarkdownStructure is not treated as an AST. Image syntax emits an
alt-text placeholder and warning; unsupported links emit visible labels and warning.
No image/resource fetching or XML input parsing. Code language is not highlighted;
missing closing fence warns. No import or round-trip promise. Source text is never
modified. Warning offsets identify originating blocks, not exact token spans.

Tests: Java real package generation, ownership, deterministic bytes/hash, exact and
one-over source/table/XML/ZIP bounds, immutable cap validation, hostile scanning,
all observed cancellation seams, output write/late ZIP finish/close failures.
Independent Python ZIP/XML semantic assertions inspect actual styles, runs,
numbering/restarts, tables, relationship safety and literal fallback text. unzip
independently checks CRC. Installed LibreOffice opens rich DOCX and exports PDF;
pdftotext verifies content and bounded pagination. Host font-dependent glyph
coverage/visual layout and device providers remain separate acceptance gates.

Artifacts are only under ignored verification/build/docx-export. No binaries or
user content belong in Git. Repeated runs overwrite only these generated fixtures.

Independent-review fixes: rule/table-divider recognition uses linear bounded
scanners (16,384-dash and12,000-unit divider stack-overflow regressions); XML output
preflights UTF8 byte count before allocation and emits text in512UTF16-unit chunks.
Validation/scanning/output include periodic cooperative cancellation, including odd
indexes from supplementary codepoints. Standalone CR produces a logical break;
CRLF produces one. Escaped punctuation inside formatted spans/link/image labels
falls back as the entire literal paragraph with UNSUPPORTED_INLINE warning.
Math-like dollar syntax stays literal with UNSUPPORTED_BLOCK. Four-character fence
openers and closing-prefix-plus-tail produce whole literal fence blocks, preserving
source markers/tail with warnings. No Error catch conceals stack/memory errors.

Office runner now uses a newly created output directory and isolated profile each
run with a45-second timeout; a pre-existing PDF cannot satisfy the check. Independent
list tests resolve each paragraph numId+ilvl through actual definitions and assert
ordered starts/restarts and nested mixed types. Current focused evidence:184Java
checks +14review regressions +86independent ZIP/XML/list checks; unzip CRC and fresh
LibreOffice/PDF11content-pagination checks PASS. No aggregate/build/device claims.

The universal host aggregate invokes `bash verification/docx_export/run.sh --core-only`:
Java behavior, independent ZIP/XML and CRC checks run; Office/PDF checks explicitly
report NOT RUN. The default command remains the separate installed-tool Office gate.
This does not establish Activity/SAF integration or physical Word acceptance.
