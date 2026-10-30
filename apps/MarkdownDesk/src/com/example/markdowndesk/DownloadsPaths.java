package com.example.markdowndesk;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Filesystem-only, platform-independent safety boundary for a voluntary Downloads browser.
 * Never infer a local path from a DownloadsProvider numeric ID or an arbitrary content URI.
 */
final class DownloadsPaths {
    private static final int MAX_VISIBLE_ITEMS = 2000;
    static final class Entry {
        final String name;
        final String relative;
        final boolean directory;
        Entry(String name, String relative, boolean directory) {
            this.name = name;
            this.relative = relative;
            this.directory = directory;
        }
    }
    private DownloadsPaths() {}

    static boolean supported(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown")
                || lower.endsWith(".mdown") || lower.endsWith(".txt")
                || lower.endsWith(".text");
    }

    private static Path inside(File root, String relative) throws IOException {
        if (root == null || relative == null || relative.startsWith("/")
                || relative.indexOf('\\') >= 0 || relative.indexOf('\u0000') >= 0) {
            throw new IOException("Invalid Downloads path.");
        }
        Path base = root.getCanonicalFile().toPath();
        Path actual = new File(base.toFile(), relative).getCanonicalFile().toPath();
        if (!actual.startsWith(base)) throw new IOException("Downloads path escapes the authorized folder.");
        return actual;
    }

    static File document(File root, String relative) throws IOException {
        Path p = inside(root, relative);
        if (p.equals(root.getCanonicalFile().toPath()) || !Files.isRegularFile(p)
                || !Files.isReadable(p) || !supported(p.getFileName().toString())) {
            throw new IOException("Downloaded document is missing, inaccessible, or unsupported.");
        }
        return p.toFile();
    }

    static File directory(File root, String relative) throws IOException {
        Path p = inside(root, relative);
        if (!Files.isDirectory(p) || !Files.isReadable(p)) {
            throw new IOException("Downloads folder is inaccessible.");
        }
        return p.toFile();
    }

    static String relative(File root, File file) throws IOException {
        if (file == null) throw new IOException("No Downloads file was supplied.");
        Path base = root.getCanonicalFile().toPath();
        Path path = file.getCanonicalFile().toPath();
        if (path.equals(base) || !path.startsWith(base)) {
            throw new IOException("This file is outside Downloads.");
        }
        String result = base.relativize(path).toString().replace(File.separatorChar, '/');
        document(root, result);
        return result;
    }

    static List<Entry> list(File root, String relative) throws IOException {
        Path folder = directory(root, relative).toPath();
        List<Entry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path candidate : stream) {
                if (Files.isSymbolicLink(candidate) || !Files.isReadable(candidate)) continue;
                boolean isDirectory = Files.isDirectory(candidate);
                if (!isDirectory && (!Files.isRegularFile(candidate)
                        || !supported(candidate.getFileName().toString()))) continue;
                if (entries.size() == MAX_VISIBLE_ITEMS) {
                    throw new IOException("Downloads contains too many entries for one view. Sort some files into subfolders.");
                }
                String name = candidate.getFileName().toString();
                String child = relative.length() == 0 ? name : relative + "/" + name;
                // Validate each discovered path independently, even if a directory races with us.
                try {
                    inside(root, child);
                } catch (IOException rejected) {
                    continue;
                }
                entries.add(new Entry(name, child, isDirectory));
            }
        }
        Collections.sort(entries, Comparator
                .comparing((Entry item) -> !item.directory)
                .thenComparing(item -> item.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.name));
        return Collections.unmodifiableList(entries);
    }

    static String parent(String relative) {
        int slash = relative.lastIndexOf('/');
        return slash < 0 ? "" : relative.substring(0, slash);
    }
}
