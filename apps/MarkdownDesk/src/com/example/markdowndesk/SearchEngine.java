package com.example.markdowndesk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public final class SearchEngine {
    private SearchEngine() {
    }

    public static final class Options {
        public final boolean caseSensitive;
        public final boolean regex;
        public final boolean wholeWord;

        public Options(boolean z, boolean z2, boolean z3) {
            this.caseSensitive = z;
            this.wholeWord = z2;
            this.regex = z3;
        }
    }

    public static final class Match {
        public final int end;
        public final int line;
        public final int start;

        public Match(int i, int i2, int i3) {
            this.start = i;
            this.end = i2;
            this.line = i3;
        }
    }

    public static final class Replacement {
        public final int count;
        public final String text;

        Replacement(String str, int i) {
            this.text = str;
            this.count = i;
        }
    }

    public static List<Match> findAll(String str, String str2, Options options) {
        ArrayList arrayList = new ArrayList();
        if (str == null || str2 == null || str2.isEmpty()) {
            return arrayList;
        }
        Matcher matcher = compile(str2, options).matcher(str);
        int i = 1;
        int iStart = 0;
        while (matcher.find()) {
            while (iStart < matcher.start()) {
                if (str.charAt(iStart) == '\n') {
                    i++;
                }
                iStart++;
            }
            arrayList.add(new Match(matcher.start(), matcher.end(), i));
            iStart = matcher.start();
        }
        return arrayList;
    }

    public static Replacement replaceAll(String str, String str2, String str3, Options options) {
        if (str == null) {
            str = "";
        }
        if (str3 == null) {
            str3 = "";
        }
        int i = 0;
        if (str2 == null || str2.isEmpty()) {
            return new Replacement(str, 0);
        }
        Matcher matcher = compile(str2, options).matcher(str);
        StringBuffer stringBuffer = new StringBuffer(str.length());
        while (matcher.find()) {
            matcher.appendReplacement(stringBuffer, options.regex ? str3 : Matcher.quoteReplacement(str3));
            i++;
        }
        matcher.appendTail(stringBuffer);
        return new Replacement(stringBuffer.toString(), i);
    }

    public static boolean matchesSelection(String str, String str2, Options options) {
        if (str == null || str2 == null || str2.isEmpty()) {
            return false;
        }
        return compile(str2, options).matcher(str).matches();
    }

    private static Pattern compile(String str, Options options) {
        if (!options.regex) {
            str = Pattern.quote(str);
        }
        if (options.wholeWord) {
            str = "(?<![\\p{L}\\p{N}_])(?:" + str + ")(?![\\p{L}\\p{N}_])";
        }
        return Pattern.compile(str, !options.caseSensitive ? 74 : 8);
    }
}
