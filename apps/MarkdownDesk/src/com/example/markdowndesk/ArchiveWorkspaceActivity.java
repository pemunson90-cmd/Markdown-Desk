package com.example.markdowndesk;

import android.app.Activity;
import com.example.markdowndesk.workspace.search.UnifiedSearchJump;
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
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.markdowndesk.archive.ArchiveJournal;
import com.example.markdowndesk.workspace.archive.ArchiveNotebookPane;
import com.example.markdowndesk.workspace.archive.ConversationTabs;
import com.example.markdowndesk.workspace.archive.ArchiveAnnotationSearch;
import com.example.markdowndesk.workspace.archive.ArchiveCitations;
import com.example.markdowndesk.workspace.archive.ArchiveScrollScript;
import com.example.markdowndesk.workspace.archive.ArchiveHighlightScript;
import com.example.markdowndesk.workspace.annotations.AnnotationTone;
import com.example.markdowndesk.workspace.archive.ArchiveSearchDialog;
import com.example.markdowndesk.workspace.archive.ArchiveSearchSpec;
import com.example.markdowndesk.workspace.archive.ArchiveSearchPassage;
import com.example.markdowndesk.workspace.archive.ArchiveViewport;
import com.example.markdowndesk.workspace.WorkspaceRouter;
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
    private static final int REQUEST_IMPORT=8201, REQUEST_NOTES_TREE=8202, REQUEST_TRANSCRIPT=8203, REQUEST_TRANSCRIPT_PDF=8204;
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
    private LinearLayout screen, controls, pane, readerHeader,conversationTabsRow;
    private HorizontalScrollView conversationTabsScroll;
    private ConversationTabs conversationTabs;
    private HorizontalScrollView readerNavigation;
    private String pendingFindTerm; // Saved annotation links retain literal Find.
    private ArchiveDatabase.Hit indexedPassage;
    private boolean jumpToIndexedPassage, indexedReaderVisit;
    private ArchiveSearchSpec currentSearch;
    // A reader reached through search retains its exact result page and filters.
    private String returnSearchTerm, returnSearchScope;
    private int returnSearchOffset;
    private final ArrayList<String> priorArchiveChats=new ArrayList<>();
    private boolean returnUnifiedSearch, openedFromJsonInspector;
    private String inspectorImportSource;
    private int importedSourcePage;
    private boolean importedSourceList;
    private TextView title, subtitle;
    private PreviewWebView reader;
    private ScrollView listing;
    private ArchiveDatabase.Conversation active;
    private int windowOffset, chunkOffset, focusOrdinal=-1, savedScroll;
    private volatile int generation;
    private int anchorOrdinal=-1,anchorPart,anchorY;
    private long lastAnchorCapture;
    private volatile String savedSelectionJson;
    private boolean incomingNavigationConsumed;
    private boolean fullscreen, chatLayout, restoreLastPosition;
    /** Shared workspace mode contract: Read(0), Inspect(1), Split(2). */
    private int archiveDisplayMode;
    static final String EXTRA_INDEXED_PASSAGE="archive_indexed_passage";
    private String pendingExportChat, query="";
    private Uri notesTree;
    private String viewMode="home";
    private ArrayList<ArchiveDatabase.Message> visibleMessages=new ArrayList<>();
    // The viewport caps DOM nodes, NOT the number of accessible messages.
    private ArchiveViewport viewport;
    private String viewportChatId;
    private boolean adjacentLoading, chunkLoading;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("markdown_desk",MODE_PRIVATE);
        journal=new ArchiveJournal(new java.io.File(getFilesDir(),"claude-archive/annotations-v2.journal"));
        database=new ArchiveDatabase(this,"session");
        conversationTabs=new ConversationTabs(this);
        reloadAppearance();
        archiveDisplayMode=Math.max(0,Math.min(2,prefs.getInt("archive_display_mode",0)));
        String tree=prefs.getString("archive_notes_tree",null);
        if(tree!=null)try{notesTree=Uri.parse(tree);}catch(RuntimeException ignored){}
        if(state!=null){
            incomingNavigationConsumed=state.getBoolean("incoming_navigation_consumed",false);
            savedSelectionJson=state.getString("archive_selection");
            anchorOrdinal=state.getInt("anchor_ordinal",-1);
            anchorPart=Math.max(0,state.getInt("anchor_part",0));
            anchorY=state.getInt("anchor_y",0);
            pendingExportChat=state.getString("pending_export_chat");
            pendingFindTerm=state.getString("pending_find_term");
            indexedPassage=readIndexedPassage(state.getBundle(EXTRA_INDEXED_PASSAGE));
            jumpToIndexedPassage=state.getBoolean("jump_to_indexed_passage");
            indexedReaderVisit=state.getBoolean("indexed_reader_visit");
            windowOffset=Math.max(0,state.getInt("offset",0));
            chunkOffset=Math.max(0,state.getInt("chunk",0));
            focusOrdinal=state.getInt("focused",-1);
            savedScroll=state.getInt("scroll",0);
            restoreLastPosition="reader".equals(state.getString("view","home"));
            fullscreen=state.getBoolean("focus",false);
            chatLayout=state.getBoolean("bubbles",false);
            archiveDisplayMode=Math.max(0,Math.min(2,state.getInt("archive_display_mode",archiveDisplayMode)));
            viewMode=state.getString("view","home");
            returnSearchTerm=state.getString("return_search_term");
            returnSearchScope=state.getString("return_search_scope");
            returnSearchOffset=Math.max(0,state.getInt("return_search_offset",0));
            ArrayList<String> previous=state.getStringArrayList("archive_prior_chat_ids");
            if(previous!=null)for(String id:previous)
                if(id!=null&&!id.isEmpty()&&id.length()<=4096){
                    if(priorArchiveChats.size()==16)priorArchiveChats.remove(0);
                    priorArchiveChats.add(id);
                }
            returnUnifiedSearch=state.getBoolean("return_unified_search",false);
            openedFromJsonInspector=state.getBoolean("opened_from_json_inspector",false);
            inspectorImportSource=state.getString("inspector_import_source");
            importedSourcePage=state.getInt("imported_source_page");
            importedSourceList=state.getBoolean("imported_source_list");
            String savedQuery=state.getString("search_spec_term");
            if(savedQuery!=null)try{
                currentSearch=new ArchiveSearchSpec(savedQuery,state.getString("search_spec_speaker"),
                        state.getString("search_spec_from"),state.getString("search_spec_to"));
            }catch(IllegalArgumentException invalid){
                returnSearchTerm=null;returnSearchScope=null;returnSearchOffset=0;
            }
            String chat=state.getString("chat");
            if(chat!=null)active=findConversation(chat);
            if(active!=null&&!active.generationId.equals(state.getString("anchor_generation"))){anchorOrdinal=-1;savedSelectionJson=null;}
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
        if(incomingNavigationConsumed || (!handleIncomingInspectorImport(getIntent())
                && !handleIncomingIndexedPassage(getIntent()) && !handleDeepLink(getIntent())
                && !openUnifiedTab(getIntent()))){
            if(getIntent()!=null && getIntent().getBooleanExtra(WorkspaceRouter.EXTRA_SEARCH_ARCHIVE,false)){
                getIntent().removeExtra(WorkspaceRouter.EXTRA_SEARCH_ARCHIVE);
                searchDialog(null);return;
            }
            if(active!=null && "reader".equals(viewMode))showReader(false);
            else if("imported".equals(viewMode)&&inspectorImportSource!=null)
                showImportedSource(inspectorImportSource,importedSourcePage);
            else showHome();
        }
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        if(handleIncomingInspectorImport(intent)||handleIncomingIndexedPassage(intent))return;
        if(openUnifiedTab(intent))return;
        if(intent!=null && intent.getBooleanExtra(WorkspaceRouter.EXTRA_SEARCH_ARCHIVE,false)){
            intent.removeExtra(WorkspaceRouter.EXTRA_SEARCH_ARCHIVE);
            searchDialog(null);
        }else handleDeepLink(intent);
    }
    /** Logical tab switch requests preserve the original Archive session IDs. */
    private boolean openUnifiedTab(Intent intent){
        if(intent==null||!intent.hasExtra(UnifiedTabs.EXTRA_TAB_ID))return false;
        String id=intent.getStringExtra(UnifiedTabs.EXTRA_TAB_ID);
        intent.removeExtra(UnifiedTabs.EXTRA_TAB_ID);
        incomingNavigationConsumed=true;
        ArchiveDatabase.Conversation target=findConversation(id);
        if(target==null)status("This conversation is not indexed; reimport its source");
        else openChat(target);
        return true;
    }

    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);
        state.putBoolean("incoming_navigation_consumed",incomingNavigationConsumed);
        state.putString("archive_selection",savedSelectionJson);
        state.putString("anchor_generation",active==null?null:active.generationId);
        state.putInt("anchor_ordinal",anchorOrdinal);
        state.putInt("anchor_part",anchorPart);state.putInt("anchor_y",anchorY);
        state.putString("pending_export_chat",pendingExportChat);
        state.putString("pending_find_term",pendingFindTerm);
        if(indexedPassage!=null)state.putBundle(EXTRA_INDEXED_PASSAGE,indexedPassageBundle(indexedPassage));
        state.putBoolean("jump_to_indexed_passage",jumpToIndexedPassage);
        state.putBoolean("indexed_reader_visit",indexedReaderVisit);
        state.putString("chat",active==null?null:active.id);
        state.putInt("offset",windowOffset);state.putInt("chunk",chunkOffset);
        state.putInt("focused",focusOrdinal);
        state.putInt("scroll",reader==null?savedScroll:reader.getScrollY());
        state.putBoolean("focus",fullscreen);state.putBoolean("bubbles",chatLayout);
        state.putInt("archive_display_mode",archiveDisplayMode);
        state.putString("view",viewMode);
        state.putString("return_search_term",returnSearchTerm);
        state.putString("return_search_scope",returnSearchScope);
        state.putInt("return_search_offset",returnSearchOffset);
        state.putStringArrayList("archive_prior_chat_ids",new ArrayList<>(priorArchiveChats));
        state.putBoolean("return_unified_search",returnUnifiedSearch);
        state.putBoolean("opened_from_json_inspector",openedFromJsonInspector);
        state.putString("inspector_import_source",inspectorImportSource);
        state.putInt("imported_source_page",importedSourcePage);
        state.putBoolean("imported_source_list",importedSourceList);
        if(currentSearch!=null){
            state.putString("search_spec_term",currentSearch.term);
            state.putString("search_spec_speaker",currentSearch.speaker);
            state.putString("search_spec_from",currentSearch.fromDate);
            state.putString("search_spec_to",currentSearch.toDate);
        }
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
        // Symbol-only controls need meaningful TalkBack names on compact Fold widths.
        String spoken=label;
        switch(label){
            case "‹":spoken="Back to previous Archive screen";break;
            case "⋮":spoken="More Archive actions";break;
            case "←":spoken="Previous conversation messages";break;
            case "→":spoken="Next conversation messages";break;
            case "A−":spoken="Decrease reading text size";break;
            case "A+":spoken="Increase reading text size";break;
            case "Mark":spoken="Bookmark current message";break;
            case "Style":spoken="Reader appearance settings";break;
        }
        b.setContentDescription(spoken);
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
        LinearLayout header=new LinearLayout(this);readerHeader=header;
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(palette.surface);
        header.addView(button("‹",v->back()),new LinearLayout.LayoutParams(dp(52),dp(48)));
        LinearLayout headings=vertical();
        title=text("Conversation Archive",18);title.setTypeface(null,Typeface.BOLD);headings.addView(title);
        subtitle=text("Read · Search · Link",11);subtitle.setTextColor(palette.muted);headings.addView(subtitle);
        header.addView(headings,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(button("⋮",v->moreMenu()),new LinearLayout.LayoutParams(dp(55),dp(48)));
        screen.addView(header,new LinearLayout.LayoutParams(-1,-2));
        conversationTabsScroll=new HorizontalScrollView(this);
        conversationTabsScroll.setHorizontalScrollBarEnabled(false);
        conversationTabsRow=new LinearLayout(this);
        conversationTabsRow.setPadding(dp(7),dp(3),dp(7),dp(3));
        conversationTabsScroll.addView(conversationTabsRow);
        screen.addView(conversationTabsScroll,new LinearLayout.LayoutParams(-1,-2));
        refreshConversationTabs();
        HorizontalScrollView nav=new HorizontalScrollView(this);readerNavigation=nav;
        nav.setHorizontalScrollBarEnabled(false);
        controls=new LinearLayout(this);controls.setPadding(dp(6),dp(4),dp(6),dp(4));
        nav.addView(controls);screen.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        pane=vertical();screen.addView(pane,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(screen);
        applyFocusChrome();
    }
    private void action(String label,View.OnClickListener tap){
        controls.addView(button(label,tap),new LinearLayout.LayoutParams(-2,dp(48)));
    }
    private void refreshConversationTabs(){
        if(conversationTabs==null)return;
        UnifiedTabs.updateArchive(this,conversationTabs.entries(),conversationTabs.selected());
        if(conversationTabsRow==null)return;
        conversationTabsRow.removeAllViews();
        List<ConversationTabs.Entry> items=conversationTabs.entries();
        conversationTabsScroll.setVisibility(items.isEmpty()?View.GONE:View.VISIBLE);
        for(ConversationTabs.Entry item:items){
            boolean selected=item.id.equals(conversationTabs.selected());
            String label=(selected?"● ":"○ ")+item.title;
            if(label.length()>26)label=label.substring(0,25)+"…";
            Button tab=button(label,v->{
                ArchiveDatabase.Conversation c=findConversation(item.id);
                if(c!=null)openChat(c);
                else status("This conversation needs to be reindexed");
            });
            tab.setContentDescription("Open archive tab: "+item.title+". Long press to close.");
            tab.setOnLongClickListener(v->{
                new AlertDialog.Builder(this).setTitle("Close archive tab?")
                    .setMessage(item.title+"\nYour notes and reading position are preserved.")
                    .setPositiveButton("Close tab",(d,which)->{
                        conversationTabs.close(item.id);
                        if(active!=null&&item.id.equals(active.id)){
                            ArchiveDatabase.Conversation next=findConversation(conversationTabs.selected());
                            if(next!=null)openChat(next);else showHome();
                        }else refreshConversationTabs();
                    }).setNegativeButton("Keep",null).show();
                return true;
            });
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dp(46));
            params.rightMargin=dp(6);
            conversationTabsRow.addView(tab,params);
        }
    }
    private void commonActions(){
        controls.removeAllViews();
        action("Library",v->showHome());
        action("Search indexed conversations",v->startActivity(new Intent(this,UnifiedSearchActivity.class)
                .putExtra(UnifiedSearchActivity.EXTRA_ARCHIVE_SCOPE,true)
                .putExtra(UnifiedSearchActivity.EXTRA_RETURN_TO_JSON,openedFromJsonInspector)));
        action("All tabs",v->UnifiedTabs.show(this));
        action("Saved links",v->showNotebook(true));action("Import",v->chooseImport());
        action("Reading style",v->appearanceDialog());
        action("Inspect JSON",v->startActivity(new Intent(this,JsonExplorerActivity.class)));
    }
    private void setTitle(String s,String sub){title.setText(s);subtitle.setText(sub);}
    private void view(View child){pane.removeAllViews();pane.addView(child,new LinearLayout.LayoutParams(-1,-1));}
    private void status(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void fail(String label,Exception ex){
        String message=ex==null?label:label+"\n"+safe(ex.getMessage());
        new AlertDialog.Builder(this).setTitle("Conversation Archive").setMessage(message)
                .setPositiveButton("OK",null).show();
    }
    private static String safe(String s){return s==null?"Unavailable":(s.length()>400?s.substring(0,400):s);}

    private void showHome(){
        rememberReadingPlace();
        priorArchiveChats.clear();
        returnSearchTerm=null;returnSearchScope=null;returnSearchOffset=0;returnUnifiedSearch=false;
        clearIndexedPassage();
        generation++;viewMode="home";active=null;reader=null;pendingFindTerm=null;focusOrdinal=-1;chunkOffset=0;
        applyFocusChrome();
        commonActions();refreshConversationTabs();setTitle("Conversation Archive","All imported conversations");
        ScrollView scroll=new ScrollView(this);LinearLayout body=vertical();
        body.setPadding(dp(12),dp(8),dp(12),dp(20));scroll.addView(body);
        add(body,"Import LLM JSON archive",v->chooseImport());
        add(body,"Continue reading",v->continueReading());
        TextView h=text("CONVERSATIONS",14);h.setTypeface(null,Typeface.BOLD);body.addView(h);
        List<ArchiveDatabase.Conversation> chats=database.conversations("",150,0);
        if(chats.isEmpty())body.addView(text("Import Claude, linear messages, or ChatGPT JSON. ChatGPT branches are kept as separate conversations, selected branch first. Other JSON opens in Inspector. The original file remains unchanged.",14));
        for(ArchiveDatabase.Conversation chat:chats){
            add(body,chat.name+"\n"+safe(chat.updated)+"  ·  "+chat.messages+" messages",v->openChat(chat));
        }
        add(body,"More conversations…",v->browsePage(150));
        add(body,"Annotations & bookmarks",v->showNotebook(false));
        view(scroll);
    }
    private void browsePage(int offset){
        clearIndexedPassage();
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
        return database.conversation(id);
    }
    private void openChat(ArchiveDatabase.Conversation c){
        clearIndexedPassage();
        returnSearchTerm=null;returnSearchScope=null;returnSearchOffset=0;returnUnifiedSearch=false;
        savedSelectionJson=null;
        active=c;viewMode="reader";pendingFindTerm=null;viewport=null;viewportChatId=null;
        windowOffset=prefs.getInt("archive_chat_position_"+c.id,0);
        focusOrdinal=-1;chunkOffset=0;
        anchorOrdinal=prefs.getInt("archive_chat_anchor_ordinal_"+c.id,-1);
        anchorPart=Math.max(0,prefs.getInt("archive_chat_anchor_part_"+c.id,0));
        anchorY=prefs.getInt("archive_chat_anchor_y_"+c.id,0);
        if(!c.generationId.equals(prefs.getString("archive_chat_anchor_generation_"+c.id,null)))anchorOrdinal=-1;
        savedScroll=prefs.getInt("archive_chat_scroll_"+c.id,0);
        restoreLastPosition=true;
        prefs.edit().putString("archive_last_chat",c.id).apply();
        showReader(false);
    }
    private void showReader(boolean keepScroll){
        if(active==null){showHome();return;}
        if(indexedPassage!=null){
            if(!active.id.equals(indexedPassage.chatId)
                    ||!active.generationId.equals(indexedPassage.generationId)){
                indexedPassage=null;jumpToIndexedPassage=false;
                status("The indexed source changed. Search again to open the passage.");
            }else if(jumpToIndexedPassage||((keepScroll||restoreLastPosition)&&anchorOrdinal<0)){
                focusOrdinal=indexedPassage.messageOrdinal;
                chunkOffset=Math.max(0,indexedPassage.partOrdinal);
                if(jumpToIndexedPassage)restoreLastPosition=false;
            }
        }
        final ArchiveDatabase.Hit passage=indexedPassage;
        final boolean jumpToPassage=jumpToIndexedPassage;
        if(!conversationTabs.open(active.id,active.name)){
            status("Archive has 12 open tabs. Close one to open another.");
            showHome();return;
        }
        refreshConversationTabs();
        if((keepScroll||restoreLastPosition)&&savedSelectionJson!=null){
            try{
                JSONObject selected=new JSONObject(savedSelectionJson);
                int selectedOrdinal=database.findMessageOrdinal(active.id,selected.optString("id",""));
                int selectedPart=selected.optInt("sourcePart",-1);
                // Ensure a selection near the end of a trimmed 36-message DOM
                // is present in the new bounded window before restoring its range.
                if(selectedOrdinal>=0&&selectedPart>=0){
                    anchorOrdinal=selectedOrdinal;anchorPart=Math.max(0,selectedPart-1);anchorY=0;
                }
            }catch(Exception invalid){savedSelectionJson=null;}
        }
        final boolean restoreAnchor=(keepScroll||restoreLastPosition)&&anchorOrdinal>=0&&anchorOrdinal<active.messages;
        final int restoredOrdinal=anchorOrdinal,restoredPart=anchorPart,restoredY=anchorY;
        if(restoreAnchor){focusOrdinal=anchorOrdinal;chunkOffset=anchorPart;}
        else if(!keepScroll&&!restoreLastPosition){anchorOrdinal=-1;savedSelectionJson=null;}
        final int token=++generation;
        adjacentLoading=false;chunkLoading=false;
        if(viewport==null||!active.id.equals(viewportChatId)||viewport.total()!=active.messages
                ||(focusOrdinal>=0&&(focusOrdinal<viewport.start()||focusOrdinal>=viewport.end()))){
            viewport=new ArchiveViewport(active.messages,
                    focusOrdinal>=0?focusOrdinal:windowOffset);
            viewportChatId=active.id;
        }
        if(keepScroll && reader!=null)savedScroll=reader.getScrollY();
        final boolean restorePosition=keepScroll||restoreLastPosition;
        final int targetScroll=Math.max(0,savedScroll);
        final int navigationOrdinal=focusOrdinal;
        restoreLastPosition=false;
        viewMode="reader";reloadAppearance();applyFocusChrome();
        setTitle(active.name,active.messages+" messages · read-only");
        controls.removeAllViews();
        // Same semantic three-mode row as Markdown Desk's Write / Read / Split.
        // Inspect deliberately keeps source JSON immutable and opens the raw tree.
        action(archiveDisplayMode==0?"● Read":"Read",v->setArchiveDisplayMode(0));
        action("Inspect",v->setArchiveDisplayMode(1));
        action(archiveDisplayMode==2?"● Split":"Split",v->setArchiveDisplayMode(2));
        action("←",v->navigateWindow(-WINDOW_MESSAGES));
        action("→",v->navigateWindow(WINDOW_MESSAGES));
        action("A−",v->font(-1));action("A+",v->font(1));
        action("Find",v->searchDialog(active.id));
        if(returnSearchTerm!=null)action("Results",v->returnToSearch());
        action("All tabs",v->UnifiedTabs.show(this));
        action("Copy",v->copySelectedPassage(false));
        action("Share",v->copySelectedPassage(true));
        action("Print quote",v->printSelectedPassage());
        action("Print transcript",v->printFullTranscript());
        action("Annotate",v->captureSelection());
        action("Mark",v->bookmarkCurrent());
        action("Notes",v->showNotebook(false));
        action("Backlinks",v->showIncomingArchiveLinks());
        action("Jump",v->jumpDialog());
        action("Focus",v->toggleFocus());
        action("Layout",v->{chatLayout=!chatLayout;showReader(true);});
        action("Export .md",v->exportTranscript());
        action("Export PDF",v->exportTranscriptPdf());
        action("Style",v->appearanceDialog());
        PreviewWebView web=new PreviewWebView(this);
        reader=web;web.setBackgroundColor(palette.canvas);
        web.addJavascriptInterface(new SelectionStateBridge(web,token),"ArchiveSelectionState");
        web.setTextSp(fontSp);web.setOnTextScaleListener(size->{
            fontSp=size;prefs.edit().putFloat("preview_font_size",fontSp).apply();showReader(true);
        });
        web.setOnScrollChangeListener((view,x,y,oldX,oldY)->{
            if(token==generation&&reader==web&&"reader".equals(viewMode)){
                savedScroll=Math.max(0,y);
                long now=android.os.SystemClock.uptimeMillis();
                if(now-lastAnchorCapture>=100){lastAnchorCapture=now;captureReadingAnchor(web,token);}
            }
        });
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);settings.setBlockNetworkLoads(true);
        settings.setLoadsImagesAutomatically(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
                Uri uri=request.getUrl();String scheme=uri.getScheme();
                if("mdarchive".equals(scheme)){localAction(uri);return true;}
                if(com.example.markdowndesk.workspace.WorkspaceDeepLinks
                        .isArchiveLink(uri.toString())){
                    handleDeepLink(new Intent(Intent.ACTION_VIEW,uri));
                    return true;
                }
                if(com.example.markdowndesk.workspace.WorkspaceDeepLinks
                        .isDocumentLink(uri.toString())){
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,uri)
                                .setClass(ArchiveWorkspaceActivity.this,MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    } catch(ActivityNotFoundException|SecurityException unavailable) {
                        status("Markdown document reader unavailable");
                    }
                    return true;
                }
                if(com.example.markdowndesk.workspace.WorkspaceDeepLinks
                        .isInspectorLink(uri.toString())){
                    try{
                        startActivity(new Intent(Intent.ACTION_VIEW,uri)
                                .setClass(ArchiveWorkspaceActivity.this,JsonExplorerActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    }catch(ActivityNotFoundException|SecurityException denied){
                        status("Saved JSON source unavailable; no file grant was created");
                    }
                    return true;
                }
                if("https".equals(scheme)||"http".equals(scheme)||"mailto".equals(scheme)){
                    try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}
                    catch(ActivityNotFoundException e){status("No browser available");}
                }
                return true;
            }
            @Override public void onPageFinished(WebView v,String url){
                if(token==generation&&v==reader){
                    if(restoreAnchor)v.evaluateJavascript(
                            ArchiveScrollScript.restorePosition(restoredOrdinal,restoredPart,restoredY),null);
                    else if(restorePosition)v.post(()->{
                        if(token==generation&&reader==v)v.scrollTo(0,targetScroll);
                    });
                    if(!restorePosition&&navigationOrdinal>=0)
                        v.evaluateJavascript("(function(){var e=document.querySelector('[data-ordinal=\""+
                                navigationOrdinal+"\"]');if(e)e.scrollIntoView();})()",null);
                    restoreReadingSelection(web,token);
                    refreshHighlights(web,new ArrayList<>(visibleMessages),token);
                    if(jumpToPassage&&passage==indexedPassage){
                        v.evaluateJavascript("(function(){var e=document.getElementById('"
                                +ArchiveSearchPassage.ANCHOR_ID+"');if(!e)return false;"
                                +"e.scrollIntoView({block:'start'});return true;})()",result->{
                            if(token==generation&&v==reader&&passage==indexedPassage){
                                jumpToIndexedPassage=false;
                                if(!"true".equals(result))status("Exact passage unavailable. Search again.");
                            }
                        });
                    }
                    if(pendingFindTerm!=null&&!pendingFindTerm.isEmpty()){
                        v.findAllAsync(pendingFindTerm);
                        pendingFindTerm=null;
                    }
                }
            }
        });
        if(archiveDisplayMode==2){
            // Fold7's actual window width determines split orientation, not model name.
            // The same notebook can be opened alone on compact screens.
            boolean expanded=getResources().getConfiguration().screenWidthDp>=650;
            LinearLayout split=new LinearLayout(this);
            split.setOrientation(expanded?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
            View notes=ArchiveNotebookPane.create(this,
                new java.io.File(getFilesDir(),"claude-archive/annotations-v2.journal"),
                active.id,palette.text,palette.muted,palette.canvas,io,this::showAnnotationActions);
            if(expanded){
                split.addView(web,new LinearLayout.LayoutParams(0,-1,2f));
                split.addView(notes,new LinearLayout.LayoutParams(0,-1,1f));
            }else{
                split.addView(web,new LinearLayout.LayoutParams(-1,0,3f));
                split.addView(notes,new LinearLayout.LayoutParams(-1,0,2f));
            }
            view(split);
        }else view(web);
        final String renderedChat=active.id,renderedArchive=active.archiveId;
        final String renderedGeneration=active.generationId;
        final ArchiveViewport initial=viewport;
        final int targetOrdinal=focusOrdinal, targetChunk=chunkOffset;
        io.execute(()->{
            try{
                database.requireExportGeneration(renderedArchive,renderedGeneration);
                int count=initial.end()-initial.start();
                List<ArchiveDatabase.Message> messages=database.messages(renderedChat,
                        initial.start(),count);
                if(messages.size()!=count)throw new IOException("Conversation message window is incomplete");
                for(int row=0;row<messages.size();row++)
                    ArchiveTranscriptIntegrity.message(initial.start()+row,
                            messages.get(row).ordinal,messages.get(row).segments);
                String passageHtml="";
                if(passage!=null&&passage.matchStart>=0&&passage.matchEnd>passage.matchStart){
                    try{passageHtml=renderIndexedPassage(passage);}
                    catch(IOException stale){ /* The message can still open without an exact mark. */ }
                }
                final boolean hasPassage=!passageHtml.isEmpty();
                String html=renderMessages(messages,targetOrdinal,targetChunk,passageHtml,
                        hasPassage?passage.messageOrdinal:-1);
                database.requireExportGeneration(renderedArchive,renderedGeneration);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||generation!=token||active==null
                            ||!renderedChat.equals(active.id)
                            ||!renderedGeneration.equals(active.generationId))return;
                    if(passage!=null&&!hasPassage){
                        indexedPassage=null;jumpToIndexedPassage=false;
                        status("Exact passage unavailable. Search again.");
                    }
                    visibleMessages=new ArrayList<>(messages);
                    focusOrdinal=-1;chunkOffset=0;
                    web.loadDataWithBaseURL("https://markdown.local/",html,"text/html","UTF-8",null);
                });
            }catch(Exception e){runOnUiThread(()->{
                if(generation!=token)return;
                if(passage!=null){
                    indexedPassage=null;jumpToIndexedPassage=false;
                    status("Unable to open the indexed passage. Search again.");
                }else fail("Unable to render conversation",e);
            });}
        });
    }
    private void setArchiveDisplayMode(int mode){
        if(active==null){showHome();return;}
        if(mode==1){
            String source=database.sourceUri(active.archiveId);
            if(source==null||source.trim().isEmpty()){
                status("Original JSON source is unavailable; reselect the archive for inspection");return;
            }
            try{
                Intent inspect=new Intent(this,JsonExplorerActivity.class);
                inspect.setData(Uri.parse(source));
                inspect.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(inspect);
            }catch(RuntimeException failure){fail("Unable to inspect JSON source",failure);}
            return;
        }
        if(mode!=0&&mode!=2)return;
        if(archiveDisplayMode==mode)return;
        archiveDisplayMode=mode;
        prefs.edit().putInt("archive_display_mode",mode).apply();
        savedScroll=0;
        showReader(false);
    }
    private String renderIndexedPassage(ArchiveDatabase.Hit hit)throws IOException{
        int start=hit.matchStart,end=hit.matchEnd;
        if(end-start<=ArchiveSearchPassage.MAX_DISPLAY_UNITS)
            return ArchiveSearchPassage.render(database.searchPassage(hit,start,end),null);
        int half=ArchiveSearchPassage.MAX_DISPLAY_UNITS/2;
        return ArchiveSearchPassage.render(database.searchPassage(hit,start,start+half),
                database.searchPassage(hit,end-half,end));
    }
    private String renderMessages(List<ArchiveDatabase.Message> messages,int onlyOrdinal,int partOffset,
            String passageHtml,int passageOrdinal)throws IOException{
        // The shared renderer provides exactly the same CSS/Markdown as normal Read.
        String shell=MarkdownRenderer.render("",palette,fontSp,typography);
        int body=shell.indexOf("<body");
        int start=body<0?-1:shell.indexOf('>',body);
        int end=shell.lastIndexOf("</body>");
        if(start<0||end<start)throw new IllegalStateException("Renderer returned invalid HTML shell");
        boolean continuous=true; // Search and deep links use the same continuous reader.
        StringBuilder html=new StringBuilder(shell.substring(0,start+1));
        html.append("<style>.archive-msg{padding:13px 10px;margin:9px 0;border-radius:12px;border:1px solid ")
                .append(css(palette.border)).append("}.archive-msg.chat{margin-left:18px}")
                .append(".archive-meta{font-size:.78em;opacity:.83;margin-bottom:9px}")
                .append(".archive-more{display:block;padding:14px;min-height:44px}")
                .append(".archive-sentinel{display:block;padding:16px;min-height:44px;text-align:center}")
                .append("mark.md-archive-highlight{background:").append(css(palette.raised))
                .append(";color:").append(css(palette.text)).append(";border-bottom:2px solid ")
                .append(css(palette.accent)).append(";cursor:pointer;border-radius:3px}")
                .append("mark.md-archive-tone-violet{background:rgba(186,106,235,.36)}")
                .append("mark.md-archive-tone-rose{background:rgba(236,100,152,.32)}")
                .append("mark.md-archive-tone-mint{background:rgba(82,196,158,.32)}")
                .append("mark.md-archive-tone-amber{background:rgba(242,180,70,.34)}")
                .append("mark.md-archive-tone-blue{background:rgba(90,150,245,.33)}")
                .append("mark.md-archive-tone-slate{background:rgba(132,152,171,.34)}</style>");
        if(!passageHtml.isEmpty())html.append("<style>#archive-search-passage{scroll-margin-top:18px;margin:0 0 1.1em}"
                +"#archive-search-passage pre{white-space:pre-wrap;overflow-wrap:anywhere}"
                +"#archive-search-passage mark{background:#ffd76a;color:#171717}</style>");
        if(continuous){
            html.append("<a id=\"archive-before\" class=\"archive-sentinel\" href=\"mdarchive://adjacent?direction=-1\" data-more=\"")
                .append(viewport.hasBefore()?"1":"0").append("\"")
                .append(viewport.hasBefore()?"":" hidden").append(">Earlier messages</a>")
                .append("<div id=\"archive-stream\">");
        }
        html.append(renderMessageSections(messages,onlyOrdinal,partOffset,passageHtml,passageOrdinal));
        if(continuous){
            html.append("</div><a id=\"archive-after\" class=\"archive-sentinel\" href=\"mdarchive://adjacent?direction=1\" data-more=\"")
                .append(viewport.hasAfter()?"1":"0").append("\"")
                .append(viewport.hasAfter()?"":" hidden").append(">More messages</a>")
                .append(ArchiveScrollScript.boot())
                .append(ArchiveHighlightScript.boot());
        }else{
            html.append("<a class=\"archive-more\" href=\"mdarchive://return\">Return to conversation</a>");
        }
        html.append(shell.substring(end));
        return html.toString();
    }
    /** Standalone HTML sections used for both the initial page and in-place DOM insertion. */
    private String renderMessageSections(List<ArchiveDatabase.Message> messages,int onlyOrdinal,
            int partOffset,String passageHtml,int passageOrdinal)throws IOException{
        StringBuilder html=new StringBuilder();
        if(messages.isEmpty())html.append("<p>No messages in this conversation.</p>");
        for(ArchiveDatabase.Message m:messages){
            int part=ArchiveViewport.firstPart(m.ordinal,onlyOrdinal,partOffset);
            if(part<0||part>m.segments)throw new IOException("Message segment offset is invalid");
            int partsToRead=Math.min(CHUNK_PARTS,m.segments-part);
            String content=partsToRead==0?"":database.textChunkVerified(m.id,part,partsToRead);
            boolean more=m.segments>part+CHUNK_PARTS;
            String rendered=MarkdownRenderer.render(content,palette,fontSp,typography);
            int b=rendered.indexOf("<body"),opening=b<0?-1:rendered.indexOf('>',b),closing=rendered.lastIndexOf("</body>");
            if(opening<0||closing<opening)throw new IllegalStateException("Markdown fragment framing error");
            html.append("<section class=\"archive-msg ").append(chatLayout?"chat":"book")
                .append("\" data-message-id=\"").append(escape(m.messageId))
                .append("\" data-ordinal=\"").append(m.ordinal).append("\">")
                .append("<div class=\"archive-meta\">").append(escape(m.role))
                .append(" · ").append(escape(m.created))
                .append(" · #").append(m.ordinal+1).append("</div>");
            if(m.ordinal==passageOrdinal)html.append(passageHtml);
            html.append("<div class=\"archive-content\"><div class=\"archive-part\" data-start-part=\"")
                .append(part).append("\" data-part-end=\"")
                .append(Math.min(part+CHUNK_PARTS,m.segments)).append("\">")
                .append(rendered,opening+1,closing).append("</div></div>");
            if(more){
                html.append("<a class=\"archive-more\" data-autoload=\"1\" href=\"")
                    .append("mdarchive://chunk?direction=1&ordinal=")
                    .append(m.ordinal).append("&part=").append(part+CHUNK_PARTS)
                    .append("\">More of this message</a>");
            }else if(part>0){
                html.append("<a href=\"mdarchive://return?ordinal=").append(m.ordinal)
                    .append("\">Back to message beginning</a>");
            }
            html.append("</section>");
        }
        return html.toString();
    }
    /** Reads one adjacent batch and inserts it without recreating the WebView. */
    private void loadAdjacent(int direction){
        if(direction!=1&&direction!=-1)return;
        if(adjacentLoading||active==null||viewport==null||reader==null)return;
        final ArchiveViewport window=viewport;
        final ArchiveViewport.Load load=direction>0?window.after():window.before();
        if(load==null)return;
        final int token=generation;
        final PreviewWebView web=reader;
        final String chatId=active.id,archiveId=active.archiveId,sourceGeneration=active.generationId;
        adjacentLoading=true;
        io.execute(()->{
            try{
                database.requireExportGeneration(archiveId,sourceGeneration);
                List<ArchiveDatabase.Message> batch=database.messages(chatId,load.offset,load.count);
                if(batch.size()!=load.count)throw new IOException("Archive index changed during reading");
                for(int row=0;row<batch.size();row++)
                    ArchiveTranscriptIntegrity.message(load.offset+row,
                            batch.get(row).ordinal,batch.get(row).segments);
                String fragments=renderMessageSections(batch,-1,0,"",-1);
                database.requireExportGeneration(archiveId,sourceGeneration);
                runOnUiThread(()->{
                    if(token!=generation||web!=reader||active==null||!chatId.equals(active.id)
                            ||!sourceGeneration.equals(active.generationId))return;
                    int excess=Math.max(0,window.end()-window.start()+batch.size()-ArchiveViewport.MAX_VISIBLE);
                    int futureStart=direction>0?window.start()+excess:load.offset;
                    int futureEnd=direction>0?window.end()+batch.size():window.end()-excess;
                    String command=ArchiveScrollScript.insert(fragments,direction,excess,
                            futureStart>0,futureEnd<window.total());
                    web.evaluateJavascript(command,accepted->{
                        if(token!=generation||web!=reader)return;
                        adjacentLoading=false;
                        if(!"true".equals(accepted)){
                            web.evaluateJavascript("window.mdArchiveRelease&&window.mdArchiveRelease()",null);
                            return;
                        }
                        window.insert(load,batch.size());
                        windowOffset=window.start();
                        // Updating tab state only after confirmed DOM insertion avoids
                        // a mismatch when a newer mode/document invalidates the result.
                        if(!indexedReaderVisit)prefs.edit().putInt("archive_chat_position_"+chatId,windowOffset).apply();
                        refreshHighlights(web,batch,token);
                        if(direction>0){
                            visibleMessages.addAll(batch);
                            while(visibleMessages.size()>ArchiveViewport.MAX_VISIBLE)visibleMessages.remove(0);
                        }else{
                            visibleMessages.addAll(0,batch);
                            while(visibleMessages.size()>ArchiveViewport.MAX_VISIBLE)
                                visibleMessages.remove(visibleMessages.size()-1);
                        }
                    });
                });
            }catch(Exception e){runOnUiThread(()->{
                if(token!=generation||web!=reader)return;
                adjacentLoading=false;
                web.evaluateJavascript("window.mdArchiveRelease&&window.mdArchiveRelease()",null);
                fail("Could not load adjacent conversation messages",e);
            });}
        });
    }
    /** Load a precise source-segment range in either direction, retaining the
     * source ordinal and selection rather than replacing the entire WebView.
     */
    private void loadChunk(int ordinal,int startPart,boolean backwards,int untilPart){
        if(chunkLoading||active==null||reader==null)return;
        if(ordinal<0||ordinal>=active.messages||startPart<0)return;
        if(backwards&&(untilPart<=startPart||untilPart-startPart>CHUNK_PARTS))return;
        final int token=generation;
        final String chat=active.id,archiveId=active.archiveId,sourceGeneration=active.generationId;
        final PreviewWebView web=reader;
        chunkLoading=true;
        io.execute(()->{
            try{
                database.requireExportGeneration(archiveId,sourceGeneration);
                ArchiveDatabase.Message message=database.messageAt(chat,ordinal);
                if(message==null||message.ordinal!=ordinal||startPart>=message.segments)
                    throw new IOException("Message section no longer exists or changed");
                if(backwards&&untilPart>message.segments)
                    throw new IOException("Earlier section exceeds message length");
                int take=backwards?untilPart-startPart:Math.min(CHUNK_PARTS,message.segments-startPart);
                String chunk=database.textChunkVerified(message.id,startPart,take);
                String rendered=MarkdownRenderer.render(chunk,palette,fontSp,typography);
                int body=rendered.indexOf("<body");
                int begin=body<0?-1:rendered.indexOf('>',body);
                int end=rendered.lastIndexOf("</body>");
                if(begin<0||end<begin)throw new IOException("Markdown fragment framing failed");
                int next=Math.min(message.segments,startPart+CHUNK_PARTS);
                String fragment=rendered.substring(begin+1,end);
                String command=backwards?
                        ArchiveScrollScript.prependChunk(ordinal,fragment,startPart,untilPart):
                        ArchiveScrollScript.appendChunk(ordinal,fragment,startPart,next,next<message.segments);
                database.requireExportGeneration(archiveId,sourceGeneration);
                runOnUiThread(()->{
                    if(token!=generation||reader!=web||active==null||!chat.equals(active.id)
                            ||!sourceGeneration.equals(active.generationId))return;
                    web.evaluateJavascript(command,result->{
                        if(token!=generation||reader!=web)return;
                        chunkLoading=false;
                        if("true".equals(result))refreshHighlights(web,
                                java.util.Collections.singletonList(message),token);
                        else web.evaluateJavascript("window.mdArchiveChunkRelease&&window.mdArchiveChunkRelease()",null);
                    });
                });
            }catch(Exception e){runOnUiThread(()->{
                if(token!=generation||reader!=web)return;
                chunkLoading=false;
                web.evaluateJavascript("window.mdArchiveChunkRelease&&window.mdArchiveChunkRelease()",null);
                fail("Unable to load message section",e);
            });}
        });
    }
    /** Reapply only records belonging to the messages loaded into the active
     * DOM. Selection failure remains explicit: ambiguous quotes are not marked.
     */
    private void refreshHighlights(PreviewWebView web,List<ArchiveDatabase.Message> messages,int token){
        if(messages==null||messages.isEmpty())return;
        final String chat=active==null?null:active.id;
        if(chat==null)return;
        final java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(ArchiveDatabase.Message m:messages)if(m.messageId!=null)ids.add(m.messageId);
        io.execute(()->{
            try{
                ArchiveJournal.ReadResult rows=journal.load();
                if(rows.truncatedTail)return;
                ArrayList<ArchiveJournal.Annotation> matched=new ArrayList<>();
                for(ArchiveJournal.Annotation a:rows.annotations)
                    if(chat.equals(a.chatId)&&ids.contains(a.messageId)
                            &&a.quote!=null&&!a.quote.isEmpty())matched.add(a);
                if(matched.isEmpty())return;
                String script=ArchiveHighlightScript.add(matched);
                runOnUiThread(()->{
                    if(token==generation&&reader==web&&active!=null&&chat.equals(active.id))
                        web.evaluateJavascript(script,ignored->restoreReadingSelection(web,token));
                });
            }catch(Exception ignored){/* Journal remains untouched. */}
        });
    }
    private static String css(int color){return String.format(Locale.ROOT,"#%06x",color&0xffffff);}
    private static String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private void localAction(Uri uri){
        String host=uri.getHost();
        if("adjacent".equals(host)){
            try{loadAdjacent(Integer.parseInt(uri.getQueryParameter("direction")));}
            catch(RuntimeException ignored){status("Could not navigate conversation");}
        }else if("chunk".equals(host)){
            try{
                boolean backwards="-1".equals(uri.getQueryParameter("direction"));
                int until=backwards?Integer.parseInt(uri.getQueryParameter("until")):-1;
                loadChunk(Integer.parseInt(uri.getQueryParameter("ordinal")),
                        Integer.parseInt(uri.getQueryParameter("part")),backwards,until);
            }catch(RuntimeException ignored){status("Cannot load message section");}
        }else if("next".equals(host))navigateWindow(WINDOW_MESSAGES);
        else if("previous".equals(host))navigateWindow(-WINDOW_MESSAGES);
        else if("continue".equals(host)){
            try{focusOrdinal=Integer.parseInt(uri.getQueryParameter("ordinal"));
                chunkOffset=Integer.parseInt(uri.getQueryParameter("part"));savedScroll=0;showReader(false);
            }catch(RuntimeException ignored){status("Message anchor unavailable");}
        }else if("annotation".equals(host)){
            String annotationId=uri.getQueryParameter("id");
            if(annotationId==null||annotationId.trim().isEmpty())return;
            final String chat=active==null?null:active.id;
            io.execute(()->{
                try{
                    ArchiveJournal.ReadResult rows=journal.load();
                    if(rows.truncatedTail)return;
                    for(ArchiveJournal.Annotation item:rows.annotations){
                        if(annotationId.equals(item.id)&&chat!=null&&chat.equals(item.chatId)){
                            runOnUiThread(()->{
                                if(active!=null&&chat.equals(active.id))showAnnotationActions(item);
                            });
                            return;
                        }
                    }
                    runOnUiThread(()->status("Saved annotation no longer exists"));
                }catch(Exception error){runOnUiThread(()->fail("Could not open annotation",error));}
            });
        }else if("return".equals(host)){
            focusOrdinal=-1;chunkOffset=0;showReader(false);
        }
    }
    private void navigateWindow(int step){
        if(active==null)return;
        clearIndexedPassage();
        windowOffset=Math.max(0,Math.min(Math.max(0,active.messages-1),windowOffset+step));
        windowOffset=(windowOffset/WINDOW_MESSAGES)*WINDOW_MESSAGES;
        viewport=null;viewportChatId=null;
        focusOrdinal=-1;chunkOffset=0;savedScroll=0;pendingFindTerm=null;
        prefs.edit().putInt("archive_chat_position_"+active.id,windowOffset)
                .putInt("archive_chat_scroll_"+active.id,0).apply();
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
                    clearIndexedPassage();
                    windowOffset=((n-1)/WINDOW_MESSAGES)*WINDOW_MESSAGES;
                    viewport=null;viewportChatId=null;
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
        if(!"reader".equals(viewMode)){status("Open a conversation to enter reading Focus");return;}
        savedScroll=reader==null?0:reader.getScrollY();fullscreen=!fullscreen;applyFocusChrome();
    }
    private void applyFocusChrome(){
        if(screen==null)return;
        boolean immersed=fullscreen&&"reader".equals(viewMode);
        if(immersed)getWindow().setFlags(1024,1024);
        else getWindow().clearFlags(1024);
        if(readerHeader!=null)readerHeader.setVisibility(immersed?View.GONE:View.VISIBLE);
        if(readerNavigation!=null)readerNavigation.setVisibility(immersed?View.GONE:View.VISIBLE);
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
        ArchiveSearchDialog.show(this,scopedChat,currentSearch,spec->{
            currentSearch=spec;
            showSearch(spec.term,scopedChat,0);
        });
    }
    private void showSearch(String term,String scope,int offset){
        rememberReadingPlace();
        clearIndexedPassage();
        returnSearchTerm=term;returnSearchScope=scope;returnSearchOffset=Math.max(0,offset);
        query=term;viewMode="search";
        setTitle(scope==null?"Archive search":"Find in conversation",term);
        controls.removeAllViews();action("Search again",v->searchDialog(scope));
        action("Library",v->showHome());
        ScrollView scroll=new ScrollView(this);LinearLayout rows=vertical();scroll.addView(rows);view(scroll);
        final int token=++generation;
        io.execute(()->{
            try{
                ArchiveSearchSpec options=currentSearch;
                List<ArchiveDatabase.Hit> hits=database.search(term,scope,
                        options==null?null:options.speaker,
                        options==null?null:options.fromDate,
                        options==null?null:options.toDate,60,offset,()->generation!=token);
                List<ArchiveJournal.Annotation> notes=new ArrayList<>();
                String noteWarning=null;
                if(offset==0 && (currentSearch==null||!currentSearch.filtersMessagesOnly())){
                    try{notes=ArchiveAnnotationSearch.find(new java.io.File(getFilesDir(),
                            "claude-archive/annotations-v2.journal"),term,scope,100);}
                    catch(IOException sourceUnavailable){noteWarning="Note search is unavailable until its journal is repaired.";}
                }
                final List<ArchiveJournal.Annotation> matchedNotes=notes;
                final String warning=noteWarning;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||token!=generation)return;
                    if(hits.isEmpty()&&matchedNotes.isEmpty())rows.addView(text("No matching indexed messages or saved annotations.",14));
                    if(!hits.isEmpty())rows.addView(text("MATCHING CONVERSATION MESSAGES",14));
                    for(ArchiveDatabase.Hit hit:hits){
                        add(rows,hit.chatTitle+" · "+safe(hit.created)+"\n"+safe(hit.excerpt),
                                v->openIndexedPassage(hit,findConversation(hit.chatId),false));
                    }
                    if(warning!=null)rows.addView(text(warning,13));
                    if(currentSearch!=null&&currentSearch.filtersMessagesOnly())
                        rows.addView(text("Speaker/date filters apply to source messages. Clear filters to search saved notes too.",12));
                    if(!matchedNotes.isEmpty()){
                        rows.addView(text("MATCHING NOTES, HIGHLIGHTS & SAVED LINKS",14));
                        for(ArchiveJournal.Annotation note:matchedNotes){
                            add(rows,safe(note.chatTitle)+" · "+safe(note.messageDate)+"\n"+
                                safe(note.label)+" · "+safe(note.note),v->showAnnotationActions(note));
                        }
                    }
                    if(hits.size()==60)add(rows,"More search results",v->showSearch(term,scope,offset+60));
                });
            }catch(Exception e){runOnUiThread(()->{if(generation==token)fail("Search failed",e);});}
        });
    }
    private void bookmarkCurrent(){
        if(active==null)return;
        final ArrayList<ArchiveDatabase.Message> candidates=new ArrayList<>(visibleMessages);
        if(candidates.isEmpty()){status("No loaded message to bookmark");return;}
        if(candidates.size()==1){saveBookmarkFor(candidates.get(0));return;}
        String[] labels=new String[candidates.size()];
        for(int i=0;i<candidates.size();i++){
            ArchiveDatabase.Message m=candidates.get(i);
            labels[i]="#"+(m.ordinal+1)+" · "+safe(m.role)+" · "+safe(m.created);
        }
        new AlertDialog.Builder(this).setTitle("Bookmark which message?")
            .setItems(labels,(dialog,which)->saveBookmarkFor(candidates.get(which)))
            .setNegativeButton("Cancel",null).show();
    }
    private void saveBookmarkFor(ArchiveDatabase.Message m){
        if(active==null||m==null)return;
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id=UUID.randomUUID().toString();a.archiveId=active.archiveId;
        a.chatId=active.id;a.chatTitle=active.name;a.messageId=m.messageId;
        a.messageDate=m.created;a.type="bookmark";
        a.label="Reading place · message "+(m.ordinal+1);
        a.createdAt=System.currentTimeMillis();a.updatedAt=a.createdAt;
        saveAnnotation(a);
    }
    private void copySelectedPassage(boolean share){selectedPassageAction(share,false);}
    private void printSelectedPassage(){selectedPassageAction(false,true);}
    private void selectedPassageAction(boolean share,boolean print){
        if(reader==null||active==null){status("Select text in a conversation first");return;}
        // Javascript returns a JSON-encoded string, not executable content.
        String js="(function(){var s=window.getSelection();if(!s||!s.rangeCount)return '';"+
            "var t=s.toString();if(!t||!t.trim()||t.length>65536)return '';"+
            "var n=s.anchorNode;var p=n?(n.nodeType===1?n:n.parentElement):null;"+
            "var e=p&&p.closest('[data-message-id]');"+
            "var end=s.focusNode;var q=end?(end.nodeType===1?end:end.parentElement):null;"+
            "if(!e||!q||q.closest('[data-message-id]')!==e)return '';"+
            "return JSON.stringify({id:e.getAttribute('data-message-id'),quote:t});})()";
        final String chatId=active.id,archiveId=active.archiveId,title=active.name;
        final PreviewWebView sourceWeb=reader;
        final int selectedGeneration=generation;
        sourceWeb.evaluateJavascript(js,result->{
            if(selectedGeneration!=generation||sourceWeb!=reader||active==null
                    ||!chatId.equals(active.id)||!archiveId.equals(active.archiveId)
                    ||isFinishing()||isDestroyed())return;
            try{
                Object decoded=new JSONTokener(result).nextValue();
                if(!(decoded instanceof String)||((String)decoded).isEmpty()){
                    status("Select text within one message first");return;
                }
                JSONObject selection=new JSONObject((String)decoded);
                String message=selection.optString("id","");
                if(message.isEmpty())throw new IOException("Missing message identity");
                String date=dateOfVisibleMessage(message);
                String citation=ArchiveCitations.citation(title,date,archiveId,chatId,
                        message,selection.optString("quote",""));
                if(print){
                    // Capture this immutable, source-verified quote, not the entire
                    // Archive WebView or an unbounded conversation window.
                    DocumentPrint.start(this,
                        ()->new DocumentPrint.Capture(citation,title+" · quoted passage",
                                palette,fontSp,false,typography),
                        this::status);
                }else if(share){
                    Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT,citation);
                    startActivity(Intent.createChooser(intent,"Share selected passage"));
                }else{
                    ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Archive quotation",citation));
                    status("Quote, conversation date and source link copied");
                }
            }catch(Exception error){fail("Unable to copy selected passage",error);}
        });
    }
    private void captureSelection(){
        if(reader==null||active==null){status("Open a conversation first");return;}
        // Bind to the selected text range, not the first matching quote.
        String js=com.example.markdowndesk.workspace.archive.ArchiveSelectionScript.capture();
        final String selectedChat=active.id, selectedArchive=active.archiveId;
        final int selectedGeneration=generation;
        final PreviewWebView selectedWeb=reader;
        selectedWeb.evaluateJavascript(js,result->{
            if(selectedGeneration!=generation||selectedWeb!=reader||active==null
                    ||!selectedChat.equals(active.id)||!selectedArchive.equals(active.archiveId)
                    ||isFinishing()||isDestroyed())return;
            try{
                Object decoded=new JSONTokener(result).nextValue();
                if(!(decoded instanceof String)||((String)decoded).isEmpty()){
                    status("Select a passage in the conversation first");return;
                }
                JSONObject selection=new JSONObject((String)decoded);
                String msg=selection.optString("id","");
                if(msg.isEmpty())throw new IllegalArgumentException("No message identity");
                annotationDialog(selection,selectedChat,selectedArchive,selectedGeneration);
            }catch(Exception e){fail("Unable to capture selected passage",e);}
        });
    }
    private void annotationDialog(JSONObject selection,String chat,String archiveId,int sourceGeneration){
        final String[] kinds={"Highlight","Note","Saved source link","Cross-conversation link","Attach external URL","Bookmark"};
        final String[] codes={"highlight","note","source_link","cross_link","external_link","bookmark"};
        new AlertDialog.Builder(this).setTitle("Save selected passage")
            .setItems(kinds,(dialog,index)->annotationDetails(codes[index],selection,
                    chat,archiveId,sourceGeneration))
            .setNegativeButton("Cancel",null).show();
    }
    private void annotationDetails(String kind,JSONObject selection,
                                   String chat,String archiveId,int sourceGeneration){
        LinearLayout layout=vertical();layout.setPadding(dp(20),dp(4),dp(20),dp(10));
        EditText label=new EditText(this);label.setHint("Label");layout.addView(label);
        EditText note=new EditText(this);note.setHint("Your annotation (optional)");
        note.setMinLines(2);layout.addView(note);
        EditText target=new EditText(this);
        target.setHint("Destination URL or archive link");
        if("cross_link".equals(kind)||"external_link".equals(kind))layout.addView(target);
        EditText tags=new EditText(this);tags.setHint("Tags separated by commas");layout.addView(tags);
        Spinner tone=new Spinner(this);
        ArrayAdapter<String> toneChoices=new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,AnnotationTone.labels());
        toneChoices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tone.setAdapter(toneChoices);
        tone.setContentDescription("Annotation highlight color");
        layout.addView(tone);
        TextView quote=text("Selected: "+safe(selection.optString("quote","")),12);layout.addView(quote);
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Save "+kind.replace('_',' '))
            .setView(scroll).setPositiveButton("Save",null)
            .setNegativeButton("Cancel",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(sourceGeneration!=generation||active==null||!chat.equals(active.id)
                    ||!archiveId.equals(active.archiveId)||isFinishing()||isDestroyed()){
                status("Reading source changed; reselect the passage before saving.");return;
            }
            String selectedQuote=selection.optString("quote","");
            int first=selection.optInt("start",-1),last=selection.optInt("end",-1);
            if(first<0||last-first!=selectedQuote.length()||selectedQuote.isEmpty()){
                status("Exact selection coordinates are unavailable; reselect passage.");return;
            }
            String messageId=selection.optString("id","");
            boolean present=false;
            for(ArchiveDatabase.Message row:visibleMessages)
                if(messageId.equals(row.messageId)){present=true;break;}
            if(!present){status("Selected message is no longer loaded; reselect passage.");return;}
            if(("external_link".equals(kind)||"cross_link".equals(kind))&&target.getText().toString().trim().isEmpty()){
                target.setError("Enter a destination");return;
            }
            ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
            a.id=UUID.randomUUID().toString();a.archiveId=archiveId;
            a.chatId=chat;a.chatTitle=active.name;
            a.messageId=selection.optString("id","");
            a.messageDate=dateOfVisibleMessage(a.messageId);
            a.quote=selection.optString("quote","");a.prefix=selection.optString("prefix","");
            a.suffix=selection.optString("suffix","");
            a.selectionStart=selection.optInt("start",-1);a.selectionEnd=selection.optInt("end",-1);
            a.sourcePart=Math.max(-1,selection.optInt("sourcePart",-1));
            a.type=kind;a.label=label.getText().toString().trim();
            a.note=note.getText().toString();a.target=target.getText().toString().trim();
            a.tags=AnnotationTone.withTone(tags.getText().toString().trim(),
                    AnnotationTone.byIndex(tone.getSelectedItemPosition()));
            a.createdAt=System.currentTimeMillis();a.updatedAt=a.createdAt;
            d.dismiss();saveAnnotation(a);
        }));
        d.show();
    }
    private String dateOfVisibleMessage(String id){
        for(ArchiveDatabase.Message m:visibleMessages)if(id.equals(m.messageId))return m.created;
        return null;
    }
    private void saveAnnotation(ArchiveJournal.Annotation annotation){
        saveAnnotation(annotation,null);
    }
    private void saveAnnotation(ArchiveJournal.Annotation annotation,ArchiveJournal.Annotation expected){
        io.execute(()->{
            try{
                if(expected==null)journal.save(annotation);
                else journal.saveIfCurrent(expected,annotation);
            }
            catch(Exception error){runOnUiThread(()->fail("Annotation was not saved",error));return;}
            if(annotation.quote!=null&&!annotation.quote.isEmpty()){
                String script=ArchiveHighlightScript.add(
                    java.util.Collections.singletonList(annotation));
                runOnUiThread(()->{
                    if(reader!=null&&active!=null&&annotation.chatId.equals(active.id))
                        reader.evaluateJavascript(script,null);
                });
            }
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
    /** Only deliberately saved, source-validated links to this conversation are
     * incoming backlinks. The journal owns them; original JSON stays immutable.
     */
    private void showIncomingArchiveLinks(){
        if(active==null)return;
        final String chat=active.id,archiveId=active.archiveId;
        final int token=generation;
        io.execute(()->{
            try{
                ArchiveJournal.ReadResult data=journal.load();
                if(data.truncatedTail)throw new IOException("Incomplete annotation journal; backlinks cannot be trusted");
                ArrayList<ArchiveJournal.Annotation> incoming=new ArrayList<>();
                ArrayList<String> labels=new ArrayList<>();
                for(ArchiveJournal.Annotation a:data.annotations){
                    if(a==null||!a.isLink()||a.target==null||
                            !com.example.markdowndesk.workspace.WorkspaceDeepLinks.isArchiveLink(a.target))continue;
                    Uri target=Uri.parse(a.target);
                    List<String> parts=target.getPathSegments();
                    if(parts.size()!=4||!archiveId.equals(parts.get(1))||!chat.equals(parts.get(2)))continue;
                    String messageId=parts.get(3);
                    int ordinal=database.findMessageOrdinal(chat,messageId);
                    if(ordinal<0)continue;
                    incoming.add(a);
                    String label=a.label==null||a.label.trim().isEmpty()?a.type:a.label;
                    labels.add(safe(a.chatTitle)+" → message "+(ordinal+1)+" · "+safe(label));
                }
                runOnUiThread(()->{
                    if(token!=generation||active==null||!chat.equals(active.id)||isFinishing()||isDestroyed())return;
                    if(incoming.isEmpty()){
                        new AlertDialog.Builder(this).setTitle("Incoming Archive links")
                            .setMessage("No saved links point to this conversation.")
                            .setPositiveButton("Close",null).show();return;
                    }
                    new AlertDialog.Builder(this).setTitle("Incoming Archive links")
                        .setItems(labels.toArray(new String[0]),(dialog,which)->showAnnotationActions(incoming.get(which)))
                        .setNegativeButton("Close",null).show();
                });
            }catch(Exception failed){runOnUiThread(()->{
                if(token==generation&&active!=null&&chat.equals(active.id))
                    fail("Backlinks unavailable",failed);
            });}
        });
    }

    private void showAnnotationActions(ArchiveJournal.Annotation a){
        new AlertDialog.Builder(this).setTitle(safe(a.label)).setItems(new String[]{
                "Jump to source","Copy Markdown link","Edit note","History","Delete annotation",
                "Highlight color"},(d,index)->{
            if(index==0){
                if("markdown-document".equals(a.archiveId)){
                    String link=ArchiveMarkdownIndexes.deepLink(a);
                    if(!com.example.markdowndesk.workspace.WorkspaceDeepLinks.isDocumentLink(link)){
                        status("Saved Markdown note link is invalid; original note preserved");return;
                    }
                    try{
                        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link))
                                .setClass(this,MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    }catch(ActivityNotFoundException|SecurityException denied){
                        status("Markdown source is unavailable. No new file permission was granted.");
                    }
                    return;
                }
                if("json-document".equals(a.archiveId)){
                    String link=ArchiveMarkdownIndexes.deepLink(a);
                    if(!com.example.markdowndesk.workspace.WorkspaceDeepLinks.isInspectorLink(link)){
                        status("Saved JSON note link is invalid; original note preserved");return;
                    }
                    try{
                        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link))
                                .setClass(this,JsonExplorerActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    }catch(ActivityNotFoundException|SecurityException denied){
                        status("JSON source is unavailable; no new file grant was created");
                    }
                    return;
                }
                ArchiveDatabase.Conversation c=findConversation(a.chatId);
                if(c==null||a.archiveId==null||!a.archiveId.equals(c.archiveId)){
                    status("Archived annotation source identity no longer matches its conversation");return;
                }
                int position=database.findMessageOrdinal(a.chatId,a.messageId);
                if(position<0){status("Original message no longer indexed. Link needs review.");return;}
                rememberPriorArchiveVisit(c.id);
                active=c;windowOffset=Math.max(0,position/WINDOW_MESSAGES*WINDOW_MESSAGES);
                restoreLastPosition=false;
                focusOrdinal=position;chunkOffset=Math.max(0,a.sourcePart-1);savedScroll=0;
                clearIndexedPassage();
                pendingFindTerm=a.quote;showReader(false);
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
                        saveAnnotation(changed,a);
                    }).setNegativeButton("Cancel",null).show();
            }else if(index==3){
                final int historyGeneration=generation;
                // Notebook rows are valid even when no conversation is open.
                // The journal itself authenticates this annotation's source.
                AnnotationHistoryDialog.show(this,io,journal,a,
                        ()->generation==historyGeneration,this::status);
            }else if(index==4){
                new AlertDialog.Builder(this).setTitle("Delete this annotation?")
                    .setMessage("The original conversation is unaffected.")
                    .setPositiveButton("Delete",(x,w)->io.execute(()->{
                        try{journal.deleteIfCurrent(a);publishNotes(a.chatTitle,a.archiveId,a.chatId);
                            runOnUiThread(()->showNotebook(false));}
                        catch(Exception e){runOnUiThread(()->fail("Could not delete annotation",e));}
                    })).setNegativeButton("Cancel",null).show();
            }else if(index==5){
                new AlertDialog.Builder(this).setTitle("Annotation highlight color")
                    .setSingleChoiceItems(AnnotationTone.labels(),AnnotationTone.index(a.tags),
                        (dialog,which)->{
                            ArchiveJournal.Annotation changed=a.copy();
                            changed.tags=AnnotationTone.withTone(a.tags,AnnotationTone.byIndex(which));
                            changed.updatedAt=System.currentTimeMillis();
                            dialog.dismiss();
                            saveAnnotation(changed,a);
                        })
                    .setNegativeButton("Cancel",null).show();
            }
        }).show();
    }
    private void copyMaster(){
        io.execute(()->{
            try{
                ArchiveJournal.ReadResult snapshot=journal.load();
                if(snapshot.truncatedTail)
                    throw new IOException("Saved links are incomplete; repair journal before copying master index");
                String md=ArchiveMarkdownIndexes.master(snapshot.annotations);
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
        if(result!=RESULT_OK||data==null||data.getData()==null){
            if(request==REQUEST_TRANSCRIPT||request==REQUEST_TRANSCRIPT_PDF)pendingExportChat=null;
            return;
        }
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
        }else if(request==REQUEST_TRANSCRIPT_PDF){
            String chat=pendingExportChat;pendingExportChat=null;
            if(chat!=null)writeTranscriptPdf(chat,uri);
        }
    }
    private void importArchive(Uri uri){importArchive(uri,false,false);}
    private void importArchive(Uri uri,boolean genericJson,boolean fromInspector){
        if(importController==null)importController=ArchiveImportCoordinator.get(getApplicationContext());
        if(!importController.start(uri,genericJson)){
            status("An import is already running, or the selected file cannot be opened");
            showImportScreen();return;
        }
        inspectorImportSource=fromInspector?JsonDocumentIndexer.sourceIdentity(uri.toString(),genericJson):null;
        importedSourcePage=0;
        importedSourceList=false;
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
        setTitle("Importing JSON archive","Indexing offline · original unchanged");
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
                        status(state.detail);
                        if(inspectorImportSource==null)showHome();
                        else{
                            String indexedSource=importController.indexedSourceId();
                            if(indexedSource!=null)inspectorImportSource=indexedSource;
                            showImportedSource(inspectorImportSource,0);
                        }
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
    private void showImportedSource(String sourceId,int offset){
        List<ArchiveDatabase.Conversation> chats=database.importedConversations(sourceId,151,offset);
        if(offset==0&&chats.size()==1){
            openChat(chats.get(0));
            return;
        }
        importedSourcePage=offset;
        importedSourceList=true;
        generation++;viewMode="imported";reader=null;active=null;
        controls.removeAllViews();
        action("All conversations",v->showHome());
        setTitle("Indexed file","Conversations in the JSON file you opened");
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=vertical();
        scroll.addView(body);
        if(chats.isEmpty())body.addView(text("No conversations were indexed for this file.",14));
        for(ArchiveDatabase.Conversation chat:chats.subList(0,Math.min(chats.size(),150)))
            add(body,chat.name+" · "+chat.messages+" messages",v->openChat(chat));
        if(chats.size()>150)add(body,"Next conversations",v->showImportedSource(sourceId,offset+150));
        if(offset>0)add(body,"Previous conversations",v->showImportedSource(sourceId,Math.max(0,offset-150)));
        view(scroll);
    }
    private void renderImportProgress(ArchiveImportCoordinator.Snapshot state){
        if(importSummary==null||!"import".equals(viewMode))return;
        String stage="indexing".equals(state.state)?"Indexing conversations":
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
    /** Bounded full transcript print using existing DocumentPrint; larger
     * conversations remain exportable in complete PDF form. No partial prints.
     */
    private void printFullTranscript(){
        if(active==null)return;
        final ArchiveDatabase.Conversation selected=active;
        final int sourceGeneration=generation;
        status("Preparing complete conversation for Android printing...");
        io.execute(()->{
            java.io.ByteArrayOutputStream prepared=new java.io.ByteArrayOutputStream(32768);
            OutputStream capped=new OutputStream(){
                private long remaining=DocumentIO.MAX_DOCUMENT_BYTES;
                @Override public void write(int value)throws IOException{
                    if(remaining<1)throw new IOException("Conversation exceeds 8 MiB print budget. Export full PDF instead.");
                    prepared.write(value);remaining--;
                }
                @Override public void write(byte[] data,int at,int length)throws IOException{
                    if(data==null)throw new NullPointerException("data");
                    if(at<0||length<0||at>data.length-length)
                        throw new IndexOutOfBoundsException("Invalid transcript slice");
                    if(length>remaining)throw new IOException("Conversation exceeds 8 MiB print budget. Export full PDF instead.");
                    prepared.write(data,at,length);remaining-=length;
                }
            };
            try{
                streamTranscript(selected,capped);
                String markdown=StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(prepared.toByteArray())).toString();
                runOnUiThread(()->{
                    if(sourceGeneration!=generation||active==null||!selected.id.equals(active.id)
                            ||isFinishing()||isDestroyed())return;
                    DocumentPrint.start(this,
                        ()->new DocumentPrint.Capture(markdown,selected.name,palette,fontSp,false,typography),
                        this::status);
                });
            }catch(Exception failed){
                runOnUiThread(()->{
                    if(sourceGeneration==generation&&!isFinishing()&&!isDestroyed())
                        fail("Unable to prepare complete transcript for printing; use Export PDF for large conversations",failed);
                });
            }
        });
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
    /** Explicit full transcript PDF export. Does not read the bounded WebView
     * window, does not overwrite the original JSON, and never claims partial
     * output succeeded on a provider or index error.
     */
    private void exportTranscriptPdf(){
        if(active==null)return;
        pendingExportChat=active.id;
        String name=ArchiveMarkdownIndexes.fileName(active.name,active.id);
        if(name.endsWith(".md"))name=name.substring(0,name.length()-3)+".pdf";
        Intent create=new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf")
                .putExtra(Intent.EXTRA_TITLE,name);
        try{startActivityForResult(create,REQUEST_TRANSCRIPT_PDF);}
        catch(ActivityNotFoundException missing){pendingExportChat=null;status("Android PDF save picker unavailable");}
    }
    private void writeTranscriptPdf(String chat,Uri destination){
        final ArchiveDatabase.Conversation conversation=findConversation(chat);
        if(conversation==null){status("Conversation no longer indexed");return;}
        io.execute(()->{
            try{
                ExportReceipt.Sink out=new ExportReceipt.Sink(
                        getContentResolver().openOutputStream(destination,"w"));
                try(out){
                    ArchiveTranscriptPdf.export(database,conversation,out);
                }
                // A provider close can fail AFTER flush; only complete readback is success.
                try(InputStream check=getContentResolver().openInputStream(destination)){
                    ExportReceipt.verify(check,out.receipt());
                }
                runOnUiThread(()->status("Complete PDF conversation exported and verified"));
            }catch(Exception failure){
                runOnUiThread(()->fail(
                    "PDF export incomplete or could not be verified; do not rely on the selected document",
                    failure));
            }
        });
    }
    /** The one generation-pinned complete-transcript writer serves both signed-off
     * SAF exports and the existing bounded local print pipeline. Never use
     * the on-screen 12-message window or JSON Inspector preview as source.
     */
    private void streamTranscript(ArchiveDatabase.Conversation conversation,OutputStream out)throws IOException {
        final String chat=conversation.id;
                    final String generation=database.exportGeneration(conversation);
                    write(out,"# "+transcriptField(conversation.name)+"\n\nExported from read-only JSON Archive. Original JSON unchanged.\n\n");
                    int index=0;
                    for(;;){
                        if(Thread.currentThread().isInterrupted())throw new IOException("Transcript export cancelled");
                        database.requireExportGeneration(conversation.archiveId,generation);
                        List<ArchiveDatabase.Message> messages=database.messages(chat,index,50);
                        if(messages.isEmpty())break;
                        for(int row=0;row<messages.size();row++){
                            ArchiveDatabase.Message m=messages.get(row);
                            ArchiveTranscriptIntegrity.message(index+row,m.ordinal,m.segments);
                            write(out,"## "+transcriptField(m.role)+" · "+transcriptField(m.created)+
                                    " · message "+(m.ordinal+1)+"\n\n");
                            if(conversation.archiveId!=null&&!conversation.archiveId.isEmpty()
                                    &&m.messageId!=null&&!m.messageId.isEmpty()){
                                Uri source=new Uri.Builder().scheme("markdowndesk").authority("archive")
                                    .appendPath("v1").appendPath(conversation.archiveId)
                                    .appendPath(chat).appendPath(m.messageId).build();
                                write(out,"Source: [Open original message]("+source.toString()+")\n\n");
                            }
                            for(int part=0;part<m.segments;part+=32){
                                final int count=Math.min(32,m.segments-part);
                                write(out,database.textChunkVerified(m.id,part,count));
                            }
                            write(out,"\n\n");
                        }
                        index+=messages.size();
                    }
                    database.requireExportGeneration(conversation.archiveId,generation);
                    ArchiveTranscriptIntegrity.complete(conversation.messages,index);
                    out.flush();
    }

    private void writeTranscript(String chat,Uri destination){
        final ArchiveDatabase.Conversation conversation=findConversation(chat);
        if(conversation==null){status("Conversation no longer indexed");return;}
        io.execute(()->{
            try{
                ExportReceipt.Sink out=new ExportReceipt.Sink(
                        getContentResolver().openOutputStream(destination,"w"));
                try(out){
                    streamTranscript(conversation,out);
                }
                try(InputStream check=getContentResolver().openInputStream(destination)){
                    ExportReceipt.verify(check,out.receipt());
                }
                runOnUiThread(()->status("Complete Markdown conversation exported and verified"));
            }catch(Exception failure){
                runOnUiThread(()->fail(
                    "Transcript export incomplete or could not be verified; do not rely on the selected document",
                    failure));
            }
        });
    }
    /** Metadata only. Source message bodies are streamed without rewriting. */
    private static String transcriptField(String value){
        if(value==null)return "Unavailable";
        return value.replace('\r',' ').replace('\n',' ')
            .replace("\\","\\\\").replace("[","\\[")
            .replace("]","\\]").replace("*","\\*")
            .replace("_","\\_").replace("#","\\#");
    }
    private static void write(OutputStream output,String text)throws IOException{output.write(text.getBytes(StandardCharsets.UTF_8));}
    private void moreMenu(){
        new AlertDialog.Builder(this).setTitle("Archive reader")
            .setItems(new String[]{"Library","Search all","Master links","Choose Markdown index folder",
                    "Reader appearance","Full screen / Focus","Import LLM JSON archive","View raw JSON (existing explorer)"},(d,i)->{
                switch(i){
                    case 0:showHome();break;case 1:searchDialog(null);break;
                    case 2:showNotebook(true);break;case 3:chooseNotesFolder();break;
                    case 4:appearanceDialog();break;case 5:toggleFocus();break;
                    case 6:chooseImport();break;
                    case 7:startActivity(new Intent(this,JsonExplorerActivity.class));break;
                }
            }).show();
    }
    /** Explicit user-requested handoff of a granted JSON content URI.
     * Reuse the process-owned importer, never convert or mutate the original.
     * A general JSON file unsupported by the conversation indexer fails visibly.
     */
    private boolean handleIncomingInspectorImport(Intent intent){
        if(intent==null||!intent.hasExtra(WorkspaceRouter.EXTRA_IMPORT_JSON_URI))return false;
        String advertised=intent.getStringExtra(WorkspaceRouter.EXTRA_IMPORT_JSON_URI);
        intent.removeExtra(WorkspaceRouter.EXTRA_IMPORT_JSON_URI);
        incomingNavigationConsumed=true;
        Uri selected=intent.getData();
        if(selected==null||advertised==null||!advertised.equals(selected.toString())
                ||!"content".equalsIgnoreCase(selected.getScheme())){
            status("Unable to import: original Android document URI is invalid");
            showHome();return true;
        }
        openedFromJsonInspector=true;
        boolean genericJson=intent.getBooleanExtra("com.example.markdowndesk.INDEX_GENERIC_JSON",false);
        intent.removeExtra("com.example.markdowndesk.INDEX_GENERIC_JSON");
        intent.setData(null);
        importArchive(selected,genericJson,true);
        return true;
    }

    static Bundle indexedPassageBundle(ArchiveDatabase.Hit hit){
        Bundle data=new Bundle();
        data.putString("chat",hit.chatId);
        data.putString("message",hit.messageId);
        data.putString("generation",hit.generationId);
        data.putLong("row",hit.messageRow);
        data.putInt("ordinal",hit.messageOrdinal);
        data.putInt("part",hit.partOrdinal);
        data.putInt("last_part",hit.endPartOrdinal);
        data.putInt("start",hit.matchStart);
        data.putInt("end",hit.matchEnd);
        return data;
    }

    private static ArchiveDatabase.Hit readIndexedPassage(Bundle data){
        if(data==null)return null;
        ArchiveDatabase.Hit hit=new ArchiveDatabase.Hit();
        hit.chatId=data.getString("chat");
        hit.messageId=data.getString("message");
        hit.generationId=data.getString("generation");
        hit.messageRow=data.getLong("row",-1);
        hit.messageOrdinal=data.getInt("ordinal",-1);
        hit.partOrdinal=data.getInt("part",-1);
        hit.endPartOrdinal=data.getInt("last_part",-1);
        hit.matchStart=data.getInt("start",-1);
        hit.matchEnd=data.getInt("end",-1);
        return hit.chatId!=null&&hit.generationId!=null&&hit.messageRow>0&&hit.messageOrdinal>=0?hit:null;
    }

    private void clearIndexedPassage(){
        indexedPassage=null;
        jumpToIndexedPassage=false;
        indexedReaderVisit=false;
    }

    private void openIndexedPassage(ArchiveDatabase.Hit hit,ArchiveDatabase.Conversation source,
            boolean returnToUnifiedSearch){
        if(source==null||!source.id.equals(hit.chatId)||!source.generationId.equals(hit.generationId)
                ||hit.messageOrdinal<0||hit.messageOrdinal>=source.messages){
            status("The indexed source changed. Search again to open the passage.");
            if(returnToUnifiedSearch&&reader==null)showHome();
            return;
        }
        rememberReadingPlace();
        if(returnToUnifiedSearch){
            returnSearchTerm=null;returnSearchScope=null;returnSearchOffset=0;
            rememberPriorArchiveVisit(source.id);
        }
        returnUnifiedSearch=returnToUnifiedSearch;
        active=source;windowOffset=hit.messageOrdinal/WINDOW_MESSAGES*WINDOW_MESSAGES;
        focusOrdinal=hit.messageOrdinal;chunkOffset=Math.max(0,hit.partOrdinal);
        pendingFindTerm=null;savedSelectionJson=null;savedScroll=0;restoreLastPosition=false;
        indexedPassage=hit;jumpToIndexedPassage=true;indexedReaderVisit=true;
        showReader(false);
    }

    private boolean handleIncomingIndexedPassage(Intent intent){
        if(intent==null||!intent.hasExtra(EXTRA_INDEXED_PASSAGE))return false;
        ArchiveDatabase.Hit hit=readIndexedPassage(intent.getBundleExtra(EXTRA_INDEXED_PASSAGE));
        intent.removeExtra(EXTRA_INDEXED_PASSAGE);
        incomingNavigationConsumed=true;
        if(hit==null){
            status("Invalid indexed passage. Search again.");
            if(reader==null)showHome();
        }else openIndexedPassage(hit,findConversation(hit.chatId),
                intent.getBooleanExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,false));
        return true;
    }

    private boolean handleDeepLink(Intent intent){
        if(intent==null||intent.getData()==null)return false;
        Uri uri=intent.getData();
        if(!com.example.markdowndesk.workspace.WorkspaceDeepLinks.isArchiveLink(uri.toString()))return false;
        List<String> segments=uri.getPathSegments();
        if(segments.size()!=4||!"v1".equals(segments.get(0)))return false;
        // Consume this navigation once. Rotation must restore the current reader
        // position, rather than replay the original search/deep-link destination.
        intent.setData(null);incomingNavigationConsumed=true;restoreLastPosition=false;
        String sourceArchive=segments.get(1),chat=segments.get(2),message=segments.get(3);
        ArchiveDatabase.Conversation c=findConversation(chat);
        if(c==null||c.archiveId==null||!sourceArchive.equals(c.archiveId)){
            status("Archived conversation source identity differs from saved link; reimport its source.");
            showHome();return true;
        }
        clearIndexedPassage();
        pendingFindTerm=null;
        int ordinal=database.findMessageOrdinal(chat,message);
        if(ordinal<0){status("Saved link no longer resolves to a message");showHome();return true;}
        returnUnifiedSearch=intent.getBooleanExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,false);
        rememberPriorArchiveVisit(c.id);
        active=c;windowOffset=Math.max(0,ordinal/WINDOW_MESSAGES*WINDOW_MESSAGES);
        focusOrdinal=ordinal;chunkOffset=0;savedScroll=0;
        showReader(false);
        final String annotationId=uri.getQueryParameter("link");
        if(annotationId!=null&&!annotationId.isEmpty())restoreAnnotationPassage(annotationId,c,ordinal);
        return true;
    }
    private void restoreAnnotationPassage(String id,ArchiveDatabase.Conversation source,int ordinal){
        final int token=generation;
        io.execute(()->{
            try{
                ArchiveJournal.ReadResult notes=journal.load();
                if(notes.truncatedTail)throw new IOException("Annotation history is incomplete");
                ArchiveJournal.Annotation match=null;
                for(ArchiveJournal.Annotation note:notes.annotations)
                    if(id.equals(note.id)&&source.archiveId.equals(note.archiveId)&&source.id.equals(note.chatId)
                            &&database.findMessageOrdinal(source.id,note.messageId)==ordinal){match=note;break;}
                final ArchiveJournal.Annotation found=match;
                runOnUiThread(()->{
                    if(token!=generation||active==null||!source.id.equals(active.id)||isFinishing()||isDestroyed())return;
                    if(found==null){status("Saved annotation no longer resolves; original message opened.");return;}
                    focusOrdinal=ordinal;chunkOffset=Math.max(0,found.sourcePart-1);
                    clearIndexedPassage();
                    pendingFindTerm=found.quote;savedScroll=0;showReader(false);
                });
            }catch(Exception failed){runOnUiThread(()->{
                if(token==generation&&!isFinishing()&&!isDestroyed())fail("Saved annotation unavailable",failed);
            });}
        });
    }
    /** Bounded local navigation history. No URI grants or source text are stored.
     * Reopening always resolves the recorded public chat ID against today's ready index.
     */
    private void rememberPriorArchiveVisit(String destination){
        if(active==null||destination==null||destination.equals(active.id)
                ||!"reader".equals(viewMode))return;
        rememberReadingPlace();
        if(priorArchiveChats.size()==16)priorArchiveChats.remove(0);
        priorArchiveChats.add(active.id);
    }
    /** The bridge only accepts bounded inert selection data; no file/network APIs. */
    private final class SelectionStateBridge {
        private final PreviewWebView web;private final int token;
        SelectionStateBridge(PreviewWebView web,int token){this.web=web;this.token=token;}
        @android.webkit.JavascriptInterface public void update(String snapshot){
            if(token!=generation||reader!=web)return;
            if(snapshot==null||snapshot.isEmpty()){savedSelectionJson=null;return;}
            if(snapshot.length()>262144)return;
            try{
                JSONObject state=new JSONObject(snapshot);
                String id=state.optString("id",""),quote=state.optString("quote","");
                if(id.isEmpty()||id.length()>2048||quote.isEmpty()||quote.length()>65536||
                        state.optString("prefix","").length()>64||state.optString("suffix","").length()>64)return;
                savedSelectionJson=snapshot;
            }catch(Exception invalid){/* An invalid selection never replaces valid state. */}
        }
    }
    private void restoreReadingSelection(PreviewWebView web,int token){
        if(token!=generation||reader!=web)return;
        String selected=savedSelectionJson;
        if(selected!=null&&!selected.isEmpty())web.evaluateJavascript(
                com.example.markdowndesk.workspace.archive.ArchiveSelectionScript.restore(selected),resolved->{
                    if(token!=generation||reader!=web)return;
                    if(!"true".equals(resolved))status("Previous selection no longer uniquely resolves in the loaded passage.");
                });
        web.evaluateJavascript(com.example.markdowndesk.workspace.archive.ArchiveSelectionScript.observe(),null);
    }
    private void captureReadingAnchor(PreviewWebView web,int token){
        web.evaluateJavascript(ArchiveScrollScript.position(),result->{
            if(token!=generation||reader!=web||active==null||isFinishing()||isDestroyed())return;
            try{
                Object decoded=new JSONTokener(result).nextValue();
                if(!(decoded instanceof String)||((String)decoded).isEmpty())return;
                JSONObject position=new JSONObject((String)decoded);
                int ordinal=position.optInt("ordinal",-1),part=position.optInt("part",-1);
                if(ordinal<0||ordinal>=active.messages||part<0)return;
                anchorOrdinal=ordinal;anchorPart=part;anchorY=position.optInt("y",0);
            }catch(Exception ignored){/* Keep the last verified local reading anchor. */}
        });
    }
    private void rememberReadingPlace(){
        if(active==null||indexedReaderVisit||!"reader".equals(viewMode))return;
        int y=reader==null?savedScroll:reader.getScrollY();
        prefs.edit().putInt("archive_chat_position_"+active.id,windowOffset)
                .putInt("archive_chat_scroll_"+active.id,Math.max(0,y))
                .putString("archive_chat_anchor_generation_"+active.id,active.generationId)
                .putInt("archive_chat_anchor_ordinal_"+active.id,anchorOrdinal)
                .putInt("archive_chat_anchor_part_"+active.id,anchorPart)
                .putInt("archive_chat_anchor_y_"+active.id,anchorY).apply();
    }
    @Override protected void onStop(){
        rememberReadingPlace();
        super.onStop();
    }
    private void returnToSearch(){
        String term=returnSearchTerm,scope=returnSearchScope;
        int offset=returnSearchOffset;
        if(term==null||term.isEmpty()){showHome();return;}
        rememberReadingPlace();
        showSearch(term,scope,offset);
    }
    private void back(){
        if(fullscreen && "reader".equals(viewMode)){toggleFocus();return;}
        if("reader".equals(viewMode)&&returnUnifiedSearch){
            // Return to the still-live results Activity without destroying Archive tabs.
            // If Android recreated Search, its own contract requires explicit re-search.
            Intent results=new Intent(this,UnifiedSearchActivity.class);
            results.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try{
                startActivity(results);
                rememberReadingPlace();
                returnUnifiedSearch=false;
                return;
            }catch(RuntimeException unavailable){
                status("Search results cannot be reopened. Your conversation remains available.");
            }
        }
        if("reader".equals(viewMode)&&returnSearchTerm!=null){returnToSearch();return;}
        if(inspectorImportSource!=null&&("imported".equals(viewMode)
                ||("reader".equals(viewMode)&&active!=null&&inspectorImportSource.equals(active.archiveId)))){
            rememberReadingPlace();
            if("reader".equals(viewMode)&&importedSourceList)
                showImportedSource(inspectorImportSource,importedSourcePage);
            else finish();
            return;
        }
        if("reader".equals(viewMode)){
            while(!priorArchiveChats.isEmpty()){
                String prior=priorArchiveChats.remove(priorArchiveChats.size()-1);
                ArchiveDatabase.Conversation previous=findConversation(prior);
                if(previous!=null){
                    rememberReadingPlace();
                    openChat(previous);
                    return;
                }
            }
        }
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