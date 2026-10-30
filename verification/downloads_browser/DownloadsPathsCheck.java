package com.example.markdowndesk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DownloadsPathsCheck {
    static int checks;
    private static void ok(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private interface Checked { void run() throws Exception; }
    private static void refuses(Checked action, String label) throws Exception {
        boolean rejected = false;
        try { action.run(); } catch (IOException expected) { rejected = true; }
        ok(rejected, label);
    }
    private static void test(File root) throws Exception {
        Path base = root.toPath();
        Path sub = Files.createDirectory(base.resolve("Nested"));
        Files.write(base.resolve("alpha.md"), new byte[]{35, 32, 65});
        Files.write(base.resolve("BETA.TXT"), new byte[]{66});
        Files.write(base.resolve("notes.jpg"), new byte[]{67});
        Files.write(sub.resolve("child.markdown"), new byte[]{68});
        List<DownloadsPaths.Entry> top = DownloadsPaths.list(root, "");
        ok(top.size() == 3, "text files and subdirectory only");
        ok(top.get(0).directory && top.get(0).name.equals("Nested"), "directories first");
        ok(top.get(1).name.equals("alpha.md") && top.get(2).name.equals("BETA.TXT"), "alphabetical");
        ok(DownloadsPaths.list(root, "Nested").size() == 1, "nested listing");
        ok(DownloadsPaths.parent("Nested/child.markdown").equals("Nested"), "parent");
        ok(DownloadsPaths.parent("Nested").equals(""), "root parent");
        ok(DownloadsPaths.document(root, "Nested/child.markdown").getName().equals("child.markdown"), "nested file");
        ok(DownloadsPaths.relative(root, base.resolve("alpha.md").toFile()).equals("alpha.md"), "stable relative location");
        ok(DownloadsPaths.supported("HELLO.MD") && !DownloadsPaths.supported("private.apk"), "extension policy");
        refuses(() -> DownloadsPaths.document(root, "notes.jpg"), "unsupported extension refused");
        refuses(() -> DownloadsPaths.document(root, "missing.md"), "missing file refused");
        refuses(() -> DownloadsPaths.document(root, "../outside.md"), "traversal refused");
        refuses(() -> DownloadsPaths.document(root, "/etc/passwd"), "absolute path refused");
        refuses(() -> DownloadsPaths.document(root, "Nested"), "directory is not a file");
        refuses(() -> DownloadsPaths.directory(root, "alpha.md"), "file is not a folder");
        refuses(() -> DownloadsPaths.relative(root, root), "root is not document");
        Path escape = base.resolve("escape.md");
        try {
            Files.createSymbolicLink(escape, base.getParent().resolve("outside.md"));
            ok(DownloadsPaths.list(root, "").size() == 3, "symlink absent from listing");
            refuses(() -> DownloadsPaths.document(root, "escape.md"), "symlink escape refused");
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
            // Filesystems without symlinks cannot prove this subset; a supported CI host does.
        }
        Path flood = Files.createDirectory(base.resolve("Lots"));
        for (int i=0; i<2001; i++) Files.write(flood.resolve("f"+i+".md"), new byte[0]);
        refuses(() -> DownloadsPaths.list(root, "Lots"), "bounded listing");
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("md-downloads-test-");
        try {
            test(dir.toFile());
            System.out.println("PASS DownloadsPaths " + checks + " checked conditions");
        } finally {
            try (java.util.stream.Stream<Path> walk=Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch(IOException ignored) {} });
            }
        }
    }
}
