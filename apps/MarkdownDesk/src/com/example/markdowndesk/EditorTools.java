package com.example.markdowndesk;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.BreakIterator;
import java.util.Locale;

public final class EditorTools {
    private EditorTools() {}

    public static int wordCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        BreakIterator words = BreakIterator.getWordInstance(Locale.getDefault());
        words.setText(text);
        int count = 0;
        int start = words.first();
        for (int end = words.next(); end != BreakIterator.DONE; start = end, end = words.next()) {
            for (int i = start; i < end; ) {
                int cp = text.codePointAt(i);
                if (Character.isLetterOrDigit(cp)) {
                    count++;
                    break;
                }
                i += Character.charCount(cp);
            }
        }
        return count;
    }

    public static int lineCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        int lines = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') lines++;
        return lines;
    }

    public static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest.digest(bytes)) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
