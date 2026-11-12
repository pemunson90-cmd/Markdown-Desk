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
import android.widget.AdapterView;
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
    private LinearLayout root,results,filters,archiveFilters,noteFilters,folderFilters;
    private Button stopSearch,filterToggle;
    private TextView searchStatus,filterSummary;
    private String runningScope;
    private int indexedMatches;
    private boolean moreIndexedMatches,searchIncomplete;
    private boolean indexPending,foldersPending;
    private EditText query,speaker,fromDate,toDate,noteTag;
    private Spinner scope,folder,noteKind,noteSource;
    private static final String[] NOTE_TYPES={"","note","highlight","bookmark","source_link","cross_link","external_link"};
    private static final String[] NOTE_SOURCES={"","markdown","archive","json"};
    private List<WorkspaceRootStore.RootState> roots=new ArrayList<>();
    private volatile int generation;
    static final String EXTRA_ARCHIVE_SCOPE="search_archive_scope";
    static final String EXTRA_RETURN_TO_JSON="search_return_to_json";
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
        if(saved==null&&getIntent().getBooleanExtra(EXTRA_ARCHIVE_SCOPE,false))
            scope.setSelection(UnifiedSearchScope.ARCHIVE.ordinal());
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
            updateSearchStatus("Search settings restored. Tap Search to refresh results.");
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
    private ArrayAdapter<String> choiceAdapter(List<String> items){
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,items){
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
                TextView choice=(TextView)super.getView(position,recycled,parent);
                choice.setTextColor(palette.text);
                return choice;
            }
            @Override public View getDropDownView(int position,View recycled,android.view.ViewGroup parent){
                TextView choice=(TextView)super.getDropDownView(position,recycled,parent);
                choice.setTextColor(palette.text);
                choice.setBackgroundColor(palette.surface);
                return choice;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }
    private Spinner choose(String[] items){
        Spinner spinner=new Spinner(this);
        spinner.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(palette.surface));
        spinner.setAdapter(choiceAdapter(java.util.Arrays.asList(items)));
        return spinner;
    }
    private void build(){
        root=stack();root.setFitsSystemWindows(true);root.setBackgroundColor(palette.canvas);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(palette.surface);
        Button back=button("‹",v->leaveSearch());
        back.setContentDescription("Back to previous workspace");
        header.addView(back,new LinearLayout.LayoutParams(dp(52),dp(48)));
        header.addView(text("Search all your workspaces",17),new LinearLayout.LayoutParams(0,-2,1f));
        root.addView(header);
        LinearLayout body=stack();
        body.setPadding(dp(12),dp(4),dp(12),dp(4));
        query=field("Find a phrase, title, note, or link");body.addView(query);
        query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        query.setOnEditorActionListener((field,action,event)->{
            if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)return false;
            search();
            return true;
        });
        scope=choose(UnifiedSearchScope.labels());scope.setContentDescription("Search sources");
        scope.setMinimumHeight(dp(48));
        body.addView(scope,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this);
        actions.addView(button("Search",v->search()),new LinearLayout.LayoutParams(0,-2,1f));
        stopSearch=button("Stop",v->cancel());
        stopSearch.setVisibility(View.GONE);
        actions.addView(stopSearch,new LinearLayout.LayoutParams(0,-2,1f));
        body.addView(actions);
        searchStatus=text("",13);
        searchStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        searchStatus.setVisibility(View.GONE);
        body.addView(searchStatus);
        root.addView(body);
        ScrollView page=new ScrollView(this);
        LinearLayout content=stack();
        page.addView(content);
        filters=stack();
        filters.setVisibility(View.GONE);
        filterToggle=button("Search filters",v->showFilters(filters.getVisibility()!=View.VISIBLE));
        content.addView(filterToggle);
        filterSummary=text("",13);
        filterSummary.setVisibility(View.GONE);
        content.addView(filterSummary);
        content.addView(filters);

        archiveFilters=stack();
        archiveFilters.addView(text("Indexed conversation filters",13));
        speaker=field("Speaker");archiveFilters.addView(speaker);
        fromDate=field("From YYYY-MM-DD");archiveFilters.addView(fromDate);
        toDate=field("Through YYYY-MM-DD");archiveFilters.addView(toDate);
        filters.addView(archiveFilters);

        noteFilters=stack();
        noteFilters.addView(text("Saved note filters",13));
        noteKind=choose(new String[]{"Any kind","Note","Highlight","Bookmark",
                "Source link","Cross-link","External link"});
        noteKind.setContentDescription("Saved annotation kind");
        noteFilters.addView(noteKind,new LinearLayout.LayoutParams(-1,-2));
        noteTag=field("Exact tag");noteFilters.addView(noteTag);
        noteSource=choose(new String[]{"Any note source","Markdown documents","Archive conversations","General JSON files"});
        noteSource.setContentDescription("Saved annotation origin");
        noteFilters.addView(noteSource,new LinearLayout.LayoutParams(-1,-2));
        filters.addView(noteFilters);

        folderFilters=stack();
        folderFilters.addView(text("Saved folder filter",13));
        folder=choose(new String[]{"All granted saved folders"});
        folder.setContentDescription("Authorized document folder scope");
        folderFilters.addView(folder,new LinearLayout.LayoutParams(-1,-2));
        filters.addView(folderFilters);

        AdapterView.OnItemSelectedListener filterSelection=new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                refreshFilterSummary();
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        };
        for(Spinner choice:new Spinner[]{scope,noteKind,noteSource,folder})
            choice.setOnItemSelectedListener(filterSelection);
        android.text.TextWatcher filterText=new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){refreshFilterSummary();}
            @Override public void afterTextChanged(android.text.Editable value){}
        };
        for(EditText field:new EditText[]{speaker,fromDate,toDate,noteTag})
            field.addTextChangedListener(filterText);
        refreshFilterSummary();
        results=stack();content.addView(results);
        root.addView(page,new LinearLayout.LayoutParams(-1,0,1f));setContentView(root);
    }
    private UnifiedSearchScope selectedScope(){
        return UnifiedSearchScope.values()[Math.max(0,scope.getSelectedItemPosition())];
    }
    private void showFilters(boolean expanded){
        filters.setVisibility(expanded?View.VISIBLE:View.GONE);
        filterToggle.setText(expanded?"Hide search filters":"Search filters");
    }
    private void refreshFilterSummary(){
        UnifiedSearchScope selected=selectedScope();
        archiveFilters.setVisibility(selected.includesArchive()?View.VISIBLE:View.GONE);
        noteFilters.setVisibility(selected.includesNotes()?View.VISIBLE:View.GONE);
        folderFilters.setVisibility(selected.includesFolder()?View.VISIBLE:View.GONE);
        List<String> active=new ArrayList<>();
        if(selected.includesArchive()){
            if(speaker.length()>0)active.add("Speaker "+speaker.getText().toString().trim());
            if(fromDate.length()>0)active.add("From "+fromDate.getText().toString().trim());
            if(toDate.length()>0)active.add("Through "+toDate.getText().toString().trim());
        }
        if(selected.includesNotes()){
            if(noteKind.getSelectedItemPosition()>0)active.add(noteKind.getSelectedItem().toString());
            if(noteTag.length()>0)active.add("Tag "+noteTag.getText().toString().trim());
            if(noteSource.getSelectedItemPosition()>0)active.add(noteSource.getSelectedItem().toString());
        }
        if(selected.includesFolder()&&folder.getSelectedItemPosition()>0)
            active.add(folder.getSelectedItem().toString());
        filterSummary.setText(String.join(" · ",active));
        filterSummary.setVisibility(active.isEmpty()?View.GONE:View.VISIBLE);
    }
    private void refreshFolders(){
        try{
            roots=new WorkspaceRootStore(this).list(getContentResolver());
            ArrayList<String> labels=new ArrayList<>();labels.add("All granted saved folders");
            for(WorkspaceRootStore.RootState root:roots){
                labels.add(root.root.label+(root.access==WorkspaceRootStore.Access.CURRENT?"":" · access required"));
            }
            folder.setAdapter(choiceAdapter(labels));
        }catch(Exception failure){roots.clear();error("Saved folder access",failure);}
    }
    private void add(String label,View.OnClickListener click){results.addView(button(label,click));}
    private void status(String message){results.addView(text(message,13));}
    private void error(String title,Exception ex){new AlertDialog.Builder(this).setTitle(title)
        .setMessage(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage())
        .setPositiveButton("OK",null).show();}
    private void updateSearchStatus(String message){
        searchStatus.setText(message);
        searchStatus.setVisibility(View.VISIBLE);
        stopSearch.setVisibility(indexPending||foldersPending?View.VISIBLE:View.GONE);
    }
    private void finishSearchIfReady(){
        if(indexPending||foldersPending)return;
        int matches=indexedMatches+folderHits.size();
        String outcome=matches==0?(searchIncomplete?"Search incomplete":"No matches"):
                (moreIndexedMatches?"At least ":"")+matches+(matches==1?" match":" matches");
        if(matches>0&&searchIncomplete)outcome+=" · Some sources could not be fully searched";
        updateSearchStatus(outcome+" · "+runningScope);
    }
    private void cancel(){
        cancelActive();
        updateSearchStatus("Search stopped");
    }
    private void search(){
        final String term=query.getText().toString().trim();
        if(term.isEmpty()){query.setError("Enter a phrase");return;}
        final UnifiedSearchScope selected=selectedScope();
        ArchiveSearchSpec archiveSpec=null;
        try {
            if(selected.includesArchive())
                archiveSpec=new ArchiveSearchSpec(term,speaker.getText().toString(),
                        fromDate.getText().toString(),toDate.getText().toString());
        }catch(IllegalArgumentException invalid){
            showFilters(true);
            error("Check indexed conversation search",invalid);return;
        }
        cancelActive();
        results.removeAllViews();
        searchExecuted=true;
        final int token=++generation;
        runningScope=selected.title;
        indexedMatches=0;
        moreIndexedMatches=searchIncomplete=false;
        showFilters(false);
        android.view.inputmethod.InputMethodManager keyboard=getSystemService(android.view.inputmethod.InputMethodManager.class);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(query.getWindowToken(),0);
        query.clearFocus();
        indexPending=selected.includesArchive()||selected.includesNotes();
        foldersPending=selected.includesFolder();
        updateSearchStatus("Searching "+runningScope.toLowerCase(java.util.Locale.ROOT)+"…");
        if(indexPending)searchIndex(token,term,selected,archiveSpec);
        if(foldersPending)searchFolder(token,term);
    }
    private void cancelActive(){
        indexPending=false;foldersPending=false;
        if(folderJob!=null){folderJob.cancel();folderJob=null;}
        pendingRoots.clear();folderHits.clear();seenFolderHits.clear();
        searchedRoots=skippedRoots=limitedRoots=failedRoots=totalRoots=shownFolderHits=0;
        generation++;
    }
    private void searchIndex(int token,String term,UnifiedSearchScope selected,ArchiveSearchSpec archiveSpec){
        final String sender=archiveSpec==null?null:archiveSpec.speaker;
        final String from=archiveSpec==null?null:archiveSpec.fromDate,to=archiveSpec==null?null:archiveSpec.toDate;
        final String type=NOTE_TYPES[noteKind.getSelectedItemPosition()];
        final String tag=noteTag.getText().toString().trim();
        final String origin=NOTE_SOURCES[noteSource.getSelectedItemPosition()];
        io.execute(()->{
            try{
                List<ArchiveDatabase.Hit> hits=selected.includesArchive()?database.search(term,null,sender,from,to,100,0,()->generation!=token):new ArrayList<>();
                List<ArchiveJournal.Annotation> notes=selected.includesNotes()?ArchiveAnnotationSearch.pageFiltered(
                    new File(getFilesDir(),"claude-archive/annotations-v2.journal"),term,null,100,0,type,tag,origin):new ArrayList<>();
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed())return;
                    indexedMatches=hits.size()+notes.size();
                    moreIndexedMatches=hits.size()==100||notes.size()==100;
                    for(ArchiveDatabase.Hit h:hits)add("JSON · "+h.chatTitle+(h.created==null||h.created.isEmpty()?"":" · "+h.created)+"\n"+h.excerpt,v->openArchiveHit(h));
                    if(hits.size()==100) add("More message matches",v->{v.setEnabled(false);moreMessages(token,term,sender,from,to,100);});
                    for(ArchiveJournal.Annotation a:notes)add("Note · "+a.chatTitle+" · "+a.messageDate+"\n"+a.label,v->openAnnotationHit(a));
                    if(notes.size()==100) add("More saved-note matches",v->{v.setEnabled(false);moreNotes(token,term,100,type,tag,origin);});
                    indexPending=false;
                    finishSearchIfReady();
                });
            }catch(Exception failure){runOnUiThread(()->{
                if(token!=generation)return;
                indexPending=false;
                searchIncomplete=true;
                status("Indexed search unavailable");
                finishSearchIfReady();
                error("Archive search unavailable",failure);
            });}
        });
    }
    private void moreMessages(int token,String term,String speaker,String from,String to,int offset){
        if(token!=generation)return;
        io.execute(()->{
            try{
                List<ArchiveDatabase.Hit> page=database.search(term,null,speaker,from,to,100,offset,()->generation!=token);
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed())return;
                    for(ArchiveDatabase.Hit h:page)add("JSON · "+h.chatTitle+(h.created==null||h.created.isEmpty()?"":" · "+h.created)+"\n"+h.excerpt,
                        v->openArchiveHit(h));
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
        if(selected>=roots.size()){
            searchIncomplete=true;
            status("Selected folder is unavailable. Reopen Files.");
            foldersPending=false;
            finishSearchIfReady();
            return;
        }
        pendingRoots.clear();folderHits.clear();seenFolderHits.clear();
        searchedRoots=skippedRoots=limitedRoots=failedRoots=shownFolderHits=0;
        if(selected>=0)pendingRoots.addLast(roots.get(selected));
        else pendingRoots.addAll(roots);
        totalRoots=pendingRoots.size();
        if(totalRoots==0){
            searchIncomplete=true;
            status("No saved folders to search. Add a folder in Files to search its documents.");
            foldersPending=false;
            finishSearchIfReady();
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
        searchIncomplete|=skippedRoots+limitedRoots+failedRoots>0;
        showMoreFolderMatches(token);
        foldersPending=false;
        finishSearchIfReady();
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
        final boolean inspector=WorkspaceDeepLinks.isInspectorLink(source);
        if(!markdown&&!archive&&!inspector){
            error("Cannot open saved note",new IllegalArgumentException("Unrecognized source link"));
            return;
        }
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(source))
                .setClass(this,markdown?MainActivity.class:
                        archive?ArchiveWorkspaceActivity.class:JsonExplorerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,true);
        // Opening a saved link does not create or impersonate a provider URI grant.
        try{startActivity(intent);}
        catch(Exception e){error("Cannot open saved note",e);}
    }
    private void openArchiveHit(ArchiveDatabase.Hit hit){
        if(hit==null||hit.chatId==null)return;
        Intent intent=new Intent(this,ArchiveWorkspaceActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra(ArchiveWorkspaceActivity.EXTRA_INDEXED_PASSAGE,
                ArchiveWorkspaceActivity.indexedPassageBundle(hit));
        intent.putExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,true);
        try{startActivity(intent);}catch(Exception e){error("Cannot open archived passage",e);}
    }
    private void leaveSearch(){
        if(getIntent().getBooleanExtra(EXTRA_RETURN_TO_JSON,false)){
            startActivity(new Intent(this,JsonExplorerActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        finish();
    }
    @Override public void onBackPressed(){leaveSearch();}
    @Override protected void onDestroy(){cancelActive();io.shutdownNow();if(database!=null)database.close();super.onDestroy();}
}