package com.example.markdowndesk.workspace.archive;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.example.markdowndesk.archive.ArchiveJournal;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executor;

/** Reusable read-only research panel for side-by-side Fold and compact layouts.
 * Notes are the canonical append-only journal; the panel never edits the source JSON.
 */
public final class ArchiveNotebookPane {
    public interface Actions { void onOpen(ArchiveJournal.Annotation annotation); }
    private ArchiveNotebookPane(){}

    public static ScrollView create(Activity activity, File journalFile,String chatId,
            int textColor,int mutedColor,int background,Executor backgroundWorker,Actions actions){
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout body=new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity,12),dp(activity,10),dp(activity,12),dp(activity,18));
        body.setBackgroundColor(background);
        TextView title=text(activity,"Notes & links",16,textColor,true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(title);
        body.addView(text(activity,"Attached to this conversation · tap to navigate",12,mutedColor,false));
        final TextView status=text(activity,"Loading annotations…",13,mutedColor,false);
        body.addView(status);
        scroll.addView(body);
        // Keep file reads off the UI thread even for large researcher notebooks.
        backgroundWorker.execute(()->{
            try{
                ArchiveJournal.ReadResult result=new ArchiveJournal(journalFile).load();
                ArrayList<ArchiveJournal.Annotation> rows=new ArrayList<>();
                for(ArchiveJournal.Annotation a:result.annotations)
                    if(chatId.equals(a.chatId)) rows.add(a);
                activity.runOnUiThread(()->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    body.removeView(status);
                    if(result.truncatedTail)body.addView(text(activity,
                            "A previous annotation write was interrupted. New saves are blocked until repair.",13,textColor,true));
                    if(rows.isEmpty())body.addView(text(activity,"No notes or links for this conversation yet.",13,mutedColor,false));
                    for(ArchiveJournal.Annotation a:rows){
                        LinearLayout row=new LinearLayout(activity);
                        row.setOrientation(LinearLayout.VERTICAL);
                        row.setPadding(dp(activity,10),dp(activity,9),dp(activity,10),dp(activity,9));
                        GradientDrawable surface=new GradientDrawable();surface.setColor(background);
                        surface.setStroke(dp(activity,1),mutedColor);surface.setCornerRadius(dp(activity,9));
                        row.setBackground(surface);
                        String kind=a.type==null?"Note":a.type.replace('_',' ');
                        row.addView(text(activity,kind+"  ·  "+fallback(a.label),13,textColor,true));
                        if(a.quote!=null&&!a.quote.isEmpty())row.addView(text(activity,abbreviate(a.quote,160),12,mutedColor,false));
                        if(a.note!=null&&!a.note.isEmpty())row.addView(text(activity,abbreviate(a.note,220),13,textColor,false));
                        row.setOnClickListener(v->actions.onOpen(a));
                        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                        lp.topMargin=dp(activity,8);body.addView(row,lp);
                    }
                });
            }catch(Exception e){activity.runOnUiThread(()->{
                if(!activity.isFinishing()&&!activity.isDestroyed())status.setText("Unable to load notes. Your saved journal was not changed.");
            });}
        });
        return scroll;
    }

    private static TextView text(Activity activity,String value,int sp,int color,boolean bold){
        TextView t=new TextView(activity);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        t.setPadding(0,dp(activity,4),0,dp(activity,4));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private static String fallback(String s){return s==null||s.trim().isEmpty()?"Untitled":s;}
    private static String abbreviate(String s,int n){return s.length()>n?s.substring(0,n)+"…":s;}
    private static int dp(Activity c,int n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
}