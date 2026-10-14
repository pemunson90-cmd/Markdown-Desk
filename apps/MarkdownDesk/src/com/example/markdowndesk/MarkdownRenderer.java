package com.example.markdowndesk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public final class MarkdownRenderer {
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)(.*)$");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern ORDERED = Pattern.compile("^\\s*(\\d+)\\.\\s+(.+)$");
    private static final Pattern UNORDERED = Pattern.compile("^\\s*[-+*]\\s+(.+)$");
    private static final Pattern TASK = Pattern.compile("^\\[([ xX])]\\s+(.*)$");
    private static final Pattern TABLE_DIVIDER = Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    private static final Pattern CODE_SPAN = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^]\\n]*)]\\(([^)\\n]*)\\)");
    private static final Pattern LINK = Pattern.compile("(?<!!)\\[([^]\\n]+)]\\(([^)\\n]+)\\)");

    private MarkdownRenderer() {
    }

    public static String render(String str, boolean z, float f) {
        return render(str, UiTheme.palette(z ? 1 : 0, z), f);
    }

    static String render(String str, UiTheme.Palette palette, float f) {
        Pattern pattern;
        String str2;
        boolean z = palette.dark;
        String str3 = str == null ? "" : str;
        String[] strArrSplit = str3.split("\\n", -1);
        int[] iArrLineOffsets = lineOffsets(strArrSplit);
        StringBuilder sb = new StringBuilder(Math.max(256, str3.length() + 256));
        int i = 0;
        while (i < strArrSplit.length) {
            String strCr = cr(strArrSplit[i]);
            if (strCr.trim().isEmpty()) {
                i++;
            } else {
                int i2 = iArrLineOffsets[i];
                Matcher matcher = FENCE.matcher(strCr);
                boolean z2 = true;
                if (matcher.matches()) {
                    String strGroup = matcher.group(1);
                    String strTrim = matcher.group(2).trim();
                    StringBuilder sb2 = new StringBuilder();
                    i++;
                    while (i < strArrSplit.length && !cr(strArrSplit[i]).trim().startsWith(strGroup)) {
                        if (sb2.length() > 0) {
                            sb2.append('\n');
                        }
                        sb2.append(cr(strArrSplit[i]));
                        i++;
                    }
                    if (i < strArrSplit.length) {
                        i++;
                    }
                    sb.append("<pre data-source-offset=\"").append(i2).append("\"><code");
                    if (!strTrim.isEmpty()) {
                        sb.append(" data-language=\"").append(escape(strTrim)).append("\"");
                    }
                    sb.append(">").append(escape(sb2.toString())).append("</code></pre>");
                } else {
                    Matcher matcher2 = HEADING.matcher(strCr);
                    if (matcher2.matches()) {
                        int length = matcher2.group(1).length();
                        sb.append("<h").append(length).append(" data-source-offset=\"").append(i2).append("\">").append(inline(matcher2.group(2))).append("</h").append(length).append('>');
                        i++;
                    } else if (strCr.trim().matches("([-*_])(?:\\s*\\1){2,}")) {
                        sb.append("<hr data-source-offset=\"").append(i2).append("\">");
                        i++;
                    } else {
                        int i3 = i + 1;
                        if (i3 < strArrSplit.length && strCr.contains("|") && TABLE_DIVIDER.matcher(cr(strArrSplit[i3])).matches()) {
                            List<String> listCells = cells(strCr);
                            sb.append("<div class=\"table-scroll\" data-source-offset=\"").append(i2).append("\"><table><thead><tr>");
                            Iterator<String> it = listCells.iterator();
                            while (it.hasNext()) {
                                sb.append("<th>").append(inline(it.next().trim())).append("</th>");
                            }
                            sb.append("</tr></thead><tbody>");
                            i += 2;
                            while (i < strArrSplit.length) {
                                String strCr2 = cr(strArrSplit[i]);
                                if (strCr2.trim().isEmpty() || !strCr2.contains("|")) {
                                    break;
                                }
                                List<String> listCells2 = cells(strCr2);
                                sb.append("<tr>");
                                int i4 = 0;
                                while (i4 < listCells.size()) {
                                    sb.append("<td>").append(inline(i4 < listCells2.size() ? listCells2.get(i4).trim() : "")).append("</td>");
                                    i4++;
                                }
                                sb.append("</tr>");
                                i++;
                            }
                            sb.append("</tbody></table></div>");
                        } else if (strCr.matches("^\\s*>.*$")) {
                            sb.append("<blockquote data-source-offset=\"").append(i2).append("\">");
                            while (i < strArrSplit.length && cr(strArrSplit[i]).matches("^\\s*>.*$")) {
                                if (!z2) {
                                    sb.append("<br>");
                                }
                                sb.append(inline(cr(strArrSplit[i]).replaceFirst("^\\s*>\\s?", "")));
                                i++;
                                z2 = false;
                            }
                            sb.append("</blockquote>");
                        } else {
                            Matcher matcher3 = UNORDERED.matcher(strCr);
                            Matcher matcher4 = ORDERED.matcher(strCr);
                            if (matcher3.matches() || matcher4.matches()) {
                                boolean zMatches = matcher4.matches();
                                String str4 = zMatches ? "ol" : "ul";
                                sb.append('<').append(str4).append(" data-source-offset=\"").append(i2).append("\">");
                                while (i < strArrSplit.length) {
                                    if (zMatches) {
                                        pattern = ORDERED;
                                        str2 = strArrSplit[i];
                                    } else {
                                        pattern = UNORDERED;
                                        str2 = strArrSplit[i];
                                    }
                                    Matcher matcher5 = pattern.matcher(cr(str2));
                                    if (!matcher5.matches()) {
                                        break;
                                    }
                                    String strGroup2 = matcher5.group(zMatches ? 2 : 1);
                                    Matcher matcher6 = TASK.matcher(strGroup2);
                                    sb.append("<li>");
                                    if (matcher6.matches()) {
                                        sb.append("<input type=\"checkbox\" disabled");
                                        if (!matcher6.group(1).equals(" ")) {
                                            sb.append(" checked");
                                        }
                                        sb.append("> ").append(inline(matcher6.group(2)));
                                    } else {
                                        sb.append(inline(strGroup2));
                                    }
                                    sb.append("</li>");
                                    i++;
                                }
                                sb.append("</").append(str4).append('>');
                            } else {
                                StringBuilder sb3 = new StringBuilder();
                                while (i < strArrSplit.length) {
                                    String strCr3 = cr(strArrSplit[i]);
                                    if (strCr3.trim().isEmpty() || (sb3.length() > 0 && startsBlock(strArrSplit, i))) {
                                        break;
                                    }
                                    if (sb3.length() > 0) {
                                        sb3.append("<br>");
                                    }
                                    sb3.append(inline(strCr3));
                                    i++;
                                }
                                sb.append("<p data-source-offset=\"").append(i2).append("\">").append((CharSequence) sb3).append("</p>");
                            }
                        }
                    }
                }
            }
        }
        String strCss = css(palette.canvas);
        String strCss2 = css(palette.text);
        String strCss3 = css(palette.muted);
        String strCss4 = css(palette.code);
        String strCss5 = css(palette.border);
        String strCss6 = css(palette.accent);
        float fMax = Math.max(12.0f, Math.min(f, 32.0f));
        float h1 = Math.max(28.0f, Math.min(fMax * 1.36f, 34.0f));
        float h2 = Math.max(24.0f, Math.min(fMax * 1.22f, 29.0f));
        float h3 = Math.max(21.0f, Math.min(fMax * 1.12f, 25.0f));
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>html{color-scheme:" + (z ? "dark" : "light") + ";scroll-behavior:auto;background:" + strCss + ";-webkit-text-size-adjust:100%}body{box-sizing:border-box;max-width:720px;margin:0 auto;padding:38px 28px 28px;background:" + strCss + ";color:" + strCss2 + ";font-family:Georgia,'Times New Roman',serif;font-size:" + String.format(Locale.ROOT, "%.1f", Float.valueOf(fMax)) + "px;line-height:1.72;overflow-wrap:anywhere;-webkit-font-smoothing:antialiased;-webkit-text-size-adjust:100%}[data-source-offset]{scroll-margin-top:24px;border-radius:5px;transition:background .15s ease}h1,h2,h3,h4,h5,h6{font-family:system-ui,sans-serif;letter-spacing:-.018em;line-height:1.16;margin:1.6em 0 .55em}h1{font-size:" + String.format(Locale.ROOT, "%.1f", Float.valueOf(h1)) + "px;margin-top:.35em}h2{font-size:" + String.format(Locale.ROOT, "%.1f", Float.valueOf(h2)) + "px;border-bottom:1px solid " + strCss5 + ";padding-bottom:.28em}h3{font-size:" + String.format(Locale.ROOT, "%.1f", Float.valueOf(h3)) + "px}p{margin:.9em 0}a,.link{color:" + strCss6 + "}blockquote{margin:1em 0;padding:.15em 1em;border-left:4px solid " + strCss6 + ";color:" + strCss3 + "}pre{background:" + strCss4 + ";padding:18px;border:1px solid " + strCss5 + ";border-radius:14px;overflow-x:auto;white-space:pre;line-height:1.5}code{font-family:'SFMono-Regular',Consolas,monospace;background:" + strCss4 + ";padding:.12em .34em;border-radius:5px;font-size:.9em}pre code{padding:0}table{border-collapse:separate;border-spacing:0;min-width:100%;border:1px solid " + strCss5 + ";border-radius:12px;overflow:hidden}th,td{border-right:1px solid " + strCss5 + ";border-bottom:1px solid " + strCss5 + ";padding:10px 12px;text-align:left;white-space:nowrap}th{background:" + strCss4 + ";font-family:system-ui,sans-serif}tr:last-child td{border-bottom:0}th:last-child,td:last-child{border-right:0}.table-scroll{overflow-x:auto;margin:1.2em 0}ul,ol{padding-left:1.5em}li{margin:.25em 0}hr{border:0;border-top:1px solid " + strCss5 + ";margin:1.6em 0}.blocked-image{color:" + strCss3 + ";font-style:italic}</style></head><body>" + String.valueOf(sb) + syncScript() + "</body></html>";
    }

    private static int[] lineOffsets(String[] strArr) {
        int[] iArr = new int[strArr.length];
        int i = 0;
        int length = 0;
        while (i < strArr.length) {
            iArr[i] = length;
            length += strArr[i].length();
            i++;
            if (i < strArr.length) {
                length++;
            }
        }
        return iArr;
    }

    private static String syncScript() {
        return "<script>(function(){function blocks(){return Array.prototype.slice.call(document.querySelectorAll('[data-source-offset]'));}window.mdScrollToSource=function(off){var b=blocks(),best=null;for(var i=0;i<b.length;i++){var n=parseInt(b[i].dataset.sourceOffset||'0',10);if(n<=off)best=b[i];else break;}if(!best&&b.length)best=b[0];if(best)window.scrollTo(0,Math.max(0,best.offsetTop-16));};window.mdSourceAtViewport=function(){var b=blocks(),y=window.scrollY+24,best=b.length?b[0]:null;for(var i=0;i<b.length;i++){if(b[i].offsetTop<=y)best=b[i];else break;}return best?parseInt(best.dataset.sourceOffset||'0',10):0;};document.addEventListener('click',function(e){if(e.target.closest&&e.target.closest('a'))return;var n=e.target.closest?e.target.closest('[data-source-offset]'):null;if(n){location.href='mdsrc:'+n.dataset.sourceOffset;e.preventDefault();}},false);})();</script>";
    }

    private static boolean startsBlock(String[] strArr, int i) {
        String strCr = cr(strArr[i]);
        if (strCr.trim().isEmpty() || FENCE.matcher(strCr).matches() || HEADING.matcher(strCr).matches() || UNORDERED.matcher(strCr).matches() || ORDERED.matcher(strCr).matches() || strCr.matches("^\\s*>.*$")) {
            return true;
        }
        int i2 = i + 1;
        return i2 < strArr.length && strCr.contains("|") && TABLE_DIVIDER.matcher(cr(strArr[i2])).matches();
    }

    private static List<String> cells(String str) {
        String text = str.trim();
        if (text.startsWith("|")) text = text.substring(1);
        if (text.endsWith("|") && !isEscaped(text, text.length() - 1)) text = text.substring(0, text.length() - 1);
        ArrayList<String> cells = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                if (c == '|') cell.append('|');
                else { cell.append('\\'); cell.append(c); }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '|') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        if (escaped) cell.append('\\');
        cells.add(cell.toString());
        return cells;
    }

    private static boolean isEscaped(String text, int index) {
        int slashes = 0;
        for (int i = index - 1; i >= 0 && text.charAt(i) == '\\'; i--) slashes++;
        return (slashes & 1) == 1;
    }

    private static String inline(String source) {
        ArrayList<String> protectedFragments = new ArrayList<String>();
        String text = protectBackslashEscapes(source, protectedFragments);

        ArrayList<String> codeFragments = new ArrayList<String>();
        Matcher code = CODE_SPAN.matcher(text);
        StringBuffer codeBuffer = new StringBuffer();
        while (code.find()) {
            int index = codeFragments.size();
            codeFragments.add("<code>" + escape(code.group(1)) + "</code>");
            code.appendReplacement(codeBuffer, Matcher.quoteReplacement("\u0001CODE" + index + "\u0002"));
        }
        code.appendTail(codeBuffer);

        Matcher image = IMAGE.matcher(escape(codeBuffer.toString()));
        StringBuffer imageBuffer = new StringBuffer();
        while (image.find()) {
            image.appendReplacement(imageBuffer, Matcher.quoteReplacement("<span class=\"blocked-image\">🖼 " + (image.group(1).isEmpty() ? "Image" : image.group(1)) + " (remote image blocked)</span>"));
        }
        image.appendTail(imageBuffer);

        Matcher link = LINK.matcher(imageBuffer.toString());
        StringBuffer linkBuffer = new StringBuffer();
        while (link.find()) {
            String href = link.group(2).trim();
            String rendered;
            if (href.startsWith("https://") || href.startsWith("http://") || href.startsWith("mailto:")) {
                rendered = "<a href=\"" + href + "\">" + link.group(1) + "</a>";
            } else {
                rendered = "<span class=\"link\">" + link.group(1) + "</span>";
            }
            link.appendReplacement(linkBuffer, Matcher.quoteReplacement(rendered));
        }
        link.appendTail(linkBuffer);

        String rendered = linkBuffer.toString()
                .replaceAll("~~([^~\\n]+)~~", "<del>$1</del>")
                .replaceAll("\\*\\*([^*\\n]+)\\*\\*", "<strong>$1</strong>")
                .replaceAll("(?<![\\p{L}\\p{N}_])__([^_\\n]+)__(?![\\p{L}\\p{N}_])", "<strong>$1</strong>")
                .replaceAll("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)", "<em>$1</em>")
                .replaceAll("(?<![\\p{L}\\p{N}_])_([^_\\n]+)_(?![\\p{L}\\p{N}_])", "<em>$1</em>");

        for (int i = 0; i < codeFragments.size(); i++) {
            rendered = rendered.replace("\u0001CODE" + i + "\u0002", codeFragments.get(i));
        }
        for (int i = 0; i < protectedFragments.size(); i++) {
            rendered = rendered.replace("\u0001ESC" + i + "\u0002", protectedFragments.get(i));
        }
        return rendered;
    }

    private static String protectBackslashEscapes(String source, List<String> protectedFragments) {
        StringBuilder out = new StringBuilder(source.length());
        String escapable = "\\`*_{}[]()#+-.!|>~";
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\' && i + 1 < source.length() && escapable.indexOf(source.charAt(i + 1)) >= 0) {
                char literal = source.charAt(++i);
                int index = protectedFragments.size();
                protectedFragments.add(escape(Character.toString(literal)));
                out.append('\u0001').append("ESC").append(index).append('\u0002');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String escape(String str) {
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String css(int i) {
        return String.format(Locale.ROOT, "#%06X", Integer.valueOf(i & 16777215));
    }

    private static String cr(String str) {
        return str.endsWith("\r") ? str.substring(0, str.length() - 1) : str;
    }
}
