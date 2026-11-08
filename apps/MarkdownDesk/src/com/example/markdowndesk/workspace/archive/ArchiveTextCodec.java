package com.example.markdowndesk.workspace.archive;

/** Lossless reconstruction of indexed archive text.
 *
 * SQLite substr() counts Unicode code points while the stored overlap_len is
 * measured in Java UTF-16 code units. Reconstructing a segment in SQL silently
 * drops characters when its overlap contains surrogate pairs (e.g. emoji).
 * Use Java substring with the original unit convention for all v2 databases.
 * Keep this codec pure Java so regressions run offline without Android.
 */
public final class ArchiveTextCodec {
    private ArchiveTextCodec() {}

    public static String reconstruct(String originalContent, int overlapUnits, String indexedBody) {
        // The pre-v2 tables kept actual text in parts.content. Prefer it as-is.
        if (originalContent != null && !originalContent.isEmpty()) return originalContent;
        if (indexedBody == null) throw new IllegalArgumentException("Missing indexed archive segment");
        if (overlapUnits < 0 || overlapUnits > indexedBody.length())
            throw new IllegalArgumentException("Corrupted indexed archive segment overlap");
        if (overlapUnits > 0 && overlapUnits < indexedBody.length()
                && Character.isLowSurrogate(indexedBody.charAt(overlapUnits)))
            throw new IllegalArgumentException("Archive segment overlap splits a surrogate pair");
        return indexedBody.substring(overlapUnits);
    }
}