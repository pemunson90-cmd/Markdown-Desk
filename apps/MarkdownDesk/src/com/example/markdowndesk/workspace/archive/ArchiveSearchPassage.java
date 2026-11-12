package com.example.markdowndesk.workspace.archive;

/** A reader fragment for an exact, verified UTF-16 match in original source. */
public final class ArchiveSearchPassage {
    public static final int MAX_DISPLAY_UNITS = 1024;
    public static final String ANCHOR_ID = "archive-search-passage";

    private ArchiveSearchPassage() {}

    public static String render(String first, String last) {
        // A display cut may bisect a supplementary character. Omit the partial
        // character at either side of the explicit omission.
        if (last != null) {
            if (!first.isEmpty() && Character.isHighSurrogate(first.charAt(first.length() - 1)))
                first = first.substring(0, first.length() - 1);
            if (!last.isEmpty() && Character.isLowSurrogate(last.charAt(0)))
                last = last.substring(1);
        }
        return "<aside id=\"" + ANCHOR_ID + "\" aria-label=\"Indexed search passage\">"
                + "<div>Indexed search passage · original text</div><pre><mark>" + escape(first)
                + "</mark>" + (last == null ? "" : " <span>[… source text omitted …]</span> <mark>"
                + escape(last) + "</mark>") + "</pre></aside>";
    }

    private static String escape(String source) {
        return source.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\r", "&#13;");
    }
}
