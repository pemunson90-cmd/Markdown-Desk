package com.example.markdowndesk;
public final class AppearanceBehaviorTest {
    static int checks;
    static void check(String name, boolean condition) {checks++; if (!condition) throw new AssertionError(name);}
    static void choice(AppearanceHarness.Button button, String label, boolean selected) {
        check("selected " + label, button.selected==selected);
        check("accessible name " + label, label.equals(button.description));
        var node=button.node();
        check("checkable " + label, node.checkable);
        check("checked " + label, node.checked==selected);
    }
    public static void main(String[] args) {
        AppearanceHarness h=new AppearanceHarness();
        var ui=new AppearanceHarness.AppearanceUi();
        var dialog=ui.dialog;
        var originalButtons=ui.buttons.toArray();
        for (int theme : new int[]{0,8,1,7,2,6,3,5,4,0}) {
            h.themeId=theme; h.palette.name=ui.themeNames[theme];
            for (int mask=0;mask<8;mask++) {
                h.lineNumbers=(mask&1)!=0; h.typewriterMode=(mask&2)!=0; h.launchSplashEnabled=(mask&4)!=0;
                h.refresh(ui);
                for (int i=0;i<9;i++) {
                    choice(ui.themeButtons[i], ui.themeNames[i], i==theme);
                    check("visible theme marker", ui.themeButtons[i].text.equals((i==theme?"●  ":"○  ")+ui.themeNames[i]));
                }
                choice(ui.linesButton,"Line numbers",h.lineNumbers);
                choice(ui.typewriterButton,"Typewriter mode",h.typewriterMode);
                choice(ui.splashButton,"Show launch splash",h.launchSplashEnabled);
                check("action has no choice semantics", !ui.galleryButton.selected && !ui.galleryButton.node().checkable);
                check("existing dialog and simulated scroll", ui.dialog==dialog && dialog.scrollY==321);
                check("existing controls", java.util.Arrays.equals(originalButtons,ui.buttons.toArray()));
                check("title refresh", ui.title.text.equals("Appearance · "+ui.themeNames[theme]));
                check("preferences unchanged", h.themeId==theme && h.lineNumbers==((mask&1)!=0)
                    && h.typewriterMode==((mask&2)!=0) && h.launchSplashEnabled==((mask&4)!=0));
            }
        }
        check("existing window styled", h.styledDialogs==80);
        ui.dialog.showing=false; h.refresh(ui);
        check("dismissed window not styled", h.styledDialogs==80);
        // The delegate reads current selection, rather than capturing a stale boolean.
        ui.linesButton.setSelected(!h.lineNumbers);
        check("node reads current state", ui.linesButton.node().checked==!h.lineNumbers);
        System.out.println("PASS " + checks + " host assertions on AST-extracted Appearance refresh/selection");
    }
}
