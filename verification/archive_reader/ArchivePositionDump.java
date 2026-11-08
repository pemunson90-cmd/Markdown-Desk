package com.example.markdowndesk.workspace.archive;
public final class ArchivePositionDump {
    public static void main(String[] args){
        System.out.print("restore".equals(args[0])?ArchiveScrollScript.restorePosition(72,120,340):ArchiveScrollScript.position());
    }
}
