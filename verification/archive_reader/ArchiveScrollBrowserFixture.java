package com.example.markdowndesk.workspace.archive;
public final class ArchiveScrollBrowserFixture {
  public static void main(String[] args){
    StringBuilder html=new StringBuilder("<!doctype html><html><head><meta charset='utf-8'></head><body>");
    html.append("<a id='archive-before' href='#' data-more='1'>Earlier</a>")
        .append("<div id='archive-stream'>");
    for(int i=12;i<36;i++)html.append("<section data-ordinal='").append(i)
        .append("' style='height:50px'>Message ").append(i).append("</section>");
    html.append("</div><a id='archive-after' href='#' data-more='1'>Later</a>");
    html.append(ArchiveScrollScript.boot());
    StringBuilder next=new StringBuilder();
    for(int i=36;i<48;i++)next.append("<section data-ordinal='")
        .append(i).append("' style='height:50px'>Message ").append(i).append("</section>");
    html.append("<script>").append("var a=")
        .append(ArchiveScrollScript.insert(next.toString(),1,0,true,true))
        .append(";var sections=document.querySelectorAll('#archive-stream section');")
        .append("document.body.setAttribute('data-pass',a && sections.length==36 && sections[0].getAttribute('data-ordinal')=='12' && sections[35].getAttribute('data-ordinal')=='47'?'PASS':'FAIL');")
        .append("</script></body></html>");
    System.out.print(html);
  }
}