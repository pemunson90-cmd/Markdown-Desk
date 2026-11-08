package com.example.markdowndesk.workspace;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import com.example.markdowndesk.workspace.ui.WorkspaceChrome;

/** Single app, two entry points. Existing editor and archive stay untouched. */
public final class MarkdownDeskApplication extends Application implements Application.ActivityLifecycleCallbacks {
    @Override public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(this);}
    @Override public void onActivityCreated(Activity a,Bundle state){}
    @Override public void onActivityStarted(Activity a){}
    @Override public void onActivityResumed(Activity a){if(WorkspaceRouter.eligible(a))WorkspaceChrome.bind(a);}
    @Override public void onActivityPaused(Activity a){}
    @Override public void onActivityStopped(Activity a){}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle state){}
    @Override public void onActivityDestroyed(Activity a){WorkspaceChrome.unbind(a);}
}