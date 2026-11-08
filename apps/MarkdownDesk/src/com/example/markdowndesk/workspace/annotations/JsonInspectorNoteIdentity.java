package com.example.markdowndesk.workspace.annotations;

import com.example.markdowndesk.archive.ArchiveJournal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.UUID;

/** JSON Inspector annotations use the existing AJN2 journal, keyed by source
 * content URI + exact canonical JSON path. No new annotation database or
 * modification of original JSON. Paths do not grant URI access.
 */
public final class JsonInspectorNoteIdentity {
    public static final String SOURCE="json-document";
    private JsonInspectorNoteIdentity(){}

    public static String requireDocument(String raw){
        if(raw==null||raw.isEmpty()||raw.length()>8192)
            throw new IllegalArgumentException("Invalid JSON source URI");
        try{
            URI uri=new URI(raw);
            if(!"content".equalsIgnoreCase(uri.getScheme())||
                    uri.getRawAuthority()==null||uri.getRawAuthority().isEmpty()||
                    uri.getUserInfo()!=null||uri.getPort()!=-1||
                    uri.getRawPath()==null||uri.getRawPath().isEmpty()||
                    uri.getRawFragment()!=null)
                throw new IllegalArgumentException("JSON annotations require a content URI");
            return raw;
        }catch(URISyntaxException malformed){
            throw new IllegalArgumentException("Invalid JSON source URI",malformed);
        }
    }
    public static boolean owns(ArchiveJournal.Annotation a,String source){
        return a!=null&&SOURCE.equals(a.archiveId)&&
                source!=null&&source.equals(a.chatId)&&
                a.messageId!=null&&validPath(a.messageId);
    }
    private static boolean validPath(String path){
        try{JsonPathCodec.parse(path);return true;}
        catch(IllegalArgumentException bad){return false;}
    }
    public static ArchiveJournal.Annotation create(String source,String path,String title,
            String kind,String quote,String prefix,String suffix,int start,int end,
            String label,String note,String tags,String target){
        String uri=requireDocument(source);
        JsonPathCodec.parse(path);
        if(!ArchiveJournal.validType(kind))
            throw new IllegalArgumentException("Invalid annotation kind");
        if(quote==null||quote.isEmpty()||quote.length()>65536||
                start<0||end<=start||end-start!=quote.length()||
                prefix!=null&&prefix.length()>64||suffix!=null&&suffix.length()>64)
            throw new IllegalArgumentException("Select a bounded, exact JSON passage");
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id=UUID.randomUUID().toString();
        a.archiveId=SOURCE;a.chatId=uri;a.chatTitle=title==null||title.isEmpty()?"JSON document":title;
        a.messageId=path;a.type=kind;a.quote=quote;
        a.prefix=Objects.toString(prefix,"");a.suffix=Objects.toString(suffix,"");
        a.selectionStart=start;a.selectionEnd=end;
        a.label=Objects.toString(label,"");a.note=Objects.toString(note,"");
        a.tags=Objects.toString(tags,"");a.target=Objects.toString(target,"");
        a.createdAt=System.currentTimeMillis();a.updatedAt=a.createdAt;
        return a;
    }
}
