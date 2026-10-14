package com.example.markdowndesk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Append-only names for existing protected snapshots. No document payloads or deletion. */
public final class HistoryCatalog {
    private static final int MAGIC=0x4d444843, VERSION=1;
    private static final int MAX_RECORD_BYTES=16384, MAX_LABEL_BYTES=256, MAX_STRING_BYTES=4096;
    private static final int MAX_RECORDS=4096;
    private static final long MAX_METADATA_BYTES=8L*1024*1024;

    public static final class Checkpoint {
        public final String id, snapshotId, documentId, snapshotHash, label;
        public final long createdAt;
        private Checkpoint(String id,String snapshotId,String documentId,String snapshotHash,String label,long createdAt) {
            this.id=id;this.snapshotId=snapshotId;this.documentId=documentId;this.snapshotHash=snapshotHash;
            this.label=label;this.createdAt=createdAt;
        }
    }

    File storageRoot(){return root;}
    ProtectionStore sourceStore(){return store;}
    private final File root;
    private final ProtectionStore store;
    private final ProtectionStore.Durability durability;
    private final Object lock;

    /** root is a separate private catalog directory, normally the sibling history_catalog. */
    public HistoryCatalog(File root,ProtectionStore store,ProtectionStore.Durability durability) throws IOException {
        if(root==null || store==null || durability==null)throw new IOException("Invalid catalog configuration");
        checkAncestors(root.getAbsoluteFile().toPath());
        this.root=HistoryStorage.canonicalRoot(root);
        this.store=store;this.durability=durability;
        lock=HistoryStorage.monitorFor(this.root);
        if(lock!=HistoryStorage.monitorFor(store.storageRoot()))throw new IOException("Catalog and source require same private parent");
        synchronized(lock) {
            checkAncestors(this.root.toPath());
            File parent=this.root.getParentFile();
            if(parent==null || !Files.isDirectory(parent.toPath(),LinkOption.NOFOLLOW_LINKS)
                    || !parent.getCanonicalFile().equals(parent))throw new IOException("Catalog parent is not trusted canonical directory");
            if(!Files.exists(this.root.toPath(),LinkOption.NOFOLLOW_LINKS))Files.createDirectory(this.root.toPath());
            if(!Files.isDirectory(this.root.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid catalog directory");
            // Retry durability even when a failed prior construction left the directory behind.
            durability.syncDirectory(parent);durability.syncDirectory(this.root);
            inventory();
        }
    }

    public Checkpoint name(String snapshotId,String label) throws IOException {
        synchronized(lock) {
            validateId(snapshotId);validateLabel(label);
            Inventory inventory=inventory();
            // Invalid committed metadata cannot silently become a partial history.
            for(File entry:inventory.files)if(entry.getName().endsWith(".checkpoint"))readCheckpoint(entry);
            ProtectionStore.Snapshot snapshot=store.snapshot(snapshotId);
            Checkpoint checkpoint=new Checkpoint(UUID.randomUUID().toString(),snapshot.id,snapshot.documentId,
                    snapshot.hash,label,System.currentTimeMillis());
            validate(checkpoint);
            byte[] record=encode(checkpoint);
            if(inventory.files.size()>=MAX_RECORDS || record.length>MAX_METADATA_BYTES-inventory.bytes)
                throw new IOException("Catalog quota exhausted (including stages)");
            File destination=file(checkpoint.id,".checkpoint"), stage=file(UUID.randomUUID().toString(),".stage");
            checkAncestors(root.toPath());
            if(Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Immutable checkpoint exists");
            // Generated paths, CREATE_NEW, and retained failures mirror the private protection protocol.
            HistoryStorage.changed(root);Files.createFile(stage.toPath());
            try(FileOutputStream out=new FileOutputStream(stage)) {
                out.write(record);out.flush();durability.syncFile(out.getFD());
            }
            // Revalidate the source immediately before metadata publication. No source is ever rewritten.
            validateSource(checkpoint);
            durability.move(stage,destination);
            if(Files.exists(stage.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Atomic move did not consume catalog stage");
            durability.syncDirectory(root);
            byte[] committed=readRecord(destination);
            if(!Arrays.equals(record,committed))throw new IOException("Committed checkpoint differs");
            return decodeAndValidate(destination,committed);
        }
    }

    /** Validates the complete catalog before filtering, so another document's corruption remains visible. */
    public List<Checkpoint> list(String documentId) throws IOException {
        synchronized(lock) {
            if(documentId==null || documentId.isEmpty())throw new IOException("Invalid document ID");
            utf8(documentId,MAX_STRING_BYTES);
            List<Checkpoint> result=new ArrayList<>();
            for(Checkpoint checkpoint:readAll())if(checkpoint.documentId.equals(documentId))result.add(checkpoint);
            return Collections.unmodifiableList(result);
        }
    }

    /** Fresh complete immutable checkpoint metadata, avoiding a full rescan for each document. */
    public List<Checkpoint> all() throws IOException {
        synchronized(lock) {return store.withValidatedSources(this::allAgainstValidatedSnapshots);}
    }

    /** Never returns a partial set that a later retention planner could mistake for complete pins. */
    public Set<String> pinnedSnapshotIds() throws IOException {
        synchronized(lock) {
            Set<String> result=new LinkedHashSet<>();
            for(Checkpoint checkpoint:readAll())result.add(checkpoint.snapshotId);
            return Collections.unmodifiableSet(result);
        }
    }

    List<Checkpoint> allAgainstValidatedSnapshots(ProtectionStore.ValidatedSources sources)throws IOException {
        sources.require(store);
        if(!sources.inventory.complete)throw new IOException("Incomplete validated source inventory");
        Inventory inventory=inventory();durability.syncDirectory(root);List<Checkpoint> result=new ArrayList<>();
        for(File entry:inventory.files){if(entry.getName().endsWith(".stage"))throw new IOException("Retained checkpoint stage");
            result.add(decodeAndValidate(entry,readRecord(entry),sources));}
        return Collections.unmodifiableList(result);
    }
    private List<Checkpoint> readAll() throws IOException {
        Inventory inventory=inventory();
        durability.syncDirectory(root);
        List<Checkpoint> result=new ArrayList<>();
        for(File entry:inventory.files) {
            // A crash stage may be partial or refer to a pin not yet published. Retain it and block pruning.
            if(entry.getName().endsWith(".stage"))throw new IOException("Uncommitted catalog metadata; pin enumeration blocked");
            result.add(readCheckpoint(entry));
        }
        return result;
    }

    private static final class Inventory {
        final List<File> files=new ArrayList<>();long bytes;
    }
    private Inventory inventory() throws IOException {
        checkAncestors(root.toPath());
        if(!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Catalog directory missing");
        Inventory inventory=new Inventory();
        // Stop before collecting unbounded directory entries, including interrupted stages.
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(root.toPath())) {
            for(Path path:entries) {
                if(inventory.files.size()>=MAX_RECORDS)throw new IOException("Catalog entry limit exceeded");
                String name=path.getFileName().toString();
                String extension=name.endsWith(".checkpoint")?".checkpoint":name.endsWith(".stage")?".stage":null;
                if(extension==null)throw new IOException("Unknown catalog metadata");
                validateId(name.substring(0,name.length()-extension.length()));
                BasicFileAttributes attributes=attributes(path);
                if(!attributes.isRegularFile() || attributes.size()<0 || attributes.size()>MAX_RECORD_BYTES)
                    throw new IOException("Invalid catalog entry");
                if(attributes.size()>MAX_METADATA_BYTES-inventory.bytes)throw new IOException("Catalog byte limit exceeded");
                inventory.bytes+=attributes.size();inventory.files.add(path.toFile());
            }
        } catch(java.nio.file.DirectoryIteratorException failure) {
            throw new IOException("Cannot enumerate catalog",failure.getCause());
        }
        inventory.files.sort((a,b)->a.getName().compareTo(b.getName()));
        return inventory;
    }

    private Checkpoint readCheckpoint(File file) throws IOException {return decodeAndValidate(file,readRecord(file));}
    private Checkpoint decodeAndValidate(File file,byte[] raw) throws IOException {return decodeAndValidate(file,raw,null);}
    private Checkpoint decodeAndValidate(File file,byte[] raw,ProtectionStore.ValidatedSources sources) throws IOException {
        byte[] expected=Arrays.copyOfRange(raw,raw.length-32,raw.length);
        if(!MessageDigest.isEqual(expected,sha(Arrays.copyOf(raw,raw.length-32))))throw new IOException("Checkpoint checksum mismatch");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(raw,0,raw.length-32))) {
            if(in.readInt()!=MAGIC || in.readInt()!=VERSION || in.readInt()!=raw.length-44)
                throw new IOException("Unsupported checkpoint framing");
            Checkpoint checkpoint=new Checkpoint(readString(in,MAX_STRING_BYTES),readString(in,MAX_STRING_BYTES),
                    readString(in,MAX_STRING_BYTES),readString(in,MAX_STRING_BYTES),readString(in,MAX_LABEL_BYTES),in.readLong());
            if(in.available()!=0 || !file.getName().equals(checkpoint.id+".checkpoint"))throw new IOException("Checkpoint identity/framing mismatch");
            validate(checkpoint);
            if(sources==null)validateSource(checkpoint);else validateSource(checkpoint,sources.snapshot(store,checkpoint.snapshotId));return checkpoint;
        }
    }
    private byte[] readRecord(File file) throws IOException {
        checkAncestors(root.toPath());
        BasicFileAttributes before=attributes(file.toPath());long size=before.size();
        if(!before.isRegularFile() || size<44 || size>MAX_RECORD_BYTES)throw new IOException("Invalid checkpoint size");
        ByteBuffer buffer=ByteBuffer.allocate((int)size);
        try(SeekableByteChannel channel=Files.newByteChannel(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            while(buffer.hasRemaining())if(channel.read(buffer)<0)throw new IOException("Truncated checkpoint");
            if(channel.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Checkpoint grew while reading");
        }
        BasicFileAttributes after=attributes(file.toPath());
        if(!after.isRegularFile() || after.size()!=size || !Objects.equals(before.fileKey(),after.fileKey()))
            throw new IOException("Checkpoint changed while reading");
        return buffer.array();
    }
    private void validateSource(Checkpoint checkpoint) throws IOException {
        ProtectionStore.Snapshot source=store.snapshot(checkpoint.snapshotId);
        validateSource(checkpoint,source);
    }
    private static void validateSource(Checkpoint checkpoint,ProtectionStore.Snapshot source)throws IOException {
        if(!source.id.equals(checkpoint.snapshotId) || !source.documentId.equals(checkpoint.documentId)
                || !source.hash.equals(checkpoint.snapshotHash))throw new IOException("Checkpoint source link mismatch");
    }
    private static void validate(Checkpoint checkpoint) throws IOException {
        validateId(checkpoint.id);validateId(checkpoint.snapshotId);validateLabel(checkpoint.label);
        if(checkpoint.documentId==null || checkpoint.documentId.isEmpty() || checkpoint.createdAt<0
                || checkpoint.snapshotHash==null || !checkpoint.snapshotHash.matches("[0-9a-f]{64}"))
            throw new IOException("Invalid checkpoint metadata");
        utf8(checkpoint.documentId,MAX_STRING_BYTES);
    }
    private static void validateLabel(String label) throws IOException {
        utf8(label,MAX_LABEL_BYTES);
        if(label.codePoints().allMatch(value->Character.isWhitespace(value)||Character.isSpaceChar(value)))
            throw new IOException("Checkpoint label must be nonblank");
    }
    private static void validateId(String id) throws IOException {
        try {if(id==null || !UUID.fromString(id).toString().equals(id))throw new IOException("Invalid catalog ID");}
        catch(IllegalArgumentException failure){throw new IOException("Invalid catalog ID",failure);}
    }
    private File file(String id,String extension) throws IOException {validateId(id);return new File(root,id+extension);}
    private static void checkAncestors(Path path) throws IOException {
        for(Path current=path;current!=null;current=current.getParent())
            if(Files.isSymbolicLink(current))throw new IOException("Symlink in catalog path");
    }
    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
    }
    private static byte[] encode(Checkpoint checkpoint) throws IOException {
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(body)) {
            writeString(out,checkpoint.id,MAX_STRING_BYTES);writeString(out,checkpoint.snapshotId,MAX_STRING_BYTES);
            writeString(out,checkpoint.documentId,MAX_STRING_BYTES);writeString(out,checkpoint.snapshotHash,MAX_STRING_BYTES);
            writeString(out,checkpoint.label,MAX_LABEL_BYTES);out.writeLong(checkpoint.createdAt);
        }
        ByteArrayOutputStream record=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(record)) {
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(body.size());body.writeTo(out);
            out.write(sha(record.toByteArray()));
        }
        if(record.size()>MAX_RECORD_BYTES)throw new IOException("Checkpoint record limit exceeded");
        return record.toByteArray();
    }
    private static byte[] utf8(String value,int limit) throws IOException {
        if(value==null || value.length()>limit)throw new IOException("Checkpoint string byte limit exceeded");
        try {
            ByteBuffer buffer=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            if(buffer.remaining()>limit)throw new IOException("Checkpoint string byte limit exceeded");
            byte[] result=new byte[buffer.remaining()];buffer.get(result);return result;
        } catch(CharacterCodingException failure){throw new IOException("Invalid checkpoint UTF-8",failure);}
    }
    private static void writeString(DataOutputStream out,String value,int limit) throws IOException {
        byte[] bytes=utf8(value,limit);out.writeInt(bytes.length);out.write(bytes);
    }
    private static String readString(DataInputStream in,int limit) throws IOException {
        int length=in.readInt();
        if(length<0 || length>limit || length>in.available())throw new IOException("Invalid checkpoint string length");
        byte[] bytes=new byte[length];in.readFully(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch(CharacterCodingException failure){throw new IOException("Invalid checkpoint UTF-8",failure);}
    }
    private static byte[] sha(byte[] bytes) throws IOException {
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(NoSuchAlgorithmException failure){throw new IOException("SHA-256 unavailable",failure);}
    }
}
