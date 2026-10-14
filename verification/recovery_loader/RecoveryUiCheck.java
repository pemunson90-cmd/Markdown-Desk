package com.example.markdowndesk;
import java.util.*;
public class RecoveryUiCheck {
 static int assertions;
 static void eq(Object e,Object a,String n){assertions++;if(!Objects.equals(e,a))throw new AssertionError(n+": expected="+e+", actual="+a);}
 public static void main(String[]args){
  MainActivity a=new MainActivity();SessionStore.State s=new SessionStore.State();
  for(int i=0;i<12;i++){DocumentTab t=new DocumentTab();t.uri="content://session/"+i;t.text="session"+i;s.tabs.add(t);}s.activeIndex=4;
  RecoveryStore.Draft d=new RecoveryStore.Draft();d.uri="content://orphan";d.text="unmatched draft";d.displayName="orphan.md";
  a.legacyRecoveryProtectionFailed=true;
  a.lambda$restoreSessionOrRecovery$41(s,new MainActivity.Intent(),new java.io.IOException("protection failed"),d);
  eq(12,a.tabs.size(),"all twelve preserved");eq(4,a.activeTab,"active retained");eq(1,a.pending,"unmatched recovery independently surfaced");
  eq(1,a.errors.size(),"protection error visible even with valid session");eq(0,a.untitled,"no thirteenth tab");
  eq(true,a.dialog.message.contains("Draft protection failed"),"failed protection not claimed available");a.dialog.positive.onClick(null,0);eq(1,a.protectedViews,"pending recovery links independent protected versions");
  System.out.println("PASS recovery UI actual callback: "+assertions+" assertions");
 }
}
