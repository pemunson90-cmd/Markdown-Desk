# Reading layout Appearance and capture checks

Run with the existing JDK17:

    PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH bash verification/reader_typography_ui/run.sh

The extractor copies actual Activity read/adjust/update/construct/refresh methods
and the actual HTML-share action. The real immutable ReaderTypography and renderer
are compiled. Small widgets record actual callbacks, values, selection, colors and
persistence; they do not reimplement adjustment logic. No Android UI is simulated
as device acceptance.

40 UI checks cover three family choices, exact existing defaults, independent safe
typed preference loading, clamped +/- controls and availability, immutable previous
values, reset, same-dialog live updates/colors, stable numeric labels under comma
locale, accessible action names and persisted value types. Controls use line-height
steps .10 (1.20..2.20), paragraph-gap steps .10 (.50..1.50), margin steps4px
(12..44) and width steps40px (480..960); reset retains exact1.72/.90/28/720 serif.

6 capture checks execute real LatestPreview with held executors: the final immutable
style identity reaches the worker, old request constructors/null style retain exact
defaults, and newer style work replaces stale pending work. The separate existing
preview_behavior suite now tests Activity delivery/page identity guards, custom HTML
and held stale style A/B/A delivery. Existing navigation/outline suites preserve
source/DOM/viewport safeguards.

3 HTML-share checks execute the actual Activity action: HTML uses current validated
style, the prepared exported String remains fixed after later style changes, and
plain Markdown sharing is unchanged. The print Capture integration is verified by
the separate real print ownership runner (20cases/134checks +18controlled checks),
not by a custom print model here. Real PDF framing/raster tests belong to the print
worker and are not rerun in this packet.

New local preference keys reader_font_family, reader_line_height,
reader_paragraph_gap, reader_margin_px and reader_measure_px are not a new portable
archive contract. Optional future archive coverage is deferred. No source font-size
or editing behavior changes. No APK, device, WebView/IME visual acceptance or broad
aggregate build. latest-output.txt retains the focused checks and actual Android
source javac result; SDK resources generated during Undo compilation are reused
because this slice changes no resources.
