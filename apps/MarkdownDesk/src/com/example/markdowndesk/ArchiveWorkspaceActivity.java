package com.example.markdowndesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.markdowndesk.archive.ArchiveJournal;
import com.example.markdowndesk.archive.ArchiveMarkdownIndexes;
import com.example.markdowndesk.archive.ArchiveStreamParser;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only archive research workspace. Never opens original JSON for writing.
 * Shared palette, typography, preview Markdown renderer and font preferences.
 * Search is database-backed, and all user annotations have independent durable records.
 */
public final class ArchiveWorkspaceActivity extends Activity implements ArchiveImportCoordinator.Listener {
    private static final int REQUEST_IMPORT=8201, REQUEST_NOTES_TREE=8202, REQUEST_TRANSCRIPT=8203;
    private static final int WINDOW_MESSAGES=12, CHUNK_PARTS=20;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private ArchiveImportCoordinator importController;
    private TextView importSummary,importDetails,importFootnote;
    private ProgressBar importProgress;
    private Handler importHandler=new Handler(Looper.getMainLooper());
    private Runnable importTicker;
    private boolean importDoneNotified;

    private ArchiveDatabase database;
    private ArchiveJournal journal;
    private UiTheme.Palette palette;
    private ReaderTypography typography;
    private float fontSp;
    private LinearLayout screen, controls, pane;
    private TextView title, subtitle;
    private PreviewWebView reader;
    private ScrollView listing;
    private ArchiveDatabase.Conversation active;
    private int windowOffset, chunkOffset, focusOrdinal=-1, savedScroll, generation;
    private boolean fullscreen, chatLayout;
    private String pendingExportChat, query="";
    private Uri notesTree;
    private String viewMode="home";
    private ArrayList<ArchiveDatabase.Message> visibleMessages=new ArrayList<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("markdown_desk",MODE_PRIVATE);
        journal=new ArchiveJournal(new java.io.File(getFilesDir(),"claude-archive/annotations-v2.journal"));
        database=new ArchiveDatabase(this,"session");
        reloadAppearance();
        String tree=prefs.getString("archive_notes_tree",null);
        if(tree!=null)try{notesTree=Uri.parse(tree);}catch(RuntimeException ignored){}
        if(state!=null){
            windowOffset=Math.max(0,state.getInt("offset",0));
            chunkOffset=Math.max(0,state.getInt("chunk",0));
            focusOrdinal=state.getInt("focused",-1);
            savedScroll=state.getInt("scroll",0);
            fullscreen=state.getBoolean("focus",false);
            chatLayout=state.getBoolean("bubbles",false);
            viewMode=state.getString("view","home");
            String chat=state.getString("chat");
            if(chat!=null)active=findConversation(chat);
        }
        buildChrome();
        importController=ArchiveImportCoordinator.get(getApplicationContext());
        importController.attach(this);
        if(importController.snapshot().running()){
            showImportScreen();return;
        }
        if("interrupted".equals(importController.snapshot().state)){
            showInterruptedImport();return;
        }
        if(!handleDeepLink(getIntent())){
            if(active!=null && "reader".equals(viewMode))showReader(false);
            else showHome();
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);
        state.putString("chat",active==null?null:active.id);
        state.putInt("offset",windowOffset);state.putInt("chunk",chunkOffset);
        state.putInt("focused",focusOrdinal);
        state.putInt("scroll",reader==null?savedScroll:reader.getScrollY());
        state.putBoolean("focus",fullscreen);state.putBoolean("bubbles",chatLayout);
        state.putString("view",viewMode);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void reloadAppearance(){
        int theme=prefs.getInt("ui_theme",0);
        palette=UiTheme.palette(theme,prefs.getBoolean("dark_theme",false));
        typography=ReaderTypography.fromValues(prefs.getString("reader_font_family",null),
                prefs.getFloat("reader_line_height",Float.NaN),
                prefs.getFloat("reader_paragraph_gap",Float.NaN),
                prefs.getInt("reader_margin_px",0),prefs.getInt("reader_measure_px",0));
        fontSp=prefs.getFloat("preview_font_size",18f);
        fontSp=Math.max(12f,Math.min(32f,fontSp));
    }
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private TextView text(String s,int sp){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);
        t.setTextColor(palette.text);t.setPadding(dp(8),dp(7),dp(8),dp(7));return t;
    }
    private Button button(String label,View.OnClickListener tap){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(13);
        b.setTextColor(UiTheme.readableForeground(palette.text,palette.surface));
        b.setBackground(UiTheme.rounded(palette.raised,dp(8),palette.border,dp(1)));
        b.setMinHeight(dp(48));b.setPadding(dp(12),dp(7),dp(12),dp(7));b.setOnClickListener(tap);
        return b;
    }
    private void add(LinearLayout target,String label,View.OnClickListener tap){
        target.addView(button(label,tap),new LinearLayout.LayoutParams(-1,-2));
    }
    private void buildChrome(){
        getWindow().setStatusBarColor(palette.surface);
        getWindow().setNavigationBarColor(palette.surface);
        screen=vertical();screen.setBackgroundColor(palette.canvas);
        // Let Android calculate display-cutout, status and navigation insets.
        // Do not use the old code32 custom decorFits/insets listener: it crashed
        // this app on the owner's Fold7. fitsSystemWindows works at both widths.
        screen.setFitsSystemWindows(true);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(palette.surface);
        header.addView(button("‹",v->back()),new LinearLayout.LayoutParams(dp(52),dp(48)));
        LinearLayout headings=vertical();
        title=text("Claude Archive",18);title.setTypeface(null,Typeface.BOLD);headings.addView(title);
        subtitle=text("Read · Search · Link",11);subtitle.setTextColor(palette.muted);headings.addView(subtitle);
        header.addView(headings,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(button("⋮",v->moreMenu()),new LinearLayout.LayoutParams(dp(55),dp(48)));
        screen.addView(header,new LinearLayout.LayoutParams(-1,-2));
        HorizontalScrollView nav=new HorizontalScrollView(this);nav.setHorizontalScrollBarEnabled(false);
        controls=new LinearLayout(this);controls.setPadding(dp(6),dp(4),dp(6),dp(4));
        nav.addView(controls);screen.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        pane=vertical();screen.addView(pane,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(screen);
        applyFocusChrome();
    }
    private void action(String label,View.OnClickListener tap){
        controls.addView(button(label,tap),new LinearLayout.LayoutParams(-2,dp(48)));
    }
    private void commonActions(){
        controls.removeAllViews();
        action("Library",v->showHome());action("Search",v->searchDialog(null));
        action("Saved links",v->showNotebook(true));action("Import",v->chooseImport());
        action("Reading style",v->appearanceDialog());
    }
    private void setTitle(String s,String sub){title.setText(s);subtitle.setText(sub);}
    private void view(View child){pane.removeAllViews();pane.addView(child,new LinearLayout.LayoutParams(-1,-1));}
    private void status(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void fail(String label,Exception ex){
        String message=ex==null?label:label+"\n"+safe(ex.getMessage());
        new AlertDialog.Builder(this).setTitle("Claude Archive").setMessage(message)
                .setPositiveButton("OK",null).show();
    }
    private static String safe(String s){return s==null?"Unavailable":(s.length()>400?s.substring(0,400):s);}

    private void showHome(){
        generation++;viewMode="home";active=null;reader=null;focusOrdinal=-1;chunkOffset=0;
        commonActions();setTitle("Claude Archive","All imported conversations");
        ScrollView scroll=new ScrollView(this);LinearLayout body=vertical();
        body.setPadding(dp(12),dp(8),dp(12),dp(20));scroll.addView(body);
        add(body,"Import Claude conversations.json",v->chooseImport());
        add(body,"Continue reading",v->continueReading());
        TextView h=text("CONVERSATIONS",14);h.setTypeface(null,Typeface.BOLD);body.addView(h);
        List<ArchiveDatabase.Conversation> chats=database.conversations("",150,0);
        if(chats.isEmpty())body.addView(text("Import a Claude export to build a local, offline conversation library. The original file remains unchanged.",14));
        for(ArchiveDatabase.Conversation chat:chats){
            add(body,chat.name+"\n"+safe(chat.updated)+"  ·  "+chat.messages+" messages",v->openChat(chat));
        }
        add(body,"More conversations…",v->browsePage(150));
        add(body,"Annotations & bookmarks",v->showNotebook(false));
        view(scroll);
    }
    private void browsePage(int offset){
        generation++;viewMode="home";active=null;commonActions();
        setTitle("Conversation library","Results "+(offset+1)+" onward");
        ScrollView scroll=new ScrollView(this);LinearLayout body=vertical();scroll.addView(body);
        for(ArchiveDatabase.Conversation c:database.conversations("",150,offset))
            add(body,c.name+" · "+c.messages+" messages",v->openChat(c));
        add(body,"Next conversations",v->browsePage(offset+150));view(scroll);
    }
    private void continueReading(){
        String id=prefs.getString("archive_last_chat",null);
        ArchiveDatabase.Conversation c=id==null?null:findConversation(id);
        if(c==null){status("No saved reading location yet");return;}
        openChat(c);
    }
    private ArchiveDatabase.Conversation findConversation(String id){
        if(id==null)return null;
        // A bounded scan of indexed titles; for deep links prefer DB identity lookup.
        int offset=0;
        for(int round=0;round<200;round++){
            List<ArchiveDatabase.Conversation> batch=database.conversations("",150,offset);
            for(ArchiveDatabase.Conversation c:batch)if(c.id.equals(id))return c;
            if(batch.size()<150)break;
            offset+=150;
        }
        return null;
    }
    private void openChat(ArchiveDatabase.Conversation c){
        active=c;viewMode="reader";
        windowOffset=prefs.getInt("archive_chat_position_"+c.id,0);
        focusOrdinal=-1;chunkOffset=0;savedScroll=0;
        prefs.edit().putString("archive_last_chat",c.id).apply();
        showReader(false);
    }
    private void showReader(boolean keepScroll){
        if(active==null){showHome();return;}
        final int token=++generation;
        viewMode="reader";reloadAppearance();
        setTitle(active.name,active.messages+" messages · read-only");
        controls.removeAllViews();
        action("←",v->navigateWindow(-WINDOW_MESSAGES));
        action("→",v->navigateWindow(WINDOW_MESSAGES));
        action("A−",v->font(-1));action("A+",v->font(1));
        action("Find",v->searchDialog(active.id));
        action("Annotate",v->captureSelection());
        action("Mark",v->bookmarkCurrent());
        action("Notes",v->showNotebook(false));
        action("Jump",v->jumpDialog());
        action("Focus",v->toggleFocus());
        action("Layout",v->{chatLayout=!chatLayout;showReader(true);});
        action("Export .md",v->exportTranscript());
        action("Style",v->appearanceDialog());
        PreviewWebView web=new PreviewWebView(this);
        reader=web;web.setBackgroundColor(palette.canvas);
        web.setTextSp(fontSp);web.setOnTextScaleListener(size->{
            fontSp=size;prefs.edit().putFloat("preview_font_size",fontSp).apply();showReader(true);
        });
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);settings.setBlockNetworkLoads(true);
        settings.setLoadsImagesAutomatically(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
                Uri uri=request.getUrl();String scheme=uri.getScheme();
                if("mdarchive".equals(scheme)){localAction(uri);return true;}
                if("https".equals(scheme)||"http".equals(scheme)||"mailto".equals(scheme)){
                    try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}
                    catch(ActivityNotFoundException e){status("No browser available");}
                }
                return true;
            }
            @Override public void onPageFinished(WebView v,String url){
                if(token==generation&&v==reader && keepScroll)
                    v.scrollTo(0,savedScroll);
            }
        });
        if(keepScroll && savedScroll<=0 && reader!=null)savedScroll=reader.getScrollY();
        view(web);
        final int start=windowOffset;
        final int targetOrdinal=focusOrdinal, targetChunk=chunkOffset;
        io.execute(()->{
            try{
                List<ArchiveDatabase.Message> messages=database.messages(active.id,
                        targetOrdinal>=0?targetOrdinal:start,targetOrdinal>=0?1:WINDOW_MESSAGES);
                String html=renderMessages(messages,targetOrdinal,targetChunk);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||generation!=token)return;
                    visibleMessages=new ArrayList<>(messages);
                    web.loadDataWithBaseURL("https://markdown.local/",html,"text/html","UTF-8",null);
                });
            }catch(Exception e){runOnUiThread(()->{if(generation==token)fail("Unable to render conversation",e);});}
        });
    }
    private String renderMessages(List<ArchiveDatabase.Message> messages,int onlyOrdinal,int partOffset){
        // Reuse the exact Markdown renderer and CSS used by the Markdown reader.
        String shell=MarkdownRenderer.render("",palette,fontSp,typography);
        int body=shell.indexOf("<body");
        int start=body<0?-1:shell.indexOf('>',body);
        int end=shell.lastIndexOf("</body>");
        if(start<0||end<start)throw new IllegalStateException("Renderer produced unexpected HTML");
        StringBuilder html=new StringBuilder(shell.substring(0,start+1));
        html.append("<style>.archive-msg{padding:13px 10px;margin:9px 0;border-radius:12px;border:1px solid ")
                .append(css(palette.border)).append("}.archive-msg.chat{margin-left:18px}")
                .append(".archive-meta{font-size:.78em;opacity:.83;margin-bottom:9px}")
                .append(".archive-more{display:block;padding:14px;min-height:44px}</style>");
        if(messages.isEmpty())html.append("<p>No messages on this page.</p>");
        for(ArchiveDatabase.Message m:messages){
            int part=onlyOrdinal<0?0:partOffset;
            String content=database.textChunk(m.id,part,CHUNK_PARTS);
            boolean more=m.segments>part+CHUNK_PARTS;
            String rendered=MarkdownRenderer.render(content,palette,fontSp,typography);
            int b=rendered.indexOf("<body"),opening=b<0?-1:rendered.indexOf('>',b),closing=rendered.lastIndexOf("</body>");
            if(opening<0||closing<opening)throw new IllegalStateException("Markdown fragment framing error");
            html.append("<section class=\"archive-msg ").append(chatLayout?"chat":"book")
                .append("\" data-message-id=\"").append(escape(m.messageId))
                .append("\" data-ordinal=\"").append(m.ordinal).append("\">")
                .append("<div class=\"archive-meta\">").append(escape(m.role))
                .append(" · ").append(escape(m.created))
                .append(" · #").append(m.ordinal+1).append("</div>")
                .append(rendered,opening+1,closing);
            if(more){
                html.append("<a class=\"archive-more\" href=\"mdarchive://continue?ordinal=")
                    .append(m.ordinal).append("&part=").append(part+CHUNK_PARTS)
                    .append("\">Continue full message (next section)</a>");
            }else if(part>0){
                html.append("<a href=\"mdarchive://return?ordinal=").append(m.ordinal)
                    .append("\">Back to message beginning</a>");
            }
            html.append("</section>");
        }
        if(onlyOrdinal>=0)html.append("<a class=\"archive-more\" href=\"mdarchive://return?ordinal=")
                .append(onlyOrdinal).append("\">Return to conversation</a>");
        else if(windowOffset+messages.size()<active.messages)html.append("<a class=\"archive-more\" href=\"mdarchive://next\">Next messages →</a>");
        if(windowOffset>0)html.append("<a class=\"archive-more\" href=\"mdarchive://previous\">← Previous messages</a>");
        html.append(shell.substring(end));
        return html.toString();
    }
    private static String css(int color){return String.format(Locale.ROOT,"#%06x",color&0xffffff);}
    private static String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private void localAction(Uri uri){
        String host=uri.getHost();
        if("next".equals(host))navigateWindow(WINDOW_MESSAGES);
        else if("previous".equals(host))navigateWindow(-WINDOW_MESSAGES);
        else if("continue".equals(host)){
            try{focusOrdinal=Integer.parseInt(uri.getQueryParameter("ordinal"));
                chunkOffset=Integer.parseInt(uri.getQueryParameter("part"));savedScroll=0;showReader(false);
            }catch(RuntimeException ignored){status("Message anchor unavailable");}
        }else if("return".equals(host)){
            focusOrdinal=-1;chunkOffset=0;showReader(false);
        }
    }
    private void navigateWindow(int step){
        if(active==null)return;
        windowOffset=Math.max(0,Math.min(Math.max(0,active.messages-1),windowOffset+step));
        windowOffset=(windowOffset/WINDOW_MESSAGES)*WINDOW_MESSAGES;
        focusOrdinal=-1;chunkOffset=0;savedScroll=0;
        prefs.edit().putInt("archive_chat_position_"+active.id,windowOffset).apply();
        showReader(false);
    }
    private void jumpDialog(){
        if(active==null)return;
        EditText input=new EditText(this);input.setSingleLine();input.setInputType(2);
        input.setHint("Message number, 1–"+active.messages);
        new AlertDialog.Builder(this).setTitle("Jump to message").setView(input)
            .setPositiveButton("Jump",(d,w)->{
                try{
                    int n=Integer.parseInt(input.getText().toString().trim());
                    if(n<1||n>active.messages){status("Number outside conversation");return;}
                    windowOffset=((n-1)/WINDOW_MESSAGES)*WINDOW_MESSAGES;
                    focusOrdinal=-1;chunkOffset=0;savedScroll=0;showReader(false);
                }catch(NumberFormatException bad){status("Enter a message number");}
            }).setNegativeButton("Cancel",null).show();
    }
    private void font(int change){
        savedScroll=reader==null?0:reader.getScrollY();
        fontSp=Math.max(12f,Math.min(32f,fontSp+change));
        prefs.edit().putFloat("preview_font_size",fontSp).apply();showReader(true);
    }
    private void toggleFocus(){
        savedScroll=reader==null?0:reader.getScrollY();fullscreen=!fullscreen;applyFocusChrome();
    }
    private void applyFocusChrome(){
        if(screen==null)return;
        if(fullscreen)getWindow().setFlags(1024,1024);
        else getWindow().clearFlags(1024);
        // Keep a small, recoverable toolbar on the phone. Hide it with existing Focus
        // only while reading; Android back/menu exit always remains reachable.
        controls.setVisibility(fullscreen&&"reader".equals(viewMode)?View.GONE:View.VISIBLE);
        if(fullscreen&&"reader".equals(viewMode)){
            screen.setOnClickListener(v->{});
        }
    }
    private void appearanceDialog(){
        LinearLayout options=vertical();options.setPadding(dp(18),dp(8),dp(18),dp(8));
        for(ReaderTypography.Family family:ReaderTypography.Family.values()){
            add(options,family.name().replace('_',' '),v->{
                typography=typography.withFamily(family);
                prefs.edit().putString("reader_font_family",family.name()).apply();
                if(active!=null)showReader(true);
            });
        }
        add(options,"Looser line spacing",v->{
            typography=typography.withLineHeight(Math.min(2.2f,typography.lineHeight+.1f));persistType();});
        add(options,"Tighter line spacing",v->{
            typography=typography.withLineHeight(Math.max(1.2f,typography.lineHeight-.1f));persistType();});
        add(options,"More paragraph spacing",v->{typography=typography.withParagraphGapEm(Math.min(1.5f,typography.paragraphGapEm+.1f));persistType();});
        add(options,"Less paragraph spacing",v->{typography=typography.withParagraphGapEm(Math.max(.5f,typography.paragraphGapEm-.1f));persistType();});
        add(options,"Wider reading measure",v->{typography=typography.withMeasurePx(Math.min(960,typography.measurePx+40));persistType();});
        add(options,"Narrower reading measure",v->{typography=typography.withMeasurePx(Math.max(480,typography.measurePx-40));persistType();});
        add(options,"Change global theme in Markdown Desk",v->{
            new AlertDialog.Builder(this).setTitle("Shared themes")
                .setItems(new String[]{"Paper","Midnight","Sepia","Forest","Bubblegum","Amethyst","Black Rose","Ocean","Arcade"},(d,which)->{
                    prefs.edit().putInt("ui_theme",which).apply();
                    savedScroll=reader==null?0:reader.getScrollY();reloadAppearance();buildChrome();
                    if(active!=null)showReader(true);else showHome();
                }).show();
        });
        ScrollView scroll=new ScrollView(this);scroll.addView(options);
        new AlertDialog.Builder(this).setTitle("Shared reader appearance")
            .setView(scroll).setPositiveButton("Done",null).show();
    }
    private void persistType(){
        prefs.edit().putFloat("reader_line_height",typography.lineHeight)
             .putFloat("reader_paragraph_gap",typography.paragraphGapEm)
             .putInt("reader_margin_px",typography.horizontalMarginPx)
             .putInt("reader_measure_px",typography.measurePx).apply();
        savedScroll=reader==null?0:reader.getScrollY();if(active!=null)showReader(true);
    }

    private void searchDialog(String scopedChat){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Exact phrase in indexed messages");
        input.setText(query);input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle(scopedChat==null?"Search all conversations":"Find in this conversation")
            .setView(input).setPositiveButton("Find",(d,w)->showSearch(input.getText().toString(),scopedChat,0))
            .setNegativeButton("Cancel",null).show();
    }
    private void showSearch(String term,String scope,int offset){
        query=term;viewMode="search";
        setTitle(scope==null?"Archive search":"Find in conversation",term);
        controls.removeAllViews();action("Search again",v->searchDialog(scope));
        action("Library",v->showHome());
        ScrollView scroll=new ScrollView(this);LinearLayout rows=vertical();scroll.addView(rows);view(scroll);
        final int token=++generation;
        io.execute(()->{
            try{
                List<ArchiveDatabase.Hit> hits=database.search(term,scope,60,offset);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||token!=generation)return;
                    if(hits.isEmpty())rows.addView(text("No matches in the indexed messages.",14));
                    for(ArchiveDatabase.Hit hit:hits){
                        add(rows,hit.chatTitle+" · "+safe(hit.created)+"\n"+safe(hit.excerpt),v->{
                            ArchiveDatabase.Conversation c=findConversation(hit.chatId);
                            if(c==null){status("Source chat unavailable");return;}
                            active=c;windowOffset=(hit.messageOrdinal/WINDOW_MESSAGES)*WINDOW_MESSAGES;
                            focusOrdinal=-1;chunkOffset=0;savedScroll=0;showReader(false);
                        });
                    }
                    if(hits.size()==60)add(rows,"More search results",v->showSearch(term,scope,offset+60));
                });
            }catch(Exception e){runOnUiThread(()->{if(generation==token)fail("Search failed",e);});}
        });
    }
    private void bookmarkCurrent(){
        if(active==null)return;
        ArchiveDatabase.Message m=database.messageAt(active.id,focusOrdinal>=0?focusOrdinal:windowOffset);
        if(m==null){status("No message to bookmark");return;}
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id=UUID.randomUUID().toString();a.archiveId=active.archiveId;
        a.chatId=active.id;a.chatTitle=active.name;a.messageId=m.messageId;
        a.messageDate=m.created;a.type="bookmark";
        a.label="Reading place · message "+(m.ordinal+1);
        a.createdAt=System.currentTimeMillis();a.updatedAt=a.createdAt;
        saveAnnotation(a);
    }
    private void captureSelection(){
        if(reader==null||active==null){status("Open a conversation first");return;}
        // Pull text and a stable message identity from the selected DOM passage.
        String js="(function(){var s=window.getSelection();if(!s||!s.rangeCount)return '';"
            +"var n=s.anchorNode;var p=n?(n.nodeType===1?n:n.parentElement):null;"
            +"var e=p&&p.closest('[data-message-id]');if(!e)return '';"
            +"var t=s.toString();var all=e.innerText||'';var i=all.indexOf(t);"
            +"return JSON.stringify({id:e.getAttribute('data-message-id'),quote:t,"
            +"start:i,end:i<0?-1:i+t.length,prefix:i<0?'':all.substring(Math.max(0,i-64),i),"
            +"suffix:i<0?'':all.substring(i+t.length,i+t.length+64)});})()";
        reader.evaluateJavascript(js,result->{
            try{
                Object decoded=new JSONTokener(result).nextValue();
                if(!(decoded instanceof String)||((String)decoded).isEmpty()){
                    status("Select a passage in the conversation first");return;
                }
                JSONObject selection=new JSONObject((String)decoded);
                String msg=selection.optString("id","");
                if(msg.isEmpty())throw new IllegalArgumentException("No message identity");
                annotationDialog(selection);
            }catch(Exception e){fail("Unable to capture selected passage",e);}
        });
    }
    private void annotationDialog(JSONObject selection){
        final String[] kinds={"Highlight","Note","Saved source link","Cross-conversation link","Attach external URL","Bookmark"};
        final String[] codes={"highlight","note","source_link","cross_link","external_link","bookmark"};
        new AlertDialog.Builder(this).setTitle("Save selected passage")
            .setItems(kinds,(dialog,index)->annotationDetails(codes[index],selection))
            .setNegativeButton("Cancel",null).show();
    }
    private void annotationDetails(String kind,JSONObject selection){
        LinearLayout layout=vertical();layout.setPadding(dp(20),dp(4),dp(20),dp(10));
        EditText label=new EditText(this);label.setHint("Label");layout.addView(label);
        EditText note=new EditText(this);note.setHint("Your annotation (optional)");
        note.setMinLines(2);layout.addView(note);
        EditText target=new EditText(this);
        target.setHint("Destination URL or archive link");
        if("cross_link".equals(kind)||"external_link".equals(kind))layout.addView(target);
        EditText tags=new EditText(this);tags.setHint("Tags separated by commas");layout.addView(tags);
        TextView quote=text("Selected: "+safe(selection.optString("quote","")),12);layout.addView(quote);
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Save "+kind.replace('_',' '))
            .setView(scroll).setPositiveButton("Save",null)
            .setNegativeButton("Cancel",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(("external_link".equals(kind)||"cross_link".equals(kind))&&target.getText().toString().trim().isEmpty()){
                target.setError("Enter a destination");return;
            }
            ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
            a.id=UUID.randomUUID().toString();a.archiveId=active.archiveId;
            a.chatId=active.id;a.chatTitle=active.name;
            a.messageId=selection.optString("id","");
            a.messageDate=dateOfVisibleMessage(a.messageId);
            a.quote=selection.optString("quote","");a.prefix=selection.optString("prefix","");
            a.suffix=selection.optString("suffix","");
            a.selectionStart=selection.optInt("start",-1);a.selectionEnd=selection.optInt("end",-1);
            a.type=kind;a.label=label.getText().toString().trim();
            a.note=note.getText().toString();a.target=target.getText().toString().trim();
            a.tags=tags.getText().toString().trim();a.createdAt=System.currentTimeMillis();a.updatedAt=a.createdAt;
            d.dismiss();saveAnnotation(a);
        }));
        d.show();
    }
    private String dateOfVisibleMessage(String id){
        for(ArchiveDatabase.Message m:visibleMessages)if(id.equals(m.messageId))return m.created;
        return null;
    }
    private void saveAnnotation(ArchiveJournal.Annotation annotation){
        io.execute(()->{
            try{ journal.save(annotation); }
            catch(Exception error){runOnUiThread(()->fail("Annotation was not saved",error));return;}
            if(notesTree==null){
                runOnUiThread(()->status("Annotation saved locally. Choose a notes folder to publish Markdown indexes."));
                return;
            }
            try{
                publishNotes(annotation.chatTitle,annotation.archiveId,annotation.chatId);
                runOnUiThread(()->status("Annotation saved and Markdown indexes updated"));
            }catch(Exception sync){
                runOnUiThread(()->fail("Annotation saved locally; Markdown index synchronization failed. You can retry safely.",sync));
            }
        });
    }
    private boolean syncAllIndexes() throws IOException {
        if(notesTree==null)return false;
        ArchiveJournal.ReadResult data=journal.load();
        if(data.truncatedTail)throw new IOException("Annotation journal has a partial last record; preserve it before publishing");
        new ArchiveSidecarPublisher(this,notesTree).publishAll(data.annotations);
        return true;
    }
    private boolean publishNotes(String title,String archiveId,String chatId)throws IOException{
        if(notesTree==null)return false;
        ArchiveJournal.ReadResult data=journal.load();
        if(data.truncatedTail)throw new IOException("Interrupted annotation journal. Preserve and repair before publishing.");
        try{new ArchiveSidecarPublisher(this,notesTree).publish(title,archiveId,chatId,data.annotations);}
        catch(RuntimeException permission){throw new IOException("Markdown indexes require a writable notes folder",permission);}
        return true;
    }
    private void showNotebook(boolean linksOnly){
        viewMode=linksOnly?"links":"notebook";
        controls.removeAllViews();action("Library",v->showHome());
        action("Master links",v->showNotebook(true));action("All notes",v->showNotebook(false));
        action("Choose notes folder",v->chooseNotesFolder());
        action("Copy master",v->copyMaster());
        setTitle(linksOnly?"Saved links":"Reading notebook",notesTree==null?"Indexes not yet connected":"Markdown indexes connected");
        ScrollView scroll=new ScrollView(this);LinearLayout rows=vertical();scroll.addView(rows);view(scroll);
        final int token=++generation;
        io.execute(()->{
            try{
                ArchiveJournal.ReadResult result=journal.load();
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed())return;
                    if(result.truncatedTail)rows.addView(text("A prior annotation write was interrupted; new writes paused to preserve evidence.",14));
                    for(ArchiveJournal.Annotation a:result.annotations){
                        if(linksOnly&&!a.isLink())continue;
                        add(rows,safe(a.chatTitle)+" · "+safe(a.messageDate)+"\n"+
                            safe(a.label)+" · "+safe(a.quote),v->showAnnotationActions(a));
                    }
                    if(result.annotations.isEmpty())rows.addView(text("No annotations yet.",14));
                });
            }catch(Exception e){runOnUiThread(()->{if(token==generation)fail("Notebook unavailable",e);});}
        });
    }
    private void showAnnotationActions(ArchiveJournal.Annotation a){
        new AlertDialog.Builder(this).setTitle(safe(a.label)).setItems(new String[]{
                "Jump to source","Copy Markdown link","Edit note","Delete annotation"},(d,index)->{
            if(index==0){
                ArchiveDatabase.Conversation c=findConversation(a.chatId);
                if(c==null){status("Conversation is not indexed; reimport source");return;}
                active=c;int position=database.findMessageOrdinal(a.chatId,a.messageId);
                windowOffset=Math.max(0,position/WINDOW_MESSAGES*WINDOW_MESSAGES);
                focusOrdinal=-1;chunkOffset=0;savedScroll=0;showReader(false);
            }else if(index==1){
                ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Archive link",ArchiveMarkdownIndexes.deepLink(a)));
                status("Link copied");
            }else if(index==2){
                EditText input=new EditText(this);input.setText(a.note);
                new AlertDialog.Builder(this).setTitle("Edit note").setView(input)
                    .setPositiveButton("Save",(x,which)->{
                        ArchiveJournal.Annotation changed=a.copy();
                        changed.note=input.getText().toString();changed.updatedAt=System.currentTimeMillis();
                        saveAnnotation(changed);
                    }).setNegativeButton("Cancel",null).show();
            }else if(index==3){
                new AlertDialog.Builder(this).setTitle("Delete this annotation?")
                    .setMessage("The original conversation is unaffected.")
                    .setPositiveButton("Delete",(x,w)->io.execute(()->{
                        try{journal.delete(a.id);publishNotes(a.chatTitle,a.archiveId,a.chatId);
                            runOnUiThread(()->showNotebook(false));}
                        catch(Exception e){runOnUiThread(()->fail("Could not delete annotation",e));}
                    })).setNegativeButton("Cancel",null).show();
            }
        }).show();
    }
    private void copyMaster(){
        io.execute(()->{
            try{
                String md=ArchiveMarkdownIndexes.master(journal.load().annotations);
                runOnUiThread(()->{
                    ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    c.setPrimaryClip(ClipData.newPlainText("Master saved links",md));status("Master links copied");
                });
            }catch(Exception e){runOnUiThread(()->fail("Could not copy master index",e));}
        });
    }
    private void chooseNotesFolder(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(intent,REQUEST_NOTES_TREE);}
        catch(ActivityNotFoundException missing){status("Android folder picker unavailable");}
    }
    private void chooseImport(){
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(picker,REQUEST_IMPORT);}
        catch(ActivityNotFoundException missing){status("Android file picker unavailable");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(!"content".equalsIgnoreCase(uri.getScheme())){status("Only Android document sources are supported");return;}
        if(request==REQUEST_IMPORT){
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            catch(SecurityException|IllegalArgumentException unavailable){status("Temporary file permission; archive cache remains available");}
            importArchive(uri);
        }else if(request==REQUEST_NOTES_TREE){
            try{getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                notesTree=uri;prefs.edit().putString("archive_notes_tree",uri.toString()).apply();
                status("Notes folder connected. New links and notes will publish Markdown indexes.");
                io.execute(()->{
                    try{syncAllIndexes();}
                    catch(Exception e){runOnUiThread(()->fail("Could not publish indexes",e));}
                });
            }catch(SecurityException denied){fail("Writable folder permission denied",denied);}
        }else if(request==REQUEST_TRANSCRIPT){
            String chat=pendingExportChat;pendingExportChat=null;
            if(chat!=null)writeTranscript(chat,uri);
        }
    }
    private void importArchive(Uri uri){
        if(importController==null)importController=ArchiveImportCoordinator.get(getApplicationContext());
        if(!importController.start(uri)){
            status("An import is already running, or the selected file cannot be opened");
            showImportScreen();return;
        }
        importDoneNotified=false;
        showImportScreen();
    }
    private void showInterruptedImport(){
        ++generation;viewMode="import";reader=null;active=null;
        controls.removeAllViews();
        setTitle("Import interrupted","Previous completed archive remains available");
        LinearLayout box=vertical();box.setPadding(dp(18),dp(18),dp(18),dp(18));
        box.addView(text("Android stopped the previous indexing process. The previous complete index is preserved. You can restart the same export safely.",16));
        add(box,"Restart import from beginning",v->{
            if(importController.retryInterrupted())showImportScreen();
            else status("Unable to reopen the previously selected file");
        });
        add(box,"Return to conversation library",v->showHome());
        view(box);
    }
    private void showImportScreen(){
        ++generation;viewMode="import";reader=null;active=null;
        controls.removeAllViews();
        setTitle("Importing Claude archive","Indexing offline · original unchanged");
        FrameLayout wrapper=new FrameLayout(this);
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=vertical();body.setPadding(dp(18),dp(18),dp(18),dp(22));
        importSummary=text("Preparing import…",17);
        importSummary.setTypeface(null,Typeface.BOLD);
        importDetails=text("Reading archive metadata",14);
        importProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        importProgress.setMax(1000);importProgress.setIndeterminate(true);
        importFootnote=text("Original JSON is read-only. Your last completed archive stays accessible if importing fails.",12);
        body.addView(importSummary);
        body.addView(importProgress,new LinearLayout.LayoutParams(-1,dp(12)));
        body.addView(importDetails);
        body.addView(importFootnote);
        add(body,"Cancel import safely",v->new AlertDialog.Builder(this)
                .setTitle("Cancel indexing?")
                .setMessage("The original JSON and previous completed index will be preserved. Unfinished staging records will be cleaned up.")
                .setPositiveButton("Cancel import",(d,w)->importController.cancel())
                .setNegativeButton("Continue indexing",null).show());
        add(body,"Return to library (indexing continues while app runs)",v->showHome());
        scroll.addView(body);
        int width=Math.max(dp(260),Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(740)));
        wrapper.addView(scroll,new FrameLayout.LayoutParams(width,-1,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        view(wrapper);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renderImportProgress(importController.snapshot());
        if(importTicker!=null)importHandler.removeCallbacks(importTicker);
        importTicker=()->{
            if(!isFinishing()&&!isDestroyed()&&"import".equals(viewMode)){
                renderImportProgress(importController.snapshot());
                importHandler.postDelayed(importTicker,5000);
            }
        };
        importHandler.postDelayed(importTicker,5000);
    }
    @Override public void onArchiveImportChanged(ArchiveImportCoordinator.Snapshot state){
        runOnUiThread(()->{
            if(isFinishing()||isDestroyed())return;
            if(state.running())getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if("import".equals(viewMode)){
                renderImportProgress(state);
                if(!state.running()&&!"interrupted".equals(state.state)&&!importDoneNotified){
                    importDoneNotified=true;
                    if("complete".equals(state.state)){
                        status(state.detail);showHome();
                    }else if("failed".equals(state.state)||"cancelled".equals(state.state)){
                        new AlertDialog.Builder(this).setTitle("Archive import "+state.state)
                            .setMessage(state.detail+(state.error==null?"":"\n\n"+state.error))
                            .setPositiveButton("Return to library",(d,w)->showHome())
                            .setNeutralButton("Retry",(d,w)->{
                                if(importController.retryInterrupted())showImportScreen();
                            }).show();
                    }
                }
            }
        });
    }
    private void renderImportProgress(ArchiveImportCoordinator.Snapshot state){
        if(importSummary==null||!"import".equals(viewMode))return;
        String stage="indexing".equals(state.state)?"Indexing Claude conversations":
                "cleaning".equals(state.state)?"Cleaning index generations":
                "publishing".equals(state.state)?"Publishing completed archive":
                "checking".equals(state.state)?"Checking free space":state.detail;
        importSummary.setText(stage);
        long mib=1024L*1024;
        String file=state.size>0?String.format(Locale.ROOT,"%,d / %,d MiB",state.bytes/mib,state.size/mib):
                String.format(Locale.ROOT,"%,d MiB read (total unknown)",state.bytes/mib);
        String available=state.free>=0?String.format(Locale.ROOT,"%,d MiB free",state.free/mib):"Free space unavailable";
        long inactive=Math.max(0,(System.currentTimeMillis()-state.lastChange)/1000);
        String stalled=state.running() && inactive>=30?"\nNo progress update for "+inactive+" seconds. Checking file provider/storage…":"";
        importDetails.setText(state.detail+"\n"+file+"\n"+
                state.conversations+" conversations · "+state.messages+" messages\n"+available+stalled);
        if(state.size>0){
            importProgress.setIndeterminate(false);
            importProgress.setProgress((int)Math.min(1000,state.bytes*1000/Math.max(1,state.size)));
        }else importProgress.setIndeterminate(true);
    }
    private void exportTranscript(){
        if(active==null)return;
        pendingExportChat=active.id;
        Intent create=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/markdown").putExtra(Intent.EXTRA_TITLE,
                ArchiveMarkdownIndexes.fileName(active.name,active.id));
        try{startActivityForResult(create,REQUEST_TRANSCRIPT);}
        catch(ActivityNotFoundException missing){pendingExportChat=null;status("Android save picker unavailable");}
    }
    private void writeTranscript(String chat,Uri destination){
        final ArchiveDatabase.Conversation conversation=findConversation(chat);
        if(conversation==null){status("Conversation no longer indexed");return;}
        io.execute(()->{
            try(OutputStream out=getContentResolver().openOutputStream(destination,"w")){
                if(out==null)throw new IOException("Selected provider cannot create Markdown file");
                write(out,"# "+conversation.name+"\n\nExported from read-only Claude Archive. Original JSON unchanged.\n\n");
                int index=0;
                for(;;){
                    List<ArchiveDatabase.Message> messages=database.messages(chat,index,50);
                    if(messages.isEmpty())break;
                    for(ArchiveDatabase.Message m:messages){
                        write(out,"## "+m.role+" · "+safe(m.created)+" · message "+(m.ordinal+1)+"\n\n");
                        for(int part=0;part<m.segments;part+=32)write(out,database.textChunk(m.id,part,32));
                        write(out,"\n\n");
                    }
                    index+=messages.size();
                }
                out.flush();runOnUiThread(()->status("Complete Markdown conversation exported"));
            }catch(Exception e){runOnUiThread(()->fail("Transcript export failed; do not rely on partial document",e));}
        });
    }
    private static void write(OutputStream output,String text)throws IOException{output.write(text.getBytes(StandardCharsets.UTF_8));}
    private void moreMenu(){
        new AlertDialog.Builder(this).setTitle("Archive reader")
            .setItems(new String[]{"Library","Search all","Master links","Choose Markdown index folder",
                    "Reader appearance","Full screen / Focus","Import Claude export","View raw JSON (existing explorer)"},(d,i)->{
                switch(i){
                    case 0:showHome();break;case 1:searchDialog(null);break;
                    case 2:showNotebook(true);break;case 3:chooseNotesFolder();break;
                    case 4:appearanceDialog();break;case 5:toggleFocus();break;
                    case 6:chooseImport();break;
                    case 7:startActivity(new Intent(this,JsonExplorerActivity.class));break;
                }
            }).show();
    }
    private boolean handleDeepLink(Intent intent){
        if(intent==null||intent.getData()==null)return false;
        Uri uri=intent.getData();
        if(!"markdowndesk".equals(uri.getScheme())||!"archive".equals(uri.getHost()))return false;
        List<String> segments=uri.getPathSegments();
        if(segments.size()<4||!"v1".equals(segments.get(0)))return false;
        String chat=segments.get(2),message=segments.get(3);
        ArchiveDatabase.Conversation c=findConversation(chat);
        if(c==null){status("Archived conversation unavailable. Reimport its source.");showHome();return true;}
        active=c;int ordinal=database.findMessageOrdinal(chat,message);
        windowOffset=Math.max(0,ordinal/WINDOW_MESSAGES*WINDOW_MESSAGES);
        focusOrdinal=-1;chunkOffset=0;showReader(false);return true;
    }
    private void back(){
        if("reader".equals(viewMode)&&focusOrdinal>=0){focusOrdinal=-1;chunkOffset=0;showReader(false);return;}
        if(!"home".equals(viewMode)){showHome();return;}
        finish();
    }
    @Override public void onBackPressed(){back();}
    @Override protected void onDestroy(){
        if(importTicker!=null)importHandler.removeCallbacks(importTicker);
        if(importController!=null)importController.detach(this);
        generation++;io.shutdownNow();
        if(database!=null)database.close();
        super.onDestroy();
    }
}