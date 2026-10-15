# Print-only paged PDF goldens

`bash verification/print_layout/run.sh` compiles the real renderer and print frame
helper, checks13 framing/content invariants, then generates15 actual PDFs with the
installed Chromium. Requirements: JDK17, Chromium, pdftotext/pdfinfo/pdftoppm,
Python3 with websockets and Pillow. No dependencies are downloaded. Generated HTML,
PDFs, extracted text/bounding boxes, page rasters, Chromium log and observations
are under `verification/build/print-layout` (ignored, not committed).

`bash verification/preview_navigation/run_print.sh` separately exercises actual
DocumentPrint admission, capture, readiness, security settings and adapter lifecycle
against Android boundary stubs, including captured reader typography. The print
fixture reconstructs the original renderer HTML by removing only the added root
attribute and final fixed stylesheet; it compares exact original content rather
than comparing the production helper with itself.

`bash verification/print_layout/run.sh --java-only` runs only the13 Java framing/
validation checks and fixture generation, explicitly reporting PDF checks NOT RUN.
This mode needs only JDK17 and can be registered in the universal host aggregate;
the default actual-PDF runner remains an explicit tool-dependent gate. No aggregate
execution or aggregate PDF coverage is claimed here.

## Behavior

DocumentPrintLayout adds a fixed paper stylesheet during the existing single HTML
framing copy. Canonical UUID root token and renderer body/trusted scripts survive
unchanged. Invalid token/head/root framing fails explicitly. Screen rendering and
shared renderer CSS are unchanged. Print uses12mm CSS page margins and white paper
with dark text/neutral borders; captured font family/size/line/paragraph spacing
remain. Preview measure/padding is removed in print. Long cells and code wrap;
table headers repeat; headings prefer staying with following content. Ordinary rows
avoid breaks when possible; rows/code taller than paper can fragment across pages.

The existing5arg Capture constructor keeps default ReaderTypography; a new6arg
constructor captures immutable ReaderTypography (null falls back to defaults) and
the print worker calls the existing4arg renderer. This does not change ownership,
admission,30s readiness deadline, cancellation, token validation or disposal.

## Real PDF evidence

Latest run: Chromium154.0.8037.57 on Debian13. Fontconfig defaults: Noto Serif /
DejaVu Sans Mono. Golden checks concern semantic content/page geometry, not PDF
byte hashes or a font-dependent exact page-count promise.

| Fixture | Observed pages | Assertions |
| --- | ---: | --- |
| Headings/paragraphs A4 |11|48 ordered paragraph/heading/following marker sets; headings with following text |
| Table A4 / Letter |8 /6|70 rows, every cell character, rightmost sentinels, repeated headers |
| Oversized single row A4 |6|180 unique fragment markers and side-cell endpoint survive |
| Code A4 / Letter |4 /4|120 lines,300-character wide line and endpoint, following paragraph |
| Semantic fixture, nine palettes A4 |1 each|identical text/pages; lists/tasks, links, accented Latin/Greek/Cyrillic, escaped script and blocked-image alt |

Every exported word's bounding box stays at least25pt inside paper edges. Screen
versus print computed styles prove overrides are print-only. Each theme prints on
white with dark text; raster spot checks confirm white paper. Eight page rasters
were visually inspected: table pages1/2, oversized-row page2, code pages1/3/4,
Paper/Midnight semantic pages. Headers repeat, tall content fragments, and the long
code line/right endpoint are visible without horizontal clipping. Color emoji and
disabled task checkbox appearance remain Chromium/font defaults.

The browser cannot navigate workspace file URLs in this environment. The runner
uses DevTools Page.setDocumentContent with the exact generated HTML in an isolated
blank page, blocks HTTP/HTTPS requests and uses an isolated temporary profile.
This is real Chromium PDF output, not an Android WebView or print-spooler test.
Android Save as PDF, print-dialog margin interaction, font/device/Fold checks and
real local images are NOT RUN / outside this slice. Images remain the renderer's
explicit blocked-image textual placeholder; no image support is claimed.

## Bounds and limitations

Each HTML fixture is <=128KiB; each PDF <=8MiB and <=12 pages. A browser case has a
45s DevTools deadline; total browser work has180s deadline. Poppler extraction and
raster subprocesses have15s/20s timeouts. Browser startup/shutdown and profile
cleanup are bounded; no network assets or fonts are fetched. Missing prerequisites
fail explicitly. The Java fixture runs with128MiB heap. These limits bound tests;
they are not newly claimed user-document or Android memory budgets.

Production security remains file/content access off, network blocked, images off,
DOM storage off, navigation consumed, and only existing trusted readiness JavaScript.
No source/title strings enter CSS or token attributes. Markdown data is unchanged;
there is no new output path, overwrite authority or persistent-document mutation.
The added stylesheet is fixed-size overhead within the existing full HTML copy;
8MiB source validation still does not prove a full renderer/WebView heap budget.

## Development evidence

Initial framing test runner failed before implementation because DocumentPrintLayout
was absent. Existing print regression then caught its old prefix-only parity
expectation; its replacement now independently verifies unchanged renderer HTML.
New typography tests failed for the absent captured-style constructor/field before
that implementation. Final existing print runner:20 cases/134 checks plus18
controlled-renderer checks PASS. Final layout runner:13 framing checks and15 real
PDFs/8 reviewed page rasters PASS. A strengthened table-character test initially
miscounted a word split by an interleaved row label in pdftotext layout order; removing
known label/header text before exact character comparison fixes that test artifact
without relaxing full cell coverage. No APK build/device run or lifecycle relaxation.
