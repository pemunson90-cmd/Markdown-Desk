package com.example.markdowndesk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** App-private files only, on one filesystem. Never delete a previous checkpoint first. */
final class AtomicCommit {
    private AtomicCommit() {}
    static void replace(File staged, File destination) throws IOException {
        if (!staged.getCanonicalFile().getParentFile().equals(destination.getCanonicalFile().getParentFile()))
            throw new IOException("Checkpoint replacement must stay in the same directory");
        // Do not fall back to delete/rename if the filesystem cannot guarantee atomic replacement.
        Files.move(staged.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
