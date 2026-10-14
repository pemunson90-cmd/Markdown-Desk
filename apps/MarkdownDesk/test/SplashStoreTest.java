import android.content.Context;
import android.net.Uri;
import com.example.markdowndesk.SplashStore;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SplashStoreTest {

    private static final byte[] PNG = {-119,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82,0,0,0,1,0,0,0,1,8,2,0,0,0,-112,119,83,-34,0,0,0,12,73,68,65,84,120,-100,99,96,96,96,0,0,0,4,0,1,-10,23,56,85,0,0,0,0,73,69,78,68,-82,66,96,-126};

    private static byte[] png(int extra) {
        byte[] out = new byte[PNG.length + extra];
        System.arraycopy(PNG, 0, out, 0, PNG.length);
        return out;
    }


    private static int passed = 0;
    private static int failed = 0;

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  pass  " + what);
        } else {
            failed++;
            System.out.println("  FAIL  " + what);
        }
    }

    private static Context fresh() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "ssdir" + System.nanoTime());
        dir.mkdirs();
        return new Context(dir);
    }

    private static SplashStore.Item byId(List<SplashStore.Item> items, String id) {
        for (SplashStore.Item i : items) {
            if (i.id.equals(id)) {
                return i;
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("defaults");
        Context c = fresh();
        List<SplashStore.Item> all = SplashStore.all(c);
        check("25 bundled images ship", all.size() == 25);
        check("all enabled on a fresh install", SplashStore.enabled(c).size() == 25);
        check("every id is unique", new HashSet<String>(idsOf(all)).size() == all.size());
        check("every bundled source points at the asset folder", allAssets(all));
        check("nothing removed to start", SplashStore.removedCount(c) == 0);

        System.out.println("toggling");
        SplashStore.Item first = all.get(0);
        SplashStore.setEnabled(c, first, false);
        check("disabling one drops it from enabled()", SplashStore.enabled(c).size() == 24);
        check("disabled image alone leaves the rotation", byId(SplashStore.enabled(c), first.id) == null);
        check("disabled state survives a reread", !byId(SplashStore.all(c), first.id).enabled);
        check("all() still lists it", SplashStore.all(c).size() == 25);
        SplashStore.setEnabled(c, first, true);
        check("re-enabling restores it", SplashStore.enabled(c).size() == 25);

        System.out.println("bulk switches");
        SplashStore.setAllEnabled(c, false);
        check("all off leaves none enabled", SplashStore.enabled(c).isEmpty());
        check("all off keeps the gallery intact", SplashStore.all(c).size() == 25);
        check("random() returns nothing when none are on", SplashStore.random(c) == null);
        SplashStore.setAllEnabled(c, true);
        check("all on restores every image", SplashStore.enabled(c).size() == 25);

        System.out.println("removing and restoring bundled images");
        SplashStore.remove(c, byId(SplashStore.all(c), first.id));
        check("removed image leaves the gallery", SplashStore.all(c).size() == 24);
        check("removedCount tracks it", SplashStore.removedCount(c) == 1);
        check("removed image is not in the rotation", byId(SplashStore.enabled(c), first.id) == null);
        SplashStore.restoreBundled(c);
        check("restore brings it back", SplashStore.all(c).size() == 25);
        check("restored image is enabled", byId(SplashStore.all(c), first.id).enabled);

        System.out.println("importing");
        c = fresh();
        Uri png = Uri.of("My Art_01.png", "image/png", PNG);
        SplashStore.Item imported = SplashStore.importImage(c, png);
        check("import returns an item", imported != null);
        check("import is not bundled", !imported.bundled);
        check("import is enabled by default", imported.enabled);
        check("label cleans up the file name", "My Art 01".equals(imported.label));
        check("bytes land in a private file", new File(imported.source).length() == PNG.length);
        check("file keeps the source extension", imported.source.endsWith(".png"));
        check("gallery grows by one", SplashStore.all(c).size() == 26);
        check("rotation grows by one", SplashStore.enabled(c).size() == 26);
        check("import survives a reread", byId(SplashStore.all(c), imported.id) != null);

        SplashStore.Item second = SplashStore.importImage(c, Uri.of("second.jpg", "image/jpeg", PNG));
        check("two imports coexist", SplashStore.all(c).size() == 27);
        check("imports get distinct ids", !second.id.equals(imported.id));
        check("imports get distinct files", !second.source.equals(imported.source));

        SplashStore.setEnabled(c, byId(SplashStore.all(c), imported.id), false);
        check("imports can be switched off", SplashStore.enabled(c).size() == 26);
        check("switching off does not delete the file", new File(imported.source).exists());

        System.out.println("deleting an import");
        String path = imported.source;
        SplashStore.remove(c, byId(SplashStore.all(c), imported.id));
        check("deleted import leaves the gallery", SplashStore.all(c).size() == 26);
        check("deleted import's file is gone", !new File(path).exists());
        check("the other import is untouched", new File(second.source).exists());
        check("deleting an import does not touch removedCount", SplashStore.removedCount(c) == 0);
        SplashStore.restoreBundled(c);
        check("restore does not resurrect deleted imports", SplashStore.all(c).size() == 26);

        System.out.println("rejecting bad input");
        c = fresh();
        int before = SplashStore.all(c).size();
        try {
            SplashStore.importImage(c, Uri.of("notes.txt", "text/plain", new byte[]{0}));
            check("a non-image import throws", false);
        } catch (Exception e) {
            check("a non-image import throws", true);
        }
        check("a rejected import adds nothing", SplashStore.all(c).size() == before);
        check("a rejected import leaves no file behind", countFiles(c) == 0);

        byte[] huge = png(13 * 1024 * 1024);
        try {
            SplashStore.importImage(c, Uri.of("huge.png", "image/png", huge));
            check("an oversized import throws", false);
        } catch (Exception e) {
            check("an oversized import throws", true);
        }
        check("an oversized import leaves no file behind", countFiles(c) == 0);

        System.out.println("random selection");
        c = fresh();
        Map<String, Integer> counts = new HashMap<String, Integer>();
        String previous = null;
        boolean repeated = false;
        for (int i = 0; i < 4000; i++) {
            SplashStore.Item pick = SplashStore.random(c);
            if (previous != null && pick.id.equals(previous)) {
                repeated = true;
            }
            previous = pick.id;
            Integer n = counts.get(pick.id);
            counts.put(pick.id, n == null ? 1 : n + 1);
        }
        check("never repeats the previous image back to back", !repeated);
        check("every enabled image gets chosen", counts.size() == 25);
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int n : counts.values()) {
            min = Math.min(min, n);
            max = Math.max(max, n);
        }
        check("spread stays reasonable (min " + min + ", max " + max + " of ~160)", min > 85 && max < 245);

        c = fresh();
        SplashStore.setAllEnabled(c, false);
        SplashStore.Item only = SplashStore.all(c).get(3);
        SplashStore.setEnabled(c, only, true);
        for (int i = 0; i < 20; i++) {
            SplashStore.Item pick = SplashStore.random(c);
            if (pick == null || !pick.id.equals(only.id)) {
                check("a single enabled image is always chosen", false);
                break;
            }
        }
        check("a single enabled image is always chosen", true);

        System.out.println("corrupt preferences");
        c = fresh();
        c.getSharedPreferences("markdown_desk", 0).edit().putString("splash_disabled", "{not json").apply();
        c.getSharedPreferences("markdown_desk", 0).edit().putString("splash_custom", "also not json").apply();
        check("bad preferences do not crash all()", SplashStore.all(c).size() == 25);
        check("bad preferences are cleared", SplashStore.enabled(c).size() == 25);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static List<String> idsOf(List<SplashStore.Item> items) {
        List<String> out = new ArrayList<String>();
        for (SplashStore.Item i : items) {
            out.add(i.id);
        }
        return out;
    }

    private static boolean allAssets(List<SplashStore.Item> items) {
        for (SplashStore.Item i : items) {
            if (!i.bundled || !i.source.startsWith("splash/") || !i.source.endsWith(".webp")) {
                return false;
            }
        }
        return true;
    }

    private static int countFiles(Context c) {
        File dir = new File(c.getFilesDir(), "splash_images");
        String[] names = dir.list();
        return names == null ? 0 : names.length;
    }
}
