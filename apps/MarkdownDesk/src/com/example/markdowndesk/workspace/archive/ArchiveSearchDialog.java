package com.example.markdowndesk.workspace.archive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.function.Consumer;

/** One search dialog for a chat or the complete archive, sharing the same
 * filters and error handling. It never loads archive files on the UI thread.
 */
public final class ArchiveSearchDialog {
    private ArchiveSearchDialog() {}
    public static void show(Activity activity,String chatId,ArchiveSearchSpec previous,
                            Consumer<ArchiveSearchSpec> onSearch) {
        int margin=Math.round(18*activity.getResources().getDisplayMetrics().density);
        LinearLayout fields=new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(margin,margin/2,margin,margin/2);
        EditText phrase=field(activity,"Exact phrase or words",previous==null?"":previous.term);
        EditText speaker=field(activity,"Speaker (optional)",previous==null?"":previous.speaker);
        EditText from=field(activity,"From YYYY-MM-DD (optional)",previous==null?"":previous.fromDate);
        EditText to=field(activity,"Through YYYY-MM-DD (optional)",previous==null?"":previous.toDate);
        fields.addView(phrase);fields.addView(speaker);fields.addView(from);fields.addView(to);
        ScrollView scroll=new ScrollView(activity);scroll.addView(fields);
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle(chatId==null?"Search all conversations":"Find in conversation")
            .setView(scroll).setPositiveButton("Search",null)
            .setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(event->dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v->{
                    try{
                        ArchiveSearchSpec spec=new ArchiveSearchSpec(
                            phrase.getText().toString(),speaker.getText().toString(),
                            from.getText().toString(),to.getText().toString());
                        dialog.dismiss();onSearch.accept(spec);
                    }catch(IllegalArgumentException bad){
                        phrase.setError(bad.getMessage());
                    }
                }));
        dialog.show();
    }
    private static EditText field(Activity activity,String hint,String value){
        EditText text=new EditText(activity);text.setSingleLine(true);
        text.setHint(hint);text.setText(value);return text;
    }
}