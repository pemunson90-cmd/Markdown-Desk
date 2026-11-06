package com.example.markdowndesk.workspace.archive;
public final class ArchiveInlineJsDump {
    public static void main(String[] args){
        String a=ArchiveHighlightScript.boot(),b=ArchiveScrollScript.boot();
        System.out.print(a.substring("<script>".length(),a.length()-"</script>".length()));
        System.out.print("\n");
        System.out.print(b.substring("<script>".length(),b.length()-"</script>".length()));
    }
}