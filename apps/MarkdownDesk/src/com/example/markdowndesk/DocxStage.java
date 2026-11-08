package com.example.markdowndesk;

import android.system.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Exclusive ephemeral DOCX namespace. Never accepts arbitrary private-store paths. */
final class DocxStage {
    static final long MAX=8L<<20;
    static final class DisposalFailure extends IOException {DisposalFailure(Throwable cause){super("Stage close uncertain",cause);}}
    private final File directory,file;
    private final Object directoryKey,fileKey;
    private final FileChannel writer;
    private boolean closeAttempted,closeSucceeded,uncertain;
    private long attempted,length;
    private String hash;

    private static final class Listing implements AutoCloseable {
        final DirectoryStream<Path> stream;
        Listing(DirectoryStream<Path> stream){this.stream=stream;}
        public void close()throws DisposalFailure {try{stream.close();}catch(IOException|RuntimeException|Error failed){throw new DisposalFailure(failed);}}
    }
    private DocxStage(File directory,File file,FileChannel writer)throws IOException {
        this.directory=directory;this.file=file;this.writer=writer;
        directoryKey=attrs(directory).fileKey();fileKey=attrs(file).fileKey();
        if(directoryKey==null||fileKey==null)throw new IOException("Missing staging identity");
    }
    static DocxStage create(File cache)throws IOException {
        File parent=cache.getCanonicalFile();BasicFileAttributes base=attrs(parent);
        if(!base.isDirectory()||base.fileKey()==null)throw new IOException("Invalid cache root");
        File directory=new File(parent,"md-docx-stage-v1");
        if(!Files.exists(directory.toPath(),LinkOption.NOFOLLOW_LINKS)){Files.createDirectory(directory.toPath());syncDirectory(parent);}
        BasicFileAttributes root=attrs(directory);if(!root.isDirectory()||root.isSymbolicLink()||root.fileKey()==null)throw new IOException("Invalid stage namespace");
        File orphan=null;Object orphanKey=null;
        try(Listing members=new Listing(Files.newDirectoryStream(directory.toPath()))) {
            for(Path path:members.stream){if(orphan!=null)throw new IOException("Multiple staging members");String name=path.getFileName().toString();
                if(name.length()!=42||!name.endsWith(".stage"))throw new IOException("Unknown staging member");
                try{if(!UUID.fromString(name.substring(0,36)).toString().equals(name.substring(0,36)))throw new IllegalArgumentException();}catch(IllegalArgumentException bad){throw new IOException("Unknown staging member");}
                BasicFileAttributes a=attrs(path.toFile());if(!a.isRegularFile()||a.isSymbolicLink()||a.fileKey()==null||a.size()>MAX)throw new IOException("Unsafe staging member");orphan=path.toFile();orphanKey=a.fileKey();
            }
        }
        if(!Objects.equals(root.fileKey(),attrs(directory).fileKey()))throw new IOException("Stage namespace replaced");
        if(orphan!=null){if(!Objects.equals(orphanKey,attrs(orphan).fileKey()))throw new IOException("Orphan identity changed");Files.delete(orphan.toPath());syncDirectory(directory);}
        File file=new File(directory,UUID.randomUUID()+".stage");
        FileChannel channel=FileChannel.open(file.toPath(),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        return takeCreated(directory,file,channel);
    }
    /** Ownership transfer includes constructor/allocation failure, not only checked IO. */
    private static DocxStage takeCreated(File directory,File file,FileChannel channel)throws IOException {
        try{return new DocxStage(directory,file,channel);}
        catch(IOException|RuntimeException|Error failure){closeAfterFailure(channel,failure);throw failure;}
    }
    private static boolean closeAfterFailure(FileChannel channel,Throwable failure){
        try{channel.close();return false;}
        catch(IOException|RuntimeException|Error close){failure.addSuppressed(new DisposalFailure(close));return true;}
    }

    OutputStream output(){return new OutputStream(){
        public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}
        public void write(byte[] bytes,int offset,int count)throws IOException {
            if(closeAttempted||count<0||count>MAX-attempted)throw new IOException("Staging byte allowance exhausted");
            attempted+=count;ByteBuffer buffer=ByteBuffer.wrap(bytes,offset,count);
            while(buffer.hasRemaining())if(writer.write(buffer)<=0)throw new IOException("Staging write stalled");
        }
        public void close()throws IOException {
            if(closeAttempted)return;closeAttempted=true;Throwable failure=null;
            try{writer.force(true);}catch(IOException|RuntimeException|Error bad){failure=bad;}
            finally{
                try{writer.close();closeSucceeded=true;}catch(IOException|RuntimeException|Error bad){uncertain=true;if(failure==null)failure=bad;else failure.addSuppressed(bad);}
            }
            if(failure instanceof IOException)throw(IOException)failure;
            if(failure instanceof RuntimeException)throw(RuntimeException)failure;
            if(failure instanceof Error)throw(Error)failure;
        }
    };}
    boolean disposed(){return closeSucceeded&&!uncertain;}
    long length(){return length;}
    String hash(){return hash;}
    void seal(MarkdownDocx.Result result,MarkdownDocx.Cancellation cancel)throws IOException {
        if(!closeAttempted||!disposed()||result.length<1||result.length>MAX)throw new IOException("Unclosed stage");
        length=result.length;hash=result.sha256;syncDirectory(directory);
        try(ReadHandle input=openVerified(cancel)){/* verify before exposing source-free offer */}
    }
    ReadHandle openVerified(MarkdownDocx.Cancellation cancel)throws IOException {
        if(!disposed())throw new IOException("Staging disposal uncertain");
        identity();BasicFileAttributes before=attrs(file);
        if(before.size()!=length||hash==null)throw new IOException("Staging length changed");
        FileChannel input=FileChannel.open(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);
        ReadHandle handle=null;
        try {
            handle=new ReadHandle(input);
            MessageDigest digest=digest();ByteBuffer buffer=ByteBuffer.allocate(65536);long total=0;
            while(true){cancel.check();buffer.clear();int n=input.read(buffer);if(n<0)break;if(n==0)throw new IOException("Staging read stalled");total+=n;if(total>length)throw new IOException("Staging grew");digest.update(buffer.array(),0,n);}
            identity();if(total!=length||!hash.equals(hex(digest.digest())))throw new IOException("Staging checksum changed");input.position(0);return handle;
        }catch(IOException|RuntimeException|Error failure){
            if(handle==null){boolean prior=uncertain;uncertain=true;boolean failedClose=closeAfterFailure(input,failure);uncertain=prior||failedClose;}
            else try{handle.close();}catch(IOException|RuntimeException|Error close){failure.addSuppressed(new DisposalFailure(close));}
            throw failure;
        }
    }
    final class ReadHandle implements AutoCloseable {
        final FileChannel channel;private boolean closed;
        ReadHandle(FileChannel channel){this.channel=channel;}
        public void close()throws IOException {if(closed)return;closed=true;try{channel.close();}catch(IOException|RuntimeException|Error failure){uncertain=true;throw failure;}}
    }
    void identity()throws IOException {
        BasicFileAttributes root=attrs(directory),a=attrs(file);
        if(!root.isDirectory()||!Objects.equals(directoryKey,root.fileKey())||!a.isRegularFile()||a.isSymbolicLink()||!Objects.equals(fileKey,a.fileKey()))throw new IOException("Staging identity changed");
    }
    void cleanup()throws IOException {
        if(!disposed())throw new IOException("Staging disposal uncertain");
        BasicFileAttributes root=attrs(directory);if(!root.isDirectory()||!Objects.equals(directoryKey,root.fileKey()))throw new IOException("Stage namespace replaced");
        if(Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS)){identity();Files.delete(file.toPath());}
        syncDirectory(directory);
    }
    private static BasicFileAttributes attrs(File path)throws IOException{return Files.readAttributes(path.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException bad){throw new AssertionError(bad);}}
    static String hex(byte[] bytes){StringBuilder text=new StringBuilder();for(byte b:bytes)text.append(String.format(Locale.ROOT,"%02x",b&255));return text.toString();}
    private static void syncDirectory(File directory)throws IOException {
        FileDescriptor descriptor;
        try{descriptor=Os.open(directory.getPath(),OsConstants.O_RDONLY|OsConstants.O_NOFOLLOW|OsConstants.O_CLOEXEC,0);}catch(ErrnoException bad){throw new IOException("Stage directory open failed",bad);}
        Throwable failure=null;
        try{if(!OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode))throw new IOException("Stage directory changed");Os.fsync(descriptor);}
        catch(ErrnoException bad){failure=new IOException("Stage directory sync failed",bad);}
        catch(IOException|RuntimeException|Error bad){failure=bad;}
        finally{
            try{Os.close(descriptor);}catch(ErrnoException|RuntimeException|Error bad){DisposalFailure close=new DisposalFailure(bad);if(failure!=null)close.addSuppressed(failure);failure=close;}
        }
        if(failure instanceof IOException)throw(IOException)failure;
        if(failure instanceof RuntimeException)throw(RuntimeException)failure;
        if(failure instanceof Error)throw(Error)failure;
    }
}
