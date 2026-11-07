package com.example.markdowndesk.archive;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Multiple Activity owners must not interleave journal read/append records. */
public final class ArchiveJournalConcurrencyCheck {
    private static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
    private static ArchiveJournal.Annotation note(String id) {
        ArchiveJournal.Annotation n = new ArchiveJournal.Annotation();
        n.id = id; n.archiveId = "a"; n.chatId = "chat"; n.messageId = "m";
        n.type = "note"; n.note = "private test note " + id;
        return n;
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("md-archive-journal-concurrent-");
        File path = dir.resolve("annotations.journal").toFile();
        try {
            int workers = 4, perWorker = 25;
            Thread[] threads = new Thread[workers];
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> failed = new AtomicReference<>();
            for (int i = 0; i < workers; i++) {
                final int worker = i;
                threads[i] = new Thread(() -> {
                    ArchiveJournal ownInstance = new ArchiveJournal(path);
                    try {
                        start.await();
                        for (int j = 0; j < perWorker; j++) {
                            ownInstance.save(note(worker + "/" + j));
                            // Interleave an independent reader after the saved record.
                            if (j % 7 == 0)
                                check(!new ArchiveJournal(path).load().truncatedTail,
                                        "complete writes must remain readable");
                        }
                    } catch (Throwable error) {
                        failed.compareAndSet(null, error);
                    }
                }, "archive-journal-writer-" + i);
                threads[i].start();
            }
            start.countDown();
            for (Thread worker : threads) worker.join();
            if (failed.get() != null) throw new AssertionError("Journal writer failed", failed.get());
            ArchiveJournal.ReadResult read = new ArchiveJournal(path).load();
            check(!read.truncatedTail, "no interrupted record");
            check(read.annotations.size() == workers * perWorker, "lost/interleaved writes");
            Set<String> ids = new HashSet<>();
            for (ArchiveJournal.Annotation note : read.annotations) ids.add(note.id);
            check(ids.size() == workers * perWorker, "duplicate or lost annotation identity");
            new ArchiveJournal(path).delete("0/0");
            check(new ArchiveJournal(path).load().annotations.size() == workers * perWorker - 1,
                    "independent delete still serializes");
            System.out.println("PASS: cross-instance journal concurrency, readers, unique writes, delete");
        } finally {
            Files.deleteIfExists(path.toPath());
            Files.deleteIfExists(dir);
        }
    }
}
