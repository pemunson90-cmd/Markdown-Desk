package com.example.markdowndesk;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import org.mothership.suite.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;

/** A separate standard-launch Activity: each caller result belongs to exactly one editor session. */
public final class SuiteEditorActivity extends Activity {
    // Shared ordering prevents an old Activity's queued autosave overwriting its rotated successor.
    private static final ExecutorService io=Executors.newSingleThreadExecutor();
    private static final java.util.Map<String,SuiteEditorActivity> active=new java.util.HashMap<>();
    private final ExecutorService renders=Executors.newSingleThreadExecutor();
    private String sessionId;
    private final Handler main=new Handler(Looper.getMainLooper());
    private JSONObject request;
    private File folder;
    private EditText editor;
    private WebView preview;
    private TextView status;
    private LinearLayout chrome;
    private Button fullButton;
    private String text="",baseHash="";
    private boolean editable,previewing,loaded,saving,full;
    private float font=18;
    private int renderGeneration=0;
    private Runnable pendingDraft;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        try{
            EditorProtocol.require(EditorProtocol.MOTHERSHIP.equals(getCallingPackage())&&SuiteTrust.trusted(this,EditorProtocol.MOTHERSHIP,EditorProtocol.MOTHERSHIP_CERT),"Open this editor from the signed Mothership app");
            if(EditorProtocol.CAPS.equals(getIntent().getAction())){
                JSONObject caps=new JSONObject().put("protocol_version",1).put("read",true).put("edit",true).put("save_return",true).put("encoding","UTF-8").put("max_bytes",EditorProtocol.MAX_BYTES);
                setResult(RESULT_OK,new Intent().putExtra(EditorProtocol.RESULT,caps.toString()));finish();return;
            }
            EditorProtocol.require(EditorProtocol.OPEN.equals(getIntent().getAction()),"Unknown editor action");
            String raw=getIntent().getStringExtra(EditorProtocol.REQUEST);
            EditorProtocol.require(raw!=null&&raw.length()<=8192,"Missing or oversized request");
            request=new JSONObject(raw);EditorJson.request(request);EditorProtocol.require(request.getInt("protocol_version")==1,"Unsupported protocol");
            String id=EditorProtocol.id(request.getString("session_id"));
            synchronized(active){
                SuiteEditorActivity previous=active.get(id);
                EditorProtocol.require(previous==null||previous.isDestroyed()||previous.isChangingConfigurations(),"This working copy is already open. Return to its existing editor window.");
                active.put(id,this);sessionId=id;
            }
            baseHash=EditorProtocol.hash(request.getJSONObject("target").getString("base_sha256"));
            EditorProtocol.target(request.getJSONObject("target").getString("kind"),request.getString("mode"),request.getString("save_target"));
            EditorProtocol.require(request.getString("correlation_token").length()>=32,"Invalid correlation token");
            EditorProtocol.require("UTF-8".equals(request.getString("encoding")),"Unsupported encoding");
            EditorProtocol.require(EditorProtocol.DESK.equals(request.getString("expected_receiver_package"))&&EditorProtocol.DESK_CERT.equals(request.getString("expected_receiver_certificate_sha256")),"Receiver identity mismatch");
            int limit=request.getInt("max_bytes");EditorProtocol.require(limit>0&&limit<=EditorProtocol.MAX_BYTES,"Invalid byte limit");
            Uri uri=getIntent().getData();
            EditorProtocol.require(uri!=null&&uri.toString().equals(request.getString("staging_uri"))&&uri.toString().equals(EditorProtocol.inputUri(id)),"Unexpected working-copy URI");
            editable="edit".equals(request.getString("mode"));previewing=!editable;
            if(state!=null){previewing=state.getBoolean("preview",previewing);font=state.getFloat("font",18);full=state.getBoolean("full",false);}
            folder=new File(getFilesDir(),"suite-sessions/"+id);EditorProtocol.require(folder.mkdirs()||folder.isDirectory(),"Working directory unavailable");
            buildUi();
            io.execute(()->{
                try{
                    File meta=new File(folder,"request.json"),draft=new File(folder,"draft.md");byte[] bytes;
                    if(meta.isFile()){
                        JSONObject old=new JSONObject(new String(readFile(meta,8192),StandardCharsets.UTF_8));
                        EditorProtocol.resultIdentity(id,request.getString("correlation_token"),baseHash,old.getString("session_id"),old.getString("correlation_token"),old.getJSONObject("target").getString("base_sha256"));
                        sameRequest(old,request);
                    }else writeFile(meta,raw.getBytes(StandardCharsets.UTF_8));
                    if(editable&&draft.isFile())bytes=readFile(draft,limit);
                    else try(InputStream in=getContentResolver().openInputStream(uri)){
                        if(in==null)throw new IOException("Working-copy permission unavailable");bytes=EditorProtocol.read(in,limit);
                        EditorProtocol.require(baseHash.equals(EditorProtocol.sha256(bytes)),"Working-copy integrity failure");
                    }
                    String initial=EditorProtocol.decode(bytes);
                    runOnUiThread(()->{if(isFinishing()||isDestroyed())return;text=initial;if(editable)editor.setText(initial);loaded=true;showMode();status.setText(editable?"Working copy — Save and return sends a revision for review":"Read only — original source is unchanged");});
                }catch(Exception e){runOnUiThread(()->fail(e));}
            });
        }catch(Exception e){fail(e);}
    }
    private void buildUi(){
        boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        int fg=dark?Color.rgb(230,235,244):Color.rgb(27,32,40),bg=dark?Color.rgb(24,30,41):Color.WHITE;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setFitsSystemWindows(true);
        chrome=new LinearLayout(this);chrome.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView toolbar=new HorizontalScrollView(this);LinearLayout buttons=new LinearLayout(this);
        button(buttons,"Back",v->onBackPressed());
        if(editable){button(buttons,"Save and return",v->saveReturn());button(buttons,"Read / Edit",v->{previewing=!previewing;showMode();});}
        fullButton=new Button(this);fullButton.setMinHeight((int)(48*getResources().getDisplayMetrics().density));fullButton.setOnClickListener(v->{full=!full;applyFull();});buttons.addView(fullButton);
        button(buttons,"A−",v->{font=Math.max(12,font-2);showMode();});button(buttons,"A+",v->{font=Math.min(32,font+2);showMode();});
        toolbar.addView(buttons);chrome.addView(toolbar);
        status=new TextView(this);status.setTextColor(fg);status.setPadding(16,8,16,8);status.setText("Opening working copy…");chrome.addView(status);root.addView(chrome);
        editor=new EditText(this);editor.setTextColor(fg);editor.setBackgroundColor(Color.TRANSPARENT);editor.setGravity(Gravity.TOP);editor.setPadding(16,12,16,24);editor.setSingleLine(false);editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);editor.setHorizontallyScrolling(false);editor.setSaveEnabled(false);editor.setFreezesText(false);
        editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){text=s.toString();if(loaded&&editable&&!saving)scheduleDraft();}public void afterTextChanged(Editable e){}});
        preview=new WebView(this);preview.setSaveEnabled(false);preview.setBackgroundColor(bg);
        WebSettings settings=preview.getSettings();settings.setJavaScriptEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);settings.setBlockNetworkImage(true);settings.setDomStorageEnabled(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        preview.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return true;}@Override public boolean shouldOverrideUrlLoading(WebView w,String u){return true;}@Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}});
        root.addView(editor,new LinearLayout.LayoutParams(-1,0,1));root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);applyFull();showMode();
    }
    private void button(LinearLayout parent,String label,View.OnClickListener click){Button b=new Button(this);b.setText(label);b.setMinHeight((int)(48*getResources().getDisplayMetrics().density));b.setOnClickListener(click);parent.addView(b);}
    private void applyFull(){if(fullButton!=null)fullButton.setText(full?"Exit full screen":"Full screen");getWindow().getDecorView().setSystemUiVisibility(full?View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY:View.SYSTEM_UI_FLAG_VISIBLE);}
    private void showMode(){
        if(editor==null)return;editor.setTextSize(font);editor.setEnabled(editable&&!saving);editor.setVisibility(previewing?View.GONE:View.VISIBLE);preview.setVisibility(previewing?View.VISIBLE:View.GONE);
        if(previewing){
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);
            final int generation=++renderGeneration;final String snapshot=text;final float size=font;final boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
            renders.execute(()->{try{String html=MarkdownRenderer.render(snapshot,dark,size);runOnUiThread(()->{if(!isDestroyed()&&previewing&&generation==renderGeneration)preview.loadDataWithBaseURL(null,html,"text/html","UTF-8",null);});}catch(Exception e){runOnUiThread(()->status.setText("Preview unavailable; the working copy is retained"));}});
        }
    }
    private void scheduleDraft(){
        if(pendingDraft!=null)main.removeCallbacks(pendingDraft);
        pendingDraft=()->persistDraft(text);main.postDelayed(pendingDraft,500);
    }
    private void persistDraft(String snapshot){
        if(!loaded||!editable||saving)return;
        io.execute(()->{try{byte[] bytes=EditorProtocol.encode(snapshot);EditorProtocol.require(bytes.length<=request.getInt("max_bytes"),"Draft exceeds editor limit");writeFile(new File(folder,"draft.md"),bytes);}catch(Exception e){runOnUiThread(()->{if(status!=null)status.setText("Draft not saved: "+message(e));});}});
    }
    private void saveReturn(){
        if(!loaded||!editable||saving)return;saving=true;editor.setEnabled(false);if(pendingDraft!=null)main.removeCallbacks(pendingDraft);final String snapshot=text;status.setText("Saving working copy…");
        io.execute(()->{try{
            byte[] bytes=EditorProtocol.encode(snapshot);EditorProtocol.require(bytes.length<=request.getInt("max_bytes"),"Edited document exceeds the negotiated limit");
            writeFile(new File(folder,"draft.md"),bytes);writeFile(new File(folder,"result.md"),bytes);
            String hash=EditorProtocol.sha256(bytes);JSONObject result=result(baseHash.equals(hash)?"unchanged":"saved");
            Uri uri=Uri.parse(EditorProtocol.resultUri(request.getString("session_id")));
            result.put("result_uri",uri.toString()).put("result_sha256",hash).put("byte_count",bytes.length);
            writeFile(new File(folder,"result.json"),result.toString().getBytes(StandardCharsets.UTF_8));
            runOnUiThread(()->{if(isDestroyed()||isFinishing())return;Intent data=new Intent().setDataAndType(uri,"text/markdown").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(EditorProtocol.RESULT,result.toString());data.setClipData(ClipData.newRawUri("Edited working copy",uri));setResult(RESULT_OK,data);finish();});
        }catch(Exception e){runOnUiThread(()->{saving=false;editor.setEnabled(true);status.setText("Not returned: "+message(e));});}});
    }
    private JSONObject result(String state)throws JSONException{return new JSONObject().put("protocol_version",1).put("session_id",request.getString("session_id")).put("correlation_token",request.getString("correlation_token")).put("base_sha256",baseHash).put("status",state).put("completed_at",Instant.now().toString());}
    @Override public void onBackPressed(){
        if(full){full=false;applyFull();return;}
        if(!loaded||!editable){setResult(RESULT_CANCELED);finish();return;}
        if(saving)return;
        new AlertDialog.Builder(this).setTitle("Return to Mothership").setMessage("Save and return this working copy, or keep a local draft to resume from Mothership Tasks.").setPositiveButton("Save and return",(d,w)->saveReturn()).setNeutralButton("Keep draft and leave",(d,w)->{
            if(pendingDraft!=null)main.removeCallbacks(pendingDraft);final String snapshot=text;saving=true;
            io.execute(()->{try{byte[] bytes=EditorProtocol.encode(snapshot);EditorProtocol.require(bytes.length<=request.getInt("max_bytes"),"Draft exceeds editor limit");writeFile(new File(folder,"draft.md"),bytes);runOnUiThread(()->{if(isDestroyed()||isFinishing())return;setResult(RESULT_CANCELED);finish();});}catch(Exception e){runOnUiThread(()->{saving=false;status.setText("Could not keep draft: "+message(e));});}});
        }).setNegativeButton("Stay",null).show();
    }
    @Override protected void onPause(){if(loaded&&editable&&!saving){if(pendingDraft!=null)main.removeCallbacks(pendingDraft);persistDraft(text);}super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("preview",previewing);out.putFloat("font",font);out.putBoolean("full",full);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){if(pendingDraft!=null)main.removeCallbacks(pendingDraft);renderGeneration++;if(preview!=null)preview.destroy();renders.shutdownNow();synchronized(active){if(active.get(sessionId)==this)active.remove(sessionId);}super.onDestroy();}
    private void fail(Exception e){TextView error=new TextView(this);error.setPadding(24,48,24,24);error.setText("Markdown Desk could not open this session.\n\n"+message(e)+"\n\nYour original document is unchanged. Use Back to return.");setContentView(error);setResult(RESULT_CANCELED);}
    private static void sameRequest(JSONObject old,JSONObject current)throws JSONException{
        String[] fields={"protocol_version","session_id","correlation_token","project_id","branch_id","mode","save_target","staging_uri","mime_type","encoding","max_bytes","expected_receiver_package","expected_receiver_certificate_sha256","created_at"};
        for(String key:fields)EditorProtocol.require(old.get(key).equals(current.get(key)),"Session request changed: "+key);
        JSONObject a=old.getJSONObject("target"),b=current.getJSONObject("target");
        for(String key:new String[]{"kind","id","base_revision_id","base_sha256"})EditorProtocol.require(a.get(key).equals(b.get(key)),"Session target changed: "+key);
    }
    private static String message(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private static byte[] readFile(File file,int limit)throws IOException{try(InputStream in=new FileInputStream(file)){return EditorProtocol.read(in,limit);}}
    private static synchronized void writeFile(File dest,byte[] bytes)throws IOException{
        File tmp=new File(dest.getParentFile(),dest.getName()+".tmp");try{try(FileOutputStream out=new FileOutputStream(tmp)){out.write(bytes);out.getFD().sync();}AtomicCommit.replace(tmp,dest);}finally{tmp.delete();}
    }
}
