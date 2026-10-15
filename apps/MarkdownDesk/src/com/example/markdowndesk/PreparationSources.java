package com.example.markdowndesk;

import java.io.*;
import java.util.*;

/** Process-owned source lifetime before durable BEGIN. No capture/publication authority. */
final class PreparationSources {
    interface Factory { InputStream open() throws IOException; }
    static final class Capability { private Capability() {} }
    static Capability newProducer() { return new Capability(); }
    private static final ThreadLocal<Integer> PARTICIPANT = new ThreadLocal<>();
    private final File root;
    private final Object history;
    private final UUID preparation;
    private final Capability producer;
    private final HistoryStorage.Budget budget;
    private boolean frozen, uncertain;
    private int resources, running, admissions;
    private Proof claim;

    private PreparationSources(File root, UUID preparation, Capability producer,
                               HistoryStorage.Budget budget) throws IOException {
        this.root = HistoryStorage.canonicalRoot(root);
        this.history = HistoryStorage.monitorFor(root);
        this.preparation = preparation;
        this.producer = producer;
        this.budget = budget;
    }
    static PreparationSources forPreparation(File root, UUID preparation,
            HistoryStorage.Budget budget, Capability producer) throws IOException {
        HistoryOperations.rejectParticipant();
        if (preparation == null || preparation.version() != 4 || budget == null || producer == null)
            throw new IOException("Missing preparation ownership binding");
        File canonical = HistoryStorage.canonicalRoot(root);
        Object history = HistoryStorage.monitorFor(canonical);
        synchronized (history) {
            PreparationSources current = HistoryStorage.preparationSources(canonical);
            if (current != null) {
                if (!current.root.equals(canonical) || !current.preparation.equals(preparation)
                        || current.budget != budget || current.producer != producer)
                    throw new IOException("Unfinished preparation source owner already registered");
                return current;
            }
            // Retained until an actual durable handoff API is reviewed. No generic reset/finally-clear.
            budget.charge(2048L + canonical.getPath().length() * 2L);
            PreparationSources result = new PreparationSources(canonical, preparation, producer, budget);
            HistoryStorage.preparationSources(canonical, result);
            return result;
        }
    }
    static void rejectParticipant() throws IOException {
        if (PARTICIPANT.get() != null) throw new IOException("Preparation participant cannot enter terminal work");
    }
    private static void enter() {
        Integer depth = PARTICIPANT.get(); PARTICIPANT.set(depth == null ? 1 : depth + 1);
    }
    private static void leave() {
        int depth = PARTICIPANT.get();
        if (depth == 1) PARTICIPANT.remove(); else PARTICIPANT.set(depth - 1);
    }
    private void outsideHistory() throws IOException {
        if (Thread.holdsLock(history)) throw new IOException("Source callback work under history monitor");
    }
    /** Metadata consumers cannot create a second independent budget for this owner. */
    void reserveMetadata(HistoryStorage.Budget supplied, long bytes) throws IOException {
        outsideHistory(); HistoryOperations.rejectParticipant();
        synchronized (this) {
            if (supplied != budget || frozen || uncertain || claim != null)
                throw new IOException("Foreign budget or frozen preparation owner");
            budget.charge(bytes);
        }
    }
    Reader open(Factory factory) throws IOException {
        outsideHistory(); HistoryOperations.rejectParticipant();
        if (factory == null) throw new IOException("Missing owned source factory");
        synchronized (this) {
            if (frozen || uncertain || claim != null) throw new IOException("Preparation source admission frozen");
            // Bound retained uncertain registrations and wrapper allocation in the same caller budget.
            if (admissions == 64) throw new IOException("Preparation source admission limit");
            budget.charge(512); admissions++; resources++; running++;
        }
        enter();
        try {
            InputStream stream = factory.open();
            if (stream == null) throw new IOException("Source factory returned no owned stream");
            return new Reader(stream);
        } catch (IOException | RuntimeException | Error failure) {
            synchronized (this) { frozen = true; uncertain = true; }
            throw failure;
        } finally {
            try { synchronized (this) { running--; } } finally { leave(); }
        }
    }
    void freeze() throws IOException {
        outsideHistory(); HistoryOperations.rejectParticipant();
        synchronized (this) { frozen = true; }
    }
    Proof claimLocked() throws IOException {
        HistoryStorage.requireMutation(root); HistoryOperations.rejectParticipant();
        synchronized (this) {
            if (!frozen || uncertain || resources != 0 || running != 0 || claim != null)
                throw new IOException("Preparation sources are not unclaimed and quiescent");
            if (HistoryStorage.preparationSources(root) != this)
                throw new IOException("Preparation source registration changed");
            claim = new Proof(this); return claim;
        }
    }
    static final class Proof {
        private final PreparationSources owner;
        private Proof(PreparationSources owner) { this.owner = owner; }
        void validateLocked() throws IOException {
            HistoryStorage.requireMutation(owner.root); HistoryOperations.rejectParticipant();
            synchronized (owner) {
                if (owner.claim != this || !owner.frozen || owner.uncertain
                        || owner.resources != 0 || owner.running != 0
                        || HistoryStorage.preparationSources(owner.root) != owner)
                    throw new IOException("Preparation source proof changed");
            }
        }
    }
    /** Deliberately not InputStream: no payload-allocating convenience API or delegate escape. */
    final class Reader implements AutoCloseable {
        private final InputStream delegate;
        private final Thread thread = Thread.currentThread();
        private boolean closed, busy;
        private Reader(InputStream delegate) { this.delegate = delegate; }
        private boolean start(boolean closing) throws IOException {
            outsideHistory();
            if (Thread.currentThread() != thread) throw new IOException("Source reader thread mismatch");
            synchronized (PreparationSources.this) {
                if (busy) throw new IOException("Reentrant source I/O");
                if (closed) { if (closing) return false; throw new IOException("Closed source reader"); }
                if (!closing && frozen) throw new IOException("Preparation source reads frozen");
                busy = true; running++;
            }
            enter(); return true;
        }
        private void finish(boolean closedSuccessfully) {
            try {
                synchronized (PreparationSources.this) {
                    if (closedSuccessfully) { closed = true; resources--; }
                    busy = false; running--;
                }
            } finally { leave(); }
        }
        int read(byte[] bytes, int offset, int length) throws IOException {
            if (offset < 0 || length < 0 || offset > bytes.length - length) throw new IndexOutOfBoundsException();
            start(false);
            try { return delegate.read(bytes, offset, length); }
            finally { finish(false); }
        }
        public void close() throws IOException {
            if (!start(true)) return;
            boolean success = false;
            try { delegate.close(); success = true; }
            finally { finish(success); }
        }
    }
}
