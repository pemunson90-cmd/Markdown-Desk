package com.example.markdowndesk.workspace;

public final class WorkspaceDeepLinksTest {
    private static void check(boolean actual,boolean expected,String value){
        if(actual!=expected)throw new AssertionError("Unexpected archive link decision: "+value);
    }
    public static void main(String[] args){
        String[] good={
            "markdowndesk://archive/v1/a/chat/m",
            "markdowndesk://archive/v1/a/chat%2Fsuffix/msg-123?link=annotation-1",
            "MARKDOWNDESK://ARCHIVE/v1/id/id/id?link=%20",
        };
        String[] bad={
            "javascript:alert(1)","file:///etc/passwd","content://media/a",
            "markdowndesk://archive/v1/a/chat", "markdowndesk://other/v1/a/b/c",
            "markdowndesk://archive.evil/v1/a/b/c", "markdowndesk://archive/v2/a/b/c",
            "markdowndesk://user@archive/v1/a/b/c","markdowndesk://archive:3/v1/a/b/c",
            "markdowndesk://archive/v1/../a/c","markdowndesk://archive/v1/a/b/c#payload",
            "markdowndesk://archive/v1/a/b/c?redirect=http://evil",
            "markdowndesk://archive/v1/a/b/c?link=x&next=bad",
            "markdowndesk://archive/v1/a/b/c\" onclick=\"x",
        };
        for(String v:good)check(WorkspaceDeepLinks.isArchiveLink(v),true,v);
        for(String v:bad)check(WorkspaceDeepLinks.isArchiveLink(v),false,v);
        check(WorkspaceDeepLinks.isArchiveLink(null),false,"null");
        check(WorkspaceDeepLinks.isArchiveLink("a".repeat(5000)),false,"oversized");
        System.out.println("PASS: constrained internal Markdown Desk archive link routing");
    }
}