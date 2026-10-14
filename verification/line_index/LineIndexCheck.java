package com.example.markdowndesk;

import java.util.Random;

class EditorCallbackSurface {
    int callbacks;
    EditorCallbackSurface() { onTextChanged("ctor\nvalue\n", 0, 0, 11); }
    protected void onTextChanged(CharSequence text, int start, int before, int count) { callbacks++; }
}

public final class LineIndexCheck {
    private static int assertions;
    static final class CountedText implements CharSequence {
        final String text;
        int reads;
        CountedText(String text) { this.text = text; }
        public int length() { return text.length(); }
        public char charAt(int i) { reads++; return text.charAt(i); }
        public CharSequence subSequence(int start, int end) { throw new AssertionError("snapshot"); }
        public String toString() { throw new AssertionError("snapshot"); }
    }
    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }
    private static void verify(LineIndexHarness editor, String text) {
        int line = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i > 0 && text.charAt(i - 1) == '\n') line++;
            check(editor.line(i) == line, "LF line at offset " + i + " in " + text);
        }
    }
    private static void replace(LineIndexHarness editor, StringBuilder text, int start, int removed, String inserted) {
        text.replace(start, start + removed, inserted);
        editor.onTextChanged(new CountedText(text.toString()), start, removed, inserted.length());
        verify(editor, text.toString());
    }
    public static void main(String[] args) {
        LineIndexHarness editor = new LineIndexHarness();
        verify(editor, "ctor\nvalue\n");
        String large = "alpha beta\n".repeat(20000);
        editor.onTextChanged(new CountedText(large), 0, 0, large.length());
        CountedText updated = new CountedText("x" + large);
        editor.onTextChanged(updated, 0, 0, 1);
        check(updated.reads <= 1, "single-character edit must only read inserted character; read " + updated.reads);
        check(editor.callbacks == 3, "delegate callback to superclass once");
        CountedText deleted = new CountedText(large);
        editor.onTextChanged(deleted, 0, 1, 0);
        check(deleted.reads == 0, "deletion reads no surviving characters");
        CountedText sameLength = new CountedText("A" + large.substring(1));
        editor.onTextChanged(sameLength, 0, 1, 1);
        check(sameLength.reads == 1, "same-length replacement reads inserted character only");
        StringBuilder text = new StringBuilder();
        editor.onTextChanged(text, 0, sameLength.length(), 0);
        verify(editor, "");
        for (String fixture : new String[]{"", "\n", "\n\n", "a\r\nb\r\n", "BOM\uFEFFé\ud83d\ude00\na\n", "abc\rdef"}) {
            replace(editor, text, 0, text.length(), fixture);
            replace(editor, text, 0, 0, "\n");
            replace(editor, text, text.length(), 0, "end\n");
            replace(editor, text, 0, Math.min(2, text.length()), "");
        }
        replace(editor, text, 0, text.length(), "\n".repeat(10000));
        replace(editor, text, 1, 9998, "\r\n".repeat(5000));
        replace(editor, text, 0, text.length(), "a\r\nb");
        replace(editor, text, 2, 1, ""); // Remove LF separately from CR.
        replace(editor, text, 2, 0, "\n");
        Random random = new Random(912733L);
        String[] inserts = {"", "a", "\n", "\r\n", "\n\n", "_é\ud83d\ude00\n", "abc"};
        for (int n = 0; n < 3000; n++) {
            int start = random.nextInt(text.length() + 1);
            int removed = random.nextInt(text.length() - start + 1);
            replace(editor, text, start, removed, inserts[random.nextInt(inserts.length)]);
        }
        // Invalid/inconsistent callbacks rebuild from the supplied authoritative text.
        for (int[] event : new int[][]{{-1, 0, 2}, {0, -1, 2}, {0, 0, -1}, {99, 0, 0}, {0, 999, 0}, {0, 0, 999}, {0, 0, 0}}) {
            String authoritative = "\nalpha\r\nbeta\n";
            editor.onTextChanged(new CountedText(authoritative), event[0], event[1], event[2]);
            verify(editor, authoritative);
            editor.onTextChanged(new CountedText("old"), 0, authoritative.length(), 3);
        }
        System.out.println("Line index PASS: " + assertions + " assertions; 3000 deterministic edits");
    }
}
