package com.example.markdowndesk.workspace.archive;

public final class ArchiveSearchHitDump {
    public static void main(String[] args){
        if(!"false".equals(ArchiveSearchHitScript.jump("message-7",-1,"text")))
            throw new AssertionError("invalid segment accepted");
        if(!"false".equals(ArchiveSearchHitScript.jump("message-7",4,"")))
            throw new AssertionError("empty match accepted");
        System.out.print(ArchiveSearchHitScript.jump("message-7",4,"foo-bar café 😀"));
    }
}
