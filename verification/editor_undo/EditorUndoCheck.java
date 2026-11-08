public final class EditorUndoCheck {
 static int checks;static final int UNDO=16908338,REDO=16908339;
 static void yes(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  UndoHarness a=new UndoHarness();a.setup();
  yes(a.buttons.size()==2&&a.buttons.keySet().iterator().next().equals("Undo"),"visible history actions first in their configured group");
  yes(a.descriptions.get("Undo").equals("Undo last edit")&&a.descriptions.get("Redo").equals("Redo last undone edit"),"accessible action names");
  a.buttons.get("Undo").onClick(new View());yes(a.editor.nativeCalls==1&&a.editor.lastId==UNDO,"Undo click one native call");a.buttons.get("Redo").onClick(new View());yes(a.editor.nativeCalls==2&&a.editor.lastId==REDO,"Redo click one native call");
  a.editor.nativeCalls=0;a.editor.onTextContextMenuItem(UNDO);yes(a.editor.nativeCalls==1,"native menu/shortcut exactly once without recursion");
  a.editor.onTextContextMenuItem(99);yes(a.editor.nativeCalls==2&&a.editor.lastId==99,"unrelated context action delegates unchanged");
  yes(!a.editor.performNativeHistoryAction(99)&&a.editor.nativeCalls==2,"native helper rejects unsupported action");
  int calls=a.editor.nativeCalls,focus=a.editor.focusCalls;a.editorLocked=true;
  yes(a.action(UNDO)&&a.editor.onTextContextMenuItem(REDO),"locked history actions consumed");yes(a.editor.nativeCalls==calls&&a.editor.focusCalls==focus,"locked no native call or focus");
  a.editorLocked=false;a.activeTab=-1;yes(a.action(UNDO)&&a.editor.nativeCalls==calls,"invalid tab blocked");a.activeTab=1;yes(a.action(REDO)&&a.editor.nativeCalls==calls,"past-end tab blocked");a.activeTab=0;
  yes(!a.action(99),"activity unsupported action unhandled");a.readOnly=true;yes(a.action(UNDO)&&a.editor.nativeCalls==calls+1,"provider read-only retains existing editing semantics");
  for(boolean old:new boolean[]{false,true}){
   a.smartEditing=old;a.suppressChanges=false;a.editor.afterNative=()->{yes(a.smartEditing&&!a.suppressChanges,"replay guard active without suppressing watchers");a.continueMarkdownLine(a.editor.getText());};
   a.editor.text=new Editable("- item\n");a.editor.selection=a.editor.text.length();a.action(UNDO);yes(a.editor.text.toString().equals("- item\n"),"undo replay newline creates no markdown prefix");yes(a.editor.selection==7,"wrapper leaves selection native-owned");yes(a.smartEditing==old,"smart editing restored");
   a.editor.fail=true;try{a.action(REDO);throw new AssertionError("native exception swallowed");}catch(IllegalStateException expected){yes(a.smartEditing==old,"smart editing restored on failure");}a.editor.fail=false;
  }
  a.editor.afterNative=null;int ctrl=KeyEvent.META_CTRL_ON,shift=KeyEvent.META_SHIFT_ON;
  for(int[] route:new int[][]{{54,ctrl,UNDO},{54,ctrl|shift,REDO},{53,ctrl,REDO}}){calls=a.editor.nativeCalls;yes(a.onKeyShortcut(route[0],new KeyEvent(route[1]))&&a.editor.nativeCalls==calls+1&&a.editor.lastId==route[2],"shortcut exact native action");}
  for(int modifiers:new int[]{ctrl|KeyEvent.META_ALT_ON,ctrl|KeyEvent.META_META_ON,shift,0}){calls=a.editor.nativeCalls;yes(!a.onKeyShortcut(54,new KeyEvent(modifiers))&&a.editor.nativeCalls==calls,"unsupported shortcut modifiers delegate");}
  a.editorLocked=true;calls=a.editor.nativeCalls;yes(a.onKeyShortcut(54,new KeyEvent(ctrl))&&a.editor.nativeCalls==calls,"locked shortcut consumed without mutation");
  KineticEditText unattached=new KineticEditText();yes(unattached.onTextContextMenuItem(UNDO)&&unattached.nativeCalls==1,"default native behavior before listener installation");
  System.out.println("Editor Undo routing: "+checks+" checks PASS; native engine/IME/device acceptance not simulated");
 }
}
