package com.example.markdowndesk.archive;

import java.util.ArrayList;
import java.util.List;

/** Never overwrite another conversation's portable Markdown notebook. */
public final class SidecarIdentityCheck {
    private static void check(boolean value,String reason) {
        if(!value)throw new AssertionError(reason);
    }
    private static ArchiveJournal.Annotation note(String archive,String chat) {
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id="note-1";a.type="note";a.archiveId=archive;a.chatId=chat;
        a.messageId="message-1";a.chatTitle="Shared title";a.quote="Exact text";
        return a;
    }
    public static void main(String[] args) {
        String title="Shared title", unsafe="id/a", canonical="id_a";
        String fresh=ArchiveMarkdownIndexes.fileName(title,unsafe);
        String safe=ArchiveMarkdownIndexes.fileName(title,canonical);
        check(!fresh.equals(safe),"sanitized short IDs must differ");
        check(ArchiveMarkdownIndexes.legacyFileName(title,unsafe).equals(safe),
                "old lossy filename collision should be detectable");
        check(fresh.equals(ArchiveMarkdownIndexes.fileName(title,unsafe)),
                "same source gets deterministic filename");
        check(!ArchiveMarkdownIndexes.fileName(title,"a".repeat(120)+"A").equals(
                ArchiveMarkdownIndexes.fileName(title,"a".repeat(120)+"B")),
                "long ID collision forbidden");
        List<ArchiveJournal.Annotation> notes=new ArrayList<>();
        String noNotes=ArchiveMarkdownIndexes.perChat(title,"archiveA",canonical,notes);
        check(ArchiveMarkdownIndexes.matchesOwner(noNotes,"archiveA",canonical),
                "new empty notebook has authenticated owner marker");
        check(!ArchiveMarkdownIndexes.matchesOwner(noNotes,"archiveA",unsafe),
                "colliding sanitized ID cannot own another notebook");
        check(!ArchiveMarkdownIndexes.matchesOwner(noNotes,"archiveB",canonical),
                "archive source must also match");
        notes.add(note("archiveA",canonical));
        String existing=ArchiveMarkdownIndexes.perChat(title,"archiveA",canonical,notes);
        String legacy=existing.replaceFirst("<!-- markdown-desk-owner-sha256:[^>]+ -->\\n",""); 
        check(ArchiveMarkdownIndexes.matchesOwner(legacy,"archiveA",canonical),
                "attributable legacy deep link may be preserved");
        check(!ArchiveMarkdownIndexes.matchesOwner(legacy,"archiveA",unsafe),
                "wrong legacy source cannot be adopted");
        check(!ArchiveMarkdownIndexes.matchesOwner(
                noNotes.replaceFirst("<!-- markdown-desk-owner-sha256:[^>]+ -->\\n",""),
                "archiveA",canonical),"ambiguous markerless empty legacy notebook fails closed");
        String withProse="# Personal notes\\n\\n"+existing+"\\nmanual tail\\n";
        String merged=ArchiveMarkdownIndexes.replaceGeneratedRegion(withProse,existing);
        check(merged.startsWith("# Personal notes") && merged.endsWith("manual tail\\n"),
                "user prose must survive generated-region updates");
        System.out.println("PASS: sidecar source ownership, short/long IDs, legacy fail-closed, prose");
    }
}
