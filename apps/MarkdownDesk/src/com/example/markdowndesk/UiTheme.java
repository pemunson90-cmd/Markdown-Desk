package com.example.markdowndesk;

import android.graphics.drawable.GradientDrawable;

/* JADX INFO: loaded from: classes.dex */
final class UiTheme {
    static final int FOREST = 3;
    static final int BUBBLEGUM = 4;
    static final int AMETHYST = 5;
    static final int BLACK_ROSE = 6;
    static final int OCEAN = 7;
    static final int ARCADE = 8;
    static final int MIDNIGHT = 1;
    static final int PAPER = 0;
    static final int SEPIA = 2;

    static final class Palette {
        final int accent;
        final int accentSoft;
        final int border;
        final int canvas;
        final int code;
        final int danger;
        final boolean dark;
        final int muted;
        final String name;
        final int raised;
        final int surface;
        final int text;

        Palette(String str, boolean z, String str2, String str3, String str4, String str5, String str6, String str7, String str8, String str9, String str10, String str11) {
            this.name = str;
            this.dark = z;
            this.canvas = UiTheme.parse(str2);
            this.surface = UiTheme.parse(str3);
            this.raised = UiTheme.parse(str4);
            this.text = UiTheme.parse(str5);
            this.muted = UiTheme.parse(str6);
            this.accent = UiTheme.parse(str7);
            this.accentSoft = UiTheme.parse(str8);
            this.border = UiTheme.parse(str9);
            this.code = UiTheme.parse(str10);
            this.danger = UiTheme.parse(str11);
        }
    }

    private UiTheme() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int parse(String str) {
        return (int) (Long.parseLong(str.substring(MIDNIGHT), 16) | 4278190080L);
    }

    static Palette palette(int i, boolean z) {
        if (i != MIDNIGHT && (i >= 0 || !z)) {
            if (i != SEPIA) {
                if (i == FOREST) {
                    return new Palette("Forest", true, "#101714", "#17201C", "#213029", "#E9F1EA", "#9FB1A5", "#78B891", "#263D30", "#34483B", "#1B2821", "#FFB4AB");
                }
                if (i == BUBBLEGUM) {
                    return new Palette("Bubblegum", false, "#FFF5FB", "#FFEAF6", "#FFF9FD", "#3C2435", "#8A657D", "#E45FA7", "#FFD0E9", "#E8B9D2", "#FCE6F2", "#C33C6F");
                }
                if (i == AMETHYST) {
                    return new Palette("Amethyst", true, "#120E18", "#1A1322", "#281B36", "#F6EEFF", "#BFAFD0", "#B873FF", "#382149", "#4B3961", "#21182C", "#FF7AAE");
                }
                if (i == BLACK_ROSE) {
                    return new Palette("Black Rose", true, "#140E11", "#1D1418", "#2A1B21", "#F7EEF1", "#B7A0A8", "#D06A8B", "#3C222D", "#4A343B", "#24171C", "#FF9AA8");
                }
                if (i == OCEAN) {
                    return new Palette("Ocean", true, "#0C1518", "#112027", "#19313A", "#EDF8FA", "#9CB8BF", "#5CC6C7", "#1D3C42", "#31515A", "#14262C", "#FF9A9A");
                }
                if (i == ARCADE) {
                    return new Palette("Arcade", true, "#0D0B15", "#151125", "#221B39", "#F8F4FF", "#AFA4C7", "#45E6FF", "#222C4C", "#4B3E67", "#18152B", "#FF5DA8");
                }
                return new Palette("Paper", false, "#FBF8F2", "#F7F3EC", "#FFFFFF", "#28231E", "#746D64", "#C85C32", "#F5DED3", "#DED7CE", "#F0ECE5", "#B3261E");
            }
            return new Palette("Sepia", false, "#F2E6CF", "#F8EFDD", "#FFF8E9", "#392F24", "#746654", "#9D4D2D", "#EBD0B4", "#D3C1A5", "#E9DBC3", "#A73535");
        }
        return new Palette("Midnight", true, "#12110F", "#191815", "#24221E", "#F4EFE7", "#ABA49A", "#E7956F", "#3A2720", "#393630", "#211F1B", "#FFB4AB");
    }

    static GradientDrawable rounded(int i, float f, int i2, int i3) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(i);
        gradientDrawable.setCornerRadius(f);
        if (i3 > 0) {
            gradientDrawable.setStroke(i3, i2);
        }
        return gradientDrawable;
    }
}
