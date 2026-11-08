package com.example.markdowndesk.workspace.archive;

/** Unicode-safe prefix overlap between indexed text segments. */
public final class ArchiveOverlap {
    private ArchiveOverlap() {}
    public static String tail(String previous, String segment, int maxUnits) {
        if (maxUnits < 0) throw new IllegalArgumentException("Negative overlap limit");
        if (maxUnits == 0) return "";
        String combined = (previous == null ? "" : previous) + (segment == null ? "" : segment);
        int start = Math.max(0, combined.length() - maxUnits);
        if (start > 0 && start < combined.length()
                && Character.isHighSurrogate(combined.charAt(start - 1))
                && Character.isLowSurrogate(combined.charAt(start))) start++;
        return combined.substring(start);
    }
}