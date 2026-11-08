package com.example.markdowndesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.markdowndesk.archive.ArchiveJournal;
import com.example.markdowndesk.archive.ArchiveMarkdownIndexes;
import com.example.markdowndesk.workspace.WorkspaceDeepLinks;
import com.example.markdowndesk.workspace.annotations.AnnotationTone;
import com.example.markdowndesk.workspace.annotations.JsonInspectorNoteIdentity;
import com.example.markdowndesk.workspace.annotations.JsonPathCodec;
import com.example.markdowndesk.workspace.annotations.VerifiedTextAnchor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** General JSON Inspector reader notes. Uses the SAME source-locked AJN2 journal
 * and safe staged Markdown index publisher as Archive and Markdown. JSON is
 * opened exclusively for read; page path is an identity, not a URI permission.
 */
final class JsonInspectorNotes {
    static final class Navigation {
        final ArrayList<JsonExplorerEngine.Step> path;
        final JsonExplorerEngine.Page page;
        final int anchor;
        Navigation(ArrayList<JsonExplorerEngine.Step> path,JsonExplorerEngine.Page page,int anchor){
            this.path=path;this.page=page;this.anchor=anchor;
        }
    }
    private final Activity host;
    private final ExecutorService io;
    private final Supplier<Uri> source;
    private final Supplier<List<JsonExplorerEngine.Step>> path;
    private final IntSupplier revision;
    private final Consumer<String> message;
    private final Consumer<Navigation> onNavigation;
    private final Runnable onChanged;
    private final ArchiveJournal journal;

