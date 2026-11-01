package com.example.markdowndesk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operator-owned: renders sync-sensitive documents and emits a JavaScript prelude
 * (CASES) holding each page's actual script text, its data-source-offset blocks in DOM
 * order, and source length. footnote_sync.cjs executes those real page scripts.
 */
public final class FootnoteSyncDump {
    static final String[] DOCS = {
        // footnote-free controls (current behavior must be preserved)
        "# Title\n\nPara one.\n\nPara two.\n\n- a\n- b\n",
        "| a | b |\n|---|---|\n| 1 | 2 |\n\n> q\n\nEnd.\n",
        // definitions at the end (the usual layout)
        "P1[^a] text.\n\nP2 longer text here.\n\n[^a]: Note A.\n",
        // definition in the middle
        "P1[^a]\n\n[^a]: A\n\nP2 long paragraph text\n\nP3 tail\n",
        // definitions at the top
        "[^a]: A\n[^b]: B\n\nP1[^a]\n\nP2[^b]\n\n## H\n\nP3\n",
    };

    private static String js(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20 || c > 0x7e) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    public static void main(String[] args) throws Exception {
        StringBuilder out = new StringBuilder("const CASES=[");
        Pattern script = Pattern.compile("<script>(.*?)</script>", Pattern.DOTALL);
        Pattern block = Pattern.compile("data-source-offset=\"(\\d+)\"");
        for (int d = 0; d < DOCS.length; d++) {
            String html = MarkdownRenderer.render(DOCS[d], UiTheme.palette(0, false), 18);
            StringBuilder scripts = new StringBuilder();
            Matcher s = script.matcher(html);
            while (s.find()) scripts.append(s.group(1)).append(";\n");
            String body = html.substring(html.indexOf("<body>"));
            StringBuilder offs = new StringBuilder();
            Matcher m = block.matcher(body.replaceAll("(?s)<script>.*?</script>", ""));
            while (m.find()) offs.append(offs.length() == 0 ? "" : ",").append(m.group(1));
            out.append(d == 0 ? "" : ",").append("{doc:").append(d).append(",length:").append(DOCS[d].length())
               .append(",offsets:[").append(offs).append("],script:").append(js(scripts.toString())).append("}");
        }
        out.append("];\n");
        Files.write(Paths.get(args[0]), out.toString().getBytes(StandardCharsets.UTF_8));
    }
}
