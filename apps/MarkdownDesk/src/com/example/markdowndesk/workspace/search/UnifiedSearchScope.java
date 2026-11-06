package com.example.markdowndesk.workspace.search;

/** Selected search engine scopes are explicit: a limited folder provider scan
 * cannot silently masquerade as a complete archive or filesystem index. */
public enum UnifiedSearchScope {
    ALL("All accessible sources",true,true,true),
    ARCHIVE("Indexed conversations",true,false,false),
    NOTES("Saved notes, highlights and links",false,true,false),
    FOLDERS("Authorized Markdown folder",false,false,true);
    public final String title;
    private final boolean archive,notes,folder;
    UnifiedSearchScope(String title,boolean archive,boolean notes,boolean folder){
        this.title=title;this.archive=archive;this.notes=notes;this.folder=folder;
    }
    public boolean includesArchive(){return archive;}
    public boolean includesNotes(){return notes||this==ALL;}
    public boolean includesFolder(){return folder;}
    public static String[] labels(){UnifiedSearchScope[] all=values();String[] names=new String[all.length];for(int i=0;i<all.length;i++)names[i]=all[i].title;return names;}
}