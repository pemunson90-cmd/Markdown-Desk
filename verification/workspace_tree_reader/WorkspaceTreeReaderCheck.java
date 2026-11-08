package com.example.markdowndesk;
import android.content.*;import android.database.*;import android.net.*;import android.os.*;import android.provider.DocumentsContract;
import java.util.*;
public final class WorkspaceTreeReaderCheck {
 static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 interface Action{void run()throws Exception;}static void reject(Action a)throws Exception{try{a.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError("expected rejection");}
 static final class Rows implements Cursor {
  final List<Object[]> data=new ArrayList<>();int at=-1,moves,closes;boolean missing,closeFail,getFail,moveFail;java.lang.Error bodyFatal,closeFatal;Runnable onMove;
  Rows add(String id,String name,String mime){data.add(new Object[]{id,name,mime,null,-1L,0L});return this;}
  public boolean moveToNext(){moves++;if(bodyFatal!=null)throw bodyFatal;if(onMove!=null)onMove.run();if(moveFail)throw new SecurityException();return ++at<data.size();}
  public int getColumnIndex(String n){return missing?-1:Arrays.asList("document_id","_display_name","mime_type","_size","last_modified","flags").indexOf(n);}
  public boolean isNull(int c){return data.get(at)[c]==null;}public String getString(int c){if(getFail)throw new IllegalStateException();return (String)data.get(at)[c];}
  public long getLong(int c){return ((Number)data.get(at)[c]).longValue();}public void close(){closes++;if(closeFatal!=null)throw closeFatal;if(closeFail)throw new IllegalStateException();}
 }
 static final class Resolver extends ContentResolver {
  Rows rows=new Rows();int calls;Uri queried;CancellationSignal signal;String[] projection;RuntimeException failure;boolean nullCursor;
  public Cursor query(Uri u,String[] p,String s,String[] a,String o,CancellationSignal c){calls++;queried=u;projection=p;signal=c;if(failure!=null)throw failure;return nullCursor?null:rows;}
 }
 static WorkspaceTreeReader reader(Resolver r){return new WorkspaceTreeReader(r,Uri.parse("content://provider/tree/root"));}
 static WorkspaceTreeReader.Page list(WorkspaceTreeReader r,CancellationSignal c){return r.prepareList(r.root(),WorkspaceTreeReader.Limits.defaults(),c).read();}
 public static void main(String[] args)throws Exception {
  Resolver r=new Resolver();WorkspaceTreeReader tree=reader(r);CancellationSignal c=new CancellationSignal();
  WorkspaceTreeReader.Page p=list(tree,c);check(p.state==WorkspaceTreeReader.State.COMPLETE&&p.entries.isEmpty(),"empty complete");check(r.calls==1&&r.rows.closes==1&&r.signal==c&&r.projection.length==6,"query contract and close");
  check(r.queried.toString().equals("content://provider/tree/root/document/root/children"),"exact root query");
  r.rows=new Rows().add("nested","Folder",DocumentsContract.Document.MIME_TYPE_DIR).add("file","a.md","text/markdown");p=list(tree,c);
  check(p.entries.size()==2&&p.entries.get(0).directory!=null&&p.entries.get(1).directory==null,"types");check(p.entries.get(1).size==null&&p.entries.get(1).modified==null,"unknown metadata");
  tree.prepareList(p.entries.get(0).directory,WorkspaceTreeReader.Limits.defaults(),c).read();check(r.queried.toString().endsWith("/document/nested/children"),"nested minted handle");
  final WorkspaceTreeReader foreign=reader(new Resolver());reject(()->tree.prepareList(foreign.root(),WorkspaceTreeReader.Limits.defaults(),c).read());
  reject(()->new WorkspaceTreeReader(r,Uri.parse("file:///tree/root")));reject(()->new WorkspaceTreeReader(r,Uri.parse("content://provider/document/root")));
  reject(()->new WorkspaceTreeReader.Limits(0,1,1,1));reject(()->new WorkspaceTreeReader.Limits(257,1,1,1));reject(()->new WorkspaceTreeReader.Limits(1,101,1,1));reject(()->new WorkspaceTreeReader.Limits(1,1,65537,1));reject(()->new WorkspaceTreeReader.Limits(1,1,1,4097));
  r.rows=new Rows().add("one","one","text/plain").add("two","two","text/plain");p=tree.prepareList(tree.root(),new WorkspaceTreeReader.Limits(1,100,65536,4096),c).read();check(p.state==WorkspaceTreeReader.State.LIMITED&&p.entries.size()==1&&r.rows.moves==1,"row cap no lookahead");
  r.rows=new Rows().add("one","one","text/plain");p=tree.prepareList(tree.root(),new WorkspaceTreeReader.Limits(256,1,65536,4096),c).read();check(p.state==WorkspaceTreeReader.State.LIMITED&&r.rows.moves==1,"result cap conservative");
  r.rows=new Rows().add("one","one","text/plain");p=tree.prepareList(tree.root(),new WorkspaceTreeReader.Limits(256,100,1,4096),c).read();check(p.state==WorkspaceTreeReader.State.LIMITED&&p.entries.isEmpty(),"text cap before retention");
  r.rows=new Rows().add("one","one","text/plain");p=list(tree,c);int units=p.retainedTextUnits;
  r.rows=new Rows().add("one","one","text/plain");p=tree.prepareList(tree.root(),new WorkspaceTreeReader.Limits(256,100,units,4096),c).read();check(p.entries.size()==1&&p.state==WorkspaceTreeReader.State.LIMITED&&r.rows.moves==1,"exact text cap LIMITED without lookahead");
  r.rows=new Rows().add("one","one","text/plain");check(tree.prepareList(tree.root(),new WorkspaceTreeReader.Limits(256,100,units-1,4096),c).read().entries.isEmpty(),"one under text");
  r.rows=new Rows().add("one","a","text/plain").add("one","b","text/plain");p=list(tree,c);check(p.state==WorkspaceTreeReader.State.FAILED&&p.error==WorkspaceTreeReader.Error.MALFORMED_PROVIDER&&p.entries.size()==1,"duplicate partial failed");
  for(String bad:new String[]{null,"","x".repeat(4097)}){r.rows=new Rows().add(bad,"x","text/plain");check(list(tree,c).state==WorkspaceTreeReader.State.FAILED,"bad id");}
  r.rows=new Rows();r.rows.missing=true;check(list(tree,c).error==WorkspaceTreeReader.Error.MALFORMED_PROVIDER,"missing columns");
  r.rows=new Rows();r.rows.closeFail=true;check(list(tree,c).state==WorkspaceTreeReader.State.FAILED,"close cannot complete");
  r.nullCursor=true;check(list(tree,c).error==WorkspaceTreeReader.Error.PROVIDER_FAILURE,"null cursor");r.nullCursor=false;
  r.failure=new SecurityException();check(list(tree,c).error==WorkspaceTreeReader.Error.ACCESS_REVOKED,"revoked query");r.failure=new IllegalStateException();check(list(tree,c).state==WorkspaceTreeReader.State.FAILED,"query error");r.failure=new OperationCanceledException();check(list(tree,c).state==WorkspaceTreeReader.State.CANCELLED,"query cancelled");r.failure=null;
  CancellationSignal cancelled=new CancellationSignal();cancelled.cancel();int calls=r.calls;check(list(tree,cancelled).state==WorkspaceTreeReader.State.CANCELLED&&calls==r.calls,"pre cancellation no query");
  CancellationSignal during=new CancellationSignal();r.rows=new Rows().add("one","one","text/plain");r.rows.onMove=during::cancel;p=list(tree,during);check(p.state==WorkspaceTreeReader.State.CANCELLED&&p.entries.isEmpty()&&r.rows.closes==1,"ignored provider cancellation seam");
  r.rows=new Rows().add("one","one","text/plain");r.rows.moveFail=true;check(list(tree,c).error==WorkspaceTreeReader.Error.ACCESS_REVOKED&&r.rows.closes==1,"revoked move closes");
  r.rows=new Rows().add("one","one","text/plain");r.rows.getFail=true;check(list(tree,c).state==WorkspaceTreeReader.State.FAILED&&r.rows.closes==1,"getter closes");
  r.rows=new Rows().add("one","one","text/plain");p=list(tree,c);final WorkspaceTreeReader.Page immutable=p;try{immutable.entries.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
  r.rows=new Rows().add("known","name","text/plain");r.rows.data.set(0,new Object[]{"known","name","text/plain",42L,99L,7L});p=list(tree,c);check(p.entries.get(0).size==42&&p.entries.get(0).modified==99&&p.entries.get(0).flags==7,"known metadata");
  r.rows=new Rows().add("field","x".repeat(4096),"text/plain");check(list(tree,c).state==WorkspaceTreeReader.State.COMPLETE,"exact4096 field");
  r.rows=new Rows().add("field","x".repeat(4097),"text/plain");check(list(tree,c).error==WorkspaceTreeReader.Error.MALFORMED_PROVIDER,"oversized name");
  r.rows=new Rows().add("field","x",null);check(list(tree,c).error==WorkspaceTreeReader.Error.MALFORMED_PROVIDER,"missing mime");
  r.rows=new Rows().add("field",null,"text/plain");check(list(tree,c).error==WorkspaceTreeReader.Error.MALFORMED_PROVIDER,"missing name");
  r.rows=new Rows();for(int i=0;i<101;i++)r.rows.add("id"+i,"x","text/plain");p=list(tree,c);check(p.entries.size()==100&&p.scannedRows==100&&r.rows.moves==100&&p.state==WorkspaceTreeReader.State.LIMITED,"100 result maximum and one over unread");
  r.rows=new Rows().add("one","one","text/plain").add("two","two","text/plain");CancellationSignal partial=new CancellationSignal();r.rows.onMove=()->{if(r.rows.moves==2)partial.cancel();};p=list(tree,partial);check(p.state==WorkspaceTreeReader.State.CANCELLED&&p.entries.size()==1&&p.scannedRows==2,"partial cancellation");
  r.rows=new Rows();CancellationSignal eof=new CancellationSignal();r.rows.onMove=eof::cancel;check(list(tree,eof).state==WorkspaceTreeReader.State.CANCELLED&&r.rows.closes==1,"cancel during eof move");
  r.rows=new Rows();r.failure=new IllegalStateException(new java.io.FileNotFoundException());check(list(tree,c).error==WorkspaceTreeReader.Error.UNAVAILABLE,"unavailable cause");r.failure=null;
  r.rows=new Rows().add("one","one","text/plain");r.rows.moveFail=true;r.rows.closeFail=true;p=list(tree,c);check(p.state==WorkspaceTreeReader.State.FAILED&&p.error==WorkspaceTreeReader.Error.PROVIDER_FAILURE&&r.rows.closes==1,"close failure overrides revoked result without completing");
    Resolver opaque=new Resolver();WorkspaceTreeReader encoded=new WorkspaceTreeReader(opaque,Uri.parse("content://provider/tree/primary%3AFolder%2F100%25"));
  opaque.rows=new Rows().add("primary:Folder/a%20.md","nested",DocumentsContract.Document.MIME_TYPE_DIR);p=list(encoded,c);
  check(opaque.queried.toString().equals("content://provider/tree/primary%3AFolder%2F100%25/document/primary%3AFolder%2F100%25/children"),"encoded opaque root ID child query");
  check(p.entries.get(0).documentId.equals("primary:Folder/a%20.md")&&p.entries.get(0).uri.toString().equals("content://provider/tree/primary%3AFolder%2F100%25/document/primary%3AFolder%2Fa%2520.md"),"colon slash percent document ID preserved and encoded");
  encoded.prepareList(p.entries.get(0).directory,WorkspaceTreeReader.Limits.defaults(),c).read();
  check(opaque.queried.toString().equals("content://provider/tree/primary%3AFolder%2F100%25/document/primary%3AFolder%2Fa%2520.md/children"),"encoded opaque nested directory query");
  Resolver poison=new Resolver();poison.rows.closeFail=true;WorkspaceTreeReader pr=reader(poison);WorkspaceTreeReader.Listing owned=pr.prepareList(pr.root(),WorkspaceTreeReader.Limits.defaults(),c);p=owned.read();check(p.closeDisposition()==WorkspaceTreeReader.CloseDisposition.UNCERTAIN&&owned.closeDisposition()==WorkspaceTreeReader.CloseDisposition.UNCERTAIN,"minted page and owner cursor uncertainty");
  try{owned.read();throw new AssertionError();}catch(IllegalStateException expected){checks++;}
  for(boolean closeFatal:new boolean[]{false,true}){Resolver fatal=new Resolver();java.lang.Error original=new AssertionError("body fatal");fatal.rows.bodyFatal=original;fatal.rows.closeFail=!closeFatal;fatal.rows.closeFatal=closeFatal?new AssertionError("close fatal"):null;WorkspaceTreeReader fr=reader(fatal);WorkspaceTreeReader.Listing receipt=fr.prepareList(fr.root(),WorkspaceTreeReader.Limits.defaults(),c);try{receipt.read();throw new AssertionError();}catch(java.lang.Error actual){check(actual==original&&receipt.closeDisposition()==WorkspaceTreeReader.CloseDisposition.UNCERTAIN&&fatal.rows.closes==1,"body Error primary with close failure owned");}}
  Resolver closedFatal=new Resolver();java.lang.Error original=new AssertionError("body closed");closedFatal.rows.bodyFatal=original;WorkspaceTreeReader cr=reader(closedFatal);WorkspaceTreeReader.Listing closedReceipt=cr.prepareList(cr.root(),WorkspaceTreeReader.Limits.defaults(),c);try{closedReceipt.read();throw new AssertionError();}catch(java.lang.Error actual){check(actual==original&&closedReceipt.closeDisposition()==WorkspaceTreeReader.CloseDisposition.CLOSED,"body Error successful close proved");}
    System.out.println("PASS workspace tree reader: "+checks+" assertions");
 }
}
