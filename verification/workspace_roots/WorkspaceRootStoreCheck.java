package com.example.markdowndesk;
import android.content.*;import android.net.Uri;import java.io.*;import java.util.*;
public final class WorkspaceRootStoreCheck {
 static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}interface Action {void run()throws Exception;}
 static void reject(Action a)throws Exception{try{a.run();}catch(IOException expected){checks++;return;}throw new AssertionError("expected IOException");}
 static final class Prefs implements SharedPreferences {String raw;boolean success=true;int commits;public String getString(String k,String d){return raw==null?d:raw;}public Editor edit(){return new Editor(){String next;public Editor putString(String k,String v){next=v;return this;}public boolean commit(){commits++;raw=next;return success;}};}}
 static final class Resolver extends ContentResolver {List<UriPermission> grants=new ArrayList<>();public List<UriPermission> getPersistedUriPermissions(){return grants;}void grant(Uri uri,boolean read){grants.add(new UriPermission(uri,read));}}
 static Uri uri(int n){return Uri.parse("content://provider/tree/root"+n);}
 public static void main(String[] args)throws Exception {
  Prefs p=new Prefs();Resolver r=new Resolver();WorkspaceRootStore s=new WorkspaceRootStore(new Context(p));check(s.list(r).isEmpty(),"absent empty");
  reject(()->s.rememberGranted(uri(1),"one",r));check(p.commits==0,"missing grant no mutation");r.grant(uri(1),false);reject(()->s.rememberGranted(uri(1),"one",r));r.grant(uri(1),true);
  s.rememberGranted(uri(1),"one",r);check(s.list(r).get(0).access==WorkspaceRootStore.Access.CURRENT,"current persisted read");
  r.grants.clear();check(s.list(r).get(0).access==WorkspaceRootStore.Access.ACCESS_NEEDED,"revoked retained");r.grant(Uri.parse(uri(1).toString()+"/document/child"),true);reject(()->s.rememberGranted(uri(1),"one",r));
  for(int i=1;i<=8;i++){r.grant(uri(i),true);s.rememberGranted(uri(i),"root"+i,r);}check(s.list(r).size()==8,"eight roots");
  r.grant(uri(9),true);String before=p.raw;reject(()->s.rememberGranted(uri(9),"nine",r));check(p.raw.equals(before),"no eviction");
  s.rememberGranted(uri(1),"renamed",r);check(s.list(r).size()==8&&s.list(r).get(0).root.label.equals("renamed"),"dedup touch top");
  s.remove(uri(1));check(s.list(r).size()==7&&r.grants.stream().anyMatch(g->g.getUri().toString().equals(uri(1).toString())&&g.isReadPermission()),"remove keeps grants");
  try{s.list(r).clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
  reject(()->s.rememberGranted(Uri.parse("file:///tree/x"),"x",r));reject(()->s.rememberGranted(Uri.parse("content://provider/document/x"),"x",r));
  reject(()->s.rememberGranted(uri(2),"x".repeat(257),r));s.rememberGranted(uri(2),"x".repeat(256),r);check(s.list(r).get(0).root.label.length()==256,"label exact");
  Prefs bad=new Prefs();WorkspaceRootStore broken=new WorkspaceRootStore(new Context(bad));
  for(String raw:new String[]{"[".repeat(12000)+"0"+"]".repeat(12000), "{'version':1,'roots':[]}","{\"version\":1,\"roots\":[]]}","{\"version\":1,\"roots\":[],\"extra\":"+"[".repeat(8)+"0"+"]".repeat(8)+"}","{", "{\"version\":1,\"roots\":[]}trailing", "x".repeat(32769),"{\"version\":2,\"roots\":[]}","{\"version\":1,\"roots\":[{\"uri\":\"content://p/tree/x\",\"label\":\"x\",\"selectedAt\":0},{\"uri\":\"content://p/tree/x\",\"label\":\"x\",\"selectedAt\":0}]}"}){
   bad.raw=raw;reject(()->broken.list(r));reject(()->broken.rememberGranted(uri(2),"x",r));reject(()->broken.remove(uri(2)));check(bad.raw.equals(raw)&&bad.commits==0,"malformed preserved");
  }
  String punctuation="quoted \" \\ [] {} '";s.rememberGranted(uri(2),punctuation,r);check(s.list(r).get(0).root.label.equals(punctuation),"string-aware delimiters escapes and apostrophe");
  Prefs depth=new Prefs();depth.raw="{\"version\":1,\"roots\":[],\"extra\":"+"[".repeat(7)+"0"+"]".repeat(7)+"}";check(new WorkspaceRootStore(new Context(depth)).list(r).isEmpty(),"exact nesting8 accepted");
  p.success=false;reject(()->s.rememberGranted(uri(2),"failed",r));check(s.list(r).get(0).root.label.equals("failed"),"commit false may mutate memory without success");reject(()->s.remove(uri(2)));
  Prefs bounds=new Prefs();WorkspaceRootStore b=new WorkspaceRootStore(new Context(bounds));String exact="content://p/tree/"+"a".repeat(4096-"content://p/tree/".length());final Uri exactUri=Uri.parse(exact);r.grant(exactUri,true);b.rememberGranted(exactUri,"exact",r);check(b.list(r).get(0).root.uri.toString().length()==4096,"URI exact4096");
  final Uri over=Uri.parse(exact+"x");r.grant(over,true);reject(()->b.rememberGranted(over,"over",r));
  // Escaped long labels/URIs exceed serialized cap before root-count cap.
  Prefs total=new Prefs();WorkspaceRootStore t=new WorkspaceRootStore(new Context(total));boolean refused=false;
  for(int i=0;i<8;i++){Uri huge=Uri.parse("content://p/tree/"+(char)('a'+i)+"z".repeat(4000));r.grant(huge,true);try{t.rememberGranted(huge,"\n".repeat(256),r);}catch(IOException expected){refused=true;break;}}
  check(refused&&total.raw.length()<=32768,"serialized cap");
  System.out.println("PASS workspace roots: "+checks+" assertions");
 }
}
