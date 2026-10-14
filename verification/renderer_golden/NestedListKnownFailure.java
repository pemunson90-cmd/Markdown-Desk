package com.example.markdowndesk;

/** Independent expected HTML bodies for contiguous list lines. Kept under its
 * original class name so existing host callers continue running this regression. */
public final class NestedListKnownFailure {
    private static int checks;
    private static String body(String markdown) {
        String html = MarkdownRenderer.render(markdown, UiTheme.palette(0, false), 18);
        return html.substring(html.indexOf("<body>") + 6, html.indexOf("<script>"));
    }
    private static void equal(String name, String expected, String actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(name + " expected <" + expected + ">, got <" + actual + ">");
    }
    private static int count(String text, String token) {
        int count=0, from=0;
        while ((from=text.indexOf(token,from))>=0) { count++; from+=token.length(); }
        return count;
    }
    public static void main(String[] args) {
        equal("nested bullets", "<ul data-source-offset=\"0\"><li>parent<ul><li>child</li></ul></li><li>sibling</li></ul>", body("- parent\n  - child\n- sibling"));
        equal("mixed ordered and bullets", "<ol data-source-offset=\"0\"><li>first<ul><li>bullet<ol><li>number</li></ol></li></ul></li><li>second</li></ol>", body("1. first\n  - bullet\n    1. number\n2. second"));
        equal("nested type switch", "<ul data-source-offset=\"0\"><li>parent<ul><li>bullet</li></ul><ol><li>number</li></ol></li><li>tail</li></ul>", body("- parent\n  - bullet\n  1. number\n- tail"));
        equal("tasks nested and dedented", "<ul data-source-offset=\"0\"><li><input type=\"checkbox\" disabled checked> Parent<ul><li><input type=\"checkbox\" disabled> Child<ul><li>Deep</li></ul></li></ul></li><li>Tail</li></ul>", body("- [x] Parent\n  - [ ] Child\n    - Deep\n- Tail"));
        equal("dedent to intermediate then root", "<ul data-source-offset=\"0\"><li>A<ul><li>B<ul><li>C</li></ul></li><li>D</li></ul></li><li>E</li></ul>", body("- A\n  - B\n    - C\n  - D\n- E"));
        equal("flat lists separated by blank line", "<ul data-source-offset=\"0\"><li>a</li><li>b</li></ul><ol data-source-offset=\"9\"><li>c</li><li>d</li></ol>", body("- a\n- b\n\n1. c\n2. d"));
        equal("flat type switch starts new root", "<ul data-source-offset=\"0\"><li>a</li></ul><ol data-source-offset=\"4\"><li>b</li></ol>", body("- a\n1. b"));
        equal("CRLF tabs and escapes", "<ul data-source-offset=\"0\"><li>top<ul><li>&lt;tag&gt; &amp; *literal*</li><li>peer</li></ul></li><li>end</li></ul>", body("- top\r\n\t- <tag> & \\*literal\\*\r\n    - peer\r\n- end"));
        StringBuilder deep = new StringBuilder();
        for (int level=0; level<200; level++) {
            if (level>0) deep.append('\n');
            for (int j=0; j<level; j++) deep.append('\t');
            deep.append("- item");
        }
        deep.append("\n- tail");
        String deepHtml=body(deep.toString());
        equal("deep list openings", "200", Integer.toString(count(deepHtml,"<ul")));
        equal("deep list closings", "200", Integer.toString(count(deepHtml,"</ul>")));
        equal("deep list items", "201", Integer.toString(count(deepHtml,"<li>")));
        equal("deep dedent last sibling", "true", Boolean.toString(deepHtml.endsWith("<li>tail</li></ul>")));
        System.out.println("PASS " + checks + " independent nested-list body/structure assertions");
    }
}
