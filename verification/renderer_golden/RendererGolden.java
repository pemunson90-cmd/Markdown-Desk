package com.example.markdowndesk;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independently written host oracles for the Markdown body and palette CSS. */
public final class RendererGolden {
    private static int checks;
    private static final String[] LINK_CSS = {"B9552E", "E7956F", "9D4D2D", "78B891", "B64C86", "B873FF", "D06A8B", "5CC6C7", "45E6FF"};
    private static final String[] NAMES = {"Paper", "Midnight", "Sepia", "Forest", "Bubblegum", "Amethyst", "Black Rose", "Ocean", "Arcade"};
    private static final String[] COLORS = {
        "FBF8F2 28231E 746D64 F0ECE5 DED7CE C85C32 light",
        "12110F F4EFE7 ABA49A 211F1B 393630 E7956F dark",
        "F2E6CF 392F24 746654 E9DBC3 D3C1A5 9D4D2D light",
        "101714 E9F1EA 9FB1A5 1B2821 34483B 78B891 dark",
        "FFF5FB 3C2435 8A657D FCE6F2 E8B9D2 E45FA7 light",
        "120E18 F6EEFF BFAFD0 21182C 4B3961 B873FF dark",
        "140E11 F7EEF1 B7A0A8 24171C 4A343B D06A8B dark",
        "0C1518 EDF8FA 9CB8BF 14262C 31515A 5CC6C7 dark",
        "0D0B15 F8F4FF AFA4C7 18152B 4B3E67 45E6FF dark"
    };

