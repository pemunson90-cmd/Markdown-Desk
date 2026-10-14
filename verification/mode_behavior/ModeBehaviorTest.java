public final class ModeBehaviorTest {
    static int checks;
    static void check(String name, boolean condition) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
    static void invoke(ModeHarness h, String name) {
        try {
            var method = ModeHarness.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(h);
        } catch (ReflectiveOperationException e) { throw new AssertionError(name, e); }
    }
    static void setMode(ModeHarness h, int mode, boolean keyboard) {
        try {
            var method = ModeHarness.class.getDeclaredMethod("setMode", int.class, boolean.class);
            method.setAccessible(true);
            method.invoke(h, mode, keyboard);
        } catch (ReflectiveOperationException e) { throw new AssertionError("setMode", e); }
    }
    public static void main(String[] args) {
        for (int mode=0; mode<3; mode++) {
            ModeHarness h = new ModeHarness();
            h.mode=mode;
            h.tabs.get(0).mode=mode;
            h.editor.scrollY=456;
            h.readToolsScroll.scrollY=654;
            invoke(h, "toggleFocusMode");
            check("focus on " + mode, h.focusMode);
            check("focus exit action " + mode, "Exit focus mode".equals(h.focusButton.description));
            check("focus selected state " + mode, h.focusButton.selected);
            check("mode retained on " + mode, h.mode==mode && h.tabs.get(0).mode==mode);
            check("chrome hidden on " + mode, h.tabsScroll.visibility==8 && h.modeBar.visibility==8 && h.eyebrowView.visibility==8 && h.statusView.visibility==8);
            check("tools hidden on " + mode, h.formatScroll.visibility==8 && h.readToolsScroll.visibility==8);
            check("tight header on " + mode, h.header.left==28 && h.header.right==28 && h.header.top==8 && h.header.bottom==8);
            check("chrome refreshed on " + mode, h.chromeStyleCalls==5 && h.focusButton.styledActive);
            check("focus preserved scroll on " + mode, h.editor.scrollY==456 && h.readToolsScroll.scrollY==654 && h.previewScrollY==777);
            invoke(h, "toggleFocusMode");
            check("focus off " + mode, !h.focusMode);
            check("focus enter action " + mode, "Enter focus mode".equals(h.focusButton.description));
            check("focus unselected state " + mode, !h.focusButton.selected);
            check("chrome restored " + mode, h.tabsScroll.visibility==0 && h.modeBar.visibility==0 && h.eyebrowView.visibility==0 && h.statusView.visibility==0);
            check("format visibility " + mode, h.formatScroll.visibility==(mode==1?8:0));
            check("read tools visibility " + mode, h.readToolsScroll.visibility==(mode==1?0:8));
            check("header restored " + mode, h.header.top==16 && h.header.bottom==16);
            check("chrome refreshed twice " + mode, h.chromeStyleCalls==10 && !h.focusButton.styledActive);
            check("retained scroll twice " + mode, h.editor.scrollY==456 && h.previewScrollY==777);
        }
        for (int mode=0; mode<3; mode++) {
            for (boolean requestKeyboard : new boolean[]{false,true}) {
                ModeHarness h = new ModeHarness();
                setMode(h, mode, requestKeyboard);
                check("set mode " + mode, h.mode==mode && h.tabs.get(0).mode==mode);
                check("rebuild status session " + mode, h.rebuilds==1 && h.statuses==1 && h.sessions==1);
                check("keyboard branch " + mode, mode==1 || !requestKeyboard ? h.hideKeyboardCalls==1 && h.showKeyboardCalls==0 : h.hideKeyboardCalls==0 && h.showKeyboardCalls==1 && h.editor.focused);
                check("split hint " + mode, h.preferences.splitHintSeen==(mode==2));
                check("extracted setMode does not directly mutate simulated scroll " + mode, h.editor.scrollY==321 && h.previewScrollY==777);
            }
        }
        for (int mode=0; mode<3; mode++) {
            ModeHarness h = new ModeHarness();
            h.mode=mode;
            invoke(h, "updateModeChrome");
            check("selected mode button " + mode, h.editModeButton.styledActive==(mode==0) && h.previewModeButton.styledActive==(mode==1) && h.splitModeButton.styledActive==(mode==2));
            check("lock affordance " + mode, h.lockButton.enabled==(mode!=1) && h.lockButton.alphaPercent==(mode==1?45:100));
            h.readOnly=true;
            invoke(h, "updateModeChrome");
            check("read only lock affordance " + mode, !h.lockButton.enabled && h.lockButton.alphaPercent==45);
            h.readOnly=false;
            invoke(h, "toggleEditorLock");
            check("lock state tab " + mode, h.editorLocked && h.tabs.get(0).editorLocked);
            check("locked editor " + mode, h.editor.keyListener==null && !h.editor.cursorVisible && h.hideKeyboardCalls==1);
            check("unlock action label " + mode, "Unlock editing".equals(h.lockButton.description));
            check("lock icon " + mode, h.lockButton.icon==2 && h.lockButton.iconColor==h.palette.accent);
            check("lock refresh " + mode, h.sessions==1 && h.formatUpdates==1 && h.responsiveUpdates==1 && h.chromeStyleCalls==15);
            invoke(h, "toggleEditorLock");
            check("unlock state " + mode, !h.editorLocked && !h.tabs.get(0).editorLocked && h.editor.keyListener!=null && h.editor.cursorVisible);
            check("lock action label " + mode, "Lock editing".equals(h.lockButton.description));
            check("unlock icon " + mode, h.lockButton.icon==3 && h.lockButton.iconColor==h.palette.text);
        }
        System.out.println("PASS " + checks + " host assertions on five AST-extracted MainActivity methods");
    }
}
