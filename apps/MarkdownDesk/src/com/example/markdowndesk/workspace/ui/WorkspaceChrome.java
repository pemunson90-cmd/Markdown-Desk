package com.example.markdowndesk.workspace.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.PopupMenu;
import java.util.Map;
import java.util.WeakHashMap;
import com.example.markdowndesk.workspace.WorkspacePreferences;
import com.example.markdowndesk.workspace.WorkspaceRouter;
import com.example.markdowndesk.workspace.WorkspaceRouter.Workspace;

/**
 * The same navigation/header component appears in the writer, archive and JSON inspector.
 * This is a shell, not an editor implementation: it never touches document contents.
 *
 * Unlike a floating accessibility overlay, it is an ordinary measured child of each
 * Activity's existing vertical root. Insets stay owned by that root, preventing the
 * Fold7's status-bar overlap regression. Lifecycle binding is idempotent.
 */
public final class WorkspaceChrome {
    private static final Map<Activity, Attachment> ATTACHED=new WeakHashMap<>();
    private static final Object TAG=new Object();
    private WorkspaceChrome(){}

    public static synchronized void bind(Activity activity){
        if(!WorkspaceRouter.eligible(activity))return;
        Attachment attachment=ATTACHED.get(activity);
        if(attachment==null){
            attachment=new Attachment(activity);
            ATTACHED.put(activity,attachment);
            attachment.observe();
        }
        attachment.reconcile();
    }
    public static synchronized void unbind(Activity activity){
        Attachment a=ATTACHED.remove(activity);
        if(a!=null)a.release();
    }

