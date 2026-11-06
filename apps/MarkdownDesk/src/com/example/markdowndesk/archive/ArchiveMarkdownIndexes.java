package com.example.markdowndesk.archive;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Deterministic portable Markdown projection. The master contains only saved links. */
public final class ArchiveMarkdownIndexes {
    public static final String BEGIN="<!-- MARKDOWN-DESK-ARCHIVE:BEGIN -->";
    public static final String END="<!-- MARKDOWN-DESK-ARCHIVE:END -->";
    public static String perChat(String chatTitle,String archiveId,String chatId,
                                 Collection<ArchiveJournal.Annotation> all) {
        ArrayList<ArchiveJournal.Annotation> rows=filter(all,chatId,false);
        StringBuilder result=new StringBuilder("# ").append(text(chatTitle)).append(" — Reading Notebook\n\n")
                .append("_Read-only source. Notes and links are stored separately from the original JSON._\n\n")
                .append(BEGIN).append("\n\n");
        appendSection(result,"Saved Links",rows,true,archiveId,chatId);
        appendSection(result,"Notes, Highlights and Bookmarks",rows,false,archiveId,chatId);
        return result.append(END).append("\n").toString();
    }
    public static String master(Collection<ArchiveJournal.Annotation> all) {
        ArrayList<ArchiveJournal.Annotation> links=filter(all,null,true);
        StringBuilder result=new StringBuilder("# Master Saved Links — Claude Archive\n\n")
                .append("_Only links deliberately saved by the reader appear here._\n\n")
                .append(BEGIN).append("\n\n");
        if(links.isEmpty())result.append("_No saved links._\n\n");
        for(ArchiveJournal.Annotation row:links) {
            result.append("- **").append(text(row.chatTitle)).append("** · message ")
                .append(text(row.messageDate)).append(" · saved ").append(instant(row.createdAt)).append("\n")
                .append("  [").append(text(label(row))).append("](").append(deepLink(row)).append(")\n");
            details(result,row,"  ");
        }
        return result.append("\n").append(END).append("\n").toString();
    }
    public static String replaceGeneratedRegion(String existing,String replacement) {
        if(existing==null||existing.isEmpty())return replacement;
        int start=existing.indexOf(BEGIN),end=existing.indexOf(END);
        if(start<0&&end<0)return existing.replaceFirst("\\s+$","")+"\n\n"+replacement;
        if(start<0||end<start)throw new IllegalArgumentException("Corrupted managed Markdown markers; original preserved");
        int rp= replacement.indexOf(BEGIN),re=replacement.indexOf(END);
        if(rp<0||re<rp)throw new IllegalArgumentException("Missing replacement markers");
        return existing.substring(0,start)+replacement.substring(rp,re+END.length())
                +existing.substring(end+END.length());
    }
    public static String fileName(String title,String id) {
        String clean=title==null?"Untitled":title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]"," ")
                .replaceAll("\\s+"," ").trim();
        if(clean.isEmpty())clean="Untitled";
        if(clean.length()>64)clean=clean.substring(0,64).trim();
        String suffix=id==null?"no-id":id.replaceAll("[^A-Za-z0-9_-]", "_");
        if(suffix.length()>64)suffix=suffix.substring(0,64);
        return clean+"--"+suffix+".md";
    }
    private static ArrayList<ArchiveJournal.Annotation> filter(Collection<ArchiveJournal.Annotation> rows,String chatId,boolean linksOnly){
        ArrayList<ArchiveJournal.Annotation> found=new ArrayList<>();
        for(ArchiveJournal.Annotation a:rows)if((chatId==null||chatId.equals(a.chatId)) && (!linksOnly||a.isLink()))found.add(a);
        found.sort(Comparator.comparingLong((ArchiveJournal.Annotation a)->a.createdAt).reversed()
                .thenComparing(a->a.id==null?"":a.id));
        return found;
    }
    private static void appendSection(StringBuilder out,String title,List<ArchiveJournal.Annotation> rows,
                                      boolean links,String archiveId,String chatId){
        out.append("## ").append(title).append("\n\n");
        int count=0;
        for(ArchiveJournal.Annotation a:rows){
            if(a.isLink()!=links)continue;
            count++;
            out.append("### ").append(text(label(a))).append("\n\n")
                .append("- Kind: ").append(text(a.type)).append("\n")
                .append("- Message date: ").append(text(a.messageDate)).append("\n")
                .append("- Saved: ").append(instant(a.createdAt)).append("\n")
                .append("- Source: [Open original passage](").append(deepLink(a)).append(")\n");
            details(out,a,"- ");
            out.append("<!-- archive-record-id: ").append(text(a.id)).append(" -->\n\n");
        }
        if(count==0)out.append("_None yet._\n\n");
    }
    private static void details(StringBuilder out,ArchiveJournal.Annotation a,String prefix){
        if(a.quote!=null&&!a.quote.isEmpty())out.append(prefix).append("Quote: “").append(text(a.quote)).append("”\n");
        if(a.note!=null&&!a.note.isEmpty())out.append(prefix).append("Note: ").append(text(a.note)).append("\n");
        if(a.target!=null&&!a.target.isEmpty()){
            String url=safeTarget(a.target);
            if(url!=null)out.append(prefix).append("Destination: [").append(text(a.target)).append("](").append(url).append(")\n");
            else out.append(prefix).append("Destination (non-clickable): ").append(text(a.target)).append("\n");
        }
        if(a.tags!=null&&!a.tags.isEmpty())out.append(prefix).append("Tags: ").append(text(a.tags)).append("\n");
    }
    public static String deepLink(ArchiveJournal.Annotation a){
        return "markdowndesk://archive/v1/"+enc(a.archiveId)+"/"+enc(a.chatId)
                +"/"+enc(a.messageId)+"?link="+enc(a.id);
    }
    private static String enc(String value){
        try{return URLEncoder.encode(value==null?"":value,"UTF-8").replace("+","%20");}
        catch(java.io.UnsupportedEncodingException impossible){throw new IllegalStateException(impossible);}
    }
    private static String instant(long millis){return millis<=0?"unknown":Instant.ofEpochMilli(millis).toString();}
    private static String label(ArchiveJournal.Annotation a){return a.label==null||a.label.trim().isEmpty()?a.type:a.label;}
    private static String safeTarget(String value){
        String v=value.trim();if(v.matches("(?i)^(https?://|mailto:|markdowndesk://)[^\\s<>]+$"))return v.replace(")","%29");
        return null;
    }
    /** Keep prose readable while neutralizing Markdown structural injection. */
    private static String text(String value){
        if(value==null||value.isEmpty())return "unknown";
        return value.replace("\\","\\\\").replace("\r"," ").replace("\n"," ")
                .replace("[","\\[").replace("]","\\]")
                .replace("*","\\*").replace("_","\\_")
                .replace("<","&lt;").replace(">","&gt;").replace("|","\\|");
    }
    private ArchiveMarkdownIndexes(){}
}