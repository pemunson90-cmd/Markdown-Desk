package com.example.markdowndesk;
import java.io.*;import java.nio.file.*;import java.util.zip.*;
final class ReviewRegression {
 static int checks;
 static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 static String xml(DocxCheck.Sink sink)throws Exception{try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(sink.toByteArray()))){ZipEntry e;while((e=z.getNextEntry())!=null)if(e.getName().equals("word/document.xml"))return new String(z.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}throw new AssertionError();}
 public static void main(String[]args)throws Exception{
  // Legal maximum-line inputs used to overflow the Java regex stack.
  for(String s:new String[]{"-".repeat(16384),"a|b\n"+"|---".repeat(3000)}){DocxCheck.Sink out=new DocxCheck.Sink();DocxCheck.export(s,out);check(out.closes==1,"stack-safe scanner closes");}
  String source="**a\\!** [a\\!](https://example.com) ![a\\!](https://blocked)\n\n$x$ and $$y$$\n\none\rtwo\n\n```\none\rtwo\n```\n\n**bad *nested** one\rtwo\n\n````\nsecret\n````\n\n```\ncode\n```TAIL\n";
  DocxCheck.Sink out=new DocxCheck.Sink();MarkdownDocx.Result r=DocxCheck.export(source,out);String xml=xml(out);
  check(xml.contains("**a\\!** [a\\!](https://example.com) ![a\\!](https://blocked)")&&r.warnings.containsKey(MarkdownDocx.Warning.UNSUPPORTED_INLINE),"escaped spans and labels retained literal with warning");check(r.warnings.containsKey(MarkdownDocx.Warning.UNSUPPORTED_BLOCK),"math warns");check(xml.split("<w:br/>",-1).length-1>=3,"standalone CR emits breaks in paragraph/code/fallback");check(xml.contains("TAIL"),"ambiguous closing fence tail retained");check(xml.contains("````"),"four-marker opener retained");
  Files.write(Path.of(args[0],"review.docx"),out.toByteArray());
  DocxCheck.Sink crlf=new DocxCheck.Sink();DocxCheck.export("```\na\r\nb\n```",crlf);String x=xml(crlf);check(x.split("<w:br/>",-1).length-1==1,"CRLF single break");
  for(String escaped:new String[]{"**a\\!**","[a\\!](https://example.com)","![a\\!](https://blocked)"}){DocxCheck.Sink e=new DocxCheck.Sink();MarkdownDocx.Result result=DocxCheck.export(escaped,e);check(xml(e).contains(escaped)&&result.warnings.containsKey(MarkdownDocx.Warning.UNSUPPORTED_INLINE),"each escaped construct falls back visibly");}
  DocxCheck.Sink math=new DocxCheck.Sink();check(DocxCheck.export("$x$ and $$y$$",math).warnings.containsKey(MarkdownDocx.Warning.UNSUPPORTED_BLOCK),"math alone warns");
  final int[] beforeWrite={0};final boolean[] written={false};ByteArrayOutputStream destination=new ByteArrayOutputStream();
  OutputStream phase=new OutputStream(){public void write(int b){written[0]=true;destination.write(b);}public void write(byte[]b,int o,int n){written[0]=true;destination.write(b,o,n);}};
  MarkdownDocx.export("a"+"😀".repeat(8191),"t",phase,MarkdownDocx.Limits.defaults(),()->{if(!written[0])beforeWrite[0]++;});check(beforeWrite[0]>=6,"supplementary UTF16 validation checks every4096 units despite odd indexes");
  DocxCheck.Sink textSink=new DocxCheck.Sink();try{MarkdownDocx.export(("x".repeat(4000)+"\n").repeat(100),"t",textSink,MarkdownDocx.Limits.defaults(),()->{if(textSink.size()>300)throw new MarkdownDocx.ExportException(MarkdownDocx.Failure.CANCELLED);});throw new AssertionError("emission cancellation");}catch(MarkdownDocx.ExportException expected){check(expected.failure==MarkdownDocx.Failure.CANCELLED&&textSink.closes==1,"long emission cancellation closes");}
  final int[] calls={0};MarkdownDocx.Cancellation cancellation=()->{calls[0]++;};DocxCheck.Sink longText=new DocxCheck.Sink();MarkdownDocx.export(("😀".repeat(4095)+"\n").repeat(8),"t",longText,MarkdownDocx.Limits.defaults().with(MarkdownDocx.Budget.LINE_UNITS,16384),cancellation);
  System.out.println("PASS DOCX review regressions "+checks+" checks");
 }
}