    private static final class Attachment {
        final Activity activity;
        final Workspace workspace;
        ViewGroup currentRoot;
        View originalTop;
        LinearLayout shell;
        TextView label;
        Button context,more,files,markdown,archive;
        boolean updating;
        int lastSurface=Integer.MIN_VALUE,lastText=Integer.MIN_VALUE;
        boolean lastUnified;
        ViewTreeObserver observer;
        ViewTreeObserver.OnGlobalLayoutListener layoutListener;
        Attachment(Activity activity){this.activity=activity;workspace=WorkspaceRouter.classify(activity);}
        void observe(){
            observer=activity.getWindow().getDecorView().getViewTreeObserver();
            layoutListener=()->reconcile();
            observer.addOnGlobalLayoutListener(layoutListener);
        }
        void release(){
            if(observer!=null && observer.isAlive() && layoutListener!=null)
                observer.removeOnGlobalLayoutListener(layoutListener);
            if(originalTop!=null)originalTop.setVisibility(View.VISIBLE);
            // Do not mutate view trees during Activity destruction.
            originalTop=null;currentRoot=null;shell=null;
        }
        void reconcile(){
            if(updating || activity.isFinishing() || (Build.VERSION.SDK_INT>=17 && activity.isDestroyed()))return;
            updating=true;
            try{
                ViewGroup content=activity.findViewById(android.R.id.content);
                if(content==null||content.getChildCount()==0)return;
                View child=content.getChildAt(0);
                if(!(child instanceof LinearLayout))return;
                LinearLayout root=(LinearLayout)child;
                if(root.getOrientation()!=LinearLayout.VERTICAL)return;
                boolean unified=WorkspacePreferences.unified(activity);
                if(currentRoot!=root){
                    if(originalTop!=null)originalTop.setVisibility(View.VISIBLE);
                    currentRoot=root;
                    originalTop=findOriginalTop(root);
                    shell=createShell(root,unified);
                    lastUnified=!unified;
                }
                if(unified!=lastUnified){
                    // In unified mode the existing header buttons remain bound to their
                    // original actions; only their presentation is replaced.
                    if(originalTop!=null && workspace!=Workspace.INSPECT)
                        originalTop.setVisibility(unified?View.GONE:View.VISIBLE);
                    shell.setVisibility(View.VISIBLE);
                    updateWidth(unified);
                    lastUnified=unified;
                }
                updateColors();
                boolean readingFocus=(activity.getWindow().getAttributes().flags & 1024)!=0;
                shell.setVisibility(readingFocus?View.GONE:View.VISIBLE);
                updateTitle();
                if(workspace==Workspace.MARKDOWN && activity.getIntent()!=null
                    && activity.getIntent().getBooleanExtra(WorkspaceRouter.EXTRA_OPEN_FILES,false)){
                    // Consume before invoking the original Files action so a rebuild
                    // or a Fold configuration change cannot reopen the chooser.
                    activity.getIntent().removeExtra(WorkspaceRouter.EXTRA_OPEN_FILES);
                    if(originalTop instanceof LinearLayout){
                        LinearLayout row=(LinearLayout)originalTop;
                        if(row.getChildCount()>0)row.getChildAt(0).performClick();
                    }
                }
            }finally{updating=false;}
        }
        private View findOriginalTop(LinearLayout root){
            if(root.getChildCount()==0)return null;
            View first=root.getChildAt(0);
            if(workspace==Workspace.MARKDOWN && first instanceof LinearLayout){
                LinearLayout header=(LinearLayout)first;
                return header.getChildCount()>0?header.getChildAt(0):null;
            }
            if(workspace==Workspace.ARCHIVE)return first;
            return null; // Raw JSON's inspector controls are never hidden.
        }
        private LinearLayout createShell(LinearLayout root,boolean unified){
            LinearLayout bar=new LinearLayout(activity);
            bar.setTag(TAG);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(5),dp(3),dp(5),dp(3));
            bar.setMinimumHeight(dp(48));
            files=button("Files",()->openFiles());
            files.setContentDescription("Open files or go back");
            bar.addView(files,new LinearLayout.LayoutParams(dp(52),dp(44)));
            LinearLayout heading=new LinearLayout(activity);heading.setOrientation(LinearLayout.VERTICAL);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            label=new TextView(activity);label.setTextSize(14);label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER_VERTICAL);
            heading.addView(label,new LinearLayout.LayoutParams(-1,dp(42)));
            bar.addView(heading,new LinearLayout.LayoutParams(0,dp(44),1f));
            context=button(workspace==Workspace.MARKDOWN?"Save":workspace==Workspace.ARCHIVE?"Notes":"Raw",()->openContext());
            bar.addView(context,new LinearLayout.LayoutParams(dp(54),dp(44)));
            markdown=button("✎",()->WorkspaceRouter.open(activity,Workspace.MARKDOWN,false));
            markdown.setContentDescription("Switch to Markdown Desk workspace");
            bar.addView(markdown,new LinearLayout.LayoutParams(dp(43),dp(44)));
            archive=button("▤",()->WorkspaceRouter.open(activity,Workspace.ARCHIVE,false));
            archive.setContentDescription("Switch to Archive Reader workspace");
            bar.addView(archive,new LinearLayout.LayoutParams(dp(43),dp(44)));
            more=button("⋯",()->openMenu());
            more.setContentDescription("Workspace switch and view options");
            bar.addView(more,new LinearLayout.LayoutParams(dp(42),dp(44)));
            root.addView(bar,0,new LinearLayout.LayoutParams(-1,dp(50)));
            return bar;
        }
        private void updateWidth(boolean unified){
            label.setVisibility(unified?View.VISIBLE:View.GONE);
            files.setVisibility(unified?View.VISIBLE:View.GONE);
            context.setVisibility(unified?View.VISIBLE:View.GONE);
            archive.setVisibility(View.VISIBLE);
            markdown.setVisibility(View.VISIBLE);
            if(unified){
                // Prefer the actual available width: cover, unfold and split-screen
                // are all responsive without hardcoding a particular phone model.
                boolean narrow=activity.getResources().getConfiguration().screenWidthDp<350;
                files.getLayoutParams().width=dp(narrow?45:52);
                context.getLayoutParams().width=dp(narrow?45:54);
                label.setTextSize(narrow?12:14);
            }
            shell.requestLayout();
        }
        private void updateTitle(){
            if(label==null)return;
            String prefix=workspace==Workspace.MARKDOWN?"Markdown Desk":workspace==Workspace.INSPECT?"JSON Inspector":"Archive Reader";
            String dynamic="";
            if(originalTop instanceof LinearLayout){
                LinearLayout original=(LinearLayout)originalTop;
                if(workspace==Workspace.MARKDOWN && original.getChildCount()>1){
                    View titleBox=original.getChildAt(1);
                    if(titleBox instanceof LinearLayout){
                        LinearLayout h=(LinearLayout)titleBox;
                        if(h.getChildCount()>1 && h.getChildAt(1) instanceof TextView)
                            dynamic=String.valueOf(((TextView)h.getChildAt(1)).getText());
                    }
                }else if(workspace==Workspace.ARCHIVE && original.getChildCount()>1){
                    View h=original.getChildAt(1);
                    if(h instanceof LinearLayout && ((LinearLayout)h).getChildCount()>0
                            && ((LinearLayout)h).getChildAt(0) instanceof TextView)
                        dynamic=String.valueOf(((TextView)((LinearLayout)h).getChildAt(0)).getText());
                }
            }
            String desired=dynamic.isEmpty()?prefix:dynamic;
            if(!desired.contentEquals(label.getText()))label.setText(desired);
        }
        private void updateColors(){
            int bg=Color.rgb(33,32,40),text=Color.WHITE;
            View sample=originalTop!=null?originalTop:currentRoot;
            View colorSample=sample;
            if(colorSample!=null && !(colorSample.getBackground() instanceof ColorDrawable)
                    && colorSample.getParent() instanceof View)
                colorSample=(View)colorSample.getParent();
            if(colorSample!=null && colorSample.getBackground() instanceof ColorDrawable)
                bg=((ColorDrawable)colorSample.getBackground()).getColor();
            TextView sampleLabel=findText(sample,0);
            if(sampleLabel!=null)text=sampleLabel.getCurrentTextColor();
            if(bg==lastSurface && text==lastText)return;
            lastSurface=bg;lastText=text;
            shell.setBackgroundColor(bg);
            label.setTextColor(text);
            for(Button b:new Button[]{files,context,markdown,archive,more}){
                b.setTextColor(text);
                b.setAllCaps(false);
                GradientDrawable d=new GradientDrawable();d.setCornerRadius(dp(9));
                boolean selected=(b==markdown && workspace==Workspace.MARKDOWN) ||
                    (b==archive && (workspace==Workspace.ARCHIVE||workspace==Workspace.INSPECT));
                d.setColor(selected?overlay(bg,text,0.18f):bg);
                d.setStroke(dp(1),overlay(bg,text,selected?0.45f:0.21f));
                b.setBackground(d);
            }
        }
        private static int overlay(int bg,int fg,float alpha){
            return Color.rgb(Math.round(Color.red(bg)*(1-alpha)+Color.red(fg)*alpha),
                Math.round(Color.green(bg)*(1-alpha)+Color.green(fg)*alpha),
                Math.round(Color.blue(bg)*(1-alpha)+Color.blue(fg)*alpha));
        }
        private TextView findText(View root,int depth){
            if(root instanceof TextView)return (TextView)root;
            if(depth>3 || !(root instanceof ViewGroup))return null;
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                TextView t=findText(group.getChildAt(i),depth+1);
                if(t!=null)return t;
            }
            return null;
        }
        private Button button(String name,Runnable action){
            Button b=new Button(activity);b.setText(name);b.setTextSize(12);
            b.setMinHeight(dp(44));b.setMinimumWidth(0);
            b.setPadding(dp(2),0,dp(2),0);
            b.setOnClickListener(v->action.run());
            return b;
        }
        private void openFiles(){
            if(workspace==Workspace.ARCHIVE || workspace==Workspace.INSPECT){
                WorkspaceRouter.openFiles(activity);return;
            }
            if(workspace==Workspace.MARKDOWN && originalTop instanceof LinearLayout){
                ((LinearLayout)originalTop).getChildAt(0).performClick();return;
            }
            WorkspaceRouter.open(activity,Workspace.ARCHIVE,false);
        }
        private void openContext(){
            if(workspace==Workspace.MARKDOWN && originalTop instanceof LinearLayout){
                LinearLayout old=(LinearLayout)originalTop;
                if(old.getChildCount()>2){old.getChildAt(2).performClick();return;}
            }
            if(workspace==Workspace.ARCHIVE){
                if(performTextAction(currentRoot,"Notes"))return;
            }
            WorkspaceRouter.open(activity,Workspace.ARCHIVE,false);
        }
        private boolean performTextAction(View v,String label){
            if(v==null)return false;
            if(v instanceof Button && label.equalsIgnoreCase(String.valueOf(((Button)v).getText()))){
                v.performClick();return true;
            }
            if(v instanceof ViewGroup){
                ViewGroup group=(ViewGroup)v;
                for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i)!=shell &&
                        performTextAction(group.getChildAt(i),label))return true;
            }
            return false;
        }
        private void openMenu(){
            PopupMenu popup=new PopupMenu(activity,more);
            Workspace other=workspace==Workspace.MARKDOWN?Workspace.ARCHIVE:Workspace.MARKDOWN;
            String destination=other==Workspace.MARKDOWN?"Markdown Desk":"Archive Reader";
            popup.getMenu().add("Switch to "+destination).setOnMenuItemClickListener(item->{WorkspaceRouter.open(activity,other,false);return true;});
            popup.getMenu().add("Switch & close this workspace").setOnMenuItemClickListener(item->{WorkspaceRouter.open(activity,other,true);return true;});
            popup.getMenu().add(WorkspacePreferences.unified(activity)?"Use original workspace chrome":"Use unified Markdown Desk chrome")
                .setOnMenuItemClickListener(item->{WorkspacePreferences.toggleUnified(activity);lastUnified=!WorkspacePreferences.unified(activity);reconcile();return true;});
            popup.getMenu().add("Search Archive").setOnMenuItemClickListener(item->{
                WorkspaceRouter.openArchiveSearch(activity);return true;
            });
            popup.getMenu().add("Inspect raw JSON").setOnMenuItemClickListener(item->{WorkspaceRouter.open(activity,Workspace.INSPECT,false);return true;});
            if(workspace==Workspace.MARKDOWN && originalTop instanceof LinearLayout){
                LinearLayout old=(LinearLayout)originalTop;
                if(old.getChildCount()>4){
                    popup.getMenu().add("Markdown Desk tools").setOnMenuItemClickListener(item->{
                        old.getChildAt(4).performClick();return true;
                    });
                }
                if(old.getChildCount()>3){
                    popup.getMenu().add("Help & shortcuts").setOnMenuItemClickListener(item->{
                        old.getChildAt(3).performClick();return true;
                    });
                }
            }
            if(workspace==Workspace.ARCHIVE && originalTop instanceof LinearLayout){
                popup.getMenu().add("Back within Archive").setOnMenuItemClickListener(item->{
                    ((LinearLayout)originalTop).getChildAt(0).performClick();return true;
                });
            }
            if(workspace==Workspace.ARCHIVE && originalTop instanceof LinearLayout)
                popup.getMenu().add("Archive reader options").setOnMenuItemClickListener(item->{
                    LinearLayout old=(LinearLayout)originalTop;
                    if(old.getChildCount()>0)old.getChildAt(old.getChildCount()-1).performClick();
                    return true;
                });
            popup.show();
        }
        private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    }
}