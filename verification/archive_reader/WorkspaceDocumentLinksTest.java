package com.example.markdowndesk.workspace;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class WorkspaceDocumentLinksTest {
    static void ok(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    public static void main(String[] args) {
        String target="content://com.android.externalstorage.documents/document/primary%3AResearch%2Fmy%20notes.md";
        String encoded=URLEncoder.encode(target,StandardCharsets.UTF_8).replace("+","%20");
        String link="markdowndesk://document/v1/"+encoded;
        ok(WorkspaceDeepLinks.isDocumentLink(link),"valid encoded content URI");
        ok(target.equals(WorkspaceDeepLinks.documentTarget(link)),"full original content URI preserved");
        ok(WorkspaceDeepLinks.isInternalLink(link+"?note=n-22"),"optional annotation ID");
        ok(WorkspaceDeepLinks.isArchiveLink("markdowndesk://archive/v1/a/chat/m"),"existing archive valid");
        String[] bad={
            "file:///tmp/notes.md", "javascript:alert(1)",
            "markdowndesk://document/v1/file%3A%2F%2F%2Fetc%2Fpasswd",
            "markdowndesk://document/v1/https%3A%2F%2Fevil.com%2Fnotes",
            "markdowndesk://document/v1/content%3A%2F%2Fprovider", // no document path
            "markdowndesk://document/v1/content%3A%2F%2Fuser%40provider%2Fdoc",
            "markdowndesk://other/v1/"+encoded,
            "markdowndesk://document.evil/v1/"+encoded,
            "markdowndesk://user@document/v1/"+encoded,
            "markdowndesk://document:123/v1/"+encoded,
            "markdowndesk://document/v1/"+encoded+"#frag",
            "markdowndesk://document/v1/"+encoded+"/extra",
            "markdowndesk://document/v1/"+encoded+"?redirect=https://bad",
            "markdowndesk://document/v1/"+encoded+"?note=n&url=bad",
            "markdowndesk://document/v1/"+encoded+"?note=%QQ",
            "markdowndesk://document/v2/"+encoded,
            "markdowndesk://document/v1/%ZZ",
            "markdowndesk://document/v1/"+URLEncoder.encode("content://provider/a/../b",StandardCharsets.UTF_8)
        };
        for(String candidate:bad) {
            ok(!WorkspaceDeepLinks.isDocumentLink(candidate),"reject "+candidate);
            ok(WorkspaceDeepLinks.documentTarget(candidate)==null,"never return untrusted URI");
        }
        ok(!WorkspaceDeepLinks.isDocumentLink(null),"reject null");
        ok(!WorkspaceDeepLinks.isDocumentLink("a".repeat(8200)),"reject oversize");
        System.out.println("PASS: constrained document links, read-only target parsing, hostile URI cases");
    }
}
