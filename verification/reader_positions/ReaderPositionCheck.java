package com.example.markdowndesk;
import android.content.*;import org.json.*;import java.io.*;import java.util.*;import java.lang.reflect.*;
public class ReaderPositionCheck {
 static int checks;interface Work{void run()throws Exception;}
 static void yes(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static void no(Work w)throws Exception{try{w.run();throw new AssertionError("accepted");}catch(IOException expected){yes(expected.getCause()==null&&!expected.getMessage().contains("PRIVATE"),"bounded error");}}
 static final ReaderAnchor.Cancellation OK=()->{};
 static ReaderAnchor.Anchor anchor(String s,int offset)throws Exception{return ReaderAnchor.capture(s,offset,ReaderAnchor.Kind.TEXT,OK);}
 static final class Prefs implements SharedPreferences {String raw;int commits;boolean success=true;public String getString(String k,String d){return raw==null?d:raw;}public Editor edit(){return new Editor(){String next;public Editor putString(String k,String v){next=v;return this;}public boolean commit(){commits++;raw=next;return success;}};}}
 static String uri(int n){return "content://provider/document/"+n;}
 static ReaderPositionStore store(Prefs p){return new ReaderPositionStore(new Context(p));}
 static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
 static String scalarText(int size){return "a".repeat(size);}
 static void anchors()throws Exception{
  String source="# Heading\r\n\nA unique passage 😀 with café and é.\nTail";int at=source.indexOf("A unique");ReaderAnchor.Anchor a=anchor(source,at);
  ReaderAnchor.Resolution exact=ReaderAnchor.resolve(a,source,OK);yes(exact.status==ReaderAnchor.Status.EXACT&&exact.offset==at&&exact.hashedUnits==source.length()&&exact.searchComparisons==0,"exact fingerprint offset");
  String changed="inserted before\n"+source;ReaderAnchor.Resolution moved=ReaderAnchor.resolve(a,changed,OK);yes(moved.status==ReaderAnchor.Status.RELOCATED&&moved.offset==at+16,"context moved uniquely");
  yes(moved.searchComparisons<=2L*changed.length()+2L*288,"actual KMP work bound");
  yes(ReaderAnchor.resolve(a,source+"\n"+source,OK).status==ReaderAnchor.Status.UNRESOLVED,"duplicate context unresolved");
  yes(ReaderAnchor.resolve(a,source.replace("unique","deleted"),OK).status==ReaderAnchor.Status.UNRESOLVED,"changed quote unresolved");
  ReaderAnchor.Anchor empty=anchor("",0);yes(ReaderAnchor.resolve(empty,"",OK).status==ReaderAnchor.Status.EXACT,"empty exact");yes(ReaderAnchor.resolve(empty,"x",OK).status==ReaderAnchor.Status.UNRESOLVED,"empty no heuristic");
  ReaderAnchor.Anchor emoji=anchor("abc😀tail",4);yes(emoji.offset==3,"surrogate offset normalized");
  String near="😀".repeat(180);ReaderAnchor.Anchor unicode=anchor(near,131);yes(!Character.isLowSurrogate(unicode.quote.charAt(0))&&!Character.isHighSurrogate(unicode.quote.charAt(unicode.quote.length()-1)),"quote codepoint boundaries");
  yes(unicode.quote.length()<=160&&unicode.prefix.length()<=64&&unicode.suffix.length()<=64,"context bounds");
  ReaderAnchor.Anchor heading=ReaderAnchor.capture(source,0,ReaderAnchor.Kind.HEADING,OK);yes(ReaderAnchor.resolve(heading,"new\n"+source,OK).status==ReaderAnchor.Status.RELOCATED,"heading same context");
  for(String bad:new String[]{"\ud800","\udc00","a\ud800b"})no(()->anchor(bad,0));no(()->anchor("x",2));
  no(()->ReaderAnchor.capture("a",0,ReaderAnchor.Kind.TEXT,()->{throw new IOException("cancelled");}));
  ReaderAnchor.Anchor eof=anchor("end",3);yes(ReaderAnchor.resolve(eof,"before end",OK).offset==10,"EOF context");
  String adversarial="a".repeat(200000);ReaderAnchor.Anchor pattern=anchor("a".repeat(287)+"b",64);ReaderAnchor.Resolution result=ReaderAnchor.resolve(pattern,adversarial,OK);yes(result.status==ReaderAnchor.Status.UNRESOLVED&&result.searchComparisons<=2L*adversarial.length()+576,"repeated prefix actual comparison bound");
  final int[] cancels={0};no(()->ReaderAnchor.capture("a"+"😀".repeat(10000),0,ReaderAnchor.Kind.TEXT,()->{if(++cancels[0]==3)throw new IOException("cancelled");}));yes(cancels[0]==3,"periodic supplementary validation cancellation");
  String maximum=scalarText(8<<20);ReaderAnchor.Anchor cap=anchor(maximum,0);yes(cap.sourceLength==(8<<20),"exact native byte/unit max");no(()->anchor(maximum+"a",0));no(()->anchor("é".repeat((4<<20)+1),0));
  CharSequence over=new CharSequence(){public int length(){return (8<<20)+1;}public char charAt(int i){throw new AssertionError("read before length validation");}public CharSequence subSequence(int s,int e){throw new AssertionError();}public String toString(){throw new AssertionError("copied");}};no(()->ReaderAnchor.capture(over,0,ReaderAnchor.Kind.TEXT,OK));
  CharSequence noCopy=new CharSequence(){public int length(){return 3;}public char charAt(int i){return "abc".charAt(i);}public CharSequence subSequence(int s,int e){throw new AssertionError("source slice");}public String toString(){throw new AssertionError("whole source copy");}};yes(ReaderAnchor.capture(noCopy,1,ReaderAnchor.Kind.TEXT,OK).offset==1,"streaming no whole source copy");
  for(Field f:ReaderAnchor.Anchor.class.getDeclaredFields())yes(Modifier.isFinal(f.getModifiers())&&f.getType()!=CharSequence.class,"immutable no source field");
 }
 static void moreAnchors()throws Exception{
  for(String text:new String[]{"", "abc", "é", "Ω", "😀", "a😀é\r\n"}){
   String expected=hex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   yes(anchor(text,0).sourceHash.equals(expected),"strictUTF8 independent fingerprint oracle");
  }
  ReaderAnchor.Anchor original=anchor("😀x",0);
  ReaderAnchor.Anchor forged=ReaderAnchor.restore(ReaderAnchor.Kind.TEXT,original.sourceHash,3,0,"a","","");
  no(()->ReaderAnchor.resolve(forged,"😀x",OK));
  final int[] reads={0};String source="a".repeat(20000);
  CharSequence observable=new CharSequence(){public int length(){return source.length();}public char charAt(int i){reads[0]++;return source.charAt(i);}public CharSequence subSequence(int s,int e){throw new AssertionError();}};
  ReaderAnchor.Anchor absent=anchor("b".repeat(200),0);
  no(()->ReaderAnchor.resolve(absent,observable,()->{if(reads[0]>=source.length()+4096)throw new IOException("cancelled");}));
  yes(reads[0]<=source.length()+8192,"search phase periodic cancellation");
  ReaderAnchor.Anchor even=anchor("é".repeat(4<<20),0);yes(even.sourceLength==(4<<20),"exact8MiB multibyte source");
 }
 static void terminalCancellation()throws Exception{
  int escaped=0;
  for(String mode:new String[]{"capture","exact","duplicate"}){
   String text=mode.equals("duplicate")?"abcabc":"abc";final int[] reads={0};final boolean[] cancelled={false};
   CharSequence immutable=new CharSequence(){public int length(){return text.length();}public char charAt(int i){if(++reads[0]>text.length())cancelled[0]=true;return text.charAt(i);}public CharSequence subSequence(int s,int e){throw new AssertionError("no source copies");}};
   ReaderAnchor.Cancellation cancellation=()->{if(cancelled[0])throw new IOException("cancelled");};
   ReaderAnchor.Anchor a=anchor("abc",0);
   boolean refused=false;try{if(mode.equals("capture"))ReaderAnchor.capture(immutable,0,ReaderAnchor.Kind.TEXT,cancellation);else ReaderAnchor.resolve(a,immutable,cancellation);}catch(IOException expected){refused=true;checks++;}
   if(!refused){System.out.println("RED terminal "+mode+" returned after cancellation");escaped++;}
   yes(cancelled[0],"terminal "+mode+" cancellation raised after hash");
  }
  // Empty-source anchors skip KMP. A post-fingerprint length read can still observe retirement.
  final boolean[] finishedHash={false},cancelled={false};final int[] reads={0};String text="new";
  CharSequence immutable=new CharSequence(){public int length(){if(finishedHash[0])cancelled[0]=true;return text.length();}public char charAt(int i){reads[0]++;return text.charAt(i);}public CharSequence subSequence(int s,int e){throw new AssertionError();}};
  ReaderAnchor.Cancellation cancellation=()->{if(cancelled[0])throw new IOException("cancelled");if(reads[0]==text.length())finishedHash[0]=true;};
  ReaderAnchor.Anchor empty=anchor("",0);try{ReaderAnchor.resolve(empty,immutable,cancellation);System.out.println("RED terminal empty-anchor returned after cancellation");escaped++;}catch(IOException expected){checks++;}yes(cancelled[0],"empty-anchor terminal cancellation");
  yes(escaped==0,"all terminal metadata branches honor cancellation; escaped="+escaped);
 }
 static void stores()throws Exception{
  Prefs p=new Prefs();ReaderPositionStore s=store(p);ReaderAnchor.Anchor a=anchor("passage",0);yes(s.read(uri(0))==null,"absent distinct");
  s.saveLocation(uri(0),a);ReaderPositionStore.Bookmark b=s.addBookmark(uri(0),"first",a);ReaderPositionStore.DocumentState d=store(p).read(uri(0));yes(d.location.sourceHash.equals(a.sourceHash)&&d.bookmarks.size()==1&&d.bookmarks.get(0).id.equals(b.id),"reopen durable immutable data");
  try{d.bookmarks.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
  no(()->s.addBookmark(null,"untitled",a));no(()->s.saveLocation("file:///PRIVATE",a));no(()->s.addBookmark(uri(0),"x".repeat(81),a));
  s.saveLocation(uri(1),anchor("different",0));yes(s.read(uri(0)).location.sourceHash.equals(a.sourceHash),"exact URI independent SaveAs");
  s.removeBookmark(uri(0),b.id);yes(s.read(uri(0)).bookmarks.isEmpty()&&s.read(uri(0)).location!=null,"remove bookmark only");s.forget(uri(1));yes(s.read(uri(1))==null,"forget local record");
  for(int i=0;i<16;i++)s.addBookmark(uri(0),"b"+i,a);String before=p.raw;no(()->s.addBookmark(uri(0),"overflow",a));yes(p.raw.equals(before),"per document no eviction");
  for(int i=1;i<64;i++)s.saveLocation(uri(i),a);s.saveLocation(uri(64),a);yes(s.read(uri(0)).bookmarks.size()==16&&s.read(uri(1))==null&&s.read(uri(64))!=null,"location only LRU preserves bookmarks");
  s.saveLocation(uri(2),a);s.saveLocation(uri(65),a);yes(s.read(uri(3))==null&&s.read(uri(2))!=null,"LRU touch");
  Prefs global=new Prefs();ReaderPositionStore g=store(global);for(int i=0;i<8;i++)for(int j=0;j<16;j++)g.addBookmark(uri(i),"b",a);String all=global.raw;no(()->g.addBookmark(uri(9),"b",a));yes(global.raw.equals(all),"global128 no eviction");
  Prefs blocked=new Prefs();ReaderPositionStore full=store(blocked);for(int i=0;i<64;i++)full.addBookmark(uri(i),"b",a);String fixed=blocked.raw;no(()->full.saveLocation(uri(65),a));yes(blocked.raw.equals(fixed),"all bookmarked cap refuses autosave");
  p.success=false;no(()->s.saveLocation(uri(0),anchor("changed",0)));yes(s.read(uri(0)).location.quote.equals("changed"),"false commit may change memory no rollback");
  Prefs corrupt=new Prefs();ReaderPositionStore c=store(corrupt);
  for(String raw:new String[]{"{", "[".repeat(10000)+"0"+"]".repeat(10000), "{\"version\":1,\"version\":1,\"documents\":[]}","{\"version\":2,\"documents\":[]}","{\"version\":1,\"documents\":[]}tail", "x".repeat(524289)}){corrupt.raw=raw;no(()->c.read(uri(0)));no(()->c.saveLocation(uri(0),a));no(()->c.removeBookmark(uri(0),b.id));no(()->c.forget(uri(0)));yes(corrupt.raw.equals(raw)&&corrupt.commits==0,"corrupt bytes preserved all paths");}
  Prefs valid=new Prefs();ReaderPositionStore v=store(valid);v.saveLocation(uri(0),a);String good=valid.raw;valid.raw=good.replace(a.sourceHash,"G".repeat(64));no(()->v.read(uri(0)));yes(valid.commits==1,"invalid hydrated hash never rewritten");
  valid.raw=good.replace("\"offset\":0","\"offset\":99");no(()->v.read(uri(0)));valid.raw=good.replace("\"kind\":\"TEXT\"","\"kind\":\"UNKNOWN\"");no(()->v.read(uri(0)));
  Prefs punctuation=new Prefs();ReaderPositionStore punctuationStore=store(punctuation);String label="quotes \" \\ [] {} ' 😀";punctuationStore.addBookmark(uri(0),label,a);yes(punctuationStore.read(uri(0)).bookmarks.get(0).label.equals(label),"escaped JSON label");
  final String longUri="content://p/"+"x".repeat(4096-12);ReaderPositionStore u=store(new Prefs());u.saveLocation(longUri,a);no(()->u.saveLocation(longUri+"x",a));
 }
 static void asciiEscapeRegression()throws Exception{
  Prefs p=new Prefs();ReaderPositionStore s=store(p);ReaderAnchor.Anchor a=anchor("passage",0);ReaderPositionStore.Bookmark b=s.addBookmark(uri(0),"LABEL",a);
  String valid=p.raw;String invalid=valid.replace("LABEL","\\uＦＦＦＦ");p.raw=invalid;int accepted=0;
  Work[] operations={()->s.read(uri(0)),()->s.saveLocation(uri(0),a),()->s.addBookmark(uri(0),"new",a),()->s.removeBookmark(uri(0),b.id),()->s.forget(uri(0))};
  String[] names={"read","saveLocation","addBookmark","removeBookmark","forget"};
  for(int i=0;i<operations.length;i++){
   p.raw=invalid;int before=p.commits;try{operations[i].run();System.out.println("RED nonASCII JSON escape accepted by "+names[i]);accepted++;}catch(IOException expected){checks++;}
   if(!p.raw.equals(invalid)||p.commits!=before){System.out.println("RED corrupt reading data changed by "+names[i]);accepted++;}else checks++;
  }
  yes(accepted==0,"nonASCII escapes refused and preserved across all operations");
  p.raw=valid.replace("LABEL","\\u004C\\u0061\\u0062\\u0065\\u006C");
  yes(s.read(uri(0)).bookmarks.get(0).label.equals("Label"),"valid ASCII JSON escapes roundtrip");
 }
 static void moreStoreEdges()throws Exception{
  ReaderAnchor.Anchor a=anchor("abc",0);Prefs p=new Prefs();ReaderPositionStore s=store(p);ReaderPositionStore.Bookmark b=s.addBookmark(uri(0),"b",a);String good=p.raw;
  JSONObject envelope=new JSONObject(good);JSONArray documents=envelope.getJSONArray("documents");documents.put(documents.getJSONObject(0));String duplicate=envelope.toString();
  p.raw=duplicate;no(()->s.read(uri(0)));yes(p.raw.equals(duplicate)&&p.commits==1,"duplicate document preserved");
  envelope=new JSONObject(good);JSONArray bookmarks=envelope.getJSONArray("documents").getJSONObject(0).getJSONArray("bookmarks");bookmarks.put(bookmarks.getJSONObject(0));p.raw=envelope.toString();no(()->s.read(uri(0)));
  for(String bad:new String[]{good.replace("\"offset\":0","\"offset\":-1"),good.replace("\"length\":3","\"length\":8388609"),good.replace("\"sourceMissing\":0","\"x\":0")+"trailing",good.replace("\"TEXT\"","\"TEXT\",\"kind\":\"TEXT\""),good.replace("\"version\":1","\"version\":1.0"),good.replace("\"version\":1","\"version\":true")}){p.raw=bad;no(()->s.read(uri(0)));yes(p.raw.equals(bad)&&p.commits==1,"invalid hydrated metadata unchanged");}
  p.raw=good+" ".repeat(524288-good.length());yes(s.read(uri(0)).bookmarks.size()==1,"exact serialized unit cap");String full=p.raw;p.raw+=" ";no(()->s.read(uri(0)));yes(p.raw.length()==524289,"over serialized cap not reset");
  p.raw=good.replace("\"version\"","\"\\u0076ersion\",\"version\":1");no(()->s.read(uri(0))); // decoded duplicate key
  p.raw=good;no(()->s.addBookmark(uri(0),"bad\ud800",a));
  for(String raw:new String[]{good.replace("\"uri\"","\"uri\":\"x\",\"uri\""),good.replace("\"quote\":\"abc\"","\"quote\":\"\\ud800\""),good.replace("\"offset\":0","\"offset\":9223372036854775808")}){p.raw=raw;no(()->s.read(uri(0)));}
  Prefs large=new Prefs();ReaderPositionStore bounded=store(large);ReaderAnchor.Anchor controls=anchor("\u0000".repeat(288),64);boolean limited=false;int accepted=0;
  for(int i=0;i<64&&!limited;i++)for(int j=0;j<2;j++){
   String base="content://provider/document/"+i+"/";String key=base+"x".repeat(4096-base.length());String before=large.raw;
   try{bounded.addBookmark(key,"\u0000".repeat(80),controls);accepted++;}catch(IOException limit){limited=true;yes(large.raw.equals(before),"oversize serialized write preserves prior bytes");break;}
  }
  yes(limited&&accepted<128&&large.raw.length()<=524288,"actual escaped serialized cap before bookmark count cap");
  for(Field field:ReaderPositionStore.DocumentState.class.getDeclaredFields())yes(Modifier.isFinal(field.getModifiers()),"state immutable");
  for(Field field:ReaderPositionStore.Bookmark.class.getDeclaredFields())yes(Modifier.isFinal(field.getModifiers()),"bookmark immutable");
 }
 static final class DiskPrefs implements SharedPreferences {
  String memory,disk;boolean fail;int commits;
  public String getString(String key,String fallback){return memory==null?fallback:memory;}
  public Editor edit(){return new Editor(){String next;public Editor putString(String key,String value){next=value;return this;}public boolean commit(){commits++;memory=next;if(fail)return false;disk=next;return true;}};}
  void reopen(){memory=disk;}
 }
 static void removalRetryDurability()throws Exception {
  for(String mode:new String[]{"bookmark-with-location","bookmark-only","forget"}){
   DiskPrefs prefs=new DiskPrefs();ReaderPositionStore saved=new ReaderPositionStore(new Context(prefs));String key=uri(987);
   ReaderAnchor.Anchor place=anchor("keep private text",0);
   if(mode.equals("bookmark-with-location"))saved.saveLocation(key,place);
   ReaderPositionStore.Bookmark bookmark=saved.addBookmark(key,"kept until removal commits",place);
   Work remove=()->{if(mode.equals("forget"))saved.forget(key);else saved.removeBookmark(key,bookmark.id);};
   prefs.fail=true;no(remove);yes(prefs.memory!=null&&!prefs.memory.equals(prefs.disk),"failed removal changes memory but not disk");
   prefs.fail=false;remove.run();prefs.reopen();ReaderPositionStore.DocumentState reopened=new ReaderPositionStore(new Context(prefs)).read(key);
   yes(mode.equals("bookmark-with-location")?reopened!=null&&reopened.location!=null&&reopened.bookmarks.isEmpty():reopened==null,"successful removal retry remains removed on reopen: "+mode);
  }
 }
 public static void main(String[]args)throws Exception{anchors();moreAnchors();terminalCancellation();stores();moreStoreEdges();asciiEscapeRegression();removalRetryDurability();System.out.println("Reader positions: "+checks+" checks PASS");}
}
