package com.example.markdowndesk;

/** Independent alpha compositing model, not an Android screenshot test. */
public final class SplashHintContrastCheck {
    private static int over(int foreground,int background) {
        int alpha=foreground>>>24,result=0xff000000;
        for(int shift:new int[]{0,8,16}) {
            int value=(((foreground>>>shift)&255)*alpha+((background>>>shift)&255)*(255-alpha)+127)/255;
            result|=value<<shift;
        }
        return result;
    }
    public static void main(String[] args) {
        int overlay=UiTheme.SPLASH_HINT_BACKGROUND;
        int checks=0;
        for(int channel=0;channel<256;channel++) {
            int background=0xff000000|channel|(channel<<8)|(channel<<16);
            double ratio=PaletteContrastAudit.contrast(0xffffffff,over(overlay,background));
            if(ratio<4.5) throw new AssertionError("White splash hint contrast="+ratio+" at gray="+channel);
            double alpha=(overlay>>>24)/255.0;
            double linearLuminance=(1-alpha)*PaletteContrastAudit.luminance(background);
            if(1.05/(linearLuminance+0.05)<4.5) throw new AssertionError("Linear-light splash hint contrast failed");
            checks+=2;
        }
        System.out.println("PASS "+checks+" sRGB/linear-light composited splash hint cases including worst-case white; real image/UI paint NOT RUN");
    }
}
