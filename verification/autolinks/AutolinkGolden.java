package com.example.markdowndesk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Operator-owned acceptance for Reading-mode autolinks (ROADMAP M5 GFM compatibility),
 * selected by Foundry goal-planning run 1053a59b (proposal gfm-autolinks). Written before
 * implementation; must fail on the footnotes release candidate 134e1eea.
 * Contract (GFM-style subset): bare http:// and https:// URLs and angle-bracket
 * <http(s)://...> / <mailto:...> autolinks render as <a href="X">X</a> with HTML-escaped
 * X; trailing sentence punctuation and unbalanced ')' stay outside; code, existing
 * explicit links, and other schemes (javascript:, ftp:, www.) are untouched. Documents
 * without autolink candidates render byte-identically in every theme.
 */
public final class AutolinkGolden {
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

    /** No autolink candidates (explicit links and lookalikes included); output must not change. */
    static final String[] PLAIN = {
        "",
        "# Title\n\nPlain paragraph with *emphasis*, **strong**, `code` and [link](https://example.com).\n",
        "[https://example.org](https://example.org) and [t](http://x.y/a?b=1&c=2) and [m](mailto:a@b.co)\n",
        "Code `https://example.com` and\n\n```\nhttps://example.com/fenced\n<https://example.com>\n```\n",
        "Not links: javascript:alert(1) ftp://host/x www.example.com http:/ https:// <notaurl> a<b>c\n",
        "| a | b |\n|---|---|\n| [x](https://e.x) | y |\n\n> quote\n\n- [ ] task\n",
        "Footnote[^1].\n\n[^1]: Plain note.\n",
        "Unicode é中😀 text\r\nwith CRLF\r\n",
    };

