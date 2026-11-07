package com.example.markdowndesk;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public final class RecentStoreIntegrityCheck {
    private static final class MemoryPrefs implements SharedPreferences {
        final Map<String,String> saved = new HashMap<>();
        int mutations;
        @Override public String getString(String key,String fallback) { return saved.getOrDefault(key,fallback); }
        @Override public Editor edit() {
            return new Editor() {
                final Map<String,String> changes = new HashMap<>();
                String removal;
                @Override public Editor putString(String key,String value) { changes.put(key,value); return this; }
                @Override public Editor remove(String key) { removal=key; return this; }
                @Override public void apply() {
                    if(removal!=null) { saved.remove(removal); mutations++; }
                    if(!changes.isEmpty()) { saved.putAll(changes); mutations++; }
                }
            };
        }
    }
    static void ok(boolean pass, String detail) {
        if(!pass) throw new AssertionError(detail);
    }
    public static void main(String[] args) {
        MemoryPrefs prefs = new MemoryPrefs();
        Context ctx = new Context(prefs);
        String key = "recent_documents";
        String original = "[broken private entries";
        prefs.saved.put(key,original);
        ok(RecentStore.read(ctx).isEmpty(), "malformed input must fail closed");
        ok(original.equals(prefs.saved.get(key)) && prefs.mutations==0,
                "read may not erase malformed metadata");
        RecentStore.touch(ctx,"content://new","New");
        ok(original.equals(prefs.saved.get(key)) && prefs.mutations==0,
                "touch may not overwrite unreadable history");
        RecentStore.remove(ctx,"content://old");
        ok(original.equals(prefs.saved.get(key)) && prefs.mutations==0,
                "remove may not overwrite unreadable history");
        prefs.saved.put(key,"[]");
        RecentStore.touch(ctx,"content://new","New");
        ok(RecentStore.read(ctx).size()==1, "valid Recents still record newly opened documents");
        ok("content://new".equals(RecentStore.read(ctx).get(0).uri),
                "stored URI survives valid read");
        RecentStore.remove(ctx,"content://new");
        ok(RecentStore.read(ctx).isEmpty(), "explicit remove works for valid Recents");
        System.out.println("PASS: corrupt Recents read/touch/remove are non-destructive; valid add/remove intact");
    }
}
