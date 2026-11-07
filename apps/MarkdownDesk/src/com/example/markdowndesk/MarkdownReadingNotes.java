package com.example.markdowndesk;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.markdowndesk.archive.ArchiveJournal;
import com.example.markdowndesk.archive.ArchiveMarkdownIndexes;
import com.example.markdowndesk.workspace.WorkspaceDeepLinks;
import com.example.markdowndesk.workspace.annotations.MarkdownNoteIdentity;
import com.example.markdowndesk.workspace.annotations.MarkdownNoteScript;
import com.example.markdowndesk.workspace.annotations.AnnotationTone;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Read-only Markdown annotations. The existing AJN2 journal owns all records. */
final class MarkdownReadingNotes {
    private final MainActivity activity;
    private final PreviewWebView web;
    private final ExecutorService worker;
    private final Supplier<Uri> source;
    private final Supplier<String> title;
    private final BooleanSupplier previewReady;
    private final Supplier<Long> revision;
    private final ArchiveJournal journal;
    private String pendingDocument, pendingNote;

    MarkdownReadingNotes(MainActivity host,PreviewWebView preview,ExecutorService io,
            Supplier<Uri> currentUri,Supplier<String> documentTitle,BooleanSupplier currentPreview,
            Supplier<Long> previewRevision) {
        activity=host;web=preview;worker=io;source=currentUri;title=documentTitle;previewReady=currentPreview;
        revision=previewRevision;
        journal=new ArchiveJournal(new File(host.getFilesDir(),"claude-archive/annotations-v2.journal"));
    }

    private String document() {
        Uri uri=source.get();
        if(uri==null)return null;
        try{return MarkdownNoteIdentity.requireDocument(uri.toString());}
        catch(IllegalArgumentException bad){return null;}
    }
    private boolean live(String uri) {
        return uri!=null&&uri.equals(document())&&!activity.isFinishing()&&!activity.isDestroyed();
    }
    private void message(String text) {
        if(!activity.isFinishing()&&!activity.isDestroyed())
            Toast.makeText(activity,text,Toast.LENGTH_LONG).show();
    }

    /** Called only after MainActivity acknowledges an exact current WebView page. */
    void decorate(String uri,BooleanSupplier samePage) {
        if(!live(uri)||!samePage.getAsBoolean())return;
        worker.execute(()->{
            try {
                ArchiveJournal.ReadResult result=journal.load();
                List<ArchiveJournal.Annotation> matches=new ArrayList<>();
                for(ArchiveJournal.Annotation a:result.annotations)
                    if(MarkdownNoteIdentity.owns(a,uri))matches.add(a);
                activity.runOnUiThread(()->{
                    if(!live(uri)||!samePage.getAsBoolean())return;
                    web.evaluateJavascript(MarkdownNoteScript.boot(), ignored->{
                        if(!live(uri)||!samePage.getAsBoolean())return;
                        web.evaluateJavascript(MarkdownNoteScript.decorate(matches), ignored2->{
                            if(!live(uri)||!samePage.getAsBoolean())return;
                            if(uri.equals(pendingDocument)&&pendingNote!=null) {
                                String noteId=pendingNote;
                                pendingDocument=null;pendingNote=null;
                                jumpVerified(matches,noteId,uri);
                            }
                        });
                    });
                    if(result.truncatedTail)
                        message("Notes journal has an interrupted record; preserve it before writing.");
                });
            } catch(Exception error) {
                activity.runOnUiThread(()->{if(live(uri))message("Reading notes unavailable: "+error.getMessage());});
            }
        });
    }

    void requestNote(String uri,String noteId) {
        if(uri==null||noteId==null||noteId.isEmpty())return;
        pendingDocument=uri;pendingNote=noteId;
        if(previewReady.getAsBoolean()&&live(uri))decorate(uri,previewReady);
    }

    private void jumpVerified(List<ArchiveJournal.Annotation> records,String id,String uri) {
        boolean owned=false;
        for(ArchiveJournal.Annotation a:records)
            if(id.equals(a.id)&&MarkdownNoteIdentity.owns(a,uri)){owned=true;break;}
        if(!owned){message("Saved note no longer belongs to this document.");return;}
        web.evaluateJavascript(MarkdownNoteScript.jump(id),result->{
            if(live(uri)&&!"true".equals(result))
                message("The quoted passage changed or is ambiguous; note was not moved.");
        });
    }

