package com.example.markdowndesk;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Explicit local shortcuts only. Metadata grants no access. No provider, permission,
 * recents, saved-root, history or document operations belong to this store. */
final class WorkspaceFavoritesStore {
    private static final String PREFS="workspace_favorites",KEY="favorites_v1";
    private static final int MAX_RECORDS=64,MAX_URI=4096,MAX_LABEL=256,MAX_SERIALIZED=32768;
    private static final Object LOCK=new Object();
    private final SharedPreferences preferences;
    enum Kind {DOCUMENT,TREE}
    static final class Favorite {
        final Uri uri;final String label;final Kind kind;
        private Favorite(Uri uri,String label,Kind kind){this.uri=uri;this.label=label;this.kind=kind;}
    }
    WorkspaceFavoritesStore(Context applicationContext){
        if(applicationContext==null)throw new IllegalArgumentException("Missing favorites context");
        preferences=applicationContext.getSharedPreferences(PREFS,0);
    }
    List<Favorite> read()throws IOException {synchronized(LOCK){return Collections.unmodifiableList(load());}}
    Favorite pin(Uri exactUri,String label,Kind kind)throws IOException {
        synchronized(LOCK){
            validate(exactUri,label,kind);List<Favorite> saved=load();int existing=-1;
            for(int i=0;i<saved.size();i++)if(saved.get(i).uri.toString().equals(exactUri.toString())){existing=i;break;}
            if(existing>=0&&saved.get(existing).kind!=kind)throw invalid();
            if(existing<0&&saved.size()==MAX_RECORDS)throw limit();
            Favorite favorite=new Favorite(exactUri,label,kind);
            if(existing<0)saved.add(favorite);else saved.set(existing,favorite);
            write(saved);return favorite;
        }
    }
    void unpin(Uri exactUri)throws IOException {
        synchronized(LOCK){
            uri(exactUri);List<Favorite> saved=load();
            for(Iterator<Favorite> it=saved.iterator();it.hasNext();)if(it.next().uri.toString().equals(exactUri.toString()))it.remove();
            // Commit even an absent removal: a preceding false commit may have
            // changed process memory without persisting the removal to disk.
            write(saved);
        }
    }
    private List<Favorite> load()throws IOException {
        try {
            String raw=preferences.getString(KEY,null);if(raw==null)return new ArrayList<>();
            if(raw.length()>MAX_SERIALIZED)throw limit();new Grammar(raw).validate();
            Object decoded=new JSONTokener(raw).nextValue();if(!(decoded instanceof JSONObject))throw invalid();
            JSONObject envelope=(JSONObject)decoded;keys(envelope,"version","favorites");Object version=envelope.get("version");
            if(!(version instanceof Integer||version instanceof Long)||((Number)version).longValue()!=1)throw invalid();
            JSONArray rows=envelope.getJSONArray("favorites");if(rows.length()>MAX_RECORDS)throw limit();
            List<Favorite> saved=new ArrayList<>(rows.length());Set<String> seen=new HashSet<>();
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);keys(row,"uri","label","kind");
                Uri uri=Uri.parse(string(row,"uri"));String label=string(row,"label");Kind kind=Kind.valueOf(string(row,"kind"));
                validate(uri,label,kind);if(!seen.add(uri.toString()))throw invalid();saved.add(new Favorite(uri,label,kind));
            }
            return saved;
        }catch(JSONException|RuntimeException failure){throw invalid();}
    }
    private void write(List<Favorite> saved)throws IOException {
        try {
            JSONArray rows=new JSONArray();for(Favorite favorite:saved){JSONObject row=new JSONObject();row.put("uri",favorite.uri.toString());row.put("label",favorite.label);row.put("kind",favorite.kind.name());rows.put(row);}
            JSONObject envelope=new JSONObject();envelope.put("version",1);envelope.put("favorites",rows);String raw=envelope.toString();
            if(raw.length()>MAX_SERIALIZED)throw limit();
            if(!preferences.edit().putString(KEY,raw).commit())throw persistence();
        }catch(JSONException|RuntimeException failure){throw persistence();}
    }
    private static String string(JSONObject row,String name)throws JSONException,IOException {Object value=row.get(name);if(!(value instanceof String))throw invalid();return(String)value;}
    private static void keys(JSONObject row,String...expected)throws IOException {if(row.length()!=expected.length)throw invalid();for(String key:expected)if(!row.has(key))throw invalid();}
    private static void validate(Uri uri,String label,Kind kind)throws IOException {
        uri(uri);text(label,MAX_LABEL);if(kind==null||kind==Kind.TREE&&!DocumentsContract.isTreeUri(uri))throw invalid();
        if(kind==Kind.TREE)try{String id=DocumentsContract.getTreeDocumentId(uri);if(id==null||id.isEmpty())throw invalid();}catch(RuntimeException failure){throw invalid();}
    }
    private static void uri(Uri uri)throws IOException {
        if(uri==null)throw invalid();text(uri.toString(),MAX_URI);
        if(!"content".equals(uri.getScheme())||uri.getAuthority()==null||uri.getAuthority().isEmpty())throw invalid();
    }
    private static void text(String text,int maximum)throws IOException {
        if(text==null||text.isEmpty()||text.length()>maximum)throw invalid();
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(++i==text.length()||!Character.isLowSurrogate(text.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}
    }
    /** Schema-local strict grammar/depth/decoded-key preflight before org.json.
     * No comments, single quotes, bare keys, floats, duplicate keys or recursion>8. */
    private static final class Grammar {
        final String raw;int at,containers;
        Grammar(String raw){this.raw=raw;}
        void validate()throws IOException {value(0);space();if(at!=raw.length())throw invalid();}
        void value(int depth)throws IOException {
            space();if(at==raw.length())throw invalid();char c=raw.charAt(at);
            if(c=='{'||c=='['){
                if(depth==8||++containers>128)throw invalid();at++;space();boolean object=c=='{';char end=object?'}':']';if(take(end))return;
                Set<String> keys=object?new HashSet<>():null;int count=0;
                do{if(++count>(object?3:64))throw invalid();if(object){String key=quoted(true);if(!keys.add(key))throw invalid();space();if(!take(':'))throw invalid();}value(depth+1);space();if(take(end))return;if(!take(','))throw invalid();space();}while(true);
            }
            if(c=='"'){quoted(false);return;}
            int start=at;if(take('-')&&at==raw.length())throw invalid();if(at==raw.length()||raw.charAt(at)<'0'||raw.charAt(at)>'9')throw invalid();
            if(raw.charAt(at++)!='0')while(at<raw.length()&&raw.charAt(at)>='0'&&raw.charAt(at)<='9')at++;
            if(at-start>20)throw invalid();
        }
        String quoted(boolean key)throws IOException {
            space();int start=at;if(!take('"'))throw invalid();
            while(at<raw.length()){
                char c=raw.charAt(at++);if(c=='"'){
                    if(!key)return null;if(at-start>194)throw invalid();
                    try{Object decoded=new JSONTokener(raw.substring(start,at)).nextValue();if(!(decoded instanceof String)||((String)decoded).length()>32)throw invalid();return(String)decoded;}catch(JSONException failure){throw invalid();}
                }
                if(c<32)throw invalid();if(c=='\\'){
                    if(at==raw.length())throw invalid();char escape=raw.charAt(at++);
                    if(escape=='u'){for(int i=0;i<4;i++){if(at==raw.length())throw invalid();char hex=raw.charAt(at++);if(!(hex>='0'&&hex<='9'||hex>='a'&&hex<='f'||hex>='A'&&hex<='F'))throw invalid();}}
                    else if("\"\\/bfnrt".indexOf(escape)<0)throw invalid();
                }
            }throw invalid();
        }
        boolean take(char c){if(at<raw.length()&&raw.charAt(at)==c){at++;return true;}return false;}
        void space(){while(at<raw.length()&&(raw.charAt(at)==' '||raw.charAt(at)=='\n'||raw.charAt(at)=='\r'||raw.charAt(at)=='\t'))at++;}
    }
    private static IOException invalid(){return new IOException("Saved favorites are invalid or unavailable; existing data was kept.");}
    private static IOException limit(){return new IOException("Saved favorites limit reached; remove a favorite before adding another.");}
    private static IOException persistence(){return new IOException("Favorites could not be persisted.");}
}
