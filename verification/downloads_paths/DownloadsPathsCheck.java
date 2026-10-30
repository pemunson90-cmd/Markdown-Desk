package com.example.markdowndesk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Direct production DownloadsPaths regression; no Android mocks or signing. */
public final class DownloadsPathsCheck {
    static int assertions;
    static void ok(boolean success, String explanation) {
        assertions++;
        if (!success) throw new AssertionError(explanation);
    }
    interface Checked { void run() throws Exception; }
    static void rejected(Checked action) throws Exception {
        boolean blocked = false;
        try { action.run(); } catch (IOException expected) { blocked = true; }
        ok(blocked, "Expected invalid or inaccessible Downloads path to fail");
    }
    public static void main(String[] args) throws Exception {
        Path parent = Files.createTempDirectory("md-downloads-check-");
        Path root = Files.createDirectory(parent.resolve("Download"));
        try {
            Files.write(root.resolve("one.md"), new byte[]{1});
            Files.write(root.resolve("not-an-editor.pdf"), new byte[]{1});
            Path sub = Files.createDirectory(root.resolve("Notes"));
            Files.write(sub.resolve("two.txt"), new byte[]{2});
            Files.write(parent.resolve("outside.md"), new byte[]{3});
            ok(DownloadsPaths.supported("ONE.MD"), "Case-insensitive supported extension");
            ok(!DownloadsPaths.supported("one.md.exe"), "Do not trust disguised extension");
            List<DownloadsPaths.Entry> items = DownloadsPaths.list(root.toFile(), "");
            ok(items.size() == 2, "Only supported document and folder");
            ok(items.get(0).directory && "Notes".equals(items.get(0).name), "Folder first");
            ok("one.md".equals(items.get(1).relative), "Stable file location");
            ok(DownloadsPaths.list(root.toFile(), "Notes").size() == 1, "Nested folder listing");
            ok("Notes/two.txt".equals(DownloadsPaths.relative(root.toFile(), sub.resolve("two.txt").toFile())), "Canonical nested path");
            ok(DownloadsPaths.parent("Notes/two.txt").equals("Notes"), "Parent path");
            ok(DownloadsPaths.parent("one.md").equals(""), "Root parent");
            rejected(() -> DownloadsPaths.document(root.toFile(), "../outside.md"));
            rejected(() -> DownloadsPaths.document(root.toFile(), "not-an-editor.pdf"));
            rejected(() -> DownloadsPaths.document(root.toFile(), "missing.md"));
            rejected(() -> DownloadsPaths.document(root.toFile(), "Notes"));
            rejected(() -> DownloadsPaths.directory(root.toFile(), "../"));
            rejected(() -> DownloadsPaths.document(root.toFile(), "/one.md"));
            rejected(() -> DownloadsPaths.document(root.toFile(), "Notes/../../outside.md"));
            try {
                Files.createSymbolicLink(root.resolve("outside-link.md"), parent.resolve("outside.md"));
                rejected(() -> DownloadsPaths.document(root.toFile(), "outside-link.md"));
                ok(DownloadsPaths.list(root.toFile(), "").size() == 2, "Hide symlink");
            } catch (UnsupportedOperationException | SecurityException notSupported) {
                // Symbolic link unavailable in this runtime; other traversal checks remain.
            }
            System.out.println("DownloadsPathsCheck " + assertions + " passed");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(parent)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException failure) { throw new RuntimeException(failure); }
                });
            }
        }
    }
}
