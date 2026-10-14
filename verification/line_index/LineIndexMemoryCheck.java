package com.example.markdowndesk;

/** Actual LF-dense boundary, not the previous no-newline boundary fixture. */
public final class LineIndexMemoryCheck {
    private static byte[] headroom;
    public static void main(String[] args) {
        headroom = new byte[32 * 1024 * 1024];
        String text = "\n".repeat(8 * 1024 * 1024 - 1);
        LogicalLineIndex index = new LogicalLineIndex();
        index.changed(text, 0, 0, text.length());
        check(index.size() == text.length() + 1, "near-boundary line count");
        check(index.start(index.size() - 1) == text.length(), "trailing empty line");
        String appended = text.concat("\n");
        index.changed(appended, 1, text.length() - 1, appended.length() - 1);
        check(index.size() == appended.length() + 1, "dense partial paste at exact boundary");
        check(index.start(index.size() - 1) == appended.length(), "last pasted start");
        text = null;
        String beyond = appended.concat("\n");
        index.changed(beyond, appended.length(), 0, 1);
        check(index.size() == beyond.length() + 1, "small growth after dense boundary");
        check(index.start(index.size() - 1) == beyond.length(), "last appended start");
        index.changed("x", 0, beyond.length(), 1);
        check(index.size() == 1 && index.start(0) == 0, "small document replacement");
        check(index.lineForOffset(1) == 0 && headroom.length == 32 * 1024 * 1024, "lookup/headroom");
        System.out.println("Logical line index: 8 LF-dense memory checks PASS (128MiB heap/32MiB held)");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