    private static void equal(String label, String expected, String actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(label + " expected <" + expected + ">, got <" + actual + ">");
    }
    private static void has(String label, String actual, String fragment) {
        checks++;
        if (!actual.contains(fragment)) throw new AssertionError(label + " missing <" + fragment + ">");
    }
    private static String body(String markdown) {
        String html = MarkdownRenderer.render(markdown, UiTheme.palette(0, false), 18);
        return html.substring(html.indexOf("<body>") + 6, html.indexOf("<script>"));
    }
    private static String hex(int value) { return String.format(Locale.ROOT, "%06X", value & 0xffffff); }
    private static double channel(int rgb, int shift) {
        double c = ((rgb >> shift) & 255) / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
    private static double luminance(int rgb) {
        return .2126 * channel(rgb, 16) + .7152 * channel(rgb, 8) + .0722 * channel(rgb, 0);
    }
    private static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
    }
    private static int cssHex(String html, String selector, String property, int fallback) {
        int start = html.indexOf(selector + "{");
        if (start < 0) return fallback;
        int end = html.indexOf('}', start);
        Matcher value = Pattern.compile("(?:^|;)" + property + ":#([0-9A-Fa-f]{6})")
                .matcher(html.substring(start + selector.length() + 1, end));
        return value.find() ? 0xff000000 | Integer.parseInt(value.group(1), 16) : fallback;
    }
    private static void atLeastAA(String label, int foreground, int background) {
        checks++;
        double ratio = contrast(foreground, background);
        if (ratio < 4.5) throw new AssertionError(String.format(Locale.ROOT,
                "%s contrast %.3f:1 foreground #%06X background #%06X", label, ratio,
                foreground & 0xffffff, background & 0xffffff));
    }
    private static void audit(String name, String pair, int foreground, int background) {
        double ratio = contrast(foreground, background);
        System.out.printf(Locale.ROOT, "CONTRAST %-10s %-14s %.2f:1 %s%n", name, pair, ratio, ratio >= 4.5 ? "AA" : "BELOW-AA");
    }
    public static void main(String[] args) {
        equal("headings and offsets", "<h1 data-source-offset=\"0\">One &amp; Two</h1><h2 data-source-offset=\"13\">Deux</h2><h6 data-source-offset=\"22\">六</h6>", body("# One & Two\n\n## Deux\n\n###### 六"));
        equal("ordered and task lists", "<ol data-source-offset=\"0\"><li>First</li><li>Second</li></ol><ul data-source-offset=\"20\"><li><input type=\"checkbox\" disabled checked> Done</li><li><input type=\"checkbox\" disabled> Open</li></ul>", body("1. First\n2. Second\n\n- [x] Done\n- [ ] Open"));
        equal("table structure and escaped pipe", "<div class=\"table-scroll\" data-source-offset=\"0\"><table><thead><tr><th>A</th><th>B</th></tr></thead><tbody><tr><td>a|b</td><td>&lt;x&gt;</td></tr></tbody></table></div>", body("| A | B |\n| --- | --- |\n| a\\|b | <x> |"));
        equal("escaping and inline code", "<p data-source-offset=\"0\">&lt;tag&gt; &amp; &quot;quote&quot; &#39;single&#39; *literal* <code>&lt;b&gt;&amp;</code> <strong>bold</strong></p>", body("<tag> & \"quote\" 'single' \\*literal\\* `<b>&` **bold**"));
        equal("fenced code literal and language", "<pre data-source-offset=\"0\"><code data-language=\"java\">if (a &lt; b &amp;&amp; c) {\n  *literal*\n}</code></pre>", body("```java\nif (a < b && c) {\n  *literal*\n}\n```"));
        equal("unicode and link policy", "<p data-source-offset=\"0\">Été 雪 😀 <a href=\"https://example.org/a?x=1&amp;y=2\">site</a> <span class=\"link\">local</span> <span class=\"blocked-image\">🖼 alt (remote image blocked)</span></p>", body("Été 雪 😀 [site](https://example.org/a?x=1&y=2) [local](note.md) ![alt](https://example.org/p.png)"));
        equal("LF line break and paragraph", "<p data-source-offset=\"0\">a<br>b</p><p data-source-offset=\"5\">c</p>", body("a\nb\n\nc\n"));
        equal("CRLF source offsets", "<h1 data-source-offset=\"0\">A</h1><p data-source-offset=\"7\">B<br>C</p>", body("# A\r\n\r\nB\r\nC"));
        for (int i = 0; i < NAMES.length; i++) {
            UiTheme.Palette p = UiTheme.palette(i, false);
            String[] expected = COLORS[i].split(" ");
            equal("palette name " + i, NAMES[i], p.name);
            equal("palette colors " + i, String.join(" ", expected).substring(0, String.join(" ", expected).lastIndexOf(' ')), String.join(" ", hex(p.canvas), hex(p.text), hex(p.muted), hex(p.code), hex(p.border), hex(p.accent)));
            equal("palette scheme " + i, expected[6], p.dark ? "dark" : "light");
            audit(p.name, "body/canvas", p.text, p.canvas);
            audit(p.name, "muted/canvas", p.muted, p.canvas);
            audit(p.name, "rendered link/canvas", 0xff000000 | Integer.parseInt(LINK_CSS[i], 16), p.canvas);
            if (contrast(0xff000000 | Integer.parseInt(LINK_CSS[i], 16), p.canvas) < 4.5) throw new AssertionError("link contrast " + p.name);
            String contexts = MarkdownRenderer.render("| [header](https://example.com) | ![alt](https://example.com/i.png) |\n| --- | --- |\n| x | y |\n\n[`inline`](https://example.com)\n\n> `quoted`\n\n> [`linked`](https://example.com)", p, 18);
            has("two-column linked header " + i, contexts, "<th><a href=\"https://example.com\">header</a></th>");
            has("blocked image in table header " + i, contexts, "<th><span class=\"blocked-image\">🖼 alt (remote image blocked)</span></th>");
            has("linked inline code " + i, contexts, "<a href=\"https://example.com\"><code>inline</code></a>");
            has("quoted inline code " + i, contexts, "<code>quoted</code></blockquote>");
            has("linked code inside quote " + i, contexts, "<code>linked</code></a></blockquote>");
            int baseLink = cssHex(contexts, "a,.link", "color", 0);
            int codeBackground = cssHex(contexts, "code", "background", 0);
            int headerBackground = cssHex(contexts, "th", "background", 0);
            int contextLink = cssHex(contexts, "th a,th .link,a code,.link code", "color", baseLink);
            int quoteColor = cssHex(contexts, "blockquote", "color", 0);
            int quotedCode = cssHex(contexts, "blockquote code", "color", quoteColor);
            int blockedBase = cssHex(contexts, ".blocked-image", "color", 0);
            int headerBlocked = cssHex(contexts, "th .blocked-image", "color", blockedBase);
            checks++;
            if (baseLink == 0 || codeBackground == 0 || headerBackground == 0 || quoteColor == 0 || blockedBase == 0)
                throw new AssertionError("missing rendered CSS colors for " + p.name);
            atLeastAA(p.name + " header link/code", contextLink, headerBackground);
            atLeastAA(p.name + " linked inline code/code", contextLink, codeBackground);
            atLeastAA(p.name + " quoted inline code/code", quotedCode, codeBackground);
            atLeastAA(p.name + " blocked image in header/code", headerBlocked, headerBackground);
            if (contexts.contains("blockquote code{") && contexts.contains("th a,th .link,a code,.link code{")) {
                checks++;
                if (contexts.indexOf("th a,th .link,a code,.link code{") < contexts.indexOf("blockquote code{"))
                    throw new AssertionError("linked code cascade order " + p.name);
            }
            for (float size : new float[]{11, 12, 18, 32, 33}) {
                String html = MarkdownRenderer.render("# Title", p, size);
                float applied = Math.max(12, Math.min(32, size));
                has("canvas CSS " + i, html, "html{color-scheme:" + expected[6] + ";scroll-behavior:auto;background:#" + expected[0]);
                has("text CSS " + i, html, "color:#" + expected[1] + ";font-family:Georgia");
                has("link CSS " + i, html, "a,.link{color:#" + LINK_CSS[i] + "}");
                has("reading size " + i + "/" + size, html, "font-size:" + String.format(Locale.ROOT, "%.1f", applied) + "px;line-height:1.72");
                has("heading body " + i, html, "<h1 data-source-offset=\"0\">Title</h1>");
                if (size == 11 || size == 12) {
                    has("minimum h1 " + i, html, "h1{font-size:28.0px");
                    has("minimum h2 " + i, html, "h2{font-size:24.0px");
                    has("minimum h3 " + i, html, "h3{font-size:21.0px");
                }
                if (size == 32 || size == 33) {
                    has("maximum h1 " + i, html, "h1{font-size:34.0px");
                    has("maximum h2 " + i, html, "h2{font-size:29.0px");
                    has("maximum h3 " + i, html, "h3{font-size:25.0px");
                }
            }
        }
        System.out.println("PASS " + checks + " renderer/palette assertions; 9 palettes x 5 reading sizes; contrast observations above");
    }
}
