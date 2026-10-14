package com.example.markdowndesk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public final class MarkdownStructure {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})[\\t ]+(.+?)\\s*$");

    private MarkdownStructure() {
    }

    public static final class Heading {
        public final int level;
        public final int line;
        public final int offset;
        public final String text;

        Heading(int i, String str, int i2, int i3) {
            this.level = i;
            this.text = str;
            this.offset = i2;
            this.line = i3;
        }
    }

    public static List<Heading> headings(String str) {
        ArrayList<Heading> arrayList = new ArrayList<Heading>();
        if (str == null || str.isEmpty()) {
            return arrayList;
        }
        String fence = null;
        int i = 0;
        boolean inFence = false;
        int lineNumber = 1;
        while (i <= str.length()) {
            int iIndexOf = str.indexOf(10, i);
            if (iIndexOf < 0) {
                iIndexOf = str.length();
            }
            String strSubstring = str.substring(i, iIndexOf);
            if (strSubstring.endsWith("\r")) {
                strSubstring = strSubstring.substring(0, strSubstring.length() - 1);
            }
            String strTrim = strSubstring.trim();
            if (strTrim.startsWith("```") || strTrim.startsWith("~~~")) {
                String marker = strTrim.substring(0, 3);
                if (!inFence) {
                    inFence = true;
                    fence = marker;
                } else if (marker.equals(fence)) {
                    fence = null;
                    inFence = false;
                }
            } else if (!inFence) {
                Matcher matcher = HEADING.matcher(strSubstring);
                if (matcher.matches()) {
                    arrayList.add(new Heading(matcher.group(1).length(), cleanHeading(matcher.group(2)), i, lineNumber));
                }
            }
            if (iIndexOf == str.length()) {
                break;
            }
            i = iIndexOf + 1;
            lineNumber++;
        }
        return arrayList;
    }

    private static String cleanHeading(String str) {
        String text = str.replaceFirst("\\s+#+\\s*$", "").trim();
        text = text.replaceAll("`([^`]+)`", "$1")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("(?<![\\p{L}\\p{N}_])__([^_]+)__(?![\\p{L}\\p{N}_])", "$1")
                .replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "$1")
                .replaceAll("(?<![\\p{L}\\p{N}_])_([^_]+)_(?![\\p{L}\\p{N}_])", "$1")
                .replaceAll("~~([^~]+)~~", "$1");
        return text;
    }
}
