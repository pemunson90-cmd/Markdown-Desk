package com.example.markdowndesk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Each invocation constructs one deterministic source and renders it once. */
public final class Probe {
    private static String source(String name) {
        if (name.equals("table_2000") || name.equals("table_5000")) {
            int columns = name.equals("table_2000") ? 2000 : 5000;
            return "|a".repeat(columns) + "|\n" + "|---".repeat(columns) + "|\n" + "|b".repeat(columns) + "|\n";
        }
        if (name.equals("spaces_dangling_pipe")) {
            return "| a | b |\n" + " ".repeat(10000) + "|---|---|" + " ".repeat(10000) + "|\n| x | y |\n";
        }
        if (name.equals("fragments_1000") || name.equals("fragments_3000")) {
            int repeats = name.equals("fragments_1000") ? 1000 : 3000;
            return ("\\* `code` ").repeat(repeats) + "\n";
        }
        throw new IllegalArgumentException("Unknown case " + name);
    }

    private static String hash(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected one named case");
        String input = source(args[0]);
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= 8388608) throw new AssertionError("Fixture over app byte limit");
        System.out.printf(Locale.ROOT, "CASE %s bytes=%d sha256=%s%n", args[0], bytes.length, hash(bytes));
        System.out.flush();
        long start = System.nanoTime();
        try {
            String html = MarkdownRenderer.render(input, UiTheme.palette(UiTheme.PAPER, false), 18.0f);
            System.out.printf(Locale.ROOT, "OK html_chars=%d wall_ms=%.3f%n", html.length(),
                    (System.nanoTime() - start) / 1e6);
        } catch (Throwable failure) {
            System.out.printf(Locale.ROOT, "THREW %s wall_ms=%.3f%n", failure.getClass().getName(),
                    (System.nanoTime() - start) / 1e6);
            System.out.flush();
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
