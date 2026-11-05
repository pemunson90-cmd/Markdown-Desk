package com.example.markdowndesk;
import java.nio.file.*;
final class Capture {
 static final String INPUT="# Reader <unsafe>\n\nHello **bold** [blocked](javascript:alert(1))\n\n- [x] task\n\n```java\n<x>\n```\n\n> quote\n\nA | B\n--- | ---\nx | y\n";
 public static void main(String[] args) throws Exception {
  for(int i=0;i<9;i++) Files.writeString(Path.of("verification/reader_typography/theme-"+i+".html"),MarkdownRenderer.render(INPUT,UiTheme.palette(i,false),18));
 }
}
