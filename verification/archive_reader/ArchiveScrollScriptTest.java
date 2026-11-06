package com.example.markdowndesk.workspace.archive;
public final class ArchiveScrollScriptTest {
  private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
  public static void main(String[] args){
    String init=ArchiveScrollScript.boot();
    check(init.contains("mdarchive://adjacent")&&init.contains("mdArchiveInsert"),"scroll hooks");
    check(init.contains("stream.firstElementChild")&&init.contains("stream.lastElementChild"),"bounded DOM");
    String payload="</script><h1>\"x\\\\\n💜</h1>";
    String js=ArchiveScrollScript.insert(payload,-1,12,true,false);
    check(!js.contains("</script>")&&js.contains("\\u003c/script\\u003e"),"untrusted HTML cannot close script");
    check(js.contains(",-1,12,true,false"),"page command flags");
    check(ArchiveScrollScript.quote("a\nb").equals("\"a\\nb\""),"quotes and newlines");
    check(init.contains("mdArchivePrependChunk")&&init.contains("updateEarlier"),
        "long messages load in both directions");
    check(init.contains("data-part-end")&&init.contains("heldSelection"),
        "do not drop unread message segments or active selections");
    String later=ArchiveScrollScript.appendChunk(24,"<b>ok</b>",20,40,true);
    check(later.contains(",20,40,true"),"forward chunk adjacency");
    String earlier=ArchiveScrollScript.prependChunk(24,"<b>ok</b>",0,20);
    check(earlier.contains(",0,20"),"backward chunk adjacency");
    try{ArchiveScrollScript.prependChunk(0,"",20,20);throw new AssertionError("accepted zero-length chunk");}
    catch(IllegalArgumentException expected){}
    System.out.println("PASS: bounded reader scroll script and safe HTML serialization");
  }
}