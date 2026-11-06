package com.example.markdowndesk.workspace.archive;

import com.example.markdowndesk.archive.ArchiveJournal;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search durable user notes, bookmarks and links, never original source URLs.
 * Stable offset pages prevent silently truncating a large research notebook.
 */
public final class ArchiveAnnotationSearch {
    private ArchiveAnnotationSearch() {}

    public static List<ArchiveJournal.Annotation> find(File journal,String needle,String chat,int limit)
            throws IOException {
        return page(journal,needle,chat,limit,0);
    }
    public static List<ArchiveJournal.Annotation> page(File journal,String needle,String chat,
                                                        int limit,int offset) throws IOException {
        ArrayList<ArchiveJournal.Annotation> matches=new ArrayList<>();
        if(needle==null||needle.trim().isEmpty())return matches;
        final String term=needle.toLowerCase(Locale.ROOT).trim();
        final int start=Math.max(0,offset),cap=Math.max(1,Math.min(limit,200));
        ArchiveJournal.ReadResult result=new ArchiveJournal(journal).load();
        if(result.truncatedTail)
            throw new IOException("Annotation journal needs recovery before search can be trusted");
        int seen=0;
        for(ArchiveJournal.Annotation a:result.annotations) {
            if(chat!=null&&!chat.equals(a.chatId))continue;
            if(contains(a.label,term)||contains(a.quote,term)||contains(a.note,term)
                    ||contains(a.tags,term)||contains(a.target,term)||contains(a.chatTitle,term)) {
                if(seen++<start)continue;
                matches.add(a);
                if(matches.size()>=cap)break;
            }
        }
        return matches;
    }
    private static boolean contains(String value,String needle){
        return value!=null&&value.toLowerCase(Locale.ROOT).contains(needle);
    }
}