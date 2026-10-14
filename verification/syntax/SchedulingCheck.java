package com.example.markdowndesk;

public final class SchedulingCheck {
 public static void main(String[] args)throws Exception{
  SchedulingHarness h=new SchedulingHarness();h.editor.text=new SyntaxCheck.Text("# first\nplain\n");h.scheduleHighlight();
  SyntaxCheck.check(h.main.queued.size()==1&&h.main.delay==90,"initial90ms request");h.main.flush();
  SyntaxCheck.Text expected=new SyntaxCheck.Text(h.editor.text.text.toString());BaselineSyntaxHighlighter.apply(expected,false);SyntaxCheck.equal(h.editor.text,expected);
  h.editor.text.replace(3,1,"x");h.onTextChanged(h.editor.text,3,1,1);
  h.editor.text.replace(5,0,"y");h.onTextChanged(h.editor.text,5,0,1);
  SyntaxCheck.check(h.main.queued.size()==1&&h.main.delay==90,"edits coalesce into one90ms request");h.main.flush();
  expected=new SyntaxCheck.Text(h.editor.text.text.toString());BaselineSyntaxHighlighter.apply(expected,false);SyntaxCheck.equal(h.editor.text,expected);
  h.editor.text.replace(0,0,"<!--");h.onTextChanged(h.editor.text,0,0,4);
  h.suppressChanges=true;h.editor.text=new SyntaxCheck.Text("**another tab**\n");h.onTextChanged(h.editor.text,0,0,h.editor.text.length());h.suppressChanges=false;h.scheduleHighlight();
  SyntaxCheck.check(h.main.queued.size()==1,"tab switch cancels old request");h.main.flush();
  expected=new SyntaxCheck.Text(h.editor.text.text.toString());BaselineSyntaxHighlighter.apply(expected,false);SyntaxCheck.equal(h.editor.text,expected);
  h.dark=true;h.scheduleHighlight();h.main.flush();BaselineSyntaxHighlighter.apply(expected,true);SyntaxCheck.equal(h.editor.text,expected);
  System.out.println("Syntax production watcher/scheduler PASS:90ms coalescing, suppressed tab switch, theme reset");
 }
}
