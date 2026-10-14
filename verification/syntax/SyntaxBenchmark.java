package com.example.markdowndesk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Text mutation is outside timing; changed(), regex matching and real adapter span updates are timed. */
public final class SyntaxBenchmark {
 static final int PAIRS=5;
 static volatile long sink;
 static String fixture(int words){String line="alpha beta gamma delta epsilon zeta eta theta iota kappa\n";StringBuilder text=new StringBuilder();for(int row=0;row<words/10;row++){text.append(row%100==0?line.replace("alpha","needle"):line);if(row%100==99)text.append('\n');}return text.toString();}
 static double batch(boolean incremental,MarkdownSyntaxHighlighter.State state,SyntaxCheck.Text text,int at){long ns=0;for(int i=0;i<PAIRS;i++){text.replace(at,0,"x");long start=System.nanoTime();if(incremental){state.changed(text,at,0,1);state.apply(text,false);}else BaselineSyntaxHighlighter.apply(text,false);ns+=System.nanoTime()-start;text.replace(at,1,"");start=System.nanoTime();if(incremental){state.changed(text,at,1,0);state.apply(text,false);}else BaselineSyntaxHighlighter.apply(text,false);ns+=System.nanoTime()-start;}sink=text.reads+text.writes;return ns/(PAIRS*2.0*1e6);}
 public static void main(String[] args)throws Exception{
  System.out.println("path,words,chars,position,sample1_ms,sample2_ms,sample3_ms,sample4_ms,sample5_ms,median_ms,syntax_enabled");
  for(int words:new int[]{10000,100000,500000}){String fixture=fixture(words);byte[] digest=MessageDigest.getInstance("SHA-256").digest(fixture.getBytes(StandardCharsets.UTF_8));StringBuilder hash=new StringBuilder();for(byte b:digest)hash.append(String.format("%02x",b&255));System.err.println("fixture words="+words+" chars="+fixture.length()+" sha256="+hash);
   for(boolean incremental:new boolean[]{false,true})for(String location:new String[]{"beginning","middle","end"}){int at=location.equals("beginning")?0:location.equals("middle")?fixture.length()/2:fixture.length();SyntaxCheck.Text text=new SyntaxCheck.Text(fixture);MarkdownSyntaxHighlighter.State state=new MarkdownSyntaxHighlighter.State();if(incremental)state.apply(text,false);else BaselineSyntaxHighlighter.apply(text,false);for(int i=0;i<2;i++)batch(incremental,state,text,at);double[] samples=new double[5];for(int i=0;i<5;i++)samples[i]=batch(incremental,state,text,at);double[] sorted=samples.clone();Arrays.sort(sorted);System.out.printf(Locale.ROOT,"%s,%d,%d,%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%s%n",incremental?"incremental":"baseline",words,fixture.length(),location,samples[0],samples[1],samples[2],samples[3],samples[4],sorted[2],fixture.length()<=1500000);}
  }
 }
}
