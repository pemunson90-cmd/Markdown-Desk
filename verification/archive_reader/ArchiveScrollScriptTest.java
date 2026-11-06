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
    System.out.println("PASS: bounded reader scroll script and safe HTML serialization");
  }
}