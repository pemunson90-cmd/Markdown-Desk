package com.example.markdowndesk;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Catalogue of launch splash images: the ones shipped with the app plus any the
 * reader has imported. Each entry can be switched off without being removed, and
 * removed entries can be brought back.
 */
public final class SplashStore {

    private static final String PREFS = "markdown_desk";
    private static final String KEY_DISABLED = "splash_disabled";
    private static final String KEY_REMOVED = "splash_removed";
    private static final String KEY_CUSTOM = "splash_custom";
    private static final String KEY_LAST = "splash_last";

    private static final String ASSET_DIR = "splash";
    private static final String IMPORT_DIR = "splash_images";
    private static final long MAX_IMPORT_BYTES = 12L * 1024L * 1024L;

    private static final Random RANDOM = new Random();

    /** Images shipped inside the APK, in the order they appear in the gallery. */
    private static final String[][] BUNDLED = {
            {"splash_01_cardigan", "Cardigan and lace"},
            {"splash_02_seconds", "Second helping"},
            {"splash_03_silver", "Silver horns"},
            {"splash_04_striped", "Striped socks"},
            {"splash_05_chin", "Chin in hands"},
            {"splash_06_garters", "Garters"},
            {"splash_07_scrap", "One more scrap"},
            {"splash_08_fanned", "Whole folder"},
            {"splash_09_turtleneck", "Turtleneck"},
            {"splash_10_shoulder", "Over the shoulder"},
            {"splash_11_banner", "Wide banner"},
            {"splash_12_lookback", "Looking back"},
            {"splash_13_profile", "Side profile"},
            {"splash_14_armraised", "Arm raised"},
            {"splash_15_kneesup", "Knees up"},
            {"splash_16_lace", "Lace, cross-legged"},
            {"splash_17_sigil", "Sigil hoodie"},
            {"splash_18_whitetee", "White tee"},
            {"splash_19_fishnet", "Fishnet"},
            {"splash_20_sweater", "Striped sweater"},
            {"splash_21_bunny", "Pink bunny"},
            {"splash_22_chibi_avatar", "Chibi avatar"},
            {"splash_23_fullbody_cutout", "Full-body cutout"},
            {"splash_24_folder_chew", "Folder chew"},
            {"splash_25_folder_devour", "Folder devour"},
    };

    private SplashStore() {
    }

    public static final class Item {
        public final String id;
        public final String label;
        public final boolean bundled;
        /** Asset path for bundled images, absolute file path for imported ones. */
        public final String source;
        public boolean enabled;

        Item(String id, String label, boolean bundled, String source, boolean enabled) {
            this.id = id;
            this.label = label;
            this.bundled = bundled;
            this.source = source;
            this.enabled = enabled;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, 0);
    }

