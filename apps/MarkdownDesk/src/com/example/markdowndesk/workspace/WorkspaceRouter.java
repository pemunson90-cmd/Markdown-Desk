package com.example.markdowndesk.workspace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.Toast;

/** Activities share one package/task. Do not clear task history or bypass editor save guards. */
public final class WorkspaceRouter {
    public enum Workspace { MARKDOWN, ARCHIVE, INSPECT }

    private WorkspaceRouter() {}

    public static Workspace classify(Activity activity) {
        String name=activity.getClass().getName();
        if(name.endsWith(".ArchiveWorkspaceActivity"))return Workspace.ARCHIVE;
        if(name.endsWith(".JsonExplorerActivity"))return Workspace.INSPECT;
        return Workspace.MARKDOWN;
    }

    public static boolean eligible(Activity activity) {
        String name=activity.getClass().getName();
        return name.endsWith(".MainActivity")||name.endsWith(".ArchiveWorkspaceActivity")
                ||name.endsWith(".JsonExplorerActivity");
    }

    public static final String EXTRA_OPEN_FILES = "com.example.markdowndesk.workspace.OPEN_FILES";
    public static final String EXTRA_SEARCH_ARCHIVE = "com.example.markdowndesk.workspace.SEARCH_ARCHIVE";

    public static void openArchiveSearch(Activity current){
        Intent intent=new Intent();
        intent.setClassName(current.getPackageName(),destination(Workspace.ARCHIVE));
        intent.putExtra(EXTRA_SEARCH_ARCHIVE,true);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try{current.startActivity(intent);}
        catch(RuntimeException unavailable){
            Toast.makeText(current,"Archive search is unavailable",Toast.LENGTH_LONG).show();
        }
    }


    /** Global Files navigation is hosted by the existing, protected Markdown hub. */
    public static void openFiles(Activity current){
        if(classify(current)==Workspace.MARKDOWN)return;
        Intent intent=new Intent();
        intent.setClassName(current.getPackageName(),destination(Workspace.MARKDOWN));
        intent.putExtra(EXTRA_OPEN_FILES,true);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try{current.startActivity(intent);}
        catch(RuntimeException unavailable){
            Toast.makeText(current,"Files workspace is unavailable",Toast.LENGTH_LONG).show();
        }
    }

    private static String destination(Workspace target){
        switch(target){
            case ARCHIVE:return "com.example.markdowndesk.ArchiveWorkspaceActivity";
            case INSPECT:return "com.example.markdowndesk.JsonExplorerActivity";
            default:return "com.example.markdowndesk.MainActivity";
        }
    }

    public static void open(Activity current,Workspace target,boolean closeCurrent){
        Workspace from=classify(current);
        if(from==target && target!=Workspace.INSPECT)return;
        if(!closeCurrent){
            launch(current,target);
            return;
        }
        // The main editor owns all dirty-data checks; do not call finish() here.
        // It might need to ask the owner about an unsaved document.
        if(from==Workspace.MARKDOWN){
            new AlertDialog.Builder(current).setTitle("Close Markdown workspace")
                .setMessage("Markdown Desk must finish its existing save/recovery checks first. " +
                    "The archive will open only if Markdown Desk closes safely. " +
                    "Use Switch (keep current) to leave the editor open instead.")
                .setPositiveButton("Close safely",(dialog,which)->{
                    current.onBackPressed();
                    if(current.isFinishing())launch(current,target);
                    else Toast.makeText(current,"Complete the editor's save/close step, then switch.",Toast.LENGTH_LONG).show();
                }).setNegativeButton("Keep open",null).show();
            return;
        }
        // Archive and raw JSON read-only screens have no unsaved source edits.
        // Their annotation journal is fsynced before a successful Save acknowledgement.
        if(launch(current,target))current.finish();
    }

    private static boolean launch(Activity current,Workspace target){
        Intent intent=new Intent();
        intent.setClassName(current.getPackageName(),destination(target));
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try{current.startActivity(intent);return true;}
        catch(RuntimeException exception){
            new AlertDialog.Builder(current).setTitle("Workspace unavailable")
                .setMessage("The other workspace could not be opened. Your current workspace has not been closed.")
                .setPositiveButton("OK",null).show();
            return false;
        }
    }
}