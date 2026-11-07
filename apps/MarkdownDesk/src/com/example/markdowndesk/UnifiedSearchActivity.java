package com.example.markdowndesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.View;
import com.example.markdowndesk.archive.ArchiveJournal;
import com.example.markdowndesk.archive.ArchiveMarkdownIndexes;
import com.example.markdowndesk.workspace.WorkspaceDeepLinks;
import com.example.markdowndesk.workspace.archive.ArchiveAnnotationSearch;
import com.example.markdowndesk.workspace.archive.ArchiveSearchSpec;
import com.example.markdowndesk.workspace.search.UnifiedSearchScope;
import com.example.markdowndesk.workspace.search.UnifiedSearchJump;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Unified local search across existing Archive, annotation and authorized SAF
 * folder engines. Each result keeps its own source identity and navigation.
 * A limited folder scan is labelled LIMITED, never advertised as exhaustive.
 */
public final class UnifiedSearchActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private ArchiveDatabase database;
    private WorkspaceSearchLoader.Ticket folderJob;
    private UiTheme.Palette palette;
    private LinearLayout root,results;
    private EditText query,speaker,fromDate,toDate,noteTag;
    private Spinner scope,folder,noteKind,noteSource;
    private static final String[] NOTE_TYPES={"","note","highlight","bookmark","source_link","cross_link","external_link"};
    private static final String[] NOTE_SOURCES={"","markdown","archive"};
    private List<WorkspaceRootStore.RootState> roots=new ArrayList<>();
    private int generation;

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        SharedPreferences p=getSharedPreferences("markdown_desk",MODE_PRIVATE);
        palette=UiTheme.palette(p.getInt("ui_theme",0),p.getBoolean("dark_theme",false));
        database=new ArchiveDatabase(this,"session");
        setTitle("Search · Markdown Desk");
        build();
        refreshFolders();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout stack(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(palette.text);v.setPadding(dp(10),dp(8),dp(10),dp(8));return v;}
    private Button button(String label,View.OnClickListener action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(palette.text);b.setTextSize(14);
        b.setBackground(UiTheme.rounded(palette.surface,dp(9),palette.border,dp(1)));
        b.setMinHeight(dp(48));b.setOnClickListener(action);return b;}
    private EditText field(String hint){EditText v=new EditText(this);v.setSingleLine(true);v.setHint(hint);
        v.setTextColor(palette.text);v.setHintTextColor(palette.muted);v.setTextSize(15);return v;}
    private Spinner choose(String[] items){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);return s;}
    private void build(){
        root=stack();root.setFitsSystemWindows(true);root.setBackgroundColor(palette.canvas);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(palette.surface);
        header.addView(button("‹",v->finish()),new LinearLayout.LayoutParams(dp(52),dp(48)));
        header.addView(text("Search all your workspaces",17),new LinearLayout.LayoutParams(0,-2,1f));
        root.addView(header);
        ScrollView page=new ScrollView(this);LinearLayout body=stack();
        body.setPadding(dp(12),dp(8),dp(12),dp(20));page.addView(body);
        query=field("Find a phrase, title, note, or link");body.addView(query);
        scope=choose(UnifiedSearchScope.labels());body.addView(scope,new LinearLayout.LayoutParams(-1,dp(48)));
        speaker=field("Speaker (optional)");body.addView(speaker);
        fromDate=field("From YYYY-MM-DD (optional)");body.addView(fromDate);
        toDate=field("Through YYYY-MM-DD (optional)");body.addView(toDate);
        body.addView(text("Speaker and date filters apply to indexed conversation messages only.",12));
        body.addView(text("Saved-note filters (for Notes or All searches)",13));
        noteKind=choose(new String[]{"Any kind","Note","Highlight","Bookmark",
                "Source link","Cross-link","External link"});
        body.addView(noteKind,new LinearLayout.LayoutParams(-1,dp(48)));
        noteTag=field("Exact tag (optional)");body.addView(noteTag);
        noteSource=choose(new String[]{"Any note source","Markdown documents","Archive conversations"});
        body.addView(noteSource,new LinearLayout.LayoutParams(-1,dp(48)));
        body.addView(text("Authorized Markdown / JSON folder (for folder or All searches)",13));
        folder=choose(new String[]{"No saved folder selected"});body.addView(folder,new LinearLayout.LayoutParams(-1,dp(48)));
        body.addView(button("Search",v->search()));
        body.addView(button("Stop search",v->cancel()));
        results=stack();body.addView(results);
        root.addView(page,new LinearLayout.LayoutParams(-1,0,1f));setContentView(root);
    }
    private void refreshFolders(){
        try{
            roots=new WorkspaceRootStore(this).list(getContentResolver());
            ArrayList<String> labels=new ArrayList<>();labels.add("No saved folder selected");
            for(WorkspaceRootStore.RootState root:roots){
                labels.add(root.root.label+(root.access==WorkspaceRootStore.Access.CURRENT?"":" · access required"));
            }
            ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);folder.setAdapter(a);
        }catch(Exception failure){roots.clear();error("Saved folder access",failure);}
    }
    private void add(String label,View.OnClickListener click){results.addView(button(label,click));}
    private void status(String message){results.addView(text(message,13));}
    private void error(String title,Exception ex){new AlertDialog.Builder(this).setTitle(title)
        .setMessage(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage())
        .setPositiveButton("OK",null).show();}
    private void cancel(){generation++;if(folderJob!=null){folderJob.cancel();folderJob=null;}
        status("Search cancelled; no documents changed.");}
    private void search(){
        final String term=query.getText().toString().trim();
        if(term.isEmpty()){query.setError("Enter a phrase");return;}
        try {
            // Validate actual calendar dates and ordering, not only their shape.
            new ArchiveSearchSpec(term,speaker.getText().toString(),
                    fromDate.getText().toString(),toDate.getText().toString());
        }catch(IllegalArgumentException invalid){
            error("Invalid search filters",invalid);return;
        }
        cancelActive();
        results.removeAllViews();
        final int token=++generation;
        UnifiedSearchScope selected=UnifiedSearchScope.values()[scope.getSelectedItemPosition()];
        if(selected.includesArchive()||selected.includesNotes())searchIndex(token,term,selected);
        if(selected.includesFolder())searchFolder(token,term);
    }
    private void cancelActive(){if(folderJob!=null){folderJob.cancel();folderJob=null;}generation++;}
    private void searchIndex(int token,String term,UnifiedSearchScope selected){
        final String sender=speaker.getText().toString().trim();
        final String from=fromDate.getText().toString().trim(),to=toDate.getText().toString().trim();
        final String type=NOTE_TYPES[noteKind.getSelectedItemPosition()];
        final String tag=noteTag.getText().toString().trim();
        final String origin=NOTE_SOURCES[noteSource.getSelectedItemPosition()];
        io.execute(()->{
            try{
                List<ArchiveDatabase.Hit> hits=selected.includesArchive()?database.search(term,null,sender,from,to,100,0):new ArrayList<>();
                List<ArchiveJournal.Annotation> notes=selected.includesNotes()?ArchiveAnnotationSearch.pageFiltered(
                    new File(getFilesDir(),"claude-archive/annotations-v2.journal"),term,null,100,0,type,tag,origin):new ArrayList<>();
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed())return;
                    status("Indexed messages: "+hits.size()+" on this page; saved notes: "+notes.size()+" on this page");
                    for(ArchiveDatabase.Hit h:hits)add("JSON · "+h.chatTitle+" · "+h.created+"\n"+h.excerpt,v->openArchiveHit(h.chatId,h.messageId));
                    if(hits.size()==100) add("More message matches",v->{v.setEnabled(false);moreMessages(token,term,sender,from,to,100);});
                    for(ArchiveJournal.Annotation a:notes)add("Note · "+a.chatTitle+" · "+a.messageDate+"\n"+a.label,v->openAnnotationHit(a));
                    if(notes.size()==100) add("More saved-note matches",v->{v.setEnabled(false);moreNotes(token,term,100,type,tag,origin);});
                });
            }catch(Exception failure){runOnUiThread(()->{if(token==generation)error("Archive search unavailable",failure);});}
        });
    }
    private void moreMessages(int token,String term,String speaker,String from,String to,int offset){
        if(token!=generation)return;
        io.execute(()->{
            try{
                List<ArchiveDatabase.Hit> page=database.search(term,null,speaker,from,to,100,offset);
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed())return;
                    for(ArchiveDatabase.Hit h:page)add("JSON · "+h.chatTitle+" · "+h.created+"\n"+h.excerpt,
                        v->openArchiveHit(h.chatId,h.messageId));
                    if(page.size()==100)add("More message matches",v->{v.setEnabled(false);moreMessages(token,term,speaker,from,to,offset+100);});
                    if(page.isEmpty())status("All indexed message matches shown");
                });
            }catch(Exception failure){runOnUiThread(()->{if(token==generation)error("Cannot load more message results",failure);});}
        });
    }
    private void moreNotes(int token,String term,int offset,String type,String tag,String origin){
        if(token!=generation)return;
        io.execute(()->{
            try{
                List<ArchiveJournal.Annotation> page=ArchiveAnnotationSearch.pageFiltered(
                    new File(getFilesDir(),"claude-archive/annotations-v2.journal"),term,null,100,offset,type,tag,origin);
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed())return;
                    for(ArchiveJournal.Annotation a:page)add("Note · "+a.chatTitle+" · "+a.messageDate+"\n"+a.label,
                        v->openAnnotationHit(a));
                    if(page.size()==100)add("More saved-note matches",v->{v.setEnabled(false);moreNotes(token,term,offset+100,type,tag,origin);});
                    if(page.isEmpty())status("All saved-note matches shown");
                });
            }catch(Exception failure){runOnUiThread(()->{if(token==generation)error("Cannot load more note results",failure);});}
        });
    }
    private void searchFolder(int token,String term){
        int selected=folder.getSelectedItemPosition()-1;
        if(selected<0||selected>=roots.size()){
            status("Folder scope requires a saved, authorized Markdown/JSON folder. Choose one in Markdown Desk Files first.");return;
        }
        WorkspaceRootStore.RootState root=roots.get(selected);
        if(root.access!=WorkspaceRootStore.Access.CURRENT){status("Folder access is no longer granted. Reauthorize it from Files.");return;}
        try{
            folderJob=WorkspaceSearchLoader.trySearch(this,root.root.uri,term,
                WorkspaceLiteralSearch.Limits.defaults(),delivery->{
                    if(token!=generation)return;
                    folderJob=null;
                    if(delivery.result==null){status("Folder search did not complete. No source files changed.");return;}
                    WorkspaceLiteralSearch.Result r=delivery.result;
                    status("Markdown folder: "+r.hits.size()+" matching files · "+r.state+
                            (r.reasons.isEmpty()?"":" · limits: "+r.reasons));
                    for(WorkspaceLiteralSearch.FileHit file:r.hits){
                        for(WorkspaceLiteralSearch.Match match:file.matches){
                            String label=(file.json?"JSON · ":"MD · ")+file.displayName+
                                    " · line "+match.line+"\n"+match.snippet+
                                    (file.json?"\nOpen JSON Inspector (line jump unavailable)":"");
                            add(label,v->{
                                if(file.json)openJsonHit(file);
                                else openMarkdownHit(file,match,term);
                            });
                        }
                    }
                });
            if(folderJob==null)status("Another protected folder search is already in progress.");
        }catch(Exception e){status("Folder search unavailable: "+e.getMessage());}
    }
    /** JSON files have a separate structured source reader. The literal scan
     * does not mint a JSON node/path identity, so do not claim an exact jump.
     */
    private void openJsonHit(WorkspaceLiteralSearch.FileHit file){
        if(file==null||file.uri==null)return;
        Intent intent=new Intent(Intent.ACTION_VIEW,file.uri)
                .setClass(this,JsonExplorerActivity.class)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try{startActivity(intent);}catch(Exception failed){error("Cannot open JSON Inspector",failed);}
    }
    private void openMarkdownHit(WorkspaceLiteralSearch.FileHit file,
                                 WorkspaceLiteralSearch.Match match,String literal){
        if(file==null||match==null||file.uri==null)return;
        Intent intent=new Intent(Intent.ACTION_VIEW,file.uri);
        intent.setClassName(getPackageName(),"com.example.markdowndesk.MainActivity");
        intent.putExtra(UnifiedSearchJump.EXTRA_URI,file.uri.toString());
        intent.putExtra(UnifiedSearchJump.EXTRA_HASH,file.hash);
        intent.putExtra(UnifiedSearchJump.EXTRA_LITERAL,literal);
        intent.putExtra(UnifiedSearchJump.EXTRA_OFFSET,match.offsetUtf16);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try{startActivity(intent);}catch(Exception e){error("Cannot open Markdown file",e);}
    }
    /** Journal-backed annotations retain a real source kind, unlike indexed
     * message hits. Never reinterpret a Markdown content URI as an Archive ID.
     */
    private void openAnnotationHit(ArchiveJournal.Annotation annotation){
        if(annotation==null)return;
        final String source=ArchiveMarkdownIndexes.deepLink(annotation);
        final boolean markdown=WorkspaceDeepLinks.isDocumentLink(source);
        final boolean archive=WorkspaceDeepLinks.isArchiveLink(source);
        if(!markdown&&!archive){
            error("Cannot open saved note",new IllegalArgumentException("Unrecognized source link"));
            return;
        }
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(source))
                .setClass(this,markdown?MainActivity.class:ArchiveWorkspaceActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        // Opening a saved link does not create or impersonate a provider URI grant.
        try{startActivity(intent);}
        catch(Exception e){error("Cannot open saved note",e);}
    }
    private void openArchiveHit(String chatId,String messageId){
        if(chatId==null)return;
        String archiveId=chatId.contains("/")?chatId.substring(0,chatId.indexOf('/')):"session";
        Intent intent=new Intent();intent.setClassName(getPackageName(),"com.example.markdowndesk.ArchiveWorkspaceActivity");
        intent.setAction(Intent.ACTION_VIEW);
        Uri uri=new Uri.Builder().scheme("markdowndesk").authority("archive")
            .appendPath("v1").appendPath(archiveId).appendPath(chatId).appendPath(messageId==null?"":messageId).build();
        intent.setData(uri);intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try{startActivity(intent);}catch(Exception e){error("Cannot open archived passage",e);}
    }
    @Override protected void onDestroy(){cancelActive();io.shutdownNow();if(database!=null)database.close();super.onDestroy();}
}