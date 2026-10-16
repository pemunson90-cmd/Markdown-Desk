package com.example.markdowndesk;
import android.net.Uri;
import android.content.Context;
import android.widget.EditText;
import java.lang.reflect.*;
/** Real reader dialog methods and persistent store, with controlled Android UI boundaries. */
public final class ReaderPlacesUiCheck {
 static int checks;
 static void yes(boolean good,String why){checks++;if(!good)throw new AssertionError(why);}
 static final String URI="content://fixture/document/reading";
 static WorkspaceUiCheck.Prefs roots=new WorkspaceUiCheck.Prefs();
 static WorkspaceUiCheck.Prefs favoritePrefs=new WorkspaceUiCheck.Prefs();
 static WorkspaceUiCheck.Prefs readerPrefs=new WorkspaceUiCheck.Prefs();
 static WorkspaceUiCheck.Provider provider=new WorkspaceUiCheck.Provider();
 static WorkspaceHarness create(boolean saved){
  WorkspaceHarness h=new WorkspaceHarness(new Context(roots,favoritePrefs,readerPrefs,provider));
  if(saved){
   WorkspaceHarness.DocumentTab tab=new WorkspaceHarness.DocumentTab();
   tab.id="real-reading-tab";tab.displayName="Reading.md";
   h.tabs.add(tab);h.activeTab=0;h.currentUri=Uri.parse(URI);
  }
  h.editor.text=new StringBuilder("## Opening\nThis is the first paragraph.\n## Next\nThis is the second paragraph.");
  h.editor.selection=15;
  return h;
 }
 static Object invoke(WorkspaceHarness h,String name,Class<?>[] types,Object...args)throws Exception {
  Method m=WorkspaceHarness.class.getDeclaredMethod(name,types);
  m.setAccessible(true);
  return m.invoke(h,args);
 }
 static void call(WorkspaceHarness h,String method)throws Exception {
  invoke(h,method,new Class<?>[0]);
 }
 static void save(WorkspaceHarness h,boolean named,String label)throws Exception {
  invoke(h,"saveReaderAnchor",new Class<?>[]{boolean.class,String.class},named,label);
 }
 static WorkspaceHarness.AlertDialog current(WorkspaceHarness h)throws Exception {
  Field f=WorkspaceHarness.class.getDeclaredField("readerDialog");f.setAccessible(true);
  return (WorkspaceHarness.AlertDialog)f.get(h);
 }
 static WorkspaceHarness.AlertDialog dialog(WorkspaceHarness h)throws Exception {
  WorkspaceHarness.AlertDialog d=current(h);
  yes(d!=null&&d.isShowing(),"current reader dialog is visible");return d;
 }
 static void settle(WorkspaceHarness h){
  for(int i=0;i<12;i++){
   h.drainLocal();
   if(h.io.tasks.isEmpty()&&!android.os.Handler.pending())return;
  }
  throw new AssertionError("Reading places I/O/UI callbacks did not settle");
 }
 static ReaderPositionStore.DocumentState state(WorkspaceHarness h)throws Exception {
  return new ReaderPositionStore(h.application).read(URI);
 }
 static void normal()throws Exception {
  WorkspaceHarness h=create(true);
  call(h,"showReaderPlaces");settle(h);
  yes(dialog(h).message.contains("No places saved"),"empty bookmark UI explains how to save places");
  dialog(h).getButton(-3).tap();settle(h);
  yes(state(h)!=null&&state(h).location!=null&&state(h).bookmarks.isEmpty(),
    "manual reading place persisted with exact current source");
  yes(provider.queries==0&&provider.takes==0,"reading place never writes the source provider");
  call(h,"showReaderPlaces");settle(h);
  yes(dialog(h).items.length==1&&dialog(h).items[0].contains("Return to"),
    "saved location appears in real dialog");
  dialog(h).getButton(-1).tap(); // opens new bookmark name UI
  WorkspaceHarness.AlertDialog naming=dialog(h);
  yes(naming.customView instanceof EditText,"real accessible bookmark name input");
  ((EditText)naming.customView).setText("Chapter two");
  naming.getButton(-1).tap();settle(h);
  yes(state(h).bookmarks.size()==1&&state(h).bookmarks.get(0).label.equals("Chapter two"),
     "named bookmark persists exact source anchor");
  call(h,"showReaderPlaces");settle(h);
  yes(dialog(h).items.length==2&&dialog(h).items[1].contains("Chapter two"),
     "named bookmark appears beside saved position");
  h.editor.selection=0;
  dialog(h).item(1);
  yes(dialog(h).message.contains("verified text"),"selection opens guarded options");
  dialog(h).getButton(-1).tap();settle(h);
  yes(h.editor.selection==15&&h.editor.visible==15,
     "bookmark moves actual editor selection and visible anchor");
  call(h,"showReaderPlaces");settle(h);dialog(h).item(1);
  dialog(h).getButton(-3).tap();settle(h);
  yes(state(h).bookmarks.isEmpty()&&state(h).location!=null,
      "remove only named bookmark, keeping saved reading place");
  yes(provider.queries==0&&provider.takes==0,"bookmarks never touch provider document bytes");
 }
 static void refusalsAndStaleness()throws Exception {
  WorkspaceHarness unsaved=create(false);
  call(unsaved,"showReaderPlaces");
  yes(unsaved.error.contains("Open a saved document"),"unsaved doc refuses reader metadata");
  WorkspaceHarness h=create(true);
  h.mode=1;save(h,false,null);
  yes(h.error.contains("Switch to Write"),"Read mode never silently saves stale source cursor");
  yes(state(h)==null,"refused Read-mode capture is not persisted");
  h.mode=0;
  call(h,"showReaderPlaces");h.pause();settle(h);
  yes(current(h)==null,"pause discards stale asynchronous reading dialog");
  h.editor.selection=10;save(h,false,null);settle(h);
  yes(state(h)!=null,"fresh save works after stale pending view");
  String before=h.editor.text.toString();
  h.editor.text.append("\nThis source was edited.");
  call(h,"showReaderPlaces");settle(h);
  dialog(h).item(0);settle(h);
  yes(h.editor.text.toString().equals(before+"\nThis source was edited."),
      "moving to a saved place never modifies content");
 }
 public static void main(String[] args){
  try{
   normal();refusalsAndStaleness();
   System.out.println("Reading places actual UI: "+checks+" checks PASS; exact Activity actions and reader store");
   System.exit(0);
  }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
 }
}
