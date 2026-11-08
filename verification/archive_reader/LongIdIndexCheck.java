package com.example.markdowndesk.archive;
import java.util.*;
public class LongIdIndexCheck {
  public static void main(String[] args){
    String same="x".repeat(120);
    String a=ArchiveMarkdownIndexes.fileName("Same title", same+"A");
    String b=ArchiveMarkdownIndexes.fileName("Same title", same+"B");
    if(a.equals(b)) throw new AssertionError("Different IDs overwrite same Markdown index");
    if(!a.equals(ArchiveMarkdownIndexes.fileName("Same title",same+"A")))
      throw new AssertionError("Unstable sidecar name");
    if(a.contains("/")||a.contains("\\"))throw new AssertionError("Unsafe name");
    System.out.println("PASS: deterministic unique safe filenames for long conversation IDs");
  }
}