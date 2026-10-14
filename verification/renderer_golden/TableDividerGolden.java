package com.example.markdowndesk;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.regex.Pattern;

/** Rendered table behavior and bounded grammar comparison against the old short-input regex. */
public final class TableDividerGolden {
    private static final Pattern LEGACY = Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    private static int checks;
    private static void check(String label, boolean condition) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static String body(String markdown) {
        String html = MarkdownRenderer.render(markdown, UiTheme.palette(0, false), 18);
        return html.substring(html.indexOf("<body>") + 6, html.indexOf("<script>"));
    }
    private static int count(String text, String token) {
        int found=0, at=0;
        while ((at=text.indexOf(token,at))>=0) { found++; at+=token.length(); }
        return found;
    }
    private static void wideTable(int columns) {
        StringBuilder header=new StringBuilder(columns*2+1);
        StringBuilder divider=new StringBuilder(columns*4+1);
        StringBuilder row=new StringBuilder(columns*2+1);
        for (int i=0;i<columns;i++) {
            header.append("|h"); divider.append("|---"); row.append("|v");
        }
        header.append('|'); divider.append('|'); row.append('|');
        String rendered=body(header+"\n"+divider+"\n"+row);
        check(columns+" column table starts", rendered.startsWith("<div class=\"table-scroll\" data-source-offset=\"0\"><table>"));
        check(columns+" headers", count(rendered,"<th>")==columns);
        check(columns+" cells", count(rendered,"<td>")==columns);
        check(columns+" table closes", rendered.endsWith("</tbody></table></div>"));
    }
    public static void main(String[] args) throws Exception {
        wideTable(2000);
        wideTable(5000);
        String[] valid={"---|---", "|---|---|", " \t:---: | ---: ", "\t| :---: | --- |\t", "---|---|---"};
        String[] invalid={"---", "|---|", "---|--", "---|---||", ": ---|---", "---|---|oops", "\u2003---|---"};
        for (String divider:valid) {
            String rendered=body("A|B\n"+divider+"\na|b");
            check("valid table divider " + divider, rendered.contains("<table><thead><tr><th>A</th><th>B</th></tr></thead>"));
        }
        for (String divider:invalid) {
            String rendered=body("A|B\n"+divider+"\na|b");
            check("invalid table divider " + divider, !rendered.contains("<table>"));
        }
        Method parser=MarkdownRenderer.class.getDeclaredMethod("isTableDivider",String.class);
        parser.setAccessible(true);
        Random random=new Random(0x7ab1eL);
        String alphabet=" -:|\t\f\r\n\u000bX";
        for (int sample=0;sample<12000;sample++) {
            int length=random.nextInt(28);
            StringBuilder candidate=new StringBuilder(length);
            for (int i=0;i<length;i++) candidate.append(alphabet.charAt(random.nextInt(alphabet.length())));
            String text=candidate.toString();
            boolean old=LEGACY.matcher(text).matches();
            boolean now=(boolean)parser.invoke(null,text);
            check("legacy short grammar sample " + sample + " <" + text + ">",old==now);
        }
        String[] spaces={"", " ", "\t", "\f", "\r", "\n", "\u000b"};
        for (int sample=0;sample<2000;sample++) {
            int columns=2+random.nextInt(5);
            StringBuilder candidate=new StringBuilder(spaces[random.nextInt(spaces.length)]);
            if (random.nextBoolean()) candidate.append('|');
            for (int col=0;col<columns;col++) {
                candidate.append(spaces[random.nextInt(spaces.length)]);
                if (random.nextBoolean()) candidate.append(':');
                for (int dash=0, total=3+random.nextInt(5);dash<total;dash++) candidate.append('-');
                if (random.nextBoolean()) candidate.append(':');
                candidate.append(spaces[random.nextInt(spaces.length)]);
                if (col+1<columns) candidate.append('|');
            }
            if (random.nextBoolean()) candidate.append('|');
            candidate.append(spaces[random.nextInt(spaces.length)]);
            String text=candidate.toString();
            boolean old=LEGACY.matcher(text).matches();
            boolean now=(boolean)parser.invoke(null,text);
            check("seeded generated grammar sample "+sample+" <"+text+">", old==now);
        }
        System.out.println("PASS "+checks+" table-divider assertions (2k/5k rendered columns, manual grammar, 14k seeded short differential cases)");
    }
}