    private static Set<String> readSet(Context context, String key) {
        Set<String> out = new HashSet<String>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.isEmpty()) {
                    out.add(value);
                }
            }
        } catch (JSONException e) {
            prefs(context).edit().remove(key).apply();
        }
        return out;
    }

    private static void writeSet(Context context, String key, Set<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        prefs(context).edit().putString(key, array.toString()).apply();
    }

    private static List<Item> readCustom(Context context, Set<String> disabled) {
        List<Item> out = new ArrayList<Item>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY_CUSTOM, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                String id = entry.optString("id", "");
                String file = entry.optString("file", "");
                if (id.isEmpty() || file.isEmpty()) {
                    continue;
                }
                File stored = new File(importDir(context), file);
                if (!stored.exists()) {
                    continue;
                }
                out.add(new Item(id, entry.optString("label", "Imported image"), false,
                        stored.getAbsolutePath(), !disabled.contains(id)));
            }
        } catch (JSONException e) {
            prefs(context).edit().remove(KEY_CUSTOM).apply();
        }
        return out;
    }

    private static void writeCustom(Context context, List<Item> items) {
        JSONArray array = new JSONArray();
        for (Item item : items) {
            if (item.bundled) {
                continue;
            }
            JSONObject entry = new JSONObject();
            try {
                entry.put("id", item.id);
                entry.put("label", item.label);
                entry.put("file", new File(item.source).getName());
                array.put(entry);
            } catch (JSONException e) {
                // A single unwritable entry is dropped rather than losing the rest.
            }
        }
        prefs(context).edit().putString(KEY_CUSTOM, array.toString()).apply();
    }

    private static File importDir(Context context) {
        File dir = new File(context.getFilesDir(), IMPORT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Every image the reader can see in the gallery, bundled first. */
    public static List<Item> all(Context context) {
        Set<String> disabled = readSet(context, KEY_DISABLED);
        Set<String> removed = readSet(context, KEY_REMOVED);
        List<Item> out = new ArrayList<Item>();
        for (String[] entry : BUNDLED) {
            if (removed.contains(entry[0])) {
                continue;
            }
            out.add(new Item(entry[0], entry[1], true, ASSET_DIR + "/" + entry[0] + ".webp",
                    !disabled.contains(entry[0])));
        }
        out.addAll(readCustom(context, disabled));
        return out;
    }

    public static List<Item> enabled(Context context) {
        List<Item> out = new ArrayList<Item>();
        for (Item item : all(context)) {
            if (item.enabled) {
                out.add(item);
            }
        }
        return out;
    }

    public static int removedCount(Context context) {
        return readSet(context, KEY_REMOVED).size();
    }

    public static void setEnabled(Context context, Item item, boolean enabled) {
        Set<String> disabled = readSet(context, KEY_DISABLED);
        if (enabled) {
            disabled.remove(item.id);
        } else {
            disabled.add(item.id);
        }
        writeSet(context, KEY_DISABLED, disabled);
        item.enabled = enabled;
    }

    public static void setAllEnabled(Context context, boolean enabled) {
        Set<String> disabled = readSet(context, KEY_DISABLED);
        for (Item item : all(context)) {
            if (enabled) {
                disabled.remove(item.id);
            } else {
                disabled.add(item.id);
            }
        }
        writeSet(context, KEY_DISABLED, disabled);
    }

    /**
     * Removes an image from the gallery. Imported files are deleted from disk;
     * bundled images are hidden and can be brought back with {@link #restoreBundled}.
     */
    public static void remove(Context context, Item item) {
        if (item.bundled) {
            Set<String> removed = readSet(context, KEY_REMOVED);
            removed.add(item.id);
            writeSet(context, KEY_REMOVED, removed);
        } else {
            List<Item> keep = new ArrayList<Item>();
            for (Item existing : readCustom(context, Collections.<String>emptySet())) {
                if (existing.id.equals(item.id)) {
                    new File(existing.source).delete();
                } else {
                    keep.add(existing);
                }
            }
            writeCustom(context, keep);
        }
        Set<String> disabled = readSet(context, KEY_DISABLED);
        if (disabled.remove(item.id)) {
            writeSet(context, KEY_DISABLED, disabled);
        }
    }

    public static void restoreBundled(Context context) {
        prefs(context).edit().remove(KEY_REMOVED).apply();
    }

    /**
     * Copies the picked image into private storage so it keeps working after the
     * original is moved or its permission is revoked.
     */
    public static Item importImage(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String displayName = queryDisplayName(resolver, uri);
        String extension = extensionFor(displayName, resolver.getType(uri));
        String fileName = UUID.randomUUID().toString() + extension;
        File target = new File(importDir(context), fileName);

        InputStream input = resolver.openInputStream(uri);
        if (input == null) {
            throw new IOException("That image could not be opened.");
        }
        long copied = 0;
        try {
            OutputStream output = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    copied += read;
                    if (copied > MAX_IMPORT_BYTES) {
                        throw new IOException("That image is larger than the 12 MB limit.");
                    }
                    output.write(buffer, 0, read);
                }
                output.flush();
            } finally {
                output.close();
            }
        } catch (IOException e) {
            target.delete();
            throw e;
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
                // Nothing useful to do with a failure to close a read stream.
            }
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(target.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            target.delete();
            throw new IOException("That file is not an image Android can display.");
        }

        Item item = new Item(UUID.randomUUID().toString(), labelFor(displayName), false,
                target.getAbsolutePath(), true);
        List<Item> custom = readCustom(context, Collections.<String>emptySet());
        custom.add(item);
        writeCustom(context, custom);
        return item;
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        if (!"content".equalsIgnoreCase(uri.getScheme())) {
            return uri.getLastPathSegment();
        }
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    return cursor.getString(column);
                }
            }
        } catch (Exception e) {
            return uri.getLastPathSegment();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return uri.getLastPathSegment();
    }

    private static String extensionFor(String displayName, String mimeType) {
        if (displayName != null) {
            int dot = displayName.lastIndexOf(46);
            if (dot > 0 && dot < displayName.length() - 1) {
                String candidate = displayName.substring(dot).toLowerCase(Locale.US);
                if (candidate.length() <= 6 && candidate.matches("\\.[a-z0-9]+")) {
                    return candidate;
                }
            }
        }
        if (mimeType != null) {
            if (mimeType.endsWith("png")) {
                return ".png";
            }
            if (mimeType.endsWith("webp")) {
                return ".webp";
            }
        }
        return ".jpg";
    }

    private static String labelFor(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "Imported image";
        }
        String name = displayName.trim();
        int dot = name.lastIndexOf(46);
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replace('_', ' ').replace('-', ' ').trim();
        if (name.isEmpty()) {
            return "Imported image";
        }
        return name.length() > 42 ? name.substring(0, 41) + "…" : name;
    }

    /**
     * Picks an enabled image at random, avoiding an immediate repeat of the last
     * one shown whenever there is more than one to choose from.
     */
    public static Item random(Context context) {
        List<Item> pool = enabled(context);
        if (pool.isEmpty()) {
            return null;
        }
        Item chosen = pool.get(RANDOM.nextInt(pool.size()));
        if (pool.size() > 1) {
            String last = prefs(context).getString(KEY_LAST, "");
            int guard = 0;
            while (chosen.id.equals(last) && guard < 8) {
                chosen = pool.get(RANDOM.nextInt(pool.size()));
                guard++;
            }
        }
        prefs(context).edit().putString(KEY_LAST, chosen.id).apply();
        return chosen;
    }

    /** Decodes an image down to roughly the requested box, never larger than the source. */
    public static Bitmap load(Context context, Item item, int maxWidth, int maxHeight) {
        if (item == null) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            decode(context, item, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight,
                    Math.max(1, maxWidth), Math.max(1, maxHeight));
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return decode(context, item, options);
        } catch (IOException e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static Bitmap decode(Context context, Item item, BitmapFactory.Options options)
            throws IOException {
        if (!item.bundled) {
            return BitmapFactory.decodeFile(item.source, options);
        }
        InputStream input = context.getAssets().open(item.source);
        try {
            return BitmapFactory.decodeStream(input, null, options);
        } finally {
            input.close();
        }
    }

    private static int sampleSize(int width, int height, int maxWidth, int maxHeight) {
        int sample = 1;
        while (width / (sample * 2) >= maxWidth && height / (sample * 2) >= maxHeight) {
            sample *= 2;
        }
        return sample;
    }
}
