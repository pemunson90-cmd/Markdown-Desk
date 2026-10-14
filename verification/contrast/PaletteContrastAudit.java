package com.example.markdowndesk;

/** Numerically audits existing color pairs; no Android paint/disabled-state claim. */
public final class PaletteContrastAudit {
    static double linear(int value) {
        double c = value / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
    static double luminance(int color) {
        return 0.2126 * linear((color >> 16) & 255) + 0.7152 * linear((color >> 8) & 255) + 0.0722 * linear(color & 255);
    }
    static double contrast(int a, int b) {
        double x=luminance(a), y=luminance(b);
        return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);
    }
    public static void main(String[] args) {
        boolean gate = args.length == 1 && args[0].equals("--gate");
        int failures = 0;
        System.out.println("theme,pair,contrast,required,result");
        for (int i=0; i<9; i++) {
            UiTheme.Palette p=UiTheme.palette(i,false);
            String[] labels={"Save white/accent", "body/canvas", "muted/raised", "selected accent/accentSoft", "link accent/canvas", "dialog accent/raised"};
            int[] fg={0xffffffff,p.text,p.muted,p.accent,p.accent,p.accent};
            int[] bg={p.accent,p.canvas,p.raised,p.accentSoft,p.canvas,p.raised};
            for (int k=0; k<labels.length; k++) {
                double ratio=contrast(fg[k],bg[k]);
                boolean pass=ratio>=4.5;
                System.out.printf(java.util.Locale.ROOT,"%s,%s,%.3f,4.5,%s%n",p.name,labels[k],ratio,pass?"PASS":"FAIL");
                if (!pass) failures++;
            }
        }
        System.out.println("Observed baseline contrast failures: "+failures+"; Android rendering NOT RUN");
        if (gate && failures>0) throw new AssertionError("Existing text contrast pairs below4.5:1: "+failures);
    }
}
