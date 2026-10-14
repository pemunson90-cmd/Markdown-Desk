package com.example.markdowndesk;

import android.text.Editable;
import android.text.style.*;
import java.lang.reflect.*;
import java.util.*;

/** Executes the production Editable/span adapter, with host-only span boundary modeling. */
public final class SyntaxCheck {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static final class Mark {final Object span;int start,end,flags;Mark(Object s,int a,int b,int f){span=s;start=a;end=b;flags=f;}}
 static final class Text implements Editable {
  final StringBuilder text=new StringBuilder();final List<Mark> spans=new ArrayList<>();long reads;int writes;
  Text(String value){text.append(value);}
  public int length(){return text.length();}
  public char charAt(int at){reads++;return text.charAt(at);}
  public CharSequence subSequence(int a,int b){throw new AssertionError("No text snapshot");}
  public String toString(){throw new AssertionError("No text snapshot");}
  @SuppressWarnings("unchecked") public <T>T[] getSpans(int a,int b,Class<T> type){List<T> found=new ArrayList<>();for(Mark m:spans)if(type.isInstance(m.span)&&m.start<=b&&m.end>=a)found.add(type.cast(m.span));return found.toArray((T[])Array.newInstance(type,found.size()));}
  public void setSpan(Object span,int a,int b,int flags){if(a<0||b<a||b>length())throw new AssertionError("Invalid span");spans.add(new Mark(span,a,b,flags));writes++;}
  public void removeSpan(Object span){spans.removeIf(m->m.span==span);writes++;}
  Mark mark(Object s){for(Mark m:spans)if(m.span==s)return m;return null;}
  public int getSpanStart(Object s){Mark m=mark(s);return m==null?-1:m.start;}
  public int getSpanEnd(Object s){Mark m=mark(s);return m==null?-1:m.end;}
  public int getSpanFlags(Object s){Mark m=mark(s);return m==null?0:m.flags;}
  void replace(int at,int before,String inserted){int end=at+before,delta=inserted.length()-before;for(Iterator<Mark> it=spans.iterator();it.hasNext();){Mark m=it.next();if(m.start>=end)m.start+=delta;else if(m.start>=at)m.start=at+inserted.length();if(m.end>end)m.end+=delta;else if(m.end>at)m.end=at;if(m.start>=m.end&&m.flags==33)it.remove();}text.replace(at,end,inserted);}
  List<String> syntax(){List<String> result=new ArrayList<>();for(Mark m:spans)if(m.span.getClass().getName().contains("$Syntax")){int value=m.span instanceof ForegroundColorSpan?((ForegroundColorSpan)m.span).getForegroundColor():m.span instanceof BackgroundColorSpan?((BackgroundColorSpan)m.span).getBackgroundColor():((StyleSpan)m.span).getStyle();result.add(m.span.getClass().getSimpleName()+":"+m.start+":"+m.end+":"+m.flags+":"+value);}Collections.sort(result);return result;}
 }
 // Reflection permits RED against the unchanged full-apply baseline, without a production placeholder.
 static final class Engine {
  final Object state;final Method changed,apply,reset;
  Engine()throws Exception{Object found=null;Method c=null,a=null,r=null;try{Class<?> type=Class.forName("com.example.markdowndesk.MarkdownSyntaxHighlighter$State");found=type.getConstructor().newInstance();c=type.getMethod("changed",CharSequence.class,int.class,int.class,int.class);a=type.getMethod("apply",Editable.class,boolean.class);r=type.getMethod("reset");}catch(ClassNotFoundException baseline){}state=found;changed=c;apply=a;reset=r;}
  void invoke(Method method,Object...args)throws Exception{try{method.invoke(state,args);}catch(InvocationTargetException e){throw new RuntimeException(e.getCause());}}
  void changed(Text t,int at,int before,int count)throws Exception{if(state!=null)invoke(changed,t,at,before,count);}
  void reset()throws Exception{if(state!=null)invoke(reset);}
  void apply(Text t,boolean dark)throws Exception{if(state==null)MarkdownSyntaxHighlighter.apply(t,dark);else invoke(apply,t,dark);}
 }
 static List<String> precedence(Text text,int at){List<String> result=new ArrayList<>();for(Mark m:text.spans)if(m.span.getClass().getName().contains("$Syntax")&&m.start<=at&&m.end>at){int value=m.span instanceof ForegroundColorSpan?((ForegroundColorSpan)m.span).getForegroundColor():m.span instanceof BackgroundColorSpan?((BackgroundColorSpan)m.span).getBackgroundColor():((StyleSpan)m.span).getStyle();result.add(m.span.getClass().getSimpleName()+":"+value);}return result;}
 static void equal(Text actual,Text oracle){check(actual.syntax().equals(oracle.syntax()),"incremental spans differ from full apply\n"+actual.syntax()+"\n"+oracle.syntax());Set<Integer> boundaries=new TreeSet<>();for(Mark m:oracle.spans)if(m.span.getClass().getName().contains("$Syntax")){boundaries.add(m.start);boundaries.add(m.end);}for(int at:boundaries)check(precedence(actual,at).equals(precedence(oracle,at)),"overlapping span precedence differs at "+at);}
 static void edit(Engine e,Text a,Text b,int at,int before,String inserted,boolean flush,boolean dark)throws Exception{a.replace(at,before,inserted);b.replace(at,before,inserted);e.changed(a,at,before,inserted.length());if(flush){String bytes=a.text.toString();Map<Object,int[]> foreign=new IdentityHashMap<>();for(Mark m:a.spans)if(!m.span.getClass().getName().contains("$Syntax"))foreign.put(m.span,new int[]{m.start,m.end,m.flags});e.apply(a,dark);check(bytes.contentEquals(a.text),"highlight mutated text");for(Map.Entry<Object,int[]> entry:foreign.entrySet()){Mark m=a.mark(entry.getKey());int[] range=entry.getValue();check(m!=null&&m.start==range[0]&&m.end==range[1]&&m.flags==range[2],"highlight changed composition/user span");}BaselineSyntaxHighlighter.apply(b,dark);equal(a,b);}}
 static void regular()throws Exception{
  Engine e=new Engine();Text a=new Text(("plain paragraph words here\n").repeat(10000)),b=new Text(a.text.toString());e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);
  a.reads=0;a.writes=0;edit(e,a,b,5,1,"x",true,false);check(a.reads<2000,"single-line edit must avoid whole-document regex reads; actual "+a.reads);
  String[] fixtures={"", "# Heading\n**bold** and _italics_\n`code` [x](y)\n", "a\r\nb\u0085**x\u2028**\n", "<!--\nabc\n--x>", "```\nunclosed\n", "~~~lang\ncode\n~~~\n", "a\n<!--closed-->\nb", "__a__ **b** *c* _d_\n", "# a\n# b\n# c\n"};
  for(String fixture:fixtures){a=new Text(fixture);b=new Text(fixture);e.reset();e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);equal(a,b);Object foreign=new Object();a.setSpan(foreign,0,a.length(),17);
   Random random=new Random(80171);String[] inserts={"", "a", "\n", "\r\n", "**", "`", "~~~", "<!--", "-->", "# ", "_é😀_", "[x](y)"};
   for(int i=0;i<400;i++){int at=random.nextInt(a.length()+1),before=random.nextInt(a.length()-at+1);edit(e,a,b,at,before,inserts[random.nextInt(inserts.length)],i%4==3,false);}
   e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);equal(a,b);check(a.mark(foreign)!=null,"foreign span removed");e.apply(a,true);BaselineSyntaxHighlighter.apply(b,true);equal(a,b);
  }
  a=new Text("# heading\n**bold** and _italic_ [x](y) `inline`\nplain\n".repeat(20));b=new Text(a.text.toString());e.reset();e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);
  a.setSpan(new ForegroundColorSpan(123),2,8,33);a.setSpan(new BackgroundColorSpan(456),4,7,289);
  Random local=new Random(180713);String[] localInserts={"", "x", "\n", "\r\n", "**", "_", "`", "[a](b)", "# ", "\u2028"};
  for(int i=0;i<1200;i++){int at=local.nextInt(a.length()+1),removed=local.nextInt(Math.min(3,a.length()-at)+1);edit(e,a,b,at,removed,localInserts[local.nextInt(localInserts.length)],i%5==4,false);}
  e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);equal(a,b);
  a=new Text("<!--\nabc\n--x>");b=new Text(a.text.toString());e.reset();e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);edit(e,a,b,a.length()-2,1,"-",true,false);
  a=new Text("# x\n# y\n# z\n");b=new Text(a.text.toString());e.reset();e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);edit(e,a,b,0,0,"first\n",false,false);edit(e,a,b,10,3,"",false,false);edit(e,a,b,a.length(),0,"**end**",true,false);
  a=new Text("# visible\n".repeat(150001));b=new Text(a.text.toString());e.reset();e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);edit(e,a,b,3,1,"x",true,false);
  a=new Text("x".repeat(1500000));b=new Text(a.text.toString());e.reset();e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);edit(e,a,b,0,0,"# ",true,false);edit(e,a,b,0,2,"",true,false);
  a=new Text("new editable\n**text**");b=new Text(a.text.toString());e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);equal(a,b);
  for(int[] invalid:new int[][]{{-1,0,0},{0,-1,0},{0,0,-1},{999,0,0},{0,999,0},{0,0,999}}){e.changed(a,invalid[0],invalid[1],invalid[2]);e.apply(a,false);equal(a,b);}
  a=new Text("# one\nplain\n# three\n");b=new Text(a.text.toString());e.reset();e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);Object retained=a.spans.get(a.spans.size()-1).span;edit(e,a,b,8,1,"x",true,false);check(a.mark(retained)!=null,"unaffected line spans should retain their identity");
  // Creation by joining token fragments, followed by deletion before the debounced pass.
  for(String token:new String[]{"<!--", "```", "~~~"}){String original=token.substring(0,1)+"X"+token.substring(1)+"\nbody\n-->\n";a=new Text(original);b=new Text(original);e.reset();e.apply(a,false);BaselineSyntaxHighlighter.apply(b,false);edit(e,a,b,1,1,"",false,false);edit(e,a,b,0,token.length(),"",true,false);}
  System.out.println("Syntax production adapter PASS: "+checks+" checks; host Editable model, not Android IME acceptance");
 }
 public static void main(String[] args)throws Exception{regular();}
}
