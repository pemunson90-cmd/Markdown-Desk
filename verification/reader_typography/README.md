# Reader typography renderer checks

`bash verification/reader_typography/run.sh` compiles the production validated
immutable typography model and renderer. JDK17: **80 assertions PASS**.

The nine theme HTML fixtures were captured before the renderer change. Default
and null styles must match those bytes; custom styles may change only body font,
line height, horizontal margins, paragraph gap and measure. Tests cover fixed font
families, independent invalid-field fallback, finite bounds, hostile family input,
and locale-independent CSS. Parser output, source offsets and sync script remain
unchanged. Independent source review approved this model/renderer slice.

Appearance persistence, async preview integration and Android layout require their
own checks. This fixture does not establish device typography or release acceptance.
