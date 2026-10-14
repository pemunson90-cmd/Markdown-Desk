package com.example.markdowndesk;
import java.nio.file.*;
public class RendererCheck {
 public static void main(String[] args) throws Exception {
  String[] names={"Paper","Midnight","Sepia","Forest","Bubblegum","Amethyst","Black Rose","Ocean","Arcade"};
  String sample="# Large heading with several words\n\n## Secondary title\n\n### Third title\n\nBody text with a_very_long_intraword_name and [long link](https://example.com).\n\n"+"longword".repeat(40)+"\n\n| Heading | Second |\n| --- | --- |\n| a\\|b | c |\n\n```\n"+"code".repeat(60)+"\n```\n";
  Path out=Path.of(args[0]);Files.createDirectories(out);
  for(int i=0;i<names.length;i++) {
   var p=UiTheme.palette(i,false);if(!p.name.equals(names[i]))throw new AssertionError("Theme "+i);
   for(float size:new float[]{12,18,32}) {
    String html=MarkdownRenderer.render(sample,p,size);
    if(size==32 && !(html.contains("h1{font-size:34.0px")&&html.contains("h2{font-size:29.0px")&&html.contains("h3{font-size:25.0px")))throw new AssertionError("Heading ceiling");
    if(!html.contains("a_very_long_intraword_name")||!html.contains("a|b"))throw new AssertionError("Markdown regression");
    Files.writeString(out.resolve("theme-"+i+"-"+(int)size+".html"),html);
   }
  }
  System.out.println("PASS actual renderer output: 9 themes x 3 reading sizes; heading ceilings and Markdown samples");
 }
}
