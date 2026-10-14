package com.example.markdowndesk;
import java.util.*;import java.nio.charset.StandardCharsets;import android.widget.*;
/** Executes extracted Activity callbacks with real immutable/native history objects. */
public final class HistoryActionsCheck {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
 static MainActivity activity(HistoryControllerCheck.Fixture f){MainActivity a=new MainActivity(f.controller,f.store);for(int i=0;i<12;i++){DocumentTab t=new DocumentTab();t.id="tab-"+i;t.uri="content://history/"+i;t.text="original "+i;t.dirty=true;t.readOnly=true;t.editorLocked=true;t.mode=1;a.tabs.add(t);}a.switchTab(0);return a;}
 static void staleLoads()throws Exception {
  try(HistoryControllerCheck.Fixture f=new HistoryControllerCheck.Fixture()) {
   for(int scenario=0;scenario<7;scenario++){
    MainActivity a=activity(f);a.open();a.io.drain();
    switch(scenario){case 0:a.switchTab(1);a.switchTab(0);break;case 1:a.tabs.get(0).uri="content://changed";break;case 2:a.tabs.clear();break;case 3:a.destroy();break;case 4:a.finishing=true;break;case 5:a.open();a.io.drain();a.main.last();break;case 6:a.pause();break;}
    MainActivity.AlertDialog latest=a.historyDialog;a.main.drain();
    if(scenario<5)check(a.historyDialog==null,"stale load suppressed scenario "+scenario);
    else if(scenario==5)check(a.historyDialog==latest&&latest!=null,"old queued load cannot replace newer dialog");
    else check(a.historyDialog!=null&&a.recoveries==1,"pause preserves read result and schedules ordinary recovery");
   }
   MainActivity a=activity(f);a.open();a.io.drain();a.destroy();a.main.drain();check(a.historyDialog==null,"destroy drops already queued delivery");
   a=activity(f);a.open();a.destroy();a.io.drain();a.main.drain();check(a.historyDialog==null,"queued IO finishing after destroy cannot show");
  }
 }
 static void naming()throws Exception {
  try(HistoryControllerCheck.Fixture f=new HistoryControllerCheck.Fixture()) {
   MainActivity a=activity(f);a.open();a.flush();int before=f.store.snapshots().size();
   a.historyDialog.positive();MainActivity.AlertDialog name=a.historyDialog;((EditText)name.content).setText("cancelled");name.negative();a.flush();check(f.store.snapshots().size()==before,"negative naming creates no snapshot");
   a.historyDialog.positive();name=a.historyDialog;((EditText)name.content).setText("stale");a.switchTab(1);a.switchTab(0);name.positive();a.flush();check(f.store.snapshots().size()==before,"A B A stale positive creates no snapshot");
   a.open();a.flush();a.historyDialog.positive();name=a.historyDialog;((EditText)name.content).setText("owned");a.editor.setText("captured\r\n雪");a.utf8Bom=true;name.positive();a.editor.setText("newer edits");a.switchTab(1);a.io.drain();a.main.drain();
   List<ProtectionStore.Snapshot> saved=f.store.snapshots();check(saved.size()==before+1,"confirmed capture survives tab switch");check(Arrays.equals(saved.get(0).bytes(),bytes("\ufeffcaptured\r\n雪")),"confirmation captures exact BOM CRLF Unicode bytes");check(a.tabs.get(0).dirty&&a.tabs.get(0).text.equals("newer edits"),"naming preserves newer dirty text");check(a.historyDialog==null&&a.activeTab==1&&a.tabs.size()==12,"late name callback cannot reopen wrong tab or alter capacity");check(a.tabs.get(0).readOnly&&a.tabs.get(0).editorLocked&&a.tabs.get(0).mode==1,"private naming preserves lock and View mode");
   a.switchTab(0);a.open();a.flush();a.historyDialog.positive();name=a.historyDialog;((EditText)name.content).setText(" ");name.positive();a.flush();check(f.store.snapshots().size()==before+1&&!a.errors.isEmpty(),"blank label reports failure without snapshot");
   a.open();a.flush();a.historyDialog.positive();name=a.historyDialog;((EditText)name.content).setText("old uri");a.tabs.get(0).uri="content://different";name.positive();a.flush();check(f.store.snapshots().size()==before+1,"URI-changed positive rejected");
  }
 }
 static void staleNameConfirmations()throws Exception {
  try(HistoryControllerCheck.Fixture f=new HistoryControllerCheck.Fixture()) {
   for(int scenario=0;scenario<5;scenario++) {
    MainActivity a=activity(f);a.open();a.flush();a.historyDialog.positive();MainActivity.AlertDialog name=a.historyDialog;((EditText)name.content).setText("stale positive");
    int before=f.store.snapshots().size();
    switch(scenario){case 0:a.tabs.clear();break;case 1:a.destroy();break;case 2:a.finishing=true;break;case 3:a.open();a.flush();break;case 4:name.cancel();break;}
    name.positive();a.flush();check(f.store.snapshots().size()==before,"stale naming positive cannot publish scenario "+scenario);
   }
  }
 }
 static void statuses()throws Exception {
  try(HistoryControllerCheck.Fixture f=new HistoryControllerCheck.Fixture()) {
   MainActivity a=activity(f);String tab=a.tabs.get(0).id,uri=a.tabs.get(0).uri,h=f.controller.resolveDocument(uri,tab);
   ProtectionStore.Transaction indexed=f.group(tab,uri,1,true);check(f.controller.recordVerifiedSave(new HistoryController.SaveRoute(tab,uri,uri,1),indexed.id).indexed,"status fixture real optional ownership");
   f.group(tab,uri,2,true);f.group(tab,uri,3,false);f.store.putSnapshot(tab,uri,"orphan original",4,bytes("old"),"original");
   a.open();a.flush();String all=String.join("\n",a.historyDialog.rows.items);
   check(Arrays.stream(a.historyDialog.rows.items).anyMatch(row->row.endsWith(" · Verified save")),"indexed verified displayed");
   check(all.contains("history indexing unconfirmed"),"unindexed completion explicitly displayed");check(all.contains("Save not confirmed"),"pending explicitly displayed");check(all.contains("Original\n")&&all.contains("Protected snapshot · no completed save"),"orphan original retains source role and honest status");
  }
 }
 static void rowsAndActions()throws Exception {
  try(HistoryControllerCheck.Fixture f=new HistoryControllerCheck.Fixture()) {
   MainActivity a=activity(f);String uri=a.tabs.get(0).uri,tab=a.tabs.get(0).id;String history=f.controller.resolveDocument(uri,tab);
   for(int i=0;i<203;i++)f.store.putSnapshot(tab,uri,"fixture.md",i,bytes("row "+i+"\n"),"attempted");
   List<HistoryIndex.Version> versions=f.controller.list(history);String first=versions.get(0).snapshotId;
   HistoryCatalog catalog=new HistoryCatalog(f.parent.resolve("history_catalog").toFile(),f.store,f.disk);
   for(int n=0;n<5;n++)catalog.name(first,"name-"+n);
   versions=f.controller.list(history);
   try{versions.clear();throw new AssertionError("mutable versions");}catch(UnsupportedOperationException expected){checks++;}
   try{versions.get(0).names.clear();throw new AssertionError("mutable names");}catch(UnsupportedOperationException expected){checks++;}
   a.open();a.flush();check(a.historyDialog.rows.items.length==101&&a.historyDialog.title.text.contains("203"),"first page 100 plus next and full total");String row=a.historyDialog.rows.items[0];check(row.contains("2 more names")&&count(row,"name-")==3,"max three rendered names and exact remainder count");check(row.contains("no completed save"),"orphan status is explicit");
   a.historyDialog.item(100);check(a.historyDialog.rows.items.length==102&&a.historyDialog.rows.items[0].startsWith("Previous"),"middle page100 previous next");
   a.historyDialog.item(101);check(a.historyDialog.rows.items.length==4,"final page3 plus previous");a.historyDialog.item(0);check(a.historyDialog.rows.items.length==102,"previous returns middle page");a.historyDialog.item(0);check(a.historyDialog.rows.items.length==101,"previous returns first page");
   for(int theme=0;theme<9;theme++){
    a.palette=UiTheme.palette(theme,false);a.page(versions,history);MainActivity.AlertDialog d=a.historyDialog;
    check(d.title.color==a.palette.text,"custom title color theme "+theme);TextView view=(TextView)d.rows.getView(0,null,null);check(view.color==a.palette.text,"row binding theme "+theme);view.color=0;check(((TextView)d.rows.getView(1,view,null)).color==a.palette.text,"recycled row binding theme "+theme);
    d.item(0);check(a.historyDialog.message.color==a.palette.text&&a.historyDialog.title.color==a.palette.text,"action message/title theme "+theme);
   }
   MainActivity.Build.VERSION.SDK_INT=26;a.page(versions,history);check(!a.historyDialog.title.heading,"API26 heading call guarded");MainActivity.Build.VERSION.SDK_INT=28;a.page(versions,history);check(a.historyDialog.title.heading,"API28 custom heading marked");
   a.historyDialog.item(0);a.historyDialog.positive();a.flush();check(first.equals(a.pickerId),"actual selected row reaches picker only after fresh membership");
   a.pickerId=null;String foreign=f.store.putSnapshot("other","content://other","other",1,bytes("foreign"),"attempted").id;a.restore(foreign,history);a.flush();check(a.pickerId==null&&!a.errors.isEmpty(),"foreign snapshot cannot launch picker");
   a.restore(first,history);a.io.drain();a.switchTab(1);a.main.drain();check(a.pickerId==null,"stale restored selection cannot launch picker");
   a.switchTab(0);a.open();a.flush();MainActivity.AlertDialog cancelled=a.historyDialog;cancelled.cancel();
   a.pickerId=null;cancelled.item(0);check(a.pickerId==null&&a.historyDialog==null,"cancelled list callback rejected");
  }
 }
 static int count(String s,String word){int n=0,p=0;while((p=s.indexOf(word,p))>=0){n++;p+=word.length();}return n;}
 static void diff()throws Exception {
  try(HistoryControllerCheck.Fixture f=new HistoryControllerCheck.Fixture()) {
   MainActivity a=activity(f);String uri=a.tabs.get(0).uri,tab=a.tabs.get(0).id,h=f.controller.resolveDocument(uri,tab);
   String left=f.store.putSnapshot(tab,uri,"left",1,bytes("old\r\n"),"attempted").id,right=f.store.putSnapshot(tab,uri,"right",2,bytes("new\n"),"attempted").id;
   a.open();a.flush();a.historyDialog.item(0);a.historyDialog.neutral();a.historyDialog.item(1);a.flush();
   TextView output=(TextView)((ScrollView)a.historyDialog.content).child;check(a.historyDialog.title.text.equals("Exact version comparison"),"two actual row selections run real diff");check(output.text.contains("[CRLF]")&&output.text.contains("[LF]")&&output.text.contains("BOM false")&&output.selectable,"diff exposes exact line endings bytes BOM selectable text");
   String large=f.store.putSnapshot(tab,uri,"large",3,bytes("x".repeat(HistoryDiff.MAX_PREVIEW_BYTES+1)),"attempted").id;a.select(left,large,h);a.flush();output=(TextView)((ScrollView)a.historyDialog.content).child;check(a.historyDialog.title.text.startsWith("Incomplete")&&output.text.contains("byte limit")&&output.text.contains("262145"),"actual bounded diff displays incomplete reason and full byte count");
   a.select(left,right,h);a.io.drain();a.open();a.io.drain();a.main.last();MainActivity.AlertDialog latest=a.historyDialog;a.main.drain();check(a.historyDialog==latest,"older diff cannot replace newer history request");
   a.select(left,right,h);a.destroy();a.io.drain();a.main.drain();check(a.historyDialog==latest,"postdestroy diff cannot create dialog");
  }
 }
 public static void main(String[] args)throws Exception {staleLoads();naming();staleNameConfirmations();statuses();rowsAndActions();diff();System.out.println("History Activity integration: "+checks+" checks PASS (real backend, modeled Android boundary)");}
}