    /** Capture selection on the current acknowledged preview, without changing document bytes. */
    void annotate() {
        final String uri=document();
        if(uri==null){message("Save this document to an Android file before annotating.");return;}
        if(!previewReady.getAsBoolean()){message("Open the document in Reading mode first.");return;}
        final long atRevision=revision.get();
        web.evaluateJavascript(MarkdownNoteScript.capture(),raw->{
            if(!live(uri)||!previewReady.getAsBoolean()||revision.get()!=atRevision)return;
            try {
                Object value=new JSONTokener(raw==null?"null":raw).nextValue();
                JSONObject selected=value instanceof JSONObject?(JSONObject)value:
                        value instanceof String?new JSONObject((String)value):null;
                if(selected==null||selected.optString("quote","").trim().isEmpty()){
                    message("Select a passage in Reading mode first.");return;
                }
                showCreation(uri,selected,atRevision);
            }catch(Exception invalid){message("Cannot verify this passage selection.");}
        });
    }

    private void showCreation(String uri,JSONObject selection,long revisionAtSelection) {
        String[] names={"Highlight","Note","Save source link","Cross-link","External link","Bookmark"};
        String[] kinds={"highlight","note","source_link","cross_link","external_link","bookmark"};
        new AlertDialog.Builder(activity).setTitle("Annotate selected passage")
            .setItems(names,(d,which)->showDetails(uri,selection,kinds[which],revisionAtSelection))
            .setNegativeButton("Cancel",null).show();
    }

