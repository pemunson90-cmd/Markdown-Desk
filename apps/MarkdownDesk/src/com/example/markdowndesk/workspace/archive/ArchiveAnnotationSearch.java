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
        return pageFiltered(journal,needle,chat,limit,offset,null,null,null);
    }
    /** Filter before applying the offset: never skip matching records merely
     * because earlier unqualified records occupied a page.
     * kind is an AJN2 note type, tag is one exact comma-separated tag,
     * and source is "markdown", "archive" or null (all).
     */
    public static List<ArchiveJournal.Annotation> pageFiltered(File journal,String needle,String chat,
            int limit,int offset,String kind,String tag,String source) throws IOException {
        ArrayList<ArchiveJournal.Annotation> matches=new ArrayList<>();
        if(needle==null||needle.trim().isEmpty())return matches;
        final String term=needle.toLowerCase(Locale.ROOT).trim();
        final String type=kind==null?"":kind.trim();
        final String desiredTag=tag==null?"":tag.trim();
        final String origin=source==null?"":source.trim();
        if(!type.isEmpty()&&!ArchiveJournal.validType(type))
            throw new IllegalArgumentException("Invalid annotation type filter");
        if(desiredTag.length()>128||desiredTag.indexOf(',')>=0||desiredTag.indexOf('\n')>=0||
                desiredTag.indexOf('\r')>=0)
            throw new IllegalArgumentException("Select one bounded tag");
        if(!origin.isEmpty()&&!"markdown".equals(origin)&&!"archive".equals(origin))
            throw new IllegalArgumentException("Invalid note source filter");
        final int start=Math.max(0,offset),cap=Math.max(1,Math.min(limit,200));
        ArchiveJournal.ReadResult result=new ArchiveJournal(journal).load();
        if(result.truncatedTail)
            throw new IOException("Annotation journal needs recovery before search can be trusted");
        int seen=0;
        for(ArchiveJournal.Annotation a:result.annotations) {
            if(a==null||chat!=null&&!chat.equals(a.chatId))continue;
            if(!type.isEmpty()&&!type.equals(a.type))continue;
            if(!desiredTag.isEmpty()&&!hasTag(a.tags,desiredTag))continue;
            if("markdown".equals(origin)&&!"markdown-document".equals(a.archiveId))continue;
            if("archive".equals(origin)&&(a.archiveId==null||a.archiveId.isEmpty()||
                    "markdown-document".equals(a.archiveId)))continue;
            if(contains(a.label,term)||contains(a.quote,term)||contains(a.note,term)
                    ||contains(a.tags,term)||contains(a.target,term)||contains(a.chatTitle,term)) {
                if(seen++<start)continue;
                matches.add(a);
                if(matches.size()>=cap)break;
            }
        }
        return matches;
    }
    private static boolean hasTag(String tags,String wanted){
        if(tags==null)return false;
        for(String part:tags.split(",",-1))
            if(part.trim().equalsIgnoreCase(wanted))return true;
        return false;
    }
    private static boolean contains(String value,String needle){
        return value!=null&&value.toLowerCase(Locale.ROOT).contains(needle);
    }
}