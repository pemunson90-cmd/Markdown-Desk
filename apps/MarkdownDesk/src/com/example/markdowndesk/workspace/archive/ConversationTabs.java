package com.example.markdowndesk.workspace.archive;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only conversation tabs, matching Markdown Desk's 12-tab limit.
 * No source JSON data or user notes are stored in the tab index.
 */
public final class ConversationTabs {
    public static final int LIMIT=12;
    public static final class Entry {
        public final String id,title;
        Entry(String id,String title){this.id=id;this.title=title;}
    }
    private final SharedPreferences prefs;
    private final ArrayList<Entry> items=new ArrayList<>();
    private String selected;
    public ConversationTabs(Context context){
        prefs=context.getSharedPreferences("markdown_desk",Context.MODE_PRIVATE);
        selected=prefs.getString("archive_selected_tab_v1",null);
        String raw=prefs.getString("archive_open_tabs_v1","[]");
        try{
            JSONArray arr=new JSONArray(raw);
            for(int i=0;i<arr.length()&&items.size()<LIMIT;i++){
                JSONObject o=arr.getJSONObject(i);
                String id=o.optString("id","").trim();
                if(id.isEmpty()||has(id))continue;
                items.add(new Entry(id,title(o.optString("title","Untitled"))));
            }
        }catch(Exception invalid){items.clear();}
        if(!has(selected))selected=items.isEmpty()?null:items.get(items.size()-1).id;
    }
    public List<Entry> entries(){return Collections.unmodifiableList(new ArrayList<>(items));}
    public String selected(){return selected;}
    public boolean open(String id,String name){
        if(id==null||id.trim().isEmpty())return false;
        if(!has(id)){
            if(items.size()>=LIMIT)return false;
            items.add(new Entry(id,title(name)));
        }
        selected=id;persist();return true;
    }
    public void close(String id){
        if(id==null)return;
        for(int i=0;i<items.size();i++)if(id.equals(items.get(i).id)){
            items.remove(i);break;
        }
        if(id.equals(selected))selected=items.isEmpty()?null:items.get(items.size()-1).id;
        persist();
    }
    private boolean has(String id){
        if(id==null)return false;
        for(Entry e:items)if(id.equals(e.id))return true;
        return false;
    }
    private static String title(String title){
        if(title==null||title.trim().isEmpty())return "Untitled";
        return title.length()>160?title.substring(0,160):title;
    }
    private void persist(){
        JSONArray arr=new JSONArray();
        try{
            for(Entry e:items){JSONObject o=new JSONObject();o.put("id",e.id);o.put("title",e.title);arr.put(o);}
        }catch(Exception invalid){return;}
        prefs.edit().putString("archive_open_tabs_v1",arr.toString())
            .putString("archive_selected_tab_v1",selected).apply();
    }
}