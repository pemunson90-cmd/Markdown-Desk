package com.example.markdowndesk;

import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Local folder shortcuts only. Actual persisted read grants determine current
 * access; remembering/removing a shortcut never takes or releases permissions. */
final class WorkspaceRootStore {
    private static final String PREFS="workspace_roots",KEY="roots_v1";
    private static final int MAX_ROOTS=8,MAX_URI=4096,MAX_LABEL=256,MAX_SERIALIZED=32768;
    private static final Object LOCK=new Object();
    private final SharedPreferences preferences;
    enum Access {CURRENT,ACCESS_NEEDED}
    static final class Root {
        final Uri uri;final String label;final long selectedAt;
        private Root(Uri uri,String label,long selectedAt){this.uri=uri;this.label=label;this.selectedAt=selectedAt;}
    }
    static final class RootState {
        final Root root;final Access access;
        private RootState(Root root,boolean read){this.root=root;access=read?Access.CURRENT:Access.ACCESS_NEEDED;}
    }
    WorkspaceRootStore(Context context){
        if(context==null)throw new IllegalArgumentException("Missing registry context");
        preferences=context.getSharedPreferences(PREFS,0);
    }
    List<RootState> list(ContentResolver resolver)throws IOException {
        synchronized(LOCK){
            List<Root> roots=read();Set<String> grants=readGrants(resolver);List<RootState> result=new ArrayList<>(roots.size());
            for(Root root:roots)result.add(new RootState(root,grants.contains(root.uri.toString())));
            return Collections.unmodifiableList(result);
        }
    }
    Root rememberGranted(Uri selectedTree,String label,ContentResolver resolver)throws IOException {
        synchronized(LOCK){
            validate(selectedTree,label,0);List<Root> roots=read();
            if(!readGrants(resolver).contains(selectedTree.toString()))throw new IOException("Folder needs a persisted read grant");
            List<Root> next=new ArrayList<>();Root selected=new Root(selectedTree,label,System.currentTimeMillis());next.add(selected);
            for(Root root:roots)if(!root.uri.toString().equals(selectedTree.toString()))next.add(root);
            if(next.size()>MAX_ROOTS)throw new IOException("Saved folder limit reached; remove a folder from the list first");
            write(next);return selected;
        }
    }
    void remove(Uri exactTree)throws IOException {
        synchronized(LOCK){
            validate(exactTree,"",0);List<Root> roots=read();List<Root> next=new ArrayList<>();
            for(Root root:roots)if(!root.uri.toString().equals(exactTree.toString()))next.add(root);
            write(next);
        }
    }
    private static Set<String> readGrants(ContentResolver resolver)throws IOException {
        if(resolver==null)throw new IOException("Missing folder grant resolver");
        try {
            List<UriPermission> permissions=resolver.getPersistedUriPermissions();if(permissions==null)throw new IOException("Folder grants unavailable");
            Set<String> granted=new HashSet<>();
            for(UriPermission permission:permissions)if(permission!=null&&permission.isReadPermission()&&permission.getUri()!=null)
                granted.add(permission.getUri().toString());
            return granted;
        }catch(RuntimeException failure){throw new IOException("Folder grants unavailable",failure);}
    }
    private List<Root> read()throws IOException {
        try {
            String raw=preferences.getString(KEY,null);if(raw==null)return new ArrayList<>();
            if(raw.length()>MAX_SERIALIZED)throw new IOException("Saved folder registry exceeds its limit");
            preflight(raw);
            JSONTokener parser=new JSONTokener(raw);Object decoded=parser.nextValue();
            if(!(decoded instanceof JSONObject)||parser.nextClean()!=0)throw new IOException("Malformed saved folder registry");
            JSONObject envelope=(JSONObject)decoded;Object version=envelope.get("version");
            if(!(version instanceof Integer||version instanceof Long)||((Number)version).longValue()!=1)throw new IOException("Unsupported saved folder registry");
            JSONArray rows=envelope.getJSONArray("roots");if(rows.length()>MAX_ROOTS)throw new IOException("Too many saved folders");
            List<Root> roots=new ArrayList<>(rows.length());Set<String> seen=new HashSet<>();
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);Object u=row.get("uri"),l=row.get("label"),t=row.get("selectedAt");
                if(!(u instanceof String)||!(l instanceof String)||!(t instanceof Integer||t instanceof Long))throw new IOException("Malformed saved folder record");
                Uri uri=Uri.parse((String)u);String label=(String)l;long timestamp=((Number)t).longValue();validate(uri,label,timestamp);
                if(!seen.add(uri.toString()))throw new IOException("Duplicate saved folder record");roots.add(new Root(uri,label,timestamp));
            }
            return roots;
        }catch(JSONException|RuntimeException invalid){throw new IOException("Saved folder registry could not be read; existing data was kept",invalid);}
    }
    /** Bound parser recursion before org.json sees input. Only double-quoted
     * strings are accepted; comments/single-quoted or bare names cannot hide
     * delimiters from this scan through org.json's permissive lexer. */
    private static void preflight(String raw)throws IOException {
        char[] stack=new char[8];int depth=0;boolean quoted=false,escaped=false;
        for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);
            if(quoted){
                if(escaped){
                    if(c=='u'){
                        if(i>raw.length()-5)throw new IOException("Malformed saved folder escape");
                        for(int j=0;j<4;j++)if(Character.digit(raw.charAt(++i),16)<0)throw new IOException("Malformed saved folder escape");
                    }else if("\"\\/bfnrt".indexOf(c)<0)throw new IOException("Malformed saved folder escape");
                    escaped=false;
                }else if(c=='\\')escaped=true;
                else if(c=='\"')quoted=false;
                else if(c<32)throw new IOException("Malformed saved folder string");
                continue;
            }
            if(c=='\"'){quoted=true;continue;}
            if(c=='{'||c=='['){
                if(depth==stack.length)throw new IOException("Saved folder nesting limit");stack[depth++]=c;
            }else if(c=='}'||c==']'){
                if(depth==0||stack[--depth]!=(c=='}'?'{':'['))throw new IOException("Malformed saved folder delimiters");
            }else if(c==':'||c==','||c==' '||c=='\t'||c=='\r'||c=='\n'||c=='-'||c>='0'&&c<='9'){
                // Number/grammar validity remains checked by the schema/parser.
            }else throw new IOException("Saved folder registry requires double-quoted JSON");
        }
        if(quoted||escaped||depth!=0)throw new IOException("Malformed saved folder delimiters");
    }
    private static void validate(Uri uri,String label,long timestamp)throws IOException {
        try {
            if(uri==null||uri.toString().length()>MAX_URI||!"content".equals(uri.getScheme())||uri.getAuthority()==null||uri.getAuthority().isEmpty()
                    ||!DocumentsContract.isTreeUri(uri)||label==null||label.length()>MAX_LABEL||timestamp<0)throw new IOException("Invalid saved folder record");
            String id=DocumentsContract.getTreeDocumentId(uri);if(id==null||id.isEmpty())throw new IOException("Invalid saved folder tree");
        }catch(RuntimeException invalid){throw new IOException("Invalid saved folder tree",invalid);}
    }
    private void write(List<Root> roots)throws IOException {
        try {
            JSONArray rows=new JSONArray();for(Root root:roots){JSONObject row=new JSONObject();row.put("uri",root.uri.toString());row.put("label",root.label);row.put("selectedAt",root.selectedAt);rows.put(row);}
            JSONObject envelope=new JSONObject();envelope.put("version",1);envelope.put("roots",rows);String raw=envelope.toString();
            if(raw.length()>MAX_SERIALIZED)throw new IOException("Saved folder registry exceeds its limit");
            // A failed commit may have changed process memory. Never claim durable
            // success, erase the old registry, or release grants to compensate.
            if(!preferences.edit().putString(KEY,raw).commit())throw new IOException("Saved folder registry could not be persisted");
        }catch(JSONException|RuntimeException failure){throw new IOException("Saved folder registry could not be persisted",failure);}
    }
}