    JsonInspectorNotes(Activity host,ExecutorService io,Supplier<Uri> source,
            Supplier<List<JsonExplorerEngine.Step>> path,IntSupplier revision,
            Consumer<String> message,Consumer<Navigation> onNavigation,Runnable onChanged) {
        this.host=host;this.io=io;this.source=source;this.path=path;
        this.revision=revision;this.message=message;this.onNavigation=onNavigation;
        this.onChanged=onChanged;
        journal=new ArchiveJournal(new File(host.getFilesDir(),"claude-archive/annotations-v2.journal"));
    }
    static String canonicalPath(List<JsonExplorerEngine.Step> current){
        ArrayList<JsonPathCodec.Step> converted=new ArrayList<>();
        for(JsonExplorerEngine.Step step:current)
            converted.add(step.name!=null?JsonPathCodec.Step.field(step.name):
                    JsonPathCodec.Step.element(step.index));
        return JsonPathCodec.format(converted);
    }
    private boolean live(Uri uri,int version){
        return uri!=null&&uri.equals(source.get())&&version==revision.getAsInt()
                &&!host.isFinishing()&&!host.isDestroyed();
    }
    private void report(Uri uri,String line){
        host.runOnUiThread(()->{
            if(uri!=null&&uri.equals(source.get())&&!host.isFinishing()&&!host.isDestroyed())
                message.accept(line);
        });
    }
    private void reportChanged(Uri uri,String line){
        host.runOnUiThread(()->{
            if(uri!=null&&uri.equals(source.get())&&!host.isFinishing()&&!host.isDestroyed()){
                message.accept(line);
                if(onChanged!=null)onChanged.run();
            }
        });
    }
    private static int toneColor(String tags){
        switch(AnnotationTone.fromTags(tags)){
            case "rose":return Color.argb(83,236,100,152);
            case "mint":return Color.argb(83,82,196,158);
            case "amber":return Color.argb(87,242,180,70);
            case "blue":return Color.argb(84,90,150,245);
            case "slate":return Color.argb(87,132,152,171);
            default:return Color.argb(92,186,106,235);
        }
    }
    void decorateValue(Uri uri,String canonical,String value,int valueStart,int version,
            Consumer<SpannableString> ready) {
        if(uri==null||value==null||value.length()>2_000_000)return;
        io.execute(()->{
            try{
                ArchiveJournal.ReadResult data=journal.load();
                if(data.truncatedTail)throw new IOException("Annotation journal has an interrupted record");
                SpannableString spans=new SpannableString(value);
                int decorated=0;
                for(ArchiveJournal.Annotation a:data.annotations){
                    if(!JsonInspectorNoteIdentity.owns(a,uri.toString())||
                            !canonical.equals(a.messageId)||a.selectionStart<valueStart||
                            a.selectionEnd>valueStart+value.length())continue;
                    int at=VerifiedTextAnchor.locate(value,a.quote,a.prefix,a.suffix,a.selectionStart-valueStart);
                    if(at<0)continue; // Explicitly unresolved, never guess.
                    spans.setSpan(new BackgroundColorSpan(toneColor(a.tags)),at,at+a.quote.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if(++decorated>=1000)break; // protect huge spans, retain notes in journal
                }
                host.runOnUiThread(()->{if(live(uri,version))ready.accept(spans);});
            }catch(Exception invalid){
                report(uri,"Saved JSON highlights unavailable: "+invalid.getMessage());
            }
        });
    }
    /** Bookmark the current JSON scalar without requiring the user to manually
     * select text. A unique quote/context still proves source identity.
     * Container pages without a stable scalar are intentionally not guessed.
     */
    void bookmarkValue(String value,int valueStart){
        final Uri uri=source.get();
        if(uri==null||value==null||value.isEmpty()){
            message.accept("Open a nonempty JSON value before bookmarking.");return;
        }
        int end=Math.min(value.length(),128);
        if(end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;
        if(end<=0){message.accept("Cannot safely bookmark this value.");return;}
        String quote=value.substring(0,end);
        String suffix=value.substring(end,Math.min(value.length(),end+64));
        if(!VerifiedTextAnchor.matchesSelected(value,quote,"",suffix,0,end)){
            message.accept("This value is ambiguous; select a more specific passage to bookmark.");return;
        }
        final ArrayList<JsonExplorerEngine.Step> steps=new ArrayList<>(path.get());
        final String canonical;
        try{canonical=canonicalPath(steps);}
        catch(IllegalArgumentException invalid){message.accept("JSON path is too long to bookmark.");return;}
        showCreation(uri,steps,canonical,value,quote,"",suffix,0,end,
                revision.getAsInt(),"bookmark",valueStart);
    }

    void annotateSelection(TextView reader,String selectedValue,int valueStart){
        final Uri uri=source.get();
        if(uri==null||reader==null||selectedValue==null){
            message.accept("Open a JSON value first.");return;
        }
        int first=reader.getSelectionStart(),last=reader.getSelectionEnd();
        if(first<0||last<=first||last>selectedValue.length()||last-first>65536){
            message.accept("Select a passage in this JSON value before annotating.");return;
        }
        String quote=selectedValue.substring(first,last);
        String prefix=selectedValue.substring(Math.max(0,first-64),first);
        String suffix=selectedValue.substring(last,Math.min(selectedValue.length(),last+64));
        if(!VerifiedTextAnchor.matchesSelected(selectedValue,quote,prefix,suffix,first,last)){
            message.accept("The selection is ambiguous or changed. Select a more specific passage.");return;
        }
        final int version=revision.getAsInt(),start=first,end=last;
        final ArrayList<JsonExplorerEngine.Step> steps=new ArrayList<>(path.get());
        final String canonical;
        try{canonical=canonicalPath(steps);}
        catch(IllegalArgumentException invalid){message.accept("JSON path is too long for saved notes.");return;}
        final String[] names={"Highlight","Note","Bookmark","Save source link","Cross-link","External link"};
        final String[] types={"highlight","note","bookmark","source_link","cross_link","external_link"};
        new AlertDialog.Builder(host).setTitle("Annotate JSON passage")
                .setItems(names,(dialog,index)->showCreation(uri,steps,canonical,selectedValue,
                        quote,prefix,suffix,start,end,version,types[index],valueStart))
                .setNegativeButton("Cancel",null).show();
    }
    private void showCreation(Uri uri,ArrayList<JsonExplorerEngine.Step> steps,String canonical,
            String value,String quote,String prefix,String suffix,int first,int last,int version,String kind,int valueStart){
        LinearLayout form=new LinearLayout(host);form.setOrientation(LinearLayout.VERTICAL);
        TextView selected=new TextView(host);selected.setText("Selected: "+quote);form.addView(selected);
        EditText label=new EditText(host);label.setHint("Label");form.addView(label);
        EditText note=new EditText(host);note.setHint("Your note");note.setMinLines(2);form.addView(note);
        EditText tags=new EditText(host);tags.setHint("Tags, separated by commas");form.addView(tags);
        EditText target=new EditText(host);
        target.setHint("Destination URL or saved Markdown Desk link");
        if("cross_link".equals(kind)||"external_link".equals(kind))form.addView(target);
        Spinner tone=new Spinner(host);
        ArrayAdapter<String> colors=new ArrayAdapter<>(host,
                android.R.layout.simple_spinner_item,AnnotationTone.labels());
        colors.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tone.setAdapter(colors);tone.setContentDescription("Annotation highlight color");form.addView(tone);
        ScrollView view=new ScrollView(host);view.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(host).setTitle("Save JSON "+kind)
                .setView(view).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(tap->{
            if(!live(uri,version)||
                    !canonical.equals(canonicalPath(path.get()))){
                message.accept("JSON source changed; select this passage again.");return;
            }
            String destination=target.getText().toString().trim();
            if(("cross_link".equals(kind)||"external_link".equals(kind))&&destination.isEmpty()){
                target.setError("Destination required");return;
            }
            ArchiveJournal.Annotation row;
            try{
                row=JsonInspectorNoteIdentity.create(uri.toString(),canonical,
                        uri.getLastPathSegment(),kind,quote,prefix,suffix,first+valueStart,last+valueStart,
                        label.getText().toString(),note.getText().toString(),
                        AnnotationTone.withTone(tags.getText().toString(),
                                AnnotationTone.byIndex(tone.getSelectedItemPosition())),destination);
                if(!WorkspaceDeepLinks.isInspectorLink(ArchiveMarkdownIndexes.deepLink(row)))
                    throw new IllegalArgumentException("JSON URI is too long for a safe saved link");
            }catch(IllegalArgumentException invalid){
                message.accept("Note cannot be saved: "+invalid.getMessage());return;
            }
            dialog.dismiss();
            io.execute(()->{
                try{
                    JsonExplorerEngine.Page latest=JsonExplorerEngine.scanWindow(
                            ()->host.getContentResolver().openInputStream(uri),steps,
                            JsonExplorerEngine.valuePage(valueStart),valueStart,value.length());
                    if(!"Value".equals(latest.type)||!value.equals(latest.value)||
                            !VerifiedTextAnchor.matchesSelected(latest.value,quote,prefix,suffix,first,last))
                        throw new IOException("JSON value changed; annotation not saved");
                    journal.save(row);
                    String publish=publish(uri,row.chatTitle);
                    reportChanged(uri,"JSON note saved locally. "+publish);
                }catch(Exception failed){report(uri,"JSON note not saved: "+failed.getMessage());}
            });
        }));
        dialog.show();
    }
    private String publish(Uri uri,String title){
        String folder=host.getSharedPreferences("markdown_desk",Context.MODE_PRIVATE)
                .getString("archive_notes_tree",null);
        if(folder==null||folder.isEmpty())return "Choose a notes folder in Archive to publish Markdown indexes.";
        try{
            ArchiveJournal.ReadResult snapshot=journal.load();
            if(snapshot.truncatedTail)throw new IOException("Interrupted journal record; sidecar preserved");
            new ArchiveSidecarPublisher(host,Uri.parse(folder)).publish(title,
                    JsonInspectorNoteIdentity.SOURCE,uri.toString(),snapshot.annotations);
            return "Portable Markdown indexes updated.";
        }catch(Exception e){
            return "Portable index sync failed; saved journal retained: "+e.getMessage();
        }
    }
    void showNotes(){
        final Uri uri=source.get();if(uri==null){message.accept("Open a JSON file first.");return;}
        final int version=revision.getAsInt();
        io.execute(()->{
            try{
                ArchiveJournal.ReadResult data=journal.load();
                if(data.truncatedTail)throw new IOException("Interrupted journal; notes cannot be trusted");
                ArrayList<ArchiveJournal.Annotation> rows=new ArrayList<>();
                for(ArchiveJournal.Annotation a:data.annotations)
                    if(JsonInspectorNoteIdentity.owns(a,uri.toString()))rows.add(a);
                host.runOnUiThread(()->{
                    if(!live(uri,version))return;
                    if(rows.isEmpty()){
                        new AlertDialog.Builder(host).setTitle("JSON reading notebook")
                                .setMessage("No saved notes for this JSON document.")
                                .setPositiveButton("Close",null).show();return;
                    }
                    String[] labels=new String[rows.size()];
                    for(int i=0;i<rows.size();i++){
                        ArchiveJournal.Annotation a=rows.get(i);
                        labels[i]=a.messageId+" · "+(a.label==null||a.label.isEmpty()?a.type:a.label);
                    }
                    new AlertDialog.Builder(host).setTitle("JSON reading notebook")
                            .setItems(labels,(d,which)->showNoteActions(uri,rows.get(which)))
                            .setNegativeButton("Close",null).show();
                });
            }catch(Exception failed){report(uri,"JSON notes unavailable: "+failed.getMessage());}
        });
    }
    private void showNoteActions(Uri uri,ArchiveJournal.Annotation row){
        if(!JsonInspectorNoteIdentity.owns(row,uri.toString()))return;
        String[] actions={"Go to exact passage","Copy source link","Edit note","History",
                "Delete","Highlight color"};
        new AlertDialog.Builder(host).setTitle(row.label==null?row.type:row.label)
                .setItems(actions,(dialog,which)->{
                    if(which==0)jumpToNote(uri,row.id);
                    else if(which==1){
                        ClipboardManager clipboard=(ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE);
                        if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText(
                                "JSON note link",ArchiveMarkdownIndexes.deepLink(row)));
                    }else if(which==2){
                        EditText edit=new EditText(host);edit.setText(row.note);
                        new AlertDialog.Builder(host).setTitle("Edit JSON note").setView(edit)
                            .setPositiveButton("Save",(d,w)->{
                                ArchiveJournal.Annotation changed=row.copy();
                                changed.note=edit.getText().toString();changed.updatedAt=System.currentTimeMillis();
                                update(uri,row,changed);
                            }).setNegativeButton("Cancel",null).show();
                    }else if(which==3){
                        final int version=revision.getAsInt();
                        AnnotationHistoryDialog.show(host,io,journal,row,
                                ()->live(uri,version),message);
                    }else if(which==4){
                        new AlertDialog.Builder(host).setTitle("Delete JSON note?")
                            .setMessage("Original JSON remains unchanged.")
                            .setPositiveButton("Delete",(d,w)->io.execute(()->{
                                try{
                                    journal.deleteIfCurrent(row);
                                    String synced=publish(uri,row.chatTitle);
                                    reportChanged(uri,"JSON note deleted. "+synced);
                                }catch(Exception e){report(uri,"Cannot delete note: "+e.getMessage());}
                            })).setNegativeButton("Cancel",null).show();
                    }else if(which==5){
                        new AlertDialog.Builder(host).setTitle("JSON note highlight color")
                            .setSingleChoiceItems(AnnotationTone.labels(),AnnotationTone.index(row.tags),
                                (d,w)->{
                                    ArchiveJournal.Annotation changed=row.copy();
                                    changed.tags=AnnotationTone.withTone(row.tags,AnnotationTone.byIndex(w));
                                    changed.updatedAt=System.currentTimeMillis();
                                    d.dismiss();update(uri,row,changed);
                                }).setNegativeButton("Cancel",null).show();
                    }
                }).show();
    }
    private void update(Uri uri,ArchiveJournal.Annotation before,ArchiveJournal.Annotation changed){
        io.execute(()->{
            try{
                journal.saveIfCurrent(before,changed);
                String publication=publish(uri,changed.chatTitle);
                reportChanged(uri,"JSON note updated. "+publication);
            }catch(Exception e){report(uri,"JSON note update rejected: "+e.getMessage());}
        });
    }
    void jumpToNote(Uri uri,String noteId){
        if(uri==null||noteId==null||noteId.isEmpty())return;
        final int version=revision.getAsInt();
        io.execute(()->{
            try{
                ArchiveJournal.ReadResult rows=journal.load();
                if(rows.truncatedTail)throw new IOException("Interrupted annotation journal");
                ArchiveJournal.Annotation match=null;
                for(ArchiveJournal.Annotation a:rows.annotations)
                    if(noteId.equals(a.id)&&JsonInspectorNoteIdentity.owns(a,uri.toString())){match=a;break;}
                if(match==null)throw new IOException("Saved note no longer belongs to this JSON source");
                List<JsonPathCodec.Step> decoded=JsonPathCodec.parse(match.messageId);
                ArrayList<JsonExplorerEngine.Step> steps=new ArrayList<>();
                for(JsonPathCodec.Step part:decoded)
                    steps.add(part.key==null?JsonExplorerEngine.Step.element(part.index):
                            JsonExplorerEngine.Step.field(part.key));
                int first=(match.selectionStart/JsonExplorerEngine.VALUE_CHARS)*JsonExplorerEngine.VALUE_CHARS;
                int count=(int)Math.min(2L*JsonExplorerEngine.VALUE_CHARS+128,
                        Math.max(JsonExplorerEngine.VALUE_CHARS,(long)match.selectionEnd-first+64));
                JsonExplorerEngine.Page found=JsonExplorerEngine.scanWindow(
                        ()->host.getContentResolver().openInputStream(uri),steps,
                        JsonExplorerEngine.valuePage(match.selectionStart),first,count);
                if(!"Value".equals(found.type))throw new IOException("Saved JSON value is no longer present");
                int at=VerifiedTextAnchor.locate(found.value,match.quote,match.prefix,
                        match.suffix,match.selectionStart-found.valueStart);
                if(at<0)throw new IOException("Quoted passage changed or is ambiguous");
                Navigation destination=new Navigation(steps,found,at);
                host.runOnUiThread(()->{if(live(uri,version))onNavigation.accept(destination);});
            }catch(Exception e){report(uri,"Cannot jump to saved JSON note: "+e.getMessage());}
        });
    }
    private void showLinkActions(ArchiveJournal.Annotation linking){
        final String link=ArchiveMarkdownIndexes.deepLink(linking);
        final boolean markdown=WorkspaceDeepLinks.isDocumentLink(link);
        final boolean archive=WorkspaceDeepLinks.isArchiveLink(link);
        final boolean inspector=WorkspaceDeepLinks.isInspectorLink(link);
        if(!markdown&&!archive&&!inspector){
            message.accept("Linking source is invalid; existing note preserved.");return;
        }
        new AlertDialog.Builder(host).setTitle("Incoming link source")
                .setItems(new String[]{"Open linking passage","Copy source link"},(d,which)->{
                    if(which==1){
                        ClipboardManager clipboard=(ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE);
                        if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Linking source",link));
                    }else{
                        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(link))
                                .setClass(host,markdown?MainActivity.class:
                                        archive?ArchiveWorkspaceActivity.class:JsonExplorerActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        try{host.startActivity(intent);}
                        catch(RuntimeException denied){message.accept("Linking source cannot be opened; no file permission was granted.");}
                    }
                }).setNegativeButton("Close",null).show();
    }

