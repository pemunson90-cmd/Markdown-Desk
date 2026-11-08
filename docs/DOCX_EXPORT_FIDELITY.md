# DOCX export fidelity

The export core, private staging, verified SAF new-copy publication and actual
Activity controls are implemented and independently reviewed. Physical Android
picker/provider/Word acceptance is still open. DOCX import and round-trip are not
implemented or promised. This document export is separate from deferred
private app-state export/import.

| Markdown input | Current core output | Limits and warnings |
| --- | --- | --- |
| ATX headings H1–H6 | Word heading styles and outline levels | No setext headings or generated contents page |
| Paragraphs and newlines | Paragraphs and explicit breaks | Lone CR and CRLF preserve logical breaks; Markdown whitespace layout is not a Word round-trip |
| Simple bold, italic, strike, inline code | Formatted runs | Ambiguous/nested syntax or escapes inside formatted spans become literal paragraph text with a warning |
| Links | External hyperlinks for lowercase http, https and mailto | Other targets retain labels with warning; no resource fetching; complex/escaped labels fall back literally |
| Bullet/decimal lists | Real numbering, nested four-column levels and contiguous-list starts | Unsupported indentation, depth or continuation stays literal with warning |
| Task items | Static checked/unchecked boxes | No interactive Word form controls |
| Rectangular tables | Real cells, borders and repeated header | Ragged or overwide blocks remain literal, including surplus cells and divider; no merged cells or alignment promise |
| Fenced code | Monospace text, spaces, tabs and breaks | No highlighting; ambiguous fences remain literal with warning; missing close warns |
| Quotes and rules | Indented paragraphs and borders | Nested block fidelity is limited by the documented subset |
| Unicode | Logical Unicode and escaped XML text | Invalid surrogates or XML-disallowed scalars fail; installed office fonts determine glyph coverage |
| Images | Visible alt-text placeholder and warning | No embedded images, local-resource reads or network access |
| HTML, footnotes and math-like dollar syntax | Visible literal text and warning | No executable markup, macros, fields or extensions |

The core supports source up to 1 MiB of strict UTF-8, 16,384 UTF-16 units per line,
10,000 blocks and cells, 32 table columns, 1,000 hyperlink relationships, and 8 MiB
each of generated XML and compressed ZIP. Other scanner/run/depth limits are
specified in [the focused verification guide](../verification/docx_export/README.md).
A DOCX-only limit never changes the native editor's existing 8 MiB policy.

Core generation owns and closes its supplied staging stream. It returns checksums,
length and bounded warnings only after ZIP finish and close succeed. It does not
establish filesystem durability or provider publication. Integration must capture
source once, present limitations, use a separately selected new-copy destination,
verify binary readback and leave source identity, dirty state and history unchanged.

Independent ZIP/XML/CRC checks and a fresh isolated LibreOffice opening/PDF conversion
pass for the supported fixtures. Actual Android SAF publication, physical Word
acceptance and all remaining release gates stay open.

DOCX import feasibility was investigated on 2026-10-16. A bounded read-only basic
DOCX to new untitled Markdown conversion is feasible, subject to strict ZIP/XML
limits and an explicitly supported subset. It is not implemented or exposed in
the app. A future importer must refuse macros, external relationships, embedded
content and unsupported content-bearing structures, disclose formatting losses
and keep the DOCX source separate from the Markdown save destination. A tiny
host-only probe parsed a core-generated3126-byte/7-member ZIP (7943 inflated bytes,
8 paragraphs) and refused a hostile DTD. This is feasibility evidence; Android
parser security and device acceptance remain open. General Word compatibility
and lossless DOCX round-trip remain unpromised.
