package com.example.markdowndesk.workspace.archive;

import com.example.markdowndesk.archive.ArchiveJournal;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searches notes, highlights, bookmarks and explicit user-created links.
 * Never touches original Claude/JSON, and doesn't turn every source URL into a saved link.
 */
public final class ArchiveAnnotationSearch {
    private ArchiveAnnotationSearch(){}
    public static List<ArchiveJournal.Annotation> find(File journal,String needle,String chat,
                                                        int limit) throws IOException {
        ArrayList<ArchiveJournal.Annotation> matches=new ArrayList<>();
        if(needle==null || needle.trim().isEmpty())return matches;
        String term=needle.toLowerCase(Locale.ROOT).trim();
        ArchiveJournal.ReadResult result=new ArchiveJournal(journal).load();
        if(result.truncatedTail)throw new IOException("Annotation journal needs recovery before search can be trusted");
        for(ArchiveJournal.Annotation a:result.annotations){
            if(chat!=null&&!chat.equals(a.chatId))continue;
            if(contains(a.label,term)||contains(a.quote,term)||contains(a.note,term)
                    ||contains(a.tags,term)||contains(a.target,term)||contains(a.chatTitle,term)){
                matches.add(a);
                if(matches.size()>=Math.max(1,Math.min(limit,200)))break;
            }
        }
        return matches;
    }
    private static boolean contains(String value,String needle){
        return value!=null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}