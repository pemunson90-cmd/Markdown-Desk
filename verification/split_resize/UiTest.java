package com.example.markdowndesk;
public final class UiTest {
 static int checks,failures;
 static void check(boolean ok,String label){checks++;if(!ok){failures++;System.err.println("FAIL "+label);}}
 static void near(float value,float expected,String label){check(Math.abs(value-expected)<.00001,label);}
 public static void main(String[] args){
  MainActivity a=new MainActivity();String full="A very long complete document name.md";
  a.tabs.add(new MainActivity.DocumentTab(full,true));a.tabs.add(new MainActivity.DocumentTab(null,false));a.updateTabsUi();
  check(a.tabButtons.get(0).description.equals("Switch to "+full),"full tab action name");check(a.tabButtons.get(0).selected,"active tab selected");check(!a.tabButtons.get(1).selected,"inactive unselected");check(a.tabButtons.get(1).description.equals("Switch to Untitled"),"null name fallback");check(a.tabButtons.get(0).text.startsWith("●  ") && a.tabButtons.get(0).text.contains("…"),"visual dirty/truncation preserved");
  a.activeTab=1;a.updateTabsUi();check(!a.tabButtons.get(0).selected && a.tabButtons.get(1).selected,"selection refresh");check(a.gallery("Blue sunset").description!=null && a.gallery("Blue sunset").description.equals("Remove Blue sunset"),"gallery contextual name");
  a.rebuildBody();MainActivity.View d=a.body.children.get(1);check(d.focusable,"divider keyboard focus");check(d.important==1,"divider accessibility inclusion");check(d.delegate!=null,"divider accessible adjustment");check(d.key!=null,"divider keyboard adjustment");
  if(d.delegate!=null && d.key!=null){
   MainActivity.AccessibilityNodeInfo n=new MainActivity.AccessibilityNodeInfo();d.delegate.onInitializeAccessibilityNodeInfo(d,n);
   check(n.range!=null,"divider range semantics");if(n.range!=null){near(n.range.min,.28f,"range min");near(n.range.max,.72f,"range max");near(n.range.current,.5f,"range current");}
   check(n.actions.contains(4096)&&n.actions.contains(8192),"scroll actions");check(n.actions.contains(16),"accessible midpoint action");
   check(d.delegate.performAccessibilityAction(d,4096,null),"forward consumed");near(a.splitRatio,.55f,"forward increases source");check(a.preferences.writes==1,"accessible adjustment persisted");near(((MainActivity.LinearLayout.LayoutParams)a.editor.layoutParams).weight,.55f,"source resized");near(((MainActivity.LinearLayout.LayoutParams)a.preview.layoutParams).weight,.45f,"reading resized");
   check(d.key.onKey(d,21,new MainActivity.KeyEvent(0)),"left consumed");near(a.splitRatio,.5f,"left decreases source");check(!d.key.onKey(d,22,new MainActivity.KeyEvent(1)),"key up ignored");
   a.splitRatio=.71f;d.key.onKey(d,22,new MainActivity.KeyEvent(0));near(a.splitRatio,.72f,"upper clamp");a.splitRatio=.29f;d.delegate.performAccessibilityAction(d,8192,null);near(a.splitRatio,.28f,"lower clamp");
   d.delegate.performAccessibilityAction(d,16,null);near(a.splitRatio,.5f,"accessible midpoint");a.splitRatio=.72f;d.key.onKey(d,122,new MainActivity.KeyEvent(0));near(a.splitRatio,.5f,"home midpoint");check(!d.key.onKey(d,99,new MainActivity.KeyEvent(0)),"unrelated key ignored");check(!d.delegate.performAccessibilityAction(d,99,null),"unrelated action delegated");
  }
  int writes=a.preferences.writes;d.touch.onTouch(d,new MainActivity.MotionEvent(0,0));near(a.splitRatio,.28f,"drag lower clamp");check(a.preferences.writes==writes,"drag not persisted early");d.touch.onTouch(d,new MainActivity.MotionEvent(2,2000));near(a.splitRatio,.72f,"drag upper clamp");d.touch.onTouch(d,new MainActivity.MotionEvent(1,2000));check(a.preferences.writes==writes+1,"release persisted");d.touch.onTouch(d,new MainActivity.MotionEvent(3,2000));check(a.preferences.writes==writes+2,"cancel persistence preserved");
  a.widthDp=840;a.filesRailRequested=true;a.rebuildBody();check(a.body.getOrientation()==1,"rail constrains Split width");
  a.widthDp=1000;a.rebuildBody();check(a.body.getOrientation()==0,"wide rail Split horizontal");
  a.filesRailRequested=false;
  a.widthDp=400;a.rebuildBody();check(a.body.children.size()==2,"compact split retains vertical two panes");check(a.body.getOrientation()==1,"compact vertical orientation");
  a.widthDp=800;a.mode=0;a.rebuildBody();check(a.body.children.size()==1&&a.body.children.get(0)==a.editor,"Write retains single source pane");a.mode=1;a.rebuildBody();check(a.body.children.size()==1&&a.body.children.get(0)==a.preview,"Read retains single preview pane");
  a.tabs.clear();for(int i=0;i<12;i++)a.tabs.add(new MainActivity.DocumentTab("Document "+i,false));a.activeTab=11;a.updateTabsUi();check(a.tabButtons.size()==13,"all twelve tabs and new action retained");check(a.tabButtons.get(11).selected&&!a.tabButtons.get(12).selected,"last document selection and new-action state");
  // Exercise the actual extracted production tab touch listener. An interrupted
  // long-press must not survive into the next ordinary tap on the same tab.
  MainActivity.PostQueue queued = new MainActivity.PostQueue();
  a.tabsScroll = queued;
  a.tabs.clear();
  a.tabs.add(new MainActivity.DocumentTab("Safety case.md",true));
  a.activeTab = 0;
  a.updateTabsUi();
  int prior = queued.pending.size(); // ignore the unrelated scroll-to-selected post
  MainActivity.Button hold = a.tabButtons.get(0);
  String heldId = a.tabs.get(0).id;
  a.pendingLongPressedTabId = heldId; // old gesture never sent UP or CANCEL
  hold.touch.onTouch(hold,new MainActivity.MotionEvent(MainActivity.MotionEvent.ACTION_DOWN,0));
  check(a.pendingLongPressedTabId==null,"fresh DOWN clears interrupted close intent");
  hold.touch.onTouch(hold,new MainActivity.MotionEvent(MainActivity.MotionEvent.ACTION_UP,0));
  check(queued.pending.size()==prior,"ordinary tap after interrupted hold never queues close");
  a.pendingLongPressedTabId = heldId;
  hold.touch.onTouch(hold,new MainActivity.MotionEvent(MainActivity.MotionEvent.ACTION_CANCEL,0));
  check(a.pendingLongPressedTabId==null && queued.pending.size()==prior,
        "canceled hold queues no close");
  hold.touch.onTouch(hold,new MainActivity.MotionEvent(MainActivity.MotionEvent.ACTION_DOWN,0));
  a.pendingLongPressedTabId = heldId; // native long-click dispatch during this gesture
  hold.touch.onTouch(hold,new MainActivity.MotionEvent(MainActivity.MotionEvent.ACTION_UP,0));
  check(queued.pending.size()==prior+1,"valid completed hold posts exactly one close");

  System.out.println("UI assertions: "+checks+", failures: "+failures);if(failures!=0)throw new AssertionError("UI assertions failed");
 }
}
