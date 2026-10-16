package com.example.markdowndesk;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Local optional reading metadata only. No document, permission, session or history writes.
 * Synchronous: caller runs storage operations on its I/O queue. */
final class ReaderPositionStore {
    private static final String PREFS="reader_positions",KEY="state_v1";
    private static final int MAX_DOCUMENTS=64,MAX_PER_DOCUMENT=16,MAX_BOOKMARKS=128,
            MAX_SERIALIZED=524288,MAX_BYTES=2<<20;
    private static final Object LOCK=new Object();
    private final SharedPreferences preferences;
    static final class Bookmark {
        final String id,label;final long createdAt;final ReaderAnchor.Anchor anchor;
        private Bookmark(String id,String label,long createdAt,ReaderAnchor.Anchor anchor){this.id=id;this.label=label;this.createdAt=createdAt;this.anchor=anchor;}
    }
    static final class DocumentState {
        final String uri;final ReaderAnchor.Anchor location;final List<Bookmark> bookmarks;
        private final long touched;
        private DocumentState(String uri,ReaderAnchor.Anchor location,List<Bookmark> bookmarks,long touched){
            this.uri=uri;this.location=location;this.bookmarks=Collections.unmodifiableList(new ArrayList<>(bookmarks));this.touched=touched;
        }
    }
    ReaderPositionStore(Context context){
        if(context==null)throw new IllegalArgumentException("Missing reading context");
        preferences=context.getSharedPreferences(PREFS,0);
    }
    DocumentState read(String exactUri)throws IOException {
        synchronized(LOCK){uri(exactUri);for(DocumentState row:load())if(row.uri.equals(exactUri))return row;return null;}
    }
    void saveLocation(String exactUri,ReaderAnchor.Anchor anchor)throws IOException {
        synchronized(LOCK){uri(exactUri);requireAnchor(anchor);List<DocumentState> rows=load();DocumentState old=find(rows,exactUri);long touch=nextTouch(rows);
            replace(rows,new DocumentState(exactUri,anchor,old==null?Collections.emptyList():old.bookmarks,touch));write(rows);}
    }
    Bookmark addBookmark(String exactUri,String label,ReaderAnchor.Anchor anchor)throws IOException {
        synchronized(LOCK){uri(exactUri);text(label,80);requireAnchor(anchor);List<DocumentState> rows=load();DocumentState old=find(rows,exactUri);int total=0;for(DocumentState row:rows)total+=row.bookmarks.size();
            if(total>=MAX_BOOKMARKS||old!=null&&old.bookmarks.size()>=MAX_PER_DOCUMENT)throw limit();
            Bookmark added=new Bookmark(UUID.randomUUID().toString(),label,Math.max(0,System.currentTimeMillis()),anchor);
            List<Bookmark> bookmarks=new ArrayList<>(old==null?Collections.emptyList():old.bookmarks);bookmarks.add(added);
            replace(rows,new DocumentState(exactUri,old==null?null:old.location,bookmarks,nextTouch(rows)));write(rows);return added;}
    }
    void removeBookmark(String exactUri,String id)throws IOException {
        synchronized(LOCK){uri(exactUri);uuid(id);List<DocumentState> rows=load();DocumentState old=find(rows,exactUri);if(old==null)return;
            List<Bookmark> bookmarks=new ArrayList<>();for(Bookmark bookmark:old.bookmarks)if(!bookmark.id.equals(id))bookmarks.add(bookmark);
            if(bookmarks.size()==old.bookmarks.size())return;
            long touch=nextTouch(rows);rows.remove(old);if(old.location!=null||!bookmarks.isEmpty())rows.add(new DocumentState(exactUri,old.location,bookmarks,touch));write(rows);}
    }
    void forget(String exactUri)throws IOException {
        synchronized(LOCK){uri(exactUri);List<DocumentState> rows=load();DocumentState old=find(rows,exactUri);if(old!=null){rows.remove(old);write(rows);}}
    }
    private static DocumentState find(List<DocumentState> rows,String uri){for(DocumentState row:rows)if(row.uri.equals(uri))return row;return null;}
    private static long nextTouch(List<DocumentState> rows)throws IOException {long max=0;for(DocumentState row:rows)max=Math.max(max,row.touched);if(max==Long.MAX_VALUE)throw limit();return max+1;}
    private static void replace(List<DocumentState> rows,DocumentState next)throws IOException {
        DocumentState old=find(rows,next.uri);if(old!=null)rows.remove(old);
        else if(rows.size()==MAX_DOCUMENTS){DocumentState victim=null;for(DocumentState row:rows)if(row.bookmarks.isEmpty()&&(victim==null||row.touched<victim.touched))victim=row;if(victim==null)throw limit();rows.remove(victim);}
        rows.add(next);
    }
    private List<DocumentState> load()throws IOException {
        try{
            String raw=preferences.getString(KEY,null);if(raw==null)return new ArrayList<>();serialized(raw);new Grammar(raw).validate();
            Object decoded=new JSONTokener(raw).nextValue();if(!(decoded instanceof JSONObject))throw corrupt();JSONObject envelope=(JSONObject)decoded;keys(envelope,"version","documents");if(number(envelope,"version")!=1)throw corrupt();
            JSONArray documents=envelope.getJSONArray("documents");if(documents.length()>MAX_DOCUMENTS)throw corrupt();
            List<DocumentState> rows=new ArrayList<>();Set<String> uris=new HashSet<>(),ids=new HashSet<>();int total=0;
            for(int i=0;i<documents.length();i++){
                JSONObject row=documents.getJSONObject(i);keys(row,"uri","touched","location","bookmarks");String uri=string(row,"uri");uri(uri);if(!uris.add(uri))throw corrupt();long touch=number(row,"touched");if(touch<1)throw corrupt();
                ReaderAnchor.Anchor location=row.isNull("location")?null:anchor(row.getJSONObject("location"));JSONArray saved=row.getJSONArray("bookmarks");if(saved.length()>MAX_PER_DOCUMENT||(total+=saved.length())>MAX_BOOKMARKS)throw corrupt();
                List<Bookmark> bookmarks=new ArrayList<>();for(int j=0;j<saved.length();j++){
                    JSONObject item=saved.getJSONObject(j);keys(item,"id","label","createdAt","anchor");String id=string(item,"id"),label=string(item,"label");uuid(id);text(label,80);long time=number(item,"createdAt");if(time<0||!ids.add(id))throw corrupt();bookmarks.add(new Bookmark(id,label,time,anchor(item.getJSONObject("anchor"))));
                }
                if(location==null&&bookmarks.isEmpty())throw corrupt();rows.add(new DocumentState(uri,location,bookmarks,touch));
            }
            return rows;
        }catch(JSONException|RuntimeException failure){throw corrupt();}
    }
    private void write(List<DocumentState> rows)throws IOException {
        try{
            JSONArray documents=new JSONArray();for(DocumentState row:rows){JSONObject item=new JSONObject();item.put("uri",row.uri);item.put("touched",row.touched);item.put("location",row.location==null?JSONObject.NULL:json(row.location));JSONArray bookmarks=new JSONArray();for(Bookmark bookmark:row.bookmarks){JSONObject b=new JSONObject();b.put("id",bookmark.id);b.put("label",bookmark.label);b.put("createdAt",bookmark.createdAt);b.put("anchor",json(bookmark.anchor));bookmarks.put(b);}item.put("bookmarks",bookmarks);documents.put(item);}
            JSONObject envelope=new JSONObject();envelope.put("version",1);envelope.put("documents",documents);String raw=envelope.toString();serialized(raw);
            if(!preferences.edit().putString(KEY,raw).commit())throw persistence();
        }catch(JSONException|RuntimeException failure){throw persistence();}
    }
    private static JSONObject json(ReaderAnchor.Anchor anchor)throws JSONException {
        JSONObject row=new JSONObject();row.put("kind",anchor.kind.name());row.put("hash",anchor.sourceHash);row.put("length",anchor.sourceLength);row.put("offset",anchor.offset);row.put("quote",anchor.quote);row.put("prefix",anchor.prefix);row.put("suffix",anchor.suffix);return row;
    }
    private static ReaderAnchor.Anchor anchor(JSONObject row)throws IOException,JSONException {
        keys(row,"kind","hash","length","offset","quote","prefix","suffix");long length=number(row,"length"),offset=number(row,"offset");if(length<0||length>ReaderAnchor.MAX_SOURCE||offset<0||offset>length)throw corrupt();
        ReaderAnchor.Kind kind;try{kind=ReaderAnchor.Kind.valueOf(string(row,"kind"));}catch(IllegalArgumentException failure){throw corrupt();}
        return ReaderAnchor.restore(kind,string(row,"hash"),(int)length,(int)offset,string(row,"quote"),string(row,"prefix"),string(row,"suffix"));
    }
    private static void requireAnchor(ReaderAnchor.Anchor anchor)throws IOException {
        if(anchor==null)throw corrupt();ReaderAnchor.restore(anchor.kind,anchor.sourceHash,anchor.sourceLength,anchor.offset,anchor.quote,anchor.prefix,anchor.suffix);
    }
    private static String string(JSONObject row,String name)throws JSONException,IOException {Object value=row.get(name);if(!(value instanceof String))throw corrupt();return(String)value;}
    private static long number(JSONObject row,String name)throws JSONException,IOException {Object value=row.get(name);if(!(value instanceof Integer||value instanceof Long))throw corrupt();return((Number)value).longValue();}
    private static void keys(JSONObject row,String...expected)throws IOException {
        if(row.length()!=expected.length)throw corrupt();for(String key:expected)if(!row.has(key))throw corrupt();
    }
    private static void uuid(String id)throws IOException {try{if(id==null||id.length()!=36||!UUID.fromString(id).toString().equals(id))throw corrupt();}catch(IllegalArgumentException failure){throw corrupt();}}
    private static void uri(String value)throws IOException {
        text(value,4096);try{Uri uri=Uri.parse(value);if(!"content".equals(uri.getScheme())||uri.getAuthority()==null||uri.getAuthority().isEmpty())throw corrupt();for(int i=0;i<value.length();i++)if(Character.isWhitespace(value.charAt(i))||Character.isISOControl(value.charAt(i)))throw corrupt();}catch(RuntimeException failure){throw corrupt();}
    }
    private static void text(String value,int max)throws IOException {
        if(value==null||value.length()>max)throw corrupt();for(int i=0;i<value.length();i++){char ch=value.charAt(i);if(Character.isHighSurrogate(ch)){if(++i==value.length()||!Character.isLowSurrogate(value.charAt(i)))throw corrupt();}else if(Character.isLowSurrogate(ch))throw corrupt();}
    }
    private static void serialized(String raw)throws IOException {
        if(raw==null||raw.length()>MAX_SERIALIZED)throw limit();long bytes=0;
        for(int i=0;i<raw.length();i++){char ch=raw.charAt(i);if(Character.isHighSurrogate(ch)){if(++i==raw.length()||!Character.isLowSurrogate(raw.charAt(i)))throw corrupt();bytes+=4;}else{if(Character.isLowSurrogate(ch))throw corrupt();bytes+=ch<128?1:ch<2048?2:3;}if(bytes>MAX_BYTES)throw limit();}
    }
    /** Private grammar preflight bounds recursion/containers and rejects duplicate decoded keys
     * before org.json can discard duplicates. This accepts exactly JSON strings/integers/null. */
    private static final class Grammar {
        final String text;int at,containers;
        Grammar(String text){this.text=text;}
        void validate()throws IOException {value(0);space();if(at!=text.length())throw corrupt();}
        void value(int depth)throws IOException {
            space();if(at==text.length())throw corrupt();char c=text.charAt(at);
            if(c=='{'||c=='['){if(depth==8||++containers>1024)throw corrupt();at++;space();boolean object=c=='{';char end=object?'}':']';if(take(end))return;Set<String>keys=object?new HashSet<>():null;int count=0;
                do{if(++count>(object?12:128))throw corrupt();if(object){String key=quoted(true);if(!keys.add(key))throw corrupt();space();if(!take(':'))throw corrupt();}value(depth+1);space();if(take(end))return;if(!take(','))throw corrupt();space();}while(true);
            }
            if(c=='"'){quoted(false);return;}
            if(text.startsWith("null",at)){at+=4;return;}
            int start=at;if(take('-')&&at==text.length())throw corrupt();if(at>=text.length()||text.charAt(at)<'0'||text.charAt(at)>'9')throw corrupt();
            if(text.charAt(at++)!='0')while(at<text.length()&&text.charAt(at)>='0'&&text.charAt(at)<='9')at++;
            if(at-start>20)throw corrupt();
        }
        String quoted(boolean key)throws IOException {
            space();int start=at;if(!take('"'))throw corrupt();while(at<text.length()){
                char c=text.charAt(at++);if(c=='"'){
                    if(!key)return null;if(at-start>194)throw corrupt();try{Object value=new JSONTokener(text.substring(start,at)).nextValue();if(!(value instanceof String)||((String)value).length()>32)throw corrupt();return(String)value;}catch(JSONException failure){throw corrupt();}
                }
                if(c<32)throw corrupt();if(c=='\\'){
                    if(at==text.length())throw corrupt();char escape=text.charAt(at++);if(escape=='u'){for(int i=0;i<4;i++){if(at==text.length())throw corrupt();char hex=text.charAt(at++);if(!(hex>='0'&&hex<='9'||hex>='a'&&hex<='f'||hex>='A'&&hex<='F'))throw corrupt();}}else if("\"\\/bfnrt".indexOf(escape)<0)throw corrupt();
                }
            }throw corrupt();
        }
        boolean take(char c){if(at<text.length()&&text.charAt(at)==c){at++;return true;}return false;}
        void space(){while(at<text.length()&&(text.charAt(at)==' '||text.charAt(at)=='\n'||text.charAt(at)=='\r'||text.charAt(at)=='\t'))at++;}
    }
    private static IOException corrupt(){return new IOException("Saved reading data is invalid or unavailable; existing data was kept.");}
    private static IOException limit(){return new IOException("Saved reading data limit reached; remove a bookmark or saved place.");}
    private static IOException persistence(){return new IOException("Saved reading data could not be persisted.");}
}
