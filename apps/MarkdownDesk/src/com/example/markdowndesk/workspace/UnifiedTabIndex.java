package com.example.markdowndesk.workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared logical view over the existing protected Markdown and Archive tabs.
 * The original source-specific session stores remain authoritative for saving,
 * closing and recovery; this list never stores document content.
 */
public final class UnifiedTabIndex {
    public enum Kind { MARKDOWN, ARCHIVE, INSPECT }
    public static final class Entry {
        public final Kind kind;
        public final String id,title;
        public final boolean active,dirty;
        public Entry(Kind kind,String id,String title,boolean active,boolean dirty){
            if(kind==null||id==null||id.isEmpty())throw new IllegalArgumentException("Missing tab identity");
            this.kind=kind;this.id=id;this.title=title==null||title.isEmpty()?"Untitled":title;
            this.active=active;this.dirty=dirty;
        }
    }
    private UnifiedTabIndex() {}
    /** Preserve the earlier two-kind API for existing host and app callers. */
    public static List<Entry> merge(List<Entry> markdown,List<Entry> archive){
        return merge(markdown,archive,null);
    }
    public static List<Entry> merge(List<Entry> markdown,List<Entry> archive,List<Entry> inspector){
        ArrayList<Entry> all=new ArrayList<>();Set<String> known=new HashSet<>();
        append(all,known,markdown,Kind.MARKDOWN);
        append(all,known,archive,Kind.ARCHIVE);
        append(all,known,inspector,Kind.INSPECT);
        return Collections.unmodifiableList(all);
    }
    private static void append(List<Entry> into,Set<String> seen,List<Entry> from,Kind kind){
        if(from==null)return;
        int count=0;
        for(Entry e:from){
            if(e==null||e.kind!=kind)continue;
            if(count>=12)break;
            if(seen.add(kind.name()+"/"+e.id)){into.add(e);count++;}
        }
    }
}