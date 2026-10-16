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
    static WorkspaceHarness.AlertDialog dialog(WorkspaceHarness h){
        yes(h.favoriteDialog!=null&&h.favoriteDialog.isShowing(),"visible, current favorites dialog");
        return h.favoriteDialog;
    }
    static void settle(WorkspaceHarness h){h.drainLocal();}
    static void normalAndPersistence()throws Exception {
        WorkspaceUiCheck.Prefs roots=new WorkspaceUiCheck.Prefs(),favorites=new WorkspaceUiCheck.Prefs();
        WorkspaceUiCheck.Provider provider=new WorkspaceUiCheck.Provider();
        WorkspaceHarness h=withDocument(roots,favorites,provider);
        h.showWorkspaceFavorites();settle(h);
        WorkspaceHarness.AlertDialog empty=dialog(h);
        yes(empty.message.contains("No favorites"),"empty UI is honest");
        empty.getButton(-1).tap();settle(h);
        WorkspaceHarness.AlertDialog list=dialog(h);
        yes(list.items.length==1&&list.items[0].contains("A real document.md"),"document pin shown in UI");
        List<WorkspaceFavoritesStore.Favorite> saved=new WorkspaceFavoritesStore(h.application).read();
        yes(saved.size()==1&&saved.get(0).uri.equals(DOCUMENT)
           &&saved.get(0).kind==WorkspaceFavoritesStore.Kind.DOCUMENT,"actual document store persists exact URI");
        yes(provider.takes==0&&provider.queries==0,"favorite did not request permissions or provider calls");
        list.item(0);
        WorkspaceHarness.AlertDialog options=dialog(h);
        yes(options.message.contains("current document permission"),"document options describe permission");
        options.getButton(-1).tap();
        yes(h.opens==1&&DOCUMENT.equals(h.opened)&&h.openExisting,"favorite opens ordinary route");
        h.showWorkspaceFavorites();settle(h);dialog(h).item(0);
        dialog(h).getButton(-3).tap();settle(h);
        yes(dialog(h).items==null&&new WorkspaceFavoritesStore(h.application).read().isEmpty(),
           "removing shortcut preserves document and leaves empty list");
        yes(h.opens==1&&provider.takes==0&&provider.queries==0,"unpin never changes opened document or provider");
        WorkspaceHarness again=withDocument(roots,favorites,provider);
        again.showWorkspaceFavorites();settle(again);
        yes(dialog(again).items==null,"removed shortcut stays removed across recreated Activity");
    }
    static void failuresAndStale()throws Exception {
        WorkspaceUiCheck.Prefs roots=new WorkspaceUiCheck.Prefs(),favorites=new WorkspaceUiCheck.Prefs();
        WorkspaceUiCheck.Provider provider=new WorkspaceUiCheck.Provider();
        WorkspaceHarness empty=new WorkspaceHarness(new Context(roots,favorites,provider));
        empty.showWorkspaceFavorites();settle(empty);dialog(empty).getButton(-1).tap();
        yes(empty.error.contains("Save or open"),"cannot favorite untitled unsaved current document");
        yes(favorites.value==null,"refused unbound favorite makes no storage write");
        WorkspaceHarness h=withDocument(roots,favorites,provider);
        favorites.commit=false;h.showWorkspaceFavorites();settle(h);dialog(h).getButton(-1).tap();settle(h);
        yes(h.error.contains("Favorite could not be saved"),"failed disk commit is not reported as saved");
        favorites.commit=true;
        h.showWorkspaceFavorites();h.pause();settle(h);
        yes(h.favoriteDialog==null,"queued stale UI discarded after onPause");
        yes(h.io.tasks.isEmpty(),"stale async work settled");
    }
    static void folderPermission()throws Exception {
        WorkspaceUiCheck.Prefs roots=new WorkspaceUiCheck.Prefs(),favorites=new WorkspaceUiCheck.Prefs();
        WorkspaceUiCheck.Provider provider=new WorkspaceUiCheck.Provider();
        WorkspaceHarness h=new WorkspaceHarness(new Context(roots,favorites,provider));
        h.pinWorkspaceFavorite(TREE,"Projects",WorkspaceFavoritesStore.Kind.TREE);settle(h);
        WorkspaceHarness.AlertDialog list=dialog(h);
        yes(list.items.length==1&&list.items[0].contains("Folder · Projects"),"folder favorite appears");
        list.item(0);dialog(h).getButton(-1).tap();settle(h);
        yes(h.error.contains("needs access")&&provider.takes==0,"no permission means no guessed folder open");
        yes(new WorkspaceFavoritesStore(h.application).read().size()==1,"missing grant preserves shortcut");
        h.result(-1,WorkspaceUiCheck.selection(65));WorkspaceUiCheck.settle(h);
        h.showWorkspaceFavorites();settle(h);dialog(h).item(0);dialog(h).getButton(-1).tap();
        yes(h.browsing(),"real current saved-folder grant permits ordinary browser navigation");
        h.cancel();
    }
    public static void main(String[] args)throws Exception{
        normalAndPersistence();failuresAndStale();folderPermission();
        System.out.println("Favorites actual UI: "+checks+
          " checks PASS; exact Activity route and local store, modeled Android permissions");
    }
}
