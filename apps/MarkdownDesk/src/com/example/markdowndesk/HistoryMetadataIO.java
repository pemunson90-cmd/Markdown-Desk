package com.example.markdowndesk;

import java.io.*;
import java.nio.*;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Bounded append-only metadata protocol shared by identity and optional ownership records. */
final class HistoryMetadataIO {
    static final int MAX_RECORD=16384, MAX_STRING=4096, MAX_ENTRIES=4096;
    static final long MAX_BYTES=8L*1024*1024;
    final File root;
    final Object lock;
    private final String extension;
    private final int magic;
    private final ProtectionStore.Durability durability;
    interface Writer {void write(DataOutputStream out)throws IOException;}
    interface Check {void validate()throws IOException;}
    HistoryMetadataIO(File root,String extension,int magic,ProtectionStore.Durability durability)throws IOException {
        if(root==null||durability==null)throw new IOException("Invalid history metadata configuration");
        ancestors(root.getAbsoluteFile().toPath());
        this.root=HistoryStorage.canonicalRoot(root);this.extension=extension;this.magic=magic;this.durability=durability;
        lock=HistoryStorage.monitorFor(this.root);
        synchronized(lock){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(this.root)){
            if(HistoryStorage.constructorReadOnly(this.root,(".identity".equals(extension)?3:4),null))return;
            ancestors(this.root.toPath());File parent=this.root.getParentFile();
            if(parent==null||!Files.isDirectory(parent.toPath(),LinkOption.NOFOLLOW_LINKS)||!parent.getCanonicalFile().equals(parent))
                throw new IOException("History metadata parent is not canonical directory");
            if(!Files.exists(this.root.toPath(),LinkOption.NOFOLLOW_LINKS))Files.createDirectory(this.root.toPath());
            if(!Files.isDirectory(this.root.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid history metadata directory");
            durability.syncDirectory(parent);durability.syncDirectory(this.root);inventory();
        }}
    }
    private static final class Inventory {final List<File> files=new ArrayList<>();long bytes;}
    private Inventory inventory()throws IOException {
        ancestors(root.toPath());if(!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("History metadata directory missing");
        Inventory result=new Inventory();
        try(DirectoryStream<Path> paths=Files.newDirectoryStream(root.toPath())){
            for(Path path:paths){
                if(result.files.size()>=MAX_ENTRIES)throw new IOException("History metadata entry limit exceeded");
                String name=path.getFileName().toString(),ext=name.endsWith(extension)?extension:name.endsWith(".stage")?".stage":null;
                if(ext==null)throw new IOException("Unknown history metadata");
                id(name.substring(0,name.length()-ext.length()));BasicFileAttributes attrs=attributes(path);
                if(!attrs.isRegularFile()||attrs.size()<0||attrs.size()>MAX_RECORD)throw new IOException("Invalid history metadata entry");
                if(attrs.size()>MAX_BYTES-result.bytes)throw new IOException("History metadata byte limit exceeded");
                result.bytes+=attrs.size();result.files.add(path.toFile());
            }
        }catch(DirectoryIteratorException failure){throw new IOException("Cannot enumerate history metadata",failure.getCause());}
        result.files.sort(Comparator.comparing(File::getName));return result;
    }
    List<File> files()throws IOException {
        return files(null);
    }
    List<File> files(HistoryTombstoneView view)throws IOException {
        Inventory inventory=inventory();if(view==null)durability.syncDirectory(root);
        for(File file:inventory.files)if(file.getName().endsWith(".stage"))throw new IOException("Uncommitted history metadata retained; enumeration blocked");
        if(view!=null)inventory.files.removeIf(file->view.contains(extension.equals(".identity")?"identity":"index",file.getName()));
        return inventory.files;
    }
    byte[] encode(Writer writer)throws IOException {
        ByteArrayOutputStream body=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(body)){writer.write(out);}
        if(body.size()>MAX_RECORD-44)throw new IOException("History metadata record limit exceeded");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){
            out.writeInt(magic);out.writeInt(1);out.writeInt(body.size());body.writeTo(out);out.write(sha(bytes.toByteArray()));
        }return bytes.toByteArray();
    }
    DataInputStream body(File file)throws IOException {
        byte[] bytes=read(file);
        if(!MessageDigest.isEqual(Arrays.copyOfRange(bytes,bytes.length-32,bytes.length),sha(Arrays.copyOf(bytes,bytes.length-32))))
            throw new IOException("History metadata checksum mismatch");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes,0,bytes.length-32));
        if(in.readInt()!=magic||in.readInt()!=1||in.readInt()!=bytes.length-44)throw new IOException("Unsupported history metadata framing");
        return in;
    }
    void commit(String id,byte[] record,Check check)throws IOException {
        HistoryStorage.requireMutation(root);id(id);Inventory inventory=inventory();
        if(inventory.files.size()>=MAX_ENTRIES||record.length>MAX_BYTES-inventory.bytes)throw new IOException("History metadata quota exhausted including stages");
        File destination=new File(root,id+extension),stage=new File(root,UUID.randomUUID()+".stage");
        if(Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Immutable history record exists");
        if(HistoryStorage.controlled(root))HistoryStorage.capacity(root).admit(root,record.length,0,1,0);
        HistoryStorage.changed(root);Files.createFile(stage.toPath());
        try(FileOutputStream out=new FileOutputStream(stage)){out.write(record);out.flush();durability.syncFile(out.getFD());}
        check.validate();durability.move(stage,destination);
        if(Files.exists(stage.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Atomic history move did not consume stage");
        durability.syncDirectory(root);
        if(!Arrays.equals(record,read(destination)))throw new IOException("Committed history metadata differs");
    }
    private byte[] read(File file)throws IOException {
        ancestors(root.toPath());BasicFileAttributes before=attributes(file.toPath());long size=before.size();
        if(!before.isRegularFile()||size<44||size>MAX_RECORD)throw new IOException("Invalid history metadata size");
        ByteBuffer buffer=ByteBuffer.allocate((int)size);
        try(SeekableByteChannel channel=Files.newByteChannel(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(buffer.hasRemaining())if(channel.read(buffer)<0)throw new IOException("Truncated history metadata");
            if(channel.read(ByteBuffer.allocate(1))!=-1)throw new IOException("History metadata grew while reading");
        }
        BasicFileAttributes after=attributes(file.toPath());
        if(!after.isRegularFile()||size!=after.size()||!Objects.equals(before.fileKey(),after.fileKey()))throw new IOException("History metadata changed while reading");
        return buffer.array();
    }
    static void id(String value)throws IOException {
        try{if(value==null||!UUID.fromString(value).toString().equals(value))throw new IOException("Invalid history UUID");}
        catch(IllegalArgumentException failure){throw new IOException("Invalid history UUID",failure);}
    }
    static byte[] utf8(String value)throws IOException {
        if(value==null||value.length()>MAX_STRING)throw new IOException("Invalid history string length");
        try{
            ByteBuffer buffer=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            if(buffer.remaining()>MAX_STRING)throw new IOException("History string byte limit exceeded");byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;
        }catch(CharacterCodingException failure){throw new IOException("Invalid history UTF-8",failure);}
    }
    static void string(DataOutputStream out,String value)throws IOException {byte[] bytes=utf8(value);out.writeInt(bytes.length);out.write(bytes);}
    static String string(DataInputStream in)throws IOException {
        int length=in.readInt();if(length<0||length>MAX_STRING||length>in.available())throw new IOException("Invalid history string framing");
        byte[] bytes=new byte[length];in.readFully(bytes);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException failure){throw new IOException("Invalid history UTF-8",failure);}
    }
    static boolean empty(String value){return value==null||value.isEmpty();}
    private static byte[] sha(byte[] bytes)throws IOException {
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException failure){throw new IOException("SHA-256 unavailable",failure);}
    }
    private static BasicFileAttributes attributes(Path path)throws IOException {return Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static void ancestors(Path path)throws IOException {for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Symlink in history metadata path");}
}
