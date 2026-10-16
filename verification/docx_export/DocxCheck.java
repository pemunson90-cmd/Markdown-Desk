package com.example.markdowndesk;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
final class DocxCheck {
 static int checks;static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static final class Sink extends ByteArrayOutputStream {int closes;boolean failWrite,failClose;int failAfter=Integer.MAX_VALUE;public synchronized void write(byte[]b,int o,int n){if(failWrite||count+n>failAfter)throw new RuntimeException("write injected");super.write(b,o,n);}public void close()throws IOException{closes++;if(failClose)throw new IOException("close injected");}}
 static String fixture="# Heading & <text>\n\nA **bold** and *italic* and ~~strike~~ with `code` [safe](https://example.com/a?x=1&y=2).\nSecond line\n\n1. first\n    - nested\n2. second\n\nA | B\n--- | ---\ncell | escaped \\| pipe\n\n```java\n  <xml> & code\n\tline\n```\n\n> quoted\n\n---\n\nUnicode: café é שלום 中文 😀\n\n![alt](https://blocked/image) [bad](javascript:bad)\n";
 static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
 static MarkdownDocx.Result export(String source,Sink out)throws Exception{return MarkdownDocx.export(source,"Document",out,MarkdownDocx.Limits.defaults(),()->{});}
 static void failure(String source,Sink sink,MarkdownDocx.Limits limits,MarkdownDocx.Cancellation cancel)throws Exception{try{MarkdownDocx.export(source,"Document",sink,limits,cancel);throw new AssertionError("must fail");}catch(IOException expected){checks++;}check(sink.closes==1,"owned close once");}
 public static void main(String[]args)throws Exception{
  Sink out=new Sink();MarkdownDocx.Result r=export(fixture,out);check(out.closes==1,"success closes");check(r.length==out.size(),"length");check(r.sha256.equals(hex(java.security.MessageDigest.getInstance("SHA-256").digest(out.toByteArray()))),"actual digest");check(!r.warnings.isEmpty(),"blocked resources warn");Files.write(Path.of(args[0],"rich.docx"),out.toByteArray());
  Sink again=new Sink();export(fixture,again);check(Arrays.equals(out.toByteArray(),again.toByteArray()),"deterministic");
  TimeZone originalZone=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));Sink otherZone=new Sink();export(fixture,otherZone);TimeZone.setDefault(originalZone);check(Arrays.equals(out.toByteArray(),otherZone.toByteArray()),"timezone independent ZIP");
  for(String invalid:new String[]{"\u0000","\u0001","\ud800","\udc00","\ufffe"})failure(invalid,new Sink(),MarkdownDocx.Limits.defaults(),()->{});
  failure("a",new Sink(),MarkdownDocx.Limits.defaults(),()->{throw new MarkdownDocx.ExportException(MarkdownDocx.Failure.CANCELLED);});
  Sink close=new Sink();close.failClose=true;failure("ok",close,MarkdownDocx.Limits.defaults(),()->{});
  Sink write=new Sink();write.failWrite=true;failure("ok",write,MarkdownDocx.Limits.defaults(),()->{});
  for(MarkdownDocx.Budget b:MarkdownDocx.Budget.values()){try{MarkdownDocx.Limits.defaults().with(b,0);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}try{MarkdownDocx.Limits.defaults().with(b,b.maximum+1);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}}
  failure("ab",new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.SOURCE_UNITS,1),()->{});
  failure("é",new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.SOURCE_BYTES,1),()->{});
  failure("ab",new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.LINE_UNITS,1),()->{});
  failure("one\n\ntwo",new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.BLOCKS,1),()->{});
  failure("one **two**",new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.RUNS,1),()->{});
  failure("text",new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.XML_BYTES,1),()->{});
  failure("text",new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.ZIP_BYTES,1),()->{});
  Sink exact=new Sink();MarkdownDocx.export("a","t",exact,MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.SOURCE_UNITS,1).with(MarkdownDocx.Budget.SOURCE_BYTES,1).with(MarkdownDocx.Budget.LINE_UNITS,1),()->{});check(exact.closes==1,"exact source limits");
  failure("[x](https://example.com)",new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.TARGET_UNITS,2),()->{});
  failure("[x](https://a) [y](mailto:b)",new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.RELATIONSHIPS,1),()->{});
  failure("[".repeat(1000),new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.WORK,10000),()->{});
  String table="A|B\n---|---\nx|y";
  failure(table,new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.CELLS,3),()->{});
  failure(table+"\n\n"+table,new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.TABLES,1),()->{});
  Sink cellExact=new Sink();MarkdownDocx.export(table,"t",cellExact,MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.CELLS,4).with(MarkdownDocx.Budget.COLUMNS,2).with(MarkdownDocx.Budget.TABLES,1),()->{});check(cellExact.size()>0,"exact table budgets");
  long xmlBytes=0;try(ZipInputStream zi=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))){ZipEntry e;while((e=zi.getNextEntry())!=null)xmlBytes+=zi.readAllBytes().length;}
  Sink exactOutput=new Sink();MarkdownDocx.export(fixture,"Document",exactOutput,MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.XML_BYTES,(int)xmlBytes).with(MarkdownDocx.Budget.ZIP_BYTES,out.size()),()->{});check(Arrays.equals(out.toByteArray(),exactOutput.toByteArray()),"exact XML and ZIP budgets");
  failure(fixture,new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.XML_BYTES,(int)xmlBytes-1),()->{});
  failure(fixture,new Sink(),MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.ZIP_BYTES,out.size()-1),()->{});
  Sink finish=new Sink();finish.failAfter=out.size()-20;failure(fixture,finish,MarkdownDocx.Limits.defaults(),()->{});
  final int[] callbacks={0};MarkdownDocx.export("# hi\n\nA **b**", "t",new Sink(),MarkdownDocx.Limits.defaults(),()->{callbacks[0]++;});
  for(int stop=1;stop<=callbacks[0];stop++){final int seam=stop;final int[] calls={0};failure("# hi\n\nA **b**",new Sink(),MarkdownDocx.Limits.defaults(),()->{if(++calls[0]==seam)throw new MarkdownDocx.ExportException(MarkdownDocx.Failure.CANCELLED);});}
  Sink capsFallback=new Sink();MarkdownDocx.Result capsResult=MarkdownDocx.export("a|b\n---|---\nx|y\n\n- root\n    - deep","t",capsFallback,MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.COLUMNS,1).with(MarkdownDocx.Budget.LIST_DEPTH,1),()->{});check(capsResult.warnings.containsKey(MarkdownDocx.Warning.AMBIGUOUS_TABLE)&&capsResult.warnings.containsKey(MarkdownDocx.Warning.AMBIGUOUS_LIST),"unsupported structures warn");Files.write(Path.of(args[0],"caps-fallback.docx"),capsFallback.toByteArray());
  Sink semantics=new Sink();export("###### Sixth\n\n3. three\n4. four\n\n9. restart\n\n- [x] done\n- [ ] todo\n\n[mail](mailto:hello@example.com) [web](http://example.com)\n\n```\nspaces  \n```\n",semantics);Files.write(Path.of(args[0],"semantics.docx"),semantics.toByteArray());
  Sink fallback=new Sink();export("a | b\n--- | ---\n1 | 2 | SURPLUS\n\n**ambiguous * nested**\n\n  - oddindent\n\n[x](https://example.com/a(b))",fallback);Files.write(Path.of(args[0],"fallback.docx"),fallback.toByteArray());
  System.out.println("PASS DOCX Java "+checks+" checks");
 }
}
