package com.example.markdowndesk;
import java.util.*;
public final class ReaderPreviewCheck {
 static int checks;static void yes(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  ArrayDeque<Runnable> worker=new ArrayDeque<>(),main=new ArrayDeque<>();List<LatestPreview.Result> results=new ArrayList<>();LatestPreview preview=new LatestPreview(worker::add,main::add,results::add);
  ReaderTypography custom=ReaderTypography.defaults().withFamily(ReaderTypography.Family.SANS).withLineHeight(2f);
  LatestPreview.Request request=new LatestPreview.Request("tab",1,"# Heading",UiTheme.palette(0,false),18,1,custom);
  yes(request.typography==custom,"captured immutable style identity");yes(java.lang.reflect.Modifier.isFinal(LatestPreview.Request.class.getDeclaredField("typography").getModifiers()),"request field final");
  preview.request(request);while(!worker.isEmpty())worker.remove().run();while(!main.isEmpty())main.remove().run();
  yes(results.size()==1&&results.get(0).html.equals(MarkdownRenderer.render(request.source,request.palette,request.fontSp,custom)),"worker renders captured style");
  LatestPreview.Request compatibility=new LatestPreview.Request("tab",2,"text",request.palette,18,1);yes(compatibility.typography.family==ReaderTypography.Family.SERIF&&compatibility.typography.lineHeight==1.72f,"old constructor exact defaults");
  LatestPreview.Request nullStyle=new LatestPreview.Request("tab",3,"text",request.palette,18,1,null);yes(nullStyle.typography.lineHeight==1.72f,"null style defaults");
  results.clear();preview.request(request);LatestPreview.Request newer=new LatestPreview.Request("tab",4,"text",request.palette,18,1,custom.withFamily(ReaderTypography.Family.MONOSPACE));preview.request(newer);
  while(!worker.isEmpty())worker.remove().run();while(!main.isEmpty())main.remove().run();yes(results.size()==1&&results.get(0).request==newer,"latest style wins blocked work");preview.close();
  System.out.println("Reader preview capture: "+checks+" checks PASS");
 }
}
