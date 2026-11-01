package com.example.markdowndesk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Operator-owned acceptance for reader footnotes (ROADMAP M6 "optional footnotes",
 * M5 GFM compatibility). Written before implementation; must fail on code28.
 * Contract: single-line definitions "[^label]: text"; references "[^label]" render as
 * inert numbered superscripts (no navigation href) numbered by first reference;
 * a footnotes section lists referenced definitions in that order, each item carrying
 * the definition line's data-source-offset. Documents without footnote syntax render
 * byte-identically to the code28 renderer in every theme.
 */
public final class FootnoteGolden {
    private static int checks;

    private static void ok(String label, boolean condition) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static void has(String label, String actual, String fragment) {
        ok(label + " missing <" + fragment + "> in <" + actual + ">", actual.contains(fragment));
    }
    private static void lacks(String label, String actual, String fragment) {
        ok(label + " unexpectedly contains <" + fragment + "> in <" + actual + ">", !actual.contains(fragment));
    }
    private static String html(String markdown, int theme) {
        return MarkdownRenderer.render(markdown, UiTheme.palette(theme, theme == 1 || theme == 3 || theme >= 5), 18);
    }
    private static String body(String markdown) {
        String h = html(markdown, 0);
        return h.substring(h.indexOf("<body>") + 6, h.indexOf("<script>"));
    }
    private static int count(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }
    private static String sha(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format("%02x", x));
        return b.toString();
    }

    /** Documents with no footnote syntax; their rendering must not change. */
    static final String[] PLAIN = {
        "",
        "# Title\n\nPlain paragraph with *emphasis*, **strong**, `code` and [link](https://example.com).\n",
        "- one\n- two\n  - nested\n1. first\n2. second\n\n- [ ] task\n- [x] done\n",
        "| a | b |\n|---|---|\n| 1 | 2 |\n\n> quoted\n> lines\n\n---\n",
        "```java\nint[] a = new int[1]; // [x] not a task\n```\n\nArray a[1] and caret ^ and [label] alone.\n",
        "Unicode é中😀 text\r\nwith CRLF\r\n\r\n## Heading 2\r\n",
        "![img](local.png) and [rel](notes.md) and <b>raw</b> & entities\n",
        "Brackets [^ spaced] and [^] and x^2 and a[^b c] stay.\n",
    };

    public static void main(String[] args) throws Exception {
        Path baseline = Paths.get(args.length > 1 ? args[1] : "verification/footnotes/plain-baseline.sha256");
        if (args.length > 0 && args[0].equals("--write-baseline")) {
            List<String> lines = new ArrayList<>();
            for (int d = 0; d < PLAIN.length; d++)
                for (int t = 0; t < 9; t++) lines.add(d + " " + t + " " + sha(html(PLAIN[d], t)));
            Files.write(baseline, lines, StandardCharsets.UTF_8);
            System.out.println("wrote " + lines.size() + " baseline hashes");
            return;
        }

        // 1. Byte-identical rendering for footnote-free documents (all nine themes).
        List<String> expected = Files.readAllLines(baseline, StandardCharsets.UTF_8);
        ok("baseline size", expected.size() == PLAIN.length * 9);
        for (String line : expected) {
            String[] p = line.split(" ");
            int d = Integer.parseInt(p[0]), t = Integer.parseInt(p[1]);
            ok("plain document " + d + " theme " + t + " changed", sha(html(PLAIN[d], t)).equals(p[2]));
        }

        // 2. Basic references and definitions.
        String doc = "Claim[^1] and more[^note].\n\n[^1]: First *source*.\n[^note]: Second.\n";
        String b = body(doc);
        has("first ref", b, "<sup class=\"footnote-ref\">1</sup>");
        has("second ref", b, "<sup class=\"footnote-ref\">2</sup>");
        has("section", b, "<section class=\"footnotes\"");
        ok("one section", count(b, "<section class=\"footnotes\"") == 1);
        ok("two items in order", count(b, "<li data-source-offset=") >= 2
                && b.indexOf("First <em>source</em>.") >= 0 && b.indexOf("First <em>source</em>.") < b.indexOf("Second."));
        has("definition inline markdown", b, "First <em>source</em>.");
        lacks("raw ref", b, "[^1]");
        lacks("raw named ref", b, "[^note]");
        lacks("no navigation href", b, "href=\"#");
        ok("section after body text", b.indexOf("<section class=\"footnotes\"") > b.indexOf("Claim"));
        int def1 = doc.indexOf("[^1]:"), def2 = doc.indexOf("[^note]:");
        has("item offset 1", b, "<li data-source-offset=\"" + def1 + "\"");
        has("item offset 2", b, "<li data-source-offset=\"" + def2 + "\"");
        has("paragraph offset kept", b, "data-source-offset=\"0\"");

        // 3. Numbering follows first reference, not definition order; repeated refs reuse number.
        String o = body("A[^b] B[^a] again[^b].\n\n[^a]: Alpha.\n[^b]: Beta.\n");
        ok("beta listed first", o.indexOf("Beta.") >= 0 && o.indexOf("Beta.") < o.indexOf("Alpha."));
        ok("repeat reuses number", count(o, "<sup class=\"footnote-ref\">1</sup>") == 2);
        ok("two numbers only", count(o, "<sup class=\"footnote-ref\">") == 3
                && !o.contains("<sup class=\"footnote-ref\">3</sup>"));

        // 4. Undefined references stay literal; unreferenced definitions are omitted.
        String u = body("x[^zzz] stays.\n");
        has("undefined literal", u, "x[^zzz] stays.");
        lacks("no section without definitions", u, "class=\"footnotes\"");
        String unref = body("Body only.\n\n[^lonely]: Never cited.\n");
        lacks("unreferenced not listed", unref, "Never cited.");
        lacks("unreferenced no section", unref, "class=\"footnotes\"");
        lacks("unreferenced def not raw", unref, "[^lonely]:");

        // 5. Code is verbatim.
        String c = body("Use `a[^1]` here[^1].\n\n```\n[^1]: not a definition\nb[^1]\n```\n\n[^1]: Real.\n");
        has("code span literal", c, "<code>a[^1]</code>");
        has("fenced literal def", c, "[^1]: not a definition");
        has("fenced literal ref", c, "b[^1]");
        ok("only one real ref", count(c, "<sup class=\"footnote-ref\">") == 1);
        has("real definition", c, "Real.");

        // 6. Escaping.
        String e = body("Ref[^1].\n\n[^1]: <script>alert(1)</script> & co\n");
        has("escaped def", e, "&lt;script&gt;");
        lacks("no raw script", e, "<script>alert");

        // 6b. A definition containing '|' directly after a table is a definition, not a table row.
        String tp = body("| a | b |\n|---|---|\n| x[^1] | y |\n[^1]: note a|b\n");
        has("pipe def listed", tp, "note a|b</li>");
        lacks("pipe def not a row", tp, ": note a</td>");
        ok("pipe def one ref", count(tp, "<sup class=\"footnote-ref\">") == 1);

        // 7. Works in every theme and with CRLF line endings.
        String crlf = "Text[^1].\r\n\r\n[^1]: Windows note.\r\n";
        for (int t = 0; t < 9; t++) {
            String h = html(crlf, t);
            String bb = h.substring(h.indexOf("<body>") + 6, h.indexOf("<script>"));
            has("theme " + t + " ref", bb, "<sup class=\"footnote-ref\">1</sup>");
            has("theme " + t + " note", bb, "Windows note.");
            lacks("theme " + t + " CR leak", bb, "\r");
        }

        System.out.println("FootnoteGolden: " + checks + " checks PASS");
    }
}
