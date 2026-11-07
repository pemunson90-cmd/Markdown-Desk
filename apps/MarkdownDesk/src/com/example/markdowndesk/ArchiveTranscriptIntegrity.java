package com.example.markdowndesk;

import java.io.IOException;
import java.util.Objects;

/** Fail-closed transcript completeness checks independent of Android and the PDF renderer. */
final class ArchiveTranscriptIntegrity {
    private ArchiveTranscriptIntegrity(){}

    static void message(int expectedOrdinal,int actualOrdinal,int segments)throws IOException {
        if(expectedOrdinal<0 || actualOrdinal!=expectedOrdinal || segments<0)
            throw new IOException("Indexed conversation changed: missing or reordered message");
    }
    static void part(int expectedPart,int actualPart)throws IOException {
        if(expectedPart<0 || actualPart!=expectedPart)
            throw new IOException("Indexed message changed: missing or reordered text segment");
    }
    static void complete(int expected,int written)throws IOException {
        if(expected<0 || written!=expected)
            throw new IOException("Transcript incomplete: indexed count "+expected+", exported "+written);
    }
    static void generation(String expected,String current)throws IOException {
        if(expected==null || expected.isEmpty() || !Objects.equals(expected,current))
            throw new IOException("Archive was reindexed during export; no complete transcript was produced");
    }
}
