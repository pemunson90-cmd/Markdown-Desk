package com.example.markdowndesk.workspace.annotations;

import com.example.markdowndesk.archive.ArchiveJournal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.UUID;

/** Source-specific identity layered over the existing, append-only annotation journal. */
public final class MarkdownNoteIdentity {
    public static final String SOURCE = "markdown-document";
    public static final String MESSAGE = "document";
    private MarkdownNoteIdentity() {}

    public static String requireDocument(String raw) {
        if(raw == null || raw.length() > 8192 || raw.isEmpty()) throw new IllegalArgumentException("Missing document URI");
        try {
            URI uri = new URI(raw);
            if(!"content".equalsIgnoreCase(uri.getScheme()) || uri.getRawAuthority() == null ||
                    uri.getRawAuthority().isEmpty() || uri.getUserInfo() != null || uri.getPort() != -1 ||
                    uri.getRawPath() == null || uri.getRawPath().isEmpty() ||
                    uri.getRawFragment() != null || uri.getRawPath().contains(".."))
                throw new IllegalArgumentException("Only existing Android document URI identities are accepted");
            return raw;
        } catch(URISyntaxException invalid) {
            throw new IllegalArgumentException("Invalid source URI",invalid);
        }
    }

    public static boolean owns(ArchiveJournal.Annotation annotation,String uri) {
        return annotation != null && SOURCE.equals(annotation.archiveId) &&
                uri != null && uri.equals(annotation.chatId) &&
                MESSAGE.equals(annotation.messageId);
    }

    public static ArchiveJournal.Annotation create(String document,String title,String kind,
            String quote,String prefix,String suffix,int start,int end,String label,String note,String tags) {
        String uri=requireDocument(document);
        if(!ArchiveJournal.validType(kind))throw new IllegalArgumentException("Invalid annotation type");
        if(quote==null || quote.trim().isEmpty() || quote.length()>65536)
            throw new IllegalArgumentException("Select a nonempty, bounded passage");
        if(start<0 || end<=start || end-start!=quote.length())
            throw new IllegalArgumentException("Source text range does not match selected quote");
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id=UUID.randomUUID().toString();
        a.archiveId=SOURCE;a.chatId=uri;a.messageId=MESSAGE;
        a.chatTitle=title==null||title.trim().isEmpty()?"Markdown document":title;
        a.type=kind;a.quote=quote;a.prefix=Objects.toString(prefix,"");a.suffix=Objects.toString(suffix,"");
        a.selectionStart=start;a.selectionEnd=end;a.label=Objects.toString(label,"");
        a.note=Objects.toString(note,"");a.tags=Objects.toString(tags,"");
        a.createdAt=System.currentTimeMillis();a.updatedAt=a.createdAt;
        return a;
    }
}
