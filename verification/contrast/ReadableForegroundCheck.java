package com.example.markdowndesk;

import java.util.Locale;
import java.util.Random;

/** Independent WCAG contrast and 8-bit blend oracle for opaque foregrounds/backgrounds. */
public final class ReadableForegroundCheck {
    private static int checks;
    private static double linear(int component) {
        double c = component / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
    private static double luminance(int rgb) {
        return .2126 * linear((rgb >>> 16) & 255) + .7152 * linear((rgb >>> 8) & 255) + .0722 * linear(rgb & 255);
    }
    private static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
    }
    private static int blend(int preferred, int endpoint, int amount) {
        int value = 0xff000000;
        for (int shift : new int[]{16,8,0}) {
            int a=(preferred >>> shift)&255, b=(endpoint >>> shift)&255;
            value |= ((a*(255-amount)+b*amount+127)/255) << shift;
        }
        return value;
    }
    private static int firstPassingStep(int preferred, int background, int endpoint) {
        for (int step=1; step<=255; step++) if (contrast(blend(preferred, endpoint, step), background)>=4.5) return step;
        return 256;
    }
    private static void verify(String label, int preferred, int background) {
        int actual=UiTheme.readableForeground(preferred, background);
        checks++;
        if ((actual >>> 24)!=255) throw new AssertionError(label + " not opaque: " + Integer.toHexString(actual));
        checks++;
        if (contrast(actual, background)<4.5) throw new AssertionError(String.format(Locale.ROOT, "%s contrast %.4f",label,contrast(actual,background)));
        if (contrast(preferred, background)>=4.5) {
            checks++;
            if (actual!=preferred) throw new AssertionError(label + " changed already readable color");
        } else {
            int black=firstPassingStep(preferred,background,0xff000000);
            int white=firstPassingStep(preferred,background,0xffffffff);
            int minimum=Math.min(black,white);
            checks++;
            if (actual!=blend(preferred,0xff000000,minimum) && actual!=blend(preferred,0xffffffff,minimum)) {
                throw new AssertionError(label + " not the minimum readable blend: " + Integer.toHexString(actual));
            }
        }
    }
    public static void main(String[] args) {
        verify("black on white",0xff000000,0xffffffff);
        verify("white on black",0xffffffff,0xff000000);
        verify("same white",0xffffffff,0xffffffff);
        verify("same black",0xff000000,0xff000000);
        for (int i=0;i<9;i++) {
            UiTheme.Palette p=UiTheme.palette(i,false);
            verify(p.name+" white/accent",0xffffffff,p.accent);
            verify(p.name+" accent/soft",p.accent,p.accentSoft);
            verify(p.name+" accent/canvas",p.accent,p.canvas);
            verify(p.name+" accent/raised",p.accent,p.raised);
        }
        Random random=new Random(0x19d0c0deL);
        for (int i=0;i<2048;i++) verify("random "+i,0xff000000|random.nextInt(1<<24),0xff000000|random.nextInt(1<<24));
        System.out.println("PASS "+checks+" independent contrast/blend assertions across palette pairs and 2048 seeded pairs");
    }
}
