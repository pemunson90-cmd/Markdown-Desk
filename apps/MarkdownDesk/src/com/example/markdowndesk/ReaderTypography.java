package com.example.markdowndesk;

import java.util.Locale;

/** Validated reader preferences. Only fixed, locally defined font stacks reach CSS. */
final class ReaderTypography {
    enum Family {
        SERIF("Georgia,'Times New Roman',serif"),
        SANS("system-ui,sans-serif"),
        MONOSPACE("'SFMono-Regular',Consolas,monospace");
        final String css;
        Family(String css) { this.css = css; }
    }

    final Family family;
    final float lineHeight;
    final float paragraphGapEm;
    final int horizontalMarginPx;
    final int measurePx;

    private ReaderTypography(Family family, float lineHeight, float paragraphGapEm,
                             int horizontalMarginPx, int measurePx) {
        this.family = family;
        this.lineHeight = lineHeight;
        this.paragraphGapEm = paragraphGapEm;
        this.horizontalMarginPx = horizontalMarginPx;
        this.measurePx = measurePx;
    }

    static ReaderTypography defaults() {
        return new ReaderTypography(Family.SERIF, 1.72f, .9f, 28, 720);
    }

    /** Invalid persisted values fall back independently; names must match enum constants. */
    static ReaderTypography fromValues(String family, float lineHeight, float paragraphGapEm,
                                       int horizontalMarginPx, int measurePx) {
        Family validated = Family.SERIF;
        if (family != null) {
            try { validated = Family.valueOf(family); }
            catch (IllegalArgumentException ignored) { /* Use the known default stack. */ }
        }
        return new ReaderTypography(validated,
                valid(lineHeight, 1.2f, 2.2f) ? lineHeight : 1.72f,
                valid(paragraphGapEm, .5f, 1.5f) ? paragraphGapEm : .9f,
                horizontalMarginPx >= 12 && horizontalMarginPx <= 44 ? horizontalMarginPx : 28,
                measurePx >= 480 && measurePx <= 960 ? measurePx : 720);
    }

    private static boolean valid(float value, float min, float max) {
        return Float.isFinite(value) && value >= min && value <= max;
    }

    ReaderTypography withFamily(Family value) {
        return fromValues(value == null ? null : value.name(), lineHeight, paragraphGapEm, horizontalMarginPx, measurePx);
    }
    ReaderTypography withLineHeight(float value) {
        return fromValues(family.name(), value, paragraphGapEm, horizontalMarginPx, measurePx);
    }
    ReaderTypography withParagraphGapEm(float value) {
        return fromValues(family.name(), lineHeight, value, horizontalMarginPx, measurePx);
    }
    ReaderTypography withHorizontalMarginPx(int value) {
        return fromValues(family.name(), lineHeight, paragraphGapEm, value, measurePx);
    }
    ReaderTypography withMeasurePx(int value) {
        return fromValues(family.name(), lineHeight, paragraphGapEm, horizontalMarginPx, value);
    }

    static String cssNumber(float value) {
        // Values have at most float precision; ROOT prevents locale decimal commas.
        return String.format(Locale.ROOT, "%s", Float.toString(value));
    }
}