    public static void main(String[] args) throws Exception {
        Path baseline = Paths.get(args.length > 1 ? args[1] : "verification/autolinks/plain-baseline.sha256");
        if (args.length > 0 && args[0].equals("--write-baseline")) {
            List<String> lines = new ArrayList<>();
            for (int d = 0; d < PLAIN.length; d++)
                for (int t = 0; t < 9; t++) lines.add(d + " " + t + " " + sha(html(PLAIN[d], t)));
            Files.write(baseline, lines, StandardCharsets.UTF_8);
            System.out.println("wrote " + lines.size() + " baseline hashes");
            return;
        }

        // 1. Byte identity for documents without autolink candidates, all nine themes.
        List<String> expected = Files.readAllLines(baseline, StandardCharsets.UTF_8);
        ok("baseline size", expected.size() == PLAIN.length * 9);
        for (String line : expected) {
            String[] p = line.split(" ");
            int d = Integer.parseInt(p[0]), t = Integer.parseInt(p[1]);
            ok("plain document " + d + " theme " + t + " changed", sha(html(PLAIN[d], t)).equals(p[2]));
        }

        // 2. Bare URL with query string; escaping; trailing period outside.
        String b = body("See https://example.com/a?b=1&c=2.\n");
        has("bare url", b, "See <a href=\"https://example.com/a?b=1&amp;c=2\">https://example.com/a?b=1&amp;c=2</a>.</p>");
        has("http bare", body("Old http://example.net/x here\n"), "<a href=\"http://example.net/x\">http://example.net/x</a> here");

        // 3. Trailing punctuation and parentheses.
        has("comma", body("Go https://a.example/x, then\n"), "<a href=\"https://a.example/x\">https://a.example/x</a>, then");
        has("question", body("Is it https://a.example/q?\n"), "<a href=\"https://a.example/q\">https://a.example/q</a>?");
        has("paren wrap", body("(see https://a.example/p)\n"), "(see <a href=\"https://a.example/p\">https://a.example/p</a>)");
        has("balanced paren kept", body("https://en.wikipedia.org/wiki/Foo_(bar) x\n"),
                "<a href=\"https://en.wikipedia.org/wiki/Foo_(bar)\">https://en.wikipedia.org/wiki/Foo_(bar)</a> x");
        has("underscores intact", body("https://e.example/a_b_c\n"), "<a href=\"https://e.example/a_b_c\">https://e.example/a_b_c</a>");
        has("line start", body("https://start.example\n"), "<p data-source-offset=\"0\"><a href=\"https://start.example\">https://start.example</a></p>");

        // 4. Angle-bracket autolinks.
        has("angle https", body("Visit <https://example.com/x?y=1&z=2> now\n"),
                "Visit <a href=\"https://example.com/x?y=1&amp;z=2\">https://example.com/x?y=1&amp;z=2</a> now");
        has("angle mailto", body("Mail <mailto:a.b@example.co> please\n"),
                "Mail <a href=\"mailto:a.b@example.co\">mailto:a.b@example.co</a> please");
        lacks("angle brackets gone", body("<https://example.com>\n"), "&lt;https");

        // 5. Never inside code or existing links; never other schemes.
        String c = body("`https://in.code` and https://out.example\n");
        has("code verbatim", c, "<code>https://in.code</code>");
        ok("one anchor beside code", count(c, "<a href=") == 1);
        String e = body("[https://a.example](https://a.example) and [t](https://b.example)\n");
        ok("explicit links not nested", count(e, "<a href=") == 2 && !e.contains("<a href=\"https://a.example\"><a"));
        String j = body("javascript:alert(1) ftp://h/x www.example.com <javascript:alert(1)>\n");
        lacks("no unsafe or other-scheme anchors", j, "<a ");

        // 6. Contexts: emphasis, heading, table cell, list, blockquote, footnote definition.
        has("in strong", body("**https://s.example**\n"), "<strong><a href=\"https://s.example\">https://s.example</a></strong>");
        has("in heading", body("## Ref https://h.example\n"), "<a href=\"https://h.example\">https://h.example</a></h2>");
        has("in table", body("| a | b |\n|---|---|\n| https://t.example | y |\n"), "<td><a href=\"https://t.example\">https://t.example</a></td>");
        has("in list", body("- https://l.example\n"), "<a href=\"https://l.example\">https://l.example</a>");
        has("in quote", body("> https://q.example\n"), "<a href=\"https://q.example\">https://q.example</a>");
        has("in footnote", body("Note[^1].\n\n[^1]: Source https://f.example.\n"),
                "Source <a href=\"https://f.example\">https://f.example</a>.</li>");

        // 7. HTML injection safety: quotes and angle brackets can't escape the attribute.
        String inj = body("https://x.example/\"onmouseover=\"alert(1)\n");
        lacks("no raw quote in attribute", inj, "\"onmouseover=\"");
        lacks("no script", body("https://x.example/<script>alert(1)</script>\n"), "<script>alert");

        // 8. CRLF and every theme.
        for (int t = 0; t < 9; t++) {
            String h = html("Link https://theme.example\r\n", t);
            String bb = h.substring(h.indexOf("<body>") + 6, h.indexOf("<script>"));
            has("theme " + t, bb, "<a href=\"https://theme.example\">https://theme.example</a>");
            lacks("theme " + t + " CR", bb, "\r");
        }

        // 9. Robustness (independent review of Foundry release 262a79be): literal placeholder
        //    control characters in user text must never crash or duplicate links.
        String[] hostile = {"x \u0001AL7\u0002 https://a.example", "x \u0001AL99999999999\u0002 https://a.example",
                "x \u0001AL0\u0002 https://a.example", "\u0001AL0\u0002\u0001AL1\u0002 <https://b.example> https://c.example"};
        for (int h = 0; h < hostile.length; h++) {
            String out;
            try {
                out = body(hostile[h] + "\n");
            } catch (RuntimeException ex) {
                throw new AssertionError("hostile placeholder input " + h + " crashed: " + ex);
            }
            ok("hostile " + h + " anchors not duplicated", count(out, "<a href=\"https://a.example\">") <= 1
                    && count(out, "<a href=\"https://b.example\">") <= 1 && count(out, "<a href=\"https://c.example\">") <= 1);
            ok("hostile " + h + " real link kept", out.contains("https://"));
        }

        // 10. Trailing ')' trimming is linear: 100k closing parens render quickly (baseline ~20 ms).
        StringBuilder parens = new StringBuilder("https://p.example/x");
        for (int i = 0; i < 100_000; i++) parens.append(')');
        body(parens + "\n");  // warm up
        long t0 = System.nanoTime();
        String pb = body(parens + "\n");
        long ms = (System.nanoTime() - t0) / 1_000_000;
        ok("100k trailing parens took " + ms + " ms (limit 750)", ms < 750);
        has("parens trimmed", pb, "<a href=\"https://p.example/x\">https://p.example/x</a>)");

        System.out.println("AutolinkGolden: " + checks + " checks PASS");
    }
}
