package com.example.markdowndesk;
import java.nio.file.*;
import java.util.Locale;
final class ReaderTypographyCheck {
 static int checks;
 static void eq(Object a,Object b){checks++;if(!a.equals(b))throw new AssertionError("Expected "+a+" got "+b);}
 static void has(String html,String css){checks++;if(!html.contains(css))throw new AssertionError(css);}
 public static void main(String[] args)throws Exception{
  Locale.setDefault(Locale.FRANCE);
  ReaderTypography defaults=ReaderTypography.defaults();
  for(int i=0;i<9;i++){
   UiTheme.Palette p=UiTheme.palette(i,false);
   String old=Files.readString(Path.of("verification/reader_typography/theme-"+i+".html"));
   eq(old,MarkdownRenderer.render(Capture.INPUT,p,18));
   eq(old,MarkdownRenderer.render(Capture.INPUT,p,18,defaults));
   ReaderTypography custom=ReaderTypography.fromValues("SANS",2.2f,1.5f,44,960);
   String changed=MarkdownRenderer.render(Capture.INPUT,p,18,custom);
   String expected=old.replace("max-width:720px","max-width:960px").replace("padding:38px 28px 28px","padding:38px 44px 28px").replace("font-family:Georgia,'Times New Roman',serif","font-family:system-ui,sans-serif").replace("line-height:1.72","line-height:2.2").replace("p{margin:.9em 0}","p{margin:1.5em 0}");
   eq(expected,changed);
  }
  for(String family:new String[]{null,"unknown","serif;}</style><script>alert(1)</script>","serif"}){
   ReaderTypography invalid=ReaderTypography.fromValues(family,Float.NaN,Float.POSITIVE_INFINITY,-1,10000);
   eq(defaults.family,invalid.family);eq(defaults.lineHeight,invalid.lineHeight);eq(defaults.paragraphGapEm,invalid.paragraphGapEm);eq(defaults.horizontalMarginPx,invalid.horizontalMarginPx);eq(defaults.measurePx,invalid.measurePx);
  }
  for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,1.19f,2.21f}) eq(1.72f,defaults.withLineHeight(bad).lineHeight);
  for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,.49f,1.51f}) eq(.9f,defaults.withParagraphGapEm(bad).paragraphGapEm);
  for(int bad:new int[]{11,45,Integer.MIN_VALUE,Integer.MAX_VALUE}) eq(28,defaults.withHorizontalMarginPx(bad).horizontalMarginPx);
  for(int bad:new int[]{479,961,Integer.MIN_VALUE,Integer.MAX_VALUE}) eq(720,defaults.withMeasurePx(bad).measurePx);
  eq(ReaderTypography.Family.SANS,defaults.withFamily(ReaderTypography.Family.SANS).family);
  ReaderTypography mixed=ReaderTypography.fromValues("MONOSPACE",Float.NEGATIVE_INFINITY,.5f,12,480);
  eq(1.72f,mixed.lineHeight);eq(.5f,mixed.paragraphGapEm);eq(12,mixed.horizontalMarginPx);eq(480,mixed.measurePx);
  has(MarkdownRenderer.render("test",UiTheme.palette(0,false),18,mixed),"font-family:'SFMono-Regular',Consolas,monospace;font-size:");
  eq(ReaderTypography.Family.SERIF,defaults.withFamily(null).family);
  eq(1.2f,defaults.withLineHeight(1.2f).lineHeight);eq(.5f,defaults.withParagraphGapEm(.5f).paragraphGapEm);eq(12,defaults.withHorizontalMarginPx(12).horizontalMarginPx);eq(480,defaults.withMeasurePx(480).measurePx);
  eq(1.72f,defaults.withLineHeight(2.21f).lineHeight);eq(.9f,defaults.withParagraphGapEm(.49f).paragraphGapEm);eq(28,defaults.withHorizontalMarginPx(45).horizontalMarginPx);eq(720,defaults.withMeasurePx(479).measurePx);
  System.out.println("Reader typography: "+checks+" assertions PASS");
 }
}