    private void showDetails(String uri,JSONObject selection,String kind,long revisionAtSelection) {
        LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(24,16,24,8);
        TextView quote=new TextView(activity);
        quote.setText("Selected: "+selection.optString("quote",""));
        form.addView(quote);
        EditText label=new EditText(activity);label.setHint("Label");form.addView(label);
        EditText note=new EditText(activity);note.setHint("Your note");note.setMinLines(2);form.addView(note);
        EditText target=new EditText(activity);
        if("cross_link".equals(kind)||"external_link".equals(kind)){
            target.setHint("Destination URL or Archive source link");form.addView(target);
        }
        EditText tags=new EditText(activity);tags.setHint("Tags (comma-separated)");form.addView(tags);
        Spinner tone=new Spinner(activity);
        ArrayAdapter<String> toneChoices=new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,AnnotationTone.labels());
        toneChoices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tone.setAdapter(toneChoices);
        tone.setContentDescription("Annotation highlight color");
        form.addView(tone);
        ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Save "+kind.replace('_',' '))
                .setView(scroll).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(shown->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!live(uri)||!previewReady.getAsBoolean()||revision.get()!=revisionAtSelection){
                message("Document changed; reselect passage.");return;
            }
            String destination=target.getText().toString().trim();
            if(("cross_link".equals(kind)||"external_link".equals(kind))&&destination.isEmpty()){
                target.setError("Destination required");return;
            }
            try {
                ArchiveJournal.Annotation a=MarkdownNoteIdentity.create(uri,title.get(),kind,
                    selection.getString("quote"),selection.optString("prefix",""),
                    selection.optString("suffix",""),selection.getInt("start"),selection.getInt("end"),
                    label.getText().toString(),note.getText().toString(),
                    AnnotationTone.withTone(tags.getText().toString(),
                            AnnotationTone.byIndex(tone.getSelectedItemPosition())));
                a.target=destination;
                dialog.dismiss();save(a,uri);
            }catch(Exception bad){message("Passage could not be saved: "+bad.getMessage());}
        }));
        dialog.show();
    }

    private void save(ArchiveJournal.Annotation a,String uri) {
        save(a,uri,null);
    }

    private void save(ArchiveJournal.Annotation a,String uri,ArchiveJournal.Annotation expected) {
        worker.execute(()->{
            try {
                if(expected==null)journal.save(a);
                else journal.saveIfCurrent(expected,a);
                String publication=publish(uri,a.chatTitle);
                activity.runOnUiThread(()->{
                    if(live(uri)){
                        message(publication);
                        if(previewReady.getAsBoolean())decorate(uri,previewReady);
                    }
                });
            }catch(Exception error){
                activity.runOnUiThread(()->{if(live(uri))message("Annotation not saved: "+error.getMessage());});
            }
        });
    }

    /** Journal commit is already durable before publication starts. */
    private String publish(String uri,String name) {
        String folder=activity.getSharedPreferences("markdown_desk",Context.MODE_PRIVATE)
                .getString("archive_notes_tree",null);
        if(folder==null||folder.isEmpty())return "Note saved locally. Choose a notes folder in Archive to publish indexes.";
        try {
            ArchiveJournal.ReadResult result=journal.load();
            if(result.truncatedTail)return "Note saved; journal needs repair before publishing indexes.";
            new ArchiveSidecarPublisher(activity,Uri.parse(folder))
                .publish(name,MarkdownNoteIdentity.SOURCE,uri,result.annotations);
            return "Note saved and Markdown indexes updated.";
        }catch(Exception failed) {
            return "Note saved locally; index sync failed. Original notebook preserved: "+failed.getMessage();
        }
    }

    /** Rebuild indexes from the durable shared journal after a folder grant.
     * The original Markdown documents and archive JSON are never opened for writing.
     */
    void syncIndexes() {
        worker.execute(()->{
            try {
                ArchiveJournal.ReadResult data=journal.load();
                if(data.truncatedTail)throw new IllegalStateException("Interrupted journal record; existing indexes preserved");
                String folder=activity.getSharedPreferences("markdown_desk",Context.MODE_PRIVATE)
                        .getString("archive_notes_tree",null);
                if(folder==null||folder.isEmpty())throw new IllegalStateException("Choose a notes folder first");
                new ArchiveSidecarPublisher(activity,Uri.parse(folder)).publishAll(data.annotations);
                activity.runOnUiThread(()->message("Saved Markdown note indexes synchronized."));
            }catch(Exception failure){
                activity.runOnUiThread(()->message("Notes folder saved, but index sync did not finish: "+failure.getMessage()));
            }
        });
    }

    /** Saved inbound links are indexed from the journal, not from parsed source text.
     * A link does not grant access to the source document.
     */
    void showBacklinks() {
        final String current=document();
        if(current==null){message("Save the document first to inspect backlinks.");return;}
        worker.execute(()->{
            try {
                ArchiveJournal.ReadResult data=journal.load();
                ArrayList<ArchiveJournal.Annotation> inbound=new ArrayList<>();
                for(ArchiveJournal.Annotation a:data.annotations) {
                    if(a==null||!a.isLink()||a.target==null)continue;
                    String destination=WorkspaceDeepLinks.documentTarget(a.target);
                    if(destination==null) {
                        try{destination=MarkdownNoteIdentity.requireDocument(a.target);}
                        catch(IllegalArgumentException ignored){}
                    }
                    if(current.equals(destination))inbound.add(a);
                }
                activity.runOnUiThread(()->{
                    if(!live(current))return;
                    if(data.truncatedTail)message("A journal record is interrupted; existing backlinks may be incomplete.");
                    if(inbound.isEmpty()){
                        new AlertDialog.Builder(activity).setTitle("Incoming document links")
                            .setMessage("No saved links point to this document.")
                            .setPositiveButton("Close",null).show();
                        return;
                    }
                    String[] labels=new String[inbound.size()];
                    for(int i=0;i<inbound.size();i++){
                        ArchiveJournal.Annotation a=inbound.get(i);
                        String label=a.label==null||a.label.trim().isEmpty()?"Saved link":a.label;
                        labels[i]=a.chatTitle+" · "+label;
                    }
                    new AlertDialog.Builder(activity).setTitle("Incoming document links")
                        .setItems(labels,(dialog,index)->showBacklinkActions(current,inbound.get(index)))
                        .setNegativeButton("Close",null).show();
                });
            }catch(Exception failure){
                activity.runOnUiThread(()->{if(live(current))message("Backlinks unavailable: "+failure.getMessage());});
            }
        });
    }

    private void showBacklinkActions(String current,ArchiveJournal.Annotation a) {
        if(!live(current))return;
        final String origin=ArchiveMarkdownIndexes.deepLink(a);
        new AlertDialog.Builder(activity).setTitle(a.chatTitle)
            .setItems(new String[]{"Open linking passage","Copy linking passage URL"},(dialog,index)->{
                if(index==1){
                    ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if(clipboard!=null){
                        clipboard.setPrimaryClip(ClipData.newPlainText("Backlink source",origin));
                        message("Backlink source copied");
                    }
                    return;
                }
                if(!live(current))return;
                Uri link=Uri.parse(origin);
                boolean markdown=WorkspaceDeepLinks.isDocumentLink(origin);
                boolean archive=WorkspaceDeepLinks.isArchiveLink(origin);
                if(!markdown&&!archive){message("Saved backlink source is invalid.");return;}
                Intent view=new Intent(Intent.ACTION_VIEW,link)
                    .setClass(activity,markdown?MainActivity.class:ArchiveWorkspaceActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                try{activity.startActivity(view);}
                catch(ActivityNotFoundException|SecurityException denied){
                    message("The linking passage could not be opened.");
                }
            }).setNegativeButton("Close",null).show();
    }

    void showNotes() {
        String uri=document();
        if(uri==null){message("Save this document first to view source-linked notes.");return;}
        worker.execute(()->{
            try {
                ArchiveJournal.ReadResult data=journal.load();
                ArrayList<ArchiveJournal.Annotation> rows=new ArrayList<>();
                for(ArchiveJournal.Annotation a:data.annotations)
                    if(MarkdownNoteIdentity.owns(a,uri))rows.add(a);
                activity.runOnUiThread(()->{
                    if(!live(uri))return;
                    if(data.truncatedTail)message("Interrupted note journal record; edits are protected.");
                    if(rows.isEmpty()){new AlertDialog.Builder(activity)
                        .setTitle("Reading notebook").setMessage("No notes for this document yet.")
                        .setPositiveButton("Close",null).show();return;}
                    String[] labels=new String[rows.size()];
                    for(int i=0;i<rows.size();i++){
                        ArchiveJournal.Annotation a=rows.get(i);
                        labels[i]=(a.label==null||a.label.isEmpty()?a.type:a.label)+
                                " · "+(a.quote==null?"":a.quote);
                    }
                    new AlertDialog.Builder(activity).setTitle("Reading notebook")
                        .setItems(labels,(d,index)->showActions(uri,rows.get(index)))
                        .setNegativeButton("Close",null).show();
                });
            }catch(Exception failed){activity.runOnUiThread(()->{if(live(uri))message("Notebook unavailable: "+failed.getMessage());});}
        });
    }

    private void showActions(String uri,ArchiveJournal.Annotation a) {
        if(!MarkdownNoteIdentity.owns(a,uri))return;
        new AlertDialog.Builder(activity).setTitle(a.label==null?a.type:a.label)
            .setItems(new String[]{"Go to passage","Copy document note link","Edit note","History","Delete note","Highlight color"},
              (d,index)->{
                if(index==0) {
                    if(!previewReady.getAsBoolean()){message("Open Reading mode to jump to a note.");return;}
                    requestNote(uri,a.id);
                }else if(index==1) {
                    ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if(clipboard!=null){
                        clipboard.setPrimaryClip(ClipData.newPlainText("Document note",ArchiveMarkdownIndexes.deepLink(a)));
                        message("Note link copied");
                    }
                }else if(index==2) {
                    EditText input=new EditText(activity);input.setText(a.note==null?"":a.note);
                    new AlertDialog.Builder(activity).setTitle("Edit note").setView(input)
                        .setPositiveButton("Save",(dialog,which)->{
                            ArchiveJournal.Annotation changed=a.copy();
                            changed.note=input.getText().toString();
                            changed.updatedAt=System.currentTimeMillis();
                            save(changed,uri,a);
                        }).setNegativeButton("Cancel",null).show();
                }else if(index==3) {
                    AnnotationHistoryDialog.show(activity,worker,journal,a,()->live(uri),this::message);
                }else if(index==4) {
                    new AlertDialog.Builder(activity).setTitle("Delete note?")
                        .setMessage("The original Markdown document will not change.")
                        .setPositiveButton("Delete",(dialog,which)->worker.execute(()->{
                            try{
                                journal.deleteIfCurrent(a);
                                String publication=publish(uri,a.chatTitle);
                                activity.runOnUiThread(()->{
                                    if(live(uri)){
                                        message("Note deleted locally. "+publication);
                                        if(previewReady.getAsBoolean())decorate(uri,previewReady);
                                    }
                                });
                            }catch(Exception fail){
                                activity.runOnUiThread(()->{if(live(uri))message("Note could not be deleted: "+fail.getMessage());});
                            }
                        })).setNegativeButton("Cancel",null).show();
                }else if(index==5) {
                    new AlertDialog.Builder(activity).setTitle("Annotation highlight color")
                        .setSingleChoiceItems(AnnotationTone.labels(),AnnotationTone.index(a.tags),
                                (dialog,selected)->{
                                    ArchiveJournal.Annotation changed=a.copy();
                                    changed.tags=AnnotationTone.withTone(a.tags,
                                            AnnotationTone.byIndex(selected));
                                    changed.updatedAt=System.currentTimeMillis();
                                    dialog.dismiss();
                                    save(changed,uri,a);
                                })
                        .setNegativeButton("Cancel",null).show();
                }
              }).show();
    }
}
