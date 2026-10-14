package com.example.markdowndesk;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public final class RecentStore {
    private static final String KEY = "recent_documents";
    private static final String PREFS = "markdown_desk";

    private RecentStore() {
    }

    public static final class Item {
        public final String name;
        public final long openedAt;
        public final String uri;

        Item(String str, String str2, long j) {
            this.uri = str;
            this.name = str2;
            this.openedAt = j;
        }
    }

    public static List<Item> read(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS, 0);
        ArrayList arrayList = new ArrayList();
        try {
            JSONArray jSONArray = new JSONArray(sharedPreferences.getString(KEY, "[]"));
            for (int i = 0; i < jSONArray.length(); i++) {
                JSONObject jSONObjectOptJSONObject = jSONArray.optJSONObject(i);
                if (jSONObjectOptJSONObject != null) {
                    String strOptString = jSONObjectOptJSONObject.optString("uri", "");
                    if (strOptString.isEmpty()) {
                        continue;
                    } else {
                        arrayList.add(new Item(strOptString, jSONObjectOptJSONObject.optString("name", "Document"), jSONObjectOptJSONObject.optLong("openedAt", 0L)));
                    }
                }
            }
        } catch (JSONException e) {
            sharedPreferences.edit().remove(KEY).apply();
        }
        return arrayList;
    }

    public static void touch(Context context, String str, String str2) {
        ArrayList arrayList = new ArrayList();
        arrayList.add(new Item(str, str2, System.currentTimeMillis()));
        for (Item item : read(context)) {
            if (!item.uri.equals(str) && arrayList.size() < 12) {
                arrayList.add(item);
            }
        }
        write(context, arrayList);
    }

    public static void remove(Context context, String str) {
        ArrayList arrayList = new ArrayList();
        for (Item item : read(context)) {
            if (!item.uri.equals(str)) {
                arrayList.add(item);
            }
        }
        write(context, arrayList);
    }

    private static void write(Context context, List<Item> list) {
        JSONArray jSONArray = new JSONArray();
        for (Item item : list) {
            JSONObject jSONObject = new JSONObject();
            try {
                jSONObject.put("uri", item.uri);
                jSONObject.put("name", item.name);
                jSONObject.put("openedAt", item.openedAt);
                jSONArray.put(jSONObject);
            } catch (JSONException e) {
            }
        }
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY, jSONArray.toString()).apply();
    }
}
