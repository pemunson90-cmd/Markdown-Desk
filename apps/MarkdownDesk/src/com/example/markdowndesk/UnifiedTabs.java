package com.example.markdowndesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;
import com.example.markdowndesk.workspace.UnifiedTabIndex;
import com.example.markdowndesk.workspace.archive.ConversationTabs;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** One tab switcher for all source types. Actual content/state remains in the
 * source-specific protected session stores; metadata here is an indexed view.
 */
public final class UnifiedTabs {
    public static final String EXTRA_TAB_ID="com.example.markdowndesk.OPEN_LOGICAL_TAB";
    private static final String PREFS="markdown_desk", MD="unified_markdown_tabs_v1", AR="unified_archive_tabs_v1";
    private UnifiedTabs() {}

    public static void updateMarkdown(Context context,List<DocumentTab> tabs,int activeIndex){
        if(context==null||tabs==null||tabs.isEmpty())return;
        ArrayList<UnifiedTabIndex.Entry> entries=new ArrayList<>();
        for(int i=0;i<tabs.size()&&i<12;i++){
            DocumentTab tab=tabs.get(i);
            if(tab==null||tab.id==null||tab.id.isEmpty())continue;
            entries.add(new UnifiedTabIndex.Entry(UnifiedTabIndex.Kind.MARKDOWN,tab.id,
                    tab.displayName,i==activeIndex,tab.dirty));
        }
        save(context,MD,entries);
    }
    public static void updateArchive(Context context,List<ConversationTabs.Entry> tabs,String selected){
        if(context==null||tabs==null)return;
        ArrayList<UnifiedTabIndex.Entry> entries=new ArrayList<>();
        for(ConversationTabs.Entry entry:tabs){
            if(entry==null)continue;
            entries.add(new UnifiedTabIndex.Entry(UnifiedTabIndex.Kind.ARCHIVE,entry.id,entry.title,
                    entry.id.equals(selected),false));
        }
        save(context,AR,entries);
    }
    private static void save(Context context,String key,List<UnifiedTabIndex.Entry> rows){
        JSONArray data=new JSONArray();
        for(UnifiedTabIndex.Entry e:rows){
            JSONObject item=new JSONObject();
            try{item.put("id",e.id);item.put("title",e.title);item.put("active",e.active);
                item.put("dirty",e.dirty);data.put(item);}
            catch(Exception broken){return;}
        }
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String serialized=data.toString();
        if(!serialized.equals(prefs.getString(key,"")))prefs.edit().putString(key,serialized).apply();
    }
    private static List<UnifiedTabIndex.Entry> read(Context context,String key,UnifiedTabIndex.Kind kind){
        ArrayList<UnifiedTabIndex.Entry> rows=new ArrayList<>();
        String saved=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(key,"[]");
        try{
            JSONArray arr=new JSONArray(saved);
            for(int i=0;i<arr.length()&&i<12;i++){
                JSONObject row=arr.getJSONObject(i);String id=row.optString("id","");
                if(id.isEmpty())continue;
                rows.add(new UnifiedTabIndex.Entry(kind,id,row.optString("title","Untitled"),
                        row.optBoolean("active"),row.optBoolean("dirty")));
            }
        }catch(Exception invalid){rows.clear();}
        return rows;
    }
    public static List<UnifiedTabIndex.Entry> current(Context context){
        return UnifiedTabIndex.merge(read(context,MD,UnifiedTabIndex.Kind.MARKDOWN),
                read(context,AR,UnifiedTabIndex.Kind.ARCHIVE));
    }
    public static void show(Activity current){
        List<UnifiedTabIndex.Entry> tabs=current(current);
        if(tabs.isEmpty()){
            Toast.makeText(current,"No open workspace tabs",Toast.LENGTH_SHORT).show();return;
        }
        String[] labels=new String[tabs.size()];
        for(int i=0;i<tabs.size();i++){
            UnifiedTabIndex.Entry e=tabs.get(i);
            labels[i]=(e.active?"▶ ":"")+(e.kind==UnifiedTabIndex.Kind.ARCHIVE?"JSON · ":"MD · ")
                    +e.title+(e.dirty?"  ●":"");
        }
        new AlertDialog.Builder(current).setTitle("All Markdown Desk tabs")
            .setItems(labels,(dialog,index)->open(current,tabs.get(index)))
            .setNegativeButton("Close",null).show();
    }
    private static void open(Activity current,UnifiedTabIndex.Entry entry){
        Intent intent=new Intent();
        intent.setClassName(current.getPackageName(),entry.kind==UnifiedTabIndex.Kind.ARCHIVE?
                "com.example.markdowndesk.ArchiveWorkspaceActivity":"com.example.markdowndesk.MainActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_TAB_ID,entry.id);
        try{current.startActivity(intent);}
        catch(RuntimeException failed){Toast.makeText(current,"Tab unavailable; source session was preserved",Toast.LENGTH_LONG).show();}
    }
}