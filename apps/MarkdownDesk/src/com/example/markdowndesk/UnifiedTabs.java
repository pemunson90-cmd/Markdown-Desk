package com.example.markdowndesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
    private static final String PREFS="markdown_desk", MD="unified_markdown_tabs_v1", AR="unified_archive_tabs_v1", JS="unified_inspector_tabs_v1";
    static final String LAST_INSPECTOR_URI="last_inspector_uri";
    static final String LAST_INSPECTOR_POSITION="last_inspector_position";
    private UnifiedTabs() {}

    public static void updateMarkdown(Context context,List<DocumentTab> tabs,int activeIndex){
        if(context==null||tabs==null)return;
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
    /** General JSON files are read-only sessions; no file bytes or URI grants are stored.
     * A document already open is reselected, otherwise the user must close an old tab
     * when all 12 Inspector slots are occupied (never silently evict data or sessions).
     */
    /** Logical tabs are for existing provider-backed documents only; malformed
     * oversized/authority-free identities are never persisted or reopened.
     * This validates identity, not a substitute for an actual Android read grant.
     */
    static boolean validInspectorSource(Uri uri){
        if(uri==null)return false;
        String raw=uri.toString();
        return raw.length()<=8192
                &&"content".equalsIgnoreCase(uri.getScheme())
                &&uri.getHost()!=null&&!uri.getHost().isEmpty()
                &&uri.getPath()!=null&&!uri.getPath().isEmpty()
                &&uri.getFragment()==null;
    }

    public static boolean openInspector(Context context,Uri uri){
        if(context==null||!validInspectorSource(uri))return false;
        String id=uri.toString();
        List<UnifiedTabIndex.Entry> before=read(context,JS,UnifiedTabIndex.Kind.INSPECT);
        ArrayList<UnifiedTabIndex.Entry> next=new ArrayList<>();
        boolean existing=false;
        for(UnifiedTabIndex.Entry entry:before){
            if(entry.id.equals(id))existing=true;
            next.add(new UnifiedTabIndex.Entry(UnifiedTabIndex.Kind.INSPECT,entry.id,entry.title,
                    entry.id.equals(id),false));
        }
        if(!existing){
            if(next.size()>=12)return false;
            String tail=uri.getLastPathSegment();
            String title=tail==null||tail.trim().isEmpty()?"JSON document":tail;
            if(title.length()>120)title=title.substring(0,120);
            next.add(new UnifiedTabIndex.Entry(UnifiedTabIndex.Kind.INSPECT,id,title,true,false));
        }
        save(context,JS,next);
        return true;
    }

    /** Explicit close only. Never releases a SAF grant or modifies a source file. */
    public static void closeInspector(Context context,Uri uri){
        if(context==null||uri==null)return;
        String id=uri.toString();
        List<UnifiedTabIndex.Entry> before=read(context,JS,UnifiedTabIndex.Kind.INSPECT);
        ArrayList<UnifiedTabIndex.Entry> next=new ArrayList<>();
        boolean wasActive=false;
        for(UnifiedTabIndex.Entry entry:before){
            if(entry.id.equals(id)){wasActive=entry.active;continue;}
            next.add(entry);
        }
        if(next.size()==before.size())return;
        if(wasActive && !next.isEmpty()){
            int finalIndex=next.size()-1;
            for(int i=0;i<next.size();i++){
                UnifiedTabIndex.Entry entry=next.get(i);
                next.set(i,new UnifiedTabIndex.Entry(entry.kind,entry.id,entry.title,i==finalIndex,false));
            }
        }
        save(context,JS,next);
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .remove("json_inspector_position_v1:"+id).apply();
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
                read(context,AR,UnifiedTabIndex.Kind.ARCHIVE),
                read(context,JS,UnifiedTabIndex.Kind.INSPECT));
    }
    public static void show(Activity current){
        List<UnifiedTabIndex.Entry> tabs=current(current);
        if(tabs.isEmpty()){
            Toast.makeText(current,"No open workspace tabs",Toast.LENGTH_SHORT).show();return;
        }
        String[] labels=new String[tabs.size()];
        for(int i=0;i<tabs.size();i++){
            UnifiedTabIndex.Entry e=tabs.get(i);
            labels[i]=(e.active?"▶ ":"")+(e.kind==UnifiedTabIndex.Kind.ARCHIVE?"Archive · ":e.kind==UnifiedTabIndex.Kind.INSPECT?"Inspect · ":"MD · ")
                    +e.title+(e.dirty?"  ●":"");
        }
        new AlertDialog.Builder(current).setTitle("All Markdown Desk tabs")
            .setItems(labels,(dialog,index)->open(current,tabs.get(index)))
            .setNeutralButton("Close JSON tab…",(dialog,which)->showInspectorClose(current))
            .setNegativeButton("Cancel",null).show();
    }
    private static void showInspectorClose(Activity current){
        List<UnifiedTabIndex.Entry> all=read(current,JS,UnifiedTabIndex.Kind.INSPECT);
        if(all.isEmpty()){
            Toast.makeText(current,"No Inspector tabs to close",Toast.LENGTH_SHORT).show();return;
        }
        String[] labels=new String[all.size()];
        for(int i=0;i<all.size();i++)labels[i]=all.get(i).title;
        new AlertDialog.Builder(current).setTitle("Close a JSON Inspector tab")
            .setItems(labels,(dialog,which)->{
                UnifiedTabIndex.Entry entry=all.get(which);
                Uri uri=Uri.parse(entry.id);
                if(current instanceof JsonExplorerActivity
                    && ((JsonExplorerActivity)current).showingDocument(uri)){
                    ((JsonExplorerActivity)current).closeInspectorTabForSwitch();
                    current.finish();
                }else closeInspector(current,uri);
            }).setNegativeButton("Cancel",null).show();
    }

    static List<UnifiedTabIndex.Entry> readingPlaces(Context context){
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        ArrayList<UnifiedTabIndex.Entry> places=new ArrayList<>();
        Uri json=Uri.parse(prefs.getString(LAST_INSPECTOR_URI,""));
        if(validInspectorSource(json)){
            String name=json.getLastPathSegment();
            if(name!=null&&name.contains("/"))name=name.substring(name.lastIndexOf('/')+1);
            places.add(new UnifiedTabIndex.Entry(UnifiedTabIndex.Kind.INSPECT,json.toString(),
                    name==null||name.isEmpty()?"Last JSON file":name,false,false));
        }else for(UnifiedTabIndex.Entry entry:read(context,JS,UnifiedTabIndex.Kind.INSPECT)){
            if(entry.active){places.add(entry);break;}
        }
        String archive=prefs.getString("archive_last_chat","");
        if(!archive.isEmpty()){
            String title=prefs.getString("archive_last_title",null);
            if(title==null)for(UnifiedTabIndex.Entry entry:read(context,AR,UnifiedTabIndex.Kind.ARCHIVE)){
                if(entry.id.equals(archive)){title=entry.title;break;}
            }
            places.add(new UnifiedTabIndex.Entry(UnifiedTabIndex.Kind.ARCHIVE,archive,
                    title==null?"Last conversation":title,false,false));
        }
        return places;
    }

    static void open(Activity current,UnifiedTabIndex.Entry entry){
        Intent intent=new Intent();
        intent.setClassName(current.getPackageName(),entry.kind==UnifiedTabIndex.Kind.ARCHIVE?
                "com.example.markdowndesk.ArchiveWorkspaceActivity":
                entry.kind==UnifiedTabIndex.Kind.INSPECT?
                "com.example.markdowndesk.JsonExplorerActivity":"com.example.markdowndesk.MainActivity");
        if(entry.kind==UnifiedTabIndex.Kind.INSPECT){
            Uri uri=Uri.parse(entry.id);
            if(!validInspectorSource(uri)){
                Toast.makeText(current,"Invalid JSON tab source",Toast.LENGTH_LONG).show();return;
            }
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(uri); // Never fabricate a SAF grant; reauthorization may be required.
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_TAB_ID,entry.id);
        try{current.startActivity(intent);}
        catch(RuntimeException failed){Toast.makeText(current,"Tab unavailable; source session was preserved",Toast.LENGTH_LONG).show();}
    }
}