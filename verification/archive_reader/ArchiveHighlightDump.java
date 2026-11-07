package com.example.markdowndesk.workspace.archive;
public final class ArchiveHighlightDump {
  public static void main(String[] args){
    String html=ArchiveHighlightScript.boot();
    System.out.print(html.substring("<script>".length(),html.length()-"</script>".length()));
  }
}
