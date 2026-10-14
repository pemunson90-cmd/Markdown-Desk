package com.example.markdowndesk;

public final class StyleContrastCheck {
    public static void main(String[] args) {
        int checks=0;
        for(int theme=0;theme<9;theme++) for(boolean selected:new boolean[]{false,true}) for(boolean filled:new boolean[]{false,true}) {
            StyleHarness h=new StyleHarness();h.palette=UiTheme.palette(theme,false);
            StyleHarness.Button button=new StyleHarness.Button();h.apply(button,selected,filled);
            int background=filled?h.palette.accent:selected?h.palette.accentSoft:h.palette.surface;
            int recorded = ((android.graphics.drawable.GradientDrawable)button.background).color;
            if(recorded != (filled?h.palette.accent:selected?h.palette.accentSoft:0)) throw new AssertionError("Actual button background differs");
            double ratio=PaletteContrastAudit.contrast(button.textColor,recorded==0?h.palette.surface:recorded);
            if(ratio<4.5) throw new AssertionError(h.palette.name+" selected="+selected+" filled="+filled+" text contrast="+ratio);
            if(button.background==null) throw new AssertionError("Background styling removed");
            checks+=2;
        }
        System.out.println("PASS "+checks+" foreground/background assertions on AST-extracted production styleButton; Android paint NOT RUN");
    }
}
