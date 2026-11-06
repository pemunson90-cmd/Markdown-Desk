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
import com.example.markdowndesk.workspace.archive.ArchiveAnnotationSearch;
import com.example.markdowndesk.workspace.search.UnifiedSearchScope;
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
    private EditText query,speaker,fromDate,toDate;
    private Spinner scope,folder;
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
        body.addView(text("Authorized Markdown folder (for folder or All searches)",13));
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
    private boolean goodDate(String date){return date.isEmpty()||date.matches("\\d{4}-\\d{2}-\\d{2}");}
    private void search(){
        final String term=query.getText().toString().trim();
        if(term.isEmpty()){query.setError("Enter a phrase");return;}
        if(!goodDate(fromDate.getText().toString().trim())){fromDate.setError("Use YYYY-MM-DD");return;}
        if(!goodDate(toDate.getText().toString().trim())){toDate.setError("Use YYYY-MM-DD");return;}
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
        io.execute(()->{
            try{
                List<ArchiveDatabase.Hit> hits=selected.includesArchive()?database.search(term,null,sender,from,to,100,0):new ArrayList<>();
                List<ArchiveJournal.Annotation> notes=selected.includesNotes()?ArchiveAnnotationSearch.page(
                    new File(getFilesDir(),"claude-archive/annotations-v2.journal"),term,null,100,0):new ArrayList<>();
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed())return;
                    status("Indexed messages: "+hits.size()+" on this page; saved notes: "+notes.size()+" on this page");
                    for(ArchiveDatabase.Hit h:hits)add("JSON · "+h.chatTitle+" · "+h.created+"\n"+h.excerpt,v->openArchiveHit(h.chatId,h.messageId));
                    if(hits.size()==100) add("More message matches",v->{v.setEnabled(false);moreMessages(token,term,sender,from,to,100);});
                    for(ArchiveJournal.Annotation a:notes)add("Note · "+a.chatTitle+" · "+a.messageDate+"\n"+a.label,v->openArchiveHit(a.chatId,a.messageId));
                    if(notes.size()==100) add("More saved-note matches",v->{v.setEnabled(false);moreNotes(token,term,100);});
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
    private void moreNotes(int token,String term,int offset){
        if(token!=generation)return;
        io.execute(()->{
            try{
                List<ArchiveJournal.Annotation> page=ArchiveAnnotationSearch.page(
                    new File(getFilesDir(),"claude-archive/annotations-v2.journal"),term,null,100,offset);
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed())return;
                    for(ArchiveJournal.Annotation a:page)add("Note · "+a.chatTitle+" · "+a.messageDate+"\n"+a.label,
                        v->openArchiveHit(a.chatId,a.messageId));
                    if(page.size()==100)add("More saved-note matches",v->{v.setEnabled(false);moreNotes(token,term,offset+100);});
                    if(page.isEmpty())status("All saved-note matches shown");
                });
            }catch(Exception failure){runOnUiThread(()->{if(token==generation)error("Cannot load more note results",failure);});}
        });
    }
    private void searchFolder(int token,String term){
        int selected=folder.getSelectedItemPosition()-1;
        if(selected<0||selected>=roots.size()){
            status("Folder scope requires a saved, authorized Markdown folder. Choose one in Markdown Desk Files first.");return;
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
                        for(WorkspaceLiteralSearch.Match match:file.matches)
                            add("MD · "+file.displayName+" · line "+match.line+"\n"+match.snippet,
                                v->openMarkdownHit(file.uri));
                    }
                });
            if(folderJob==null)status("Another protected folder search is already in progress.");
        }catch(Exception e){status("Folder search unavailable: "+e.getMessage());}
    }
    private void openMarkdownHit(Uri uri){
        Intent intent=new Intent(Intent.ACTION_VIEW,uri);
        intent.setClassName(getPackageName(),"com.example.markdowndesk.MainActivity");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try{startActivity(intent);}catch(Exception e){error("Cannot open Markdown file",e);}
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