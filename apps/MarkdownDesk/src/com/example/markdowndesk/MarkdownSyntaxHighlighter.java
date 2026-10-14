package com.example.markdowndesk;

import android.graphics.Color;
import android.text.Editable;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public final class MarkdownSyntaxHighlighter {
    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})([\\t ]+)(.*)$");
    private static final Pattern CODE = Pattern.compile("(?s)(^|\\n)[\\t ]*(```|~~~).*?(?:\\n[\\t ]*\\2[\\t ]*(?=\\n|$)|$)");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\n]+`");
    private static final Pattern LINK = Pattern.compile("!?\\[[^]\\n]*]\\([^\\n)]*\\)");
    private static final Pattern BOLD = Pattern.compile("(?:\\*\\*(?=\\S)(.+?\\S)\\*\\*|(?<![\\p{L}\\p{N}_])__(?=\\S)(.+?\\S)__(?![\\p{L}\\p{N}_]))");
    private static final Pattern ITALIC = Pattern.compile("(?:(?<!\\*)\\*(?=\\S)(.+?\\S)\\*(?!\\*)|(?<![\\p{L}\\p{N}_])_(?=\\S)(.+?\\S)_(?![\\p{L}\\p{N}_]))");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern QUOTE_LIST = Pattern.compile("(?m)^[\\t ]*(?:>|[-+*]|\\d+\\.)[\\t ]+");

    private MarkdownSyntaxHighlighter() {
    }

    private static final class SyntaxColorSpan extends ForegroundColorSpan {
        SyntaxColorSpan(int i) {
            super(i);
        }
    }

    private static final class SyntaxStyleSpan extends StyleSpan {
        SyntaxStyleSpan(int i) {
            super(i);
        }
    }

    private static final class SyntaxBackgroundSpan extends BackgroundColorSpan {
        SyntaxBackgroundSpan(int i) {
            super(i);
        }
    }

    public static void apply(Editable editable, boolean z) {
        for (SyntaxColorSpan syntaxColorSpan : (SyntaxColorSpan[]) editable.getSpans(0, editable.length(), SyntaxColorSpan.class)) {
            editable.removeSpan(syntaxColorSpan);
        }
        for (SyntaxStyleSpan syntaxStyleSpan : (SyntaxStyleSpan[]) editable.getSpans(0, editable.length(), SyntaxStyleSpan.class)) {
            editable.removeSpan(syntaxStyleSpan);
        }
        for (SyntaxBackgroundSpan syntaxBackgroundSpan : (SyntaxBackgroundSpan[]) editable.getSpans(0, editable.length(), SyntaxBackgroundSpan.class)) {
            editable.removeSpan(syntaxBackgroundSpan);
        }
        if (editable.length() == 0 || editable.length() > 1500000) {
            return;
        }
        int color = Color.parseColor(z ? "#D0BCFF" : "#6750A4");
        int color2 = Color.parseColor(z ? "#9CCAFF" : "#315F9C");
        int color3 = Color.parseColor(z ? "#9A94A3" : "#706A75");
        int color4 = Color.parseColor(z ? "#F2C97D" : "#7A4C00");
        int color5 = Color.parseColor(z ? "#211F26" : "#F3EDF7");
        applyColor(editable, COMMENT, color3);
        applyColor(editable, QUOTE_LIST, color3);
        applyColor(editable, LINK, color2);
        applyStyleAndColor(editable, HEADING, 1, color);
        applyStyle(editable, BOLD, 1);
        applyStyle(editable, ITALIC, 2);
        applyColorBackground(editable, INLINE_CODE, color4, color5);
        applyColorBackground(editable, CODE, color4, color5);
    }

    private static void applyColor(Editable editable, Pattern pattern, int i) {
        Matcher matcher = pattern.matcher(editable);
        while (matcher.find()) {
            editable.setSpan(new SyntaxColorSpan(i), matcher.start(), matcher.end(), 33);
        }
    }

    private static void applyStyle(Editable editable, Pattern pattern, int i) {
        Matcher matcher = pattern.matcher(editable);
        while (matcher.find()) {
            editable.setSpan(new SyntaxStyleSpan(i), matcher.start(), matcher.end(), 33);
        }
    }

    private static void applyStyleAndColor(Editable editable, Pattern pattern, int i, int i2) {
        Matcher matcher = pattern.matcher(editable);
        while (matcher.find()) {
            editable.setSpan(new SyntaxStyleSpan(i), matcher.start(), matcher.end(), 33);
            editable.setSpan(new SyntaxColorSpan(i2), matcher.start(), matcher.end(), 33);
        }
    }

    private static void applyColorBackground(Editable editable, Pattern pattern, int i, int i2) {
        Matcher matcher = pattern.matcher(editable);
        while (matcher.find()) {
            editable.setSpan(new SyntaxColorSpan(i), matcher.start(), matcher.end(), 33);
            editable.setSpan(new SyntaxBackgroundSpan(i2), matcher.start(), matcher.end(), 33);
        }
    }
}
