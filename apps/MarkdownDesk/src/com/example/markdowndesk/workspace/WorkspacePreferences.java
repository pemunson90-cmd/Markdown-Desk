package com.example.markdowndesk.workspace;

import android.content.Context;
import android.content.SharedPreferences;

/** One source of truth for workspace presentation and navigation preferences. */
public final class WorkspacePreferences {
    public static final String KEY_UNIFIED = "workspace_unified_shell_v1";
    private static final String STORE = "markdown_desk";
    private WorkspacePreferences() {}

    public static boolean unified(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getBoolean(KEY_UNIFIED, true);
    }
    public static boolean toggleUnified(Context context) {
        SharedPreferences prefs=context.getSharedPreferences(STORE,Context.MODE_PRIVATE);
        boolean enabled=!prefs.getBoolean(KEY_UNIFIED,true);
        prefs.edit().putBoolean(KEY_UNIFIED,enabled).apply();
        return enabled;
    }
}