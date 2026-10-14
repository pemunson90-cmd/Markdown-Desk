package com.example.markdowndesk;

/** Intended nested-list semantics. This deliberately fails on baseline 2.2.5. */
public final class NestedListKnownFailure {
    public static void main(String[] args) {
        String html = MarkdownRenderer.render("- parent\n  - child\n- sibling", UiTheme.palette(0, false), 18);
        String body = html.substring(html.indexOf("<body>") + 6, html.indexOf("<script>"));
        String expected = "<ul data-source-offset=\"0\"><li>parent<ul><li>child</li></ul></li><li>sibling</li></ul>";
        if (!expected.equals(body)) throw new AssertionError("nested list body expected <" + expected + ">, got <" + body + ">");
        System.out.println("PASS nested list structure");
    }
}