    void showBacklinks(){
        final Uri uri=source.get();if(uri==null){message.accept("Open a JSON file first.");return;}
        final int version=revision.getAsInt();
        io.execute(()->{
            try{
                ArchiveJournal.ReadResult data=journal.load();
                if(data.truncatedTail)throw new IOException("Interrupted annotation journal");
                ArrayList<ArchiveJournal.Annotation> incoming=new ArrayList<>();
                for(ArchiveJournal.Annotation a:data.annotations){
                    if(a!=null&&a.isLink()&&a.target!=null&&
                            uri.toString().equals(WorkspaceDeepLinks.inspectorTarget(a.target)))
                        incoming.add(a);
                }
                host.runOnUiThread(()->{
                    if(!live(uri,version))return;
                    if(incoming.isEmpty()){
                        new AlertDialog.Builder(host).setTitle("Incoming JSON links")
                                .setMessage("No deliberately saved links point to this file.")
                                .setPositiveButton("Close",null).show();return;
                    }
                    String[] labels=new String[incoming.size()];
                    for(int i=0;i<incoming.size();i++)
                        labels[i]=incoming.get(i).chatTitle+" · "+
                                (incoming.get(i).label==null?"Saved link":incoming.get(i).label);
                    new AlertDialog.Builder(host).setTitle("Incoming JSON links")
                            .setItems(labels,(d,which)->showLinkActions(incoming.get(which)))
                            .setNegativeButton("Close",null).show();
                });
            }catch(Exception failure){report(uri,"JSON backlinks unavailable: "+failure.getMessage());}
        });
    }
}
