package com.example.markdowndesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.example.markdowndesk.archive.ArchiveJournal;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** History is read-only. No undo, journal append or source document write here. */
final class AnnotationHistoryDialog {
    private AnnotationHistoryDialog(){}
    static void show(Activity activity,ExecutorService worker,ArchiveJournal journal,
            ArchiveJournal.Annotation snapshot,BooleanSupplier stillCurrent,Consumer<String> error){
        worker.execute(()->{
            try{
                ArchiveJournal.HistoryResult history=journal.history(snapshot);
                List<ArchiveJournal.Revision> rows=history.revisions;
                String[] labels=new String[rows.size()];
                for(int i=0;i<rows.size();i++){
                    ArchiveJournal.Revision revision=rows.get(i);
                    labels[i]="Revision "+(i+1)+" · "+(revision.deleted?"Deleted":
                        "Saved "+(revision.annotation.updatedAt==0?"(unknown time)":
                            new java.util.Date(revision.annotation.updatedAt).toString()));
                }
                activity.runOnUiThread(()->{
                    if(!stillCurrent.getAsBoolean()||activity.isFinishing()||activity.isDestroyed())return;
                    if(rows.isEmpty()){
                        new AlertDialog.Builder(activity).setTitle("Note history")
                            .setMessage("No historical versions were found.")
                            .setPositiveButton("Close",null).show();return;
                    }
                    new AlertDialog.Builder(activity).setTitle("Note history (read only)")
                        .setItems(labels,(d,index)->{
                            ArchiveJournal.Revision chosen=rows.get(index);
                            if(chosen.deleted){
                                new AlertDialog.Builder(activity).setTitle("Deletion event")
                                    .setMessage("This historical deletion does not change the current journal.")
                                    .setPositiveButton("Close",null).show();return;
                            }
                            String note=chosen.annotation.note==null?"":chosen.annotation.note;
                            String quote=chosen.annotation.quote==null?"":chosen.annotation.quote;
                            String view="Quote: "+(quote.length()>400?quote.substring(0,400)+"…":quote)+
                                    "\n\nNote: "+(note.length()>8000?note.substring(0,8000)+"…":note);
                            AlertDialog.Builder detail=new AlertDialog.Builder(activity)
                                    .setTitle(labels[index]).setMessage(view)
                                    .setPositiveButton("Close",null);
                            if(!note.isEmpty())detail.setNeutralButton("Copy full note",(dialog,which)->{
                                ClipboardManager clipboard=(ClipboardManager)
                                        activity.getSystemService(Context.CLIPBOARD_SERVICE);
                                if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Historical note",note));
                            });
                            detail.show();
                        }).setNegativeButton("Close",null).show();
                });
            }catch(Exception failed){
                activity.runOnUiThread(()->{
                    if(stillCurrent.getAsBoolean()&&!activity.isFinishing()&&!activity.isDestroyed())
                        error.accept("Note history unavailable: "+failed.getMessage());
                });
            }
        });
    }
}
