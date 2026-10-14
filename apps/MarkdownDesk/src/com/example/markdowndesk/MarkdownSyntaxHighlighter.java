package com.example.markdowndesk;

import android.graphics.Color;
import android.text.Editable;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public final class MarkdownSyntaxHighlighter {
    private static final int MAX_HIGHLIGHT_LENGTH = 1500000;
    private static final int MAX_LOCAL_LENGTH = 65536;
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

    /** One editor's bounded pending range. Raw global markers conservatively require a full pass. */
    public static final class State {
        private CharSequence target;
        private int length, dirtyStart, dirtyEnd;
        private boolean initialized, full = true, dirty, globalMarker, dark;

        public void reset() {
            target = null;
            initialized = false;
            full = true;
            dirty = false;
        }

        public void changed(CharSequence text, int start, int before, int count) {
            int nextLength = text.length();
            if (target != text || !initialized || start < 0 || before < 0 || count < 0
                    || start > length || before > length - start || start > nextLength
                    || count > nextLength - start || (long) length - before + count != nextLength) {
                target = text;
                length = nextLength;
                full = true;
                dirty = false;
                return;
            }
            if ((length > MAX_HIGHLIGHT_LENGTH) != (nextLength > MAX_HIGHLIGHT_LENGTH)) full = true;
            length = nextLength;
            if (full || globalMarker || nextLength > MAX_HIGHLIGHT_LENGTH || count > MAX_LOCAL_LENGTH) {
                full = true;
                return;
            }
            if (hasGlobalMarker(text, Math.max(0, start - 3), (int) Math.min(nextLength, (long) start + count + 3))) {
                full = true;
                return;
            }
            if (dirty) {
                int removedEnd = start + before;
                int delta = count - before;
                dirtyStart = dirtyStart < start ? dirtyStart : dirtyStart > removedEnd ? dirtyStart + delta : start;
                dirtyEnd = dirtyEnd < start ? dirtyEnd : dirtyEnd > removedEnd ? dirtyEnd + delta : start + count;
                dirtyStart = Math.min(dirtyStart, start);
                dirtyEnd = Math.max(dirtyEnd, start + count);
            } else {
                dirtyStart = start;
                dirtyEnd = start + count;
                dirty = true;
            }
            if (dirtyEnd - dirtyStart > MAX_LOCAL_LENGTH) full = true;
        }

        public void apply(Editable text, boolean nextDark) {
            if (target != text || !initialized || dark != nextDark || length != text.length()) full = true;
            if (!full && !dirty) return;
            // A full pass puts owned styles after existing equal-priority styles.
            // Search/IME styles can be added without a text callback, so retained
            // syntax on untouched lines would otherwise have different precedence.
            if (!full && hasForeignStyle(text)) full = true;
            int start = 0, end = text.length();
            if (!full) {
                start = dirtyStart;
                end = dirtyEnd;
                while (start > 0 && text.charAt(start - 1) != '\n' && end - start <= MAX_LOCAL_LENGTH) start--;
                while (end < text.length() && end - start <= MAX_LOCAL_LENGTH) {
                    if (text.charAt(end++) == '\n') break;
                }
                if (end - start > MAX_LOCAL_LENGTH) full = true;
            }
            if (full) {
                MarkdownSyntaxHighlighter.apply(text, nextDark);
                globalMarker = text.length() <= MAX_HIGHLIGHT_LENGTH && hasGlobalMarker(text, 0, text.length());
            } else {
                removeSyntax(text, start, end, false);
                applyPatterns(text, nextDark, start, end);
            }
            target = text;
            length = text.length();
            initialized = true;
            dark = nextDark;
            full = false;
            dirty = false;
        }
    }

    private static boolean hasForeignStyle(Editable text) {
        for (CharacterStyle span : text.getSpans(0, text.length(), CharacterStyle.class)) {
            if (!(span instanceof SyntaxColorSpan) && !(span instanceof SyntaxStyleSpan)
                    && !(span instanceof SyntaxBackgroundSpan)) return true;
        }
        return false;
    }

    private static boolean hasGlobalMarker(CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if ((c == '`' || c == '~') && i + 2 < text.length()
                    && text.charAt(i + 1) == c && text.charAt(i + 2) == c) return true;
            if (c == '<' && i + 3 < text.length() && text.charAt(i + 1) == '!'
                    && text.charAt(i + 2) == '-' && text.charAt(i + 3) == '-') return true;
        }
        return false;
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
        removeSyntax(editable, 0, editable.length(), true);
        if (editable.length() == 0 || editable.length() > MAX_HIGHLIGHT_LENGTH) {
            return;
        }
        applyPatterns(editable, z, 0, editable.length());
    }

    private static void removeSyntax(Editable text, int start, int end, boolean full) {
        for (Class<?> type : new Class<?>[]{SyntaxColorSpan.class, SyntaxStyleSpan.class, SyntaxBackgroundSpan.class}) {
            for (Object span : text.getSpans(start, end, type)) {
                int a = text.getSpanStart(span), b = text.getSpanEnd(span);
                // Android queries include touching spans; an adjacent unchanged line must retain them.
                if (full || (a < end && b > start) || (a == b && a >= start && a <= end)) text.removeSpan(span);
            }
        }
    }

    private static void applyPatterns(Editable editable, boolean z, int start, int end) {
        int color = Color.parseColor(z ? "#D0BCFF" : "#6750A4");
        int color2 = Color.parseColor(z ? "#9CCAFF" : "#315F9C");
        int color3 = Color.parseColor(z ? "#9A94A3" : "#706A75");
        int color4 = Color.parseColor(z ? "#F2C97D" : "#7A4C00");
        int color5 = Color.parseColor(z ? "#211F26" : "#F3EDF7");
        applyColor(editable, COMMENT, color3, start, end);
        applyColor(editable, QUOTE_LIST, color3, start, end);
        applyColor(editable, LINK, color2, start, end);
        applyStyleAndColor(editable, HEADING, 1, color, start, end);
        applyStyle(editable, BOLD, 1, start, end);
        applyStyle(editable, ITALIC, 2, start, end);
        applyColorBackground(editable, INLINE_CODE, color4, color5, start, end);
        applyColorBackground(editable, CODE, color4, color5, start, end);
    }

    private static Matcher matcher(Editable text, Pattern pattern, int start, int end) {
        return pattern.matcher(text).region(start, end).useAnchoringBounds(false).useTransparentBounds(true);
    }

    private static void applyColor(Editable editable, Pattern pattern, int i, int start, int end) {
        Matcher matcher = matcher(editable, pattern, start, end);
        while (matcher.find()) {
            editable.setSpan(new SyntaxColorSpan(i), matcher.start(), matcher.end(), 33);
        }
    }

    private static void applyStyle(Editable editable, Pattern pattern, int i, int start, int end) {
        Matcher matcher = matcher(editable, pattern, start, end);
        while (matcher.find()) {
            editable.setSpan(new SyntaxStyleSpan(i), matcher.start(), matcher.end(), 33);
        }
    }

    private static void applyStyleAndColor(Editable editable, Pattern pattern, int i, int i2, int start, int end) {
        Matcher matcher = matcher(editable, pattern, start, end);
        while (matcher.find()) {
            editable.setSpan(new SyntaxStyleSpan(i), matcher.start(), matcher.end(), 33);
            editable.setSpan(new SyntaxColorSpan(i2), matcher.start(), matcher.end(), 33);
        }
    }

    private static void applyColorBackground(Editable editable, Pattern pattern, int i, int i2, int start, int end) {
        Matcher matcher = matcher(editable, pattern, start, end);
        while (matcher.find()) {
            editable.setSpan(new SyntaxColorSpan(i), matcher.start(), matcher.end(), 33);
            editable.setSpan(new SyntaxBackgroundSpan(i2), matcher.start(), matcher.end(), 33);
        }
    }
}
