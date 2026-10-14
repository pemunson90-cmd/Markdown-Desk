package com.example.markdowndesk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.security.DigestInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable private protection envelopes. No retention, provider writes or mutable index. */
public final class ProtectionStore {
    public static final int MAX_DOCUMENT_BYTES = 8388608;
    public static final long DEFAULT_QUOTA_BYTES = 64L * 1024 * 1024;
    private static final int MAGIC = 0x4d445053, VERSION = 1, STRING_LIMIT = 4096;
    private static final int MAX_ENVELOPE_BYTES = MAX_DOCUMENT_BYTES + 32768;
    private static final int MAX_ENTRIES = 65536;
    private static final byte SNAPSHOT = 1, PREPARED = 2, COMPLETE = 3;

    public interface Durability {
        void syncFile(FileDescriptor fd) throws IOException;
        /** Must atomically move within one directory; must fail if unsupported. */
        void move(File stage, File destination) throws IOException;
        void syncDirectory(File directory) throws IOException;
    }

    public static final class Snapshot {
        public final String id, documentId, uri, title, role, hash;
        public final long revision, timestamp;
        public final int byteCount;
        private final ProtectionStore store;
        private Snapshot(ProtectionStore store,String id,String documentId,String uri,String title,long revision,
                         String role,long timestamp,int byteCount,String hash) {
            this.store=store;this.id=id;this.documentId=documentId;this.uri=uri;this.title=title;
            this.revision=revision;this.role=role;this.timestamp=timestamp;this.byteCount=byteCount;this.hash=hash;
        }
        /** Fresh validated disk bytes; no payload is retained by the handle. */
        public byte[] bytes() throws IOException {return store.snapshotBytes(this);}
    }

    public static final class Transaction {
        public final String id, expectedHash;
        public final Snapshot original, attempted;
        public final boolean verified;
        private Transaction(String id, Snapshot original, Snapshot attempted, String expectedHash, boolean verified) {
            this.id=id; this.original=original; this.attempted=attempted;
            this.expectedHash=expectedHash; this.verified=verified;
        }
    }

