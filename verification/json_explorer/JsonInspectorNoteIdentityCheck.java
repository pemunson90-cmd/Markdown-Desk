package com.example.markdowndesk.workspace.annotations;

import com.example.markdowndesk.archive.ArchiveJournal;
import com.example.markdowndesk.archive.ArchiveMarkdownIndexes;
import com.example.markdowndesk.workspace.WorkspaceDeepLinks;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class JsonInspectorNoteIdentityCheck {
    private static void ok(boolean v,String name){if(!v)throw new AssertionError(name);}
    private static void reject(Runnable action,String name){
        try{action.run();throw new AssertionError("Accepted unsafe source: "+name);}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args){
        List<JsonPathCodec.Step> path=Arrays.asList(
                JsonPathCodec.Step.field("conversation"),
                JsonPathCodec.Step.field("a\"b\\c\n\r\t"),
                JsonPathCodec.Step.element(12));
        String address=JsonPathCodec.format(path);
        ok(address.startsWith("$[\"conversation\"]"),"canonical root");
        List<JsonPathCodec.Step> result=JsonPathCodec.parse(address);
        ok(result.size()==3&&"a\"b\\c\n\r\t".equals(result.get(1).key)&&result.get(2).index==12,
                "round-trip escaped path");
        ok(JsonPathCodec.parse("$").isEmpty(),"root path supported");
        reject(()->JsonPathCodec.parse("$[01]"),"noncanonical array index");
        reject(()->JsonPathCodec.parse("$[-1]"),"negative index");
        reject(()->JsonPathCodec.parse("$[\"a\"]junk"),"unexpected suffix");
        reject(()->JsonPathCodec.parse("$[\"unterminated]"),"broken quote");
        reject(()->JsonPathCodec.parse("$[\"a\\x\"]"),"unknown escape");
        String uri="content://com.example.documents/tree/root/document/file%2Fchat.json";
        ArchiveJournal.Annotation note=JsonInspectorNoteIdentity.create(uri,address,"Import",
                "highlight","target","alpha "," omega",6,12,"Here","Remember","craft","https://example.com");
        ok(JsonInspectorNoteIdentity.owns(note,uri),"exact source owner");
        ok(!JsonInspectorNoteIdentity.owns(note,uri+"-other"),"no cross-document ID alias");
        ok(address.equals(note.messageId),"source path saved");
        String link=ArchiveMarkdownIndexes.deepLink(note);
        ok(WorkspaceDeepLinks.isInspectorLink(link),"JSON sidecar deep link routing");
        ok(uri.equals(WorkspaceDeepLinks.inspectorTarget(link)),"validated source URI roundtrip");
        ok(note.id.equals(WorkspaceDeepLinks.inspectorNoteId(link)),"note identity from link");
        ok(!WorkspaceDeepLinks.isInspectorLink(link+"&other=true"),"refuse unexpected query");
        ok(!WorkspaceDeepLinks.isInspectorLink(link.replace("inspector","archive")),"refuse substituted host");
        reject(()->JsonInspectorNoteIdentity.requireDocument("file:///tmp/source.json"),"no file scheme");
        reject(()->JsonInspectorNoteIdentity.create(uri,"$[01]","t","highlight","x","","",0,1,"","","",""),
                "noncanonical JSON address");
        ok(VerifiedTextAnchor.locate("alpha target omega","target","alpha "," omega",6)==6,
                "exact quote/context");
        ok(VerifiedTextAnchor.locate("new alpha target omega","target","alpha "," omega",6)==10,
                "unique reanchor after drift");
        ok(VerifiedTextAnchor.locate("alpha target omega alpha target omega","target","alpha "," omega",6)==-1,
                "ambiguous text rejected");
        ok(VerifiedTextAnchor.locate("beta target omega","target","alpha "," omega",6)==-1,
                "changed context rejected");
        ok(VerifiedTextAnchor.matchesSelected("alpha target omega","target","alpha "," omega",6,12),
                "selection verifies current value");
        ok(!VerifiedTextAnchor.matchesSelected("alpha target omega","target","alpha "," omega",0,6),
                "incorrect offset rejected");
        ok(VerifiedTextAnchor.locate("💜 target","target","💜 ", "",3)==3,"UTF-16 supplementary offset");
        System.out.println("PASS source-verified JSON Inspector annotation, link and anchor identity");
    }
}
