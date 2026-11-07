package com.example.markdowndesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private boolean searchExecuted;
    private final Handler rootSearchHandler=new Handler(Looper.getMainLooper());
    private final ArrayDeque<WorkspaceRootStore.RootState> pendingRoots=new ArrayDeque<>();
    private final ArrayList<FolderHit> folderHits=new ArrayList<>();
    private final Set<String> seenFolderHits=new HashSet<>();
    private int searchedRoots,skippedRoots,limitedRoots,failedRoots,totalRoots,shownFolderHits;
    private static final int FOLDER_PAGE=100;
    private static final class FolderHit {
        final WorkspaceLiteralSearch.FileHit file;
        final WorkspaceLiteralSearch.Match match;
        final String term,rootLabel;
        FolderHit(String rootLabel,WorkspaceLiteralSearch.FileHit file,WorkspaceLiteralSearch.Match match,String term){
            this.rootLabel=rootLabel;this.file=file;this.match=match;this.term=term;
        }
    }

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        SharedPreferences p=getSharedPreferences("markdown_desk",MODE_PRIVATE);
        palette=UiTheme.palette(p.getInt("ui_theme",0),p.getBoolean("dark_theme",false));
        database=new ArchiveDatabase(this,"session");
        setTitle("Search · Markdown Desk");
        build();
        refreshFolders();
        restoreSearchState(saved);
    }
    /** Restore search controls by URI identity, not folder list position. Never
     * restart a physical SAF scan automatically after rotation or process death.
     */
    private void restoreSearchState(Bundle saved){
        if(saved==null)return;
        query.setText(saved.getString("search_query",""));
        speaker.setText(saved.getString("search_speaker",""));
        fromDate.setText(saved.getString("search_from",""));
        toDate.setText(saved.getString("search_to",""));
        noteTag.setText(saved.getString("search_tag",""));
        scope.setSelection(Math.max(0,Math.min(UnifiedSearchScope.values().length-1,
                saved.getInt("search_scope",0))));
        noteKind.setSelection(Math.max(0,Math.min(NOTE_TYPES.length-1,
                saved.getInt("search_kind",0))));
        noteSource.setSelection(Math.max(0,Math.min(NOTE_SOURCES.length-1,
                saved.getInt("search_source",0))));
        String savedFolder=saved.getString("search_folder_uri","");
        for(int i=0;i<roots.size();i++)
            if(roots.get(i).root.uri.toString().equals(savedFolder)){
                folder.setSelection(i+1);break;
            }
        searchExecuted=saved.getBoolean("search_executed",false);
        if(searchExecuted)
            status("Search controls restored. Tap Search to refresh results after rotation.");
    }
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
        out.putString("search_query",query.getText().toString());
        out.putString("search_speaker",speaker.getText().toString());
        out.putString("search_from",fromDate.getText().toString());
        out.putString("search_to",toDate.getText().toString());
        out.putString("search_tag",noteTag.getText().toString());
        out.putInt("search_scope",scope.getSelectedItemPosition());
        out.putInt("search_kind",noteKind.getSelectedItemPosition());
        out.putInt("search_source",noteSource.getSelectedItemPosition());
        int index=folder.getSelectedItemPosition()-1;
        if(index>=0&&index<roots.size())
            out.putString("search_folder_uri",roots.get(index).root.uri.toString());
        out.putBoolean("search_executed",searchExecuted);
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
        body.addView(text("Authorized Markdown / JSON folders: all granted roots by default, or select one to narrow.",13));
        folder=choose(new String[]{"All granted saved folders"});body.addView(folder,new LinearLayout.LayoutParams(-1,dp(48)));
        body.addView(button("Search",v->search()));
        body.addView(button("Stop search",v->cancel()));
        results=stack();body.addView(results);
        root.addView(page,new LinearLayout.LayoutParams(-1,0,1f));setContentView(root);
    }
    private void refreshFolders(){
        try{
            roots=new WorkspaceRootStore(this).list(getContentResolver());
            ArrayList<String> labels=new ArrayList<>();labels.add("All granted saved folders");
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
    private void cancel(){cancelActive();status("Search cancelled; no documents changed.");}
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
        searchExecuted=true;
        final int token=++generation;
        UnifiedSearchScope selected=UnifiedSearchScope.values()[scope.getSelectedItemPosition()];
        if(selected.includesArchive()||selected.includesNotes())searchIndex(token,term,selected);
        if(selected.includesFolder())searchFolder(token,term);
    }
    private void cancelActive(){
        if(folderJob!=null){folderJob.cancel();folderJob=null;}
        pendingRoots.clear();folderHits.clear();seenFolderHits.clear();
        searchedRoots=skippedRoots=limitedRoots=failedRoots=totalRoots=shownFolderHits=0;
        generation++;
    }
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
    /** Search ALL currently granted saved folders unless the user narrows to one.
     * The source-owned loader allows only one physical scan at a time. Never
     * enqueue a new scan inside its delivery callback: its ownership retires after
     * that callback returns. Main-handler post supplies the required boundary.
     */
    private void searchFolder(int token,String term){
        int selected=folder.getSelectedItemPosition()-1;
        if(selected>=roots.size()){status("Selected folder is unavailable. Reopen Files.");return;}
        pendingRoots.clear();folderHits.clear();seenFolderHits.clear();
        searchedRoots=skippedRoots=limitedRoots=failedRoots=shownFolderHits=0;
        if(selected>=0)pendingRoots.addLast(roots.get(selected));
        else pendingRoots.addAll(roots);
        totalRoots=pendingRoots.size();
        if(totalRoots==0){
            status("No saved folders. Indexed Archive and notes may still have matches; add a folder in Files to include documents.");
            return;
        }
        scanNextRoot(token,term);
    }
    private void scanNextRoot(int token,String term){
        if(token!=generation||isFinishing()||isDestroyed())return;
        if(folderJob!=null)return;
        while(!pendingRoots.isEmpty()){
            WorkspaceRootStore.RootState next=pendingRoots.removeFirst();
            if(next.access!=WorkspaceRootStore.Access.CURRENT){
                skippedRoots++;
                status("Folder not searched (grant missing): "+next.root.label);
                continue;
            }
            try{
                WorkspaceSearchLoader.Ticket ticket=WorkspaceSearchLoader.trySearch(this,
                        next.root.uri,term,WorkspaceLiteralSearch.Limits.defaults(),delivery->{
                    if(token!=generation||isFinishing()||isDestroyed())return;
                    folderJob=null;
                    searchedRoots++;
                    if(delivery.result==null){
                        failedRoots++;
                        status("Folder failed: "+next.root.label+" · no documents changed");
                    }else {
                        WorkspaceLiteralSearch.Result r=delivery.result;
                        if(!"COMPLETE".equals(String.valueOf(r.state)))limitedRoots++;
                        status("Folder "+next.root.label+": "+r.hits.size()+" matching files · "+r.state+
                                (r.reasons.isEmpty()?"":" · limits: "+r.reasons));
                        for(WorkspaceLiteralSearch.FileHit file:r.hits){
                            if(file.uri==null)continue;
                            for(WorkspaceLiteralSearch.Match match:file.matches){
                                String key=file.uri.toString()+"\t"+file.hash+"\t"+match.offsetUtf16;
                                if(seenFolderHits.add(key))
                                    folderHits.add(new FolderHit(next.root.label,file,match,term));
                            }
                        }
                    }
                    // Loader releases its process-wide admission only AFTER this
                    // callback returns; a synchronous trySearch would spuriously BUSY.
                    if(!rootSearchHandler.post(()->scanNextRoot(token,term))){
                        failedRoots+=pendingRoots.size();
                        pendingRoots.clear();
                        finishFolderSearch(token);
                    }
                });
                if(ticket==null){
                    failedRoots+=pendingRoots.size()+1;
                    pendingRoots.clear();
                    status("Protected folder search engine is busy. Folder results are incomplete; try again after it finishes.");
                    finishFolderSearch(token);
                    return;
                }
                folderJob=ticket;
                return;
            }catch(RuntimeException failure){
                failedRoots++;
                status("Folder unavailable: "+next.root.label+" · "+failure.getClass().getSimpleName());
                // A poisoned physical owner remains protected by WorkspaceSearchLoader.
                // A following trySearch will report BUSY instead of overlapping it.
            }
        }
        finishFolderSearch(token);
    }
    private void finishFolderSearch(int token){
        if(token!=generation||isFinishing()||isDestroyed())return;
        status("Folder search finished: "+searchedRoots+" scanned of "+totalRoots+
                "; "+skippedRoots+" missing grants, "+limitedRoots+" limited, "+
                failedRoots+" failed/busy; "+folderHits.size()+" distinct passage matches"+
                (skippedRoots+limitedRoots+failedRoots>0?" · INCOMPLETE":""));
        showMoreFolderMatches(token);
    }
    private void showMoreFolderMatches(int token){
        if(token!=generation||isFinishing()||isDestroyed())return;
        int end=Math.min(folderHits.size(),shownFolderHits+FOLDER_PAGE);
        for(int i=shownFolderHits;i<end;i++){
            FolderHit hit=folderHits.get(i);
            String label=(hit.file.json?"JSON · ":"MD · ")+hit.file.displayName+
                    " · "+hit.rootLabel+" · line "+hit.match.line+"\n"+hit.match.snippet;
            add(label,v->{
                if(hit.file.json)openJsonHit(hit.file,hit.match,hit.term);
                else openMarkdownHit(hit.file,hit.match,hit.term);
            });
        }
        shownFolderHits=end;
        if(shownFolderHits<folderHits.size()){
            Button more=button("More folder matches ("+(folderHits.size()-shownFolderHits)+" remaining)",v->{
                v.setEnabled(false);
                results.removeView(v);
                showMoreFolderMatches(token);
            });
            results.addView(more);
        }
    }
    /** JSON files have a separate structured source reader. The literal scan
     * does not mint a JSON node/path identity, so do not claim an exact jump.
     */
    private void openJsonHit(WorkspaceLiteralSearch.FileHit file,
                             WorkspaceLiteralSearch.Match match,String literal){
        if(file==null||file.uri==null)return;
        Intent intent=new Intent(Intent.ACTION_VIEW,file.uri)
                .putExtra(UnifiedSearchJump.EXTRA_URI,file.uri.toString())
                .putExtra(UnifiedSearchJump.EXTRA_HASH,file.hash)
                .putExtra(UnifiedSearchJump.EXTRA_LITERAL,literal)
                .putExtra(UnifiedSearchJump.EXTRA_OFFSET,match.offsetUtf16)
                .putExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,true)
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
        intent.putExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,true);
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
        intent.putExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,true);
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
        intent.putExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,true);
        try{startActivity(intent);}catch(Exception e){error("Cannot open archived passage",e);}
    }
    @Override protected void onDestroy(){cancelActive();io.shutdownNow();if(database!=null)database.close();super.onDestroy();}
}