    /** Exact on-disk descriptor; hashes cover the whole serialized envelope, never just payload. */
    public static final class StoredRecord {
        public final String rootRole, filename, id, kind, envelopeHash;
        public final long serializedBytes;
        StoredRecord(String role,String filename,String id,String kind,long size,String hash){
            rootRole=role;this.filename=filename;this.id=id;this.kind=kind;serializedBytes=size;envelopeHash=hash;
        }
    }
    public static final class Inventory {
        public final List<StoredRecord> records;
        public final List<Snapshot> snapshots;
        public final List<Transaction> transactions;
        public final List<String> diagnostics;
        public final long actualBytes, reservedCompletionBytes, reservedCompletionSlots;
        public final boolean complete, pruneBlocked, usageComplete;
        Inventory(HistoryStorage.FilesView files,Map<String,Snapshot> snapshots,Map<String,Transaction> transactions,
                  List<String> diagnostics,long bytes,long slots){
            records=java.util.Collections.unmodifiableList(new ArrayList<>(files.records));
            this.snapshots=java.util.Collections.unmodifiableList(new ArrayList<>(snapshots.values()));
            this.transactions=java.util.Collections.unmodifiableList(new ArrayList<>(transactions.values()));
            this.diagnostics=java.util.Collections.unmodifiableList(new ArrayList<>(diagnostics));
            actualBytes=files.bytes;reservedCompletionBytes=bytes;reservedCompletionSlots=slots;
            usageComplete=files.usageComplete;complete=diagnostics.isEmpty()&&usageComplete;pruneBlocked=!complete;
        }
    }
    interface SourceOperation<T> {T run(ValidatedSources sources)throws IOException;}
    static final class ValidatedSources implements AutoCloseable {
        private final ProtectionStore owner;
        private boolean active=true;
        private final long generation;
        private final Map<String,Snapshot> snapshots=new java.util.LinkedHashMap<>();
        private final Map<String,Transaction> transactions=new java.util.LinkedHashMap<>();
        final HistoryStorage.Budget budget;
        Inventory inventory;
        ValidatedSources(ProtectionStore owner,HistoryStorage.Budget budget)throws IOException {this.owner=owner;this.budget=budget;generation=HistoryStorage.generation(owner.root);}
        void require(ProtectionStore actual)throws IOException {if(!active||owner!=actual||!Thread.holdsLock(owner.lock)||generation!=HistoryStorage.generation(owner.root))throw new IOException("Foreign or expired validated source operation");}
        Snapshot snapshot(ProtectionStore actual,String id)throws IOException {require(actual);Snapshot snapshot=snapshots.get(id);if(snapshot==null)throw new IOException("Missing validated source");return snapshot;}
        int validatedSnapshotCount()throws IOException {require(owner);return snapshots.size();}
        public void close(){active=false;snapshots.clear();transactions.clear();inventory=null;}
    }
    File storageRoot(){return root;}
    long constructorQuotaBytes(){return quotaBytes;}
    public Inventory inspect()throws IOException {return withValidatedSources(sources->sources.inventory);}
    <T> T withValidatedSources(SourceOperation<T> operation)throws IOException {
        return withValidatedSources(new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET),operation);
    }
    <T> T withValidatedSources(HistoryStorage.Budget budget,SourceOperation<T> operation)throws IOException {
        synchronized(lock){try(ValidatedSources sources=new ValidatedSources(this,budget)){inspectSources(sources);sources.require(this);T result=operation.run(sources);sources.require(this);return result;}}
    }
    private void inspectSources(ValidatedSources sources)throws IOException {
        HistoryStorage.FilesView files=HistoryStorage.inspect(root,"store",MAX_ENVELOPE_BYTES,MAX_ENTRIES,sources.budget);
        List<String> diagnostics=new ArrayList<>(files.diagnostics);
        try{durability.syncDirectory(root);}catch(IOException failure){diagnostics.add("store: directory validation unavailable");}
        for(StoredRecord record:files.records)if("snapshot".equals(record.kind)&&record.id!=null&&record.envelopeHash!=null){
            try{Snapshot snapshot=readSnapshot(record.id);sources.budget.charge(1024L+2L*(snapshot.id.length()+snapshot.documentId.length()+
                (snapshot.uri==null?0:snapshot.uri.length())+(snapshot.title==null?0:snapshot.title.length())+snapshot.role.length()+snapshot.hash.length()));
                sources.snapshots.put(snapshot.id,snapshot);
            }catch(HistoryStorage.BudgetExceeded exceeded){diagnostics.add("store: metadata budget");break;}catch(IOException failure){diagnostics.add("store: invalid snapshot");}
        }
        long reserved=0,slots=0;
        for(StoredRecord record:files.records)if("transaction".equals(record.kind)&&record.id!=null&&record.envelopeHash!=null){
            try{Transaction prepared=readTransaction(record.id,false,sources.snapshots);
                boolean completed=java.nio.file.Files.exists(file(record.id,".complete").toPath(),LinkOption.NOFOLLOW_LINKS);
                if(completed&&!sameTransaction(prepared,readTransaction(record.id,true,sources.snapshots)))throw new IOException("Completion mismatch");
                sources.budget.charge(1024);Transaction transaction=new Transaction(prepared.id,prepared.original,prepared.attempted,prepared.expectedHash,completed);
                sources.transactions.put(transaction.id,transaction);
                long staged=completionStageBytes(transaction,completed);
                if(!completed){reserved=Math.addExact(reserved,transactionEnvelope(transaction,COMPLETE).length-Math.max(0,staged));slots=Math.addExact(slots,staged<0?1:0);}
            }catch(HistoryStorage.BudgetExceeded exceeded){diagnostics.add("store: metadata budget");break;}catch(IOException|ArithmeticException failure){diagnostics.add("store: invalid transaction or completion");}
        }
        for(StoredRecord record:files.records)if(("complete".equals(record.kind)||"completion-stage".equals(record.kind))&&!sources.transactions.containsKey(record.id))diagnostics.add("store: dangling completion or stage");
        sources.inventory=new Inventory(files,sources.snapshots,sources.transactions,diagnostics,reserved,slots);
    }

    private final File root;
    private final Durability durability;
    private final long quotaBytes;
    private final Object lock;

    public ProtectionStore(File root, Durability adapter, long quotaBytes) throws IOException {
        if (root==null || adapter==null || quotaBytes<0) throw new IOException("Invalid store configuration");
        this.root=HistoryStorage.canonicalRoot(root);
        this.durability=adapter; this.quotaBytes=quotaBytes;
        lock=HistoryStorage.monitorFor(this.root);
        synchronized (lock) {
            checkAncestors(this.root.toPath());
            File parent=this.root.getParentFile();
            if (parent==null || !Files.isDirectory(parent.toPath(), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Protection parent does not exist");
            if (!Files.exists(this.root.toPath(), LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(this.root.toPath());
            if (!Files.isDirectory(this.root.toPath(), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid protection directory");
            // Re-sync even an existing root: a previous failed creation may have left it behind.
            durability.syncDirectory(parent);
            durability.syncDirectory(this.root);
            entries();
        }
    }

    public Snapshot putSnapshot(String documentId, String uri, String title, long revision, byte[] bytes, String role) throws IOException {
        synchronized (lock) {
            validateMetadata(documentId, uri, title, revision, role);
            if (bytes==null || bytes.length>MAX_DOCUMENT_BYTES) throw new IOException("Snapshot byte limit exceeded");
            byte[] captured=bytes.clone();
            Snapshot snapshot=new Snapshot(this,UUID.randomUUID().toString(),documentId,uri,title,revision,role,
                    System.currentTimeMillis(),captured.length,digest(captured));
            byte[] prefix=snapshotPrefix(snapshot);
            checkQuota(prefix.length+captured.length+32L,0,0);
            commitSnapshot(snapshot,prefix,captured);
            return readSnapshot(snapshot.id);
        }
    }

    public Transaction prepare(Snapshot original, Snapshot attempted, String expectedHash) throws IOException {
        synchronized (lock) {
            if (original==null || attempted==null) throw new IOException("Missing protection snapshots");
            Snapshot source=readSnapshot(original.id), edit=readSnapshot(attempted.id);
            validatePair(source,edit,expectedHash);
            if (!sameSnapshot(source,original) || !sameSnapshot(edit,attempted)) throw new IOException("Snapshot provenance mismatch");
            Transaction transaction=new Transaction(UUID.randomUUID().toString(),source,edit,expectedHash,false);
            byte[] envelope=transactionEnvelope(transaction,PREPARED);
            // Keep enough quota for a separate completion even after subsequent failed attempts.
            checkQuota(envelope.length,transactionEnvelope(transaction,COMPLETE).length,0);
            commit(transaction.id+".transaction",envelope);
            return readTransaction(transaction.id,false);
        }
    }

    public void complete(Transaction transaction) throws IOException {
        synchronized (lock) {
            if (transaction==null) throw new IOException("Missing transaction");
            Transaction stored=readTransaction(transaction.id,false);
            if (!sameTransaction(stored,transaction)) throw new IOException("Transaction provenance mismatch");
            // Validate every reservation/stage before any completion mutation, including retries.
            unresolvedReservation();
            File destination=file(transaction.id,".complete");
            if (Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS)) {
                readCompletion(stored);
                durability.syncDirectory(root);
                return;
            }
            byte[] envelope=transactionEnvelope(stored,COMPLETE);
            long staged=completionStageBytes(stored,false);
            if(staged>=0&&staged!=envelope.length)throw new IOException("Partial completion stage retained; retry blocked");
            if(staged<0)checkQuota(envelope.length,0,envelope.length);
            else checkQuota(0,0,0,0,0);
            commitCompletion(stored,envelope,staged>=0);
            readCompletion(stored);
        }
    }

    public Transaction validate(Transaction transaction) throws IOException {
        synchronized (lock) {
            if(transaction==null) throw new IOException("Missing transaction");
            entries();
            Transaction stored=readTransaction(transaction.id,false);
            if(!sameTransaction(stored,transaction)) throw new IOException("Transaction provenance mismatch");
            boolean verified=Files.exists(file(stored.id,".complete").toPath(),LinkOption.NOFOLLOW_LINKS);
            if(verified) readCompletion(stored);
            return new Transaction(stored.id,stored.original,stored.attempted,stored.expectedHash,verified);
        }
    }

    public Snapshot snapshot(String id) throws IOException {
        synchronized(lock) {entries();durability.syncDirectory(root);return readSnapshot(id);}
    }

    private byte[] snapshotBytes(Snapshot snapshot) throws IOException {
        synchronized(lock) {
            entries();
            SnapshotData data=readSnapshotData(snapshot.id,true);
            if(!sameSnapshot(snapshot,data.snapshot)) throw new IOException("Snapshot provenance mismatch");
            return data.bytes;
        }
    }

    /** Enumerates snapshots independently of transaction records, including orphan attempts. */
    public List<Snapshot> snapshots() throws IOException {
        synchronized (lock) {
            File[] files=entries();
            durability.syncDirectory(root);
            List<Snapshot> result=new ArrayList<>();
            for(File f:files) if(f.getName().endsWith(".snapshot")) result.add(readSnapshot(idFrom(f,".snapshot")));
            return result;
        }
    }

    /** Invalid committed records or referenced snapshots fail explicitly. Staging is never deleted. */
    public List<Transaction> transactions() throws IOException {
        synchronized (lock) {
            File[] files=entries();
            durability.syncDirectory(root);
            Map<String,Transaction> result=new HashMap<>();
            for(File f:files) if(f.getName().endsWith(".transaction")) {
                String id=idFrom(f,".transaction");
                Transaction t=readTransaction(id,false);
                boolean verified=Files.exists(file(id,".complete").toPath(),LinkOption.NOFOLLOW_LINKS);
                if(verified) readCompletion(t);
                result.put(id,new Transaction(id,t.original,t.attempted,t.expectedHash,verified));
            }
            for(File f:files) if(f.getName().endsWith(".complete")) {
                String id=idFrom(f,".complete");
                if(!result.containsKey(id)) throw new IOException("Completion without preparation");
            }
            List<Transaction> sorted=new ArrayList<>(result.values());
            sorted.sort((a,b)->a.id.compareTo(b.id));
            return sorted;
        }
    }

    private void commit(String name, byte[] envelope) throws IOException {
        entries();
        File stage=file(UUID.randomUUID().toString(),".stage");
        File destination=new File(root,name);
        if(Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("Immutable destination exists");
        // CREATE_NEW prevents accidental reuse; failed staging and committed files are retained.
        HistoryStorage.changed(root);
        Files.createFile(stage.toPath());
        try(FileOutputStream stream=new FileOutputStream(stage)) {
            stream.write(envelope);
            stream.flush();
            durability.syncFile(stream.getFD());
        }
        durability.move(stage,destination);
        if(Files.exists(stage.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("Atomic move did not consume stage");
        durability.syncDirectory(root);
        if(!Arrays.equals(envelope,readEnvelopeBytes(destination))) throw new IOException("Committed envelope validation failed");
    }

    private void commitCompletion(Transaction transaction,byte[] envelope,boolean staged)throws IOException {
        entries();
        File stage=file(transaction.id,".completion-stage"),destination=file(transaction.id,".complete");
        if(Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Immutable destination exists");
        HistoryStorage.changed(root);
        if(!staged)Files.createFile(stage.toPath());
        // Append mode opens an exact retained stage without truncating or writing it on retry.
        try(FileOutputStream stream=new FileOutputStream(stage,staged)) {
            if(!staged)stream.write(envelope);
            stream.flush();durability.syncFile(stream.getFD());
        }
        if(completionStageBytes(transaction,false)!=envelope.length)throw new IOException("Completion stage changed");
        durability.move(stage,destination);
        if(Files.exists(stage.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Atomic move did not consume completion stage");
        durability.syncDirectory(root);
        if(!Arrays.equals(envelope,readEnvelopeBytes(destination)))throw new IOException("Committed completion validation failed");
    }

    private void checkQuota(long newBytes,long newReservation,long releasedReservation)throws IOException {
        checkQuota(newBytes,newReservation,releasedReservation,1+(newReservation>0?1:0),releasedReservation>0?1:0);
    }
    private void checkQuota(long newBytes,long newReservation,long releasedReservation,long newSlots,long releasedSlots)throws IOException {
        File[] files=entries();Reservation reserved=unresolvedReservation();
        try {
            long total=0;
            for(File f:files){long size=attributes(f).size();if(size<0)throw new IOException("Invalid protection size");total=Math.addExact(total,size);}
            if(releasedReservation>reserved.bytes||releasedSlots>reserved.entries)throw new IOException("Unowned completion reservation");
            long needed=Math.addExact(Math.subtractExact(reserved.bytes,releasedReservation),Math.addExact(newBytes,newReservation));
            if(Math.addExact(total,needed)>quotaBytes)throw new IOException("Protection quota exhausted");
            long slots=Math.addExact(Math.subtractExact(reserved.entries,releasedSlots),newSlots);
            if(Math.addExact(files.length,slots)>MAX_ENTRIES)throw new IOException("Protection entry limit exhausted");
        }catch(ArithmeticException failure){throw new IOException("Protection accounting overflow",failure);}
    }

    private static final class Reservation {long bytes,entries;}
    private Reservation unresolvedReservation()throws IOException {
        Reservation reservation=new Reservation();File[] files=entries();
        try {
            for(File f:files)if(f.getName().endsWith(".transaction")) {
                Transaction transaction=readTransaction(idFrom(f,".transaction"),false);
                boolean done=Files.exists(file(transaction.id,".complete").toPath(),LinkOption.NOFOLLOW_LINKS);
                if(done)readCompletion(transaction);
                long staged=completionStageBytes(transaction,done);
                if(!done){
                    reservation.bytes=Math.addExact(reservation.bytes,transactionEnvelope(transaction,COMPLETE).length-Math.max(0,staged));
                    if(staged<0)reservation.entries=Math.addExact(reservation.entries,1);
                }
            }
            for(File f:files) {
                String extension=f.getName().endsWith(".complete")?".complete":f.getName().endsWith(".completion-stage")?".completion-stage":null;
                if(extension!=null&&!Files.exists(file(idFrom(f,extension),".transaction").toPath(),LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Completion or stage without preparation");
            }
        }catch(ArithmeticException failure){throw new IOException("Protection reservation overflow",failure);}
        return reservation;
    }

    /** -1 means absent. Only an exact prefix can occupy this preparation's reservation. */
    private long completionStageBytes(Transaction transaction,boolean completed)throws IOException {
        File stage=file(transaction.id,".completion-stage");
        if(!Files.exists(stage.toPath(),LinkOption.NOFOLLOW_LINKS))return -1;
        if(completed)throw new IOException("Completion and retained stage both exist");
        byte[] expected=transactionEnvelope(transaction,COMPLETE);
        BasicFileAttributes before=attributes(stage);long size=before.size();
        if(!before.isRegularFile()||size<0||size>expected.length)throw new IOException("Invalid completion stage size");
        ByteBuffer bytes=ByteBuffer.allocate((int)size);
        try(SeekableByteChannel channel=Files.newByteChannel(stage.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            while(bytes.hasRemaining())if(channel.read(bytes)<0)throw new IOException("Truncated completion stage");
            if(channel.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Completion stage grew");
        }
        BasicFileAttributes after=attributes(stage);
        if(!after.isRegularFile()||after.size()!=size||!Objects.equals(before.fileKey(),after.fileKey())
                ||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("Completion stage changed");
        if(!Arrays.equals(bytes.array(),Arrays.copyOf(expected,(int)size)))throw new IOException("Completion stage differs from reservation");
        return size;
    }

    private File[] entries() throws IOException {
        checkAncestors(root.toPath());
        if(!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("Protection directory missing");
        File[] files=root.listFiles();
        if(files==null || files.length>MAX_ENTRIES) throw new IOException("Cannot enumerate protection directory");
        Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));
        for(File f:files) if(!attributes(f).isRegularFile()) throw new IOException("Non-regular protection entry");
        return files;
    }

    private static BasicFileAttributes attributes(File file) throws IOException {
        return Files.readAttributes(file.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
    }

    private static void checkAncestors(Path path) throws IOException {
        for(Path p=path;p!=null;p=p.getParent()) if(Files.isSymbolicLink(p)) throw new IOException("Symlink in protection path");
    }

    private File file(String id,String extension) throws IOException {
        validateId(id);
        return new File(root,id+extension);
    }
    private static String idFrom(File f,String extension) throws IOException {
        String id=f.getName().substring(0,f.getName().length()-extension.length());
        validateId(id); return id;
    }
    private static void validateId(String id) throws IOException {
        try {if(id==null || !UUID.fromString(id).toString().equals(id)) throw new IOException("Invalid protection ID");}
        catch(IllegalArgumentException e){throw new IOException("Invalid protection ID",e);}
    }
    private static void validateHash(String hash) throws IOException {
        if(hash==null || hash.length()!=64 || !hash.matches("[0-9a-f]{64}")) throw new IOException("Invalid protection hash");
    }
    private static void validateMetadata(String doc,String uri,String title,long revision,String role) throws IOException {
        if(doc==null || doc.isEmpty() || revision<0 || !("original".equals(role)||"attempted".equals(role))) throw new IOException("Invalid snapshot metadata");
        for(String value:new String[]{doc,uri,title,role}) if(value!=null) metadataBytes(value);
    }
    private static void validatePair(Snapshot source,Snapshot edit,String expectedHash) throws IOException {
        validateHash(expectedHash);
        if(!source.role.equals("original") || !edit.role.equals("attempted") || source.id.equals(edit.id)
                || !source.documentId.equals(edit.documentId) || !Objects.equals(source.uri,edit.uri)
                || source.revision!=edit.revision || !source.hash.equals(expectedHash)) throw new IOException("Invalid transaction snapshots");
    }
    private static boolean sameSnapshot(Snapshot a,Snapshot b) {
        return a.id.equals(b.id)&&a.documentId.equals(b.documentId)&&Objects.equals(a.uri,b.uri)
                &&Objects.equals(a.title,b.title)&&a.revision==b.revision&&a.timestamp==b.timestamp
                &&a.role.equals(b.role)&&a.hash.equals(b.hash)&&a.byteCount==b.byteCount
                &&a.store.root.equals(b.store.root);
    }
    private static boolean sameTransaction(Transaction a,Transaction b) {
        return a.id.equals(b.id)&&a.expectedHash.equals(b.expectedHash)&&sameSnapshot(a.original,b.original)&&sameSnapshot(a.attempted,b.attempted);
    }

    private byte[] snapshotPrefix(Snapshot s) throws IOException {
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(body)) {
            writeString(out,s.id);writeString(out,s.documentId);writeString(out,s.uri);writeString(out,s.title);
            out.writeLong(s.revision);writeString(out,s.role);out.writeLong(s.timestamp);
            writeString(out,s.hash);out.writeInt(s.byteCount);
        }
        ByteArrayOutputStream prefix=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(prefix)) {
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeByte(SNAPSHOT);
            out.writeInt(body.size()+s.byteCount);body.writeTo(out);
        }
        return prefix.toByteArray();
    }

    private void commitSnapshot(Snapshot snapshot,byte[] prefix,byte[] payload) throws IOException {
        entries();
        MessageDigest checksum=shaDigest();checksum.update(prefix);checksum.update(payload);
        byte[] expectedChecksum=checksum.digest();
        File stage=file(UUID.randomUUID().toString(),".stage"),destination=file(snapshot.id,".snapshot");
        if(Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("Immutable destination exists");
        HistoryStorage.changed(root);
        Files.createFile(stage.toPath());
        try(FileOutputStream out=new FileOutputStream(stage)) {
            out.write(prefix);out.write(payload);out.write(expectedChecksum);out.flush();durability.syncFile(out.getFD());
        }
        durability.move(stage,destination);
        if(Files.exists(stage.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("Atomic move did not consume stage");
        durability.syncDirectory(root);
        SnapshotData committed=readSnapshotData(snapshot.id,false);
        if(!sameSnapshot(snapshot,committed.snapshot) || !MessageDigest.isEqual(expectedChecksum,committed.checksum))
            throw new IOException("Committed envelope validation failed");
    }
    private byte[] transactionEnvelope(Transaction t,byte type) throws IOException {
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(body)) {
            writeString(out,t.id);writeString(out,t.original.id);writeString(out,t.attempted.id);writeString(out,t.expectedHash);
            out.writeInt(t.original.byteCount);writeString(out,t.original.hash);
            out.writeInt(t.attempted.byteCount);writeString(out,t.attempted.hash);
            writeString(out,t.original.documentId);out.writeLong(t.original.revision);
        }
        return envelope(type,body.toByteArray());
    }
    private static byte[] envelope(byte type,byte[] body) throws IOException {
        ByteArrayOutputStream serialized=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(serialized)) {
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeByte(type);out.writeInt(body.length);out.write(body);
            out.write(sha(serialized.toByteArray()));
        }
        if(serialized.size()>MAX_ENVELOPE_BYTES) throw new IOException("Envelope byte limit exceeded");
        return serialized.toByteArray();
    }
    private byte[] readEnvelopeBytes(File file) throws IOException {
        checkAncestors(root.toPath());
        BasicFileAttributes before=attributes(file);
        long size=before.size();
        if(!before.isRegularFile() || size<45 || size>MAX_ENVELOPE_BYTES) throw new IOException("Invalid envelope size");
        ByteBuffer buffer=ByteBuffer.allocate((int)size);
        try(SeekableByteChannel channel=Files.newByteChannel(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            while(buffer.hasRemaining()) if(channel.read(buffer)<0) throw new IOException("Truncated envelope");
            if(channel.read(ByteBuffer.allocate(1))!=-1) throw new IOException("Envelope grew while reading");
        }
        if(attributes(file).size()!=size) throw new IOException("Envelope changed while reading");
        return buffer.array();
    }
    private DataInputStream readBody(File file,byte type) throws IOException {
        if(attributes(file).size()>32768) throw new IOException("Transaction envelope byte limit exceeded");
        byte[] raw=readEnvelopeBytes(file);
        byte[] expected=Arrays.copyOfRange(raw,raw.length-32,raw.length);
        if(!MessageDigest.isEqual(expected,sha(Arrays.copyOf(raw,raw.length-32)))) throw new IOException("Envelope integrity mismatch");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(raw));
        if(in.readInt()!=MAGIC || in.readInt()!=VERSION || in.readByte()!=type) throw new IOException("Unsupported protection envelope");
        int length=in.readInt();
        if(length<0 || length!=raw.length-45) throw new IOException("Invalid envelope length");
        return new DataInputStream(new ByteArrayInputStream(raw,13,length));
    }
    /** Tracks exact remaining bytes without trusting InputStream.available() from a file channel. */
    private static final class LimitedInput extends InputStream {
        final InputStream input;long remaining;
        LimitedInput(InputStream input,long size){this.input=input;remaining=size;}
        @Override public int read() throws IOException {
            if(remaining==0)return -1;int result=input.read();if(result<0)throw new IOException("Truncated envelope");remaining--;return result;
        }
        @Override public int read(byte[] bytes,int offset,int length) throws IOException {
            if(length==0)return 0;if(remaining==0)return -1;
            int result=input.read(bytes,offset,(int)Math.min(length,remaining));
            if(result<0)throw new IOException("Truncated envelope");remaining-=result;return result;
        }
        @Override public int available(){return (int)Math.min(remaining,Integer.MAX_VALUE);}
    }
    private static final class SnapshotData {
        final Snapshot snapshot;final byte[] bytes,checksum;
        SnapshotData(Snapshot snapshot,byte[] bytes,byte[] checksum){this.snapshot=snapshot;this.bytes=bytes;this.checksum=checksum;}
    }
    private Snapshot readSnapshot(String id) throws IOException {return readSnapshotData(id,false).snapshot;}
    private SnapshotData readSnapshotData(String id,boolean capture) throws IOException {
        File file=file(id,".snapshot");checkAncestors(root.toPath());
        BasicFileAttributes before=attributes(file);long size=before.size();
        if(!before.isRegularFile() || size<45 || size>MAX_ENVELOPE_BYTES) throw new IOException("Invalid envelope size");
        try(SeekableByteChannel channel=Files.newByteChannel(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            InputStream raw=Channels.newInputStream(channel);
            LimitedInput limited=new LimitedInput(raw,size-32);
            MessageDigest checksum=shaDigest();
            DataInputStream in=new DataInputStream(new DigestInputStream(limited,checksum));
            if(in.readInt()!=MAGIC || in.readInt()!=VERSION || in.readByte()!=SNAPSHOT || in.readInt()!=size-45)
                throw new IOException("Invalid snapshot envelope header");
            String storedId=readString(in),doc=readString(in),uri=readString(in),title=readString(in);
            long revision=in.readLong();String role=readString(in);long timestamp=in.readLong();String hash=readString(in);
            int length=in.readInt();
            if(!id.equals(storedId) || timestamp<0 || length<0 || length>MAX_DOCUMENT_BYTES || length!=limited.remaining)
                throw new IOException("Invalid snapshot envelope");
            validateMetadata(doc,uri,title,revision,role);validateHash(hash);
            MessageDigest payloadHash=shaDigest();byte[] bytes=capture?new byte[length]:null;
            if(capture) {in.readFully(bytes);payloadHash.update(bytes);}
            else {
                byte[] buffer=new byte[8192];
                while(limited.remaining>0){int count=in.read(buffer,0,(int)Math.min(buffer.length,limited.remaining));payloadHash.update(buffer,0,count);}
            }
            if(!hex(payloadHash.digest()).equals(hash)) throw new IOException("Snapshot hash mismatch");
            byte[] expectedChecksum=new byte[32];new DataInputStream(raw).readFully(expectedChecksum);
            if(!MessageDigest.isEqual(expectedChecksum,checksum.digest()) || raw.read()!=-1) throw new IOException("Envelope integrity mismatch");
            BasicFileAttributes after=attributes(file);
            if(!after.isRegularFile() || after.size()!=size || !Objects.equals(before.fileKey(),after.fileKey())) throw new IOException("Envelope changed while reading");
            return new SnapshotData(new Snapshot(this,id,doc,uri,title,revision,role,timestamp,length,hash),bytes,expectedChecksum);
        }
    }
    private Transaction readTransaction(String id,boolean completion) throws IOException {return readTransaction(id,completion,null);}
    private Transaction readTransaction(String id,boolean completion,Map<String,Snapshot> validated) throws IOException {
        try(DataInputStream in=readBody(file(id,completion?".complete":".transaction"),completion?COMPLETE:PREPARED)) {
            String storedId=readString(in),originalId=readString(in),attemptedId=readString(in),expectedHash=readString(in);
            int originalLength=in.readInt();String originalHash=readString(in);int attemptedLength=in.readInt();String attemptedHash=readString(in);
            String documentId=readString(in);long revision=in.readLong();
            if(!id.equals(storedId) || in.available()!=0) throw new IOException("Invalid transaction envelope");
            Snapshot source=validated==null?readSnapshot(originalId):validated.get(originalId),edit=validated==null?readSnapshot(attemptedId):validated.get(attemptedId);
            if(source==null||edit==null)throw new IOException("Missing validated transaction source");validatePair(source,edit,expectedHash);
            if(originalLength!=source.byteCount || attemptedLength!=edit.byteCount || !source.hash.equals(originalHash)
                    || !edit.hash.equals(attemptedHash) || !source.documentId.equals(documentId) || source.revision!=revision) throw new IOException("Transaction link mismatch");
            return new Transaction(id,source,edit,expectedHash,completion);
        }
    }
    private void readCompletion(Transaction transaction) throws IOException {
        if(!sameTransaction(transaction,readTransaction(transaction.id,true))) throw new IOException("Completion does not match preparation");
    }
    private static void writeString(DataOutputStream out,String value) throws IOException {
        if(value==null){out.writeInt(-1);return;}
        byte[] bytes=metadataBytes(value);
        out.writeInt(bytes.length);out.write(bytes);
    }
    private static byte[] metadataBytes(String value) throws IOException {
        if(value.length()>STRING_LIMIT) throw new IOException("Metadata byte limit exceeded");
        try {
            ByteBuffer buffer=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            if(buffer.remaining()>STRING_LIMIT) throw new IOException("Metadata byte limit exceeded");
            byte[] result=new byte[buffer.remaining()];buffer.get(result);return result;
        }catch(CharacterCodingException e){throw new IOException("Invalid metadata encoding",e);}
    }
    private static String readString(DataInputStream in) throws IOException {
        int length=in.readInt();if(length==-1)return null;
        if(length<0 || length>STRING_LIMIT || length>in.available()) throw new IOException("Invalid metadata length");
        byte[] bytes=new byte[length];in.readFully(bytes);
        try {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){throw new IOException("Invalid metadata encoding",e);}
    }
    private static MessageDigest shaDigest() throws IOException {
        try{return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}
    }
    private static byte[] sha(byte[] bytes) throws IOException {return shaDigest().digest(bytes);}
    private static String digest(byte[] bytes) throws IOException {return hex(sha(bytes));}
    private static String hex(byte[] hash) {
        char[] result=new char[64];char[] digits="0123456789abcdef".toCharArray();
        for(int i=0;i<hash.length;i++){int v=hash[i]&255;result[i*2]=digits[v>>>4];result[i*2+1]=digits[v&15];}
        return new String(result);
    }
}
