package com.example.markdowndesk;
import java.util.*;
public final class NavigationUiCheck {
 static int checks;
 static void eq(Object a,Object b,String m){checks++;if(!Objects.equals(a,b))throw new AssertionError(m+": "+a+" != "+b);}
 static void run(String query,String action){MainActivity a=new MainActivity();a.commands();NavigationPicker p=NavigationPicker.latest;p.filter(query);eq(1,p.displayed.rows.size(),"unique command "+query);p.click(0);eq(List.of(action),a.calls,"actual dispatcher "+query);p.click(0);eq(1,a.calls.size(),"no repeat "+query);}
 public static void main(String[] args){
  MainActivity a=new MainActivity();a.commands();eq(22,NavigationPicker.latest.displayed.rows.size(),"all commands retained");
  run("export docx","docx");run("undo","undo");run("redo","redo");run("reading bookmarks","bookmarks");run("workspace","files");run("split mode","mode:2:false");run("write mode","mode:0:true");run("read mode","mode:1:false");run("pdf","print");run("open file","open");run("decrease","font:-1");run("increase","font:1");
  a=new MainActivity();a.recents.add(new MainActivity.RecentStore.Item("Draft.md","content://a/first"));a.recents.add(new MainActivity.RecentStore.Item("Draft.md","content://a/second"));a.recent();NavigationPicker p=NavigationPicker.latest;
  eq(2,p.displayed.rows.size(),"duplicate names kept");p.filter("second");p.click(0);eq(List.of("uri:content://a/second:3:true"),a.calls,"filtered Recent uses captured URI + original grant route");
  a=new MainActivity();a.recents.add(new MainActivity.RecentStore.Item("A","content://a"));
  a.recentFromFiles();NavigationPicker.latest.back();eq(List.of("files-back"),a.calls,"recent Back reopens Files");
  a=new MainActivity();a.commands();NavigationPicker old=NavigationPicker.latest;a.recents.add(new MainActivity.RecentStore.Item("A","content://a"));old.filter("recent documents");old.click(0);eq("Recent documents",NavigationPicker.latest.title,"reentrant second picker survives old close");eq(true,old.closed,"old picker retired");
  a=new MainActivity();a.outline();p=NavigationPicker.latest;p.filter("two");p.click(0);eq(List.of("source:11"),a.calls,"filtered heading uses original source offset");
  a=new MainActivity();a.mode=1;a.outline();p=NavigationPicker.latest;p.filter("two");p.click(0);eq(List.of("preview:11"),a.calls,"Read uses preview route");
  a=new MainActivity();a.outline();p=NavigationPicker.latest;a.revision++;p.click(0);eq(Collections.emptyList(),a.calls,"stale heading rejected");
  a=new MainActivity();a.commands();p=NavigationPicker.latest;a.onStop();p.click(0);eq(true,p.closed,"stop closes chooser");eq(Collections.emptyList(),a.calls,"stop rejects click");eq(1L,a.downloadsRequest,"Downloads stop cancellation retained");
  a=new MainActivity();a.commands();p=NavigationPicker.latest;a.onConfigurationChanged(new MainActivity.Configuration());p.click(0);eq(true,p.closed,"configuration closes chooser");eq(List.of("capture","body","chrome","rail"),a.calls,"existing Fold refresh preserved");
  a=new MainActivity();a.commands();p=NavigationPicker.latest;a.destroyed=true;p.click(0);eq(Collections.emptyList(),a.calls,"destroyed Activity rejects selection");
  a=new MainActivity();a.editor.text="no headings";a.outline();eq(List.of("No headings in this document"),a.toasts,"empty outline honest");a.recent();eq(2,a.toasts.size(),"empty Recents honest");
  System.out.println("Actual navigation callers/dispatcher: "+checks+" assertions PASS");
 }
}
