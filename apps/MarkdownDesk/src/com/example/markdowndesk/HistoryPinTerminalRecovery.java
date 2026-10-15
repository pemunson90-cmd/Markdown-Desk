package com.example.markdowndesk;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Append only an exact canonical CLOSE suffix to an existing no-follow inode. */
final class HistoryPinTerminalRecovery {
    enum Event {BEFORE_APPEND,AFTER_APPEND,AFTER_FORCE}
    interface Boundary {void at(Event event)throws IOException;}
    interface Validation {void check()throws IOException;}
    private HistoryPinTerminalRecovery(){}
    static void append(File stage,byte[] expected,Validation validation,Boundary boundary)throws IOException {
        if(expected.length!=HistoryPinRecord.CLOSE_BYTES)throw new IOException("Noncanonical CLOSE");
        BasicFileAttributes original=attributes(stage);byte[] prefix=prefix(stage,original);
        if(prefix.length>=expected.length||!Arrays.equals(prefix,Arrays.copyOf(expected,prefix.length)))throw new IOException("Invalid append prefix");
        boundary.at(Event.BEFORE_APPEND);
        try(FileChannel channel=FileChannel.open(stage.toPath(),StandardOpenOption.WRITE,StandardOpenOption.APPEND,LinkOption.NOFOLLOW_LINKS)){
            same(original,attributes(stage));if(!Arrays.equals(prefix,prefix(stage,original)))throw new IOException("CLOSE prefix changed");
            validation.check();
            // No delegate/observer call between the final identity/authority proof and direct append.
            ByteBuffer suffix=ByteBuffer.wrap(expected,prefix.length,expected.length-prefix.length);
            while(suffix.hasRemaining())if(channel.write(suffix)<=0)throw new IOException("CLOSE append made no progress");
            boundary.at(Event.AFTER_APPEND);verify(stage,original,expected);channel.force(true);boundary.at(Event.AFTER_FORCE);verify(stage,original,expected);
        }
        verify(stage,original,expected);
    }
    private static byte[] prefix(File stage,BasicFileAttributes before)throws IOException {
        if(!before.isRegularFile()||before.size()<0||before.size()>HistoryPinRecord.CLOSE_BYTES)throw new IOException("CLOSE stage bounds/type");
        ByteBuffer bytes=ByteBuffer.allocate((int)before.size());
        try(SeekableByteChannel channel=Files.newByteChannel(stage.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(bytes.hasRemaining())if(channel.read(bytes)<=0)throw new IOException("CLOSE prefix truncated");if(channel.read(ByteBuffer.allocate(1))!=-1)throw new IOException("CLOSE prefix grew");
        }same(before,attributes(stage));return bytes.array();
    }
    private static void verify(File stage,BasicFileAttributes original,byte[] expected)throws IOException {
        BasicFileAttributes now=attributes(stage);sameInode(original,now);if(!Arrays.equals(expected,prefix(stage,now)))throw new IOException("CLOSE append readback changed");
    }
    private static BasicFileAttributes attributes(File file)throws IOException{return Files.readAttributes(file.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static void sameInode(BasicFileAttributes a,BasicFileAttributes b)throws IOException {
        if(!a.isRegularFile()||!b.isRegularFile()||a.fileKey()==null||!Objects.equals(a.fileKey(),b.fileKey())||!a.creationTime().equals(b.creationTime()))throw new IOException("CLOSE stage inode changed");
    }
    private static void same(BasicFileAttributes a,BasicFileAttributes b)throws IOException {sameInode(a,b);if(a.size()!=b.size()||!a.lastModifiedTime().equals(b.lastModifiedTime()))throw new IOException("CLOSE prefix changed");}
}
