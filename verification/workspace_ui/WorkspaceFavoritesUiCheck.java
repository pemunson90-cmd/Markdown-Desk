package com.example.markdowndesk;
import android.content.Context;
import android.net.Uri;
import java.util.List;

/** Extracted real Activity favorites UI, real persistent store, modeled Android surfaces. */
public final class WorkspaceFavoritesUiCheck {
    static int checks;
    static void yes(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final Uri DOCUMENT=Uri.parse("content://fixture/document/draft");
    static final Uri TREE=WorkspaceUiCheck.ROOT;

    static WorkspaceHarness withDocument(WorkspaceUiCheck.Prefs roots,WorkspaceUiCheck.Prefs favorites,
                                          WorkspaceUiCheck.Provider provider){
        WorkspaceHarness h=new WorkspaceHarness(new Context(roots,favorites,provider));
        WorkspaceHarness.DocumentTab tab=new WorkspaceHarness.DocumentTab();
        tab.id="tab-1";tab.displayName="A real document.md";
        h.tabs.add(tab);h.activeTab=0;h.currentUri=DOCUMENT;
        return h;
    }
    // Keep production access modifiers unchanged: the test invokes exact extracted
    // private Activity members through reflection, matching Android's ordinary UI path.
    static void show(WorkspaceHarness h)throws Exception{
        java.lang.reflect.Method method=WorkspaceHarness.class.getDeclaredMethod("showWorkspaceFavorites");
        method.setAccessible(true);method.invoke(h);
    }
    static void pin(WorkspaceHarness h,Uri uri,String label,WorkspaceFavoritesStore.Kind kind)throws Exception{
        java.lang.reflect.Method method=WorkspaceHarness.class.getDeclaredMethod("pinWorkspaceFavorite",
                Uri.class,String.class,WorkspaceFavoritesStore.Kind.class);
        method.setAccessible(true);method.invoke(h,uri,label,kind);
    }
    static WorkspaceHarness.AlertDialog current(WorkspaceHarness h)throws Exception{
        java.lang.reflect.Field field=WorkspaceHarness.class.getDeclaredField("favoriteDialog");
        field.setAccessible(true);return (WorkspaceHarness.AlertDialog)field.get(h);
    }
    static WorkspaceHarness.AlertDialog dialog(WorkspaceHarness h)throws Exception{
        WorkspaceHarness.AlertDialog dialog=current(h);
        yes(dialog!=null&&dialog.isShowing(),"visible, current favorites dialog");
        return dialog;
    }
    static void settle(WorkspaceHarness h){
        // UI callback can schedule fresh disk work, which then posts another UI
        // callback. Drain both queues until stable, rather than stopping at one turn.
        for(int i=0;i<8;i++){
            h.drainLocal();
            if(h.io.tasks.isEmpty()&&!android.os.Handler.pending())return;
        }
        throw new AssertionError("Favorites I/O and UI delivery did not settle");
    }
    static void normalAndPersistence()throws Exception {
        WorkspaceUiCheck.Prefs roots=new WorkspaceUiCheck.Prefs(),favorites=new WorkspaceUiCheck.Prefs();
        WorkspaceUiCheck.Provider provider=new WorkspaceUiCheck.Provider();
        WorkspaceHarness h=withDocument(roots,favorites,provider);
        show(h);settle(h);
        WorkspaceHarness.AlertDialog empty=dialog(h);
        yes(empty.message.contains("No favorites"),"empty UI is honest");
        empty.getButton(-2).tap();
        // The direct Workspace star performs a single save action, without
        // re-opening Favorites or presenting a transient confirmation dialog.
        h.tapWorkspaceAction("☆ Favorite current document");settle(h);
        yes(current(h)==null,"single-tap pin leaves no surprising Favorites dialog");
        yes(android.widget.Toast.latest.contains("Added to Favorites"),"one-tap pin confirms via toast");
        show(h);settle(h);
        WorkspaceHarness.AlertDialog list=dialog(h);
        yes(list.items.length==1&&list.items[0].contains("A real document.md"),"document pin shown in UI");
        yes(list.message==null,"populated favorite list is not displaced by message text");
        List<WorkspaceFavoritesStore.Favorite> saved=new WorkspaceFavoritesStore(h.application).read();
        yes(saved.size()==1&&saved.get(0).uri.equals(DOCUMENT)
           &&saved.get(0).kind==WorkspaceFavoritesStore.Kind.DOCUMENT,"actual document store persists exact URI");
        yes(provider.takes==0&&provider.queries==0,"favorite did not request permissions or provider calls");
        list.item(0);
        yes(h.opens==1&&DOCUMENT.equals(h.opened)&&h.openExisting,"single-tap favorite opens ordinary route");
        yes(current(h)==null,"one-tap favorite opens without options dialog");
        show(h);settle(h);dialog(h).getButton(-3).tap();settle(h);
        WorkspaceHarness.AlertDialog remove=dialog(h);
        yes(remove.message==null,"remove picker shows list instead of swallowing it behind dialog message");
        yes(remove.items.length==1,"removal lists only saved shortcuts");
        remove.item(0);settle(h);
        yes(new WorkspaceFavoritesStore(h.application).read().isEmpty(),
           "removing shortcut preserves document and leaves empty list");
        yes(android.widget.Toast.latest.contains("Favorite removed"),"remove gives inline feedback");
        yes(h.opens==1&&provider.takes==0&&provider.queries==0,"unpin never changes opened document or provider");
        WorkspaceHarness again=withDocument(roots,favorites,provider);
        show(again);settle(again);
        yes(dialog(again).items==null,"removed shortcut stays removed across recreated Activity");
    }
    static void failuresAndStale()throws Exception {
        WorkspaceUiCheck.Prefs roots=new WorkspaceUiCheck.Prefs(),favorites=new WorkspaceUiCheck.Prefs();
        WorkspaceUiCheck.Provider provider=new WorkspaceUiCheck.Provider();
        WorkspaceHarness empty=new WorkspaceHarness(new Context(roots,favorites,provider));
        show(empty);settle(empty);dialog(empty).getButton(-1).tap();
        yes(empty.error.contains("Save or open"),"cannot favorite untitled unsaved current document");
        yes(favorites.value==null,"refused unbound favorite makes no storage write");
        WorkspaceHarness h=withDocument(roots,favorites,provider);
        favorites.commit=false;show(h);settle(h);dialog(h).getButton(-1).tap();settle(h);
        yes(h.error.contains("Favorite could not be saved"),"failed disk commit is not reported as saved");
        favorites.commit=true;
        show(h);h.pause();settle(h);
        yes(current(h)==null,"queued stale UI discarded after onPause");
        yes(h.io.tasks.isEmpty(),"stale async work settled");
    }
    static void folderPermission()throws Exception {
        WorkspaceUiCheck.Prefs roots=new WorkspaceUiCheck.Prefs(),favorites=new WorkspaceUiCheck.Prefs();
        WorkspaceUiCheck.Provider provider=new WorkspaceUiCheck.Provider();
        WorkspaceHarness h=new WorkspaceHarness(new Context(roots,favorites,provider));
        pin(h,TREE,"Projects",WorkspaceFavoritesStore.Kind.TREE);settle(h);
        show(h);settle(h);
        WorkspaceHarness.AlertDialog list=dialog(h);
        yes(list.items.length==1&&list.items[0].contains("Folder · Projects"),"folder favorite appears");
        list.item(0);settle(h);
        yes(h.error.contains("needs access")&&provider.takes==0,"no permission means no guessed folder open");
        yes(new WorkspaceFavoritesStore(h.application).read().size()==1,"missing grant preserves shortcut");
        h.result(-1,WorkspaceUiCheck.selection(65));WorkspaceUiCheck.settle(h);
        show(h);settle(h);dialog(h).item(0);
        yes(h.browsing(),"one tap with real saved-folder grant opens ordinary browser");
        h.cancel();
    }
    public static void main(String[] args){
        // The modeled provider broker has process-global workers. Match the existing
        // workspace harness by exiting deterministically after test completion.
        try{
            normalAndPersistence();failuresAndStale();folderPermission();
            System.out.println("Favorites actual UI: "+checks+
                " checks PASS; exact Activity route and local store, modeled Android permissions");
            System.exit(0);
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
}
