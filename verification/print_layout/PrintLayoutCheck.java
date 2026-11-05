package com.example.markdowndesk;
import java.nio.file.*;
import java.util.*;
public final class PrintLayoutCheck {
 static final String TOKEN="12345678-1234-1234-1234-123456789abc";
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 interface Work{void run();}
 static void rejects(Work w){try{w.run();throw new AssertionError("invalid frame accepted");}catch(IllegalArgumentException expected){checks++;}}
 static String page(String md,int theme){return DocumentPrintLayout.frame(MarkdownRenderer.render(md,UiTheme.palette(theme,false),18),TOKEN);}
 public static void main(String[] args)throws Exception {
  Path out=Path.of(args[0]);Files.createDirectories(out);
  String html=MarkdownRenderer.render("# Exact body\n\nsource < & text",UiTheme.palette(0,false),18);
  String framed=DocumentPrintLayout.frame(html,TOKEN);
  check(framed.startsWith("<!doctype html><html data-md-preview=\""+TOKEN+"\">"),"exact readiness token");
  check(framed.substring(framed.indexOf("<body>")).equals(html.substring(html.indexOf("<body>"))),"body/scripts unchanged");
  for(String token:Arrays.asList("", "1234",TOKEN.toUpperCase(),TOKEN+"\" onload=\"bad",null))rejects(()->DocumentPrintLayout.frame(html,token));
  for(String bad:Arrays.asList("", "<html></html>",html.replace("</head>",""),html.replace("<head>",""),html.replace("</head>","</head></head>")))rejects(()->DocumentPrintLayout.frame(bad,TOKEN));
  rejects(()->DocumentPrintLayout.frame(null,TOKEN));
  StringBuilder headings=new StringBuilder("# Paged headings\n\n");
  for(int i=0;i<48;i++)headings.append("Paragraph").append(String.format(Locale.ROOT,"%03d",i)).append(" Ordinary paged text preserves content in sequence. ").append("A measured paragraph with several words and punctuation. ".repeat(3)).append("\n\n## Heading").append(String.format(Locale.ROOT,"%03d",i)).append("\n\nFollowing").append(String.format(Locale.ROOT,"%03d",i)).append(" stays with its heading.\n\n");
  Files.writeString(out.resolve("headings.html"),page(headings.toString(),0));
  StringBuilder table=new StringBuilder("# Paged table\n\n| RepeatHeader | RightHeader |\n| --- | --- |\n");
  for(int i=0;i<70;i++)table.append("| Row").append(String.format(Locale.ROOT,"%03d",i)).append(" | ").append("WideCell".repeat(9)).append(" Right").append(String.format(Locale.ROOT,"%03d",i)).append(" |\n");
  Files.writeString(out.resolve("table.html"),page(table.toString(),1));
  StringBuilder tall=new StringBuilder("# Oversized row\n\n| TallHeader | SideHeader |\n| --- | --- |\n| ");
  for(int i=0;i<180;i++)tall.append("Tall").append(String.format(Locale.ROOT,"%03d",i)).append(" several words preserve each fragment ");
  tall.append(" | SideEnd |\n");Files.writeString(out.resolve("tall-row.html"),page(tall.toString(),1));
  StringBuilder code=new StringBuilder("# Paged code\n\n```text\n");
  for(int i=0;i<120;i++)code.append("CodeLine").append(String.format(Locale.ROOT,"%03d",i)).append("  preserve whitespace and complete lines\n");
  code.append("WideStart_").append("x".repeat(300)).append("_WideEnd\n```\n\nCodeAfter\n");
  Files.writeString(out.resolve("code.html"),page(code.toString(),1));
  String semantics="# Semantic export\n\ncafé Ω Ж UnicodeEnd\n\n1. OrderedFirst\n2. OrderedSecond\n   - NestedLeaf\n\n- [x] TaskDone\n- [ ] TaskOpen\n\n![ImageAlt](https://example.invalid/private.png)\n\n<script>window.untrustedPrint=true</script>\n\n[SafeLink](https://example.invalid/)\n\n> QuotedText\n\n`InlineCode`\n";
  for(int i=0;i<9;i++)Files.writeString(out.resolve("theme"+i+".html"),page(semantics,i));
  System.out.println("Print layout framing: "+checks+" checks PASS;13 bounded real-renderer fixtures");
 }
}
