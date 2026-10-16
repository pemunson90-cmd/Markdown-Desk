package com.example.markdowndesk;
import android.content.*;import android.net.Uri;import android.os.*;import android.database.Cursor;import java.util.*;import java.util.concurrent.*;import java.lang.reflect.*;
public final class WorkspaceUiCheck {
 static int checks;static void yes(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static final Uri ROOT=Uri.parse("content://fixture/tree/root");
 static final class Prefs implements SharedPreferences {String value;boolean commit=true;public String getString(String key,String fallback){return value==null?fallback:value;}public Editor edit(){return new Editor(){public Editor putString(String key,String v){value=v;return this;}public boolean commit(){return commit;}};}}
 static final class Provider extends ContentResolver {
  final List<UriPermission> grants=new ArrayList<>();final Map<String,List<String[]>> children=new HashMap<>();int takes,takenFlags,queries,closes;boolean security,retain=true,revoke,fail,partial;volatile CountDownLatch entered,release;volatile CancellationSignal lastSignal;
  public List<UriPermission> getPersistedUriPermissions(){return new ArrayList<>(grants);}public void takePersistableUriPermission(Uri uri,int flags){takes++;takenFlags=flags;if(security)throw new SecurityException("denied");if(retain){grants.removeIf(g->g.getUri().toString().equals(uri.toString()));grants.add(new UriPermission(uri,(flags&1)!=0));}}
  public Cursor query(Uri uri,String[] projection,String s,String[] a,String order,CancellationSignal signal){queries++;lastSignal=signal;if(entered!=null)entered.countDown();CountDownLatch wait=release;if(wait!=null)try{wait.await();}catch(InterruptedException e){throw new RuntimeException(e);}if(revoke)throw new SecurityException("revoked");if(fail)return null;
   String id=uri.toString().split("/document/",2)[1].split("/children",2)[0];List<String[]> rows=new ArrayList<>(children.getOrDefault(id,List.of()));
   return new Cursor(){int at=-1;public boolean moveToNext(){if(partial&&at==0)throw new IllegalStateException("partial");return ++at<rows.size();}public int getColumnIndex(String n){return Arrays.asList(projection).indexOf(n);}public boolean isNull(int n){return n>=3;}public String getString(int n){return rows.get(at)[n];}public long getLong(int n){return 0;}public void close(){closes++;}};
  }
 }
 static String[] dir(String id){return new String[]{id,id,"vnd.android.document/directory"};}static String[] file(String id){return new String[]{id,id+".md","text/markdown"};}
 static WorkspaceHarness setup(Prefs prefs,Provider provider){return new WorkspaceHarness(new Context(prefs,provider));}
 static boolean busy()throws Exception {Field f=WorkspaceDirectoryLoader.class.getDeclaredField("owner");f.setAccessible(true);return f.get(null)!=null;}
 static void settle(WorkspaceHarness h)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);do{h.drainLocal();if(!busy()){h.drainLocal();return;}Thread.sleep(2);}while(System.nanoTime()<end);throw new AssertionError("browse did not settle");}
 static Intent selection(int flags){return new Intent("result").setData(ROOT).addFlags(flags);}
 static void grantJourney()throws Exception {
  Prefs prefs=new Prefs();Provider p=new Provider();p.children.put("root",List.of(dir("nested"),file("first")));p.children.put("nested",Collections.singletonList(file("inside")));WorkspaceHarness h=setup(prefs,p);
  h.add();yes(h.pickerRequest==1005&&h.picker.action.equals(Intent.ACTION_OPEN_DOCUMENT_TREE)&&h.picker.getFlags()==195,"actual folder picker exact flags");h.result(0,null);h.drainLocal();yes(p.takes==0&&prefs.value==null,"picker cancel no mutations");
  h.result(-1,selection(1));yes(h.error.contains("could not be saved")&&p.takes==0,"temporary grant refused");
  h.result(-1,selection(65));h.drainLocal();settle(h);yes(p.takenFlags==1&&p.takes==1&&prefs.value!=null,"returned read only persisted then actual remember");yes(p.queries==1&&h.status().contains("nested"),"remember immediately queries real root reader");yes(h.buttons.isEmpty(),"browser row buttons not globally retained");
  h.row("Folder · nested").tap();settle(h);yes(h.depth()==2&&h.status().contains("inside.md"),"actual nested reader query");h.up();settle(h);yes(h.depth()==1&&h.status().contains("first.md"),"Up returns actual root");
  h.row("File · first.md").tap();yes(h.opens==1&&h.openFlags==0&&h.openExisting&&h.opened.toString().equals("content://fixture/tree/root/document/first")&&!h.browsing(),"exact ordinary open route after disposal");
  h.roots();h.drainLocal();yes(h.folderDialog().items.length==1,"saved root reopened");h.chooseRoot(0);settle(h);h.browserDialog().back();yes(!h.browsing(),"Back at root closes");
  p.grants.clear();h.roots();h.drainLocal();yes(h.folderDialog().items[0].contains("Access needed"),"actual current grant join");h.chooseRoot(0);h.folderDialog().getButton(-3).tap();h.drainLocal();yes(new WorkspaceRootStore(h.application).list(p).isEmpty(),"remove affects shortcut only");
 }
 static void failuresAndStale()throws Exception {
  Prefs prefs=new Prefs();Provider p=new Provider();WorkspaceHarness h=setup(prefs,p);p.security=true;h.result(-1,selection(67));h.drainLocal();yes(prefs.value==null&&!h.browsing(),"persist SecurityException visible no success");p.security=false;p.retain=false;h.result(-1,selection(65));h.drainLocal();yes(prefs.value==null&&!h.browsing(),"absent actual persisted read refused");p.retain=true;prefs.commit=false;h.result(-1,selection(65));h.drainLocal();yes(!h.browsing()&&h.error.contains("could not be saved"),"commit false no durable success");prefs.commit=true;
  p.children.put("root",Collections.singletonList(dir("root")));h.result(-1,selection(65));settle(h);h.row("Folder · root").tap();yes(h.depth()==1&&h.error.contains("already"),"ancestry URI cycle refused");h.cancel();
  p.children.put("root",List.of(file("one"),file("two")));p.partial=true;h.roots();h.drainLocal();h.chooseRoot(0);settle(h);yes(h.status().contains("Partial results")&&h.status().contains("one.md")&&!h.status().contains("empty"),"real reader failure partial distinct from empty");android.widget.Button old=h.row("File · one.md");h.pause();old.tap();yes(h.opens==0&&!h.browsing(),"stale row after pause cannot open");p.partial=false;
  p.revoke=true;h.roots();h.drainLocal();h.chooseRoot(0);settle(h);yes(h.status().contains("revoked")&&!h.status().contains("empty"),"revocation not empty");h.cancel();p.revoke=false;p.children.put("root",List.of());h.roots();h.drainLocal();h.chooseRoot(0);settle(h);yes(h.status().contains("This folder is empty"),"observed complete empty");h.cancel();
  List<String[]> many=new ArrayList<>();for(int n=0;n<101;n++)many.add(file("f"+n));p.children.put("root",many);h.roots();h.drainLocal();h.chooseRoot(0);settle(h);yes(h.status().contains("not all items")&&!h.status().contains("This folder is empty"),"real reader limited distinguished");h.cancel();
 }
 static void boundedNavigation()throws Exception {
  Prefs prefs=new Prefs();Provider p=new Provider();for(int n=0;n<33;n++)p.children.put(n==0?"root":"d"+n,Collections.singletonList(dir("d"+(n+1))));WorkspaceHarness h=setup(prefs,p);h.result(-1,selection(65));settle(h);
  for(int n=1;n<32;n++){h.row("Folder · d"+n).tap();settle(h);}yes(h.depth()==32,"32 frames admitted");int queries=p.queries;h.row("Folder · d32").tap();yes(h.depth()==32&&p.queries==queries&&h.error.contains("32"),"depth33 refused before query/push");h.cancel();
  p.children.put("root",Collections.singletonList(new String[]{"binary","binary.bin","application/octet-stream"}));h.roots();h.drainLocal();h.chooseRoot(0);settle(h);android.widget.Button stale=h.row("File · binary.bin");stale.tap();yes(h.opens==0,"other MIME requires explicit text confirmation");h.confirm();yes(h.opens==1&&h.opened.toString().endsWith("/binary"),"explicit other MIME normal open");
  h.roots();h.drainLocal();h.chooseRoot(0);settle(h);stale=h.row("File · binary.bin");h.retry();settle(h);stale.tap();yes(h.opens==1,"old row after same-frame reload rejected");h.cancel();
 }
 static void confirmationConsent()throws Exception {
  Prefs prefs=new Prefs();Provider p=new Provider();p.children.put("root",List.of(new String[]{"old","old.bin","application/octet-stream"},new String[]{"new","new.bin","application/octet-stream"}));WorkspaceHarness h=setup(prefs,p);h.result(-1,selection(65));settle(h);
  h.row("File · old.bin").tap();WorkspaceHarness.AlertDialog old=h.confirmation();android.widget.Button oldPositive=old.getButton(-1);old.dismiss();oldPositive.tap();yes(h.opens==0&&h.browsing(),"dismissed confirmation late positive cannot open");yes(h.confirmation()==null,"dismiss clears exact confirmation reference");
  h.row("File · old.bin").tap();old=h.confirmation();oldPositive=old.getButton(-1);h.row("File · new.bin").tap();WorkspaceHarness.AlertDialog current=h.confirmation();oldPositive.tap();yes(h.opens==0&&h.confirmation()==current&&current.isShowing(),"replaced old positive cannot consume new dialog");current.getButton(-1).tap();yes(h.opens==1&&h.opened.toString().endsWith("/new")&&!h.browsing(),"current showing confirmation opens exact selected file");
 }
 static void paletteProperties()throws Exception {
  Prefs prefs=new Prefs();Provider p=new Provider();p.children.put("root",Collections.singletonList(new String[]{"file","file.bin","application/octet-stream"}));WorkspaceHarness h=setup(prefs,p);h.result(-1,selection(65));settle(h);h.cancel();
  android.view.View recycled=null;
  for(int theme=0;theme<9;theme++){
   h.palette=UiTheme.palette(theme,false);h.roots();h.drainLocal();WorkspaceHarness.AlertDialog roots=h.folderDialog();yes(roots.customTitle!=null&&roots.customTitle.color==h.palette.text,"root title active palette "+theme);yes(roots.adapter!=null,"root rows use explicit themed binder");
   android.widget.TextView row=(android.widget.TextView)roots.adapter.getView(0,recycled,null);yes(row.color==h.palette.text,"new/recycled saved root foreground "+theme);recycled=row;row.color=0;yes(((android.widget.TextView)roots.adapter.getView(0,row,null)).color==h.palette.text,"every recycled row resets stale color "+theme);
   yes(((android.graphics.drawable.GradientDrawable)roots.window.background).color==h.palette.raised,"actual dialog background "+theme);yes(roots.getButton(-1).color==UiTheme.readableForeground(h.palette.accent,h.palette.raised),"actual dialog button foreground "+theme);
   h.chooseRoot(0);settle(h);yes(h.browserDialog().customTitle!=null&&h.browserDialog().customTitle.color==h.palette.text,"browser title active palette "+theme);h.row("File · file.bin").tap();WorkspaceHarness.AlertDialog confirm=h.confirmation();yes(confirm.customTitle!=null&&confirm.customTitle.color==h.palette.text&&confirm.messageView.color==h.palette.text,"confirmation title/message active palette "+theme);confirm.dismiss();h.options();WorkspaceHarness.AlertDialog options=h.folderDialog();yes(options.customTitle!=null&&options.customTitle.color==h.palette.text&&options.messageView.color==h.palette.text,"root options title/message active palette "+theme);h.cancel();
  }
 }
 static void pauseAutosaveOrdering()throws Exception {
  Prefs prefs=new Prefs();Provider p=new Provider();WorkspaceHarness h=setup(prefs,p);p.entered=new CountDownLatch(1);p.release=new CountDownLatch(1);h.result(-1,selection(65));h.drainLocal();yes(p.entered.await(3,TimeUnit.SECONDS),"pause fixture actual query entered");
  CountDownLatch cancelEntered=new CountDownLatch(1),cancelRelease=new CountDownLatch(1);p.lastSignal.setOnCancelListener(()->{cancelEntered.countDown();try{if(!cancelRelease.await(3,TimeUnit.SECONDS))throw new AssertionError("finite cancel hook timed out");}catch(InterruptedException failure){throw new RuntimeException(failure);}});
  h.dirty=true;h.pauseTrace.clear();List<String> observed=new ArrayList<>();int[] queued={-1};Throwable[] failure={null};
  Thread observer=new Thread(()->{try{if(!cancelEntered.await(3,TimeUnit.SECONDS))throw new AssertionError("cancel listener not reached");observed.addAll(h.pauseTrace);queued[0]=h.io.tasks.size();}catch(Throwable problem){failure[0]=problem;}finally{cancelRelease.countDown();}},"pause-cancel-observer");observer.start();
  try{h.pause();}finally{cancelRelease.countDown();p.release.countDown();}observer.join(4000);yes(!observer.isAlive()&&failure[0]==null,"finite blocking cancel observation completed");
  yes(observed.equals(List.of("capture","recovery","session"))&&queued[0]==2,"local capture/recovery/session queued BEFORE provider cancellation waits");settle(h);
 }
 static void physicalSlot()throws Exception {
  Prefs prefs=new Prefs();Provider p=new Provider();WorkspaceHarness h=setup(prefs,p);p.entered=new CountDownLatch(1);p.release=new CountDownLatch(1);h.result(-1,selection(65));h.drainLocal();yes(p.entered.await(3,TimeUnit.SECONDS),"physical provider query entered");h.destroy();h.result(-1,selection(65));yes(p.takes==1,"destroyed picker result cannot queue registry work");
  WorkspaceHarness next=setup(prefs,p);next.roots();next.drainLocal();next.chooseRoot(0);yes(next.status().contains("Retry later")&&p.queries==1,"recreation cannot replace cancelled physical slot");final int[] saves={0};next.io.execute(()->saves[0]++);next.io.drain();yes(saves[0]==1,"independent save/session queue progresses during stuck query");p.release.countDown();settle(next);next.retry();settle(next);yes(p.queries==2,"retry only after actual query cleanup");next.cancel();
 }
 public static void main(String[] args){try{Looper.getMainLooper();if(args.length>0){if(args[0].equals("confirmation"))confirmationConsent();else if(args[0].equals("pause"))pauseAutosaveOrdering();else paletteProperties();System.out.println("review regression PASS");System.exit(0);}grantJourney();failuresAndStale();boundedNavigation();physicalSlot();confirmationConsent();paletteProperties();pauseAutosaveOrdering();System.out.println("Workspace actual UI journey: "+checks+" checks PASS; real store/reader/process broker, Android UI boundaries only");System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
